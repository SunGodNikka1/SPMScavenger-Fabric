# STEP 7B - mutation -> reject -> replan -> converge.
#
# ORDERING IS LOad-BEARING. SpmScavengerInstallPolicy reads cfg.exploring at ENTITY_LOAD to decide
# replacesHostStroll and installsOverlandExploration, and a later config change does NOT re-wire an
# already-loaded entity. Arming before the summon would permanently give this fixture mob a
# different goal stack - no ExploringGoal, no TrackedLocalWanderGoal, host stroll retained - and
# restoring the flag afterwards could never put those goals back.
#
# So the mob is summoned and fully seeded FIRST, and `mutate arm` is the last setup command here:
# after ENTITY_LOAD, before the mob's first ordinary AI tick.
function te3:_base
function te3:_merchants_autonomous
summon playermob:player_mob ~ ~ ~ {CustomName:'"TE3Mob"',PersistenceRequired:1b,Tags:["te3","te3_mob"]}
spmscavenger debug te3 seed autonomous
spmscavenger debug te3 fixture
# Deterministic gather terrain: a MINIMAL VALIDATED TREE as the unrelated WEALTH target
# the 003c witness needs - production requires a rooted 3+ log trunk with a canopy, so a lone log
# was never legal. Refuses if iron is exposed in radius, or if the built tree fails isGatherableLog.
spmscavenger debug te3 terrain
# LAST. Holds discretionary exploration until the mob's first Trade Everything plan exists, then
# restores the exact previous value. See the honesty note in Te3ProbeCommand: this flag gates more
# than ExploringGoal.
spmscavenger debug te3 mutate arm
# No "ready" line here either. te3 terrain prints readiness on success, and `mutate arm` refuses
# outright when terrain is not armed - so a failed build cannot produce a half-armed 7B run.
say [TE3] 7B: after the readiness block above, also run /spmscavenger debug te3 watch report
