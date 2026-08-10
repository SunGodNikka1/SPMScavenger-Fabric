kill @e[type=marker,tag=spm_p4_anchor]
summon marker ~ ~ ~ {Tags:["spm_p4_anchor"]}
tellraw @s [{"text":"[SPM Phase4] ","color":"gold"},{"text":"Anchor set.","color":"green"}]
