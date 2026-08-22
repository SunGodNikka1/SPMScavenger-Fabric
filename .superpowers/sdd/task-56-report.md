# Task-56 report — V3-D transient village-work facts (`VillageWorkFacts`)

**Status:** `DONE` — static behavioral accept; runtime VR-T3e/f **UNVERIFIED** (deferred to task-57 / V3 campaign).

**Target:** `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`  
**Brief:** `task-56-brief.md` v1 + **D-VR-083-A1** amendment (User, 2026-08-21)  
**Gate 0:** `task-56-gate0-report.md` — **PASS** after R2 vacancy lock

---

## Summary

Implemented transient, settlement-bound **`VillageWorkFacts`** with three HOME capacity layers and
**`PopulationSupportVacancyPolicy`** (D-VR-083-A1). Deleted **`eligibleBedCount`** /
**`freePopulationCapacity`** subtraction authority. Shared **`VillagePerceptionScheduler`** budget
with work-fact refresh deduped by **`SettlementIdentity`**. Anchor supersede invalidates stale cache
entries without count migration.

---

## D-VR-083-A1 lock (implemented)

```text
Population-support candidate (not permission):
    facts == FRESH + COMPLETE
    AND adultVillagerCount >= 2
    AND currentFreeHomeCapacity > 0
```

Task-57 owns breeder-local 48-block `HAS_SPACE` + reachability revalidation before food commit.

---

## Deliverables

| Component | Path |
| --- | --- |
| `SettlementIdentity` | `village/work/SettlementIdentity.java` |
| `VillageWorkFacts` + enums | `village/work/VillageWorkFacts.java`, `WorkFactsCompleteness`, `WorkFactsFreshness` |
| Observation kernel/service | `VillageWorkObservationKernel.java`, `VillageWorkObservationService.java` |
| Cache + scheduler + service | `VillageWorkFactsCache.java`, `VillageWorkFactsScheduler.java`, `VillageWorkFactsService.java` |
| Policies | `FreshnessPolicy.java`, `PopulationSupportVacancyPolicy.java` |
| Tuning | `VillageWorkTuning.java` |
| Diagnostics | `VillageWorkFactsDiagnostics.java` |
| Wiring | `VillagePerceptionService`, `VillageMemorySavedData.record`, `VillagePerceptionScheduler.onServerTick`, `SpmScavenger` shutdown |
| RFC | `plans/RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md` — D-VR-083 + **D-VR-083-A1** |

---

## Verification

| Command | CWD | Result |
| --- | --- | --- |
| `.\gradlew.bat compileJava` | `SPMScavenger-1.21.1-Fabric` | **PASS** (`CONFIRMED`) |
| `.\gradlew.bat test` | `SPMScavenger-1.21.1-Fabric` | **PASS** — 1495 tests, 0 failures (`CONFIRMED`) |

New tests: `village/work/*Test.java` (policy, freshness, cache, scheduler, structural).

---

## Self-review vs brief

| Requirement | Status |
| --- | --- |
| Transient facts, not SavedData | **DONE** |
| No `KnownVillage` population persistence | **DONE** — structural test |
| Three HOME layers + `adultVillagerCount` | **DONE** |
| No `eligibleBedCount` / subtraction | **DONE** — structural test |
| `VillageWorkAdmission` unchanged | **DONE** — structural test |
| Scheduler budget sharing | **DONE** — remaining budget after perception drain |
| Anchor supersede invalidation | **DONE** — `VillageMemorySavedData.record` |
| No workstation fields | **DONE** |
| No P4 executor / task-57 | **DONE** |

---

## Concerns / deferred

- **Runtime VR-T3e/f** — settlement-wide vacancy vs breeder-local 48-block gate not play-tested.
- **Kernel POI touch** — second bounded touch in `village/work/` (V1 contract covers top-level `village/*.java` only).
- **Task-57** — population food executor + reachability probe not in scope.
