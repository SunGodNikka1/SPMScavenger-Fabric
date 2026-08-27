# Task 65 brief: V4-D — VillageIntent ownership and lifecycle

## Status and target

**Status:** AUTHORIZED by User, 2026-08-26.  
**Target:** `D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`  
**Source reference:** `D:\Apps\Minecraft Port\Projects\references\artifacts\playermob-fabric-0.89.0+1.21.1.jar`  
**Canonical decision:** `plans/RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md`, D-VR-091 only.  
**Report:** `.superpowers/sdd/task-65-report.md`

## Source evidence (`CODE_CONFIRMED`)

- `WorkDemandPolicy.MaterialDemand` is a derived live fact containing `materialKey`, positive
  `derivedDeficit`, and `consumerKey`; no stable identity abstraction exists yet.
- `ExistingRouteFeasibility.ExistingRouteStatus` is the canonical current route fact and only
  `INFEASIBLE` permits trade displacement. `UNKNOWN` deliberately behaves as existing work.
- `TradeDemandGate` owns no state and re-runs live local trade authorization on every call; its
  current-offer requirement cannot authorize a remote remembered-market investigation.
- `SettlementDestinationRanker.Selection` is a pure V4-C result and stores facts/utility/Opinion
  only for the moment of selection.
- `SpmScavenger` already releases runtime-only per-mob registries on entity unload/death and clears
  them on server stop.
- Negative probes: canonical `DemandIdentity` — **NOT FOUND**; `VillageIntent` owner — **NOT FOUND**;
  any persisted navigation/intent field in village SavedData — **NOT FOUND**.

## Binding architecture

1. Add canonical `MaterialDemandIdentity(materialKey, consumerKey)` beside `MaterialDemand`, with
   `MaterialDemand.identity()`. Deficit is deliberately excluded.
2. Add a minimal transient `VillageIntent`: kind, destination, opening tick, and optional required-
   trade demand identity. `REQUIRED_TRADE` requires the identity; structural-only
   `RETURN_HOME`/`VISIT_SETTLEMENT` carry none and gain no producer in this slice.
3. A required-trade intent opens only from all current facts together: a live demand, canonical
   `INFEASIBLE` status, a V4-C selection, and that destination still present in the caller-resolved
   hard-compatible remembered set. Ranking alone is insufficient.
4. Revalidation checks only legitimacy: same live identity, route still `INFEASIBLE`, destination
   still remembered/compatible. Interruption changes current admissibility to suspended while
   preserving a valid intent. Missing/changed facts close it immediately.
5. Capability TTL, Opinion and new rankings are absent from revalidation by construction. They
   cannot mid-trip veto or retarget a valid commitment.
6. Runtime storage is one intent per loaded PlayerMob UUID, with no entity/world/path/market refs,
   no TTL and no SavedData. Entity unload/death releases it; server stop clears all. Reopening after
   unload derives from persistent semantic facts rather than resurrecting execution state.
7. No Goal, COMMUTE, pathing, live market, route-failure producer, home producer, or runtime witness
   is added.

## Alternatives and decision

| Option | Benefit | Failure mode | Disposition |
| --- | --- | --- | --- |
| Persist intent in village SavedData | Survives restart/unload | Stale execution commitment becomes semantic memory and can resurrect dead authority | Rejected |
| Cache `TradeDemandGate.Authorization` or ranked selection | Easy resume | Old offer/permission/rank becomes authority; deficit change can falsely invalidate | Rejected |
| Transient identity-only registry + live revalidation | Retains reason through scheduler interruption without retaining permission | Unload discards progress and requires recomputation | Selected |

Switch only if a later locked lifecycle proves cross-unload execution continuity is product-critical;
that would require a fresh authority model, not serializing today's decision.

## Behavioral Prediction — MAIBS-1

V4-D installs no Goal and causes no physical movement. A player sees no behavior change yet. A
future V4-E consumer can retain a settlement destination through combat, but it must re-read demand,
existing-route status, and destination candidacy before resuming; the intent itself grants nothing.

| Checkpoint | Predicted state | Owner |
| --- | --- | --- |
| T0 | live demand + INFEASIBLE route + admitted selection may open one intent | pure intent policy/registry |
| T+10 interrupted | intent remains; currently admissible is false; no path exists in V4-D | future scheduler/COMMUTE |
| T+60 resume | fresh matching facts make the same destination admissible | live demand/route facts |
| T+200 deficit 3→2 | same identity; intent remains | canonical demand identity |
| T+1200 | no timer kills a semantically valid long trip; unload/stop still releases runtime state | lifecycle hooks |

Goal interaction: none in D. Combat/shelter/commands cannot be preempted because D owns no flags,
priority or navigation. Existing Gather/Smelt/Craft and V2 keep their authority.

Predicted weird behaviors:

1. A capability hint can expire while the remembered destination remains committed —
   `ACCEPTABLE_STEPPING_STONE`; expiry is UNKNOWN, not disproval, and live V2 checks arrival.
2. A much better candidate can appear without retargeting — `ACCEPTABLE_STEPPING_STONE`; deliberate
   hysteresis prevents A↔B thrash.
3. Chunk/dimension unload discards the transient intent — `ACCEPTABLE_STEPPING_STONE`; persistent
   village/trader facts permit a fresh decision after load without retaining authority.
4. If a future consumer opens intents but never calls revalidation, a semantically obsolete entry
   can remain until lifecycle release — `RUNTIME_QUESTION`; V4-E must make revalidation part of every
   resume/evaluation boundary and its structural tests must pin that call.

Falsifier after V4-E: combat ends and an old intent resumes after its demand disappeared, Opinion
alone retargets a live commitment, or an intent appears in NBT. Any is an architecture defect.

**Gate:** `PASS — BEHAVIORALLY_PLAUSIBLE` for V4-D's non-executing lifecycle slice; physical resume
and travel remain not applicable until V4-E.

## TDD and verification

RED then GREEN covers opening, stable identity/deficit change, demand/route/destination invalidation,
combat suspension/fresh resume, no reranking/capability veto, registry lifecycle, and structural
absence of navigation, market authority, persistence, route-failure production and V4-E+ behavior.

```text
.\gradlew.bat test --tests "com.noobk.spmscavenger.village.intent.*"
.\gradlew.bat clean build
```

**Must happen:** a legitimate required-trade commitment survives interruption and deficit change,
then resumes only after current facts are revalidated.  
**Must not happen:** intent opens from ranking alone, caches permission/market/path data, retargets on
preference changes, survives owner lifecycle, or starts COMMUTE.

No Minecraft launch. No commit or push.
