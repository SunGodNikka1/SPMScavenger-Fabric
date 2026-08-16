# Task 47 report: V2-A — `VillagerTradeAdapter` + `OfferSnapshot`

**Status:** `DONE` — static complete, two-cost gap closed; **runtime `UNVERIFIED`** (no Minecraft launch, deferred by the User until V2-E/H can produce a real walk → face → transact chain).

## Files changed

| File | Role |
| --- | --- |
| `src/main/java/com/noobk/spmscavenger/village/trade/OfferSnapshot.java` | immutable frozen offer; copies at construction; `matchesLive` exact revalidation |
| `src/main/java/com/noobk/spmscavenger/village/trade/TradeTransaction.java` | pure staged inventory arithmetic; multi-slot debit + insert; commit |
| `src/main/java/com/noobk/spmscavenger/village/trade/VillagerTradeAdapter.java` | `inspectOffers` · `canAfford` · `performTrade` with typed `TradeResult` |
| `src/test/java/…/trade/TradeTransactionTest.java` | 9 tests |
| `src/test/java/…/trade/TradeAdapterContractTest.java` | 10 tests, incl. structural bans |

## Commands

Working directory: `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

| Command | Result |
| --- | --- |
| `.\gradlew.bat compileJava` | `BUILD SUCCESSFUL` |
| `.\gradlew.bat build` | `BUILD SUCCESSFUL` — **968 tests, 0 failures/errors** (was 949) |

## Semantics implemented

```text
1 re-resolve live offer by index      -> OFFER_GONE
2 matchesLive(snapshot)               -> OFFER_CHANGED
3 !isOutOfStock()                     -> OUT_OF_STOCK
4 stage  = copy of every slot
5 debit costA across slots            -> CANNOT_AFFORD
6 debit costB across slots            -> CANNOT_AFFORD
7 preflight insert assemble()         -> NO_ROOM
8 commit                              <- first mutation of anything real
9 notifyTrade(live)                   <- exactly once, after 8
```

Every refusal returns before step 8, so the backpack and the villager are untouched.

## Evidence

| Claim | Label | Proof |
| --- | --- | --- |
| `getResult()` returns the live field; `assemble()` copies | `CODE_CONFIRMED` | pinned jar, `MerchantOffer` offsets 0–7 both methods |
| `getCostA()` copies via `copyWithCount`; `getBaseCostA()` does not | `CODE_CONFIRMED` | same class |
| `take(a,b)` mutates only the two stacks passed | `CODE_CONFIRMED` | offsets 0–44 |
| `notifyTrade` takes no `Player` | `CODE_CONFIRMED` | pre-existing baseline, re-read |
| Snapshot cannot alias a live offer | `CONFIRMED` (unit) | `mustNotHappen_theSnapshotAliasesTheLiveOfferResult` |
| Multi-slot debit works | `CONFIRMED` (unit) | 16+4 wheat → 20 |
| A trade actually completes against a live villager in a world | **`UNVERIFIED`** | needs VR-T2 |

## Negative controls (each run in isolation)

| Control | Fails |
| --- | --- |
| snapshot holds `getResult()` instead of `assemble()` | `mustNotHappen_theSnapshotAliasesTheLiveOfferResult`, `mustNotHappen_anyCodePathReachesTheLiveStacks` |
| single-slot debit (the `take`-shaped mistake) | `mustHappen_costIsDebitedAcrossMultipleSlots`, `mustHappen_partialSlotIsLeftWithTheRemainder` |
| commit before the preflight insert | `mustHappen_commitPrecedesNotifyAndFollowsEveryCheck` |

## Self-review against the brief

| Requirement | Status |
| --- | --- |
| `assemble()` / `getCostA()` only | done — structural test bans the other two across all three files |
| multi-slot debit; `take` not used for payment | done — `take` is not called at all |
| preflight result insertion | done |
| exact live-offer revalidation | done — item **and** count on both costs and result |
| `notifyTrade` exactly once after commit | done — structural test asserts one call site and its position |
| no `setTradingPlayer` / `MerchantMenu` / client types | done — structural test |
| no new persisted per-mob state | done — nothing persisted in this slice |
| no goal / scheduling / demand integration | done — V2-B/E own those |

## Concerns

1. **Runtime is entirely unproven.** `performTrade` has never run against a live `Villager`.
   `matchesLive`, `isOutOfStock` and `notifyTrade` are exercised only through constructed
   `MerchantOffer`s. VR-T2 in the vanilla-only instance is the missing proof, and it is the User's
   call.
2. **`inspectOffers` calls `villager.getOffers()`**, which lazily populates offers for a villager
   that has none yet. That is vanilla's own behaviour on any inspection and does not mutate the
   world, but it is a side effect of *looking* and is worth confirming at runtime that it does not
   perturb a villager the mob merely walked past.
3. **Result-slot merging assumes `getMaxStackSize()` from the result stack.** Correct for vanilla;
   a modded item with a container-dependent limit could differ. Not exercised.
4. **No trade has a cost B in the tests.** The code path is implemented and unit-tested through
   `debit`, but no constructed offer used `costB`, so the two-cost branch of `matchesLive` and
   `performTrade` is `INFERRED` rather than exercised end-to-end.

## Next

V2-B (`TradeEvaluationPolicy`) per the LOCKED sequencing. VR-T2 remains separately authorized and
must run with `tradeeverything` **absent** (`D-VR-069`).

---

## Closure addendum (2026-08-15) — two-cost E2E, `#take` reconciliation, placement rule

### Two-cost gap closed (User: REQUIRED before V2-B)

`performTrade`'s body was extracted to package-private `executeAgainst(Container, MerchantOffers,
OfferSnapshot, Consumer<MerchantOffer>)`. `performTrade` is now a thin wrapper supplying the
villager's offer list and its `notifyTrade`, so the tests exercise **the shipping path**, not a
parallel one — and the whole chain becomes provable without a live entity.

`TwoCostTradeTest` (6 tests):

| Test | Proves |
| --- | --- |
| two-cost trade across slots | A spans slots 0+4, B spans 1+6; exact debit of both, exact result, `notifyTrade` once with the **live** offer, uses +1 |
| second cost insufficient | A affordable, B one short → `CANNOT_AFFORD`, **zero** inventory mutation, **zero** offer mutation, no notify |
| wrong second-cost item | a different item does not satisfy B |
| same item for both costs | both costs draw from **one** staging array, so 8 emeralds cannot pay 8+8; 16 can |
| result cannot fit | both costs returned in full, no use recorded |
| changed second cost | B rising 4 → 6 is `OFFER_CHANGED`, not a silent overpay |

Negative controls added: debiting B from a **fresh** staging array (independent costs) fails the
multi-slot and same-item tests; moving `notify` before `commit` fails the ordering test.

### `#take` wording reconciled (User: RECONCILE)

The locked evidence line said `#satisfiedBy` / `#take` "mutate payment stacks without
`MerchantMenu`", which established *a menu is not required* — it was never a mandate to pay through
`take`. RFC updated in both places to mark `#take` **superseded for payment** by `D-VR-061` /
`D-VR-071`, with the reason (menu-shaped: it shrinks only the two stacks handed to it) and the fact
that **V2-A calls `take` zero times**. `satisfiedBy`'s validation role is retained and noted.

### Placement rule recorded (User: WATCH)

`inspectOffers` calls `getOffers()`, which lazily populates a villager's offers. **It must not be
called from a broad passive per-tick villager scan** — that would initialise offers across every
villager the mob walks past. Offer inspection belongs to bounded trade-candidate evaluation and
admission only. This is a constraint on V2-E's goal, recorded here so it is not discovered there.

### Deferred by the User

| Item | Status |
| --- | --- |
| VR-T2 runtime | **DEFER** — V2-A has no autonomous executor; runtime becomes meaningful at V2-E/H |
| modded stack-limit behaviour | **DEFER** — vanilla baseline first; compatibility evidence, not a 1.12.0 blocker |

### Final

`.\gradlew.bat clean build` → **974 tests, 0 failures**. Five negative controls across the slice.
