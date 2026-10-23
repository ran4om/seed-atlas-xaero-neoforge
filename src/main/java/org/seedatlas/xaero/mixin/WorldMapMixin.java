package org.seedatlas.xaero.mixin;

import org.seedatlas.xaero.integration.biome.SeedAtlasBiomeOverlayRenderer;
import org.seedatlas.xaero.integration.marker.SeedAtlasStructureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.WorldMap;

/** Adds the Seed Atlas layers once Xaero's full-map handler exists. */
@Mixin(value = WorldMap.class, remap = false)
abstract class WorldMapMixin {
    @Inject(method = "loadLater", at = @At(
        value = "FIELD",
        target = "Lxaero/map/WorldMap;mapElementRenderHandler:Lxaero/map/element/MapElementRenderHandler;",
        opcode = org.objectweb.asm.Opcodes.PUTSTATIC,
        shift = At.Shift.AFTER
    ))
    private void seedAtlas$registerRenderers(CallbackInfo ci) {
        WorldMap.mapElementRenderHandler.add(SeedAtlasBiomeOverlayRenderer.INSTANCE);
        WorldMap.mapElementRenderHandler.add(SeedAtlasStructureRenderer.INSTANCE);
    }
}
