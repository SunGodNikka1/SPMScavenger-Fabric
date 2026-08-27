# Task 63 brief: V4-B — SettlementOpinionBias

## Status and target

**Status:** AUTHORIZED by User, 2026-08-26.  
**Target:** `D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`  
**Canonical decision:** `plans/RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md`, D-VR-025 / V4-B.  
**Report:** `.superpowers/sdd/task-63-report.md`

## Source evidence (`CODE_CONFIRMED`)

- `PlaceOpinionRouteRanker.destinationBias(...)` already owns the established Place preference
  normalization and the `MAX_ROUTE_BIAS = 15` soft cap.
- `KnownVillage.anchor()` is the canonical current settlement geography. No persisted Place key is
  present on `KnownVillage` or `MobVillageMemory`.
- `DiscretionaryScoringInput` carries affect/personality-derived social inputs, but no locked V4
  coefficient maps those channels to settlement preference. They remain neutral in this slice.
- `PlaceOpinionMemory` is access-ordered; its ordinary `preference(...)` read changes LRU order.
  Settlement evaluation therefore consumes an immutable captured preference map, never a live
  mutating lookup.
- Negative probes: existing `SettlementOpinionBias` — **NOT FOUND**; persisted settlement Opinion
  subject/key — **NOT FOUND**; direct `PersonalityModel` or `AffectiveState` composition in village
  destination code — **NOT FOUND**.

## Binding implementation contract

1. Add `SettlementOpinionBias.request(KnownVillage, DiscretionaryScoringInput,
   SettlementOpinionContext)` in the Opinion package.
2. `SettlementOpinionContext` is immutable already-resolved input: a defensive snapshot of existing
   Place preferences only. It owns no level, entity, memory service, scanner, or writer.
3. Evaluate the chunk containing the village's **current** anchor through
   `PlaceOpinionRouteRanker.destinationBias(...)`; never persist or migrate a settlement Place key.
4. Return zero when the global Opinion feature gate or the supplied scoring input is disabled.
5. Clamp the final integer to `[-MAX_ROUTE_BIAS, +MAX_ROUTE_BIAS]`. Undefined affect, personality,
   relationship, and trader-preference contributions are exactly neutral.
6. Add structural guards against village destination code importing/owning `PersonalityModel` or
   `AffectiveState` composition and against persistence/authority semantics in this facade.

## Alternatives and decision

| Option | Benefit | Failure mode | Disposition |
| --- | --- | --- | --- |
| Store one Place chunk key per settlement | Cheap lookup | Anchor supersession freezes stale geography and invents migration semantics | Rejected |
| Pass live `MobExperienceContext`/services | Easy access to every signal | Service-locator boundary can create/mutate memory and turns input into a god-object | Rejected |
| Pass immutable Place snapshot | Current-anchor lookup remains geographic; evaluation is non-mutating | Snapshot is round-local and must be refreshed by caller | Selected |

Switch only if a later locked Opinion decision defines another contribution and its normalization;
then add that already-resolved fact to the context and keep the same outer clamp.

## Behavioral Prediction — MAIBS-1

V4-B installs no Goal, destination consumer, claim, path, scan, or world mutation. Across several
minutes of play, mobs move and schedule exactly as before. The only new observable potential is for
a later V4-C consumer: among already-valid settlement destinations, a liked current anchor may gain
at most +15 and a disliked one may lose at most 15. If an anchor rekeys across a chunk boundary, the
new geographic Place preference applies immediately on the next resolved context; no old preference
is migrated. Combat, shelter, mandatory progression, village work, and trade authority remain
unchanged because the result is an integer preference only.

**Gate:** `PASS — BEHAVIORALLY_PLAUSIBLE` for static implementation. Runtime destination behavior is
out of scope because no destination consumer exists in V4-B.

## Verification and acceptance

RED then GREEN tests cover positive/negative/neutral Place bias, both feature gates, hard cap,
current-anchor chunk crossing, snapshot/non-mutation, absence of persistence/frozen keys, and no
permission/eligibility surface.

**Must happen:** current-anchor Place preference produces only a bounded integer soft bias.  
**Must not happen:** evaluation mutates memory, freezes settlement geography, loads/scans world
state, creates permission/denial, or introduces V4-C+ behavior.

Commands:

```text
.\gradlew.bat test --tests "com.noobk.spmscavenger.opinion.SettlementOpinionBiasTest" --tests "com.noobk.spmscavenger.opinion.SettlementOpinionBoundaryTest"
.\gradlew.bat clean build
```

No Minecraft launch. No commit or push.
