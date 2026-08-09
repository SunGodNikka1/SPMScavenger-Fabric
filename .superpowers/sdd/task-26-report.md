# Task 26 report: MI-7R — Controlled Descent Semantic Repair

**Status:** `DONE_WITH_CONCERNS`

## Verification
`.\gradlew.bat test` — **225 tests, 0 failures** (`CONFIRMED`, cwd `Projects/SPMScavenger-1.21.1-Fabric`)

## Delivered

| ID | Fix | Evidence |
| --- | --- | --- |
| **R1** | `StairStepSafety.validatePostBreakGeometry` projects break cells as passable before headroom/footing checks | `StairStepSafety.java`; `StairStepSafetyTest.solidStoneStairPlanAcceptsWithPostBreakProjection` |
| **R2** | `BreakCapability.fromMob` / `ToolBox.ownsToolFor`; `ControlledDescentGoal` uses `validatePlan(level, plan, mob)` and `ownsToolFor` at break time | `StairStepSafety.java` L32–53, L58–60; `ControlledDescentGoal.java` L234–241, L255 |
| **R3** | `ControlledDescentCaveHandoff` — MI-6 `CaveContextPolicy.isSubterranean` + `CaveLandingResolver` ahead probe; removed three-air `openedCave()` | `ControlledDescentCaveHandoff.java`; `ControlledDescentGoal.java` L278–281 |
| **R4** | `shouldHandoffTunnelSearch` — diamond band + `diamondProgressionDemand > 0` → `HANDOFF_TUNNEL_SEARCH` in `tick` and `completeStep` | `ControlledDescentGoal.java` L164–167, L283–301 |

## Tests added
- `StairStepSafetyTest` (3) — post-break projection, capability rejection, geometry helper
- `ControlledDescentCaveHandoffTest` (4) — subterranean classification, no false surface handoff

## MAIBS static re-pass (post-MI-7R)

| Defect | Static verdict |
| --- | --- |
| R1 | **REPAIRED** — solid-stone plan accepts with projected geometry (`StairStepSafetyTest`) |
| R2 | **REPAIRED** — plan uses `BreakCapability.fromMob`; break path uses `ToolBox.ownsToolFor` after equip gate |
| R3 | **REPAIRED** — handoff uses subterranean + landing resolver; footing-compatible |
| R4 | **REPAIRED** — band termination wired to `HANDOFF_TUNNEL_SEARCH` |

**Gate MAIBS-1 (static):** `PASS_WITH_CONCERNS` — executor loop semantics repaired in code; **runtime falsifying probes still UNVERIFIED**.

## Concerns
- No in-world staircase / cave-handoff / band-termination runtime proof (`UNVERIFIED`)
- `BreakCapability.fromTool(ItemStack.EMPTY)` still treats empty hand as harvest-capable (legacy helper; mob path uses `fromMob`)
- GoalSelector priority-3 contention with `GatherResourcesGoal` unchanged (`INFERRED` from task-25 report)
- Task 25 status remains `BLOCKED` until runtime probes; MI-7E semantics repaired by MI-7R

## Self-review vs brief
- All four R1–R4 requirements implemented and unit-tested per brief
- `task-25-maibs-report.md` static re-check recorded above
- No Minecraft launch, no commits
