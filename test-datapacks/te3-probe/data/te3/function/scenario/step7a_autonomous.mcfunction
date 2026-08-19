# STEP 7A - integrated autonomous V2-TE runtime fixture.
#
# The fixture sets the world up and then STOPS. It does not select a target, force navigation,
# start the goal, call a source, call the adapter, give emeralds, give a pickaxe, or insert a
# synthetic row. Everything after setup is TradeWithVillagerGoal deciding for itself:
#
#   iron_pickaxe_upgrade demand -> discovery -> TE SELL candidate -> walk -> Q2 requote
#   -> detached TE transaction -> emeralds -> re-resolve -> vanilla Toolsmith BUY -> walk
#   -> execute BUY -> owns iron pickaxe -> demand satisfied
#
# The mob here is NOT NoAI - unlike every earlier probe fixture, the whole point is that it moves.
function te3:_base
function te3:_merchants_autonomous
summon playermob:player_mob ~ ~ ~ {CustomName:'"TE3Mob"',PersistenceRequired:1b,Tags:["te3","te3_mob"]}
# Stone pickaxe + iron axe, 5 log stacks, torches, 3 sticks, ONE FREE SLOT, and NO emeralds.
# Run #1 seeded six log stacks and the mob crafted a log into planks, taking its last slot - the
# first TE emerald then had nowhere to land. The sticks stop the craft chain before it starts.
spmscavenger debug te3 seed autonomous
# Discards vanilla draws until the armorer is all-sell and the toolsmith lists an affordable pickaxe.
spmscavenger debug te3 fixture
# Deterministic gather terrain: a MINIMAL VALIDATED TREE as the unrelated WEALTH target
# the 003c witness needs - production requires a rooted 3+ log trunk with a canopy, so a lone log
# was never legal. Refuses if iron is exposed in radius, or if the built tree fails isGatherableLog.
spmscavenger debug te3 terrain
# No "ready" line here. te3 terrain prints readiness on SUCCESS only - a failed terrain step used
# to be followed by the scenario cheerfully announcing the fixture was ready.
