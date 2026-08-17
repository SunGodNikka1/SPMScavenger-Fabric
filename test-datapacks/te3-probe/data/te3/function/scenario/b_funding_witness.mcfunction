# R12 - the deterministic B_FUNDING witness. ONE scenario, ONE run, no reroll loop for the user.
#
# Proves the route the source census established: an ordinary novice armorer whose board has no
# non-emerald cost pays EMERALDS for authorized logs, and those emeralds fund an iron pickaxe on a
# DIFFERENT villager. Emerald ESCALATION is structurally impossible for logs/planks/sticks -
# payoutFor weighs ONE item (1 x 0.75) against unit x cap, whose floor is 1 - so the all-sell
# fallback board is the only door, which is why the fixture conditions for exactly that board.
function te3:_base
function te3:_merchants_b
function te3:_mob
# Inventory is seeded by the Java probe: /item cannot reach the InventoryCarrier backpack.
#
# CAPACITY, not just value. 8 backpack slots: 6 log stacks (384), a 5-emerald stack for the TE
# payout to merge into, and torches. Filling all 8 with logs would have been worth more and failed
# NO_ROOM on the first SELL, because VillagerTradeAdapter stages the debit and requires the result
# to insert before it commits. Purchasing power is unchanged: floor(383/22) = 17 TE uses + 5 held
# = 22, which covers the whole confirmed 8..22 iron-pickaxe envelope.
spmscavenger debug te3 seed funding
# Discards vanilla boards until vanilla rolls the required ones. Authors no Offers tag.
spmscavenger debug te3 fixture
say [TE3] scenario B witness. Run: /spmscavenger debug te3 index
say [TE3] then: /spmscavenger debug te3 scan minecraft:iron_ingot
say [TE3] expect B_FUNDING > 0 with SELL @armorer funding BUY @toolsmith
