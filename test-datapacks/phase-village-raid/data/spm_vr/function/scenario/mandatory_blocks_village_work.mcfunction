# VR-T3j — mandatory ownership blocks village work (no fake authority injection).
function spm_vr:_lib/reset
function spm_vr:_lib/setup_village_stub
setblock ~4 ~-1 ~4 minecraft:farmland
setblock ~4 ~ ~4 minecraft:wheat[age=7]
function spm_vr:_lib/spawn_ally
item replace entity @e[type=playermob:player_mob,tag=spm_vr.subject,limit=1] container.0 with minecraft:wheat_seeds 16
fill ~7 ~ ~7 ~9 ~ ~9 minecraft:oak_log replace air
tellraw @a [{"text":"[spm_vr] mandatory_blocks_village_work (VR-T3j) — gather claim should deny village crop work","color":"gold"}]
