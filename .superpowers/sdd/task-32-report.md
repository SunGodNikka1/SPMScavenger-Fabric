# Task 32 report: PERF Slice 4A profiling checkpoint

## Status

`BLOCKED` — runtime prerequisites and Spark captures not executed in this session.

Slice 4A **protocol and fixtures are ready**; PERF RFC remains **open** pending evidence.

## Files created or changed

- `.superpowers/sdd/task-32-brief.md`
- `.superpowers/sdd/task-32-report.md`
- `docs/porting/PERFORMANCE_LOG.md` (template)
- `test-datapacks/phase4-perf/**` (population + RT-PERF-F1 scenario)
- `.superpowers/sdd/progress.md`

## Summary

Prepared the post-Slice-2 profiling checkpoint: PERFORMANCE_LOG template, phase4-perf datapack
with 1/10/50/100 PlayerMob spawn functions, furnace-duplicate edge-case scenario, and a decision
rubric aligned with the RFC. No `runClient` or Spark profiler was executed because the dev instance
has no `run/` tree and no Social Player Mobs or Spark JARs were found in the workspace.

## Commands and exact results

| Command | Result |
|---------|--------|
| `.\gradlew.bat clean test` | `BUILD SUCCESSFUL`; **527 tests**, 0 failures (`CONFIRMED`, `build/reports/tests/test/index.html`) |
| `.\gradlew.bat runClient` | **Not run** — blocked on mod prerequisites + launch approval workflow |
| Spark profiler | **Not run** |

## Blockers

1. **No dev run directory** — `Projects/SPMScavenger-1.21.1-Fabric/run/` absent; first `runClient` will bootstrap Gradle run folder.
2. **SPM JAR not in workspace** — `playermob` required (`NOT FOUND` under `D:\Apps\Minecraft Port`).
3. **Spark JAR not in workspace** — profiler mod required (`NOT FOUND`).
4. **No Spark artifact paths** — `docs/porting/PERFORMANCE_LOG.md` rows empty (`UNVERIFIED`).

## What is ready (CONFIRMED)

| Asset | Path |
|-------|------|
| Perf datapack README | `test-datapacks/phase4-perf/README.md` |
| Spawn presets | `spm_phase4:spawn/count_{1,10,50,100}` |
| Furnace race scenario | `spm_phase4:scenario/furnace_duplicate_setup`, `place_shared_furnace` |
| Log template | `docs/porting/PERFORMANCE_LOG.md` |
| Static baseline | `task-31-report.md` (527 tests) |

## Slice 4A launch plan (needs approval)

1. Place in `run/mods/`:
   - `spmscavenger-1.9.2.jar` (from `build/libs/`)
   - Social Player Mobs 0.86.0+1.21.1 Fabric
   - Spark (Fabric 1.21.1)
2. `.\gradlew.bat runClient`
3. Create flat world; enable datapack `phase4-perf`
4. `/function spm_phase4:setup` → `/function spm_phase4:arena/build`
5. For each N in {1,10,50,100}: `/function spm_phase4:spawn/count_N` → warm 60s → `/spark profiler start --timeout 120`
6. Fill `PERFORMANCE_LOG.md`; run RT-PERF-F1 manually
7. Apply decision rubric — **only then** authorize or defer PERF-3

## PERF-3 decision (pre-evidence)

| Question | Answer |
|----------|--------|
| Is PlanningSession justified now? | **UNVERIFIED** — no `createPath` Spark share recorded |
| Should we repair gather/furnace first? | **UNVERIFIED** — phased clocks not measured at N=50/100 |
| Is PERF-5B urgent? | **UNVERIFIED** — no heap/GC sample |

**Recommendation:** Do not begin Slice 3 until P4A-10 and P4A-50 Spark exports exist.

## Concerns

- First profiling session should use `opinionEnabled=false` to isolate Slices 0A–2.
- Repeat with `opinionEnabled=true` only if baseline implicates `OpinionExperienceRegistry`.
- RT-PERF-F1 may show duplicate furnaces as **product-acceptable** under current `ABSENT_RECENT`
  design — record counts, not assumed defect.

## Self-review

| Brief requirement | Status |
|-----------------|--------|
| No Slice 3 | Met |
| 1/10/50/100 protocol | Fixtures ready |
| Spark metrics defined | Template ready |
| PERFORMANCE_LOG | Created, unfilled |
| Runtime execution | **Blocked** |
