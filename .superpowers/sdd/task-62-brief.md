# Task 62 brief: V4-A — KnownVillager positive capability evidence

## Status and target

**Status:** AUTHORIZED by User, 2026-08-26.  
**Target:** `D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`  
**Pinned host reference:** `D:\Apps\Minecraft Port\Projects\references\artifacts\playermob-fabric-0.89.0+1.21.1.jar`  
**Canonical decision:** `plans/RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md`, `D-VR-090` / V4-A.  
**Report:** `.superpowers/sdd/task-62-report.md`

## Source evidence (`CODE_CONFIRMED`)

- `TradeEvaluationPolicy.evaluate(...)` admits a BUY only when the live result item key equals the
  live `MaterialDemand.materialKey`; it does not authorize from remembered state.
- `OfferSnapshot`, `OfferRef.Requote`, and `TradeInventoryFacts` use
  `ItemStack.isSameItemSameComponents` for quote/output identity. A capability descriptor therefore
  stores a count-normalized item-and-components sample, while live V2 remains the sole consumer
  satisfaction and transaction authority.
- `TradeWithVillagerGoal.authorizedCandidate(...)` already obtains the complete vanilla board for
  every villager it legitimately scans. V4-A observes those existing vanilla snapshots after the
  scan; it adds no villager search, `getOffers()` call, or market admission.
- `MobVillageMemory` already owns settlement rekey, settlement LRU eviction, save/load, unload
  preservation, and permanent-owner deletion through `VillageMemorySavedData.forgetEverywhere`.
  Trader evidence belongs inside that same row.
- Negative probes: an existing `KnownVillager` type — **NOT FOUND**; separate trader SavedData —
  **NOT FOUND**; a persistent trader-memory representation containing price/slot/uses/offer authority
  — **NOT FOUND** (live V2 `OfferSnapshot` types are transient market evidence, not village memory).

## Binding implementation contract

1. Add `KnownVillager` inside `MobVillageMemory`, keyed by stable villager UUID and associated with
   one canonical remembered settlement anchor. Store last observed profession, level, and tick.
2. Store at most 16 count-normalized output capabilities per trader. Identity is exact
   item-and-components using V2's established `ItemStack.isSameItemSameComponents` semantics; price,
   cost, board/source slot, uses, affordability, `MerchantOffer`, `OfferSnapshot`, and authorization
   are absent.
3. A complete live vanilla-board observation replaces that trader's positive capability set:
   matching hints refresh, new hints become POSITIVE, and disappeared hints are physically removed
   to UNKNOWN. A missing/unloaded trader causes no observation and therefore erases nothing.
4. Hints expire after 168,000 ticks. Production observation prunes all expired hints before update;
   the future-facing active-capability read also physically prunes and reports whether persistence
   changed so the SavedData owner can mark dirty.
5. Enforce both trader bounds after every observation: 16 per settlement and 64 per mob. Victim
   ordering is oldest `lastSeenTick`, then unsigned UUID. Trader identity may remain with zero hints.
6. Anchor supersession rekeys trader rows. Settlement eviction deletes its traders. Ordinary unload
   and restart preserve them. Permanent owner removal deletes them with the containing memory.
7. NBT stores only identity, canonical settlement, profession/level/lastSeen, and count-normalized
   capability stack plus observation tick. Malformed/orphan rows and empty/corrupt capabilities fail
   closed. Registry-aware ItemStack serialization preserves components.
8. Production observation is piggy-backed on an already-authorized V2 vanilla market scan. It cannot
   influence the candidate list, ranking, affordability, admission, Q1/Q2, or execution.

## Alternatives and decision

| Option | Benefit | Failure mode | Disposition |
| --- | --- | --- | --- |
| Persist only item IDs | Small schema | Enchanted/component-bearing outputs collapse into false capability equivalence | Rejected |
| Persist full offer snapshots | Easy later ranking | Stale price/slot/uses becomes counterfeit market authority | Rejected |
| Persist count-normalized item+components only | Faithful positive output identity without transaction data | Requires registry-aware NBT and explicit live invalidation | Selected |

Switch only if a future V2 consumer matcher deliberately changes which output components are
semantic; then the descriptor must change with that matcher, not independently.

## Behavioral Prediction — MAIBS-1

V4-A adds memory, not a Goal, intent, authority claim, path, or transaction. During several minutes
of play a mob trades exactly as before. When V2 already performs a legitimate nearby market scan,
the mob records which exact outputs the vanilla board showed. Leaving/chunk-unloading preserves that
evidence; it does not make the mob walk back yet. A later live scan refreshes or selectively removes
the evidence, while all purchase permission remains live.

| Time | Predicted observable behavior | Invalidation/reacquisition |
| --- | --- | --- |
| T0 scan | Existing V2 route gate and market scan run unchanged; vanilla outputs are remembered | No live scan means no new trader memory |
| T+200 leave/unload | Trader identity and positive hints persist; no new travel begins | Missing entity is UNKNOWN, not NO |
| T+168000 | An unrefreshed hint stops participating and is physically pruned on normal capability observation/read | Seeing the exact output again recreates POSITIVE |
| revisit | Complete live vanilla board refreshes matches and removes only outputs no longer present | Other outputs and trader identity survive |

GoalSelector/authority prediction: no new Goal and no priority/flag changes. Combat, shelter,
Gather, Trade, village work, and Opinion interruption/resume remain byte-for-byte owned by existing
systems. Failure would be silent persistence drift, not new movement: component collapse would make
later V4 choose a settlement for the wrong variant; stale-board retention would keep suggesting a
capability after a live disproof. Both are deterministic test targets.

**Gate:** `PASS — BEHAVIORALLY_PLAUSIBLE` for implementation. Runtime destination behavior is not in
V4-A and cannot be claimed from this static slice.

## TDD and verification

Record RED before GREEN for:

- per-settlement/global simultaneous bounds and UUID tie-break;
- 16-hint bound, exact component identity, TTL boundary, and physical pruning;
- complete-board selective invalidation and missing-trader preservation;
- anchor rekey, settlement eviction, save/load, malformed/orphan rows;
- ordinary unload preservation and containing-memory permanent cleanup;
- structural ban on persisted price/slot/uses/offer/authorization fields and on new V4-B+ types;
- observation integration after existing market discovery without affecting decisions.

Commands:

```text
.\gradlew.bat test --tests "com.noobk.spmscavenger.village.KnownVillager*" --tests "com.noobk.spmscavenger.village.MobVillageMemory*" --tests "com.noobk.spmscavenger.goal.KnownVillagerObservation*"
.\gradlew.bat clean build
```

## Binding constraints

- No Minecraft launch.
- No commit or push.
- No destination ranking, SettlementOpinionBias, VillageInteractionDirector, COMMUTE integration,
  first-home promotion, or V4 runtime witness.
- Preserve unrelated user changes and the production/validation artifact boundary.

## Acceptance

**Must happen:** an already-permitted live vanilla board observation records bounded,
component-exact positive outputs; restart/rekey preserve them; expiry or live disproof returns only
the affected capability to UNKNOWN and physically removes it.

**Must not happen:** memory stores or executes stale price/slot/uses/offer authority, missing traders
become permanent NO, evidence creates trade/progression authority, or V4-A adds movement/ranking.
