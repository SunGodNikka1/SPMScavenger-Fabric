# Task 57 report: V3-E population food support episode

**Status:** `CLOSED` / `STATIC-BEHAVIORAL ACCEPT`  
**Slice:** V3-E — `PopulationFoodSupportGoal`  
**Brief:** `.superpowers/sdd/task-57-brief.md` v1.2 LOCKED  
**Gate 0:** `.superpowers/sdd/task-57-gate0-report.md` — `GATE_0_PASS`  
**Target:** `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`  
**Closure:** 2026-08-21 — bounded closure review complete; documentation-only closure record

## Summary

Implemented V3-E population food delivery: priority-4 `PopulationFoodSupportGoal` with
PATHING → HANDOFF_PREPARE → COMMIT (single backpack debit + bounded toss) → ACK_WAIT →
`DELIVERED_ACK` or `COMMITTED_UNCONFIRMED`, wired through `PopulationFoodSupportAdmission`
(mandatory profile + `mobGriefing`), `PopulationFoodRecipientSelector` (facts candidacy +
`wantsMoreFood && !canBreed` + trade/social interlocks + dual reachability), `BreederLocalHomeProof`
(read-only HOME, no `PoiManager.take()`), `PopulationFoodExpendabilityPolicy` (nutrition reserve 12),
and dedicated `PopulationFoodHandoff` kernel.

Facts remain evidence-only; no `VillageWorkFacts` mutation, no breeding commands, no Brain mutation,
no SOCIAL/familiarity credit, no `VillageWorkSelector`, no task-58 work.

**Recipient selection terminology:** `PopulationFoodRecipientSelector` collects a bounded **K**
villager sample via `getEntities(..., MAX_RECIPIENT_CANDIDATES)`, then applies deterministic
distance + UUID ordering **within that returned bounded sample**. This does **not** claim globally
nearest-K across all eligible adults unless engine collection semantics guarantee it (they do not).

## User locks (v1.2 — applied)

| Lock | Implementation |
| --- | --- |
| `mobGriefing` hard gate | `PopulationFoodSupportAdmission.mobGriefingPermits`, goal `canUse`/`canContinueToUse`, handoff kernel |
| Nutrition reserve 12 | `PopulationFoodTuning.MIN_SURVIVAL_NUTRITION_RESERVE`; `PlayerNutritionReserve` uses `DataComponents.FOOD` |
| PD-57-6 interlocks | `PopulationFoodInterlocks` — `TradeSessionClaimWindow` + `SocialExecutionBindingRegistry` exact `subjectId` |
| PD-57-7 recipient need | `needsFood` = `wantsMoreFood() && !canBreed()` at SELECT + `handoffPreflight` |
| G0-A dual reachability | Villager nav → HOME (`BreederLocalHomeProof`); PlayerMob nav → recipient (selector path) |
| G0-B ACK_WAIT | Observation-only window; shared post-episode cooldown for both terminal outcomes |
| No `PoiManager.take()` | `BreederLocalHomeProof` uses bounded `getInRange(HAS_SPACE)` iterator only |
| RET-1 cooldown | `PopulationFoodEpisodeCooldown` + unload/death/`SERVER_STOP` release in `SpmScavenger` |

## Verification

| Command | CWD | Result |
| --- | --- | --- |
| `.\gradlew.bat compileJava` | `Projects/SPMScavenger-1.21.1-Fabric` | **PASS** `CONFIRMED` |
| `.\gradlew.bat test` | `Projects/SPMScavenger-1.21.1-Fabric` | **1543 tests, 0 failures** `CONFIRMED` (closure verification run) |

## Closure items (bounded closure review)

| ID | Status | Fix | Evidence |
| --- | --- | --- | --- |
| **CLOSE-57-1** | **CLOSED** | Two-level bounded work: K villager sample + per-recipient HOME probe cap; existential HOME proof | `BreederLocalHomeProofBudgetTest`, `PopulationFoodRecipientProbeBudgetTest`, structural seams |
| **CLOSE-57-1R** | **CLOSED** | `>K` adults supported via bounded sample (not fail-closed); HOME ∃ proof not invalidated by unexamined records | `BreederLocalHomeProofBudgetTest`, probe budget tests |
| **CLOSE-57-1F** | **CLOSED** | Bounded **provider** enumeration — `getEntities(..., MAX)` (no `getEntitiesOfClass`); HOME iterator breaks on success or probe cap | structural + `BreederLocalHomeProofBudgetTest` production iterator tests |
| **CLOSE-57-2** | **CLOSED** | `handoffPreflight` re-reads `MobVillageMemory`, exact-anchor remembrance, `VillageWorkFactsService.peek` — **no** `plan.facts()` fallback | `PopulationFoodHandoffPreflightTest` |
| **CLOSE-57-3** | **CLOSED** | `withinHandoffDistance` — `mob.distanceToSqr(recipient) < REACH_DISTANCE_SQR` at COMMIT preflight | `PopulationFoodHandoffPreflightTest` |

## Scenario matrix (T57-1…T57-14)

| ID | Static / behavioral evidence | Runtime |
| --- | --- | --- |
| T57-1 | Expendability + handoff kernel + selector wiring `CONFIRMED` | `UNVERIFIED` (VR-T3e) |
| T57-2 | `BreederLocalHomeProof` per-villager HOME proof `CONFIRMED` | `UNVERIFIED` |
| T57-3 | Read-only HOME + `canReach` semantics `CONFIRMED` | `UNVERIFIED` |
| T57-4 | `handoffPreflight` peeks **current** cache facts + `FreshnessPolicy` + candidacy `CONFIRMED` | `UNVERIFIED` |
| T57-5 | `VillageWorkAdmission` + mandatory claim via `permits` / `handoffPreflight` `CONFIRMED` | `UNVERIFIED` |
| T57-6 | `PopulationFoodExpendabilityPolicyTest.t57_6_exactReserveBlocksDelivery` `CONFIRMED` | `UNVERIFIED` |
| T57-7 | Eligible adult + settlement bounds `CONFIRMED` | `UNVERIFIED` |
| T57-8 | Single `commitKernel` debit path `CONFIRMED` | `UNVERIFIED` |
| T57-9 | Selector filters `PopulationSupportVacancyPolicy` candidacy `CONFIRMED` | `UNVERIFIED` |
| T57-10 | Shared P4 `VILLAGE_WORK` flags; no selector arbitration `CONFIRMED` | `UNVERIFIED` (VR-T3j) |
| T57-11 | `handoffPreflight` before COMMIT `CONFIRMED` | `UNVERIFIED` |
| T57-12 | Bounded villager sample + HOME provider/probe budgets `CONFIRMED` | `UNVERIFIED` |
| T57-13 | `PopulationFoodHandoffBehaviorTest.t57_13_mobGriefingFalsePerformsNoDebit` `CONFIRMED` | `UNVERIFIED` |
| T57-14 | `PopulationFoodRecipientNeedTest` + admission revalidation `CONFIRMED` | `UNVERIFIED` |

**Runtime VR-T3e / VR-T3j:** `UNVERIFIED` — deferred to batched V3 campaign; Minecraft launch not authorized for Task-57 closure.

## Negative controls

| Control | Test | Result |
| --- | --- | --- |
| Reserve `0` forbidden | `PopulationFoodStructuralTest.mustHappen_reserveConstantIsTwelveNotZero` | `CONFIRMED` |
| `mobGriefing` gates debit | `PopulationFoodHandoffBehaviorTest.negativeControl_griefingFlagMustGateDebit` | `CONFIRMED` |
| Exact-twelve nutrition blocks plan | `PopulationFoodExpendabilityPolicyTest.negativeControl_wrongReserveWouldLetExactTwelvePass` | `CONFIRMED` |
| No interlock without claim | `PopulationFoodInterlocksTest.negativeControl_noClaimMeansNoBlock` | `CONFIRMED` |
| Unconfirmed cooldown anti-loop | `PopulationFoodEpisodeCooldownTest.negativeControl_unconfirmedMustNotImmediatelyRetry` | `CONFIRMED` |
| `canBreed` blocks need alone | `PopulationFoodRecipientNeedTest.negativeControl_breedableVillagerMustNotPassNeedsFoodAlone` | `CONFIRMED` |
| Stale plan facts must not authorize COMMIT | `PopulationFoodHandoffPreflightTest.close57_2_negativeControl_planFactsFallbackWouldHavePassed` | `CONFIRMED` |
| Superseded anchor blocks COMMIT | `PopulationFoodHandoffPreflightTest.close57_2_supersededAnchorIsNotRemembered` | `CONFIRMED` |
| Recipient outside reach blocks COMMIT | `PopulationFoodHandoffPreflightTest.close57_3_recipientOutsideReachFailsDistanceGate` | `CONFIRMED` |
| HOME provider/probe budget | `BreederLocalHomeProofBudgetTest` production iterator + test-seam cases | `CONFIRMED` |
| Villager expensive-probe budget | `PopulationFoodRecipientProbeBudgetTest` | `CONFIRMED` |

## Deliverables

| Path | Role |
| --- | --- |
| `goal/PopulationFoodSupportGoal.java` | P4 episode state machine |
| `village/PopulationFoodSupportAdmission.java` | Admission + handoff preflight |
| `village/population/PopulationFoodRecipientSelector.java` | Settlement + villager selection |
| `village/population/BreederLocalHomeProof.java` | Read-only HOME reachability |
| `village/population/PopulationFoodExpendabilityPolicy.java` | Disposable breeding-food authority |
| `village/population/PopulationFoodHandoff.java` | Toss + ACK observe kernel |
| `village/population/PopulationFoodDeliveryPlan.java` | Immutable episode plan |
| `village/population/PopulationFoodEpisodeCooldown.java` | RET-1 cooldown map |
| `village/population/PopulationFoodInterlocks.java` | Trade + social interlocks |
| `village/population/PlayerNutritionReserve.java` | Edible nutrition accounting |
| `village/population/VillagerFoodInventory.java` | Villager `FOOD_POINTS` mirror |
| `village/population/PopulationFoodTuning.java` | Constants |
| `village/population/PopulationFoodTerminalOutcome.java` | Terminal enum |
| `mining/MoveHolderClassifier.java` | `VILLAGE_WORK` taxonomy pin |
| `SpmScavenger.java` | P4 registration + lifecycle hooks |

### Tests (`village/population/` — 10 classes)

`PopulationFoodStructuralTest`, `PopulationFoodTaxonomyTest`,
`PopulationFoodExpendabilityPolicyTest`, `PopulationFoodInterlocksTest`,
`PopulationFoodHandoffBehaviorTest`, `PopulationFoodRecipientNeedTest`,
`PopulationFoodEpisodeCooldownTest`, `BreederLocalHomeProofBudgetTest`,
`PopulationFoodRecipientProbeBudgetTest`, `PopulationFoodHandoffPreflightTest`.

## Deferred / documented limitations (not closure blockers)

| Item | Status |
| --- | --- |
| Runtime VR-T3e / VR-T3j | `UNVERIFIED` — deferred to batched V3 campaign |
| PlaceTorch P4 contention | KNOWN RUNTIME QUESTION (out of scope per brief) |
| Crop replant reserve intersection | Vacuous under vanilla-only crop set + Task-55 harvest semantics; no active delegation |
| Social binding interlock | Trade claim tested live; social path structural (`admitExact` package-private) |
| `perItemNutrition <= 0` fallback | Generic guard; no pinned per-item vanilla nutrition claims |

## Self-review vs brief

| Requirement | Met |
| --- | --- |
| Facts evidence-only | Yes — read via `peek`/`FreshnessPolicy`; no writes |
| No `VillageWorkSelector` | Yes |
| No HOME ticket / `take()` | Yes |
| No Brain / breeding commands | Yes |
| Single debit + single toss | Yes — one `commitKernel` call |
| ACK_WAIT observation only | Yes |
| Cooldown on both terminals | Yes |
| task-58 not started | Yes |

## Out of scope (confirmed not shipped)

`VillageWorkSelector`, compost executor, bed claiming, `KnownVillage` population persistence,
`VillageWorkFacts` mutation, SOCIAL credit, PlaceTorch contention repair.
