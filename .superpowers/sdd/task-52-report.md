# Task 52 report — shared `MandatoryOwnership` + `V2-DEF-002` repair (`D-VR-084`)

**Status:** `DONE_WITH_CONCERNS` — static-behavioural acceptance after R1 repair; runtime witness
deliberately deferred to the batched V3 campaign per the brief (AV-1: this proof class is not
`CONFIRMED` behaviour).
**Brief:** `.superpowers/sdd/task-52-brief.md` (amended QW-V3-1, 2026-08-20; R1 review 2026-08-20).
**RFC:** `plans/RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md` D-VR-084.
**Authorization:** User (task-52 already authorized; QW-V3-1 lock and QW-V3-2 disposition applied).
**Commit record:** the implementation was committed as `2215c0b` (author SunGodNikka1, 2026-08-20
02:14) on `master`, including this report. No commit was made by the implementing agent in this
session; the working tree was committed externally after the work.

## R1 repair (2026-08-20, review-driven)

**Blocker fixed — `ownedMandatoryRoute` is now the single authority.** The first implementation
factored only `select → of` into `ownedMandatoryRoute`, leaving `scanCovers` checked separately
inside `publishRouteExhaustion`. That split the predicate: the pending-claim publisher asked "a
demand with a modelled precursor", the exhaustion publisher asked "a demand whose intent covers
the precursor" — two policies remaining equivalent by coincidence until a change separates them,
which is the V2-DEF-003 class. The full factored prefix is now:

```text
WorkDemandPolicy.select(...)
    -> GatherRoutePrecursor.scanCovers(demand, currentIntent())
    -> GatherRoutePrecursor.of(demand)
    -> OwnedRoute
```

`publishRouteExhaustion` consumes `ownedMandatoryRoute` and no longer re-checks coverage. One
predicate, two consumers. Structural controls added: `scanCoversLivesInTheFactoredRouteOnly`
(asserts `scanCovers` is in `ownedMandatoryRoute` and absent from `publishRouteExhaustion`), plus
the updated `ProductionRoutePathTest` pinning the same property.

**Verification evidence corrected.** The original report overstated TDD and mutation coverage. The
accurate record is in the RED/mutation section below.

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
| `src/main/java/com/noobk/spmscavenger/goal/GatherResourcesGoal.java` | factored `ownedMandatoryRoute` (select + scanCovers + of) + `OwnedRoute`/`MandatoryRouteIdentity`; pending claim published before `scanClock.claim(now)`; releases on ABANDONED/ROUTE_HANDED_OFF/EXECUTOR_STARTED; generation minted only at `EXECUTOR_STARTED` with a live claim |
| `src/test/java/com/noobk/spmscavenger/activity/MandatoryOwnershipTest.java` | **new** — scenarios 1–12 decision rows + delegation structural control |
| `src/test/java/com/noobk/spmscavenger/activity/MandatoryOwnershipRegistryTest.java` | **new** — anti-self-renewal, generation, release reasons, RET-1 lifetime |
| `src/test/java/com/noobk/spmscavenger/activity/MandatoryOwnershipTemporalSimulationTest.java` | **new** — simulations A and B |
| `src/test/java/com/noobk/spmscavenger/activity/MandatoryOwnershipWiringTest.java` | **new** — structural silent-revert protections incl. R1 single-authority + mint-site controls |
| `src/test/java/com/noobk/spmscavenger/WealthMaskingMandatoryRouteTest.java` | updated assertion to the factored `route.precursor()` seam |
| `src/test/java/com/noobk/spmscavenger/village/trade/ProductionRoutePathTest.java` | updated assertion: precursor + coverage questions live in `ownedMandatoryRoute`, not duplicated |

## Verification commands (all from `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`)

| Command | Result |
| --- | --- |
| `.\gradlew.bat compileTestJava` (before implementation) | `BUILD FAILED` — **RED (compile)**: new test classes reference absent production types |
| `.\gradlew.bat test --tests "com.noobk.spmscavenger.activity.*"` (after impl) | `BUILD SUCCESSFUL` — 50 tests green |
| `.\gradlew.bat test` (full suite) | `BUILD SUCCESSFUL` — **1357 tests, 0 failures/errors/skips** (1354 pre-R1 + 3 new wiring controls) |
| `.\gradlew.bat clean build` (pre-R1) | `BUILD SUCCESSFUL` — `spmscavenger-1.11.0.jar`, SHA-256 `8AE2395B…21925` |
| `.\gradlew.bat test` (post-R1, mutation-reverted tree) | `BUILD SUCCESSFUL` — 1357 tests green |
| JAR inspection (pre-R1) | 4 `activity/MandatoryOwnership*` classes packaged |
| source scan post-R1 | no `TEMP NEGATIVE-CONTROL MUTATION` markers remain |

## RED-before-GREEN — accurate record

The brief names scenarios 3, 5, 11 for RED-before-GREEN. The honest record:

- **Original TDD: PARTIAL.** Pre-implementation RED was a general test compilation failure because
  the production classes did not exist yet. That proves the tests referenced missing types; it does
  NOT demonstrate that scenarios 3 or 11 reject a plausible wrong implementation on their own.
- **Scenario 5 mutation: CONFIRMED post-implementation.** Removing the anti-self-renewal guard
  made `scenario5_sameDemandAfterExpiryDoesNotSelfRenew` fail at line 61, in isolation.
- **Scenarios 3/11: green behavioral assertions present; targeted mutation proof now provided**
  (below) — the brief's demand is satisfied post-implementation, not pre-implementation.

### Mutation matrix — every control actually run in isolation, then reverted

| Control | Mutation | Fails | Result |
| --- | --- | --- | --- |
| NC-1 (generation comparison) | remove anti-self-renewal guard from `publish` | `scenario5_sameDemandAfterExpiryDoesNotSelfRenew` (line 61) | `CONFIRMED` |
| NC-2 (expiry is deletion, not predicate) | `liveClaim` returns the expired claim instead of deleting | `expiredClaimIsDeletedNotPredicateFalse` + `scenario5` | `CONFIRMED` |
| NC-3 (running arm delegates) | `MandatoryOwnership` re-derives the blocking set locally | `negativeControl_runningArmDelegatesToDiscretionaryEligibility` + `scenario9` (cause wrong: MANDATORY_AUTHORITY vs UNKNOWN_ACTIVE) | `CONFIRMED` |
| Scenario 3 (fail-open) | no claim → deny (frozen-demand shape) | `scenario3_demandExistsNobodyClaimsAllowsDiscretionary` + `simulationB_unservableDemandNeverFreezesDiscretionary` | `CONFIRMED` |
| Scenario 11 (runtime-only) | `release` is a no-op | `scenario11_unloadAndServerStopRemoveTheClaim` + `ordinaryReleaseDeletesTheClaim` | `CONFIRMED` |
| NC-5 / P2 (mint per scan) | `mandatoryEpisodeGeneration++` added in `canUse` | `generationIsMintedOnlyAtExecutorStart` + `generationCounterAppearsOnlyOncePlusTheStartIncrement` | `CONFIRMED` |
| NC-8 / P6-P7 (termination mints) | `mandatoryEpisodeGeneration++` added at the ABANDONED release | `generationIsMintedOnlyAtExecutorStart` + `generationCounterAppearsOnlyOncePlusTheStartIncrement` (producer-side; the registry-level abandon test alone cannot catch a producer-side increment — this is why the wiring controls exist) | `CONFIRMED` |

Eight controls were required. Seven were exercised by actual mutation runs; **NC-4 is WAIVED as
STRUCTURALLY SUBSUMED, not mutation-confirmed.** The producer generation counter has exactly one
mint site, asserted structurally by `generationCounterAppearsOnlyOncePlusTheStartIncrement` (exactly
one `mandatoryEpisodeGeneration++` in the whole goal) and `generationIsMintedOnlyAtExecutorStart`
(the increment is inside `start()`, guarded by a live claim). A TTL-specific generation mint is the
same forbidden shape already proven by the scan and abandon mutations (NC-5/P2 and NC-8/P6-P7): any
path that advances generation — TTL, scan, abandon, or handoff — fails the same two wiring tests.
Because the structural contract cannot distinguish one forbidden mint path from another, NC-4 is
covered by the same mechanism and is not separately mutation-run; it is waived on that basis rather
than claimed as mutation-confirmed.

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
| TDD | **PARTIAL** — pre-GREEN RED was compile-only (missing types); scenario-5 mutation `CONFIRMED` post-implementation; scenarios 3/11 targeted mutations now run and `CONFIRMED`; NC-2/3/5/8 also mutation-run (see matrix) |
| SPM stock, no world scan in decision | `CONFIRMED` — `MandatoryOwnership` has no `Level`/world access |
| Gather remains sole `RouteExhaustionEvidence` publisher | `CONFIRMED` — untouched; claim is not exhaustion evidence |
| Single authority (R1) | `CONFIRMED` — `ownedMandatoryRoute` owns select+scanCovers+of; `publishRouteExhaustion` consumes, no duplicate coverage check; structural test |

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
7. **Identity-bound release/start — prerequisite of the first future task that adds a SECOND
   `MandatoryOwnershipClaim` publisher, NOT solved here.** `release(UUID, ReleaseReason)` does not
   identify which claim/owner is being released, and `Gather.start()` treats any live claim for the
   mob as the claim it is taking over. That is valid with exactly one publisher (Gather). When a
   future task wires a second claim publisher — Trade, Mining, or V3 cleanup, whichever proves to be
   first — the shape becomes unsafe: that publisher publishes a claim, an unrelated Gather start
   sees "some live claim", releases the other publisher's claim, and increments Gather's generation.
   Before the second publisher is wired, release/start ownership must carry identity-bound
   authorization (the releasing owner must prove it owns the claim it releases; the starting
   executor must prove the claim belongs to its route). This is a prerequisite of whichever task
   first adds a second publisher; it is deliberately NOT a prerequisite of task-53 (V3-A), which
   adds only a second **consumer** of `MandatoryOwnership`, not a second publisher.
8. **`publish` is not identity-parameterized for the owner** — the claim's `consumerKey`/route
   identity names the *work*, not the *publisher*. Same consequence as concern 7; folded into it.

## V2-DEF-002 status (four-part, `KNOWN_DEFECTS.md`)

```text
Gather-owned observed path      REPAIRED / STATIC-BEHAVIORAL ACCEPT
shared MandatoryOwnership seam  IMPLEMENTED
unwired mandatory publishers    DEFERRED - fail-open coverage, by design
runtime witness                 DEFERRED - batched V3 campaign
```

Not a blanket `REPAIRED`, not `CLOSED`, not runtime-confirmed.
