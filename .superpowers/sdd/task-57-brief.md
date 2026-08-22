# Task 57 brief: V3-E population food support executor (`PopulationFoodSupportGoal`)

**Slice:** one bounded, committed **food-delivery episode** that offers disposable breeding food to an
eligible adult villager when settlement facts indicate population-support **candidacy**. Task-57 owns
the executor and handoff semantics — **not** breeding itself, bed claiming, or villager Brain
mutation.

**Authorization gates (LOCKED sequence):**

| Gate | Status | User phrase to authorize |
| --- | --- | --- |
| **Brief design** | **v1 — for review** | **BEGIN task-57 / V3-E — BRIEF DESIGN ONLY** (User, 2026-08-21) |
| **Gate 0 — read-only source audit** | **NOT AUTHORIZED** | **authorize task-57 gate 0** |
| **Full implementation** | **NOT AUTHORIZED** | **authorize task-57** / **Implement V3-E** |

**Target:** `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

**Source reference:** `d:\Apps\Minecraft Port\Projects\references\SocialPlayerMobs-v0.86.0`
**(read-only oracle only — SPM is deliberately NOT a compile dependency)**

**RFC:** `plans/RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md` — V3-E, D-VR-078, D-VR-082-A1,
D-VR-083-A1, D-VR-084, VR-T3e, VR-T3j.

**Depends on (CLOSED / STATIC-BEHAVIORAL ACCEPT):**

| Task | Deliverable | V3-E use |
| --- | --- | --- |
| **task-52** | `MandatoryOwnership` | hard-block when pending/running mandatory claim exists |
| **task-53** | `VillageScenarioProfile`, `VillageWorkAdmission`, `VILLAGE_WORK` taxonomy | profile + mandatory gate only — **must not** read `VillageWorkFacts` |
| **task-55** | `VillageHarvestEpisodeGoal` | sibling P4 `VILLAGE_WORK` executor; minimum mutual exclusion |
| **task-56** | `VillageWorkFacts`, `PopulationSupportVacancyPolicy`, observation kernel | **evidence only** — candidacy predicate + freshness; **never** permission or mutation target |

**Not authorized without separate authorization:** production Java · Gate 0 audit · Minecraft runtime
launch · task-58 · commit · push.

```text
  VillageWorkFacts (transient evidence)          PopulationFoodSupportGoal (P4)
  ┌──────────────────────────────┐              ┌─────────────────────────────┐
  │ FRESH + COMPLETE             │  candidate   │ VillageWorkAdmission        │
  │ adultVillagerCount >= 2      │ ────────────► │ PopulationSupportVacancy    │
  │ currentFreeHomeCapacity > 0  │  only        │ PopulationFoodExpendability │
  └──────────────────────────────┘              │ BreederLocalHomeProof       │
            ▲                                   │ RecipientSelector           │
            │ read-only                         │ one bounded delivery        │
            │                                   └──────────────┬──────────────┘
  VillageWorkFactsCache                          no breeding / no bed claim
  (task-56 — task-57 must NOT mutate)                         │
                                                              ▼
                                                   vanilla villager may breed later
                                                   (outside task-57 scope)
```

---

## Architecture lock — inherit without reopening

| Decision | Authority | Task-57 posture |
| --- | --- | --- |
| Settlement-wide **candidate** only | D-VR-083-A1 | `PopulationSupportVacancyPolicy.isPopulationSupportCandidate(facts)` — not permission |
| Commit-time breeder-local proof | D-VR-083-A1, task-56 gate0 | live `HOME` + `HAS_SPACE` within vanilla 48-block breeder radius + reachability |
| No breeding command / bed claim / Brain mutation | D-VR-078 | food offer only; vanilla decides willingness and breeding |
| `VILLAGE_WORK` is discretionary | D-VR-082-A1 | `VillageWorkAdmission` remains canonical profile + mandatory authority |
| Mandatory ownership hard-block | D-VR-084 | pending/running claim ⇒ episode cannot start or continue |
| Facts are evidence only | task-56 structural contract | task-57 reads facts; **must not** write counts, freshness, or completeness back into cache/SavedData |
| Deleted subtraction authority | D-VR-083-A1 | **no** `eligibleBedCount`, **no** `freePopulationCapacity` |

**Hard rule:** `VillageWorkAdmission` **must not** import `village.work` or read `VillageWorkFacts`.
Candidacy is evaluated inside the population-food executor path only.

---

## Why this slice exists

Task-56 supplies settlement-bound, transient evidence that a village **might** benefit from food
support. Task-57 turns that evidence into **one** bounded, reversible-until-commit world action:
transfer disposable breeding food to a chosen adult villager when breeder-local vacancy and
reachability still hold at handoff.

V3-E is **not** a breeding mission. The mob does not stand beside a villager dropping food until
breeding occurs, does not command `VillagerMakeLove`, and does not claim POI tickets.

---

## Core episode architecture

One committed episode per activation. No long-lived “breeding task.”

```text
VillageWorkFacts candidate
        │
        ▼
PopulationFoodSupport selection
  (settlement identity + schedule facts refresh if needed)
        │
        ▼
choose recipient
  (deterministic adult ranking — see §3)
        │
        ▼
disposable-food preflight
  (PopulationFoodExpendabilityPolicy)
        │
        ▼
breeder-local vacant HOME proof
  (read-only — see §4)
        │
        ▼
bounded reachable route
        │
        ▼
WALK
        │
        ▼
HANDOFF PREPARE
  re-read:
    VillageWorkAdmission
    fresh/complete facts
    recipient truth
    inventory/disposable surplus
    HOME + HAS_SPACE + reachability
        │
        ▼
COMMIT
  transfer exactly one bounded delivery
        │
        ▼
stop — episode DONE
        │
        ▼
later attempt must re-resolve everything
```

### Phase table (implementation target)

| Phase | Purpose | World mutation |
| --- | --- | --- |
| `IDLE` | scan cooldown / not running | none |
| `SELECT` | bind settlement, facts, recipient, food choice, route | none |
| `PATHING` | navigate toward recipient | movement only |
| `HANDOFF_PREPARE` | final preflight (tick-aligned with harvest WINDUP pattern) | none |
| `COMMIT` | irreversible transfer | **one** bounded delivery |
| `DONE` | `stop()` clears bindings; post-visit cooldown | none |

**Mirror task-55 pattern:** `VillageHarvestEpisodeGoal` uses `PATHING → WINDUP` with admission
re-check at windup tick 1 and commit at windup end (`VillageHarvestEpisodeGoal.java`). Population
food should use the same **late commit** shape: nothing leaves the backpack until `HANDOFF_PREPARE`
passes.

### Proposed types (names provisional until Gate 0)

| Type | Role |
| --- | --- |
| `PopulationFoodSupportGoal` | P4 `Goal` — episode state machine |
| `PopulationFoodSupportAdmission` | `VillageWorkAdmission` wiring + executor-specific gates |
| `PopulationFoodRecipientSelector` | pure deterministic recipient ranking |
| `BreederLocalHomeProof` | read-only vacant-HOME evidence around chosen villager |
| `PopulationFoodExpendabilityPolicy` | pure disposable-food authority |
| `PopulationFoodDeliveryPlan` | immutable snapshot: recipient, item stack spec, food-value budget, route handle |
| `PopulationFoodHandoff` | commit primitive — **single** delivery site |

---

## 1. Food semantics

### Gate 0 obligation (not guessed in brief)

Gate 0 **must** source-pin Minecraft **1.21.1** villager breeding food behavior:

| Food (gen-1 compare set) | Gate 0 must establish |
| --- | --- |
| bread | food points contributed to willingness / breeding |
| carrots | same |
| potatoes | same |
| beetroots | same |

**Questions for Gate 0:**

- What is the vanilla willingness threshold (food points / inventory slots)?
- Does each item type contribute equal points or per-item values?
- Is there a stable tag (`#villager_food` or equivalent) suitable for gen-1 abstraction?
- What foods does `Villager` actually accept from ground pickup vs player trade?

**Gen-1 brief preference:** vanilla breeding foods only unless Gate 0 finds a stable tag/API that
covers the same semantics without PlayerMob-edible false positives.

**Explicit rejection:** PlayerMob `wantsFood()` / `EatFoodGoal` edibility **≠** villager breeding
utility. Do not reuse mob hunger classification as villager food classification.

### `PopulationFoodExpendabilityPolicy`

One pure policy class. Authority order (highest protection wins):

```text
1. survival reserve
2. progression / mandatory material reserve
3. crop replant reserve (managed harvest episode bank)
4. active mandatory-owner requirements
5. other explicitly protected inventory (equipment, held-item veto, never-food tag, etc.)
        ↓
only remainder → village-disposable breeding food
```

**Research mandate (Gate 0 + implementation):** reuse existing expendability/reserve authorities rather
than re-deriving every reserve independently.

| Existing policy | Likely reuse |
| --- | --- |
| `FuelExpendability` | held-item / damageable / `never_fuel` veto pattern |
| `SellExpendabilityPolicy` | `heldUnits - reservedUnits` surplus shape |
| Harvest episode replant bank | subtract seeds/crop material reserved for managed replant |
| `MandatoryOwnership` / trade chain reserves | do not spend items already claimed by another consumer |

**Hard rule:** population support **must not** invent ownership of resources already reserved
elsewhere. If two consumers would spend the same stack, the policy returns `0` disposable for that
material.

**Gate 0 must inventory:** every reserve publisher/consumer that touches food-like items in the
backpack and document the delegation graph for `PopulationFoodExpendabilityPolicy`.

---

## 2. Transfer quantity

**Do not lock** magic counts (3, 6, “one stack”) in the brief. Gate 0 pins food-point values;
implementation locks numeric caps in `VillageWorkTuning` as **`PROVISIONAL`** until profiling.

### Candidate model (locked shape, provisional numbers)

```text
one episode
  → one recipient
  → one bounded delivery
  → enough disposable food to make meaningful progress toward vanilla willingness
  → hard maximum per episode (food-value budget AND item-count ceiling)
```

**Preferences:**

- Prefer **food-value budget** over raw item count when vanilla foods differ in point value.
- Delivery quantity = `min(meaningfulProgressBudget, disposableFoodValue, perEpisodeHardCap)` —
  exact formula deferred to Gate 0 food-point table.
- **Must not** dump the entire disposable surplus merely because inventory contains it.

**After successful handoff:** episode `DONE`. No second drop in the same activation.

**Anti-pattern:** standing beside villager repeatedly dropping until breeding occurs — forbidden.

---

## 3. Recipient selection

### Gen-1 eligibility (locked)

Recipient **must** be:

- alive
- adult (`getAge() == 0` — same signal as task-56 observation kernel)
- loaded
- within exact settlement bounds (`SettlementBoundsPolicy.within(villagerPos, anchor)`)
- reachable under locked local semantics (§4)
- not removed

**Forbidden:** baby villagers, dead/removed entities, out-of-settlement adults.

### Willingness / food-deficit signal (Gate 0 decision)

Gate 0 must determine whether vanilla exposes a **safe, read-only** willingness or food-inventory fact
that improves recipient choice without Brain mutation or invasive mixin coupling.

| Outcome | Gen-1 selection rule |
| --- | --- |
| **Clean read-only signal exists** | May use as secondary ranking key only — never as sole gate |
| **No clean signal** | Do **not** fake precision; use deterministic structural ranking |

### Gen-1 deterministic ranking (fallback / baseline)

```text
eligible adult villager
  + breeder-local vacant HOME evidence (read-only)
  + reachable
  + nearest / lowest path cost
  + stable UUID tie-break
```

**Do not** mutate villager inventory or Brain to simplify selection.

### Settlement binding

Recipient must belong to the **same** `SettlementIdentity` as the facts that triggered candidacy
(`dimension + anchor` from mob's remembered `KnownVillage`). No cross-settlement recipient borrowing.

---

## 4. Vacant HOME authority

### Two-layer model (locked)

| Layer | Owner | Semantics |
| --- | --- | --- |
| Settlement-wide candidate | task-56 `VillageWorkFacts.currentFreeHomeCapacity` | cheap filter only |
| Breeder-local commit proof | task-57 `BreederLocalHomeProof` | live truth at handoff |

Task-57 **must** establish around the **chosen villager**:

```text
HOME POI
AND PoiManager.Occupancy.HAS_SPACE
AND within vanilla breeder-local radius (48 blocks — Gate 0 cite)
AND reachability per locked semantics
```

### Critical prohibition

Task-57 **must NOT** call mutating `PoiManager.take(...)` (or equivalent ticket-claiming API) merely
to test vacancy. Claiming a bed ticket to probe existence is **forbidden**.

### Gate 0 obligation

1. Pin exact vanilla breeder bed search in `VillagerMakeLove` / breeding AI (1.21.1 source).
2. Identify read-only enumeration: `getInRange(HOME, center, 48, HAS_SPACE)` or stronger equivalent.
3. If the only exact vanilla path mutates POI state → design a **non-mutating approximation** and
   label its semantic boundary (`INFERRED` acceptable for probe; `CONFIRMED` required for commit
   path if approximation is used).
4. Document false-positive / false-negative risk of any approximation.

### Reachability (Gate 0 pins)

Gate 0 must cite the same reachability check vanilla breeding uses (path to bed POI block, bed
position, or interaction point — **not** guessed). Brief default: navigation path exists to a
candidate vacant HOME associated with the recipient within 48 blocks; exact API deferred to Gate 0.

---

## 5. Commit ownership

Dropping / transferring food is the **irreversible commit**. Immediately before commit, revalidate
**all** of:

| Check | Abort if fail |
| --- | --- |
| `VillageWorkAdmission` permits | yes |
| no combat target / shelter takeover / mandatory claim | yes |
| recipient still valid (alive, adult, loaded, in bounds) | yes |
| same `SettlementIdentity` still current for mob memory | yes |
| facts still `FRESH + COMPLETE` | yes |
| `PopulationSupportVacancyPolicy` still candidate | yes |
| breeder-local vacant HOME still exists | yes |
| route / interaction distance valid | yes |
| disposable food still available at planned quantity | yes |
| exact transfer quantity still expendable | yes |

**Any failure before handoff:** `ABORT` — **zero items transferred**.

**No staged inventory debit** survives across scheduler ticks. Backpack mutation happens only inside
`COMMIT` after final preflight (same discipline as `CropHarvestTransaction.commit`).

**After irreversible handoff:** do not attempt rollback of a world item the villager may already own
or pick up. Episode ends; future attempts re-resolve.

---

## 6. Transfer mechanism

### Gate 0 comparison (required before implementation)

| Option | Pros | Risks |
| --- | --- | --- |
| **Reuse existing item-drop primitive** from SPM greet/gift path | proven motion + entity spawn | semantic coupling to `FriendlyGreetGoal`, social credit, wrong `ActivityClass` |
| **Dedicated `PopulationFoodHandoff` primitive** | clean `VILLAGE_WORK` semantics | must still match vanilla pickup behavior |

**Brief lock:**

- Reuse **mechanics** where Gate 0 proves safe (spawn `ItemEntity`, throw vector, ownership).
- **Do not** reuse `FriendlyGreetGoal` admission, phases, or social episode authority.
- Population support is `ActivityClass.VILLAGE_WORK` — pin in `MoveHolderClassifier` alongside
  `VillageHarvestEpisodeGoal`.
- **Must not** increment social-event / familiarity credit merely because food was handed to a
  villager.

**Gate 0 must inspect:** pinned `FriendlyGreetGoal` gift/drop implementation in SPM reference tree;
identify the smallest mechanical primitive that can throw/drop an item without entering SOCIAL
taxonomy.

**Trade interlock:** evaluate whether `TradeSessionClaimWindow` must suppress population food toward
the same villager during an active trade session (mirror greet interlock pattern).

---

## 7. Pickup semantics

Gate 0 **must** pin how villagers acquire dropped breeding food:

| Topic | Gate 0 output |
| --- | --- |
| inventory pickup capability | can villager pick up item entities? |
| food item recognition | which items are accepted from ground? |
| `mobGriefing` | any interaction with food pickup? |
| pickup delay / ownership | player-thrown vs mob-spawned item rules |
| inventory-full behavior | what happens when villager cannot accept? |

**Problem statement:** PlayerMob spawned item **≠** guaranteed villager received food.

Gate 0 must choose the **strongest practical handoff success definition** supported by vanilla +
implementation constraints:

| Candidate success semantics | Strength | Weakness |
| --- | --- | --- |
| `ItemEntity` created | easy to test | floor item may despawn / never picked up |
| item thrown toward recipient | better intent | still no pickup proof |
| recipient inventory contains food after bounded wait | strongest practical | async; timing coupling |
| villager willingness incremented | closest to purpose | may be unreadable without invasive access |

**Brief posture:** prefer the strongest definition Gate 0 can implement without Brain mutation. Do
**not** claim “delivery succeeded” if the only proof is a floor item spawned.

Document chosen semantics in task-57 report with `CONFIRMED` / `INFERRED` / `UNVERIFIED` per AV-1.

---

## 8. Interruption and authority

### Before `COMMIT` — abort cleanly (zero transfer)

| Interrupt | Behavior |
| --- | --- |
| combat target acquired | abort |
| shelter / mandatory safety takeover | abort |
| mandatory pending or running claim appears | abort |
| profile no longer `VILLAGE_ALLY` | abort |
| facts become `STALE` or `INCOMPLETE` | abort |
| recipient invalid | abort |
| inventory reserve change removes disposable surplus | abort |
| breeder-local vacancy disappears | abort |
| path timeout / unreachable | abort |

### After `COMMIT`

Episode `DONE`. No rollback. Future candidacy requires full re-resolution.

### Admission continuation

`canContinueToUse` must re-check `PopulationFoodSupportAdmission` (profile + mandatory + combat) each
tick while pathing — mirror `VillageHarvestEpisodeGoal.canContinueToUse`.

Use `ActivityObservationService.observeExcluding(selector, this, ...)` when admission must not
count the running episode as blocking itself (task-55 R1-3 pattern).

---

## 9. Interaction with other V3 work

### Scope boundary (locked)

Task-57 **must not** build the full multi-V3 `VillageWorkSelector` unless RFC explicitly requires it
in this slice (**it does not** — V3-E row lists optional selector at V3-A level only).

### Minimum arbitration required

When both executors are P4 `MOVE|LOOK` goals, normal scheduler occupancy + `VillageWorkAdmission`
may already prevent concurrent village work. Task-57 must **prove** the minimum needed:

| Situation | Required behavior |
| --- | --- |
| `VillageHarvestEpisodeGoal` running | `PopulationFoodSupportGoal` does **not** start |
| `PopulationFoodSupportGoal` running | `VillageHarvestEpisodeGoal` does **not** start |
| mandatory claim appears | population episode yields before commit |

**Likely mechanism (Gate 0 confirms):** `ActivityObservationService` sees another running
`VILLAGE_WORK` goal → `VillageWorkAdmission` / discretionary eligibility fails → `canUse` false.
Harvest already pins `VillageHarvestEpisodeGoal → VILLAGE_WORK`. Population goal gets the same pin.

**Do not** create a general V3 director prematurely.

### Priority note

Both harvest and population register at priority **4** (D-VR-082). Whichever passes `canUse` first in
the selector ordering wins for that tick. Brief requires **explicit test** that the loser does not
start while the winner is active (T57-10).

---

## 10. Anti-loop contract

**Locked:**

```text
one successful delivery → episode ends
→ per-settlement and/or per-recipient bounded re-evaluation window
→ no immediate gift loop
```

**Provisional cooldown constants** live in `VillageWorkTuning` — **`PROVISIONAL`** until Gate 0 /
brief revision explains the failure mode each cooldown prevents (VR-T3e “endless gifting”, approach→abort
cycles).

**Forbidden:**

- durable per-villager “breeding task” SavedData
- standing drop loop until breeding triggers
- re-enqueue same recipient every tick after successful handoff

**Allowed after episode:** schedule facts refresh (task-56 scheduler) on next normal village-work tick
— not a tight spin loop.

---

## Required behavioral scenarios

Mapped to RFC **VR-T3e** (population food) and **VR-T3j** (mandatory authority).

| ID | Setup | Must happen | Must not happen | RFC |
| --- | --- | --- | --- | --- |
| **T57-1** | FRESH+COMPLETE facts, ≥2 adults, settlement vacancy, disposable food, local reachable vacant HOME | one delivery may commit | commit without full preflight | VR-T3e |
| **T57-2** | settlement candidate true; chosen villager has **no** breeder-local vacant HOME | zero transfer | transfer based on settlement-wide vacancy alone | VR-T3e |
| **T57-3** | HOME exists but not reachable under locked semantics | zero transfer | commit on clairvoyant vacancy | VR-T3e |
| **T57-4** | facts become `STALE` during `WALK` | abort before handoff | stale facts at commit | VR-T3e |
| **T57-5** | mandatory claim appears during `WALK` | abort before handoff | food spent under mandatory block | VR-T3e / VR-T3j |
| **T57-6** | inventory falls to protected reserve during `WALK` | zero transfer | spend protected food | VR-T3e |
| **T57-7** | baby / dead / removed / out-of-settlement recipient | never selected or abort before commit | select invalid recipient | VR-T3e |
| **T57-8** | successful handoff path | exactly **one** bounded delivery; episode ends | second drop same episode | VR-T3e |
| **T57-9** | no vacancy (facts not candidate) | no episode start; no repeated gifts | gift loop without deficit | VR-T3e |
| **T57-10** | crop `VILLAGE_WORK` already running | population does not start | concurrent P4 village work | VR-T3j |
| **T57-11** | recipient invalid immediately before `COMMIT` | zero transfer | commit to invalid target | VR-T3e |
| **T57-12** | multiple eligible villagers | deterministic selection; bounded path probes | random recipient; unbounded enumeration | VR-T3e |

### Negative controls (implementation phase)

At least one mutation-confirmed probe per row where a single missing check would let the test pass
incorrectly (task-55 / task-56 discipline).

---

## Gate 0 proposal (read-only — **NOT AUTHORIZED** by this brief)

Gate 0 executes only after **authorize task-57 gate 0**. If any stop condition triggers,
implementation remains **HOLD**.

### G0-1 — Vanilla breeding food values and thresholds

- Source-pin 1.21.1 `Villager`, willingness / food inventory, breeding food acceptance.
- Compare bread, carrots, potatoes, beetroots — food points, not stack size guesses.
- Output: food-value table + recommended gen-1 item set or tag.

### G0-2 — Villager pickup and inventory semantics

- How ground items enter villager inventory; delays; `mobGriefing`; full inventory behavior.
- Output: recommended handoff success definition (§7).

### G0-3 — Read-only HOME + `HAS_SPACE` enumeration

- Cite `PoiManager.getInRange` / `PoiRecord.hasSpace()` usage in breeding path.
- Prove non-mutating probe exists OR design approximation with explicit semantic boundary.
- **STOP** if only exact probe is `take()` — design alternative before implementation.

### G0-4 — Vanilla reachability for breeding

- Cite `VillagerMakeLove` / path logic used before breeding.
- Map to Scavenger navigation proof (pathfinder, interaction range).

### G0-5 — SPM gift/greet/drop machinery

- Inspect pinned `FriendlyGreetGoal` and any item-throw primitive.
- Decision: reusable mechanical primitive vs dedicated handoff — with social-credit isolation proof.

### G0-6 — Expendability authority inventory

- Map survival, progression, replant, mandatory, trade, fuel/sell reserves touching food items.
- Output: delegation graph for `PopulationFoodExpendabilityPolicy`.

### G0-7 — Scheduler / priority interaction

- Confirm `VILLAGE_WORK` mutual exclusion between harvest and population goals via
  `ActivityObservationService` + admission.
- Document selector registration order in `SpmScavenger.java` if tie-breaking matters.

### G0-8 — Willingness / deficit read-only signal (optional)

- If vanilla exposes safe read: document API and limits.
- If not: record `NOT FOUND` (three probes) and lock structural recipient ranking.

### Gate 0 stop conditions

| Condition | Action |
| --- | --- |
| Only mutating `PoiManager.take()` proves local vacancy | **HOLD** — design read-only probe first |
| No villager food pickup path exists for mob-spawned items | **HOLD** — revise handoff mechanism |
| Expendability cannot be expressed without double-spend across consumers | **HOLD** — design reserve delegation |
| Handoff success cannot be defined stronger than “item entity existed” | **PASS WITH CONCERNS** — document `UNVERIFIED` runtime gap for VR-T3e campaign |

---

## Deliverables (implementation phase — **NOT AUTHORIZED**)

| Component | Path (proposed) |
| --- | --- |
| Episode goal | `goal/PopulationFoodSupportGoal.java` |
| Admission wiring | `village/PopulationFoodSupportAdmission.java` |
| Recipient selection | `village/population/PopulationFoodRecipientSelector.java` |
| Home proof | `village/population/BreederLocalHomeProof.java` |
| Expendability | `village/population/PopulationFoodExpendabilityPolicy.java` |
| Handoff commit | `village/population/PopulationFoodHandoff.java` |
| Tuning | extend `village/work/VillageWorkTuning.java` or `village/population/PopulationFoodTuning.java` |
| Taxonomy pin | `MoveHolderClassifier.java` |
| Goal registration | `SpmScavenger.java` (priority 4) |
| Tests | `village/population/*Test.java`, scenario rows T57-1…T57-12 |

**Must not ship in task-57:** `VillageWorkSelector` general arbitration · compost executor (V3-F) ·
workstation fact consumption · breeding commands · bed claiming · `KnownVillage` population
persistence · `VillageWorkFacts` mutation.

---

## Verification plan (implementation phase)

| Command | When |
| --- | --- |
| `.\gradlew.bat compileJava` | after each compile-affecting batch |
| `.\gradlew.bat test` | full suite before handoff |
| Runtime VR-T3e / VR-T3j | batched V3 campaign only — **not** authorized by this brief |

---

## Relationship to task-56

```text
task-56 (CLOSED)  →  transient facts + candidacy policy
task-57 (BRIEF)   →  population food executor consuming facts at handoff
task-58+          →  not in scope
```

Task-57 reads `VillageWorkFacts` through `VillageWorkFactsService.peek` / cache with
`FreshnessPolicy.apply` at selection and commit boundaries. It may **schedule** facts refresh via
existing task-56 scheduler; it **must not** fabricate or persist population counts.

---

## Open product decisions (resolve in Gate 0 or brief v1.1)

| ID | Question | Brief lean |
| --- | --- | --- |
| **PD-57-1** | Handoff success = pickup proof vs item-entity spawn | strongest practical per Gate 0 |
| **PD-57-2** | Food-value “meaningful progress” budget formula | derive from vanilla threshold, cap in tuning |
| **PD-57-3** | Post-delivery cooldown scope (settlement vs recipient) | both — exact ticks provisional |
| **PD-57-4** | Trade session interlock with same villager | likely yes — Gate 0 confirms |
| **PD-57-5** | Read-only willingness signal | use only if Gate 0 finds clean API |

---

## Self-review vs user authorization

| Requirement | Brief status |
| --- | --- |
| Brief design only — no production Java | **DONE** (this document) |
| Gate 0 not run | **DONE** |
| No Minecraft launch | **DONE** |
| No task-58 | **DONE** |
| Inherit D-VR-083-A1 / 078 / 082-A1 / 084 without reopening | **DONE** |
| Episode architecture as specified | **DONE** |
| Food semantics + expendability policy | **DONE** |
| Transfer quantity — no magic 3/6 lock | **DONE** |
| Recipient selection rules | **DONE** |
| Vacant HOME — no `take()` probe | **DONE** |
| Commit preflight list | **DONE** |
| Transfer mechanism comparison | **DONE** |
| Pickup semantics | **DONE** |
| Interruption rules | **DONE** |
| Minimum V3 arbitration | **DONE** |
| Anti-loop contract | **DONE** |
| T57-1…T57-12 scenarios | **DONE** |
| Gate 0 proposal | **DONE** |

**Status:** `BRIEF v1 — FOR REVIEW`  
**Implementation:** `HOLD`
