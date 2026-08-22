# Task 57 report: V3-E population food support episode

**Status:** `DONE_WITH_CONCERNS` (CLOSE-57-1/2/3 repaired; re-review for static accept; runtime `UNVERIFIED`)  
**Slice:** V3-E — `PopulationFoodSupportGoal`  
**Brief:** `.superpowers/sdd/task-57-brief.md` v1.2 LOCKED  
**Gate 0:** `.superpowers/sdd/task-57-gate0-report.md` — `GATE_0_PASS`  
**Target:** `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

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

## User locks (v1.2 — applied)

| Lock | Implementation |
| --- | --- |
| `mobGriefing` hard gate | `PopulationFoodSupportAdmission.mobGriefingPermits`, goal `canUse`/`canContinueToUse`, handoff kernel |
| Nutrition reserve 12 | `PopulationFoodTuning.MIN_SURVIVAL_NUTRITION_RESERVE`; `PlayerNutritionReserve` uses `DataComponents.FOOD` |
| PD-57-6 interlocks | `PopulationFoodInterlocks` — `TradeSessionClaimWindow` + `SocialExecutionBindingRegistry` exact `subjectId` |
| PD-57-7 recipient need | `needsFood` = `wantsMoreFood() && !canBreed()` at SELECT + `handoffPreflight` |
| G0-A dual reachability | Villager nav → HOME (`BreederLocalHomeProof`); PlayerMob nav → recipient (selector path) |
| G0-B ACK_WAIT | Observation-only window; shared post-episode cooldown for both terminal outcomes |
| No `PoiManager.take()` | `BreederLocalHomeProof` uses `getInRange(HAS_SPACE)` only |
| RET-1 cooldown | `PopulationFoodEpisodeCooldown` + unload/death/`SERVER_STOP` release in `SpmScavenger` |

## Verification

| Command | CWD | Result |
| --- | --- | --- |
| `.\gradlew.bat compileJava` | `Projects/SPMScavenger-1.21.1-Fabric` | **PASS** `CONFIRMED` |
| `.\gradlew.bat test` | `Projects/SPMScavenger-1.21.1-Fabric` | **1541 tests, 0 failures** `CONFIRMED` |

## CLOSE-57 repairs (bounded closure review)

| ID | Fix | Evidence |
| --- | --- | --- |
| **CLOSE-57-1** | Two-level probe budget: `VillagerRecipientCandidateSource` (`getEntities` max `MAX+1`, expensive HOME/path cap `MAX_RECIPIENT_CANDIDATES`) + `VacantHomeCandidateSource` (`MAX_HOME_PROBES_PER_RECIPIENT` per recipient) | `BreederLocalHomeProofBudgetTest`, `PopulationFoodRecipientProbeBudgetTest`, structural seams |
| **CLOSE-57-2** | `handoffPreflight` re-reads `MobVillageMemory`, exact-anchor remembrance, `VillageWorkFactsService.peek` — **no** `plan.facts()` fallback | `PopulationFoodHandoffPreflightTest` (supersede, stale cache, vacancy loss, negative control) |
| **CLOSE-57-3** | `withinHandoffDistance` — `mob.distanceToSqr(recipient) < REACH_DISTANCE_SQR` at COMMIT preflight | `PopulationFoodHandoffPreflightTest.close57_3_*` |

## Scenario matrix (T57-1…T57-14)

| ID | Static / behavioral evidence | Runtime |
| --- | --- | --- |
| T57-1 | Expendability + handoff kernel + selector wiring `CONFIRMED` (unit/structural) | `UNVERIFIED` (VR-T3e) |
| T57-2 | `BreederLocalHomeProof` per-villager HOME proof `CONFIRMED` (structural) | `UNVERIFIED` |
| T57-3 | Read-only HOME + `canReach` semantics `CONFIRMED` (structural) | `UNVERIFIED` |
| T57-4 | `handoffPreflight` peeks **current** cache facts + `FreshnessPolicy` + candidacy `CONFIRMED` | `UNVERIFIED` |
| T57-5 | `VillageWorkAdmission` + mandatory claim via `permits` / `handoffPreflight` `CONFIRMED` (source) | `UNVERIFIED` |
| T57-6 | `PopulationFoodExpendabilityPolicyTest.t57_6_exactReserveBlocksDelivery` `CONFIRMED` | `UNVERIFIED` |
| T57-7 | Eligible adult + settlement bounds `CONFIRMED` (structural) | `UNVERIFIED` |
| T57-8 | Single `commitKernel` debit path `CONFIRMED` (handoff behavior tests) | `UNVERIFIED` |
| T57-9 | Selector filters `PopulationSupportVacancyPolicy` candidacy `CONFIRMED` (source) | `UNVERIFIED` |
| T57-10 | Shared P4 `VILLAGE_WORK` flags; no selector arbitration `CONFIRMED` (taxonomy + structural) | `UNVERIFIED` (VR-T3j) |
| T57-11 | `handoffPreflight` before COMMIT `CONFIRMED` (goal + admission source) | `UNVERIFIED` |
| T57-12 | Bounded villager + HOME probe budgets with fail-closed enumeration `CONFIRMED` | `UNVERIFIED` |
| T57-13 | `PopulationFoodHandoffBehaviorTest.t57_13_mobGriefingFalsePerformsNoDebit` `CONFIRMED` | `UNVERIFIED` |
| T57-14 | `PopulationFoodRecipientNeedTest` + admission revalidation `CONFIRMED` (structural) | `UNVERIFIED` |

**Runtime VR-T3e / VR-T3j:** `UNVERIFIED` — Minecraft launch not authorized.

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
| HOME probe budget | `BreederLocalHomeProofBudgetTest.close57_1_homeEnumerationStopsAtPerRecipientBudget` | `CONFIRMED` |
| Villager expensive-probe budget | `PopulationFoodRecipientProbeBudgetTest.close57_1_villagerProbeBudgetStopsBeforeUnboundedWork` | `CONFIRMED` |

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

### Tests (`village/population/` — 41 cases across 10 classes)

`PopulationFoodStructuralTest`, `PopulationFoodTaxonomyTest`,
`PopulationFoodExpendabilityPolicyTest`, `PopulationFoodInterlocksTest`,
`PopulationFoodHandoffBehaviorTest`, `PopulationFoodRecipientNeedTest`,
`PopulationFoodEpisodeCooldownTest`, `BreederLocalHomeProofBudgetTest`,
`PopulationFoodRecipientProbeBudgetTest`, `PopulationFoodHandoffPreflightTest`.

## Concerns / not done

1. **Static accept re-review** — CLOSE-57 repairs applied; awaiting confirmation after this pass.
2. **Runtime VR-T3e/j** — no live pathing, handoff toss pickup, P4 contention with harvest/PlaceTorch.
3. **PlaceTorch P4 contention** — KNOWN RUNTIME QUESTION (out of scope per brief).
4. **Crop replant reserve intersection** — vacuous under current vanilla-only crop set + Task-55 mature-crop drop semantics (wheat/beetroot seeds ≠ breeding items; carrot/potato replant drops guaranteed at harvest). No active delegation exists; not a closure blocker.
5. **Expendability `perItemNutrition <= 0` fallback** — generic guard for non-player-edible stacks; no specific vanilla item claim without pinned nutrition probe.
6. **Social interlock** — trade claim tested live; social binding path verified structurally (`admitExact` package-private).

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
