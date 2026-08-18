# P0-1 exact quote parity. ONE known seller, because the claim is per-villager and a second board
# only adds ways to be right by accident.
#
# You never type a command with the merchant screen open - you cannot, and closing it is exactly
# when TE removes the row we need to read. `parity snapshot` arms a server-tick observer that
# captures the first real priced synthetic quote for the pinned villager and input.
#
#   1. fresh process (PROCEDURAL - the probe cannot verify this; see the README)
#   2. condition the seller   3. snapshot the DIRECT quote, which also arms the observer
#   4. open the merchant, stage the SAME stack, close it   5. live   6. closed
function te3:_base
summon villager ~2 ~ ~ {VillagerData:{profession:"minecraft:armorer",level:1,type:"minecraft:plains"},PersistenceRequired:1b,NoAI:1b,CustomName:'"TE3 Parity Seller"',Tags:["te3"]}
# All-sell board, so DefaultBuyItemSelector falls back to EMERALD and oak_log actually quotes.
# Re-rolled from vanilla draws; no Offers tag is authored.
spmscavenger debug te3 fixture
give @s minecraft:oak_log 64
say [TE3] P0-1. Hold the oak_log stack, then: /spmscavenger debug te3 parity snapshot
say [TE3] open the villager, put that stack in the trade slot, then CLOSE the screen
say [TE3] then: /spmscavenger debug te3 parity live   and: /spmscavenger debug te3 parity closed
say [TE3] if the observer window lapsed: /spmscavenger debug te3 parity arm
