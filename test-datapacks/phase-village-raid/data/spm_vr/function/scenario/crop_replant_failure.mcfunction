# VR-T3c — pre-COMMIT invalidation only (task-55 atomic contract: no repair phase).
function spm_vr:_lib/reset
function spm_vr:_lib/setup_village_stub
setblock ~4 ~-1 ~4 minecraft:farmland
setblock ~4 ~ ~4 minecraft:wheat[age=7]
function spm_vr:_lib/spawn_ally
# No seeds: preflight must ABORT before any harvest mutation.
tellraw @a [{"text":"[spm_vr] crop_replant_failure (VR-T3c) — expect ABORT/zero mutation; optional INVARIANT_FAILURE only if transaction invariant induced","color":"gold"}]
