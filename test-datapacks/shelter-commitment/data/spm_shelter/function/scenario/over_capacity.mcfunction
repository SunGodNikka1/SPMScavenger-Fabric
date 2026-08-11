function spm_shelter:arena/build
kill @e[type=playermob:player_mob,tag=spm_shelter_test]
execute at @e[type=marker,tag=spm_shelter_anchor,limit=1] positioned ~-2 ~ ~-3 run function spm_shelter:spawn/_summon_one_at
execute at @e[type=marker,tag=spm_shelter_anchor,limit=1] positioned ~-2 ~ ~-2 run function spm_shelter:spawn/_summon_one_at
execute at @e[type=marker,tag=spm_shelter_anchor,limit=1] positioned ~-2 ~ ~-1 run function spm_shelter:spawn/_summon_one_at
execute at @e[type=marker,tag=spm_shelter_anchor,limit=1] positioned ~-2 ~ ~ run function spm_shelter:spawn/_summon_one_at
execute at @e[type=marker,tag=spm_shelter_anchor,limit=1] positioned ~-2 ~ ~1 run function spm_shelter:spawn/_summon_one_at
execute at @e[type=marker,tag=spm_shelter_anchor,limit=1] positioned ~-2 ~ ~2 run function spm_shelter:spawn/_summon_one_at
execute at @e[type=marker,tag=spm_shelter_anchor,limit=1] positioned ~-2 ~ ~3 run function spm_shelter:spawn/_summon_one_at
execute at @e[type=marker,tag=spm_shelter_anchor,limit=1] positioned ~-4 ~ ~-2 run function spm_shelter:spawn/_summon_one_at
execute at @e[type=marker,tag=spm_shelter_anchor,limit=1] positioned ~-4 ~ ~-1 run function spm_shelter:spawn/_summon_one_at
execute at @e[type=marker,tag=spm_shelter_anchor,limit=1] positioned ~-4 ~ ~ run function spm_shelter:spawn/_summon_one_at
execute at @e[type=marker,tag=spm_shelter_anchor,limit=1] positioned ~-4 ~ ~1 run function spm_shelter:spawn/_summon_one_at
execute at @e[type=marker,tag=spm_shelter_anchor,limit=1] positioned ~-4 ~ ~2 run function spm_shelter:spawn/_summon_one_at
tellraw @s [{"text":"[SCR-2C] ","color":"gold"},{"text":"Twelve mobs: interior capacity fills first; surplus mobs may choose separated porch/other fallback and must not pile at one cell.","color":"aqua"}]
