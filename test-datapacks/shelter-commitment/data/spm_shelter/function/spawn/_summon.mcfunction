execute unless entity @e[type=marker,tag=spm_shelter_anchor,limit=1] run tellraw @s [{"text":"[SPM Shelter] No anchor; run anchor/set first.","color":"red"}]
execute if entity @e[type=marker,tag=spm_shelter_anchor,limit=1] at @e[type=marker,tag=spm_shelter_anchor,limit=1] run function spm_shelter:spawn/_summon_at
