"""
Generates the mod's 16x16 block textures in Create's style.

Style rules taken from Create's own textures (casings, dark metal block,
industrial iron): smooth, low-contrast surfaces with no stripes, light from the
top left, a frame (light edge top and left, dark edge bottom and right, a darker
outline), bolt heads in the corners of panels. Nothing random that would differ
from tile to tile.

Each material comes twice: "<name>" with its frame (the look of a single block,
and where the tools take their frame from) and "<name>_fill" without (the fill
of large surfaces, which the tools draw with a frame only around the outer edge,
like Create's connected textures). Colours for steel come from Create's
industrial iron block; the yellow is construction-machine paint.

Run:  python generate_textures.py <output folder>
"""
import random
import sys
from pathlib import Path

from PIL import Image

# --- palettes -----------------------------------------------------------------
# Yellow paint, light to dark.
Y = ["#f5cf63", "#e6b445", "#d39e33", "#bd8828", "#9c6d1f", "#7a5418", "#553a10"]
# Steel, from Create's industrial iron block, light to dark.
S = ["#c9c7c4", "#989797", "#757171", "#676161", "#595858", "#4f4f4f", "#444444", "#3f3e42", "#353438", "#2b2b2e"]
BLACK = ["#3a3a3c", "#2e2e30", "#252527", "#1c1c1e"]


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def new():
    return Image.new("RGBA", (16, 16))


def grain(img, palette, base, seed, x0=0, y0=0, x1=16, y1=16):
    """Fills an area with faint vertical grain: each column is a little lighter or darker."""
    rnd = random.Random(seed)
    columns = [rnd.choice([-1, 0, 0, 0, 0, 0, 1]) for _ in range(16)]
    for x in range(x0, x1):
        for y in range(y0, y1):
            shade = base + columns[x] + (1 if rnd.random() < 0.03 else 0) - (1 if rnd.random() < 0.03 else 0)
            img.putpixel((x, y), rgb(palette[max(0, min(len(palette) - 1, shade))]))


def light_frame(img, palette, light, dark, x0=0, y0=0, x1=16, y1=16):
    """One-pixel bevel: light on top and left, dark on bottom and right."""
    for x in range(x0, x1):
        img.putpixel((x, y0), rgb(palette[light]))
        img.putpixel((x, y1 - 1), rgb(palette[dark]))
    for y in range(y0, y1):
        img.putpixel((x0, y), rgb(palette[light]))
        img.putpixel((x1 - 1, y), rgb(palette[dark]))


def bolt(img, x, y):
    """A bolt head: one light pixel with a dark one below right."""
    img.putpixel((x, y), rgb(S[0]))
    img.putpixel((x + 1, y + 1), rgb(S[8]))


def wear(img, seed, count, edge_only=True):
    """Worn paint: a few small chips near the edges where the steel underneath shows."""
    rnd = random.Random(seed)
    for _ in range(count):
        if edge_only:
            side = rnd.randrange(4)
            t = rnd.randrange(2, 13)
            x, y = [(t, 1), (t, 13), (1, t), (13, t)][side]
        else:
            x, y = rnd.randrange(2, 13), rnd.randrange(2, 13)
        # A chip: two dark steel pixels side by side, with a light pixel above as its edge.
        img.putpixel((x, y), rgb(S[5]))
        img.putpixel((x + 1, y), rgb(S[6]))
        img.putpixel((x, y - 1), rgb(Y[0]))


# --- textures -----------------------------------------------------------------

def mix(a, b, t):
    """A colour between two palette colours: t = 0 gives a, t = 1 gives b."""
    ca, cb = rgb(a), rgb(b)
    return tuple(round(ca[i] + (cb[i] - ca[i]) * t) for i in range(3)) + (255,)


def smooth(img, palette, base, seed, speckle=0.06):
    """A smooth surface: one base shade with a few pixels only a hair lighter or darker. No stripes."""
    rnd = random.Random(seed)
    for x in range(16):
        for y in range(16):
            r = rnd.random()
            if r < speckle:
                colour = mix(palette[base], palette[base + 1], 0.35)
            elif r > 1 - speckle:
                colour = mix(palette[base], palette[base - 1], 0.35)
            else:
                colour = rgb(palette[base])
            img.putpixel((x, y), colour)


def plate_frame(img, palette, light, dark, outline):
    """Each block reads as one steel plate: light edge top and left, dark edge bottom and right, a darker outline."""
    light_frame(img, palette, light, dark, 1, 1, 15, 15)
    for i in range(16):
        for p in [(i, 0), (0, i), (i, 15), (15, i)]:
            img.putpixel(p, rgb(palette[outline]))


def painted_fill():
    """Yellow-painted steel without a frame: the fill of large connected surfaces. Tiles seamlessly."""
    img = new()
    smooth(img, Y, 2, seed=1)
    return img


def painted_plate():
    """Yellow-painted steel plate with its frame: the block texture, and the source of the frame on tools."""
    img = painted_fill()
    plate_frame(img, Y, 1, 3, 4)
    return img


def painted_panel():
    """A yellow panel with a frame and a bolt in each corner, like Create's casings."""
    img = new()
    smooth(img, Y, 2, seed=3)
    plate_frame(img, Y, 1, 3, 4)
    light_frame(img, Y, 3, 1, 3, 3, 13, 13)   # inner edge: the panel sits lower
    for x, y in [(2, 2), (12, 2), (2, 12), (12, 12)]:
        bolt(img, x, y)
    return img


def painted_ribbed():
    """Yellow corrugated sheet with upright ribs: for container walls and as a deco block."""
    img = new()
    for x in range(16):
        phase = x % 4
        shade = [1, 2, 3, 4][phase]            # each rib: light, mid, darker, shadow
        for y in range(16):
            img.putpixel((x, y), rgb(Y[shade]))
    rnd = random.Random(5)
    for _ in range(6):
        img.putpixel((rnd.randrange(16), rnd.randrange(16)), rgb(Y[2]))
    return img


def worn_steel_fill():
    """Worn steel without a frame, for cutting edges and teeth: smooth light steel, two short scratches."""
    img = new()
    smooth(img, S, 2, seed=7)
    for x, y in [(4, 5), (9, 10)]:            # fixed, so every tile is the same
        for i in range(3):
            img.putpixel((x + i, y), mix(S[2], S[1], 0.5))
    return img


def worn_steel():
    """Worn steel with its frame."""
    img = worn_steel_fill()
    plate_frame(img, S, 1, 4, 6)
    return img


def dark_steel_fill():
    """Dark steel without a frame, for hinges, brackets and pins, close to Create's dark metal block."""
    img = new()
    smooth(img, S, 7, seed=9)
    return img


def dark_steel():
    """Dark steel with its frame."""
    img = dark_steel_fill()
    plate_frame(img, S, 5, 8, 9)
    return img


def hazard_stripes():
    """Yellow and black warning stripes, 45 degrees, 4 pixels wide; tiles seamlessly. No randomness: every tile is identical."""
    img = new()
    for x in range(16):
        for y in range(16):
            band = ((x + y) // 4) % 2
            if band == 0:
                shade = 1 if (x + y) % 4 == 0 else 2
                img.putpixel((x, y), rgb(Y[shade]))
            else:
                shade = 0 if (x + y) % 4 == 0 else 2
                img.putpixel((x, y), rgb(BLACK[shade]))
    return img


def tread_plate():
    """Steel tread plate: short raised bars in alternating directions. Floors, steps, the container's floor."""
    img = new()
    grain(img, S, 4, seed=11)
    for j in range(4):
        for i in range(4):
            x, y = i * 4 + 1, j * 4 + 1
            if (i + j) % 2 == 0:
                pixels = [(x, y), (x + 1, y + 1)]
            else:
                pixels = [(x + 1, y), (x, y + 1)]
            for k, (px, py) in enumerate(pixels):
                img.putpixel((px, py), rgb(S[1] if k == 0 else S[2]))
                img.putpixel((px, py + 1), rgb(S[7]))   # shadow under each raised pixel
    return img


TEXTURES = {
    "painted_steel_yellow": painted_plate,
    "painted_steel_yellow_fill": painted_fill,
    "painted_steel_yellow_panel": painted_panel,
    "painted_steel_yellow_ribbed": painted_ribbed,
    "worn_steel": worn_steel,
    "worn_steel_fill": worn_steel_fill,
    "dark_steel": dark_steel,
    "dark_steel_fill": dark_steel_fill,
    "hazard_stripes": hazard_stripes,
    "tread_plate": tread_plate,
}

if __name__ == "__main__":
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "out")
    out.mkdir(parents=True, exist_ok=True)
    for name, make in TEXTURES.items():
        make().save(out / f"{name}.png")
        print("wrote", out / f"{name}.png")
