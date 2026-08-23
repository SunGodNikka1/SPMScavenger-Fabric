# Settlement bootstrap — VillagePerception needs VILLAGE + IS_OCCUPIED POI (PoiManager ticket state).
# Three HOME beds: vanilla villager AI must acquire the first two via PoiManager.take(); one spare HOME.
setblock ~ ~-1 ~ minecraft:grass_block
fill ~-8 ~-1 ~-2 ~2 ~-1 ~4 minecraft:grass_block replace minecraft:air
setblock ~1 ~ ~ minecraft:bell
setblock ~-3 ~ ~1 minecraft:red_bed[part=head,facing=south]
setblock ~-3 ~ ~2 minecraft:red_bed[part=foot,facing=south]
setblock ~-5 ~ ~1 minecraft:blue_bed[part=head,facing=south]
setblock ~-5 ~ ~2 minecraft:blue_bed[part=foot,facing=south]
setblock ~-7 ~ ~1 minecraft:white_bed[part=head,facing=south]
setblock ~-7 ~ ~2 minecraft:white_bed[part=foot,facing=south]
summon minecraft:villager ~-3.5 ~1 ~1.5 {Tags:["spm_vr.helper","spm_vr.villager","spm_vr.villager1"],PersistenceRequired:1b,Age:0}
summon minecraft:villager ~-5.5 ~1 ~1.5 {Tags:["spm_vr.helper","spm_vr.villager","spm_vr.villager2"],PersistenceRequired:1b,Age:0}
gamerule mobGriefing true
time set 18000
schedule function spm_vr:_lib/claim_village_beds 20t
schedule function spm_vr:_lib/claim_village_beds 60t
