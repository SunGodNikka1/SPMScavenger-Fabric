# Task 59 pre-launch readiness report (fixture sub-slice)

**Status:** `DONE` — semantic fixture repair complete; **Minecraft / VR-T3 campaign NOT AUTHORIZED**  
**Brief:** `.superpowers/sdd/task-59-brief.md`  
**Target:** `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`  
**Date:** 2026-08-22

---

## Authorization state

| Scope | Status |
| --- | --- |
| Task-59 preparation | **ACCEPTED** |
| Pre-launch fixture sub-slice (structure) | **DONE** |
| Fixture semantic repair sub-slice | **DONE** |
| Minecraft / `runClient` / batched VR-T3 | **NOT AUTHORIZED** |

---

## Semantic repair disposition (user-authorized slice)

| Defect | Fix | Evidence |
| --- | --- | --- |
| Shared bootstrap fake POI claim | **FIXED** — `claim_village_beds` TP-only; `settlementBootstrapDoesNotFakeHomeOwnership` forbids NBT injection | `_lib/claim_village_beds.mcfunction`; matrix gate 0 preflight |
| VR-T3e wrong recipient (second PlayerMob) | `population_food_deficit`: villager `spm_vr.villager2` cleared inventory + `spm_vr.food_recipient`; subject carries bread | `scenario/population_food_deficit.mcfunction`; `populationFoodDeficitTargetsVillagerRecipient` |
| VR-T3b immediate interrupt | Staged zombie via `schedule … stage_interrupt_zombie 120t` after `crop_managed_single` | `scenario/crop_interrupt_combat.mcfunction`, `_lib/stage_interrupt_zombie.mcfunction`; `cropInterruptCombatStagesInterruptionAfterPathingWindow` |
| VR-T3k two crops bypass contention | **One** mature wheat; two allies with seeds | `scenario/crop_multi_mob.mcfunction`; `cropMultiMobContendsForSingleMatureCrop` |
| VR-T3l Hunger effect ≠ wantsFood | Mature **carrots**, **empty backpack**, **oak logs** for mandatory gather → `VillageWorkAdmission` deny while `VILLAGE_ALLY` via `spawn_ally` | `scenario/crop_hungry_veto.mcfunction`; `cropHungryVetoEstablishesWantsFoodAndAdmissionDenialShape` |

**Explicit non-changes:** Tasks 52–58 production semantics untouched. No runtime instrumentation. No Minecraft launch.

---

## Structural test suite

`SpmVrDatapackStructureTest` — **14 tests** (shape + semantic regression guards).  
These do **not** prove runtime behavior; they block recurrence of the identified fixture mistakes.

---

## Build evidence (`CONFIRMED`)

| Command | Result |
| --- | --- |
| `.\gradlew.bat test` | **1603 tests**, 0 failures (`build/reports/tests/test/index.html`) |
| `SpmVrDatapackStructureTest` | 14/14 pass |

---

## Operator notes (live campaign — still unauthorized)

1. Run **settlement bootstrap preflight** (matrix gate 0) — ≥120t after stub load; halt on `FIXTURE_FAILURE`.
2. Allow vanilla bed acquisition; **do not** inject HOME/sleep NBT if preflight fails.

---

## Remaining `UNVERIFIED` (expected until runtime campaign)

| Item | Notes |
| --- | --- |
| Bed POI occupation in live world | Vanilla `PoiManager.take()` only — preflight gate 0 verifies before VR-T3 rows |

---

## Launch gate (for your final pre-launch checkpoint)

| Gate | Ready? |
| --- | --- |
| Environment pin + VR-T3c wording + observation windows | **YES** |
| 13 presets with semantic shape fixes | **YES** |
| Structural + regression string guards | **YES** |
| Runtime VR-T3 evidence | **NO** — awaiting authorization |

---

## Resume revalidation — 2026-08-23

Task-59 was resumed after V2-TE-W2.4 removed its temporary witness/fixture tooling. The runtime
environment is now repinned to the resulting clean production artifact; no V3 production code or
fixture semantics changed during this reconciliation.

| Evidence | Result |
| --- | --- |
| Focused `SpmVrDatapackStructureTest` | **14/14 PASS** |
| Executable scenario inventory | **13/13 present** — VR-T3a–e,g–m + D-VR-084 witness |
| `.\gradlew.bat clean build` | **PASS** — 1614 tests, 0 failures/errors/skips |
| Clean Scavenger JAR | `build/libs/spmscavenger-1.11.0.jar` |
| SHA-256 | `5EF3639FF03DA20191C3C83BCF662461DB081A8ABFA00E2CEBDC8C93A8B49BF9` |
| Host 0.89.0 artifact SHA-256 | `C8DC0E89C3FD632B6DCC7F8E46D3AE4955DD5504CBA53F72B62314850A64E612` — matches environment pin |
| Packaged upstream Trade Everything classes | **0** |
| Temporary V2-TE witness classes/references | **0 / 0** |

**AV-1 boundary:** fixture structure, artifact identity, compile, tests, and packaging are
`CONFIRMED`. All live VR-T3 behavior remains `UNVERIFIED`; Minecraft launch still requires explicit
authorization.
