# Task 48 report: V2-B — `TradeEvaluationPolicy`

**Status:** `DONE` — pure policy, static complete. No runtime component exists in this slice.

## Files changed

| File | Role |
| --- | --- |
| `village/trade/TradeEvaluation.java` | immutable result: direction, consumer, material, contribution, costs, unit price, utility |
| `village/trade/TradeEvaluationPolicy.java` | `evaluate(demand, offer[, emeraldDeficit])` → `Result` (evaluation **or** rejection) |
| `test/…/TradeEvaluationPolicyTest.java` | 12 tests |

## Commands

| Command | Result |
| --- | --- |
| `.\gradlew.bat clean build` | `BUILD SUCCESSFUL` — **986 tests, 0 failures/errors** (was 974) |

## The boundary, as implemented

`evaluate` takes a demand it did not choose and returns a contribution and a price. It never concludes
that trading wins. Enforced structurally: the policy may not reference `Container`,
`WorkDemandPolicy.select`, `VillagerTradeAdapter`, `performTrade`, `notifyTrade`, `Villager` or
`Level`, and may not consult a clock or randomness.

| Input | Output |
| --- | --- |
| need 3 iron · offer 4 emeralds → 1 iron | BUY, contributes 1 of 3, payment 4, consumer `iron_tool_frontier` |
| need 3 iron · offer 3 emeralds → diamond sword | `WRONG_MATERIAL` — valuable is not relevant |
| need 3 iron · offer 1 emerald → 16 iron | contributes **3**, capped at the deficit |
| sell wheat, no emerald deficit | `NO_CONSUMER_FOR_PAYMENT` |
| sell wheat, `mending_book` needs 27 emeralds | SELL, 1 emerald toward 27, attributed to **`mending_book`** |

The SELL attribution is deliberate: the consumer is the one that needs the *emeralds*, not the one
that named the material, or the sell step would be credited to the wrong appetite.

## Negative controls (each run in isolation)

| Control | Fails |
| --- | --- |
| uncap the contribution | `mustNotHappen_contributionExceedsTheDeficit` |
| let a non-matching offer fall through to `buy` | `mustNotHappen_aValuableButIrrelevantOfferEvaluates` |
| synthesise an emerald deficit when none was supplied | `mustNotHappen_aSellOfferEvaluatesWithoutANamedConsumer` |

## MAIBS (light — no executor in this slice)

`mustNotHappen_repeatedEvaluationDriftsOrAccumulates`: 100 evaluations of the same demand/offer pair
return identical contribution, utility and unit cost, and leave both the demand's deficit and the
snapshot's result untouched. Evaluating creates no demand, accumulates nothing, and does not drift.
The physical GoalSelector gate remains V2-E.

## Self-review against the brief

| Requirement | Status |
| --- | --- |
| pure policy, no container/entity/world/clock | done — structural tests |
| contribution capped at deficit | done |
| value alone grants no permission | done |
| SELL requires a named emerald deficit | done |
| consumer carried through for V2-C attribution | done |
| utility comparable, never a verdict | done — coverage minus unit price, deliberately simple |
| does not become a second demand selector | done — structural ban on `WorkDemandPolicy.select` |

## Concerns

1. **The utility function is a placeholder with a defensible shape, not a tuned one.**
   `coverage * 100 - unitCost` orders cheaper-per-unit and larger-coverage above their opposites,
   which is all V2-C needs today. It has no runtime evidence and no calibration against the
   gather/smelt/craft alternatives it will eventually be compared with — that comparison is V2-C's
   and may well require a different scale. Treat the constant as arbitrary until then.
2. **Emeralds are hardcoded as the currency** (`BuiltInRegistries.ITEM.getKey(Items.EMERALD)`).
   True for vanilla villagers and wandering traders. A mod adding a merchant that pays in something
   else would not be recognised as a SELL opportunity — it would fall to `WRONG_MATERIAL`, which is
   the safe direction, but it is a hardcode and belongs in the compatibility ledger rather than
   passing unnoticed.
3. **One demand, one offer.** Choosing among several offers from the same villager, and among
   several villagers, is not modelled here. That is V2-C/V2-E, but worth stating so nobody assumes
   this class ranks a set.

## Next

V2-C `TradeDemandRegistrar` per the LOCKED sequencing: one real external progression/tool demand plus
a deterministic test demand, and the decision — which this slice deliberately does not make — of
whether TRADE is admitted as an acquisition route at all.
