# Task 28 report: MI-14C3 — Progress Lease

## Result

**IMPLEMENTED / UNIT GREEN / MAIBS-1 FAIL — INTEGRATION DEFECT / RUNTIME UNVERIFIED**

MI-14C3 now releases a controlled-descent project that has started but produces no observable dig
progress for 2400 admissible ticks. TEMPORARY and CONTENTION episodes pause the progress window by
their exact duration. The existing C1 start lease and temporary-blocker grace remain independent.

## RED → GREEN evidence

- RED: `gradlew.bat test --tests ...MiningExecutionC3Test` failed at `compileTestJava` with 22
  missing-symbol/signature errors for the deliberately absent C3 API.
- GREEN focused: C1 + C2 + C3 lease suites passed.
- GREEN full: `gradlew.bat clean build` → `BUILD SUCCESSFUL`; 310 tests, 0 failures, 0 errors,
  0 skipped across 44 suites.
- `git diff --check` passed.

## Implementation

| Area | Change | Evidence |
| --- | --- | --- |
| Persisted lease | Added `lastExecutionProgressAt` (`-1` sentinel) and `progressPausedTicks`; NBT v3; v2 migration invents no progress | `MiningExecutionLease`; C3 migration/save-load tests |
| Policy | Added `PROGRESS_LEASE_TICKS=2400`; a started, admissible lease revokes `NO_PROGRESS` after the strict `>` boundary | `ExecutionLeasePolicy`; C3-A/C tests |
| True pause | Settles each completed TEMPORARY/CONTENTION episode into accumulated paused ticks; blocker changes cannot double count | `MiningExecutionLease.recordBlocker`; C3-B/E tests |
| First window | No-progress sentinel uses immutable `executorStartedAt` as its effective baseline without writing fake progress | `ExecutionLeasePolicy.evaluate`; C3-C test |
| Director | Full lease evaluation used in authorization; added narrow `markExecutionProgress` | `MiningDirector` |
| Executor | Marks only successful planned block removal, completed step, or terminal handoff; failed `destroyBlock` stays on the target and does not increment budget/progress | `ControlledDescentGoal` |

## C3 falsification matrix

| ID | Static/unit result | Runtime status |
| --- | --- | --- |
| C3-A stuck after start/progress | PASS — `NO_PROGRESS` after 2400 admissible ticks | `UNVERIFIED` |
| C3-B combat pause | PASS — exact blocked duration excluded; C1 combat grace unchanged | `UNVERIFIED` |
| C3-C real progress vs tick | PASS — explicit mark refreshes; start/tick has no mark | `UNVERIFIED` |
| C3-D never started | PASS — `LEASE_EXPIRED` through C1 start lease, no progress timestamp | `UNVERIFIED` |
| C3-E contention pause | PASS — >2400 blocked ticks excluded and resume boundary tested | `UNVERIFIED` |

## Post-implementation MAIBS prediction

| Layer | Result |
| --- | --- |
| Planned | Started-but-stale descent releases; combat/contention pauses; only physical/terminal progress refreshes. |
| Implemented | Matches the plan. Pause requires a completed blocker episode and uses an accumulated persisted duration. |
| Predicted runtime | A freely runnable mob that cannot remove its target or finish a step remains aimed at/retrying that work, then ends `NO_PROGRESS` after about 120 seconds of admissible server ticks. Short combat pauses both execution and C3 aging; C1 may still terminate combat lasting over its separate 60-second grace. C2 contention can last longer without causing an immediate post-resume C3 timeout. |
| Semantic drift | The RFC named only `lastExecutionProgressAt`; correct pause semantics required one additional persisted accumulator. This is implementation detail, not policy drift. Failed block destruction now remains on the target rather than advancing past an unremoved cell. |
| Verdict | **SUPERSEDED by the full MAIBS audit below: FAIL — ARCHITECTURE_DEFECT** |

### Predicted weird behavior audit

1. A protected or externally restored target may visibly be retried until the lease expires. This is
   an acceptable bounded failure; advancing through a solid cell would be worse.
2. Combat longer than 1200 ticks ends through C1 `COMBAT`, not C3. This is intentional clock
   separation, but players may perceive it as “did not resume after a long fight.”
3. A server lag spike still advances game ticks only as Minecraft processes them; the 2400-tick
   lease is tick-time, not wall-time. This is appropriate for TPS safety but means low TPS extends
   the real-world wait.
4. Multiple mobs have independent leases; C3 does not reserve staircase cells or repair inter-mob
   obstruction. C2/contention and future site reservation remain responsible.

Runtime falsifier: hold a started descent under CONTENTION beyond 2400 ticks, release it, and verify
it does not immediately revoke; then obstruct a dig while blocker is NONE and verify one and only
one `NO_PROGRESS` completion after the admissible window.

## Artifact

- `build/libs/spmscavenger-1.9.2.jar`
- Size: 312,575 bytes
- SHA-256: `F0F14E9A6C5B33A241848805275A0E4419E73140872C00E70E336856413F03D3`
- Packaging checks: `fabric.mod.json` present; `MiningExecutionLease.class` present; 169 entries.

No Minecraft runtime was launched. No commit or push was performed.

## Full behavioral-simulation addendum — 2026-08-09

The explicit `minecraft-ai-behavioral-simulation` audit traced the lease through the complete goal
and project-budget loop. It found two integration failures hidden by the pure lease tests.

### Planned → implemented → predicted runtime

| Layer | Result |
| --- | --- |
| Intended behavior | A running descent that makes no physical progress ends `NO_PROGRESS`; legitimate preemption pauses that clock. |
| Implemented mechanism | C3 expires at admissible age `> 2400`. The active executor separately increments project usage before every action and ends `SEARCH_BUDGET_EXHAUSTED` at `ticksElapsed >= 2400`. |
| Predicted behavior | An actively stuck goal reaches the project-budget terminal first. If it had already spent 2300 ticks before its last real progress, it can end about 100 ticks later as `SEARCH_BUDGET_EXHAUSTED`; C3-A's `NO_PROGRESS` is unreachable on that active path. |
| Failure/weirdness | A successful unit test of `ExecutionLeasePolicy` does not prove the integrated executor can reach that outcome. Terminal budget exhaustion is itself marked as progress immediately before deleting the lease, masking the stale-progress diagnosis. |
| Confidence | `CODE_CONFIRMED`; runtime presentation remains `UNVERIFIED`. |

### Coordinate/physical trace

At stand cell `(0,64,0)` heading east, `StairStepPlanner` selects next stand `(1,63,0)` and breaks
`(1,65,0) → (1,64,0) → (1,63,0)`. A denied final break remains solid. The mob repeatedly faces and
attempts that same cell; it cannot occupy `(1,63,0)`. Each goal tick still increments project
`ticksElapsed`. At 2400 total executor ticks, `tick()` emits `SEARCH_BUDGET_EXHAUSTED` before another
lease evaluation can satisfy strict `>2400` from the last progress timestamp.

### GoalSelector interaction

| Goal/activity | Priority | Flags | Lease classification | Predicted result |
| --- | ---: | --- | --- | --- |
| Environmental escape | 0 | MOVE/LOOK | `PROTECTED_INTERRUPT`, excluded from contention | Preempts descent; C3 incorrectly sees `NONE` rather than a paused blocker |
| SPM command/train/flee family | 1 | usually MOVE/LOOK | Unknown goals pause as CONTENTION; `TrainRecoveryGoal` is protected and does not | Mixed semantics for equally physical interruptions |
| Combat | 2 | MOVE/LOOK plus target | `COMBAT_TARGET` TEMPORARY | Correct C3 pause; separate 1200-tick C1 grace can end project |
| `StayNearGoal` | 2 | MOVE/LOOK | `PROTECTED_INTERRUPT`, excluded from contention | Can prevent start forever or age a previously started C3 clock while the executor physically cannot run |
| Controlled descent | 3 | MOVE/LOOK | executor | Active stalls hit project budget before C3 |
| Host loot goals | 3 | goal-dependent | running unknown MOVE holder becomes CONTENTION | Existing holder pauses; a newly eligible equal-priority goal may not preempt active descent (`GAME_MECHANICS_INFERRED`) |
| Lease observer | 9 | none | evaluates every staggered observer cycle | Cannot make the mob move; only updates/revokes persistent state |

### Time simulation

- **T0:** director assigns; if the executor wins MOVE, `start()` records only `executorStartedAt`.
- **T+10:** it breaks, walks, or retries the current planned cell. Successful removal refreshes C3.
- **T+60:** repeated navigation calls or failed destruction do not refresh C3, correctly.
- **T+200:** a hard block can consume the maximum per-block break duration; successful removal then
  refreshes. A denied removal stays selected and begins a retry loop.
- **T+1200:** stalled active descent still exists, but its project budget is already half consumed.
- **T+2400 total active ticks:** project budget terminates first as `SEARCH_BUDGET_EXHAUSTED`.
  `NO_PROGRESS` does not occur on the advertised C3-A path.

### Protected-interrupt start-lease defect

`MoveHolderClassifier` intentionally maps `StayNearGoal`, `TrainRecoveryGoal`, shelter, and escape to
`PROTECTED_INTERRUPT`, and `blocksMiningExecution` returns false for that class. Therefore the
director resolves blocker `NONE` although a higher-priority goal physically owns MOVE. For an
assignment that never started, C1 only expires through CONTENTION, so a persistent stay tether can
retain an unstarted assignment indefinitely. For an already-started assignment, C3 can age during
the protected interruption even though the executor is not admissible.

Three absence probes:

1. **NOT FOUND:** protected goal / stay-anchor handling in `controlledDescentBlocker`.
2. **NOT FOUND:** `PLAYER_ORDER` use outside the `MiningProjectEnd` enum.
3. **NOT FOUND:** stay-anchor admission check in `ExplorationActivityGoal.directorTick` or
   `MiningDirector.mayStartControlledDescent`; stay-anchor checks exist only in `ExploringGoal`.

### Predicted weird behaviors

| Behavior | Classification |
| --- | --- |
| Solid/denied stair cell is retried until the whole-project budget reports success-like `SEARCH_BUDGET_EXHAUSTED`, not stale failure | `ARCHITECTURE_DEFECT` |
| Mob receives a descent assignment while `StayNearGoal` continuously owns MOVE; assignment never starts and never reaches the start lease | `ARCHITECTURE_DEFECT` |
| A long environmental escape or train recovery consumes C3 time despite physically preempting descent | `ARCHITECTURE_DEFECT` |
| Equal-priority host loot becomes ready after descent starts but cannot necessarily preempt it | `RUNTIME_QUESTION` |
| Multiple mobs dig independent adjacent routes without reservation and may obstruct each other | `ACCEPTABLE_STEPPING_STONE` only because both loops are bounded by project budget |

### MAIBS-1 gate

**FAIL — ARCHITECTURE_DEFECT.** MI-14C3 compiles, persists, and its pure policy works, but the
advertised active-stall outcome is shadowed by the equal 2400-tick project budget, and protected
MOVE ownership is not represented as an execution blocker.

Two repair directions must be compared before further code:

1. **Recommended:** make progress expiry strictly earlier than the minimum remaining active project
   budget and add an explicit protected-interrupt/player-order blocker policy. This preserves total
   project budget as a separate cap and gives C3 a reachable diagnostic role.
2. **Alternative:** remove tick exhaustion from controlled descent and let C3 own stall detection,
   keeping project budgets for blocks/distance/failures. Cleaner clock ownership, but changes the
   established total-work safety bound and needs a replacement absolute lifetime cap.

Must happen after repair: a denied planned break produces exactly one `NO_PROGRESS` before total
budget exhaustion, while a stay/player order either prevents assignment or records an explicit
pause/revoke reason. Must not happen: a protected higher-priority MOVE owner is reported as
admissible `NONE`, or a terminal budget event refreshes progress merely to erase the lease.
