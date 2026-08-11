kill @e[type=playermob:player_mob,tag=spm_shelter_test]
execute at @e[type=marker,tag=spm_shelter_anchor,limit=1] run function spm_shelter:arena/_clear
kill @e[type=marker,tag=spm_shelter_anchor]
tellraw @s [{"text":"[SPM Shelter] Test mob, arena, and anchor removed.","color":"green"}]
