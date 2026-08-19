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

**Status:** OPEN, architecture finding. **Discovered:** step 7B runtime, 2026-08-18. **Applies to:**
Opinion / discretionary director, not Trade Everything. **Severity:** low frequency, high surprise.

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
| Progression demand exists, route owner not yet executable | discretionary EXPLORE is refused admission | a ~150-block expedition starts while the mob has unresolved mandatory work | `OPEN` |
| Progression demand genuinely unsatisfiable (no route at all) | discretionary activity resumes normally | the mob stands idle for ever guarding a demand it cannot serve | `OPEN` |
| No progression demand | today's behaviour unchanged | discretionary activity is suppressed by a blocker that never clears | `OPEN` |

Runtime proof class (AV-1): an observed objective trace, not a compile. The second row is the one
that makes this non-trivial — a naive blocker converts a wandering mob into a frozen one.

---

## V2-DEF-003 — required gather resources were not consumer-accurate

**Status:** REPAIRED — unit gate green, **runtime UNVERIFIED**. **Discovered:** step 7B runtime,
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
