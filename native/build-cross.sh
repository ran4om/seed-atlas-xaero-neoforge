#!/bin/sh
# Builds the bundled Linux (glibc 2.17 and newer), Windows and macOS libraries with zig cc (0.13.0) from a configured CMake build
# directory, which holds the fetched engine and the 1.21.1-patched finders.c:
#   cmake -S native -B build/native && sh native/build-cross.sh build/native src/main/resources/natives
set -e
BUILD=$1
OUT=$2
ZIG=${ZIG:-zig}
SRC=$(dirname "$0")
ENGINE=$BUILD/_deps/seedatlas_source-src/seedatlas-engine
for spec in x86_64-linux-gnu.2.17:linux-x86_64:so x86_64-windows-gnu:windows-x86_64:dll x86_64-macos:macos-x86_64:dylib aarch64-macos:macos-aarch64:dylib; do
    target=${spec%%:*}
    rest=${spec#*:}
    platform=${rest%%:*}
    ext=${rest#*:}
    case $ext in
        so) extra=-Wl,-soname,seedatlas_xaero.so ;;
        dll) extra=-static-libgcc ;;
        dylib) extra=-Wl,-install_name,@rpath/seedatlas_xaero.dylib ;;
    esac
    mkdir -p "$OUT/$platform"
    # No debug info, and source paths relative, so no build machine paths end up in the libraries.
    "$ZIG" cc -target "$target" -std=c11 -O3 -fno-strict-aliasing -fvisibility=hidden -shared -g0 -s \
        -ffile-prefix-map="$ENGINE"=engine -ffile-prefix-map="$BUILD"=build -ffile-prefix-map="$SRC"=native \
        -DSEEDATLAS_XAERO_BUILD=1 -I"$ENGINE" -I"$SRC" \
        "$SRC/seedatlas_xaero.c" "$BUILD/engine/finders.c" "$ENGINE/generator.c" "$ENGINE/layers.c" \
        "$ENGINE/biomenoise.c" "$ENGINE/biomes.c" "$ENGINE/noise.c" "$ENGINE/util.c" \
        $extra -lm -o "$OUT/$platform/seedatlas_xaero.$ext"
    rm -f "$OUT/$platform/seedatlas_xaero.lib" "$OUT/$platform/seedatlas_xaero.pdb"
done
