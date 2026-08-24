function spm_vr:_lib/reset
tellraw @s [{"text":"[spm_vr] Fixture-tagged entities and schedules removed; placed world blocks are preserved because provenance-safe rollback is unavailable.","color":"yellow"}]
# Blocks are preserved. Use a disposable test world or restore its backup between clusters.
