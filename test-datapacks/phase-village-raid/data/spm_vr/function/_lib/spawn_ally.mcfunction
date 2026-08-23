# Spawn one PlayerMob subject and assign VILLAGE_ALLY via production command seam.
summon playermob:player_mob ~2 ~1 ~ {Tags:["spm_vr.mob","spm_vr.subject"],PersistenceRequired:1b}
execute as @e[type=playermob:player_mob,tag=spm_vr.subject,limit=1] run spmscavenger village profile set @s village_ally
