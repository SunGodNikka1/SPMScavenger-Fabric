# Task 29 report: MI-14C repair package (MAIBS C2 FAIL remediation)

**Status:** `DONE_WITH_CONCERNS`

## Verification

`.\gradlew.bat test` — **302 tests, 0 failures** (`CONFIRMED`, cwd `Projects/SPMScavenger-1.21.1-Fabric`)

## PLANNED → IMPLEMENTED → PREDICTED RUNTIME

### MI-14C2-R1 — Handoff Authority Lifetime

| Layer | Result |
| --- | --- |
| **Planned** | Persistent `CAVE_CONTINUATION` commitment survives `consumeTransition()` |
| **Implemented** | `MiningExecutionCommitment`, `claimCaveContinuation`, `ExecutionIntentPolicy` + `MiningGoalKind` derive from commitment; `ExploringGoal` sets/clears on accept/abandon/complete |
| **Predicted runtime** | After cave handoff accept, gather/smelt/craft continue yielding until expedition ends or commitment expires (`INFERRED`; runtime `UNVERIFIED`) |

### MI-14C2-R2 — Scheduler-Wide MOVE Contention

| Layer | Result |
| --- | --- |
| **Planned** | Classify host/unknown MOVE holders; never silent `NONE` when MOVE blocked |
| **Implemented** | `MoveHolderClassification`, `MoveHolderClassifier`, `MoveContentionPolicy.hasBlockingMoveHolder` |
| **Predicted runtime** | `FollowLovedOneGoal`-class holders produce `CONTENTION`; protected goals (escape/shelter/stay/combat) do not (`INFERRED`; live GoalSelector `UNVERIFIED`) |

### MI-14C1-R2 — Revocation-Safe Executor Stop

| Layer | Result |
| --- | --- |
| **Planned** | `stop()` must not resurrect director-revoked project |
| **Implemented** | `MiningProject.matchesSession`, `MiningDirector.shouldPersistExecutorCheckpoint`, guarded `ControlledDescentGoal.stop()` |
| **Predicted runtime** | Revoke → stop leaves terminal lifecycle only, no `RUNNING` resurrection (`INFERRED`; runtime `UNVERIFIED`) |

## Delivered

| ID | Files |
| --- | --- |
| R1 | `ExecutionCommitmentKind.java`, `MiningExecutionCommitment.java`, `MiningProjectSavedData` (commitments NBT), `ExecutionIntentPolicy`, `MiningGoalKind`, `ExploringGoal`, `MiningDirector.mayStartControlledDescent(..., now)` |
| R2 | `MoveHolderClassification.java`, `MoveHolderClassifier.java`, `MoveContentionPolicy` |
| C1-R2 | `MiningProject.matchesSession`, `MiningDirector.shouldPersistExecutorCheckpoint`, `ControlledDescentGoal.stop()` |

## Regression tests

| Test | Defect |
| --- | --- |
| `MiningExecutionRepairTest.r1_*` (4) | M1 handoff lifetime |
| `MiningExecutionRepairTest.r2_*` (2) | M2 classifier |
| `MiningExecutionRepairTest.c1r2_*` (3) | M3 safe stop |
| `MiningExecutionC2Test.c2g_intentChangeYieldsGatherAfterHandoffClaimed` | M1 multi-cycle |
| `MiningDirectorTest.mustNotHappen_claimedCaveContinuationHoldsLockUntilCleared` | R1 director gate |

## MAIBS C2 re-pass (static)

**Verdict:** `PASS_WITH_RUNTIME_UNVERIFIED`

| Case | Re-pass |
| --- | --- |
| C2-A | **PASS** static — commitment sustains `CAVE_HANDOFF` after consume |
| C2-B | **PASS** static — participating yield unchanged |
| C2-C | **PASS** |
| C2-D | **PASS** static |
| C2-E | **PASS** |
| C2-F | **PASS** static — unknown/host MOVE → blocking classification |
| C2-G | **PASS** static — post-claim gather still YIELD |

**MI-14C3** remains unstarted per brief; gate now permits it when authorized.

## Concerns

- No Minecraft launch; live GoalSelector contention and handoff latency `UNVERIFIED`
- `MoveContentionPolicy` integration test without real `Mob`/`GoalSelector` mock
- Commitment NBT round-trip not separately tested (covered indirectly via policy tests)

## Self-review vs brief

- All three repair items implemented with required falsification tests
- No MI-14C3 work
- No commits, no Minecraft launch
