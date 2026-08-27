# Task 62 report: V4-A — KnownVillager positive capability evidence

## Outcome

**DONE / STATIC+PACKAGE ACCEPTED** for locked `D-VR-090`. No Minecraft runtime was launched and no
runtime destination/travel claim is made.

## Implemented scope

- `TradeOutputCapability`: immutable count-normalized item-and-components descriptor using V2's
  established `ItemStack.isSameItemSameComponents` identity and registry-aware ItemStack NBT.
- `KnownVillager`: UUID, canonical settlement anchor, observed profession/level/tick, and bounded
  positive capability hints only. No market/transaction authority fields.
- `MobVillageMemory`: simultaneous 16/settlement and 64/mob trader bounds, 16 hints/trader,
  oldest-observation then UUID eviction, 168,000-tick physical hint pruning, rekey, settlement
  eviction, save/load, and existing owner lifecycle.
- `VillageMemorySavedData`: dirty-owning observation/prune seams without creating a second data owner.
- `KnownTraderMarketObservation` + `TradeWithVillagerGoal`: passively record the complete vanilla
  board V2 already read after existing route/cooldown admission. No second scan; observation result
  cannot affect candidate selection, ranking, Q1/Q2, affordability, or execution. Query-shaped TE
  synthetic quotes are deliberately not persisted as a complete stable board.

## TDD evidence

RED was recorded when `KnownVillagerTest` failed compilation on the absent descriptor, trader model,
bounds, observation, pruning, and registry-aware persistence APIs. GREEN now covers 12 new behavioral
tests plus 2 structural observation-boundary tests. The retained R0 structural test was synchronized
from "KnownVillager absent" to "KnownVillager present, post-V4-A producers absent."

Deterministic cases include local/global bounds, UUID tie-break, capability bound/TTL/physical
deletion, identity surviving empty hints, component distinction/count normalization, selective live
disproof, missing-trader preservation, anchor rekey, settlement eviction, malformed/orphan rejection,
direct and top-level save/load, dirty expiry rewrite, permanent cleanup, unknown-settlement refusal,
and absence of any extra scan/policy/revalidation/transaction call in the passive bridge.

## Build and package evidence

Final `gradlew.bat clean build` passed in 38 seconds:

- production: **1,649 tests**, 0 failures/errors/skips;
- validation: **57 tests**, 0 failures/errors/skips;
- production JAR: zero validation namespace, Task-59 campaign/Gate0/contamination/scenario resources,
  and upstream Trade Everything classes;
- validation JAR: 60 classes, all under the validation namespace, zero exact production duplicates;
- production: `build/libs/spmscavenger-1.11.0.jar`, SHA-256
  `9FEF386814C1ABCFD34CC4B7813E2FD44EA524192335E9529762ED34B25C34C4`;
- validation: `build/libs/spmscavenger-1.11.0-validation.jar`, SHA-256
  `BB02D551AEED4733434A3756401A9B520091C4056477A7C347CD656CC5F546A0`.

## Semantic-drift review

Planned → implemented is exact for positive-only evidence, bounds, TTL, component identity,
selective invalidation, and lifecycle. One boundary was made explicit from source evidence:
production records the complete **vanilla board** already observed by V2, not query-dependent TE
synthetic sell quotes. Treating a quote generated only for the current authorized inventory as a
complete durable trader board would let a later different query falsely disprove or refresh it.

No Goal, priority, activity class, navigation, progression authority, transaction, home promotion,
Opinion ranker, director, or COMMUTE behavior changed. The least-verified claim is live world
persistence across an actual server restart; it remains **UNVERIFIED** because runtime was forbidden,
while codec/lifecycle behavior is `CODE_CONFIRMED` by deterministic SavedData round trips.

## Acceptance

**Must happen:** PASS — existing legitimate live vanilla board observations create bounded,
component-exact POSITIVE hints; expiry/live disproof returns only affected hints to UNKNOWN.

**Must not happen:** PASS — no stale offer authority is persisted, missing traders do not become NO,
unknown settlements create nothing, and memory cannot affect live trade planning/execution.

## Scope exclusions

No SettlementOpinionBias, destination ranking, VillageInteractionDirector, COMMUTE integration,
first-home promotion, V4 runtime witness, Minecraft launch, commit, or push.
