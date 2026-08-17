# TEMPORARY V2-H PROOF SUPPORT.
#
# Non-gatherable structural shell. No logs anywhere: logs are a pass-one gather candidate whenever a
# tool upgrade is outstanding, and one in range stops the scan ever reporting
# NO_CANDIDATES_IN_RADIUS. Smooth stone is deliberate too - it is not cobblestone, so it is not a
# cobble candidate either. (Planks are NOT a gather candidate; the intent covers logs, coal,
# cobblestone, raw iron and diamond. They are still avoided here only because they come from logs.)
fill ~-16 ~-1 ~-16 ~16 ~-1 ~16 smooth_stone
fill ~-16 ~0 ~-16 ~16 ~5 ~16 air

# Village centre. Floor attachment so the bell is a real, supported block - a bell at ~1 with no
# support is not placeable and would leave the village POI short one member.
setblock ~ ~ ~ bell[attachment=floor]

# Beds: vanilla village POIs, and villagers must be able to claim them themselves.
setblock ~-2 ~ ~4 red_bed[facing=north,part=foot]
setblock ~-2 ~ ~3 red_bed[facing=north,part=head]
setblock ~2 ~ ~4 blue_bed[facing=north,part=foot]
setblock ~2 ~ ~3 blue_bed[facing=north,part=head]

# Bounded stalls. Each merchant is enclosed beside its own workstation so vanilla POI claiming has
# a short, unambiguous path and the villager cannot wander off before it acquires the site.
fill ~-11 ~0 ~-2 ~-7 ~2 ~2 smooth_stone hollow
fill ~-10 ~0 ~-1 ~-8 ~2 ~1 air
setblock ~-9 ~ ~1 smithing_table

fill ~7 ~0 ~-2 ~11 ~2 ~2 smooth_stone hollow
fill ~8 ~0 ~-1 ~10 ~2 ~1 air
setblock ~9 ~ ~1 fletching_table
