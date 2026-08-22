# Task-58 Gate 0 report — read-only source audit (V3-F opportunistic composting)

**Status:** `GATE_0_PASS` — implementation **NOT AUTHORIZED** (Gate 0 only).

**Audit date:** 2026-08-22  
**Target project:** `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`  
**Minecraft baseline:** 1.21.1, official Mojang mappings (`loom.officialMojangMappings()`); evidence from
Fabric Loom merged jar + `.\gradlew.bat genSources` (BUILD SUCCESSFUL 2026-08-22).  
**Brief:** `task-58-brief.md` v1 (authorized 2026-08-22).

**Authorization:** Gate 0 only — **no production Java**, **no Minecraft launch**, **no task-59**.

---

## Executive summary

| Gate | Verdict | Locked decision |
| --- | --- | --- |
| **G0-1 — Composter state machine** | **PASS** | Levels 0–7 fill; level 7 schedules +20t tick → 8 (READY); failed RNG still consumes item via `insertItem`; READY extraction resets to 0 and spawns bone meal |
| **G0-2 — Canonical mutation primitive** | **PASS** | `ComposterBlock.insertItem(entity, state, level, stack, pos)` — single shrink owner; mirror delta to backpack |
| **G0-3 — Compostability registry** | **PASS** | Mechanical: `ComposterBlock.getValue(stack) > 0` (`COMPOSTABLES` map); Fabric modded: `CompostingChanceRegistry.INSTANCE` — **not** spend authority |
| **G0-4 — Farmer POI truth** | **PASS** | `PoiTypes.FARMER` registers all composter block states; bounded `getInRange(FARMER, …)` filter to `ComposterBlock` at COMMIT |
| **G0-5 — Farmer parity (`WorkAtComposter`)** | **PASS** | Vanilla farmer composts **wheat/beetroot seeds only**; extracts at level 8 — evidence only, not PlayerMob authority |
| **G0-6 — Reserve delegation** | **PASS WITH CONCERNS** | New `CompostReserveModel` required; `SellReserveModel` returns **empty** for seeds (correct fail-closed); overlapping protectors enumerated below |
| **G0-7 — Observation integration** | **PASS** | Extend `VillageWorkFactsService.refreshNow` / `drainBudget` hook — same cadence as task-56; no executor scanner |
| **G0-8 — Scheduler interference** | **PASS WITH CONCERNS** | **P5** structurally correct (below P4 harvest/population/torch, above P7 campfire); `PlaceTorchGoal` contention remains `RUNTIME_QUESTION` |
| **G0-9 — Commit atomicity** | **PASS** | `insertItem` shrinks only when `level < 7` and compostable; pre-debit forbidden; stale full composter → no shrink |
| **G0-10 — READY ownership** | **PASS** | Gen-1 **input-only (Option A)** locked — no `extractProduce` in task-58 |

**Gate 0 stop conditions:** none triggered. Implementation may proceed after separate **authorize task-58**.

---

## G0-1 — Composter state machine (Minecraft 1.21.1)

**Evidence (`CONFIRMED` — Fabric Loom merged jar, official Mojang mappings):**

| Constant / property | Value |
| --- | --- |
| `ComposterBlock.MIN_LEVEL` | 0 |
| `ComposterBlock.MAX_LEVEL` | 7 (fill cap) |
| `ComposterBlock.READY` | 8 (bone meal ready) |
| `ComposterBlock.LEVEL` | `BlockStateProperties.LEVEL_COMPOSTER` |

### Level transitions

```text
INSERT (eligible, level < 7):
    addItem() rolls random against getValue(item)
    success → level + 1
    failure → level unchanged
    insertItem ALWAYS stack.shrink(1) when eligible

level == 7 after successful addItem:
    scheduleTick(pos, block, 20)

tick at level == 7:
    cycle LEVEL → 8 (READY sound)

READY (level == 8):
    extractProduce() → spawn bone meal entity, reset level → 0
```

**`addItem` RNG (`CONFIRMED` — javap `ComposterBlock.addItem`):**

```text
if (level != 0 || chance > 0) AND random.nextDouble() < chance:
    level++
else:
    state unchanged
```

**Failed-chance consumption (`CONFIRMED`):** `insertItem` calls `addItem` then **unconditionally** `stack.shrink(1)` when
`level < 7 && getValue(stack) > 0` — unchanged level after failed roll is **completed attempt**, not retry signal.

**Full composter rejection (`CONFIRMED`):** when `level >= 7`, `insertItem` returns input state **without** shrinking stack.

---

## G0-2 — Canonical mutation primitive

### Locked commit API

```java
BlockState next = ComposterBlock.insertItem(
    mob,                    // PlayerMob entity — GameEvent context
    blockState,             // live composter state at pos
    serverLevel,
    singleItemCopy,         // count-1 view of backpack slot
    composterPos);
```

**Post-conditions (`CONFIRMED`):**

| Outcome | Backpack | Block |
| --- | --- | --- |
| Eligible insert | `copy.count` decreased by 1 → mirror to slot | `next` may equal or differ from input state |
| Level ≥ 7 or not compostable | unchanged (no shrink) | unchanged |
| Failed RNG | −1 item | level may be unchanged |

**Rejected patterns:**

- `stack.consume(1)` before `insertItem` (double debit)
- `useItemOn` player path (different consumption semantics; not entity-insertion shaped)
- Pre-debit backpack then call `addItem` directly without shrink discipline

**Interaction distance (`PROVISIONAL`):** reuse harvest episode reach family (`REACH_DISTANCE_SQR = 4.0` —
`VillageHarvestEpisodeGoal.java`); Gate implementation may tune after pathing probe.

**`mobGriefing` (`INFERRED` — project authority, not vanilla composter gate):**

| Probe | Result |
| --- | --- |
| `ComposterBlock.insertItem` / `addItem` | **NOT FOUND** — no `RULE_MOBGRIEFING` check |
| task-57 precedent | **LOCKED** — hard gate at canUse / canContinue / PREPARE / COMMIT |

Vanilla does not gate composter insertion on `mobGriefing`; task-58 inherits **project policy** from task-57.

---

## G0-3 — Compostability registry (mechanical truth only)

### Vanilla map (`CONFIRMED` — `ComposterBlock.bootStrap()` + javap `getValue`)

Mechanical eligibility for implementation:

```java
ComposterBlock.getValue(stack) > 0.0F
```

javap shows `addItem` / `insertItem` consult `COMPOSTABLES.getFloat(stack.getItem())`; default return −1.0F.

### Gen-1 seed compost chances (task-58 initial domain)

| Item | `COMPOSTABLES` chance | Gen-1 expendability |
| --- | ---: | --- |
| `Items.WHEAT_SEEDS` | **0.30** | **candidate** after `CompostReserveModel` |
| `Items.BEETROOT_SEEDS` | **0.30** | **candidate** after `CompostReserveModel` |
| `Items.CARROT` | 0.65 | **deny gen-1** (villager food + planting item) |
| `Items.POTATO` | 0.65 | **deny gen-1** |
| `Items.BREAD` | 0.85 | **deny** (villager breeding food) |

### Fabric modded extension (`CONFIRMED` — `fabric-content-registries-v0`)

`net.fabricmc.fabric.api.registry.CompostingChanceRegistry.INSTANCE` extends `Item2ObjectMap<Float>`.

**Rule:** use for **mechanical** `getValue` augmentation only in `CompostMechanicalEligibility`. Modded
compostability does **not** imply expendability (`D58-4`).

**NOT FOUND (3 probes):**

| Probe | Result |
| --- | --- |
| Minecraft `#c:compostables` item tag as authority | **NOT FOUND** in vanilla jar as spend/compost gate |
| `ComposterBlock.getValue` delegates to tag | **NOT FOUND** — uses `COMPOSTABLES` map |
| Scavenger existing compost helper | **NOT FOUND** — greenfield |

---

## G0-4 — Farmer POI truth

**Evidence (`CONFIRMED` — `PoiTypes.java` in mapped sources):**

```text
register(registry, FARMER, getBlockStates(Blocks.COMPOSTER), 1, 1);
```

| Topic | Finding |
| --- | --- |
| POI type for composter | `PoiTypes.FARMER` |
| Block association | **all** `Blocks.COMPOSTER` states |
| Task-58 observation | bounded `getInRange(FARMER, anchor, OBSERVATION_RADIUS, ANY)` + live block filter `instanceof ComposterBlock` |
| Occupancy for PlayerMob target | **not required** for candidate list — COMMIT checks `LEVEL < 7` only |
| Invalidation | reuse `VillageWorkFactsService.onAnchorSuperseded` + `ComposterWorkFactsCache.invalidate` |

**Rejected:** executor-local cubic block scan; stuffing positions into `VillageWorkFacts`.

---

## G0-5 — Java farmer parity (`WorkAtComposter`)

**Evidence (`CONFIRMED` — javap Fabric 1.21.1 `WorkAtComposter`):**

```text
COMPOSTABLE_ITEMS = ImmutableList.of(WHEAT_SEEDS, BEETROOT_SEEDS)
```

Farmer workstation loop:

1. If `LEVEL == 8` → `ComposterBlock.extractProduce(villager, …)` first  
2. Then insert seeds from villager inventory via `ComposterBlock.insertItem` (multi-attempt loop with budget)

**Implications for task-58 (evidence only):**

| Farmer behavior | PlayerMob gen-1 |
| --- | --- |
| Composts wheat/beetroot **seeds** | Aligns with `CompostReserveModel` seed-first domain |
| Extracts READY bone meal | **Do not mirror** — `D58-11` input-only |
| Multi-insert per workstation visit | **Do not mirror** — one attempt per activation (`D58-7`) |

---

## G0-6 — Reserve delegation graph

### Scavenger claimants overlapping compostables

| Authority | Items touched | Posture for compost |
| --- | --- | --- |
| **`CompostReserveModel` (new)** | wheat/beetroot seeds gen-1 | **primary spend authority** — `held − replantReserve` |
| **`HarvestCandidatePolicy`** | planting items per `CropReplantSemantics` | reserve **≥ 1** wheat/beetroot seed when inventory-seeded replant required |
| **`SellReserveModel`** | logs, planks, sticks only | seeds → `OptionalInt.empty()` → **refuse** (correct) |
| **`PlayerNutritionReserve`** | edible items | seeds generally non-edible → orthogonal; edible compostables denied |
| **`PopulationFoodExpendabilityPolicy`** | `Villager.FOOD_POINTS` items | bread/carrot/potato/beetroot → **deny** |
| **`FuelExpendability`** | damageable, held, `never_fuel` tag | tools/armor → deny |
| **`MandatoryOwnership`** | gather routes | hard-block episode; **no publisher** from task-58 |
| **Furnace / smelt / craft chains** | coal, iron, wheat, etc. | unmodelled → **refuse** via `SellReserveModel.empty` or explicit deny |

### `CompostReserveModel` gen-1 lock (`PROVISIONAL` numbers)

```text
disposableWheatSeeds  = max(0, count(WHEAT_SEEDS)  - REPLANT_RESERVE_WHEAT)   // REPLANT_RESERVE = 1
disposableBeetSeeds   = max(0, count(BEETROOT_SEEDS) - REPLANT_RESERVE_BEET)  // REPLANT_RESERVE = 1
```

**Rationale (`CONFIRMED` — `CropReplantSemantics.java`):** wheat/beetroot have `guaranteedPlantingDrop=false`;
`HarvestCandidatePolicy` requires `≥ 1` planting item in backpack before managed harvest.

**Carrot/potato gen-1:** **deny** — villager `FOOD_POINTS` + planting item + compostable; defer until explicit
surplus model exists.

**Unknown / modded compostable:** disposable = 0 (`D58-5`).

---

## G0-7 — Observation integration (no new scanner)

**Evidence (`CONFIRMED` — shipped production):**

```113:119:d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric\src\main\java\com\noobk\spmscavenger\village\VillagePerceptionScheduler.java
    public void onServerTick(MinecraftServer server) {
        int budget = VillagePerceptionTuning.GLOBAL_QUERY_BUDGET_PER_TICK;
        int used = serviceUpTo(budget, server::getLevel);
        int remaining = budget - used;
        if (remaining > 0) {
            com.noobk.spmscavenger.village.work.VillageWorkFactsService.drainBudget(server, remaining);
        }
    }
```

**Locked integration pattern:**

```text
VillagePerceptionScheduler.onServerTick
    → VillageWorkFactsService.drainBudget
        → VillageWorkFactsScheduler.serviceUpTo
            → refreshNow (EXTEND)
                ├─ VillageWorkObservationService.observe  (existing)
                └─ ComposterWorkObservationService.observe (new)
```

**Also wire:**

- `VillageWorkFactsService.scheduleForMob` / `refreshNow` path (already used by task-57 selection)
- `onAnchorSuperseded` → invalidate **both** caches + `cancelPending`

**Mirror task-56 CLOSE-56-2:** lazy FARMER POI iterator, `MAX_COMPOSTERS_PER_OBSERVATION` cap, no `.toList()`.

---

## G0-8 — Scheduler interference (P5)

**Current registration (`CONFIRMED` — `SpmScavenger.java`):**

| Priority | Goals |
| ---: | --- |
| **4** | `PlaceTorchGoal`, `VillageHarvestEpisodeGoal`, `PopulationFoodSupportGoal` |
| **7** | `CampfireGoal` |
| **8–9** | explore / wander band |

**Locked:** register `CompostGoal` at **priority 5**.

**Arbitration (`CONFIRMED` — task-57 G0-7 pattern):**

- P4 `VILLAGE_WORK` siblings use `ActivityObservationService` — concurrent harvest/population blocked
- P5 compost cannot preempt P4 when P4 `canUse()` true
- `PlaceTorchGoal` P4 contention may delay compost — **`RUNTIME_QUESTION`** (document only; do not fix in task-58)

---

## G0-9 — Commit atomicity (SELECT → COMMIT)

| Hazard | Mitigation |
| --- | --- |
| Double debit | Only `insertItem` shrinks copy; mirror once to backpack |
| Composter becomes full | COMMIT preflight `LEVEL < 7`; `insertItem` no-ops without shrink |
| Composter replaced | live block `instanceof ComposterBlock` check |
| RNG re-roll loop | one `insertItem` per activation; unchanged level → DONE + cooldown |
| Chunk unload | loaded-chunk check at PREPARE |

---

## G0-10 — READY ownership (bone meal)

| Option | Description | Gen-1 |
| --- | --- | --- |
| **A — input only** | Add disposable material; never extract READY output | **`LOCKED`** |
| B — self-produced ownership | Track causal contribution | **REJECTED** — over-engineered |
| C — public village work | Ally may empty READY composter | **DEFERRED** — needs public-resource policy |

**Evidence for A:** farmer extracts at level 8 (`WorkAtComposter`); PlayerMob extraction risks stealing
village production without ownership tracking.

**Implementation:** never call `ComposterBlock.extractProduce` in task-58; skip targets with `LEVEL >= 7`.

---

## Brief amendments (v1.1 locks for implementation)

| ID | Lock |
| --- | --- |
| **G0-2** | Commit primitive = `ComposterBlock.insertItem` + backpack mirror |
| **G0-3** | Mechanical = `ComposterBlock.getValue(stack) > 0`; gen-1 expendability seeds only |
| **G0-6** | `CompostReserveModel` with replant reserve = 1 for wheat/beetroot seeds |
| **G0-7** | Extend `refreshNow` / shared scheduler — no parallel scanner |
| **G0-8** | `CompostGoal` priority **5** |
| **G0-10** | Input-only; no extraction |

---

## Self-review vs task-58 brief

| Brief Gate 0 item | Report status |
| --- | --- |
| G0-1 state machine | **DONE** |
| G0-2 mutation primitive | **DONE** |
| G0-3 compostability registry | **DONE** |
| G0-4 farmer POI | **DONE** |
| G0-5 farmer parity | **DONE** |
| G0-6 reserve delegation | **DONE** (with concerns) |
| G0-7 observation integration | **DONE** |
| G0-8 scheduler | **DONE** (with concerns) |
| G0-9 commit atomicity | **DONE** |
| G0-10 READY ownership | **DONE** |
| No production Java | **DONE** |
| No Minecraft launch | **DONE** |

**Status:** `GATE_0_PASS`  
**Implementation:** `HOLD` — awaits **authorize task-58**
