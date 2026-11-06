"""
The Cylinder Mount's two block models (pin across x, and turned: pin across z), with the
platings' edges (owner, 6 November 2026): a dark steel base plate framed like Dark Steel
Plating, dark steel ears with an outline and bevel down both sides, and a polished iron pin
with a light and a dark edge along it. Textures from tools/textures/generate_textures.py.

Run:  python generate_cylinder_resources.py <src/main/resources folder>
"""
import json
import sys
from pathlib import Path

MOD = "create_construction_site"
FACES = ("north", "east", "south", "west", "up", "down")


def tex(name):
    return f"{MOD}:block/{name}"


def element(lo, hi, faces):
    return {"from": lo, "to": hi, "faces": {f: faces[f] for f in FACES}}


def face(texture, uv, rotation=0):
    d = {"texture": texture, "uv": uv}
    if rotation:
        d["rotation"] = rotation
    return d


def mount(turned):
    base_side = face("#base", [2, 0, 14, 3])
    base_top = face("#base", [2, 2, 14, 14])
    base = element([2, 0, 2], [14, 3, 14], {"north": base_side, "east": base_side, "south": base_side,
                                            "west": base_side, "up": base_top, "down": base_top})
    wide = face("#ear", [0, 0, 6, 9])       # outline and bevel down both sides
    narrow = face("#ear", [2, 0, 4, 9])     # plain dark steel
    top = face("#ear", [2, 0, 4, 6])
    pin_side = face("#pin", [0, 0, 4, 12], 90)   # light and dark edge along the pin
    pin_flat = face("#pin", [0, 0, 4, 12])
    pin_end = face("#pin", [0, 0, 4, 4])
    if not turned:
        ears = [([3, 3, 5], [5, 12, 11]), ([11, 3, 5], [13, 12, 11])]
        ear_faces = {"north": narrow, "south": narrow, "east": wide, "west": wide, "up": top, "down": top}
        pin = element([2, 6, 6], [14, 10, 10], {"north": pin_side, "south": pin_side, "up": pin_side,
                                                "down": pin_side, "east": pin_end, "west": pin_end})
    else:
        ears = [([5, 3, 3], [11, 12, 5]), ([5, 3, 11], [11, 12, 13])]
        ear_faces = {"north": wide, "south": wide, "east": narrow, "west": narrow, "up": top, "down": top}
        pin = element([6, 6, 2], [10, 10, 14], {"east": pin_side, "west": pin_side, "up": pin_flat,
                                                "down": pin_flat, "north": pin_end, "south": pin_end})
    return {"parent": "minecraft:block/block",
            "textures": {"base": tex("cylinder_mount_base"), "ear": tex("cylinder_eye"),
                         "pin": tex("cylinder_rod"), "particle": tex("dark_steel_fill")},
            "elements": [base] + [element(lo, hi, ear_faces) for lo, hi in ears] + [pin]}


def main(root):
    folder = Path(root) / f"assets/{MOD}/models/block"
    folder.mkdir(parents=True, exist_ok=True)
    for name, turned in (("cylinder_mount", False), ("cylinder_mount_turned", True)):
        (folder / f"{name}.json").write_text(json.dumps(mount(turned), indent=2) + "\n", encoding="utf-8")
    print("wrote the two Cylinder Mount models")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "src/main/resources")
