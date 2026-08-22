# Task 57 brief: V3-E population food support executor (`PopulationFoodSupportGoal`)

**Slice:** one bounded, committed **food-delivery episode** that offers disposable breeding food to an
eligible adult villager when settlement facts indicate population-support **candidacy**. Task-57 owns
the executor and handoff semantics — **not** breeding itself, bed claiming, or villager Brain
mutation.

**Authorization gates (LOCKED sequence):**

| Gate | Status | User phrase to authorize |
| --- | --- | --- |
| **Brief design** | **v1.2 — final locks** (User, 2026-08-21) | **BEGIN task-57 / V3-E — BRIEF DESIGN ONLY** |
| **Gate 0 — read-only source audit** | **PASS** — see `task-57-gate0-report.md` (User authorized 2026-08-21) | **authorize task-57 gate 0** |
| **Full implementation** | **NOT AUTHORIZED** | **authorize task-57** / **Implement V3-E** |

**Brief revision history:**

- v1 — initial brief (User accepted 2026-08-21)
- v1.1 — **G0-A** dual reachability + **G0-B** commit/ACK separation locked after Gate 0 authorization
- v1.2 — **final locks:** `mobGriefing` authority gate; `MIN_SURVIVAL_NUTRITION_RESERVE = 12`;
  PD-57-6 greet interlock; PD-57-7 recipient food-need invariant; PlaceTorch runtime question;
  Gate 0 self-review drift fix (User, 2026-08-21)

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
| **`mobGriefing` authority** | **v1.2 LOCKED** | Hard gate at `canUse`, `canContinueToUse`, `HANDOFF_PREPARE`, `COMMIT` — not optimization |
| **Survival nutrition reserve** | **v1.2 LOCKED** | `MIN_SURVIVAL_NUTRITION_RESERVE = 12` PlayerMob nutrition points (not `FOOD_POINTS`) |
| **Recipient food-need invariant** | **v1.2 LOCKED** | `wantsMoreFood() && !canBreed()` at SELECT + `HANDOFF_PREPARE` |
| **Exact-villager interlocks** | **v1.2 LOCKED** | SOCIAL binding + trade claim block handoff to **that** villager only — not global village work |

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
    HOME + HAS_SPACE + reachability (B: villager→HOME, A: mob→recipient)
        │
        ▼
COMMIT
  transfer exactly one bounded delivery
  (item irreversibly leaves backpack)
        │
        ▼
ACK_WAIT
  observe only — inventory / item-entity absorption
  never transfer again; never rollback
        │
        ▼
terminal: DELIVERED_ACK | COMMITTED_UNCONFIRMED
        │
        ▼
DONE — episode ends
        │
        ▼
later attempt must re-resolve everything
```

### Phase table (implementation target)

| Phase | Purpose | World mutation |
| --- | --- | --- |
| `IDLE` | scan cooldown / not running | none |
| `SELECT` | bind settlement, facts, recipient, food choice, route | none |
| `PATHING` | navigate toward recipient (**PlayerMob→recipient path A**) | movement only |
| `HANDOFF_PREPARE` | final preflight (tick-aligned with harvest WINDUP pattern) | none |
| `COMMIT` | irreversible transfer — **one** bounded delivery | backpack debit + item entity |
| `ACK_WAIT` | bounded post-commit observation only (**G0-B**) | none |
| `DONE` | `stop()` clears bindings; outcome-specific cooldown | none |

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
1. survival nutrition reserve (MIN_SURVIVAL_NUTRITION_RESERVE — see below)
2. progression / mandatory material reserve
3. crop replant reserve (managed harvest episode bank)
4. active mandatory-owner requirements
5. other explicitly protected inventory (equipment, held-item veto, never-food tag, etc.)
        ↓
only remainder → village-disposable breeding food
```

#### `MIN_SURVIVAL_NUTRITION_RESERVE` (**v1.2 LOCKED**)

```text
MIN_SURVIVAL_NUTRITION_RESERVE = 12   // PlayerMob / player food nutrition points
```

**Units are deliberately not villager food points.** Player survival authority and villager transfer
sizing use different vanilla scales:

| Authority | Unit | Gen-1 constants |
| --- | --- | --- |
| **Player survival** | PlayerMob **nutrition** (edible item `FoodProperties`) | remaining backpack nutrition ≥ **12** after any proposed debit |
| **Villager transfer sizing** | `Villager.FOOD_POINTS` | bread=4, carrot/potato/beetroot=1 |

**Policy shape (locked):** consider the **entire edible backpack pool** globally. A breeding-food unit
may leave only when the **remaining** backpack still satisfies the 12-nutrition reserve. Do **not**
pin gen-1 reserves as “keep N carrots” or “keep N bread” — food types differ in PlayerMob nutrition.

**Forbidden fallback:** `reserve == 0` when calculation is unknown or unimplemented.

**AV-1 labels for task-57 report:**

| Claim | Label |
| --- | --- |
| Reserve shape (nutrition pool, global edible backpack) | **DESIGN_LOCKED** |
| `12` nutrition source (SPM `ForagePolicy.HEAL_BUFFER_NUTRITION` oracle) | **INFERRED** |
| Static implementation + tests | **CONFIRMED** (after ship) |
| Runtime adequacy | **UNVERIFIED** until batched V3 campaign |

**Research mandate (Gate 0 + implementation):** reuse existing expendability/reserve authorities where
orthogonal; survival nutrition reserve is **new** Scavenger authority (Gate 0: no existing pin).

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

### Recipient food-need invariant (**v1.2 LOCKED — PD-57-7**)

Gate 0 established read-only APIs (`CONFIRMED` — `task-57-gate0-report.md` G0-8):

- `wantsMoreFood()` → inventory food points **< 12**
- `canBreed()` → `foodLevel + inventory food points >= 12` && adult && not sleeping

**Baseline recipient food need (authority, not ranking hint):**

```text
wantsMoreFood() == true
AND canBreed() == false
```

| Signal | Meaning for population food |
| --- | --- |
| `!wantsMoreFood()` | inventory already ≥ 12 food points → **extra food unnecessary** |
| `canBreed()` | total breeding food authority already sufficient → **extra food unnecessary** |

**Re-check at `HANDOFF_PREPARE`.** Failure → **ABORT**, **0 transfer**.

`hasExcessFood()` may remain **diagnostic / ranking** only; `!wantsMoreFood()` already excludes
saturated inventory. No separate authority rule required.

### Exact-villager occupancy interlocks (**v1.2 LOCKED**)

Do **not** globally suppress population work when any greet or trade exists. Suppress handoff to
**the same villager** when that mob already occupies them socially or in trade:

```text
population candidate villager V

SocialExecutionBindingRegistry.binding(mobId)
    subjectId == V.uuid
    AND phase in {ADMITTED, RUNNING}
→ V unavailable for population handoff (Alice remains eligible)

TradeSessionClaimWindow.claims(mob, V, tick)
→ population cannot handoff to V
```

Registry already carries `subjectId` and `Phase` (`SocialExecutionBindingRegistry.Binding` —
`CONFIRMED` `src`). No new social authority type.

### Gen-1 deterministic ranking (among food-need-eligible adults)

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

### Reachability — two independent proofs (**G0-A LOCKED**)

Task-57 has **two path proofs that must not be collapsed**:

| ID | Question | Pathfinder owner | When evaluated |
| --- | --- | --- | --- |
| **B** | Can this villager reach a breeder-local vacant HOME? | **`chosen Villager.getNavigation()`** — `VillagerMakeLove.canReach` semantics | SELECT + HANDOFF_PREPARE |
| **A** | Can PlayerMob physically deliver food? | **PlayerMob navigation** → recipient | SELECT + PATHING + HANDOFF_PREPARE |

```text
recipient candidate
    ↓
villager-local HOME proof (B)
    HOME + HAS_SPACE within 48 of recipient
    recipient→HOME: villager nav path.canReach()
    ↓
PlayerMob path (A)
    PlayerMob→recipient: mob nav path / interaction distance
    ↓
handoff
```

**Architecture defect:** proving B because PlayerMob can path to the bed block, or substituting A for B.

Gate 0 pins B to:

```text
villager.getNavigation().createPath(bedPos, poiType.validRange()) != null
    && path.canReach()
```

(same as `VillagerMakeLove.java` — see `task-57-gate0-report.md` G0-A).

### Read-only HOME enumeration (Gate 0 **PASS**)

---

## 5. Commit ownership

Dropping / transferring food is the **irreversible commit**. Immediately before commit, revalidate
**all** of:

| Check | Abort if fail |
| --- | --- |
| `RULE_MOBGRIEFING` true (`v1.2` — villager pickup requires `canEntityGrief`) | yes |
| `VillageWorkAdmission` permits | yes |
| no combat target / shelter takeover / mandatory claim | yes |
| recipient still valid (alive, adult, loaded, in bounds) | yes |
| recipient food-need: `wantsMoreFood() && !canBreed()` | yes |
| recipient not blocked by SOCIAL binding or trade claim (exact villager) | yes |
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

**After irreversible handoff:** do not attempt rollback. Episode enters **ACK_WAIT**, then `DONE` with
a terminal outcome (see §G0-B).

### G0-B — Commit vs delivery acknowledgment (**LOCKED**)

| Phase | Meaning |
| --- | --- |
| **COMMIT** | Backpack debit + spawn/throw item — **irreversible** |
| **ACK_WAIT** | Observe only (inventory food-point delta vs pre-commit snapshot; item entity absorbed). **Never** transfer again. **Never** rollback. |
| **DELIVERED_ACK** | ACK observed within bounded window — safe to treat as villager received food |
| **COMMITTED_UNCONFIRMED** | Commit happened but receipt unproved — label **OFFERED** / **HANDOFF_COMMITTED**, not "received" |

**Anti-loop:** `COMMITTED_UNCONFIRMED` uses **same or longer** cooldown as `DELIVERED_ACK` — must not
immediately re-offer (VR-T3e).

Provisional: `ACK_WAIT_TICKS` ≥ `tossPickUpDelay + margin` (Gate 0: pickUpDelay **10**; suggest **40**
ticks total — `PROVISIONAL`).

If ACK cannot be implemented cheaply (implementation regression only), fall back to immediate `DONE`
with **`COMMITTED_UNCONFIRMED`** semantics only — never claim delivery without observation.

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

**Trade interlock (`LOCKED` — Gate 0 + v1.2):** when `TradeSessionClaimWindow.claims(mobId, V, tick)`,
population handoff to **V** is forbidden. Other villagers remain eligible.

**Greet / SOCIAL interlock (`LOCKED` — PD-57-6):** when
`SocialExecutionBindingRegistry.binding(mobId)` is live with `subjectId == V`, population handoff to
**V** is forbidden. Do not suppress population work globally.

---

## 7. Pickup semantics and `mobGriefing` authority (**v1.2 LOCKED**)

Vanilla villager item pickup is gated by `canEntityGrief` (Gate 0 G0-2 — `CONFIRMED`). Committing food
with `mobGriefing == false` is **predictably useless** — this is a **hard authority gate**, not an
optimization.

**Required at all four checkpoints:**

| Checkpoint | `mobGriefing == false` |
| --- | --- |
| `canUse` | episode does not start |
| `canContinueToUse` | episode stops (abort path) |
| `HANDOFF_PREPARE` | **ABORT** — 0 backpack debit, 0 `ItemEntity` |
| `COMMIT` | **ABORT** — 0 backpack debit, 0 `ItemEntity` |

Mirror `VillageHarvestEpisodeGoal` griefing checks in admission (`VillageHarvestEpisodeGoal.java`).

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
| **`mobGriefing == false`** | **abort** (0 transfer) — re-check every tick + HANDOFF_PREPARE |
| combat target acquired | abort |
| shelter / mandatory safety takeover | abort |
| mandatory pending or running claim appears | abort |
| profile no longer `VILLAGE_ALLY` | abort |
| facts become `STALE` or `INCOMPLETE` | abort |
| recipient invalid | abort |
| recipient food-sufficient (`!wantsMoreFood()` or `canBreed()`) | abort |
| recipient SOCIAL/trade occupied (exact villager) | abort |
| inventory reserve change removes disposable surplus | abort |
| breeder-local vacancy disappears | abort |
| path timeout / unreachable | abort |

### After `COMMIT`

Episode enters **ACK_WAIT** then `DONE`. No rollback. Terminal outcome drives cooldown (§G0-B).

### Admission continuation

`canUse` and `canContinueToUse` must enforce **`RULE_MOBGRIEFING`** and re-check
`PopulationFoodSupportAdmission` (profile + mandatory + combat) each tick while pathing — mirror
`VillageHarvestEpisodeGoal.canContinueToUse`.

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

### `PlaceTorchGoal` P4 contention — **KNOWN RUNTIME QUESTION** (v1.2)

`PlaceTorchGoal` also shares priority **4** with `MOVE|LOOK` (`SpmScavenger.java`) — **pre-existing**
policy contention, **not** a task-57 correctness defect.

```text
KNOWN RUNTIME QUESTION:
  persistent PlaceTorch demand may delay village work
```

**Do not repair** PlaceTorch arbitration in task-57. Only change P4 ordering or add a `VillageWorkSelector`
if the **batched VR-T3 campaign** demonstrates village-work starvation. Avoid inventing a V3 director for
a theoretical scheduler problem.

---

## 10. Anti-loop contract

**Locked:**

```text
one terminal handoff per episode → DONE
→ cooldown keyed to outcome (DELIVERED_ACK vs COMMITTED_UNCONFIRMED)
→ no immediate gift loop
```

**G0-B:** unconfirmed offer (`COMMITTED_UNCONFIRMED`) **must not** immediately trigger another drop.

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
| **T57-13** | `mobGriefing=false` | population episode does not start; zero transfer | commit with griefing off | VR-T3e |
| **T57-14** | recipient `!wantsMoreFood()` or `canBreed()` at handoff | zero transfer | food to food-sufficient villager | VR-T3e |

### Negative controls (implementation phase)

At least one mutation-confirmed probe per row where a single missing check would let the test pass
incorrectly (task-55 / task-56 discipline).

---

## Gate 0 proposal — **COMPLETE (`GATE_0_PASS`)**

See **`task-57-gate0-report.md`** for full evidence. Summary:

| Gate | Result |
| --- | --- |
| G0-1 Food points | bread=4, carrot/potato/beetroot=1; breeding ≥12 total |
| G0-2 Pickup | mobGriefing + pickUpDelay + `wantsToPickUp` |
| G0-3 Read-only HOME | `getInRange(HAS_SPACE)` — no `take()` |
| **G0-A** Dual reachability | Villager nav → HOME; PlayerMob nav → recipient |
| G0-5 SPM toss | Reuse physics; dedicated handoff primitive |
| G0-6 Expendability | New food policy; food unmodelled in `SellReserveModel` |
| G0-7 P4 exclusion | Shared MOVE\|LOOK flags |
| G0-8 Willingness | `wantsMoreFood()` / `canBreed()` public |
| **G0-B** ACK phase | **ACK_WAIT** recommended |

Implementation remains **HOLD** until **authorize task-57**.

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

## Resolved product decisions (v1.2)

| ID | Decision | Status |
| --- | --- | --- |
| **PD-57-1** | Handoff success = ACK_WAIT + inventory delta vs spawn-only | **LOCKED** (G0-B) |
| **PD-57-2** | Food-value meaningful-progress budget | **PROVISIONAL** tuning |
| **PD-57-3** | Post-delivery cooldown scope | **PROVISIONAL** — settlement + recipient |
| **PD-57-4** | Trade session interlock — exact villager via `TradeSessionClaimWindow` | **LOCKED** |
| **PD-57-5** | Read-only willingness APIs | **LOCKED** — used for PD-57-7 authority, not sole ranking |
| **PD-57-6** | Greet interlock — exact villager via `SocialExecutionBindingRegistry` | **LOCKED** |
| **PD-57-7** | Recipient food need: `wantsMoreFood() && !canBreed()` | **LOCKED** — authority at SELECT + HANDOFF_PREPARE |

---

## Self-review vs user authorization

| Requirement | Brief status |
| --- | --- |
| Brief design only — no production Java | **DONE** (this document) |
| Gate 0 audit | **DONE** — `GATE_0_PASS` (`task-57-gate0-report.md`) |
| No Minecraft launch | **DONE** |
| No task-58 | **DONE** |
| Inherit D-VR-083-A1 / 078 / 082-A1 / 084 without reopening | **DONE** |
| Episode architecture as specified | **DONE** |
| Food semantics + expendability policy | **DONE** |
| `MIN_SURVIVAL_NUTRITION_RESERVE = 12` (v1.2) | **DONE** |
| `mobGriefing` hard authority gate (v1.2) | **DONE** |
| Recipient food-need invariant (v1.2) | **DONE** |
| Exact-villager SOCIAL/trade interlocks (v1.2) | **DONE** |
| Transfer quantity — no magic 3/6 lock | **DONE** |
| Recipient selection rules | **DONE** |
| Vacant HOME — no `take()` probe | **DONE** |
| Commit preflight list | **DONE** |
| Transfer mechanism comparison | **DONE** |
| Pickup semantics | **DONE** |
| Interruption rules | **DONE** |
| Minimum V3 arbitration | **DONE** |
| PlaceTorch runtime question documented (v1.2) | **DONE** |
| Anti-loop contract | **DONE** |
| T57-1…T57-14 scenarios | **DONE** |
| Gate 0 evidence incorporated | **DONE** |

**Status:** `BRIEF v1.2` — Gate 0 **PASS** (`task-57-gate0-report.md`)  
**Implementation:** `HOLD` — awaits **authorize task-57**
