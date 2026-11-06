# Moves the showcase studio to where you stand (the next scene is built here).
# Written by tools/showcase/generate_showcase.py: change the script, not this file.
kill @e[type=minecraft:marker,tag=ccs_studio]
execute align xyz run summon minecraft:marker ~ ~ ~ {Tags:["ccs_studio"]}
tellraw @s {"text":"Showcase studio moved here.","color":"gold"}
