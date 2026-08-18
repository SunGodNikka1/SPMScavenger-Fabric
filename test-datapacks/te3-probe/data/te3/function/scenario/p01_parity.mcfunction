# P0-1 exact quote parity. ONE known seller, because the claim is per-villager and a second board
# only adds ways to be right by accident.
#
# Order is the proof, not a convenience:
#   1. fresh process   2. condition the seller   3. snapshot the DIRECT quote with NO session yet
#   4. open the merchant normally and stage the SAME stack   5. compare   6. close and verify removal
# `parity snapshot` refuses if a synthetic row already exists, which is what enforces step 3 before
# step 4 - TE installs that row in onSetTradingPlayer and removes it when the player is set to null.
function te3:_base
summon villager ~2 ~ ~ {VillagerData:{profession:"minecraft:armorer",level:1,type:"minecraft:plains"},PersistenceRequired:1b,NoAI:1b,CustomName:'"TE3 Parity Seller"',Tags:["te3"]}
# All-sell board, so DefaultBuyItemSelector falls back to EMERALD and oak_log actually quotes.
# Re-rolled from vanilla draws; no Offers tag is authored.
spmscavenger debug te3 fixture
give @s minecraft:oak_log 64
say [TE3] P0-1. Hold the oak_log stack, then: /spmscavenger debug te3 parity snapshot
say [TE3] then open the villager, put that stack in the trade slot, and: parity live
say [TE3] then close the merchant and: parity closed
