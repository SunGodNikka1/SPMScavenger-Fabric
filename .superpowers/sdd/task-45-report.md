# Task 45 report — V1-D production village perception driver

**Status:** `DONE`  
**Version:** 1.10.0  
**Brief:** `.superpowers/sdd/task-45-brief.md`

## Summary

Shipped the bounded production perception driver (D-VR-033 / V1-D): flagless
`VillagePerceptionObserver`, server-scoped `VillagePerceptionScheduler` with fair admission and
≤1 global POI query per tick, and `VillagePerceptionService` bridging `VillagePerception.observe` →
`VillageMemorySavedData.record`. Wired in `SpmScavenger` with `ServerTickEvents.END_SERVER_TICK`
and RET-1 cleanup on unload/death/server stop.

**VR-T1A (2026-08-14):** User closed core village perception runtime as **PASS** — autonomous
discovery, full observer→scheduler→service→record path, stable same-village identity after
leave/return, save/reload persistence, and cross-dimension persistence (Bob session). Debounce
`Long.MIN_VALUE` overflow was runtime-confirmed as the V1-D blocker; `VillagePerceptionEnqueueDebounce`
repair runtime-confirmed.

**Post-VR-T1A cleanup:** removed temporary `village-probe`, `village-driver`, `village-memory`
commands and all trace/diagnostics plumbing (`VillagePerceptionServiceTrace`,
`VillagePerceptionDriverDiagnostics`). Contract test guards against reintroduction.

## Verification commands

| Command | CWD | Result |
| --- | --- | --- |
| `.\gradlew.bat test --tests "*village*"` | `Projects/SPMScavenger-1.21.1-Fabric` | **CONFIRMED** — BUILD SUCCESSFUL |
| `.\gradlew.bat clean build` | `Projects/SPMScavenger-1.21.1-Fabric` | **CONFIRMED** — post VR-T1A cleanup (this session) |

## Evidence (CONFIRMED — static)

- Production path: `VillagePerceptionService.observeAndRecord` → `VillagePerception.observe` →
  `VillageMemorySavedData.record` (`village/VillagePerceptionService.java`).
- Observer flagless: `EnumSet.noneOf(Goal.Flag.class)` (`goal/VillagePerceptionObserver.java`).
- Enqueue debounce: `VillagePerceptionEnqueueDebounce` with `hasEnqueued` guard
  (`goal/VillagePerceptionEnqueueDebounce.java`); regression tests include legacy overflow negative control.
- Global budget: `GLOBAL_QUERY_BUDGET_PER_TICK = 1` (`village/VillagePerceptionTuning.java`);
  `VillagePerceptionSchedulerTest.mustHappen_globalBudgetOneQueryPerServerTick`.
- Fair admission: `VillagePerceptionSchedulerTest` (6 tests) — dedup, observer-bound capacity,
  emergency cap refuse, unregister cleanup, dimension round-robin.
- Reload registration: `ensureVillagePerceptionObserver` on `alreadyInstalled` early return (`SpmScavenger.java`).
- VR-T1A diagnostics removed: contract `mustHappen_vrT1aDiagnosticsRemoved`.
- RET-1: queue keyed `(dimension, uuid)`; `unregisterObserver` on unload/death; `shutdown` on server stop.

## Runtime evidence (CONFIRMED — User, Bob session, VR-T1A PASS 2026-08-14)

| Check | Result |
| --- | --- |
| Autonomous record | Driver path RECORDED 7–8 POIs; anchor `-11666, 82, 7709` |
| Pre-fix failure | Debounce overflow: dirty YES, enqueue never, service NOT_RUN, memory 0 |
| Leave ~400 blocks | Still 1 village; same anchor; `First seen: 123682` |
| Return to village | Same anchor; `First seen` unchanged; `Last seen: 133393` |
| Save/reload | Anchor and `First seen` persisted |
| Cross-dimension | Per-user session confirmation |

## Must happen / must not happen (brief)

| Gate | Status | Evidence |
| --- | --- | --- |
| Production `observe` → `record` for ticking PlayerMobs | **CONFIRMED** (runtime) | VR-T1A Bob session |
| ≤1 global POI query/server tick | **CONFIRMED** (unit test) | Scheduler test + tuning constant |
| No MOVE/LOOK on observer | **CONFIRMED** (structural) | Contract test + source |
| No shared observation cache | **CONFIRMED** (inspection) | No cache class; per-mob memory only |
| No admission starvation ≤100 observers | **CONFIRMED** (unit test) | 10-mob admission test; bound = observer count |
| No stale queue UUID after unload/death | **CONFIRMED** (unit test) | Unregister test + SpmScavenger hooks |
| V1-R4 supersede regression | **CONFIRMED** (existing tests) | `PerceptionCoverageTest` unchanged green |
| VR-T1A diagnostics removed post-PASS | **CONFIRMED** (structural) | Files deleted; contract test |

## Deferred (not blocking V1 / VR-T1A)

- **VR-T1b:** 10/50/100-mob backlog + B-VR-58 POI query cost profiling
- **Permanent-removal sweep:** static-confirmed (`mustHappen_permanentRemovalSweepsEveryDimension`); runtime eviction probe deferred
- **Anchor vs `Raid.getCenter()`:** deferred to V5 raid work

## Self-review vs brief

All in-scope deliverables shipped. VR-T1A closed PASS. Temporary diagnostics removed per user directive.
Frontier advanced to V2 Trading in RFC.

## Artifact path

`Projects/SPMScavenger-1.21.1-Fabric/build/libs/spmscavenger-1.10.0.jar` (from clean build).
