# R12 B-witness market. Deliberately only TWO villagers: the TE emerald seller and the pickaxe
# buyer. The A-scenario's fletcher and farmer are market noise here - the witness has to show a
# SELL on one merchant funding a BUY on another, and every extra board is another way for the
# result to be true for a reason nobody checked.
#
# Both are summoned as ordinary employed villagers with NO authored Offers. `te3 fixture` then
# discards vanilla draws until each rolls the board the source census proved reachable:
#   armorer  lvl1 - both listings drawn from the 4 ItemsForEmeralds (p=0.6), so no non-emerald
#                   cost exists and DefaultBuyItemSelector falls back to EMERALD
#   toolsmith lvl3 - the EnchantedItemForEmeralds(IRON_PICKAXE) listing is drawn (p=0.4)
# The pickaxe's price and enchantment stay whatever vanilla rolled; nothing here fixes them.
summon villager ~2 ~ ~ {VillagerData:{profession:"minecraft:armorer",level:1,type:"minecraft:plains"},PersistenceRequired:1b,NoAI:1b,CustomName:'"TE3 Armorer"',Tags:["te3"]}
summon villager ~-2 ~ ~ {VillagerData:{profession:"minecraft:toolsmith",level:3,type:"minecraft:plains"},PersistenceRequired:1b,NoAI:1b,CustomName:'"TE3 Toolsmith"',Tags:["te3"]}
