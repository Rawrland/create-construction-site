# The gallery_hydraulics showcase scene: /function create_construction_site:showcase/gallery_hydraulics
# Written by tools/showcase/generate_showcase.py: change the script, not this file.
execute unless entity @e[type=minecraft:marker,tag=ccs_studio] align xyz run summon minecraft:marker ~ ~ ~ {Tags:["ccs_studio"]}
execute at @e[type=minecraft:marker,tag=ccs_studio,limit=1] run function create_construction_site:showcase/_place {scene:"gallery_hydraulics",cx:13,cy:5,cz:2,fx:13,fy:1.5,fz:14}
