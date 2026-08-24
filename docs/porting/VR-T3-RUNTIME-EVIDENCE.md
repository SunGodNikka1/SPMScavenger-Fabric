# VR-T3 runtime evidence record

**Status:** Gate 0 `RUNTIME_CONFIRMED`; settlement-dependent row evidence remains `UNVERIFIED`.
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

For every row, attach the relevant screenshot/video filenames and paste transition-only
`[spmscavenger/v3-witness]` lines from `latest.log`.

| ID | Preset | Start→end tick | Must-happen evidence | Must-not evidence/falsifier probe | Inspector/log evidence | Verdict |
| --- | --- | --- | --- | --- | --- | --- | --- |
| VR-T3j | `mandatory_blocks_village_work` | — | — | — | — | `UNVERIFIED` |
| D-VR-084 | `mandatory_ownership_witness` | — | — | — | — | `UNVERIFIED` |
| VR-T3g | `storage_public_deny` | — | — | — | — | `UNVERIFIED` |
| VR-T3h | `storage_unknown_deny` | — | — | — | — | `UNVERIFIED` |
| VR-T3i | `storage_granted_permit` | — | — | — | — | `UNVERIFIED` |
| VR-T3a | `crop_managed_single` | — | — | — | — | `UNVERIFIED` |
| VR-T3b | `crop_interrupt_combat` | — | — | — | — | `UNVERIFIED` |
| VR-T3c | `crop_replant_failure` | — | — | — | — | `UNVERIFIED` |
| VR-T3k | `crop_multi_mob` | — | — | — | — | `UNVERIFIED` |
| VR-T3l | `crop_hungry_veto` | — | — | — | — | `UNVERIFIED` |
| VR-T3m | `crop_multi_cycle` | — | — | — | — | `UNVERIFIED` |
| VR-T3e | `population_food_deficit` | — | — | — | — | `UNVERIFIED` |
| VR-T3d | `compost_seed_surplus` | — | — | — | — | `UNVERIFIED` |

## Semantic-drift review

| Question | Evidence / disposition |
| --- | --- |
| Did the correct Scavenger goal perform each positive result? | — |
| Did any row pass only because a host goal performed the work? | — |
| Did P4 torch contention starve V3 work? | — |
| Did SPM 0.89 target acquire/loss semantics change interruption? | — |
| Did any hidden pending claim disagree with visible activity? | — |

## Final disposition

| Item | Result |
| --- | --- |
| Applicable rows passed | — / 13 |
| Failed rows | — |
| Incomplete rows | — |
| Weird/runtime questions | — |
| V3-G closure recommendation | `UNVERIFIED` |

Temporary `V3RuntimeWitnessCommands` must be removed and the clean production JAR rebuilt after
accepted evidence. Preserve this record and the RFC contribution when removing instrumentation.
