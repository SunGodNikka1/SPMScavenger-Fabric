# Register occupied HOME POI — villagers must claim beds (IS_OCCUPIED), not merely exist near unclaimed beds.
tp @e[type=minecraft:villager,tag=spm_vr.villager1,limit=1] ~-3.5 ~1 ~1.5
tp @e[type=minecraft:villager,tag=spm_vr.villager2,limit=1] ~-5.5 ~1 ~1.5
execute as @e[type=minecraft:villager,tag=spm_vr.villager1,limit=1] run data merge entity @s {Brain:{memories:{"minecraft:home":{value:{pos:[I;-3,1,1],dimension:"minecraft:overworld"},ttl:24000}}}}
execute as @e[type=minecraft:villager,tag=spm_vr.villager2,limit=1] run data merge entity @s {Brain:{memories:{"minecraft:home":{value:{pos:[I;-5,1,1],dimension:"minecraft:overworld"},ttl:24000}}}}
execute as @e[type=minecraft:villager,tag=spm_vr.villager1,limit=1] run data merge entity @s {SleepingX:-3,SleepingY:1,SleepingZ:1}
execute as @e[type=minecraft:villager,tag=spm_vr.villager2,limit=1] run data merge entity @s {SleepingX:-5,SleepingY:1,SleepingZ:1}
