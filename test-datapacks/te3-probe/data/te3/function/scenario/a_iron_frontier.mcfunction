# A/B/E candidate. Stone pick + iron axe = live iron_pickaxe_upgrade consumer, so demand is
# iron_ingot x3 and the projection is iron_pickaxe. Authorized inputs: logs, planks, sticks.
function te3:_base
function te3:_merchants
function te3:_mob
item replace entity @e[tag=te3_mob,limit=1] weapon.mainhand with stone_pickaxe
item replace entity @e[tag=te3_mob,limit=1] weapon.offhand with iron_axe
item replace entity @e[tag=te3_mob,limit=1] inventory.0 with oak_log 48
item replace entity @e[tag=te3_mob,limit=1] inventory.1 with stick 64
item replace entity @e[tag=te3_mob,limit=1] inventory.2 with oak_planks 32
# Torches FIRST. placeTorches=true with torchStockTarget=8 raises a CHARCOAL demand, and SURVIVAL
# outranks PROGRESSION - so surplus logs with no torches select CHARCOAL, not IRON_INGOT, and the
# "iron frontier" scenario would silently be a torch scenario.
item replace entity @e[tag=te3_mob,limit=1] inventory.3 with torch 16
say [TE3] scenario A/B: iron frontier. Run: /spmscavenger debug te3 index
say [TE3] then: /spmscavenger debug te3 scan minecraft:iron_ingot   (asserts the demand)
