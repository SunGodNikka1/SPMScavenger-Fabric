# VR-T3 runtime matrix (V3-G closure)

**Status:** fixtures + temporary automated campaign controller **IMPLEMENTED**; Gate-0 runtime
snapshot **CONFIRMED**. VR-T3j completed one valid 1000-tick observation window, but the run is
`FIXTURE_INCOMPLETE` because no mandatory route instantiated; no V3 product verdict was reached.
Any launch with the newly rebuilt controller artifact requires separate approval (AGENTS.md Gate 6).

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
| Passive hidden-authority + Gate-0 snapshot | `/spmscavenger debug v3 inspect <mob>` — temporary one-shot read only; reports remembered settlement, population facts, and explicit Gate-0 verdict |
| Automated campaign | `/spmscavenger debug v3 run <preset>` → `status` / `report` / `stop` / `reset`; one bounded fixture session, transition-only evidence, no product verdict |
| Evidence record | `docs/porting/VR-T3-RUNTIME-EVIDENCE.md` |
| Environment / JAR hashes | `docs/porting/VR-T3-RUNTIME-ENVIRONMENT.md` |
| Static/build baseline | see `task-59-prelaunch-report.md` |
| Gate-0 runtime sample | **PASS / RUNTIME_CONFIRMED** at game tick 1240; row start refused by the subsequently locked shelter-release precondition |
| **Controller-artifact campaign** | **NOT AUTHORIZED** — User must approve the new exact JAR before launch |

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
| **VR-T3j** | Mandatory work blocks fresh village work | Opinion/discretionary displaces mandatory | `mandatory_blocks_village_work` | `MANDATORY_ROUTE_READY` then **1000 ticks** — mandatory claim/live gather must complete or block before village crop work | task-52/53 wiring + Task-59 frontier tests | **RUNTIME PASS** on `BDAA788C...A0B2BDC`: `LIVE_CLAIM` opening; pig combat preemption; Gather resume; `SCAVENGE_WORK` ownership; Village Work never displaced mandatory |
| **VR-T3k** | Two mobs: first commits; second revalidates | Double break; global reservation | `crop_multi_mob` | Through first mob **COMMIT** + second mob **abandon/reacquire** + **200 ticks** | static `CONFIRMED` — task-55 | `UNVERIFIED` |
| **VR-T3l** | Host harvest veto in managed domain when V3 refused | Wilderness veto; stock food suppressed | `crop_hungry_veto` | **800 ticks** — crop must remain planted; no host strip | static `INFERRED` — D-VR-079-A1 | `UNVERIFIED` |
| **VR-T3m** | Replant stock from episode banked drops across cycles | Floor-pickup replant supply | `crop_multi_cycle` | Baseline first replant, then **≥2 complete same-cell natural growth/harvest cycles** + **400 ticks** | `ContainerMergeTest`, F8 tests; detector static `CONFIRMED` | `MATRIX/FIXTURE CONTRADICTION` — 4000-tick controller cap is not a realistic unaccelerated two-cycle window; runtime `UNVERIFIED` |
| **D-VR-084 witness** | Pending mandatory claim blocks discretionary + village work admission | Claim refresh from demand alone | `mandatory_ownership_witness` | `MANDATORY_ROUTE_READY` then **1000 ticks** — real Gather publisher claim visible; village work refused while claim live | task-52 scenarios + Task-59 frontier tests | repaired fixture runtime `UNVERIFIED` |
| **SPM 0.89 caution** | Target invalidation/hand-off observable under interruption | Misread as V3 defect | reuse `crop_interrupt_combat` / `mandatory_blocks_village_work` | Same windows as parent rows | host-delta doc only | `UNVERIFIED` |

### VR-T3c contract (task-55 atomic — no repair)

Task-55 locked architecture: if support/crop/planting preflight becomes invalid, **abort before
mutation**. A transaction invariant failure surfaces as `INVARIANT_FAILURE`. There is **no repair
phase** and **no second mandatory publisher**. Closure map: `setBlock false / invariant` →
`ABORT or INVARIANT_FAILURE`.

---

## Settlement bootstrap preflight (campaign gate 0 — mandatory)

The preferred controller path performs this gate automatically before any VR-T3 row that depends
on `setup_village_stub`. After the scenario function returns successfully, it records
`bootstrapStartTick` and remains in `WAITING_GATE0_BOOTSTRAP` for at least 120 ticks. Intermediate
readable numeric deficits are diagnostic only during that grace period. Afterward, structural
impossibilities fail immediately while `claimedHomeCount < 2` remains dynamic `INCOMPLETE` within
the unchanged 2400-tick overall timeout anchored to fixture start. The controller never refreshes
facts or writes HOME. The one-shot manual fallback remains:

```text
/spmscavenger debug v3 inspect @e[tag=spm_vr.subject,limit=1]
```

The snapshot reads already-produced memory/facts only. `Gate0=INCOMPLETE` means observation is not
yet readable, or natural HOME acquisition is still pending, and may be sampled again after normal
production cadence; it does not create or refresh evidence. `Gate0=FIXTURE_FAILURE` is emitted only
for complete/fresh structural impossibility: fewer than two adults, fewer than three usable HOME
POIs, or no free HOME after two claims. Dynamic HOME claims still below two at the original overall
deadline produce `FIXTURE_INCOMPLETE` with exact final counts.

| Check | Pass criterion | On fail |
| --- | --- | --- |
| Scavenger settlement observation | inspector reports `settlement observed: YES` and exact anchor/identity | Continue only when population facts also readable; otherwise `INCOMPLETE` |
| V3-E population facts | inspector reports `COMPLETE`, `FRESH`, `adultVillagerCount >= 2`, `totalUsableHomeCapacity >= 3`, `claimedHomeCount >= 2`, `currentFreeHomeCapacity >= 1` | **STOP** on structural `Gate0=FIXTURE_FAILURE`; wait naturally on dynamic claim `INCOMPLETE`; overall claim timeout is `FIXTURE_INCOMPLETE` |
| Gate-0 verdict | inspector emits `Gate0=PASS` | Do not execute population-dependent closure rows until PASS |

### Settlement-row evidence-window precondition

Gate 0 proves settlement/population fixture truth; it does not prove that mandatory activity has
released the subject. Immediately before any settlement-dependent row begins its evidence window,
transition or wait until daytime and run the inspector again.

| Readout | Disposition |
| --- | --- |
| `RowPrecondition=READY` | No live `SHELTER_HOLD`; the row may start if its other prerequisites pass |
| `RowPrecondition=WAITING_DAYTIME` | Shelter is still active before daytime; do not start the row; transition/wait and inspect again |
| `RowPrecondition=FIXTURE_INCOMPLETE` | Shelter remained active during daytime; do not start the row or classify V3 behavior; preserve evidence as fixture-incomplete |

The one-shot witness reads `ServerLevel.isDay()` and existing activity and never changes them. The
temporary controller, only after Gate0 PASS and before opening, clears weather and advances the
disposable fixture to day, then waits up to 200 daytime ticks for production to release
`SHELTER_HOLD`. It does not stop the Goal or clear authority. A retained hold ends as exact
`FIXTURE_INCOMPLETE`; `Gate0=PASS` remains valid.

### Mandatory-route evidence-window precondition

VR-T3j and D-VR-084 use one declared iron-pick fixture frontier. Before either evidence window
opens, the temporary controller passively re-runs the real `WorkDemandPolicy`,
`GatherIntentPolicy`, and `GatherRoutePrecursor`. It then reads
`MandatoryOwnershipRegistry.liveClaim(subjectUUID, currentTick)`:

- selected demand is `minecraft:iron_ingot` for `spmscavenger:iron_pickaxe_upgrade`;
- the modeled precursor is `RAW_IRON` and the live Gather intent covers it;
- `ScavengerCrafting.nextStep(...) == NOTHING`;
- smelting has no carried raw input to execute before Gather;
- a matching non-expired production claim opens as `MANDATORY_ROUTE_READY source=LIVE_CLAIM`
  without requiring the duplicate target-geometry prediction;
- when no matching claim exists, the bounded candidate/protection/tool checks and non-steering path
  probe remain the `source=PASSIVE_FALLBACK` and require an eligible complete approach.

Failure is terminal `FIXTURE_INCOMPLETE` before `WINDOW_OPEN`; the controller reports the failed
production fact. The fixture does not publish a claim, call a Gather Goal, change admission, or
award the expected result. Claim logs include consumer key, generation, open/expiry/current ticks,
and route identity as diagnostic text only; the concrete route implementation class is never a gate.

## Automated controller contract

```text
/spmscavenger debug v3 run mandatory_blocks_village_work
  -> establish PREPARING immediately
  -> execute loaded preset at command origin on next server tick (Overworld only)
  -> record exact bootstrapStartTick after successful function execution
  -> remove unrelated PlayerMobs inside protected 192-block observation envelope, pre-window only
  -> force-load only newly acquired 32-block scenario-core chunks
  -> WAITING_GATE0_BOOTSTRAP for >=120 natural ticks; threshold deficits remain diagnostic
  -> after +120: structural failure is terminal; dynamic HOME claims continue bounded waiting
  -> natural Gate0 PASS / INCOMPLETE / FIXTURE_INCOMPLETE / FIXTURE_FAILURE
  -> logged day/weather fixture transition
  -> wait genuine SHELTER_HOLD release
  -> for T3j/D-VR-084, require current exact policy + matching LIVE_CLAIM, otherwise passive target fallback
  -> forced fresh 192-block quarantine scan (never cadence-skipped)
  -> ROW_PRECONDITION_READY + exact WINDOW_OPEN tick
  -> core exit records SUBJECT_LEFT_CORE + distance/pendingClaim/activeClasses; never leashes subject
  -> outer-envelope unrelated PlayerMobs produce bounded telemetry only
  -> core entry, <=16-block subject proximity, or targeting relation is EXTERNAL_INTERFERENCE
  -> geometry rows become spatially INCOMPLETE only beyond 224 blocks; VR-T3j continues its clock
  -> T3k observes two crop commitments, one commit, and stale contender release
  -> T3m requires two mature->replant transitions on the same crop cell
  -> passive transition/terminal capture for the row clock
  -> release exact owned chunk tickets

/spmscavenger debug v3 report
```

Startup failures terminate as `FIXTURE_FAILURE` with `startupStage`, exception class/message, and
root cause in status/report; the full stack trace uses `[spmscavenger/v3-campaign]` in `latest.log`.
Run `reset` to remove provably tagged partial fixture entities/schedules.

`OBSERVATION_COMPLETE` means the minimum evidence clock completed; it is not PASS. `INCOMPLETE`,
`FIXTURE_INCOMPLETE`, `FIXTURE_FAILURE`, and `EXTERNAL_INTERFERENCE` classify observation/fixture
state only. After opening, unrelated PlayerMobs are reported rather than removed, and the controller
does not teleport/steer the subject or suppress declared combat. Normal stop/reset, subject
unload/death, dimension loss, terminal completion, and server stop release owned chunk tickets.

### Post-open spatial interpretation

`SCENARIO_CORE` is the 32-block target/evidence region and the only controller-forced chunk region.
It is not a subject leash. `OBSERVATION_ENVELOPE` extends to 192 horizontal blocks, derived from a
subject opening within the 32-block core plus the production `ExploringGoal` 150-block expedition
route cap (182, rounded to 192). Unrelated PlayerMobs are sampled there every 20 ticks as
contamination. The 192–224 region is a recorded escape margin. Beyond 224, crop/storage rows may be
spatially `INCOMPLETE`; VR-T3j remains readable because its 1000-tick question is authority state,
not proximity to a core block. Status/report preserve first core exit, maximum distance, current
zone, pending-claim history, and transition evidence. No distant envelope chunks are fixture-forced.
A process crash cannot run lifecycle cleanup; use a disposable backed-up world and inspect forced
chunks before reuse.

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
| Mob readout | preferred controller `report`; one-shot `/spmscavenger debug v3 inspect @e[tag=spm_vr.subject,limit=1]` remains a manual corroboration tool |
| Profiler | only if perf row added later — not a V3-G gate |
| Row record | per-id PASS/FAIL/WEIRD with **tick range covering the min observation window** and falsifier quote |
| Environment | JAR hashes from `VR-T3-RUNTIME-ENVIRONMENT.md` verified on instance |

The temporary inspector logs each requested snapshot with `[spmscavenger/v3-witness]`; the
controller logs bounded transitions with `[spmscavenger/v3-campaign]`. The controller retains only
one identifier/value session plus one immutable last report, never live entity/world references.
Remove both temporary surfaces and rebuild the clean production artifact after accepted runtime
evidence; preserve the evidence record.

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
| 2026-08-24 | VR-T3j **RUNTIME PASS** on `BDAA788C...A0B2BDC`: matching live claim opened the full 1000-tick window; autonomous pig combat temporarily preempted Gather; Village Work remained blocked; Gather resumed as `SCAVENGE_WORK`; no displacement observed. D-VR-084 remains independently unverified |
| 2026-08-24 | Three `8C2D...A69F2` T3j reproductions corroborated a live exact-consumer claim and Village Work denial while duplicate geometry prevented official opening. Matching live claim now yields `source=LIVE_CLAIM`; geometry remains only the no-claim fallback. Runtime evidence is corroboration, not closure. Replacement SHA `BDAA788C...A0B2BDC`; runtime not authorized |
| 2026-08-24 | VR-T3j `ED07...F56E3` completed tick 282→1282 with no claim/mandatory Gather; classified fixture-incomplete. T3j/D-VR-084 now use a policy-proven iron frontier plus pre-window `MANDATORY_ROUTE_READY`; replacement SHA `8C2DBBA5...A6CA69F2`, runtime not authorized |
| 2026-08-24 | Live `38C3...8588FC` falsification at exact +120/HOME1; reclassified natural HOME deficit as dynamic waiting, preserved structural immediate failures and original 2400-tick deadline; replacement SHA `ED07F88D...F56E3`, runtime not authorized |
| 2026-08-24 | Final static T3k/T3m correction: all original contenders must release after commit; opening maturity is baseline-only and T3m requires two later same-cell cycles. Recorded the 4000-tick natural-growth contradiction; SHA `38C3E332...8588FC`; runtime not authorized |
| 2026-08-24 | Superseded `626FB...F599`; separated 192-block subject telemetry from causal contamination authority, forced a fresh final isolation scan, and repaired T3k contention/T3m same-cell temporal evidence; replacement SHA `2D2E6492...DF8D7D`, runtime not authorized |
| 2026-08-24 | Replaced 32-block subject leash with core/envelope/escape model; T3j preserves exploration-without-mandatory-ownership evidence; contamination envelope remains protected |
| 2026-08-24 | Discarded premature VR-T3j attempt (`claimedHomeCount < 2`, window never opened); added explicit post-function 120-tick Gate-0 bootstrap boundary; T3k/T3m observation-model gaps remain backlog |
| 2026-08-24 | Repaired startup containment and Minecraft 1.21 function execution boundary; exact artifact repinned; live rerun not authorized |
| 2026-08-23 | Added temporary one-command campaign controller, passive row clocks, pre-window contamination isolation, exact opening/terminal evidence, and owned fixture chunk lifecycle; launch remains unauthorized |
| 2026-08-23 | Gate-0 witness completion — remembered settlement identity + full population facts + explicit PASS/FIXTURE_FAILURE/INCOMPLETE |
| 2026-08-23 | Gate-0 runtime PASS recorded; added independent no-`SHELTER_HOLD` settlement-row precondition after daytime transition |
| 2026-08-23 | Runtime-validation packet — one-shot hidden-authority inspector, datapack help/cleanup, operator runbook, evidence worksheet, new artifact approval gate |
| 2026-08-22 | Initial matrix — task-59 prep; `playermob` 0.89.0; VR-T3f excluded |
| 2026-08-23 | Remove fake HOME/sleep NBT from bootstrap; settlement preflight gate 0; structural test inverted |
| 2026-08-22 | Pre-launch repair — VR-T3c atomic contract; per-row observation windows; fixture gate COMPLETE; environment pin |
