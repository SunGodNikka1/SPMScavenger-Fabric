# RT-PERF-F1 setup: two mobs with charcoal smelt demand, no furnace in arena
function spm_phase4:arena/build
kill @e[type=playermob:player_mob,tag=spm_p4_perf]
playermob summon Steve ~2 ~ ~2 named P4RaceA
playermob summon Steve ~5 ~ ~2 named P4RaceB
tag @e[type=playermob:player_mob,distance=..8] add spm_p4_perf
playermob stay P4RaceA here 32
playermob stay P4RaceB here 32
give @e[type=playermob:player_mob,tag=spm_p4_perf,limit=1,sort=nearest] oak_log 16
give @e[type=playermob:player_mob,tag=spm_p4_perf,limit=1,sort=furthest] oak_log 16
tellraw @s [{"text":"[SPM Phase4] ","color":"gold"},{"text":"Furnace race setup: wait for ABSENT_RECENT on both, then place_shared_furnace","color":"yellow"}]
