# VR-T3b staged interrupt — zombie spawns after PATHING window, not at episode start.
execute at @e[type=playermob:player_mob,tag=spm_vr.subject,limit=1] run summon minecraft:zombie ~10 ~ ~4 {Tags:["spm_vr.helper"],PersistenceRequired:1b}
tellraw @a [{"text":"[spm_vr] VR-T3b staged interrupt — zombie spawned after PATHING delay","color":"yellow"}]
