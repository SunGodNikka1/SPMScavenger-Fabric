# Task 52 report — shared `MandatoryOwnership` + `V2-DEF-002` repair (`D-VR-084`)

**Status:** `DONE` — static-behavioural acceptance; runtime witness deliberately deferred to the
batched V3 campaign per the brief (AV-1: this proof class is not `CONFIRMED` behaviour).
**Brief:** `.superpowers/sdd/task-52-brief.md` (amended QW-V3-1, 2026-08-20).
**RFC:** `plans/RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md` D-VR-084.
**Authorization:** User (task-52 already authorized; QW-V3-1 lock and QW-V3-2 disposition applied).

## Files changed

| File | Role |
| --- | --- |
| `src/main/java/com/noobk/spmscavenger/activity/MandatoryOwnershipClaim.java` | **new** — immutable claim record (mobId, consumerKey, routeIdentity, generation, openedAt, expiresAt) |
| `src/main/java/com/noobk/spmscavenger/activity/MandatoryOwnershipRegistry.java` | **new** — runtime-only per-mob slot (claim + anti-self-renewal terminal); publish/release/liveClaim/removePermanently/shutdown; RET-1 wired |
| `src/main/java/com/noobk/spmscavenger/activity/MandatoryOwnership.java` | **new** — pure decision: combat → pending claim → running arm (delegated to `DiscretionaryEligibility`) |
| `src/main/java/com/noobk/spmscavenger/opinion/DiscretionaryEligibility.java` | `VILLAGE_TRADE` joins `blocksDiscretionaryChoice` (D-VR-082-A1 item 2) |
| `src/main/java/com/noobk/spmscavenger/opinion/InvalidationCause.java` | add `MANDATORY_PENDING_CLAIM` |
| `src/main/java/com/noobk/spmscavenger/opinion/DiscretionaryActivityDirector.java` | consumes `MandatoryOwnership.evaluate` instead of `DiscretionaryEligibility` directly (requirement 9) |
| `src/main/java/com/noobk/spmscavenger/SpmScavenger.java` | eviction: `release(ORDINARY)` on unload, `removePermanently` on destroy/death, `shutdownServerState` on server stop |
| `src/main/java/com/noobk/spmscavenger/goal/GatherResourcesGoal.java` | factored `ownedMandatoryRoute` + `OwnedRoute`/`MandatoryRouteIdentity`; pending claim published before `scanClock.claim(now)`; releases on ABANDONED/ROUTE_HANDED_OFF/EXECUTOR_STARTED; generation minted only at `EXECUTOR_STARTED` with a live claim |
| `src/test/java/com/noobk/spmscavenger/activity/MandatoryOwnershipTest.java` | **new** — scenarios 1–12 decision rows + delegation structural control |
| `src/test/java/com/noobk/spmscavenger/activity/MandatoryOwnershipRegistryTest.java` | **new** — anti-self-renewal, generation, release reasons, RET-1 lifetime |
| `src/test/java/com/noobk/spmscavenger/activity/MandatoryOwnershipTemporalSimulationTest.java` | **new** — simulations A and B |
| `src/test/java/com/noobk/spmscavenger/activity/MandatoryOwnershipWiringTest.java` | **new** — structural silent-revert protections |
| `src/test/java/com/noobk/spmscavenger/WealthMaskingMandatoryRouteTest.java` | updated assertion to the factored `route.precursor()` seam |
| `src/test/java/com/noobk/spmscavenger/village/trade/ProductionRoutePathTest.java` | updated assertion: precursor question moved to `ownedMandatoryRoute` |

## Verification commands (all from `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`)

| Command | Result |
| --- | --- |
| `.\gradlew.bat compileTestJava` (before implementation) | `BUILD FAILED` — **RED captured**: `MandatoryOwnership` / `MandatoryOwnershipRegistry` / `MandatoryOwnershipClaim` missing, all new test classes fail to compile |
| `.\gradlew.bat test --tests "com.noobk.spmscavenger.activity.*"` (after impl) | `BUILD SUCCESSFUL` — 50 tests green |
| `.\gradlew.bat test` (full suite) | `BUILD SUCCESSFUL` — **1354 tests, 0 failures/errors/skips** |
| `.\gradlew.bat clean build` | `BUILD SUCCESSFUL` — artifact `build/libs/spmscavenger-1.11.0.jar` |
| JAR inspection | 4 `activity/MandatoryOwnership*` classes packaged; `DiscretionaryEligibility` packaged |
| artifact SHA-256 | `8AE2395B12FFDA7F02C636D0B0B87731C86788F42662DBDA781F9107E7F21925` |

## RED-before-GREEN capture (brief requires scenarios 3, 5, 11)

RED was the module compile failure of the new test classes against the absent implementation
(`compileTestJava` → `BUILD FAILED`, captured above). After implementation, the load-bearing
**scenario 5** negative control was additionally verified by mutation in isolation:

| Mutation | Result |
| --- | --- |
| remove the anti-self-renewal guard from `publish` | `scenario5_sameDemandAfterExpiryDoesNotSelfRenew` **FAILED** at `MandatoryOwnershipRegistryTest.java:61` (republish accepted — the frozen-demand freeze returns) |
| revert | full suite green again |

Scenario 3 and scenario 11 were asserted in the suite and in isolated class runs:
`simulationB_unservableDemandNeverFreezesDiscretionary` (EXPLORE legal at T3 and T400 — the
unservable-demand gate) and `scenario11_unloadAndServerStopRemoveTheClaim` (runtime claim
disappears) pass in isolation.

## Twelve scenarios + two simulations — all green

Scenarios 1–12, 6a/6b/6c, simulations A/B, and the producer-side controls are recorded with
must/must-not rows in `docs/porting/TEST_MATRIX.md` (task-52 section). Evidence labels:
`CONFIRMED` (unit/structural/build). Runtime rows remain `UNVERIFIED` by design.

## Mechanism named (brief requirement)

**Generation is minted at release, never at publish, and only ONE release reason mints it.**
Production call sites:

- `GatherResourcesGoal.start()` — guarded by a live claim lookup:
  `if (MandatoryOwnershipRegistry.liveClaim(mob.getUUID(), now).isPresent()) { mandatoryEpisodeGeneration++; release(EXECUTOR_STARTED); }`
  The live-claim guard is load-bearing: a wealth-only or cooperative start (no claim published)
  must NOT mint, or an unrelated start would reauthorize an abandoned mandatory identity (P6).
- `GatherResourcesGoal.canUse()` terminal paths — `release(ROUTE_HANDED_OFF)` (handoff/yield) and
  `release(ABANDONED)` (nothing selected, no handoff); neither mints.
- `GatherResourcesGoal.stop()` — `release(ORDINARY)`; defensive, no mint.

The registry never mints: it stores the terminal `(consumerKey, routeIdentity, generation)` of the
claim that last occupied the slot, and `publish` refuses when the same route carries a generation
`<=` remembered. Expiry and release delete the **claim half** and retain the **terminal half**
(that retention is the anti-self-renewal memory); `removePermanently` (death/destroy) and server
stop clear the whole slot (RET-1 bound).

**Route identity is stable semantic facts only (QW-V3-1).** For Gather,
`MandatoryRouteIdentity(materialKey, precursor)` — never the derived deficit, scan results,
timestamps, candidate positions, or evidence epochs. A different identity is a different pair,
accepted outright as a different episode; a same identity with merely fresher observation is
refused (scenario 6c).

## Self-review mapped to the brief

| Requirement | Status |
| --- | --- |
| 1 demand never creates authority; a claim does | `CONFIRMED` — four states; scenario 3/simulation B |
| 2 running half consumed, never reimplemented | `CONFIRMED` — delegation structural test |
| 3 claim never refreshed by demand existence | `CONFIRMED` — scenario 5 + mutation-verified negative control |
| 4 new claim needs new justification | `CONFIRMED` — 6a/6b/6c; only EXECUTOR_STARTED mints |
| 5 one canonical pending owner per mob | `CONFIRMED` — single slot, replace-never-append |
| 6 runtime-only, never persisted | `CONFIRMED` — not in `PerMobSavedData`; wiring test asserts absence |
| 7 pending fails open, running fails closed | `CONFIRMED` — scenario 10 + UNKNOWN_ACTIVE fail-closed |
| 8 VILLAGE_TRADE blocks discretionary | `CONFIRMED` — scenario 8 + structural |
| 9 director gains input, not policy | `CONFIRMED` — single-line seam swap; no scoring/utility change |
| 10 RET-1 named key/bound/eviction | `CONFIRMED` — mobId key, one slot, expiry deletion, 3+ production call sites |
| TDD | `CONFIRMED` — RED compile captured; scenario-5 mutation verified; isolation runs |
| SPM stock, no world scan in decision | `CONFIRMED` — `MandatoryOwnership` has no `Level`/world access |
| Gather remains sole `RouteExhaustionEvidence` publisher | `CONFIRMED` — untouched; claim is not exhaustion evidence |

## Concerns

1. **Alternating-key limit (recorded, not fixed).** Two different canonical route identities
   alternating could in principle keep a mob blocked (each is a different pair, accepted outright).
   Two different consumer keys are two different pieces of real work; accepted for gen-1 per the
   brief, restated here.
2. **Deliberate fail-open of the pending half.** Trade, Mining, and future V3 cleanup remain
   unwired publishers. For their episodes the pending side fails open exactly as before — scenario
   10 behaving as designed. The defect's general form survives this slice; status line 3 in
   `KNOWN_DEFECTS.md` records this and must not be dropped.
3. **Reacquisition after handoff/abandonment is not granted by task-52.** An identity that Gather
   abandons or hands off produces no further accepted claim (pending fails open). A future
   authoritative-transition mechanism is a separate, later decision.
4. **Generation guarded by live-claim lookup in `start()`** is the chosen minting mechanism (the
   brief demanded the mechanism be named and argued). The guard prevents a wealth/cooperative start
   from minting; the cost is one registry read per `start()`, which is O(1).
5. **`trackedClaimCount()`** counts slots holding a claim half (live or not yet expiry-read) — a
   diagnostic; expiry-read nulls the claim so the count drops after `liveClaim` observes expiry.
6. **Runtime witness deferred by decision** (no dedicated session): pending claim active → no
   expedition; abandoned/expired → discretionary movement resumes. Folded into the batched V3
   campaign per the brief; do not schedule a standalone session.

## V2-DEF-002 status (four-part, `KNOWN_DEFECTS.md`)

```text
Gather-owned observed path      REPAIRED / STATIC-BEHAVIORAL ACCEPT
shared MandatoryOwnership seam  IMPLEMENTED
unwired mandatory publishers    DEFERRED - fail-open coverage, by design
runtime witness                 DEFERRED - batched V3 campaign
```

Not a blanket `REPAIRED`, not `CLOSED`, not runtime-confirmed.
