function spm_shelter:arena/build
kill @e[type=playermob:player_mob,tag=spm_shelter_test]
execute at @e[type=marker,tag=spm_shelter_anchor,limit=1] positioned ~ ~ ~-2 run function spm_shelter:spawn/_summon_one_at
execute at @e[type=marker,tag=spm_shelter_anchor,limit=1] positioned ~ ~ ~-1 run function spm_shelter:spawn/_summon_one_at
execute at @e[type=marker,tag=spm_shelter_anchor,limit=1] positioned ~ ~ ~1 run function spm_shelter:spawn/_summon_one_at
execute at @e[type=marker,tag=spm_shelter_anchor,limit=1] positioned ~ ~ ~2 run function spm_shelter:spawn/_summon_one_at
tellraw @s [{"text":"[SCR-2B] ","color":"gold"},{"text":"Four mobs: they must reserve separated interior standing areas rather than one best block.","color":"aqua"}]
