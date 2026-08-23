# VR-T3e — PlayerMob → adult villager food handoff (V3-E), not mob-to-mob gifting.
function spm_vr:_lib/reset
function spm_vr:_lib/setup_village_stub
function spm_vr:_lib/spawn_ally
# Recipient must be a villager with food need (wantsMoreFood, not breed-ready).
execute as @e[type=minecraft:villager,tag=spm_vr.villager2,limit=1] run data modify entity @s Inventory set value []
execute as @e[type=minecraft:villager,tag=spm_vr.villager2,limit=1] run tag @s add spm_vr.food_recipient
item replace entity @e[type=playermob:player_mob,tag=spm_vr.subject,limit=1] container.0 with minecraft:bread 8
tellraw @a [{"text":"[spm_vr] population_food_deficit (VR-T3e) — ally delivers once to villager recipient; no gift loop","color":"gold"}]
