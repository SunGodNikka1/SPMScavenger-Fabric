# TEMPORARY V2-H PROOF SUPPORT.
#
# Spawned WITH AI, next to their matching workstations, so vanilla claims the workstation POIs
# itself. Nothing here authors Brain memories, POI occupancy, offers, KnownVillage or relationship
# state - the village has to be real, or `VillagePerception` is being handed a village that vanilla
# never agreed exists.
#
# Exactly ONE of each: `/spmscavenger debug vrt2 setup` refuses an ambiguous fixture, because a
# proof cannot say which merchant the mob traded with if two could have.
#
# Separation is proof machinery, not scenery. 18 blocks apart: inside the 16-block trade candidate
# radius of the centre, far outside each other's 3-block interaction range. After the fourth sale
# the mob must WALK, and that walk is the observer's window to witness the same chain sitting in
# BUY_TARGET before the purchase closes the consumer.
summon villager ~-9 ~ ~ {VillagerData:{profession:"minecraft:toolsmith",level:2,type:"minecraft:plains"},PersistenceRequired:1b,CustomName:'"VRT2 Toolsmith"',Tags:["vrt2","vrt2_merchant","vrt2_toolsmith"]}
summon villager ~9 ~ ~ {VillagerData:{profession:"minecraft:fletcher",level:1,type:"minecraft:plains"},PersistenceRequired:1b,CustomName:'"VRT2 Fletcher"',Tags:["vrt2","vrt2_merchant","vrt2_fletcher"]}
