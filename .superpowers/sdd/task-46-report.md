# Task 46 report — V1.5 Settlement attachment & return

**Status:** `DONE_WITH_CONCERNS`  
**Target version:** 1.11.0  
**RFC:** `plans/RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md` — D-VR-034…052

## Summary

Shipped V1.5 slices **A–D + F** only. **V1.5-E rejected** per User D-VR-052 (deferred to V3 `StorageOwnership`). Mob-owned `SettlementRelationship` map, accumulation service, commute expeditions, village-aware SOCIAL bias, and temporary `designate-home` command.

## Files created or changed

- `village/SettlementRelationship.java`, `AttachmentBand.java`, `SettlementTuning.java`, `SettlementBoundsPolicy.java`
- `village/SettlementRelationshipService.java`, `SettlementReturnPolicy.java`
- `village/MobVillageMemory.java`, `VillageMemorySavedData.java`
- `goal/ExpeditionKind.java`, `ExploringGoal.java`, `VillagePerceptionObserver.java`
- `opinion/SettlementSocialBias.java`, `SocialExecutionBindingRegistry.java`, `DiscretionaryScoringInput.java`, `ActivityUtilityScorer.java`, `DiscretionaryActivityDirector.java`, `ExplorationActivityGoal.java`
- `debug/VillageDesignateHomeCommand.java`, `SpmScavenger.java`
- `test/.../SettlementRelationshipTest.java`, `SettlementReturnPolicyTest.java`
- `gradle.properties` → `1.11.0`

## Commands and exact results

```text
cd Projects\SPMScavenger-1.21.1-Fabric
.\gradlew.bat clean build
BUILD SUCCESSFUL — 905 tests
```

## Evidence

| Claim | Label | Evidence |
| --- | --- | --- |
| Compile + unit tests | **CONFIRMED** | `clean build` output above |
| No V1.5-E / RaidContainersGoal mixin | **CONFIRMED** | grep — no production mixin added |
| No village-memory/probe/driver | **CONFIRMED** | only `designate-home` under `debug/` |
| VR-T1.5a runtime | **CONFIRMED** | User Bob session 2026-08-15 — far start, autonomous return, entered village ~`-11666`, hostile resume |
| VR-T1.5b–c runtime | **UNVERIFIED** | not yet observed |
| Multi-leg commute in world | **CONFIRMED** | VR-T1.5a Bob session; dead-zone repair pass 3 validated |

## Self-review (brief mapping)

| Slice | Done |
| --- | --- |
| A persistence + rekey/evict | Yes |
| B accumulation @ 64² | Yes |
| C COMMUTE + multi-leg | Yes (static) |
| D social anchor + bias | Yes |
| E ally loot gate | **Not shipped** (D-VR-052) |
| F designate-home | Yes (temporary) |

## Concerns

- VR-T1.5b–c runtime still required before full V1.5 runtime closure.
- `designate-home` eligible for removal per D-VR-051 (VR-T1.5a PASS); keep until VR-T1.5b–c or user directs cleanup.
- `designate-home` picks nearest remembered village to target mob — operator must ensure Bob has memory.

## Repair pass (2026-08-15, pre-VR-T1.5)

| Fix | Issue | Resolution |
| --- | --- | --- |
| COMMUTE experience attribution | `emitExpeditionUnlocked` / `emitExpeditionTerminal` taught `OVERLAND_EXPLORATION` per leg | `attributesExplorationExperience()` gates all expedition experience emitters; COMMUTE also skips exploration novelty memory |
| Load/evict sync | `evictBeyondBound()` before relationship NBT load resurrected orphan rows | Load relationships first, then evict; `pruneOrphanRelationships()` after eviction |

`.\gradlew.bat clean build` — **BUILD SUCCESSFUL** (908 tests after repair).

## Repair pass 2 (2026-08-15, pre-VR-T1.5)

| Fix | Issue | Resolution |
| --- | --- | --- |
| Familiarity bootstrap | `empty(tick)` made `tick - lastVisitTick == 0` forever | `SettlementRelationship.empty()` uses `lastVisitTick = 0`; visit/presence paths use `bootstrap` when no row exists |
| Load regression test | `remember()` pre-evicted before save | Test builds oversized NBT directly (17 villages + 17 relationships) |

## Repair pass 3 (2026-08-15, VR-T1.5a FAIL)

**Runtime evidence (Bob, overworld taiga):** remembered village anchor ~(-11666, 7709); Bob stopped at ~(-11592, 7716) — **~74 blocks** from anchor. Inside 64-block arrival bounds: no. Inside 128-block commute **start** cutoff: yes → **dead zone**.

| Fix | Issue | Resolution |
| --- | --- | --- |
| Commute dead zone | `shouldCommute()` applied `COMMUTE_MIN_DISTANCE` (128) to **chain** legs as well as new starts | Split: `shouldStartCommute` / `shouldStartCommuteAt` (128 gate) vs `shouldContinueCommute` / `shouldContinueCommuteAt` (64-boundary only). `ExploringGoal.completeExpedition` chains via `tryChainCommuteLeg` + continue policy |
| Dead-zone unit tests | Bob repro not covered | `mustNotHappen_startCommuteInsideDeadZoneBetweenBoundsAndMinDistance` at 74 blocks; continue=true, start=false |

`.\gradlew.bat clean build` — **BUILD SUCCESSFUL** (914 tests).

## VR-T1.5a runtime (`PASS`, User, 2026-08-15)

**World:** overworld taiga (Bob fixture). **Home anchor:** ~`-11666, 7709` (`designate-home`).

| Must happen | Result |
| --- | --- |
| Bob starts far from home | **CONFIRMED** |
| Autonomous return commute (multi-leg) | **CONFIRMED** |
| Enters 64-block home bounds at village | **CONFIRMED** — ~`-11666` |
| Expedition survives hostile interrupt | **CONFIRMED** — flee/hide during monster; explore resumed after death |

**Prior failure (repair pass 3):** stopped ~74 blocks out (`-11592, 7716`) — dead zone from 128-block gate on chain legs. **REPAIRED** before this PASS.

**Not in scope for VR-T1.5a:** flee-past-weak-monster personality tuning; VR-T1.5b familiarity; VR-T1.5c social bias.

## Repair pass 4 (2026-08-15, VR-T1.5b tuning rebalance)

**User finding:** VR-T1.5b mechanics **PASS**; passive grind (+1/10s → ~100 min for HIGH) **unplayable** for typical mob lifetimes.

| Constant | Before | After |
| --- | --- | --- |
| `VISIT_FAMILIARITY_BUMP` | 25 | **50** |
| `PRESENCE_FAMILIARITY_BUMP` | 1 | **5** |
| `PRESENCE_FAMILIARITY_CAP` | — | **250** (new; passive cannot reach HIGH alone) |
| `SOCIAL_FAMILIARITY_BUMP` | 40 | 40 |
| `MEDIUM_BAND_MIN` / `HIGH_BAND_MIN` | 200 / 600 | unchanged |

`SettlementRelationship.presenceFamiliarity` persisted; `bumpPresenceFamiliarity` used from heartbeat only. HIGH requires returns, social, and later trade/work/defense.

`settlement-status` now prints `Presence: X / 250`.
