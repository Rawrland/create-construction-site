# Resets one test station. Run from its command block, with the station's name and box:
#   function create_construction_site:test/reset {station:<name>,dx:..,dy:..,dz:..,fx:..,fy:..,fz:..}
# Written by tools/teststations/generate_test_stations.py: change the script, not this file.
$execute positioned ~0 ~-1 ~2 run kill @e[type=!minecraft:player,dx=$(dx),dy=$(dy),dz=$(dz)]
$fill ~0 ~-1 ~2 ~$(fx) ~$(fy) ~$(fz) minecraft:air
$execute positioned ~0 ~-1 ~2 run kill @e[type=minecraft:item,dx=$(dx),dy=$(dy),dz=$(dz)]
$place template create_construction_site:test/$(station) ~0 ~-1 ~2
$tellraw @a[distance=..48] {"storage":"create_construction_site:test","nbt":"text.$(station)","interpret":true}
