# Task 55 report: V3-C committed harvest→replant episode

**Status:** `DONE_WITH_CONCERNS`  
**Slice:** D-VR-079 / D-VR-079-A1 / D-VR-083 (budget contract)  
**Brief:** `.superpowers/sdd/task-55-brief.md` v2.1  
**Gate 0:** `.superpowers/sdd/task-55-gate0-report.md` — PASS  
**Target:** `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

## Summary

Implemented V3-C: managed crop domain + continuous host `HarvestCropsGoal` veto + priority-4
`VillageHarvestEpisodeGoal` with atomic mature→age-0 `setBlock` commit, single `Block.getDrops`
roll at COMMIT, F8 backpack banking via `ContainerMerge.insert` remainder semantics, and
`VILLAGE_WORK` taxonomy pin. No `destroyBlock`, no `MandatoryOwnership` publisher in the crop
package.

## User implementation locks (applied)

| Lock | Implementation |
| --- | --- |
| Final pre-roll revalidation | `CropHarvestTransaction.commit` re-checks admission (caller), mature state equality, farmland, `deterministicReplantFeasible` **before** `Block.getDrops` |
| Exact post-commit verification | `after.equals(ageZero)`; `setBlock==false` → ABORT + escrow restore; mismatch after `setBlock==true` → INVARIANT_FAILURE, no drop grant |
| Named update flag | `Block.UPDATE_ALL` (not magic `3`) |
| Remainder insertion API | `ContainerMerge.insert(Container, ItemStack) → ItemStack` remainder |

## Verification

| Command | CWD | Result |
| --- | --- | --- |
| `.\gradlew.bat test` | `Projects/SPMScavenger-1.21.1-Fabric` | **1453 tests, 0 failures** `CONFIRMED` |

### New tests (task-55)

| Class | Rows |
| --- | --- |
| `CropReplantSemanticsTest` | gen-1 crops, maturity split, Gate 0 drop flags |
| `HarvestCandidatePolicyTest` | held-seed rule, immature exclusion |
| `CropHarvestTransactionTest` | admission abort, structural commit contract |
| `ContainerMergeTest` | remainder conservation |
| `ManagedCropDomainStructuralTest` | no SPM/MandatoryOwnership imports in crop package |
| `VillageHarvestTaxonomyTest` | `VILLAGE_WORK` pin |

### VR-T3 static matrix

| ID | Must happen | Must not happen | Evidence |
| --- | --- | --- | --- |
| VR-T3a | mature→age-0 via one `setBlock` | `destroyBlock` | structural + semantics tests `CONFIRMED` |
| VR-T3b | pre-COMMIT abort safe | world mutation | goal abort paths + transaction admission gate `INFERRED` |
| VR-T3c | preflight/setBlock false → ABORT | grant on failure | `CropHarvestTransactionTest` structural `CONFIRMED` |
| VR-T3k | commit-time block truth | global reservation map | revalidation in `commit` `CONFIRMED` (static) |
| VR-T3l | veto in managed domain | host strip when admission denies | mixin + veto policy `INFERRED` (no live SPM) |
| VR-T3m | backpack banking | floor pickup recovery | `ContainerMerge` + F8 ordering in transaction `CONFIRMED` (static) |

**Runtime VR-T3a–c/k/l/m:** `UNVERIFIED` — Minecraft launch not authorized.

## Deliverables

| Path | Role |
| --- | --- |
| `village/crop/CropReplantSemantics.java` | vanilla-only crop semantics |
| `village/crop/ManagedCropDomainPolicy.java` | `managedCropCell` |
| `village/crop/HarvestCandidatePolicy.java` | deterministic candidacy |
| `village/crop/HarvestCropVetoPolicy.java` | host veto predicate |
| `village/crop/CropHarvestTransaction.java` | PREPARE/COMMIT/abort |
| `village/crop/HarvestCropGuardCompatibility.java` | diagnostics |
| `village/VillageHarvestAdmission.java` | admission wiring (excludes self `VILLAGE_WORK` on continuation) |
| `inventory/ContainerMerge.java` | remainder insert API |
| `goal/VillageHarvestEpisodeGoal.java` | P4 episode |
| `compat/OptionalHarvestCropTargetResolver.java` | reflective target |
| `mixin/HarvestCropsManagedDomainMixin.java` | continuous veto |
| `SpmScavenger.java` | P4 registration + compat lifecycle |
| `MoveHolderClassifier.java` | `VILLAGE_WORK` pin |

## Concerns

1. **Runtime witness deferred** — mixin hook observation and multi-mob commit races need live SPM + village fields.
2. **`VillageHarvestAdmission` self-exclusion** — continuation observation filters running `VILLAGE_WORK`; without this, `MandatoryOwnership` would deny the episode while it runs. Documented in class javadoc; not runtime-proven.
3. **Farmland support** — uses `FarmBlock` below crop (public API); not full `canSurvive` (protected).

## Self-review vs brief

- Two predicates split: **YES**
- Gen-1 crops include wheat: **YES**
- No `ForagePolicy` / SPM compile imports in crop package: **YES** (structural test)
- No second `MandatoryOwnership` publisher: **YES**
- Single `getDrops` at COMMIT only: **YES** (structural test)
- Held-seed rule for wheat/beetroot: **YES** (`HarvestCandidatePolicyTest`)
