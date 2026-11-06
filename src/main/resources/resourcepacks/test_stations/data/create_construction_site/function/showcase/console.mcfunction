# The console showcase scene: /function create_construction_site:showcase/console
# Written by tools/showcase/generate_showcase.py: change the script, not this file.
execute unless entity @e[type=minecraft:marker,tag=ccs_studio] align xyz run summon minecraft:marker ~ ~ ~ {Tags:["ccs_studio"]}
execute at @e[type=minecraft:marker,tag=ccs_studio,limit=1] run function create_construction_site:showcase/_place {scene:"console",cx:14.5,cy:2.1,cz:12.6,fx:14.5,fy:0.7,fz:15.6}
