"""
Writes the showcase scenes: finished-looking setups of the mod's blocks to take screenshots of
(for the mod page). They go into the test stations data pack, so they exist only in the game
started from the development setup and never in the released mod file.

In the game, stand on a flat, empty place and run one of:

    /function create_construction_site:showcase/excavator
    /function create_construction_site:showcase/bulldozer
    /function create_construction_site:showcase/dump_truck
    /function create_construction_site:showcase/site
    /function create_construction_site:showcase/console
    /function create_construction_site:showcase/metal

and the gallery, every block on show in a white photo studio:

    /function create_construction_site:showcase/gallery_tools        the three tools, bare and joined
    /function create_construction_site:showcase/gallery_colours      every colour of each tool
    /function create_construction_site:showcase/gallery_hydraulics   cylinders and the Operator Console
    /function create_construction_site:showcase/gallery_barriers     cones, lamps, posts, boards, fence
    /function create_construction_site:showcase/gallery_blocks       the building blocks

The first scene marks your position as the "studio" (a marker entity). Every scene is built
there, replacing the one before (a 28 x 16 x 28 area toward +x and +z, ground included), the
clock is set to noon with clear weather and stopped, and you are put into spectator mode at the
scene's camera spot, looking at it. Press F1 to hide the hand and the HUD, then F2 for the
screenshot. /gamemode creative brings you back.

    /function create_construction_site:showcase/move_here   moves the studio to where you stand

The scenes need Create only (the hydraulic cylinder on the excavator is decorative).

Run:  python generate_showcase.py <src/main/resources folder>
"""
import sys
import uuid
from pathlib import Path

sys.dont_write_bytecode = True
TOOLS = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOLS / "ponder"))
sys.path.insert(0, str(TOOLS / "teststations"))

from generate_schematics import MOD, Float, Structure, blade_nbt, bucket_nbt, dirt_entry, dump_body_nbt  # noqa: E402
from generate_test_stations import cylinder_mount_nbt  # noqa: E402

SIZE = (28, 16, 28)   # the studio area: x, y (ground at 0), z
PACK = "test_stations"
TAG = "ccs_studio"


def m(name):
    return f"{MOD}:{name}"


def painted(dye, name):
    return m(name) if dye is None else m(f"{dye}_{name}")


def grass(s):
    sx, _, sz = s.size
    for x in range(sx):
        for z in range(sz):
            s.set((x, 0, z), "minecraft:grass_block", snowy="false")


def ground(s, x0, z0, x1, z1, block="minecraft:dirt"):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            s.set((x, 0, z), block)


def box(s, x0, y0, z0, x1, y1, z1, block, **props):
    s.fill(x0, y0, z0, x1, y1, z1, block, **props)


def slab(s, pos, block, kind="bottom"):
    s.set(pos, f"{block}_slab", type=kind, waterlogged="false")


# --- tools, painted, all facing west (working side toward -x) -------------------------------


def bucket_row(s, dye, x, y, z0, width, content=None):
    """A bucket 1 high, `width` blocks along z; its anchor is the block with the highest z."""
    anchor_z = z0 + width - 1
    for i, z in enumerate(range(z0, z0 + width)):
        s.set((x, y, z), painted(dye, "excavator_bucket"),
              bucket_nbt((0, 0, anchor_z - z), 1, width, content, picked_up_at=i + 1), facing="west", joined="true")


def blade(s, dye, x, y0, z0, height, width, stored_bottom=0):
    """A blade; its anchor is the bottom block at the left end (the highest z)."""
    ax, ay, az = x, y0, z0 + width - 1
    for y in range(y0, y0 + height):
        for z in range(z0, z0 + width):
            nbt = blade_nbt((ax - x, ay - y, az - z), height, width, stored_bottom if y == y0 else 0)
            s.set((x, y, z), painted(dye, "bulldozer_blade"), nbt, facing="west", joined="true")


def dump_body(s, dye, x0, y0, z0, sx, sy, sz, content=None):
    props = {f"gate_{side}": "false" for side in ("north", "east", "south", "west")}
    for x in range(x0, x0 + sx):
        for y in range(y0, y0 + sy):
            for z in range(z0, z0 + sz):
                nbt = dump_body_nbt((x0 - x, y0 - y, z0 - z), (sx, sy, sz), content)
                s.set((x, y, z), painted(dye, "dump_body"), nbt, joined="true", **props)


def cylinder(s, name, barrel, barrel_facing, rod, rod_facing, tube):
    """A decorative Hydraulic Cylinder between two linked Cylinder Mounts."""
    link = uuid.uuid5(uuid.NAMESPACE_URL, f"{MOD}/showcase/{name}")
    s.set(barrel, m("cylinder_mount"), cylinder_mount_nbt(link, True, tube, 1.0, driven=False),
          facing=barrel_facing, turned="false")
    s.set(rod, m("cylinder_mount"), cylinder_mount_nbt(link, False, tube, 1.0, driven=False),
          facing=rod_facing, turned="false")


def heap(s, cells, block="minecraft:dirt"):
    """cells: {(x, z): height}"""
    for (x, z), h in cells.items():
        for y in range(1, h + 1):
            s.set((x, y, z), block)


# --- the scenes --------------------------------------------------------------------------
# Each returns (structure, camera position, point the camera looks at), in studio coordinates
# (block corners; ground surface at y 1).

YELLOW = "yellow"
BODY = m("yellow_brass_plating")
STEEL = m("dark_steel_plating")
IRON = "create:industrial_iron_block"


def tracks(s, x0, x1, zs):
    for z in zs:
        box(s, x0, 1, z, x1, 1, z, STEEL)


def excavator():
    s = Structure(*SIZE)
    grass(s)
    # The dig: a dirt patch with a trench and a heap beside it.
    ground(s, 1, 10, 9, 21)
    for x in range(3, 7):
        for z in range(13, 18):
            s.blocks.pop((x, 0, z), None)
            s.set((x, 0, z), "minecraft:air")
    heap(s, {(1, 19): 2, (2, 19): 3, (3, 19): 2, (1, 20): 1, (2, 20): 2, (3, 20): 1, (2, 18): 1, (4, 19): 1})
    # Undercarriage and deck.
    tracks(s, 12, 19, (13, 17))
    box(s, 13, 1, 14, 18, 1, 16, STEEL)
    box(s, 12, 2, 13, 19, 2, 17, BODY)
    # Cab (west end, north side) with glass and the Operator Console inside.
    for pos in [(12, 3, 13), (12, 4, 13), (13, 3, 13), (13, 4, 13), (12, 4, 14)]:
        s.set(pos, "minecraft:glass")
    s.set((12, 3, 14), m("operator_console"), {"id": m("operator_console")}, facing="west")
    box(s, 12, 5, 13, 13, 5, 14, BODY)
    # Engine housing and counterweight.
    box(s, 15, 3, 14, 19, 4, 17, BODY)
    box(s, 19, 3, 13, 19, 3, 17, STEEL)
    for z in (14, 16):
        s.set((20, 3, z), m("hazard_plate"), facing="east", flipped="false",
              north="false", south="false", west="false", east="false")
    box(s, 16, 5, 15, 18, 5, 16, m("grating_slab"), type="bottom", waterlogged="false")
    s.set((19, 5, 17), "minecraft:iron_bars", north="false", south="false", east="false", west="false",
          waterlogged="false")
    s.set((19, 4, 13), m("warning_beacon"), face="floor", facing="south")
    # Boom up and out to the west, stick down, bucket full of dirt.
    for pos in [(14, 3, 15), (13, 4, 15), (12, 5, 15), (11, 6, 15), (10, 7, 15), (9, 7, 15), (8, 7, 15),
                (7, 7, 15), (7, 6, 15), (7, 5, 15), (7, 4, 15)]:
        s.set(pos, IRON)
    bucket_row(s, YELLOW, 6, 4, 14, 3, content="minecraft:dirt")
    # The boom cylinder: barrel on the housing, rod under the boom.
    cylinder(s, "excavator_boom", (15, 5, 15), "up", (10, 6, 15), "down", 2.6)
    return s, (3.5, 7.5, 30.0), (12.0, 4.0, 15.5)


def bulldozer():
    s = Structure(*SIZE)
    grass(s)
    ground(s, 4, 12, 26, 18, "minecraft:coarse_dirt")
    tracks(s, 12, 19, (13, 17))
    box(s, 13, 1, 14, 18, 1, 16, STEEL)
    box(s, 13, 2, 13, 19, 3, 17, BODY)
    # Cab at the back.
    for z in range(14, 17):
        for y in (4, 5):
            s.set((16, y, z), "minecraft:glass")
            s.set((18, y, z), "minecraft:glass")
    for x in (16, 17, 18):
        for y in (4, 5):
            s.set((x, y, 14), "minecraft:glass")
            s.set((x, y, 16), "minecraft:glass")
    box(s, 16, 6, 14, 18, 6, 16, BODY)
    s.set((14, 4, 16), "minecraft:iron_bars", north="false", south="false", east="false", west="false",
          waterlogged="false")
    s.set((14, 5, 16), "minecraft:iron_bars", north="false", south="false", east="false", west="false",
          waterlogged="false")
    s.set((18, 7, 15), m("warning_beacon"), face="floor", facing="south")
    # Push arms and the blade, two high and five wide, with dirt riding in front of it.
    for z in (13, 17):
        s.set((12, 2, z), IRON)
    blade(s, YELLOW, 11, 1, 13, 2, 5, stored_bottom=2)
    heap(s, {(x, z): 1 for x in range(7, 11) for z in range(13, 18)})
    heap(s, {(8, 14): 2, (8, 15): 2, (8, 16): 2, (9, 15): 2, (9, 14): 2, (9, 16): 2, (8, 15): 3})
    heap(s, {(5, 15): 1, (6, 14): 1, (6, 16): 1})
    return s, (4.0, 6.0, 27.0), (12.5, 2.5, 15.5)


def dump_truck():
    s = Structure(*SIZE)
    grass(s)
    ground(s, 2, 11, 25, 19, "minecraft:coarse_dirt")
    # Wheels, chassis, cab.
    for x in (9, 15, 16):
        for z in (13, 17):
            s.set((x, 1, z), "minecraft:black_concrete")
    box(s, 8, 2, 14, 17, 2, 16, STEEL)
    box(s, 8, 3, 13, 9, 4, 17, BODY)
    box(s, 8, 4, 14, 8, 4, 16, "minecraft:glass")
    for z in (13, 17):
        s.set((9, 4, z), "minecraft:glass")
    box(s, 8, 5, 13, 9, 5, 17, STEEL)
    for z in (14, 16):
        s.set((7, 3, z), m("warning_flasher"), face="wall", facing="west")
    # The dump body, 7 long, 5 wide, full of dirt.
    box(s, 10, 2, 13, 17, 2, 17, STEEL)
    dump_body(s, YELLOW, 11, 3, 13, 7, 1, 5, content="minecraft:dirt")
    # A heap it has dumped before.
    heap(s, {(21, 13): 1, (22, 13): 1, (21, 14): 2, (22, 14): 2, (23, 14): 1, (21, 15): 2, (22, 15): 3,
             (23, 15): 2, (21, 16): 2, (22, 16): 2, (23, 16): 1, (22, 17): 1})
    return s, (3.0, 6.5, 27.0), (13.0, 3.0, 15.5)


def site():
    s = Structure(*SIZE)
    grass(s)
    # A road along x with a centre line, closed on its near lane.
    ground(s, 0, 12, 27, 18, "minecraft:gray_concrete")
    for x in range(0, 28, 3):
        s.set((x, 0, 15), "minecraft:white_concrete")
        s.set((x + 1, 0, 15), "minecraft:white_concrete") if x + 1 < 28 else None
    # Cones closing the near lane, every other one with a lamp; a stacked one at the start.
    for i, x in enumerate(range(4, 24, 2)):
        lamp = "flasher" if i % 2 == 0 else "none"
        s.set((x, 1, 12 + min(i, 3)), m("traffic_cone"), cones="1", base="true", top="true", lamp=lamp,
              lamp_axis="z")
    s.set((2, 1, 12), m("traffic_cone"), cones="3", base="true", top="true", lamp="beacon", lamp_axis="z")
    # Barrier boards across the lane, facing the camera.
    for x in (8, 9, 10):
        s.set((x, 1, 13), m("barrier_board"), facing="north", left=str(x < 10).lower(), right=str(x > 8).lower())
    # Delineator posts along the far edge.
    for x in range(1, 27, 4):
        s.set((x, 1, 19), m("delineator"), facing="north", flipped="false")
    # The site behind: a fence with tarps, a dump body and a bucket waiting.
    fence = [(x, 22) for x in range(2, 26)]
    tarps = {x: ("orange" if (x // 4) % 2 == 0 else "none") for x in range(2, 26)}
    for x, z in fence:
        e, w = (x + 1, z) in fence, (x - 1, z) in fence
        post = (x % 2 == 0) or not (e and w)
        for y, half in ((1, "lower"), (2, "upper")):
            s.set((x, y, z), m("site_fence"), north="false", south="false", east=str(e).lower(),
                  west=str(w).lower(), waterlogged="false", half=half, post=str(post).lower(),
                  bottom=str(half == "lower").lower(), tarp=tarps[x], tarp_south="false", tarp_east="true")
    ground(s, 3, 23, 24, 26, "minecraft:coarse_dirt")
    dump_body(s, YELLOW, 6, 1, 24, 3, 1, 2, content="minecraft:gravel")
    bucket_row(s, YELLOW, 14, 1, 24, 3)
    s.set((20, 1, 24), m("warning_beacon"), face="floor", facing="south")
    return s, (1.5, 4.0, 6.0), (13.0, 1.0, 18.0)


def console():
    s = Structure(*SIZE)
    grass(s)
    # A small control platform of tread plate with a wall of dark steel behind.
    box(s, 11, 0, 13, 17, 0, 18, m("tread_plate"))
    box(s, 11, 1, 18, 17, 3, 18, STEEL)
    box(s, 11, 1, 17, 11, 3, 17, STEEL)
    box(s, 17, 1, 17, 17, 3, 17, STEEL)
    for x in range(12, 17):
        s.set((x, 1, 17), m("hazard_plate"), facing="north", flipped="false",
              north="false", south="false", west=str(x > 12).lower(), east=str(x < 16).lower())
    levers = {f"Lever{i}": Float(v) for i, v in enumerate((0.15, 0.5, 0.85, 1.0, 0.3))}
    s.set((14, 1, 15), m("operator_console"), {"id": m("operator_console"), **levers}, facing="north")
    for x in (12, 16):
        s.set((x, 2, 17), m("warning_flasher"), face="wall", facing="north")
    return s, (14.5, 3.1, 12.6), (14.5, 1.7, 15.6)


def metal():
    s = Structure(*SIZE)
    grass(s)
    box(s, 2, 0, 8, 25, 0, 23, m("tread_plate"))
    dyes = ["white", "light_gray", "gray", "black", "brown", "red", "orange", "yellow",
            "lime", "green", "cyan", "light_blue", "blue", "purple", "magenta", "pink"]
    # A wall of the 16 brass colours, 2 x 2 each, in two rows.
    for i, dye in enumerate(dyes):
        x0, y0 = 6 + 2 * (i % 8), 1 + 2 * (1 - i // 8)
        for dx in range(2):
            for dy in range(2):
                s.set((x0 + dx, y0 + dy, 21), painted(dye, "brass_plating"))
    # Pillars of dark steel and polished iron at the ends, hazard stripes along the foot.
    box(s, 4, 1, 21, 5, 4, 21, STEEL)
    box(s, 22, 1, 21, 23, 4, 21, m("polished_iron_plating"))
    for x in range(4, 24):
        s.set((x, 1, 20), m("hazard_plate"), facing="up", flipped="false",
              north="false", south="false", west=str(x > 4).lower(), east=str(x < 23).lower())
    # A corrugated shed on the left, a grating walkway with steps on the right.
    box(s, 3, 1, 10, 3, 3, 14, m("corrugated_sheet"), axis="y")
    box(s, 4, 1, 10, 7, 3, 10, m("orange_corrugated_sheet"), axis="y")
    box(s, 3, 4, 10, 7, 4, 14, m("gray_corrugated_sheet"), axis="x")
    for x in range(17, 24):
        for z in range(11, 13):
            slab(s, (x, 2, z), m("grating"), "top")
    s.set((16, 1, 11), m("tread_plate_stairs"), facing="east", half="bottom", shape="straight", waterlogged="false")
    s.set((16, 1, 12), m("tread_plate_stairs"), facing="east", half="bottom", shape="straight", waterlogged="false")
    for x in (17, 23):
        for z in (11, 12):
            s.set((x, 1, z), IRON)
    # Stairs and slabs of yellow and red plating in the middle.
    for i, dye in enumerate(("yellow", "red")):
        z = 12 + 2 * i
        s.set((10, 1, z), painted(dye, "brass_plating_stairs"), facing="east", half="bottom", shape="straight",
              waterlogged="false")
        s.set((11, 1, z), painted(dye, "brass_plating"))
        s.set((11, 2, z), painted(dye, "brass_plating_stairs"), facing="east", half="bottom", shape="straight",
              waterlogged="false")
        s.set((12, 1, z), painted(dye, "brass_plating"))
        s.set((12, 2, z), painted(dye, "brass_plating"))
        slab(s, (13, 1, z), painted(dye, "brass_plating"), "bottom")
    return s, (14.0, 5.5, 3.0), (14.0, 2.5, 18.0)


# --- the gallery: every block on show in a clean studio -------------------------------------

DYES = ["white", "light_gray", "gray", "black", "brown", "red", "orange", "yellow",
        "lime", "green", "cyan", "light_blue", "blue", "purple", "magenta", "pink"]
VERSIONS = [None] + DYES
PLINTH = "minecraft:smooth_quartz"


def studio(s, wall_z=24):
    """A white floor and a light grey backdrop (back wall and its floor joint), like a photo studio."""
    sx, sy, sz = s.size
    for x in range(sx):
        for z in range(sz):
            s.set((x, 0, z), "minecraft:white_concrete")
        for y in range(1, 11):
            s.set((x, y, wall_z), "minecraft:light_gray_concrete")
        s.set((x, 0, wall_z - 1), "minecraft:light_gray_concrete")


def plinth(s, x0, z0, x1=None, z1=None, height=1):
    x1, z1 = x1 if x1 is not None else x0, z1 if z1 is not None else z0
    box(s, x0, 1, z0, x1, height, z1, PLINTH)
    return height + 1   # the y the shown block stands on


def gallery_tools():
    """The three tools bare (as crafted) in front, bigger yellow ones joined behind."""
    s = Structure(*SIZE)
    studio(s)
    # Front row: one block of each, bare brass, on plinths.
    y = plinth(s, 8, 12)
    s.set((8, y, 12), m("excavator_bucket"), bucket_nbt(), facing="north", joined="true")
    y = plinth(s, 13, 12)
    s.set((13, y, 12), m("bulldozer_blade"), blade_nbt(), facing="north", joined="true")
    y = plinth(s, 18, 12)
    dump_body(s, None, 18, y, 12, 1, 1, 1)
    # Back row: joined, yellow, loaded.
    y = plinth(s, 6, 16, 6, 20)
    bucket_row(s, YELLOW, 6, y, 17, 3, content="minecraft:dirt")
    y = plinth(s, 12, 17, 12, 21)
    blade(s, YELLOW, 12, y, 17, 2, 4, stored_bottom=1)
    y = plinth(s, 17, 17, 20, 20)
    dump_body(s, YELLOW, 17, y, 17, 4, 1, 4, content="minecraft:gravel")
    return s, (14.0, 6.0, 1.5), (13.5, 2.0, 15.0)


def gallery_colours():
    """Every colour of each tool on steps: buckets in front, blades in the middle, dump bodies at the back."""
    s = Structure(*SIZE)
    studio(s)
    x0 = 5
    for row, (z, h) in enumerate(((12, 1), (14, 2), (16, 3))):
        box(s, x0 - 1, 1, z, x0 + len(VERSIONS), h, z, PLINTH)
        for i, dye in enumerate(VERSIONS):
            x, y = x0 + i, h + 1
            if row == 0:
                s.set((x, y, z), painted(dye, "excavator_bucket"), bucket_nbt(), facing="north", joined="true")
            elif row == 1:
                s.set((x, y, z), painted(dye, "bulldozer_blade"), blade_nbt(), facing="north", joined="true")
            else:
                props = {f"gate_{side}": "false" for side in ("north", "east", "south", "west")}
                s.set((x, y, z), painted(dye, "dump_body"), dump_body_nbt(), joined="true", **props)
    return s, (13.5, 7.0, 2.0), (13.5, 2.5, 14.5)


def gallery_hydraulics():
    """Hydraulic cylinders (upright, slanted, lying) and the Operator Console."""
    s = Structure(*SIZE)
    studio(s)
    # Upright: floor mount up to a mount under a beam on two posts.
    box(s, 4, 1, 15, 4, 5, 15, IRON)
    box(s, 8, 1, 15, 8, 5, 15, IRON)
    box(s, 4, 6, 15, 8, 6, 15, IRON)
    cylinder(s, "gallery_upright", (6, 1, 15), "up", (6, 5, 15), "down", 2.0)
    # Slanted: from a plinth up to a mount on the side of a pillar.
    plinth(s, 11, 15)
    box(s, 15, 1, 15, 15, 5, 15, IRON)
    cylinder(s, "gallery_slanted", (11, 2, 15), "up", (14, 4, 15), "west", 2.0)
    # Lying: two mounts facing each other on plinths.
    plinth(s, 17, 15)
    plinth(s, 21, 15)
    cylinder(s, "gallery_lying", (17, 2, 15), "east", (21, 2, 15), "west", 2.0)
    # Single mounts in front, and the console on its plinth.
    for x, facing in ((5, "up"), (7, "north")):
        y = plinth(s, x, 11)
        s.set((x, y, 11), m("cylinder_mount"), facing=facing, turned="false")
    levers = {f"Lever{i}": Float(v) for i, v in enumerate((0.15, 0.5, 0.85, 1.0, 0.3))}
    y = plinth(s, 19, 11)
    s.set((19, y, 11), m("operator_console"), {"id": m("operator_console"), **levers}, facing="north")
    return s, (13.0, 6.0, 2.0), (13.0, 2.5, 14.0)


def gallery_barriers():
    """The site barriers: cones and lamps, delineator posts, barrier boards, the fence with tarps."""
    s = Structure(*SIZE)
    studio(s)
    # Front: cones 1, 2 and 3 high, then with a flasher and a beacon.
    for i, (cones, lamp) in enumerate(((1, "none"), (2, "none"), (3, "none"), (1, "flasher"), (2, "beacon"))):
        s.set((4 + 2 * i, 1, 11), m("traffic_cone"), cones=str(cones), base="true", top="true", lamp=lamp,
              lamp_axis="z")
    # The lamps on their own, on a plinth and on its side.
    plinth(s, 16, 11, 17, 11)
    s.set((16, 2, 11), m("warning_flasher"), face="floor", facing="north")
    s.set((17, 2, 11), m("warning_beacon"), face="floor", facing="north")
    s.set((16, 1, 10), m("warning_flasher"), face="wall", facing="north")
    # Delineator posts, both stripe directions.
    s.set((20, 1, 11), m("delineator"), facing="north", flipped="false")
    s.set((22, 1, 11), m("delineator"), facing="north", flipped="true")
    # Middle: a single barrier board and three joined.
    s.set((5, 1, 15), m("barrier_board"), facing="north", left="false", right="false")
    for x in (8, 9, 10):
        s.set((x, 1, 15), m("barrier_board"), facing="north", left=str(x < 10).lower(), right=str(x > 8).lower())
    # Back: the site fence, bare and with three tarp colours.
    fence = list(range(4, 24))
    tarps = {x: ("none" if x < 8 else "orange" if x < 13 else "green" if x < 18 else "blue") for x in fence}
    for x in fence:
        e, w = x + 1 in fence, x - 1 in fence
        post = (x % 2 == 0) or not (e and w)
        for y, half in ((1, "lower"), (2, "upper")):
            s.set((x, y, 19), m("site_fence"), north="false", south="false", east=str(e).lower(),
                  west=str(w).lower(), waterlogged="false", half=half, post=str(post).lower(),
                  bottom=str(half == "lower").lower(), tarp=tarps[x], tarp_south="false", tarp_east="true")
    return s, (13.5, 5.0, 2.0), (13.5, 1.5, 14.0)


def gallery_blocks():
    """The building blocks: plating colours, steel and iron, tread plate, grating, hazard, corrugated sheet."""
    s = Structure(*SIZE)
    studio(s)
    # Back wall of plating: bare brass, the 16 colours, dark steel and polished iron, one block each.
    blocks = [m("brass_plating")] + [painted(d, "brass_plating") for d in DYES] + [STEEL, m("polished_iron_plating")]
    x0 = 14 - len(blocks) // 2
    for i, b in enumerate(blocks):
        s.set((x0 + i, 1, 20), b)
        s.set((x0 + i, 2, 20), b)
    # Corrugated sheet in every colour, a row in front of the plating.
    sheets = [m("corrugated_sheet")] + [painted(d, "corrugated_sheet") for d in DYES]
    x0 = 14 - len(sheets) // 2
    for i, b in enumerate(sheets):
        s.set((x0 + i, 1, 17), b, axis="y")
    # Front: tread plate with stairs and a slab, grating, hazard stripes and plates.
    box(s, 4, 1, 12, 6, 1, 12, m("tread_plate"))
    s.set((7, 1, 12), m("tread_plate_stairs"), facing="west", half="bottom", shape="straight", waterlogged="false")
    s.set((8, 1, 12), m("tread_plate_slab"), type="bottom", waterlogged="false")
    box(s, 10, 1, 12, 11, 1, 12, m("grating"))
    s.set((12, 1, 12), m("grating_slab"), type="bottom", waterlogged="false")
    s.set((14, 1, 12), m("hazard_stripes"), flipped="false")
    s.set((15, 1, 12), m("hazard_stripes_red_white"), flipped="false")
    for x, b in ((17, "hazard_plate"), (18, "hazard_plate"), (20, "hazard_plate_red_white"), (21, "hazard_plate_red_white")):
        s.set((x, 1, 12), m(b), facing="up", flipped="false", north="false", south="false",
              west=str(x in (18, 21)).lower(), east=str(x in (17, 20)).lower())
    # Stairs and slabs of three platings.
    for i, b in enumerate(("brass_plating", "yellow_brass_plating", "dark_steel_plating")):
        x = 5 + 7 * i
        s.set((x, 1, 15), m(f"{b}_stairs"), facing="west", half="bottom", shape="straight", waterlogged="false")
        s.set((x + 1, 1, 15), m(b))
        s.set((x + 2, 1, 15), m(f"{b}_slab"), type="bottom", waterlogged="false")
    return s, (13.5, 5.5, 3.0), (13.5, 1.5, 15.5)


SCENES = {
    "excavator": excavator,
    "bulldozer": bulldozer,
    "dump_truck": dump_truck,
    "site": site,
    "console": console,
    "metal": metal,
    "gallery_tools": gallery_tools,
    "gallery_colours": gallery_colours,
    "gallery_hydraulics": gallery_hydraulics,
    "gallery_barriers": gallery_barriers,
    "gallery_blocks": gallery_blocks,
}


# --- the functions -------------------------------------------------------------------------


def fmt(v):
    return ("%g" % v)


def write(resources):
    data = resources / "resourcepacks" / PACK / "data" / MOD
    fn = data / "function" / "showcase"
    fn.mkdir(parents=True, exist_ok=True)
    sx, sy, sz = SIZE
    header = "# Written by tools/showcase/generate_showcase.py: change the script, not this file."

    # Shared: clear the studio, place a scene, set the light, put the player at the camera.
    place = [
        "# Builds one showcase scene at the studio marker. Called by the scene functions.",
        header,
        f"execute positioned ~ ~-1 ~ run kill @e[type=!minecraft:player,type=!minecraft:marker,dx={sx - 1},dy={sy},dz={sz - 1}]",
        f"fill ~ ~-3 ~ ~{sx - 1} ~-2 ~{sz - 1} minecraft:dirt",
        f"fill ~ ~ ~ ~{sx - 1} ~{sy - 2} ~{sz - 1} minecraft:air",
        f"$place template {MOD}:showcase/$(scene) ~ ~-1 ~",
        f"execute positioned ~ ~-1 ~ run kill @e[type=minecraft:item,dx={sx - 1},dy={sy},dz={sz - 1}]",
        "time set noon",
        "weather clear",
        "gamerule doDaylightCycle false",
        "gamerule doWeatherCycle false",
        "gamerule doMobSpawning false",
        "gamemode spectator @s",
        "$tp @s ~$(cx) ~$(cy) ~$(cz) facing ~$(fx) ~$(fy) ~$(fz)",
        'tellraw @s [{"text":"Showcase: ","color":"gold"},{"text":"F1 hides the HUD, F2 takes the screenshot. '
        '/gamemode creative to leave spectator mode.","color":"white"}]',
    ]
    (fn / "_place.mcfunction").write_text("\n".join(place) + "\n")

    for name, make in SCENES.items():
        structure, cam, look = make()
        structure.save(data / "structure" / "showcase" / f"{name}.nbt")
        # Camera spots are relative to the ground block under the marker; the marker stands on top of it.
        args = dict(scene=name, cx=cam[0], cy=cam[1] - 1, cz=cam[2], fx=look[0], fy=look[1] - 1, fz=look[2])
        arglist = ",".join(f'{k}:"{v}"' if k == "scene" else f"{k}:{fmt(v)}" for k, v in args.items())
        lines = [
            f"# The {name} showcase scene: /function {MOD}:showcase/{name}",
            header,
            f"execute unless entity @e[type=minecraft:marker,tag={TAG}] align xyz run summon minecraft:marker ~ ~ ~ {{Tags:[\"{TAG}\"]}}",
            f"execute at @e[type=minecraft:marker,tag={TAG},limit=1] run function {MOD}:showcase/_place {{{arglist}}}",
        ]
        (fn / f"{name}.mcfunction").write_text("\n".join(lines) + "\n")

    move = [
        "# Moves the showcase studio to where you stand (the next scene is built here).",
        header,
        f"kill @e[type=minecraft:marker,tag={TAG}]",
        f"execute align xyz run summon minecraft:marker ~ ~ ~ {{Tags:[\"{TAG}\"]}}",
        'tellraw @s {"text":"Showcase studio moved here.","color":"gold"}',
    ]
    (fn / "move_here.mcfunction").write_text("\n".join(move) + "\n")
    print("wrote", len(SCENES), "showcase scenes to", data)


if __name__ == "__main__":
    write(Path(sys.argv[1] if len(sys.argv) > 1 else "src/main/resources"))
