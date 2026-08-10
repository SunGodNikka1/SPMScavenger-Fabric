function spm_phase4:arena/_clear
fill ~-48 ~-1 ~-48 ~48 ~-1 ~48 minecraft:grass_block
fill ~-48 ~ ~-48 ~48 ~3 ~48 minecraft:air replace
function spm_phase4:arena/_forceload_wide
tellraw @s [{"text":"[SPM Phase4] ","color":"gold"},{"text":"P4A-EXPLORE arena: open pad, no trees/furnace, wide forceload, no barriers.","color":"green"}]
