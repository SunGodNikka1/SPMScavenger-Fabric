# Task-56 Gate 0 report — read-only source audit (V3-D `VillageWorkFacts`)

**Status:** `GATE_0_PASS` — D-VR-083-A1 (**R2 vanilla vacancy**) adopted; implementation **AUTHORIZED**.

**Audit date:** 2026-08-21  
**Target project:** `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`  
**Minecraft baseline:** 1.21.1, official Mojang mappings (`loom.officialMojangMappings()`);
`.\gradlew.bat genSources` **CONFIRMED** (5363 decompile misses, BUILD SUCCESSFUL).  
**Brief:** `task-56-brief.md` v1 (accepted).

**Authorization:** Gate 0 only — **no production Java**, **no Minecraft launch**, **no task-57**.

---

## Executive summary

| Gate | Verdict | Locked decision |
| --- | --- | --- |
| **G0-1 — `eligibleBedCount` / bed layers** | **RESOLVED (D-VR-083-A1)** | **`currentFreeHomeCapacity`** (`HAS_SPACE`) is population-support candidate signal; **`eligibleBedCount` / subtraction deleted**. |
| **G0-2 — `SettlementIdentity` anchor lifecycle** | **PASS** | Anchor replacement A→B: **B canonical**; invalidate cache + pending work for **A**; **no** count migration; **no** proximity merge; sharing only on **exact** `SettlementIdentity`. |
| **G0-3 — Workstation facts** | **PASS** | **No** workstation/support fields in task-56 — no immediate consumer; ship minimal `VillageWorkFacts` (identity, counts, observedAtTick, completeness, freshness). |
| **G0-4 — Scheduler reuse** | **PASS** | Second consumer on existing `VillagePerceptionScheduler` budget; work-fact dedup key **`(dimension, SettlementIdentity)`**; village-memory perception keeps **`(dimension, mobId)`**. |
| **G0-5 — Settlement binding** | **PASS** | Facts bound to `KnownVillage.anchor()` via `SettlementBoundsPolicy.within(pos, anchor)` (64-block radius family); observation origin = **mob feet** at refresh time. |
| **G0-6 — Freshness / cache RET-1** | **PASS** | Transient server cache; explicit invalidation on anchor supersede + freshness window; bounded entries; server stop clears all. |
| **G0-7 — V3-E consumption contract** | **PASS** | Task-57 may read listed fields only when `FRESH` + `COMPLETE`; facts never admission input. |

---

## G0-1 — `eligibleBedCount` precision and vanilla breeding bed semantics

### User-mandated layer model (LOCKED vocabulary)

| Layer | Definition (settlement-bound, loaded-only) | Role in task-56 |
| --- | --- | --- |
| **`totalUsableHomeCapacity`** | Count of `PoiTypes.HOME` records in bounds with `PoiManager.Occupancy.ANY`, after `VillagePerception.withinPerception` | Capacity denominator candidate for D-VR-083 |
| **`claimedHomeCount`** | Count with `Occupancy.IS_OCCUPIED` (`freeTickets != maxTickets`) | Diagnostic; ticket-held beds |
| **`currentFreeHomeCapacity`** | Count with `Occupancy.HAS_SPACE` (`freeTickets > 0`) | **Vanilla breeding bed gate** |
| **`villagerCount`** | Adult villagers (`getAge() == 0`) in bounds, loaded chunks | RFC predicate input |

**Identity (HOME POI, maxTickets=1):** for each loaded HOME record,
`totalUsableHomeCapacity = claimedHomeCount + currentFreeHomeCapacity` when all HOME POIs in the
count set are fully visible.

**Forbidden:** `eligibleBedCount = currentFreeHomeCapacity` then
`freePopulationCapacity = eligibleBedCount - villagerCount` — **double-counts occupancy**.

### Vanilla 1.21.1 evidence (`CONFIRMED` — Mojang sources via `genSources`)

#### HOME POI registration

`PoiTypes.bootstrap` registers `HOME` on bed **HEAD** block states with **`maxTickets = 1`**,
`validRange = 1`:

```122:122:C:\Users\noobk\.gradle\caches\ng_execute\4095aa04c7e27a55fa360fd148f3783175bf5135f9cdf9e76bacf637a233f06e\transformed\net\minecraft\world\entity\ai\village\poi\PoiTypes.java
        register(registry, HOME, BEDS, 1, 1);
```

New `PoiRecord` starts with `freeTickets = maxTickets()` (fully unclaimed):

```38:40:C:\Users\noobk\.gradle\caches\ng_execute\4095aa04c7e27a55fa360fd148f3783175bf5135f9cdf9e76bacf637a233f06e\transformed\net\minecraft\world\entity\ai\village\poi\PoiRecord.java
    public PoiRecord(BlockPos pod, Holder<PoiType> poiType, Runnable setDirty) {
        this(pod, poiType, poiType.value().maxTickets(), setDirty);
    }
```

#### Occupancy predicates

```68:74:C:\Users\noobk\.gradle\caches\ng_execute\4095aa04c7e27a55fa360fd148f3783175bf5135f9cdf9e76bacf637a233f06e\transformed\net\minecraft\world\entity\ai\village\poi\PoiRecord.java
    public boolean hasSpace() {
        return this.freeTickets > 0;
    }

    public boolean isOccupied() {
        return this.freeTickets != this.poiType.value().maxTickets();
    }
```

```303:306:C:\Users\noobk\.gradle\caches\ng_execute\4095aa04c7e27a55fa360fd148f3783175bf5135f9cdf9e76bacf637a233f06e\transformed\net\minecraft\world\entity\ai\village\poi\PoiManager.java
    public static enum Occupancy {
        HAS_SPACE(PoiRecord::hasSpace),
        IS_OCCUPIED(PoiRecord::isOccupied),
        ANY(p_27223_ -> true);
```

`take()` — the breeding bed claim — filters **`HAS_SPACE`** and calls `acquireTicket()`:

```155:162:C:\Users\noobk\.gradle\caches\ng_execute\4095aa04c7e27a55fa360fd148f3783175bf5135f9cdf9e76bacf637a233f06e\transformed\net\minecraft\world\entity\ai\village\poi\PoiManager.java
    public Optional<BlockPos> take(Predicate<Holder<PoiType>> typePredicate, BiPredicate<Holder<PoiType>, BlockPos> combinedTypePosPredicate, BlockPos pos, int distance) {
        return this.getInRange(typePredicate, pos, distance, PoiManager.Occupancy.HAS_SPACE)
            .filter(p_217934_ -> combinedTypePosPredicate.test(p_217934_.getPoiType(), p_217934_.getPos()))
            .findFirst()
            .map(p_217881_ -> {
                p_217881_.acquireTicket();
                return p_217881_.getPos();
```

#### Breeding behavior — decisive bed check

`Villager.canBreed()` checks **food + adult age only** — **no bed / POI / villager-count term**:

```701:703:C:\Users\noobk\.gradle\caches\ng_execute\4095aa04c7e27a55fa360fd148f3783175bf5135f9cdf9e76bacf637a233f06e\transformed\net\minecraft\world\entity\npc\Villager.java
    public boolean canBreed() {
        return this.foodLevel + this.countFoodPointsInInventory() >= 12 && !this.isSleeping() && this.getAge() == 0;
    }
```

`VillagerMakeLove.isBreedingPossible()` likewise has **no** bed-count predicate — only breed target
validity + `canBreed()` on both partners.

**At birth**, vanilla calls `takeVacantBed()` → `PoiManager.take(HOME, …, radius **48**)`:

```65:77:C:\Users\noobk\.gradle\caches\ng_execute\4095aa04c7e27a55fa360fd148f3783175bf5135f9cdf9e76bacf637a233f06e\transformed\net\minecraft\world\entity\ai\behavior\VillagerMakeLove.java
    private void tryToGiveBirth(ServerLevel level, Villager parent, Villager partner) {
        Optional<BlockPos> optional = this.takeVacantBed(level, parent);
        if (optional.isEmpty()) {
            level.broadcastEntityEvent(partner, (byte)13);
            level.broadcastEntityEvent(parent, (byte)13);
        } else {
```

```93:97:C:\Users\noobk\.gradle\caches\ng_execute\4095aa04c7e27a55fa360fd148f3783175bf5135f9cdf9e76bacf637a233f06e\transformed\net\minecraft\world\entity\ai\behavior\VillagerMakeLove.java
    private Optional<BlockPos> takeVacantBed(ServerLevel level, Villager villager) {
        return level.getPoiManager()
            .take(
                p_217509_ -> p_217509_.is(PoiTypes.HOME), (p_217506_, p_217507_) -> this.canReach(villager, p_217507_, p_217506_), villager.blockPosition(), 48
            );
```

**NOT FOUND (3 probes):** no vanilla `getCountInRange(HOME, …)` subtracted against villager entity
count in breeding path; no `totalBeds - population` formula in `VillagerMakeLove`, `VillagerGoalPackages`,
or `Villager.canBreed()`.

| Probe | Result |
| --- | --- |
| `getCountInRange` + `HOME` in `world/entity` | **Only** `CatSpawner` (cat spawn heuristic, `IS_OCCUPIED`) — not breeding |
| `villager.*bed` / `bed.*villager` in `world/entity` | Breeding path only via `VillagerMakeLove.takeVacantBed` |
| `freePopulation` / `eligibleBed` in vanilla sources | **NOT FOUND** |

#### Secondary occupancy: block-state `BedBlock.OCCUPIED`

`ValidateNearbyPoi` treats a bed as physically occupied when the block state says so — **orthogonal**
to POI tickets (`ValidateNearbyPoi.bedIsOccupied`). Task-56 capacity layers use **POI ticket truth**
(to match `take()` / `AcquirePoi`), not block-state `OCCUPIED` alone.

### Decision table — observed → eligible

| Stage | Vanilla / mod signal | Contributes to |
| --- | --- | --- |
| Bed block present | Bed HEAD in loaded chunk | POI may exist after section sync |
| HOME POI present | `PoiManager.exists(HOME, pos)` | `totalUsableHomeCapacity` (ANY) |
| HOME POI ticket free | `hasSpace()` / `HAS_SPACE` | `currentFreeHomeCapacity` |
| HOME POI ticket held | `isOccupied()` / `IS_OCCUPIED` | `claimedHomeCount` |
| Villager adult in bounds | `EntityType.VILLAGER`, `getAge()==0` | `villagerCount` |
| Breeding succeeds | `takeVacantBed` non-empty + path `canReach` | **`currentFreeHomeCapacity > 0`** near breeder (48), not global subtraction |
| RFC `freePopulationCapacity` | `max(0, eligibleBedCount - villagerCount)` | **Policy layer (D-VR-083)** — not vanilla's authoritative breeding gate |

### OUTCOME B — semantic mismatch (explicit)

**Vanilla's decisive breeding-capacity fact** at birth time is:

> ∃ a `PoiTypes.HOME` record with **`HAS_SPACE`**, within **48** blocks of the breeding villager,
> path-reachable — then `take()` claims it for the baby.

**D-VR-083 locked formula** assumes:

```text
freePopulationCapacity = max(0, eligibleBedCount - villagerCount)
```

with `eligibleBedCount` intended as **total** HOME capacity (not free beds).

These align **only in equilibrium** when `claimedHomeCount ≈ villagerCount` (each adult holds one
HOME ticket). They **diverge** when ticket holders and villager census disagree.

| Scenario | `total` | `villagerCount` | `currentFree` | RFC `total − villagers` | Vanilla breed at birth |
| --- | ---: | ---: | ---: | ---: | --- |
| Homeless surplus | 4 | 6 | 2 | **0** | **Can** (`take` finds HAS_SPACE) |
| Ticket leak / stale claim | 4 | 3 | 0 | **1** | **Cannot** (no HAS_SPACE) |

**Gate 0 ruling:** This is **OUTCOME B**. Do **not** rename `currentFreeHomeCapacity` to
`eligibleBedCount` to force the RFC formula to fit vanilla.

**Implementation HOLD:** Task-56 Java implementation must not ship `PopulationCapacityPolicy` /
`freePopulationCapacity` as the locked D-VR-083 predicate until D-VR-083 is reconciled. Acceptable
pre-reconciliation work (if separately authorized): observation kernel + multi-layer counts +
freshness/cache only.

### Reconciliation options (for D-VR-083 — product decision, not Gate 0)

| Option | Change | Tradeoff |
| --- | --- | --- |
| **R1** | Keep subtraction formula; `eligibleBedCount = totalUsableHomeCapacity`; document non-equivalence | Simple headroom proxy; may over/under-shoot vs actual breeding |
| **R2** | V3-E predicate uses `currentFreeHomeCapacity > 0` (+ `villagerCount >= 2`) | Matches vanilla gate; abandons locked subtraction formula |
| **R3** | Conjunctive: subtraction **and** `currentFreeHomeCapacity > 0` | Conservative food support; stricter than either alone |
| **R4** | Expose all layers; defer `PopulationCapacityPolicy` until task-57 brief locks consumer | Best auditability; two-step delivery |

**Recommendation:** **R4** for implementation sequencing — facts carry all four layers; policy waits
for explicit D-VR-083 amendment.

---

## G0-2 — `SettlementIdentity(anchor)` lifecycle

### Evidence (`CONFIRMED` — project source)

Anchor is immutable per `KnownVillage` instance; supersede returns a **replacement** object:

```102:109:d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric\src\main\java\com\noobk\spmscavenger\village\KnownVillage.java
    KnownVillage withObservation(BlockPos newAnchor, long tick, ObservationQuality newQuality) {
        long previousSeen = lastSeenTick;
        observedAt(tick);
        if (!newQuality.supersedes(quality, tick, previousSeen)) {
            return this;
        }
        return new KnownVillage(
                newAnchor, tier, firstSeenTick, Math.max(tick, previousSeen), newQuality);
```

`MobVillageMemory.remember()` swaps the list entry and **rekeys relationship** — it does **not**
migrate transient work facts:

```142:150:d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric\src\main\java\com\noobk\spmscavenger\village\MobVillageMemory.java
    public KnownVillage remember(BlockPos anchor, long tick, ObservationQuality quality) {
        KnownVillage existing = at(anchor).orElse(null);
        if (existing != null) {
            BlockPos oldAnchor = existing.anchor();
            KnownVillage updated = existing.withObservation(anchor, tick, quality);
            if (updated != existing) {
                villages.set(villages.indexOf(existing), updated);
                rekeyRelationship(oldAnchor, updated.anchor());
```

### Locked lifecycle (User preference — conservative)

```text
KnownVillage(anchor=A)  --supersede-->  KnownVillage(anchor=B)

SettlementIdentity(dim, B)  → canonical for all new reads
SettlementIdentity(dim, A)  → stale/orphan; facts invalidated immediately; allowed to expire
```

| Concern | Locked behavior |
| --- | --- |
| Cached facts under A | **`invalidate(A)`** on supersede; entries may age out under cache bound — **never** migrate counts A→B |
| Pending scheduler refresh for A | Cancel / refuse to apply results keyed to A; enqueue refresh for B if a mob still remembers that village |
| Readers | Resolve `SettlementIdentity` from **current** `KnownVillage.anchor()` after `remember()` — never trust a stale anchor cached on the mob |
| Orphan cleanup | Bounded cache (RET-1) + explicit invalidation on supersede + server stop `clear()`; **no** fuzzy proximity merge |
| Cross-mob sharing | **Exact** `SettlementIdentity` equality only — **no** new global village-merge algorithm |

**Hook site (`INFERRED`):** `VillageWorkFactsCache` invalidation callable from
`VillagePerceptionService` / memory write path when `updated.anchor() != oldAnchor` after `remember()`.
Exact wiring is implementation detail; Gate 0 requires the **semantic** contract above.

---

## G0-3 — Workstation / support fields

| Consumer probe | Result |
| --- | --- |
| Task-57 / V3-E population food (brief) | Uses `villagerCount`, capacity layers, freshness — **no** workstation fields |
| Task-56 brief optional V3-E fields | **Deferred** — no immediate executor question |
| RFC V3-D “job-site/restock facts” | **Future slice** — not dependency-ready for task-56 |

**Locked model for task-56:**

```text
VillageWorkFacts
├── SettlementIdentity
├── villagerCount
├── totalUsableHomeCapacity
├── claimedHomeCount
├── currentFreeHomeCapacity
├── observedAtTick
├── completeness
└── freshness
```

`freePopulationCapacity` / `PopulationCapacityPolicy` — **blocked** until G0-1 reconciliation (§G0-1).

Widen when a named executor documents a concrete field requirement (task-57 brief or later V3 slice).

---

## G0-4 — `VillagePerceptionScheduler` reuse

**Evidence (`CONFIRMED`):** `VillagePerceptionScheduler` — global budget
`GLOBAL_QUERY_BUDGET_PER_TICK = 1`, pending dedup `(dimension, mobId)`:

```17:18:d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric\src\main\java\com\noobk\spmscavenger\village\VillagePerceptionTuning.java
    public static final int GLOBAL_QUERY_BUDGET_PER_TICK = 1;
```

```88:89:d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric\src\main\java\com\noobk\spmscavenger\village\VillagePerceptionScheduler.java
    boolean requestObservation(ResourceKey<Level> dimension, UUID mobId) {
        PendingKey key = new PendingKey(dimension, mobId);
```

| Mechanism | Village memory (existing) | Work facts (task-56) |
| --- | --- | --- |
| Observer dirty / heartbeat | `VillagePerceptionObserver` | Reuse same dirty cadence to **enqueue** settlement refresh |
| Debounce | `VillagePerceptionEnqueueDebounce` | Reuse for empty/blocked observation |
| Scheduler | `VillagePerceptionScheduler` | **Second `ObservationConsumer`** or companion drain — **same** tick budget |
| Dedup key | `(dimension, mobId)` | **`(dimension, SettlementIdentity)`** — one pending refresh per settlement |
| Budget | 1 global query/tick | Shared — acceptable latency; no per-mob full scan |

**Must not:** parallel always-on scanner; `VillagePerception.observe()` on executor hot paths (brief lock).

---

## G0-5 — Settlement binding

| Rule | Source | Lock |
| --- | --- | --- |
| Identity key | `(dimension, KnownVillage.anchor())` | `BlockPos` immutable; equals/hash on anchor |
| Spatial filter | `SettlementBoundsPolicy.within(pos, anchor)` | 64-block radius (`VILLAGE_QUERY_RADIUS²`) |
| Observation origin | Mob `blockPosition()` at refresh | Not anchor-as-origin; anchor only bounds counts |
| Multi-village | One `KnownVillage` → one `SettlementIdentity` | Scenario 6 — **no** count aggregation across anchors |
| Anchor superseded | Reader uses **B**; facts for **A** invalid | §G0-2 |

**Note:** Vanilla breeding `takeVacantBed` uses **48** from **breeder position**; task-56 settlement
counts use **anchor-bound 64** — intentional mod settlement model, not a clone of per-villager 48.

---

## G0-6 — Freshness, completeness, cache RET-1

### Freshness (`INFERRED` — provisional tuning)

| State | Condition |
| --- | --- |
| **FRESH** | `gameTime - observedAtTick <= freshnessWindow` AND completeness minimum met |
| **STALE** | Window expired OR explicit invalidation (anchor supersede, dimension change) |
| **INCOMPLETE** | Partial chunk coverage; observation aborted; required entities/POIs in unloaded in-bounds footprint |

**Provisional window:** `k * HEARTBEAT_TICKS` (`k >= 2`, default **2** → 400 ticks) — **PROVISIONAL**.

**Invalidation triggers (production):**

1. Anchor supersede A→B (§G0-2)
2. Freshness window expiry
3. Server stop / dimension unload of cache owner
4. Optional: chunk unload inside settlement footprint → mark stale (do not clairvoyant refresh)

### RET-1 declaration (task-56 cache)

| Field | Value |
| --- | --- |
| **Key** | `SettlementIdentity` (`ResourceKey<Level>` + `BlockPos` anchor) |
| **Bound** | `VillageWorkTuning.MAX_CACHED_SETTLEMENTS` — **PROVISIONAL** (start **64**) |
| **Eviction owner** | `VillageWorkFactsCache.put` / `peek` path — LRU by `observedAtTick`; explicit `invalidate`; server stop `clear()` |
| **Death** | N/A — cache is server-global, not per-mob |
| **Unload** | Preserve until stale/expired — not SavedData |
| **Dimension change** | Cache partitioned by `ResourceKey<Level>` |
| **Server stop** | `VillageWorkFactsCache.clear()` via server lifecycle hook (mirror `VillagePerceptionScheduler.shutdown`) |
| **Anchor supersede** | `invalidate(oldIdentity)` — **no** migration |

**Tri-state API:** `STALE` / `INCOMPLETE` ⇒ downstream treats capacity as **unknown** — not zero, not positive.

---

## G0-7 — V3-E consumption contract (read-only for task-57)

Task-57 may read **only** when `freshness == FRESH` **and** `completeness` meets minimum:

| Field | Allowed use |
| --- | --- |
| `villagerCount` | Predicate input |
| `totalUsableHomeCapacity` | Predicate / diagnostics |
| `claimedHomeCount` | Diagnostics |
| `currentFreeHomeCapacity` | Predicate candidate post D-VR-083 reconciliation |
| `freePopulationCapacity` | **Blocked** until D-VR-083 reconciled |
| `freshness` / `completeness` | Mandatory gate |

**Forbidden:** `VillageWorkAdmission` import; facts as permission; affirmative work on `STALE`/`INCOMPLETE`;
breeding commands / bed claim / Brain mutation (D-VR-078).

---

## Gate 0 stop-condition evaluation

| Stop condition | Result |
| --- | --- |
| Cannot define bed layers with vanilla citation | **PASS** — layers defined; vanilla cites `HAS_SPACE` / `take()` |
| Only clairvoyant unloaded reads | **PASS** — reuse `withinPerception` / `hasChunk` invariant |
| Facts require persistence | **PASS** — transient cache only |
| RFC formula matches vanilla breeding gate | **FAIL → OUTCOME B** — **implementation HOLD** |

---

## Handoff

| Item | State |
| --- | --- |
| Gate 0 audit | **COMPLETE** |
| Task-56 implementation | **AUTHORIZED** — D-VR-083-A1 synchronized |
| Task-57 | **NOT STARTED** — breeder-local 48-block reachability revalidation deferred |

**Suggested D-VR-083 amendment question (one line):** Should V3-E population food support track
**headroom** (`totalUsableHomeCapacity − villagerCount`), **vanilla vacancy** (`currentFreeHomeCapacity`),
or **both** (conjunctive)?
