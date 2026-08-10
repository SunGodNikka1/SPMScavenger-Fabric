# Task 27 brief: MI-14C2 — Execution Intent & Arbitration

## Target

`D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

## Prerequisite

- MI-14C1 + MI-14C1-R1 `IMPLEMENTED` (commit `a6e9793` verified)
- RFC Topic MI-14C2 contract `LOCKED` (D-MIW-037, D-MIW-038)

## Purpose

Make `MiningDirector` decisions enforceable through `GoalSelector` without replacing
`GoalSelector` and without allowing mining to override combat/survival.

## Binding constraints

### Separation (D-MIW-037)

- `ExecutionIntent` = what **should** have execution authority (derived from persistent state)
- `ExecutionBlocker.CONTENTION` = why authorized executor **isn't receiving** MOVE (scheduler observation)
- **Do not merge** these concepts

### Non-exclusive handoffs (D-MIW-038)

- `TUNNEL_HANDOFF_PENDING` → arbitration **NEUTRAL** until `TunnelSearchGoal` exists
- Do NOT force unrelated goals to YIELD; do NOT consume transition; do NOT pretend executor exists
- `SEARCH_BUDGET_EXHAUSTED` lock → arbitration NEUTRAL

### Actionable intents only

May cause YIELD in other goals: `CONTROLLED_DESCENT`, `CAVE_HANDOFF` only.

### Admission AND continuation

Every participating executor consults the **same** arbiter in **both** `canUse()` and
`canContinueToUse()`. `canUse`-only is a FAIL.

### Contention producer (required)

When C1-admissible assigned/actionable executor cannot obtain MOVE because another **running**
goal owns it → classify `ExecutionBlocker.CONTENTION` for lease layer.

Combat stays `COMBAT_TARGET` (TEMPORARY), not contention.

### Non-goals

- Do not change Minecraft priority integers
- Do not wire `LOW_FOOD` merely because enum exists

## Deliverables

1. `ExecutionIntent` enum + `ExecutionIntentPolicy.derive(store, mobId)` from persistent state
2. `ArbitrationDecision` enum (`ALLOW`, `YIELD`, `NEUTRAL`)
3. `MiningExecutionArbiter` (or equivalent) — pure policy from `(intent, goalKind)`
4. Wire arbiter into `ControlledDescentGoal`, `GatherResourcesGoal`, `SmeltAtFurnaceGoal`,
   `CraftTorchesGoal`, `ExploringGoal` — **both** admission and continuation
5. `MiningDirector` / lease path: detect MOVE contention → `CONTENTION` when otherwise AUTHORIZE
6. Unit tests for intent derivation, matrices, and falsification scenarios C2-A…G

## Matrices (locked)

### CONTROLLED_DESCENT

| Goal | Decision |
| --- | --- |
| ControlledDescentGoal | ALLOW |
| Gather / Smelt / CraftTorches / Exploring (ordinary) | YIELD |
| Combat / survival | NEUTRAL |

### CAVE_HANDOFF

| Goal | Decision |
| --- | --- |
| ExploringGoal on acceptCaveHandoff | ALLOW |
| ControlledDescent / Gather / Smelt / CraftTorches | YIELD |
| Combat / survival | NEUTRAL |

### TUNNEL_HANDOFF_PENDING

All participating goals → NEUTRAL (no exclusive authority).

## Falsification (required before DONE)

| ID | Must happen | Must not |
| --- | --- | --- |
| C2-A | Gather yields on CAVE_FOUND; explore eligible | Gather keeps MOVE |
| C2-B | Smelt yields on descent assignment | Smelt blocks descent forever |
| C2-C | Combat suspends via TEMPORARY lease | Combat as contention |
| C2-D | Mining resumes after combat ends | Wrong revocation |
| C2-E | Ordinary behavior under TUNNEL_HANDOFF_PENDING | Global freeze / transition consumed |
| C2-F | CONTENTION observable when MOVE blocked | Eternal AUTHORIZE |
| C2-G | canContinueToUse yields on intent change | Wait for natural stop |

## Verification

`.\gradlew.bat test` from project root

## Report

`.superpowers/sdd/archive/task-27-report.md`

## Defer

- MI-14C3 progress lease
- TunnelSearchGoal executor
- LOW_FOOD director classification
- Minecraft launch (separate approval)
