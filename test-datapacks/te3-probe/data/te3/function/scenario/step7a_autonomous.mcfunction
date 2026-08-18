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
# Stone pickaxe + iron axe, 6 log stacks, torches, ONE FREE SLOT, and deliberately NO emeralds.
spmscavenger debug te3 seed autonomous
# Discards vanilla draws until the armorer is all-sell and the toolsmith lists an affordable pickaxe.
spmscavenger debug te3 fixture
say [TE3] step 7A fixture ready. Now: /spmscavenger debug te3 index
say [TE3] then: /spmscavenger debug te3 watch on     and WAIT - do not touch the mob
say [TE3] then, after it has walked and traded: /spmscavenger debug te3 watch report
