package org.seedatlas.xaero.compat;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/**
 * Applies the Seed Atlas mixins only when the installed Xaero's World Map still matches them
 * (see {@link XaeroCompatibility}); otherwise none is applied and Xaero runs untouched.
 */
public final class SeedAtlasMixinPlugin implements IMixinConfigPlugin {
    private Boolean compatible;

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (this.compatible == null) {
            XaeroCompatibility.Report report = XaeroCompatibility.check(SeedAtlasMixinPlugin::read);
            XaeroCompatibility.publish(report);
            log(report);
            this.compatible = report.compatible();
        }
        return this.compatible;
    }

    /**
     * Untransformed bytes: this runs while mixins are being prepared, before any target loads.
     * Under NeoForge's ModLauncher the bytecode provider cannot serve classes yet at this point,
     * so the class files are read as resources of the game class loader instead.
     */
    private static ClassNode read(String internalName) {
        for (ClassLoader loader : new ClassLoader[] {Thread.currentThread().getContextClassLoader(),
                SeedAtlasMixinPlugin.class.getClassLoader()}) {
            if (loader == null) continue;
            try (InputStream stream = loader.getResourceAsStream(internalName + ".class")) {
                if (stream == null) continue;
                ClassNode node = new ClassNode();
                new ClassReader(stream.readAllBytes()).accept(node, 0);
                return node;
            } catch (IOException | RuntimeException exception) {
                // Try the next source.
            }
        }
        try {
            return MixinService.getService().getBytecodeProvider().getClassNode(internalName.replace('/', '.'), false);
        } catch (ClassNotFoundException | IOException | RuntimeException exception) {
            return null;
        }
    }

    private static void log(XaeroCompatibility.Report report) {
        ILogger logger = MixinService.getService().getLogger("seedatlas_xaero");
        if (!report.compatible()) {
            logger.error("Seed Atlas stays disabled: the installed Xaero's World Map changed what it relies on. "
                + "Xaero's map works normally; update Seed Atlas to restore the seed layer.");
            report.problems().forEach(problem -> logger.error("  - {}", problem));
        }
        report.warnings().forEach(warning -> logger.warn("Seed Atlas optional feature unavailable: {}", warning));
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
