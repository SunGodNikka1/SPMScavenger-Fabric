# Task 54 brief: V3-B minimum `StorageOwnership` + host `RaidContainersGoal` guard (`D-VR-081`, `D-VR-017`)

**Slice:** `GlobalPos`-keyed explicit storage permission registry + ally loot guard on pinned SPM
`RaidContainersGoal`. **Authorization:** brief only — User, 2026-08-20. **Implementation is NOT
authorized** until User separately says **authorize task-54** or **Implement V3-B**.

**Brief revision:** User peer review, 2026-08-20 — **Blocker repairs** (destruction seam API,
hot-path scanner, purity split, chest topology, command safety, guard compatibility sentinel).
**Still not implementation-authorized.**

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
                   explicit operator grants
                            │
                            ▼
               StoragePermissionSavedData
                  GlobalPos + reverse UUID
                            │
             ┌──────────────┴──────────────┐
             ▼                             ▼
StorageContainerResolver          StorageGrantLifecycle
 loaded/non-loading truth         block-state replacement
 canonical logical identity       chest topology transitions
 loaded validation                stale-row cleanup
             │                             │
             └──────────────┬──────────────┘
                            ▼
                  StorageOwnershipPolicy
                    PURE classification
                    (diagnostics / commands)
                            │
              ┌─────────────┴─────────────┐
              ▼                           ▼
     diagnostics / `storage get`    StorageRaidPolicy
                                    HOT PATH — ally only:
                                    loaded valid container
                                    + explicit grant for mob
                                            │
                                            ▼
                            RaidContainersAllyStorageMixin
                              canUse + canContinueToUse
                            + StorageGuardCompatibility
                              (must not fail silently)
```

## Why this slice exists

SPM `RaidContainersGoal` treats every visible chest/barrel/shulker alike (`CODE_CONFIRMED` — pinned
host). For a `VILLAGE_ALLY` PlayerMob that is **dangerous**: only **explicit** mob-owned or
operator-shared containers may loot (`D-VR-017` / `D-VR-081`). Task-53 locked profile authority;
task-54 implements the **positive permission registry** and the **continuous host guard** — not a
one-shot admission strip.

**Peer review incorporated (User, 2026-08-20):**

1. **`BlockEntity#setRemoved(RemovalReason)` does not exist in 1.21.1** — brief reopened; destruction
   seam must follow **block-state replacement**, not entity-style removal reasons.
2. **`VillagePerception.observe()` must not run on the guard hot path** — ally safety is
   **grant-or-deny**; `VILLAGE_PUBLIC` is a diagnostic category only.
3. **Side effects split from pure policy** — resolver + lifecycle vs `StorageOwnershipPolicy`.
4. **Chest topology transitions** invalidate old logical grants — no clever migration in gen-1.
5. **`own`/`share`/`unshare`/`revoke` require loaded, established container truth** — no chunk loading.
6. **Guard compatibility sentinel** — supported SPM present ⇒ hook **must** be verifiably installed.

## Load-bearing requirements

| # | Requirement |
| --- | --- |
| 1 | **`StorageOwnership` enum** outputs exactly: `MOB_OWNED`, `EXPLICITLY_SHARED_WITH_MOB`, `VILLAGE_PUBLIC`, `FOREIGN`, `UNKNOWN` — semantics locked by `D-VR-017`. |
| 2 | **Positive permission is explicit only.** `MOB_OWNED` and `EXPLICITLY_SHARED_WITH_MOB` come **only** from the permission registry (operator commands in gen-1). HOME/HIGH/trade/village discovery **must not** create grants. |
| 3 | **Registry keys are canonical `GlobalPos`.** Never naked `BlockPos`. Double-chest halves **must** resolve to one key before grant, revoke, invalidation, or guard evaluation. |
| 4 | **Semantic persistence (D-VR-081):** grants survive chunk unload, mob dimension change, and server restart. Normal deletion: explicit revoke, block-state replacement / topology invalidation, loaded stale cleanup, and permanent mob removal via `PerMobSavedData.forgetAll`. |
| 5 | **Do not treat chunk unload or block-entity unload as destruction.** Fabric `ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD` represents unload, **not** typed destruction — **must not** delete grants. |
| 6 | **Continuous guard** on pinned `RaidContainersGoal` at **`canUse` (after candidate selection) and `canContinueToUse` (every evaluation)** — not global goal removal, not mixin-only on `canUse`. |
| 7 | **Non-ally behaviour unchanged.** When `profile != VILLAGE_ALLY`, `StorageRaidPolicy` is a **no-op permit** — host scheduling unchanged. |
| 8 | **Ally hot path (D-VR-017 enforcement):** when `profile == VILLAGE_ALLY`, permit **only** if chunk loaded, target is still a valid lootable logical container, **and** registry carries explicit permission for **that mob**. Everything else → deny. **Do not** invoke `VillagePerception.observe()` on this path. |
| 9 | **`MandatoryOwnership` untouched.** Storage guard **does not** publish or consume `MandatoryOwnership`. No second publisher unless implementation proves a blocker — brief assumes none. |
| 10 | **RET-1:** register mob-attributed grant cleanup in `PerMobSavedData.forgetAll()`; peek-only cleanup; no silent LRU/TTL authority eviction. |
| 11 | **Side effects live in `StorageGrantLifecycle` only** — not in `StorageOwnershipPolicy` or `StorageRaidPolicy`. |
| 12 | **Chest topology policy (gen-1):** any SINGLE ↔ LEFT/RIGHT transition invalidates grants tied to the **pre-transition logical container identity**; operator must re-grant explicitly. |
| 13 | **Guard compatibility:** when pinned SPM is present and ally-storage feature is enabled, the guard hook **must** be verifiably installed; unsupported host shape ⇒ loud diagnostic and **disable ally-storage enforcement** (fail safe for non-allies; do not pretend allies are protected). |

## Deliverables (implementation-authorized later)

| Path | Role |
| --- | --- |
| `village/storage/StorageOwnership.java` | **new** — enum |
| `village/storage/StorageContainerResolver.java` | **new** — loaded truth, canonical identity, lootable-container predicate (host-equivalent) |
| `village/storage/StorageGrantLifecycle.java` | **new** — replacement/topology invalidation + stale-row cleanup (mutating) |
| `village/storage/StorageOwnershipPolicy.java` | **new** — **pure** classify from supplied facts + registry snapshot |
| `village/storage/StoragePermissionSavedData.java` | **new** — persisted explicit grants + reverse UUID index |
| `village/storage/StorageRaidPolicy.java` | **new** — **hot path** ally grant-or-deny only |
| `village/storage/StorageGuardCompatibility.java` | **new** — init-time / runtime sentinel for mixin install state |
| `mixin/RaidContainersAllyStorageMixin.java` | **new** — `@Pseudo` inject on `RaidContainersGoal` only |
| `command/VillageStorageCommands.java` | **new** — operator grant/revoke/query |
| `PerMobSavedData.java` | register mob grant sweep |
| `SpmScavenger.java` | register lifecycle hooks, compatibility check, commands |
| Tests | VR-T3g–i + lifecycle/topology + structural negatives |

**Explicitly NOT in this slice:** V3 executors · `VillageWorkAdmission` changes · global goal removal ·
auto-grants · `VillagePerception.observe()` on guard hot path · clever chest-topology grant migration.

---

## Architecture split (LOCKED)

### `StorageContainerResolver` — world truth, no persistence mutation

Responsibilities:

- Chunk loaded check **without loading/generating** the chunk
- Host-equivalent lootable-container predicate (chest/barrel/shulker)
- Canonical logical identity for a **loaded** position (`ChestBlock.getConnectedBlockPos` + lexicographic min for stable doubles)
- `resolveLoaded(level, pos) → Optional<ResolvedContainer>` with canonical `GlobalPos` + logical kind

**Must not:** write SavedData · call `VillagePerception.observe()` · load chunks.

### `StorageGrantLifecycle` — all mutating cleanup

Responsibilities:

- Invalidate grants at **pre-transition logical identity** (see Lifecycle seam + Topology)
- Stale-row cleanup when loaded validation proves grant target is no longer a valid logical container
- Called from block-state replacement hook and explicit command paths — **not** from `StorageRaidPolicy`

**Must not:** be invoked from every `canContinueToUse` tick unless a separate bounded maintenance
task is added later (not in gen-1).

### `StorageOwnershipPolicy` — pure classification

```java
StorageOwnership classify(
        ResolvedContainerFacts facts,   // supplied by caller
        UUID mobId,
        GrantSnapshot grants)           // read-only registry view
```

**Pure:** no I/O, no SavedData writes, no world mutation.

**Decision order (diagnostics / `storage get` only — first match wins):**

| Step | Condition | Result |
| --- | --- | --- |
| P1 | Facts: chunk not loaded | `UNKNOWN` |
| P2 | Facts: not a valid lootable logical container | `UNKNOWN` |
| P3 | Grants: `ownerMobId == mobId` | `MOB_OWNED` |
| P4 | Grants: `mobId ∈ sharedMobIds` | `EXPLICITLY_SHARED_WITH_MOB` |
| P5 | Facts: `withinSettlementBounds == true` | `VILLAGE_PUBLIC` |
| P6 | Loaded container, no grant, not public | `FOREIGN` |

**P5 fact source (LOCKED for gen-1 diagnostics):** caller supplies a **bounded boolean** derived
from **existing** village memory / perception **cache or scheduler output** — **not** a fresh
`VillagePerception.observe()` call per classification. Acceptable gen-1 sources:

- `KnownVillage` anchor list already in dimension memory **and** `SettlementBoundsPolicy.within(containerPos, anchor)` for any known anchor in that dimension; **or**
- a explicitly passed-in diagnostic fact from a command that already loaded the chunk.

**Rejected for P5:** invoking `VillagePerception.observe(level, pos)` inside policy or guard;
goal-tick POI scans; V3-D workstation scanner duplication.

### `StorageRaidPolicy` — hot path (LOCKED)

```java
boolean mayLoot(PlayerMob mob, ServerLevel level, BlockPos targetPos) {
    if (profileOf(mob) != VILLAGE_ALLY) {
        return true;                         // non-ally: host unchanged
    }
    if (!StorageGuardCompatibility.guardActive()) {
        return true;                         // only when SPM absent / feature disabled — see Compatibility
    }
    Optional<ResolvedContainer> resolved = resolver.resolveLoaded(level, targetPos);
    if (resolved.isEmpty()) {
        return false;                        // unloaded or not a valid container → deny ally
    }
    return grants.hasExplicitPermission(resolved.get().canonicalGlobal(), mob.getUUID());
}
```

**Explicit permission** = owner or share row at canonical `GlobalPos` for this mob.

**Must not happen on hot path:** `StorageOwnershipPolicy.classify()` ·
`VillagePerception.observe()` · POI queries · grant lifecycle mutation · settlement scans.

**Equivalence note:** for `VILLAGE_ALLY`, deny when classification would be
`{VILLAGE_PUBLIC, FOREIGN, UNKNOWN}` **without** needing to compute which one — grant-or-deny only.

---

## Double-chest canonical identity (stable topology)

When the loaded chest topology is already stable (single, or an established LEFT/RIGHT pair):

```text
canonicalPos(level, pos):
    if pos is a double-chest half:
        other = ChestBlock.getConnectedBlockPos(pos, blockState)
        return lexicographically smaller BlockPos among {pos, other}
    else:
        return pos

canonicalGlobal(level, pos) = GlobalPos.of(level.dimension(), canonicalPos(level, pos))
```

**Must happen:** grant at either half → one canonical row; guard at either half → same decision.

**Must not happen:** two registry rows for one double chest.

---

## Chest topology transitions (LOCKED — gen-1)

Stable canonicalization **alone is insufficient**. Logical container identity can change while both
positions remain chest blocks.

| Transition | Policy |
| --- | --- |
| SINGLE granted at A → chest B placed → A+B become double | **Invalidate** grant stored under A's old single identity **and** any grant under new double canonical key before merge completes |
| DOUBLE granted → either half destroyed → survivor becomes SINGLE | **Invalidate** double grant (old logical identity) |
| DOUBLE half re-pairs to different neighbour | **Invalidate** old double grant |
| Barrel / shulker replaced with different block | **Invalidate** via replacement seam |

**Gen-1 rule:** **Any chest topology transition SINGLE ↔ LEFT/RIGHT invalidates grants for the
affected pre-transition logical container(s). Re-grant explicitly.** No automatic migration.

**Lifecycle hook requirement:** replacement/topology handler must compute **old logical identity from
`oldState` + `pos` before the transition**, invalidate grants for that identity, **then** allow the
world to settle. Post-transition canonicalization alone cannot detect "grant at A, now canonical is B".

### Topology test vectors (required)

| ID | Scenario | Expected |
| --- | --- | --- |
| T1 | Single granted at A → merge into double A+B | old grant **invalidated** |
| T2 | Double granted → destroy one half | double grant **invalidated** |
| T3 | Double re-pairs to different neighbour | old double grant **invalidated** |
| T4 | Stable double, grant one half, query other | same grant / permit |

---

## Permission registry (`StoragePermissionSavedData`)

(Semantics unchanged from prior draft — explicit owner/share, Overworld-hosted, reverse index, peek/get
discipline, RET-1 via `forgetAll`.)

**Rejected:** TTL/LRU silent eviction · unload-triggered deletion · chunk loading from command paths.

---

## Fabric 1.21.1 destruction / replacement lifecycle seam (REOPENED — User correction)

### Why `BlockEntity#setRemoved(RemovalReason)` is rejected

In Mojang-mapped **Minecraft 1.21.1** (this project's compile baseline), `BlockEntity` exposes:

```text
setRemoved()
clearRemoved()
isRemoved()
```

**There is no `RemovalReason` argument on `BlockEntity`.** That API belongs to ordinary `Entity`, not
block entities. Fabric `ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD` is an unload notification only —
**not** a typed destruction discriminator.

**Brief status:** the prior `setRemoved(UNLOADED)` vs `setRemoved(CHANGED)` distinction is **REJECTED**
and must not appear in implementation or tests.

### Correct semantic direction (LOCKED intent, mixin target TBD)

Invalidate grants on **logical container replacement/destruction**, preserve on **chunk unload**:

```text
chunk unload
    → no logical block replacement at grant key
    → KEEP grant


old logical container replaced/destroyed
    → block-state replacement at affected position(s)
    → INVALIDATE grant(s) for pre-transition logical identity
```

**Candidate seam (1.21.1 mapped API):** `BlockBehaviour.onRemove(oldState, level, pos, newState, moved)`
— receives both old and new block states. This is the semantic event needed to:

1. Detect container → non-container replacement
2. Detect chest topology transitions (SINGLE ↔ LEFT/RIGHT) via `oldState` vs `newState`
3. **Avoid** firing on mere chunk unload (no block replacement)

### Gate 0 — source audit before locking mixin target (mandatory first implementation step)

Implementer **must** trace and document the actual server path (pinned mappings / decompile), e.g.:

```text
Level#setBlock / Level#removeBlock
    → Block#onRemove(oldState, ...)
    → block entity removal / creation
    → (confirm unload path does NOT invoke onRemove for mere chunk unload)
```

**Deliverable:** short audit note in `task-54-report.md` with pinned class/method paths and the chosen
hook(s). **Do not lock exact mixin target in code until Gate 0 passes.**

**Rejected hooks:**

| Hook | Verdict |
| --- | --- |
| `BlockEntity#setRemoved(RemovalReason)` | **REJECT** — API does not exist |
| `ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD` → delete grant | **REJECT** — unload ≠ destruction |
| Chunk unload eviction | **REJECT** |

**Fabric unload event:** may be used for **optional bounded validation on load** only — never grant deletion on unload alone.

### Bounded stale-grant handling (via lifecycle, not guard)

| When | Action | Owner |
| --- | --- | --- |
| Block-state replacement / topology change | invalidate pre-transition logical grant(s) | `StorageGrantLifecycle` |
| Loaded admin/diagnostic validation finds invalid target | delete stale row | `StorageGrantLifecycle` |
| Unloaded chunk | preserve grant; ally guard **denies** via `resolveLoaded` empty | `StorageRaidPolicy` |
| Guard hot path every tick | **no lifecycle mutation** | — |

---

## Continuous `RaidContainersGoal` guard

### Mixin

**New mixin** — `RaidContainersAllyStorageMixin` — **`@Pseudo`**, targets **only**
`games.brennan.playermob.entity.goal.RaidContainersGoal`.

| Method | Injection | Behaviour |
| --- | --- | --- |
| `canUse` | after `targetPos` assigned, before return true | if `!StorageRaidPolicy.mayLoot(...)` → false |
| `canContinueToUse` | `@At("HEAD")`, cancellable | if `targetPos != null` && `!mayLoot(...)` → false |

**Method names:** both readable and intermediary (`method_6264` / `method_6266`) per
`SpmGoalMixinNamingTest`.

### Guard compatibility sentinel (LOCKED — addresses `require = 0` risk)

`@Inject(..., require = 0)` remains acceptable **only together with** explicit compatibility enforcement:

| State | Behaviour |
| --- | --- |
| SPM **absent** | Guard inactive; no-op; no error |
| Pinned SPM **present**, host shape **supported** | `StorageGuardCompatibility.guardActive() == true` after init self-test; mixin injections verified (structural test + optional runtime flag set from successful inject callback) |
| SPM present, shape **unsupported** (mixin miss / method rename) | **Loud diagnostic** at server start; `guardActive() == false`; **do not silently claim ally safety** |

**Must happen:** structural test fails CI if `RaidContainersAllyStorageMixin` lacks dual method names
or policy wiring.

**Must not happen:** supported SPM + silent mixin miss → allies loot protected containers believing
guard is active. Log level **WARN or ERROR**, not debug-only.

**Product note:** when `guardActive()` is false but profile is `VILLAGE_ALLY`, operators should treat
ally storage safety as **UNVERIFIED** — document in command feedback / log.

---

## Commands (gen-1 explicit assignment)

All `/spmscavenger village storage …` require operator permission (`hasPermission(2)`).

```text
/spmscavenger village storage get <x> <y> <z> [@mob]
/spmscavenger village storage own <mob> <x> <y> <z>
/spmscavenger village storage share <mob> <x> <y> <z>
/spmscavenger village storage unshare <mob> <x> <y> <z>
/spmscavenger village storage revoke <x> <y> <z>
/spmscavenger village storage list <mob>
```

### Positional command safety (LOCKED)

**`own` / `share`:**

```text
require chunk already loaded at target (no load/generate)
require host-equivalent lootable container exists at target
canonicalize from loaded world truth via StorageContainerResolver
then write grant via get(server)
```

**`unshare` / `revoke` (gen-1):**

```text
require chunk loaded + resolvable canonical target
then mutate via peek(server)
```

**`get`:**

```text
unloaded target → report UNKNOWN (no chunk load)
loaded → resolve + pure policy classify (may use bounded settlement fact)
```

**`list <mob>`:** returns persisted canonical `GlobalPos` keys — diagnostic even when chunks unloaded.

**Rejected:** granting against unloaded coordinates; commands that force chunk loading/generation.

---

## Verification — VR-T3g–i + lifecycle + structural negatives

### VR-T3 scenarios (static / unit)

| ID | Scenario | Expected |
| --- | --- | --- |
| **VR-T3g** | `VILLAGE_ALLY` + no explicit grant at settlement chest | `mayLoot` false; mixin denies admit + continuation |
| **VR-T3h** | `VILLAGE_ALLY` + unloaded / unresolvable target | fail closed |
| **VR-T3i-a** | `NEUTRAL` + any target | guard permits |
| **VR-T3i-b** | `VILLAGE_ALLY` + explicit own/share grant | guard permits |
| **VR-T3i-c** | `VILLAGE_ALLY` + grant at settlement chest | **grant permits** — hot path does not need `VILLAGE_PUBLIC` label |

### Lifecycle / topology tests

| ID | Scenario | Expected |
| --- | --- | --- |
| L1 | Grant survives chunk unload/reload | row persists |
| L2 | Block-state replacement container → stone | grant invalidated (lifecycle) |
| L3 | `BLOCK_ENTITY_UNLOAD` only | grant **preserved** |
| L4 | Loaded stale validation | lifecycle deletes row |
| L5 | Stable double half query | same grant |
| L6 | `forgetAll(mobId)` | peek-only sweep |
| T1–T4 | Topology table above | grants invalidated per policy |

### Structural negatives

| ID | Check | Must fail if |
| --- | --- | --- |
| S1 | Naked `BlockPos` grant keys | no `GlobalPos` |
| S2 | Unload deletes grants | unload listener mutates registry |
| S3 | `BlockEntity#setRemoved(RemovalReason)` | any reference in production/tests |
| S4 | `VillagePerception.observe` in `StorageRaidPolicy` | hot-path import/call |
| S5 | `StorageOwnershipPolicy` mutates SavedData | purity violation |
| S6 | Mixin only `canUse` | missing `canContinueToUse` |
| S7 | Non-ally denied | `NEUTRAL` → false |
| S8 | `own`/`share` on unloaded chunk | command succeeds |
| S9 | Chunk load from storage command | forced load API used |
| S10 | Supported SPM + guard silently inactive | no compatibility sentinel |
| S11 | `MandatoryOwnership` in storage guard | wiring grep |
| S12 | UUID store not in `PerMobSavedData` | removal contract test |

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
| `docs/porting/TEST_MATRIX.md` | VR-T3g–i + lifecycle/topology rows |
| `plans/RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md` | V3-B brief revision; destruction seam correction |
| `.superpowers/sdd/progress.md` | brief revision note |

## Report (when implemented)

`.superpowers/sdd/task-54-report.md` — Gate 0 source audit paths; map every brief requirement;
label `CONFIRMED`/`INFERRED`/`UNVERIFIED`. Runtime VR-T3g–i **UNVERIFIED** until approved launch.

---

## Peer review disposition (2026-08-20)

| Item | Verdict |
| --- | --- |
| GlobalPos, Overworld SavedData, explicit grants, continuous guard, RET-1 peek cleanup, no MandatoryOwnership publisher | **KEEP** |
| `BlockEntity#setRemoved(RemovalReason)` seam | **REJECT** — corrected above |
| C5 `VillagePerception.observe()` on guard/classifier hot path | **REJECT** — hot path grant-or-deny; P5 diagnostics bounded only |
| Monolithic "pure classifier" with purge side effect | **FIXED** — resolver / lifecycle / policy split |
| Chest merge/split semantics | **ADDED** — topology invalidation + tests |
| Unloaded grant commands | **ADDED** — loaded-chunk requirement |
| Silent optional guard (`require=0` alone) | **HARDENED** — compatibility sentinel |

**task-54 implementation:** **NOT AUTHORIZED**
