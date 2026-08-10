# Task 30 report: MI-14C3-R1 protected interruption lease semantics

## Status

`DONE_WITH_CONCERNS` — implementation, unit/static validation, clean build, and post-code MAIBS
pass are complete. Observable Minecraft behavior remains `UNVERIFIED` because no runtime launch
was authorized.

## Files created or changed

- `.superpowers/sdd/archive/task-30-brief.md`
- `.superpowers/sdd/archive/task-30-report.md`
- `.superpowers/sdd/progress.md`
- `plans/RFC-MINING-INTELLIGENCE-AND-WEALTH-SYSTEM.md`
- `docs/porting/DECISIONS.md`
- `docs/porting/TEST_MATRIX.md`
- `src/main/java/com/noobk/spmscavenger/goal/ExplorationActivityGoal.java`
- `src/main/java/com/noobk/spmscavenger/mining/ExecutionBlocker.java`
- `src/main/java/com/noobk/spmscavenger/mining/ExecutionLeasePolicy.java`
- `src/main/java/com/noobk/spmscavenger/mining/MiningDirector.java`
- `src/main/java/com/noobk/spmscavenger/mining/MiningExecutionLease.java`
- `src/main/java/com/noobk/spmscavenger/mining/MoveContentionPolicy.java`
- `src/main/java/com/noobk/spmscavenger/mining/MoveHolderClassification.java`
- `src/main/java/com/noobk/spmscavenger/mining/MoveHolderClassifier.java`
- `src/main/java/com/noobk/spmscavenger/mining/SchedulerConflictPolicy.java`
- `src/test/java/com/noobk/spmscavenger/mining/ExecutionLeasePolicyTest.java`
- `src/test/java/com/noobk/spmscavenger/mining/MiningExecutionC3R1Test.java`
- `src/test/java/com/noobk/spmscavenger/mining/MiningExecutionC3Test.java`
- `src/test/java/com/noobk/spmscavenger/mining/MiningExecutionRepairTest.java`

## Summary

R1 now separates “may mining preempt this goal?” from “can the executor acquire all required
scheduler flags?”. `SchedulerConflictPolicy` checks `MOVE + LOOK`, maps the pinned SPM host goal
families to typed blockers, and preserves protected arbitration. `MiningExecutionLease` NBT v4
persists exact pre-start pause time; safety/recovery is condition-bound, commands prevent/revoke,
combat keeps its 1200-tick grace, progress expires after 400 admissible ticks, and the total project
budget remains 2400 executor ticks.

Post-GREEN feedback simulation found a same-observer control loop: `CommandedActionGoal` could
revoke an assignment and the director could immediately create another because the empty store
derived intent `NONE`. Admission now scans using the intended `CONTROLLED_DESCENT` authority before
creating the project.

## Commands and exact results

Working directory for every command:

`D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

1. RED:

   `gradlew.bat test --tests com.noobk.spmscavenger.mining.MiningExecutionC3R1Test`

   Result: `FAILED` during `compileTestJava`, 28 missing R1 API/type errors including absent
   `SAFETY_RECOVERY`, `PLAYER_ORDER`, `startPausedTicks`, required-flag classifier overload, and
   protected taxonomy values. This proves the test did not pass against the pre-R1 implementation.

2. First targeted GREEN:

   `gradlew.bat test --tests ...MiningExecutionC3R1Test --tests ...ExecutionLeasePolicyTest --tests ...MiningExecutionRepairTest --tests ...MiningExecutionC2Test`

   Result: `BUILD SUCCESSFUL` in 8s.

3. First full regression:

   `gradlew.bat clean test`

   Result: `FAILED`; 320 tests ran and four legacy `MiningExecutionC3Test` fixtures still encoded
   the superseded 2400-tick progress boundary. Production behavior was correct; fixtures were
   rewritten around the locked 400-tick boundary.

4. Full test after fixture repair:

   `gradlew.bat clean test`

   Result: `BUILD SUCCESSFUL` in 16s; 320 tests at that revision.

5. Targeted tests after revoke→reassign repair:

   `gradlew.bat test --tests ...MiningExecutionC3R1Test --tests ...MiningExecutionC3Test --tests ...MiningExecutionRepairTest`

   Result: `BUILD SUCCESSFUL` in 8s.

6. Final build after adding v3→v4 migration coverage:

   `gradlew.bat clean build`

   Result: `BUILD SUCCESSFUL` in 9s; XML aggregation reports **321 tests**, with
   `MiningExecutionC3R1Test` **11 tests / 0 failures / 0 errors**.

Final artifact:

- `build/libs/spmscavenger-1.9.2.jar`
- size: `317707` bytes
- SHA-256: `F9095275AC30E3487459E0E33C0416430641EA4D7EDC4C5980607528B19BA46F`

## Source evidence

- `CODE_CONFIRMED` — SPM v0.86 goal registration:
  `D:\Apps\Minecraft Port\Projects\references\SocialPlayerMobs-v0.86.0\src\main\java\games\brennan\playermob\entity\PlayerMobEntity.java`, goal registration around lines
  756–835.
- `CODE_CONFIRMED` — `EatFoodGoal` declares LOOK only; FireBucket, CommandedAction,
  TrainRecovery, Flee, social/door, combat, SeekAmmo, FollowLovedOne, and StayNear flag declarations
  are in the pinned reference's `entity/goal/` sources.
- `CODE_CONFIRMED` — controlled descent requires MOVE + LOOK:
  `src/main/java/com/noobk/spmscavenger/goal/ControlledDescentGoal.java`.
- `CONFIRMED` — C3-F1…F7, exact pause, timeout reachability, and v2/v3/v4 migration are unit-tested.
- `UNVERIFIED` — live GoalSelector order, animation/readout, wall-clock feel at low TPS, and actual
  safety/eating/command resumption require an approved Minecraft runtime probe.

Three negative probes preserved from the pre-repair audit:

1. protected/stay blocker handling was `NOT FOUND` in the old `controlledDescentBlocker`;
2. `MiningProjectEnd.PLAYER_ORDER` consumers were `NOT FOUND` outside the enum;
3. stay-anchor assignment prevention was `NOT FOUND` in the old director observer.

All three now have explicit production call paths.

## Post-implementation MAIBS semantic-drift review

```text
PLANNED
  typed required-flag blockers + protected pause + player authority + 400 progress ticks
IMPLEMENTED
  complete MOVE/LOOK intersection; pinned host taxonomy; NBT v4 pause clocks;
  stay/command admission guard; existing combat grace; 2400 total cap retained
PREDICTED RUNTIME
  safety/eating temporarily owns the body, then mining resumes with the remaining clock;
  player orders prevent/end mining; genuine admitted stalls end before total-budget exhaustion
```

| Goal | Priority/flags | Can interrupt | State/lease result | Observable prediction |
| --- | --- | --- | --- | --- |
| EnvironmentalEscape | 0, MOVE+LOOK | Yes | condition-bound safety pause | escape continues; mining resumes after clear |
| CommandedAction | 1, MOVE+LOOK | Yes | prevent/revoke PLAYER_ORDER | no autonomous command fight or reassign loop |
| StayNear | 2, MOVE+LOOK + persistent anchor | Yes | anchor prevents; running goal revokes | miner obeys tether |
| EatFood | 3, LOOK | Yes at complete flag admission | bounded LOW_FOOD pause | finishes eating, then descent admits |
| Combat | 2, MOVE+LOOK | Yes | existing COMBAT_TARGET grace | short combat resumes; long combat invalidates context |
| Gather/smelt/follow/unknown | MOVE and/or LOOK | Yes | CONTENTION | C2 yield or bounded never-started release |

Temporal trace:

- `T0`: descent is assigned; safety already running produces `SAFETY_RECOVERY`, not `NONE`.
- `T+10`: flagless observer records suspension; before start it accumulates protected pause only
  when the episode clears/changes.
- `T+60`: repeated same blocker does not settle or duplicate credit.
- `T+200`: if safety clears, the exact episode is settled once and the executor recalculates its
  path; if a player order exists, assignment is absent/revoked instead.
- `T+1200` and beyond: observable safety remains suspended with no mining expiry. Once free, only
  admissible ticks count; >400 without progress yields `NO_PROGRESS`, while total execution is
  independently capped at 2400.

Predicted weird behaviors:

1. `ACCEPTABLE_STEPPING_STONE`: an indefinitely stuck safety goal preserves a mining assignment;
   deleting the assignment cannot solve the trap, so safety owns its termination.
2. `ACCEPTABLE_STEPPING_STONE`: an unknown future LOOK/MOVE goal is conservative CONTENTION and may
   release a never-started assignment after 600 admissible ticks.
3. `RUNTIME_QUESTION`: observer cadence can delay visible state updates by up to its 10-tick
   stagger, though executor admission also checks the scheduler directly.
4. `RUNTIME_QUESTION`: equal-priority EatFood/descent ordering is source-supported but needs a live
   run to confirm the exact visual sequence under Fabric/vanilla GoalSelector.

Gate result: **MAIBS-1 static `PASS — BEHAVIORALLY_PLAUSIBLE`; runtime `UNVERIFIED`.** No remaining
static architecture defect was found in F1–F7. The runtime falsification matrix must still test long
safety, pre-start eating, stay/command, protected→combat→protected, save/reload, and a >400-tick
admissible obstruction.

## Self-review

- [x] Arbitration and lease impact are separate.
- [x] Full required-flag intersection replaces MOVE-only production logic.
- [x] Safety pause is condition-bound and non-preemptible.
- [x] Combat grace remains bounded and separate.
- [x] Player commands prevent/revoke and do not reassign in the same observer cycle.
- [x] Pre-start pause is separate, exact-once, persisted, and migrated.
- [x] 400 progress ticks precede the unchanged 2400 total budget.
- [x] C3-F1…F7 and persistence tests pass.
- [x] RFC, decision, test matrix, and progress ledger updated.
- [x] No Tunnel Search, Minecraft launch, commit, or push.

## Concerns

- Runtime behavior, save/reload in Minecraft, and real SPM GoalSelector ordering are `UNVERIFIED`.
- The pinned host taxonomy uses class-name suffixes to avoid a compile dependency. Unknown renamed
  goals fail conservatively to CONTENTION, but a renamed player-command class would lose immediate
  `PLAYER_ORDER` semantics until the addon updates.
- Gradle reports deprecated features incompatible with Gradle 10; this is pre-existing toolchain
  debt and did not fail the current Gradle 9.2 build.
