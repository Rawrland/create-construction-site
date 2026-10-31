# Builds the stations in a row in front of the player (toward +z, along +x).
# Written by tools/teststations/generate_test_stations.py: change the script, not this file.
gamerule commandBlockOutput false
# bucket_digging
data modify storage create_construction_site:test text.bucket_digging set value '[{"text":"Bucket: digging","color":"gold"},{"text":"\\n"},{"text":"Flip the START lever off (on top of the clutch).","color":"white"},{"text":"\\n"},{"text":"Expect: the carriage runs to the end of the shaft, the bucket digs the lower dirt block, the dirt on top drops into the hole, and the bucket shows its load.","color":"white"}]'
fill ~0 ~ ~0 ~13 ~1 ~1 minecraft:air
setblock ~0 ~ ~1 minecraft:command_block[facing=up]{Command:"function create_construction_site:test/reset {station:bucket_digging,dx:14,dy:8,dz:9,fx:13,fy:7,fz:10}"}
setblock ~0 ~ ~0 minecraft:stone_button[face=wall,facing=north]
setblock ~0 ~1 ~1 minecraft:oak_sign[rotation=8]{is_waxed:1b,front_text:{messages:['{"text":"Bucket:"}','{"text":"digging"}','{"text":"Button: reset"}','{"text":""}']}}
execute positioned ~0 ~ ~1 run function create_construction_site:test/reset {station:bucket_digging,dx:14,dy:8,dz:9,fx:13,fy:7,fz:10}
# bucket_emptying
data modify storage create_construction_site:test text.bucket_emptying set value '[{"text":"Bucket: emptying","color":"gold"},{"text":"\\n"},{"text":"Flip the START lever off (on the clutch behind the bearing). The bearing turns the full bucket over.","color":"white"},{"text":"\\n"},{"text":"Expect: it starts to spill at about 20 degrees, the last block leaves at about 45, newest first; the dirt falls and lands as a heap. Flip the lever back to stop.","color":"white"}]'
fill ~17 ~ ~0 ~25 ~1 ~1 minecraft:air
setblock ~17 ~ ~1 minecraft:command_block[facing=up]{Command:"function create_construction_site:test/reset {station:bucket_emptying,dx:9,dy:8,dz:10,fx:8,fy:7,fz:11}"}
setblock ~17 ~ ~0 minecraft:stone_button[face=wall,facing=north]
setblock ~17 ~1 ~1 minecraft:oak_sign[rotation=8]{is_waxed:1b,front_text:{messages:['{"text":"Bucket:"}','{"text":"emptying"}','{"text":"Button: reset"}','{"text":""}']}}
execute positioned ~17 ~ ~1 run function create_construction_site:test/reset {station:bucket_emptying,dx:9,dy:8,dz:10,fx:8,fy:7,fz:11}
# blade_gaps
data modify storage create_construction_site:test text.blade_gaps set value '[{"text":"Blade: pile and gaps","color":"gold"},{"text":"\\n"},{"text":"Flip the START lever off.","color":"white"},{"text":"\\n"},{"text":"Expect: the blade takes the row of dirt onto its pile and carries it; over the trench the three blocks drop in and fill it; the blade drives on empty.","color":"white"}]'
fill ~29 ~ ~0 ~42 ~1 ~1 minecraft:air
setblock ~29 ~ ~1 minecraft:command_block[facing=up]{Command:"function create_construction_site:test/reset {station:blade_gaps,dx:14,dy:8,dz:9,fx:13,fy:7,fz:10}"}
setblock ~29 ~ ~0 minecraft:stone_button[face=wall,facing=north]
setblock ~29 ~1 ~1 minecraft:oak_sign[rotation=8]{is_waxed:1b,front_text:{messages:['{"text":"Blade: pile"}','{"text":"and gaps"}','{"text":"Button: reset"}','{"text":""}']}}
execute positioned ~29 ~ ~1 run function create_construction_site:test/reset {station:blade_gaps,dx:14,dy:8,dz:9,fx:13,fy:7,fz:10}
# blade_full
data modify storage create_construction_site:test text.blade_full set value '[{"text":"Blade: full, backing away","color":"gold"},{"text":"\\n"},{"text":"The blade is already full. Flip the START lever off; once it has passed the dirt, flip the REVERSE lever.","color":"white"},{"text":"\\n"},{"text":"Expect: the dirt it meets is moved off its nearer end and forms a ridge; backing away, the blade leaves its pile on the ground as it was.","color":"white"}]'
fill ~46 ~ ~0 ~59 ~1 ~1 minecraft:air
setblock ~46 ~ ~1 minecraft:command_block[facing=up]{Command:"function create_construction_site:test/reset {station:blade_full,dx:14,dy:7,dz:9,fx:13,fy:6,fz:10}"}
setblock ~46 ~ ~0 minecraft:stone_button[face=wall,facing=north]
setblock ~46 ~1 ~1 minecraft:oak_sign[rotation=8]{is_waxed:1b,front_text:{messages:['{"text":"Blade: full,"}','{"text":"backing away"}','{"text":"Button: reset"}','{"text":""}']}}
execute positioned ~46 ~ ~1 run function create_construction_site:test/reset {station:blade_full,dx:14,dy:7,dz:9,fx:13,fy:6,fz:10}
# gantry_into_stone
data modify storage create_construction_site:test text.gantry_into_stone set value '[{"text":"Gantry into stone","color":"gold"},{"text":"\\n"},{"text":"Flip the START lever off. When the gantry has stopped, break the stone.","color":"white"},{"text":"\\n"},{"text":"Expect: the blade takes the dirt, the gantry stops at the stone and waits; once the stone is broken it moves on.","color":"white"}]'
fill ~63 ~ ~0 ~76 ~1 ~1 minecraft:air
setblock ~63 ~ ~1 minecraft:command_block[facing=up]{Command:"function create_construction_site:test/reset {station:gantry_into_stone,dx:14,dy:7,dz:9,fx:13,fy:6,fz:10}"}
setblock ~63 ~ ~0 minecraft:stone_button[face=wall,facing=north]
setblock ~63 ~1 ~1 minecraft:oak_sign[rotation=8]{is_waxed:1b,front_text:{messages:['{"text":"Gantry"}','{"text":"into stone"}','{"text":"Button: reset"}','{"text":""}']}}
execute positioned ~63 ~ ~1 run function create_construction_site:test/reset {station:gantry_into_stone,dx:14,dy:7,dz:9,fx:13,fy:6,fz:10}
# bearing_through_stone
data modify storage create_construction_site:test text.bearing_through_stone set value '[{"text":"Bearing through stone","color":"gold"},{"text":"\\n"},{"text":"Flip the START lever off. The bucket must lead with its open side: if it swings back first, flip the REVERSE lever.","color":"white"},{"text":"\\n"},{"text":"Expect: the bearing keeps turning; the bucket leaves the stone alone and digs the top dirt block on the far side.","color":"white"}]'
fill ~80 ~ ~0 ~88 ~1 ~1 minecraft:air
setblock ~80 ~ ~1 minecraft:command_block[facing=up]{Command:"function create_construction_site:test/reset {station:bearing_through_stone,dx:9,dy:7,dz:9,fx:8,fy:6,fz:10}"}
setblock ~80 ~ ~0 minecraft:stone_button[face=wall,facing=north]
setblock ~80 ~1 ~1 minecraft:oak_sign[rotation=8]{is_waxed:1b,front_text:{messages:['{"text":"Bearing"}','{"text":"through stone"}','{"text":"Button: reset"}','{"text":""}']}}
execute positioned ~80 ~ ~1 run function create_construction_site:test/reset {station:bearing_through_stone,dx:9,dy:7,dz:9,fx:8,fy:6,fz:10}
# sizes
data modify storage create_construction_site:test text.sizes set value '[{"text":"Sizes","color":"gold"},{"text":"\\n"},{"text":"Take buckets and blades from the chests and place them next to each other.","color":"white"},{"text":"\\n"},{"text":"Expect: they join into one tool. A bucket is 1 high and up to 3 wide, 2 high and up to 5, or 3 high and up to 7; a blade is up to 3 high and 9 wide.","color":"white"}]'
fill ~92 ~ ~0 ~100 ~1 ~1 minecraft:air
setblock ~92 ~ ~1 minecraft:command_block[facing=up]{Command:"function create_construction_site:test/reset {station:sizes,dx:9,dy:5,dz:9,fx:8,fy:4,fz:10}"}
setblock ~92 ~ ~0 minecraft:stone_button[face=wall,facing=north]
setblock ~92 ~1 ~1 minecraft:oak_sign[rotation=8]{is_waxed:1b,front_text:{messages:['{"text":"Sizes"}','{"text":"Button: reset"}','{"text":""}','{"text":""}']}}
execute positioned ~92 ~ ~1 run function create_construction_site:test/reset {station:sizes,dx:9,dy:5,dz:9,fx:8,fy:4,fz:10}
# dump_body_sizes
data modify storage create_construction_site:test text.dump_body_sizes set value '[{"text":"Dump Body: sizes","color":"gold"},{"text":"\\n"},{"text":"Take Dump Bodies from the chest and place them as boxes. Place one on a full side of a body (also the top); sneak to place just one. Use the Wrench on a side; wear the Goggles.","color":"white"},{"text":"\\n"},{"text":"Expect: touching blocks join into one box, up to 7 wide, 15 long and 4 high (16 in a row does not join as one). Placed on a full side, the whole layer appears at once, if the bigger size is allowed. A new body is closed on all sides (locking bars). The Wrench switches a side to a tailgate (hinges on top, latches at the bottom) and back. Tailgate and wall edges are closed, nothing to see through.","color":"white"}]'
fill ~104 ~ ~0 ~112 ~1 ~1 minecraft:air
setblock ~104 ~ ~1 minecraft:command_block[facing=up]{Command:"function create_construction_site:test/reset {station:dump_body_sizes,dx:9,dy:5,dz:9,fx:8,fy:4,fz:10}"}
setblock ~104 ~ ~0 minecraft:stone_button[face=wall,facing=north]
setblock ~104 ~1 ~1 minecraft:oak_sign[rotation=8]{is_waxed:1b,front_text:{messages:['{"text":"Dump Body:"}','{"text":"sizes"}','{"text":"Button: reset"}','{"text":""}']}}
execute positioned ~104 ~ ~1 run function create_construction_site:test/reset {station:dump_body_sizes,dx:9,dy:5,dz:9,fx:8,fy:4,fz:10}
# dump_body_loading
data modify storage create_construction_site:test text.dump_body_loading set value '[{"text":"Dump Body: loading","color":"gold"},{"text":"\\n"},{"text":"Flip the START lever off. The bearing tips the full bucket over the Dump Body.","color":"white"},{"text":"\\n"},{"text":"Expect: the dirt falls into the Dump Body and is shown inside it; nothing lands on top of it or beside it. Goggles show 3 of 15 blocks.","color":"white"}]'
fill ~116 ~ ~0 ~124 ~1 ~1 minecraft:air
setblock ~116 ~ ~1 minecraft:command_block[facing=up]{Command:"function create_construction_site:test/reset {station:dump_body_loading,dx:9,dy:8,dz:10,fx:8,fy:7,fz:11}"}
setblock ~116 ~ ~0 minecraft:stone_button[face=wall,facing=north]
setblock ~116 ~1 ~1 minecraft:oak_sign[rotation=8]{is_waxed:1b,front_text:{messages:['{"text":"Dump Body:"}','{"text":"loading"}','{"text":"Button: reset"}','{"text":""}']}}
execute positioned ~116 ~ ~1 run function create_construction_site:test/reset {station:dump_body_loading,dx:9,dy:8,dz:10,fx:8,fy:7,fz:11}
# dump_body_tipping
data modify storage create_construction_site:test text.dump_body_tipping set value '[{"text":"Dump Body: tipping","color":"gold"},{"text":"\\n"},{"text":"All four sides of this Dump Body were opened with the Wrench. Flip the START lever off; the bearing tips it. Flip REVERSE to tip it the other way.","color":"white"},{"text":"\\n"},{"text":"Expect: from about 20 degrees the lower side\'s tailgate swings open, hanging straight down, and the dirt streams out from that side, top first; by about 45 degrees it is empty. Only the lowest side opens, and it stays open the whole time the body leans toward it.","color":"white"}]'
fill ~128 ~ ~0 ~136 ~1 ~1 minecraft:air
setblock ~128 ~ ~1 minecraft:command_block[facing=up]{Command:"function create_construction_site:test/reset {station:dump_body_tipping,dx:9,dy:8,dz:12,fx:8,fy:7,fz:13}"}
setblock ~128 ~ ~0 minecraft:stone_button[face=wall,facing=north]
setblock ~128 ~1 ~1 minecraft:oak_sign[rotation=8]{is_waxed:1b,front_text:{messages:['{"text":"Dump Body:"}','{"text":"tipping"}','{"text":"Button: reset"}','{"text":""}']}}
execute positioned ~128 ~ ~1 run function create_construction_site:test/reset {station:dump_body_tipping,dx:9,dy:8,dz:12,fx:8,fy:7,fz:13}
# dump_body_closed_side
data modify storage create_construction_site:test text.dump_body_closed_side set value '[{"text":"Dump Body: closed side","color":"gold"},{"text":"\\n"},{"text":"The west side is closed (locking bar), the others were opened with the Wrench. Flip the START lever off; if the body tips to the east instead, flip REVERSE.","color":"white"},{"text":"\\n"},{"text":"Expect: tipped toward the west, nothing leaves and no gate opens, until the body is nearly upside down: then it empties through its open top. Tipped toward the east, it empties over the east side.","color":"white"}]'
fill ~140 ~ ~0 ~148 ~1 ~1 minecraft:air
setblock ~140 ~ ~1 minecraft:command_block[facing=up]{Command:"function create_construction_site:test/reset {station:dump_body_closed_side,dx:9,dy:8,dz:12,fx:8,fy:7,fz:13}"}
setblock ~140 ~ ~0 minecraft:stone_button[face=wall,facing=north]
setblock ~140 ~1 ~1 minecraft:oak_sign[rotation=8]{is_waxed:1b,front_text:{messages:['{"text":"Dump Body:"}','{"text":"closed side"}','{"text":"Button: reset"}','{"text":""}']}}
execute positioned ~140 ~ ~1 run function create_construction_site:test/reset {station:dump_body_closed_side,dx:9,dy:8,dz:12,fx:8,fy:7,fz:13}
# paint_shop
data modify storage create_construction_site:test text.paint_shop set value '[{"text":"Paint shop","color":"gold"},{"text":"\\n"},{"text":"Look at the rows: bare first, then the 16 colours. Craft a bare tool with a dye, and eight around one dye. Use a painted tool item on the Water Cauldron; put one on the Depot in front of the fan.","color":"white"},{"text":"\\n"},{"text":"Expect: bare tools are brass and painted ones their colour, all with dark steel teeth and blade edges; and tools of different colours next to each other do not join. Crafting gives one, or eight. The cauldron turns the whole stack bare and loses one level of water; the fan washes items on the Depot bare. Right-clicking a placed tool with a dye does nothing. Ponder (W) opens for every colour.","color":"white"}]'
fill ~152 ~ ~0 ~172 ~1 ~1 minecraft:air
setblock ~152 ~ ~1 minecraft:command_block[facing=up]{Command:"function create_construction_site:test/reset {station:paint_shop,dx:21,dy:6,dz:14,fx:20,fy:5,fz:15}"}
setblock ~152 ~ ~0 minecraft:stone_button[face=wall,facing=north]
setblock ~152 ~1 ~1 minecraft:oak_sign[rotation=8]{is_waxed:1b,front_text:{messages:['{"text":"Paint shop"}','{"text":"Button: reset"}','{"text":""}','{"text":""}']}}
execute positioned ~152 ~ ~1 run function create_construction_site:test/reset {station:paint_shop,dx:21,dy:6,dz:14,fx:20,fy:5,fz:15}
# metal_yard
data modify storage create_construction_site:test text.metal_yard set value '[{"text":"Metal block yard","color":"gold"},{"text":"\\n"},{"text":"Look at every block; walk on the tread plate and the grating; use the Wrench on hazard stripes and plates; place hazard plates on floors, walls and ceilings; place corrugated sheets against different faces.","color":"white"},{"text":"\\n"},{"text":"Expect: each colour of the Brass Plating wall has one frame around its 2 by 2 patch, the bare brass 3 by 3 wall one frame around the whole wall, and so do the Dark Steel and Polished Iron walls next to it; Dark Steel looks like the tools\' frames and teeth. Four Brass Sheets in a square give four Brass Plating. Hazard stripes flip with the Wrench; plates lie flat on their face, keep a small margin on their own and join into one panel with the same plate next to them (yellow patch on the floor, red-white panel on the stone wall), you walk into wall plates without bumping. Corrugated ribs run up or across and continue across block joins. You see the red wool through the grating and can walk on it.","color":"white"}]'
fill ~176 ~ ~0 ~205 ~1 ~1 minecraft:air
setblock ~176 ~ ~1 minecraft:command_block[facing=up]{Command:"function create_construction_site:test/reset {station:metal_yard,dx:30,dy:8,dz:15,fx:29,fy:7,fz:16}"}
setblock ~176 ~ ~0 minecraft:stone_button[face=wall,facing=north]
setblock ~176 ~1 ~1 minecraft:oak_sign[rotation=8]{is_waxed:1b,front_text:{messages:['{"text":"Metal block yard"}','{"text":"Button: reset"}','{"text":""}','{"text":""}']}}
execute positioned ~176 ~ ~1 run function create_construction_site:test/reset {station:metal_yard,dx:30,dy:8,dz:15,fx:29,fy:7,fz:16}
# plating_steps
data modify storage create_construction_site:test text.plating_steps set value '[{"text":"Plating slabs and stairs","color":"gold"},{"text":"\\n"},{"text":"Look at the rows (bare brass, red, dark steel, polished iron), then build your own with the slabs, stairs and blocks from the chests.","color":"white"},{"text":"\\n"},{"text":"Expect: a slab or stair joins its block and the other slabs and stairs of the same colour into one surface with one frame around it; the frame follows every edge where the surface ends, also the top of a slab\'s side and the foot of a step; different colours keep their own frames.","color":"white"}]'
fill ~209 ~ ~0 ~234 ~1 ~1 minecraft:air
setblock ~209 ~ ~1 minecraft:command_block[facing=up]{Command:"function create_construction_site:test/reset {station:plating_steps,dx:26,dy:8,dz:15,fx:25,fy:7,fz:16}"}
setblock ~209 ~ ~0 minecraft:stone_button[face=wall,facing=north]
setblock ~209 ~1 ~1 minecraft:oak_sign[rotation=8]{is_waxed:1b,front_text:{messages:['{"text":"Plating"}','{"text":"slabs and stairs"}','{"text":"Button: reset"}','{"text":""}']}}
execute positioned ~209 ~ ~1 run function create_construction_site:test/reset {station:plating_steps,dx:26,dy:8,dz:15,fx:25,fy:7,fz:16}
# site_yard
data modify storage create_construction_site:test text.site_yard set value '[{"text":"Site barriers","color":"gold"},{"text":"\\n"},{"text":"Use cones on cones to stack them (any height, the column grows into the blocks above), put a Warning Flasher or Warning Beacon on a cone, sneak with an empty hand to take it off (then the top cone; sneaking with a cone also takes the top cone off); place the lamps on floors, walls and ceilings too (see the iron truck). Place delineator posts and barrier boards in a row, site fence in runs, corners and stacked; use a carpet on the fence from either side and shears to take it off. Use the Wrench on a delineator post.","color":"white"},{"text":"\\n"},{"text":"Expect: cones stack, a column breaks all at once and drops every cone, lamps sit on top of the stack, blink and give light, the flasher\'s lens faces you; on their own the lamps stand, hang and stick to walls and stay when the block behind goes; delineator posts face you and the Wrench flips their stripes; barrier boards in a row join with one stand per joint and cannot be jumped over; the fence is two blocks tall, joins in every direction, has a post, feet and clamps on every other block of a straight run and at every end, corner and T, stacks without feet, and takes a tarp in the carpet\'s colour on the side you stand on.","color":"white"}]'
fill ~238 ~ ~0 ~263 ~1 ~1 minecraft:air
setblock ~238 ~ ~1 minecraft:command_block[facing=up]{Command:"function create_construction_site:test/reset {station:site_yard,dx:26,dy:10,dz:15,fx:25,fy:9,fz:16}"}
setblock ~238 ~ ~0 minecraft:stone_button[face=wall,facing=north]
setblock ~238 ~1 ~1 minecraft:oak_sign[rotation=8]{is_waxed:1b,front_text:{messages:['{"text":"Site barriers"}','{"text":"Button: reset"}','{"text":""}','{"text":""}']}}
execute positioned ~238 ~ ~1 run function create_construction_site:test/reset {station:site_yard,dx:26,dy:10,dz:15,fx:25,fy:9,fz:16}
tellraw @a[distance=..48] [{"text":"Test stations built.","color":"green"},{"text":"\n"},{"text":"Each station's button puts it back as it was and tells you in chat what to do and what should happen.","color":"white"}]
