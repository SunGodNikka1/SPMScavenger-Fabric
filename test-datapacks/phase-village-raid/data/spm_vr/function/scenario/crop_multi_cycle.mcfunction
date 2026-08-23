# VR-T3m — multi-cycle crop depletion witness.
function spm_vr:_lib/reset
function spm_vr:_lib/setup_village_stub
fill ~3 ~-1 ~3 ~7 ~-1 ~3 minecraft:farmland
setblock ~3 ~ ~3 minecraft:wheat[age=7]
setblock ~4 ~ ~3 minecraft:wheat[age=7]
setblock ~5 ~ ~3 minecraft:wheat[age=7]
function spm_vr:_lib/spawn_ally
item replace entity @e[type=playermob:player_mob,tag=spm_vr.subject,limit=1] container.0 with minecraft:wheat_seeds 4
tellraw @a [{"text":"[spm_vr] crop_multi_cycle (VR-T3m) — observe multiple cycles until seed reserve blocks replant","color":"gold"}]
