# RFC: Humanized action presentation / transition layer

## RFC Identity

| Field | Value |
| --- | --- |
| **Project root** | `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric` |
| **Host platform** | Social Player Mobs (`playermob`) v0.86.0 |
| **Target system** | **Cross-cutting PlayerMob presentation** — post-completion and pre-work micro-behaviors |
| **Mode** | `PLANNING` — User architecture contribution; not implementation-authorized |
| **Status** | `DESIGNING` — invariant locked; architecture `PROPOSED` |
| **Nearest frontier** | Peer review **D-ATP-001…008**; pick **Phase 0** vertical slice after review |
| **Last update** | 2026-08-18 (User contribution + `Agent_Cursor` RFC bootstrap) |
| **Related** | `RFC-VANILLA-AUTONOMOUS-PROGRESSION.md`, `RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md`, `docs/wiki/Opinion-System.md`, `RFC-FURNACE-SMELTING.md` |
| **Gate** | MRFC-1, MAIBS-1 |

### Critical terminology

| Term | Meaning | Not this |
| --- | --- | --- |
| **Authority / permission** | Policy, Goal admission, `TaskLifecycle`, commitment, inventory mutation | Presentation timing |
| **Presentation** | Brief LOOK / pause / cosmetic motion after authority already succeeded, or before work begins | A second chance to admit work |
| **`MiningTransition`** | MI-14 workflow handoff record (`CAVE_FOUND`, etc.) | Cosmetic pause |
| **`TradeSessionClaimWindow`** | Taxonomy guard — blocks greet during trade FACE/EXECUTE | Optional post-trade villager gaze |
| **`PassiveExpressionGoal`** | Idle discretionary gaze at priority 8 | Episode-bound completion decoration |

---

## Executive Summary

PlayerMobs today tend to **snap** between economically correct states: break → 180° sprint; craft → run;
eat → mine; loot → leave; trade → leave; arrive → instant workstation use. The underlying **policy
and executors are often right**; the **observable motion** reads mechanical.

This RFC defines a **Humanized Action Presentation / Transition Layer** — a cross-cutting subsystem
that may **delay or decorate** already-authorized outcomes for roughly **100–1000 ms** (2–20 ticks
at 20 TPS), **not every time**, without changing what the mob is allowed to do.

**Core invariant (`LOCK RECOMMENDED` — User, 2026-08-18):**

> Presentation may delay or decorate an already completed/authorized action briefly. It may never
> manufacture permission, change policy outcomes, or prevent urgent behavior from preempting it.

Combat, environmental escape, player orders, shelter emergencies, and similar **mandatory**
authorities **immediately cancel** presentation. Opinion/personality may later modulate *how* the mob
reacts (gaze length, swing speed, pause duration) — **not** what it may do. This aligns with the
existing Opinion rule: *preference affects choice; preference does not create permission*
(`docs/wiki/Opinion-System.md`; reinforced in `RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md`).

**Evidence baseline (`CODE_CONFIRMED`, three probes):**

1. **NOT FOUND:** `ActionPresentation`, `PresentationEpisode`, `TransitionLayer` in Scavenger `src`.
2. **FOUND (adjacent, not substitute):** `PassiveExpressionGoal` — idle LOOK-only cosmetic gaze;
   `TradeSessionClaimWindow` — trade/greet permission guard; `MiningTransition` — mining workflow
   handoff state.
3. **FOUND (pattern):** `PassiveExpressionProfile` + Opinion experience registry — personality-shaped
   timing without changing goal admission.

**Recommendation:** central **presentation envelope** fed by executor completion hooks; bounded
ephemeral tickets (RET-1); LOOK-first micro-phases; optional probabilistic skip; Phase 0 proves one
vertical before scaling to mine/craft/chest/arrival.

---

## Brainstorming

Early candidates from the User contribution (2026-08-18). Serious designs moved to stable topics below.

| ID | Idea | Class | Disposition |
| --- | --- | --- | --- |
| B-ATP-01 | **Mine:** break → glance drop → collect → orientation pause → depart | `NEW` | → Topic: Episode catalogue |
| B-ATP-02 | **Craft:** finish → hand motion → look around → depart | `NEW` | → Topic: Episode catalogue |
| B-ATP-03 | **Chest:** finish loot → face chest briefly → turn to next dest → depart | `NEW` | → Topic: Episode catalogue |
| B-ATP-04 | **Trade:** transaction → face villager → short reaction → depart | `NEW` | → Topic: Episode catalogue; distinct from `TradeSessionClaimWindow` |
| B-ATP-05 | **Arrival:** reach workstation → orient → tiny pause → begin work | `NEW` | → Topic: Episode catalogue |
| B-ATP-06 | **Probabilistic application** — not every completion | `PRODUCT` | → D-ATP-004 |
| B-ATP-07 | **Personality presets** — reserved / social / energetic / relaxed | `OPINION` | → D-ATP-005; deferred Phase 2 |
| B-ATP-08 | **Central envelope vs per-goal tails** | `ARCHITECTURE` | → D-ATP-006 |
| B-ATP-09 | **Instant preemption matrix** | `SAFETY` | → D-ATP-003 |
| B-ATP-10 | **Client-only animation** | `ALTERNATIVE` | **REJECTED** gen-1 — server authority + log/readout need server-side episode |

**Rejected (dedup):** embedding long pauses inside every Goal's `stop()` (duplication, inconsistent
preemption); using presentation to implement `SocialGreetClaimWindow` / trade face guards (permission
layer); stretching `MiningTransition` to cover cosmetics (workflow semantics differ).

---

## Topic: Core invariant and authority boundary (`User` + `Agent_Cursor`)

**Author:** `User` (architecture, 2026-08-18); `Agent_Cursor` (RFC integration)
**Status:** `LOCK RECOMMENDED`

### Problem (observable)

When authority transitions are instantaneous, observers infer **policy churn** or **scheduler bugs**
even when inventory, demands, and commitments are correct. Trading is the most visible case today,
but the defect class is **general**.

### Authority vs presentation split

```text
Policy / executor                Presentation layer
─────────────────────           ─────────────────────────────
WorkDemandPolicy.select()       (no role)
Goal.canUse / TaskLifecycle     (no role)
Inventory / block mutation      completes FIRST — authoritative
Commitment SETTLED / ARRIVED      fact recorded BEFORE decoration
        │                              │
        └──── completion signal ─────┘
                    │
            PresentationEpisode (ephemeral)
                    │
        LOOK / brief pause / cosmetic swing
                    │
        yield on preemption OR timer expiry
                    │
        next authorized goal may start MOVE
```

**Must happen:** inventory and world state reflect the completed action **before** presentation
starts; cancelling presentation never rolls back authority outcomes.
**Must not happen:** presentation blocks combat/command/shelter/escape; presentation re-opens a
finished trade or re-triggers craft; presentation invents a new `canUse()` path.

**Decision:** **D-ATP-001** — presentation is **strictly downstream** of authority (`LOCK RECOMMENDED`).

---

## Topic: Preemption and cancellation (`Agent_Cursor`)

**Status:** `PROPOSED`

Presentation is **always subordinate**. Any of the following **clears** the active episode immediately
(no rollback of completed work):

| Preemptor | Examples | Notes |
| --- | --- | --- |
| **Combat / threat** | `MANDATORY_COMBAT`, hostile target, `EnvironmentalEscapeGoal`, coward EVACUATE | Includes mid-pause damage |
| **Player command** | stay, follow, commanded use | SPM command band |
| **Mandatory safety / shelter** | `SeekShelterGoal`, `ShelterNightAuthority`, raid-adjacent flee | Do not delay dusk entry for a chest gaze |
| **Death / unload / dimension change** | entity lifecycle | RET-1 cleanup |
| **Higher-priority MOVE owner** | only when that owner is **mandatory**, not discretionary wander | Avoid presentation fighting P1 helpers |

**Must not happen:** mob stands in fire during "relaxed pause"; player order ignored for 500 ms.

**Decision:** **D-ATP-003** — explicit preempt enum + production cancel call sites (`PROPOSED`).

---

## Topic: Episode catalogue — gen-1 profiles (`User`)

**Status:** `PROPOSED` — behavioral sketches; timings are tuning, not locked numbers.

### MINE (`B-ATP-01`)

```text
final block break (authority complete)
  → optional glance at drop position (LOOK, 2–6t)
  → collect path may overlap glance if pickup goal already authorized
  → short orientation pause (LOOK, 2–8t)
  → depart (release MOVE to next goal)
```

**Caveat:** if drop despawn / hostile pressure is live, **skip or truncate** glance (`INFERRED` —
presentation must not cost items).

### CRAFT (`B-ATP-02`)

```text
recipe commit success
  → brief hand/swing cosmetic (optional, 1–3t)
  → look around (LOOK, 4–12t)
  → depart
```

### CHEST (`B-ATP-03`)

```text
container session end (RaidContainers / loot executor)
  → remain facing chest (LOOK, 4–10t)
  → turn toward next destination heading (LOOK, 2–6t)
  → depart
```

### TRADE (`B-ATP-04`)

```text
staged transaction commit + notifyTrade (authority complete)
  → remain facing villager (LOOK, 4–12t)   // presentation
  → short reaction (optional swing, 1–4t)
  → depart
```

**Distinction (`CONFIRMED` code):** `TradeSessionClaimWindow` (`village/trade/`) blocks **greet
admission** during trade FACE/EXECUTE for taxonomy — it is **permission**, not optional linger.
Presentation may **extend** villager-facing LOOK **after** commit if claim window already released,
or **subsume** redundant post-commit LOOK if durations are merged carefully in implementation.

### ARRIVAL (`B-ATP-05`)

```text
path arrival at workstation / furnace / villager
  → orient toward target block/entity (LOOK, 2–6t)
  → tiny pause (2–4t)
  → begin authorized work phase (smelt insert, trade FACE, etc.)
```

**Relation:** `ShelterCommitment` ARRIVED/SETTLED is **authority**; arrival presentation is **pre-work**
decoration only and must not delay mandatory shelter occupancy.

**Decision:** **D-ATP-008** — catalogue entries are **data-driven episode kinds**, not hardcoded
per-goal tail code (`PROPOSED`).

---

## Topic: Architecture — presentation envelope (`Agent_Cursor`)

**Status:** `PROPOSED`

### Options reviewed

| Option | Description | Verdict |
| --- | --- | --- |
| **A** | Copy-paste pause blocks in each Goal `stop()` | **Rejected** — drift, inconsistent preempt, untestable |
| **B** | Client-only animation packets | **Rejected** gen-1 — no server readout; desync risk |
| **C** | Central `ActionPresentationService` + ephemeral `PresentationEpisode` ticket | **Recommended** |
| **D** | Dedicated low-priority `PresentationGoal` reading pending ticket | **Recommended** as executor — LOOK-only, priority **7–8** band |

### Recommended shape (gen-1)

```text
Executor completes authoritative work
        ↓
ActionPresentationService.tryBegin(mob, kind, context, rng)
        ↓
PresentationEpisode (UUID mob key, kind, target look pos/entity,
                     startedTick, deadlineTick, profile modifiers)
        ↓
PresentationGoal.canUse() when episode active && !preempted
        ↓
tick: setLookAt / optional swing; no navigation, no inventory
        ↓
stop: clear episode; executors unchanged
```

**RET-1:** one episode per mob; keyed by mob UUID; cleared on preempt, expiry, unload, death, server
stop; **no** entity references in saved data (ephemeral only).

**Flags:** `LOOK` only by default; **no** `MOVE` during presentation unless a future product decision
explicitly adds a one-tick stance reset (default **off**).

**Decision:** **D-ATP-006** — central service + shared goal (`PROPOSED`).

---

## Topic: Probability and personality (`User` + `Agent_Cursor`)

**Status:** `PROPOSED`

| Knob | Gen-1 default | Personality (Phase 2) |
| --- | --- | --- |
| Apply episode? | ~40–70% per eligible completion (`B-ATP-06`) | trait shifts probability, not eligibility |
| Pause duration | 2–20 ticks base | reserved ↓, relaxed ↑ |
| Social gaze length | trade / greet targets | social mob ↑ at same villager |
| Departure speed | neutral | energetic ↓ pause, faster swing |
| Hand/swing cosmetic | low chance on craft/trade | energetic ↑ |

Reuse **`PassiveExpressionProfile`** patterns (`PassiveExpressionGoal.java`) — sample min/max hold
from Opinion experience context. **Do not** route presentation through discretionary director admission;
it is not a new GAO intent.

**Decision:** **D-ATP-004** — probabilistic skip (`PROPOSED`); **D-ATP-005** — Opinion modulates
presentation parameters only (`PROPOSED`, Phase 2).

---

## Topic: Integration map (`Agent_Cursor`)

**Status:** `RESEARCHING` — hook points to verify at implementation time.

| Producer (authority) | Episode kind | Notes |
| --- | --- | --- |
| `GatherResourcesGoal` / mining break completion | `MINE_COMPLETE` | after break event; before/parallel collect |
| `ScavengerCrafting` / craft goals (`CraftTorchesGoal`, etc.) | `CRAFT_COMPLETE` | after inventory commit |
| SPM `RaidContainersGoal` / container loot session | `CHEST_COMPLETE` | after container close or loot budget |
| `TradeWithVillagerGoal` post-commit | `TRADE_COMPLETE` | after inventory delta + `notifyTrade`; see D-ATP-007 |
| `SmeltAtFurnaceGoal` / furnace arrival | `ARRIVAL_WORKSTATION` | pre-insert orientation |
| `TradeWithVillagerGoal` pre-FACE | `ARRIVAL_MERCHANT` | orient before trade session |

**Cross-RFC:**

- `RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md` — trade presentation complements V2; does not replace
  `TradeSessionClaimWindow` or `onTradeEpisode` learning.
- `RFC-VANILLA-AUTONOMOUS-PROGRESSION.md` — arrival presentation must respect shelter commitment
  authority (`ShelterNightAuthority`, SCR-2R5); presentation never opens shelter hold.
- `RFC-FURNACE-SMELTING.md` — smelt arrival pause before insert.

**Decision:** **D-ATP-007** — trade permission windows and trade presentation are separate layers
(`PROPOSED`).

---

## Phased delivery

| Phase | Scope | Release gate |
| --- | --- | --- |
| **ATP-0** | `ActionPresentationService`, `PresentationEpisode`, `PresentationGoal`, preempt matrix, one vertical (**trade post-commit** or **craft complete**) | Static tests + one runtime visual scenario |
| **ATP-1** | Mine, chest, arrival profiles; probabilistic skip | VR-ATP-1…3 |
| **ATP-2** | Opinion personality modulation (`PassiveExpressionProfile` extension) | VR-ATP-4 |
| **ATP-3** | Inspector readout line (`presentation: TRADE_COMPLETE 6t`) | Optional |

**Not gen-1:** persistent presentation memory; cross-mob synchronized choreography; blocking MOVE
strafe animations.

---

## Validation — runtime matrix (`PROPOSED`)

| ID | Must happen | Must not happen |
| --- | --- | --- |
| **VR-ATP-1** | After successful trade, mob faces villager ~0.5s then departs | Instant 180° sprint; greet credited as trade |
| **VR-ATP-2** | After craft, occasional look-around before next gather | Craft rollback on presentation cancel |
| **VR-ATP-3** | Player command during pause → instant obedience | 500 ms command delay |
| **VR-ATP-4** | Reserved vs relaxed profiles differ in visible pause length | Personality changes trade admission |

Static tests (`PROPOSED`): preempt clears episode; authority mutation precedes `tryBegin`; no episode
survives unload; probabilistic skip does not block `TaskLifecycle.SUCCESS`.

---

## MAIBS — behavioral prediction (`Agent_Cursor`, static)

| Weird behavior | Classification | Response |
| --- | --- | --- |
| Mob glances at drop then spawns sprint away | **Intended** gen-1 | Readable transition |
| Mob pauses in cave while zombie approaches | **Defect** if pause not cleared | Preempt matrix |
| Two pauses stack (trade + chest) | **Defect** | One episode per mob |
| Presentation every single break | **Tuning** — too robotic opposite | Raise skip probability |
| Trade linger blocks next demand step | **Defect** | Episode ends before chain advance |

**Pre-implementation verdict:** `BEHAVIORALLY_PLAUSIBLE` **conditional** on D-ATP-001/003 — runtime
`UNVERIFIED`.

---

## Decision registry

| ID | Decision | Status |
| --- | --- | --- |
| **D-ATP-001** | Presentation never grants permission; authority completes first | **`LOCK RECOMMENDED`** (User) |
| **D-ATP-002** | Ephemeral server-side episodes only; no saved presentation state | **`LOCK RECOMMENDED`** |
| **D-ATP-003** | Mandatory preemptors cancel immediately | `PROPOSED` |
| **D-ATP-004** | Probabilistic application — not every completion | `PROPOSED` |
| **D-ATP-005** | Opinion modulates duration/style, not eligibility | `PROPOSED` (Phase 2) |
| **D-ATP-006** | Central `ActionPresentationService` + shared LOOK goal | `PROPOSED` |
| **D-ATP-007** | Trade presentation ≠ `TradeSessionClaimWindow` | `PROPOSED` |
| **D-ATP-008** | Data-driven episode kinds for mine/craft/chest/trade/arrival | `PROPOSED` |

---

## Contribution

| Agent | Date | Change |
| --- | --- | --- |
| User | 2026-08-18 | **New RFC idea — Action Transitions / Humanized Action Presentation.** Cross-cutting micro-behaviors after mine/craft/chest/trade/arrival; core invariant (presentation ≠ permission); personality modulates reaction not authority. |
| Agent_Cursor | 2026-08-18 | **RFC bootstrap.** Created `RFC-ACTION-TRANSITIONS.md`; deduped against `MiningTransition`, `PassiveExpressionGoal`, `TradeSessionClaimWindow`; B-ATP-01…10; D-ATP-001…008; phased ATP-0…3; VR-ATP-1…4; MAIBS static review. **No implementation/build/runtime/commit/push.** |

---

## Appendix A — Strongest objections (peer review targets)

1. **Progression cost:** even 5 ticks × many mobs may add visible delay in dense automation — cap
   global presentation duty cycle or skip under backlog pressure (`PRODUCT DECISION`).
2. **Pickup race:** mine glance before collect may lose drops if mistuned — prefer glance **toward**
   drop while collect already authorized, or skip under despawn pressure.
3. **GoalSelector churn:** a new P7–8 goal must not fight `PassiveExpressionGoal` — unify LOOK
   cosmetic goals or arbitrate eligibility.
4. **Trade double-pause:** post-commit presentation + existing claim-window timing must be composed
   explicitly (D-ATP-007).

**Viable alternative:** extend `PassiveExpressionGoal` to accept episode-triggered holds instead of
a new goal — reduces selector entries; switch if P8 contention appears in profiling.
