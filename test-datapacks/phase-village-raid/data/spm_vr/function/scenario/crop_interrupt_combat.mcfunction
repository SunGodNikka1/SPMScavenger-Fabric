# VR-T3b — interrupt before COMMIT; SPM 0.96 reaction/target-edge timing witness.
function spm_vr:scenario/crop_managed_single
# The campaign controller invokes the declared zombie helper at exact window-open +120t.
# This avoids firing during night/Gate-0 bootstrap and never sets the production combat target.
tellraw @a [{"text":"[spm_vr] crop_interrupt_combat (VR-T3b) — staged combat interrupt; expect zero crop mutation","color":"gold"}]
