# VR-T3 runtime evidence record

**Status:** Gate 0 and VR-T3j `RUNTIME_CONFIRMED`. Under locked `D-VR-088`, D-VR-084, T3c,
and T3h are closure-satisfied without standalone runtime; T3m uses static substitution after T3a.
Eight representative integration rows remain runtime-required.
**Matrix:** `docs/porting/VR-T3-RUNTIME-MATRIX.md`  
**Environment:** `docs/porting/VR-T3-RUNTIME-ENVIRONMENT.md`

## Session identity

| Field | Recorded value |
| --- | --- |
| Date/operator | — |
| World/seed/backup | — |
| Minecraft / Loader / Fabric API | — |
| Social Player Mobs file / SHA-256 | — |
| SPM Scavenger file / SHA-256 | — |
| Datapack enabled + `spm_vr:help` | — |
| Excluded-mod audit | — |
| `latest.log` path | — |

## Gate 0 — settlement bootstrap

| Evidence | Result |
| --- | --- |
| Preset / start tick / inspection tick (≥120t) | — |
| Settlement anchor readable | — |
| `adultVillagerCount >= 2` | — |
| `claimedHomeCount >= 2` | — |
| `currentFreeHomeCapacity >= 1` | — |
| Inspector `completeness` / `freshness` | — |
| Inspector `Gate0` line + reason | — |
| Verdict (`PASS` / `FIXTURE_FAILURE` / `INCOMPLETE`) | — |

### Gate-0 sample R1 — 2026-08-23

User-supplied `[spmscavenger/v3-witness]` output at game tick **1240** records:

| Evidence | Result |
| --- | --- |
| Reported day time | `912` |
| Settlement | `YES`; overworld anchor `-20,-60,15` |
| Population facts | adults `2`; usable HOME capacity `3`; claimed HOME `2`; free HOME `1` |
| Quality | `COMPLETE`, `FRESH`, observed at tick `1155` |
| Gate 0 | `PASS — settlement and population thresholds readable` |
| Activity/authority | `SeekShelterGoal:SHELTER_HOLD`; mandatory permission and Village Work both denied by `MANDATORY_AUTHORITY` |
| Independent corroboration | Opinion readout: `Holding night shelter (mandatory)`, `SHELTER_HOLD / SETTLED` |

**Disposition:** Gate 0 is `RUNTIME_CONFIRMED`. No settlement-dependent row began. Under the locked
row-start contract, the reported daytime + live shelter hold is `FIXTURE_INCOMPLETE`; re-run the
new inspector after shelter release and require `RowPrecondition=READY` before opening a row window.

### Settlement-row precondition

| Evidence | Result |
| --- | --- |
| Gate0 remains independent | `PASS` |
| No `SHELTER_HOLD` before row start | **NO** in R1 |
| RowPrecondition | `FIXTURE_INCOMPLETE` (derived from direct R1 time/activity evidence; exact new command line awaits a new approved artifact run) |
| Row clock started | **NO** |

Do not continue population-dependent rows after `FIXTURE_FAILURE`. Do not inject HOME or sleeping
NBT to repair the fixture.

## Per-row evidence

For every row, attach the relevant screenshot/video filenames and paste bounded transition-only
`[spmscavenger/v3-campaign]` lines plus any manual corroborating `[spmscavenger/v3-witness]` lines
from `latest.log`. Record the controller's exact opening/terminal ticks and observation disposition;
then adjudicate PASS/FAIL/WEIRD separately against the matrix.

| ID | Preset | Start→end tick | Must-happen evidence | Must-not evidence/falsifier probe | Inspector/log evidence | Verdict |
| --- | --- | --- | --- | --- | --- | --- | --- |
| VR-T3j | `mandatory_blocks_village_work` | full 1000-tick window on `BDAA788C...A0B2BDC` (exact ticks not supplied) | `source=LIVE_CLAIM` opened the row; autonomous pig combat preempted Gather; Gather resumed; running `SCAVENGE_WORK` retained mandatory authority | Village Work stayed blocked during combat and running Gather; no Village Work displacement observed | User-adjudicated runtime report; local workspace logs do not contain the external session. Superseded `ED07...F56E3` fixture-incomplete attempt remains historical evidence only | **`RUNTIME PASS`** |
| D-VR-084 | no standalone run (`mandatory_ownership_witness` is historical) | T3j observed live pending/running mandatory ownership across combat preemption and resume | Task-52 proves expiry/no-renewal/release/lifecycle negative controls | `BDAA788C...A0B2BDC` T3j runtime + `.superpowers/sdd/task-52-report.md` mutation matrix | **`CLOSURE SATISFIED COMPOSITIONALLY`** |
| VR-T3g | `storage_public_deny` | — | — | — | — | `UNVERIFIED` |
| VR-T3h | no standalone run required | task-54 tri-state/UNKNOWN policy denies absent ownership evidence | Missing evidence must not become permission | deterministic task-54 tests; T3g remains the representative live host deny-hook row | **`CLOSURE SATISFIED — STATIC-SUBSUMED`** |
| VR-T3i | `storage_granted_permit` | — | — | — | — | `UNVERIFIED` |
| VR-T3a | `crop_managed_single` | — | — | — | — | `UNVERIFIED` |
| VR-T3b | `crop_interrupt_combat` | — | — | — | — | `UNVERIFIED` |
| VR-T3c | no standalone run required | preflight/rollback/escrow and exact age-0 transaction properties | no partial mutation, false success, loot on failure, or repair phase | `CropHarvestTransactionBehaviorTest` + task-55 mutation evidence | **`CLOSURE SATISFIED — STATIC/TRANSACTION CONFIRMED`** |
| VR-T3k | `crop_multi_mob` | — | — | — | — | `UNVERIFIED` |
| VR-T3l | **fixture redesign required** | Must observe actual SPM 0.96 `HarvestCropsGoal` hook attachment and managed-crop refusal | Must preserve wilderness/unresolved fail-open; no mandatory-claim surrogate | current `crop_hungry_veto` nearby-log premise is invalid; witness must tolerate reaction-scaled timing | **`RUNTIME REQUIRED — BLOCKED BY FIXTURE DESIGN`** |
| VR-T3m | no standalone run required | T3a will supply representative live crop episode; deterministic tests prove banked-drop conservation/repetition | no floor-pickup supply or reserve drain | `ContainerMergeTest`, `CropHarvestTransactionBehaviorTest`, task-55 F8 evidence | **`STATIC SUBSTITUTION — PENDING T3a RUNTIME`** |
| VR-T3e | `population_food_deficit` | — | — | — | — | `UNVERIFIED` |
| VR-T3d | `compost_seed_surplus` | — | — | — | — | `UNVERIFIED` |

## Semantic-drift review

| Question | Evidence / disposition |
| --- | --- |
| Did the correct Scavenger goal perform each positive result? | VR-T3j: `LIVE_CLAIM` opening followed by running `SCAVENGE_WORK`; **PASS** |
| Did any row pass only because a host goal performed the work? | — |
| Did P4 torch contention starve V3 work? | — |
| Did historical SPM 0.89 target acquire/loss semantics change interruption? | VR-T3j: autonomous pig combat temporarily preempted Gather and Gather resumed afterward; no harmful drift observed in this row. This does not prove v0.96 reaction-delayed acquisition. |
| Did any hidden pending claim disagree with visible activity? | VR-T3j: no; pending live claim opened the row and running `SCAVENGE_WORK` later supplied the visible mandatory owner |

## Final disposition

| Item | Result |
| --- | --- |
| Runtime-confirmed rows | **1** (`VR-T3j`; no rerun) |
| Closure-satisfied without standalone runtime | **3** (`VR-T3c`, `VR-T3h`, `D-VR-084`) |
| Conditional static substitution | **VR-T3m** — closes when representative VR-T3a runtime passes |
| Remaining runtime-required rows | **8** — `VR-T3a`, `b`, `d`, `e`, `g`, `i`, `k`, `l` |
| Failed rows | — |
| Fixture/matrix blockers | VR-T3l requires minimal host-hook fixture redesign; old nearby-log/mandatory premise invalid. Historical T3m 4000-tick natural-growth matrix is superseded as a closure obligation |
| Weird/runtime questions | autonomous pig combat was a valid interruption witness, not a failure |
| V3-G closure recommendation | **OPEN** — run only the eight representative integration rows after T3l fixture review and separate runtime approval |

Task 60 extracted `V3RuntimeWitnessCommands` and all Task-59 lifecycle/scenario machinery from the
production JAR into the separately packaged validation mod while preserving this evidence record.
No row verdict changed. Resume only with an exact approved production/validation artifact pair.
