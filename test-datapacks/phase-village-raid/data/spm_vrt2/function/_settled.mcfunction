# TEMPORARY V2-H PROOF SUPPORT. Reached only when BOTH named merchants exist AND hold a real
# job-site memory.
say [VR-T2] toolsmith and fletcher have both naturally acquired job sites.

# Freeze first, so opening the stalls cannot let them wander off the site they just claimed.
execute as @e[type=villager,tag=vrt2_merchant] run data merge entity @s {NoAI:1b}

# Then open the stalls. They were warm-up machinery only: production navigates directly to the
# Villager entity until within 3 blocks, so a merchant left walled in either burns the path and
# approach budget on an unreachable target - which the round would demote as "unreachable villager",
# a correct response to a fixture artefact - or permits an unnatural through-wall transaction.
#
# `replace smooth_stone` touches walls and nothing else. The claimed workstations are deliberately
# NOT re-set afterwards: re-asserting a block that a villager has already claimed raises a question
# about its POI record that this fixture has no reason to ask.
fill ~-11 ~0 ~-2 ~-7 ~2 ~2 air replace smooth_stone
fill ~7 ~0 ~-2 ~11 ~2 ~2 air replace smooth_stone
say [VR-T2] stalls opened, merchants frozen, claimed workstations untouched.

function spm_vrt2:spawn/_mob
say [VR-T2] world fixture ready. Now run: /spmscavenger debug vrt2 setup
