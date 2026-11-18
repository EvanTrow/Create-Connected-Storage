# Create: Storage Merger

Adds the Storage Linker, a grey andesite pipe that merges separate Create Item Vaults into one big shared inventory.

Vaults only merge on their own when they line up into a single rectangular block. Put a Storage Linker between two vaults and they act as one storage: a funnel, chute or packager on either vault reaches the items in both. Chain linkers together to join vaults that are further apart, and link as many as you like.

## Features:

- Merges any number of Item Vaults into one shared inventory
- Works with Create: Connected's Item Silos too, including vaults and silos mixed in one network
- Linkers chain through each other, so a pipe run can join storages that aren't next to each other
- Items fill the storage they enter first, then spill over into the rest of the network
- Packagers and Stock Keepers count a linked network once, even with packagers on several members
- Connects like a Create fluid pipe, with a rim where it meets a storage
- Holds no data of its own: break it and the storages split again with their items intact, and it moves fine on contraptions
- Sneak + Wrench picks it up
- Crafted from a Fluid Pipe + Andesite Alloy (makes 2)

Requires: Create. Optional: Create: Connected (Item Silo support).

## Notes for modpack makers

- The block tag `storagemerger:linkable_storage` controls which blocks the linker visually connects to. Merging works for Create's Item Vault and Create: Connected's Item Silo.
- A single network is capped at 64 storages and 1024 linkers.

## Building

```
./gradlew build
```

`./deploy.ps1` builds the jar and copies it into a Minecraft mods folder (`-ModsDir` to choose one).

The linker textures and block models are generated from Create's fluid pipe assets (Create is MIT licensed):

```
python tools/recolor_pipes.py <path to create jar>
python tools/gen_assets.py <path to create jar>
```
