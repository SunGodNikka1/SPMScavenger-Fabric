# Task 64 brief: V4-C — factual settlement destination ranking

## Status and target

**Status:** AUTHORIZED by User, 2026-08-26.  
**Target:** `D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`  
**Source reference:** `D:\Apps\Minecraft Port\Projects\references\artifacts\playermob-fabric-0.89.0+1.21.1.jar`  
**Canonical decisions:** `plans/RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md`, D-VR-092 and ranking-policy portion of D-VR-093.  
**Report:** `.superpowers/sdd/task-64-report.md`

## Source evidence (`CODE_CONFIRMED`)

- `MobVillageMemory` contains at most 16 remembered villages per dimension and already owns home,
  relationship, and bounded `KnownVillager` evidence.
- `TradeOutputCapability` preserves exact item-and-components identity; `KnownVillager` hints expire
  after 168,000 ticks and physically prune through the owning `VillageMemorySavedData` dirty path.
- `SettlementOpinionBias` returns only the established bounded `[-15,+15]` soft preference.
- `VillageMemorySavedData` is dimension-local and has non-allocating `peek`; dimension belongs in
  every candidate's stable factual identity.
- `TradeCandidateRound` proves the existing project rule: path failure is transient executor
  evidence, not persistent world knowledge.
- Negative probes: `FactualVillageUtility` — **NOT FOUND**; `VillageInteractionDirector` or
  `VillageIntent` — **NOT FOUND**; persisted settlement route-failure/blacklist state — **NOT FOUND**.

## Binding architecture

1. Resolve immutable ranking facts from already remembered settlements. The SavedData owner may
   physically prune expired capability hints and mark dirty; no world/perception/entity/chunk read
   participates.
2. Comparator order is structural, not weighted: objective/dimension candidacy →
   `POSITIVE_HINT` before `UNKNOWN` → factual tuple → bounded `SettlementOpinionBias` → canonical
   dimension+anchor tie-break.
3. Capability matching reuses `TradeOutputCapability` exact item/components. Expiry or mismatch is
   `UNKNOWN`, never NO.
4. Factual tuple is deliberately non-additive and narrow: horizontal squared anchor distance first,
   then home and familiarity only as factual tie-breaks. No Y, safety, wealth, population,
   workstation, tier, or terrain/path proxy.
5. Transient route-attempt evidence is immutable, capped to the maximum remembered candidate set,
   generation-bounded, non-persistent, and has no producer in V4-C. Active evidence temporarily
   excludes its exact settlement; expiry restores candidacy.
6. Selection returns a ranked factual result only. It creates no authority, intent, path, commute,
   trade authorization, or live-market truth.

## Alternatives and decision

| Option | Benefit | Failure mode | Disposition |
| --- | --- | --- | --- |
| One additive score (`hint + home - distance + opinion`) | Easy tuning | Later weights can let UNKNOWN outrank the sole POSITIVE source | Rejected |
| Structured lexicographic comparator | D-VR-092 remains true by construction | Opinion acts only when higher factual dimensions are comparable | Selected |
| Persist failed routes on village memory | Survives restart | Temporary geometry becomes counterfeit permanent world knowledge | Rejected |
| Bounded immutable attempt input | Retry semantics without execution ownership | Producer remains deferred until COMMUTE integration | Selected |

Switch from the factual tuple only after a separately locked rule defines a new factual field and
its precedence. Never smuggle that change in as a numeric weight.

## Behavioral Prediction — MAIBS-1

V4-C installs no Goal, scheduler flag, path, movement, scanner, interaction, or world mutation. A
player sees no behavior change yet. Later, when V4-D/E consume it, a blocking output with one
positive remembered source selects that source even if an UNKNOWN home is nearer/liked. Selection
does not prove arrival or trade; COMMUTE and live V2 revalidation remain future owners.

| Checkpoint | Predicted state | Owner |
| --- | --- | --- |
| T0 ranking | remembered, same-dimension candidates only; expired hints prune to UNKNOWN | SavedData facts + pure ranker |
| T+10 | no path/movement starts because no intent/executor exists | unchanged GoalSelector |
| T+200 route failure (future) | immutable cooldown input may select another settlement | future COMMUTE producer, not ranker |
| cooldown expiry | original candidate participates again | pure time comparison |
| T+1200 | no retained V4-C state and no autonomous loop | existing systems unchanged |

Goal interaction: none. No priority or flag changes; combat, shelter, Gather, Trade, village work,
Exploration and COMMUTE retain their existing ownership.

Predicted weird behaviors:

1. Stale POSITIVE evidence can cause a future fruitless visit — `ACCEPTABLE_STEPPING_STONE`; live
   V2 must disprove/refresh on arrival.
2. If the sole candidate is temporarily demoted, selection is empty until cooldown expiry —
   `ACCEPTABLE_STEPPING_STONE`; prevents immediate retry thrash.
3. Opinion may rarely decide because any factual tuple difference precedes it —
   `ACCEPTABLE_STEPPING_STONE`; this is the explicit lexicographic contract, not a hidden weight.
4. No live demotion occurs before V4-E supplies failure evidence — `ACCEPTABLE_STEPPING_STONE` and
   scope boundary, not missing ranker state.

Falsifier after future integration: a liked UNKNOWN settlement beats the sole unexpired POSITIVE
source, ranking touches an unloaded chunk/path API, or a cooldown survives as SavedData. Any is an
architecture defect.

**Gate:** `PASS — BEHAVIORALLY_PLAUSIBLE` for deterministic V4-C implementation; runtime travel is
not applicable until V4-E.

## TDD and verification

RED then GREEN covers the complete requested matrix: empty/single, evidence precedence despite
Opinion/home, factual and Opinion ordering, stable tie, TTL/component identity, unloaded/source-only
facts, dimension legality, transient demotion/expiry/bounds/non-persistence, and structural bans on
world/path/authority/V4-D+ behavior.

```text
.\gradlew.bat test --tests "com.noobk.spmscavenger.village.routing.*"
.\gradlew.bat clean build
```

**Must happen:** one deterministic same-dimension result obeys the declared comparator and cooldown.  
**Must not happen:** UNKNOWN crosses the POSITIVE class boundary; ranker loads/scans/paths, persists
failures, creates authority/intent, or implements V4-D+.

No Minecraft launch. No commit or push.
