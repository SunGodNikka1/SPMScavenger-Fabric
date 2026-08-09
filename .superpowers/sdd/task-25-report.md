# Task 25 report: MI-7E

**Status:** `DONE_WITH_CONCERNS`

## Verification
`.\gradlew.bat test` — **218 tests, 0 failures** (`CONFIRMED`)

## Delivered
- `MiningProject` — `budgetUsage` field + NBT round-trip
- `ControlledDescentGoal.java` — EXHAUSTED gate, break/move loop, `MiningProjectSavedData`
- `SpmScavenger` — registered at priority 3 with shared `ExplorationReadiness`

## Must happen (code-level)
- `canUse` requires `NaturalDescentExhaustionPolicy.mayStartControlledDescent` (`CONFIRMED` — grep)
- No start without pick tier (`CONFIRMED` — `ToolTierPolicy`)

## Concerns
- Observable staircase descent, cave-mouth falsifying probe, budget stop — `UNVERIFIED`
- `CAVE_FOUND` handoff heuristic is coarse (3-air column)
