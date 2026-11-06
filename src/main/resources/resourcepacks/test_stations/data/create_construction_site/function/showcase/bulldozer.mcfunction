# The bulldozer showcase scene: /function create_construction_site:showcase/bulldozer
# Written by tools/showcase/generate_showcase.py: change the script, not this file.
execute unless entity @e[type=minecraft:marker,tag=ccs_studio] align xyz run summon minecraft:marker ~ ~ ~ {Tags:["ccs_studio"]}
execute at @e[type=minecraft:marker,tag=ccs_studio,limit=1] run function create_construction_site:showcase/_place {scene:"bulldozer",cx:4,cy:5,cz:27,fx:12.5,fy:1.5,fz:15.5}
