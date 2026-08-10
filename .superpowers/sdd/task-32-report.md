# Task 32 report: PERF Slice 4A — representative session & RFC close

## Status

`DONE_WITH_CONCERNS` — Spark analyzed from `Projects/sparkprofile_ai_enriched.json`; dominant Scavenger
hotspot fixed; PERF RFC **closed**; PERF-3 **deferred**.

## Spark session (`CONFIRMED`)

| Field | Value |
|-------|-------|
| Artifact | `Projects/sparkprofile_ai_enriched.json` (`t4wbc4UOKv.sparkprofile`) |
| Duration | 121s server thread |
| Instance | Fabulously Optimized (live overworld, **not** P4A isolated fixture) |
| PlayerMobs | 14 |
| MSPT median / p95 | 6.96 / 29.67 |
| `spmscavenger` self-time | **0.21%** (252ms) |

### Top Scavenger inclusive (within mod)

| Method | Inclusive |
|--------|-----------|
| `CraftTorchesGoal.findTable` / `atTable` | ~4196ms |
| `ExploringGoal.planCurrentStage` | ~1036ms |
| `GatherResourcesGoal.findTarget` | ~120ms |
| `FurnaceStations.findUsable` | ~88ms |

## Fix applied (`CONFIRMED` compile)

`CraftTorchesGoal` and `SmeltAtFurnaceGoal`: crafting-table search gated by `PhasedScanClock`
(interval 40, salts 71/72). Previously `findTable` ran up to **every tick** while approaching a
table — same ~16,807-probe volume as pre-Slice-1 furnace scans at radius 24.

- `CraftTorchesGoal.java`
- `SmeltAtFurnaceGoal.java`
- `CraftingTableScanCadenceTest.java`

`.\gradlew.bat compileJava` — **BUILD SUCCESSFUL**. Full test suite: **UNVERIFIED** (Gradle test
executor failure in session).

## PERF RFC disposition

| Package | Status |
|---------|--------|
| PERF-4, 5A, 1, 2 | Shipped |
| Craft-table phased scan | Shipped (post-Spark) |
| PERF-3 PlanningSession | **Deferred** |
| PERF-5B retention | **Deferred** |
| PERF-6 gate | **Closed** |

## Concerns

- Profile is **modpack live**, not controlled `p4a_representative` — explore sanity gate not isolated.
- Re-profile after craft-table fix to confirm `findTable` drop (optional).
- Gradle `:test` executor intermittently fails in this environment.
