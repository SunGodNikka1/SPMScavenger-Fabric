# D candidate. Every input is either unmodelled by SellReserveModel (fails closed) or fully
# reserved by the craft chain, so no quote may be authorized however lucrative it is - W-5,
# disposition before valuation.
function te3:_base
function te3:_merchants
function te3:_mob
# Inventory is seeded by the Java probe: /item cannot reach the InventoryCarrier backpack.
spmscavenger debug te3 seed protected
say [TE3] scenario D: all inputs unmodelled or reserved. Expect D_ILLEGAL, zero A/B.
