# Task 68 brief: V4-G representative runtime validation preparation

## Status and target

**Status:** PREPARED — STATIC/PACKAGE CONFIRMED; RUNTIME UNVERIFIED  
**Target:** `D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`  
**Host source reference:** `D:\Apps\Minecraft Port\Projects\references\SocialPlayerMobs-v0.96.0`  
**Host artifact:** `D:\Apps\Minecraft Port\Projects\references\artifacts\playermob-fabric-0.96.0+1.21.1.jar`  
**Canonical decision:** `D-VR-095`; V4-A through V4-F static/package accepted  
**Report:** `.superpowers/sdd/task-68-report.md`

No Minecraft launch, commit, production debug behavior, production fixture hook, or product semantic
change. All mutable setup, passive observation, commands, mixins and resources belong to
`spmscavenger_validation` / `com.noobk.spmscavenger.validation.*`.

## Source evidence (`CODE_CONFIRMED`)

- Production `KnownTraderMarketObservation.recordVanillaBoard`: consumes the complete vanilla board
  already read by V2 and records positive output capability only.
- Production `VillageInteractionDirector`: resolves live `WorkDemandPolicy`, canonical
  `ExistingRouteFeasibility`, remembered candidates and Opinion; opens/revalidates/releases exact
  REQUIRED_TRADE bindings.
- Production `ExploringGoal`: REQUIRED_TRADE seeds the existing COMMUTE executor, stores the exact
  binding in expedition state, disposes path state on interruption, targets the final canonical
  anchor, and calls `completeArrival` before V2 regains local ownership.
- Production `TradeWithVillagerGoal` + `VillagerTradeAdapter.performResolvedTrade`: rediscover the
  local live board, revalidate a live `MerchantOffer`, and transact that object.
- Production `SeekShelterGoal.lieDown` + `FirstHomePromotion`: the only first-home producer follows
  successful real `startSleeping(bedPos)` / `isSleeping()`.
- Pinned SPM v0.96 SHA-256:
  `508EDA58611A2A0738E257F98C2E14C5032C6EFBF5B1A985C9F93EE295131097`.

Negative probes before implementation: no V4 runtime controller in production; no V4 validation
command/controller/tracker in the validation sidecar; no existing validation mixin observing the
V4 director, COMMUTE, transaction, or first-home seams.

## Fixture architecture

One bounded session and one single-village arena:

```text
fixture setup (validation) -> ordinary V2/V4 production -> passive validation tracker
```

Command contract:

```text
/spmscavenger debug v4 run
/spmscavenger debug v4 status
/spmscavenger debug v4 report
/spmscavenger debug v4 stop
/spmscavenger debug v4 reset
```

The controller creates tagged fixture entities/geometry, controls declared time/inventory/offers,
forces and later releases a bounded corridor of chunks, and may teleport the subject only before
Phase-A evidence opens. The tracker never calls ranking, intent mutation, navigation, trade,
sleeping, home designation, or inventory/world mutation.

## Sequential state machine

### Phase A — REQUIRED_TRADE

1. Natural settlement perception creates one remembered settlement while HOME remains absent.
2. Stone-pick + stick prerequisites create the production iron-pickaxe consumer. A completed natural
   Gather scan in an ore-free bounded area must produce canonical INFEASIBLE evidence.
3. V2 reads a controlled initial vanilla offer `8 emerald -> iron_pickaxe`; the subject has no
   currency/funding, so the board can be remembered but not transacted.
4. After the positive capability is observed, validation replaces the live board with
   `10 emerald -> iron_pickaxe`, gives exactly 10 emeralds, moves the subject once to the declared
   180-block departure point, and opens the evidence window. HOME must still be absent.
5. Production must open/adopt REQUIRED_TRADE, bind existing COMMUTE, return to the canonical anchor,
   release the travel intent, rediscover the changed live board, and execute the 10-emerald offer.
6. A declared hostile may induce one bounded combat interruption after COMMUTE begins. It must not
   call the subject's Goal/navigation/intent APIs. Interruption evidence is reported separately and
   must never create route-failure evidence.

### Phase B — first HOME

Only after Phase A passes: validation sets the already-remembered relationship to familiarity 600,
keeps HOME absent, and sets night. It does not call `FirstHomePromotion`, `designateHome`, or
`startSleeping`. Production `SeekShelterGoal` must choose/reach the fixture bed, sleep, and designate
the canonical remembered anchor.

## Evidence and verdicts

Timestamp transitions only, with `PASS`, `FAIL`, `INCOMPLETE`, or `FIXTURE_FAILURE`:

- HOME before trade; settlement/capability; initial and changed offer fingerprints;
- demand identity and `ExistingRouteStatus`; selected destination; exact intent/binding identity;
- director admission/revalidation; REQUIRED_TRADE COMMUTE seed; positions and canonical arrival;
- interruption/stop/resume and route-attempt count; arrival release;
- changed-board rediscovery; exact live executed fingerprint/result/inventory;
- Phase-B familiarity, bed, association count, running `SeekShelterGoal`, sleeping, and HOME.

Never assign PASS from fixture-created state. An executed fingerprint must equal the changed live
fingerprint and differ from the initial fingerprint.

## RET-1 / lifecycle

Exactly one static session keyed by the fixture subject UUID, with a hard overall deadline and
bounded transition log. No world/entity is retained after terminal report creation. Unload, death,
dimension loss, server stop, operator stop and reset release tracker/session and forced chunks.
Tagged fixture cleanup removes only exact fixture entities; placed blocks are preserved and reported
because post-run world provenance is ambiguous.

## MAIBS-1 prediction — `PASS: BEHAVIORALLY_PLAUSIBLE`, runtime `UNVERIFIED`

The fixture supplies a flat, cleared, force-loaded 180-block corridor. Production owns every
selection, Goal adoption, path, arrival, market scan, transaction, shelter path, sleep and HOME
write. Expected sequence is bounded by natural settlement/route bootstrap, <=2400 ticks for Phase A,
and <=2400 ticks for Phase B.

Weird behaviors and probes:

1. The canonical settlement anchor may land farther than V2's 16-block trader radius —
   `FIXTURE_FAILURE`, record exact anchor/trader distance before departure.
2. Route exhaustion can expire before the return completes — `INCOMPLETE`, record evidence/status
   transitions; do not republish from validation.
3. A hostile may fail to induce natural target acquisition or may die immediately — interruption
   sub-proof `INCOMPLETE`, without converting the principal trade/home result into fake PASS.

**Must happen:** changed live offer, production REQUIRED_TRADE COMMUTE, current-board execution, and
later real sleep produce the two phase passes in order.

**Must not happen:** HOME exists before the trade return; cached initial terms execute; validation
opens intent/directive, calls ranking, steers post-window navigation, trades, sleeps, designates home,
or publishes route failure/exhaustion.

## Verification

- RED-first validation state-machine/fingerprint/authority/lifecycle tests.
- Structural tests proving production has zero V4 witness dependency and tracker/mixins are passive.
- Datapack structure/resource tests and preflight classifier tests.
- `gradlew.bat clean build`; production + validation package audits; exact hashes and test counts.
- Synchronize RFC, test/runtime matrix, environment pin, runbook, progress, and Task-68 report.
