function spm_phase1:spawn/_summon
data modify entity @e[type=playermob:player_mob,tag=spm_p1_test,limit=1] Inventory set value [{Slot:0b,id:"minecraft:cobblestone",count:3b},{Slot:1b,id:"minecraft:stick",count:2b},{Slot:2b,id:"minecraft:wooden_axe",count:1b}]
item replace entity @e[type=playermob:player_mob,tag=spm_p1_test,limit=1] weapon.mainhand with minecraft:stone_pickaxe
tellraw @s [{"text":"[SPM Phase1] ","color":"gold"},{"text":"Should craft stone axe.","color":"green"}]
