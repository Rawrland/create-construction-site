"""
Writes the many small resource files for the paint versions of the tools and for the
metal blocks: block states, models, loot tables, tags, recipes and language entries.

Each tool (excavator_bucket, bulldozer_blade, dump_body), Brass Plating and the corrugated
sheet come bare (brass) and in the 16 dye colours. Run it again whenever something here changes; it
overwrites what it wrote before and leaves every other file alone.

Run:  python generate_paint_resources.py <src/main/resources folder>
"""
import json
import sys
from pathlib import Path

MOD = "create_construction_site"
# The order of the creative tab: like vanilla's wool.
DYES = ["white", "light_gray", "gray", "black", "brown", "red", "orange", "yellow",
        "lime", "green", "cyan", "light_blue", "blue", "purple", "magenta", "pink"]
TOOLS = {"excavator_bucket": "Excavator Bucket", "bulldozer_blade": "Bulldozer Blade", "dump_body": "Dump Body"}
# Item tags of every version of a kind (the plural names).
KIND_TAGS = {"excavator_bucket": "excavator_buckets", "bulldozer_blade": "bulldozer_blades",
             "dump_body": "dump_bodies", "corrugated_sheet": "corrugated_sheets"}


def name(dye):
    return " ".join(w.capitalize() for w in dye.split("_"))


def vid(dye, base):
    return base if dye is None else f"{dye}_{base}"


def rl(path):
    return f"{MOD}:{path}"


class Out:
    def __init__(self, root):
        self.root = Path(root)
        self.count = 0

    def json(self, rel, data):
        path = self.root / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
        self.count += 1

    def assets(self, rel, data):
        self.json(f"assets/{MOD}/{rel}", data)

    def data(self, rel, data):
        self.json(f"data/{MOD}/{rel}", data)


def loot(out, block_id):
    out.data(f"loot_table/blocks/{block_id}.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0,
                   "entries": [{"type": "minecraft:item", "name": rl(block_id)}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]})


def slab_loot(out, block_id):
    out.data(f"loot_table/blocks/{block_id}.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{
            "type": "minecraft:item", "name": rl(block_id),
            "functions": [{"function": "minecraft:set_count", "add": False, "count": 2.0,
                           "conditions": [{"condition": "minecraft:block_state_property", "block": rl(block_id),
                                           "properties": {"type": "double"}}]},
                          {"function": "minecraft:explosion_decay"}]}]}]})


def shapeless(out, rid, ingredients, result, count=1):
    out.data(f"recipe/{rid}.json", {
        "type": "minecraft:crafting_shapeless", "category": "building",
        "ingredients": ingredients, "result": {"count": count, "id": result}})


def dye_recipes(out, kind_tag, dye, result, group):
    """One plus one dye, and eight around one dye, like terracotta."""
    item = {"tag": kind_tag}
    colour = {"tag": f"c:dyes/{dye}"}
    shapeless(out, f"dyeing/{result.split(':')[1]}", [colour, item], result)
    out.data(f"recipe/dyeing/{result.split(':')[1]}_8.json", {
        "type": "minecraft:crafting_shaped", "category": "building", "group": group,
        "pattern": ["###", "#D#", "###"], "key": {"#": item, "D": colour},
        "result": {"count": 8, "id": result}})


def splashing(out, rid, ingredient_tag, result):
    out.data(f"recipe/splashing/{rid}.json", {
        "type": "create:splashing", "ingredients": [{"tag": ingredient_tag}], "results": [{"id": result}]})


def stonecutting(out, rid, ingredient, result, count):
    out.data(f"recipe/stonecutting/{rid}.json", {
        "type": "minecraft:stonecutting", "ingredient": ingredient, "result": {"count": count, "id": result}})


def tag(out, kind, rel, values):
    out.json(f"data/{kind}/tags/{rel}.json", {"replace": False, "values": values})


def main(root):
    out = Out(root)
    lang_path = Path(root) / f"assets/{MOD}/lang/en_us.json"
    lang = json.loads(lang_path.read_text(encoding="utf-8"))
    pickaxe = []
    versions = [None] + DYES

    # --- tools -------------------------------------------------------------------
    for tool, title in TOOLS.items():
        base_state = json.loads((Path(root) / f"assets/{MOD}/blockstates/{tool}.json").read_text(encoding="utf-8"))
        base_item = json.loads((Path(root) / f"assets/{MOD}/models/item/{tool}.json").read_text(encoding="utf-8"))
        all_ids = []
        for dye in versions:
            block_id = vid(dye, tool)
            all_ids.append(rl(block_id))
            texture = rl("block/brass" if dye is None else f"block/painted_steel_{dye}")
            # The tool is drawn in code; the block model only gives the breaking particles.
            out.assets(f"models/block/{block_id}.json", {"textures": {"particle": texture}})
            state = {"variants": {k: dict(v, model=rl(f"block/{block_id}")) for k, v in base_state["variants"].items()}}
            out.assets(f"blockstates/{block_id}.json", state)
            item = dict(base_item, textures={"particle": texture})
            out.assets(f"models/item/{block_id}.json", item)
            loot(out, block_id)
            if dye is not None:
                lang[f"block.{MOD}.{block_id}"] = f"{name(dye)} {title}"
                dye_recipes(out, f"{MOD}:{KIND_TAGS[tool]}", dye, rl(block_id), f"{MOD}:dyed_{tool}")
        pickaxe += all_ids
        paint_tooltip(lang, tool)
        tag(out, MOD, f"item/{KIND_TAGS[tool]}", all_ids)
        tag(out, MOD, f"item/painted_{KIND_TAGS[tool]}", all_ids[1:])
        splashing(out, f"washing_{tool}", f"{MOD}:painted_{KIND_TAGS[tool]}", rl(tool))

    # --- dark steel and polished iron plating ------------------------------------------
    # The tools' other two metals as blocks, with the same textures (no recipes yet).
    for block_id, texture, title in [("dark_steel_plating", "dark_steel", "Dark Steel Plating"),
                                     ("polished_iron_plating", "polished_iron", "Polished Iron Plating")]:
        out.assets(f"models/block/{block_id}.json",
                   {"parent": "minecraft:block/cube_all", "textures": {"all": rl(f"block/{texture}")}})
        out.assets(f"blockstates/{block_id}.json", {"variants": {"": {"model": rl(f"block/{block_id}")}}})
        out.assets(f"models/item/{block_id}.json", {"parent": rl(f"block/{block_id}")})
        loot(out, block_id)
        lang[f"block.{MOD}.{block_id}"] = title
        pickaxe.append(rl(block_id))

    # --- brass plating ----------------------------------------------------------------
    # A smooth brass block, bare and in the 16 colours, with the same textures as the tools.
    plating_ids = []
    for dye in versions:
        block_id = vid(dye, "brass_plating")
        plating_ids.append(rl(block_id))
        texture = rl("block/brass" if dye is None else f"block/painted_steel_{dye}")
        out.assets(f"models/block/{block_id}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": texture}})
        out.assets(f"blockstates/{block_id}.json", {"variants": {"": {"model": rl(f"block/{block_id}")}}})
        out.assets(f"models/item/{block_id}.json", {"parent": rl(f"block/{block_id}")})
        loot(out, block_id)
        lang[f"block.{MOD}.{block_id}"] = "Brass Plating" if dye is None else f"{name(dye)} Brass Plating"
        if dye is not None:
            dye_recipes(out, f"{MOD}:brass_platings", dye, rl(block_id), f"{MOD}:dyed_brass_plating")
    pickaxe += plating_ids
    tag(out, MOD, "item/brass_platings", plating_ids)
    tag(out, MOD, "item/painted_brass_platings", plating_ids[1:])
    splashing(out, "washing_brass_plating", f"{MOD}:painted_brass_platings", rl("brass_plating"))
    # Four of Create's Brass Sheets in a square give four Brass Plating.
    out.data("recipe/brass_plating.json", {
        "type": "minecraft:crafting_shaped", "category": "building",
        "pattern": ["##", "##"], "key": {"#": {"tag": "c:plates/brass"}},
        "result": {"count": 4, "id": rl("brass_plating")}})

    # --- plating slabs and stairs (add-metal-slabs-and-stairs) --------------------------
    # Every plating as slab and stairs, in its colour, with its texture; the connected textures
    # come from client.MetalCTModel. Crafted and cut from the plating of the same colour.
    platings = [(vid(dye, "brass_plating"), "brass" if dye is None else f"painted_steel_{dye}",
                 "Brass Plating" if dye is None else f"{name(dye)} Brass Plating") for dye in versions]
    platings += [("dark_steel_plating", "dark_steel", "Dark Steel Plating"),
                 ("polished_iron_plating", "polished_iron", "Polished Iron Plating")]
    slab_ids, stair_ids = [], []
    for block_id, texture, title in platings:
        tex = rl(f"block/{texture}")
        slab, stairs = f"{block_id}_slab", f"{block_id}_stairs"
        slab_ids.append(rl(slab))
        stair_ids.append(rl(stairs))
        faces = {"bottom": tex, "top": tex, "side": tex}
        out.assets(f"models/block/{slab}.json", {"parent": "minecraft:block/slab", "textures": faces})
        out.assets(f"models/block/{slab}_top.json", {"parent": "minecraft:block/slab_top", "textures": faces})
        for suffix, parent in [("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")]:
            out.assets(f"models/block/{stairs}{suffix}.json", {"parent": f"minecraft:block/{parent}", "textures": faces})
        out.assets(f"blockstates/{slab}.json", {"variants": {
            "type=bottom": {"model": rl(f"block/{slab}")},
            "type=top": {"model": rl(f"block/{slab}_top")},
            "type=double": {"model": rl(f"block/{block_id}")}}})
        out.assets(f"blockstates/{stairs}.json", {"variants": stairs_variants(rl(f"block/{stairs}"))})
        out.assets(f"models/item/{slab}.json", {"parent": rl(f"block/{slab}")})
        out.assets(f"models/item/{stairs}.json", {"parent": rl(f"block/{stairs}")})
        slab_loot(out, slab)
        loot(out, stairs)
        lang[f"block.{MOD}.{slab}"] = f"{title} Slab"
        lang[f"block.{MOD}.{stairs}"] = f"{title} Stairs"
        plating = {"item": rl(block_id)}
        out.data(f"recipe/{slab}.json", {
            "type": "minecraft:crafting_shaped", "category": "building", "pattern": ["###"],
            "key": {"#": plating}, "result": {"count": 6, "id": rl(slab)}})
        out.data(f"recipe/{stairs}.json", {
            "type": "minecraft:crafting_shaped", "category": "building", "pattern": ["#  ", "## ", "###"],
            "key": {"#": plating}, "result": {"count": 4, "id": rl(stairs)}})
        stonecutting(out, f"{slab}_from_{block_id}", plating, rl(slab), 2)
        stonecutting(out, f"{stairs}_from_{block_id}", plating, rl(stairs), 1)
    pickaxe += slab_ids + stair_ids

    # --- corrugated sheet ------------------------------------------------------------
    sheet_ids = []
    for dye in versions:
        block_id = vid(dye, "corrugated_sheet")
        sheet_ids.append(rl(block_id))
        texture = rl("block/corrugated_brass" if dye is None else f"block/corrugated_{dye}")
        out.assets(f"models/block/{block_id}.json", {
            "parent": "minecraft:block/cube_column", "textures": {"end": texture, "side": texture}})
        # Like a log: the ribs run along the axis it was placed along.
        out.assets(f"blockstates/{block_id}.json", {"variants": {
            "axis=y": {"model": rl(f"block/{block_id}")},
            "axis=z": {"model": rl(f"block/{block_id}"), "x": 90},
            "axis=x": {"model": rl(f"block/{block_id}"), "x": 90, "y": 90}}})
        out.assets(f"models/item/{block_id}.json", {"parent": rl(f"block/{block_id}")})
        loot(out, block_id)
        lang[f"block.{MOD}.{block_id}"] = "Corrugated Brass Sheet" if dye is None else f"{name(dye)} Corrugated Sheet"
        if dye is not None:
            dye_recipes(out, f"{MOD}:corrugated_sheets", dye, rl(block_id), f"{MOD}:dyed_corrugated_sheet")
    pickaxe += sheet_ids
    tag(out, MOD, "item/corrugated_sheets", sheet_ids)
    tag(out, MOD, "item/painted_corrugated_sheets", sheet_ids[1:])
    splashing(out, "washing_corrugated_sheet", f"{MOD}:painted_corrugated_sheets", rl("corrugated_sheet"))

    # --- tread plate -------------------------------------------------------------------
    tread = rl("block/tread_plate")
    out.assets("models/block/tread_plate.json", {"parent": "minecraft:block/cube_all", "textures": {"all": tread}})
    out.assets("models/block/tread_plate_slab.json", {"parent": "minecraft:block/slab",
               "textures": {"bottom": tread, "top": tread, "side": tread}})
    out.assets("models/block/tread_plate_slab_top.json", {"parent": "minecraft:block/slab_top",
               "textures": {"bottom": tread, "top": tread, "side": tread}})
    for suffix in ["", "_inner", "_outer"]:
        out.assets(f"models/block/tread_plate_stairs{suffix}.json", {"parent": f"minecraft:block/{'stairs' if not suffix else suffix[1:] + '_stairs'}",
                   "textures": {"bottom": tread, "top": tread, "side": tread}})
    out.assets("blockstates/tread_plate.json", {"variants": {"": {"model": rl("block/tread_plate")}}})
    out.assets("blockstates/tread_plate_slab.json", {"variants": {
        "type=bottom": {"model": rl("block/tread_plate_slab")},
        "type=top": {"model": rl("block/tread_plate_slab_top")},
        "type=double": {"model": rl("block/tread_plate")}}})
    out.assets("blockstates/tread_plate_stairs.json", {"variants": stairs_variants(rl("block/tread_plate_stairs"))})
    for b in ["tread_plate", "tread_plate_slab", "tread_plate_stairs"]:
        out.assets(f"models/item/{b}.json", {"parent": rl(f"block/{b}")})
    loot(out, "tread_plate")
    slab_loot(out, "tread_plate_slab")
    loot(out, "tread_plate_stairs")
    lang.update({f"block.{MOD}.tread_plate": "Tread Plate", f"block.{MOD}.tread_plate_slab": "Tread Plate Slab",
                 f"block.{MOD}.tread_plate_stairs": "Tread Plate Stairs"})
    pickaxe += [rl("tread_plate"), rl("tread_plate_slab"), rl("tread_plate_stairs")]

    # --- hazard stripes and plates -------------------------------------------------------
    for block_id, texture_name, title in [("hazard_stripes", "hazard_stripes", "Hazard Stripes"),
                                          ("hazard_stripes_red_white", "hazard_stripes_red_white", "Red-White Hazard Stripes")]:
        texture = rl(f"block/{texture_name}")
        # One texture per face, worked out for the whole block, so the stripes run on in the same
        # colour over every edge (made by generate_textures.py: hazard_face). Flipped turns them on
        # the four sides.
        for suffix in ["", "_flipped"]:
            faces = {f: rl(f"block/{texture_name}{suffix}_{f}") for f in ["north", "east", "south", "west", "up", "down"]}
            out.assets(f"models/block/{block_id}{suffix}.json", {
                "parent": "minecraft:block/cube", "textures": {"particle": texture, **faces}})
        out.assets(f"blockstates/{block_id}.json", {"variants": {
            "flipped=false": {"model": rl(f"block/{block_id}")},
            "flipped=true": {"model": rl(f"block/{block_id}_flipped")}}})
        out.assets(f"models/item/{block_id}.json", {"parent": rl(f"block/{block_id}")})
        loot(out, block_id)
        lang[f"block.{MOD}.{block_id}"] = title
        pickaxe.append(rl(block_id))

        plate_id = block_id.replace("stripes", "plate")
        # The plate: a panel 1 pixel thick with a 2-pixel margin to the block's edges; next to the
        # same plate a side joins and its margin closes (HazardPlateBlock works out which). It is
        # built from parts: the middle always, a strip per joined side, a corner piece where two
        # joined sides meet. The texture is laid by position, so stripes run on from plate to plate.
        # Flipped plates use the mirrored stripes (a turned texture would not line up between parts).
        parts = {
            "middle": ([2, 0, 2], [14, 1, 14], {}),
            "north": ([2, 0, 0], [14, 1, 2], {"north": "true"}),
            "south": ([2, 0, 14], [14, 1, 16], {"south": "true"}),
            "west": ([0, 0, 2], [2, 1, 14], {"west": "true"}),
            "east": ([14, 0, 2], [16, 1, 14], {"east": "true"}),
            "north_west": ([0, 0, 0], [2, 1, 2], {"north": "true", "west": "true"}),
            "north_east": ([14, 0, 0], [16, 1, 2], {"north": "true", "east": "true"}),
            "south_west": ([0, 0, 14], [2, 1, 16], {"south": "true", "west": "true"}),
            "south_east": ([14, 0, 14], [16, 1, 16], {"south": "true", "east": "true"}),
        }
        for flipped in [False, True]:
            stripes = rl(f"block/{texture_name}{'_mirrored' if flipped else ''}")
            for part, (lo, hi, _) in parts.items():
                x0, _, z0 = lo
                x1, _, z1 = hi
                # The thin edges show the same pixels as the top's outermost row or column, so the
                # stripes turn over the edge without a jump (owner, 27 October 2026). Along x the row
                # is read as it is; along z the column is read through the stripes' symmetry (the
                # stripes are the same mirrored across their diagonal), since a face cannot read a
                # texture's column directly.
                def col(z_from, z_to, x):
                    if flipped:
                        return [16 - z_from, 15 - x, 16 - z_to, 16 - x]
                    return [z_from, x, z_to, x + 1]
                faces = {
                    "up": {"texture": "#all", "uv": [x0, z0, x1, z1]},
                    "down": {"texture": "#all", "uv": [x0, 16 - z1, x1, 16 - z0], "cullface": "down"},
                    "north": {"texture": "#all", "uv": [x1, z0, x0, z0 + 1]},
                    "south": {"texture": "#all", "uv": [x0, z1 - 1, x1, z1]},
                    "west": {"texture": "#all", "uv": col(z0, z1, x0)},
                    "east": {"texture": "#all", "uv": col(z1, z0, x1 - 1)},
                }
                out.assets(f"models/block/{plate_id}{'_flipped' if flipped else ''}_{part}.json", {
                    "parent": "minecraft:block/block", "textures": {"all": stripes, "particle": stripes},
                    "elements": [{"from": lo, "to": hi, "faces": faces}]})
        # Turned onto the face it sticks to, like a button.
        turns = {"up": {}, "down": {"x": 180}, "north": {"x": 90}, "east": {"x": 90, "y": 90},
                 "south": {"x": 90, "y": 180}, "west": {"x": 90, "y": 270}}
        multipart = []
        for facing, turn in turns.items():
            for flipped in ["false", "true"]:
                for part, (_, _, needs) in parts.items():
                    model = rl(f"block/{plate_id}{'_flipped' if flipped == 'true' else ''}_{part}")
                    multipart.append({"when": {"facing": facing, "flipped": flipped, **needs},
                                      "apply": dict(model=model, **turn)})
        out.assets(f"blockstates/{plate_id}.json", {"multipart": multipart})
        out.assets(f"models/item/{plate_id}.json", {"parent": rl(f"block/{plate_id}_middle")})
        loot(out, plate_id)
        lang[f"block.{MOD}.{plate_id}"] = title.replace("Stripes", "Plate")
        pickaxe.append(rl(plate_id))
        stonecutting(out, f"{plate_id}_from_{block_id}", {"item": rl(block_id)}, rl(plate_id), 4)

    shapeless(out, "hazard_stripes", [{"item": rl("yellow_brass_plating")}, {"tag": "c:dyes/black"}], rl("hazard_stripes"))
    shapeless(out, "hazard_stripes_red_white", [{"item": rl("white_brass_plating")}, {"tag": "c:dyes/red"}],
              rl("hazard_stripes_red_white"))

    # --- grating ---------------------------------------------------------------------------
    grating = rl("block/grating")
    out.assets("models/block/grating.json", {"parent": "minecraft:block/cube_all", "render_type": "minecraft:cutout",
                                             "textures": {"all": grating}})
    for suffix, parent in [("_slab", "slab"), ("_slab_top", "slab_top")]:
        out.assets(f"models/block/grating{suffix}.json", {"parent": f"minecraft:block/{parent}", "render_type": "minecraft:cutout",
                   "textures": {"bottom": grating, "top": grating, "side": grating}})
    out.assets("blockstates/grating.json", {"variants": {"": {"model": rl("block/grating")}}})
    out.assets("blockstates/grating_slab.json", {"variants": {
        "type=bottom": {"model": rl("block/grating_slab")},
        "type=top": {"model": rl("block/grating_slab_top")},
        "type=double": {"model": rl("block/grating")}}})
    for b in ["grating", "grating_slab"]:
        out.assets(f"models/item/{b}.json", {"parent": rl(f"block/{b}")})
    loot(out, "grating")
    slab_loot(out, "grating_slab")
    lang.update({f"block.{MOD}.grating": "Grating", f"block.{MOD}.grating_slab": "Grating Slab"})
    pickaxe += [rl("grating"), rl("grating_slab")]

    # --- stonecutting from Create's Block of Industrial Iron ----------------------------------
    iron = {"item": "create:industrial_iron_block"}
    for result, count in [("tread_plate", 1), ("tread_plate_slab", 2), ("tread_plate_stairs", 1),
                          ("grating", 1), ("grating_slab", 2)]:
        stonecutting(out, f"{result}_from_industrial_iron_block", iron, rl(result), count)
    stonecutting(out, "tread_plate_slab_from_tread_plate", {"item": rl("tread_plate")}, rl("tread_plate_slab"), 2)
    stonecutting(out, "tread_plate_stairs_from_tread_plate", {"item": rl("tread_plate")}, rl("tread_plate_stairs"), 1)
    # The brass Corrugated Sheet is cut from Brass Plating.
    stonecutting(out, "corrugated_sheet_from_brass_plating", {"item": rl("brass_plating")}, rl("corrugated_sheet"), 1)
    stonecutting(out, "grating_slab_from_grating", {"item": rl("grating")}, rl("grating_slab"), 2)

    # --- shared tags ---------------------------------------------------------------------------
    tag(out, "minecraft", "block/mineable/pickaxe", pickaxe)
    tag(out, "minecraft", "block/slabs", [rl("tread_plate_slab"), rl("grating_slab")] + slab_ids)
    tag(out, "minecraft", "item/slabs", [rl("tread_plate_slab"), rl("grating_slab")] + slab_ids)
    tag(out, "minecraft", "block/stairs", [rl("tread_plate_stairs")] + stair_ids)
    tag(out, "minecraft", "item/stairs", [rl("tread_plate_stairs")] + stair_ids)

    lang_path.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("wrote", out.count, "files and the language file")


PAINT_CONDITION = "When crafted with Dye"
PAINT_BEHAVIOUR = "_Paints_ it. Wash it _bare_ again in a _Water Cauldron_ or with a _Fan_."


def paint_tooltip(lang, tool):
    """Adds the painting line to a tool's Shift tooltip as its last condition, once."""
    key = f"block.{MOD}.{tool}.tooltip"
    n = 1
    while f"{key}.condition{n}" in lang:
        if lang[f"{key}.condition{n}"] == PAINT_CONDITION:
            lang[f"{key}.behaviour{n}"] = PAINT_BEHAVIOUR
            return
        n += 1
    lang[f"{key}.condition{n}"] = PAINT_CONDITION
    lang[f"{key}.behaviour{n}"] = PAINT_BEHAVIOUR


def stairs_variants(model):
    """Vanilla's stair block state, the same for every stair (as in oak_stairs.json)."""
    variants = {}
    ys = {"east": 0, "south": 90, "west": 180, "north": 270}
    for facing, y in ys.items():
        for half in ["bottom", "top"]:
            for shape in ["straight", "inner_left", "inner_right", "outer_left", "outer_right"]:
                m = model if shape == "straight" else model + ("_inner" if shape.startswith("inner") else "_outer")
                rot = y
                if half == "bottom" and shape in ("inner_left", "outer_left"):
                    rot = y - 90
                if half == "top" and shape in ("inner_right", "outer_right"):
                    rot = y + 90
                v = {"model": m}
                if half == "top":
                    v["x"] = 180
                rot %= 360
                if rot:
                    v["y"] = rot
                if half == "top" or rot:
                    v["uvlock"] = True
                variants[f"facing={facing},half={half},shape={shape}"] = v
    return variants


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "src/main/resources")
