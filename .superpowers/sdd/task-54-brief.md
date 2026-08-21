# Task 54 brief: V3-B minimum `StorageOwnership` + host `RaidContainersGoal` guard (`D-VR-081`, `D-VR-017`)

**Slice:** `GlobalPos`-keyed explicit storage permission registry + `StorageOwnership` classifier +
continuous admission/continuation guard on pinned SPM `RaidContainersGoal`. **Authorization:** brief
only — User, 2026-08-20. **Implementation is NOT authorized** until User separately says
**authorize task-54** or **Implement V3-B**.

**Target:** `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

**Source reference:** `d:\Apps\Minecraft Port\Projects\references\SocialPlayerMobs-v0.86.0`

**Source evidence (host loot goal — read-only oracle):**

| Path | Evidence |
| --- | --- |
| `.../entity/goal/RaidContainersGoal.java` | Priority-3 loot goal; `canUse()` scans loaded chunks, sets `targetPos`, returns true; `canContinueToUse()` while `targetPos != null`; `isLootableContainer` = chest/barrel/shulker only |
| `.../entity/PlayerMobEntity.java:835` | Registers `RaidContainersGoal` at priority **3** |
| `.../entity/goal/RaidContainersGoal.java:240-247` | Mid-raid bail when BE is no longer a `Container` — **does not** consult ally policy today |

**Depends on (task-53 — DONE):** `VillageScenarioProfile`, `PlayerMobVillagePolicySavedData`,
`VillageWorkAdmission` (separate concern — **must not** be merged into storage admission).

**RFC:** `plans/RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md` — D-VR-017, D-VR-081, VR-T3g–i.

**Not authorized:** Minecraft runtime launch · commit · push · V3 executors (crop/compost/population) ·
`VillageWorkSelector` · global `RaidContainersGoal` removal · new `MandatoryOwnership` publisher ·
full personal/village chest economy · `VILLAGE_PUBLIC` operator-free auto-permission.

```text
VillageScenarioProfile (task-53)
        │
        ▼
StorageOwnershipClassifier ──► StorageOwnership enum
        ▲                              │
StoragePermissionSavedData             │
(explicit own/share grants)            │
        │                              │
        └──────────► StorageRaidPolicy.mayLoot(mob, level, pos)
                              │
                              ▼
              RaidContainersAllyStorageMixin
              (canUse + canContinueToUse only)
```

## Why this slice exists

SPM `RaidContainersGoal` treats every visible chest/barrel/shulker alike (`CODE_CONFIRMED` — pinned
host). For a `VILLAGE_ALLY` PlayerMob that is **dangerous**: village public storage must be **denied**,
unknown ownership must **fail closed**, and only **explicit** mob-owned or operator-shared containers
may loot (`D-VR-017`). Task-53 locked profile authority; task-54 implements the **positive permission
half** (`D-VR-081`) and the **continuous host guard** — not a one-shot admission strip.

**Peer review baseline (User, 2026-08-20):** task-54 must resolve concrete Fabric **1.21.1**
destruction/replacement seams, double-chest identity, persistent explicit sharing, continuous guard,
non-ally unchanged behaviour, RET-1 cleanup, and **bounded** stale-grant handling. **Chunk unload and
block-entity unload are not destruction.**

## Load-bearing requirements

| # | Requirement |
| --- | --- |
| 1 | **`StorageOwnership` enum** outputs exactly: `MOB_OWNED`, `EXPLICITLY_SHARED_WITH_MOB`, `VILLAGE_PUBLIC`, `FOREIGN`, `UNKNOWN` — semantics locked by `D-VR-017`. |
| 2 | **Positive permission is explicit only.** `MOB_OWNED` and `EXPLICITLY_SHARED_WITH_MOB` come **only** from the permission registry (operator commands in gen-1). HOME/HIGH/trade/village discovery **must not** create grants. |
| 3 | **Registry keys are canonical `GlobalPos`.** Never naked `BlockPos`. Double-chest halves **must** resolve to one key before classify, grant, revoke, or invalidation. |
| 4 | **Semantic persistence (D-VR-081):** grants survive chunk unload, mob dimension change, and server restart. Normal deletion: explicit revoke, container destruction/replacement, bounded stale cleanup on loaded validation, and permanent mob removal via `PerMobSavedData.forgetAll`. |
| 5 | **Do not treat chunk unload or `BlockEntity` unload as destruction.** Grants **preserve** on unload; invalidation requires a **replacement/destruction** signal or loaded-chunk proof the container is gone. |
| 6 | **Continuous guard** on pinned `RaidContainersGoal` at **`canUse` (after candidate selection) and `canContinueToUse` (every evaluation)** — not global goal removal, not mixin-only on `canUse`. |
| 7 | **Non-ally behaviour unchanged.** When `profile != VILLAGE_ALLY`, `StorageRaidPolicy` is a **no-op permit** — host scheduling unchanged. |
| 8 | **Ally predicate (D-VR-017):** when `profile == VILLAGE_ALLY`, permit **only** if ownership ∈ `{MOB_OWNED, EXPLICITLY_SHARED_WITH_MOB}` **for that mob**; `{VILLAGE_PUBLIC, FOREIGN, UNKNOWN}` → deny admission **and** continuation. |
| 9 | **`MandatoryOwnership` untouched.** Storage guard **does not** publish or consume `MandatoryOwnership` / `MandatoryOwnershipRegistry`. Task-54 must not add a second mandatory publisher unless implementation discovers a proven gap — **brief assumes none**; record in report if that assumption breaks. |
| 10 | **RET-1:** register mob-attributed grant cleanup in `PerMobSavedData.forgetAll()`; document key, bound, production eviction call sites; cleanup must not materialize empty SavedData files. |
| 11 | **Bounded stale-grant handling:** when a grant exists but a **loaded** chunk proves the canonical position is not a lootable container (same predicate as host `isLootableContainer`), **delete the grant** and classify `UNKNOWN`. No silent cap eviction of live ally assignments analogous to village-memory LRU. |
| 12 | **Mixin contract:** new `@Pseudo` goal mixin must name **both** readable and intermediary goal methods (`method_6264` / `method_6266`) per `SpmGoalMixinNamingTest`; policy lives outside mixin package (`compat` resolver pattern). |

## Deliverables (implementation-authorized later)

| Path | Role |
| --- | --- |
| `village/storage/StorageOwnership.java` | **new** — classifier output enum |
| `village/storage/ContainerCanonicalPos.java` | **new** — `GlobalPos` canonicalization (double chest) |
| `village/storage/StoragePermissionSavedData.java` | **new** — persisted explicit grants keyed by canonical `GlobalPos` |
| `village/storage/StorageOwnershipClassifier.java` | **new** — pure classify API; loaded-chunk validation + stale purge |
| `village/storage/StorageRaidPolicy.java` | **new** — `mayLoot(mob, level, pos)` = profile gate + D-VR-017 predicate |
| `village/storage/StorageGrantLifecycle.java` | **new** — invalidation hooks (destruction/replacement + load validation) |
| `mixin/RaidContainersAllyStorageMixin.java` | **new** — `@Pseudo` inject on `RaidContainersGoal` only |
| `command/VillageStorageCommands.java` | **new** — operator grant/revoke/query (see Commands) |
| `PerMobSavedData.java` | register mob grant sweep |
| `SpmScavenger.java` | register lifecycle hooks + commands |
| Tests | VR-T3g–i scenarios + structural negatives + mixin contract |

**Explicitly NOT in this slice:**

- Crop/compost/population executors (V3-C/D/E/F)
- `VillageWorkAdmission` changes (already profile + `MandatoryOwnership` only)
- Stripping or disabling `RaidContainersGoal` globally
- Automatic share from settlement relationship / trade success
- Second `MandatoryOwnership` publisher without documented proof
- Full village chest claiming / personal storage UI

---

## Double-chest canonical identity (LOCKED for gen-1)

**Rule:** map any lootable container position to **one canonical block position** before any registry
or classifier operation.

```text
canonicalPos(level, pos):
    if pos is a double-chest half:
        other = ChestBlock.getConnectedBlockPos(pos, blockState)
        return lexicographically smaller BlockPos among {pos, other}
            (BlockPos#compareTo — Y, then Z, then X order in 1.21.1)
    else:
        return pos

canonicalGlobal(level, pos) = GlobalPos.of(level.dimension(), canonicalPos(level, pos))
```

**Must happen:**

- Grant at either half → stored under canonical `GlobalPos`
- Classifier at either half → same ownership class
- Guard when `targetPos` is either half → same decision
- Invalidation at either half → removes canonical grant once

**Must not happen:** two registry rows for one double chest; opposite half reads as `UNKNOWN` while
the other reads `MOB_OWNED`.

**Test vectors:** LEFT+RIGHT pair (both orientations), single chest unchanged, barrel/shulker
unchanged (no pairing).

---

## Permission registry (`StoragePermissionSavedData`)

### Semantics

```text
no row at canonical GlobalPos
    = no explicit permission (classifier may still yield VILLAGE_PUBLIC / FOREIGN / UNKNOWN)


ownerMobId set at GlobalPos
    → MOB_OWNED for that mob (and only that mob unless also shared)


sharedMobIds contains mobId
    → EXPLICITLY_SHARED_WITH_MOB for that mob


set owner / share
    → get(server) — allocating write


revoke / invalidate
    → peek(server) — non-creating delete


operator revoke all for mob
    → remove mob as owner; remove mob from all share sets; delete empty rows
```

**Hosting (recommended — mirror task-53):** one server-canonical `SavedData` on **Overworld**
`DataStorage`, keyed by `GlobalPos` (dimension included in key). Reads that must not allocate use
`peek`; writes use `get`.

**Reverse index (RET-1):** maintain `mobId → Set<GlobalPos>` inside the same store (or recomputable
on forget — brief prefers **explicit reverse index** so `forgetAll(mobId)` is O(grants for mob), not
O(all grants in server)).

### RET-1 contract

| Question | Answer |
| --- | --- |
| Key | canonical `GlobalPos` (+ reverse `UUID → grants`) |
| Bound | one row per explicitly granted container; reverse index sized to grants — **no silent LRU demotion** |
| Normal eviction | explicit revoke; destruction/replacement invalidation; loaded stale validation; **`PerMobSavedData.forgetAll(mobId)`** |
| Chunk unload | **preserve** |
| Block entity unload | **preserve** |
| Server stop | **persist** |
| `forgetAll` path | **peek-only** — must not materialize save file for mob that never had grants |

**Rejected:** copying `VillageMemorySavedData.MAX_TRACKED_MOBS` silent eviction; deleting grants on
chunk unload; dimension-local permission files without `GlobalPos.dimension()`.

---

## Classifier (`StorageOwnershipClassifier`)

**Pure function** (unit-testable without booting a world where possible):

```java
StorageOwnership classify(
        ServerLevel level,
        UUID mobId,
        BlockPos pos)   // raw candidate; canonicalized internally
```

**Decision order (locked — first match wins):**

| Step | Condition | Result |
| --- | --- | --- |
| C1 | Chunk at canonical pos **not loaded** | `UNKNOWN` (ally fail-closed; host scan rarely picks these anyway) |
| C2 | Loaded chunk: block entity **not** lootable container (host-equivalent predicate) | **purge grant if present** → `UNKNOWN` |
| C3 | Registry: `ownerMobId == mobId` | `MOB_OWNED` |
| C4 | Registry: `mobId ∈ sharedMobIds` | `EXPLICITLY_SHARED_WITH_MOB` |
| C5 | Loaded: `VillagePerception.observe(level, canonicalPos).isSettlement()` | `VILLAGE_PUBLIC` |
| C6 | Loaded lootable container, not public, no grant | `FOREIGN` |

**Notes:**

- C5 reuses existing settlement predicate — **no second village scanner**.
- C2 is the **bounded stale-grant handler**: invalid evidence deletes persisted permission, does not
  merely return `UNKNOWN` while leaving a lying row.
- Classifier **must not** read `SettlementRelationship`, HOME/HIGH bands, or `VillageWorkAdmission`.

**`StorageRaidPolicy` (guard entry point):**

```java
boolean mayLoot(PlayerMob mob, ServerLevel level, BlockPos targetPos) {
    if (PlayerMobVillagePolicySavedData.profileOf(...) != VILLAGE_ALLY) {
        return true;   // non-ally: host unchanged
    }
    return switch (classifier.classify(...)) {
        case MOB_OWNED, EXPLICITLY_SHARED_WITH_MOB -> true;
        default -> false;
    };
}
```

---

## Fabric 1.21.1 destruction / replacement lifecycle seam (LOCKED)

**Problem:** grants must survive unload but die when a chest is broken or replaced while the server
still cares about permissions.

### Primary invalidation seam (production — CONFIRMED approach)

**Mixin `BlockEntity#setRemoved`** in addon (not SPM):

```text
@Inject(method = "setRemoved", at = @At("HEAD"))
onBlockEntityRemoved(RemovalReason reason):
    if reason == UNLOADED:
        return                    // NOT destruction — D-VR-081 / User directive
    invalidateGrantsAt(canonicalGlobalPos(level, pos))
```

`BlockEntity.RemovalReason.UNLOADED` is the chunk-unload path. **`CHANGED` / `DISCARDED` / other
non-`UNLOADED` reasons** invalidate grants at the canonical position.

**Evidence class:** pinned Minecraft 1.21.1 `BlockEntity#setRemoved` assigns reason before removal;
chunk unload uses `UNLOADED` (same pattern as entity unload semantics documented in
`VillageMemorySavedData` javadoc).

### Secondary seam (block replaced without block entity callback ordering)

**Block state change at canonical pos** where the new state is **not** a lootable container block
(chest/barrel/shulker base block):

```text
Hook: Fabric ServerBlockEntityEvents.BLOCK_ENTITY_LOAD (validation)
   OR mixin tail of Level#setBlock / Block#onRemove on server side
```

**Brief locks minimum:** primary `setRemoved` **plus** classifier C2 on loaded access. If implementer
finds a replacement path that bypasses `setRemoved` with `UNLOADED` only, add the secondary hook and
document it in the report — do not ship with a known bypass.

### Explicitly rejected invalidation triggers

| Trigger | Verdict |
| --- | --- |
| Chunk unload | **preserve grant** |
| `BlockEntity` removal reason `UNLOADED` | **preserve grant** |
| Mob dimension change | **preserve grant** |
| Server stop | **persist grant** |
| Staleness TTL alone (time since last seen) | **rejected** — use loaded proof (C2), not age |

### Bounded stale-grant handling (summary)

| When | Action |
| --- | --- |
| Classify/guard with **loaded** chunk, container gone | **delete grant** + `UNKNOWN` |
| Classify with **unloaded** chunk | `UNKNOWN`, grant **preserved** |
| `BLOCK_ENTITY_LOAD` at canonical pos | optional eager C2 validation (same as classify) — **must remain bounded** (single pos, no scan) |

---

## Continuous `RaidContainersGoal` guard

### Mixin target and injection sites

**New mixin** — `RaidContainersAllyStorageMixin` — **`@Pseudo`**, targets **only**
`games.brennan.playermob.entity.goal.RaidContainersGoal` (do **not** extend
`FriendlyGreetShelterHoldMixin`; shelter hold and storage policy are orthogonal).

| Method | Injection | Behaviour |
| --- | --- | --- |
| `canUse` | `@At` **after** `targetPos` is assigned from scan, **before** return true | if `!StorageRaidPolicy.mayLoot(mob, level, targetPos)` → return false |
| `canContinueToUse` | `@At("HEAD")`, cancellable | if `targetPos != null` and `!mayLoot(...)` → return false |

**Resolve mob** via `OptionalGoalMobResolver` (compat package — not mixin package).

**Method names:** `method = {"canUse", "method_6264"}` and `{"canContinueToUse", "method_6266"}`;
`require = 0` with **`SpmGoalMixinNamingTest` extended** or dedicated contract test for this mixin.

### Why continuation matters

Host goal can hold `targetPos` across OPENING/LOOTING phases for many ticks. Permission or ownership
can change, or a grant can be invalidated while running. **VR-T3g–i require continuation denial**, not
only admission denial.

### Non-ally control (VR-T3i)

| Profile | Guard effect |
| --- | --- |
| `NEUTRAL` (default) | mixin calls policy → **always permit** → byte-identical to stock host |
| `VILLAGE_ALLY` | enforce D-VR-017 |

**Must not happen:** mixin returns false for non-allies; global `@Disable` of `RaidContainersGoal`.

---

## Commands (gen-1 explicit assignment)

All `/spmscavenger village storage …` subcommands require **operator permission** (`hasPermission(2)`).
Target positions use block hit or explicit coordinates; **always canonicalize** before storage.

```text
/spmscavenger village storage get <x> <y> <z>     # classify at canonical GlobalPos (optional @mob context)
/spmscavenger village storage own <mob> <x> <y> <z>
/spmscavenger village storage share <mob> <x> <y> <z>
/spmscavenger village storage unshare <mob> <x> <y> <z>
/spmscavenger village storage revoke <x> <y> <z>   # remove entire grant row
/spmscavenger village storage list <mob>           # list explicit grants for mob
```

**Properties:**

- Non-PlayerMob entity args → reject with explicit feedback
- `own` / `share` use **`get(server)`**; `revoke` / `unshare` use **`peek`**
- Commands **must not** set `VILLAGE_ALLY` profile (task-53 commands own that)
- Sharing is **persistent explicit** — not session-scoped

**Out of scope:** mass-default village sharing; marking `VILLAGE_PUBLIC` via command (public is
**classified**, not granted).

---

## Constraints

- **TDD required** for classifier ordering, canonical pos, policy predicate, and mixin contract.
- **SPM stays stock** — addon mixin only; no fork.
- **No `MandatoryOwnership` changes** without a report-blocker proving task-54 cannot close VR-T3g–i
  otherwise.
- **`VillageWorkAdmission` unchanged** — storage is parallel safety, not village-work admission.
- Reuse host lootable-container predicate — **one definition**, referenced by classifier C2 and
  documented as shared with `RaidContainersGoal` semantics.

---

## Verification — VR-T3g–i + structural negatives

| ID | Scenario | Expected |
| --- | --- | --- |
| **VR-T3g** | `VILLAGE_ALLY` + container classified `VILLAGE_PUBLIC` | `mayLoot` false; `canUse`/`canContinueToUse` denied | 
| **VR-T3h** | `VILLAGE_ALLY` + `UNKNOWN` | fail closed; container untouched by guard denial before open |
| **VR-T3i-a** | `NEUTRAL` + any ownership | guard permits (host unchanged) |
| **VR-T3i-b** | `VILLAGE_ALLY` + `MOB_OWNED` / `EXPLICITLY_SHARED_WITH_MOB` | guard permits |
| **VR-T3i-c** | `VILLAGE_ALLY` + explicit grant but `VILLAGE_PUBLIC` settlement classification | **grant wins** — C3/C4 before C5; permit |

### Store / lifecycle tests

| # | Test | Expected |
| --- | --- | --- |
| L1 | Grant survives chunk unload reload | row persists |
| L2 | `setRemoved(UNLOADED)` on chest BE | grant **preserved** |
| L3 | `setRemoved(CHANGED)` or break to air | grant **removed** |
| L4 | Loaded classify, chest replaced with stone | C2 purges grant |
| L5 | Double chest: share one half, classify other | same class / same grant |
| L6 | `forgetAll(mobId)` | owner + share rows for mob gone; peek-only |
| L7 | Revoke uses peek on empty store | no file materialization |

### Structural negatives

| ID | Check | Must fail if |
| --- | --- | --- |
| S1 | Grant keyed by naked `BlockPos` | no `GlobalPos` in SavedData |
| S2 | Unload hook deletes grants | `SpmScavenger` / lifecycle registers unload eviction |
| S3 | `MandatoryOwnership` in storage guard | grep `StorageRaidPolicy` / mixin |
| S4 | HOME/HIGH creates grants | grep settlement relationship in grant paths |
| S5 | Mixin only on `canUse` | no `canContinueToUse` inject |
| S6 | Non-ally denied | policy returns false for `NEUTRAL` |
| S7 | Double-chest duplicate rows | canonicalization test |
| S8 | `forgetAll` uses `computeIfAbsent` | materialization test |
| S9 | Classifier imports `VillageWorkAdmission` | wiring test |
| S10 | New SavedData with UUID not in `PerMobSavedData` | `PerMobRemovalContractTest` |

### Commands

```powershell
cd "d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric"
.\gradlew.bat compileJava
.\gradlew.bat test
```

---

## Docs to update (when implementation is authorized)

| File | Update |
| --- | --- |
| `docs/porting/TEST_MATRIX.md` | VR-T3g–i + task-54 lifecycle rows |
| `plans/RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md` | V3-B status; destruction seam pin |
| `.superpowers/sdd/progress.md` | append when task accepted |

## Report (when implemented)

`.superpowers/sdd/task-54-report.md` — map every brief requirement; label evidence
`CONFIRMED`/`INFERRED`/`UNVERIFIED`; runtime VR-T3g–i remain `UNVERIFIED` until approved launch.

**Do not mark ally-safe looting `CONFIRMED` from unit tests alone** (AV-1).

---

## Open items for implementer (not blockers for brief lock)

| Item | Brief recommendation | If wrong, report as `NEEDS_CONTEXT` |
| --- | --- | --- |
| Secondary block-replace hook beyond `setRemoved` | Add only if reproducer found | Required before DONE if bypass exists |
| `BLOCK_ENTITY_LOAD` eager validation | Optional optimization of C2 | Not required for DONE |
| Reverse-index encoding | Inline in same SavedData | Alternative if simpler |

## Deferred (name in report, do not implement in task-54)

| Item | When |
| --- | --- |
| Full village/personal storage economy | Post-V3 minimum |
| Auto-share on trade / HOME promotion | **Rejected forever** per D-VR-017 |
| `HarvestCropsGoal` / other host goal guards | Only if separate RFC task — not V3-B minimum |
| Runtime VR-T3 batch | V3-G / user-approved launch |
