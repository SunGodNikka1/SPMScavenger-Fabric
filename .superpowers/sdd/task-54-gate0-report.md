# Task-54 Gate 0 report — read-only source audit

**Status:** `GATE_0_CLOSED` — lifecycle hook and SPM integration shape **LOCKED** for brief/task-54
implementation planning. **Full task-54 implementation remains separately unauthorized.**

**Audit date:** 2026-08-20  
**Target project:** `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`  
**Minecraft baseline:** 1.21.1, official Mojang mappings (`loom.officialMojangMappings()`), sources from
`minecraft-merged-1425f5a1b7-1.21.1-loom.mappings.1_21_1.layered+hash.2198-v2-sources.jar`
(`genSources` / Vineflower, BUILD SUCCESSFUL).  
**SPM baseline:** v0.86.0 — reference source
`Projects/references/SocialPlayerMobs-v0.86.0` + distributed jar
`run/.fabric/processedMods/playermob-0.86.0-64b5720b4b825f21.jar` (`javap`).

---

## Executive summary

| Area | Verdict | Locked decision |
| --- | --- | --- |
| **Gate 0-A lifecycle** | **PASS** | **`@Mixin` inject into `ServerLevel.onBlockStateChange(BlockPos, BlockState old, BlockState new)`** at **`HEAD`**, delegate to `StorageGrantLifecycle.onBlockStateChange(serverLevel, pos, old, new)`. |
| **Rejected lifecycle hooks** | **CONFIRMED reject** | `BlockEntity#setRemoved(RemovalReason)` — API absent; chunk unload `clearAllBlockEntities` → `setRemoved()` only; Fabric `BLOCK_ENTITY_UNLOAD` → not destruction. |
| **Gate 0-B host guard** | **PASS** | `OptionalRaidContainerTargetResolver` (reflect `mob`, `targetPos`); `canUse` **`@At("RETURN")` veto**; `canContinueToUse` **`@At("HEAD")` veto**; ally + unresolved `targetPos` → **fail closed**. |
| **Brief correction** | **Required** | `ChestBlock.getConnectedBlockPos` **NOT FOUND** in 1.21.1 — canonical double-chest partner uses **`ChestBlock.getConnectedDirection` + `BlockPos.relative`**. |

---

## Gate 0-A — Block mutation / lifecycle seam

### Call chain (CONFIRMED — Mojang sources)

```text
Level.destroyBlock / Level.removeBlock / Level.setBlockAndUpdate
    → Level.setBlock(pos, newState, flags, recursionGuard)
        → LevelChunk.setBlockState(pos, newState, movedByPiston)
            → [server] oldState.onRemove(level, pos, newState, moved)   // BlockStateBase → BlockBehaviour
            → [server] newState.onPlace(level, pos, oldState, moved)
            → returns oldState
        → if getBlockState(pos) == newState:
            → Level.onBlockStateChange(pos, oldState, newState)         // line 251
                → ServerLevel.onBlockStateChange(...)                     // POI + hook target
```

**Pinned paths:**

| Step | Class | Method | Lines (decompiled sources) |
| --- | --- | --- | --- |
| Central mutation | `net.minecraft.world.level.Level` | `setBlock(BlockPos, BlockState, int, int)` | 213–256 |
| State swap + BE lifecycle | `net.minecraft.world.level.chunk.LevelChunk` | `setBlockState(BlockPos, BlockState, boolean)` | 245–314 |
| Per-old-block callback | `net.minecraft.world.level.block.state.BlockBehaviour` | `onRemove(BlockState old, Level, BlockPos, BlockState new, boolean)` | 163–167 |
| Per-new-block callback | `net.minecraft.world.level.block.state.BlockBehaviour` | `onPlace(...)` | 160–161 |
| **Winning hook** | `net.minecraft.server.level.ServerLevel` | `onBlockStateChange(BlockPos, BlockState, BlockState)` | 1336–1349 |
| Base no-op | `net.minecraft.world.level.Level` | `onBlockStateChange(...)` | 259–260 |

**Parameter semantics at `ServerLevel.onBlockStateChange`:**

```text
blockState  = OLD state (before change)
blockState2 = NEW state (after change)
```

(Evidence: `Level.setBlock` line 251 passes `blockState2` returned from chunk `setBlockState` as old, and `blockState3` read after swap as new.)

### Chest topology path (CONFIRMED)

Double-chest pairing uses `ChestBlock.updateShape` (not a separate block type change):

| Transition | Mechanism | Source |
| --- | --- | --- |
| SINGLE → LEFT/RIGHT | Neighbor chest placed; `updateShape` sets `TYPE` to partner opposite | `ChestBlock.java` 148–155 |
| LEFT/RIGHT → SINGLE | Connection broken; `updateShape` sets `TYPE` to `SINGLE` | `ChestBlock.java` 156–157 |
| Re-pairing | Neighbor shape updates → further `updateShape` / `setBlock` on affected positions | `ChestBlock.updateShape` + `Level.setBlock` neighbor propagation |

These topology edits ultimately pass through **`Level.setBlock` → `ServerLevel.onBlockStateChange`** because `updateShape` results are applied via normal block setting / neighbor shape updates (`Block.updateFromNeighbourShapes` in post-processing and live updates).

### Chunk unload path (CONFIRMED — does NOT hit lifecycle hook)

```text
ServerLevel.unload(LevelChunk)
    → LevelChunk.clearAllBlockEntities()
        → BlockEntity.setRemoved() on each BE
        → blockEntities.clear()
```

**Pinned paths:** `ServerLevel.java` 925–928; `LevelChunk.java` 614–618; `BlockEntity.java` 218–220.

**No** `Level.setBlock`, **no** `LevelChunk.setBlockState`, **no** `onBlockStateChange` on ordinary chunk unload.

`BlockEntity.setRemoved()` in 1.21.1 is a **boolean flag only** — no `RemovalReason` parameter (**CONFIRMED** `BlockEntity.java` 214–224).

### Chunk load / deserialization (CONFIRMED — no false invalidation on ordinary load)

Ordinary load path:

```text
Chunk NBT → section block states written directly
Block entities promoted from NBT → clearRemoved()
LevelChunk.registerAllBlockEntitiesAfterLevelLoad()
```

**No** `onBlockStateChange` for the bulk of deserialized blocks.

**INFERRED edge case (document, do not over-invalidate):** `LevelChunk.postProcessGeneration()` may call `level.setBlock(pos, blockState2, 20)` for positions in the chunk `postProcessing` queue (`LevelChunk.java` 530–546). That **can** invoke `onBlockStateChange` during worldgen/post-process, not ordinary gameplay unload/reload. **`StorageGrantLifecycle` must compare `oldState` vs `newState` and only invalidate on genuine container identity/replacement/topology change** — not when `oldState == newState` (chunk `setBlockState` returns null early when unchanged).

### Alternative hooks compared

| Candidate | G0 fit | Verdict |
| --- | --- | --- |
| **`ServerLevel.onBlockStateChange`** | Single server-side entry after successful `setBlock`; receives old+new for all block types including chest TYPE property changes | **LOCK — primary** |
| `Level.setBlock` tail inject | Same data, but duplicates client/server guard logic; base `Level.onBlockStateChange` empty on client anyway | Valid fallback; **not selected** |
| `BlockBehaviour.onRemove` / `BlockStateBase.onRemove` | Fires inside chunk before level callback; same old/new; per-block-type mixin surface | **Supplemental only** if a bypass is found; **not primary** |
| `BlockEntity#setRemoved(RemovalReason)` | API **does not exist** on `BlockEntity` in 1.21.1 | **REJECT** |
| Fabric `ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD` | Unload only; same as chunk unload | **REJECT for invalidation** |

### G0-1 … G0-6 answers

| ID | Question | Answer | Evidence class |
| --- | --- | --- | --- |
| **G0-1** | Container → air / non-container replacement? | **YES** | `destroyBlock` → `setBlock` with fluid/air legacy block (`Level.java` 269–284, 251); `onRemove` when block type changes (`BlockBehaviour.java` 163–166) |
| **G0-2** | Chest SINGLE → LEFT/RIGHT? | **YES** | `ChestBlock.updateShape` 148–155 → `setBlock` chain → `onBlockStateChange` |
| **G0-3** | Chest LEFT/RIGHT → SINGLE? | **YES** | `ChestBlock.updateShape` 156–157 → same chain |
| **G0-4** | Re-pairing / neighbour change? | **YES** | `updateShape` + neighbour shape updates via `Level.setBlock` |
| **G0-5** | Ordinary chunk unload invokes hook? | **NO** | `ServerLevel.unload` → `clearAllBlockEntities` only |
| **G0-6** | Ordinary chunk load falsely invalidates? | **NO** (with edge-case guard) | Load registers BEs without `setBlock`; postProcess queue is bounded exception — lifecycle must diff states |

### LOCKED implementation note (Gate 0-A)

```java
// Mixin target (server only):
// net.minecraft.server.level.ServerLevel#onBlockStateChange(BlockPos, BlockState, BlockState)
@Inject(method = "onBlockStateChange", at = @At("HEAD"))
void spmscavenger$onBlockStateChange(BlockPos pos, BlockState oldState, BlockState newState, ...) {
    StorageGrantLifecycle.onBlockStateChange((ServerLevel) (Object) this, pos, oldState, newState);
}
```

`StorageGrantLifecycle` derives **pre-transition logical container identity from `oldState` + `pos`**, applies topology policy (SINGLE ↔ LEFT/RIGHT), and invalidates canonical `GlobalPos` grant rows. **Do not** invalidate on `oldState == newState`.

---

## Gate 0-B — SPM `RaidContainersGoal` integration

### Distributed jar shape (CONFIRMED — `javap`)

**Artifact:** `run/.fabric/processedMods/playermob-0.86.0-64b5720b4b825f21.jar`  
**Class:** `games.brennan.playermob.entity.goal.RaidContainersGoal`

| Member | JVM name | Type | Access |
| --- | --- | --- | --- |
| Host mob | `mob` | `Lgames/brennan/playermob/entity/PlayerMobEntity;` | private final |
| Selected chest | `targetPos` | `Lnet/minecraft/core/BlockPos;` | private |
| Admission | `canUse` | `()Z` | public |
| Continuation | `canContinueToUse` | `()Z` | public |

**Reference source alignment:** `RaidContainersGoal.java` lines 69–76, 114–138 (`Projects/references/SocialPlayerMobs-v0.86.0`).

**Note on intermediary names:** `javap` on the **pinned processedMods jar** shows **readable** `canUse` / `canContinueToUse` (not `method_6264`). Repository `SpmGoalMixinNamingTest` still mandates dual readable+intermediary aliases for `@Pseudo` goal mixins — **keep both** at implementation time unless a separate artifact audit proves otherwise.

### G0-B decisions (LOCKED)

| ID | Decision | Lock |
| --- | --- | --- |
| **G0-B1** | `targetPos` + `mob` access | **`OptionalRaidContainerTargetResolver`** in `compat` — reflective cached fields `targetPos` and reuse/`parallel` pattern to `OptionalGoalMobResolver` for `mob`. **Do not** `@Shadow` unless resolver fails on a future pinned jar. |
| **G0-B2** | `canUse` inject | **`@Inject(method = {"canUse", "method_6264"}, at = @At("RETURN"), cancellable = true, require = 0)`** — if `CallbackInfoReturnable.getReturnValue() == true`, resolve `targetPos`, run enforcement veto (`true → false` only). Host scan untouched. |
| **G0-B3** | `canContinueToUse` inject | **`@Inject(method = {"canContinueToUse", "method_6266"}, at = @At("HEAD"), cancellable = true, require = 0)`** — resolve `targetPos`; if present, run same policy. |
| **G0-B4** | Method names | Readable **`canUse`**, **`canContinueToUse`** (confirmed jar); add intermediary **`method_6264`**, **`method_6266`** per `SpmGoalMixinNamingTest` / `Goal` mapping. |

### Mob resolution path (LOCKED)

```text
RaidContainersAllyStorageMixin handler
    → OptionalGoalMobResolver.resolve(goal, "raid container guard")
    → PlayerMobEntity / Mob
    → PlayerMobs.isPlayerMob guard
    → PlayerMobVillagePolicySavedData.profileOf(server, mob.getUUID())
    → StorageRaidPolicy.mayLoot(mob, serverLevel, targetPos)
```

**`StorageRaidPolicy` must not import `StorageGuardCompatibility`.** If the mixin callback runs, enforce.

### Ally fail-closed on unresolved target (LOCKED — User safety invariant)

When the **`canUse` RETURN** hook observes host **`true`** (candidate selected) **and** profile is **`VILLAGE_ALLY`**:

```text
targetPos = OptionalRaidContainerTargetResolver.resolve(goal)
if targetPos == null:
    convert return to FALSE          // fail closed — do NOT preserve host true
    record StorageGuardCompatibility.TARGET_RESOLUTION_FAILED (diagnostic)
    return
else:
    apply StorageRaidPolicy.mayLoot(...)
```

Same rule in **`canContinueToUse` HEAD** when `targetPos` expected non-null (phase active): unresolved → **deny** + diagnostic.

**Non-allies:** bypass policy (return host result unchanged) — host behaviour preserved.

### Guard compatibility observations (diagnostics only)

Track separately (User v3):

| Flag | Set when |
| --- | --- |
| `HOST_SHAPE_SUPPORTED` | Server start: `RaidContainersGoal` class loadable; `mob` + `targetPos` fields readable; goal methods present |
| `CAN_USE_HOOK_OBSERVED` | First `canUse` inject executes |
| `CONTINUATION_HOOK_OBSERVED` | First `canContinueToUse` inject executes |
| `TARGET_RESOLUTION_FAILED` | Ally enforcement attempted but `targetPos`/`mob` unresolved |

**Never** branch `StorageRaidPolicy` on these flags.

### Injection viability (CONFIRMED — bytecode + source)

**`canUse` RETURN veto:** Source assigns `targetPos = found` then `return true` (`RaidContainersGoal.java` 127–128). `javap` shows `putfield targetPos` immediately before `iconst_1` / `ireturn`. RETURN inject after host logic is **viable**.

**`canContinueToUse` HEAD veto:** Source reads `targetPos != null` first (`RaidContainersGoal.java` 132–138). HEAD inject **viable**.

---

## Brief corrections applied by this audit

1. **Lifecycle hook:** lock **`ServerLevel.onBlockStateChange`** (not `BlockEntity#setRemoved(RemovalReason)`).
2. **Double-chest partner pos:** replace brief’s `ChestBlock.getConnectedBlockPos` with **`getConnectedDirection(BlockState)` + `blockPos.relative(direction)`** (`ChestBlock.java` 182–184) — **`getConnectedBlockPos` NOT FOUND** in 1.21.1 sources (≥3 probes: `ChestBlock.java`, repo-wide sources grep, `AbstractChestBlock` absent as separate file in merged jar listing).
3. **Ally unresolved target:** fail closed + compatibility diagnostic (added to brief v3.1).
4. **Gate 0 status:** **CLOSED** — User may authorize **task-54 implementation** separately.

---

## Authorization state

| Gate | Status |
| --- | --- |
| Gate 0 read-only audit | **COMPLETE** (`task-54-gate0-report.md`) |
| Brief hooks | **LOCKED** (see brief v3.1 delta) |
| **Full task-54 / V3-B implementation** | **NOT AUTHORIZED** — requires separate User authorization |

---

## Evidence index

| Claim | Proof |
| --- | --- |
| Minecraft 1.21.1 sources | `.gradle/loom-cache/.../minecraft-merged-...-sources.jar` (Vineflower `genSources`) |
| SPM `RaidContainersGoal` fields/methods | `javap -private` on `run/.fabric/processedMods/playermob-0.86.0-64b5720b4b825f21.jar` |
| SPM readable source | `Projects/references/SocialPlayerMobs-v0.86.0/.../RaidContainersGoal.java` |
| Goal intermediary mapping | `SpmGoalMixinNamingTest` / `mappings-base.tiny` class_1352 |
