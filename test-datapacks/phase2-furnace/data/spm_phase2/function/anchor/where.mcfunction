execute unless entity @e[type=marker,tag=spm_p2_anchor,limit=1] run tellraw @s [{"text":"[SPM Phase2] ","color":"red"},{"text":"No anchor.","color":"white"}]
execute if entity @e[type=marker,tag=spm_p2_anchor,limit=1] at @e[type=marker,tag=spm_p2_anchor,limit=1] run particle minecraft:happy_villager ~ ~1 ~ 0.3 0.5 0.3 0 20
