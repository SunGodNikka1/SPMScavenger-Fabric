# Clear prior VR session entities and tags (idempotent).
kill @e[tag=spm_vr.mob]
kill @e[tag=spm_vr.helper]
kill @e[type=minecraft:villager,tag=spm_vr.helper]
kill @e[type=minecraft:zombie,tag=spm_vr.helper]
scoreboard objectives remove spm_vr.tick
scoreboard objectives add spm_vr.tick dummy
