# MAIBS-1 — MI-7E ControlledDescentGoal semantic-drift review

**Date:** 2026-08-09  
**Scope:** `ControlledDescentGoal`, `StairStepPlanner`, `StairStepSafety`, entry gate chain MI-6F→7E  
**Mode:** Static simulation (no Minecraft launch)  
**Gate result:** **`FAIL — ARCHITECTURE_DEFECT`** — do not proceed to MI-14; repair via **MI-7R** first.

---

## Intent vs implementation vs predicted runtime

| Layer | Result |
| --- | --- |
| **Intended** | After `EXHAUSTED`, mob commits heading, validates **post-break** stair geometry, equips owned pick, mines ordered breaks, steps down, hands off to MI-6 on real cave opening or to tunnel search in target band |
| **Implemented** | Pre-break `validatePlan` requires destination air/footing **before** breaks; tool gate uses main hand only; `CAVE_FOUND` requires three-air column including below feet |
| **Predicted (solid stone, pick in backpack)** | Goal activates → `planNextStep` → `NO_HEADROOM` or `NO_HARVEST` → failure budget consumed in ≤3 ticks → `NO_PROGRESS` exit **without mining** (`CODE_CONFIRMED`) |
| **Predicted (pick in backpack, axe in main hand)** | Same false rejection at plan time (`CODE_CONFIRMED`) |
| **Predicted (successful step in stone)** | `openedCave()` false (below is sturdy); staircase continues until budget — `CAVE_FOUND` never fires on valid footing (`CODE_CONFIRMED`) |

---

## Defect R1 — pre-break vs post-break validation (`ARCHITECTURE_DEFECT`)

**User finding:** `CONFIRMED` (`CODE_CONFIRMED`)

`StairStepPlanner` plans breaks at `forwardFeet.above()`, `forwardFeet`, `nextStand` (solid stone ahead).

`StairStepSafety.validatePlan()` after break validation calls:

```43:45:Projects/SPMScavenger-1.21.1-Fabric/src/main/java/com/noobk/spmscavenger/mining/StairStepSafety.java
        if (!hasHeadroom(level, plan.nextStandCell())) {
            return Rejection.NO_HEADROOM;
        }
```

`hasHeadroom` requires `nextStand` and `nextStand.above()` **already air**. In ordinary solid terrain both are stone → **`NO_HEADROOM` before any break**.

**Geometry trace (stand Y=64, heading NORTH):**

| Cell | Role | Pre-break state | In `requiredBreaks`? |
| --- | --- | --- | --- |
| (x,64,z-1) | forwardFeet | stone | yes (#2) |
| (x,65,z-1) | headroom | stone | yes (#1) |
| (x,63,z-1) | nextStand / floor | stone | yes (#3) |

Validator demands #2 and #3 be air **before** executor breaks them → plan self-rejects.

**NOT FOUND checks for post-break validation path:**
1. `StairStepSafety` — no `validatePlanAfterBreaks` / projected geometry (`grep` — only `validatePlan`)
2. `StairStepPlannerTest` — geometry only, no safety coupling (`StairStepPlannerTest.java`)
3. `src/test` — no `StairStepSafety` tests (`grep NOT FOUND`)

---

## Defect R2 — tool capability asymmetry (`ARCHITECTURE_DEFECT`)

**User finding:** `CONFIRMED` (`CODE_CONFIRMED`)

| Call site | Tool evidence |
| --- | --- |
| `hasUsablePick()` | `ToolTierPolicy.tierOfPick(backpack, main, offhand)` |
| `validatePlan` / `validateBreak` (plan path) | `mob.getMainHandItem()` only |
| `tickBreak` | `ToolBox.equipFor(mob, state)` **after** plan accepted |

```246:247:Projects/SPMScavenger-1.21.1-Fabric/src/main/java/com/noobk/spmscavenger/goal/ControlledDescentGoal.java
        StairStepSafety.Rejection rejection = StairStepSafety.validatePlan(
                level, plan, mob.getMainHandItem());
```

`ToolBox` already documents that backpack tools are invisible to pre-equip checks (`ToolBox.java` L14–18). `ownsToolFor` / `bestSpeed` exist for canonical capability but are not used in MI-7D/E safety path.

**Predicted:** Mob with iron pick in backpack + sword in main hand passes `canUse`, fails plan as `NO_HARVEST`, never reaches `equipFor`.

---

## Defect R3 — `CAVE_FOUND` contradicts footing safety (`ARCHITECTURE_DEFECT`)

**User finding:** `CONFIRMED` (`CODE_CONFIRMED`)

```291:295:Projects/SPMScavenger-1.21.1-Fabric/src/main/java/com/noobk/spmscavenger/goal/ControlledDescentGoal.java
    private static boolean openedCave(Level level, BlockPos feet) {
        return level.getBlockState(feet).isAir()
                && level.getBlockState(feet.above()).isAir()
                && level.getBlockState(feet.below()).isAir();
    }
```

vs

```76:78:Projects/SPMScavenger-1.21.1-Fabric/src/main/java/com/noobk/spmscavenger/mining/StairStepSafety.java
    static boolean hasFooting(Level level, BlockPos feet) {
        BlockPos below = feet.below();
        return level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP);
```

| Condition | Valid stair step | `openedCave()` |
| --- | --- | --- |
| feet | air (after break) | air required |
| above | air (after break) | air required |
| below | **sturdy solid** | **air required** |

Mutually exclusive on any step that satisfies MI-7D footing. Finding a cave should mean **traversable subterranean continuation** (MI-6 `CaveContextPolicy` / `CaveLandingResolver` / opportunity), not "floor vanished."

---

## Defect R4 — missing band termination / `HANDOFF_TUNNEL_SEARCH` (`ARCHITECTURE_DEFECT`)

**`CODE_CONFIRMED`:** `ControlledDescentGoal` never references `WorkDemandPolicy.isDiamondLocalGatherEligible` or `MiningProjectEnd.HANDOFF_TUNNEL_SEARCH`.

RFC requires handoff when target band reached with demand still blocking. Executor only ends on budget, hazard, `CAVE_FOUND`, or `NO_PROGRESS`.

**Predicted:** Mob digs through diamond band without tunnel-search handoff; descent pressure may clear via `ExplorationActivityGoal` while project semantics ignore band boundary.

---

## GoalSelector table

| Goal | Priority | Flags | vs ControlledDescent |
| --- | ---: | --- | --- |
| `ControlledDescentGoal` | 3 | MOVE, LOOK | Self |
| `GatherResourcesGoal` | 3 | MOVE, LOOK | Contests; descent registered first in `installExploration` only for explore bundle — gather registered before explore in `install()` |
| `ExploringGoal` | 8 | MOVE | Lower; descent preempts explore when active |

**Note:** `ControlledDescentGoal` registered inside `installExploration` at priority 3 **after** gather/craft/smelt in `install()`. Registration order at equal priority matters — gather may still win when both `canUse`.

---

## Time simulation (solid stone, EXHAUSTED, pick in backpack)

| Tick | Event |
| --- | --- |
| T0 | `canUse` true (EXHAUSTED + pick tier in backpack) |
| T0 | `start()` → `planNextStep` → `NO_HEADROOM` or `NO_HARVEST`; `currentStep == null`; `failedSteps=1` |
| T+1…T+2 | `tick` → `withTick` + `planNextStep` fail → `failedSteps` 2, 3 |
| T+3 | `isFailuresExhausted` (maxFailedSteps=3) → `finish(NO_PROGRESS)` |
| T+10 | Player sees mob stand at surface; **no staircase**; project cleared |

No path to `tickBreak` / `ToolBox.equipFor` in this trace.

---

## Predicted weird behaviors

| # | Behavior | Class |
| --- | --- | --- |
| 1 | Instant `NO_PROGRESS` in normal stone — zero blocks mined | `ARCHITECTURE_DEFECT` (R1/R2) |
| 2 | `CAVE_FOUND` only if footing block also air (unsupported pit) | `ARCHITECTURE_DEFECT` (R3) |
| 3 | Never `HANDOFF_TUNNEL_SEARCH` at Y band | `ARCHITECTURE_DEFECT` (R4) |
| 4 | Goal burns tick budget while replan-failing in place | `ARCHITECTURE_DEFECT` (R1 loop) |
| 5 | Pick in backpack passes gate but plan rejects — looks like "has tools but won't dig" | `ARCHITECTURE_DEFECT` (R2) |

---

## Must happen / must not (static verdict)

| Test | Static result |
| --- | --- |
| **Must happen:** first stair step in solid stone after EXHAUSTED | **FAIL** — R1 blocks |
| **Must not:** false reject when owned pick not in main hand | **FAIL** — R2 |
| **Must happen:** cave handoff uses MI-6 continuation evidence | **FAIL** — R3 |
| **Must happen:** band reached → `HANDOFF_TUNNEL_SEARCH` when demand blocks | **FAIL** — R4 |

Runtime probes remain required after MI-7R repair; they cannot upgrade current MI-7E to `PASS`.

---

## Recommended frontier (user `CONSENSUS` — adopted)

```text
DONE: MI-6F, MI-7A, MI-7B+C, MI-5H, MI-7D, MI-7E (code present; semantics broken)

NEXT: MI-7R Controlled Descent Semantic Repair
  R1  projected post-break geometry validation
  R2  canonical available-tool capability (ToolBox.ownsToolFor / bestSpeed)
  R3  real cave-opening handoff via CaveContext + MI-6 landing/opportunity
  R4  mining-band termination + HANDOFF_TUNNEL_SEARCH

THEN: MAIBS static re-simulation → runtime falsifying probes → MI-14
```

---

## Gate MAIBS-1

**`FAIL — ARCHITECTURE_DEFECT`**

MI-7E compile success and unit tests (`StairStepPlannerTest` geometry-only) do not exercise the executor loop. Task 25 status upgraded to **`BLOCKED`** pending MI-7R.

---

## MAIBS static re-pass (post-MI-7R, task 26)

**Date:** 2026-08-09  
**Mode:** Static simulation + unit tests (no Minecraft launch)  
**Gate result:** **`PASS_WITH_CONCERNS`** — R1–R4 repaired in code; runtime probes still required before MI-14.

| Defect | Post-MI-7R static verdict | Evidence |
| --- | --- | --- |
| R1 | **REPAIRED** | `validatePostBreakGeometry` + `StairStepSafetyTest` |
| R2 | **REPAIRED** | `BreakCapability.fromMob` / `validatePlan(level, plan, mob)` |
| R3 | **REPAIRED** | `ControlledDescentCaveHandoff` replaces three-air heuristic |
| R4 | **REPAIRED** | `shouldHandoffTunnelSearch` → `HANDOFF_TUNNEL_SEARCH` |

**Predicted trace (solid stone, EXHAUSTED, pick in backpack) — revised:**

| Tick | Event |
| --- | --- |
| T0 | `canUse` true; `planNextStep` → `validatePlan` **NONE** (projected geometry) |
| T0+ | `beginBreak` → hazard + `ownsToolFor` pass → `tickBreak` → `equipFor` |
| T+n | Steps complete; `openedTraversableCave` uses subterranean evidence, not air-below-feet |
| Band | At diamond-eligible Y with progression demand → `HANDOFF_TUNNEL_SEARCH` |

Runtime confirmation remains **`UNVERIFIED`** until explicit launch approval.
