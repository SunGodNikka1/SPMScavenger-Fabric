execute unless data storage spm_phase1:main initialized run function spm_phase1:_init_scoreboard
execute unless entity @e[type=playermob:player_mob,tag=spm_p1_test,limit=1] run tellraw @s [{"text":"[SPM Phase1] ","color":"red"},{"text":"No P1Test mob.","color":"white"}]
execute if entity @e[type=playermob:player_mob,tag=spm_p1_test,limit=1] run function spm_phase1:tools/_inspect_run
