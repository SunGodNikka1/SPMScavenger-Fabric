kill @e[tag=te3]
# Dropped items too. Cleanup only removed te3-TAGGED entities, so ordinary item entities left by a
# previous run survived and could be picked up by the next fixture mob - which is one candidate
# explanation for an inventory that grew by 16 logs during a stalled run. Removing them does not
# prove wealth was innocent; it removes one hypothesis so the next run can distinguish them.
kill @e[type=minecraft:item,distance=..64]
kill @e[type=minecraft:experience_orb,distance=..64]
gamerule doMobSpawning true
gamerule doDaylightCycle true
gamerule doWeatherCycle true
