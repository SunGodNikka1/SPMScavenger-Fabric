# Task 51 report: V2-E — `TradeWithVillagerGoal` + greet interlock

**Status:** `DONE_WITH_CONCERNS` — static complete; **runtime `UNVERIFIED`**, VR-T2 held.

## Files

| File | Role |
| --- | --- |
| `village/trade/TradeSessionClaimWindow.java` | `(mob, villager)` greet interlock; hard expiry; unconditional release |
| `village/trade/TradeCandidateRound.java` | transient attempt round: attempted set, path budget, cooldown |
| `village/trade/TradeDemandGate.java` | thin admission seam over `TradeDemandRegistrar` |
| `goal/TradeWithVillagerGoal.java` | P3, MOVE+LOOK executor |
| `village/trade/VillagerTradeAdapter.java` | **+** `MERCHANT_BUSY`, `MERCHANT_UNAVAILABLE`, `available(Villager)` |
| `mixin/FriendlyGreetAdmissionSeamMixin.java` | interlock consulted **before** `recordObservation` |
| `SpmScavenger.java` | goal registered at P3; claim released on unload/death/server-stop |
| `test/…/TradeInterlockAndRoundTest.java` | 15 tests |

| Command | Result |
| --- | --- |
| `.\gradlew.bat clean build` | `BUILD SUCCESSFUL` — **1030 tests, 0 failures/errors** (was 1015) |

## Locked constraints

| # | Constraint | Implementation |
| --- | --- | --- |
| 1 | targeted seam interlock, no SOCIAL sub-mode | `TradeSessionClaimWindow`, separate type; SOCIAL untouched |
| 2 | claim identity `(mob UUID, villager UUID)` | exact pairing; Alice/other-mob/other-villager all unaffected |
| 3 | claim at concrete attempt start | `beginAttempt` claims before any navigation |
| 4 | no authority over the villager | suppresses only *our* greet of *that* villager |
| 5 | complete release set | `stop()` · success · demote/reselect · round end · unload · death · server stop · 1200-tick expiry |
| 6 | seam checks before SOCIAL publication | asserted structurally; NC-1 |
| 7 | transient round, not a "decision cycle" | `TradeCandidateRound`; NC-4 |
| 8 | exhausted round → cooldown → fresh round | `EXHAUSTED_ROUND_COOLDOWN_TICKS`, demoted candidates restored |
| 9 | player-occupied merchant rejected twice | `available()` at selection, `MERCHANT_BUSY` at execution; NC-3 |
| 10 | sleep is explicit V2-E legality | `MERCHANT_UNAVAILABLE`, distinct from failure so the executor demotes |

**`TradeDemandGate` is a seam, not a director** — structurally barred from fields, comparators,
sorting, containers, levels, villagers and the adapter; it calls `TradeDemandRegistrar.decide` and
returns. Nothing is remembered between calls, so a demand that disappears mid-walk stops authorizing
the trade on the next tick.

**Attempt-boundary recomputation:** live demand via `WorkDemandPolicy.select` (never cached),
`canAfford` for the offer actually chosen, `available()` for occupancy/sleep, and V2-A's own
re-resolution and `matchesLive` at commit. V2-A atomicity preserved — every refusal is mutation-free
and the commit still precedes `notifyTrade`.

## Negative controls (each run in isolation)

| Control | Fails |
| --- | --- |
| move the interlock after social publication | `mustHappen_theInterlockPrecedesSocialPublication` |
| make `stop()`'s release conditional | `mustHappen_stopReleasesTheClaimUnconditionally` |
| drop the busy-merchant refusal | `mustHappen_theAdapterRefusesBusyAndUnavailableMerchantsBeforeMutating` |
| never demote a failed candidate | `mustHappen_anExhaustedCandidateIsDemotedAndTheNextIsTried`, `…beginIsIdempotent…` |

## Concerns

1. **`resolve()` re-keys an offer by matching result item + cost item.** Candidate ranking flattens
   offers across villagers into a synthetic index, so the villager's own index must be recovered
   before commit. Matching on item identity is ambiguous when one villager offers **two trades with
   the same item pair at different counts** — the first match wins and may be the wrong one.
   V2-A's `matchesLive` then rejects it (`OFFER_CHANGED`, mutation-free), so this is safe but
   **wasteful**: the mob walks, refuses, demotes. Carrying `(villagerUUID, realIndex)` through
   ranking instead of re-deriving it would remove the ambiguity. **The cleanest thing to fix first.**
2. **Path failure is detected only via `moveTo` returning false.** A path that is *accepted* and then
   stalls against geometry consumes no budget, so WEIRD-1/WEIRD-3 bounding is weaker than the
   prediction assumed. A no-progress timeout is the missing half. `RUNTIME_QUESTION` — VR-T2 should
   watch for a mob standing still with a live claim.
3. **`canUse` runs full candidate discovery**, including `inspectOffers` for every available villager
   in 16 blocks. Bounded and only for filtered candidates, but it is not free at P3 tick rate. The
   round cooldown covers the *exhausted* case only, not "a villager is nearby and nothing matches".
   WEIRD-5 remains live; a failed-search cooldown like `SmeltAtFurnaceGoal`'s is the fix if the log
   shows churn.
4. **No test drives the goal itself** — `canUse`/`tick`/`stop` need a `ServerLevel`, a live
   `Villager` and a navigating mob. Everything asserted here is either pure state (claim, round) or
   structural (ordering, guards). The goal's own loop is `UNVERIFIED` until VR-T2.
5. **Two MOVE goals at P3.** `TradeWithVillagerGoal` joins gather/smelt/craft/descent/tunnel. The RFC
   specifies `TradeDemandGate` mutual exclusion at the band level; what is implemented is per-goal
   admission, so two P3 goals could both be `canUse`-true and alternate. Vanilla picks the
   first-registered runnable goal, so this is bounded — but it is **not** the mutual exclusion the
   contract describes, and I did not build a band-level owner. Flagged rather than silently
   diverged.

## Next

VR-T2 (held, User's gate) is the only thing that can move concerns 2–4 off `UNVERIFIED`.
Concerns 1 and 5 are static and closable before runtime if wanted.
