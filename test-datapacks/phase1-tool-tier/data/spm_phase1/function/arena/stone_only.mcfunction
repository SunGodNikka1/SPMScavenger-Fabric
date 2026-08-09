execute unless entity @e[type=marker,tag=spm_p1_anchor,limit=1] run tellraw @s [{"text":"[SPM Phase1] ","color":"red"},{"text":"No anchor.","color":"white"}]
execute if entity @e[type=marker,tag=spm_p1_anchor,limit=1] at @e[type=marker,tag=spm_p1_anchor,limit=1] run function spm_phase1:arena/_stone_only_at
tellraw @s [{"text":"[SPM Phase1] ","color":"gold"},{"text":"Single exposed stone at anchor+5 (TT-1).","color":"green"}]
