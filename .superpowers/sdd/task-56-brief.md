# Task 56 brief: V3-D transient village-work perception (`VillageWorkFacts`)

**Slice:** bounded, loaded-world, **transient** settlement-work facts for population capacity (and
minimal workstation/support fields V3-E will need later). **Not** another `KnownVillage` / village-memory
expansion. **Not** population-food execution (task-57 / V3-E).

**Authorization gates (LOCKED sequence):**

| Gate | Status | User phrase to authorize |
| --- | --- | --- |
| **Brief design** | **v1 — for review** | (this document) |
| **Gate 0 — read-only source audit** | **NOT AUTHORIZED** | **authorize task-56 gate 0** |
| **Full implementation** | **NOT AUTHORIZED** | **authorize task-56** / **Implement V3-D** |

**Target:** `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

**Source reference:** `d:\Apps\Minecraft Port\Projects\references\SocialPlayerMobs-v0.86.0`
**(read-only oracle only — SPM is deliberately NOT a compile dependency)**

**RFC:** `plans/RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md` — D-VR-083 (budget contract),
D-VR-078 (no breeding command), V3-D / V3-E dependency edge, VR-T3f (read-only facts), VR-T3e
(population food — **deferred to task-57**).

**Depends on (CLOSED / STATIC ACCEPT):**

| Task | Deliverable | V3-D use |
| --- | --- | --- |
| **task-52** | `MandatoryOwnership` | **must not** add a publisher; facts are read-only |
| **task-53** | `VillageScenarioProfile`, `VillageWorkAdmission` | admission **must not** read `VillageWorkFacts`; facts are for later executors only |
| **task-54** | storage safety (orthogonal) | no storage types on hot path |
| **task-55** | managed crop episode (orthogonal) | **must not** change crop behavior |

**Not authorized without separate authorization:** production Java beyond this brief · Gate 0 audit ·
Minecraft runtime launch · task-57 / V3-E implementation · commit · push.

```text
  VillageMemorySavedData (persisted)              VillageWorkFactsCache (transient)
  ┌─────────────────────────────┐                ┌──────────────────────────────┐
  │ MobVillageMemory            │                │ keyed by SettlementIdentity  │
  │   KnownVillage              │   anchor bind  │   (dimension + anchor pos)   │
  │     anchor (stable id)      │ ─────────────► │                              │
  │     tier / quality / ticks  │                │ VillageWorkFacts             │
  │     (NO villager/bed counts)│                │   villagerCount              │
  └─────────────────────────────┘                │   eligibleBedCount           │
            ▲                                    │   freePopulationCapacity     │
            │ peek only                          │   workstation/support (min)  │
            │                                    │   observedAtTick             │
  VillagePerceptionObserver ──► VillagePerceptionScheduler ──► observeWorkFacts()
  (dirty / heartbeat / debounce)     (shared budget)              (loaded-only)
            │                                    │
            └──────── no second world scanner ───┘
                              │
                              ▼
                   PopulationCapacityPolicy (pure)
                   FreshnessPolicy (pure)
                              │
                              ▼
                   (task-57 / V3-E consumes later — NOT task-56)
```

## Why this slice exists

`D-VR-083` locks population food support on:

```text
freePopulationCapacity = max(0, eligibleBedCount - villagerCount)
food support eligible  iff villagerCount >= 2 AND freePopulationCapacity > 0
```

`CODE_CONFIRMED`: `KnownVillage` exposes only `ObservationQuality` / aggregate `poiCount()` — no
per-type POI breakdown, no villager count, no eligible HOME capacity (`KnownVillage.java` javadoc +
RFC task-dependency correction 2026-08-19). V3-E therefore **cannot** start until V3-D supplies
trustworthy, **transient**, **revalidatable** facts.

V1 `VillagePerception` already performs a bounded, loaded-only POI query for settlement discovery
(`VillagePerception.java`). V3-D **widens retained facts** and adds villager/entity counting — it does
**not** add a second omniscient scanner.

## Architecture lock (User, 2026-08-21)

### Persisted vs transient boundary

| Layer | Owns | Must never own (task-56) |
| --- | --- | --- |
| **`KnownVillage` / `VillageMemorySavedData`** | stable settlement identity (`anchor`), tier, first/last seen, observation **quality of discovery** | `villagerCount`, `eligibleBedCount`, `freePopulationCapacity`, workstation occupancy, freshness of population state |
| **`VillageWorkFacts` / cache** | volatile settlement-work evidence at observation time | durable SavedData rows, cross-session population counters, authority to execute V3 work |

**Hard rule:** do not add volatile villager/bed/workstation population counts to `KnownVillage` or any
other persistent semantic memory. Do not “solve freshness” by writing counts into SavedData.

### Settlement binding

Facts are keyed by **`SettlementIdentity`** = `(dimension, anchor)` matching a **specific**
`KnownVillage.anchor()` the mob remembers. Observation uses
`SettlementBoundsPolicy.within(observationOrigin, anchor)` — same radius family as existing village
work, **not** a new ad-hoc merge radius.

**Multi-village rule:** overlapping perception radii **must not** merge counts. Two nearby remembered
villages → two independent `VillageWorkFacts` entries. A mob asking about village A reads facts for A's
anchor only.

### Population capacity (locked formula — policy only in task-56)

```text
freePopulationCapacity = max(0, eligibleBedCount - villagerCount)
```

Task-56 implements the **facts + pure policy** that computes this. Task-57 owns whether/when food
support becomes **eligible** (`villagerCount >= 2` gate + disposable surplus + executor).

### Bed semantics (layers — Gate 0 must pin strongest vanilla interpretation)

`eligibleBedCount` **must not** mean “bed blocks seen” or `KnownVillage.poiCount()`.

Gate 0 must determine the strongest **vanilla 1.21.1-backed** interpretation of breeding-capacity
truth, explicitly distinguishing:

| Layer | Question | task-56 posture |
| --- | --- | --- |
| **Bed observed** | Is there a bed block at a loaded position? | diagnostic only — **not** capacity |
| **HOME POI present** | Does `PoiManager` report a `PoiTypeTags.HOME` / `PoiTypes.HOME` record? | necessary, not sufficient |
| **HOME POI usable** | Is the POI valid for villager brain use (loaded chunk, unobstructed, correct type)? | Gate 0 pins |
| **HOME POI occupied/claimed** | Is the POI ticket held by a villager? | Gate 0 pins occupancy semantics |
| **Eligible for capacity** | Does this HOME POI contribute to `eligibleBedCount`? | **only** rows passing Gate 0 rules |

**Rejected without Gate 0 evidence:** treating every `IS_OCCUPIED` village POI as a bed; using
`admittedPoiCount` from settlement discovery as bed count; counting job-site POIs toward population
capacity.

### Freshness / completeness

`VillageWorkFacts` are **evidence**, not permission.

| State | Meaning | V3-E consumption rule (task-56 establishes facts only) |
| --- | --- | --- |
| **FRESH** | observation within freshness window; completeness meets minimum | may be consumed by later policy |
| **STALE** | observation older than window or invalidated by dirty trigger | **must not** authorize affirmative work |
| **INCOMPLETE / UNKNOWN** | required chunks/entities unavailable; partial coverage; observation aborted | **fail closed** — not positive capacity |

Stale/incomplete **must not** silently become `freePopulationCapacity > 0`.

## Perception budget (D-VR-083 — architecture locked)

Reuse existing machinery where possible:

| Mechanism | Reuse |
| --- | --- |
| `VillagePerceptionObserver` | dirty on chunk transition + phased heartbeat (`VillagePerceptionTuning.HEARTBEAT_TICKS`) |
| `VillagePerceptionEnqueueDebounce` | empty-result / denied enqueue backoff |
| `VillagePerceptionScheduler` | shared server tick budget; fair lanes; emergency cap |
| `VillagePerception.withinPerception` / `PerceptionCoverage` | loaded-chunk construction invariant |
| `SettlementBoundsPolicy` | bind facts to remembered anchor |

**Must:**

- inspect **loaded** chunks only (`ServerLevel#hasChunk` — no generation side effects);
- use a **finite** observation radius (provisional: reuse `VILLAGE_QUERY_RADIUS` or a **≤** subset —
  Gate 0 pins);
- cap **entity** and **POI** candidate work per observation;
- **stagger** via scheduler — not every `PlayerMob` scanning every tick;
- apply **empty-result backoff** when observation yields no usable facts;
- prefer **non-allocating reads** on hot paths (`peek` cache, immutable snapshots);
- share work across mobs observing the same `SettlementIdentity` where safe (one refresh serves many readers).

**Must not:**

- `/locate` or clairvoyant unloaded POI reads;
- force-load chunks;
- add a parallel always-on world scanner;
- call `VillagePerception.observe()` on executor hot paths (same rule as task-54/55).

**Provisional tuning home:** `VillageWorkTuning` (new) for radii, caps, freshness windows — values
**PROVISIONAL** until profiling; architecture is locked, numbers are not.

## Load-bearing requirements

| # | Requirement |
| --- | --- |
| 1 | Introduce **`VillageWorkFacts`** — immutable snapshot: `villagerCount`, `eligibleBedCount`, `freePopulationCapacity`, optional minimal workstation/support fields for V3-E, `observedAtTick`, `freshness`, `completeness`, `SettlementIdentity`. |
| 2 | Introduce **`SettlementIdentity`** — `(ResourceKey<Level> dimension, BlockPos anchor)`; equals/hash on immutable anchor; binds to one `KnownVillage`. |
| 3 | **`VillageWorkFactsCache`** — transient, server-scoped; keyed by `SettlementIdentity`; evicted on server stop; bounded entries with explicit eviction (RET-1 declaration required). **No SavedData persistence.** |
| 4 | **`VillageWorkObservationService`** — performs one bounded observation for a given identity + observation origin; returns `VillageWorkFacts` or `INCOMPLETE`. |
| 5 | **`PopulationCapacityPolicy`** — pure `freeCapacity(facts)` implementing locked formula; returns `OptionalInt` or tri-state — **no** `> 0` ⇒ permission. |
| 6 | **`FreshnessPolicy`** — pure classification `FRESH` / `STALE` / `INCOMPLETE` from tick + completeness flags. |
| 7 | **Scheduler integration** — extend or companion-hook `VillagePerceptionScheduler` so work-fact refresh shares the global POI/observation budget; at most one pending refresh per `(dimension, settlementIdentity)` (or per mob dirty → resolves to settlement keys) — Gate 0 pins exact dedup key. |
| 8 | **Settlement-bound counting** — villager query and HOME POI admission filtered to `SettlementBoundsPolicy.within(pos, anchor)` for the target identity. |
| 9 | **Multi-village isolation** — scenario 6 is a hard gate; no aggregation across anchors. |
| 10 | **Unreadable facts fail closed** — `INCOMPLETE` / `STALE` ⇒ downstream must treat as **unknown capacity**, not zero and not positive. Document tri-state API. |
| 11 | **`VillageWorkAdmission` unchanged** — must not import facts; task-56 adds **no** P4 executor. |
| 12 | **No `MandatoryOwnership` publisher** — observation is read-only. |
| 13 | **Diagnostics seam** — optional `VillageWorkFactsDiagnostics` (session counters, stale/incomplete tallies) mirroring task-54/55 compat pattern; diagnostics **never** grant permission. |
| 14 | **Gate 0 before implementation** — bed eligibility and villager counting seams must be pinned from vanilla/MC sources; implementation stops if Gate 0 cannot establish a defensible `eligibleBedCount`. |

## Rejected (locked)

| Proposal | Why rejected |
| --- | --- |
| Persist villager/bed counts in `KnownVillage` NBT | Violates transient/persisted boundary; stale counts become permanent lies |
| Use `poiCount()` / `ObservationQuality.admitted()` as bed proxy | Aggregate discovery metric ≠ HOME capacity (RFC correction) |
| `villagerCount - bedCount > 0` food predicate | Rewards over-full villages — reversed vs D-VR-083 |
| Second full-world scanner per mob per tick | Budget contract violation |
| Workstation facts authorize trade/placement/claiming | VR-T3f — read-only facts only |
| Population food gifting / breeding / bell / raid in task-56 | task-57+ scope |
| `VillageWorkSelector` / new P4 goals | task-57+ scope |
| Crop / storage behavior changes | task-55/54 closed |
| Clairvoyant unloaded POI enumeration | Same failure mode `VillagePerception` javadoc forbids |

## Package layout (proposed)

| Path | Role |
| --- | --- |
| `village/work/SettlementIdentity.java` | stable fact cache key |
| `village/work/VillageWorkFacts.java` | immutable snapshot + freshness/completeness enums |
| `village/work/VillageWorkFactsCache.java` | transient server cache; peek/observe/invalidate |
| `village/work/VillageWorkObservationService.java` | bounded loaded observation kernel |
| `village/work/PopulationCapacityPolicy.java` | pure `freePopulationCapacity` |
| `village/work/FreshnessPolicy.java` | pure freshness classification |
| `village/work/VillageWorkTuning.java` | **PROVISIONAL** caps/radii/windows |
| `village/work/VillageWorkFactsSchedulerHook.java` (or extend `VillagePerceptionScheduler`) | enqueue shared work-fact refresh |
| `village/work/VillageWorkFactsDiagnostics.java` | optional session diagnostics |
| `src/test/java/.../village/work/*Test.java` | behavioral + structural tests |

**Explicitly NOT in this slice:**

- `PopulationFoodSupportGoal` / gift executor (task-57)
- `VillageWorkSelector`, `VillageWorkIntent`
- breeding commands, bed claiming, Brain mutation
- compost / crop / storage changes
- `MandatoryOwnershipRegistry.publish`
- SavedData persistence for facts
- Minecraft runtime / mixin wiring unless Gate 0 + implementation authorized

## Required scenario parity (task-56 tests)

| # | Scenario | Must happen | Must not happen |
| --- | --- | --- | --- |
| **1** | 2 villagers + genuinely free eligible HOME capacity in bound settlement | `freePopulationCapacity > 0`, `FRESH` facts | Treat bed-block count as capacity |
| **2** | 2 villagers + all eligible beds occupied/claimed | `freePopulationCapacity == 0` | Positive capacity from raw bed blocks |
| **3** | Beds visible but outside target `SettlementBoundsPolicy` | Excluded from `eligibleBedCount` | Radius bleed into neighbor settlement |
| **4** | Remembered village but chunks/facts unavailable | `INCOMPLETE` / `UNKNOWN` | Silent `capacity > 0` |
| **5** | Villagers/beds change after observation | Prior facts become `STALE`; no affirmative authorization until revalidated | Stale facts treated as fresh permission |
| **6** | Multiple nearby remembered villages | Independent facts per `SettlementIdentity` | Merged population counts |
| **7** | Additional beds/villagers in unloaded chunks | Count only loaded, in-bounds evidence | Omniscient totals |
| **8** | Many `PlayerMob`s in same area | Shared/staggered scheduler cadence; bounded probes per tick | Per-mob per-tick full scan multiplication |

## Gate 0 proposal (read-only — **not authorized**)

Gate 0 is a **source audit only**. No production Java. Output: `task-56-gate0-report.md`.

### G0-1 — Vanilla HOME POI / bed occupancy semantics (1.21.1)

**Pin from MC sources / mappings:**

- `PoiTypes.HOME`, `PoiTypeTags.HOME`, `PoiTypeTags.VILLAGE` membership
- `PoiManager.Occupancy.ANY` vs `IS_OCCUPIED` — which matches “claimed HOME”
- How `PoiRecord` exposes holder / ticket / owner
- Villager `Brain` HOME memory module — bed reservation vs POI claim
- Whether “eligible bed” means **unoccupied HOME ticket**, **total HOME POIs with valid tickets**, or
  vanilla breeding cap formula — cite strongest interpretation

**Deliverable:** decision table mapping layers (observed → HOME POI → usable → occupied → eligible).

### G0-2 — Villager counting seam

**Pin:**

- `ServerLevel#getEntities` / `Villager` type filter within settlement bounds
- baby vs adult counting for `villagerCount` (breeding support cares about adults — Gate 0 decides)
- dedup / spectator / removed handling
- loaded-chunk requirement (entity in unloaded chunk ⇒ incomplete, not zero)

### G0-3 — Loaded-only POI / entity query behavior

**Pin:**

- Reuse `VillagePerception.withinPerception` vs dedicated helper
- Interaction with `PoiManager#getInRange` unloaded-section hazard (documented in `VillagePerception`)
- Maximum POI records / entities processed per observation

### G0-4 — `VillagePerceptionScheduler` reuse points

**Pin:**

- Whether work-fact refresh is a **second consumer** on the same scheduler vs extended `ObservationConsumer`
- Dedup key: `(dimension, mobId)` vs `(dimension, SettlementIdentity)`
- Budget sharing with existing `GLOBAL_QUERY_BUDGET_PER_TICK = 1` — acceptable latency tradeoff
- Heartbeat / debounce reuse from `VillagePerceptionObserver`

### G0-5 — Settlement binding

**Pin:**

- `SettlementIdentity` ↔ `KnownVillage.anchor()` equality rules (immutable `BlockPos`)
- Which mob position is observation origin (mob feet vs anchor)
- Behavior when anchor is stale but memory still lists village (re-observe vs incomplete)

### G0-6 — Freshness / cache ownership

**Pin:**

- Cache key, eviction (server stop, identity forget, manual invalidate on chunk unload?)
- Freshness window ticks (provisional starting point: multiple of `HEARTBEAT_TICKS`)
- Invalidation triggers: chunk unload in footprint, villager death/spawn burst, POI ticket change
- RET-1 table: key, bound, production eviction, death/unload/dimension/stop behavior

### G0-7 — V3-E consumption contract (read-only spec for task-57)

**Document exactly what task-57 may read:**

| Field | Allowed use |
| --- | --- |
| `villagerCount` | predicate input only when `FRESH` + `COMPLETE` |
| `eligibleBedCount` | same |
| `freePopulationCapacity` | same — still not permission |
| `freshness` / `completeness` | mandatory gate before any V3-E eligibility |
| workstation/support subfields | read-only hints; no trade/claim side effects |

**Explicitly forbid:** using facts when `STALE` or `INCOMPLETE`; using facts without settlement binding;
using facts as `VillageWorkAdmission` input.

### Gate 0 stop conditions

- Cannot define `eligibleBedCount` with vanilla citation → **BLOCKED** (do not implement)
- Only clairvoyant unloaded reads available → **BLOCKED**
- Facts require persistence to be correct → **design error** — return to brief

## Verification plan (post-implementation — not run in brief phase)

| Command | When |
| --- | --- |
| `.\gradlew.bat compileJava` | after every compile-affecting batch |
| `.\gradlew.bat test` | task-56 handoff |
| Runtime VR-T3e/f | **deferred** — batched V3 campaign; not task-56 closure |

**Acceptance target (static):** scenarios 1–8 covered by behavioral tests; structural guards for
persisted-memory boundary, scheduler dedup, and admission non-coupling; Gate 0 report `PASS` or
`PASS WITH RESTRICTIONS` recorded.

## Relationship to task-57 / V3-E

```text
task-56 (V3-D)  →  trustworthy VillageWorkFacts + policies
task-57 (V3-E)  →  population food executor consuming facts under disposable-surplus rules
```

Task-56 **establishes** `freePopulationCapacity` truth. Task-57 **decides** whether to act on it.
No executor code in task-56.

## Brief revision history

- **v1** — User authorization 2026-08-21: brief design only; architecture locks for
  persisted/transient split, population formula, bed layers, freshness, budget, settlement binding,
  scenario parity, Gate 0 proposal.
