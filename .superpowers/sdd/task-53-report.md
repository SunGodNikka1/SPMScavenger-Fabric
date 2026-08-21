# Task 53 report — V3-A authority/profile foundation (`D-VR-080`, `D-VR-082-A1`)

**Status:** `DONE_WITH_CONCERNS` — static-behavioural acceptance; no runtime witness (AV-1: not
`CONFIRMED` in-world behaviour).
**Brief:** `.superpowers/sdd/task-53-brief.md` (User scope lock + 3 surgical corrections,
2026-08-20).
**Authorization:** User — *AUTHORIZED — implement task-53 / V3-A exactly from task-53-brief.md*
(2026-08-20).

## Files changed

| File | Role |
| --- | --- |
| `src/main/java/com/noobk/spmscavenger/village/VillageScenarioProfile.java` | **new** — `NEUTRAL`, `VILLAGE_ALLY`; unknown serialized → `NEUTRAL` |
| `src/main/java/com/noobk/spmscavenger/village/PlayerMobVillagePolicySavedData.java` | **new** — Overworld-canonical store; absence = `NEUTRAL`; peek-only reads/forget |
| `src/main/java/com/noobk/spmscavenger/village/VillageWorkAdmission.java` | **new** — profile + `MandatoryOwnership` consumer; preserves `authorityCause` |
| `src/main/java/com/noobk/spmscavenger/command/VillageProfileCommands.java` | **new** — operator `get` / `set neutral` / `set village_ally`; PlayerMob guard |
| `src/main/java/com/noobk/spmscavenger/activity/ActivityClass.java` | add `VILLAGE_WORK` (taxonomy only) |
| `src/main/java/com/noobk/spmscavenger/opinion/DiscretionaryEligibility.java` | `VILLAGE_WORK` joins `blocksDiscretionaryChoice`; `MAINTENANCE` unchanged |
| `src/main/java/com/noobk/spmscavenger/PerMobSavedData.java` | register `PlayerMobVillagePolicySavedData.forgetEverywhere` |
| `src/main/java/com/noobk/spmscavenger/SpmScavenger.java` | register `VillageProfileCommands` via `CommandRegistrationCallback` |
| `src/test/java/com/noobk/spmscavenger/village/VillageWorkAdmissionTest.java` | **new** — scenarios 1–6 |
| `src/test/java/com/noobk/spmscavenger/village/PlayerMobVillagePolicySavedDataTest.java` | **new** — scenarios 7–10 + store-specific + S11 |
| `src/test/java/com/noobk/spmscavenger/village/VillageWorkTaxonomyTest.java` | **new** — scenarios 11–12 |
| `src/test/java/com/noobk/spmscavenger/village/VillageWorkAdmissionWiringTest.java` | **new** — S1–S12 structural negatives |

**Explicitly not added:** P4 stub goal, `VillageWorkSelector`, intent enums, `MoveHolderClassifier`
pin, storage registry, new `MandatoryOwnership` publishers, task-52 semantic changes.

## Verification commands (from `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`)

| Command | Result |
| --- | --- |
| `.\gradlew.bat compileTestJava` (before implementation) | `BUILD FAILED` — **RED (compile)** — new test classes reference absent production types |
| `.\gradlew.bat clean compileJava compileTestJava test` (after implementation) | `BUILD SUCCESSFUL` — **1386 tests, 0 failures/errors/skips** (1357 pre-task-53 + **29 new**) |
| `.\gradlew.bat compileJava` (final) | `BUILD SUCCESSFUL` |
| `.\gradlew.bat test --tests "com.noobk.spmscavenger.village.VillageWork*" --tests "com.noobk.spmscavenger.village.PlayerMobVillagePolicy*"` | `BUILD SUCCESSFUL` — 29 task-53 tests green in isolation (with Bootstrap in policy test) |

## RED-before-GREEN

| Gate | Evidence |
| --- | --- |
| Pre-implementation RED | `compileTestJava` failed — missing `VillageScenarioProfile`, `VillageWorkAdmission`, `PlayerMobVillagePolicySavedData`, etc. |
| Scenario 4 (fail-open mandatory) | `scenario4_unclaimedDemandAllowsAlly` — green; **mutation CONFIRMED**: replacing `MandatoryOwnership.evaluate` with `Permission.allowed()` leaves scenario 4 green but breaks scenario 1 + S1 |
| Scenario 5 (NEUTRAL deny) | `scenario5_neutralProfileDenied` — green before and after implementation |

## Mutation / negative-control matrix

| Control | Mutation | Fails | Result |
| --- | --- | --- | --- |
| S1 | replace `MandatoryOwnership.evaluate(...)` with `Permission.allowed()` | `scenario1_pendingClaimDeniedWithMandatoryPendingCause` + `s1_admissionDelegatesToMandatoryOwnership` | `CONFIRMED` |
| S4 (frozen-demand shape) | same S1 mutation | scenario 4 still passes while scenario 1 fails — proves admission does not independently block on demand | `CONFIRMED` |
| S10 | `removeAssignment` writes `NEUTRAL` row instead of deleting | `setNeutralRemovesRow` (line 57) | `CONFIRMED` |
| S2–S9, S11, S12 | structural source tests (no mutation) | wiring / store tests | `CONFIRMED` (static) |

Reverted all mutations before final handoff; no `TEMP NEGATIVE-CONTROL` markers remain in
production sources.

## Scenarios 1–12 + store-specific

All rows green in full suite. Evidence: `CONFIRMED` (unit/structural/build). Runtime rows
`UNVERIFIED` by design.

| # | Test anchor | Key assertion |
| --- | --- | --- |
| 1 | `scenario1_pendingClaimDeniedWithMandatoryPendingCause` | `DENY_MANDATORY_AUTHORITY` + `MANDATORY_PENDING_CLAIM` |
| 2 | `scenario2_runningGatherDenied` | `MANDATORY_AUTHORITY` |
| 3 | `scenario3_runningTradeDenied` | `MANDATORY_AUTHORITY` |
| 4 | `scenario4_unclaimedDemandAllowsAlly` | permitted, both causes `NONE` |
| 5 | `scenario5_neutralProfileDenied` | `DENY_PROFILE` |
| 6 | `scenario6_canonicalStoreIsSingleSourceOfTruth` | single in-memory canonical store |
| 7 | `scenario7_allyRowSurvivesSimulatedUnload` | row preserved (no unload hook) |
| 8 | `scenario8_forgetRemovesAssignment` | assignment removed |
| 9 | `scenario9_saveReloadPreservesAlly` | NBT round-trip |
| 10 | `scenario10_missingEntryIsNeutral` + `loadUnknownValueDoesNotPreserveRow` | absent / unknown → no row |
| 11 | `scenario11_villageWorkBlocksDiscretionarySelection` | `VILLAGE_WORK` blocks |
| 12 | `scenario12_maintenanceDoesNotBlock` | `MAINTENANCE` does not block |

Store-specific: `profileOfUntouchedMobIsNeutralWithoutMaterializing`, `setVillageAllyCreatesOneEntry`,
`setNeutralRemovesRow`, `peekOnNullServerDoesNotMaterialize`.

## Persistence / lifecycle evidence

| Contract | Implementation |
| --- | --- |
| NEUTRAL = absence | `readProfile` → `NEUTRAL` when no map entry; `setProfile(..., NEUTRAL)` → `forget` via peek-only |
| Writes only on ally | `setProfile(..., VILLAGE_ALLY)` → `get(server)` (`computeIfAbsent`) |
| Reads never materialize | `profileOf` / `peek` / `forget` use non-creating `get` |
| Canonical host | `server.overworld().getDataStorage()` only — S12 structural |
| Permanent removal | `PerMobSavedData.forgetAll` → `PlayerMobVillagePolicySavedData.forgetEverywhere` |
| No unload eviction | `SpmScavenger` ENTITY_UNLOAD does not reference policy store — S6 |
| No silent cap | no `MAX_TRACKED_MOBS` / `prune` on policy store — S9 |
| Commands | `hasPermission(2)`; non-PlayerMob rejected with failure message |

## Self-review vs brief requirements

| # | Requirement | Status |
| --- | --- | --- |
| 1 | Two profiles; unknown → `NEUTRAL`; no reserved enum names | `CONFIRMED` |
| 2 | Cross-dimension via Overworld `DataStorage` | `CONFIRMED` (static + S12) |
| 3 | peek/get/forget/profileOf/setProfile semantics | `CONFIRMED` |
| 4 | RET-1: removal on permanent mob death only | `CONFIRMED` |
| 5 | Admission = profile + `MandatoryOwnership` only | `CONFIRMED` |
| 6 | Operator commands; no auto-promotion paths | `CONFIRMED` (static); command runtime `UNVERIFIED` |
| 7 | `VILLAGE_WORK` taxonomy pin | `CONFIRMED` |
| 8 | No P4 goal / no classifier pin | `CONFIRMED` (absent) |
| 9 | Admission not a publisher; no forbidden imports | `CONFIRMED` (S2–S4) |
| 10 | Registered in `PerMobSavedData.forgetAll` | `CONFIRMED` (S8) |

## Concerns / deferred (expected)

| Item | Notes |
| --- | --- |
| **No production consumer of `VillageWorkAdmission` yet** | By brief design — no V3 executor goal exists to call admission from `canUse`. Seam is implemented and tested at the pure boundary. |
| **Unwired Trade/Mining publishers** | task-52 scenario 10 — pending side fail-open for those episodes until separately wired. |
| **Runtime witness** | Deferred to batched V3 campaign — profile command in-world, admission under live mandatory contention, ally persistence across dimension travel. |
| **`VillageWorkSelector` / intent enum** | Deferred per User scope lock — decide when first executor batch lands. |
| **`MoveHolderClassifier` pin** | Deferred until first real V3 executor task. |

## Next frontier

**Authorize task-54 — V3-B** (`StorageOwnership` / D-VR-081). Do not begin without separate
authorization.
