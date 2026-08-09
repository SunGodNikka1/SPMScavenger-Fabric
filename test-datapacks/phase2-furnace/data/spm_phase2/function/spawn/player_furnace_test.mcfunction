function spm_phase2:spawn/_summon
data modify entity @e[type=playermob:player_mob,tag=spm_p2_test,limit=1] Inventory set value [{Slot:0b,id:"minecraft:oak_log",count:6b},{Slot:1b,id:"minecraft:stick",count:8b},{Slot:2b,id:"minecraft:cobblestone",count:8b}]
item replace entity @e[type=playermob:player_mob,tag=spm_p2_test,limit=1] weapon.mainhand with minecraft:wooden_axe
tellraw @s [{"text":"[SPM Phase2] ","color":"aqua"},{"text":"RT-F3: skip busy furnace at +6; do not steal coal.","color":"green"}]
