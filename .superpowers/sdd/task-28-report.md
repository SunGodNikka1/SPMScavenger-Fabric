# Task 28 report: MI-14C3 — Progress Lease

## Result

**IMPLEMENTED / STATICALLY VERIFIED / RUNTIME UNVERIFIED**

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
| Verdict | **BEHAVIORALLY_PLAUSIBLE / RUNTIME_UNVERIFIED** |

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
