execute store success score #ok spm_p1 run data modify entity @e[type=playermob:player_mob,tag=spm_p1_test,sort=nearest,limit=1] Inventory append value {id:"minecraft:coal",count:1b}
execute if score #ok spm_p1 matches 1 run tellraw @s [{"text":"[SPM Phase1] +1 coal","color":"green"}]
