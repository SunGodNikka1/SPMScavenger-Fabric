# E candidate - the explicit COAL vs CHARCOAL test. Surplus logs with torches below target raises a
# CHARCOAL demand; vanilla villagers buy COAL, and TE's payout chooser prefers a non-emerald
# commodity the villager buys. If a quote pays coal against a charcoal demand, that is bucket E:
# the payout would actually serve the torch chain, but the demand representation cannot express it.
function te3:_base
function te3:_merchants
function te3:_mob
# Inventory is seeded by the Java probe: /item cannot reach the InventoryCarrier backpack.
spmscavenger debug te3 seed torch
say [TE3] scenario E: torch chain. Run: /spmscavenger debug te3 index
say [TE3] then: /spmscavenger debug te3 scan minecraft:charcoal   (watch for a COAL payout)
