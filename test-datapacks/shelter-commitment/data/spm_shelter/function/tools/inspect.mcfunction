tellraw @s [{"text":"[SPM Shelter] Inspecting nearest tagged test mob","color":"gold"}]
data get entity @e[type=playermob:player_mob,tag=spm_shelter_test,sort=nearest,limit=1] Pos
data get entity @e[type=playermob:player_mob,tag=spm_shelter_test,sort=nearest,limit=1] Pose
execute at @e[type=marker,tag=spm_shelter_anchor,limit=1] if block ~4 ~1 ~ oak_door[open=true] run tellraw @s {"text":"Door: OPEN","color":"green"}
execute at @e[type=marker,tag=spm_shelter_anchor,limit=1] if block ~4 ~1 ~ oak_door[open=false] run tellraw @s {"text":"Door: CLOSED","color":"yellow"}
execute at @e[type=marker,tag=spm_shelter_anchor,limit=1] if block ~8 ~1 ~ red_bed[occupied=true] run tellraw @s {"text":"Bed: OCCUPIED","color":"yellow"}
execute at @e[type=marker,tag=spm_shelter_anchor,limit=1] if block ~8 ~1 ~ red_bed[occupied=false] run tellraw @s {"text":"Bed: FREE","color":"green"}
