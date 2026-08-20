# Known defects — open

Defects discovered but deliberately **not** repaired in the commit that found them, each with the
gate that closes it. A defect leaves this file only when its gate has evidence.

---

## V2-DEF-001 — a PlayerMob trade erases pending human-player trade reputation

**Status:** REPAIRED — unit gate green, **runtime gossip check UNVERIFIED**. **Discovered:** P0-2 source review. **Repaired:** 2026-08-17. **Applies to:** shipped vanilla V2, not only
V2-TE. **Severity:** low impact, high principle.

### What happens

`VillagerTradeAdapter` executes without a merchant session, so `villager.getTradingPlayer()` is
`null` when it calls `notifyTrade`. Vanilla `Villager#rewardTradeXp` then does:

```java
this.lastTradedPlayer = this.getTradingPlayer();   // -> null in our path
```

`lastTradedPlayer` is consumed exactly once, in `Villager#customServerAiStep`, at level-up:

```java
if (this.lastTradedPlayer != null && this.level() instanceof ServerLevel level) {
    level.onReputationEvent(ReputationEventType.TRADE, this.lastTradedPlayer, this);
    this.level().broadcastEntityEvent(this, (byte) 14);
    this.lastTradedPlayer = null;
}
```

So if a human trades with a villager that is about to level up, and a PlayerMob trades with the same
villager before that level-up resolves, the human's pending `TRADE` gossip is silently dropped. The
player loses reputation they earned, with no message and no log line.

### Why it is not bundled with P0-2

It changes **shipped** V2 behaviour. Repairing it inside the detached-execution experiment would mix
a behaviour change into a proof about a call path, and a green P0-2 would no longer be evidence
about only the thing P0-2 was testing.

### Contract

> A PlayerMob transaction must not erase pending human-player reputation attribution that it did not
> earn.

Note the contract is about **preservation**, not about the mob earning gossip. The mob is not a
player and must not receive `TRADE` reputation.

### Repair gate — V2-DEF-001

Not a one-line fix. `lastTradedPlayer` is `private` on `Villager` with no public accessor, so the
repair needs an accessor or Mixin seam, and the seam itself needs a test.

| Scenario | Must happen | Must not happen | Evidence |
| --- | --- | --- | --- |
| Player trades, then a PlayerMob trades, then the villager levels up | the player still receives `ReputationEventType.TRADE` | the mob's trade nulls the pending attribution | accessor/Mixin seam test + runtime gossip check |
| PlayerMob trades a villager no player has traded | `lastTradedPlayer` stays `null` | the mob is credited with `TRADE` reputation | seam test |
| Two PlayerMobs trade in sequence | preservation is idempotent | a saved value is restored onto a villager that has since been traded by a player | seam test |

Runtime proof class (AV-1): a gossip read before/after, not a compile.

### Repair as landed

| Piece | File |
| --- | --- |
| read/write seam for the private field | `mixin/VillagerTradeAttributionAccessor` (`@Accessor("lastTradedPlayer")`) |
| the decision, pure and generic | `village/trade/TradeAttributionPolicy` |
| save -> notify -> conditional restore | `TradeAttributionPolicy.notifyPreserving` |
| binding | `VillagerTradeAdapter.preservingAttribution(villager)`, used by `performTrade` |

**Scoped to our own transaction.** A `@Redirect` on vanilla's field write would have fixed it for
every caller in the game, including other mods' session-less trades — a larger behavioural claim than
the evidence supports. Gate SPM-0: the more compatible option wins.

**The mob can never be credited by construction.** `TradeAttributionPolicy` has no way to produce a
value that was not already in the field; it returns `after` when present and `before` otherwise.

| Gate row | Test | Status |
| --- | --- | --- |
| pending human attribution survives a mob trade | `mustHappen_aPendingHumanAttributionSurvivesAMobTrade` | GREEN |
| mob is not credited when nothing was pending | `mustNotHappen_theMobIsCreditedWhenNothingWasPending` | GREEN |
| a saved value never overwrites a newer one | `mustNotHappen_aSavedValueIsRestoredOverANewerOne` | GREEN |
| idempotent across successive mob trades | `mustHappen_preservationIsIdempotentAcrossSuccessiveMobTrades` | GREEN |
| `before` is sampled before, not after, the notify | `mustNotHappen_theSavedValueIsSampledAfterTheNotify` | GREEN |

Negative controls, each run in isolation:

| Control | Broke |
| --- | --- |
| restore unconditionally | newer-value row + not-credited row |
| sample `before` after the notify | survives / idempotent / ordering rows |
| drop the restore (the original defect) | survives / idempotent / ordering rows |

### Why this is not CLOSED

Two things remain runtime facts, not compile facts:

1. `@Accessor("lastTradedPlayer")` is a **string** target — invisible to the compiler
   (api-break-detection Shape 4). The field name is `CONFIRMED` from the mapped 1.21.1 jar, and a
   failed accessor application is load-time loud rather than silent, but "the mixin applied" is
   proven at startup, not at build.
2. The gate's own stated proof class is a **gossip read before/after** a real
   player-trade -> mob-trade -> level-up sequence. Unit tests prove the decision; they cannot prove
   `ReputationEventType.TRADE` actually reaches the player.

Until both are observed, this defect is repaired, not closed.

---

## V2-DEF-002 — discretionary activity can displace *pending* mandatory progression

**Status (four-part, 2026-08-20 — task-52):** not a blanket `REPAIRED`. Recorded separately per the
task-52 brief and Gate AV-1:

```text
Gather-owned observed path      REPAIRED / STATIC-BEHAVIORAL ACCEPT
shared MandatoryOwnership seam  IMPLEMENTED
unwired mandatory publishers    DEFERRED - fail-open coverage, by design
runtime witness                 DEFERRED - batched V3 campaign
```

**Discovered:** step 7B runtime, 2026-08-18. **Applies to:** Opinion / discretionary
director, not Trade Everything. **Severity:** low frequency, high surprise.

**Repair summary (task-52 / `D-VR-084`, 2026-08-20).** The shared claim-based `MandatoryOwnership`
authority is implemented and wired: `DiscretionaryActivityDirector` consumes
`MandatoryOwnership.evaluate` (running half delegated to `DiscretionaryEligibility`, pending half
consuming the registry's live claim), `GatherResourcesGoal` is the one wired publisher
(`ownedMandatoryRoute` factored and shared with `publishRouteExhaustion`), and the pending claim is
published before `scanClock.claim(now)` so the scan-cadence gap cannot admit EXPLORE. The four states
and the anti-self-renewal invariant (a claim may never be refreshed by demand existence) are
structural. Automated behavioural acceptance: 12 scenarios + 2 temporal simulations + producer-side
controls, 50 new tests, full suite **1354 tests, 0 failures**. The second repair-gate row below
(unservable demand) is enforced by simulation B (EXPLORE still legal at T400).

**The third status line is the honest one and must not be dropped because the suite is green:**
task-52 wires **one** publisher. Trade, Mining and future V3 cleanup remain unwired, and for their
episodes the pending side fails open exactly as before — scenario 10 behaving as designed, but the
defect's general form survives this slice. The runtime witness (pending claim active → no expedition;
abandoned/expired → discretionary movement resumes) is folded into the batched V3 campaign; no
dedicated session is scheduled.

### What was observed

A PlayerMob with an unresolved iron-pickaxe progression demand walked out of its own village:

```
plans=0 (TE 0)  revals=0  trades=0     logs=320  emeralds=0     objective=Exploring
```

### The sequence

```
iron-pickaxe progression demand exists
    trade not admitted yet (no candidate produced)
    gather has published no exhaustion evidence yet
        v
scheduler sees no ACTIVE deliberate work
        v
DiscretionaryActivityDirector: EXPLORE is available -> EXPLORE wins
        v
ExploringGoal (P8) starts; may travel ~150 blocks
        v
mob leaves the 16-block trade-discovery radius
        v
TradeWithVillagerGoal (P3) can never admit a candidate, so it can never preempt
```

`TradeWithVillagerGoal` outranks `ExploringGoal` by priority, but priority only helps once admission
has produced a candidate. **Between "a demand exists" and "a route owner became executable" the mob
looks idle to the discretionary layer**, and that window is long enough for a 150-block expedition.

### The gap

The director arbitrates among `EXPLORE`, `REST` and `SOCIAL`. It has no notion of:

> mandatory progression work exists, but its route owner has not yet become executable

The standing rule is *preference affects choice; preference does not create permission*. This is the
adjacent one that was never stated: **discretionary activity must not displace pending mandatory
progression**, not merely active progression.

### Deliberately not fixed here

A progression-pending admission blocker for discretionary activities is a real change to the
Opinion/discretionary layer, and step 7B is testing Q1/Q2 mutation semantics. Repairing it inside
that scenario would mix a behaviour change into an unrelated proof — the same reason V2-DEF-001 was
not repaired inside the P0-2 probe.

The step-7B fixture instead suppresses ordinary discretionary exploration **only until its first
Trade Everything plan exists**, saving and restoring the exact previous `cfg.exploring` value in
memory. That makes the scenario deterministic without changing production trade radius, exploration
policy, or V2-C.

### Repair gate — V2-DEF-002

| Scenario | Must happen | Must not happen | Evidence |
| --- | --- | --- | --- |
| Progression demand exists, route owner not yet executable | discretionary EXPLORE is refused admission | a ~150-block expedition starts while the mob has unresolved mandatory work | Gather-owned path `STATIC-BEHAVIORAL ACCEPT` (task-52): pending claim published pre-scan-clock, `MandatoryOwnership.evaluate` denies; runtime witness deferred to V3 campaign |
| Progression demand genuinely unsatisfiable (no route at all) | discretionary activity resumes normally | the mob stands idle for ever guarding a demand it cannot serve | simulation B: no claim → EXPLORE legal, still legal at T400 (test) |
| No progression demand | today's behaviour unchanged | discretionary activity is suppressed by a blocker that never clears | scenario 3/10 `CONFIRMED` (test); full 1354-test suite |

Runtime proof class (AV-1): an observed objective trace, not a compile. The second row is the one
that makes this non-trivial — a naive blocker converts a wandering mob into a frozen one. The
automated rows above are static-behavioural acceptance, not runtime `CONFIRMED`; the runtime witness
is deferred by decision to the batched V3 campaign.

---

## V2-DEF-003 — required gather resources were not consumer-accurate

**Status:** REPAIRED — **runtime CONFIRMED** for the ownership half (after the repair the runtime
showed `latestDispositionCause=NONE`, no `GatherResourcesGoal` owning mandatory authority). The
end-to-end convergence gate remained failing, which led to `V2-DEF-003b` below. **Discovered:** step 7B runtime,
2026-08-19. **Applies to:** shipped gather/progression policy, not Trade Everything.

### The stall

```
demand     iron_ingot x3      projection  iron_pickaxe
B_FUNDING  10                 free slots  1               plans 0
Opinion    MANDATORY_AUTHORITY, discretionaryBlocker = GatherResourcesGoal -> SCAVENGE_WORK
```

A mob carrying 3 sticks for a 2-stick iron pickaxe, 320 logs and no iron kept **LOGS** in its
required set, because `GatherIntentPolicy` made logs mandatory whenever
`wantsPickUpgrade || wantsAxeUpgrade`. `ToolTierPolicy.cobbleBelowTarget` was broad in the same way:
any wanted upgrade plus a target tier of stone-or-better kept **COBBLESTONE** mandatory against a
generic stock target, regardless of whether the active consumer had already passed the stone step.

### Why it could not clear itself

`GatherRoutePrecursor` correctly refuses to read *"scan wanted iron, found log"* as iron exhaustion.
So an irrelevant log in the required set let the scan keep succeeding at something no consumer
wanted, **RAW_IRON exhaustion evidence was never published**, and V2-C could never legally hand
route ownership to trade. Ten funded trades were available and the mob never planned one.

### The contradiction

`ScavengerCrafting.towardConsumerTool` already asked the right question — planks and logs matter
only while the recipe's **stick requirement** is short. `GatherIntentPolicy` asked a broader one.
Two independent readings of the same recipe, disagreeing.

### Repair

One shared consumer acquisition frontier, living beside the `toward*` methods so it reuses their
constants, specs and tier dispatch:

| Piece | Meaning |
| --- | --- |
| `ScavengerCrafting.toolUpgradeNeedsLogs` | logs are an acquisition only when the craft chain cannot make the missing sticks from held planks or logs |
| `ScavengerCrafting.toolUpgradeNeedsCobble` | cobble is required only while the **stone step itself** is active |
| `ScavengerCrafting.sticksNeedLogs` | the pure rule, count-explicit |

`RAW_IRON` and `DIAMOND` were left untouched — those already came from consumer-derived deficits and
were never the broad ones. `ToolTierPolicy.cobbleBelowTarget` is no longer consulted by gather.

**NEED/WEALTH preserved:** a stock target beyond the consumer's need stays an appetite and never
enters `requiredResources`.

### Gate

| Scenario | Required | Not required | Test |
| --- | --- | --- | --- |
| iron wanted, sticks sufficient, iron short | `RAW_IRON` | `LOGS`, `COBBLESTONE` | `mustHappen_theIronFrontierAsksForRawIronAndNothingElse` |
| sticks short, planks or logs held | — | `LOGS` | `mustNotHappen_logsAreRequiredWhileTheCraftChainCanStillMakeSticks` |
| sticks short, nothing to make them from | `LOGS` | — | `mustHappen_logsAreRequiredWhenNothingCanMakeSticks` |
| stone step active, cobble short | `COBBLESTONE` | — | `mustHappen_cobbleIsRequiredWhileTheStoneStepIsActive` |
| already stone, pursuing iron | — | `COBBLESTONE` | `mustNotHappen_aStockTargetKeepsCobbleMandatoryPastTheStoneStep` |
| all inputs held, craft blocked by capacity | nothing | everything | `mustNotHappen_aFullBackpackInventsAGatherRequirement` |
| unrelated log nearby | `RAW_IRON` | `LOGS` | `mustNotHappen_anIrrelevantLogKeepsTheIronRouteAlive` |

Negative controls: restoring `wantsPickUpgrade -> LOGS` breaks four tests; restoring
`cobbleBelowTarget` breaks four; making `sticksNeedLogs` ignore the craft chain breaks the pure rule.

### Why it is not CLOSED

Item **tags** are not populated by `Bootstrap.bootStrap()`, so plank/log counts read as zero in unit
tests and the "crafting owns the precursor" branch is unreachable through a container fixture. It is
proved on the pure helper instead. The end-to-end claim — that the mob now publishes RAW_IRON
exhaustion and hands the route to trade — is a **runtime** fact and remains unproved until step 7B
runs.

One pre-existing test, `craftReadySuppressesAnotherGatherTrip`, was asserting `hasDemand() == true`
for a mob holding every ingredient. It was encoding the defect and has been corrected; the
suppression property it existed for is now proved separately with a demand that genuinely exists.

---

## V2-DEF-003b — optional wealth masked a mandatory route's conclusion

**Status:** REPAIRED — **runtime CONFIRMED**. The step-7B run showed the full knowledge chain:
`GATHER PUBLISHED exhaustion for minecraft:iron_ingot` → `ROUTE iron_ingot INFEASIBLE -> trade may
displace` → `PLAN #1 TE armorer` → `REVAL #1 OK`. Two further control-flow blockers were found and
fixed before that (publish was unreachable when another resource won selection; observation was
pruned by the nearest-N selection buffer). The transaction then failed `NO_ROOM`, which is
`V2-DEF-003c`. **Discovered:** step 7B runtime,
2026-08-19, after V2-DEF-003 cleared the ownership half.

### Why the stall survived the first repair

`ResourceWealthPolicy.LOGS` saturates at 32 but keeps `wealthFactor = 0.05`, so with the runtime's
`greed = 0.1, wealthLevel = 0.1` a saturated log still carries positive utility.
`GatherIntent.hasDemand()` correctly refuses to start a scan for that alone — but once a mandatory
RAW_IRON demand had started one, `GatherCandidatePolicy` admitted any candidate with
`intent.wants(resource, cost) > 0`:

```
RAW_IRON mandatory, none in radius
saturated LOG wealth candidate in radius
  -> findTarget returns the LOG
  -> scan is not NO_CANDIDATES_IN_RADIUS
  -> RAW_IRON exhaustion never published
  -> ExistingRouteFeasibility stays UNKNOWN, trade can never displace
```

Two lossy collapses in the same direction: `isPassOneCandidate` merged need and wealth into one
boolean, and the bounded sweep merged the whole scan into `target != null`.

### The invariant

> Optional opportunity may affect **target selection**, but may not prevent a mandatory consumer
> route from reaching its **own** factual conclusion.

Wealth keeps noticing and acquiring logs. It stops being able to answer a question asked about iron.

### Repair — observational, one scan

| Piece | Meaning |
| --- | --- |
| `GatherCandidatePolicy.familyOf(BlockState)` | which resource family a block is, **independent of intent**; takes no intent parameter, which is the structural form of the guarantee |
| `GatherResourcesGoal.lastScanFamilies` | families the single sweep turned up, recorded at **pass one** and cleared per scan |
| `publishRouteExhaustion` | asks about the demand's own precursor via `GatherRoutePrecursor.of`, not the scan's overall verdict |

No rescans. **Gather remains the only publisher** — trade neither publishes nor infers exhaustion.

Recording at pass one preserves the old `CANDIDATES_ALL_REJECTED_PROTECTION` behaviour: an iron
candidate that protection rejected still marks RAW_IRON present, so the route is not called
exhausted.

### Gate

| Scenario | Conclusion |
| --- | --- |
| RAW_IRON required, no iron, wealth log nearby | RAW_IRON EMPTY -> exhaustion may publish |
| RAW_IRON required, iron nearby, wealth log nearby | RAW_IRON FOUND -> no exhaustion |
| RAW_IRON required, iron present but protection-rejected | not EMPTY -> no exhaustion |
| wealth-only saturated logs | activation unchanged |
| wealth-only unsaturated logs | normal wealth gather still works |
| mandatory iron + optional log | the log cannot stand in as success for iron |

Negative controls: restoring the whole-scan verdict breaks two tests; removing the family record
breaks the scoping test; adding an intent parameter to `familyOf` fails to compile.

### Why it is not CLOSED

Three things are not provable in a unit run and are stated rather than implied:

1. **`BlockTags.LOGS` is empty** under `Bootstrap.bootStrap()`, so `familyOf(OAK_LOG)` reads empty
   here. Ore and stone families are identity-based and are asserted; the log family is not.
2. **"Saturated wealth alone must not activate a scan" is not constructible** — wealth contexts are
   built for every resource, and coal/iron/cobblestone are unsaturated at zero held, so
   `hasDemand()` is true regardless of the logs. What is asserted instead is that this repair did
   not touch activation at all.
3. The end-to-end claim — RAW_IRON exhaustion published, `ExistingRouteFeasibility` flipping to
   INFEASIBLE, trade receiving the handoff — is **runtime**.

### Diagnostics added alongside

Passive, void, recording-gated, deduplicated: `TradeRuntimeObserver.gatherExhaustionGate` reports
which of the publish preconditions said no, and `routeFeasibility` reports the tri-state trade
actually reads. The `+16 logs` observation is **not** attributed to wealth: `te3:cleanup` only killed
`te3`-tagged entities, so dropped items from a previous run could be picked up by a later fixture
mob. Cleanup now removes loose item and XP entities, and `watch report` prints `greed`,
`wealthLevel`, `gatherSearchRadius` and `exploring` so the next run can distinguish the two causes
instead of guessing.

---

## V2-DEF-003c — publishing a handoff is not performing one

**Status:** REPAIRED — unit gate green; **`V2-DEF-003c-R1` runtime CONFIRMED** on 2026-08-19.
**Discovered:** step 7B runtime, 2026-08-19, immediately after the knowledge handoff started
working.

### The run

```
GATHER PUBLISHED exhaustion for minecraft:iron_ingot (SEARCH_COMPLETED_EMPTY)
ROUTE  iron_ingot INFEASIBLE -> trade may displace
PLAN   #1 TE armorer  Q1: 22 oak_log -> 1 emerald
REVAL  #1 Q2: 22 oak_log -> 1 emerald  OK
TRADE  #1 NO_ROOM   logs 324->324   em 0->0   pick 0->0
```

The fixture seeds 320 logs and exactly one free slot for the first emerald. The mob arrived at the
trade holding **324**.

### What actually happened

`canUse` published exhaustion and then, in the same pass, returned `true` for an unrelated wealth
log. Gather and trade are both installed at **priority 3** in the deliberate-work band, so once
gather owned the slot trade could not preempt it. The mob chopped a tree, the four logs took the one
reserved slot, and the emerald had nowhere to land.

> Knowledge handoff succeeded. **Scheduling handoff did not.**

Spending 22 logs from a 64-stack does not empty a slot, so the capacity failure is a symptom. The
defect is that optional work outranked a handoff that had already been declared.

### Repair

`MandatoryHandoffPolicy.yieldsToHandoff(mandatoryPrecursor, mandatoryFoundInSweep, selectedFamily,
consecutiveYields)` — pure, so the combinations are unit tests. Gather declines the deliberate-work
slot when a mandatory route was declared exhausted this sweep and the chosen target does not serve
it.

**Bounded at 3 consecutive scans.** An unbounded yield would trade one stall for a quieter one — no
merchant in range, no affordable quote, and a mob standing beside a tree it is not allowed to chop.
That is the assign→refuse→assign churn shape (RET-1c), so the cap exists rather than being assumed
unnecessary. The counter resets on any non-yield, making it a window rather than a lifetime budget.

Wealth gathering is untouched when there is no mandatory demand, when the mandatory resource was
found in radius, or when the selection serves the mandatory route itself.

### Gate

| Scenario | Yields? | Test |
| --- | --- | --- |
| iron route exhausted, wealth log selected | yes | `mustHappen_unrelatedWealthYieldsToAPendingHandoff` |
| iron found in sweep | no | `mustNotHappen_gatherYieldsWhileItsOwnRouteIsAlive` |
| selection serves the mandatory route | no | `mustNotHappen_gatherYieldsWorkThatServesTheMandatoryRoute` |
| no mandatory demand at all | no | `mustNotHappen_pureWealthWorkYieldsToNothing` |
| nobody takes the handoff | no, after 3 scans | `mustNotHappen_anUntakenHandoffFreezesGatherForever` |
| counter semantics | consecutive, not cumulative | `mustHappen_theYieldCounterIsConsecutiveNotCumulative` |

Controls: never yielding breaks two; yielding while the route is alive breaks one; removing the cap
breaks the freeze test; removing the counter reset breaks the semantics test.

### Not fixed by giving the fixture another slot

Deliberately. A second free slot would have made the runtime pass while hiding what the run found.

### R1 — the first repair reconstructed the authority

The initial `MandatoryHandoffPolicy` claimed to answer *"a handoff was published; should gather
yield?"* while independently **re-deriving** whether one ought to exist, from precursor + sweep
result + selection. It omitted `GatherRoutePrecursor.scanCovers`, which `publishRouteExhaustion`
enforces, so:

```
demand IRON_INGOT, and this scan did NOT cover RAW_IRON
  publishRouteExhaustion  -> refuses -> NO evidence
  reconstructed inference -> YIELD

  Gather: "I am yielding to trade."
  Trade:  reads RouteExhaustionEvidence, finds none, declines.
```

A deterministic self-stall in which both halves are individually correct — the duplicated-authority
pattern this workstream keeps removing, reintroduced by the repair for it.

**Publication is now the single authority.** `publishRouteExhaustion` returns
`Optional<HandoffPublication>`, present only where `RouteExhaustionEvidence.publish` actually ran,
and the scheduler consumes that value. Every publisher precondition is inherited because there is no
second path to a yield.

Two further problems in that first attempt:

- **A naked `int handoffYields`** was not attached to a consumer or material, so one handoff could
  inherit another's part-spent budget. Replaced by `YieldWindow(consumer, material, openedAt)`; a
  new episode opens a new window by construction rather than by remembering to reset a counter.
- **`MAX_CONSECUTIVE_YIELDS = 3`** at a 60-tick scan interval was ~180 ticks, while
  `TradeCandidateRound.EXHAUSTED_ROUND_COOLDOWN_TICKS` is 200 — gather's concession could expire
  *before trade was legally allowed to retry*. Now derived as `EXHAUSTED_ROUND_COOLDOWN_TICKS * 2`,
  with a test asserting the **relationship** rather than the number, so re-tuning either constant is
  caught here instead of in a runtime session.

### The bound is implementation policy, not the architecture

The real event is *"another route claimed or refused this handoff"*, which gather cannot observe
without coupling to trade's internals. The timer stands in for it and is documented as such. The
protocol being locked is:

```
gather route --publishes exhaustion--> HANDOFF AVAILABLE
                                         |
                   trade claims it ------+------ trade cannot serve it
                           |                            |
                mandatory progression          optional work may resume
```

Success is not "the fixture reaches the pickaxe". It is that **the same authoritative publication
that changes route feasibility also controls scheduling ownership**, with neither side reconstructing
what the other meant.

### V2-DEF-003c-R1 — Step-7A autonomous runtime confirmation

**Evidence:** user-captured `[TE3] step-7A autonomous readout`, 2026-08-19. The observer recorded
`plans=13 (TE 12)`, `revals=13`, `trades=13`, and `episodes=0`.

The causal handoff was observed in the required order:

```text
ROUTE iron_ingot UNKNOWN/FEASIBLE -> gather keeps ownership
  -> GATHER PUBLISHED exhaustion for minecraft:iron_ingot (SEARCH_COMPLETED_EMPTY)
  -> GATHER YIELDING deliberate-work slot to the published handoff
  -> ROUTE iron_ingot INFEASIBLE -> trade may displace
  -> PLAN #1 TE armorer Q1: 22 oak_log -> 1 emerald
  -> REVAL #1 Q2: 22 oak_log -> 1 emerald OK
  -> TRADE #1 TRADED logs 320->298 em 0->1 pick 0->0
```

This is direct runtime evidence for both halves of the repair: trade did **not** displace the gather
route while its status was `UNKNOWN/FEASIBLE`, and the published exhaustion then caused gather to
yield the shared deliberate-work slot before the first Trade Everything transaction. The exact
`320->298` log delta also proves the former `NO_ROOM` sequence did not recur on the first handoff.

The same uninterrupted autonomous chain then completed:

```text
12 Trade Everything funding sells
  -> 12 emeralds
  -> vanilla Toolsmith BUY: 12 emerald -> 1 iron_pickaxe
  -> backpack iron_pickaxe = 1
  -> routeEvidence tracked = 0
```

Final captured inventory was `logs=56`, `emeralds=0`, `iron_pickaxe(pack)=1`; the main hand remained
`1x iron_axe`. `routeEvidence tracked=0` confirms the scoped route evidence did not remain retained
after convergence. `episodes=0` is preserved as an observed counter, but this Step-7A scenario does
not promote any relationship-learning claim.

**Must happen — PASS:** one authoritative empty gather scan publishes the iron-ingot exhaustion,
gather yields, Trade Everything performs all twelve funding sells, and the vanilla Toolsmith purchase
satisfies the iron-pickaxe consumer.

**Must not happen — PASS:** trade does not steal ownership while gather is still
`UNKNOWN/FEASIBLE`; unrelated gather work does not consume the result slot after publication; the
chain does not stop at funding; route evidence does not survive completion.
