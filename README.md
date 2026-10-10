# Create: Construction Site

An addon for [Create Aeronautics](https://github.com/Creators-of-Aeronautics/Simulated-Project) that brings earthmoving to physics vehicles. You build the excavator or wheel loader yourself; the tools from this mod make it dig, carry and dump real blocks in the world.

The mod is in early development. Things change, and there are no crafting recipes yet, so the blocks are creative-only for now.

## What is in it

**Excavator Bucket**

- Digs shovel-type blocks (dirt, sand, gravel, clay, snow and so on) when a vehicle pushes its open side into them. Which blocks count is a block tag, `create_construction_site:bucket_diggable`, so data packs can change it.
- Keeps every dug block exactly as it was and holds one block per bucket block.
- Bucket blocks placed together join into one bigger bucket, from 1x1x1 up to 3 high, 3 deep and 7 wide.
- Tip it to empty it. The fuller the bucket, the less you have to tip it.
- Dumped material falls, lands as blocks again and forms a heap.
- Takes Create Aeronautics' ropes at its teeth, one per block of width, so a machine can tow and lift.
- Create's wrench moves the mounting ears or picks up a slice; Create's goggles show what is inside.

## Requirements

| | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.256 or newer |
| Create | 6.0.10 or newer |
| Sable | 2.0.0 or newer |
| Create Aeronautics (with Simulated) | 1.3.0 or newer |

## Building from source

```
gradlew build
```

The jar is written to `build/libs`. To start a test game with all dependencies, run `gradlew runClient`.

## Licence

MIT, see [LICENSE](LICENSE).

The mod depends on Create, Sable and Create Aeronautics but contains none of their code or assets.
