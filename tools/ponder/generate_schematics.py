"""
Writes the Ponder scenes' structure files (the small dioramas each scene starts from).

A Ponder structure file is a gzip-compressed vanilla structure, the same format a
structure block saves: size, palette of block states, blocks with their position,
state number and block entity data, and entities. See docs/notes/create-ponder.md.

No extra packages needed: the NBT writer below is about thirty lines.

Run:  python generate_schematics.py <output folder>
      (the folder is usually src/main/resources/assets/create_construction_site/ponder)

Layouts, all tools facing west (open side or working face toward -x), ground at y = 0 in
Create's white concrete and snow checker pattern:
- excavator_bucket/sizes   9x9: a 1, a 2 and a 3 high bucket one behind the other at x 1, 3..4 and 6..8,
                           as wide as each can be (z 3..5, 2..6, 1..7); every block still a single bucket.
- excavator_bucket/digging 9x9: a Gantry Shaft overhead at y 3, z 4, x 5..8; a Gantry Carriage hanging from
                           it at x 7, y 2; under it a single empty bucket at x 7, y 1 facing west.
                           Two dirt blocks stacked at x 4, y 1..2, z 4.
- excavator_bucket/emptying 9x9: a full 1 high, 3 wide bucket held up at x 4, y 3, z 3..5.
- excavator_bucket/rope    9x9: an empty 1 high, 3 wide bucket at x 4, y 1, z 3..5; an oak log at x 1, y 1, z 4
                           with a Rope Connector on top (needs Create Aeronautics).
- bulldozer_blade/gaps     9x9: a dirt floor at y 1 with a trench at x 3, z 3..5; a Gantry Shaft overhead at y 4,
                           z 4; a Gantry Carriage hanging from it at x 7, y 3; under it an empty 1 high,
                           3 wide blade at x 7, y 2, z 3..5; a row of dirt on the floor at x 5, z 3..5.
- bulldozer_blade/full     9x9: a full 1 high, 2 wide blade at x 7, y 1, z 3..4; dirt at x 4..5, y 1, z 3..4.
- bulldozer_blade/sizes    9x9: a 1, a 2 and a 3 high blade one behind the other at x 2, 4 and 6,
                           each 9 wide (z 0..8); every block still a single blade.
- dump_body/sizes          9x9: a single dump body block at x 1, y 1, z 4; 30 blocks for a body 3 wide, 2 high and
                           5 long at x 4..6, y 1..2, z 2..6, each still drawn as its own body (joined in the scene).
- dump_body/loading        9x9: an empty dump body 3 by 3 by 1 at x 3..5, y 1, z 3..5; a full 1 high, 3 wide bucket
                           above its east column at x 6, y 3, z 3..5, facing west.
- dump_body/tipping        9x9: a full dump body 3 by 3 by 1, closed on all sides as placed, at x 3..5, y 2, z 3..5 on a Mechanical Bearing facing
                           north at x 4, y 2, z 6, with a Creative Motor behind it and casings under both.
- dump_body/simulated      9x9: a full dump body on a Create Aeronautics Swivel Bearing (see dump_body_simulated).
- excavator_bucket/simulated and bulldozer_blade/simulated 9x9: the owner's small vehicle with a
                           1 high, 3 wide bucket or blade at its front (see vehicle()); needs
                           Create Aeronautics.
"""
import gzip
import struct
import sys
from pathlib import Path

DATA_VERSION = 3955  # Minecraft 1.21.1
MOD = "create_construction_site"

# --- a minimal NBT writer --------------------------------------------------------------------
# Values: Byte(n), Int(n), Long(n), Float(x), Double(x), str, dict (compound), List(type, items).
# (Also used by tools/teststations/generate_test_stations.py.)


class Byte(int): pass
class Int(int): pass
class Long(int): pass
class Float(float): pass
class Double(float): pass


class List(list):
    def __init__(self, tag, items):
        super().__init__(items)
        self.tag = tag


TAG_BYTE, TAG_INT, TAG_LONG, TAG_FLOAT, TAG_DOUBLE, TAG_STRING, TAG_LIST, TAG_COMPOUND = 1, 3, 4, 5, 6, 8, 9, 10


def tag_of(value):
    if isinstance(value, Byte): return TAG_BYTE
    if isinstance(value, Long): return TAG_LONG
    if isinstance(value, Int): return TAG_INT
    if isinstance(value, Float): return TAG_FLOAT
    if isinstance(value, Double): return TAG_DOUBLE
    if isinstance(value, str): return TAG_STRING
    if isinstance(value, List): return TAG_LIST
    if isinstance(value, dict): return TAG_COMPOUND
    raise TypeError(f"no NBT type for {value!r}")


def payload(value):
    t = tag_of(value)
    if t == TAG_BYTE: return struct.pack(">b", value)
    if t == TAG_INT: return struct.pack(">i", value)
    if t == TAG_LONG: return struct.pack(">q", value)
    if t == TAG_FLOAT: return struct.pack(">f", value)
    if t == TAG_DOUBLE: return struct.pack(">d", value)
    if t == TAG_STRING:
        data = value.encode("utf-8")
        return struct.pack(">H", len(data)) + data
    if t == TAG_LIST:
        return struct.pack(">bi", value.tag if value else 0, len(value)) + b"".join(payload(v) for v in value)
    out = b""
    for key, v in value.items():
        name = key.encode("utf-8")
        out += struct.pack(">bH", tag_of(v), len(name)) + name + payload(v)
    return out + b"\x00"


def write_nbt(path, root):
    data = struct.pack(">bH", TAG_COMPOUND, 0) + payload(root)
    path.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(path, "wb") as f:
        f.write(data)


# --- building a structure ------------------------------------------------------------------


class Structure:
    def __init__(self, size_x, size_y, size_z):
        self.size = (size_x, size_y, size_z)
        self.palette = []          # list of (name, properties)
        self.blocks = {}           # (x, y, z) -> (palette index, block entity data or None)
        self.entities = []         # (position, block position, entity data)

    def state(self, name, **properties):
        key = (name, tuple(sorted(properties.items())))
        if key not in self.palette:
            self.palette.append(key)
        return self.palette.index(key)

    def set(self, pos, name, nbt=None, **properties):
        self.blocks[pos] = (self.state(name, **properties), nbt)

    def fill(self, x0, y0, z0, x1, y1, z1, name, **properties):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.set((x, y, z), name, **properties)

    def glue(self, x0, y0, z0, x1, y1, z1):
        """Create's Super Glue over the blocks x0..x1, y0..y1, z0..z1 (as a player would spread it)."""
        # The glue entity stands at the bottom centre of its box; its box is saved relative to that.
        cx, cz = (x0 + x1 + 1) / 2, (z0 + z1 + 1) / 2
        size = (x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1)
        nbt = {
            "id": "create:super_glue",
            "Pos": List(TAG_DOUBLE, [Double(cx), Double(y0), Double(cz)]),
            "From": List(TAG_DOUBLE, [Double(-size[0] / 2), Double(0), Double(-size[2] / 2)]),
            "To": List(TAG_DOUBLE, [Double(size[0] / 2), Double(size[1]), Double(size[2] / 2)]),
        }
        self.entities.append(((cx, y0, cz), (x0, y0, z0), nbt))

    def save(self, path):
        palette = List(TAG_COMPOUND, [])
        for name, props in self.palette:
            entry = {"Name": name}
            if props:
                entry["Properties"] = {k: v for k, v in props}
            palette.append(entry)
        blocks = List(TAG_COMPOUND, [])
        for (x, y, z), (state, nbt) in sorted(self.blocks.items()):
            entry = {"pos": List(TAG_INT, [Int(x), Int(y), Int(z)]), "state": Int(state)}
            if nbt is not None:
                entry["nbt"] = nbt
            blocks.append(entry)
        write_nbt(path, {
            "DataVersion": Int(DATA_VERSION),
            "size": List(TAG_INT, [Int(v) for v in self.size]),
            "palette": palette,
            "blocks": blocks,
            "entities": List(TAG_COMPOUND, [
                {"pos": List(TAG_DOUBLE, [Double(v) for v in pos]),
                 "blockPos": List(TAG_INT, [Int(v) for v in block_pos]),
                 "nbt": nbt}
                for pos, block_pos, nbt in self.entities]),
        })


def ground(s, size_x, size_z):
    """The base plate Create's own scenes use: white concrete and snow blocks in a checker pattern."""
    for x in range(size_x):
        for z in range(size_z):
            s.set((x, 0, z), "minecraft:white_concrete" if (x + z) % 2 == 0 else "minecraft:snow_block")


# --- the tools' block entity data (field names from the block entities' write methods) ------

BUCKET_MOUNT_BACK = 1   # BucketMount: TOP, BACK, NONE
BLADE_MOUNT_CENTRE = 0  # BladeMount: CENTRE, ENDS, NONE


def bucket_nbt(anchor_offset=(0, 0, 0), height=1, width=1, content=None, picked_up_at=0):
    """content: a block name the bucket block holds, or None for empty."""
    tag = {
        "id": f"{MOD}:excavator_bucket",
        "HasContent": Byte(1 if content else 0),
        "AnchorX": Int(anchor_offset[0]), "AnchorY": Int(anchor_offset[1]), "AnchorZ": Int(anchor_offset[2]),
        "GroupHeight": Int(height), "GroupWidth": Int(width),
        "Mount": Int(BUCKET_MOUNT_BACK),
        "HoldUntilLevel": Byte(0),
    }
    if content:
        tag["StoredBlock"] = {"Name": content}
        tag["PickedUpAt"] = Long(picked_up_at)
    return tag


def joined_bucket_row(s, x, y, z0, width, content=None):
    """A bucket 1 high facing west, width blocks along z. Its anchor is the block with the highest z."""
    anchor_z = z0 + width - 1
    for i, z in enumerate(range(z0, z0 + width)):
        tool(s, "excavator_bucket", (x, y, z),
             bucket_nbt((0, 0, anchor_z - z), 1, width, content, picked_up_at=i + 1))


def dirt_entry():
    return {"Block": {"Name": "minecraft:dirt"},
            "FromRight": Float(0.0), "FromUp": Float(0.0), "FromForward": Float(0.0), "TakenAt": Long(0)}


def blade_nbt(anchor_offset=(0, 0, 0), height=1, width=1, stored=0):
    return {
        "id": f"{MOD}:bulldozer_blade",
        "Stored": List(TAG_COMPOUND, [dirt_entry() for _ in range(stored)]),
        "AnchorX": Int(anchor_offset[0]), "AnchorY": Int(anchor_offset[1]), "AnchorZ": Int(anchor_offset[2]),
        "GroupHeight": Int(height), "GroupWidth": Int(width),
        "Mount": Int(BLADE_MOUNT_CENTRE),
    }


def tool(s, name, pos, nbt):
    # "joined" is true from the start: the scenes join blocks by changing their data only,
    # because a Ponder world keeps a block entity's first block state.
    s.set(pos, f"{MOD}:{name}", nbt, facing="west", joined="true")


def joined_blade(s, x, y0, z0, height, width, stored_per_block=0, stored_bottom_only=False):
    """A blade facing west. Its anchor is the bottom block at the left end, which is the highest z."""
    anchor = (x, y0, z0 + width - 1)
    for y in range(y0, y0 + height):
        for z in range(z0, z0 + width):
            offset = (anchor[0] - x, anchor[1] - y, anchor[2] - z)
            stored = stored_per_block if (y == y0 or not stored_bottom_only) else 0
            tool(s, "bulldozer_blade", (x, y, z), blade_nbt(offset, height, width, stored))


def dump_body_nbt(anchor_offset=(0, 0, 0), size=(1, 1, 1), content=None):
    """content: a block name the dump body cell holds, or None for empty."""
    tag = {
        "id": f"{MOD}:dump_body",
        "AnchorX": Int(anchor_offset[0]), "AnchorY": Int(anchor_offset[1]), "AnchorZ": Int(anchor_offset[2]),
        "SizeX": Int(size[0]), "SizeY": Int(size[1]), "SizeZ": Int(size[2]),
        "OpenSide": Int(-1), "OpenAngle": Float(0.0),
    }
    if content:
        tag["StoredBlock"] = {"Name": content}
    return tag


def dump_body(s, x0, y0, z0, sx, sy, sz, content=None, joined=True, gates=None):
    """
    A dump body with its anchor (lowest corner) at x0, y0, z0. joined=False writes every block as its own
    1 by 1 by 1 body ("joined" true in the block state, as the scenes join blocks by data only).
    gates: the sides that may open (default none: a new dump body is closed on all sides).
    """
    gates = gates if gates is not None else ()
    props = {f"gate_{side}": "true" if side in gates else "false" for side in ("north", "east", "south", "west")}
    for x in range(x0, x0 + sx):
        for y in range(y0, y0 + sy):
            for z in range(z0, z0 + sz):
                if joined:
                    nbt = dump_body_nbt((x0 - x, y0 - y, z0 - z), (sx, sy, sz), content)
                else:
                    nbt = dump_body_nbt(content=content)
                s.set((x, y, z), f"{MOD}:dump_body", nbt, joined="true", **props)


# --- the dioramas --------------------------------------------------------------------------


def bucket_sizes():
    """
    Three buckets one behind the other, each first one block wide: 1 high at x 1, 2 high at
    x 3..4, 3 high at x 6..8. Each later grows to its widest: z 3..5, z 2..6 and z 1..7.
    Every block that will be part of a bucket is in the file; the scene shows them step by step.
    """
    s = Structure(9, 5, 9)
    ground(s, 9, 9)
    for x0, height, z0, z1 in ((1, 1, 3, 5), (3, 2, 2, 6), (6, 3, 1, 7)):
        for x in range(x0, x0 + height):
            for y in range(1, 1 + height):
                for z in range(z0, z1 + 1):
                    tool(s, "excavator_bucket", (x, y, z), bucket_nbt())
    return s


def bucket_digging():
    """
    A gantry shaft overhead along x at y 3, z 4, from x 5 to 8; a gantry carriage hanging from it
    at x 7, y 2; under it a single empty bucket at x 7, y 1, facing west. Two dirt blocks stacked
    at x 4, y 1..2, z 4: the shaft ends where the bucket's opening reaches them.
    """
    s = Structure(9, 4, 9)
    ground(s, 9, 9)
    for x in range(5, 9):
        part = "start" if x == 8 else ("end" if x == 5 else "middle")
        s.set((x, 3, 4), "create:gantry_shaft", facing="west", part=part, powered="false")
    s.set((7, 2, 4), "create:gantry_carriage", facing="down", axis_along_first="false")
    s.set((7, 1, 4), f"{MOD}:excavator_bucket", bucket_nbt(), facing="west", joined="false")
    s.fill(4, 1, 4, 4, 2, 4, "minecraft:dirt")
    return s


def bucket_emptying():
    """A full bucket 1 high and 3 wide held up at x 4, y 3, z 3..5, its open side over empty ground."""
    s = Structure(9, 5, 9)
    ground(s, 9, 9)
    joined_bucket_row(s, 4, 3, 3, 3, content="minecraft:dirt")
    return s


def bucket_rope():
    """
    An empty bucket 1 high and 3 wide at x 4, y 1, z 3..5; an oak log at x 1, y 1, z 4 with a
    Create Aeronautics Rope Connector standing on it (block state as in Simulated's own rope scene).
    """
    s = Structure(9, 4, 9)
    ground(s, 9, 9)
    joined_bucket_row(s, 4, 1, 3, 3)
    s.set((1, 1, 4), "minecraft:oak_log", axis="x")
    s.set((1, 2, 4), "simulated:rope_connector", facing="up", axis_along_first="false")
    return s


def blade_gaps():
    """
    A dirt floor at y 1 with a trench at x 3, z 3..5. A gantry shaft overhead along x at y 4, z 4;
    a gantry carriage hanging from it at x 7, y 3; under it an empty 1 high, 3 wide blade at x 7,
    y 2, z 3..5. A row of dirt on the floor at x 5, y 2, z 3..5.
    """
    s = Structure(9, 5, 9)
    ground(s, 9, 9)
    s.fill(0, 1, 2, 8, 1, 6, "minecraft:dirt")
    for z in range(3, 6):
        del s.blocks[(3, 1, z)]
    for x in range(0, 9):
        part = "start" if x == 8 else ("end" if x == 0 else "middle")
        s.set((x, 4, 4), "create:gantry_shaft", facing="west", part=part, powered="false")
    s.set((7, 3, 4), "create:gantry_carriage", facing="down", axis_along_first="false")
    joined_blade(s, 7, 2, 3, 1, 3)
    s.fill(5, 2, 3, 5, 2, 5, "minecraft:dirt")
    return s


def blade_full():
    s = Structure(9, 4, 9)
    ground(s, 9, 9)
    # Full: a blade 1 high carries 1 block per column.
    joined_blade(s, 7, 1, 3, 1, 2, stored_per_block=1)
    s.fill(4, 1, 3, 5, 1, 4, "minecraft:dirt")
    return s


def blade_sizes():
    """
    Three blades one behind the other: 1 high at x 2, 2 high at x 4, 3 high at x 6, each as wide
    as a blade can be (z 0..8). Every block still a single blade; the scene shows them step by step.
    """
    s = Structure(9, 5, 9)
    ground(s, 9, 9)
    for x, height in ((2, 1), (4, 2), (6, 3)):
        for y in range(1, 1 + height):
            for z in range(0, 9):
                tool(s, "bulldozer_blade", (x, y, z), blade_nbt())
    return s


def vehicle(tool_name):
    """
    The owner's small vehicle (17 October 2026) with three tool blocks at its front, for the
    Create Aeronautics scenes. Facing west (front toward -x), centred on z 4:
    - frame at y 2: Encased Chain Drives at x 3..6 with their axles along z (sideways), chained
      along x, and a fifth at x 7 with its axle up and down, chained to them;
    - Wheel Mounts at x 3 and x 6 on both sides (z 3 facing north, z 5 facing south), each
      holding a normal Tire;
    - a Gearbox on the back chain drive (x 7, y 3) and a Red Portable Engine in front of it
      (x 6, y 3) with its shaft toward the gearbox;
    - the tool, 1 high and 3 wide, in front of the frame and level with it: x 2, y 2, z 3..5
      (for show: it hangs above the ground, so it reads as part of the vehicle).
    Needs Create Aeronautics (Simulated and Offroad) installed.
    """
    s = Structure(9, 5, 9)
    ground(s, 9, 9)
    parts = {3: "start", 4: "middle", 5: "middle", 6: "middle", 7: "end"}
    for x, part in parts.items():
        axis = "y" if x == 7 else "z"
        s.set((x, 2, 4), "create:encased_chain_drive", axis=axis, axis_along_first="true", part=part)
    tire = {"id": "offroad:wheel_mount", "CurrentStack": {"id": "offroad:tire", "count": Int(1)}}
    for x in (3, 6):
        s.set((x, 2, 3), "offroad:wheel_mount", dict(tire), facing="north")
        s.set((x, 2, 5), "offroad:wheel_mount", dict(tire), facing="south")
    s.set((7, 3, 4), "create:gearbox", axis="z")
    s.set((6, 3, 4), "simulated:red_portable_engine", facing="east", lit="false")
    if tool_name == "excavator_bucket":
        joined_bucket_row(s, 2, 2, 3, 3)
    else:
        joined_blade(s, 2, 2, 3, 1, 3)
    return s


def bucket_simulated():
    return vehicle("excavator_bucket")


def blade_simulated():
    return vehicle("bulldozer_blade")


def dump_body_sizes():
    s = Structure(9, 4, 9)
    ground(s, 9, 9)
    s.set((1, 1, 4), f"{MOD}:dump_body", dump_body_nbt(), joined="false",
          gate_north="false", gate_east="false", gate_south="false", gate_west="false")
    dump_body(s, 4, 1, 2, 3, 2, 5, joined=False)
    return s


def dump_body_loading():
    s = Structure(9, 5, 9)
    ground(s, 9, 9)
    dump_body(s, 3, 1, 3, 3, 1, 3)
    joined_bucket_row(s, 6, 3, 3, 3, content="minecraft:dirt")
    return s


def dump_body_tipping():
    s = Structure(9, 5, 9)
    ground(s, 9, 9)
    dump_body(s, 3, 2, 3, 3, 1, 3, content="minecraft:dirt")
    s.set((4, 2, 6), "create:mechanical_bearing", facing="north")
    s.set((4, 2, 7), "create:creative_motor", facing="north")
    s.set((4, 1, 6), "create:andesite_casing")
    s.set((4, 1, 7), "create:andesite_casing")
    return s


def dump_body_simulated():
    """
    A full dump body 3 by 3 by 1 at x 3..5, y 2, z 4..6, west side open, held by a Create Aeronautics
    Swivel Bearing facing north at x 4, y 2, z 7, with the cogwheel that drives it at x 5 and casings
    under both. Needs Create Aeronautics.
    """
    s = Structure(9, 5, 9)
    ground(s, 9, 9)
    dump_body(s, 3, 2, 4, 3, 1, 3, content="minecraft:dirt", gates=("west",))
    s.set((4, 2, 7), "simulated:swivel_bearing", facing="north", powered="false", assembled="false")
    s.set((5, 2, 7), "create:cogwheel", axis="z")
    s.set((4, 1, 7), "create:andesite_casing")
    s.set((5, 1, 7), "create:andesite_casing")
    return s


SCENES = {
    "excavator_bucket/sizes": bucket_sizes,
    "excavator_bucket/digging": bucket_digging,
    "excavator_bucket/emptying": bucket_emptying,
    "excavator_bucket/rope": bucket_rope,
    "bulldozer_blade/gaps": blade_gaps,
    "bulldozer_blade/full": blade_full,
    "bulldozer_blade/sizes": blade_sizes,
    "dump_body/sizes": dump_body_sizes,
    "dump_body/loading": dump_body_loading,
    "dump_body/tipping": dump_body_tipping,
    "dump_body/simulated": dump_body_simulated,
    "excavator_bucket/simulated": bucket_simulated,
    "bulldozer_blade/simulated": blade_simulated,
}

if __name__ == "__main__":
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "ponder")
    for name, make in SCENES.items():
        path = out / f"{name}.nbt"
        make().save(path)
        print("wrote", path)
