# Task 47 report: V2-A — `VillagerTradeAdapter` + `OfferSnapshot`

**Status:** `DONE_WITH_CONCERNS` — static complete; **runtime `UNVERIFIED`** (no Minecraft launch).

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
