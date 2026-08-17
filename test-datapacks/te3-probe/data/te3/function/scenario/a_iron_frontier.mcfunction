# A/B/E candidate. Stone pick + iron axe = live iron_pickaxe_upgrade consumer, so demand is
# iron_ingot x3 and the projection is iron_pickaxe. Authorized inputs: logs, planks, sticks.
function te3:_base
function te3:_merchants
function te3:_mob
# Inventory is seeded by the Java probe: /item cannot reach the InventoryCarrier backpack.
spmscavenger debug te3 seed iron
# Torches FIRST. placeTorches=true with torchStockTarget=8 raises a CHARCOAL demand, and SURVIVAL
# outranks PROGRESSION - so surplus logs with no torches select CHARCOAL, not IRON_INGOT, and the
# "iron frontier" scenario would silently be a torch scenario.
say [TE3] scenario A/B: iron frontier. Run: /spmscavenger debug te3 index
say [TE3] then: /spmscavenger debug te3 scan minecraft:iron_ingot   (asserts the demand)
