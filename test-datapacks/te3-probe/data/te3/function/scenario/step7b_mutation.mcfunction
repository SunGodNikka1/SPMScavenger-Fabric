# STEP 7B - mutation -> reject -> replan -> converge.
#
# Identical market and mob to step 7A. The only addition is an ARMED market mutation that fires
# from the server tick the moment the mob has actually planned a Trade Everything route - i.e.
# while it is walking, after Q1 and before Q2.
#
# The mutation is upstream's own TradeEverythingApi.setItemOverride, raising oak_log's value. That
# is real pricing state changed from outside. The fixture never tells Scavenger to reject anything,
# never forces a replan, and never fabricates a Q2 mismatch in our code - a fixture that did those
# would only prove that a fixture can make a test pass.
#
# Expected, in the readout:
#   PLAN  #1 TE armorer   Q1: 22 log -> 1 emerald
#   MUTATION APPLIED
#   REVAL #1  Q2 MISMATCH/GONE -> REJECTED      logs unchanged, emeralds unchanged
#   PLAN  #2 TE armorer   Q1: 11 log -> 1 emerald
#   REVAL #2  Q2: 11 log -> 1 emerald  OK
#   TRADE #2 TRADED       logs fall, emeralds rise
function te3:_base
function te3:_merchants_autonomous
summon playermob:player_mob ~ ~ ~ {CustomName:'"TE3Mob"',PersistenceRequired:1b,Tags:["te3","te3_mob"]}
spmscavenger debug te3 seed autonomous
spmscavenger debug te3 fixture
say [TE3] step 7B fixture ready. Now: /spmscavenger debug te3 index
say [TE3] then: /spmscavenger debug te3 watch on
say [TE3] then: /spmscavenger debug te3 mutate arm     and WAIT - do not touch the mob
say [TE3] then, after it has traded: /spmscavenger debug te3 watch report
