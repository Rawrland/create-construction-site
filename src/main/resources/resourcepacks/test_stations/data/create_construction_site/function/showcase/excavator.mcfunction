# The excavator showcase scene: /function create_construction_site:showcase/excavator
# Written by tools/showcase/generate_showcase.py: change the script, not this file.
execute unless entity @e[type=minecraft:marker,tag=ccs_studio] align xyz run summon minecraft:marker ~ ~ ~ {Tags:["ccs_studio"]}
execute at @e[type=minecraft:marker,tag=ccs_studio,limit=1] run function create_construction_site:showcase/_place {scene:"excavator",cx:3.5,cy:6.5,cz:30,fx:12,fy:3,fz:15.5}
