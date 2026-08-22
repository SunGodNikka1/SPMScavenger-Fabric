# Task 55 report: V3-C committed harvest→replant episode

**Status:** `STATIC-BEHAVIORAL ACCEPT` (closure repair complete)  
**Slice:** D-VR-079 / D-VR-079-A1 / D-VR-083 (budget contract)  
**Brief:** `.superpowers/sdd/task-55-brief.md` v2.1  
**Gate 0:** `.superpowers/sdd/task-55-gate0-report.md` — PASS  
**Target:** `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

## Summary

Implemented V3-C: managed crop domain + continuous host `HarvestCropsGoal` veto + priority-4
`VillageHarvestEpisodeGoal` with atomic mature→age-0 `setBlock` commit, single drop roll at COMMIT,
F8 backpack banking via `ContainerMerge.insert` remainder semantics, and `VILLAGE_WORK` taxonomy pin.

**R1 repair (architecture accepted):** reachability-aware bounded target selection, commit-time
`mobGriefing`/loaded-world revalidation, exact-self activity observation exclusion, crop-guard
compatibility diagnostics parity with task-54, and behavioral transaction harness sharing the
production commit kernel.

**Closure repair (CLOSE-1/2):** crop continuation mixin fail-open on unresolved compatibility
evidence; selector backoff limited to concretely failed positions only (no unprobed shortlist
marking).

## User implementation locks (applied)

| Lock | Implementation |
| --- | --- |
| Final pre-roll revalidation | `CropHarvestTransaction.commitKernel` re-checks admission, `mobGriefing`, loaded chunk, mature state equality, farmland, `deterministicReplantFeasible` **before** `Operations.rollDrops` |
| Exact post-commit verification | `after.equals(ageZero)`; `replaceBlock==false` → ABORT + escrow restore; mismatch after success → INVARIANT_FAILURE, no drop grant |
| Named update flag | `Block.UPDATE_ALL` (not magic `3`) |
| Remainder insertion API | `ContainerMerge.insert(Container, ItemStack) → ItemStack` remainder |

## Verification

| Command | CWD | Result |
| --- | --- | --- |
| `.\gradlew.bat compileJava` | `Projects/SPMScavenger-1.21.1-Fabric` | **PASS** `CONFIRMED` |
| `.\gradlew.bat test` | `Projects/SPMScavenger-1.21.1-Fabric` | **1478 tests, 0 failures** `CONFIRMED` |

### Behavioral proof (R1-5 primary evidence)

| Scenario | Test | Result |
| --- | --- | --- |
| Admission denied → 0 rolls, 0 replacement | `CropHarvestTransactionBehaviorTest.admissionDeniedPerformsNoDropRollOrReplacement` | `CONFIRMED` |
| `mobGriefing=false` → 0 rolls, 0 replacement | `CropHarvestTransactionBehaviorTest.mobGriefingFalsePerformsNoDropRollOrReplacement` | `CONFIRMED` |
| Stale crop → 0 rolls | `CropHarvestTransactionBehaviorTest.staleCropPerformsNoDropRoll` | `CONFIRMED` |
| Wheat/beetroot no seed → 0 rolls | `CropHarvestTransactionBehaviorTest.wheatWithoutHeldSeedPerformsNoDropRoll` | `CONFIRMED` |
| Accepted transaction → exactly 1 roll | `CropHarvestTransactionBehaviorTest.acceptedTransactionRollsDropsExactlyOnce` | `CONFIRMED` |
| Replacement false → escrow restored, no loot | `CropHarvestTransactionBehaviorTest.replacementFalseRestoresEscrowAndGrantsNoLoot` | `CONFIRMED` |
| Replacement success → age 0 | `CropHarvestTransactionBehaviorTest.successfulReplacementVerifiesExactAgeZero` | `CONFIRMED` |
| Invariant mismatch → no staged loot | `CropHarvestTransactionBehaviorTest.invariantMismatchGrantsNoStagedLoot` | `CONFIRMED` |
| Partial inventory → conservation | `CropHarvestTransactionBehaviorTest.partialInventoryFillConservesItems` | `CONFIRMED` |
| Second actor after age-0 → 0 roll/mutation | `CropHarvestTransactionBehaviorTest.secondActorAfterAgeZeroPerformsNoRollOrMutation` | `CONFIRMED` |
| Nearest inaccessible + farther reachable | `HarvestCropTargetSelectorTest.nearestInaccessibleFartherReachableSelectsFarther` | `CONFIRMED` |
| All inaccessible → backoff, no admission target | `HarvestCropTargetSelectorTest.allInaccessibleYieldsNoTargetAndBacksOff` | `CONFIRMED` |
| Backoff active / expires | `HarvestCropTargetSelectorTest.backoffSkipsCropUntilExpiry` | `CONFIRMED` |
| Path probes hard-bounded | `HarvestCropTargetSelectorTest.pathProbesAreHardBounded` | `CONFIRMED` |
| Unprobed crops not backed off; eligible next scan | `HarvestCropTargetSelectorTest.unprobedCandidatesRemainEligibleOnNextScan` | `CONFIRMED` |
| Continuation fail-open on unresolved target | `HarvestCropsManagedDomainMixinContinuationTest.unresolvedContinuationDoesNotForceHostFalse` | `CONFIRMED` |
| Positive managed domain still vetoes continuation | `HarvestCropsManagedDomainMixinContinuationTest.positiveManagedDomainStillVetoesContinuation` | `CONFIRMED` |
| Exact-self observation exclusion | `ActivityObservationServiceExclusionTest` (4 cases) | `CONFIRMED` |
| Crop guard compat session lifecycle | `HarvestCropGuardCompatibilityTest` (4 cases) | `CONFIRMED` |

### Structural proof (wiring guards only)

| Class | Role |
| --- | --- |
| `CropHarvestTransactionTest` | `UPDATE_ALL`, single `dropRolls++` in kernel, abort vs invariant, precondition order |
| `ManagedCropDomainStructuralTest` | crop package import boundaries |
| `VillageHarvestTaxonomyTest` | `VILLAGE_WORK` pin |
| `CropReplantSemanticsTest`, `HarvestCandidatePolicyTest`, `ContainerMergeTest` | policy/unit semantics |

### VR-T3 static matrix

| ID | Must happen | Must not happen | Evidence |
| --- | --- | --- | --- |
| VR-T3a | mature→age-0 via one `setBlock` | `destroyBlock` | behavioral + semantics tests `CONFIRMED` |
| VR-T3b | pre-COMMIT abort safe | world mutation | behavioral abort cases `CONFIRMED` |
| VR-T3c | preflight/replace false → ABORT | grant on failure | behavioral `CONFIRMED` |
| VR-T3k | commit-time block truth | global reservation map | behavioral stale/loaded/griefing `CONFIRMED` |
| VR-T3l | veto in managed domain | host strip when admission denies | mixin + veto policy `INFERRED` (no live SPM) |
| VR-T3m | backpack banking | floor pickup recovery | behavioral conservation `CONFIRMED` |

**Runtime VR-T3a–c/k/l/m:** `UNVERIFIED` — Minecraft launch not authorized.

## R1 deliverables

| ID | Change |
| --- | --- |
| R1-1 | `HarvestCropTargetSelector` — bounded scan/shortlist/path-probe; `ManagedCropDomainContext` snapshot; `HarvestTargetBackoff`; stored `Path` in `start()` |
| R1-2 | `mobGriefing` in `canContinueToUse` + `commitKernel`; loaded/mature/farmland/feasibility before drop roll |
| R1-3 | `ActivityObservationService.observeExcluding` (exact goal instance); removed class-wide `VILLAGE_WORK` filter from admission |
| R1-4 | `HarvestCropGuardCompatibility` — proactive host shape probe, warmup WARN, `recordTargetResolutionFailed` on unresolved host target |
| R1-5 | `CropHarvestTransaction.Operations` + `commitKernel` shared by production `ServerLevel` adapter and behavioral tests |
| CLOSE-1 | `HarvestCropsManagedDomainMixin` continuation — record diagnostic, leave host result unchanged when mob/target/level unresolved |
| CLOSE-2 | Removed blanket shortlist backoff; backoff only on concrete failure (no approach, path fail, travel timeout) |

## Deliverables

| Path | Role |
| --- | --- |
| `village/crop/CropReplantSemantics.java` | vanilla-only crop semantics |
| `village/crop/ManagedCropDomainPolicy.java` | `managedCropCell` |
| `village/crop/ManagedCropDomainContext.java` | per-scan domain snapshot |
| `village/crop/HarvestCropTargetSelector.java` | reachability-aware selection |
| `village/crop/HarvestTargetBackoff.java` | per-goal transient backoff |
| `village/crop/CropWorldView.java` | scan block-truth surface |
| `village/crop/HarvestCandidatePolicy.java` | deterministic candidacy |
| `village/crop/HarvestCropVetoPolicy.java` | host veto predicate |
| `village/crop/CropHarvestTransaction.java` | shared commit kernel + `ServerLevel` adapter |
| `village/crop/HarvestCropGuardCompatibility.java` | diagnostics (task-54 parity) |
| `village/VillageHarvestAdmission.java` | admission wiring (`observeExcluding` on continuation) |
| `activity/ActivityObservationService.java` | `observeExcluding` / `observeRunningGoalsExcluding` |
| `inventory/ContainerMerge.java` | remainder insert API |
| `goal/VillageHarvestEpisodeGoal.java` | P4 episode with path storage |
| `compat/OptionalHarvestCropTargetResolver.java` | reflective target |
| `mixin/HarvestCropsManagedDomainMixin.java` | continuous veto + target-resolution diagnostics |
| `SpmScavenger.java` | P4 registration + compat lifecycle |
| `MoveHolderClassifier.java` | `VILLAGE_WORK` pin |

## Concerns

1. **Runtime witness deferred** — mixin hook observation, pathfinding in village fields, and multi-mob commit races need live SPM + village fields (batched V3 campaign VR-T3).
2. **Path approach heuristic** — uses adjacent air cells with solid floor; not full collision-shape approach policy from gather.
3. **Farmland support** — uses `FarmBlock` below crop (public API); not full `canSurvive` (protected).

## Acceptance

**task-55 / V3-C — STATIC-BEHAVIORAL ACCEPT** (closure CLOSE-1/2 green). Runtime VR-T3 remains deferred.

## Self-review vs brief

- R1-1 through R1-5 implemented per review; task-56 not started.
- No Minecraft runtime launched.
- Behavioral harness is primary transaction evidence; structural tests retained as wiring guards.
