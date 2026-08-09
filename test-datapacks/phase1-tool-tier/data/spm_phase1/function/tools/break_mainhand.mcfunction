item modify entity @e[type=playermob:player_mob,tag=spm_p1_test,limit=1] weapon.mainhand set components {"minecraft:damage":9999}
tellraw @s [{"text":"[SPM Phase1] ","color":"gold"},{"text":"TT-5: main-hand tool broken — should re-craft.","color":"green"}]
