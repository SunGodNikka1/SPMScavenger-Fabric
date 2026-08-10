kill @e[type=playermob:player_mob,tag=spm_p4_perf]
execute if entity @e[type=marker,tag=spm_p4_anchor,limit=1] at @e[type=marker,tag=spm_p4_anchor,limit=1] run function spm_phase4:spawn/_grid_10
execute unless entity @e[type=marker,tag=spm_p4_anchor,limit=1] as @p at @s run function spm_phase4:spawn/_grid_10
tellraw @s [{"text":"[SPM Phase4] ","color":"gold"},{"text":"Spawned 10 PlayerMobs. Warm up 60s before Spark.","color":"green"}]
