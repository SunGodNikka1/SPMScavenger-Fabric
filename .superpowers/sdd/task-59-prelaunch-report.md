# Task 59 pre-launch readiness report (fixture sub-slice)

**Status:** `DONE` — pre-launch fixture sub-slice complete; **Minecraft / VR-T3 campaign NOT AUTHORIZED**  
**Brief:** `.superpowers/sdd/task-59-brief.md`  
**Prep report:** `.superpowers/sdd/task-59-report.md` (preparation phase — unchanged scope)  
**Target:** `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`  
**Date:** 2026-08-22

---

## Authorization state

| Scope | Status |
| --- | --- |
| Task-59 preparation | **ACCEPTED** (prior) |
| Task-59 pre-launch fixture sub-slice | **COMPLETE** |
| Minecraft / `runClient` / batched VR-T3 | **NOT AUTHORIZED** |

---

## User repair package — disposition

| # | Requirement | Status | Evidence |
| --- | --- | --- | --- |
| 1 | VR-T3c matrix + manifest — atomic abort, no bounded repair | **DONE** | `VR-T3-RUNTIME-MATRIX.md` VR-T3c row + contract section; `PRESET-MANIFEST.md` `crop_replant_failure`; `SpmVrDatapackStructureTest.scenarioFunctionsRejectBoundedRepairWording` |
| 2 | Per-row minimum observation windows | **DONE** | `VR-T3-RUNTIME-MATRIX.md` scenario table column + manifest per-preset windows |
| 3 | Implement all 13 `spm_vr` function bodies | **DONE** | `test-datapacks/phase-village-raid/data/spm_vr/function/scenario/*.mcfunction` (13 files) + `_lib/` (3 files) |
| 4 | Structural datapack validation (no Minecraft) | **DONE** | `SpmVrDatapackStructureTest` — 9 tests |
| 5 | Pin runtime environment / JAR hashes | **DONE** | `docs/porting/VR-T3-RUNTIME-ENVIRONMENT.md` |
| 6 | Pre-launch readiness report | **DONE** | this file |

---

## Terminology (bookkeeping)

- **12** applicable VR-T3 letter rows: `a–e`, `g–m` (VR-T3f excluded).
- **13** executable `spm_vr` preset IDs: twelve letter rows + **D-VR-084 witness**.
- **SPM 0.89 caution** reuses presets `crop_interrupt_combat` and `mandatory_blocks_village_work` — not a fourteenth preset.

---

## VR-T3c contract correction

**Before (incorrect):** “Preflight abort or bounded repair owns cleanup.”  
**After (task-55 atomic):** pre-COMMIT invalidation → **zero mutation**; optional `INVARIANT_FAILURE` only if a real transaction invariant is induced; **no repair phase**, no second mandatory publisher.

Fixture `crop_replant_failure` spawns ally **without** replant seeds so preflight must ABORT before harvest.

---

## Fixture implementation summary

| Component | Path |
| --- | --- |
| Datapack root | `test-datapacks/phase-village-raid/` |
| `pack.mcmeta` | `pack_format` 48 |
| Load tag | `data/minecraft/tags/function/load.json` → `spm_vr:load` |
| Shared lib | `_lib/reset`, `setup_village_stub`, `spawn_ally` |
| Scenarios | 13 × `scenario/<preset_id>.mcfunction` |

### Special fixture requirements

| Preset | Requirement | Implementation |
| --- | --- | --- |
| `mandatory_ownership_witness` | Real Gather publisher claim — no fake authority | Oak log volume + empty torch slot; no debug/injection commands (`SpmVrDatapackStructureTest`) |
| `storage_granted_permit` | Real explicit ownership seam | `/spmscavenger village storage own @s ~5 ~ ~` on exact mob+chest (`SpmVrDatapackStructureTest.storageGrantedPermitUsesProductionOwnCommand`) |

**Invoke:** `/function spm_vr:scenario/<preset_id>` at fixture anchor (operator position).

---

## Environment pin (`CONFIRMED` where cited)

| Component | Value |
| --- | --- |
| Minecraft | 1.21.1 |
| Fabric Loader | 0.16.14 |
| Fabric API | 0.116.4+1.21.1 |
| `playermob` 0.89.0 | `playermob-fabric-0.89.0+1.21.1.jar` SHA-256 `C8DC0E89C3FD632B6DCC7F8E46D3AE4955DD5504CBA53F72B62314850A64E612` |
| `spmscavenger` 1.11.0 | `spmscavenger-1.11.0.jar` SHA-256 `DD01B0E25854D9B541B715D4BD8AE1A8000698F35188DEF56427C1EEE352A562` |

Full record: `docs/porting/VR-T3-RUNTIME-ENVIRONMENT.md`.

---

## Verification commands

| Command | CWD | Result |
| --- | --- | --- |
| `.\gradlew.bat compileJava` | project root | **CONFIRMED** — BUILD SUCCESSFUL |
| `.\gradlew.bat test` | project root | **CONFIRMED** — **1598 tests**, 0 failures (`build/reports/tests/test/index.html`) |
| `.\gradlew.bat test --tests "com.noobk.spmscavenger.datapack.SpmVrDatapackStructureTest"` | project root | **CONFIRMED** — 9/9 pass |

**Production Java changed:** only `SpmVrDatapackStructureTest.java` (test-only). Task-52…58 production semantics untouched.

---

## Launch gate checklist (for next review)

| Gate | Ready? |
| --- | --- |
| Matrix semantics (VR-T3c, windows, terminology) | **YES** |
| 13 executable presets | **YES** |
| Structural validation | **YES** |
| Environment / JAR hashes | **YES** |
| Static test baseline | **YES** — 1598/0 |
| Runtime VR-T3 evidence | **NO** — requires separate User authorization |
| Datapack loaded in instance | **UNVERIFIED** — operator must copy pack + verify hashes before campaign |

---

## Remaining `UNVERIFIED` (expected — not blockers for authorization request)

| Item | Upgrade path |
| --- | --- |
| All VR-T3 runtime rows | Batched campaign after User approves `runClient` |
| Settlement perception latency | May need extra ticks after `setup_village_stub` in live world |
| `population_food_deficit` villager need state | May need operator tuning (extra villagers / hunger) if static stub insufficient |
| P5 vs P4 torch contention | `RUNTIME_QUESTION` — document if observed during VR-T3d |

---

## Recommended next step

User reviews this report + matrix + manifest. If fixtures faithfully realize the matrix, authorize
the batched VR-T3 campaign in one session per matrix campaign order. Re-seed world between clusters
if state bleeds.

**Must not happen without separate approval:** `runClient`, `runServer`, or any Minecraft boot.
