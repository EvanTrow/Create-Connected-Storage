"""Generates the Ponder schematic for the Item Pump / Item Pipe scene.

Usage: python tools/gen_ponder.py

Writes src/main/resources/assets/connectedstorage/ponder/item_pump.nbt, a vanilla structure file that
Ponder loads as the scene's world. The storage line runs front to back (along z) down the middle of a
9x9 base plate, and the drive comes in off the back edge:

    z:        2      3      4      5      6      7      8      9
    x=4 y=1   vault  pipe   pipe   vault  pump   vault
    x=5 y=1                               cog    shaft  shaft  small cog
    x=4 y=0                                                    large cog

The small cogwheel beside the pump drives it; its shaft runs back past the plate edge, where a small cog
on the shaft meshes diagonally with a large cog sitting level with the base plate, just off its back edge.

The vault at z = 5 is swapped for a pipe during the scene, so the line becomes
vault, pump, pipe, pipe, pipe, vault. The pump reaches 1 pipe block per 16 RPM.
"""
import gzip
import struct
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/connectedstorage/ponder/item_pump.nbt"
SIZE = (9, 3, 10)
PLATE = 9
DATA_VERSION = 3955  # Minecraft 1.21.1

DIRECTIONS = ["north", "east", "south", "west", "up", "down"]


def pipe(**sides):
    props = {d: sides.get(d, "none") for d in DIRECTIONS}
    props["waterlogged"] = "false"
    return ("connectedstorage:item_pipe", props)


VAULT = ("create:item_vault", {"axis": "z", "large": "false"})
PUMP = ("connectedstorage:item_pump", {"facing": "south", "waterlogged": "false"})
COG = ("create:cogwheel", {"axis": "z", "waterlogged": "false"})
LARGE_COG = ("create:large_cogwheel", {"axis": "z", "waterlogged": "false"})
SHAFT = ("create:shaft", {"axis": "z", "waterlogged": "false"})

BLOCKS = {
    (4, 1, 7): VAULT,
    (4, 1, 6): PUMP,
    (4, 1, 5): VAULT,
    (4, 1, 4): pipe(north="pipe", south="pipe"),
    (4, 1, 3): pipe(north="storage", south="pipe"),
    (4, 1, 2): VAULT,
    (5, 1, 6): COG,
    (5, 1, 7): SHAFT,
    (5, 1, 8): SHAFT,
    (5, 1, 9): COG,
    (4, 0, 9): LARGE_COG,
}


# --- minimal NBT writer -------------------------------------------------------------------------------------

def _str(value):
    data = value.encode("utf-8")
    return struct.pack(">H", len(data)) + data


def _named(tag, name, payload):
    return bytes([tag]) + _str(name) + payload


def _compound(entries):
    """entries: list of (tag id, name, payload bytes)."""
    return b"".join(_named(t, n, p) for t, n, p in entries) + b"\x00"


def _list(element_tag, payloads):
    return bytes([element_tag]) + struct.pack(">i", len(payloads)) + b"".join(payloads)


def _int(value):
    return struct.pack(">i", value)


def _state(name, props):
    entries = [(8, "Name", _str(name))]
    if props:
        entries.append((10, "Properties", _compound([(8, k, _str(v)) for k, v in sorted(props.items())])))
    return _compound(entries)


def main() -> None:
    palette = []
    blocks = []

    def state_index(state):
        if state not in palette:
            palette.append(state)
        return palette.index(state)

    def freeze(state):
        name, props = state
        return name, tuple(sorted(props.items()))

    for x in range(PLATE):
        for z in range(PLATE):
            plate = ("minecraft:snow_block", {}) if (x + z) % 2 == 0 else ("minecraft:white_concrete", {})
            blocks.append(((x, 0, z), state_index(freeze(plate))))
    for pos, state in BLOCKS.items():
        blocks.append((pos, state_index(freeze(state))))

    root = _compound([
        (3, "DataVersion", _int(DATA_VERSION)),
        (9, "size", _list(3, [_int(v) for v in SIZE])),
        (9, "palette", _list(10, [_state(name, dict(props)) for name, props in palette])),
        (9, "blocks", _list(10, [
            _compound([(9, "pos", _list(3, [_int(v) for v in pos])), (3, "state", _int(index))])
            for pos, index in blocks
        ])),
        (9, "entities", _list(10, [])),
    ])
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(OUT, "wb") as f:
        f.write(bytes([10]) + _str("") + root)
    print(f"wrote {OUT} ({len(blocks)} blocks, {len(palette)} states)")


if __name__ == "__main__":
    main()
