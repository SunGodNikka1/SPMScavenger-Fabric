# VR-T3d — compost seed surplus (CompostReserveModel authority).
function spm_vr:_lib/reset
function spm_vr:_lib/setup_village_stub
setblock ~6 ~ ~ minecraft:composter
function spm_vr:_lib/spawn_ally
item replace entity @e[type=playermob:player_mob,tag=spm_vr.subject,limit=1] container.0 with minecraft:wheat_seeds 32
tellraw @a [{"text":"[spm_vr] compost_seed_surplus (VR-T3d) — observe one compost activation; seeds must not be consumed below reserve","color":"gold"}]
