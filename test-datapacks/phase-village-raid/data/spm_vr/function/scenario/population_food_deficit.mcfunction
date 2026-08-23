# VR-T3e — population food deficit (one handoff, not a gift loop).
function spm_vr:_lib/reset
function spm_vr:_lib/setup_village_stub
function spm_vr:_lib/spawn_ally
# Second ally with low food; first ally should have bread to share once.
summon playermob:player_mob ~-2 ~1 ~ {Tags:["spm_vr.mob","spm_vr.recipient"],PersistenceRequired:1b}
execute as @e[type=playermob:player_mob,tag=spm_vr.recipient,limit=1] run spmscavenger village profile set @s village_ally
item replace entity @e[type=playermob:player_mob,tag=spm_vr.subject,limit=1] container.0 with minecraft:bread 8
tellraw @a [{"text":"[spm_vr] population_food_deficit (VR-T3e) — observe single deficit handoff; no repeated gifting loop","color":"gold"}]
