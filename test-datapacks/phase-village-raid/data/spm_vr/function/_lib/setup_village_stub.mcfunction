# Minimal settlement stub around executor (~ ~ ~). Perception may take several seconds.
setblock ~ ~-1 ~ minecraft:grass_block
fill ~-2 ~-1 ~-2 ~2 ~-1 ~2 minecraft:grass_block replace minecraft:air
setblock ~1 ~ ~ minecraft:bell
setblock ~-1 ~ ~ minecraft:red_bed[part=head,facing=south]
setblock ~-1 ~ ~1 minecraft:red_bed[part=foot,facing=south]
summon minecraft:villager ~2 ~ ~ {Tags:["spm_vr.helper"],NoAI:1b,PersistenceRequired:1b}
gamerule mobGriefing true
