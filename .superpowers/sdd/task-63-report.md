# Task 63 report: V4-B — SettlementOpinionBias

## Outcome

**DONE / STATIC+PACKAGE ACCEPTED** for locked D-VR-025. No Minecraft runtime was launched and no
destination-selection behavior is claimed.

## Implemented scope

- `SettlementOpinionBias.request(...)`: one Opinion-owned facade returning only a bounded integer
  soft preference in `[-15, +15]`.
- `SettlementOpinionContext`: defensive immutable snapshot of already-existing Place preferences;
  no world, entity, SavedData, service locator, scan, or mutation surface.
- Current `KnownVillage.anchor()` block coordinates select the geographic Place chunk on every
  request. No settlement Place key is stored, persisted, or migrated.
- Both global `OpinionFeatureGate` disablement and the supplied scoring-input disablement return 0.
- Affect, personality, relationship, and trader-preference contributions remain neutral because no
  locked mapping or coefficient exists in this slice.

## RED→GREEN and defect evidence

The focused suite initially failed three geography assertions. Root cause was the retained
`PlaceOpinionRouteRanker.destinationBias(places, blockX, blockZ)` implementation calling
`new ChunkPos(blockX, blockZ)`: that constructor consumes chunk coordinates despite the API and
callers supplying block coordinates. The ranker now converts a `BlockPos` to its containing
`ChunkPos`; retained ranker tests were updated to pin the real unit boundary.

Nine new tests cover positive/negative/neutral values, both feature gates, extreme-input clamping,
current-anchor chunk crossing, defensive snapshot/non-mutation, no persistence/frozen key/world
access, integer-only authority boundary, future destination-owner import guard, and absence of
V4-C+ classes. The retained Place-ranker tests also pass.

## Build and package evidence

Final `.\gradlew.bat clean build` passed in 39 seconds:

- production: **1,658 tests**, 0 failures/errors/skips;
- validation: **57 tests**, 0 failures/errors/skips;
- both configured production/validation JAR audits passed;
- production and validation JAR class sets have zero duplicates;
- production: `build/libs/spmscavenger-1.11.0.jar`, SHA-256
  `F977525349F9298D4616E3ECCBC0449839CB0E89F39ED54BAC8396E022A7C355`;
- validation: `build/libs/spmscavenger-1.11.0-validation.jar`, SHA-256
  `BB02D551AEED4733434A3756401A9B520091C4056477A7C347CD656CC5F546A0`.

One unrelated retained deprecation warning remains in `EpisodeRetentionTest` for
`MobExperienceContext.episodeFor(UUID)`; it caused no failure.

## Semantic-drift review

Planned → implemented is exact for the ownership boundary, immutable resolved input, current-anchor
Place semantics, Opinion disablement, and hard cap. The implementation deliberately does **not**
compose sociability, stress, curiosity, relationship, or trader preference: inventing coefficients
would violate the authorization. The pre-existing block/chunk unit correction is necessary to make
the locked geographic rule true.

MAIBS prediction remains unchanged: there is no consumer, Goal, priority, claim, navigation, scan,
inventory action, or world mutation in V4-B. If this assumption were false, a source/package search
would find a new destination consumer or authority type; structural tests report none.

## Acceptance

**Must happen:** PASS — current-anchor Place preference yields the established bounded soft bias.  
**Must not happen:** PASS — no eligibility/permission result, mutation, frozen key, persistence,
chunk probing, trader scan, V4-C ranking, director, intent, COMMUTE, home producer, or runtime code.

## Scope exclusions

No FactualVillageUtility, multi-village/capability ranking, travel estimate/demotion,
VillageInteractionDirector, VillageIntent, COMMUTE integration, first-home promotion, new Place
learning producer, runtime witness, Minecraft launch, commit, or push.
