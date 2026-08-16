# Task 48 brief: V2-B — `TradeEvaluationPolicy`

**Slice:** V2-B only. **Authorization:** User, 2026-08-15 — "Begin V2-B."
**Not authorized:** Minecraft runtime launch · commit · push.

## What this slice is

Pure policy. Given **one** `WorkDemandPolicy.MaterialDemand` and **one** immutable `OfferSnapshot`,
decide whether that offer contributes to that demand and, if so, describe how.

```text
external consumer demand
        ↓
MaterialDemand (materialKey, derivedDeficit, consumerKey)
        ↓
current immutable OfferSnapshot
        ↓
TradeEvaluationPolicy
        ↓
REJECT(reason)   or   TradeEvaluation{ targetFit, quantityContribution,
                                       requiredPayment, utility, cost, consumerKey }
        ↓
V2-C decides whether TRADE is a feasible acquisition route at all
```

## The boundary that must not erode

**V2-B scores an offer. V2-C owns whether TRADE is admitted as an acquisition route.** This class
must never become a second demand selector.

Worked example — demand *3 iron ingots*, offer *4 emeralds → 1 iron ingot*:

| V2-B may conclude | V2-B may **not** conclude |
| --- | --- |
| matches the requested material | "therefore trading wins" |
| contributes 1 of 3 | that gather/smelt/craft is worse |
| payment is 4 emeralds | that the mob should acquire emeralds |
| the offer is usable | that a trade should start |

Comparison against the existing gather/smelt/craft route, and current feasibility, are V2-C's.

**Value grants no permission.** Demand *iron ingot*, offer *3 emeralds → diamond sword*: valuable,
does not satisfy the demand, **REJECT**. This is the project invariant — *preference affects choice;
preference does not create permission* — applied to acquisition.

**SELL offers invent nothing.** V2-B must not manufacture a reason to obtain emeralds. A sell
opportunity is meaningful only when a **named external consumer or `TradeChainPlan`** has already
established an emerald deficit toward a BUY step; absent that, an offer that merely *pays* emeralds
is `NO_CONSUMER_FOR_PAYMENT` — not a discovery that emeralds are desirable.

## Deliverables

1. **`TradeEvaluation`** — immutable result: `consumerKey`, `materialKey`, `quantityContribution`,
   `requiredCostA` / `requiredCostB`, `unitPaymentCost`, `utility`, `direction`.
2. **`TradeRejection`** — enumerated reason: `WRONG_MATERIAL`, `NOT_TRADEABLE`, `OUT_OF_STOCK`,
   `NO_CONSUMER_FOR_PAYMENT`, `ZERO_CONTRIBUTION`.
3. **`TradeEvaluationPolicy`** — `evaluate(MaterialDemand, OfferSnapshot)` and
   `evaluate(MaterialDemand, OfferSnapshot, EmeraldDeficit)` for the SELL direction. Pure static;
   no `Container`, no entity, no world, no registry mutation.

## Required semantics

- **BUY** — the offer's *result* matches `materialKey`. `quantityContribution =
  min(result.count, derivedDeficit)`. Never more than the deficit: over-contribution is how a
  bounded need becomes an unbounded appetite.
- **SELL** — the offer's *result* is emeralds and its cost matches a material the mob is selling.
  Requires a supplied emerald deficit carrying its own `consumerKey`; without one, reject.
- **utility** rises with contribution and falls with payment per unit. It is a **comparable number
  for V2-C**, never a verdict.
- `consumerKey` is carried through unchanged from the demand, so V2-C can attribute the route.

## Constraints

- No `Container` parameter anywhere in this slice — reachability is V2-C's question, and taking a
  container is how this class would start reserving inventory.
- No offer mutation, no `notifyTrade`, no adapter call.
- No emerald appetite: nothing here may produce a demand.
- Deterministic: same inputs, same output, no clock, no randomness.

## Verification

`.\\gradlew.bat clean build`

**Must happen**
- a matching BUY offer yields contribution capped at the deficit, with payment and consumer carried;
- a SELL offer with a named emerald deficit evaluates; utility orders cheaper-per-unit above dearer.

**Must not happen**
- a valuable but non-matching offer evaluates at all;
- a SELL offer evaluates without a named consumer;
- contribution exceeds the deficit;
- the policy takes a `Container`, calls the adapter, or references a demand selector (structural).

MAIBS (light — no executor yet): **repeated evaluation must be idempotent** — evaluating the same
offer 100 times must not create demand, accumulate wealth, or change any result. The physical
GoalSelector gate stays with V2-E.

## Docs

`.superpowers/sdd/task-48-report.md` · `docs/porting/DECISIONS.md` · `progress.md` on acceptance.
