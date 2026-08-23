# VR-T3 runtime matrix (V3-G closure)

**Status:** `APPROVED` for preparation — **runtime execution NOT AUTHORIZED** until separate User
launch approval (AGENTS.md Gate 6).

**Task:** task-59 / V3-G · **Brief:** `.superpowers/sdd/task-59-brief.md`  
**RFC:** `plans/RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md` — phase closure rule (VR-T3f non-applicable)

---

## Mod set (canonical baseline)

| Mod | Version | Required | Notes |
| --- | --- | --- | --- |
| **Social Player Mobs** (`playermob`) | **0.89.0** | **yes** | Canonical host baseline (2026-08-22); 0.86→0.89 V3-sensitive delta **PASS** |
| **SPM Scavenger** (`spmscavenger`) | current task-59 build artifact | **yes** | Built from `Projects/SPMScavenger-1.21.1-Fabric` |
| **Fabric API** | instance pin | **yes** | Match Fabulously Optimized / target pack |
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
| Static/build baseline | see `task-59-report.md` |
| **Minecraft campaign** | **NOT AUTHORIZED** — User must approve `runClient` / batched VR-T3 separately |

---

## Applicable scenario table

**Closure rule:** V3 requires V3-A…G plus all **applicable** VR-T3a–m rows. **VR-T3f is NOT
applicable** while broad V3-D2 remains deferred.

| ID | Must happen | Must not happen | `spm_vr` preset | Static evidence | Runtime status |
| --- | --- | --- | --- | --- | --- |
| **VR-T3a** | Managed mature crop ends replanted in one committed episode | Bare farmland after visible harvest | `crop_managed_single` | `CropHarvestTransactionTest`, `CompostScenarioEvidenceTest` (orthogonal), task-55 report | `UNVERIFIED` |
| **VR-T3b** | Interruption before interaction → no world mutation | Blind resume of stale path/target | `crop_interrupt_combat` | static `INFERRED` — task-55 | `UNVERIFIED` |
| **VR-T3c** | Preflight abort or bounded repair owns cleanup | Discretionary work with open repair gap | `crop_replant_failure` | `CropHarvestTransactionTest` | `UNVERIFIED` |
| **VR-T3d** | One compost unit consumed; level advance or unchanged both terminate | Double debit; reserve violation; bone-meal demand | `compost_seed_surplus` | `CompostScenarioEvidenceTest`, task-58 report | `UNVERIFIED` |
| **VR-T3e** | One disposable food delivery when deficit exists | Breeding command; reserve violation; gift loop | `population_food_deficit` | task-57 static; `PopulationFood*` tests | `UNVERIFIED` |
| **VR-T3f** | — | — | — | **DEFERRED** (V3-D2) | **N/A — non-applicable** |
| **VR-T3g** | Ally cannot loot `VILLAGE_PUBLIC` without grant | HOME/HIGH alone permits loot | `storage_public_deny` | `StorageOwnershipStructuralTest` vrT3g_* | `UNVERIFIED` |
| **VR-T3h** | UNKNOWN ownership → fail closed | Missing evidence treated as permission | `storage_unknown_deny` | vrT3h_* unit tests | `UNVERIFIED` |
| **VR-T3i** | Explicit grant permits; non-ally unchanged | Blanket strip despite grant | `storage_granted_permit` | vrT3i_* unit tests | `UNVERIFIED` |
| **VR-T3j** | Mandatory work blocks fresh village work | Opinion/discretionary displaces mandatory | `mandatory_blocks_village_work` | task-52/53 wiring tests | `UNVERIFIED` |
| **VR-T3k** | Two mobs: first commits; second revalidates | Double break; global reservation | `crop_multi_mob` | static `CONFIRMED` — task-55 | `UNVERIFIED` |
| **VR-T3l** | Host harvest veto in managed domain when V3 refused | Wilderness veto; stock food suppressed | `crop_hungry_veto` | static `INFERRED` — D-VR-079-A1 | `UNVERIFIED` |
| **VR-T3m** | Replant stock from episode banked drops | Floor-pickup replant supply | `crop_multi_cycle` | `ContainerMergeTest`, F8 tests | `UNVERIFIED` |
| **D-VR-084 witness** | Pending mandatory claim blocks discretionary + village work admission | Claim refresh from demand alone | `mandatory_ownership_witness` | task-52 scenarios (static) | `UNVERIFIED` |
| **SPM 0.89 caution** | Target invalidation/hand-off observable under interruption | Misread as V3 defect | reuse `crop_interrupt_combat` / `mandatory_blocks_village_work` | host-delta doc only | `UNVERIFIED` |

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
| Mob readout | in-game opinion/activity readout if enabled |
| Profiler | only if perf row added later — not a V3-G gate |
| Row record | per-id PASS/FAIL/WEIRD with tick range and falsifier quote |

**Banned as proof:** build success; unit test pass alone for runtime rows; subjective play without log quote.

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
| 2026-08-22 | Initial matrix — task-59 prep; `playermob` 0.89.0; VR-T3f excluded |
