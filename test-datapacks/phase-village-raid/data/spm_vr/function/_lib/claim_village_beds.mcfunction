# Nudge villagers within bed reach — vanilla AI must acquire HOME POI tickets via PoiManager.take().
# Do NOT inject villager HOME brain memory or sleep coordinates — those do not call acquireTicket().
tp @e[type=minecraft:villager,tag=spm_vr.villager1,limit=1] ~-3.5 ~1 ~1.5
tp @e[type=minecraft:villager,tag=spm_vr.villager2,limit=1] ~-5.5 ~1 ~1.5
