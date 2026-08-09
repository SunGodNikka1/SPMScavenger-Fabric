kill @e[type=marker,tag=spm_p1_anchor]
summon marker ~ ~ ~ {Tags:["spm_p1_anchor"]}
tellraw @s [{"text":"[SPM Phase1] ","color":"gold"},{"text":"Arena anchor set.","color":"green"}]
