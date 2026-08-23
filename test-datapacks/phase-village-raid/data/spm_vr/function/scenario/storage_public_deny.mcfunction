# VR-T3g — public settlement storage deny (no grant).
function spm_vr:_lib/reset
function spm_vr:_lib/setup_village_stub
setblock ~8 ~ ~ minecraft:chest
item replace block ~8 ~ ~ container.0 with minecraft:bread 16
function spm_vr:_lib/spawn_ally
tellraw @a [{"text":"[spm_vr] storage_public_deny (VR-T3g) — chest is village-public; expect deny without grant","color":"gold"}]
