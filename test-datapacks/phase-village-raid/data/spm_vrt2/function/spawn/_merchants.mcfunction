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
# BOUNDED NATURAL POOL. The iron pickaxe is a LEVEL 3 toolsmith listing (runtime finding: a level-2
# fixture merchant produced `setup FAILED - missing Toolsmith iron_pickaxe offer`), and even at
# level 3 it is not guaranteed: `updateTrades` draws 2 listings from a pool of 5, so roughly 40% of
# boards carry it. One summoned toolsmith is therefore an unreliable fixture.
#
# So spawn a small bounded set and later keep the FIRST board that naturally contains the route,
# selected on route presence alone - never on price or enchantment, and never by authoring an offer.
summon villager ~-9 ~ ~ {VillagerData:{profession:"minecraft:toolsmith",level:3,type:"minecraft:plains"},PersistenceRequired:1b,CustomName:'"VRT2 Toolsmith A"',Tags:["vrt2","vrt2_smith_candidate"]}
summon villager ~-9 ~ ~-3 {VillagerData:{profession:"minecraft:toolsmith",level:3,type:"minecraft:plains"},PersistenceRequired:1b,CustomName:'"VRT2 Toolsmith B"',Tags:["vrt2","vrt2_smith_candidate"]}
summon villager ~-9 ~ ~3 {VillagerData:{profession:"minecraft:toolsmith",level:3,type:"minecraft:plains"},PersistenceRequired:1b,CustomName:'"VRT2 Toolsmith C"',Tags:["vrt2","vrt2_smith_candidate"]}
summon villager ~-12 ~ ~ {VillagerData:{profession:"minecraft:toolsmith",level:3,type:"minecraft:plains"},PersistenceRequired:1b,CustomName:'"VRT2 Toolsmith D"',Tags:["vrt2","vrt2_smith_candidate"]}
summon villager ~9 ~ ~ {VillagerData:{profession:"minecraft:fletcher",level:1,type:"minecraft:plains"},PersistenceRequired:1b,CustomName:'"VRT2 Fletcher"',Tags:["vrt2","vrt2_merchant","vrt2_fletcher"]}
