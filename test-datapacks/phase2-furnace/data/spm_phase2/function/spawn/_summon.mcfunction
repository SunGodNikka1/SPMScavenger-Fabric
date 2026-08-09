execute if entity @e[type=marker,tag=spm_p2_anchor,limit=1] at @e[type=marker,tag=spm_p2_anchor,limit=1] positioned ~3 ~ ~ run function spm_phase2:spawn/_summon_at
execute unless entity @e[type=marker,tag=spm_p2_anchor,limit=1] as @p at @s anchored eyes positioned ^ ^ ^3 run function spm_phase2:spawn/_summon_at
