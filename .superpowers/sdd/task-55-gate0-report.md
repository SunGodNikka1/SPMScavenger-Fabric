# Task-55 Gate 0 report — read-only source audit (V3-C crop episode)

**Status:** `GATE_0_PASS` — atomic mature→age-0 commit **LOCKED**; **no** `MandatoryOwnership` publisher;
drop-roll contract **LOCKED**.

**Audit date:** 2026-08-21  
**Target project:** `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`  
**Minecraft baseline:** 1.21.1, official Mojang mappings (`loom.officialMojangMappings()`); vanilla loot
tables from `minecraft-assets` 1.21.1 tag; `genSources` UP-TO-DATE.  
**SPM baseline:** v0.86.0 reference source
`Projects/references/SocialPlayerMobs-v0.86.0` (read-only oracle — **not** a compile dependency per
`build.gradle:30`).

**Brief:** `task-55-brief.md` v2.1 (drop-roll contract amendment applied).

---

## Executive summary

| Area | Verdict | Locked decision |
| --- | --- | --- |
| **Gate 0-H host crop goal** | **PASS** | `HarvestCropsGoal`: `mob` + `targetPos`; RETURN veto on `canUse` **must clearTarget**; HEAD veto on `canContinueToUse`; dual readable+intermediary method names. |
| **Gate 0-V vanilla crops** | **PASS** | Gen-1: wheat/carrot/potato/beetroot; maturity via `CropBlock.isMature`; age-0 via `withAge(0)`; planting-material lower bounds locked (table below). |
| **Gate 0-A atomic commit** | **PASS** | Normal path: `setBlock(pos, ageZero(state), flags)` only — **no** `destroyBlock`; failed `setBlock` preserves prior state; **no** async bare-farmland repair publisher. |
| **Gate 0-I inventory** | **PASS** | `PlayerMobs.backpack(mob)` → vanilla `Container`; addon-owned merge helper (no SPM `EquipmentEvaluator` compile import). |
| **Gate 0-C compat** | **PASS** | `OptionalHarvestCropTargetResolver` + `OptionalGoalMobResolver`; `HarvestCropGuardCompatibility` diagnostics; fail-open veto on uncertain domain. |
| **MandatoryOwnership decision gate** | **PASS — no publisher** | Direct crop→crop replacement does not require post-mutation repair ownership. |

---

## Gate 0-H — Host `HarvestCropsGoal` (read-only oracle)

**Reference:** `Projects/references/SocialPlayerMobs-v0.86.0/.../HarvestCropsGoal.java`

| Member | Evidence | Notes |
| --- | --- | --- |
| Priority | `PlayerMobEntity.java:842` — priority **6** | Below V3 P4 episode |
| Flags | `MOVE \| LOOK` | Same band as other SPM work goals |
| `targetPos` | private `BlockPos`, lines 61, 99, 128 | Set **before** `return true` in `canUse` |
| `mob` | private final `PlayerMobEntity` | Constructor field |
| `canUse` gate | `wantsFood()`, combat target, griefing, scan | Hunger-gated — **not** V3 episode semantics |
| Harvest | `destroyBlock(pos, false, mob)` line 207 | **Anti-pattern** — V3 must not mirror |
| `stop()` | lines 126–134 | Clears `targetPos`; `canUse` false does **not** call `stop()` |

**Host targeted crop set (oracle):** `ForagePolicy.isRipeFoodCrop` = max-age **carrots, potatoes,
beetroots only** — **wheat excluded** from host P6. V3 managed set is intentionally broader.

### G0-H mixin shape (LOCKED)

Mirror task-54 / `RaidContainersAllyStorageMixin`:

```text
canUse @ RETURN:
  if host false → pass through
  resolve mob + targetPos
  if HarvestCropVetoPolicy.shouldVeto(mob, level, pos):
      OptionalHarvestCropTargetResolver.clearTarget(goal)   // LOCK — stale-target loop
      return false

canContinueToUse @ HEAD:
  same veto on managedCropCell at targetPos → return false
```

| ID | Decision | Lock |
| --- | --- | --- |
| **G0-H1** | Field access | `OptionalHarvestCropTargetResolver` + `OptionalGoalMobResolver` |
| **G0-H2** | `canUse` inject | `@Inject(method = {"canUse", "method_6264"}, at = RETURN, cancellable, require = 0)` |
| **G0-H3** | `canContinueToUse` | `@Inject(method = {"canContinueToUse", "method_6266"}, at = HEAD, cancellable, require = 0)` |
| **G0-H4** | clearTarget on `canUse` veto | **YES** — host sets `targetPos` before returning true (line 99–100) |

**Evidence class:** `CONFIRMED` — reference source lines cited; distributed jar `javap` **UNVERIFIED**
(processedMods jar absent in workspace) — intermediary names mandated by `SpmGoalMixinNamingTest`.

---

## Gate 0-V — Vanilla crop semantics (1.21.1)

### Maturity and age-zero (CONFIRMED — Mojang API / mappings)

| Crop | Block | Max age | `isMature` | `ageZero` |
| --- | --- | --- | --- | --- |
| Wheat | `Blocks.WHEAT` | 7 | `CropBlock.isMature(state)` | `((CropBlock) block).withAge(0)` |
| Carrots | `Blocks.CARROTS` | 7 | same | same |
| Potatoes | `Blocks.POTATOES` | 7 | same | same |
| Beetroot | `Blocks.BEETROOTS` | 3 | same | same |

**Farmland support (CONFIRMED — `CropBlock` / `PlantBlock`):** `canPlantOnTop` requires farmland
below (`PlantBlock.canPlantOnTop` → `FarmlandBlock`). `validFarmlandSupport(level, pos)` =
`canPlantOnTop(farmlandState, level, pos.below())` **or** equivalent check on block below.

### Planting material mapping (LOCKED)

| Crop | Replants with | Classified as replant material |
| --- | --- | --- |
| Wheat | `Items.WHEAT_SEEDS` | seeds item |
| Beetroot | `Items.BEETROOT_SEEDS` | seeds item |
| Carrots | `Items.CARROT` | crop item (not seeds) |
| Potatoes | `Items.POTATO` | crop item (not seeds) |

### Loot / hard lower bounds (CONFIRMED — `minecraft-assets` 1.21.1 loot tables)

Source URLs (pinned tag):
`https://github.com/InventivetalentDev/minecraft-assets/tree/1.21.1/data/minecraft/loot_table/blocks/`

| Crop | Mature loot structure | Planting-material hard lower bound | Pre-COMMIT held reserve |
| --- | --- | --- | --- |
| **Carrot** | Pool 1: unconditional 1× carrot; pool 2 (age 7): bonus carrots | **≥ 1** carrot always | **Not required** |
| **Potato** | Pool 1: unconditional 1× potato; pool 2 (age 7): bonus potatoes | **≥ 1** potato always | **Not required** |
| **Wheat** | Pool 1 (age 7): 1× wheat; pool 2 (age 7): wheat_seeds + `binomial_with_bonus_count` extra=3, p=4/7 | **Can be 0** seeds (binomial count, not guaranteed base) | **≥ 1 held wheat_seeds** before COMMIT |
| **Beetroot** | Pool 1 (age 3): 1× beetroot; pool 2: beetroot_seeds + same binomial | **Can be 0** seeds | **≥ 1 held beetroot_seeds** before COMMIT |

**Wiki cross-check (INFERRED consistent):** Java Edition wheat/beetroot seed pools use binomial
distribution — [Wheat Seeds – Minecraft Wiki](https://minecraft.wiki/w/Wheat_Seeds) documents
0-success binomial outcomes on the seed pool.

### Drop-roll contract (LOCKED — User v2.1)

```text
SCAN / PATH / WINDUP  →  NO Block.getDrops()

harvestCandidate      →  deterministic feasibility ONLY (table above)

COMMIT PREPARE        →  Block.getDrops(...) exactly ONCE — the episode's actual harvest roll
```

**Rejected:** using PREPARE dry-run or repeated `getDrops` during candidate selection — would allow
rerolling an untouched crop until favorable loot.

---

## Gate 0-A — Atomic mature→age-0 commit

### Decision gate outcome: **PASS — no MandatoryOwnership publisher**

Normal V3-C path never deliberately creates bare farmland. Post-mutation async repair is **not**
required for task-55.

### Mechanism (CONFIRMED — task-54 Gate 0-A chain + crop-specific reasoning)

```text
COMMIT:
  newState = CropReplantSemantics.ageZero(matureState)   // same block type, age property only
  success = level.setBlock(pos, newState, flags)
  if !success OR verify fails → ABORT; mature crop unchanged; escrow restored
  else → bank staged drops (never granted before verify)
```

| ID | Question | Answer | Evidence |
| --- | --- | --- | --- |
| **A-1** | Does mature→age-0 use same block type? | **YES** | `CropBlock.withAge(0)` — block id unchanged |
| **A-2** | Is `destroyBlock` required for correct drops? | **NO** | `Block.getDrops(state, ...)` reads loot from **pre-mutation** `state`; then `setBlock` replaces in place |
| **A-3** | `setBlock` failure preserves crop? | **YES** | `Level.setBlock` returns false when swap not applied; chunk `setBlockState` returns null early when unchanged (task-54 G0-A) |
| **A-4** | Deliberate air intermediate on normal path? | **NO** | V3 never calls `destroyBlock` / air placement on success path |
| **A-5** | Async bare state from V3 `setBlock`? | **NOT FOUND** on normal path | Same-type age downgrade; no publisher needed. External actors (piston, trample) out of scope. |

### `setBlock` flags (LOCKED for implementation)

Use server-appropriate update flags consistent with gameplay block mutation — default **`3`**
(`BLOCK_UPDATE | NOTIFY_NEIGHBORS`) unless implementation probe shows crop age change needs
`UPDATE_CLIENTS` as well. Gate implementation must not use flags that skip block entity sync for
blocks without BE (crops have none).

**INFERRED risk (document, not blocking):** `onRemove`/`onPlace` fire on property change; same crop
block type should not uproot. Implementation verifies with unit fixture before handoff.

---

## Gate 0-I — Inventory banking

| ID | Finding | Lock |
| --- | --- | --- |
| **I-1** | Backpack access | `PlayerMobs.backpack(mob)` → `InventoryCarrier.getInventory()` (`PlayerMobs.java:384-386`) |
| **I-2** | SPM `EquipmentEvaluator.addToContainer` | **Oracle only** — do not compile-import; implement addon `ContainerMerge` using vanilla `Container` API (merge-then-empty-slot pattern matches SPM reference lines 204–227) |
| **I-3** | Banking order | Replant-material surplus **first**, food/output **second**, overflow last |
| **I-4** | Failure semantics | Partial insert returns leftover stack; episode must not grant unstaged items on aborted COMMIT |

---

## Gate 0-C — Compatibility / diagnostics

| ID | Finding | Lock |
| --- | --- | --- |
| **C-1** | Target resolver | `OptionalHarvestCropTargetResolver` — mirror `OptionalRaidContainerTargetResolver` (`targetPos` field) |
| **C-2** | Mob resolver | `OptionalGoalMobResolver` — existing compat |
| **C-3** | Hook diagnostics | `HarvestCropGuardCompatibility` — `observeCanUseHook` / `observeContinuationHook`, session reset, warm-up tick (mirror `StorageGuardCompatibility`) |
| **C-4** | Fail-open | Uncertain `managedCropCell` → **do not veto** host harvest |
| **C-5** | P4 registration | `SpmScavenger.java:254` beside `PlaceTorchGoal` |
| **C-6** | Classifier pin | `MoveHolderClassifier` — add `VillageHarvestEpisodeGoal` → `VILLAGE_WORK` |

---

## MandatoryOwnership — explicit non-scope (CONFIRMED)

Task-55 **must not** call `MandatoryOwnershipRegistry.publish`. Registry remains single-slot per mob
(`MandatoryOwnershipRegistry.java:79`); `release(UUID)` is not claim-identity-bound — Gather and
future publishers would clobber crop repair claims.

Atomic commit removes the need for a crop repair publisher on the normal path.

---

## Locked implementation checklist (post–Gate 0)

1. `CropReplantSemantics` — vanilla-only; four crops; lower-bound table above.
2. `ManagedCropDomainPolicy` / `HarvestCropVetoPolicy` — maturity-independent domain.
3. `HarvestCandidatePolicy` — deterministic feasibility; **no** `getDrops` before COMMIT.
4. `CropHarvestTransaction` — PREPARE (one roll) → RESERVE → setBlock COMMIT → bank.
5. `HarvestCropsManagedDomainMixin` — RETURN+clearTarget / HEAD veto.
6. `VillageHarvestEpisodeGoal` at P4 — admission via `VillageWorkAdmission` only.
7. **No** `MandatoryOwnershipRegistry` in crop package (structural test).

---

## Evidence gaps (honest boundary)

| Claim | Status |
| --- | --- |
| Distributed SPM jar `javap` on `HarvestCropsGoal` | **UNVERIFIED** — jar not in workspace; reference source used |
| Live `setBlock` mature→age0 in ServerLevel fixture | **UNVERIFIED** — deferred to task-55 static tests / V3 runtime campaign |
| Runtime mixin witness | **UNVERIFIED** — batched V3 campaign |

---

## Gate 0 verdict

**GATE_0_PASS.** Task-55 implementation may proceed when User authorizes **Implement V3-C** /
**authorize task-55**. Minecraft runtime remains closed until separate launch approval.
