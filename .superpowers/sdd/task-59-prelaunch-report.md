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
| Shared bootstrap not deterministic / unoccupied beds | `setup_village_stub`: bell + **3 HOME beds**, **2 adult villagers** (AI on), night time, scheduled `claim_village_beds` with HOME memory + sleep registration | `_lib/setup_village_stub.mcfunction`, `_lib/claim_village_beds.mcfunction`; `SpmVrDatapackStructureTest.settlementBootstrapEstablishesOccupiedVillageEvidence` |
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

1. Allow **≥60 ticks** after preset load for `claim_village_beds` to run (settlement POI occupation).
2. VR-T3b: zombie appears at **t+120** — observe from preset load, not from zombie spawn alone.
3. VR-T3e: wait for `VillageWorkFacts` heartbeat before judging population-support candidacy.
4. Verify JAR hashes in `docs/porting/VR-T3-RUNTIME-ENVIRONMENT.md` before any future launch.

---

## Remaining `UNVERIFIED` (expected until runtime campaign)

| Item | Notes |
| --- | --- |
| Bed POI occupation in live world | NBT/sleep merge is best-effort without Minecraft boot — may need extra ticks or operator verification via `/data get entity` |
| All VR-T3 runtime rows | Separate User authorization required |
| `claim_village_beds` sufficiency | If POI still unoccupied at runtime, re-run `/function spm_vr:_lib/claim_village_beds` once before judging settlement rows |

---

## Launch gate (for your final pre-launch checkpoint)

| Gate | Ready? |
| --- | --- |
| Environment pin + VR-T3c wording + observation windows | **YES** |
| 13 presets with semantic shape fixes | **YES** |
| Structural + regression string guards | **YES** |
| Runtime VR-T3 evidence | **NO** — awaiting authorization |
