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
# Deterministic gather terrain: one lone ground-level oak log as the unrelated WEALTH target
# the 003c witness needs, and a refusal if any air-exposed iron ore sits inside the gather
# radius - because "the mandatory route found nothing" is the whole claim.
spmscavenger debug te3 terrain
say [TE3] step 7A ready. The V2-DEF-003c gate is the ORDER of these four lines:
say [TE3]   GATHER PUBLISHED -> GATHER YIELDING -> ROUTE INFEASIBLE -> PLAN TE
say [TE3] with logs staying 320 until the first TE transaction.
say [TE3] Now: /spmscavenger debug te3 index
say [TE3] then: /spmscavenger debug te3 watch on     and WAIT - do not touch the mob
say [TE3] then, after it has walked and traded: /spmscavenger debug te3 watch report
