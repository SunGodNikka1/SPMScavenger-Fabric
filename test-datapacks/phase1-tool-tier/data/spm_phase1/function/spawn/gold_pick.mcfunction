function spm_phase1:spawn/_summon
item replace entity @e[type=playermob:player_mob,tag=spm_p1_test,limit=1] weapon.mainhand with minecraft:golden_pickaxe
tellraw @s [{"text":"[SPM Phase1] ","color":"gold"},{"text":"Gold pick (WOOD rank) → should still want stone upgrade.","color":"green"}]
