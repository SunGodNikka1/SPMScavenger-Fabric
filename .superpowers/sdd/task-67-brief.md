# Task 67 brief: V4-F — first-home promotion

## Status and target

**Status:** DONE / STATIC+PACKAGE ACCEPTED  
**Target:** `D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`  
**Host source reference:** `D:\Apps\Minecraft Port\Projects\references\SocialPlayerMobs-v0.96.0`  
**Host artifact:** `D:\Apps\Minecraft Port\Projects\references\artifacts\playermob-fabric-0.96.0+1.21.1.jar`  
**Canonical decisions:** `D-VR-042-A1` and the first-home-only portion of `D-VR-094`  
**Report:** `.superpowers/sdd/task-67-report.md`

No Minecraft launch. No commit or push. No V4-G witness, autonomous re-home, ranking, V4-E,
debug, raid, or other product behavior.

## Source evidence (`CODE_CONFIRMED`)

- Target `SeekShelterGoal#lieDown`, lines 216–238: after live bed/claim validation it stops
  navigation and calls the real `mob.startSleeping(bedPos)`; this is the one authorized producer
  event.
- Target `VillageMemorySavedData#designateHome(ServerLevel, UUID, BlockPos, long)`, lines 245–256:
  existing canonical write path owns `MobVillageMemory.designateHome`,
  `SettlementRelationshipService.onHomeDesignated`, and dirtying.
- Target `MobVillageMemory`, lines 198–263: one optional `homeAnchor`, canonical `KnownVillage`
  anchors, and rekey-on-supersession already exist.
- Target `SettlementBoundsPolicy`: the existing 64-block settlement activity/presence relation.
  Association uses this established meaning and admits exactly one match; it does not use nearest.
- Target `SettlementTuning.HIGH_BAND_MIN = 600`; `SettlementRelationship.familiarityScore()` is the
  factual threshold input.
- Host v0.96 `PlayerMobEntity` retains normal LivingEntity sleeping behavior; the addon introduces
  no host API replacement and continues to call Minecraft's real `startSleeping`/`isSleeping`.

Three absence probes before adding the producer: no production class named `FirstHomePromotion`;
no production `designateHome(...)` caller outside the canonical SavedData API; no sleep latch,
pending-home field, or autonomous home-switch policy in production village/goal sources.

## Required implementation

1. Evaluate promotion only immediately after `startSleeping(bedPos)` and only if
   `mob.isSleeping()` is true.
2. Read existing dimension-local memory without creating it. Resolve the exact bed position against
   already remembered villages within `SettlementBoundsPolicy`; zero or more than one match is a
   no-op.
3. Require no existing home and familiarity >= `SettlementTuning.HIGH_BAND_MIN` for the canonical
   matched anchor.
4. Invoke only `VillageMemorySavedData.designateHome(level, mob UUID, canonical anchor, now)` for
   the write. Do not duplicate relationship/dirty effects.
5. Add no latch, pending state, config, timer, SavedData field, home-switch threshold, or periodic
   sleeping poll.

## Alternatives and decision

| Option | Benefit | Failure mode | Disposition |
| --- | --- | --- | --- |
| Associate through 48-block `VillageIdentityPolicy` | Tighter geometry | Identity merge answers whether two anchors are one remembered place, not whether a bed belongs to the settlement activity envelope; edge beds false-negative | Rejected |
| Choose nearest remembered anchor within 64 blocks | More promotions in overlapping villages | Converts ambiguity into invented certainty and may designate the wrong home | Rejected |
| Require exactly one remembered anchor within `SettlementBoundsPolicy` | Reuses current settlement presence/work semantics and fails ambiguity safely | Close villages may deliberately produce no home | Selected |

Switch only if runtime evidence establishes that the 64-block association systematically rejects
ordinary single-village beds or regularly creates overlap ambiguity; that would reopen association
geometry, not authorize nearest selection automatically.

## MAIBS-1 behavioral prediction — `PASS: BEHAVIORALLY_PLAUSIBLE`

| Layer/time | Prediction |
| --- | --- |
| Intended behavior | A familiar mob makes the first genuinely slept-in remembered village its stable home. |
| T0 arrival | Existing shelter navigation reaches and revalidates a real bed and live claim. No new selection/path behavior is introduced. |
| T0 interaction | `startSleeping(bedPos)` runs. Failed sleep ends the promotion branch immediately. |
| T+same tick | One non-creating memory read resolves zero/one/many bounded associations. Exactly one + score >=600 + no home invokes the existing writer once. |
| T+10…1200 | The mob sleeps/wakes under existing shelter behavior. No poll or latch retries. Later familiarity changes do nothing until another real sleep event. Existing home makes every later sleep a no-op. |
| Interruption | Combat/commands before sleeping prevent the event naturally; after successful sleeping, designation is already committed factual state. No resume state exists. |

Goal interaction: `SeekShelterGoal` retains its priority, MOVE flag, navigation, commitment, claim,
arrival and wake behavior. V4-F inserts one state transition after successful interaction and owns no
Goal or navigation flags.

Adversarial cases: two remembered anchors both within 64 blocks; remembered anchor just outside the
association envelope; score 599→600 after sleep; bed disappears/occupies at arrival; existing home
and higher-score second village; anchor supersession after designation; save/load.

Predicted weird behaviors:

1. Two close remembered villages make a real sleep produce no home — `ACCEPTABLE_STEPPING_STONE`,
   fail-safe and visible; a runtime overlap-frequency sample would determine whether geometry needs
   reopening.
2. A logical edge bed beyond 64 blocks from a poor remembered anchor produces no home —
   `RUNTIME_QUESTION`; falsify using a naturally observed large-village anchor/bed distance sample.
3. Sleep at 599 followed by later score 600 does nothing until the next sleep —
   `ACCEPTABLE_STEPPING_STONE`, explicitly locked event causality rather than a stale latch.

**Must happen:** successful real sleep, exactly one already remembered associated settlement,
familiarity 600, and no home invokes the existing designation writer once with the canonical anchor.

**Must not happen:** failed sleep, 599, missing/ambiguous memory, later score change, Opinion/trader
facts, or an existing home creates/replaces HOME; no latch or new persistent state appears.

Runtime falsifier for V4-G: on the exact production/validation pair, build genuine familiarity 600,
observe a real associated sleep, then inspect the persisted canonical home; also sleep in a second
higher-familiarity village and verify home remains unchanged. Static completion does not claim this
physical sequence is runtime-confirmed.

## Verification

- Focused first-home policy/wiring tests, including negative controls.
- Retained R0 migration/rekey/save-load tests.
- `gradlew.bat clean build` (production + validation suites and package audits).
- Update RFC, test matrix, Task 67 report, progress ledger, and exact artifact hashes.
