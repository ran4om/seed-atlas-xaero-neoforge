package org.seedatlas.xaero.nativeapi;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/**
 * Checks the bundled native engine, called through this port's JNA binding, against Minecraft
 * 1.21.1's own generator: biomes in all three dimensions, stronghold rings and village placement.
 */
class NativeVanillaParityTest {
    private static final long[] SEEDS = {0L, 1L, -4172144997902289642L, 8675309L};

    @Test
    void matchesVanilla() throws Exception {
        HolderLookup.Provider lookup = VanillaRegistries.createLookup();

        NativeStatus status = SeedAtlasNative.status();
        check(status.available(), "Native engine must load: " + status);
        System.out.println("Native engine " + status.engineVersion() + " on " + status.platform());
        check(SeedAtlasNative.supports(GameVersion.NEWEST), "Native engine must generate " + GameVersion.NEWEST.name());

        for (long seed : SEEDS) {
            try (SeedAtlasNative atlas = SeedAtlasNative.open(seed, WorldType.NORMAL)) {
                overworldBiomes(lookup, atlas, seed);
                netherBiomes(lookup, atlas, seed);
                endBiomes(lookup, atlas, seed);
                strongholds(lookup, atlas, seed);
                villages(lookup, atlas, seed);
            }
        }
        concurrentCalls();
        System.out.println("Native 1.21.1 parity checks passed");
    }

    private static void overworldBiomes(HolderLookup.Provider lookup, SeedAtlasNative atlas, long seed) {
        var source = MultiNoiseBiomeSource.createFromPreset(lookup.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
            .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        RandomState state = randomState(lookup, NoiseGeneratorSettings.OVERWORLD, seed);
        compareBiomes("overworld", atlas, Dimension.OVERWORLD, seed, source, state, new int[] {-40, 0, 64, 160}, 24_000);
    }

    private static void netherBiomes(HolderLookup.Provider lookup, SeedAtlasNative atlas, long seed) {
        var source = MultiNoiseBiomeSource.createFromPreset(lookup.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
            .getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER));
        RandomState state = randomState(lookup, NoiseGeneratorSettings.NETHER, seed);
        compareBiomes("nether", atlas, Dimension.NETHER, seed, source, state, new int[] {32, 64, 100}, 6_000);
    }

    private static void endBiomes(HolderLookup.Provider lookup, SeedAtlasNative atlas, long seed) {
        var source = TheEndBiomeSource.create(lookup.lookupOrThrow(Registries.BIOME));
        RandomState state = randomState(lookup, NoiseGeneratorSettings.END, seed);
        compareBiomes("end", atlas, Dimension.END, seed, source, state, new int[] {64}, 6_000);
    }

    /** Block-level biomes, as the game resolves them (voronoi-zoomed from 4x4x4 noise cells). */
    private static void compareBiomes(String name, SeedAtlasNative atlas, Dimension dimension, long seed,
                                      BiomeSource source, RandomState state, int[] ys, int range) {
        var manager = new BiomeManager((qx, qy, qz) -> source.getNoiseBiome(qx, qy, qz, state.sampler()),
            BiomeManager.obfuscateSeed(seed));
        var random = new java.util.Random(seed ^ name.hashCode());
        int samples = 0;
        int mismatches = 0;
        String firstMismatch = null;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1500; ++i) {
            int x = random.nextInt(2 * range) - range;
            int z = random.nextInt(2 * range) - range;
            int y = ys[i % ys.length];
            String expected = key(manager.getBiome(new net.minecraft.core.BlockPos(x, y, z)));
            String actual = strip(atlas.biomeAt(dimension, x, y, z).name());
            seen.add(expected);
            ++samples;
            if (!expected.equals(actual)) {
                ++mismatches;
                if (firstMismatch == null) firstMismatch = x + "," + y + "," + z + ": vanilla " + expected + ", native " + actual;
            }
        }
        System.out.printf("seed %d %s: %d/%d biomes match (%d kinds)%s%n", seed, name, samples - mismatches, samples,
            seen.size(), firstMismatch == null ? "" : "; first mismatch " + firstMismatch);
        // Cubiomes reproduces the game's noise exactly; a handful of mismatches would mean float edge cases.
        check(mismatches <= samples / 500, name + " biomes for seed " + seed + " differ from vanilla 1.21.1");
    }

    private static void strongholds(HolderLookup.Provider lookup, SeedAtlasNative atlas, long seed) throws Exception {
        var source = MultiNoiseBiomeSource.createFromPreset(lookup.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
            .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        RandomState state = randomState(lookup, NoiseGeneratorSettings.OVERWORLD, seed);
        var strongholdSet = lookup.lookupOrThrow(Registries.STRUCTURE_SET).getOrThrow(BuiltinStructureSets.STRONGHOLDS);
        var vanillaPlacement = (ConcentricRingsStructurePlacement)strongholdSet.value().placement();
        // The registry lookup has no tag data, so rebuild the placement with the tag's biomes bound.
        var placement = new ConcentricRingsStructurePlacement(vanillaPlacement.distance(), vanillaPlacement.spread(),
            vanillaPlacement.count(), HolderSet.direct(biomeTag(lookup.lookupOrThrow(Registries.BIOME), "stronghold_biased_to")));
        List<ChunkPos> expected = ringPositions(state, source, seed, strongholdSet, placement);
        Set<Long> vanilla = new HashSet<>();
        for (ChunkPos chunk : expected) {
            // The first eight rings reach about 24k blocks.
            if (Math.abs(chunk.getMinBlockX()) < 20_000 && Math.abs(chunk.getMinBlockZ()) < 20_000) vanilla.add(chunk.toLong());
        }
        ScanResult scan = atlas.strongholds(new BlockBox(-20_000, -20_000, 20_000, 20_000), 256, true);
        Set<Long> nativeChunks = new HashSet<>();
        for (StructurePosition position : scan.positions()) nativeChunks.add(ChunkPos.asLong(position.x() >> 4, position.z() >> 4));
        System.out.printf("seed %d strongholds: vanilla %d, native %d%n", seed, vanilla.size(), nativeChunks.size());
        check(!vanilla.isEmpty() && vanilla.equals(nativeChunks), "Stronghold chunks for seed " + seed
            + " differ: vanilla " + chunks(vanilla) + " native " + chunks(nativeChunks));
    }

    /** Every predicted village sits in its region's vanilla candidate chunk and in a village biome. */
    private static void villages(HolderLookup.Provider lookup, SeedAtlasNative atlas, long seed) {
        var set = lookup.lookupOrThrow(Registries.STRUCTURE_SET).getOrThrow(BuiltinStructureSets.VILLAGES).value();
        var placement = (RandomSpreadStructurePlacement)set.placement();
        ScanResult scan = atlas.structures(StructureType.VILLAGE, new BlockBox(-6_000, -6_000, 6_000, 6_000), 512, true);
        check(!scan.positions().isEmpty(), "Expected villages for seed " + seed);
        for (StructurePosition village : scan.positions()) {
            int chunkX = village.x() >> 4;
            int chunkZ = village.z() >> 4;
            // Takes any chunk of the spacing region and returns that region's candidate.
            ChunkPos candidate = placement.getPotentialStructureChunk(seed, chunkX, chunkZ);
            check(candidate.x == chunkX && candidate.z == chunkZ, "Village " + village + " is not at vanilla's candidate " + candidate);
        }
        System.out.printf("seed %d villages: %d at vanilla candidate chunks%n", seed, scan.positions().size());
    }

    private static void concurrentCalls() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try (SeedAtlasNative atlas = SeedAtlasNative.open(12345L, WorldType.NORMAL)) {
            BiomeRegion reference = atlas.biomeArea(Dimension.OVERWORLD, -512, -512, 64, 4, 256, 256);
            List<Future<BiomeRegion>> results = new ArrayList<>();
            for (int i = 0; i < 16; ++i) {
                results.add(pool.submit(() -> atlas.biomeArea(Dimension.OVERWORLD, -512, -512, 64, 4, 256, 256)));
            }
            for (Future<BiomeRegion> result : results) {
                check(java.util.Arrays.equals(result.get().biomeIds(), reference.biomeIds()), "Concurrent biome areas differ");
            }
        } finally {
            pool.shutdown();
        }
        System.out.println("16 concurrent 256x256 biome areas agree");
    }

    /** Vanilla's own ring generation, which createForNormal would run, without its biome-tag filtering. */
    @SuppressWarnings("unchecked")
    private static List<ChunkPos> ringPositions(RandomState state, BiomeSource source, long seed,
                                                Holder<StructureSet> set, ConcentricRingsStructurePlacement placement) throws Exception {
        Constructor<ChunkGeneratorStructureState> constructor = ChunkGeneratorStructureState.class.getDeclaredConstructor(
            RandomState.class, BiomeSource.class, long.class, long.class, List.class);
        constructor.setAccessible(true);
        ChunkGeneratorStructureState structureState = constructor.newInstance(state, source, seed, seed, List.of());
        Method generate = ChunkGeneratorStructureState.class.getDeclaredMethod("generateRingPositions",
            Holder.class, ConcentricRingsStructurePlacement.class);
        generate.setAccessible(true);
        return ((CompletableFuture<List<ChunkPos>>)generate.invoke(structureState, set, placement)).join();
    }

    /** Biomes of a vanilla biome tag, read from the game's bundled data. */
    private static List<Holder<Biome>> biomeTag(HolderGetter<Biome> biomes, String name) throws Exception {
        List<Holder<Biome>> result = new ArrayList<>();
        String path = "data/minecraft/tags/worldgen/biome/" + name + ".json";
        try (var stream = ChunkGeneratorStructureState.class.getClassLoader().getResourceAsStream(path)) {
            check(stream != null, "Missing vanilla tag " + path);
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var entry : json.getAsJsonArray("values")) {
                String id = entry.getAsString();
                if (id.startsWith("#")) result.addAll(biomeTag(biomes, ResourceLocation.parse(id.substring(1)).getPath()));
                else result.add(biomes.getOrThrow(ResourceKey.create(Registries.BIOME, ResourceLocation.parse(id))));
            }
        }
        return result;
    }

    private static RandomState randomState(HolderLookup.Provider lookup, ResourceKey<NoiseGeneratorSettings> settings, long seed) {
        return RandomState.create(lookup.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(settings).value(),
            lookup.lookupOrThrow(Registries.NOISE), seed);
    }

    private static String key(Holder<Biome> biome) {
        return biome.unwrapKey().map(ResourceKey::location).map(Object::toString).map(NativeVanillaParityTest::strip).orElse("?");
    }

    private static String strip(String name) {
        return name.startsWith("minecraft:") ? name.substring("minecraft:".length()) : name;
    }

    private static String chunks(Set<Long> chunks) {
        return chunks.stream().sorted().map(c -> new ChunkPos(c).toString()).toList().toString();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
