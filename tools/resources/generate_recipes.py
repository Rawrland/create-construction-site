"""
Recipes for every item that had none in 0.1 (add-recipes), as agreed with the owner on
6 November 2026. The painting, washing, slab, stair, tread plate, grating and hazard recipes
stay in generate_paint_resources.py.

Run:  python generate_recipes.py <src/main/resources folder>
"""
import json
import sys
from pathlib import Path

MOD = "create_construction_site"


def rl(path):
    return f"{MOD}:{path}"


def item(i):
    return {"item": i}


def tag(t):
    return {"tag": t}


IRON_INGOT = tag("c:ingots/iron")
IRON_SHEET = tag("c:plates/iron")
IRON_NUGGET = tag("c:nuggets/iron")
BRASS_INGOT = tag("c:ingots/brass")
BRASS_SHEET = tag("c:plates/brass")
ALLOY = item("create:andesite_alloy")
BRASS_PLATING = item(rl("brass_plating"))


class Out:
    def __init__(self, root):
        self.root = Path(root)
        self.count = 0

    def recipe(self, rid, data):
        path = self.root / f"data/{MOD}/recipe/{rid}.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
        self.count += 1

    def shaped(self, rid, pattern, key, result, count=1):
        self.recipe(rid, {"type": "minecraft:crafting_shaped", "category": "building", "pattern": pattern,
                          "key": key, "result": {"count": count, "id": result}})

    def stonecutting(self, rid, ingredient, result, count):
        self.recipe(f"stonecutting/{rid}", {"type": "minecraft:stonecutting", "ingredient": ingredient,
                                            "result": {"count": count, "id": result}})


def main(root):
    out = Out(root)

    # --- platings ------------------------------------------------------------------------
    # One ingot makes two platings in the stonecutter, as Create's Industrial Iron Block.
    out.stonecutting("brass_plating_from_brass_ingot", BRASS_INGOT, rl("brass_plating"), 2)
    out.stonecutting("polished_iron_plating_from_iron_ingot", IRON_INGOT, rl("polished_iron_plating"), 2)
    # Dark steel only by Create's haunting (a fan through soul fire) of polished iron.
    out.recipe("haunting/dark_steel_plating", {"type": "create:haunting",
                                               "ingredients": [item(rl("polished_iron_plating"))],
                                               "results": [{"id": rl("dark_steel_plating")}]})
    # Corrugated sheet: four brass sheets in a ring, middle and corners empty.
    out.shaped("corrugated_sheet", [" S ", "S S", " S "], {"S": BRASS_SHEET}, rl("corrugated_sheet"), 8)

    # --- tools: one Brass Plating in the middle, one tool block ------------------------------
    out.shaped("excavator_bucket", [" P ", "III"], {"P": BRASS_PLATING, "I": IRON_INGOT}, rl("excavator_bucket"))
    out.shaped("bulldozer_blade", ["A", "P", "S"], {"A": ALLOY, "P": BRASS_PLATING, "S": IRON_SHEET},
               rl("bulldozer_blade"))
    out.shaped("dump_body", ["APA"], {"A": ALLOY, "P": BRASS_PLATING}, rl("dump_body"))

    # --- hydraulics -------------------------------------------------------------------------
    out.shaped("cylinder_mount", ["SAS"], {"S": IRON_SHEET, "A": ALLOY}, rl("cylinder_mount"), 2)
    out.shaped("hydraulic_cylinder", ["P", "T", "I"],
               {"P": BRASS_PLATING, "T": item("create:fluid_tank"), "I": IRON_INGOT}, rl("hydraulic_cylinder"))
    out.shaped("operator_console", ["LRL", "LCL", " T "],
               {"L": item("minecraft:lever"), "R": item("create:redstone_link"),
                "C": item("create:linked_controller"), "T": item("create:controls")}, rl("operator_console"))

    # --- site barriers: wool for the plastic, iron bars for metal stands ---------------------
    orange, white, red, gray = (item(f"minecraft:{c}_wool") for c in ("orange", "white", "red", "gray"))
    out.shaped("traffic_cone", [" O ", " W ", "OOO"], {"O": orange, "W": white}, rl("traffic_cone"))
    out.shaped("delineator", [" W ", " R ", "GGG"], {"W": white, "R": red, "G": gray}, rl("delineator"), 4)
    out.shaped("barrier_board", ["RWR", "B B", "G G"],
               {"R": red, "W": white, "B": item("minecraft:iron_bars"), "G": gray}, rl("barrier_board"), 6)
    out.shaped("site_fence", ["BBB", "BBB", "S S"],
               {"B": item("minecraft:iron_bars"), "S": item("minecraft:stone_slab")}, rl("site_fence"), 6)
    pane, glow = item("minecraft:orange_stained_glass_pane"), item("minecraft:glowstone_dust")
    out.shaped("warning_beacon", [" N ", " P ", "NGN"], {"N": IRON_NUGGET, "P": pane, "G": glow},
               rl("warning_beacon"))
    out.shaped("warning_flasher", [" N ", "NPN", " G "], {"N": IRON_NUGGET, "P": pane, "G": glow},
               rl("warning_flasher"))

    print("wrote", out.count, "recipes")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "src/main/resources")
