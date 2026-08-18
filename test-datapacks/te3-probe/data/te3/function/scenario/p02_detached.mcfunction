# P0-2 detached synthetic execution witness. ONE seller, ONE mob, ONE transaction.
#
# The mob never opens a merchant. The quote is produced by OfferQuoter.quote and handed straight to
# VillagerTradeAdapter.executeResolved - never inserted into villager.getOffers(), no MerchantMenu,
# no MerchantContainer, no setTradingPlayer, no Player real or fake.
#
# The decisive line in the output is `uses after`. notifyTrade increments uses; TE's afterTrade hook
# resets it for synthetic offers. uses == 0 afterwards is the only DIRECT runtime evidence that the
# hook fired for an offer the villager has never carried.
function te3:_base
summon villager ~2 ~ ~ {VillagerData:{profession:"minecraft:armorer",level:1,type:"minecraft:plains"},PersistenceRequired:1b,NoAI:1b,CustomName:'"TE3 Detached Seller"',Tags:["te3"]}
function te3:_mob
# One sellable stack, seven free slots for the emerald. Capacity is not the question here.
spmscavenger debug te3 seed detached
# All-sell board so oak_log quotes to emeralds. Re-rolled from vanilla draws; no Offers authored.
spmscavenger debug te3 fixture
say [TE3] P0-2. Run: /spmscavenger debug te3 index
say [TE3] then exactly once: /spmscavenger debug te3 p02
