# Nudge villagers within bed reach — vanilla AI must acquire HOME POI tickets via PoiManager.take().
# Do NOT inject villager HOME brain memory or sleep coordinates — those do not call acquireTicket().
execute at @e[type=playermob:player_mob,tag=spm_vr.subject,limit=1] run tp @e[type=minecraft:villager,tag=spm_vr.villager1,limit=1] ~-5.5 ~ ~1.5
execute at @e[type=playermob:player_mob,tag=spm_vr.subject,limit=1] run tp @e[type=minecraft:villager,tag=spm_vr.villager2,limit=1] ~-7.5 ~ ~1.5
