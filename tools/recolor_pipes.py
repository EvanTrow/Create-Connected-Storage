"""Recolors Create's copper fluid pipe and mechanical pump textures into the Item Vault's slate blue.

Usage: python tools/recolor_pipes.py <path to create-*.jar>

Each copper shade keeps its lightness (so Create's shading survives) but takes the hue and
saturation of the vault's blue body. The pump texture also loses the flow-direction arrow on its
flange sides. Writes into src/main/resources/assets.
"""
import colorsys
import io
import sys
import zipfile
from pathlib import Path

from PIL import Image

# Create's copper ramp, lightest first.
COPPER = [
    (255, 193, 145), (227, 130, 108), (214, 123, 91), (200, 116, 86), (194, 107, 76), (178, 98, 71),
    (167, 90, 64), (154, 80, 56), (144, 73, 49), (121, 59, 43), (91, 41, 36), (61, 22, 30),
]
# Hue / saturation of the vault body (create:block/vault/vault_side_small, around (70, 78, 97)).
VAULT_HUE = 218 / 360
VAULT_SATURATION = 0.22
# Vaults read darker than copper pipes; scale lightness to match.
LIGHTNESS_SCALE = 0.85


def vault_blue(rgb):
    _, lightness, _ = colorsys.rgb_to_hls(*(c / 255 for c in rgb))
    r, g, b = colorsys.hls_to_rgb(VAULT_HUE, lightness * LIGHTNESS_SCALE, VAULT_SATURATION)
    return round(r * 255), round(g * 255), round(b * 255)


COPPER_TO_BLUE = {c: vault_blue(c) for c in COPPER}


def recolor(image: Image.Image) -> Image.Image:
    image = image.convert("RGBA")
    pixels = image.load()
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = pixels[x, y]
            mapped = COPPER_TO_BLUE.get((r, g, b))
            if mapped is not None:
                pixels[x, y] = (*mapped, a)
    return image


def remove_pump_arrow(image: Image.Image) -> Image.Image:
    """The flange side strip (pixels x 13-15, y 0-11) carries a flow arrow in rows 3-8; repeat row 2 over it."""
    pixels = image.load()
    for y in range(3, 9):
        for x in range(13, 16):
            pixels[x, y] = pixels[x, 2]
    return image


SOURCES = {
    "assets/create/textures/block/pipes.png": ("item_pipe.png", None),
    "assets/create/textures/block/pipes_connected.png": ("item_pipe_connected.png", None),
    "assets/create/textures/block/pump.png": ("item_pump.png", remove_pump_arrow),
}


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    out_dir = Path(__file__).resolve().parent.parent / "src/main/resources/assets/connectedstorage/textures/block"
    out_dir.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(sys.argv[1]) as jar:
        for source, (target, extra) in SOURCES.items():
            image = Image.open(io.BytesIO(jar.read(source))).convert("RGBA")
            if extra is not None:
                image = extra(image)
            image = recolor(image)
            image.save(out_dir / target)
            print(f"wrote {out_dir / target}")


if __name__ == "__main__":
    main()
