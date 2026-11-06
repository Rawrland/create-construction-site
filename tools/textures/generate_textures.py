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
industrial iron block. Paint comes in the 16 dye colours, each spaced in shades
like the original construction-machine yellow. Unpainted tools are bare
brass in Create's brass colours (owner's choice, 20 October 2026), their working
edges polished iron.

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
# Red and white for red-white warning stripes.
RED = ["#d44a3c", "#b8352b", "#9c2a22", "#7a1f19"]
WHITE = ["#f2f1ec", "#e2e0d9", "#cfccc3", "#b5b1a6"]

# Paint colours, one per Minecraft dye: the middle shade (index 2 of the palette).
# Chosen as machine paint (a bit deeper than Minecraft's wool), Create-like and not too bright.
DYES = {
    "white": "#d9d8d2", "light_gray": "#a4a5a2", "gray": "#787d82", "black": "#34353a",
    "brown": "#7d5536", "red": "#b3362c", "orange": "#d97a22", "yellow": None,
    "lime": "#80b532", "green": "#4f7a2c", "cyan": "#2b8c92", "light_blue": "#4f9fd2",
    "blue": "#33509f", "purple": "#76429f", "magenta": "#ab459f", "pink": "#dc8aa5",
}


def _hls(h):
    import colorsys
    r, g, b, _ = rgb(h)
    return colorsys.rgb_to_hls(r / 255, g / 255, b / 255)


def _hex(h, l, s):
    import colorsys
    r, g, b = colorsys.hls_to_rgb(h, max(0.0, min(1.0, l)), max(0.0, min(1.0, s)))
    return "#%02x%02x%02x" % (round(r * 255), round(g * 255), round(b * 255))


def paint_palette(name):
    """Seven shades of one paint colour, light to dark, spaced like the yellow paint."""
    if DYES[name] is None:
        return Y
    h, l, s = _hls(DYES[name])
    base_l = _hls(Y[2])[1]
    out = []
    for shade in Y:
        dl = _hls(shade)[1] - base_l
        # Very light or very dark paints get less spread, so white stays white and black stays black.
        spread = 0.75 if l > 0.75 or l < 0.25 else 1.0
        out.append(_hex(h, l + dl * spread, s))
    return out


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


def smooth(img, palette, base, seed, speckle=0.06, strength=0.35):
    """A smooth surface: one base shade with a few pixels only a hair lighter or darker. No stripes."""
    rnd = random.Random(seed)
    for x in range(16):
        for y in range(16):
            r = rnd.random()
            if r < speckle:
                colour = mix(palette[base], palette[base + 1], strength)
            elif r > 1 - speckle:
                colour = mix(palette[base], palette[base - 1], strength)
            else:
                colour = rgb(palette[base])
            img.putpixel((x, y), colour)


def plate_frame(img, palette, light, dark, outline):
    """Each block reads as one steel plate: light edge top and left, dark edge bottom and right, a darker outline."""
    light_frame(img, palette, light, dark, 1, 1, 15, 15)
    for i in range(16):
        for p in [(i, 0), (0, i), (i, 15), (15, i)]:
            img.putpixel(p, rgb(palette[outline]))


def painted_fill(p=Y):
    """Painted steel without a frame: the fill of large connected surfaces. Tiles seamlessly."""
    img = new()
    smooth(img, p, 2, seed=1, strength=0.1)
    return img


def painted_plate(p=Y):
    """Painted steel plate with its frame: the block texture, and the source of the frame on tools.
    No chips: single dark pixels on the frame read as stray pixels (owner, 20 October 2026)."""
    img = painted_fill(p)
    plate_frame(img, p, 1, 3, 4)
    return img


# --- bare metal ------------------------------------------------------------------

def industrial_iron_fill():
    """Bare industrial iron without a frame: the body of unpainted tools. Colours of Create's
    Block of Industrial Iron, a little more mottled than paint: raw metal."""
    img = new()
    smooth(img, S, 4, seed=13, speckle=0.1, strength=0.12)
    return img


def industrial_iron():
    """Bare industrial iron with its frame and a row of rivets along every edge."""
    img = industrial_iron_fill()
    plate_frame(img, S, 3, 7, 9)
    for t in (4, 11):
        for x, y, nx, ny in [(t, 1, t + 1, 1), (t + 1, 14, t + 2, 14), (1, t + 1, 1, t + 2), (14, t, 14, t + 1)]:
            img.putpixel((x, y), rgb(S[1]))
            img.putpixel((nx, ny), rgb(S[8]))
    return img


# Brass, colour by colour from Create's Block of Brass and Brass Casing (owner, 20 October 2026:
# "pick them exactly"). See docs/notes/create-palette.md. Each colour has one job:
BRASS_HIGHLIGHT = "#ffeb8c"   # the very bright inner edge, top and left
BRASS_MAIN = "#e4b763"        # the one middle colour for most of the surface (median of Create's brass)
BRASS_MAIN_LIGHT = "#eec168"  # Create's lighter middle spots, used sparingly
BRASS_MAIN_DARK = "#d7aa5e"   # Create's darker middle spots, used sparingly
BRASS_SHADE = "#cea05a"       # the shaded inner edge, bottom and right
BRASS_SHADE_DARK = "#bf8e55"  # deeper shade
BRASS_RIM_LIGHT = "#9e6947"   # outer rim, top and left
BRASS_RIM_DARK = "#724731"    # outer rim, bottom and right; rivets
BRASS_CORNER = "#592424"      # the rim's corners
BR = [BRASS_HIGHLIGHT, "#f7cb6c", BRASS_MAIN_LIGHT, BRASS_MAIN, BRASS_MAIN_DARK, BRASS_SHADE,
      BRASS_SHADE_DARK, BRASS_RIM_LIGHT, BRASS_RIM_DARK, BRASS_CORNER]


# Brass as a paint palette: seven shades, light to dark, in the same places as the paints'
# shades, all picked from Create's brass. So bare brass is made exactly like the coloured
# plating (owner, 20 October 2026): the same smooth fill, light edge top and left, dark
# edge bottom and right, a darker outline.
BRASS_PAINT = [BRASS_HIGHLIGHT, "#f7cb6c", BRASS_MAIN, BRASS_SHADE, "#af794f", BRASS_RIM_LIGHT, BRASS_RIM_DARK]


def brass_fill():
    """Bare brass without a frame: made like the paints' fill, from the brass palette."""
    return painted_fill(BRASS_PAINT)


def brass():
    """Bare brass with its frame: made like the painted plating, from the brass palette."""
    return painted_plate(BRASS_PAINT)


# Polished iron, light to dark: close shades, so the bright edges stay calm.
P = ["#b2b0ad", "#a6a4a2", "#9b9a99", "#908e8d", "#858282", "#767272"]


def polished_iron_fill():
    """Polished iron without a frame: Polished Iron Plating. Smooth like the paints; no scratches,
    they read as stray light pixels (owner, 29 October 2026)."""
    img = new()
    smooth(img, P, 2, seed=17, strength=0.1)
    return img


def polished_iron():
    """Polished iron with its frame."""
    img = polished_iron_fill()
    plate_frame(img, P, 1, 3, 5)
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
    smooth(img, S, 2, seed=7, strength=0.12)
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
    smooth(img, S, 7, seed=9, strength=0.12)
    return img


def dark_steel():
    """Dark steel with its frame."""
    img = dark_steel_fill()
    plate_frame(img, S, 5, 8, 9)
    return img


def _yellow_black(band, edge):
    return rgb(Y[1 if edge else 2]) if band == 0 else rgb(BLACK[0 if edge else 2])


def _red_white(band, edge):
    return rgb(RED[1 if edge else 2] if band == 0 else WHITE[0 if edge else 1])


def _stripes(colour, step, width=4):
    """Warning stripes, 45 degrees, `width` pixels wide (4 or 8, so they tile seamlessly), from a
    stripe counter step(x, y)."""
    img = new()
    for x in range(16):
        for y in range(16):
            n = step(x, y)
            img.putpixel((x, y), colour((n // width) % 2, n % width == 0))
    return img


# How wide each colour's stripes are, in pixels. Red and white are twice as wide as yellow and
# black: half as many stripes, the narrow ones looked too busy (owner, 27 October 2026).
STRIPE_WIDTH = {"yellow_black": 4, "red_white": 8}


def hazard_stripes():
    """Yellow and black warning stripes, 45 degrees, 4 pixels wide; tiles seamlessly. No randomness:
    every tile is identical. Used flat (plates, the dump body's tailgate); the block uses hazard_face."""
    return _stripes(_yellow_black, lambda x, y: x + y)


def hazard_red_white():
    """Red and white warning stripes, like hazard_stripes."""
    return _stripes(_red_white, lambda x, y: x + y, STRIPE_WIDTH["red_white"])


# Which pixel of the block each texture pixel (u, v) of a face shows, the way Minecraft lays a
# texture on a cube's face: x east, y up, z south, all 0 to 15.
FACE_PIXELS = {
    "north": lambda u, v: (15 - u, 15 - v, 0),
    "south": lambda u, v: (u, 15 - v, 15),
    "west": lambda u, v: (0, 15 - v, u),
    "east": lambda u, v: (15, 15 - v, 15 - u),
    "up": lambda u, v: (u, 15, v),
    "down": lambda u, v: (u, 0, 15 - v),
}


def hazard_face(colour, face, flipped):
    """One face of a Hazard Stripes block. The stripes are worked out for the block as a whole
    (diagonal planes through it), so at every edge and corner a yellow stripe runs on as yellow
    and black as black, also from one block to the next (owner, 20 October 2026). Flipped turns
    the stripes on the four sides the other way; on top and bottom they keep their direction,
    since no one direction can flip on all six faces at once."""
    to_block = FACE_PIXELS[face]
    def step(u, v):
        x, y, z = to_block(u, v)
        return x - y + z if flipped else x + y + z
    width = STRIPE_WIDTH["red_white"] if colour is _red_white else STRIPE_WIDTH["yellow_black"]
    return _stripes(colour, step, width)


def corrugated(p=None):
    """Corrugated sheet with upright ribs, 4 pixels each; turned for ribs that run across.
    Bare iron without a palette, painted with one."""
    img = new()
    shades = [3, 2, 4, 6] if p is None else [2, 1, 3, 4]   # each rib: lit flank, crest, shaded flank, groove
    palette = S if p is None else p
    for x in range(16):
        for y in range(16):
            img.putpixel((x, y), rgb(palette[shades[x % 4]]))
    return img


def grating():
    """Iron floor grating like a real pressed grating: load bars 2 pixels thick with 2-pixel gaps,
    cross bars 2 pixels thick every 8. Each bar is lit on top or left. Gaps are transparent."""
    img = new()
    for x in range(16):
        for y in range(16):
            # Bars sit on both edges of the tile (x 15 and 0, y 15 and 0), so a block's border is never a gap.
            load = x % 4 in (3, 0)
            cross = y % 8 in (7, 0)
            if load or cross:
                lit = (x % 4 == 3) if load and not cross else (y % 8 == 7)
                img.putpixel((x, y), rgb(S[3] if lit else S[5]))
    return img


def tread_plate():
    """Steel tread plate: short raised bars in alternating directions. Floors, steps, the container's floor."""
    img = new()
    smooth(img, S, 3, seed=11, strength=0.12)
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


# --- connected textures ------------------------------------------------------------
# The layout of Create's 8 x 8 "omnidirectional" connected-texture sheet (as its casings use),
# read from Create's andesite casing sheet: for each tile, which edges carry a frame
# (t, b, l, r) and after "/" which corners show a small inner corner (a top left,
# b top right, c bottom left, d bottom right). "." is an unused tile, "-" no edge.
CT_LAYOUT = """
tblr blr tlr lr . . . .
tbr br/a tr/c r/ac br tr . .
tbl bl/b tl/d l/bd bl tl . .
tb b/ab t/cd -/abcd -/acd -/bcd -/cd .
r/a r/c r -/abd -/ad -/bd -/d .
l/b l/d l -/abc -/ac -/bc -/c .
b/b b/a b -/ab -/a -/b - .
t/d t/c t . . . . .
"""


def connected(framed, fill):
    """Builds a 128 x 128 connected-texture sheet from a framed texture and its fill:
    each tile is the fill with the frame only on the edges the layout names."""
    sheet = Image.new("RGBA", (128, 128))
    rows = [r.split() for r in CT_LAYOUT.strip().splitlines()]
    for ty, row in enumerate(rows):
        for tx, code in enumerate(row):
            if code == ".":
                continue
            edges, _, inner = code.partition("/")
            t, b, l, r = ("t" in edges), ("b" in edges), ("l" in edges), ("r" in edges)
            tile = fill.copy()
            for x in range(16):
                for y in range(16):
                    top, bottom, left, right = y < 2, y > 13, x < 2, x > 13
                    sx = sy = None
                    # Edges: take the frame's pixels; where a corner has only one framed edge,
                    # continue that edge's strip through the corner.
                    if top and t:
                        sy = y
                        sx = x if (not left or l) and (not right or r) else 6 + x % 2
                    elif bottom and b:
                        sy = y
                        sx = x if (not left or l) and (not right or r) else 6 + x % 2
                    if left and l and sx is None:
                        sx, sy = x, (y if (not top or t) and (not bottom or b) else 6 + y % 2)
                    elif right and r and sx is None:
                        sx, sy = x, (y if (not top or t) and (not bottom or b) else 6 + y % 2)
                    if sx is not None:
                        tile.putpixel((x, y), framed.getpixel((sx, sy)))
            outline = framed.getpixel((8, 0))
            for name, (cx, cy, dx, dy) in {"a": (0, 0, 1, 1), "b": (15, 0, -1, 1),
                                           "c": (0, 15, 1, -1), "d": (15, 15, -1, -1)}.items():
                if name in inner:
                    for px, py in [(cx, cy), (cx + dx, cy), (cx, cy + dy)]:
                        tile.putpixel((px, py), outline)
            sheet.paste(tile, (tx * 16, ty * 16))
    return sheet


# --- site barriers (add-site-barriers, 29 October 2026) ---------------------------------
# Plain, smooth colours like the rest of the mod: one base colour with faint speckles.
CONE_ORANGE = ["#f08a45", "#e8722a", "#cc5f1f"]
CONE_WHITE = ["#f6f5f0", "#ebe9e2", "#dad7ce"]
RUBBER = ["#3a3a3e", "#2e2e32", "#242427"]
FENCE_STEEL = ["#a9aeb3", "#979ca1", "#83888d"]
CONCRETE = ["#b0b0ab", "#a3a39e", "#94948f"]


def plain(palette, seed):
    """A smooth surface in one colour (the middle of a 3-shade palette)."""
    img = new()
    smooth(img, palette, 1, seed=seed, strength=0.1)
    return img


def barrier_board():
    """The barrier board: red and white blocks, 4 pixels each, along the board. Tiles along it."""
    img = new()
    for x in range(16):
        for y in range(16):
            img.putpixel((x, y), rgb(RED[2] if (x // 4) % 2 == 0 else WHITE[1]))
    return img


def site_fence_mesh():
    """The fence's wire mesh, see-through between the wires: tall, narrow cells like a real site
    fence, an upright wire every 2 pixels and a level one every 8 (owner's look B, 29 October 2026)."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x in range(16):
        for y in range(16):
            if x % 2 == 1:
                img.putpixel((x, y), rgb(FENCE_STEEL[0]))
            elif y % 8 == 3:
                img.putpixel((x, y), rgb(FENCE_STEEL[1]))
    return img


TEXTURES = {
    "traffic_cone_orange": lambda: plain(CONE_ORANGE, 41),
    "traffic_cone_white": lambda: plain(CONE_WHITE, 42),
    "rubber": lambda: plain(RUBBER, 43),
    "barrier_board": barrier_board,
    "site_fence_mesh": site_fence_mesh,
    "site_fence_steel": lambda: plain(FENCE_STEEL, 44),
    "site_fence_foot": lambda: plain(CONCRETE, 45),
    "painted_steel_yellow_panel": painted_panel,
    "painted_steel_yellow_ribbed": painted_ribbed,
    "worn_steel": worn_steel,
    "worn_steel_fill": worn_steel_fill,
    "dark_steel": dark_steel,
    "dark_steel_fill": dark_steel_fill,
    "hazard_stripes": hazard_stripes,
    "tread_plate": tread_plate,
    "hazard_stripes_red_white": hazard_red_white,
    # The plates' flipped look: the same stripes the other way round (mirrored), so they still tile.
    "hazard_stripes_mirrored": lambda: _stripes(_yellow_black, lambda x, y: 15 - x + y, STRIPE_WIDTH["yellow_black"]),
    "hazard_stripes_red_white_mirrored": lambda: _stripes(_red_white, lambda x, y: 15 - x + y, STRIPE_WIDTH["red_white"]),
    **{f"{name}{'_flipped' if flipped else ''}_{face}": (lambda c=colour, f=face, fl=flipped: hazard_face(c, f, fl))
       for name, colour in [("hazard_stripes", _yellow_black), ("hazard_stripes_red_white", _red_white)]
       for flipped in [False, True] for face in FACE_PIXELS},
    "brass": brass,
    "brass_fill": brass_fill,
    "polished_iron": polished_iron,
    "polished_iron_fill": polished_iron_fill,
    "corrugated_iron": corrugated,
    "grating": grating,
}
for _dye in DYES:
    _p = paint_palette(_dye)
    TEXTURES[f"painted_steel_{_dye}"] = (lambda p=_p: painted_plate(p))
    TEXTURES[f"painted_steel_{_dye}_fill"] = (lambda p=_p: painted_fill(p))
    TEXTURES[f"corrugated_{_dye}"] = (lambda p=_p: corrugated(p))
    # The Painted Metal Block's connected texture (Create's casing layout).
    TEXTURES[f"painted_steel_{_dye}_connected"] = (lambda p=_p: connected(painted_plate(p), painted_fill(p)))
# Brass Plating's connected texture (bare brass, Create's casing layout).
TEXTURES["brass_connected"] = lambda: connected(brass(), brass_fill())
# Dark Steel Plating and Polished Iron Plating: the tools' other two metals as blocks.
TEXTURES["dark_steel_connected"] = lambda: connected(dark_steel(), dark_steel_fill())
TEXTURES["polished_iron_connected"] = lambda: connected(polished_iron(), polished_iron_fill())
# The bare Corrugated Sheet is brass too (owner, 20 October 2026): Create's brass shades for the ribs.
TEXTURES["corrugated_brass"] = lambda: corrugated(["#f7cb6c", "#eec168", BRASS_MAIN, BRASS_SHADE, BRASS_SHADE_DARK,
                                                    BRASS_SHADE_DARK, BRASS_RIM_LIGHT])

# --- hydraulics (owner, 6 November 2026: "the colours are too perfect") ----------------
# The cylinder's parts get the platings' edges: a dark outline and a light and a dark bevel
# along both sides of each bar. Only the first `width` columns are used (the renderer samples
# them pixel for pixel); the pattern runs along the rows, so the bars repeat seamlessly.

def bar(fill, palette, width, light, dark, outline=None):
    """A bar's side: columns 0..width-1 across the bar, the fill's own rows along it."""
    img = fill.copy()
    edge = 0 if outline is None else 1
    for y in range(16):
        if outline is not None:
            img.putpixel((0, y), rgb(palette[outline]))
            img.putpixel((width - 1, y), rgb(palette[outline]))
        img.putpixel((edge, y), rgb(palette[light]))
        img.putpixel((width - 1 - edge, y), rgb(palette[dark]))
    return img


def bar_end(fill, palette, width, light, dark, outline=None):
    """A bar's end face: a framed square of `width` pixels in the top-left corner (outline,
    then light on top and left, dark on bottom and right), like a plating seen end-on."""
    img = fill.copy()
    edge = 0 if outline is None else 1
    if outline is not None:
        for i in range(width):
            for p in [(i, 0), (0, i), (i, width - 1), (width - 1, i)]:
                img.putpixel(p, rgb(palette[outline]))
    light_frame(img, palette, light, dark, edge, edge, width - edge, width - edge)
    return img


def cylinder_mount_base():
    """The mount's dark steel base plate (12 x 3 x 12 pixels): its top in the middle 12 x 12
    with an outline and bevel like Dark Steel Plating; its sides in rows 0 to 2."""
    img = dark_steel_fill()
    light_frame(img, S, 5, 8, 3, 3, 13, 13)
    for i in range(2, 14):
        for p in [(i, 2), (2, i), (i, 13), (13, i)]:
            img.putpixel(p, rgb(S[9]))
    for x in range(2, 14):
        img.putpixel((x, 0), rgb(S[5]))
        img.putpixel((x, 1), rgb(S[7]))
        img.putpixel((x, 2), rgb(S[9]))
    for y in range(0, 3):
        img.putpixel((2, y), rgb(S[9]))
        img.putpixel((13, y), rgb(S[9]))
    return img


TEXTURES["cylinder_barrel"] = lambda: bar(dark_steel_fill(), S, 7, 5, 8, 9)
TEXTURES["cylinder_collar"] = lambda: bar(brass_fill(), BRASS_PAINT, 9, 1, 3, 4)
TEXTURES["cylinder_rod"] = lambda: bar(polished_iron_fill(), P, 4, 0, 4)
TEXTURES["cylinder_eye"] = lambda: bar(dark_steel_fill(), S, 6, 5, 8, 9)
TEXTURES["cylinder_mount_base"] = cylinder_mount_base
TEXTURES["cylinder_barrel_end"] = lambda: bar_end(dark_steel_fill(), S, 7, 5, 8, 9)
TEXTURES["cylinder_collar_end"] = lambda: bar_end(brass_fill(), BRASS_PAINT, 9, 1, 3, 4)
TEXTURES["cylinder_rod_end"] = lambda: bar_end(polished_iron_fill(), P, 4, 0, 4)
TEXTURES["cylinder_eye_end"] = lambda: bar_end(dark_steel_fill(), S, 6, 5, 8, 9)

if __name__ == "__main__":
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "out")
    out.mkdir(parents=True, exist_ok=True)
    for name, make in TEXTURES.items():
        make().save(out / f"{name}.png")
        print("wrote", out / f"{name}.png")
