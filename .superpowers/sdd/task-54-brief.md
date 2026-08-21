# Task 54 brief: V3-B minimum `StorageOwnership` + host `RaidContainersGoal` guard (`D-VR-081`, `D-VR-017`)

**Slice:** `GlobalPos`-keyed explicit storage permission registry + ally loot guard on pinned SPM
`RaidContainersGoal`.

**Authorization gates (LOCKED sequence):**

| Gate | Status | User phrase to authorize |
| --- | --- | --- |
| **Brief design** | **v3 — peer-review repairs applied** | (this document) |
| **Gate 0 — read-only source audit** | **NOT AUTHORIZED** | **authorize task-54 Gate 0** |
| **Gate 0 closure** | **BLOCKED** — lifecycle hook + host `targetPos` access must be **LOCKED** in audit report | — |
| **Full implementation** | **NOT AUTHORIZED** | **authorize task-54** / **Implement V3-B** (only after Gate 0 locks hooks) |

**Brief revision history:**

- v1 — initial draft (rejected: `BlockEntity#setRemoved(RemovalReason)`, POI hot path)
- v2 — architecture split, topology policy, command loaded-chunk create gate
- v3 — enforcement/diagnostics separation, Gate 0 sequence, tri-state settlement fact, asymmetric revoke, mixin access/injection pinned to Gate 0

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

**Not authorized without Gate 0 closure:** production Java · mixin wiring · lifecycle hook · Minecraft
runtime launch · commit · push.

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
 canonical logical identity       (Gate 0 — hook TBD)
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

### Gate 0-A — lifecycle invalidation hook

**Leading candidate (must be source-audited, not assumed):**

```text
ServerLevel.onBlockStateChange(BlockPos pos, BlockState oldState, BlockState newState)
```

Also exposed on `Level`. May be preferable to `BlockBehaviour.onRemove(oldState, level, pos, newState, moved)`
because task-54 must observe **same-block-type topology transitions**:

```text
CHEST[SINGLE] → CHEST[LEFT]
CHEST[RIGHT]  → CHEST[SINGLE]
re-pairing    → different connected half
```

**Audit must answer (with pinned class/method paths + evidence class):**

| # | Question | Required answer |
| --- | --- | --- |
| G0-1 | Does the chosen hook receive container → air / container → non-container replacement? | **YES** |
| G0-2 | Does it receive chest SINGLE → LEFT/RIGHT? | **YES** |
| G0-3 | Does it receive chest LEFT/RIGHT → SINGLE? | **YES** |
| G0-4 | Does it receive re-pairing / neighbour change events? | **YES** (or document supplemental hook) |
| G0-5 | Does ordinary **chunk unload** invoke it? | **NO** |
| G0-6 | Does **chunk load / deserialization** falsely invalidate? | **NO** |

**If `onBlockStateChange` passes G0-1…G0-6:** lock it as the central seam:

```text
ServerLevel.onBlockStateChange
        ↓ oldState + newState + pos
StorageGrantLifecycle.onBlockStateChange(...)
```

**Fallback candidates** (compare in report, do not implement until one wins audit):

- `BlockBehaviour.onRemove(oldState, level, pos, newState, moved)`
- Tail of `Level#setBlock` / `Level#removeBlock` (document full call chain)

**Rejected regardless of audit outcome:**

| Hook | Verdict |
| --- | --- |
| `BlockEntity#setRemoved(RemovalReason)` | **REJECT** — API does not exist on `BlockEntity` in 1.21.1 |
| `ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD` → delete grant | **REJECT** — unload ≠ destruction |
| Chunk unload eviction | **REJECT** |

### Gate 0-B — host `RaidContainersGoal` integration shape

Audit pinned SPM distributed jar / reference source and lock:

| # | Decision | Options |
| --- | --- | --- |
| G0-B1 | **`targetPos` access** | **Prefer** `OptionalRaidContainerTargetResolver` in `compat` (reflective/cached, same boundary as `OptionalGoalMobResolver`). **Only** use `@Shadow targetPos` if distributed field name is verified stable across pinned SPM. |
| G0-B2 | **`canUse` injection site** | **Prefer** `@Inject(method = {canUse, method_6264}, at = @At("RETURN"), cancellable = true)` — if host returned `true`, read selected `targetPos`, apply `StorageRaidPolicy` veto (`true → false` only). Keeps host scan untouched; avoids instruction-level anchor on `targetPos = found`. |
| G0-B3 | **`canContinueToUse` injection** | `@At("HEAD")`, cancellable — resolve `targetPos`, apply same policy. |
| G0-B4 | **Field / method names on distributed jar** | Record readable + intermediary names for `SpmGoalMixinNamingTest` extension. |

**Gate 0 closure criterion:** both **lifecycle hook** and **mixin access/injection plan** recorded as
**LOCKED** in `task-54-gate0-report.md` with `CONFIRMED` / `INFERRED` labels. Only then may User
authorize full task-54 implementation.

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

**Structural tests (CI):** dual method names on mixin; **`StorageRaidPolicy` must not reference
`StorageGuardCompatibility`**; dedicated test that compatibility type is not on raid policy classpath
wiring.

**Runtime observation:** set flags from inject callbacks (first execution). Optional server-start
shape probe for `HOST_SHAPE_SUPPORTED`.

---

## Double-chest canonical identity (stable topology)

(Unchanged — lexicographic min of double pair; see v2.)

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

**Recommended pattern (pending Gate 0-B confirmation):**

```text
canUse @ RETURN (cancellable):
    if host returned false → leave false
    if host returned true:
        target = OptionalRaidContainerTargetResolver.resolve(this)
        if target empty → leave true (host decision stands)
        if !StorageRaidPolicy.mayLoot(mob, level, target) → false

canContinueToUse @ HEAD (cancellable):
    target = OptionalRaidContainerTargetResolver.resolve(this)
    if target present && !mayLoot(...) → false
```

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
| `compat/OptionalRaidContainerTargetResolver.java` | host `targetPos` access (if Gate 0-B selects) |
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
| `canUse` instruction-level inject | **GAP** | **Prefer RETURN veto (Gate 0-B)** |

**Full task-54 implementation:** **NOT AUTHORIZED**

**Task-54 Gate 0 source audit:** **NOT AUTHORIZED** (await User: **authorize task-54 Gate 0**)
