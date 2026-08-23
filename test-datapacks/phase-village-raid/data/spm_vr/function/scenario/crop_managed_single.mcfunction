# VR-T3a — managed harvest→replant (single crop).
function spm_vr:_lib/reset
function spm_vr:_lib/setup_village_stub
setblock ~4 ~-1 ~4 minecraft:farmland
setblock ~4 ~ ~4 minecraft:wheat[age=7]
function spm_vr:_lib/spawn_ally
item replace entity @e[type=playermob:player_mob,tag=spm_vr.subject,limit=1] container.0 with minecraft:wheat_seeds 16
tellraw @a [{"text":"[spm_vr] crop_managed_single (VR-T3a) — observe replant at (~4,~,~4)","color":"gold"}]
