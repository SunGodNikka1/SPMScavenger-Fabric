# Task 49 report: V2-C — `TradeDemandRegistrar` / acquisition feasibility

**Status:** `DONE` — pure policy, static complete. No runtime component in this slice.

## Files changed

| File | Role |
| --- | --- |
| `village/trade/RouteEvidence.java` | what the decision may know now: existing-route feasibility, bounded offers, payment availability, external emerald deficit |
| `village/trade/TradeDemandRegistrar.java` | `decide(MaterialDemand, RouteEvidence)` → `AcquisitionDecision` (route · ranked offers · typed blocked reason) |
| `test/…/TradeDemandRegistrarTest.java` | 12 tests |

| Command | Result |
| --- | --- |
| `.\gradlew.bat clean build` | `BUILD SUCCESSFUL` — **998 tests, 0 failures/errors** (was 986) |

## How the common acquisition model actually compares

**Structural facts only.** The route choice reads three things — is the existing route feasible, is
there current bounded offer evidence, can the mob pay — and never a blended score. V2-B's utility is
consulted **only** to order offers after TRADE has already been selected.

That is gate 6 made structural: `mustNotHappen_utilityIsUsedToChooseBetweenRoutes` asserts every
route refusal appears before the first utility read, and that exactly one utility reference exists.

## Gate coverage

| # | Gate | How |
| --- | --- | --- |
| 1 | consumer is the sole source of need | demand is a parameter; structural ban on `WorkDemandPolicy.select`; timeline test asserts the deficit is unchanged |
| 2 | no emerald appetite | deficit is passed in or absent; NC-2 |
| 3 | attractiveness never wins | existing-route check is first and unconditional; NC-1 |
| 4 | current bounded evidence required | `hasBoundedOfferEvidence()`; `NO_OFFER_EVIDENCE` |
| 5 | no passive scanning | no `Level` / `Villager` / `getOffers` (structural) |
| 6 | trade utility is not cross-strategy | ordering assertion above; NC-3 |
| 7 | existing route stays available | `EXISTING_WORK` is the default on every refusal path |
| 8 | rank many, transact none | ranked list returned; structural ban on the adapter |
| 9 | disappearance → re-evaluation | **stateless by construction** — structural ban on fields; nothing to go stale |
| 10 | no movement | no world types at all |

## The two named controls

```text
Need iron - no useful villagers - smelt feasible
  -> EXISTING_WORK, reason EXISTING_ROUTE_AVAILABLE, no trade offers
  -> demand never becomes BLOCKED
```

```text
Need iron - one useful offer - payment affordable
  -> TRADE, contributes 1 of 3, consumer iron_tool_frontier carried through
  -> demand deficit unchanged: deciding created nothing
```

## Negative controls (each run in isolation)

| Control | Fails |
| --- | --- |
| let an attractive offer displace a feasible existing route | `mustNotHappen_anAttractiveOfferBeatsAFeasibleExistingRoute` |
| synthesise an emerald deficit when none was supplied | `mustNotHappen_aSellLegIsChosenWithoutAnExternalEmeraldDeficit` |
| read trade utility before the route is settled | `mustNotHappen_utilityIsUsedToChooseBetweenRoutes` |

## MAIBS (light — no physical movement yet)

`mustNotHappen_ownershipOscillatesAcrossAChangingWorld` runs a seven-step timeline: work available →
work gone, offer appears → offer disappears → payment spent → offer returns → work returns → work
gone again. The observed route sequence matches present evidence exactly at every step, and each
state repeated 50 times yields an identical decision.

**Convergence is by construction, not by tuning.** TRADE requires the existing route to be
*infeasible* rather than merely lower-scoring, so two nearly-equal options have no score to flip on;
and statelessness means there is no retained ownership to lag behind the world.

## Concerns

1. **`paymentAffordable` is a boolean supplied by the caller.** V2-C deliberately cannot see a
   container, so it trusts that flag. If V2-E computes it against a different offer than the one
   finally chosen, the decision is right about the wrong thing. The ranked list makes this reachable:
   affordability was asserted for *some* offer, and V2-E may attempt the best one. **V2-D/E must
   re-check affordability for the offer it actually attempts** — recorded here so it is not
   rediscovered later.
2. **Ranking assumes all offers come from reachable villagers.** V2-C has no reachability model, by
   design. If V2-E hands it offers from a villager it cannot path to, the best-ranked offer may be
   unattainable and the second never tried. Reachability filtering belongs upstream of `decide`.
3. **The emerald hardcode is inherited from V2-B**, recorded as `VANILLA_GEN1_ASSUMPTION`.
4. **`EXISTING_WORK` is a single opaque bucket.** Gather, smelt and craft are not distinguished
   because `WorkDemandPolicy` does not distinguish them for this purpose today. If a later slice
   needs "smelt is feasible but craft is not", this enum must grow rather than be reinterpreted.

## Next

V2-F taxonomy (`ActivityClass.VILLAGE_TRADE`) or V2-D chain ticket, per the LOCKED sequencing.
V2-E remains the first slice with physical movement and therefore the first full MAIBS gate.
