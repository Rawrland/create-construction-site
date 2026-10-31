# Builds the stations in a row in front of the player (toward +z, along +x).
# Written by tools/teststations/generate_test_stations.py: change the script, not this file.
gamerule commandBlockOutput false
# vehicle_bucket
data modify storage create_construction_site:test text.vehicle_bucket set value '[{"text":"Vehicle with bucket","color":"gold"},{"text":"\\n"},{"text":"Hold right-click on the Physics Assembler and pull its lever to assemble the vehicle (it is already glued). Put coal from the chest into the engine and drive into the dirt. If it drives away from the dirt, flip the REVERSE lever on the gearshift behind the engine.","color":"white"},{"text":"\\n"},{"text":"Expect: the bucket digs as on Create\'s machines and shows its load.","color":"white"}]'
fill ~0 ~ ~16 ~14 ~1 ~17 minecraft:air
setblock ~0 ~ ~17 minecraft:command_block[facing=up]{Command:"function create_construction_site:test/reset {station:vehicle_bucket,dx:15,dy:7,dz:9,fx:14,fy:6,fz:10}"}
setblock ~0 ~ ~16 minecraft:stone_button[face=wall,facing=north]
setblock ~0 ~1 ~17 minecraft:oak_sign[rotation=8]{is_waxed:1b,front_text:{messages:['{"text":"Vehicle"}','{"text":"with bucket"}','{"text":"Button: reset"}','{"text":""}']}}
execute positioned ~0 ~ ~17 run function create_construction_site:test/reset {station:vehicle_bucket,dx:15,dy:7,dz:9,fx:14,fy:6,fz:10}
# vehicle_blade
data modify storage create_construction_site:test text.vehicle_blade set value '[{"text":"Vehicle with blade","color":"gold"},{"text":"\\n"},{"text":"Hold right-click on the Physics Assembler and pull its lever to assemble the vehicle (it is already glued). Put coal from the chest into the engine and drive into the dirt. If it drives away from the dirt, flip the REVERSE lever on the gearshift behind the engine.","color":"white"},{"text":"\\n"},{"text":"Expect: the blade pushes the dirt as on Create\'s machines.","color":"white"}]'
fill ~18 ~ ~16 ~32 ~1 ~17 minecraft:air
setblock ~18 ~ ~17 minecraft:command_block[facing=up]{Command:"function create_construction_site:test/reset {station:vehicle_blade,dx:15,dy:7,dz:9,fx:14,fy:6,fz:10}"}
setblock ~18 ~ ~16 minecraft:stone_button[face=wall,facing=north]
setblock ~18 ~1 ~17 minecraft:oak_sign[rotation=8]{is_waxed:1b,front_text:{messages:['{"text":"Vehicle"}','{"text":"with blade"}','{"text":"Button: reset"}','{"text":""}']}}
execute positioned ~18 ~ ~17 run function create_construction_site:test/reset {station:vehicle_blade,dx:15,dy:7,dz:9,fx:14,fy:6,fz:10}
# dump_body_swivel
data modify storage create_construction_site:test text.dump_body_swivel set value '[{"text":"Dump Body: Swivel Bearing","color":"gold"},{"text":"\\n"},{"text":"Flip the START lever off: the Create Aeronautics Swivel Bearing makes the glued Dump Body a simulated contraption and tips it (all sides were opened with the Wrench). Flip REVERSE to tip it the other way.","color":"white"},{"text":"\\n"},{"text":"Expect: as on Create\'s bearing: from about 20 degrees the low side\'s tailgate opens, the dirt slides and streams out from that side, and the heap grows away from the body.","color":"white"}]'
fill ~36 ~ ~16 ~44 ~1 ~17 minecraft:air
setblock ~36 ~ ~17 minecraft:command_block[facing=up]{Command:"function create_construction_site:test/reset {station:dump_body_swivel,dx:9,dy:8,dz:12,fx:8,fy:7,fz:13}"}
setblock ~36 ~ ~16 minecraft:stone_button[face=wall,facing=north]
setblock ~36 ~1 ~17 minecraft:oak_sign[rotation=8]{is_waxed:1b,front_text:{messages:['{"text":"Dump Body:"}','{"text":"Swivel Bearing"}','{"text":"Button: reset"}','{"text":""}']}}
execute positioned ~36 ~ ~17 run function create_construction_site:test/reset {station:dump_body_swivel,dx:9,dy:8,dz:12,fx:8,fy:7,fz:13}
# hydraulic_cylinder
data modify storage create_construction_site:test text.hydraulic_cylinder set value '[{"text":"Hydraulic Cylinder","color":"gold"},{"text":"\\n"},{"text":"Hold right-click on the Physics Assembler and pull its lever to assemble the load (it is already glued). Then flip the LEVER beside the cylinder on, and later off. Right-click a mount with an empty hand for the settings window.","color":"white"},{"text":"\\n"},{"text":"Expect: lever on, the load rises smoothly by exactly one block in about a second and holds there; lever off, it goes back down and rests. No jumping, no bouncing, no shaking. The cylinder is drawn between the pins and follows. If nothing moves: rejoin the world once.","color":"white"}]'
fill ~48 ~ ~16 ~56 ~1 ~17 minecraft:air
setblock ~48 ~ ~17 minecraft:command_block[facing=up]{Command:"function create_construction_site:test/reset {station:hydraulic_cylinder,dx:9,dy:9,dz:9,fx:8,fy:8,fz:10}"}
setblock ~48 ~ ~16 minecraft:stone_button[face=wall,facing=north]
setblock ~48 ~1 ~17 minecraft:oak_sign[rotation=8]{is_waxed:1b,front_text:{messages:['{"text":"Hydraulic"}','{"text":"Cylinder"}','{"text":"Button: reset"}','{"text":""}']}}
execute positioned ~48 ~ ~17 run function create_construction_site:test/reset {station:hydraulic_cylinder,dx:9,dy:9,dz:9,fx:8,fy:8,fz:10}
# rope
data modify storage create_construction_site:test text.rope set value '[{"text":"Bucket: rope","color":"gold"},{"text":"\\n"},{"text":"Take the Rope Coupling from the chest. Use it on the bucket\'s middle tooth, then on the Rope Connector on the log.","color":"white"},{"text":"\\n"},{"text":"Expect: the rope ties to the tooth ring; each block of the bucket\'s width takes one rope; a plain block does not take a rope.","color":"white"}]'
fill ~60 ~ ~16 ~68 ~1 ~17 minecraft:air
setblock ~60 ~ ~17 minecraft:command_block[facing=up]{Command:"function create_construction_site:test/reset {station:rope,dx:9,dy:6,dz:9,fx:8,fy:5,fz:10}"}
setblock ~60 ~ ~16 minecraft:stone_button[face=wall,facing=north]
setblock ~60 ~1 ~17 minecraft:oak_sign[rotation=8]{is_waxed:1b,front_text:{messages:['{"text":"Bucket:"}','{"text":"rope"}','{"text":"Button: reset"}','{"text":""}']}}
execute positioned ~60 ~ ~17 run function create_construction_site:test/reset {station:rope,dx:9,dy:6,dz:9,fx:8,fy:5,fz:10}
tellraw @a[distance=..48] [{"text":"Test stations built.","color":"green"},{"text":"\n"},{"text":"Each station's button puts it back as it was and tells you in chat what to do and what should happen.","color":"white"}]
