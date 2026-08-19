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
# LAST. Holds discretionary exploration until the mob's first Trade Everything plan exists, then
# restores the exact previous value. See the honesty note in Te3ProbeCommand: this flag gates more
# than ExploringGoal.
spmscavenger debug te3 mutate arm
say [TE3] step 7B ready. Run BEFORE this scenario next time:
say [TE3]   /function te3:cleanup
say [TE3]   /spmscavenger debug te3 reset
say [TE3]   /spmscavenger debug te3 index
say [TE3]   /spmscavenger debug te3 watch on
say [TE3]   /function te3:scenario/step7b_mutation
say [TE3] Now WAIT - do not touch the mob or either villager.
say [TE3] Then: /spmscavenger debug te3 watch report
