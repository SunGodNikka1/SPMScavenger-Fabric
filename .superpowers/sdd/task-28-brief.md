# Task 28 brief: MI-14C3 — Progress Lease

## Target

`D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

## Prerequisite

- MI-14C2 `IMPLEMENTED` (task-27; 292 tests pass)
- RFC MI-14C3 contract `LOCKED` (D-MIW-039)

## Purpose

Close the **stale-active** Loop A variant: executor starts once, then produces no observable dig
progress while the assignment stays `RUNNING`.

## Binding constraints

## Behavioral prediction (MAIBS, before implementation)

| Layer | Result |
| --- | --- |
| Intended behavior | A controlled-descent assignment that started but stops digging is eventually released, while legitimate combat or MOVE contention does not consume its progress budget. |
| Implemented mechanism | Persist the last observable progress time plus accumulated blocked ticks; evaluate elapsed admissible time only while the blocker is `NONE`. |
| Predicted behavior | A real block removal or completed stair step buys another 2400 admissible ticks. A mob stuck while otherwise free to dig is revoked after that window. Combat/contention pauses the window and the same stair resumes afterward. |
| Failure/weirdness | A pause implemented as “skip evaluation” only would cause an immediate timeout on resume; treating `start()` or ordinary goal ticks as progress would make zombie assignments immortal; two independently accumulated blocker clocks could double-credit a pause. |
| Confidence | `CODE_CONFIRMED` for lease/GoalSelector wiring; `GAME_MECHANICS_INFERRED` for several-minute observable behavior until runtime approval. |

### Goal interaction prediction

| State | Lease effect | Expected physical result |
| --- | --- | --- |
| Controlled descent owns MOVE and breaks/moves | Clock runs; real break/step refreshes | Staircase continues |
| Combat preempts | TEMPORARY pause; existing grace still applies | Mob fights, then resumes remaining descent |
| C2 scheduler contention | CONTENTION pause | Mob waits for the holder to yield; no false progress timeout |
| No blocker, no successful break/step | Clock runs to expiry | Project ends `NO_PROGRESS`; mob may choose other work |

### Alternatives and decision

1. **Skip timeout evaluation while blocked.** Smallest patch, but rejected: suspended wall time is
   still included and causes immediate post-resume revocation.
2. **Accumulate exact paused ticks and subtract them from the current progress window. Recommended.**
   Preserves immutable historical timestamps, survives save/load, and makes blocker changes explicit.
3. **Persist a mutable remaining-tick budget.** Valid but larger and easier to decrement incorrectly
   across observer/executor ticks; use only if future modes need variable budgets.

Verdict before code: **BEHAVIORALLY PLAUSIBLE** with option 2. Runtime falsifier: let a started
descent wait under C2 `CONTENTION` for longer than 2400 ticks, clear the contention, and confirm the
project resumes rather than immediately ending. Test combat separately inside its existing
1200-tick temporary grace; that grace may legitimately revoke prolonged combat before C3 matters.

### Two clocks (D-MIW-039)

| Clock | Already shipped? | Field |
| --- | --- | --- |
| Start lease | C1 yes | `assignedAt` → `executorStartedAt` |
| Progress lease | **this task** | `lastExecutionProgressAt` → `now` |

`markExecutorStarted` starts the executor clock only — it does **not** count as dig progress.

### Observable progress only

Call `MiningDirector.markExecutionProgress(level, mob)` from `ControlledDescentGoal` when:

1. A planned break cell is actually removed.
2. `completeStep` advances the stand / project anchor.
3. A terminal handoff is emitted.

**Forbidden:** bare `tick()`, path replan, plan rejection.

### Progress lease evaluation

Revoke with `MiningProjectEnd.NO_PROGRESS` when **all**:

- `lease.everStarted()`
- blocker is `NONE` (admissible)
- `now - lastExecutionProgressAt > PROGRESS_LEASE_TICKS` (proposed **2400**)

**Pause** progress clock while blocker is `TEMPORARY` or `CONTENTION`.

### Non-goals

- No progress lease for tunnel search (no executor).
- Do not change C2 matrices or goal priorities.
- Do not wire `LOW_FOOD`.

## Deliverables

1. `MiningExecutionLease.lastExecutionProgressAt` + NBT v3 migration
2. `ExecutionLeasePolicy` progress timeout branch
3. `MiningDirector.markExecutionProgress`
4. Wire progress marks in `ControlledDescentGoal` (break + completeStep + terminal emit)
5. Unit tests C3-A…E

## Falsification (required before DONE)

| ID | Must happen | Must not |
| --- | --- | --- |
| C3-A | Stuck-after-step → `NO_PROGRESS` revoke | Eternal ACTIVE lease |
| C3-B | Combat suspend pauses progress clock | Timeout during combat |
| C3-C | Break/step refreshes progress | `tick()` refreshes |
| C3-D | Never-started still governed by start lease only | Double-revoke |
| C3-E | CONTENTION pauses progress clock | Revoke while waiting for yield |

## Verification

`.\gradlew.bat test` from project root

## Report

`.superpowers/sdd/task-28-report.md`

## Defer

- `TunnelSearchGoal` executor
- Progress lease for future modes
- Minecraft launch (separate approval)
