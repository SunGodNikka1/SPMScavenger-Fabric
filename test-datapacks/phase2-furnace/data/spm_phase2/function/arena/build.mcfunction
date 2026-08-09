execute unless entity @e[type=marker,tag=spm_p2_anchor,limit=1] run tellraw @s [{"text":"[SPM Phase2] ","color":"red"},{"text":"No anchor — run anchor/set first.","color":"white"}]
execute if entity @e[type=marker,tag=spm_p2_anchor,limit=1] at @e[type=marker,tag=spm_p2_anchor,limit=1] run function spm_phase2:arena/_clear
execute if entity @e[type=marker,tag=spm_p2_anchor,limit=1] at @e[type=marker,tag=spm_p2_anchor,limit=1] run function spm_phase2:arena/_fixtures
tellraw @s [{"text":"[SPM Phase2] ","color":"aqua"},{"text":"Arena built at anchor.","color":"green"}]
