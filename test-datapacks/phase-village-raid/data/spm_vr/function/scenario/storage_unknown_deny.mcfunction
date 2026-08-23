# VR-T3h — unknown / unclassified container deny.
function spm_vr:_lib/reset
setblock ~10 ~ ~ minecraft:chest
item replace block ~10 ~ ~ container.0 with minecraft:iron_ingot 8
function spm_vr:_lib/spawn_ally
tellraw @a [{"text":"[spm_vr] storage_unknown_deny (VR-T3h) — chest outside settlement stub; expect unknown deny","color":"gold"}]
