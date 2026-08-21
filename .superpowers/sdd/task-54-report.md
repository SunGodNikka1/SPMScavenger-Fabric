# Task 54 report: V3-B minimum `StorageOwnership` + host `RaidContainersGoal` guard

**Status:** `DONE_WITH_CONCERNS` (post **R1 repair**)

**Brief:** `.superpowers/sdd/task-54-brief.md` v3.2  
**Gate 0:** `.superpowers/sdd/task-54-gate0-report.md` — PASS

---

## Summary

Explicit `GlobalPos`-keyed storage permission registry, diagnostic ownership classification, ally-only `StorageRaidPolicy` enforcement on pinned SPM `RaidContainersGoal`, `ServerLevel.onBlockStateChange` lifecycle invalidation, and operator storage commands.

**R1 repair (2026-08-21):** fixed selective `forget`, pure logical-identity lifecycle predicate, fail-closed continuation on unresolved mob, compatibility session lifecycle, and `revoke-key` Brigadier parsing.

---

## Verification

| Command | CWD | Result |
| --- | --- | --- |
| `.\gradlew.bat compileJava` | `Projects/SPMScavenger-1.21.1-Fabric` | **PASS** |
| `.\gradlew.bat test` | same | **PASS** — **1428 tests**, 0 failures |

**Evidence class:** compile + unit/structural/Brigadier-parse tests. Runtime VR-T3g–i and live mixin witness remain **UNVERIFIED** (no Minecraft launch authorized).

---

## R1 repair evidence

| ID | Fix | Evidence |
| --- | --- | --- |
| **R1-1** | `StoragePermissionSavedData.forget` uses reverse index; clears owner attribution without revoking row; prunes shared-only rows | `StoragePermissionSavedDataForgetTest` (3 scenarios + save/reload each); `StoragePermissionSavedDataTest.forgetRemovesOnlyForgottenMobAttribution` |
| **R1-2** | `StorageLogicalIdentity.logicalIdentityChanged` + lifecycle invalidates only on identity change; removed `invalidateOldDoublePartner` | `StorageLogicalIdentityTest` (8 pure cases); `StorageGrantLifecycleTest` (4 grant cases); structural gate |
| **R1-3** | `canContinueToUse`: `mob == null` → `TARGET_RESOLUTION_FAILED` + `false`; non-ally returns to host | `RaidContainersAllyStorageMixin.java` |
| **R1-4** | `observeCanUseHook` at inject entry; `beginServerSession` / `shutdownServerState` / bounded warm-up tick; target-resolution warn | `StorageGuardCompatibilityTest`; `SpmScavenger.java` wiring; corrected `OptionalRaidContainerTargetResolver` warn text |
| **R1-5** | `revoke-key` uses `ResourceLocationArgument.id()` | `VillageStorageCommandsParseTest.revokeKeyParsesNamespacedDimensionAndCoordinates` |

---

## Scenario coverage

| ID | Static result | Notes |
| --- | --- | --- |
| VR-T3g–i | **PASS** (unit) | ally grant-or-deny predicate |
| D1–D3 | **PASS** | tri-state diagnostics |
| C2 | **PASS** | `revoke-key` row delete |
| Lifecycle T1–T4 (subset) | **PASS** (unit) | pure identity + grant invalidation at changed pos only — **not** full `ServerLevel` integration |
| Structural S3–S14 | **PASS** | source gates |
| VR-T3g–i **runtime** | **UNVERIFIED** | requires approved `runClient` + ally mob at chest |
| Live mixin hooks operational | **UNVERIFIED** | compatibility flags need production hook execution |

---

## Architecture invariants (CONFIRMED — post-R1)

- `forget` never calls `revokeKey` for owner removal — shared mobs on same row survive.
- Lifecycle invalidates at **old** canonical key only when `StorageLogicalIdentity.logicalIdentityChanged` is true (includes SINGLE↔double and connected-direction change).
- `StorageRaidPolicy` does not import `StorageGuardCompatibility`.
- Ally `canUse` RETURN veto calls `clearTarget(goal)` before `false`.
- Chunk unload does not delete grants.

---

## Concerns / deferred

1. **Runtime ally guard** — mixin `require = 0`; warm-up log is diagnostic only.
2. **`ServerLevel.onBlockStateChange` integration** — lifecycle logic unit-tested via test seam; no in-process block-place simulation in CI.
3. **Command C1/C3 execution** — parse proven for `revoke-key`; loaded-chunk create/refuse paths not Brigadier-executed in CI.

No git commit (user did not request).
