function spm_phase1:spawn/_summon
data modify entity @e[type=playermob:player_mob,tag=spm_p1_test,limit=1] Inventory set value [{Slot:0b,id:"minecraft:torch",count:8b},{Slot:1b,id:"minecraft:wooden_pickaxe",count:1b}]
tellraw @s [{"text":"[SPM Phase1] ","color":"gold"},{"text":"TT-4: 8 torches → gather should stop.","color":"green"}]
