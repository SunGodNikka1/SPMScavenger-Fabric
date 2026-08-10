# Distributed single trees (not a solid log cube) for realistic gather scans.
setblock ~18 ~ ~0 minecraft:oak_log
setblock ~18 ~1 ~0 minecraft:oak_log
setblock ~18 ~2 ~0 minecraft:oak_log
fill ~17 ~3 ~-1 ~19 ~4 ~1 minecraft:oak_leaves replace minecraft:air
setblock ~-18 ~ ~0 minecraft:oak_log
setblock ~-18 ~1 ~0 minecraft:oak_log
setblock ~-18 ~2 ~0 minecraft:oak_log
fill ~-19 ~3 ~-1 ~-17 ~4 ~1 minecraft:oak_leaves replace minecraft:air
setblock ~0 ~ ~18 minecraft:oak_log
setblock ~0 ~1 ~18 minecraft:oak_log
setblock ~0 ~2 ~18 minecraft:oak_log
fill ~-1 ~3 ~17 ~1 ~4 ~19 minecraft:oak_leaves replace minecraft:air
setblock ~0 ~ ~-18 minecraft:oak_log
setblock ~0 ~1 ~-18 minecraft:oak_log
setblock ~0 ~2 ~-18 minecraft:oak_log
fill ~-1 ~3 ~-19 ~1 ~4 ~-17 minecraft:oak_leaves replace minecraft:air
setblock ~24 ~ ~24 minecraft:oak_log
setblock ~24 ~1 ~24 minecraft:oak_log
fill ~23 ~2 ~23 ~25 ~3 ~25 minecraft:oak_leaves replace minecraft:air
setblock ~-24 ~ ~-24 minecraft:oak_log
setblock ~-24 ~1 ~-24 minecraft:oak_log
fill ~-25 ~2 ~-25 ~-23 ~3 ~-23 minecraft:oak_leaves replace minecraft:air
