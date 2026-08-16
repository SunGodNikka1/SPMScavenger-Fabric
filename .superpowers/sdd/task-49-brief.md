# Task 49 brief: V2-C — `TradeDemandRegistrar` / acquisition feasibility

**Slice:** V2-C only. **Authorization:** User, 2026-08-15 — "Proceed with the V2-C brief."
**Not authorized:** Minecraft runtime launch · commit · push.

This is the slice where the project moves from *"I can understand a trade"* to *"I can decide that
trading is a legitimate way to satisfy an existing goal."*

## The model

```text
External consumer  (sole source of need)
        ↓
MaterialDemand
        ↓
feasible acquisition strategies
   ├─ EXISTING_WORK   gather / smelt / craft, as WorkDemandPolicy already provides
   └─ TRADE           only with current bounded route evidence
        ↓
compare through a COMMON acquisition-level model   ← structural facts, not blended numbers
        ↓
if TRADE is chosen: V2-B utility ranks offers WITHIN the trade route
```

### Gate 6 decides the shape of the common model

**Raw V2-B utility is trade-local ranking only.** `73` trade utility and `100` smelt utility do not
share units, and inventing a conversion between emerald price and mining effort would be a magic
constant pretending to be a comparison.

So the common model compares **structural facts** — is the route feasible, what would it contribute,
what blocks it — and never a blended score. V2-B utility is used **only** to order offers once TRADE
has already been selected.

## Load-bearing gates

| # | Gate |
| --- | --- |
| 1 | External consumer remains the **sole** source of need |
| 2 | TRADE cannot create an emerald appetite |
| 3 | TRADE cannot win merely because an attractive offer exists |
| 4 | A trade candidate exists only with **current bounded route evidence** |
| 5 | No broad passive `getOffers()` scanning — offers arrive already bounded |
| 6 | Raw V2-B trade utility is **not** cross-strategy utility |
| 7 | The existing craft/smelt route remains available when trade is infeasible |
| 8 | Multiple offers/villagers may be ranked; V2-C performs **no transaction** |
| 9 | Candidate disappearance causes **re-evaluation**, not stale ownership |
| 10 | No world movement — V2-E owns execution |

**Gate 9 is satisfied by construction:** V2-C is **stateless and pure**. A decision is a function of
current evidence only, so there is no ownership to go stale.

## Deliverables

1. **`AcquisitionRoute`** — `EXISTING_WORK` · `TRADE`.
2. **`RouteEvidence`** — what the caller knows *right now*: existing-route feasibility, bounded offer
   evidence, payment availability, whether an external emerald deficit exists.
3. **`AcquisitionDecision`** — chosen route, ranked trade evaluations (empty unless TRADE), and a
   typed `blockedReason` when TRADE was refused.
4. **`TradeDemandRegistrar`** — `decide(MaterialDemand, RouteEvidence)`. Pure static.

## Required semantics

- **Existing route feasible → it stays eligible, always.** TRADE never blocks it (gate 7). Refusing
  trade must never convert a satisfiable demand into `BLOCKED`.
- **TRADE is preferred only when the existing route is infeasible** *and* current bounded evidence
  shows a viable, affordable offer. Deliberate asymmetry: TRADE must be strictly better-founded, not
  merely better-scoring. This is also what makes the decision converge instead of oscillating when
  the two are close.
- **No emerald deficit is ever synthesised.** A SELL leg is possible only when the caller supplies a
  deficit an external consumer already established.
- **Offers arrive as a bounded list.** No `Level`, no `Villager` lookup, no scan.

## Constraints

- No `Container`, `Level`, `Villager`, adapter call, or `notifyTrade`.
- No state: no fields, no cache, no clock, no randomness.
- Must not create, mutate, or select a `MaterialDemand`.

## Verification

`.\\gradlew.bat clean build`

**The two named controls**

```text
Need: iron tool · loaded villagers: none useful · smelt/craft: feasible
MUST      EXISTING_WORK remains eligible
MUST NOT  TRADE wins -> demand BLOCKED -> progression suppressed
```

```text
Need: demanded output · useful loaded offer · payment route feasible
MUST      TRADE may become a legitimate candidate
MUST NOT  the evaluation itself create demand
```

**Also must not happen**
- a trade candidate with no bounded offer evidence (gate 4);
- an emerald appetite invented for a SELL leg (gate 2);
- an attractive offer beating a feasible existing route (gate 3);
- cross-strategy comparison of raw V2-B utility (structural — the decision must not read
  `TradeEvaluation.utility()` when choosing *between* routes, only when ordering within TRADE).

**MAIBS (light — still no physical movement).** Simulate the decision feedback loop over a sequence:
offer appears → disappears → villager unavailable → payment stock changes → competing route becomes
feasible. **Ownership must converge, not oscillate**: identical evidence yields an identical
decision, and returning to earlier evidence returns the earlier decision.

## Docs

`.superpowers/sdd/task-49-report.md` · `docs/porting/DECISIONS.md` · `progress.md` on acceptance.
Record the emerald-currency hardcode as **`VANILLA_GEN1_ASSUMPTION`**, not universal merchant truth.
