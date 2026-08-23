# VR-T3b staged interrupt — zombie spawns after PATHING window, not at episode start.
summon minecraft:zombie ~12 ~1 ~4 {Tags:["spm_vr.helper"],PersistenceRequired:1b}
tellraw @a [{"text":"[spm_vr] VR-T3b staged interrupt — zombie spawned after PATHING delay","color":"yellow"}]
