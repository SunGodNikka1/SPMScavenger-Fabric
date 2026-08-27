# Task 64 report: V4-C — factual settlement destination ranking

## Status

**DONE / STATIC+PACKAGE ACCEPTED** for D-VR-092 and the ranking-policy portion of D-VR-093. No
Minecraft runtime was launched; no live destination, travel, or trade behavior is claimed.

## Files created or changed

- `src/main/java/com/noobk/spmscavenger/village/routing/CapabilityEvidenceClass.java`
- `src/main/java/com/noobk/spmscavenger/village/routing/SettlementKey.java`
- `src/main/java/com/noobk/spmscavenger/village/routing/SettlementDestinationFacts.java`
- `src/main/java/com/noobk/spmscavenger/village/routing/FactualVillageUtility.java`
- `src/main/java/com/noobk/spmscavenger/village/routing/RouteAttemptEvidence.java`
- `src/main/java/com/noobk/spmscavenger/village/routing/SettlementDestinationRanker.java`
- `src/main/java/com/noobk/spmscavenger/village/VillageMemorySavedData.java`
- routing matrix/boundary tests and the synchronized V4-B structural guard
- Task-64 brief/report, progress ledger, and canonical Village/Raid RFC

## Summary

The ranker consumes only immutable remembered facts and returns a deterministic selection. Its
comparator structurally encodes dimension/transient candidacy, capability class, factual tuple,
bounded Opinion, and stable key; no additive weight can let UNKNOWN cross POSITIVE_HINT. The
SavedData owner resolves component-exact capability facts and persists TTL pruning without any
world or market read.

## Commands and exact results

Working directory for every command:
`D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`.

- RED: `.\gradlew.bat test --tests "com.noobk.spmscavenger.village.routing.*"` failed compilation
  with 80 missing-symbol errors for the intentionally absent V4-C APIs.
- Focused GREEN: the same command passed **18 tests**, zero failures.
- First `clean build`: 1,676 tests ran; two retained V4-B structural tests failed because they still
  asserted V4-C absence. The guards were synchronized, not disabled.
- Final `.\gradlew.bat clean build`: **BUILD SUCCESSFUL** in 38 seconds; **1,676 production + 57
  validation tests**, zero failures/errors/skips; production and validation JAR audits passed.
- Static probes found zero `createPath`, `moveTo`, chunk access, perception, entity/villager scan,
  market-offer, authority, director, or intent references in the routing package.

## Artifact evidence

- Production: `build/libs/spmscavenger-1.11.0.jar`, SHA-256
  `5F40423315E31C8F97556630B692686A792309841315F4EF736599A4C5461966`.
- Validation: `build/libs/spmscavenger-1.11.0-validation.jar`, SHA-256
  `BB02D551AEED4733434A3756401A9B520091C4056477A7C347CD656CC5F546A0`.
- Production contains seven V4-C top-level routing types (eight class entries including the nested
  attempt record); validation contains none of them; exact class duplication count is zero.

One unrelated retained deprecation warning remains in `EpisodeRetentionTest` for
`MobExperienceContext.episodeFor(UUID)`.

## Deterministic evidence

- empty/single selection;
- POSITIVE_HINT over UNKNOWN despite home, distance, and ±15 Opinion;
- factual distance before Opinion within one evidence class;
- comparable positives reordered by Opinion;
- collection-order-independent canonical tie;
- exact component match and component mismatch;
- TTL boundary physically prunes to UNKNOWN and marks SavedData dirty;
- remembered facts need no perception/chunk state;
- dimension mismatch fails objective compatibility;
- active demotion selects an alternative and exact expiry restores the candidate;
- route evidence entry/generation bounds and deterministic duplicate collapse;
- horizontal estimate ignores both origin and destination Y;
- no persistent negative evidence class, failure history, blacklist, or V4-D+ type.

## Source evidence

- `CONFIRMED` — structural comparator order and lack of execution ownership:
  `src/main/java/com/noobk/spmscavenger/village/routing/SettlementDestinationRanker.java:15-69`.
- `CONFIRMED` — dirty-owned TTL pruning and component-exact remembered fact resolution:
  `src/main/java/com/noobk/spmscavenger/village/VillageMemorySavedData.java:179-215`.
- `CONFIRMED` — horizontal-only, non-additive factual tuple:
  `src/main/java/com/noobk/spmscavenger/village/routing/FactualVillageUtility.java:8-50`.
- `CONFIRMED` — bounded immutable cooldown input and exact expiry boundary:
  `src/main/java/com/noobk/spmscavenger/village/routing/RouteAttemptEvidence.java:10-70`.
- `UNVERIFIED / NOT APPLICABLE YET` — in-world destination selection, movement, retry, and live
  market revalidation have no V4-C consumer and were not launched.

## Semantic-drift and concerns

`PLANNED → IMPLEMENTED → PREDICTED RUNTIME` is exact at this slice's proof class. Home and
familiarity are factual tie-breaks only after exact horizontal-distance equality; no unsupported
weights were invented. Non-dimensional progression admission remains a caller responsibility, so
the ranker cannot mint authority. Route-attempt evidence has no producer until future movement
integration.

The strongest remaining objection is intentional: a stale positive hint can later cause a useless
trip. That is not fixed by pretending memory is live truth; V4-E/V2 must re-read the market on
arrival. Runtime behavior stays **UNVERIFIED / NOT APPLICABLE** because V4-C has no consumer.

## Acceptance

**Must happen:** PASS — deterministic selection obeys the exact comparator and cooldown expiry.  
**Must not happen:** PASS — UNKNOWN never crosses POSITIVE_HINT; no path/chunk/scan/live-market,
authority/intent, persistent blacklist, V4-D+, runtime launch, commit, or push.
