# The metal showcase scene: /function create_construction_site:showcase/metal
# Written by tools/showcase/generate_showcase.py: change the script, not this file.
execute unless entity @e[type=minecraft:marker,tag=ccs_studio] align xyz run summon minecraft:marker ~ ~ ~ {Tags:["ccs_studio"]}
execute at @e[type=minecraft:marker,tag=ccs_studio,limit=1] run function create_construction_site:showcase/_place {scene:"metal",cx:14,cy:4.5,cz:3,fx:14,fy:1.5,fz:18}
