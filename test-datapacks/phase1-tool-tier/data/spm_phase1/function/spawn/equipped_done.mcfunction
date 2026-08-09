function spm_phase1:spawn/_summon
data modify entity @e[type=playermob:player_mob,tag=spm_p1_test,limit=1] Inventory set value [{Slot:0b,id:"minecraft:stone_axe",count:1b}]
item replace entity @e[type=playermob:player_mob,tag=spm_p1_test,limit=1] weapon.mainhand with minecraft:stone_pickaxe
tellraw @s [{"text":"[SPM Phase1] ","color":"gold"},{"text":"TT-2: both stone tools owned → no cobble gather.","color":"green"}]
