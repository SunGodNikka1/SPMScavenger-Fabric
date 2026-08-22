# Task-57 Gate 0 report — read-only source audit (V3-E population food support)

**Status:** `GATE_0_PASS` — implementation **NOT AUTHORIZED** (Gate 0 only).

**Audit date:** 2026-08-21  
**Target project:** `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`  
**Minecraft baseline:** 1.21.1, official Mojang mappings (`loom.officialMojangMappings()`); sources via
existing Gradle `genSources` cache (same transform path as task-56 gate0).  
**Brief:** `task-57-brief.md` v1 (accepted) + User amendments **G0-A** / **G0-B** (2026-08-21).

**Authorization:** Gate 0 only — **no production Java**, **no Minecraft launch**, **no task-58**.

---

## Executive summary

| Gate | Verdict | Locked decision |
| --- | --- | --- |
| **G0-1 — Food points / thresholds** | **PASS** | `Villager.FOOD_POINTS`: bread=4, carrot/potato/beetroot=1; breeding gate `>= 12` total (`foodLevel` + inventory); gen-1 item set = `FOOD_POINTS` keys only |
| **G0-2 — Villager pickup semantics** | **PASS** | Pickup via `Mob.aiStep` looting; requires `mobGriefing`; `pickUpDelay` respected; `wantsToPickUp` + `WANTED_ITEMS` |
| **G0-3 — Read-only HOME + `HAS_SPACE`** | **PASS** | `getInRange(HOME, pos, 48, HAS_SPACE)` + `PoiRecord.hasSpace()` — **no `take()` required** for probe |
| **G0-A — Dual reachability** | **PASS** | **Two independent proofs:** (B) `Villager` navigation → HOME; (A) `PlayerMob` navigation → recipient — **must not collapse** |
| **G0-4 — Breeder HOME reachability owner** | **PASS** | `VillagerMakeLove.canReach(villager, bedPos, poiType)` — **`villager.getNavigation()`**, not PlayerMob |
| **G0-5 — SPM gift/drop primitive** | **PASS** | Reuse `tossGift` **mechanics** in dedicated `PopulationFoodHandoff`; **do not** invoke `FriendlyGreetGoal` |
| **G0-6 — Expendability authorities** | **PASS WITH CONCERNS** | Food is **unmodelled** in `SellReserveModel`; new `PopulationFoodExpendabilityPolicy` must add food-specific reserves (survival buffer, replant seeds) — cannot delegate food to trade sell path |
| **G0-7 — Harvest / population arbitration** | **PASS** | Shared `Flag.MOVE\|LOOK` at P4 prevents concurrent goals; no `VillageWorkSelector` required for gen-1 |
| **G0-8 — Willingness read-only signal** | **PASS** | `wantsMoreFood()`, `canBreed()`, `hasExcessFood()` are public — no Brain access required |
| **G0-B — Commit vs acknowledgment** | **PASS** | **ACK_WAIT** phase recommended; terminal outcomes `DELIVERED_ACK` vs `COMMITTED_UNCONFIRMED`; anti-loop binds to outcome |

**Gate 0 stop conditions:** none triggered. Implementation may proceed after separate **authorize task-57**.

---

## G0-1 — Vanilla breeding food values and thresholds

### Food-point table (`CONFIRMED` — `Villager.java`)

```100:100:C:\Users\noobk\.gradle\caches\ng_execute\4095aa04c7e27a55fa360fd148f3783175bf5135f9cdf9e76bacf637a233f06e\transformed\net\minecraft\world\entity\npc\Villager.java
    public static final Map<Item, Integer> FOOD_POINTS = ImmutableMap.of(Items.BREAD, 4, Items.POTATO, 1, Items.CARROT, 1, Items.BEETROOT, 1);
```

| Item | Food points | Notes |
| --- | ---: | --- |
| bread | **4** | highest value per slot |
| carrot | **1** | |
| potato | **1** | |
| beetroot | **1** | |

### Breeding / willingness thresholds (`CONFIRMED`)

```701:703:C:\Users\noobk\.gradle\caches\ng_execute\4095aa04c7e27a55fa360fd148f3783175bf5135f9cdf9e76bacf637a233f06e\transformed\net\minecraft\world\entity\npc\Villager.java
    public boolean canBreed() {
        return this.foodLevel + this.countFoodPointsInInventory() >= 12 && !this.isSleeping() && this.getAge() == 0;
    }
```

```864:870:C:\Users\noobk\.gradle\caches\ng_execute\4095aa04c7e27a55fa360fd148f3783175bf5135f9cdf9e76bacf637a233f06e\transformed\net\minecraft\world\entity\npc\Villager.java
    public boolean hasExcessFood() {
        return this.countFoodPointsInInventory() >= 24;
    }

    public boolean wantsMoreFood() {
        return this.countFoodPointsInInventory() < 12;
    }
```

- **`canBreed()`** — total food (`foodLevel` + inventory points) ≥ **12** (not stack count).
- **`wantsMoreFood()`** — inventory food points **< 12** (ignores private `foodLevel`; safe secondary rank).
- **Breeding digest** — `eatAndDigestFood()` consumes **12** points at love completion (`digestFood(12)`).

### Gen-1 item set (`LOCKED` for implementation)

Use **`Villager.FOOD_POINTS` keys only** (bread, potato, carrot, beetroot).

**Do not** treat wheat/seeds as breeding food — they appear in `WANTED_ITEMS` for farming/profession
pickup, not in `FOOD_POINTS`.

**NOT FOUND (3 probes):** stable `#villager_breeding_food` tag in vanilla 1.21.1 for these four items;
breeding authority is the **`FOOD_POINTS` map**, not a tag abstraction.

| Probe | Result |
| --- | --- |
| `data/minecraft/tags/item/*villager*food*` in vanilla jar | **NOT FOUND** as breeding-authority tag |
| `FOOD_POINTS` usage | **CONFIRMED** — sole breeding point table |
| PlayerMob `ForagePolicy.isEdible` overlap | **Different semantics** — must not reuse |

### Transfer quantity guidance (provisional numbers)

```text
meaningfulProgressBudget = min(
    disposableFoodValue,
    max(0, 12 - recipientInventoryFoodPoints),   // toward willingness inventory band
    MAX_EPISODE_FOOD_VALUE                       // VillageWorkTuning — PROVISIONAL
)
```

Delivery is **food-value bounded**, not “one stack” or “dump disposable.” Exact `MAX_EPISODE_FOOD_VALUE`
stays **`PROVISIONAL`** (suggested starting cap: **6** food points — half a breeding digest, enough
for meaningful progress without a full 12-point dump).

---

## G0-2 — Villager pickup and inventory semantics

### Pickup path (`CONFIRMED` — `Mob.aiStep`)

```573:584:C:\Users\noobk\.gradle\caches\ng_execute\4095aa04c7e27a55fa360fd148f3783175bf5135f9cdf9e76bacf637a233f06e\transformed\net\minecraft\world\entity\Mob.java
        if (!this.level().isClientSide
            && this.canPickUpLoot()
            && this.isAlive()
            && !this.dead
            && net.neoforged.neoforge.event.EventHooks.canEntityGrief(this.level(), this)) {
            ...
                if (!itementity.isRemoved() && !itementity.getItem().isEmpty() && !itementity.hasPickUpDelay() && this.wantsToPickUp(itementity.getItem())) {
                    this.pickUpItem(itementity);
```

| Topic | Finding |
| --- | --- |
| **mobGriefing** | Required — `canEntityGrief` gates looting (`CONFIRMED`) |
| **pickUpDelay** | Items with delay are **not** picked up (`CONFIRMED`) |
| **Recognition** | `Villager.wantsToPickUp` — `WANTED_ITEMS` + profession items + farmer seeds (`CONFIRMED`) |
| **Inventory full** | `InventoryCarrier.pickUpItem` returns early if `canAddItem` false — item stays on ground (`CONFIRMED`) |

### SPM `tossGift` interaction (`CONFIRMED` — reference `PlayerMobEntity.java`)

- Spawns `ItemEntity` with arc velocity toward target.
- **`setPickUpDelay(10)`** — villager cannot pick up for **10 ticks** minimum.
- **`setThrower(this)`** — thrower attribution (PlayerMob self-pickup semantics).

Population handoff should mirror delay/velocity; dedicated primitive avoids greet coupling.

---

## G0-3 — Read-only HOME + `HAS_SPACE` enumeration

### Non-mutating probe (`CONFIRMED` — stop condition **not** triggered)

Vanilla `PoiManager.take()` filters `getInRange(..., HAS_SPACE)` then **`acquireTicket()`**. The
**same filter predicate** used in `takeVacantBed` can be evaluated without calling `take()`:

```93:97:C:\Users\noobk\.gradle\caches\ng_execute\4095aa04c7e27a55fa360fd148f3783175bf5135f9cdf9e76bacf637a233f06e\transformed\net\minecraft\world\entity\ai\behavior\VillagerMakeLove.java
    private Optional<BlockPos> takeVacantBed(ServerLevel level, Villager villager) {
        return level.getPoiManager()
            .take(
                p_217509_ -> p_217509_.is(PoiTypes.HOME), (p_217506_, p_217507_) -> this.canReach(villager, p_217507_, p_217506_), villager.blockPosition(), 48
            );
```

**Read-only equivalent for task-57:**

```text
getInRange(HOME, recipient.blockPosition(), 48, HAS_SPACE)
  .filter(record -> canReach(recipient, record.pos, record.poiType))  // VillagerMakeLove semantics
  .findFirst()   // or bounded enumeration for multi-villager ranking
```

**Forbidden:** calling `take()` / `acquireTicket()` for probe.

**HOME `validRange`:** **1** (`PoiTypes.bootstrap` — task-56 gate0 `CONFIRMED`).

### Semantic boundary

Read-only probe matches vanilla **pre-claim** vacancy truth. It does **not** simulate post-`take()`
ticket contention from another villager claiming the bed between probe and vanilla birth — acceptable
race; task-57 does not command breeding.

---

## G0-A — Dual reachability (User amendment — `LOCKED`)

Task-57 has **two independent path proofs**. Collapsing them is an **architecture defect**.

### B — Villager → vacant HOME (breeder-local)

**Owner:** `VillagerMakeLove.canReach` — **`villager.getNavigation()`**, not PlayerMob.

```100:103:C:\Users\noobk\.gradle\caches\ng_execute\4095aa04c7e27a55fa360fd148f3783175bf5135f9cdf9e76bacf637a233f06e\transformed\net\minecraft\world\entity\ai\behavior\VillagerMakeLove.java
    private boolean canReach(Villager villager, BlockPos pos, Holder<PoiType> poiType) {
        Path path = villager.getNavigation().createPath(pos, poiType.value().validRange());
        return path != null && path.canReach();
    }
```

**Implementation type:** `BreederLocalHomeProof` calls this semantic on the **chosen recipient**
during SELECT and again at HANDOFF_PREPARE.

**Must not:** use PlayerMob path to bed block as substitute.

### A — PlayerMob → recipient (delivery)

**Owner:** PlayerMob / episode goal navigation — path to **recipient entity** or interaction point.

Mirror `VillageHarvestEpisodeGoal` (`REACH_DISTANCE_SQR = 4.0` ≈ 2 blocks) for handoff distance.

**Must not:** infer villager→HOME reachability because PlayerMob can walk to the bed.

### Locked pipeline

```text
recipient candidate
    ↓
villager-local HOME proof
    HOME + HAS_SPACE
    within 48 of recipient
    recipient→HOME satisfies VillagerMakeLove.canReach semantics
    ↓
PlayerMob bounded path
    PlayerMob→recipient
    ↓
handoff
```

| Test | Proves |
| --- | --- |
| **T57-3** | B fails → zero transfer even if A succeeds |
| New static probe | Mock where mob→bed reachable but villager→HOME not → must abort |

---

## G0-4 — Vanilla reachability for breeding (feeds G0-A-B)

Breeding behavior **does not** path-check partners to beds before love; bed reachability is evaluated
**at birth** via `takeVacantBed` predicate above. Task-57 pre-commit proof should use the **same**
`canReach` as `take()`'s `BiPredicate` — strongest vanilla alignment without mutating POI.

Partner approach uses `BehaviorUtils.lockGazeAndWalkToEachOther` (separate from bed path).

---

## G0-5 — SPM gift/greet/drop machinery

### `FriendlyGreetGoal` (`CONFIRMED` — reference v0.86.0)

- Phase machine: FOLLOW → CROUCH → GIFT → FETCH.
- Gift path: `selectGiftFromInventory` → **`mob.tossGift(friend, gift)`**.
- Gated on `feelingToward >= FEELING_LOVE` — **not** population support semantics.

### `tossGift` mechanical primitive (`CONFIRMED`)

```2395:2415:d:\Apps\Minecraft Port\Projects\references\SocialPlayerMobs-v0.86.0\src\main\java\games\brennan\playermob\entity\PlayerMobEntity.java
    public void tossGift(LivingEntity target, ItemStack gift) {
        ...
        ItemEntity thrown = new ItemEntity(level(), fromX, fromY, fromZ, gift);
        ...
        thrown.setPickUpDelay(10);
        thrown.setThrower(this);
        level().addFreshEntity(thrown);
```

### Decision (`LOCKED`)

| Reuse | Do not reuse |
| --- | --- |
| ItemEntity spawn, arc velocity, `pickUpDelay(10)`, thrower attribution | `FriendlyGreetGoal` phases, `selectGiftFromInventory`, feeling/love gates, SOCIAL taxonomy |
| Optional: Scavenger-owned `PopulationFoodHandoff.tossToward(Villager, ItemStack)` copying physics | `PlayerMobSocialHooks.onMobGift` for villager targets |

**Trade interlock (`CONFIRMED` recommendation):** block population episode toward villager `V` when
`TradeSessionClaimWindow.claims(mobId, V, tick)` — mirror greet admission mixin pattern.

---

## G0-6 — Expendability authority inventory

### Existing authorities (`CONFIRMED` — project source)

| Layer | Authority | Food relevance |
| --- | --- | --- |
| Held-item / damageable veto | `FuelExpendability.mayBurn` | Reuse veto pattern via `PopulationFoodExpendabilityPolicy` |
| Trade sell surplus | `SellExpendabilityPolicy` + `SellReserveModel` | **Food returns `OptionalInt.empty()`** — trade path **refuses** unmodelled food (`CONFIRMED`) |
| Log/plank/stick reserves | `SellReserveModel` / `FurnacePolicy` | Orthogonal unless food item is unmodelled log (N/A) |
| Crop replant | `HarvestCandidatePolicy.deterministicReplantFeasible` | Reserve **≥1 planting item** per managed crop type when drop not guaranteed |
| Mandatory block | `MandatoryOwnership` / `VillageWorkAdmission` | Blocks episode start — not per-item reserve |
| Survival buffer | SPM `ForagePolicy.HEAL_BUFFER_NUTRITION = 12` (`INFERRED` oracle) | PlayerMob keeps ~12 nutrition points when hurt — **PopulationFoodExpendabilityPolicy must protect equivalent food points for mob survival** |

### Delegation graph (`LOCKED` shape)

```text
PopulationFoodExpendabilityPolicy.disposableFoodPoints(item, backpack, mob, cfg)
  1. reject if not in Villager.FOOD_POINTS
  2. FuelExpendability-style held/main/offhand veto
  3. survival nutrition reserve (new — derive from SPM wantsFood / heal buffer policy; Gate 0 flags UNVERIFIED until Scavenger pins equivalent without SPM compile dep)
  4. SellReserveModel.reservedUnits when modelled (rare for food)
  5. replant seed reserve per HarvestCandidatePolicy planting items
  6. trade-chain reserved materials (if food ever modelled in chain — currently N/A)
  → remainder = disposable
```

**Concern:** Survival reserve for PlayerMob food is **not** yet quantified in Scavenger Java —
implementation must pin **`MIN_SURVIVAL_NUTRITION_RESERVE = 12`** (PlayerMob nutrition points, global
edible backpack pool) per **brief v1.2** — not item counts; **`reserve == 0` forbidden** as unknown
fallback. AV-1: shape **DESIGN_LOCKED**; `12` source **INFERRED** (SPM oracle); static tests
**CONFIRMED** after ship; runtime **UNVERIFIED**.

**Hard rule preserved:** population support never spends items already reserved by another consumer.

---

## G0-7 — Scheduler / priority interaction

### Registration (`CONFIRMED` — `SpmScavenger.java`)

```259:260:d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric\src\main\java\com\noobk\spmscavenger\SpmScavenger.java
        selector.addGoal(4, new PlaceTorchGoal(mob, 1.0));
        selector.addGoal(4, new VillageHarvestEpisodeGoal(mob, selector, 0.9));
```

`PopulationFoodSupportGoal` should register at **P4** alongside harvest (after brief locks order).

### Mutual exclusion (`CONFIRMED`)

Both episode goals use `EnumSet.of(Flag.MOVE, Flag.LOOK)`. Vanilla `GoalSelector` **cannot** run two
active goals claiming the same flags — second `canUse` fails while first `canContinueToUse`.

`VillageWorkAdmission` does **not** need to enumerate sibling V3 goals for gen-1 exclusion.

`DiscretionaryEligibility` already blocks fresh discretionary work when `VILLAGE_WORK` active
(task-55 observation) — relevant to Opinion, not harvest admission.

**No `VillageWorkSelector` required** for T57-10 in gen-1.

---

## G0-8 — Willingness / deficit read-only signal

| API | Access | Use |
| --- | --- | --- |
| `Villager.wantsMoreFood()` | public | secondary recipient rank — inventory < 12 points |
| `Villager.canBreed()` | public | diagnostic / defer offering if already willing enough |
| `Villager.hasExcessFood()` | public | negative rank — villager already saturated |
| `foodLevel` field | private | **do not** access — use `canBreed()` composite |

**NOT FOUND (3 probes):** public Brain/memory module exposing “food deficit” without side effects;
`MemoryModuleType` food modules are not safe external reads for gen-1.

**Locked:** deterministic structural ranking + optional `wantsMoreFood()` tie-break — **no Brain
mutation, no fake precision.**

---

## G0-B — Commit vs delivery acknowledgment (User amendment — `LOCKED`)

### Problem

`COMMIT` = backpack debit + world item exists. That is **not** villager received food.

### ACK feasibility (`CONFIRMED` — cheap and safe)

After commit, observe **without** second transfer:

| Signal | Cost | Strength |
| --- | --- | --- |
| `ItemEntity` removed / empty | 1 tick scan | weak |
| Recipient inventory food-point count increased vs pre-commit snapshot | O(slots) | **strong practical** |
| `canBreed()` flipped true | weak alone (async `foodLevel`) | supplementary only |

**Recommendation:** implement **ACK_WAIT** (observation only, bounded).

### Locked state machine amendment

```text
HANDOFF_PREPARE
    ↓
COMMIT
    item irreversibly leaves backpack (single bounded delivery)
    ↓
ACK_WAIT
    observe only — max ACK_WAIT_TICKS (PROVISIONAL: 40 ticks; must exceed toss pickUpDelay 10)
    ↓
terminal:
    DELIVERED_ACK        — inventory food-point delta ≥ committed food value (or item absorbed)
    COMMITTED_UNCONFIRMED — commit happened but ACK not observed in window
    ↓
DONE
```

| Rule | |
| --- | --- |
| ACK_WAIT **never** transfers another item | **LOCKED** |
| ACK_WAIT **never** rollbacks world state | **LOCKED** |
| Do not log “villager received food” on `COMMITTED_UNCONFIRMED` | **LOCKED** — use accurate label **OFFERED** / **HANDOFF_COMMITTED** |

### Anti-loop binding (`LOCKED`)

| Outcome | Cooldown |
| --- | --- |
| `DELIVERED_ACK` | normal per-settlement / per-recipient cooldown (`PROVISIONAL` ticks) |
| `COMMITTED_UNCONFIRMED` | **same or longer** cooldown — **must not** immediately re-offer |
| ABORT before COMMIT | shorter re-scan cooldown only |

Unconfirmed offer **must not** trigger another drop on the next tick (VR-T3e anti-loop).

---

## Gate 0 stop-condition evaluation

| Stop condition | Result |
| --- | --- |
| Only `PoiManager.take()` proves local vacancy | **NOT TRIGGERED** — read-only `getInRange` + `canReach` |
| No villager pickup for mob-spawned items | **NOT TRIGGERED** — standard looting path with griefing on |
| Expendability double-spend | **PASS WITH CONCERNS** — new food policy required; documented |
| Handoff success only “item entity existed” | **NOT TRIGGERED** — ACK_WAIT inventory delta recommended |

---

## Handoff to implementation

| Item | State |
| --- | --- |
| Gate 0 audit | **COMPLETE — PASS** |
| Brief amendments G0-A / G0-B | **incorporated in `task-57-brief.md` v1.1** |
| Task-57 implementation | **NOT AUTHORIZED** — awaits **authorize task-57** / **Implement V3-E** |
| Runtime VR-T3e / VR-T3j | **UNVERIFIED** — batched V3 campaign |

### Implementation must ship (from Gate 0)

1. `BreederLocalHomeProof` — recipient-scoped `getInRange` + `VillagerMakeLove.canReach` clone.
2. Separate `PlayerMob→recipient` path proof.
3. `PopulationFoodHandoff` — toss mechanics without greet semantics.
4. `PopulationFoodExpendabilityPolicy` — food-specific reserve stack.
5. Episode phases including **ACK_WAIT** and terminal outcome enum.
6. `MoveHolderClassifier` pin for `PopulationFoodSupportGoal → VILLAGE_WORK`.
7. Trade session interlock on same villager.
8. Behavioral tests T57-1…T57-12 + dual-reachability negative control.
