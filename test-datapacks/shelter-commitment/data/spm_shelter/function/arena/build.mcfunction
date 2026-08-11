execute unless entity @e[type=marker,tag=spm_shelter_anchor,limit=1] run tellraw @s [{"text":"[SPM Shelter] No anchor; run anchor/set first.","color":"red"}]
execute if entity @e[type=marker,tag=spm_shelter_anchor,limit=1] at @e[type=marker,tag=spm_shelter_anchor,limit=1] run function spm_shelter:arena/_clear
execute if entity @e[type=marker,tag=spm_shelter_anchor,limit=1] at @e[type=marker,tag=spm_shelter_anchor,limit=1] run function spm_shelter:arena/_fixtures
tellraw @s [{"text":"[SPM Shelter] ","color":"gold"},{"text":"Closed-door house built with occupied bed.","color":"green"}]
