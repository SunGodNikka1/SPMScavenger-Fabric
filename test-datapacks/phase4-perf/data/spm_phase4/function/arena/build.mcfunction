function spm_phase4:arena/_clear
fill ~-32 ~-1 ~-32 ~32 ~-1 ~32 minecraft:grass_block
fill ~-32 ~ ~-32 ~32 ~3 ~32 minecraft:air replace
# Small forest patch for gather scans
fill ~-20 ~ ~-20 ~-8 ~ ~-8 minecraft:oak_log
fill ~-20 ~1 ~-20 ~-8 ~4 ~-8 minecraft:oak_log replace minecraft:air
fill ~-20 ~5 ~-20 ~-8 ~8 ~-8 minecraft:oak_leaves replace minecraft:air
# Flat smelt staging — no pre-placed furnace (search must discover absence)
fill ~10 ~ ~10 ~18 ~ ~18 minecraft:cobblestone
setblock ~14 ~ ~14 minecraft:crafting_table
tellraw @s [{"text":"[SPM Phase4] ","color":"gold"},{"text":"Arena ready (forest NW, smelt pad SE, no furnace).","color":"green"}]
