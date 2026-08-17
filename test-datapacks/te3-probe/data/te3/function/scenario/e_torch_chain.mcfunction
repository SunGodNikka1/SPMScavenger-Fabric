# E candidate - the explicit COAL vs CHARCOAL test. Surplus logs with torches below target raises a
# CHARCOAL demand; vanilla villagers buy COAL, and TE's payout chooser prefers a non-emerald
# commodity the villager buys. If a quote pays coal against a charcoal demand, that is bucket E:
# the payout would actually serve the torch chain, but the demand representation cannot express it.
function te3:_base
function te3:_merchants
function te3:_mob
item replace entity @e[tag=te3_mob,limit=1] weapon.mainhand with iron_pickaxe
item replace entity @e[tag=te3_mob,limit=1] weapon.offhand with iron_axe
item replace entity @e[tag=te3_mob,limit=1] inventory.0 with oak_log 64
item replace entity @e[tag=te3_mob,limit=1] inventory.1 with oak_log 64
say [TE3] scenario E: torch chain, no torches, surplus logs. Watch for COAL payout vs CHARCOAL demand.
