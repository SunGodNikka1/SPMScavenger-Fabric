execute unless entity @e[type=marker,tag=spm_p1_anchor,limit=1] run tellraw @s [{"text":"[SPM Phase1] ","color":"red"},{"text":"No anchor — run anchor/set first.","color":"white"}]
execute if entity @e[type=marker,tag=spm_p1_anchor,limit=1] at @e[type=marker,tag=spm_p1_anchor,limit=1] run function spm_phase1:arena/_clear
execute if entity @e[type=marker,tag=spm_p1_anchor,limit=1] at @e[type=marker,tag=spm_p1_anchor,limit=1] run function spm_phase1:arena/_fixtures
tellraw @s [{"text":"[SPM Phase1] ","color":"gold"},{"text":"Arena built at anchor.","color":"green"}]
