# Task 58 report: V3-F opportunistic composting

**Status:** `DONE_WITH_CONCERNS`  
**Slice:** V3-F — `ComposterWorkFacts` + `CompostGoal`  
**Brief:** `.superpowers/sdd/task-58-brief.md` v1.1  
**Gate 0:** `.superpowers/sdd/task-58-gate0-report.md` — `GATE_0_PASS`  
**Target:** `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`  
**Date:** 2026-08-22

## Summary

Implemented V3-F as one unified slice:

- **Part A:** `ComposterWorkFacts` evidence layer on the existing task-56 perception/work refresh
  cadence (`VillageWorkFactsService.refreshNow` + shared scheduler invalidation).
- **Part B:** priority-5 `CompostGoal` with PATHING → INTERACT_PREPARE → single
  `ComposterBlock.insertItem` COMMIT, `CompostExpendabilityPolicy` / `CompostReserveModel`
  (gen-1 wheat/beetroot seed surplus after replant reserve = 1), and `CompostAdmission`
  (profile + mandatory + concurrent `VILLAGE_WORK` block at start).

No `VillageWorkFacts` retrofit, no executor block scanner, no bone-meal extraction, no
`MandatoryOwnership` publisher, no `VillageWorkSelector`.

## Verification

| Command | CWD | Result |
| --- | --- | --- |
| `.\gradlew.bat compileJava` | `Projects/SPMScavenger-1.21.1-Fabric` | **CONFIRMED** — success (via `test` task) |
| `.\gradlew.bat test` | `Projects/SPMScavenger-1.21.1-Fabric` | **CONFIRMED** — 1559 tests, 0 failures |

## New production types

| Area | Types |
| --- | --- |
| `village/work` | `ComposterWorkFacts`, `ComposterWorkFactsCache`, `ComposterWorkObservationKernel`, `ComposterWorkObservationService`, `ComposterWorkFactsService` |
| `village/compost` | `CompostTuning`, `CompostReserveModel`, `CompostMechanicalEligibility`, `CompostExpendabilityPolicy`, `CompostDeliveryPlan`, `CompostTransaction`, `CompostEpisodeCooldown`, `CompostTerminalOutcome`, `CompostTargetSelector`, `CompostAdmission` |
| `goal` | `CompostGoal` |

## Wiring

- `SpmScavenger.java` — `CompostGoal` at priority **5**; cooldown shutdown/release
- `MoveHolderClassifier.java` — `CompostGoal` → `ActivityClass.VILLAGE_WORK`
- `VillageWorkFactsService` — composter refresh + cache invalidation on anchor supersede/shutdown
- `VillageWorkTuning` — `MAX_COMPOSTERS_PER_OBSERVATION = 128`
- `FreshnessPolicy` — `ComposterWorkFacts` overload

## Scenario coverage (static)

| ID | Evidence |
| --- | --- |
| T58-1 | `CompostExpendabilityPolicyTest.t58_1_seedSurplusPlansOneUnit` |
| T58-3 | `ComposterWorkObservationKernelBudgetTest` |
| T58-6 | `CompostReserveModelTest` |
| T58-10 | `CompostTaxonomyTest` + `CompostAdmission.concurrentVillageWork` |
| T58-12 | `CompostExpendabilityPolicyTest.t58_12_unmodelledCompostableDoesNotPlan` |
| T58-13 | `CompostExpendabilityPolicyTest.t58_13_villagerBreedingFoodDoesNotPlan` |
| T58-14 | `CompostTransactionStructuralTest.t58_14_noExtractProducePath` |
| T58-16 | `CompostTransactionStructuralTest.t58_16_singleDebitOwnerUsesInsertItemThenMirrorShrink` |

## Concerns / UNVERIFIED

| Item | Status | Upgrade probe |
| --- | --- | --- |
| VR-T3d runtime compost loop | **UNVERIFIED** | Approved `runClient` with readable facts + seed surplus + loaded composter |
| P5 vs P4 torch contention | **UNVERIFIED** (`RUNTIME_QUESTION` per brief) | Observe goal arbitration with active `PlaceTorchGoal` |
| `CompostMechanicalEligibility` API | **ADAPTED** | Official mappings expose no public `ComposterBlock.getValue`; runtime uses `CompostingChanceRegistry` with Gate 0 offline fallback for unit tests |
| Pathing / interaction reach | **INFERRED** | Reused `REACH_DISTANCE_SQR = 4.0` harvest family |

## Self-review vs brief

- D58-1…D58-12 architecture locks respected; broad V3-D2 not implemented.
- Task-57 not reopened.
- Task-59 / V3-G not started.
