# Changelog

## 2.0.2

- Tested with Sinytra Connector and Fabric API installed. Closing a Seed Atlas menu that never
  finished opening no longer crashes the game.
- Structure markers are now on by default. Existing settings files from earlier versions turn
  them on once; after that, switching them off is remembered.
- On a server, Seed Atlas now tells you in chat when it has no seed yet, and how to set one with
  `/seedatlas seed <seed>`. Servers never send their seed, so until then the seed map stays empty.

## 2.0.1 (1.21.1 NeoForge port)

First release of the Minecraft 1.21.1 NeoForge port of
[seed-atlas-xaero](https://github.com/DUzzL/seed-atlas-xaero).

- Runs on NeoForge 21.1 with Xaero's World Map 1.47.0 or newer. Client-side only.
- Works alongside Xaero's Minimap, XaeroPlus, Sodium and ImmediatelyFast.
- Predicts 1.21.1 world generation. Biomes and strongholds are checked against vanilla's own
  generator in the test suite.
- Fixes two places where the bundled engine differed from vanilla 1.21.1: End biome borders and
  cherry groves counting as stronghold biomes.
- Only vanilla structures are shown. Mod and datapack structures are not predicted.
- Terrain shading is a little coarser than upstream on Minecraft 26.x, and Nether carvers are not
  masked, because 1.21.1 has no batched density API.
