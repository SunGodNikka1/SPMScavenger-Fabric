# VR-T3 runtime evidence record

**Status:** `EMPTY / UNVERIFIED` — fill only during an explicitly authorized Task-59 campaign.  
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
| Verdict (`PASS` / `FIXTURE_FAILURE`) | — |

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
