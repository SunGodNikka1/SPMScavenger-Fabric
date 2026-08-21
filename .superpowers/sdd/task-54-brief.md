# Task 54 brief: V3-B minimum `StorageOwnership` + host `RaidContainersGoal` guard (`D-VR-081`, `D-VR-017`)

**Slice:** `GlobalPos`-keyed explicit storage permission registry + ally loot guard on pinned SPM
`RaidContainersGoal`.

**Authorization gates (LOCKED sequence):**

| Gate | Status | User phrase to authorize |
| --- | --- | --- |
| **Brief design** | **v3 — peer-review repairs applied** | (this document) |
| **Gate 0 — read-only source audit** | **CLOSED** — see `task-54-gate0-report.md` | (authorized 2026-08-20) |
| **Gate 0 closure** | **PASS** — lifecycle + mixin shape **LOCKED** | — |
| **Full implementation** | **AUTHORIZED** — user 2026-08-20 | **authorize task-54** / **Implement V3-B** |

**Brief revision history:**

- v1 — initial draft (rejected: `BlockEntity#setRemoved(RemovalReason)`, POI hot path)
- v2 — architecture split, topology policy, command loaded-chunk create gate
- v3 — enforcement/diagnostics separation, Gate 0 sequence, tri-state settlement fact, asymmetric revoke, mixin access/injection pinned to Gate 0
- v3.1 — **Gate 0 CLOSED** (`task-54-gate0-report.md`): lock `ServerLevel.onBlockStateChange`; chest partner via `getConnectedDirection`; ally fail-closed on unresolved `targetPos`
- v3.2 — sync pass: mixin pseudocode matches Gate 0 deny semantics; **`clearTarget(goal)` on `canUse` veto** (RETURN inject writes `targetPos` before our hook)

**Target:** `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

**Source reference:** `d:\Apps\Minecraft Port\Projects\references\SocialPlayerMobs-v0.86.0`

**Source evidence (host loot goal — read-only oracle):**

| Path | Evidence |
| --- | --- |
| `.../entity/goal/RaidContainersGoal.java` | Priority-3 loot goal; `canUse()` scans loaded chunks, sets `targetPos`, returns true; `canContinueToUse()` while `targetPos != null`; private field `targetPos` |
| `.../entity/PlayerMobEntity.java:835` | Registers `RaidContainersGoal` at priority **3** |
| `.../entity/goal/RaidContainersGoal.java:240-247` | Mid-raid bail when BE is no longer a `Container` — **does not** consult ally policy today |

**Depends on (task-53 — DONE):** `VillageScenarioProfile`, `PlayerMobVillagePolicySavedData`,
`VillageWorkAdmission` (separate concern — **must not** be merged into storage admission).

**RFC:** `plans/RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md` — D-VR-017, D-VR-081, VR-T3g–i.

**Not authorized without separate implementation authorization:** production Java · mixin wiring · Minecraft runtime launch · commit · push.

```text
                   explicit operator grants
                            │
                            ▼
               StoragePermissionSavedData
                  GlobalPos + reverse UUID
                            │
             ┌──────────────┴──────────────┐
             ▼                             ▼
StorageContainerResolver          StorageGrantLifecycle
 loaded/non-loading truth         block-state change hook
 canonical logical identity       (Gate 0 — LOCKED: ServerLevel.onBlockStateChange)
 chest topology invalidation       stale-row cleanup
             │                             │
             └──────────────┬──────────────┘
                            ▼
                  StorageOwnershipPolicy
                    PURE diagnostics only
                            │
              ┌─────────────┴─────────────┐
              ▼                           ▼
     diagnostics / `storage get`    StorageRaidPolicy
                                    ENFORCEMENT ONLY
                                    (no compatibility import)
                                            ▲
                                            │
                            RaidContainersAllyStorageMixin
                              canUse RETURN veto
                              canContinueToUse HEAD veto
                                            │
                            StorageGuardCompatibility
                              DIAGNOSTICS ONLY — never
                              participates in mayLoot()
```

## Why this slice exists

SPM `RaidContainersGoal` treats every visible chest/barrel/shulker alike (`CODE_CONFIRMED` — pinned
host). For a `VILLAGE_ALLY` PlayerMob that is **dangerous**: only **explicit** mob-owned or
operator-shared containers may loot (`D-VR-017` / `D-VR-081`). Task-53 locked profile authority;
task-54 implements the **positive permission registry** and the **continuous host guard**.

---

## Task-54 Gate 0 — read-only source audit (authorize separately)

**Purpose:** lock the two production integration points that v3 deliberately leaves open before any
implementation code ships.

**Deliverable:** `.superpowers/sdd/task-54-gate0-report.md` — read-only; no production Java except
optional pinned-source notes. Updates this brief's **LOCKED** sections when audit passes.

### Gate 0-A — lifecycle invalidation hook (**LOCKED — Gate 0 CLOSED**)

**Primary seam (CONFIRMED — Mojang 1.21.1 decompiled sources + call-chain trace):**

```text
Level.setBlock(...)
    → LevelChunk.setBlockState(...)   // oldState.onRemove / newState.onPlace
    → Level.onBlockStateChange(pos, oldState, newState)
        → ServerLevel.onBlockStateChange(...)   // ← MIXIN HEAD → StorageGrantLifecycle
```

```java
// LOCKED mixin target:
// net.minecraft.server.level.ServerLevel#onBlockStateChange(BlockPos, BlockState, BlockState)
//   param2 = OLD state, param3 = NEW state
```

**Why not `BlockBehaviour.onRemove` alone:** per-block callback inside chunk; same old/new data but
wider mixin surface. Use as **supplemental** only if Gate 0 report bypass reproducer appears.

**Why not `BlockEntity#setRemoved(RemovalReason)`:** API **does not exist** on `BlockEntity` in 1.21.1
(`setRemoved()` boolean flag only). Chunk unload uses `clearAllBlockEntities()` → `setRemoved()` with
**no** `Level.setBlock` / `onBlockStateChange` (`ServerLevel.unload`, `LevelChunk.java` 614–618).

**Gate 0 audit answers (G0-1…G0-6):** all **PASS** — see `task-54-gate0-report.md`.

### Gate 0-B — host `RaidContainersGoal` integration shape (**LOCKED — Gate 0 CLOSED**)

| # | Decision | **Locked choice** |
| --- | --- | --- |
| G0-B1 | **`targetPos` / `mob` access** | **`OptionalRaidContainerTargetResolver`** in `compat` — **`resolveTarget(goal)`** and **`clearTarget(goal)`** only (no arbitrary reflective writes). **`mob`** via existing `OptionalGoalMobResolver`. Field names **CONFIRMED** in pinned jar (`javap`). |
| G0-B2 | **`canUse` inject** | **`@At("RETURN")` cancellable** — veto only when host returned `true`. Methods: `canUse` + `method_6264`. |
| G0-B3 | **`canContinueToUse` inject** | **`@At("HEAD")` cancellable**. Methods: `canContinueToUse` + `method_6266`. |
| G0-B4 | **Jar method names** | Pinned processedMods jar uses **readable** overrides; mixin **still lists intermediary** per `SpmGoalMixinNamingTest`. |

**Ally fail-closed on unresolved target (LOCKED):** when profile is `VILLAGE_ALLY` and host `canUse`
returned `true` but `targetPos` cannot be resolved → **`clearTarget(goal)`**, **deny** (`false`),
record `TARGET_RESOLUTION_FAILED`. **Do not** preserve host `true`. Same deny semantics in
`canContinueToUse` when ally enforcement applies. Non-allies: unchanged.

**`canUse` RETURN veto and host state (LOCKED):** pinned host assigns `targetPos = found` **before**
returning `true` (`RaidContainersGoal.java` 127–128). A storage veto therefore **must not** leave a
stale selected target. On any ally **`canUse` denial** (unresolved target, policy deny, or resolution
failure): **`clearTarget(goal)` then `return false`**. Conceptual invariant: same as host
`canUse() == false` with **no selected raid target** — not `false` with `targetPos` still pointing
at a protected chest. **`canContinueToUse` denial** may rely on host `stop()` clearing `targetPos`
(`RaidContainersGoal.stop()` 161–163) when the selector drops the goal; **`canUse` has no such
reset**.

Full evidence: `task-54-gate0-report.md` § Gate 0-B.

---

## Load-bearing requirements

| # | Requirement |
| --- | --- |
| 1 | **`StorageOwnership` enum:** `MOB_OWNED`, `EXPLICITLY_SHARED_WITH_MOB`, `VILLAGE_PUBLIC`, `FOREIGN`, `UNKNOWN` — `D-VR-017`. |
| 2 | **Explicit grants only** — operator commands in gen-1; no HOME/HIGH/trade auto-grants. |
| 3 | **Registry keys:** canonical `GlobalPos`; never naked `BlockPos`. |
| 4 | **Persistence (D-VR-081):** survive unload/restart/dimension change; delete on replacement/topology invalidation, explicit revoke, stale cleanup, `forgetAll`. |
| 5 | **Unload is not destruction** — no grant deletion on chunk or block-entity unload. |
| 6 | **Continuous guard** — `canUse` + `canContinueToUse` on pinned `RaidContainersGoal`. |
| 7 | **Non-ally unchanged** — `StorageRaidPolicy` no-op permit when profile ≠ `VILLAGE_ALLY`. |
| 8 | **Ally enforcement:** loaded valid container + explicit grant → permit; else → deny. **No** `VillagePerception.observe()` on hot path. |
| 9 | **`MandatoryOwnership` untouched** — no second publisher. |
| 10 | **RET-1** — `PerMobSavedData.forgetAll`; peek-only cleanup; no TTL/LRU authority loss. |
| 11 | **Mutations in `StorageGrantLifecycle` only** — not in policy or raid guard. |
| 12 | **Chest topology (gen-1):** SINGLE ↔ LEFT/RIGHT invalidates pre-transition logical grants; re-grant explicitly. |
| 13 | **Enforcement / diagnostics separation:** `StorageRaidPolicy` **must not** import or consult `StorageGuardCompatibility`. If mixin callback runs, enforce. Compatibility tracks wiring only. |
| 14 | **Gate 0 before implementation** — lifecycle hook and mixin shape **LOCKED** in gate0 report. |
| 15 | **Diagnostic settlement fact** — tri-state `SettlementStorageFact`; no boolean collapse of unknown vs outside. |
| 16 | **Asymmetric command evidence** — create authority requires loaded world truth; delete authority may target exact persisted `GlobalPos` without chunk load. |
| 17 | **`canUse` veto clears host target:** ally denial after host `canUse` returned `true` → **`OptionalRaidContainerTargetResolver.clearTarget(goal)`** before `false`. |

---

## Architecture split (LOCKED)

### `StorageContainerResolver`

- Chunk loaded check without loading/generating
- Host-equivalent lootable-container predicate
- Canonical logical identity for **loaded** positions
- `resolveLoaded(level, pos) → Optional<ResolvedContainer>`

**Must not:** mutate SavedData · POI scans · load chunks.

### `StorageGrantLifecycle`

- All grant invalidation / stale-row deletion
- Wired to **Gate-0-locked** block-state change hook
- Computes **pre-transition logical identity** from `oldState` before topology/replacement events

**Must not:** run from `canContinueToUse` every tick.

### `SettlementStorageFact` — tri-state diagnostic input (LOCKED)

```java
enum SettlementStorageFact {
    IN_KNOWN_SETTLEMENT,      // positively inside a remembered settlement anchor radius
    OUTSIDE_KNOWN_SETTLEMENT,   // positively outside all known anchors for this dimension
    UNKNOWN                     // insufficient evidence (no anchors, unloaded context, etc.)
}
```

**Source (gen-1 diagnostics only):** union of `KnownVillage` anchors already in dimension
`VillageMemorySavedData` + `SettlementBoundsPolicy.within(containerPos, anchor)` — **cheap radius
check only**; no fresh `VillagePerception.observe()`.

### `StorageOwnershipPolicy` — pure diagnostics

```java
StorageOwnership classify(
        ResolvedContainerFacts facts,
        SettlementStorageFact settlement,
        UUID mobId,
        GrantSnapshot grants)
```

**Decision order (diagnostics / `storage get` only):**

| Step | Condition | Result |
| --- | --- | --- |
| P1 | Chunk not loaded | `UNKNOWN` |
| P2 | Not a valid lootable logical container | `UNKNOWN` |
| P3 | Grant: owner == mobId | `MOB_OWNED` |
| P4 | Grant: mobId ∈ shared | `EXPLICITLY_SHARED_WITH_MOB` |
| P5 | `settlement == IN_KNOWN_SETTLEMENT` | `VILLAGE_PUBLIC` |
| P6 | `settlement == OUTSIDE_KNOWN_SETTLEMENT` | `FOREIGN` |
| P7 | `settlement == UNKNOWN` (or P1/P2) | `UNKNOWN` |

**Must not:** conflate "no remembered village" with `FOREIGN`.

### `StorageRaidPolicy` — enforcement only (LOCKED)

```java
boolean mayLoot(PlayerMob mob, ServerLevel level, BlockPos targetPos) {
    if (profileOf(mob) != VILLAGE_ALLY) {
        return true;
    }
    Optional<ResolvedContainer> resolved = resolver.resolveLoaded(level, targetPos);
    if (resolved.isEmpty()) {
        return false;
    }
    return grants.hasExplicitPermission(resolved.get().canonicalGlobal(), mob.getUUID());
}
```

**Invariants:**

- **No** `StorageGuardCompatibility` import or branch
- **No** `StorageOwnershipPolicy.classify()` on hot path
- **No** lifecycle mutation
- If mixin callback executes → this logic runs → ally without grant is **denied**

**Equivalence:** ally deny covers `{VILLAGE_PUBLIC, FOREIGN, UNKNOWN}` without computing labels.

### `StorageGuardCompatibility` — diagnostics only (LOCKED)

Tracks whether protection is **known to be wired**. **Never grants permission.**

```java
enum GuardObservation {
    HOST_SHAPE_SUPPORTED,           // pinned SPM goal class + expected methods/fields verified
    CAN_USE_HOOK_OBSERVED,          // canUse inject callback executed at least once
    CONTINUATION_HOOK_OBSERVED      // canContinueToUse inject callback executed at least once
}
```

**Separate flags** — observing `canUse` does **not** prove `canContinueToUse` injected (`require=0`
history; `SpmGoalMixinNamingTest` exists because silent misses happened before).

| Signal | Use |
| --- | --- |
| All three true | Log/info: ally storage guard operational |
| SPM absent | No observations expected; no error |
| SPM present, any false after server warm-up | **WARN/ERROR** — ally storage safety **UNVERIFIED**; operators must not assume protection |
| Mixin completely failed | Callbacks never run — **policy never invoked**; compatibility reports false; **must not** be masked by a permissive flag inside `mayLoot` |
| `TARGET_RESOLUTION_FAILED` | Ally guard ran but `targetPos`/`mob` unreadable — enforcement **denied** (fail closed) |

**Structural tests (CI):** dual method names on mixin; **`StorageRaidPolicy` must not reference
`StorageGuardCompatibility`**; dedicated test that compatibility type is not on raid policy classpath
wiring.

**Runtime observation:** set flags from inject callbacks (first execution). Optional server-start
shape probe for `HOST_SHAPE_SUPPORTED`.

---

## Double-chest canonical identity (stable topology)

When the loaded chest topology is already stable (single, or an established LEFT/RIGHT pair):

```text
canonicalPos(level, pos):
    state = level.getBlockState(pos)
    if chest half (ChestType != SINGLE):
        dir = ChestBlock.getConnectedDirection(state)
        partner = pos.relative(dir)   // Gate 0: getConnectedBlockPos NOT FOUND in 1.21.1
        return lexicographically smaller BlockPos among {pos, partner}
    else:
        return pos

canonicalGlobal = GlobalPos.of(level.dimension(), canonicalPos)
```

**Evidence:** `ChestBlock.getConnectedDirection` — Mojang 1.21.1 sources lines 182–184; Gate 0 report.

---

## Chest topology transitions (LOCKED — gen-1)

(Unchanged — SINGLE ↔ LEFT/RIGHT invalidates pre-transition grants; tests T1–T4.)

---

## Permission registry (`StoragePermissionSavedData`)

Overworld-canonical `SavedData`; `GlobalPos` keys; reverse UUID index; peek/get discipline; RET-1.

---

## Continuous `RaidContainersGoal` guard

### Mixin (shape locked at Gate 0-B)

**New mixin** — `RaidContainersAllyStorageMixin` — `@Pseudo`, **only** `RaidContainersGoal`.

**LOCKED pattern:**

```text
canUse @ RETURN (cancellable):
    if host returned false → leave false
    if profile != VILLAGE_ALLY → leave true            // non-ally unchanged
    if host returned true:
        mob = OptionalGoalMobResolver.resolve(this)
        target = OptionalRaidContainerTargetResolver.resolveTarget(this)
        if mob null OR target empty:
            clearTarget(this)                          // idempotent
            TARGET_RESOLUTION_FAILED
            return false                                 // fail closed — NOT leave true
        if !StorageRaidPolicy.mayLoot(mob, level, target):
            clearTarget(this)                          // host wrote targetPos before return
            return false

canContinueToUse @ HEAD (cancellable):
    if profile != VILLAGE_ALLY → leave host result
    mob = OptionalGoalMobResolver.resolve(this)
    target = OptionalRaidContainerTargetResolver.resolveTarget(this)
    if mob null OR target empty:
        return false                                   // ally fail closed
    if !StorageRaidPolicy.mayLoot(mob, level, target):
        return false                                   // host stop() clears targetPos on drop
```

**Compat API (LOCKED):** `OptionalRaidContainerTargetResolver` exposes only
`resolveTarget(Object goal)` and `clearTarget(Object goal)` — set `targetPos` to `null` via cached
reflective field access; no other host mutation surface.

**Method names:** readable + intermediary per `SpmGoalMixinNamingTest`.

**`require = 0`** acceptable with: dual-name structural test + separate continuation observation +
compatibility diagnostics — **not** with permissive fallback inside `mayLoot`.

---

## Commands (gen-1)

Operator permission level 2.

```text
/spmscavenger village storage get <x> <y> <z> [@mob]
/spmscavenger village storage own <mob> <x> <y> <z>
/spmscavenger village storage share <mob> <x> <y> <z>
/spmscavenger village storage unshare <mob> <x> <y> <z>
/spmscavenger village storage revoke <x> <y> <z>
/spmscavenger village storage revoke-key <dimension> <x> <y> <z>
/spmscavenger village storage list <mob>
```

### Authority evidence asymmetry (LOCKED)

**Creating permission (`own` / `share`):**

```text
require chunk loaded (no load/generate)
require host-equivalent lootable container at target
canonicalize from loaded world truth
write via get(server)
```

**Removing permission (`unshare` / `revoke` / `revoke-key`):**

```text
require only an exact persisted grant identity
MUST NOT require positive loaded world truth
MUST NOT load/generate chunk
```

| Command | Target resolution |
| --- | --- |
| `unshare <mob> <x> <y> <z>` | If chunk **loaded** → canonicalize normally. If **unloaded** → reject positional form; operator uses `revoke-key` or `list` output. |
| `revoke <x> <y> <z>` | Same — loaded positional canonicalize **or** use `revoke-key`. |
| `revoke-key <dimension> <x> <y> <z>` | Delete exact `GlobalPos` row if present via `peek(server)` — **no world query**. |

**`list <mob>`:** print canonical `GlobalPos` keys suitable for `revoke-key` — essential for stale-row
admin when lifecycle cleanup missed.

**`get`:** unloaded → `UNKNOWN` classification context; loaded → resolve + tri-state settlement +
pure policy.

---

## Deliverables

### Gate 0 only (when Gate 0 authorized)

| Path | Role |
| --- | --- |
| `.superpowers/sdd/task-54-gate0-report.md` | Audit answers G0-1…G0-6 + G0-B1…B4; **LOCK** hooks |

### Full implementation (when Gate 0 closed + User authorizes task-54)

| Path | Role |
| --- | --- |
| `village/storage/StorageOwnership.java` | enum |
| `village/storage/SettlementStorageFact.java` | tri-state diagnostic fact |
| `village/storage/StorageContainerResolver.java` | loaded truth / canonical identity |
| `village/storage/StorageGrantLifecycle.java` | mutating invalidation (Gate-0 hook) |
| `village/storage/StorageOwnershipPolicy.java` | pure diagnostics |
| `village/storage/StoragePermissionSavedData.java` | grants + reverse index |
| `village/storage/StorageRaidPolicy.java` | enforcement — **no compatibility import** |
| `village/storage/StorageGuardCompatibility.java` | diagnostics observations only |
| `compat/OptionalRaidContainerTargetResolver.java` | **`resolveTarget` / `clearTarget`** — narrow host `targetPos` boundary |
| `mixin/RaidContainersAllyStorageMixin.java` | guard injects |
| `command/VillageStorageCommands.java` | operator commands |
| `PerMobSavedData.java` | forget sweep |
| Tests | VR-T3g–i, topology, lifecycle, structural negatives |

---

## Verification

### VR-T3 (static)

| ID | Scenario | Expected |
| --- | --- | --- |
| **VR-T3g** | `VILLAGE_ALLY`, no grant, settlement chest | deny |
| **VR-T3h** | `VILLAGE_ALLY`, unloaded / invalid target | deny |
| **VR-T3i-a** | `NEUTRAL` | permit |
| **VR-T3i-b** | `VILLAGE_ALLY` + explicit grant | permit |
| **VR-T3i-c** | `VILLAGE_ALLY` + grant at settlement chest | permit (grant wins hot path) |

### Diagnostic policy tests

| ID | Scenario | Expected |
| --- | --- | --- |
| D1 | No village memory evidence, loaded chest | `UNKNOWN`, **not** `FOREIGN` |
| D2 | Inside known anchor radius | `VILLAGE_PUBLIC` (no grant) |
| D3 | Positively outside all anchors | `FOREIGN` |

### Command tests

| ID | Scenario | Expected |
| --- | --- | --- |
| C1 | `own` on unloaded chunk | refuse |
| C2 | `revoke-key` on stale GlobalPos, chunk unloaded | succeeds; row removed |
| C3 | `revoke` positional, container destroyed, grant stale | refuse positional; `revoke-key` succeeds |

### Structural negatives

| ID | Check |
| --- | --- |
| S1 | Naked `BlockPos` keys |
| S2 | Unload deletes grants |
| S3 | `BlockEntity#setRemoved(RemovalReason)` |
| S4 | `VillagePerception.observe` in `StorageRaidPolicy` |
| S5 | `StorageOwnershipPolicy` mutates SavedData |
| S6 | Mixin missing `canContinueToUse` |
| S7 | Non-ally denied |
| S8 | `own`/`share` on unloaded chunk succeeds |
| S9 | Commands force chunk load |
| S10 | `StorageRaidPolicy` imports `StorageGuardCompatibility` |
| S11 | `guardActive()` or equivalent affects `mayLoot` return |
| S12 | `MandatoryOwnership` in storage guard |
| S13 | `PerMobSavedData` registration missing |
| S14 | Boolean settlement fact (must be tri-state) |
| S15 | `revoke` requires loaded container for exact-key stale repair |

---

## Peer review disposition

| Item | v2 | v3 |
| --- | --- | --- |
| Resolver / lifecycle / policy split | **ACCEPT** | **KEEP** |
| Hot-path grant-or-deny | **ACCEPT** | **KEEP** |
| Topology T1–T4 | **ACCEPT** | **KEEP** |
| `guardActive()` inside `mayLoot` | **BLOCKER** | **REMOVED** — diagnostics only |
| Lifecycle hook TBD at implement time | **BLOCKER** | **Gate 0 required before implementation** |
| Boolean `withinSettlementBounds` | **BLOCKER** | **FIXED** — `SettlementStorageFact` tri-state |
| Revoke requires loaded target | **BLOCKER** | **FIXED** — asymmetric evidence + `revoke-key` |
| `targetPos` access unspecified | **GAP** | **Gate 0-B** |
| `canUse` instruction-level inject | **FIXED** | RETURN veto + **`clearTarget` on ally deny** |

**Full task-54 implementation:** **DONE** — see `task-54-report.md` (`DONE_WITH_CONCERNS`; runtime UNVERIFIED)

**Task-54 Gate 0 source audit:** **CLOSED** — `task-54-gate0-report.md`
