# Task 25 report: MI-7E

**Status:** `BLOCKED` — MAIBS-1 `FAIL — ARCHITECTURE_DEFECT` (see `task-25-maibs-report.md`; MI-7R required)

## Verification
`.\gradlew.bat test` — **218 tests, 0 failures** (`CONFIRMED` compile gate only)

## Delivered
- `MiningProject` — `budgetUsage` field + NBT round-trip
- `ControlledDescentGoal.java` — EXHAUSTED gate, break/move loop, `MiningProjectSavedData`
- `SpmScavenger` — registered at priority 3 with shared `ExplorationReadiness`

## MAIBS-1 defects (`CODE_CONFIRMED`)
| ID | Defect |
| --- | --- |
| R1 | `validatePlan` checks destination air **before** planned breaks |
| R2 | `hasUsablePick` uses backpack; `validatePlan` uses main hand only |
| R3 | `openedCave()` requires air below feet; safety requires sturdy below |
| R4 | No `HANDOFF_TUNNEL_SEARCH` / band termination |

## Concerns
- Observable staircase descent — **cannot occur** in solid stone until R1 fixed (`CODE_CONFIRMED` static trace)
- Runtime probes deferred until MI-7R + MAIBS re-pass
