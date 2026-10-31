"""Block states, models, loot tables, names and tags for the site barriers (add-site-barriers):
traffic cone, delineator post, barrier board and site fence.

Usage: python tools/resources/generate_barrier_resources.py [src/main/resources]
The textures come from tools/textures/generate_textures.py. Models are written as boxes with
every face's texture laid by its position in the block, so textures run on from block to block,
and a thin part shows the same pixel column on its front and its back.
"""
import json
import sys
from pathlib import Path

MOD = "create_construction_site"


def rl(path):
    return f"{MOD}:{path}"


def tex(name):
    return rl(f"block/{name}")


def _span(a0, a1):
    """A texture range for a position range; parts reaching past the block start over at 0."""
    if a1 > 16:
        return (a0 - 16, a1 - 16) if a0 >= 16 else (a0, 16)
    if a0 < 0:
        return (a0 + 16, a1 + 16) if a1 <= 0 else (0, a1)
    return (a0, a1)


def box(lo, hi, texture, faces=("north", "east", "south", "west", "up", "down")):
    """One model element. Each face samples the texture where it sits in the block."""
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    ux0, ux1 = _span(x0, x1)
    uz0, uz1 = _span(z0, z1)
    vy0, vy1 = _span(16 - y1, 16 - y0)
    uv = {
        "up": [ux0, uz0, ux1, uz1],
        "down": [ux0, uz0, ux1, uz1],
        "south": [ux0, vy0, ux1, vy1],
        "north": [ux1, vy0, ux0, vy1],   # read backwards: the same column as the south face
        "west": [uz0, vy0, uz1, vy1],
        "east": [uz1, vy0, uz0, vy1],    # read backwards: the same column as the west face
    }
    return {"from": list(lo), "to": list(hi),
            "faces": {f: {"texture": f"#{texture}", "uv": uv[f]} for f in faces}}


def board(lo, hi, texture):
    """A thin board facing north and south: its top shows the front's top row and its two ends
    show the front's edge columns, so the pattern turns over the edges (owner, 29 October 2026)."""
    e = box(lo, hi, texture)
    x0, y0, _ = lo
    x1, y1, _ = hi
    v0, v1 = 16 - y1, 16 - y0
    e["faces"]["up"]["uv"] = [x0, v0, x1, v0 + 1]
    e["faces"]["down"]["uv"] = [x0, v1 - 1, x1, v1]
    e["faces"]["west"]["uv"] = [x0, v0, x0 + 1, v1]
    e["faces"]["east"]["uv"] = [x1 - 1, v0, x1, v1]
    return e


def model(elements, textures, cutout=False):
    data = {"parent": "minecraft:block/block", "textures": textures, "elements": elements}
    if cutout:
        data["render_type"] = "minecraft:cutout"
    return data


class Out:
    def __init__(self, root):
        self.root = Path(root)
        self.count = 0

    def write(self, rel, data):
        path = self.root / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
        self.count += 1

    def assets(self, rel, data):
        self.write(f"assets/{MOD}/{rel}", data)

    def data(self, rel, data):
        self.write(f"data/{MOD}/{rel}", data)


def loot(out, block_id, count_by=None):
    """Drops itself; with count_by (property, values) it drops as many as the property says."""
    entry = {"type": "minecraft:item", "name": rl(block_id)}
    if count_by:
        prop, values = count_by
        entry["functions"] = [{
            "function": "minecraft:set_count", "add": False, "count": v,
            "conditions": [{"condition": "minecraft:block_state_property", "block": rl(block_id),
                            "properties": {prop: str(v)}}]} for v in values]
    out.data(f"loot_table/blocks/{block_id}.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [entry], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})


TURN = {"north": 0, "east": 90, "south": 180, "west": 270}


def cone(out, lang):
    """Traffic cone: orange skirt, orange step, white band, orange top (owner's C2), standing
    straight on the ground. Cones stack without limit, 2 pixels apart, eight per block; a column
    runs on through the blocks above (owner, 29 October 2026). Of each cone below the top, the
    skirt and the bottom of its step show; the top cone is drawn whole, in the column's top block.

    Models: traffic_cone_<n> for a top block with n cones (n - 1 rims and the whole top cone),
    traffic_cone_<n>_full for a block further down (n rims)."""
    t = {"orange": tex("traffic_cone_orange"), "white": tex("traffic_cone_white"),
         "particle": tex("traffic_cone_orange")}
    per_block = 8

    def layer(lo, hi, side, y):
        return ([8 - side, y + lo, 8 - side], [8 + side, y + hi, 8 + side])

    def rim(y):
        return [box(*layer(0, 1, 5.5, y), "orange"), box(*layer(1, 2, 3.5, y), "orange")]

    for cones in range(1, per_block + 1):
        rims = [e for i in range(cones - 1) for e in rim(2 * i)]
        y = 2 * (cones - 1)
        whole = [box(*layer(0, 1, 5.5, y), "orange"), box(*layer(1, 5, 3.5, y), "orange"),
                 box(*layer(5, 8, 2.5, y), "white"), box(*layer(8, 12, 1.5, y), "orange")]
        out.assets(f"models/block/traffic_cone_{cones}.json", model(rims + whole, t))
        out.assets(f"models/block/traffic_cone_{cones}_full.json", model(rims + rim(y), t))

    # Lamps on top of the stack (owner's idea, 29 October 2026). The lens blinks (an animated
    # texture) and is drawn at full brightness; the block gives light (TrafficConeBlock).
    lt = {"lens": tex("cone_lamp_lens"), "housing": tex("rubber"), "particle": tex("rubber")}
    glow = {"block_light": 15, "sky_light": 15}

    def lit(e):
        e["neoforge_data"] = glow
        return e

    for cones in range(1, per_block + 1):
        y = 12 + 2 * (cones - 1)          # the tip of the top cone
        # Flasher: a small round lens in a black housing, clipped onto the tip; it shows both ways.
        out.assets(f"models/block/cone_flasher_{cones}.json", model([
            box([7, y, 7], [9, y + 1, 9], "housing"),
            box([5.5, y + 1, 7.25], [10.5, y + 6, 8.75], "housing"),
            lit(box([6.5, y + 2, 6.75], [9.5, y + 5, 9.25], "lens"))], lt))
        # Beacon: a small amber dome on a black foot that lights all round.
        out.assets(f"models/block/cone_beacon_{cones}.json", model([
            box([6, y, 6], [10, y + 1, 10], "housing"),
            lit(box([6.5, y + 1, 6.5], [9.5, y + 4, 9.5], "lens")),
            box([7, y + 4, 7], [9, y + 5, 9], "housing")], lt))
    parts = []
    for c in range(1, per_block + 1):
        parts.append({"when": {"cones": str(c), "top": "true"}, "apply": {"model": rl(f"block/traffic_cone_{c}")}})
        parts.append({"when": {"cones": str(c), "top": "false"}, "apply": {"model": rl(f"block/traffic_cone_{c}_full")}})
        # The flasher's lens faces the player who put it on: north-south as modelled, or turned
        # to face east-west (owner, 29 October 2026). The beacon is round, so it never turns.
        for axis, turn in (("z", {}), ("x", {"y": 90})):
            parts.append({"when": {"cones": str(c), "top": "true", "lamp": "flasher", "lamp_axis": axis},
                          "apply": {"model": rl(f"block/cone_flasher_{c}"), **turn}})
        parts.append({"when": {"cones": str(c), "top": "true", "lamp": "beacon"},
                      "apply": {"model": rl(f"block/cone_beacon_{c}")}})
    out.assets("blockstates/traffic_cone.json", {"multipart": parts})
    out.assets("models/item/traffic_cone.json", {"parent": rl("block/traffic_cone_1")})
    # Breaking drops this block's cones, and the lamp if one is on.
    cone_loot = {"type": "minecraft:item", "name": rl("traffic_cone"), "functions": [{
        "function": "minecraft:set_count", "add": False, "count": v,
        "conditions": [{"condition": "minecraft:block_state_property", "block": rl("traffic_cone"),
                        "properties": {"cones": str(v)}}]} for v in range(1, per_block + 1)]}
    pools = [{"rolls": 1, "entries": [cone_loot], "conditions": [{"condition": "minecraft:survives_explosion"}]}]
    for lamp in ("flasher", "beacon"):
        pools.append({"rolls": 1, "entries": [{"type": "minecraft:item", "name": rl(f"warning_{lamp}")}], "conditions": [
            {"condition": "minecraft:block_state_property", "block": rl("traffic_cone"), "properties": {"lamp": lamp}}]})
    out.data("loot_table/blocks/traffic_cone.json", {"type": "minecraft:block", "pools": pools})
    lang[f"block.{MOD}.traffic_cone"] = "Traffic Cone"
    # Create's "Hold [Shift] for Summary", kept to one line (owner, 29 October 2026).
    lang[f"block.{MOD}.traffic_cone.tooltip.summary"] = \
        "Use a cone on another to _stack_ it, as high as you like. _Sneak_ to take the top one off."
    warning_lamps(out, lang, lt, lit)
    lamp_textures(out)


def warning_lamps(out, lang, lt, lit):
    """The same two lamps on their own (owner, 29 October 2026): Warning Flasher and Warning Beacon,
    on a floor, a wall or a ceiling like a button, for vehicles and buildings. Used on a cone, the
    item clips onto the cone instead (the cone's lamp models above)."""
    # Floor models, as on a cone but standing on the ground; the flasher's lens faces north-south.
    out.assets("models/block/warning_flasher.json", model([
        box([7, 0, 7], [9, 1, 9], "housing"),
        box([5.5, 1, 7.25], [10.5, 6, 8.75], "housing"),
        lit(box([6.5, 2, 6.75], [9.5, 5, 9.25], "lens"))], lt))
    # On a wall (facing north: on the wall to the south) the flasher stands upright on a bracket,
    # its lens facing out, away from the wall.
    out.assets("models/block/warning_flasher_wall.json", model([
        box([7, 4, 14.5], [9, 6, 16], "housing"),
        box([5.5, 2.5, 13], [10.5, 7.5, 14.5], "housing"),
        lit(box([6.5, 3.5, 12.5], [9.5, 6.5, 14.5], "lens"))], lt))
    out.assets("models/block/warning_beacon.json", model([
        box([6, 0, 6], [10, 1, 10], "housing"),
        lit(box([6.5, 1, 6.5], [9.5, 4, 9.5], "lens")),
        box([7, 4, 7], [9, 5, 9], "housing")], lt))
    for lamp, name in (("flasher", "Warning Flasher"), ("beacon", "Warning Beacon")):
        block = f"warning_{lamp}"
        variants = {}
        for facing, turn in TURN.items():
            # Turned the way vanilla turns buttons: the floor model laid on its back for walls,
            # upside down for ceilings.
            variants[f"face=floor,facing={facing}"] = {"model": rl(f"block/{block}"), **({"y": turn} if turn else {})}
            variants[f"face=ceiling,facing={facing}"] = {"model": rl(f"block/{block}"), "x": 180, "y": (turn + 180) % 360}
            wall = ({"model": rl(f"block/{block}_wall")} if lamp == "flasher"
                    else {"model": rl(f"block/{block}"), "x": 90})
            if turn:
                wall["y"] = turn
            variants[f"face=wall,facing={facing}"] = wall
        out.assets(f"blockstates/{block}.json", {"variants": variants})
        out.assets(f"models/item/{block}.json", {"parent": "minecraft:item/generated",
                                                 "textures": {"layer0": rl(f"item/{block}")}})
        loot(out, block)
        lang[f"block.{MOD}.{block}"] = name
        lang[f"block.{MOD}.{block}.tooltip.summary"] = \
            "Place it on _any block_, or use it on a _Traffic Cone_ to put it _on top_."
        # The lamps were items called Cone Flasher and Cone Beacon before.
        for old in (f"item.{MOD}.cone_{lamp}", f"item.{MOD}.cone_{lamp}.tooltip.summary"):
            lang.pop(old, None)


def lamp_textures(out):
    """The blinking lens (two frames, on and off, half a second each) and the two item pictures."""
    from PIL import Image
    on = [(255, 196, 64, 255), (255, 170, 30, 255), (230, 140, 20, 255)]
    off = [(150, 96, 30, 255), (128, 80, 24, 255), (110, 68, 20, 255)]
    lens = Image.new("RGBA", (16, 32))
    for frame, shades in enumerate((on, off)):
        for x in range(16):
            for y in range(16):
                # Bright in the middle, a little darker toward the edge: a lens, smooth.
                d = max(abs(x - 7.5), abs(y - 7.5))
                lens.putpixel((x, y + 16 * frame), shades[0] if d < 3 else shades[1] if d < 6 else shades[2])
    block_dir = out.root / f"assets/{MOD}/textures/block"
    item_dir = out.root / f"assets/{MOD}/textures/item"
    block_dir.mkdir(parents=True, exist_ok=True)
    item_dir.mkdir(parents=True, exist_ok=True)
    lens.save(block_dir / "cone_lamp_lens.png")
    (block_dir / "cone_lamp_lens.png.mcmeta").write_text(
        json.dumps({"animation": {"frametime": 10}}, indent=2) + "\n", encoding="utf-8")
    black, amber, light = (46, 46, 50, 255), (255, 170, 30, 255), (255, 214, 110, 255)
    flasher = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x in range(3, 13):
        for y in range(2, 12):
            flasher.putpixel((x, y), black)
    for x in range(5, 11):
        for y in range(4, 10):
            if (x - 7.5) ** 2 + (y - 6.5) ** 2 <= 9:
                flasher.putpixel((x, y), light if (x, y) in ((6, 5), (7, 5), (6, 6), (7, 6)) else amber)
    for x in range(7, 9):
        for y in range(12, 15):
            flasher.putpixel((x, y), black)
    beacon = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x in range(3, 13):
        for y in range(12, 15):
            beacon.putpixel((x, y), black)
    for x in range(4, 12):
        for y in range(5, 12):
            if (x - 7.5) ** 2 + max(0, 8 - y) ** 2 <= 16:
                beacon.putpixel((x, y), light if (x, y) in ((6, 7), (7, 7), (6, 8), (7, 8)) else amber)
    for x in range(6, 10):
        beacon.putpixel((x, 4), black)
    flasher.save(item_dir / "warning_flasher.png")
    beacon.save(item_dir / "warning_beacon.png")
    out.count += 4


def delineator(out, lang):
    """Delineator post: a red-white board on a rubber foot, front to the north before turning."""
    for flipped in (False, True):
        stripes = tex("hazard_stripes_red_white" + ("_mirrored" if flipped else ""))
        name = "delineator" + ("_flipped" if flipped else "")
        out.assets(f"models/block/{name}.json", model(
            [box([3, 0, 5], [13, 2, 11], "rubber"), board([5, 2, 7], [11, 16, 9], "stripes")],
            {"rubber": tex("rubber"), "stripes": stripes, "particle": stripes}))
    variants = {}
    for facing, y in TURN.items():
        for flipped in ("false", "true"):
            m = {"model": rl("block/delineator" + ("_flipped" if flipped == "true" else ""))}
            if y:
                m["y"] = y
            variants[f"facing={facing},flipped={flipped}"] = m
    out.assets("blockstates/delineator.json", {"variants": variants})
    out.assets("models/item/delineator.json", {"parent": rl("block/delineator")})
    loot(out, "delineator")
    lang[f"block.{MOD}.delineator"] = "Delineator Post"


def barrier(out, lang):
    """Barrier board, front to the north before turning: left (seen from the front) is +x.
    A stand at each free end; at a joint the stand is drawn once, by the block on the right."""
    t = {"board": tex("barrier_board"), "pole": tex("traffic_cone_white"), "rubber": tex("rubber"),
         "particle": tex("barrier_board")}

    def stand(x):
        return [box([x, 1, 7], [x + 2, 9, 9], "pole"), box([x - 1, 0, 4], [x + 3, 1, 12], "rubber")]

    out.assets("models/block/barrier_board.json", model([board([0, 9, 7], [16, 14, 9], "board")], t))
    out.assets("models/block/barrier_board_stand_left.json", model(stand(13), t))
    out.assets("models/block/barrier_board_stand_right.json", model(stand(1), t))
    out.assets("models/block/barrier_board_stand_joint.json", model(stand(15), t))
    out.assets("models/item/barrier_board.json",
               model([board([0, 9, 7], [16, 14, 9], "board")] + stand(13) + stand(1), t))
    parts = []
    for facing, y in TURN.items():
        turn = {"y": y} if y else {}
        parts += [
            {"when": {"facing": facing}, "apply": {"model": rl("block/barrier_board"), **turn}},
            {"when": {"facing": facing, "left": "false"}, "apply": {"model": rl("block/barrier_board_stand_left"), **turn}},
            {"when": {"facing": facing, "left": "true"}, "apply": {"model": rl("block/barrier_board_stand_joint"), **turn}},
            {"when": {"facing": facing, "right": "false"}, "apply": {"model": rl("block/barrier_board_stand_right"), **turn}},
        ]
    out.assets("blockstates/barrier_board.json", {"multipart": parts})
    loot(out, "barrier_board")
    lang[f"block.{MOD}.barrier_board"] = "Barrier Board"


DYES = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
        "purple", "blue", "brown", "green", "red", "black"]


def fence(out, lang):
    """Site fence, two blocks tall (owner's look B, 29 October 2026): concrete feet, a thick frame
    (2-pixel rails and posts), a mesh of upright wires every 2 pixels and level ones every 8, clamps
    where two panels meet at a post, and a privacy tarp in any carpet colour (one white wool model
    per side, tinted by the client).

    The fence is laid out over 32 pixels of height and cut into the lower and the upper block.
    Every part is drawn in its own place (not turned), so the mesh lines up in both directions.
    Model names: <part> for the lower block on the ground, <part>_stacked for a lower block on
    another fence (no feet), <part>_upper for the upper block."""
    t = {"mesh": tex("site_fence_mesh"), "steel": tex("site_fence_steel"), "foot": tex("site_fence_foot"),
         "clamp": tex("rubber"), "particle": tex("site_fence_steel")}
    variants = {"": ("lower", True), "_stacked": ("lower", False), "_upper": ("upper", True)}

    def cut(half, lo, hi):
        """The piece of a box (in fence height, 0 to 32) that lies in one block, or None."""
        y0, y1 = (lo[1], min(hi[1], 16)) if half == "lower" else (max(lo[1], 16) - 16, hi[1] - 16)
        if y1 <= y0:
            return None
        return [lo[0], y0, lo[2]], [hi[0], y1, hi[2]]

    def parts(half, grounded, items):
        els = []
        for lo, hi, texture, faces in items:
            piece = cut(half, lo, hi)
            if piece:
                els.append(box(*piece, texture, faces) if faces else box(*piece, texture))
        return els

    halves = {  # side: (x0, x1, z0, z1, along x?)
        "east": (8, 16, 7.5, 8.5, True), "west": (0, 8, 7.5, 8.5, True),
        "south": (7.5, 8.5, 8, 16, False), "north": (7.5, 8.5, 0, 8, False),
    }
    for tag, (half, grounded) in variants.items():
        base = 4 if grounded else 0
        for side, (x0, x1, z0, z1, along_x) in halves.items():
            thin = ("north", "south") if along_x else ("east", "west")
            ends = ("north", "south", "up", "down") if along_x else ("east", "west", "up", "down")
            rail = (lambda a, b: ([x0, a, 7], [x1, b, 9])) if along_x else (lambda a, b: ([7, a, z0], [9, b, z1]))
            items = [([x0, base + 2, z0], [x1, 30, z1], "mesh", thin),
                     (*rail(30, 32), "steel", ends), (*rail(base, base + 2), "steel", ends)]
            out.assets(f"models/block/site_fence_{side}{tag}.json", model(parts(half, grounded, items), t, cutout=True))
            # The tarp hangs over the whole panel, on the side the player stood on: south or east
            # ("site_fence_tarp_..."), or north or west ("..._back"). White wool, tinted in the
            # tarp's colour by the client (CreateConstructionSiteClient).
            for back in (False, True):
                a, b = (7.25, 7.5) if back else (8.5, 8.75)
                tarp = ([x0, base + 2, a], [x1, 30, b]) if along_x else ([a, base + 2, z0], [b, 30, z1])
                tarp_els = parts(half, grounded, [(*tarp, "tarp", None)])
                for e in tarp_els:
                    for face in e["faces"].values():
                        face["tintindex"] = 0
                out.assets(f"models/block/site_fence_tarp_{side}{tag}{'_back' if back else ''}.json",
                           model(tarp_els, {"tarp": "minecraft:block/white_wool", "particle": "minecraft:block/white_wool"}))
        out.assets(f"models/block/site_fence_post{tag}.json",
                   model(parts(half, grounded, [([7, base, 7], [9, 32, 9], "steel", None)]), t))
        out.assets(f"models/block/site_fence_clamp{tag}.json", model(parts(half, grounded, [
            ([6.5, base + 3, 6.5], [9.5, base + 5, 9.5], "clamp", None),
            ([6.5, 26, 6.5], [9.5, 28, 9.5], "clamp", None)]), t))
    out.assets("models/block/site_fence_foot_x.json", model([box([2, 0, 5], [14, 4, 11], "foot")], t))
    out.assets("models/block/site_fence_foot_z.json", model([box([5, 0, 2], [11, 4, 14], "foot")], t))

    when_variant = {"": {"half": "lower", "bottom": "true"}, "_stacked": {"half": "lower", "bottom": "false"},
                    "_upper": {"half": "upper"}}
    pairs = [{a: "true", b: "true"} for a, b in [("east", "west"), ("north", "south"), ("north", "east"),
                                                 ("east", "south"), ("south", "west"), ("west", "north")]]
    mp = []
    for tag, cond in when_variant.items():
        mp.append({"when": {"post": "true", **cond}, "apply": {"model": rl(f"block/site_fence_post{tag}")}})
        # Clamps where two panels meet at a post.
        mp.append({"when": {"AND": [{"post": "true", **cond}, {"OR": pairs}]},
                   "apply": {"model": rl(f"block/site_fence_clamp{tag}")}})
        for side in halves:
            mp.append({"when": {side: "true", **cond}, "apply": {"model": rl(f"block/site_fence_{side}{tag}")}})
            # East and west halves run east-west: TARP_SOUTH picks their face; north and south halves, TARP_EAST.
            facing_prop = "tarp_south" if side in ("east", "west") else "tarp_east"
            for front, suffix in (("true", ""), ("false", "_back")):
                mp.append({"when": {"AND": [{side: "true", facing_prop: front, **cond},
                                            {"OR": [{"tarp": dye} for dye in DYES]}]},
                           "apply": {"model": rl(f"block/site_fence_tarp_{side}{tag}{suffix}")}})
    # The foot lies across the run: along z under an east-west run, along x otherwise.
    ground = {"half": "lower", "bottom": "true", "post": "true"}
    mp.append({"when": {"AND": [ground, {"OR": [{"east": "true"}, {"west": "true"}]}]},
               "apply": {"model": rl("block/site_fence_foot_z")}})
    mp.append({"when": {**ground, "east": "false", "west": "false"}, "apply": {"model": rl("block/site_fence_foot_x")}})
    out.assets("blockstates/site_fence.json", {"multipart": mp})

    # The item is a flat picture of a fence panel, like a door's item.
    out.assets("models/item/site_fence.json", {"parent": "minecraft:item/generated",
                                               "textures": {"layer0": rl("item/site_fence")}})
    from PIL import Image
    icon = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    frame, wire, foot = (124, 129, 134, 255), (169, 174, 179, 255), (163, 163, 158, 255)
    for x in range(1, 15):
        for y in range(1, 13):
            edge = x in (1, 14) or y in (1, 12)
            if edge:
                icon.putpixel((x, y), frame)
            elif x % 2 == 1 or y in (4, 8):
                icon.putpixel((x, y), wire)
    for x0 in (0, 12):
        for x in range(x0, x0 + 4):
            for y in range(13, 16):
                icon.putpixel((x, y), foot)
    path = out.root / f"assets/{MOD}/textures/item/site_fence.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    icon.save(path)
    out.count += 1
    # The lower block drops the fence, and the tarp's carpet if it has one.
    entries = [{"type": "minecraft:item", "name": rl("site_fence")}]
    pools = [{"rolls": 1, "entries": entries, "conditions": [
        {"condition": "minecraft:block_state_property", "block": rl("site_fence"), "properties": {"half": "lower"}},
        {"condition": "minecraft:survives_explosion"}]}]
    for dye in DYES:
        pools.append({"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"minecraft:{dye}_carpet"}], "conditions": [
            {"condition": "minecraft:block_state_property", "block": rl("site_fence"),
             "properties": {"half": "lower", "tarp": dye}}]})
    out.data("loot_table/blocks/site_fence.json", {"type": "minecraft:block", "pools": pools})
    lang[f"block.{MOD}.site_fence"] = "Site Fence"
    lang[f"block.{MOD}.site_fence.tooltip.summary"] = \
        "Use a _Carpet_ on it to hang a _tarp_ on your side. _Shears_ take it off."


def main(root):
    out = Out(root)
    lang_path = Path(root) / f"assets/{MOD}/lang/en_us.json"
    lang = json.loads(lang_path.read_text(encoding="utf-8"))
    cone(out, lang)
    delineator(out, lang)
    barrier(out, lang)
    fence(out, lang)
    lang_path.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    # The fence is metal: mined fastest with a pickaxe. Added to the tag the paint script writes.
    tag_path = Path(root) / "data/minecraft/tags/block/mineable/pickaxe.json"
    tag = json.loads(tag_path.read_text(encoding="utf-8"))
    if rl("site_fence") not in tag["values"]:
        tag["values"].append(rl("site_fence"))
    tag_path.write_text(json.dumps(tag, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {out.count} files, the language file and the pickaxe tag")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "src/main/resources")
