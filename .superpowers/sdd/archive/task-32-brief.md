# Task 32 brief: PERF Slice 4A profiling checkpoint

## Target

`D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

## RFC

`plans/RFC-PERFORMANCE-AND-PERCEPTION.md` — Slice 4 (checkpoint only; does not close PERF RFC)

## Binding constraints

- **Do not** implement Slice 3 (`PlanningSession`) in this task.
- Profile before adding further abstractions.
- Requires **explicit** Minecraft launch approval (Gate 6).
- Record all results in `docs/porting/PERFORMANCE_LOG.md`.
- Use disposable flat world; `opinionEnabled=false` unless comparing opinion cost separately.
- **Never** issue `playermob stay` on profiling mobs (`StayAnchorState=PRESENT` blocks expeditions).
- Use **workload-split** datapack profiles; do not profile mixed stay+stress-cube fixture.

## Prerequisites (BLOCKER if missing)

| Item | Purpose |
|------|---------|
| Social Player Mobs 0.86.0+1.21.1 Fabric JAR | PlayerMob entities |
| Spark Fabric 1.21.1 JAR | Profiler |
| `build/libs/spmscavenger-1.9.2.jar` | Artifact under test |
| `test-datapacks/phase4-perf` enabled | Workload profiles + RT-PERF-F1 |

## Scenarios

| ID | Profile function | Population | Warm-up | Spark |
|----|------------------|------------|---------|-------|
| P4A-BASE-* | `profile/p4a_base/run_{1,10,50,100}` | 1 / 10 / 50 / 100 | 60s | 120s |
| P4A-GATHER-STRESS-* | `profile/p4a_gather_stress/run_{10,50}` | 10 / 50 | 60s | 120s |
| P4A-GATHER-SPARSE-* | `profile/p4a_gather_sparse/run_{10,50}` | 10 / 50 | 60s | 120s |
| P4A-SMELT-* | `profile/p4a_smelt/run_{10,50}` | 10 / 50 | 60s | 120s |
| P4A-EXPLORE-* | `profile/p4a_explore/run_{10,50}` | 10 / 50 | **90s** | 120s |
| RT-PERF-F1 | `scenario/furnace_duplicate_setup` | 2 | manual | logs |

### P4A-EXPLORE config (mandatory)

`gatherResources: false`, `smeltEnabled: false`, `exploring: true`, `opinionEnabled: false`

### P4A-EXPLORE sanity gate (mandatory before PERF-3 decision)

1. ≥1 mob creates an expedition
2. Spark: `ExploringGoal.planCurrentStage` > 0%
3. `createPath` activity from expedition planner observed

If any fail: **PROFILE INVALID FOR PERF-3 DECISION**

## Metrics to capture

- Server MSPT median / p95 (`/spark tps`, profiler export)
- Top hot methods per **matching profile**: `createPath`, `planCurrentStage`, `findTarget`, `findUsable`, `FurnaceLookup`
- Scan synchrony at N=50/100 on BASE / GATHER / SMELT
- Client FPS lows (optional)

## Decision rubric

| Dominant hotspot (in correct profile) | Next slice |
|---------------------------------------|------------|
| Explore / `createPath` / `planCurrentStage` in **P4A-EXPLORE** (sanity gate PASS) | PERF-3 justified |
| `findTarget` in **P4A-GATHER** | Further gather scan repair |
| `findUsable` in **P4A-SMELT** | Further furnace scan repair |
| Retained state / GC | PERF-5B |
| SPM/vanilla nav only | Do not rewrite Scavenger |

## Verification

- Fill `docs/porting/PERFORMANCE_LOG.md` rows with artifact paths.
- Report: `.superpowers/sdd/archive/task-32-report.md`
- Status: `DONE` only if BASE 1/10/50/100 + EXPLORE sanity gate + targeted profiles complete; else `BLOCKED` or `DONE_WITH_CONCERNS`.

## Launch command (after approval)

```powershell
cd "D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric"
.\gradlew.bat runClient
```

Copy `test-datapacks/phase4-perf` → instance `datapacks/`. Install SPM + Spark in `run/mods/`.

Report: `.superpowers/sdd/archive/task-32-report.md`
