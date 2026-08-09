function spm_phase2:spawn/_summon_b
data modify entity @e[type=playermob:player_mob,tag=spm_p2_b,limit=1] Inventory set value [{Slot:0b,id:"minecraft:raw_iron",count:4b},{Slot:1b,id:"minecraft:coal",count:4b},{Slot:2b,id:"minecraft:stick",count:4b},{Slot:3b,id:"minecraft:cobblestone",count:8b}]
item replace entity @e[type=playermob:player_mob,tag=spm_p2_b,limit=1] weapon.mainhand with minecraft:stone_pickaxe
tellraw @s [{"text":"[SPM Phase2] ","color":"aqua"},{"text":"RT-F5: P2B (IRON cap required) — one furnace claimant only.","color":"green"}]
