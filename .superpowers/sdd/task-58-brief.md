# Task 58 brief: V3-F opportunistic composting (`ComposterWorkFacts` + `CompostGoal`)

**Slice:** one unified, minimal village-work slice — bounded **composter-position evidence** on the
existing perception/work refresh cadence, plus one **opportunistic** compost episode that spends **one**
explicitly disposable compostable at **one** loaded known composter in **one** vanilla insertion
attempt. Task-58 owns facts + executor + reserve/transaction policies — **not** broad V3-D2
workstation awareness, **not** bone-meal demand, **not** compostable acquisition.

**Authorization gates (LOCKED sequence):**

| Gate | Status | User phrase to authorize |
| --- | --- | --- |
| **Brief design** | **v1.1 — G0 locks** (Gate 0 PASS 2026-08-22) | **BEGIN task-58 / V3-F — BRIEF DESIGN ONLY** |
| **Gate 0 — read-only source audit** | **PASS** — see `task-58-gate0-report.md` (User authorized 2026-08-22) | **authorize task-58 gate 0** |
| **Full implementation** | **NOT AUTHORIZED** | **authorize task-58** / **Implement V3-F** |

**Brief revision history:**

- v1 — initial brief from RFC locks `D58-1…D58-12`, `D-VR-085-A2`, `D-VR-086-A2`, `D-VR-087-A1`+`TX1`
  (User authorized brief design 2026-08-22)
- v1.1 — **G0 locks:** `insertItem` commit primitive; `getValue > 0` mechanical check; seed-only gen-1
  expendability; `CompostReserveModel` replant reserve = 1; P5 registration; input-only bone meal
  (Gate 0 PASS 2026-08-22)

**Target:** `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

**Source reference (SPM host):**
`d:\Apps\Minecraft Port\Projects\references\SocialPlayerMobs-v0.86.0`
**(read-only oracle — SPM is deliberately NOT a compile dependency)**

**Source evidence (Minecraft 1.21.1 vanilla — Gate 0 must pin exact revision from Gradle sources):**

| Topic | Class / symbol | Purpose |
| --- | --- | --- |
| Composter mechanics | `net.minecraft.world.level.block.ComposterBlock` — `insertItem`, `extractProduce`, `addItem`, `LEVEL` | COMMIT primitive, shrink semantics (`D-VR-087-TX1`) |
| Farmer POI | `net.minecraft.world.entity.ai.village.poi.PoiTypes` — `FARMER` registers composter | bounded POI enumeration filter |
| Farmer AI parity (evidence only) | `net.minecraft.world.entity.ai.behavior.WorkAtComposter` | Gate 0 farmer input/extract evidence — **not** automatic authority |
| Game rule | `net.minecraft.world.level.GameRules.RULE_MOBGRIEFING` | block interaction gate (task-57 precedent) |

**RFC:** `plans/RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md` — V3-F, `D58-1…D58-12`,
`D-VR-085-R1`+`A2`, `D-VR-086-A2`, `D-VR-087-A1`+`TX1`, VR-T3d, VR-T3j.

**Depends on (CLOSED / STATIC-BEHAVIORAL ACCEPT):**

| Task | Deliverable | V3-F use |
| --- | --- | --- |
| **task-52** | `MandatoryOwnership` | hard-block when pending/running mandatory claim exists — **must not** add publisher |
| **task-53** | `VillageScenarioProfile`, `VillageWorkAdmission` | profile + mandatory gate — **must not** read composter facts |
| **task-54** | storage safety (orthogonal) | no storage mutation |
| **task-55** | `CropReplantSemantics`, `HarvestCandidatePolicy`, managed harvest episode | replant reserve authority; sibling P4 `VILLAGE_WORK` mutual exclusion |
| **task-56** | `VillageWorkFacts`, observation kernel, scheduler, cache, anchor invalidation | **reuse cadence pattern** — **must not** stuff composter fields into `VillageWorkFacts` |
| **task-57** | `PopulationFoodExpendabilityPolicy`, `PlayerNutritionReserve`, episode shape | protection layers only — **not** primary compost authority |

**Not authorized without separate authorization:** production Java · Gate 0 audit · Minecraft runtime
launch · task-59 / V3-G · commit · push.

```text
  VillagePerceptionScheduler / VillageWorkFactsScheduler
  ┌────────────────────────────────────┐
  │ existing refresh cadence + budget  │
  └─────────────────┬──────────────────┘
                    ▼
         ComposterWorkFactsCache (transient)
         SettlementIdentity + bounded BlockPos list
         observedAtTick + completeness + freshness
                    │
                    ▼
            CompostGoal (P5 provisional)
         VillageWorkAdmission (no facts read)
         CompostExpendabilityPolicy
              ├─ mechanical compostability (not authority)
              ├─ CompostReserveModel (gen-1 narrow)
              └─ protection layers → unknown DENY
                    │
                    ▼
         SELECT → PATH → INTERACT_PREPARE → COMMIT → DONE
                    │
                    ▼
         vanilla ComposterBlock.insertItem (one attempt)
```

---

## Architecture lock — inherit without reopening

| Lock | Authority | Task-58 posture |
| --- | --- | --- |
| **D58-1** Opportunistic only | RFC | never manufacture compostable or bone-meal demand |
| **D58-2** Evidence not discovery | RFC | composter positions from perception/work cadence only |
| **D58-3** Transient bounded facts | `D-VR-085-A2` | separate `ComposterWorkFacts` — not `VillageWorkFacts` |
| **D58-4** Compostability ≠ expendability | `D-VR-086-A2` | two-step check; mechanical truth does not authorize spend |
| **D58-5** Unknown fails closed | `SellReserveModel` precedent | unmodelled → 0 disposable, not 0 reserve |
| **D58-6** Higher reserves win | RFC | replant / population / survival / progression before compost |
| **D58-7** One attempt per activation | `D-VR-087-A1` | no `while inventory has seeds: spam interact` |
| **D58-8** No RNG before COMMIT | task-55 anti-reroll discipline | COMMIT is sole vanilla compost RNG |
| **D58-9** Current-truth COMMIT | task-57 lesson | full preflight list — no stale SELECT snapshot |
| **D58-10** No new demand brain | RFC | no Gather/Trade/Craft demand · no `MandatoryOwnership` publisher · no `VillageWorkSelector` |
| **D58-11** Input-only gen-1 | RFC | no READY bone-meal extraction until Gate 0 resolves ownership |
| **D58-12** Runtime deferred | RFC | VR-T3d batched V3 campaign only |
| **Broad V3-D2** | RFC | **DEFERRED** — not in task-58 scope |
| **Task-57** | progress ledger | **DO NOT REOPEN** unless runtime falsifies locked invariant |

**Hard rule:** `VillageWorkAdmission` **must not** import composter fact types or use composter
positions as admission evidence. Facts are consumed inside the compost executor path only.

**Hard rule:** task-58 **must not** retrofit `VillageWorkFacts` or `VillageWorkObservationKernel`
population/HOME fields. Composter observation is a **sibling** record and cache.

---

## Why this slice exists

Shipped task-56 (`VillageWorkFacts`) supplies population/HOME evidence only — **no composter or
workstation positions** (`VillageWorkFacts.java`, `VillageWorkObservationKernel.java`). V3-F cannot
start with `CompostGoal.canUse() → scan for composters` — V3 rejected another independent village
scanner.

Task-58 closes the dependency drift with a **minimal** `ComposterWorkFacts` extension on the
**same** scheduler/budget/invalidation cadence as task-56, then an opportunistic executor that spends
only material every higher authority already declared disposable.

---

## Part A — `ComposterWorkFacts` (evidence layer)

### Persisted vs transient boundary (mirror task-56)

| Layer | Owns | Must never own (task-58) |
| --- | --- | --- |
| **`KnownVillage` / `VillageMemorySavedData`** | stable settlement identity | composter positions, freshness of composter state |
| **`VillageWorkFacts`** | population/HOME counts | composter positions (D58-3) |
| **`ComposterWorkFacts` / cache** | volatile loaded composter positions at observation time | permission to interact; durable SavedData |

### Record shape (`D-VR-085-A2` — locked)

```text
ComposterWorkFacts
    identity: SettlementIdentity
    composterPositions: immutable list<BlockPos>   // bounded, loaded-only, settlement-bound
    observedAtTick: long
    completeness: WorkFactsCompleteness
    freshness: WorkFactsFreshness                  // via FreshnessPolicy on read
```

**`isReadable()`:** `completeness == COMPLETE && freshness == FRESH` (same contract family as
`VillageWorkFacts.isReadable()`).

### Enumeration contract

Mirror task-56 CLOSE-56-2:

```text
lazy iterator over PoiManager.getInRange(FARMER, anchor, OBSERVATION_RADIUS, ANY)
    → filter: block at pos is ComposterBlock
    → filter: VillagePerception.withinPerception(level, pos)
    → filter: SettlementBoundsPolicy.within(pos, anchor)
    → cap: MAX_COMPOSTERS_PER_OBSERVATION (new tuning constant — PROVISIONAL until Gate 0)
    → NO .toList() on unbounded iterator
    → budget exceeded → completeness INCOMPLETE
```

**Perception coverage:** reuse `PerceptionCoverage.compute` — if not full, return `INCOMPLETE`
(same as `VillageWorkObservationKernel.observe`).

### Cache + scheduler integration

| Component | Requirement |
| --- | --- |
| `ComposterWorkFactsCache` | transient server map keyed by `SettlementIdentity`; RET-1 bound (`MAX_CACHED_SETTLEMENTS` or dedicated cap) |
| Refresh trigger | **same** `VillageWorkFactsScheduler.requestRefresh` path OR companion hook invoked from existing refresh executor — **no** per-tick mob scan |
| `onAnchorSuperseded` | extend `VillageWorkFactsService.onAnchorSuperseded` (or parallel seam) to invalidate composter cache + cancel pending composter refresh for stale identity |
| Chunk loading | observation uses **loaded-only** POI records — **must not** force-load chunks |

### Proposed types (names provisional until Gate 0)

| Type | Role |
| --- | --- |
| `ComposterWorkFacts` | immutable evidence record |
| `ComposterWorkFactsCache` | transient server cache + freshness application |
| `ComposterWorkObservationKernel` | pure bounded enumeration (testable without scheduler) |
| `ComposterWorkObservationService` | orchestrates kernel + tick stamp |
| `ComposterWorkFactsService` | peek/schedule/refresh/invalidate public API |

**Consumer rule:** positions are **candidates only**. COMMIT revalidates live `ComposterBlock` state.

---

## Part B — `CompostGoal` (executor layer)

### Opportunistic authority (`D58-1`, `D58-10`)

Task-58 **never** asks:

```text
"How can I obtain compostables?"
"How can I obtain bone meal?"
```

Task-58 **only** asks:

```text
"Do I ALREADY hold compostable material
 every higher authority declared disposable?"
```

**Forbidden side effects:**

- `GatherResourcesGoal` / material demand for seeds
- `TradeWithVillagerGoal` / trade demand for compostables
- craft demand for bone meal
- `MandatoryOwnershipClaim` publisher
- new progression consumer for fertilizer
- independent world block scanner in `canUse()`

### Priority (`PROVISIONAL` until Gate 0 scheduler review)

Register at **priority 5** in `SpmScavenger.java` — **below** P4 `PlaceTorchGoal`,
`VillageHarvestEpisodeGoal`, `PopulationFoodSupportGoal`; **above** P7 `CampfireGoal`.

```text
P4  torch / harvest / population food
P5  compost (task-58)
P7  campfire
P8/9 explore / wander
```

Gate 0 must confirm P5 does not create unacceptable starvation vs P4 siblings (`RUNTIME_QUESTION`
for torch contention remains documented — **do not fix** in task-58 without product decision).

### Core episode architecture

One committed episode per activation. No long-lived “compost task.”

```text
IDLE (implicit — goal not active)
        │
        ▼
SELECT
  bind SettlementIdentity
  bind ComposterWorkFacts (readable)
  bind one disposable input (CompostExpendabilityPolicy)
  bind one composter candidate + route
        │
        ▼
PATHING
        │
        ▼
INTERACT_PREPARE
  re-read current truth (full checklist §COMMIT preflight)
        │
        ▼
COMMIT
  exactly ONE vanilla ComposterBlock.insertItem attempt
        │
        ▼
DONE
  outcome-specific cooldown; clear bindings
```

### Phase table (implementation target)

| Phase | Purpose | World mutation |
| --- | --- | --- |
| `SELECT` | bind identity, facts, input slot, composter, path | none |
| `PATHING` | navigate toward composter | movement only |
| `INTERACT_PREPARE` | final preflight (tick-aligned — mirror harvest WINDUP / population HANDOFF_PREPARE) | none |
| `COMMIT` | one vanilla insertion attempt | inventory debit via `D-VR-087-TX1` mirror pattern |
| `DONE` | `stop()` clears bindings; cooldown | none |

**Mirror task-57:** nothing leaves the backpack until `INTERACT_PREPARE` passes.

### Proposed types (names provisional until Gate 0)

| Type | Role |
| --- | --- |
| `CompostGoal` | P5 `Goal` — episode state machine |
| `CompostAdmission` | `VillageWorkAdmission` wiring + executor-specific gates |
| `CompostTargetSelector` | pure deterministic composter ranking from facts + mob position |
| `CompostExpendabilityPolicy` | composes protection layers; cap 1 unit per episode |
| `CompostReserveModel` | gen-1 narrow disposable surplus (primary spend authority) |
| `CompostMechanicalEligibility` | vanilla `ComposterBlock.getValue` / tag check — **not** spend authority |
| `CompostDeliveryPlan` | immutable snapshot: identity, composter pos, slot, item, path |
| `CompostTransaction` | COMMIT primitive — vanilla `insertItem` + backpack mirror |

---

## Compostability vs expendability (`D58-4`, `D-VR-086-A2`)

```text
Step 1 — Mechanical (CompostMechanicalEligibility):
    ComposterBlock.getValue(stack) > 0  (Gate 0 pins API/tag)
    AND composter LEVEL < 7 at COMMIT
    → item CAN be inserted mechanically
    → does NOT authorize spending

Step 2 — Ownership (CompostExpendabilityPolicy):
    CompostReserveModel + protection layers
    → item MAY leave inventory
```

### `CompostReserveModel` (gen-1 — primary authority)

```text
for each supported planting kind K (Gate 0 pins initial set):
    heldUnits(K) in backpack
    - explicitReplantReserve(K)   // min 1 when managed harvest requires inventory seed
    - explicitOtherReserve(K)     // 0 until modelled
    = disposableUnits(K)

unknown material → disposableUnits = 0
```

**Gen-1 preference (PROVISIONAL — Gate 0 confirms compost values):**

| Material class | Gen-1 disposition |
| --- | --- |
| **Wheat seeds / beetroot seeds surplus** after replant reserve | **candidate** (cleanest initial domain) |
| Carrot / potato planting items | **deny gen-1** unless Gate 0 proves safe separation from villager food + nutrition |
| Villager breeding food | **deny** (`PopulationFoodExpendabilityPolicy` / `VillagerFoodInventory`) |
| Player nutrition stock | **deny** (`PlayerNutritionReserve` ≥ 12) |
| `SellReserveModel` modelled materials | subtract reserved; **empty = refuse** |
| Progression / fuel / held / offhand | **deny** via existing veto patterns |
| Unknown / modded compostable | **deny by default** |

**Forbidden gen-1 pattern:**

```text
anything vanilla says is compostable → disposable unless known otherwise
```

### `CompostExpendabilityPolicy` protection order (compost runs last)

```text
0. mechanical compostability (Gate 0)
1. FuelExpendability / held / offhand / damageable / never_fuel veto
2. PlayerNutritionReserve — MIN_SURVIVAL_NUTRITION_RESERVE = 12 (task-57)
3. SellReserveModel — empty/unmodelled → refuse entire material
4. CompostReserveModel — replant + explicit surplus (PRIMARY gen-1)
5. PopulationFoodExpendabilityPolicy — deny villager breeding food pools
6. remainder for slot = compost-disposable (executor caps at 1 per episode)
```

**`PopulationFoodExpendabilityPolicy` is a protection layer, not the primary compost authority**
(task-57 answers recipient-specific breeding food — different question).

Gate 0 **must inventory** every reserve publisher/consumer touching compostable-like items and
document the delegation graph.

---

## Target selection

### Composter candidate (from facts, not scanner)

Among `ComposterWorkFacts.composterPositions()` when facts are `readable`:

```text
loaded chunk at pos
block still ComposterBlock
LEVEL < 7
within SettlementBoundsPolicy for bound identity
reachable path under bounded probe budget
deterministic ordering within returned sample (task-56/57 precedent)
nearest / lowest path cost + stable pos tie-break
```

**No RNG** during selection (D58-8).

### Input selection

Among backpack slots with `disposableUnits > 0`:

```text
deterministic ranking (PROVISIONAL: highest disposable surplus kind first, then slot index)
exactly ONE unit for this episode
```

### Settlement binding

Composter and facts must share the **same** `SettlementIdentity` as the mob's remembered
`KnownVillage` anchor used to schedule refresh. No cross-settlement borrowing.

---

## COMMIT preflight (`D58-9` — current truth, not SELECT snapshot)

Immediately before COMMIT, revalidate **all** of:

| Check | Abort if fail |
| --- | --- |
| `RULE_MOBGRIEFING` true | yes |
| `CompostAdmission.permits` (profile + mandatory + combat/shelter) | yes |
| no mandatory pending/running claim | yes |
| same `SettlementIdentity` still current in mob memory | yes |
| `ComposterWorkFacts` still `readable` for that identity | yes |
| target chunk still loaded | yes |
| block at P is still `ComposterBlock` | yes |
| P still within settlement bounds | yes |
| `LEVEL < 7` | yes |
| mob within interaction distance | yes |
| planned stack still exists in planned slot | yes |
| same quantity still disposable per `CompostExpendabilityPolicy` | yes |
| mechanical compostability still true | yes |
| no concurrent P4 `VILLAGE_WORK` episode (harvest / population) | yes |

**Any failure:** `ABORT` — **0 inventory debit**, **0 block mutation**. No plan-fact fallback.

### `mobGriefing` authority (task-57 precedent)

Hard gate at `canUse`, `canContinueToUse`, `INTERACT_PREPARE`, and `COMMIT`.

---

## Transaction ownership (`D-VR-087-A1`, `D-VR-087-TX1`)

Scavenger decides: permission, target, expendability, quantity (1).

Vanilla decides: compost success/failure, level change, effects, scheduling.

**No project-owned compost percentage table.**

### COMMIT pattern (locked shape — Gate 0 pins exact call signature)

```text
1. INTERACT_PREPARE passes
2. copy stack from backpack slot S (count 1)
3. ComposterBlock.insertItem(mob, state, level, copy, pos)   // Gate 0: exact overload
4. if copy.count decreased: mirror same delta into backpack slot S
5. NEVER shrink/removeItem on backpack before insertItem eligibility
```

**Probabilistic result:** eligible insert that does **not** advance level is **vanilla success** —
episode terminates and backs off (D58-7, D58-8). **No retry in same activation.**

**Bone meal (D58-11):** gen-1 **input-only**. Do not call `extractProduce`. Do not path to READY
composter for extraction. Gate 0 documents Options A/B/C; implementation locks **A** unless Gate 0
proves a cleaner policy.

---

## Interruption and authority

### Before `COMMIT` — abort cleanly (zero debit)

| Interrupt | Behavior |
| --- | --- |
| `mobGriefing == false` | abort |
| combat target acquired | abort |
| shelter / mandatory safety takeover | abort |
| mandatory pending or running claim appears | abort |
| profile no longer `VILLAGE_ALLY` | abort |
| composter facts `STALE` or `INCOMPLETE` | abort |
| composter block invalid / full / unloaded | abort |
| inventory reserve change removes disposable surplus | abort |
| path timeout / unreachable | abort |
| another `VILLAGE_WORK` goal running | abort start (`canUse` false) |

### After `COMMIT`

Episode → `DONE` with cooldown. No rollback. No second insert same activation.

### Admission continuation

Use `ActivityObservationService.observeExcluding(selector, this, ...)` when admission must not
count the running episode as blocking itself (task-55 R1-3 / task-57 pattern).

Pin `CompostGoal` → `ActivityClass.VILLAGE_WORK` in `MoveHolderClassifier`.

---

## Interaction with other V3 work

| Situation | Required behavior |
| --- | --- |
| `VillageHarvestEpisodeGoal` running | `CompostGoal` does **not** start |
| `PopulationFoodSupportGoal` running | `CompostGoal` does **not** start |
| `CompostGoal` running | harvest / population do **not** start |
| mandatory claim appears | compost episode yields before commit |
| P4 torch active | documented `RUNTIME_QUESTION` — **do not fix** in task-58 |

**Do not** build `VillageWorkSelector` (D58-10).

---

## Anti-loop contract

```text
one terminal COMMIT per activation → DONE
→ cooldown (success and no-op-vanilla-attempt share backoff family)
→ no immediate re-insert on same composter/input without re-resolution
```

**Forbidden:**

- stand at composter `while (has seeds) interact`
- durable per-composter SavedData
- bone-meal demand registration

**Allowed:** schedule composter facts refresh on next normal village-work tick — not a tight spin.

---

## Required behavioral scenarios

Mapped to RFC **VR-T3d** (compost) and **VR-T3j** (mandatory authority).

| ID | Setup | Must happen | Must not happen | RFC |
| --- | --- | --- | --- | --- |
| **T58-1** | readable facts, disposable seed surplus, reachable composter `LEVEL < 7` | one insertion attempt may commit | commit without full preflight | VR-T3d |
| **T58-2** | no readable composter facts | no episode start | executor-local block scan | VR-T3d / D58-2 |
| **T58-3** | facts `INCOMPLETE` (budget exceeded) | no commit on incomplete evidence | use partial list as complete | VR-T3d |
| **T58-4** | facts become `STALE` during `PATHING` | abort before commit | stale facts at commit | VR-T3d |
| **T58-5** | mandatory claim during `PATHING` | abort before commit | compost under mandatory block | VR-T3j |
| **T58-6** | inventory loses disposable surplus during `PATHING` | zero debit | spend protected seed reserve | VR-T3d |
| **T58-7** | composter becomes full / wrong block / unloaded before commit | zero debit | mutate wrong block | VR-T3d |
| **T58-8** | successful commit path | exactly **one** unit consumed; episode ends | second insert same activation | VR-T3d |
| **T58-9** | vanilla eligible insert, level unchanged | episode terminates; backoff | treat unchanged level as retry | VR-T3d |
| **T58-10** | harvest or population P4 episode running | compost does not start | concurrent P4 village work | VR-T3j |
| **T58-11** | `mobGriefing=false` | no start; zero debit | commit with griefing off | VR-T3d |
| **T58-12** | unmodelled compostable item only | no spend | compost because vanilla accepts | VR-T3d / D58-5 |
| **T58-13** | villager breeding food only | no spend | compost population food | VR-T3d |
| **T58-14** | READY composter (`LEVEL == 8`) | no extraction code path | steal bone meal gen-1 | D58-11 |
| **T58-15** | anchor superseded after SELECT | abort or fail closed on stale identity | use stale settlement facts | D58-3 |
| **T58-16** | double-debit regression probe | single backpack delta matches vanilla shrink | independent pre-debit + vanilla shrink | D-VR-087-TX1 |

### Negative controls (implementation phase)

At least one mutation-confirmed probe per row where a single missing check would let the test pass
incorrectly (task-55 / task-56 / task-57 discipline).

---

## Gate 0 — **COMPLETE (`GATE_0_PASS`)**

See **`task-58-gate0-report.md`** for full evidence. Summary:

| # | Gate 0 must answer |
| --- | --- |
| **G0-1** | Minecraft 1.21.1 composter state machine: levels, full/READY transition, failed-chance consumption, scheduling, extraction/reset |
| **G0-2** | Canonical mutation primitive: exact safe `insertItem` overload for PlayerMob; inventory/world atomicity |
| **G0-3** | Compostability registry: vanilla + Fabric/modded mechanical truth — **not** spend authority |
| **G0-4** | Farmer POI truth: composter POI identity, occupancy, invalidation/replacement |
| **G0-5** | `WorkAtComposter` farmer parity: inputs/reserves/extraction — evidence only |
| **G0-6** | Reserve delegation: enumerate every Scavenger claimant overlapping compostables; unknown → fail closed |
| **G0-7** | Observation integration: prove bounded composter facts via existing perception/work cadence without new scanner |
| **G0-8** | Scheduler interference: P5 vs P4 harvest/population/torch and lower idle work |
| **G0-9** | Commit atomicity: one unit cannot be lost/duplicated if composter changes SELECT→COMMIT |
| **G0-10** | READY ownership: lock gen-1 **input-only** unless stronger policy proven |

**G0 locks incorporated into v1.1:** `ComposterBlock.insertItem` + mirror shrink; mechanical
`getValue(stack) > 0`; gen-1 expendability = wheat/beetroot seed surplus after replant reserve 1;
extend `VillageWorkFactsService.refreshNow`; priority **5**; no `extractProduce`.

Implementation remains **HOLD** until **authorize task-58**.

---

## Deliverables (implementation phase — **NOT AUTHORIZED**)

| Component | Path (proposed) |
| --- | --- |
| Composter facts record | `village/work/ComposterWorkFacts.java` |
| Composter cache | `village/work/ComposterWorkFactsCache.java` |
| Composter observation kernel | `village/work/ComposterWorkObservationKernel.java` |
| Composter observation service | `village/work/ComposterWorkObservationService.java` |
| Composter facts service | `village/work/ComposterWorkFactsService.java` |
| Episode goal | `goal/CompostGoal.java` |
| Admission wiring | `village/compost/CompostAdmission.java` |
| Target selection | `village/compost/CompostTargetSelector.java` |
| Reserve model | `village/compost/CompostReserveModel.java` |
| Expendability | `village/compost/CompostExpendabilityPolicy.java` |
| Mechanical check | `village/compost/CompostMechanicalEligibility.java` |
| Transaction | `village/compost/CompostTransaction.java` |
| Delivery plan | `village/compost/CompostDeliveryPlan.java` |
| Tuning | `village/compost/CompostTuning.java` + extend `VillageWorkTuning` caps |
| Taxonomy pin | `mining/MoveHolderClassifier.java` |
| Anchor invalidation hook | extend `village/work/VillageWorkFactsService.java` |
| Scheduler hook | extend `village/work/VillageWorkFactsScheduler.java` refresh executor |
| Goal registration | `SpmScavenger.java` (priority **5**) |
| Tests | `village/work/ComposterWork*Test.java`, `village/compost/*Test.java` |

**Must not ship in task-58:**

- broad V3-D2 `VillageWorkstationFacts` framework
- bone-meal extraction/application goal
- `VillageWorkSelector`
- `MandatoryOwnership` publisher
- `VillageWorkFacts` field changes
- Gather/Trade/Craft demand for compostables
- task-59 / V3-G runtime datapack

---

## Verification plan (implementation phase)

| Command | When |
| --- | --- |
| `.\gradlew.bat compileJava` | after each compile-affecting batch |
| `.\gradlew.bat test` | full suite before handoff |
| Runtime VR-T3d / VR-T3j | batched V3 campaign only — **not** authorized by this brief |

---

## Relationship to task-56 / task-57

```text
task-56 (CLOSED)  →  population/HOME facts + scheduler cadence (pattern to reuse)
task-57 (CLOSED)  →  episode shape + mobGriefing + nutrition reserve + DO NOT REOPEN
task-58 (BRIEF)   →  composter facts + opportunistic compost executor (unified)
task-59           →  V3-G integration/runtime — not in scope
```

Task-58 **reads** `VillageWorkFacts` scheduler triggers but **writes** only `ComposterWorkFacts`.
It **must not** fabricate population counts or mutate task-56 caches except through documented
invalidation/refresh hooks.

---

## Resolved product decisions (v1)

| ID | Decision | Status |
| --- | --- | --- |
| **PD-58-1** | Unified task-58 slice (facts + executor) | **LOCKED** |
| **PD-58-2** | Separate `ComposterWorkFacts` — not `VillageWorkFacts` retrofit | **LOCKED** (D58-3) |
| **PD-58-3** | Gen-1 expendability narrow — seed surplus first | **PROVISIONAL** until G0-3/G0-6 |
| **PD-58-4** | P5 registration | **PROVISIONAL** until G0-8 |
| **PD-58-5** | Bone meal input-only gen-1 | **LOCKED** (D58-11) |
| **PD-58-6** | Vanilla `insertItem` single debit owner | **LOCKED** (D-VR-087-TX1) |
| **PD-58-7** | No `VillageWorkSelector` / no `MandatoryOwnership` publisher | **LOCKED** (D58-10) |

---

## Self-review vs user authorization

| Requirement | Brief status |
| --- | --- |
| Brief design only — no production Java | **DONE** (this document) |
| Gate 0 audit | **DONE** — `GATE_0_PASS` (`task-58-gate0-report.md`) |
| No Minecraft launch | **DONE** |
| Inherit `D58-1…12` without reopening | **DONE** |
| `ComposterWorkFacts` + opportunistic executor unified | **DONE** |
| Compostability ≠ expendability + `CompostReserveModel` | **DONE** |
| Episode SELECT→COMMIT with current-truth preflight | **DONE** |
| One vanilla attempt; no RNG before COMMIT | **DONE** |
| No scanner / no demand manufacture | **DONE** |
| T58-1…T58-16 scenarios | **DONE** |
| Gate 0 ten-question checklist | **DONE** |
| Deliverables + verification plan | **DONE** |
| Task-57 DO NOT REOPEN | **DONE** |

**Status:** `BRIEF v1.1` — Gate 0 **PASS** (`task-58-gate0-report.md`)  
**Implementation:** `HOLD` — awaits **authorize task-58**

**Report path (implementation phase):** `.superpowers/sdd/task-58-report.md`
