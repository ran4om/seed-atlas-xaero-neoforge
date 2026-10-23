package org.seedatlas.xaero.integration;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import xaero.lib.client.graphics.XaeroRenderType;
import xaero.lib.client.graphics.XaeroRenderType.MultiPhaseBuilder;
import xaero.lib.client.graphics.XaeroRenderType.MultiPhaseRenderType;
import xaero.lib.client.graphics.shader.LibShaders;
import xaero.lib.client.graphics.shader.WorldMapShader;
import xaero.map.WorldMap;
import xaero.map.graphics.CustomRenderTypes;

/**
 * Xaero's render type for explored terrain and the shader for its zoomed-out textures, with fragment
 * shaders that leave unexplored texels out of filtering. Xaero's own blend them in as black, which
 * only shows once the seed layer, not black, surrounds the explored terrain.
 */
public final class ExploredTerrainRenderTypes extends RenderType {
    private static WorldMapShader mapShader;
    private static ShaderInstance branchShader;
    private static RenderType map;

    private ExploredTerrainRenderTypes() {
        super("seedatlas_unused", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false,
            () -> { }, () -> { });
    }

    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        ResourceProvider resources = event.getResourceProvider();
        event.registerShader(new WorldMapShader("seedatlas_xaero:explored_map", resources),
            shader -> mapShader = (WorldMapShader)shader);
        event.registerShader(new ShaderInstance(resources, "seedatlas_xaero:explored_map_branch",
            DefaultVertexFormat.POSITION_TEX), shader -> branchShader = shader);
    }

    /** Used in place of Xaero's type; any other type passes through. */
    public static RenderType replace(RenderType type) {
        if (type != CustomRenderTypes.MAP || mapShader == null) return type;
        if (map == null) {
            map = new MultiPhaseRenderType("seedatlas_explored_map", DefaultVertexFormat.POSITION_TEX,
                VertexFormat.Mode.QUADS, 256, false, false, new MultiPhaseBuilder()
                    .texture(new TextureStateShard(WorldMap.guiTextures, false, false))
                    .transparency(XaeroRenderType.DEST_TRANSPARENCY)
                    .shader(new ShaderStateShard(ExploredTerrainRenderTypes::mapShader))
                    .cull(NO_CULL)
                    .target(XaeroRenderType.KEEP_TARGET)
                    .build());
        }
        return map;
    }

    /** Xaero sets brightness and lighting on its own map shader before each draw; mirror them. */
    private static ShaderInstance mapShader() {
        WorldMapShader source = LibShaders.WORLD_MAP;
        if (source != null && source.brightness != null && source.withLight != null) {
            mapShader.setBrightness(source.brightness.getFloatBuffer().get(0));
            mapShader.setWithLight(source.withLight.getIntBuffer().get(0) != 0);
        }
        return mapShader;
    }

    /** Branch shader for Xaero's zoomed-out texture updates, or Xaero's own before ours loads. */
    public static ShaderInstance branchShader(ShaderInstance fallback) {
        return branchShader != null ? branchShader : fallback;
    }
}
