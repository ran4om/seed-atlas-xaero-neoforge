package org.seedatlas.xaero.nativeapi;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

/**
 * JNA binding of the primitive C ABI in seedatlas_xaero.h. Minecraft 1.21.1 runs on Java 21,
 * where the FFM API used upstream is still a preview, so this port calls the same library
 * through the JNA copy that Minecraft already ships. Java arrays passed as arguments are
 * copied to native memory for the call and written back afterwards.
 */
final class NativeBindings {
    static final int EXPECTED_ABI = 6;
    /** ABI 4/5 libraries still load for biomes/structures but lack newer terrain data. */
    static final int MIN_ABI = 4;
    static final int CAP_HEIGHTS = 1 << 9;
    static final int CAP_NETHER_FLOOR = 1 << 10;
    static final int CAP_NETHER_CARVING = 1 << 11;
    static final int CAP_MC_VERSIONS = 1 << 12;

    private final NativeLibraryLoader.LoadedNative library;
    private final Function abiVersion;
    private final Function capabilities;
    private final Function engineVersion;
    private final Function create;
    private final Function createVersioned;
    private final Function mcVersionSupported;
    private final Function contextBiomeName;
    private final Function contextBiomeColor;
    private final Function destroy;
    private final Function biomeAt;
    private final Function biomeChunk;
    private final Function biomeChunkSampled;
    private final Function biomeAreaSampled;
    private final Function heightAreaSampled;
    private final Function netherFloorAreaSampled;
    private final Function netherFloorAreaCarvedSampled;
    private final Function biomeName;
    private final Function biomeColor;
    private final Function scanStructures;
    private final Function scanStrongholds;
    private final Function spawn;
    private final Function scanSlimeChunks;

    private NativeBindings(NativeLibraryLoader.LoadedNative library) {
        this.library = library;
        NativeLibrary symbols = library.symbols();
        abiVersion = required(symbols, "sax_abi_version");
        capabilities = required(symbols, "sax_capabilities");
        engineVersion = required(symbols, "sax_engine_version");
        create = required(symbols, "sax_create");
        destroy = required(symbols, "sax_destroy");
        biomeAt = required(symbols, "sax_biome_at");
        biomeChunk = required(symbols, "sax_biome_chunk");
        biomeChunkSampled = required(symbols, "sax_biome_chunk_sampled");
        biomeAreaSampled = required(symbols, "sax_biome_area_sampled");
        biomeName = required(symbols, "sax_biome_name");
        biomeColor = required(symbols, "sax_biome_color");
        scanStructures = required(symbols, "sax_scan_structures");
        scanStrongholds = required(symbols, "sax_scan_strongholds");
        spawn = required(symbols, "sax_spawn");
        scanSlimeChunks = required(symbols, "sax_scan_slime_chunks");
        // Optional: absent from ABI 4 libraries, and only used when the capability bit is set.
        heightAreaSampled = optional(symbols, "sax_height_area_sampled");
        netherFloorAreaSampled = optional(symbols, "sax_nether_floor_area_sampled");
        netherFloorAreaCarvedSampled = optional(symbols, "sax_nether_floor_area_carved_sampled");
        createVersioned = optional(symbols, "sax_create_versioned");
        contextBiomeName = optional(symbols, "sax_context_biome_name");
        contextBiomeColor = optional(symbols, "sax_context_biome_color");
        mcVersionSupported = optional(symbols, "sax_mc_version_supported");
    }

    static NativeBindings load() {
        NativeBindings bindings = new NativeBindings(NativeLibraryLoader.load());
        int abi = bindings.abiVersion();
        if (abi < MIN_ABI || abi > EXPECTED_ABI) {
            throw new NativeException("Native ABI mismatch: Java expects " + MIN_ABI + ".."
                + EXPECTED_ABI
                + " but library provides " + abi);
        }
        return bindings;
    }

    boolean supportsHeights() {
        return heightAreaSampled != null && abiVersion() >= 5
            && (capabilities() & CAP_HEIGHTS) != 0;
    }

    boolean supportsNetherFloor() {
        return netherFloorAreaSampled != null && abiVersion() >= 6
            && (capabilities() & CAP_NETHER_FLOOR) != 0;
    }

    boolean supportsNetherCarving() {
        return supportsNetherFloor() && netherFloorAreaCarvedSampled != null
            && (capabilities() & CAP_NETHER_CARVING) != 0;
    }

    /** False for libraries built before version selection; they only generate their own version. */
    boolean supportsMcVersions() {
        return createVersioned != null && mcVersionSupported != null
            && (capabilities() & CAP_MC_VERSIONS) != 0;
    }

    /** Biome names and colours of the context's own version; false for libraries before version selection. */
    boolean supportsContextBiomes() {
        return contextBiomeName != null && contextBiomeColor != null;
    }

    int contextBiomeName(Pointer context, int id, byte[] out, int capacity) {
        return invokeInt(contextBiomeName, "sax_context_biome_name", context, id, out, capacity);
    }

    int contextBiomeColor(Pointer context, int id) {
        return invokeInt(contextBiomeColor, "sax_context_biome_color", context, id);
    }

    boolean mcVersionSupported(String version) {
        if (mcVersionSupported == null) {
            return false;
        }
        return invokeInt(mcVersionSupported, "sax_mc_version_supported", (Object) cString(version)) != 0;
    }

    Pointer createVersioned(long seed, int flags, String version) {
        if (createVersioned == null) {
            throw new NativeException("Native library cannot select a Minecraft version");
        }
        return invokePointer(createVersioned, "sax_create_versioned", seed, flags, cString(version));
    }

    int netherFloorAreaCarvedSampled(Pointer context, int originBlockX, int originBlockZ,
                                     int y, int sampleStep, int sampleWidth, int sampleHeight,
                                     long[] carved, int carvedCapacity,
                                     float[] heights, int capacity) {
        if (netherFloorAreaCarvedSampled == null) {
            throw new NativeException("Native library does not support Nether carving masks");
        }
        return invokeInt(netherFloorAreaCarvedSampled, "sax_nether_floor_area_carved_sampled",
            context, originBlockX, originBlockZ, y, sampleStep, sampleWidth, sampleHeight,
            carved, carvedCapacity, heights, capacity);
    }

    int netherFloorAreaSampled(Pointer context, int originBlockX, int originBlockZ,
                               int y, int sampleStep, int sampleWidth, int sampleHeight,
                               float[] heights, int capacity) {
        if (netherFloorAreaSampled == null) {
            throw new NativeException("Native library does not provide the Nether floor");
        }
        return invokeInt(netherFloorAreaSampled, "sax_nether_floor_area_sampled", context,
            originBlockX, originBlockZ, y, sampleStep, sampleWidth, sampleHeight, heights, capacity);
    }

    int heightAreaSampled(Pointer context, int dimension, int originBlockX,
                          int originBlockZ, int sampleStep, int sampleWidth,
                          int sampleHeight, float[] heights, int capacity) {
        if (heightAreaSampled == null) {
            throw new NativeException("Native library does not provide terrain heights");
        }
        return invokeInt(heightAreaSampled, "sax_height_area_sampled", context, dimension,
            originBlockX, originBlockZ, sampleStep, sampleWidth, sampleHeight, heights, capacity);
    }

    String platform() {
        return library.platform();
    }

    int abiVersion() {
        return invokeInt(abiVersion, "sax_abi_version");
    }

    int capabilities() {
        return invokeInt(capabilities, "sax_capabilities");
    }

    int engineVersion(byte[] out, int capacity) {
        return invokeInt(engineVersion, "sax_engine_version", out, capacity);
    }

    Pointer create(long seed, int flags) {
        return invokePointer(create, "sax_create", seed, flags);
    }

    void destroy(Pointer context) {
        try {
            destroy.invokeVoid(new Object[] {context});
        } catch (RuntimeException | Error error) {
            throw callFailure("sax_destroy", error);
        }
    }

    int biomeAt(Pointer context, int dimension, int x, int y, int z, int[] out) {
        return invokeInt(biomeAt, "sax_biome_at", context, dimension, x, y, z, out);
    }

    int biomeChunk(Pointer context, int dimension, int chunkX, int chunkZ,
                   int y, int[] ids, int[] colors) {
        return invokeInt(biomeChunk, "sax_biome_chunk", context, dimension, chunkX, chunkZ, y, ids, colors);
    }

    int biomeChunkSampled(Pointer context, int dimension, int chunkX, int chunkZ,
                          int y, int sampleStep, int[] ids, int[] colors) {
        return invokeInt(biomeChunkSampled, "sax_biome_chunk_sampled",
            context, dimension, chunkX, chunkZ, y, sampleStep, ids, colors);
    }

    int biomeAreaSampled(Pointer context, int dimension, int originBlockX,
                         int originBlockZ, int y, int sampleStep, int sampleWidth,
                         int sampleHeight, int[] ids, int[] colors, int capacity) {
        return invokeInt(biomeAreaSampled, "sax_biome_area_sampled", context, dimension,
            originBlockX, originBlockZ, y, sampleStep, sampleWidth, sampleHeight, ids, colors, capacity);
    }

    int biomeName(int id, byte[] out, int capacity) {
        return invokeInt(biomeName, "sax_biome_name", id, out, capacity);
    }

    int biomeColor(int id) {
        return invokeInt(biomeColor, "sax_biome_color", id);
    }

    int scanStructures(Pointer context, int type, BlockBox box,
                       boolean biomeCheck, int[] out, int capacity) {
        return invokeInt(scanStructures, "sax_scan_structures", context, type,
            box.minX(), box.minZ(), box.maxX(), box.maxZ(), biomeCheck ? 1 : 0, out, capacity);
    }

    int scanStrongholds(Pointer context, BlockBox box, boolean biomeCheck,
                        int[] out, int capacity) {
        return invokeInt(scanStrongholds, "sax_scan_strongholds", context,
            box.minX(), box.minZ(), box.maxX(), box.maxZ(), biomeCheck ? 1 : 0, out, capacity);
    }

    int spawn(Pointer context, boolean detailed, int[] out) {
        return invokeInt(spawn, "sax_spawn", context, detailed ? 1 : 0, out);
    }

    int scanSlimeChunks(Pointer context, BlockBox box, int[] out, int capacity) {
        return invokeInt(scanSlimeChunks, "sax_scan_slime_chunks", context,
            box.minX(), box.minZ(), box.maxX(), box.maxZ(), out, capacity);
    }

    /** NUL-terminated ASCII, so JNA's platform string encoding cannot change version names. */
    private static byte[] cString(String value) {
        byte[] ascii = value.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] result = new byte[ascii.length + 1];
        System.arraycopy(ascii, 0, result, 0, ascii.length);
        return result;
    }

    private static int invokeInt(Function function, String name, Object... arguments) {
        try {
            return function.invokeInt(arguments);
        } catch (RuntimeException | Error error) {
            throw callFailure(name, error);
        }
    }

    private static Pointer invokePointer(Function function, String name, Object... arguments) {
        try {
            return function.invokePointer(arguments);
        } catch (RuntimeException | Error error) {
            throw callFailure(name, error);
        }
    }

    private static Function required(NativeLibrary symbols, String name) {
        Function function = optional(symbols, name);
        if (function == null) {
            throw new NativeException("Native symbol is missing: " + name);
        }
        return function;
    }

    private static Function optional(NativeLibrary symbols, String name) {
        try {
            return symbols.getFunction(name);
        } catch (UnsatisfiedLinkError missing) {
            return null;
        }
    }

    private static NativeException callFailure(String function, Throwable error) {
        if (error instanceof NativeException nativeException) {
            return nativeException;
        }
        return new NativeException("Native call failed: " + function, error);
    }
}
