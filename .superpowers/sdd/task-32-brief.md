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

## Prerequisites (BLOCKER if missing)

| Item | Purpose |
|------|---------|
| Social Player Mobs 0.86.0+1.21.1 Fabric JAR | PlayerMob entities |
| Spark Fabric 1.21.1 JAR | Profiler |
| `build/libs/spmscavenger-1.9.2.jar` | Artifact under test |
| `test-datapacks/phase4-perf` enabled | Population + RT-PERF-F1 fixtures |

## Scenarios

| ID | Population | Duration | Spark |
|----|------------|----------|-------|
| P4A-1 | 1 | 120s profiler after 60s warm-up | `/spark profiler start --timeout 120` |
| P4A-10 | 10 | same | same |
| P4A-50 | 50 | same | same |
| P4A-100 | 100 | same | same |
| RT-PERF-F1 | 2 | manual observe | logs + furnace count |

Functions: `spm_phase4:spawn/count_{1,10,50,100}`

## Metrics to capture

- Server MSPT median / p95 (`/spark tps`, profiler export)
- Top hot methods: `createPath`, `planCurrentStage`, `findTarget`, `findUsable`, `FurnaceLookup`
- Scan synchrony: note if gather/furnace scans cluster same tick at N=50/100
- Client FPS/frame-time lows (integrated server, if profiling client)
- `OpinionExperienceRegistry.contextCount()` trend (optional F3+log if probe added later)

## Decision rubric

| Dominant hotspot | Next slice |
|------------------|------------|
| `ExploringGoal` / `createPath` | PERF-3 justified |
| `findTarget` / `findUsable` | Further scan repair before PERF-3 |
| Retained state / GC | PERF-5B |
| SPM/vanilla nav only | Do not rewrite Scavenger |

## Verification

- Fill `docs/porting/PERFORMANCE_LOG.md` rows with artifact paths.
- Report: `.superpowers/sdd/task-32-report.md`
- Status: `DONE` only if all four population Spark runs complete; else `BLOCKED` or `DONE_WITH_CONCERNS`.

## Launch command (after approval)

```powershell
cd "D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric"
.\gradlew.bat runClient
```

Copy `test-datapacks/phase4-perf` → instance `datapacks/`. Install SPM + Spark in `run/mods/`.

Report: `.superpowers/sdd/task-32-report.md`
