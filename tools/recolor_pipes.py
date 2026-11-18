"""Recolors Create's copper fluid pipe textures into the grey andesite-alloy palette.

Usage: python tools/recolor_pipes.py <path to create-*.jar>

Each copper shade maps to the andesite casing shade of the same rank (lightest to lightest), so the
pipe keeps Create's shading and just changes material. Writes into src/main/resources/assets.
"""
import io
import sys
import zipfile
from pathlib import Path

from PIL import Image

# Create's copper pipe ramp (lightest first) -> andesite alloy ramp from create:block/andesite_casing.
COPPER_TO_ANDESITE = {
    (255, 193, 145): (186, 196, 189),
    (227, 130, 108): (168, 179, 171),
    (214, 123, 91): (154, 164, 157),
    (200, 116, 86): (143, 150, 145),
    (194, 107, 76): (130, 135, 132),
    (178, 98, 71): (112, 117, 112),
    (167, 90, 64): (104, 108, 104),
    (154, 80, 56): (96, 99, 95),
    (144, 73, 49): (88, 91, 88),
    (121, 59, 43): (73, 76, 74),
    (91, 41, 36): (53, 59, 57),
    (61, 22, 30): (38, 38, 38),
}

SOURCES = {
    "assets/create/textures/block/pipes.png": "linker_pipes.png",
    "assets/create/textures/block/pipes_connected.png": "linker_pipes_connected.png",
}


def recolor(image: Image.Image) -> Image.Image:
    image = image.convert("RGBA")
    pixels = image.load()
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = pixels[x, y]
            mapped = COPPER_TO_ANDESITE.get((r, g, b))
            if mapped is not None:
                pixels[x, y] = (*mapped, a)
    return image


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    out_dir = Path(__file__).resolve().parent.parent / "src/main/resources/assets/storagemerger/textures/block"
    out_dir.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(sys.argv[1]) as jar:
        for source, target in SOURCES.items():
            image = recolor(Image.open(io.BytesIO(jar.read(source))))
            image.save(out_dir / target)
            print(f"wrote {out_dir / target}")


if __name__ == "__main__":
    main()
