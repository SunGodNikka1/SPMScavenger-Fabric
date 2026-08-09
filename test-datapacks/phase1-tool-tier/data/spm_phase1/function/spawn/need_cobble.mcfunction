function spm_phase1:spawn/_summon
data modify entity @e[type=playermob:player_mob,tag=spm_p1_test,limit=1] Inventory set value [{Slot:0b,id:"minecraft:stick",count:4b}]
item replace entity @e[type=playermob:player_mob,tag=spm_p1_test,limit=1] weapon.mainhand with minecraft:wooden_pickaxe
tellraw @s [{"text":"[SPM Phase1] ","color":"gold"},{"text":"TT-1: wood pick + sticks → mine stone at anchor+5.","color":"green"}]
