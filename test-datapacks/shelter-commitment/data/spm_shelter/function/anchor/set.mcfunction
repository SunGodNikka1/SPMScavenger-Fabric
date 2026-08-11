kill @e[type=marker,tag=spm_shelter_anchor]
summon marker ~ ~ ~ {Tags:["spm_shelter_anchor"]}
tellraw @s [{"text":"[SPM Shelter] ","color":"gold"},{"text":"Arena anchor set.","color":"green"}]
