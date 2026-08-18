# Step 7A market. Exactly two merchants, both ordinary employed villagers with NO authored Offers.
#   armorer  lvl1  - conditioned to an all-sell board, so DefaultBuyItemSelector falls back to
#                    EMERALD and oak_log quotes to emeralds (the P0-3 census route)
#   toolsmith lvl3 - conditioned to hold the EnchantedItemForEmeralds(IRON_PICKAXE) listing at a
#                    price the mob can actually reach (VR-T2's level-3 finding)
# `te3 fixture` discards vanilla draws until both hold; it authors nothing.
summon villager ~3 ~ ~-3 {VillagerData:{profession:"minecraft:armorer",level:1,type:"minecraft:plains"},PersistenceRequired:1b,NoAI:1b,CustomName:'"TE3 Armorer"',Tags:["te3"]}
summon villager ~3 ~ ~3 {VillagerData:{profession:"minecraft:toolsmith",level:3,type:"minecraft:plains"},PersistenceRequired:1b,NoAI:1b,CustomName:'"TE3 Toolsmith"',Tags:["te3"]}
