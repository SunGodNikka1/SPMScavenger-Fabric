# Task-56 report — V3-D transient village-work facts (`VillageWorkFacts`)

**Status:** `STATIC-BEHAVIORAL ACCEPT` — task-56 / V3-D closure (CLOSE-56-1/2); runtime VR-T3e/f **UNVERIFIED** (deferred to task-57 / V3 campaign).

**Target:** `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`  
**Brief:** `task-56-brief.md` v1 + **D-VR-083-A1** amendment (User, 2026-08-21)  
**Gate 0:** `task-56-gate0-report.md` — **PASS** after R2 vacancy lock

---

## Summary

Implemented transient, settlement-bound **`VillageWorkFacts`** with three HOME capacity layers and
**`PopulationSupportVacancyPolicy`** (D-VR-083-A1). Deleted **`eligibleBedCount`** /
**`freePopulationCapacity`** subtraction authority. Shared **`VillagePerceptionScheduler`** budget
with work-fact refresh deduped by **`SettlementIdentity`**. Anchor supersede invalidates stale cache
entries, cancels stale pending refresh for the superseded identity, and does not migrate counts or
proximity-merge identities.

**Closure repairs (2026-08-21):**

- **CLOSE-56-1** — `VillageWorkFactsScheduler.cancelPending` removes an exact
  `SettlementIdentity` from the dedup set and its dimension queue (empty lane retired). Wired from
  `VillageWorkFactsService.onAnchorSuperseded` after cache invalidation. `serviceUpTo` always
  invokes the refresh executor (test seam); production `refreshNow` no-ops on null level.
- **CLOSE-56-2** — `VillageWorkObservationKernel` uses lazy HOME POI iteration (no `.toList()`) and
  bounded `ServerLevel#getEntities(..., maxResults = MAX + 1)` for adult villagers. Injectable
  `HomePoiCandidateSource` / `AdultVillagerCandidateSource` seams prove enumeration stops at the
  evidence budget; over-budget → `INCOMPLETE` → no population-support candidacy.

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
| `.\gradlew.bat compileJava` | `SPMScavenger-1.21.1-Fabric` | **PASS** (`CONFIRMED`, closure re-run) |
| `.\gradlew.bat test` | `SPMScavenger-1.21.1-Fabric` | **PASS** — 1499 tests, 0 failures (`CONFIRMED`, closure re-run) |

New / closure tests:

| Test | Proves |
| --- | --- |
| `VillageWorkFactsSchedulerTest.close56_1_anchorSupersedeCancelsStalePendingIdentity` | enqueue A → supersede/cancel A → enqueue B → drain → A refreshes 0×, B 1×, A cache empty |
| `VillageWorkObservationKernelBudgetTest.close56_2_*` (3) | HOME/villager providers visited ≤ budget; over-cap → `INCOMPLETE` + no population-support candidacy |
| `VillageWorkFactsStructuralTest.mustHappen_observationKernelUsesBoundedEnumerationSeams` | no `.toList()`; `MAX_VILLAGERS + 1` seam; injectable HOME source |

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
| Anchor supersede invalidation + cancel pending | **DONE** — `onAnchorSuperseded` + `cancelPending` (CLOSE-56-1) |
| Candidate caps bound enumeration work | **DONE** — lazy HOME iterator + bounded `getEntities` (CLOSE-56-2) |
| No workstation fields | **DONE** |
| No P4 executor / task-57 | **DONE** |

---

## Concerns / deferred

- **Runtime VR-T3e/f** — settlement-wide vacancy vs breeder-local 48-block gate not play-tested.
- **Kernel POI touch** — second bounded touch in `village/work/` (V1 contract covers top-level `village/*.java` only).
- **Task-57** — population food executor + reachability probe not in scope.
