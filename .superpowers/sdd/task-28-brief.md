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
