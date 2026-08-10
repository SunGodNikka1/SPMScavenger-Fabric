kill @e[type=playermob:player_mob,tag=spm_p4_perf]
execute if entity @e[type=marker,tag=spm_p4_anchor,limit=1] at @e[type=marker,tag=spm_p4_anchor,limit=1] positioned ~0 ~ ~ run function spm_phase4:spawn/_grid_1
execute unless entity @e[type=marker,tag=spm_p4_anchor,limit=1] as @p at @s anchored feet positioned ~0 ~ ~ run function spm_phase4:spawn/_grid_1
tellraw @s [{"text":"[SPM Phase4] ","color":"gold"},{"text":"Spawned 1 PlayerMob (P4Perf*). Warm up 60s before Spark.","color":"green"}]
