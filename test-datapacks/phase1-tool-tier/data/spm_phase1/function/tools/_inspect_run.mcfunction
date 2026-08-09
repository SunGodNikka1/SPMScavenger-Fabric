tellraw @s [{"text":"[SPM Phase1] ","color":"gold"},{"text":"P1Test pack:","color":"white"}]
execute store result score #cobble spm_p1 run data get entity @e[type=playermob:player_mob,tag=spm_p1_test,limit=1] Inventory[{id:"minecraft:cobblestone"}].count 0
execute store result score #sticks spm_p1 run data get entity @e[type=playermob:player_mob,tag=spm_p1_test,limit=1] Inventory[{id:"minecraft:stick"}].count 0
execute store result score #torches spm_p1 run data get entity @e[type=playermob:player_mob,tag=spm_p1_test,limit=1] Inventory[{id:"minecraft:torch"}].count 0
execute store result score #wpick spm_p1 run data get entity @e[type=playermob:player_mob,tag=spm_p1_test,limit=1] Inventory[{id:"minecraft:wooden_pickaxe"}].count 0
execute store result score #spick spm_p1 run data get entity @e[type=playermob:player_mob,tag=spm_p1_test,limit=1] Inventory[{id:"minecraft:stone_pickaxe"}].count 0
execute store result score #saxe spm_p1 run data get entity @e[type=playermob:player_mob,tag=spm_p1_test,limit=1] Inventory[{id:"minecraft:stone_axe"}].count 0
tellraw @s [{"text":"  cobble ","color":"gray"},{"score":{"name":"#cobble","objective":"spm_p1"},"color":"yellow"},{"text":" sticks ","color":"gray"},{"score":{"name":"#sticks","objective":"spm_p1"},"color":"yellow"},{"text":" torches ","color":"gray"},{"score":{"name":"#torches","objective":"spm_p1"},"color":"yellow"}]
tellraw @s [{"text":"  w-pick ","color":"gray"},{"score":{"name":"#wpick","objective":"spm_p1"},"color":"yellow"},{"text":" s-pick ","color":"gray"},{"score":{"name":"#spick","objective":"spm_p1"},"color":"yellow"},{"text":" s-axe ","color":"gray"},{"score":{"name":"#saxe","objective":"spm_p1"},"color":"yellow"}]
