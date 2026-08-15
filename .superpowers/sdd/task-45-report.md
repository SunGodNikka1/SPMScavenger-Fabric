# Task 45 report — V1-D production village perception driver

**Status:** `DONE_WITH_CONCERNS`  
**Version:** 1.10.0  
**Brief:** `.superpowers/sdd/task-45-brief.md`

## Summary

Shipped the bounded production perception driver (D-VR-033 / V1-D): flagless
`VillagePerceptionObserver`, server-scoped `VillagePerceptionScheduler` with fair admission and
≤1 global POI query per tick, and `VillagePerceptionService` bridging `VillagePerception.observe` →
`VillageMemorySavedData.record`. Wired in `SpmScavenger` with `ServerTickEvents.END_SERVER_TICK`
and RET-1 cleanup on unload/death/server stop.

## Verification commands

| Command | CWD | Result |
| --- | --- | --- |
| `.\gradlew.bat test --tests "*village*"` | `Projects/SPMScavenger-1.21.1-Fabric` | **CONFIRMED** — BUILD SUCCESSFUL |
| `.\gradlew.bat clean build` | `Projects/SPMScavenger-1.21.1-Fabric` | **CONFIRMED** — BUILD SUCCESSFUL, full suite green (872 tests, +8 vs 864) |

## Evidence (CONFIRMED — static)

- Production path: `VillagePerceptionService.observeAndRecord` → `VillagePerception.observe` →
  `VillageMemorySavedData.record` (`village/VillagePerceptionService.java`).
- Observer flagless: `EnumSet.noneOf(Goal.Flag.class)` (`goal/VillagePerceptionObserver.java`).
- Global budget: `GLOBAL_QUERY_BUDGET_PER_TICK = 1` (`village/VillagePerceptionTuning.java`);
  `VillagePerceptionSchedulerTest.mustHappen_globalBudgetOneQueryPerServerTick`.
- Fair admission: `VillagePerceptionSchedulerTest` (6 tests) — dedup, observer-bound capacity,
  emergency cap refuse, unregister cleanup, dimension round-robin.
- Contract tests updated: `mustHappen_v1DProductionObservePathExists`, flagless observer guard;
  village package still has no Goal.
- RET-1: queue keyed `(dimension, uuid)`; `unregisterObserver` on unload/death; `shutdown` on
  server stop (`SpmScavenger.java`).

## Must happen / must not happen (brief)

| Gate | Status | Evidence |
| --- | --- | --- |
| Production `observe` → `record` for ticking PlayerMobs | **CONFIRMED** (static wiring) | Service + SpmScavenger install |
| ≤1 global POI query/server tick | **CONFIRMED** (unit test) | Scheduler test + tuning constant |
| No MOVE/LOOK on observer | **CONFIRMED** (structural) | Contract test + source |
| No shared observation cache | **CONFIRMED** (inspection) | No cache class; per-mob memory only |
| No admission starvation ≤100 observers | **CONFIRMED** (unit test) | 10-mob admission test; bound = observer count |
| No stale queue UUID after unload/death | **CONFIRMED** (unit test) | Unregister test + SpmScavenger hooks |
| V1-R4 supersede regression | **CONFIRMED** (existing tests) | `PerceptionCoverageTest` unchanged green |

## Concerns / UNVERIFIED

- **Runtime village perception (VR-T1):** `UNVERIFIED` — no Minecraft launch authorized. Mobs
  physically learning villages in loaded POI worlds not observed.
- **Traversal miss under backlog (B-VR-56):** `UNVERIFIED` — conditional Must happen is
  architecturally satisfied; sprint-through-hamlet at 50–100 mobs not measured.
- **Disabled addon:** observer and service gate on `ScavengerConfig.enabled`; existing persisted
  memory preserved — **INFERRED** from code, not runtime-proven.

## Self-review vs brief

All in-scope deliverables shipped. Out of scope (bell, trade, raid, VR-T1 datapack) not touched.
`mod_version` bumped to 1.10.0 in `gradle.properties`.

## Artifact path

`Projects/SPMScavenger-1.21.1-Fabric/build/libs/spmscavenger-1.10.0.jar` (from clean build).
