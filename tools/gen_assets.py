"""Generates the Storage Linker blockstate and block models from Create's fluid pipe models.

Usage: python tools/gen_assets.py <path to create-*.jar>

The linker reuses Create's fluid pipe geometry: every model here is a child of one of Create's
fluid pipe models that only swaps the textures for the grey linker textures. The blockstate is
Create's fluid_pipe multipart with each connection's true/false mapped onto the linker's
none / pipe / storage sides, plus:
  * an arm (connection/<dir>) on every connected side,
  * a rim (rim/<dir>) on every side touching a storage block,
  * a cap face for the core patterns Create leaves out (0, 3 or 4 perpendicular connections),
    which Create never needs because its pipes always have an open, drained end.
"""
import json
import sys
import zipfile
from itertools import product
from pathlib import Path

MOD_ID = "storagemerger"
BLOCK = "storage_linker"
TEX_CONNECTED = f"{MOD_ID}:block/linker_pipes_connected"
TEX_PIPES = f"{MOD_ID}:block/linker_pipes"
DIRECTIONS = ["north", "east", "south", "west", "up", "down"]
CONNECTED = "pipe|storage"
NONE = "none"

# Directions perpendicular to each axis (the sides whose arms change how that axis' core faces look).
PERPENDICULAR = {
    "x": ["up", "down", "north", "south"],
    "y": ["north", "south", "east", "west"],
    "z": ["up", "down", "east", "west"],
}
# The two core faces on each axis.
AXIS_FACES = {"x": ["east", "west"], "y": ["up", "down"], "z": ["north", "south"]}

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets" / MOD_ID


def write_json(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def child_model(name: str, parent: str, textures: dict) -> str:
    write_json(ASSETS / "models/block" / BLOCK / f"{name}.json", {"parent": parent, "textures": textures})
    return f"{MOD_ID}:block/{BLOCK}/{name}"


def cap_model(axis: str) -> str:
    """A core face pair for one axis, textured as a plain capped pipe end."""
    faces = {face: {"uv": [10, 2, 14, 6], "texture": "#0"} for face in AXIS_FACES[axis]}
    write_json(ASSETS / "models/block" / BLOCK / f"cap_{axis}.json", {
        "textures": {"0": TEX_CONNECTED, "particle": TEX_PIPES},
        "elements": [{"from": [4, 4, 4], "to": [12, 12, 12], "faces": faces}],
    })
    return f"{MOD_ID}:block/{BLOCK}/cap_{axis}"


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    with zipfile.ZipFile(sys.argv[1]) as jar:
        create_state = json.loads(jar.read("assets/create/blockstates/fluid_pipe.json"))

    multipart = []
    covered = {axis: set() for axis in PERPENDICULAR}

    # Core faces: Create's patterns, retextured.
    for part in create_state["multipart"]:
        create_model = part["apply"]["model"]                     # create:block/fluid_pipe/lu_x
        name = create_model.rsplit("/", 1)[1]
        axis = name[-1]
        model = child_model(name, create_model, {"0": TEX_CONNECTED, "particle": TEX_PIPES})
        when = {d: (CONNECTED if v == "true" else NONE) for d, v in part["when"].items()}
        covered[axis].add(tuple(part["when"][d] == "true" for d in PERPENDICULAR[axis]))
        multipart.append({"when": when, "apply": {"model": model}})

    # Core faces for the patterns Create doesn't cover.
    for axis, dirs in PERPENDICULAR.items():
        model = cap_model(axis)
        for pattern in product([False, True], repeat=4):
            if pattern not in covered[axis]:
                when = {d: (CONNECTED if on else NONE) for d, on in zip(dirs, pattern)}
                multipart.append({"when": when, "apply": {"model": model}})

    # Arms and rims.
    for d in DIRECTIONS:
        arm = child_model(f"connection_{d}", f"create:block/fluid_pipe/connection/{d}",
                          {"0": TEX_PIPES, "particle": TEX_PIPES})
        rim = child_model(f"rim_{d}", f"create:block/fluid_pipe/rim/{d}",
                          {"0": TEX_PIPES, "particle": TEX_PIPES})
        multipart.append({"when": {d: CONNECTED}, "apply": {"model": arm}})
        multipart.append({"when": {d: "storage"}, "apply": {"model": rim}})

    write_json(ASSETS / "blockstates" / f"{BLOCK}.json", {"multipart": multipart})
    item = child_model("item", "create:block/fluid_pipe/item", {"1": TEX_PIPES, "particle": TEX_PIPES})
    write_json(ASSETS / "models/item" / f"{BLOCK}.json", {"parent": item})
    print(f"wrote {len(multipart)} multipart entries")


if __name__ == "__main__":
    main()
