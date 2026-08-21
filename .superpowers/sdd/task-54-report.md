# Task 54 report: V3-B minimum `StorageOwnership` + host `RaidContainersGoal` guard

**Status:** `DONE_WITH_CONCERNS`

**Brief:** `.superpowers/sdd/task-54-brief.md` v3.2  
**Gate 0:** `.superpowers/sdd/task-54-gate0-report.md` — PASS (pre-implementation)

---

## Summary

Implemented explicit `GlobalPos`-keyed storage permission registry, pure diagnostic ownership classification, ally-only hot-path enforcement on pinned SPM `RaidContainersGoal`, block-state lifecycle invalidation via `ServerLevel.onBlockStateChange`, and operator `/spmscavenger village storage …` commands.

---

## Verification

| Command | CWD | Result |
| --- | --- | --- |
| `.\gradlew.bat compileJava` | `Projects/SPMScavenger-1.21.1-Fabric` | **PASS** |
| `.\gradlew.bat test` | same | **PASS** — 1409 tests, 0 failures |

**Evidence class:** compile + unit/structural tests only. Runtime VR-T3g–i remain **UNVERIFIED** (no Minecraft launch authorized).

---

## Deliverables (CONFIRMED — paths exist)

| Path | Role |
| --- | --- |
| `village/storage/StorageOwnership.java` | enum |
| `village/storage/SettlementStorageFact.java` | tri-state diagnostic |
| `village/storage/StorageContainerResolver.java` | loaded truth / canonical identity |
| `village/storage/StorageGrantLifecycle.java` | mutating invalidation |
| `village/storage/StorageOwnershipPolicy.java` | pure diagnostics |
| `village/storage/StoragePermissionSavedData.java` | grants + reverse index |
| `village/storage/StorageRaidPolicy.java` | enforcement — no compatibility import |
| `village/storage/StorageGuardCompatibility.java` | diagnostics observations |
| `village/storage/SettlementStorageFactSource.java` | cheap anchor-radius fact |
| `compat/OptionalRaidContainerTargetResolver.java` | `resolveTarget` / `clearTarget` |
| `mixin/RaidContainersAllyStorageMixin.java` | canUse RETURN + canContinueToUse HEAD |
| `mixin/ServerLevelStorageGrantLifecycleMixin.java` | lifecycle seam |
| `command/VillageStorageCommands.java` | operator commands (chained under profile registrar) |
| `PerMobSavedData.java` | `StoragePermissionSavedData.forgetEverywhere` registered |
| Tests under `src/test/.../village/storage/` | VR-T3, D1–D3, C2, structural S3/S4/S5/S6/S10–S14 |

---

## Scenario coverage

| ID | Static result | Notes |
| --- | --- | --- |
| VR-T3g | **PASS** (unit) | ally without grant → deny path via empty explicit permission |
| VR-T3h | **PASS** (unit) | ally + null level → deny |
| VR-T3i-a | **PASS** | NEUTRAL permits without grant |
| VR-T3i-b/c | **PASS** | explicit owner/share → permit on hot-path predicate |
| D1–D3 | **PASS** | tri-state settlement fact preserved in diagnostics |
| C2 | **PASS** | `revoke-key` removes row without world load |
| S3,S4,S5,S6,S10–S14 | **PASS** | structural source gates |
| Lifecycle T1–T4 | **INFERRED** | invalidation wired to `onBlockStateChange`; no block-state integration test |
| VR-T3g–i runtime | **UNVERIFIED** | requires approved `runClient` + ally mob + chest |

---

## Architecture invariants (CONFIRMED — code inspection)

- `StorageRaidPolicy` does not import or branch on `StorageGuardCompatibility`.
- Ally `canUse` RETURN veto calls `clearTarget(goal)` before `false`.
- Chunk unload does not delete grants (no lifecycle hook on unload path).
- `MandatoryOwnership` untouched — no second publisher.
- Create commands require loaded lootable container; `revoke-key` uses `peek` only.

---

## Concerns / deferred

1. **Runtime ally guard** — mixin `require = 0`; compatibility flags prove wiring only after live hook execution.
2. **Lifecycle topology T1–T4** — logic present; no simulated `ServerLevel` block-state test in CI.
3. **Command C1/C3** — structural intent covered; no Brigadier integration test.
4. **`own`/`share` use `getLoadedBlockPos`** — rejects unloaded chunks (aligned with brief); does not force-load.

---

## Docs touched

- `task-54-brief.md` — Gate 0 diagram label + authorization row
- `progress.md` — task-54 complete
- `docs/porting/TEST_MATRIX.md` — VR-T3g–i static rows

No git commit (user did not request).
