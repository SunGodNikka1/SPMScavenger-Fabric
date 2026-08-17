# D candidate. Every input is either unmodelled by SellReserveModel (fails closed) or fully
# reserved by the craft chain, so no quote may be authorized however lucrative it is - W-5,
# disposition before valuation.
function te3:_base
function te3:_merchants
function te3:_mob
item replace entity @e[tag=te3_mob,limit=1] weapon.mainhand with stone_pickaxe
item replace entity @e[tag=te3_mob,limit=1] inventory.0 with diamond 8
item replace entity @e[tag=te3_mob,limit=1] inventory.1 with iron_ingot 12
item replace entity @e[tag=te3_mob,limit=1] inventory.2 with wheat 64
item replace entity @e[tag=te3_mob,limit=1] inventory.3 with stick 2
say [TE3] scenario D: all inputs unmodelled or reserved. Expect D_ILLEGAL, zero A/B.
