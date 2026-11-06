# Create: Construction Site

An addon for [Create](https://github.com/Creators-of-Create/Create) that brings construction work to your machines. You build the excavator, bulldozer or dump truck yourself; the tools from this mod make it dig, push, carry and tip real blocks in the world. With [Create Aeronautics](https://github.com/Creators-of-Aeronautics/Simulated-Project) installed, all of it also works on physics vehicles.

The tools, the hydraulic cylinder, the Operator Console and the site barriers have no crafting recipes yet, so they are creative-only for now. The metal building blocks can be crafted.

## What is in it

### Earthmoving tools

Work on Create's contraptions (bearings, pistons, carts, trains) and on Create Aeronautics vehicles.

**Excavator Bucket**
- Digs shovel-type blocks (dirt, sand, gravel, clay, snow and so on) when a machine pushes its open side into them. Which blocks count is the block tag `create_construction_site:bucket_diggable`, so data packs can change it.
- Keeps every dug block exactly as it was and holds one block per bucket block.
- Bucket blocks placed together join into one bigger bucket, up to 3 high, 3 deep and 7 wide.
- Tip it to empty it; the fuller it is, the less you have to tip it. Dumped material falls, lands as blocks and forms a heap.
- With Create Aeronautics: takes ropes at its teeth, so a machine can tow and lift.

**Bulldozer Blade**
- Pushes loose material along as a pile and fills holes on the way; when the pile is full, the rest goes off its end.
- Leaves the pile behind when the machine backs away.
- Blade blocks placed together join into one bigger blade.

**Dump Body**
- Catches material that falls in and holds it, one block per Dump Body block.
- Tilt it to empty it over its lowest open side. Create's wrench opens and closes each side.
- Placing one on another adds a whole layer at once.

All three: Create's wrench changes the mount or picks up a whole slice, Create's goggles show what is inside, and dye paints them (wash them bare in a water cauldron or with a fan).

### Hydraulic Cylinder

- Place two **Cylinder Mounts** and link them with a **Hydraulic Cylinder**; the cylinder is drawn between their pins. The wrench turns a mount's pin by 90 degrees.
- An empty hand on a mount opens its settings: decorative or driven, tube length, speed and redstone direction.
- **Driven** (needs Create Aeronautics): redstone at the barrel's mount sets the length, 0 fully in and 15 fully out. The cylinder pushes the parts its mounts sit on with their real weight and holds hard end stops, so it can raise a boom or tip a bucket arm.

### Operator Console

A control desk for vehicles on Create's train controls desk: a cross of four buttons, four bonus buttons and five levers.
- Every control has its own Redstone Link frequency (two items each, set in the console's window). Buttons send 15 while held; levers send 0 to 15 and stay where you leave them, so a lever position becomes a cylinder position.
- Right-click to take control: your keys work the console (by default W A S D, Space Q E C, and R/F, T/G, Y/H, U/J, I/K for the levers; all of them are in Controls under "Create: Construction Site"). Sneak to leave. Scrolling on a lever moves it one step.

### Site barriers and building blocks

- **Traffic Cone** (stacks as high as you like), **Warning Flasher** and **Warning Beacon** (on any block or on top of a cone), **Delineator Post**, **Barrier Board** (joins into long barriers) and **Site Fence** (two blocks tall, connects like iron bars, takes a coloured tarp from a carpet).
- Metal building blocks: Brass Plating in 16 colours, Dark Steel and Polished Iron Plating, Tread Plate, Grating, Hazard Stripes, Hazard Plate and Corrugated Brass Sheet, several with slabs and stairs.

## Requirements

| | Version | |
| --- | --- | --- |
| Minecraft | 1.21.1 | required |
| NeoForge | any 1.21.1 version (21.1.x) | required |
| Create | 6.0.10 or newer | required |
| Create Aeronautics (with Simulated and Sable) | 1.3.0 or newer | optional: physics vehicles, driven cylinders, bucket ropes |

## Building from source

```
gradlew build
```

The jar is written to `build/libs`. To start a test game with all dependencies, run `gradlew runClient`.

## Licence

MIT, see [LICENSE](LICENSE).

The mod depends on Create and, optionally, Create Aeronautics and Sable, but contains none of their code or assets.
