function spm_phase4:arena/_clear
function spm_phase4:arena/_pad
fill ~20 ~ ~20 ~28 ~ ~28 minecraft:cobblestone
setblock ~24 ~ ~24 minecraft:crafting_table
tellraw @s [{"text":"[SPM Phase4] ","color":"gold"},{"text":"P4A-SMELT arena: smelt pad SE, no pre-placed furnace.","color":"green"}]
