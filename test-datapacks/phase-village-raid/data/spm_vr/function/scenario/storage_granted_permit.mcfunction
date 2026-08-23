# VR-T3i — explicit storage grant via production command seam (not implicit shared chest).
function spm_vr:_lib/reset
function spm_vr:_lib/setup_village_stub
setblock ~5 ~ ~ minecraft:chest
item replace block ~5 ~ ~ container.0 with minecraft:bread 16
function spm_vr:_lib/spawn_ally
execute as @e[type=playermob:player_mob,tag=spm_vr.subject,limit=1] run spmscavenger village storage own @s ~5 ~ ~
tellraw @a [{"text":"[spm_vr] storage_granted_permit (VR-T3i) — own grant on exact mob+chest; expect permit","color":"gold"}]
