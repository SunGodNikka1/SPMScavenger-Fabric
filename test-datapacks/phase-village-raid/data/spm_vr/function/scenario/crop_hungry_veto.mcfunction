# VR-T3l — hungry veto (no crop work while starving).
function spm_vr:_lib/reset
function spm_vr:_lib/setup_village_stub
setblock ~4 ~-1 ~4 minecraft:farmland
setblock ~4 ~ ~4 minecraft:wheat[age=7]
function spm_vr:_lib/spawn_ally
effect give @e[type=playermob:player_mob,tag=spm_vr.subject,limit=1] minecraft:hunger 600 255 true
tellraw @a [{"text":"[spm_vr] crop_hungry_veto (VR-T3l) — starving ally must not harvest/replant","color":"gold"}]
