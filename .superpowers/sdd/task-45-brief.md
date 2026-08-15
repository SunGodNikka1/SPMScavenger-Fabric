# Task 45 — V1-D production village perception driver

**Status:** authorized (User: Begin V1-D)  
**Target version:** 1.10.0  
**RFC:** `plans/RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md` — V1-D task contract, D-VR-033

## Goal

Wire a bounded production perception driver so ticking PlayerMobs learn villages through
`VillagePerception.observe` → `VillageMemorySavedData.record` without MOVE/LOOK flags or a shared
observation cache.

## Source evidence

| Path | Role |
| --- | --- |
| `village/VillagePerception.java` | Sole POI touch; filtered `Observation` |
| `village/VillageMemorySavedData.java` | `record(mob, observation, tick)` |
| `goal/ExplorationActivityGoal.java` | Flagless observer precedent (priority 9) |
| `goal/AnticsGoal.java` | `EnumSet.noneOf(Flag.class)` pattern |
| `SpmScavenger.java` | ENTITY_LOAD install, unload/death lifecycle |

## Binding constraints

- No Minecraft launch (VR-T1 separate approval).
- No commits unless user requests.
- ≤1 global POI query per server tick.
- Observer must not touch `PoiManager` — scheduler services at mob `blockPosition()`.
- RET-1: queue keyed `(dimension, uuid)`; evict on unload/death; shutdown on server stop.

## Deliverables

1. `VillagePerceptionObserver` — flagless Goal, chunk-dirty + heartbeat, enqueue only.
2. `VillagePerceptionScheduler` — server singleton, fair admission, round-robin lanes.
3. `VillagePerceptionService` — `observeAndRecord`.
4. `VillagePerceptionTuning` — named constants (200/20/1/256).
5. `SpmScavenger` registration + `ServerTickEvents.END_SERVER_TICK`.
6. Static tests: B-VR-57 admission, global budget, lifecycle cleanup, contract updates.

## Must happen

- Production path from ticking PlayerMob to `record`.
- ≤1 POI query/server tick (structural + unit test).
- No MOVE/LOOK on observer.

## Must not happen

- Shared observation cache.
- Stale queue UUIDs after unload/death.
- Admission starvation among ≤100 simultaneous observers.
- Hidden POI in supersede (V1-R4 regression).

## Verification

```powershell
cd Projects\SPMScavenger-1.21.1-Fabric
.\gradlew.bat test --tests "*village*"
.\gradlew.bat clean build
```

Runtime village perception: `UNVERIFIED` until VR-T1.

## Report

`.superpowers/sdd/task-45-report.md`
