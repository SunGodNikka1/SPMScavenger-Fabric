# Task 20 brief: MI-7A — minimal MiningProject session state

## Target

`D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

## Source evidence

- RFC: `plans/RFC-MINING-INTELLIGENCE-AND-WEALTH-SYSTEM.md` — Topic MI-7, MiningProject modes, D-MIW-033
- Pattern: `src/main/java/com/noobk/spmscavenger/FurnaceJobSavedData.java` — dimension SavedData + NBT

## Binding

- MI-7A only — types, immutable session record, dimension SavedData store
- **Defer:** MI-7B budget exhaustion, MI-7C exhaustion policy, MI-7D/E goal wiring, MiningDirector
- No Minecraft launch, commit, or push
- Package: `com.noobk.spmscavenger.mining`
- Not a registered Goal; `GatherResourcesGoal` unchanged

## Requirements

### Types
- `MiningProjectMode` — all RFC modes including `CONTROLLED_DESCENT`
- `MiningProjectEnd` — terminal/interrupt reasons; maps to `TaskLifecycle`
- `MiningBudget` — immutable caps record + `controlledDescentDefaults()` (D-MIW-010 shape; exhaustion deferred MI-7B)
- `MiningProject` — immutable session: mode, origin, lastSafeAnchor, depthBelowOrigin, heading, budget, lifecycle, endReason, startedGameTime, coarseReturnRoute (cap 32)

### Session API
- `MiningProject.start(...)` factory
- `withLastSafeAnchor`, `withDepthBelowOrigin`, `pushReturnStep`, `complete(end)`
- `isActive()`, `isControlledDescent()`

### Persistence
- `MiningProjectSavedData` — one active/resumable project per mob UUID per dimension
- NBT round-trip; persist `RUNNING` and `INTERRUPTED` only

## Must happen
- Unit: start CONTROLLED_DESCENT project with defaults
- Unit: complete maps end reason to lifecycle
- Unit: SavedData save/load preserves active project
- Unit: return route capped at 32

## Must not
- Staircase dig executor; NaturalDescentStatus; goal wiring
- Usage counters / budget exhaustion (MI-7B)

## Verification

`.\gradlew.bat test` from project root

## Report

`.superpowers/sdd/task-20-report.md`
