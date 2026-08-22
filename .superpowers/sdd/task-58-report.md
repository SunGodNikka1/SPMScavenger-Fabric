# Task 58 report: V3-F opportunistic composting

**Status:** `CLOSED` / `STATIC-BEHAVIORAL ACCEPT`  
**Slice:** V3-F — `ComposterWorkFacts` + `CompostGoal`  
**Brief:** `.superpowers/sdd/task-58-brief.md` v1.1  
**Gate 0:** `.superpowers/sdd/task-58-gate0-report.md` — `GATE_0_PASS`  
**Target:** `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`  
**Closure:** 2026-08-22 — CLOSE-58-1…3 static repair complete

## Summary

Implemented V3-F as one unified slice:

- **Part A:** `ComposterWorkFacts` evidence layer on task-56 perception/work refresh cadence.
- **Part B:** priority-5 `CompostGoal` with single `ComposterBlock.insertItem` COMMIT,
  `CompostReserveModel` gen-1 seed surplus authority, and `CompostAdmission` gates.

Static closure repairs (CLOSE-58-1…3) landed in the same session:

| Close item | Repair |
| --- | --- |
| **CLOSE-58-1** | `CompostExpendabilityPolicy` ranking — highest surplus, then lowest slot |
| **CLOSE-58-2** | `CompostTargetSelector.rankedProbeOrder` — rank before path probes |
| **CLOSE-58-3** | `CompostScenarioEvidenceTest` — T58-2,4,5,7,8,9,11,15 |

## Reserve wording (locked)

`CompostReserveModel` is the explicit gen-1 authority for wheat/beetroot seed surplus.
`SellReserveModel.empty()` does not authorize compost spend, but it also must not veto items
explicitly modelled by `CompostReserveModel`. Unknown-to-compost materials remain fail-closed.

## Verification

| Command | CWD | Result |
| --- | --- | --- |
| `.\gradlew.bat compileJava` | `Projects/SPMScavenger-1.21.1-Fabric` | **CONFIRMED** — success |
| `.\gradlew.bat test` | `Projects/SPMScavenger-1.21.1-Fabric` | **CONFIRMED** — 1584 tests, 0 failures |

## Scenario coverage (static)

| ID | Evidence |
| --- | --- |
| T58-1 | `CompostExpendabilityPolicyTest.t58_1_seedSurplusPlansOneUnit` |
| T58-2 | `CompostScenarioEvidenceTest.t58_2_*` |
| T58-3 | `ComposterWorkObservationKernelBudgetTest` |
| T58-4 | `CompostScenarioEvidenceTest.t58_4_*` |
| T58-5 | `CompostScenarioEvidenceTest.t58_5_*` |
| T58-6 | `CompostReserveModelTest` |
| T58-7 | `CompostScenarioEvidenceTest.t58_7_*` |
| T58-8 | `CompostScenarioEvidenceTest.t58_8_*` |
| T58-9 | `CompostScenarioEvidenceTest.t58_9_*` |
| T58-10 | `CompostTaxonomyTest` |
| T58-11 | `CompostScenarioEvidenceTest.t58_11_*` |
| T58-12 | `CompostExpendabilityPolicyTest.t58_12_*` |
| T58-13 | `CompostExpendabilityPolicyTest.t58_13_*` |
| T58-14 | `CompostTransactionStructuralTest.t58_14_*` |
| T58-15 | `CompostScenarioEvidenceTest.t58_15_*` |
| T58-16 | `CompostTransactionStructuralTest.t58_16_*` |
| CLOSE-58-1 | `CompostExpendabilityPolicyTest.close58_1_*` |
| CLOSE-58-2 | `CompostTargetSelectorRankingTest` |

## Architecture verdict (closure review)

| Area | Verdict |
| --- | --- |
| Gate 0 | PASS |
| Evidence layer | ACCEPTED |
| Transaction ownership | ACCEPTED |
| P5 / taxonomy wiring | ACCEPTED |
| Reserve domain | ACCEPTED |
| CLOSE-58-1 ranking | REPAIRED |
| CLOSE-58-2 target order | REPAIRED |
| CLOSE-58-3 scenario proof | COMPLETE |

## UNVERIFIED (deferred)

| Item | Upgrade probe |
| --- | --- |
| VR-T3d runtime compost loop | Approved `runClient` |
| P5 vs P4 torch contention | Runtime observation (`RUNTIME_QUESTION`) |

## Frontier

- **Task-59 / V3-G** — HOLD until separately authorized
- **VR-T3d** — runtime deferred
