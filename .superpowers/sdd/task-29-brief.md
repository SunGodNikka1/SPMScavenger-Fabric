# Task 29 brief: MI-14C repair package (MAIBS C2 FAIL)

## Target

`D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

## Prerequisite

- MI-14C2 unit implementation (task-27)
- MAIBS C2 static gate **FAIL** (`.superpowers/sdd/task-27-maibs-report.md`)

## Purpose

Repair three control-plane lifecycle defects exposed by multi-cycle MAIBS trace before MI-14C3.

## Binding constraints

- Do **not** start MI-14C3 in this package.
- Do **not** make mining dictator over protected host goals (combat/survival/recovery).
- Preserve Loop D honesty — do not keep `CAVE_FOUND` transition pending forever; use commitment.
- Gate AV-1: multi-cycle regression tests required; matrix-only tests insufficient.

---

## MI-14C2-R1 — Handoff Authority Lifetime

### Problem

`CAVE_HANDOFF` intent derives only from pending `CAVE_FOUND`. `acceptCaveHandoff()` consumes
transition in `canUse()`; next arbitration tick → `NONE`.

### Deliverables

1. `MiningExecutionCommitment` (or equivalent) persisted in `MiningProjectSavedData`
2. Atomic: `consumeTransition` + create `CAVE_CONTINUATION` commitment on successful handoff plan
3. `ExecutionIntentPolicy` derives `CAVE_HANDOFF` from active commitment OR project OR pending transition
4. `MiningGoalKind.classifyExploring` uses commitment for `EXPLORING_CAVE_HANDOFF`
5. Explicit clear on expedition success / abandon / failure

### Regression (required)

```text
CAVE_FOUND → Exploring accepts → transition gone → next tick
GatherResourcesGoal MUST STILL YIELD
```

---

## MI-14C2-R2 — Scheduler-Wide MOVE Contention

### Problem

`MoveContentionPolicy` ignores goals where `MiningGoalKind.classify` returns empty (all SPM host goals).

### Deliverables

1. `MoveHolderClassification` enum: `PARTICIPATING_YIELD`, `PROTECTED_INTERRUPT`, `ORDINARY_HOST_WORK`, `UNKNOWN_MOVE_HOLDER`
2. Classify running MOVE goals beyond the five scavenger executors (at minimum: SPM `FollowLovedOneGoal` → `ORDINARY_HOST_WORK`; combat goals → `PROTECTED_INTERRUPT`)
3. `UNKNOWN_MOVE_HOLDER` with actionable intent → `ExecutionBlocker.CONTENTION` (lease cannot falsely AUTHORIZE)
4. Do **not** force YIELD on protected host goals

### Regression (required)

Synthetic unknown `Goal` with `MOVE` running + assigned descent → not eternal `AUTHORIZE`/`NONE`.

---

## MI-14C1-R2 — Revocation-Safe Executor Stop

### Problem

`ControlledDescentGoal.stop()` blindly `putProject(local RUNNING copy)` after director revoked in `canContinueToUse`.

### Deliverables

1. Guard `stop()` persistence: only if store still owns same active assignment
2. Match assignment identity (project origin/mode/started time or issued lease), not merely mode enum
3. Unit test: revoke via `authorizeExecution` → `stop()` → store must not return to `RUNNING`

---

## Verification

`.\gradlew.bat test` from project root

## Report

`.superpowers/sdd/task-29-report.md`

## Defer

- MI-14C3 progress lease
- Minecraft launch (separate approval)
