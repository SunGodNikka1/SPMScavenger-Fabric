kill @e[type=marker,tag=spm_p2_anchor]
summon marker ~ ~ ~ {Tags:["spm_p2_anchor"]}
tellraw @s [{"text":"[SPM Phase2] ","color":"aqua"},{"text":"Arena anchor set.","color":"green"}]
