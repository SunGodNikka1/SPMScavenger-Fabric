# VR-T3 runtime matrix (V3-G closure)

**Status:** `APPROVED` for preparation — fixtures **IMPLEMENTED** (pre-launch); **runtime execution NOT
AUTHORIZED** until separate User launch approval (AGENTS.md Gate 6).

**Task:** task-59 / V3-G · **Brief:** `.superpowers/sdd/task-59-brief.md`  
**RFC:** `plans/RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md` — phase closure rule (VR-T3f non-applicable)  
**Environment pin:** `docs/porting/VR-T3-RUNTIME-ENVIRONMENT.md`

---

## Terminology

| Count | Meaning |
| --- | --- |
| **12** | Applicable VR-T3 **letter rows** — `a–e`, `g–m` (VR-T3f excluded) |
| **13** | Executable **`spm_vr` preset IDs** — twelve letter rows + **D-VR-084 witness** |
| **SPM 0.89 caution** | Reuses existing presets (`crop_interrupt_combat`, `mandatory_blocks_village_work`) — **not** a fourteenth preset |

---

## Mod set (canonical baseline)

| Mod | Version | Required | Notes |
| --- | --- | --- | --- |
| **Social Player Mobs** (`playermob`) | **0.89.0** | **yes** | SHA-256 pinned in `VR-T3-RUNTIME-ENVIRONMENT.md` |
| **SPM Scavenger** (`spmscavenger`) | **1.11.0** (task-59 build) | **yes** | SHA-256 pinned in environment doc |
| **Fabric API** | **0.116.4+1.21.1** | **yes** | Loader **0.16.14** — see environment doc |
| Trade Everything | — | **no** | Contaminates uncontaminated village-work proof (V2 `D-VR-069` precedent) |
| Optional compat mods | — | **no** | Unless a row is explicitly marked optional |

**Proof chain:** uncontaminated V3 runtime rows use **only** the three required mods above plus the
`spm_vr` datapack when a preset is listed.

---

## Launch gate

| Gate | Status |
| --- | --- |
| Runtime matrix document | **COMPLETE** (this file) |
| `spm_vr` preset manifest | `test-datapacks/phase-village-raid/PRESET-MANIFEST.md` |
| `spm_vr` executable fixtures | **COMPLETE** — `test-datapacks/phase-village-raid/data/spm_vr/function/` |
| Structural datapack validation | `SpmVrDatapackStructureTest` (no Minecraft boot) |
| Operator runbook | `test-datapacks/phase-village-raid/README.md`; `/function spm_vr:help` |
| Passive hidden-authority snapshot | `/spmscavenger debug v3 inspect <mob>` — temporary one-shot read only |
| Evidence record | `docs/porting/VR-T3-RUNTIME-EVIDENCE.md` |
| Environment / JAR hashes | `docs/porting/VR-T3-RUNTIME-ENVIRONMENT.md` |
| Static/build baseline | see `task-59-prelaunch-report.md` |
| **Minecraft campaign** | **NOT AUTHORIZED** — User must approve `runClient` / batched VR-T3 separately |

---

## Applicable scenario table

**Closure rule:** V3 requires V3-A…G plus all **applicable** VR-T3a–m rows. **VR-T3f is NOT
applicable** while broad V3-D2 remains deferred.

| ID | Must happen | Must not happen | `spm_vr` preset | Min observation window | Static evidence | Runtime status |
| --- | --- | --- | --- | --- | --- | --- |
| **VR-T3a** | Managed mature crop ends replanted in one committed episode | Bare farmland after visible harvest | `crop_managed_single` | From preset load through **terminal COMMIT** (replant age 0) + **200 ticks** post-terminal stabilization | `CropHarvestTransactionTest`, task-55 report | `UNVERIFIED` |
| **VR-T3b** | Interruption before interaction → no world mutation | Blind resume of stale path/target | `crop_interrupt_combat` | From interrupt trigger through **600 ticks** after combat/target loss; log must show revalidation, not stale resume | static `INFERRED` — task-55 | `UNVERIFIED` |
| **VR-T3c** | Pre-COMMIT invalidation → **zero mutation**; optional `INVARIANT_FAILURE` only if a real transaction invariant is induced | Harvest/replant mutation; discretionary explore gap; **any repair phase** | `crop_replant_failure` | From PATHING start through **terminal ABORT** (or `INVARIANT_FAILURE` log) + **200 ticks** with crop state unchanged | `CropHarvestTransactionTest` (atomic abort contract) | `UNVERIFIED` |
| **VR-T3d** | One compost unit consumed; level advance or unchanged both terminate | Double debit; reserve violation; bone-meal demand | `compost_seed_surplus` | One activation through **terminal** (level change or unchanged) + **400 ticks** with no second debit | `CompostScenarioEvidenceTest`, task-58 report | `UNVERIFIED` |
| **VR-T3e** | One disposable food delivery when deficit exists | Breeding command; reserve violation; gift loop | `population_food_deficit` | **1200 ticks** minimum — must show **one** handoff episode, not repeated gifting | task-57 static; `PopulationFood*` tests | `UNVERIFIED` |
| **VR-T3f** | — | — | — | — | **DEFERRED** (V3-D2) | **N/A — non-applicable** |
| **VR-T3g** | Ally cannot loot `VILLAGE_PUBLIC` without grant | HOME/HIGH alone permits loot | `storage_public_deny` | **800 ticks** after mob paths to chest — no open/loot continuation | `StorageOwnershipStructuralTest` vrT3g_* | `UNVERIFIED` |
| **VR-T3h** | UNKNOWN ownership → fail closed | Missing evidence treated as permission | `storage_unknown_deny` | **800 ticks** — no open; ownership remains unknown | vrT3h_* unit tests | `UNVERIFIED` |
| **VR-T3i** | Explicit grant permits; non-ally unchanged | Blanket strip despite grant | `storage_granted_permit` | **800 ticks** after `storage own` grant — permitted access may proceed once | vrT3i_* unit tests | `UNVERIFIED` |
| **VR-T3j** | Mandatory work blocks fresh village work | Opinion/discretionary displaces mandatory | `mandatory_blocks_village_work` | **1000 ticks** — mandatory claim/live gather must complete or block before village crop work | task-52/53 wiring tests | `UNVERIFIED` |
| **VR-T3k** | Two mobs: first commits; second revalidates | Double break; global reservation | `crop_multi_mob` | Through first mob **COMMIT** + second mob **abandon/reacquire** + **200 ticks** | static `CONFIRMED` — task-55 | `UNVERIFIED` |
| **VR-T3l** | Host harvest veto in managed domain when V3 refused | Wilderness veto; stock food suppressed | `crop_hungry_veto` | **800 ticks** — crop must remain planted; no host strip | static `INFERRED` — D-VR-079-A1 | `UNVERIFIED` |
| **VR-T3m** | Replant stock from episode banked drops across cycles | Floor-pickup replant supply | `crop_multi_cycle` | **≥2 complete growth/harvest cycles** or until seed reserve blocks replant + **400 ticks** | `ContainerMergeTest`, F8 tests | `UNVERIFIED` |
| **D-VR-084 witness** | Pending mandatory claim blocks discretionary + village work admission | Claim refresh from demand alone | `mandatory_ownership_witness` | **1000 ticks** — real Gather publisher claim visible; village work refused while claim live | task-52 scenarios (static) | `UNVERIFIED` |
| **SPM 0.89 caution** | Target invalidation/hand-off observable under interruption | Misread as V3 defect | reuse `crop_interrupt_combat` / `mandatory_blocks_village_work` | Same windows as parent rows | host-delta doc only | `UNVERIFIED` |

### VR-T3c contract (task-55 atomic — no repair)

Task-55 locked architecture: if support/crop/planting preflight becomes invalid, **abort before
mutation**. A transaction invariant failure surfaces as `INVARIANT_FAILURE`. There is **no repair
phase** and **no second mandatory publisher**. Closure map: `setBlock false / invariant` →
`ABORT or INVARIANT_FAILURE`.

---

## Settlement bootstrap preflight (campaign gate 0 — mandatory)

Run **before any VR-T3 row** that depends on `setup_village_stub`. Allow vanilla villager AI
**≥120 ticks** after `/function spm_vr:_lib/setup_village_stub` (or any scenario that calls it)
before reading settlement evidence.

| Check | Pass criterion | On fail |
| --- | --- | --- |
| Scavenger settlement observation | `VillagePerception` / settlement anchor readable for ally mob | **STOP** — `FIXTURE_FAILURE` |
| V3-E population facts (when judging VR-T3e or any population-dependent row) | `adultVillagerCount >= 2`, `claimedHomeCount >= 2`, `currentFreeHomeCapacity >= 1` | **STOP** — `FIXTURE_FAILURE` |

**On `FIXTURE_FAILURE`:**

- Do **not** manually inject `Brain.minecraft:home` or `SleepingX/Y/Z`.
- Do **not** count subsequent VR-T3 rows as product failures.
- Do **not** repair Tasks 52–58 production code.
- Report fixture/bootstrap failure and halt the campaign.

HOME occupancy is **POI-ticket state** (`PoiManager.take` → `PoiRecord.acquireTicket()`); memory/sleep
NBT alone does not establish `IS_OCCUPIED`.

---

## Recommended campaign order (when authorized)

Execute in one batched session where possible; re-seed world between clusters if state bleeds:

1. **Authority / mandatory** — VR-T3j, D-VR-084 witness  
2. **Storage safety** — VR-T3g, VR-T3h, VR-T3i  
3. **Crop episode** — VR-T3a → VR-T3b/c → VR-T3k → VR-T3l → VR-T3m  
4. **Population food** — VR-T3e  
5. **Compost** — VR-T3d  
6. **SPM 0.89 target-edge caution** — witness during interruption rows (VR-T3b/c/j)

---

## Evidence capture (minimum)

| Artifact | Path / method |
| --- | --- |
| Game log | `logs/latest.log` — grep `spmscavenger`, `VillageWork`, `MandatoryOwnership`, `Compost`, `Harvest` |
| Mob readout | in-game opinion/activity readout if enabled; one-shot `/spmscavenger debug v3 inspect @e[tag=spm_vr.subject,limit=1]` for running goals, pending claim, shared authority, and Village Work admission |
| Profiler | only if perf row added later — not a V3-G gate |
| Row record | per-id PASS/FAIL/WEIRD with **tick range covering the min observation window** and falsifier quote |
| Environment | JAR hashes from `VR-T3-RUNTIME-ENVIRONMENT.md` verified on instance |

The temporary inspector logs each snapshot with stable prefix `[spmscavenger/v3-witness]`. It has
no session, tick hook, retained entity/world reference, or mutation authority. Remove it and rebuild
the clean production artifact after accepted runtime evidence; preserve the evidence record.

**Banned as proof:** build success; unit test pass alone for runtime rows; subjective play without log quote; observation shorter than the row's minimum window.

---

## Semantic drift review (post-campaign)

Before marking V3 **runtime closed**, review:

- Did any row pass for the wrong reason (e.g. host goal did the work, not Scavenger executor)?
- Did P4 torch contention starve compost/harvest (document as `RUNTIME_QUESTION`, not auto-fail)?
- Did SPM 0.89 target-edge semantics change observable interruption (compatibility note only)?

---

## Revision history

| Date | Change |
| --- | --- |
| 2026-08-23 | Runtime-validation packet — one-shot hidden-authority inspector, datapack help/cleanup, operator runbook, evidence worksheet, new artifact approval gate |
| 2026-08-22 | Initial matrix — task-59 prep; `playermob` 0.89.0; VR-T3f excluded |
| 2026-08-23 | Remove fake HOME/sleep NBT from bootstrap; settlement preflight gate 0; structural test inverted |
| 2026-08-22 | Pre-launch repair — VR-T3c atomic contract; per-row observation windows; fixture gate COMPLETE; environment pin |
