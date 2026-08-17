# TEMPORARY V2-TE P0-3 PROBE SUPPORT.
# Deterministic conditions only. The datapack owns villagers, inventories, positions, professions
# and scenario progression; the Java probe owns every Trade Everything call, quote inspection and
# timing. Nothing here quotes, prices, or transacts.
gamerule doMobSpawning false
gamerule doDaylightCycle false
gamerule doWeatherCycle false
time set noon
weather clear
kill @e[tag=te3]
