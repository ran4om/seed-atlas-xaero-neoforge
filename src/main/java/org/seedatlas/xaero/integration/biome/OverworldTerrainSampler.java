package org.seedatlas.xaero.integration.biome;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

/**
 * Surface colours of the biome layer. Upstream also samples vanilla base terrain here through the
 * batched density API of Minecraft 26.x; 1.21.1 has no such API, so this port leaves terrain
 * heights to the native engine, which generates them for 1.21.1 itself.
 */
public final class OverworldTerrainSampler {
    private static final ConcurrentHashMap<String, Holder<Biome>> PREVIEW_BIOMES = new ConcurrentHashMap<>();

    private OverworldTerrainSampler() { }

    /** False on 1.21.1: callers use the native height fallback instead of {@link #sample}. */
    public static boolean available() {
        return false;
    }

    public static SurfaceGrid sample(long seed, boolean largeBiomes, int originX, int originZ,
                                     int step, int width, int height, BooleanSupplier stillCurrent) {
        throw new UnsupportedOperationException("Java terrain sampling needs Minecraft 26.x");
    }

    public record SurfaceGrid(float[] heights, float[] floors, int[] colors) { }

    /** Cheap LOD preview using the same material palette, without evaluating terrain density. */
    static int previewColor(String name, int x, int y, int z, boolean water, int step) {
        if (name == null) return -1;
        var biome = PREVIEW_BIOMES.computeIfAbsent(name, value -> Vanilla.LOOKUP.lookupOrThrow(Registries.BIOME)
                .get(ResourceKey.create(Registries.BIOME, ResourceLocation.parse(value))).orElse(null));
        return biome == null ? -1 : surfaceColor(biome, x, y, z, water, 63, step);
    }

    /** Surface colour of one sample; see {@link BiomeSurfacePalette}. */
    static int surfaceColor(Holder<Biome> biome, int x, int y, int z, boolean fluid, int seaLevel, int step) {
        return BiomeSurfacePalette.surfaceColor(biome, x, y, z, fluid, seaLevel, step);
    }

    private static final class Vanilla {
        private static final HolderLookup.Provider LOOKUP = VanillaRegistries.createLookup();
    }
}
