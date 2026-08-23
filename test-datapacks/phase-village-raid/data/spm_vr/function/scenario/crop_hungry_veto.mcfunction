# VR-T3l — host HarvestCropsGoal veto in managed domain while wantsFood() and VillageWorkAdmission denied.
function spm_vr:_lib/reset
function spm_vr:_lib/setup_village_stub
setblock ~4 ~-1 ~4 minecraft:farmland
setblock ~4 ~ ~4 minecraft:carrots[age=7]
function spm_vr:_lib/spawn_ally
# wantsFood(): empty backpack (ForagePolicy — no carried food).
item replace entity @e[type=playermob:player_mob,tag=spm_vr.subject,limit=1] container.0 with minecraft:air
# VillageWorkAdmission denied via live mandatory gather claim — profile stays VILLAGE_ALLY.
fill ~11 ~ ~11 ~13 ~2 ~13 minecraft:oak_log replace air
tellraw @a [{"text":"[spm_vr] crop_hungry_veto (VR-T3l) — wantsFood + admission deny; host must not strip managed carrots","color":"gold"}]
