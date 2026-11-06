# The dump_truck showcase scene: /function create_construction_site:showcase/dump_truck
# Written by tools/showcase/generate_showcase.py: change the script, not this file.
execute unless entity @e[type=minecraft:marker,tag=ccs_studio] align xyz run summon minecraft:marker ~ ~ ~ {Tags:["ccs_studio"]}
execute at @e[type=minecraft:marker,tag=ccs_studio,limit=1] run function create_construction_site:showcase/_place {scene:"dump_truck",cx:3,cy:5.5,cz:27,fx:13,fy:2,fz:15.5}
