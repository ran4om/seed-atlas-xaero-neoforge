package org.seedatlas.xaero.neoforge;

import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.seedatlas.xaero.SeedAtlasXaero;
import org.seedatlas.xaero.command.MinecraftCommandSource;
import org.seedatlas.xaero.command.SeedAtlasCommands;
import org.seedatlas.xaero.integration.ExploredTerrainRenderTypes;
import org.seedatlas.xaero.integration.icon.StructureIcons;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value = SeedAtlasXaero.MOD_ID, dist = Dist.CLIENT)
public final class SeedAtlasNeoForge {
    private static final Logger LOGGER = LoggerFactory.getLogger(SeedAtlasNeoForge.class);

    public SeedAtlasNeoForge(IEventBus modBus) {
        modBus.addListener((FMLClientSetupEvent event) ->
            event.enqueueWork(() -> SeedAtlasXaero.initialize(FMLPaths.CONFIGDIR.get())));
        modBus.addListener((RegisterClientReloadListenersEvent event) ->
            event.registerReloadListener(StructureIcons.reloadListener()));
        modBus.addListener((RegisterShadersEvent event) -> {
            try {
                ExploredTerrainRenderTypes.registerShaders(event);
            } catch (IOException exception) {
                // Xaero's own shaders still draw the map, just with darker seams.
                LOGGER.error("Could not load the Seed Atlas terrain shaders", exception);
            }
        });
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) ->
            SeedAtlasCommands.register(event.getDispatcher(), MinecraftCommandSource::new));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> SeedAtlasXaero.tick(Minecraft.getInstance()));
    }
}
