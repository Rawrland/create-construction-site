"""
Writes the test stations: two data packs for the game started from the development setup.

- resourcepacks/test_stations             the Create stations; build them with
                                          /function create_construction_site:test/build
- resourcepacks/test_stations_aeronautics the Create Aeronautics stations; build them with
                                          /function create_construction_site:test/build_aeronautics
                                          (only offered when Create Aeronautics is installed)

Each station is a structure file (the machine, its material and its floor) and a reset function.
`test/build` places every station in a row in front of the player (toward +z), each with a panel
in front of it: a command block with a reset button and a sign. The reset button runs the
station's reset function from the command block: it removes everything that moves in the
station's area (contraptions, glue, items, falling blocks), clears the area and places the
structure again. Pressing it also prints in chat what to do and what should happen.

All stations share one reset function, `test/reset`; each button passes its station's name and
size to it. So only three commands show up: test/build, test/build_aeronautics and test/reset.
The chat texts are kept in the world (command storage) by the build commands.

Machines run on a Creative Motor through a Clutch. A lever on top of the clutch starts and stops
them (lever on = clutch powered = stopped; every station starts stopped). Where a direction can be
wrong or should be changed, a lever on top of a Gearshift reverses it. Small signs on the clutch
and the gearshift say which lever is which.

The structure writer and the tool helpers are shared with the Ponder script
(tools/ponder/generate_schematics.py).

Run:  python generate_test_stations.py <src/main/resources folder>
"""
import sys
from pathlib import Path

sys.dont_write_bytecode = True  # no __pycache__ folder next to the Ponder script
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "ponder"))
from generate_schematics import (  # noqa: E402
    MOD, TAG_COMPOUND, TAG_STRING, Byte, Int, List, Structure,
    blade_nbt, bucket_nbt, dump_body, joined_blade, joined_bucket_row,
)

PACK_FORMAT = 48  # data packs for Minecraft 1.21.1

GAP = 3           # blocks between two stations
STATION_Z = 3     # stations start this far in front of the player (+z)
PANEL_Z = 1       # the command block of each panel


# --- small builders ------------------------------------------------------------------------


def text(line):
    return '{"text":"%s"}' % line.replace('"', '\\"')


def sign_nbt(lines):
    lines = (list(lines) + ["", "", "", ""])[:4]
    face = {"messages": List(TAG_STRING, [text(l) for l in lines]),
            "color": "black", "has_glowing_text": Byte(0)}
    return {"id": "minecraft:sign", "is_waxed": Byte(1), "front_text": face,
            "back_text": {"messages": List(TAG_STRING, [text("")] * 4), "color": "black",
                          "has_glowing_text": Byte(0)}}


def wall_sign(s, pos, facing, lines):
    s.set(pos, "minecraft:oak_wall_sign", sign_nbt(lines), facing=facing)


def chest(s, pos, facing, items):
    """items: list of (item id, count)."""
    slots = List(TAG_COMPOUND, [{"Slot": Byte(i), "id": item, "count": Int(count)}
                                for i, (item, count) in enumerate(items)])
    s.set(pos, "minecraft:chest", {"id": "minecraft:chest", "Items": slots}, facing=facing, type="single")


def floor(s):
    """Light concrete checker floor over the whole station (nothing in it a tool can take)."""
    sx, _, sz = s.size
    for x in range(sx):
        for z in range(sz):
            s.set((x, 0, z), "minecraft:white_concrete" if (x + z) % 2 == 0 else "minecraft:light_gray_concrete")


def start_lever(s, clutch, axis, sign_pos=None, sign_facing="north"):
    """A clutch with a lever on top: lever on = stopped (the station starts stopped)."""
    x, y, z = clutch
    s.set(clutch, "create:clutch", axis=axis, powered="true")
    s.set((x, y + 1, z), "minecraft:lever", face="floor", facing="north", powered="true")
    if sign_pos:
        wall_sign(s, sign_pos, sign_facing, ["START / STOP", "lever on top:", "on = stopped", "off = running"])


def reverse_lever(s, gearshift, axis, sign_pos=None, sign_facing="north", reversed_=False):
    x, y, z = gearshift
    on = "true" if reversed_ else "false"
    s.set(gearshift, "create:gearshift", axis=axis, powered=on)
    s.set((x, y + 1, z), "minecraft:lever", face="floor", facing="north", powered=on)
    if sign_pos:
        wall_sign(s, sign_pos, sign_facing, ["REVERSE", "lever on top:", "flip to turn", "the other way"])


def gantry(s, y, x0, carriage_x):
    """
    A Gantry Shaft facing west along x at height y and z 4, from x0 to 8, with a carriage hanging
    under it at carriage_x, and the drive east of it: shaft (x 9), clutch with start lever (x 10),
    shaft (x 11), gearshift with reverse lever (x 12), Creative Motor facing west (x 13).
    The carriage runs west to the shaft's end and stops there by itself.
    The shafts between keep the levers' redstone away from the gantry shaft (a powered gantry
    shaft does not move its carriage).
    """
    for x in range(x0, 9):
        part = "start" if x == 8 else ("end" if x == x0 else "middle")
        s.set((x, y, 4), "create:gantry_shaft", facing="west", part=part, powered="false")
    s.set((carriage_x, y - 1, 4), "create:gantry_carriage", facing="down", axis_along_first="false")
    s.set((9, y, 4), "create:shaft", axis="x")
    start_lever(s, (10, y, 4), "x", sign_pos=(10, y, 3))
    s.set((11, y, 4), "create:shaft", axis="x")
    reverse_lever(s, (12, y, 4), "x", sign_pos=(12, y, 3))
    s.set((13, y, 4), "create:creative_motor", facing="west")


# --- the Create stations -------------------------------------------------------------------
# Every function returns (structure, title for the sign, what to do and expect in chat).
# Tools face west (open side or working face toward -x) unless said otherwise.


def bucket_digging():
    s = Structure(14, 6, 9)
    floor(s)
    gantry(s, 3, 5, 7)
    s.set((7, 1, 4), f"{MOD}:excavator_bucket", bucket_nbt(), facing="west", joined="false")
    s.fill(4, 1, 4, 4, 2, 4, "minecraft:dirt")
    return s, ["Bucket:", "digging"], [
        "Flip the START lever off (on top of the clutch).",
        "Expect: the carriage runs to the end of the shaft, the bucket digs the lower dirt block, "
        "the dirt on top drops into the hole, and the bucket shows its load.",
    ]


def bucket_emptying():
    s = Structure(9, 6, 10)
    floor(s)
    joined_bucket_row(s, 4, 3, 3, 3, content="minecraft:dirt")
    s.glue(4, 3, 3, 4, 3, 5)
    s.set((4, 3, 6), "create:mechanical_bearing", facing="north")
    start_lever(s, (4, 3, 7), "z", sign_pos=(3, 3, 7), sign_facing="west")
    s.set((4, 3, 8), "create:creative_motor", facing="north")
    return s, ["Bucket:", "emptying"], [
        "Flip the START lever off (on the clutch behind the bearing). The bearing turns the full bucket over.",
        "Expect: it starts to spill at about 20 degrees, the last block leaves at about 45, newest first; "
        "the dirt falls and lands as a heap. Flip the lever back to stop.",
    ]


def blade_gaps():
    s = Structure(14, 6, 9)
    floor(s)
    s.fill(0, 1, 2, 8, 1, 6, "minecraft:dirt")
    for z in range(3, 6):
        del s.blocks[(3, 1, z)]
    gantry(s, 4, 0, 7)
    joined_blade(s, 7, 2, 3, 1, 3)
    s.glue(7, 2, 3, 7, 2, 5)
    s.fill(5, 2, 3, 5, 2, 5, "minecraft:dirt")
    return s, ["Blade: pile", "and gaps"], [
        "Flip the START lever off.",
        "Expect: the blade takes the row of dirt onto its pile and carries it; over the trench the "
        "three blocks drop in and fill it; the blade drives on empty.",
    ]


def blade_full():
    s = Structure(14, 5, 9)
    floor(s)
    gantry(s, 3, 0, 7)
    joined_blade(s, 7, 1, 3, 1, 2, stored_per_block=1)
    s.glue(7, 1, 3, 7, 1, 4)
    s.fill(4, 1, 3, 5, 1, 4, "minecraft:dirt")
    return s, ["Blade: full,", "backing away"], [
        "The blade is already full. Flip the START lever off; once it has passed the dirt, "
        "flip the REVERSE lever.",
        "Expect: the dirt it meets is moved off its nearer end and forms a ridge; "
        "backing away, the blade leaves its pile on the ground as it was.",
    ]


def gantry_into_stone():
    s = Structure(14, 5, 9)
    floor(s)
    gantry(s, 3, 0, 7)
    joined_blade(s, 7, 1, 3, 1, 3)
    s.glue(7, 1, 3, 7, 1, 5)
    s.fill(5, 1, 3, 5, 1, 5, "minecraft:dirt")
    s.set((3, 1, 4), "minecraft:stone")
    return s, ["Gantry", "into stone"], [
        "Flip the START lever off. When the gantry has stopped, break the stone.",
        "Expect: the blade takes the dirt, the gantry stops at the stone and waits; "
        "once the stone is broken it moves on.",
    ]


def bearing_through_stone():
    """
    A bearing facing up at (4, 2, 4) turns an arm at y 3 with a bucket at its end (7, 3, 4), open
    side north. On the bucket's circle: stone at (4, 3, 1) (north), then dirt at (1, 3, 4) (west).
    Fed from the east through a gearbox: clutch (start), shaft, gearshift (reverse), motor.
    """
    s = Structure(9, 5, 9)
    floor(s)
    s.set((4, 1, 4), "create:gearbox", axis="z")
    s.set((4, 2, 4), "create:mechanical_bearing", facing="up")
    start_lever(s, (5, 1, 4), "x", sign_pos=(5, 1, 3))
    s.set((6, 1, 4), "create:shaft", axis="x")
    reverse_lever(s, (7, 1, 4), "x", sign_pos=(7, 1, 3))
    s.set((8, 1, 4), "create:creative_motor", facing="west")
    s.fill(4, 3, 4, 6, 3, 4, "create:andesite_casing")
    s.set((7, 3, 4), f"{MOD}:excavator_bucket", bucket_nbt(), facing="north", joined="false")
    s.glue(4, 3, 4, 7, 3, 4)
    s.fill(4, 1, 1, 4, 2, 1, "minecraft:stone_bricks")
    s.set((4, 3, 1), "minecraft:stone")
    s.fill(1, 1, 4, 1, 3, 4, "minecraft:dirt")
    return s, ["Bearing", "through stone"], [
        "Flip the START lever off. The bucket must lead with its open side: if it swings back first, "
        "flip the REVERSE lever.",
        "Expect: the bearing keeps turning; the bucket leaves the stone alone and digs the top "
        "dirt block on the far side.",
    ]


def sizes():
    s = Structure(9, 3, 9)
    floor(s)
    chest(s, (2, 1, 1), "north", [(f"{MOD}:excavator_bucket", 64)] * 2)
    chest(s, (6, 1, 1), "north", [(f"{MOD}:bulldozer_blade", 64)] * 2)
    wall_sign(s, (2, 1, 0), "north", ["Buckets"])
    wall_sign(s, (6, 1, 0), "north", ["Blades"])
    return s, ["Sizes"], [
        "Take buckets and blades from the chests and place them next to each other.",
        "Expect: they join into one tool. A bucket is 1 high and up to 3 wide, 2 high and up to 5, "
        "or 3 high and up to 7; a blade is up to 3 high and 9 wide.",
    ]


def dump_body_sizes():
    s = Structure(9, 3, 9)
    floor(s)
    chest(s, (2, 1, 1), "north", [(f"{MOD}:dump_body", 64)] * 7)
    chest(s, (6, 1, 1), "north", [("create:wrench", 1), ("create:goggles", 1)])
    wall_sign(s, (2, 1, 0), "north", ["Dump Bodies"])
    wall_sign(s, (6, 1, 0), "north", ["Wrench and", "Goggles"])
    return s, ["Dump Body:", "sizes"], [
        "Take Dump Bodies from the chest and place them as boxes. Place one on a full side of a body (also the top); "
        "sneak to place just one. Use the Wrench on a side; wear the Goggles.",
        "Expect: touching blocks join into one box, up to 7 wide, 15 long and 4 high (16 in a row does not join "
        "as one). Placed on a full side, the whole layer appears at once, if the bigger size is allowed. A new body is closed on all sides (locking bars). The Wrench switches a side to a tailgate "
        "(hinges on top, latches at the bottom) and back. Tailgate and wall edges are closed, nothing to see "
        "through.",
    ]


def dump_body_loading():
    """
    The emptying station's full bucket on its bearing, over an empty dump body 5 wide (x 2..6) and 3 long,
    so the load lands in it whichever way the bearing turns.
    """
    s = Structure(9, 6, 10)
    floor(s)
    joined_bucket_row(s, 4, 3, 3, 3, content="minecraft:dirt")
    s.glue(4, 3, 3, 4, 3, 5)
    s.set((4, 3, 6), "create:mechanical_bearing", facing="north")
    start_lever(s, (4, 3, 7), "z", sign_pos=(3, 3, 7), sign_facing="west")
    s.set((4, 3, 8), "create:creative_motor", facing="north")
    dump_body(s, 2, 1, 3, 5, 1, 3)
    return s, ["Dump Body:", "loading"], [
        "Flip the START lever off. The bearing tips the full bucket over the Dump Body.",
        "Expect: the dirt falls into the Dump Body and is shown inside it; nothing lands on top of it or beside it. "
        "Goggles show 3 of 15 blocks.",
    ]


def dump_body_on_bearing(gates, title, how):
    """
    A full dump body 3 by 3 by 1 at x 3..5, y 3, z 3..5, glued, on a Mechanical Bearing with a level
    axle (facing north at x 4, z 6). Clutch with start lever, shaft, gearshift with reverse lever, motor.
    """
    def make():
        s = Structure(9, 6, 12)
        floor(s)
        dump_body(s, 3, 3, 3, 3, 1, 3, content="minecraft:dirt", gates=gates)
        s.glue(3, 3, 3, 5, 3, 5)
        s.set((4, 3, 6), "create:mechanical_bearing", facing="north")
        start_lever(s, (4, 3, 7), "z", sign_pos=(3, 3, 7), sign_facing="west")
        s.set((4, 3, 8), "create:shaft", axis="z")
        reverse_lever(s, (4, 3, 9), "z", sign_pos=(3, 3, 9), sign_facing="west")
        s.set((4, 3, 10), "create:creative_motor", facing="north")
        return s, title, how
    return make


dump_body_tipping = dump_body_on_bearing(
    ("north", "east", "south", "west"), ["Dump Body:", "tipping"], [
        "All four sides of this Dump Body were opened with the Wrench. Flip the START lever off; the bearing "
        "tips it. Flip REVERSE to tip it the other way.",
        "Expect: from about 20 degrees the lower side's tailgate swings open, hanging straight down, and the dirt "
        "streams out from that side, top first; by about 45 degrees it is empty. Only the lowest side opens, and it stays "
        "open the whole time the body leans toward it.",
    ])

dump_body_closed_side = dump_body_on_bearing(
    ("north", "east", "south"), ["Dump Body:", "closed side"], [
        "The west side is closed (locking bar), the others were opened with the Wrench. Flip the START lever "
        "off; if the body tips to the east instead, flip REVERSE.",
        "Expect: tipped toward the west, nothing leaves and no gate opens, until the body is nearly upside down: "
        "then it empties through its open top. Tipped toward the east, it empties over the east side.",
    ])


CREATE_STATIONS = {
    "bucket_digging": bucket_digging,
    "bucket_emptying": bucket_emptying,
    "blade_gaps": blade_gaps,
    "blade_full": blade_full,
    "gantry_into_stone": gantry_into_stone,
    "bearing_through_stone": bearing_through_stone,
    "sizes": sizes,
    "dump_body_sizes": dump_body_sizes,
    "dump_body_loading": dump_body_loading,
    "dump_body_tipping": dump_body_tipping,
    "dump_body_closed_side": dump_body_closed_side,
}


# --- the Create Aeronautics stations -------------------------------------------------------


def vehicle_station(tool_name):
    """
    The owner's vehicle (as in the Ponder scene), glued and with a Physics Assembler on top,
    5 blocks further east, and a heap of dirt in front of it. Frame at y 2: chain drives x 8..12,
    wheel mounts at x 8 and 11 (Suspension Strength 100), tool at x 7 (level with the frame).
    On the back: Red Portable Engine (x 10), gearshift with reverse lever (x 11), gearbox (x 12).
    The owner's test drove backwards without the gearshift, so it starts reversed (lever on).
    """
    s = Structure(15, 5, 9)
    floor(s)
    parts = {8: "start", 9: "middle", 10: "middle", 11: "middle", 12: "end"}
    for x, part in parts.items():
        axis = "y" if x == 12 else "z"
        s.set((x, 2, 4), "create:encased_chain_drive", axis=axis, axis_along_first="true", part=part)
    # ScrollValue: the mount's Suspension Strength (5..180; owner's choice 100, 17 October 2026).
    tire = {"id": "offroad:wheel_mount", "CurrentStack": {"id": "offroad:tire", "count": Int(1)},
            "ScrollValue": Int(100)}
    for x in (8, 11):
        s.set((x, 2, 3), "offroad:wheel_mount", dict(tire), facing="north")
        s.set((x, 2, 5), "offroad:wheel_mount", dict(tire), facing="south")
    s.set((12, 3, 4), "create:gearbox", axis="z")
    # Between the engine and the gearbox: a gearshift whose lever turns the drive the other way.
    reverse_lever(s, (11, 3, 4), "x", sign_pos=(11, 3, 3), reversed_=True)
    s.set((10, 3, 4), "simulated:red_portable_engine", facing="east", lit="false")
    s.set((9, 3, 4), "simulated:physics_assembler", face="floor", facing="west")
    if tool_name == "excavator_bucket":
        joined_bucket_row(s, 7, 2, 3, 3)
    else:
        joined_blade(s, 7, 2, 3, 1, 3)
    s.glue(7, 2, 3, 12, 4, 5)
    s.fill(1, 1, 2, 4, 2, 6, "minecraft:dirt")
    chest(s, (14, 1, 1), "north", [("minecraft:coal", 64)])
    return s


def vehicle_bucket():
    return vehicle_station("excavator_bucket"), ["Vehicle", "with bucket"], [
        "Hold right-click on the Physics Assembler and pull its lever to assemble the vehicle "
        "(it is already glued). Put coal from the chest into the engine and drive into the dirt. "
        "If it drives away from the dirt, flip the REVERSE lever on the gearshift behind the engine.",
        "Expect: the bucket digs as on Create's machines and shows its load.",
    ]


def vehicle_blade():
    return vehicle_station("bulldozer_blade"), ["Vehicle", "with blade"], [
        "Hold right-click on the Physics Assembler and pull its lever to assemble the vehicle "
        "(it is already glued). Put coal from the chest into the engine and drive into the dirt. "
        "If it drives away from the dirt, flip the REVERSE lever on the gearshift behind the engine.",
        "Expect: the blade pushes the dirt as on Create's machines.",
    ]


def rope():
    s = Structure(9, 4, 9)
    floor(s)
    joined_bucket_row(s, 4, 1, 3, 3)
    s.set((1, 1, 4), "minecraft:oak_log", axis="x")
    s.set((1, 2, 4), "simulated:rope_connector", facing="up", axis_along_first="false")
    chest(s, (7, 1, 1), "north", [("simulated:rope_coupling", 16)])
    return s, ["Bucket:", "rope"], [
        "Take the Rope Coupling from the chest. Use it on the bucket's middle tooth, then on the "
        "Rope Connector on the log.",
        "Expect: the rope ties to the tooth ring; each block of the bucket's width takes one rope; "
        "a plain block does not take a rope.",
    ]


def dump_body_swivel():
    """
    A full dump body 3 by 3 by 1 at x 3..5, y 3, z 4..6, glued, all sides open, held by a Create
    Aeronautics Swivel Bearing facing north at x 4, z 7. Its cog is driven by a cogwheel beside it
    (x 5), fed from behind: clutch with start lever, shaft, gearshift with reverse lever, motor.
    The levers' redstone does not reach the bearing (powered, it would turn freely).
    """
    s = Structure(9, 6, 12)
    floor(s)
    dump_body(s, 3, 3, 4, 3, 1, 3, content="minecraft:dirt", gates=("north", "east", "south", "west"))
    s.glue(3, 3, 4, 5, 3, 6)
    s.set((4, 3, 7), "simulated:swivel_bearing", facing="north", powered="false", assembled="false")
    s.set((5, 3, 7), "create:cogwheel", axis="z")
    start_lever(s, (5, 3, 8), "z", sign_pos=(6, 3, 8), sign_facing="east")
    s.set((5, 3, 9), "create:shaft", axis="z")
    reverse_lever(s, (5, 3, 10), "z", sign_pos=(6, 3, 10), sign_facing="east")
    s.set((5, 3, 11), "create:creative_motor", facing="north")
    s.fill(4, 1, 7, 5, 2, 7, "create:andesite_casing")
    return s, ["Dump Body:", "Swivel Bearing"], [
        "Flip the START lever off: the Create Aeronautics Swivel Bearing makes the glued Dump Body a simulated "
        "contraption and tips it (all sides were opened with the Wrench). Flip REVERSE to tip it the other way.",
        "Expect: as on Create's bearing: from about 20 degrees the low side's tailgate opens, the dirt slides and "
        "streams out from that side, and the heap grows away from the body.",
    ]


AERONAUTICS_STATIONS = {
    "vehicle_bucket": vehicle_bucket,
    "vehicle_blade": vehicle_blade,
    "rope": rope,
    "dump_body_swivel": dump_body_swivel,
}


# --- the data packs ------------------------------------------------------------------------


STORAGE = f"{MOD}:test"  # where `test/build` keeps each station's chat text


def chat_text(lines, color_first="gold"):
    """The chat text of a station, as a JSON text component."""
    parts = []
    for i, line in enumerate(lines):
        if i:
            parts.append('{"text":"\\n"}')
        parts.append('{"text":"%s","color":"%s"}' % (line.replace('"', '\\"'), color_first if i == 0 else "white"))
    return "[%s]" % ",".join(parts)


def snbt_string(value):
    """A single-quoted SNBT string (the JSON inside keeps its double quotes)."""
    return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"


def write_reset(data):
    """
    The one reset function all stations share. Its button passes the station's name and its box:
    dx/dy/dz for the entities to remove, fx/fy/fz for the far corner of the area to clear. The
    station's corner is (0, -1, 2) from the command block, the same for every station.
    """
    ox, oy, oz = 0, -1, STATION_Z - PANEL_Z
    lines = [
        "# Resets one test station. Run from its command block, with the station's name and box:",
        "#   function create_construction_site:test/reset {station:<name>,dx:..,dy:..,dz:..,fx:..,fy:..,fz:..}",
        "# Written by tools/teststations/generate_test_stations.py: change the script, not this file.",
        f"$execute positioned ~{ox} ~{oy} ~{oz} run kill @e[type=!minecraft:player,dx=$(dx),dy=$(dy),dz=$(dz)]",
        f"$fill ~{ox} ~{oy} ~{oz} ~$(fx) ~$(fy) ~$(fz) minecraft:air",
        f"$execute positioned ~{ox} ~{oy} ~{oz} run kill @e[type=minecraft:item,dx=$(dx),dy=$(dy),dz=$(dz)]",
        f"$place template {MOD}:test/$(station) ~{ox} ~{oy} ~{oz}",
        f'$tellraw @a[distance=..48] {{"storage":"{STORAGE}","nbt":"text.$(station)","interpret":true}}',
    ]
    path = data / "function" / "test" / "reset.mcfunction"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n")


def write_pack(resources, pack, description, stations, build_name, row_z=0, with_reset=False):
    """
    row_z: how much further from the player (+z) this pack's row is built, so both rows fit.
    with_reset: this pack holds the shared reset function (the Create pack, which is always on).
    """
    pz = PANEL_Z + row_z
    root = resources / "resourcepacks" / pack
    data = root / "data" / MOD
    (root / "pack.mcmeta").parent.mkdir(parents=True, exist_ok=True)
    (root / "pack.mcmeta").write_text(
        '{\n  "pack": {\n    "description": "%s",\n    "pack_format": %d\n  }\n}\n' % (description, PACK_FORMAT))
    if with_reset:
        write_reset(data)

    build = [
        "# Builds the stations in a row in front of the player (toward +z, along +x).",
        "# Written by tools/teststations/generate_test_stations.py: change the script, not this file.",
        "gamerule commandBlockOutput false",
    ]
    x = 0
    for name, make in stations.items():
        structure, title, how = make()
        structure.save(data / "structure" / "test" / f"{name}.nbt")
        sx, sy, sz = structure.size
        oy, oz = -1, STATION_Z - PANEL_Z
        box = f"dx:{sx},dy:{sy + 2},dz:{sz},fx:{sx - 1},fy:{oy + sy + 2},fz:{oz + sz - 1}"
        cmd = f"function {MOD}:test/reset {{station:{name},{box}}}"
        sign = [text(l) for l in (title + ["Button: reset"] + ["", "", ""])[:4]]
        build += [
            f"# {name}",
            f"data modify storage {STORAGE} text.{name} set value "
            f"{snbt_string(chat_text([' '.join(title)] + how))}",
            f"fill ~{x} ~ ~{pz - 1} ~{x + sx - 1} ~1 ~{pz} minecraft:air",
            f"setblock ~{x} ~ ~{pz} minecraft:command_block[facing=up]{{Command:\"{cmd}\"}}",
            f"setblock ~{x} ~ ~{pz - 1} minecraft:stone_button[face=wall,facing=north]",
            f"setblock ~{x} ~1 ~{pz} minecraft:oak_sign[rotation=8]"
            f"{{is_waxed:1b,front_text:{{messages:['{sign[0]}','{sign[1]}','{sign[2]}','{sign[3]}']}}}}",
            f"execute positioned ~{x} ~ ~{pz} run {cmd}",
        ]
        x += sx + GAP
    build.append("tellraw @a[distance=..48] " + chat_text([
        "Test stations built.",
        "Each station's button puts it back as it was and tells you in chat what to do and what should happen."],
        "green"))
    path = data / "function" / "test" / f"{build_name}.mcfunction"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(build) + "\n")
    print("wrote", root)


if __name__ == "__main__":
    resources = Path(sys.argv[1] if len(sys.argv) > 1 else "src/main/resources")
    write_pack(resources, "test_stations", "Create: Construction Site - test stations (development only)",
               CREATE_STATIONS, "build", with_reset=True)
    write_pack(resources, "test_stations_aeronautics",
               "Create: Construction Site - Create Aeronautics test stations (development only)",
               AERONAUTICS_STATIONS, "build_aeronautics", row_z=16)
