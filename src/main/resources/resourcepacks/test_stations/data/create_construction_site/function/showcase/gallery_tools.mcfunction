# The gallery_tools showcase scene: /function create_construction_site:showcase/gallery_tools
# Written by tools/showcase/generate_showcase.py: change the script, not this file.
execute unless entity @e[type=minecraft:marker,tag=ccs_studio] align xyz run summon minecraft:marker ~ ~ ~ {Tags:["ccs_studio"]}
execute at @e[type=minecraft:marker,tag=ccs_studio,limit=1] run function create_construction_site:showcase/_place {scene:"gallery_tools",cx:14,cy:5,cz:1.5,fx:13.5,fy:1,fz:15}
