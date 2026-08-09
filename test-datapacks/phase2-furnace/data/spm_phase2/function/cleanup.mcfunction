kill @e[type=playermob:player_mob,tag=spm_p2_test]
kill @e[type=playermob:player_mob,tag=spm_p2_b]
kill @e[type=marker,tag=spm_p2_anchor]
tellraw @s [{"text":"[SPM Phase2] ","color":"aqua"},{"text":"Test mobs and anchor removed.","color":"green"}]
