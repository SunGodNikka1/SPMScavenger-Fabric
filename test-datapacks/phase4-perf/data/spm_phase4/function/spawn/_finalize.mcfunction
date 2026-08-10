# Tag perf mobs and cluster them on the pad. Never issue playermob stay — that sets StayAnchorState=PRESENT
# and blocks ExploringGoal / planCurrentStage (ExplorationPolicy.allowsExpedition requires ABSENT).
tag @e[type=playermob:player_mob,distance=..64] add spm_p4_perf
execute if entity @e[type=marker,tag=spm_p4_anchor,limit=1] at @e[type=marker,tag=spm_p4_anchor,limit=1] run spreadplayers 14 28 false @e[type=playermob:player_mob,tag=spm_p4_perf]
execute unless entity @e[type=marker,tag=spm_p4_anchor,limit=1] as @p at @s run spreadplayers 14 28 false @e[type=playermob:player_mob,tag=spm_p4_perf]
