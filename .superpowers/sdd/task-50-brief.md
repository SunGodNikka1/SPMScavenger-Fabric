# Task 50 brief: V2-D — transient `TradeChainPlan` (SELL → BUY)

**Slice:** V2-D only. **Authorization:** User, 2026-08-15 — "Proceed with the V2-D brief."
**Not authorized:** Minecraft runtime launch · commit · push.

**Economic chain only. No movement — V2-E owns that.**

## The chain

```text
External consumer needs a target item
        ↓
TradeChainPlan
    BUY target
        ↓  not enough emeralds
    SELL disposable material   ← exists only to fund that BUY
        ↓  emerald deficit reduced
    BUY target
```

## Load-bearing requirements

| # | Requirement |
| --- | --- |
| 1 | A chain exists only because an external consumer exists |
| 2 | SELL exists only to fund that BUY — **no independent emerald appetite** |
| 3 | `consumerKey` survives every chain step |
| 4 | `desiredOutput` survives every chain step |
| 5 | Current villager / offer / path do **not** become durable identity |
| 6 | Disposable quantity is calculated **fresh** every evaluation |
| 7 | SPM may consume food or otherwise mutate the backpack; the chain **revalidates**, never locks SPM out |
| 8 | Hard expiry |
| 9 | If the desired output appears from another source, the chain terminates — it does not keep selling |
| 10 | Save/reload closes the plan neutrally; current external demand may rebuild it |
| 11 | A failed SELL does not advance to BUY |
| 12 | SELL proceeds only for the amount needed to fund the **bounded** BUY deficit |

**Requirement 12, worked:** BUY needs 9 emeralds · mob holds 7 · sell offer pays 1 emerald · mob owns
64 disposable wheat.

```text
emerald deficit        = 2
required SELL uses     = 2
```

**not** *"sell all 64 wheat because wheat is disposable."* **Disposable means permitted to spend, not
desirable to spend** — the same distinction as burnable-is-not-expendable (FS-R2) and
preference-is-not-permission.

## Deliverables

1. **`TradeChainPlan`** — transient: `consumerKey`, `desiredOutput`, `desiredQuantity`,
   `createdAtTick`, `expiresAtTick`, `step`. Carries **no** villager, offer index or path (req 5).
2. **`SellExpendabilityPolicy`** — how much of a material may be spent, computed fresh, reusing the
   `FuelExpendability` permission-layer shape rather than a second predicate (B-VR-92, SPM-2).
3. **`TradeChainPolicy`** — pure `evaluate(plan, ChainFacts, tick)` → `ChainOutcome` with the current
   step, `requiredSellUses`, and a typed termination reason.

## Required semantics

- Advancement is derived from **emeralds actually held**, never from "a sell was attempted" (req 11).
- `requiredSellUses = ceil(emeraldDeficit / emeraldsPerSellUse)`, capped by disposable quantity, and
  **zero once the deficit is met** (req 12, req 2).
- Termination reasons: `TARGET_OBTAINED_ELSEWHERE` · `CONSUMER_GONE` · `EXPIRED` · `COMPLETED`.
- **Not persisted.** Transient by construction, so req 10 is satisfied by there being nothing to load,
  and Gate RET-1e does not apply — state this explicitly rather than leaving it inferred.

## Constraints

- No `Container`, `Level`, `Villager`, adapter call, movement, or pathfinding.
- No emerald demand may ever be produced; emeralds are a means inside one bounded chain.
- Deterministic: a tick is passed in, never read from a clock.

## Verification

`.\\gradlew.bat clean build`

**MAIBS (light — still no movement). Simulate the economic state machine:**

```text
T0     target demanded
T+1    chain SELL -> BUY created
T+20   SPM eats one sellable food item
T+21   disposable quantity falls -> chain recalculates
T+40   emeralds obtained elsewhere
T+41   SELL requirement shrinks / disappears
T+60   target item obtained elsewhere
T+61   entire chain terminates
T+200  no resurrected emerald appetite
```

**Three deliberate stupid behaviours — each an `ARCHITECTURE_DEFECT` if reachable:**

1. over-selling after the emerald deficit is already satisfied;
2. continuing BUY after the external consumer disappeared;
3. recreating SELL forever because its own emerald output becomes a new demand.

Each gets a negative control that must fail when the guard is removed.

## Docs

`.superpowers/sdd/task-50-report.md` · `docs/porting/DECISIONS.md` · `progress.md` on acceptance.
