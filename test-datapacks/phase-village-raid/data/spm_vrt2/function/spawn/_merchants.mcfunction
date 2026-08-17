# TEMPORARY V2-H PROOF SUPPORT.
# Exactly ONE toolsmith and ONE fletcher - /setup refuses an ambiguous fixture, because a proof
# cannot say which merchant the mob traded with if two could have.
#
# Separation is proof machinery, not scenery. Both sit well outside the 3-block interaction range
# of each other but inside the 16-block trade candidate radius, so after the fourth sale the mob
# must physically WALK to the toolsmith - which is what gives the 1-tick observer a window to
# witness the same chain in BUY_TARGET before the purchase clears the consumer.
summon villager ~-9 ~ ~ {VillagerData:{profession:"minecraft:toolsmith",level:2,type:"minecraft:plains"},PersistenceRequired:1b,NoAI:1b,CustomName:'"VRT2 Toolsmith"'}
summon villager ~9 ~ ~ {VillagerData:{profession:"minecraft:fletcher",level:1,type:"minecraft:plains"},PersistenceRequired:1b,NoAI:1b,CustomName:'"VRT2 Fletcher"'}
