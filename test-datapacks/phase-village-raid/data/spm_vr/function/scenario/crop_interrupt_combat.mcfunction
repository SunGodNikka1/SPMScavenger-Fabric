# VR-T3b — interrupt before COMMIT; SPM 0.89 target-edge caution witness.
function spm_vr:scenario/crop_managed_single
# 120t ≈ VillageHarvestEpisodeGoal PATHING budget — interrupt after PATHING can begin, before COMMIT.
schedule function spm_vr:_lib/stage_interrupt_zombie 120t
tellraw @a [{"text":"[spm_vr] crop_interrupt_combat (VR-T3b) — staged combat interrupt; expect zero crop mutation","color":"gold"}]
