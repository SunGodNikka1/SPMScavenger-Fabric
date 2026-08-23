# VR-T3k — two PlayerMobs contending for exactly one mature crop.
function spm_vr:_lib/reset
function spm_vr:_lib/setup_village_stub
setblock ~4 ~-1 ~4 minecraft:farmland
setblock ~4 ~ ~4 minecraft:wheat[age=7]
function spm_vr:_lib/spawn_ally
summon playermob:player_mob ~3 ~1 ~5 {Tags:["spm_vr.mob","spm_vr.contender"],PersistenceRequired:1b}
execute as @e[type=playermob:player_mob,tag=spm_vr.contender,limit=1] run spmscavenger village profile set @s village_ally
item replace entity @e[type=playermob:player_mob,tag=spm_vr.subject,limit=1] container.0 with minecraft:wheat_seeds 16
item replace entity @e[type=playermob:player_mob,tag=spm_vr.contender,limit=1] container.0 with minecraft:wheat_seeds 16
tellraw @a [{"text":"[spm_vr] crop_multi_mob (VR-T3k) — two allies, one crop; first commits, second revalidates","color":"gold"}]
