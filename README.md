# Create: Connected Storage

Adds the Item Pump and Item Pipe, which merge separate Create Item Vaults into one big shared inventory.

Vaults only merge on their own when they line up into a single rectangular block. Put a running Item Pump between two vaults and they act as one storage: a funnel, chute or packager on either vault reaches the items in both. Run Item Pipes out from the pump to join vaults that are further away; the faster the pump turns, the further it reaches.

## Features:

- **Item Pump**: merges the storages connected to it. It reaches **1 pipe block per 16 RPM**, up to **16 pipe blocks at 256 RPM**, counted along the pipe from either end of the pump; storages touching the pump directly need no pipe. Below 16 RPM it does nothing. Powered like Create's Mechanical Pump, by meshing with an adjacent cogwheel. Stress impact 4 su per RPM (64 su at 16 RPM, 1024 su at 256 RPM).
- **Item Pipe**: carries the link between pumps and storages. Pipes beyond every pump's reach do nothing; put another running pump in the line to go further.
- Pumps connect on both ends of their axis: `Vault <> Pump <> Vault` and `Vault <> Pipe <> Pump <> Pipe <> Vault` both work
- A pump that is stopped or below 16 RPM cuts the link: storages behind it stay on their own
- Works with Create: Connected's Item Silos too, including vaults and silos mixed in one network
- Items fill the storage they enter first, then spill over into the rest of the network
- Packagers and Stock Keepers count a linked network once, even with packagers on several members
- Pipes connect like Create fluid pipes, with a rim where they meet a storage; both blocks use the vault's slate blue
- Breaking a pump or pipe splits the storages again with their items intact
- Sneak + Wrench picks them up

Recipes: Fluid Pipe + Andesite Alloy makes 2 Item Pipes; Mechanical Pump + Item Pipe makes an Item Pump.

Requires: Create. Optional: Create: Connected (Item Silo support).

## Notes for modpack makers

- The block tag `connectedstorage:linkable_storage` controls which blocks pipes and pumps connect to. Merging works for Create's Item Vault and Create: Connected's Item Silo.
- A single network is capped at 64 storages and 1024 pipes and pumps.

## Building

```
./gradlew build
./gradlew runGameTestServer
```

`./deploy.ps1` builds the jar and copies it into a Minecraft mods folder (`-ModsDir` to choose one).

The textures and block models are generated from Create's fluid pipe and mechanical pump assets (Create is MIT licensed):

```
python tools/recolor_pipes.py <path to create jar>
python tools/gen_assets.py <path to create jar>
```
