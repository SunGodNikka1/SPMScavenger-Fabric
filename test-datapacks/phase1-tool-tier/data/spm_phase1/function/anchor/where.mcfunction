execute unless entity @e[type=marker,tag=spm_p1_anchor,limit=1] run tellraw @s [{"text":"[SPM Phase1] ","color":"red"},{"text":"No anchor.","color":"white"}]
execute if entity @e[type=marker,tag=spm_p1_anchor,limit=1] at @e[type=marker,tag=spm_p1_anchor,limit=1] run particle minecraft:happy_villager ~ ~1 ~ 0.3 0.5 0.3 0 20
