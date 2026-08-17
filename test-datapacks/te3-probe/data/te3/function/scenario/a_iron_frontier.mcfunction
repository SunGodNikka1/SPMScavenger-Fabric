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
say [TE3] scenario A/B: iron frontier live. Run: /spmscavenger debug te3 index then scan
