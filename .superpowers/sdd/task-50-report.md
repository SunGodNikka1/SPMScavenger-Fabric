# Task 50 report: V2-D — transient `TradeChainPlan` (SELL → BUY)

**Status:** `DONE` — pure economic state machine, static complete. No movement, no transaction.

## Files changed

| File | Role |
| --- | --- |
| `village/trade/TradeChainPlan.java` | transient plan: consumer, desired output, quantity, created/expiry, step |
| `village/trade/TradeChainPolicy.java` | pure `evaluate(plan, ChainFacts, tick)` → step · `requiredSellUses` · `sellBlocked` · termination |
| `village/trade/SellExpendabilityPolicy.java` | disposable units, delegating to `FuelExpendability` |
| `test/…/TradeChainPolicyTest.java` | 17 tests |

| Command | Result |
| --- | --- |
| `.\gradlew.bat clean build` | `BUILD SUCCESSFUL` — **1015 tests, 0 failures/errors** (was 998) |

## Requirement coverage

| # | Requirement | How |
| --- | --- | --- |
| 1 | chain exists only for an external consumer | only constructor is `forConsumer`; `consumerStillWants` checked first |
| 2 | no independent emerald appetite | `requiredSellUses` derives from one bounded deficit; probe 3 |
| 3 | `consumerKey` survives every step | `at(Step)` rebuilds identity unchanged; asserted across three steps |
| 4 | `desiredOutput` survives every step | same |
| 5 | villager/offer/path are not identity | structural ban on `Villager`, `OfferSnapshot`, `offerIndex`, `BlockPos`, `Path`, `anchor` |
| 6 | disposable computed fresh | `ChainFacts` is passed per evaluation; no cache |
| 7 | SPM mutates the backpack → revalidate | shrinking stock reports `sellBlocked`, chain stays active |
| 8 | hard expiry | `DEFAULT_LIFETIME_TICKS`, constructor rejects a non-expiring chain |
| 9 | target from elsewhere terminates | `TARGET_OBTAINED_ELSEWHERE` before any funding logic |
| 10 | save/reload closes it neutrally | **not persisted** — structural ban on `SavedData`/`CompoundTag` |
| 11 | failed SELL does not advance | advancement reads emeralds held; ten failed attempts leave the step and count unchanged |
| 12 | sell only what the bounded BUY needs | `ceil(deficit / perSell)`; probe 1 |

**Requirement 12, as implemented:** needs 9 · holds 7 · sell pays 1 · owns 64 disposable wheat →
`requiredSellUses = 2`. Not 64. *Disposable means permitted to spend, not desirable to spend.*

## The three stupid behaviours

Each is unreachable by construction, and each probe below breaks the build when its guard is removed.

| Probe | Mutation | Fails |
| --- | --- | --- |
| 1 over-selling past the deficit | `needed = disposableAvailable` | 5 tests, incl. `mustNotHappen_sellRecreatesItselfFromItsOwnOutput` |
| 2 buying on without a consumer | delete the `consumerStillWants` check | `mustNotHappen_theChainContinuesWithoutItsConsumer` |
| 3 emeralds as their own appetite | floor the deficit at 1 so it never closes | `mustNotHappen_sellingContinuesAfterTheDeficitIsSatisfied`, `…SellRecreatesItselfFromItsOwnOutput` |

Probe 3 is the interesting one: a 200-iteration loop sells exactly nine times and stops. With the
deficit floored it never stops — which is precisely the runaway the requirement describes.

## MAIBS (light — no movement in this slice)

`mustHappen_theChainTracksAChangingWorldAndThenStaysDead` walks the specified timeline: T+1 chain
created needing 9 sells → T+21 SPM eats sellable food, stock falls to 3, still 9 needed but now
`sellBlocked` (recalculated, not locked out) → T+41 emeralds arrive elsewhere, requirement shrinks to
1 → T+61 the book arrives elsewhere, chain terminates → T+200 no resurrection.

## Concerns

1. **`emeraldsPerSellUse` is supplied per evaluation, not bound to a specific offer.** If the sell
   offer changes between evaluations — different villager, restocked price — the arithmetic silently
   describes a different trade. Harmless while nothing executes, but **V2-E must recompute
   `requiredSellUses` against the offer it is actually about to attempt**, which is the same class of
   handoff risk as V2-C's `paymentAffordable`. Two now; worth treating as one rule at V2-E.
2. **`ChainFacts.consumerStillWants` is a caller-supplied boolean.** V2-D cannot see the demand
   system by design, so a caller that computes it loosely could keep a chain alive past its reason.
   V2-E should derive it from the live `WorkDemandPolicy` selection rather than a cached flag.
3. **No partial-fill accounting.** The chain tracks emeralds and the target, not "3 of 9 sells done" —
   deliberately, since attempts are not recorded. If a future slice wants progress reporting it must
   derive it, not store it.
4. **The chain models one SELL material.** Funding a purchase by selling two different disposables is
   not expressible. Not needed for the locked V2 scope; a real limit to state.

## Next

**V2-E** — the first slice with physical movement, and therefore the first full MAIBS gate before
implementation, not after. It also inherits both re-check obligations above.
