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
| VR-T1.5a–c runtime | **UNVERIFIED** | launch not authorized |
| Multi-leg commute in world | **UNVERIFIED** | static chain logic in `ExploringGoal.completeExpedition` |

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

- VR-T1.5 runtime (Bob, overworld) still required before removing `designate-home`.
- `designate-home` picks nearest remembered village to target mob — operator must ensure Bob has memory.
- Commute multi-leg chaining is unit-tested at policy level only; pathfinding failures in world are **UNVERIFIED**.

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
