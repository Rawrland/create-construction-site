# Builds one showcase scene at the studio marker. Called by the scene functions.
# Written by tools/showcase/generate_showcase.py: change the script, not this file.
execute positioned ~ ~-1 ~ run kill @e[type=!minecraft:player,type=!minecraft:marker,dx=27,dy=16,dz=27]
fill ~ ~-3 ~ ~27 ~-2 ~27 minecraft:dirt
fill ~ ~ ~ ~27 ~14 ~27 minecraft:air
$place template create_construction_site:showcase/$(scene) ~ ~-1 ~
execute positioned ~ ~-1 ~ run kill @e[type=minecraft:item,dx=27,dy=16,dz=27]
time set noon
weather clear
gamerule doDaylightCycle false
gamerule doWeatherCycle false
gamerule doMobSpawning false
gamemode spectator @s
$tp @s ~$(cx) ~$(cy) ~$(cz) facing ~$(fx) ~$(fy) ~$(fz)
tellraw @s [{"text":"Showcase: ","color":"gold"},{"text":"F1 hides the HUD, F2 takes the screenshot. /gamemode creative to leave spectator mode.","color":"white"}]
