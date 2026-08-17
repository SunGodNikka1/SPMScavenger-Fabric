# TEMPORARY V2-H PROOF SUPPORT. Reached only when BOTH merchants hold a real job-site memory.
say [VR-T2] both merchants have naturally acquired job sites.

# Freeze first, so opening the stalls cannot let them wander off the claimed site.
execute as @e[type=villager,tag=vrt2_merchant] run data merge entity @s {NoAI:1b}

# Then OPEN the stalls. They were warm-up machinery only: production navigates directly to the
# Villager entity until within 3 blocks, so a merchant left behind a wall either burns the path and
# approach budget on an unreachable target or permits an unnatural through-wall transaction -
# neither of which is the physical trade VR-T2 is supposed to prove.
#
# Walls only. The workstations and the merchants stay exactly where they are, and the claimed POIs
# are untouched.
fill ~-11 ~0 ~-2 ~-7 ~2 ~2 air replace smooth_stone
fill ~7 ~0 ~-2 ~11 ~2 ~2 air replace smooth_stone
setblock ~-9 ~ ~1 smithing_table
setblock ~9 ~ ~1 fletching_table
say [VR-T2] stalls opened, merchants frozen. Spawning the PlayerMob.

function spm_vrt2:spawn/_mob
say [VR-T2] world fixture ready. Now run: /spmscavenger debug vrt2 setup
