# VR-T3b — interrupt before COMMIT; SPM 0.89 target-edge caution witness.
function spm_vr:scenario/crop_managed_single
summon minecraft:zombie ~12 ~1 ~4 {Tags:["spm_vr.helper"],PersistenceRequired:1b}
tellraw @a [{"text":"[spm_vr] crop_interrupt_combat (VR-T3b) — provoke combat during PATHING; expect zero crop mutation","color":"gold"}]
