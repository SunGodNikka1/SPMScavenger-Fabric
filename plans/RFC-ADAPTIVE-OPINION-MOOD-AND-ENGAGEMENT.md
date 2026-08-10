# RFC: Adaptive Opinion, Mood & Engagement (GA-OPINION)

## RFC Identity

| Field | Value |
| --- | --- |
| **Project root** | `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric` |
| **Host platform** | Social Player Mobs (`playermob`) v0.86.0 — reference `Projects/references/SocialPlayerMobs-v0.86.0/` |
| **Codename** | **GA-OPINION** (General Autonomy — Adaptive Opinion) |
| **Scope** | Cross-cutting discretionary intelligence layer: personality, learned opinions, short-term affect, and idle-time activity choice — **design for later**; not mining-specific |
| **Mode** | `PLANNING` |
| **Status** | GAO-0 through GAO-4 `IMPLEMENTED / STATIC VERIFIED`; runtime `UNVERIFIED` |
| **User constraint** | Addon architecture only; **must not** fork or replace SPM; Opinion disabled ⇒ SPM parity unchanged |
| **Related** | `RFC-VANILLA-AUTONOMOUS-PROGRESSION.md`; `RFC-MINING-INTELLIGENCE-AND-WEALTH-SYSTEM.md` (MI-14 execution control); `MoveHolderClassifier` (MI-14C2-R1 activity taxonomy seed); SPM `DispositionResolver`, `FollowLovedOneGoal` |
| **Owners** | User (product) |
| **Primary author** | **Agent_ChatGPT** (user-provided design, 2026-08-09) |
| **Peer review** | Agent_Cursor; Agent_Claude; Agent_Codex; user-provided contract review (2026-08-09) |
| **Last update** | 2026-08-09 (PD-GAO-03 death semantics locked; GAO-2 OpinionMemory implemented) |
| **Gate** | MRFC-1 |

---

## Executive Summary

Today, when a PlayerMob has **no urgent objective**, behavior tends toward **stand → wander → stand → wander**. Human players instead ask: *“This is boring — what do I feel like doing next?”* and choose explore, socialize, build, gather, or start a project.

**GA-OPINION** adds that missing layer as **deterministic classical AI** (no LLM, no ML):

| Layer | Role | Timescale |
| --- | --- | --- |
| **PersonalityModel** | Stable traits (curiosity, sociability, risk tolerance, …) | Long-term |
| **OpinionMemory** | Learned evaluations of activities, places, entities, environments | Medium-term, experience-driven |
| **AffectiveState** | Short-term boredom, engagement, stress, satisfaction, novelty desire | Seconds–minutes |
| **DiscretionaryActivityDirector** | Chooses *what to want* when nothing more important requires action | Per decision tick |

**North star:** A mob that acts because *nothing important is happening and this activity appeals to them* — not because a script fired `IdleGoal` again.

**Hard rule (locked proposal):** Opinion influences **preference among valid solutions**; it never removes **competence** or vetoes **mandatory** survival, progression, combat, or commands.

**SPM compatibility is non-negotiable:** Opinion is an **addon intelligence layer** beside SPM — it reuses `feelingToward` / `DispositionResolver` for social authority and observes **host** GoalSelector activity (lesson from MI-14C2-R2).

**Nearest frontier:** **GAO-4** — `DiscretionaryActivityDirector` (first visible-behavior phase).
**Preflight contract is LOCKED** (abstention, intent lifecycle, voluntary yield, opinion-on consumer
gates, mandatory invalidation, commitment/hysteresis, D-GAO-025 trace). **Implementation not
authorized until user approves GAO-4 coding.** GAO-3 scoring remains inert. Runtime `UNVERIFIED`.

---

## Brainstorming

Early candidates from Agent_Cursor brainstorm (2026-08-09). Serious items promoted to stable topics below.

| ID | Idea | Verdict |
| --- | --- | --- |
| B-01 | Reuse `MoveHolderClassifier` as GAO-0 activity oracle | **PROMOTE** → GAO-0 topic |
| B-02 | Replace `ExplorationReadiness.idleWorkTicks` with mood | **REJECT** — duplicates unlock path; mood should *feed* readiness thresholds |
| B-03 | `ExperienceEvent` bus from mining/gather/explore terminals | **PROMOTE** → Experience hooks topic |
| B-04 | `DiscretionaryIntent` record consumed by existing goals | **PROMOTE** → architecture topic |
| B-05 | Campfire + shelter + night = REST not IDLE | **PROMOTE** → REST topic |
| B-06 | Personality = UUID-seeded noise on repetition decay | **DEFER** → GAO-7 |
| B-07 | New `BoredWanderGoal` at priority 4 | **REJECT** — D-GAO-005 |
| B-08 | Opinion reads `WorkDemandPolicy` directly | **REJECT** — violates D-GAO-001; only directors emit mandatory work |
| B-09 | Trait display via JADE/nameplate mood band | **DEFER** → observable expression |
| B-10 | Opinion chooses cave vs tunnel when both legitimate | **PROMOTE** — aligns with MI integration example |
| B-11 | Merge `ExplorationActivityGoal` idle scan into `ActivityObservationService` | **PROMOTE** — one observer, two predicates (idle vs work) |
| B-12 | `AnticsGoal` = gen-0 observable expression, not discretionary intent | **PROMOTE** — extends deferred UX topic |
| B-13 | Staggered 10-tick observation cadence (match `ExplorationActivityGoal`) | **PROMOTE** — perf + SPM parity |
| B-14 | `ExperienceEvent` from `ExplorationReadiness.consume` → `EXPEDITION_UNLOCKED` | **PROMOTE** — GAO-0b |
| B-15 | Boredom decays during `FollowLovedOneGoal` / greet windows | **PROMOTE** — social is not idle |
| B-16 | Separate **engagement channel** from MI-14 lease/project clocks | **PROMOTE** — avoids C3 shadowing lesson |
| B-17 | `CampfireGoal` = REST for affect, meaningful-work for expedition unlock | **SUPERSEDED IN PART by B-28** — dual consumer remains valid; raw goal liveness is not sustained REST |
| B-18 | `AnticsGoal` + flagless observers = `PASSIVE_COSMETIC` never idle | **PROMOTE** — GAO-0 row |
| B-19 | `DiscretionaryIntent` TTL + invalidate on mandatory work | **PROMOTE** — hierarchy safety |
| B-20 | Trait presets map to `PersonalityModel` noise on event deltas only | **PROMOTE** — GAO-7 seed |
| B-28 | Arrival-anchored `RestSessionClaim`; do not equate `CampfireGoal.isRunning()` with sustained REST | **PROMOTE** → REST topic; code inspection disproves B-17's original mechanism |
| B-29 | Normalize repeated low-level events inside an `ActivityEpisode` | **PROMOTE** → Experience events; prevents block-count frequency from dominating learning |
| B-30 | Separate subjective outcome from feasibility/authority failure | **PROMOTE** → Experience events; a simulation frontier or player order must not teach “I hate exploring” |
| B-31 | Carry activity/episode identity through events and interruptions | **PROMOTE** → Experience events; prevents crediting the wrong activity after combat/work preemption |
| B-32 | Prevent accidental lockstep/contention without forbidding intentional group activity | **REFINED / RESEARCH** → deterministic staggering plus reservations only for exclusive physical resources |
| B-33 | Freeze affect while unloaded/non-ticking; make long-term offline decay an explicit policy | **PROMOTE** → persistence/product decision |
| B-34 | Bounded decision trace: candidates, utilities, blocker, claim owner, outcome | **PROMOTE** → validation; required to falsify “why did it choose that?” without log spam |
| B-35 | Goal liveness proves occupancy, not engagement or progress | **PROMOTE** → observation contract; stalled goals must not award endless engagement |

---

## Problem Statement

| Today | Target |
| --- | --- |
| Idle = failure state | **REST** is intentional; **prolonged** inactivity generates discretionary intent |
| Mood = happy/sad meter | **Opinion** = subjective evaluation of current/recent situation |
| Specialized boredom goals fighting GoalSelector | **Policy → director → existing executors** |
| Fixed personality only | **Personality + learned opinions + short-term affect** |
| Addon-only activity observation | **Scheduler-wide activity observation** (host + addon goals) |

---

## Topic: Three-layer model (Personality → Opinion → Mood)

**Status:** `PROPOSED` (Agent_ChatGPT)

```text
PERSONALITY          "What kind of person am I?"
        │
        ▼
OPINIONS             "What do I think about things?"
        │
        ▼
MOOD / ENGAGEMENT    "How do I feel right now?"
        │
        ▼
DECISION UTILITY     "What do I feel like doing?"
```

### PersonalityModel (stable)

Example trait dimensions (weights 0…1, exact set TBD):

- Curiosity, Sociability, RiskTolerance, Persistence, Materialism, Adventurousness

Personality modulates **how strongly** experiences update opinions — not a direct action picker.

### OpinionMemory (learned)

Typed opinion targets (`PROPOSED` taxonomy):

| Kind | Examples |
| --- | --- |
| **ACTIVITY** | MINING, EXPLORING, BUILDING, FARMING, SOCIALIZING, REST |
| **PLACE** | Home, Village A, Cave project #12, Nether portal site |
| **ENTITY** | Player, PlayerMob, villager, creature category |
| **ENVIRONMENT** | FOREST, CAVE, DEEP_UNDERGROUND, OCEAN, SNOW, NIGHT |
| **PROJECT** | MiningProject, building project, exploration expedition |

Per-target memory fields (`PROPOSED`):

```text
ActivityOpinionMemory
├ recentDuration
├ lastPerformed
├ recentReward
├ recentFailures
├ repetition
└ preference (learned offset)
```

Experiences apply deltas (example — mining):

- Finds diamonds → +satisfaction
- Discovers cave → +novelty
- Near death → −experience / +stress
- Same tunnel 15 min → −repetition
- Returns with loot → +accomplishment

Two mobs with same starting personality can diverge through history.

### AffectiveState (short-term)

**Do not** use one magical scalar internally. Proposed components:

```text
OpinionState (internal)
├ engagement
├ boredom
├ satisfaction
├ stress
├ novelty
└ recentActivityMemory
```

**Derived summary label** (display / coarse routing only):

| Band | Label (example) |
| ---: | --- |
| +100 | VERY ENGAGED |
| +60 | ENJOYING |
| +20 | CONTENT |
| 0 | NEUTRAL |
| −20 | RESTLESS |
| −50 | BORED |
| −80 | VERY BORED |
| −100 | SEEK CHANGE |

Example — mining diamonds: engagement +25, novelty +15, satisfaction +30, stress +5, boredom −30 → **ENJOYING**.

Example — same house 3 minutes: engagement −30, novelty −40, boredom +65 → **BORED**.

---

## Topic: Priority hierarchy (mandatory)

**Status:** `PROPOSED` — **candidate for LOCK** at implementation time

```text
SURVIVAL / SAFETY
        ↓
COMMAND / CRITICAL OBJECTIVE
        ↓
BLOCKING PROGRESSION
        ↓
ACTIVE PROJECT
        ↓
USEFUL OPPORTUNITIES
        ↓
OPINION / ENGAGEMENT
        ↓
IDLE (discretionary candidate only)
```

**Must happen:** Starving mob gathers food even if `Opinion(GATHERING_FOOD) = "incredibly bored"`.

**Must not happen:** Combat interrupted because `Opinion = bored of fighting`.

Opinion matters when there is **freedom of choice**.

---

## Topic: Architecture — Opinion does not bypass the brain

**Status:** `PROPOSED` (Agent_ChatGPT)

```text
              Autonomous Intelligence Core
                         │
        ┌────────────────┼────────────────┐
        │                │                │
 ObjectiveManager    OpinionSystem    Memory
        │                │                │
        └─────────┬──────┴────────────────┘
                  ▼
             Utility / Choice
                  │
          "What should I do?"
                  │
        ┌─────────┼─────────┐
        ▼         ▼         ▼
      Mining   Explore    Social
      Build    Farm       Progress
                  │
                  ▼
         Execution Control (MI-14C)
                  │
                  ▼
              GoalSelector
```

### Separation of concerns (aligns with MI-14)

| Layer | Answers |
| --- | --- |
| **Opinion / DiscretionaryActivityDirector** | What do I *want* to do when discretionary? |
| **MiningDirector / Progression planners** | What *must* happen for progression? |
| **MI-14C execution control** | Who may run; lease; arbitration |
| **Goals** | How (physical execution) |

**Rejected:** `BoredGoal` as a competing GoalSelector entry (`CONSENSUS` proposal).

**Accepted pattern:**

```text
OpinionSystem
        ↓
IdleOpportunityPolicy
        ↓
generates discretionary intent
        ↓
normal planner/directors
        ↓
existing executors (ExploringGoal, GatherResourcesGoal, …)
```

---

## Topic: Activity utility (discretionary scoring)

**Status:** `IMPLEMENTED / STATIC VERIFIED` (GAO-3 EXPLORE + REST only)

When no mandatory objective:

```text
ActivityUtility =
    base usefulness
  + objective relevance (soft, not blocking)
  + personality preference
  + novelty
  + expected reward
  - repetition boredom
  - recent failures
  - risk
  - cost
```

**Repetition example (locked intent):**

Loves exploration (`Opinion(EXPLORATION) = +72`) but explored 31 minutes → repetition modifier −40 → *“I still like exploring, but not right now.”*

After 2 in-game days without exploring, repetition penalty decays.

**Mining example (MI integration):**

- Progression requires diamonds → `MiningDirector` says mining required.
- `Opinion(MINING_TUNNEL) = −35`, `Opinion(CAVE_EXPLORATION) = +61`.
- Both are legitimate; utility prefers natural cave route.
- If caves exhausted → controlled descent still runs; opinion does not veto.

---

## Topic: GAO-0 — Activity taxonomy & observation (`IMPLEMENTED`)

**Status:** `IMPLEMENTED / STATIC VERIFIED` — code, focused tests, full suite, clean build, and post-implementation MAIBS pass; runtime remains `UNVERIFIED`

GAO-0 must answer: *what activity is this mob doing right now, and is that IDLE, REST, MANDATORY, or DISCRETIONARY?*

### Seed: MI-14 already classifies running goals (`CONFIRMED`)

`MoveHolderClassifier` (`mining/MoveHolderClassifier.java`) classifies one supplied Goal for
lease/arbitration meaning without compiling against SPM; `SchedulerConflictPolicy` owns the current
live-selector scan. GAO-0 reuses and extends the classifier's single-goal taxonomy while the new
`ActivityObservationService` owns the activity-observation scan.

| `MoveHolderClassification` | Example goals (suffix / type) | Opinion `ActivityClass` (proposed) |
| --- | --- | --- |
| `NOT_MOVE_HOLDER` | Flagless helpers (`PlayerMobDoorGoal`, `DigThroughGoal`) | `PASSIVE_HELPER` — distinct taxonomy class, but still counts as readiness work for exact pre-GAO-0 parity |
| `PROTECTED_SAFETY_RECOVERY` | `EnvironmentalEscapeGoal`, `SeekShelterGoal`, `FireBucketGoal`, `FleeFromCategoryGoal`, `TrainRecoveryGoal` | `MANDATORY_SAFETY` |
| `PROTECTED_PLAYER_ORDER` | `CommandedActionGoal`, `StayNearGoal` | `MANDATORY_COMMAND` |
| `PROTECTED_LOW_FOOD` | `EatFoodGoal` | `MANDATORY_SURVIVAL` |
| `PROTECTED_FINITE` | `SkepticalWatchGoal`, `FriendlyGreetGoal`, `DoorOperationGoal` | `SOCIAL_REFLEX` — meaningful, not idle |
| `PROTECTED_COMBAT` | `WeaponAwareAttackGoal`, TNT/crystal combat | `MANDATORY_COMBAT` |
| `ORDINARY_HOST_WORK` | `FollowLovedOneGoal`, `SeekAmmoGoal` | `SOCIAL_TRAVEL` / `COMBAT_PREP` |
| `COOPERATIVE_PROJECT_WORK` | `GatherResourcesGoal` during tunnel handoff | `PRODUCTIVE_COOP` — not idle |
| `UNKNOWN_MOVE_HOLDER` | Unmapped addon/host goal | `UNKNOWN_ACTIVE` — fail safe, not idle |
| Designated mining consumer | `ControlledDescentGoal`, `TunnelSearchGoal`, cave handoff explore | `PROJECT_EXECUTION` |

**Implemented (D-GAO-011):** GAO-0 defines `ActivityObservationService` as a **thin wrapper** over `MoveHolderClassifier` + addon goal-kind mapping; no second activity classifier was created.

### SPM host goal map (`CONFIRMED` — `PlayerMobEntity#registerGoals`, v0.86.0)

| Pri | Goal | Flags / notes | `ActivityClass` | `MoveHolderClassification` |
| ---: | --- | --- | --- | --- |
| 0 | `FloatGoal` | JUMP | `PASSIVE_HELPER` | `NOT_MOVE_HOLDER` |
| 0 | `FireBucketGoal` | MOVE | `MANDATORY_SAFETY` | `PROTECTED_SAFETY_RECOVERY` |
| 1 | `CommandedActionGoal` | MOVE+LOOK | `MANDATORY_COMMAND` | `PROTECTED_PLAYER_ORDER` |
| 1 | `TrainRecoveryGoal` | MOVE | `MANDATORY_SAFETY` | `PROTECTED_SAFETY_RECOVERY` |
| 1 | `FleeFromCategoryGoal` | MOVE | `MANDATORY_SAFETY` | `PROTECTED_SAFETY_RECOVERY` |
| 1 | `SkepticalWatchGoal` | LOOK | `SOCIAL_REFLEX` | `PROTECTED_FINITE` |
| 1 | `FriendlyGreetGoal` | MOVE+LOOK | `SOCIAL_REFLEX` | `PROTECTED_FINITE` |
| 1 | `PlayerMobDoorGoal` | none | `PASSIVE_HELPER` | `NOT_MOVE_HOLDER` |
| 1 | `DoorOperationGoal` | MOVE+LOOK | `SOCIAL_REFLEX` | `PROTECTED_FINITE` |
| 1 | `BlockArrowsGoal` | none | `COMBAT_PREP` | `NOT_MOVE_HOLDER` |
| 1 | `DigThroughGoal` | none | `PASSIVE_HELPER` | `NOT_MOVE_HOLDER` |
| 2 | `TntCombatGoal` / `EndCrystalCombatGoal` | MOVE | `MANDATORY_COMBAT` | `PROTECTED_COMBAT` |
| 2 | `SeekAmmoGoal` | MOVE | `COMBAT_PREP` | `ORDINARY_HOST_WORK` |
| 2 | `WeaponAwareAttackGoal` | MOVE+LOOK | `MANDATORY_COMBAT` | `PROTECTED_COMBAT` (when target) |
| 2 | `FollowLovedOneGoal` | MOVE | `SOCIAL_TRAVEL` | `ORDINARY_HOST_WORK` |
| 2 | `StayNearGoal` | MOVE | `MANDATORY_COMMAND` | `PROTECTED_PLAYER_ORDER` |
| 3 | `EatFoodGoal` | LOOK only | `MANDATORY_SURVIVAL` | `PROTECTED_LOW_FOOD` |
| 3 | `RaidContainersGoal` | MOVE | `SCAVENGE_LOOT` | `UNKNOWN_MOVE_HOLDER`* |
| 3 | `RaidArmorStandsGoal` | MOVE | `SCAVENGE_LOOT` | `UNKNOWN_MOVE_HOLDER`* |
| 3 | `CollectFloorItemsGoal` | MOVE | `SCAVENGE_LOOT` | `UNKNOWN_MOVE_HOLDER`* |
| 6 | `HarvestCropsGoal` | MOVE | `FARMING` | `UNKNOWN_MOVE_HOLDER`* |
| 7 | `AdvanceCarriageGoal` / `CrossGroupGapGoal` | MOVE | `DUNGEON_TRAIN` | `UNKNOWN_MOVE_HOLDER`* |
| 8 | `WaterAvoidingRandomStrollGoal` | MOVE | `IDLE_CANDIDATE` | unmapped host stroll |
| 9 | `LookAtPlayerGoal` | LOOK | `PASSIVE_COSMETIC` | `NOT_MOVE_HOLDER` |
| 10 | `RandomLookAroundGoal` | LOOK | `PASSIVE_COSMETIC` | `NOT_MOVE_HOLDER` |

\* GAO-0 follow-up: extend `MoveHolderClassifier` suffix map for raid/harvest/train goals so they are not `UNKNOWN_MOVE_HOLDER` during mining lease scans — orthogonal to Opinion but same taxonomy file.

### Addon goal map (`CONFIRMED` — `SpmScavenger.java` ENTITY_LOAD)

| Pri | Goal | `ActivityClass` | Notes |
| ---: | --- | --- | --- |
| 0 | `EnvironmentalEscapeGoal` | `MANDATORY_SAFETY` | |
| 2 | `SeekShelterGoal` | `MANDATORY_SAFETY`; sleeping is directly observable REST, non-bed arrival needs a rest claim | goal remains active up to 400 approach ticks; do not infer successful rest from liveness alone |
| 3 | `GatherResourcesGoal` / `CraftTorchesGoal` / `SmeltAtFurnaceGoal` | `PRODUCTIVE_COOP` or `SCAVENGE_WORK` | cooperative when MI handoff |
| 3 | `ControlledDescentGoal` / `TunnelSearchGoal` | `PROJECT_EXECUTION` | MI-14 designated consumers |
| 4 | `PlaceTorchGoal` | `MAINTENANCE` | project-adjacent |
| 7 | `CampfireGoal` | `REST_APPROACH`; arrival may open a separate `RestSessionClaim` | goal is capped at 200 ticks and normally stops after arrival (B-28) |
| 8 | `ExploringGoal` | `EXPEDITION` | not idle |
| 9 | `TrackedLocalWanderGoal` | `IDLE_CANDIDATE` | feeds boredom |
| 9 | `ExplorationActivityGoal` | `PASSIVE_OBSERVER` | flagless; never idle |
| 9 | `AnticsGoal` | `PASSIVE_COSMETIC` | flagless mimicry (B-12) |

### Proto-observer already shipped (`CONFIRMED`)

`ExplorationActivityGoal` (`goal/ExplorationActivityGoal.java`) already performs scheduler-wide scans every 10 ticks:

- Unknown running goals → `recordMeaningfulWork()` (fail-safe — **does not** recreate MI-14C2-R2).
- Only `TrackedLocalWanderGoal`, host `RandomStrollGoal`, look goals, `AnticsGoal`, and self → `recordIdleTicks(10)`.
- `FollowLovedOneGoal` and raid goals → meaningful work (`CONFIRMED` by control flow).

**Implemented decision (D-GAO-015):** GAO-0 **refactors** this loop into
`ActivityObservationService` rather than adding a second scanner. `ExplorationReadiness` keeps
calling the same predicates; later Opinion stages add affect side-effects on the same cadence.

### Shared observation contract — independent consumers (`LOCKED` for GAO-0)

One selector observation plus the episode/authority state must expose independent signals. They must
not be collapsed into one “active” boolean:

| Predicate | Consumer | True when |
| --- | --- | --- |
| `meaningfulWorkForExpedition()` | `ExplorationReadiness` | Any non-cosmetic running goal except wander/look/antics (current behavior) |
| `discretionaryIdleCandidate()` | `AffectiveState` boredom rise | Only `IDLE_CANDIDATE` + cosmetic classes active |
| `resting()` | REST band / slow boredom | live `RestSessionClaim`, or `mob.isSleeping()` in the shelter path |
| `schedulerOccupied()` | compatibility/authority guard | Any authoritative running Goal owns the relevant activity lifecycle |
| `meaningfulProgressRecently()` | `AffectiveState` engagement/stall distinction | bounded episode milestone/terminal occurred inside its freshness window |
| `discretionaryAuthorityAvailable()` | intent/director admission | no mandatory, command, protected, or unknown owner forbids voluntary replacement |

**Must not happen:** Campfire classified as `discretionaryIdleCandidate` (would spike boredom at a cozy fire).

**NOT FOUND probes (Opinion-specific):**

1. `OpinionSystem` / `ActivityObservationService` — **NOT FOUND** in `src/` (`grep`).
2. `DiscretionaryIntent` — **NOT FOUND** in `src/`.
3. `ExperienceEvent` — **NOT FOUND** in `src/`.

### Idle vs REST detection (`LOCKED` — source correction resolved)

The previous B-17 mechanism treated a running `CampfireGoal` as the sustained rest session. Source
inspection falsifies that model:

- `CampfireGoal.canContinueToUse()` is capped by `approachTicks < 200`.
- `tick()` increments that counter even after arrival, while merely looking at the fire.
- `canUse()` returns false when the mob is already within `ARRIVED_SQR` of its selected idle point.
- `stop()` clears `firePos` and `idlePos`.

Therefore the visible state “standing beside the fire” normally outlives the Goal that delivered the
mob there. A selector scan alone will classify the post-arrival mob as idle. `SeekShelterGoal` has a
similar split: `mob.isSleeping()` is durable and directly observable, but non-bed shelter arrival is
only held until its 400-tick approach budget expires.

```text
scan GoalSelector (host + addon), staggered every 10 ticks

if any ActivityClass in {MANDATORY_*, PROJECT_EXECUTION, PRODUCTIVE_COOP,
                         SOCIAL_TRAVEL, SCAVENGE_*, FARMING, EXPEDITION}
    → NOT discretionary idle

if live RestSessionClaim OR mob.isSleeping()
    → REST (boredom rises slowly; engagement may tick up mildly — PD-GAO-02)

if CampfireGoal/SeekShelterGoal is only approaching a destination
    → REST_APPROACH / MANDATORY_SAFETY, not completed REST

if only {IDLE_CANDIDATE, PASSIVE_COSMETIC, PASSIVE_OBSERVER} running
    → discretionaryIdleCandidate (boredom may rise)

if FollowLovedOneGoal running
    → SOCIAL_TRAVEL; boredom flat or decays (B-15); never emit EXPLORE intent (GAO-M2)
```

**Must not happen:** `FollowLovedOneGoal` classified as idle because addon goals are inactive (GAO-OBSERVE).

**Locked repair (D-GAO-021):** open a condition-bound `RestSessionClaim` only when physical arrival
completes a legitimately adopted REST activity or shelter-recovery authority. Anchor its lifetime at
arrival, not selection (B-27). Goal activity or proximity alone cannot manufacture a claim. Clear it
when the fire/shelter becomes invalid, the mob leaves the allowed radius, mandatory
work/combat/command takes authority, or the bounded rest session expires. The claim owns no Goal
flags; the existing observer reads it.

```text
RestSessionClaim {
  UUID claimId;
  Optional<UUID> sourceIntentId; // absent for shelter authority not originating in Opinion
  UUID commitmentId;
  RestSourceKind sourceKind; // DISCRETIONARY_REST | SHELTER_RECOVERY
  BlockPos anchor;
  RestAnchorType anchorType;
  long adoptedAt;
  long arrivedAt;
  long lastValidatedAt;
  RestCloseReason closeReason;
}
```

`DISCRETIONARY_REST` requires the matching accepted REST intent/commitment. `SHELTER_RECOVERY` is a
distinct safety-owned source: it may reduce stress and boredom without falsely claiming that Opinion
chose it. `mob.isSleeping()` remains a direct observable REST state.

**Alternatives considered:**

1. Extend `CampfireGoal` to own MOVE+LOOK for the whole rest duration. Simpler, but conflates travel
   with affect state and unnecessarily blocks compatible LOOK/MOVE activity.
2. Infer REST from proximity to every lit campfire/shelter. No state, but misclassifies passers-by and
   requires recurring world probes. Switch to this only if runtime evidence shows claims become stale
   more often than the bounded validity checks can prevent.

**Must happen:** after arriving beside a valid fire, the mob remains classified REST after
`CampfireGoal` stops.

**Must not happen:** walking past a fire, losing the fire, or accepting mandatory work grants an
unbounded REST classification.

---

## Topic: Experience events — mood inputs without new goals (`LOCKED SEMANTICS / BOUNDARY PROPOSED`)

**Status:** D-GAO-012/022/023 `LOCKED`; D-GAO-026/027 `LOCKED`; GAO-0b/0c `IMPLEMENTED / STATIC VERIFIED`

Opinion should learn from **terminals and milestones** already emitted by shipped systems — not poll block state.

| Source (shipped) | Event hook (`PROPOSED`) | Affect deltas |
| --- | --- | --- |
| `MiningDirector.markExecutionProgress` | `BLOCK_BROKEN`, `STAIR_STEP`, `HANDOFF_EMITTED` | engagement +, repetition +, novelty ± |
| `MiningProjectEnd` | `CAVE_FOUND`, `HANDOFF_TUNNEL_SEARCH`, `NO_PROGRESS` | satisfaction ±, stress ± |
| `ExposureOpportunity` take / vein idle timeout | `ORE_ACQUIRED`, `VEIN_SESSION_END` | satisfaction +, boredom − |
| `ExploringGoal` stage complete | `EXPEDITION_STAGE` | novelty +, repetition + |
| `ExploringGoal` companion invite | `SOCIAL_EXPEDITION` | sociability channel + |
| `GatherResourcesGoal` harvest | `RESOURCE_HARVEST` | materialism + |
| SPM `FriendlyGreetGoal` / combat end | `SOCIAL_INTERACTION` | stress − / + via `feelingToward` |

**Locked recommendation (D-GAO-012):** existing terminals emit one immutable `ExperienceEvent` into
an `ExperiencePipeline`; no parallel scanners. Raw events do **not** call `OpinionMemory` directly.
The episode layer owns aggregation and routes bounded affect pulses separately from normalized
long-term learning (D-GAO-022).

### GAO-0b — `ExperienceEvent` schema contract (`LOCKED`)

```text
ExperienceEvent {
  ExperienceKind kind;
  long gameTime;
  UUID episodeId;                       // causal owner; stable across interruption/resume
  OutcomeClass outcome;                 // learning eligibility, not emotional sign
  ExperienceCause cause;                // exact terminal/milestone cause; preserves semantic detail
  float engagementDelta;
  float boredomDelta;
  float satisfactionDelta;
  float stressDelta;
  float noveltyDelta;
  Optional<ActivityKind> activity;   // ACTIVITY opinions only in GAO-2
  Optional<BlockPos> place;          // GAO-5
  Optional<UUID> entity;             // GAO-6 — utility only, not relationship authority
}
```

### GAO-0b dependency audit (`CODE_CONFIRMED`, 2026-08-09)

The schema is not compilable as previously phased: `ExperienceEvent` requires both `OutcomeClass`
and `ActivityKind`; neither exists in source, while the phase table assigned `OutcomeClass` to
GAO-0c and assigned no phase to `ActivityKind`. Three implementation boundaries were considered:

1. **Recommended — schema vocabulary first.** GAO-0b owns `ExperienceKind`, `ExperienceCause`,
   `OutcomeClass`, `ActivityKind`, immutable `ExperienceEvent`, and an interface-only
   `ExperiencePipeline.accept(ExperienceEvent)` ingress. It wires no production emitters and stores
   no events. GAO-0c owns `ActivityEpisode`, processing/routing, and `RestSessionClaim`; emitter
   wiring begins only when episode ownership exists. This compiles the locked schema without a
   silent queue/no-op consumer or premature behavioral side effects.
2. Implement GAO-0b and GAO-0c together. This avoids a temporarily unused API but expands the next
   task across episode normalization, unload lifecycle, REST claims, and multiple behavioral hooks.
   Select it only if the user intentionally authorizes the larger slice.
3. Remove `OutcomeClass`/`ActivityKind` from the raw event until later. Smaller initially, but it
   contradicts the reviewed attribution contract and forces an avoidable event API migration.

**D-GAO-026 (`LOCKED`):** select option 1. `OutcomeClass` is schema vocabulary owned by GAO-0b;
GAO-0c owns its episode-learning semantics, not the enum declaration. `ExperiencePipeline` is an
ingress contract in GAO-0b and gains its processing implementation only with GAO-0c. No production
emitter may publish into an unowned/no-op pipeline.

**D-GAO-027 (`LOCKED`):** `ActivityKind` is deliberately distinct from scheduler-facing
`ActivityClass`. Initial stable values are `OVERLAND_EXPLORATION`, `CAVE_EXPLORATION`,
`CONTROLLED_DESCENT`, `TUNNEL_SEARCH`, `RESOURCE_GATHERING`, `REST`, `SOCIALIZING`, and `MIMICRY`.
Campfire is a REST anchor, not a learned activity kind. Reusing `ActivityClass` was rejected because
mandatory/authority taxonomy and subjective activity identity change for different reasons; one
coarse `MINING` value was rejected because it loses the already-required tunnel-versus-cave route
preference.

**GAO-0b behavioral prediction:** because this slice defines immutable vocabulary and an unbound
ingress interface only, a PlayerMob physically does exactly what it did after GAO-0. No Goal,
selector, cadence, persistence, event emitter, affect value, or decision path changes.

**Predicted weirdness / risks:** an unused contract is temporarily dead infrastructure
(`ACCEPTABLE_STEPPING_STONE`, bounded to GAO-0c); callers could later generate one episode ID per
milestone (`ARCHITECTURE_DEFECT`, prohibited by GAO-0c tests); and an insufficient `ActivityKind`
vocabulary could require additive enum values (`RUNTIME_QUESTION` for later activity coverage, not
permission to collapse it into `ActivityClass`).

**GAO-0b acceptance tests:**

- Must happen: the record preserves exact kind/time/episode/outcome/cause/deltas/context, rejects
  null and non-finite deltas, and the functional ingress passes the identical immutable event.
- Must not happen: no terminal/executor is wired, no event is silently queued/dropped by a concrete
  pipeline, and no affect/opinion/episode/rest/Goal behavior appears.
- MAIBS pre-implementation gate: `PASS — BEHAVIORALLY_PLAUSIBLE` for the schema-only boundary;
  observable runtime remains unchanged by construction and still requires later runtime proof.

| `ExperienceKind` | Emitter (shipped) | Notes |
| --- | --- | --- |
| `BLOCK_BROKEN` | `MiningDirector.markExecutionProgress` | repetition + |
| `STAIR_STEP` | controlled descent tick | engagement + |
| `PROJECT_END` | `MiningProjectEnd` terminal | payload carries end reason |
| `CAVE_HANDOFF_ACCEPTED` | `MiningExecutionCommitment` | novelty +; separate from lease clock (B-16) |
| `ORE_ACQUIRED` | `ExposureOpportunity` take | satisfaction + |
| `VEIN_SESSION_END` | vein idle timeout | boredom − if productive |
| `EXPEDITION_UNLOCKED` | `ExplorationReadiness.consume` | B-14 |
| `EXPEDITION_STAGE` | `ExploringGoal` stage complete | novelty + |
| `SOCIAL_EXPEDITION` | companion invite (`feelingToward` gate) | sociability channel |
| `RESOURCE_HARVEST` | `GatherResourcesGoal` | materialism + |
| `REST_SESSION` | `RestSessionClaim` open/close | mild engagement + (PD-GAO-02); not raw goal start/end |
| `SOCIAL_INTERACTION` | greet/follow proximity window | stress − via SPM bridge |

### Episode attribution and normalization (`LOCKED`)

Raw hook frequency is not subjective importance. Mining can emit many block-level milestones while a
social or rest activity emits one terminal; applying every hook directly would make mining dominate
learning merely because it has a tighter loop. `ActivityEpisode` therefore aggregates bounded
milestones by `episodeId`. It may emit bounded immediate/periodic pulses to `AffectiveState`, but only
normalized episode evidence reaches `OpinionMemory` at a learning window or terminal. The same raw
event must never be applied through both paths. Interrupting combat may pause the episode but must not
silently change its causal owner.

```text
RAW ExperienceEvent
        ↓
ActivityEpisode
        ├── bounded short-term affect pulse → AffectiveState
        └── normalized terminal/window evidence → OpinionMemory
```

`OutcomeClass` separates:

| Outcome | Affect | Long-term activity preference |
| --- | --- | --- |
| `VOLUNTARY_SUCCESS` / `VOLUNTARY_ABANDON` | yes | eligible; `ExperienceCause` determines sign |
| `EXECUTION_FAILURE` | stress/confidence | only with repeated activity-owned evidence |
| `ENVIRONMENT_UNAVAILABLE` / simulation frontier | short-lived confidence/cooldown | no negative preference |
| `PROTECTED_INTERRUPT` | normally pause/neutral | none |
| `AUTHORITY_CANCEL` / player command | neutral | none |

`OutcomeClass` controls whether evidence may affect long-term learning; it does not itself choose a
positive or negative sign. In particular, `VOLUNTARY_ABANDON` may mean boredom, satisfaction (“done
for now”), a more interesting opportunity, or social reprioritization. The preserved
`ExperienceCause`/terminal reason distinguishes them.

This prevents an unloaded frontier, unreachable target, or player order from becoming the false
belief “I dislike exploration.” It also prevents a block broken during an interrupting activity from
being credited to the suspended episode. GAO-5 may later attach environment failure to a PLACE
opinion without poisoning ACTIVITY opinion.

**Unit-test vectors (pre-implementation):**

- eight `BLOCK_BROKEN` milestones in one mining episode produce one bounded repetition update, not
  eight unrestricted preference updates;
- `PROJECT_END(NO_PROGRESS, EXECUTION_FAILURE)` raises stress but changes dislike only after the
  configured repeated-evidence rule;
- `SIMULATION_FRONTIER`, `PROTECTED_INTERRUPT`, and `AUTHORITY_CANCEL` do not reduce activity opinion;
- an exploration episode interrupted by combat resumes with the same ID and does not absorb combat
  events;
- one raw event can produce a bounded affect pulse and later normalized learning, but its affect
  delta is not applied a second time at episode commit;
- two `VOLUNTARY_ABANDON` events with different causes can produce neutral/satisfied versus negative
  learning rather than sharing a hardcoded sign.

**Must happen:** productive repeated work still increases short-term repetition pressure.

**Must not happen:** the activity with the highest event emission rate automatically becomes the most
liked/disliked activity.

**Rejected:** Reading ore through stone for mood (“found diamonds nearby”) — same clairvoyance violation as D-MIW-TS1.

---

## Topic: ExplorationReadiness integration — do not duplicate (`PROPOSED`)

**Status:** `PROPOSED` — **candidate LOCK**

`ExplorationReadiness` (`goal/ExplorationReadiness.java`) already implements a primitive discretionary unlock:

- `idleWorkTicks` (default threshold 600 via `ScavengerConfig.exploreIdleTicks`)
- `successfulLocalTrips`
- `descentPressure` (mandatory progression injection from `WorkDemandPolicy`)

**Wrong:** Delete readiness and replace with mood scalar.

**Right:** Mood **modulates thresholds**, not mandatory flags:

```text
exploreUnlockTicks =
    baseExploreIdleTicks
  × personality.curiosityModifier
  × f(boredom)          // high boredom → sooner
  ÷ f(recent REST)      // just rested → later
```

`descentPressure` remains **owned by `ExplorationActivityGoal`** — Opinion must not clear or fake it (lesson from MI-5 defect in `ExplorationReadiness.consume`).

**Must happen:** High-boredom curious mob expeditions sooner than 600 ticks without bypassing `cooldownUntilTick`.

**Must not happen:** Boredom reduces `descentPressure` or iron NEED priority.

---

## Topic: GAO-1 — AffectiveState (`IMPLEMENTED / STATIC VERIFIED`)

**Status:** `IMPLEMENTED / STATIC VERIFIED`; runtime behaviour `UNVERIFIED`

### Internal state (per mob, server-side)

```text
AffectiveState {
  float engagement;      // −100…+100, clamped
  float boredom;
  float satisfaction;
  float stress;
  float novelty;
  int ticksSinceMeaningfulEvent;
  int ticksSinceObservableProgress;
  Optional<DiscretionaryIntent> pendingIntent;
}
```

### Tick cadence (`PROPOSED` — B-13, B-16)

- **Observation:** every 10 ticks using independent `schedulerOccupied()`,
  `meaningfulProgressRecently()`, `discretionaryIdleCandidate()`, `resting()`, and
  `discretionaryAuthorityAvailable()` predicates.
- **Boredom/restlessness:** rises fastest while discretionary-idle; may rise slowly while occupied
  without recent observable progress. This clock does not grant authority to preempt the occupant.
- **Decay toward neutral:** every 20 ticks for channels not updated by the active branch/event; do
  not run decay and rise/pulse on the same channel in the same update.
- **Event application:** `ActivityEpisode` may emit bounded immediate/periodic affect pulses;
  normalized terminal/window evidence updates `OpinionMemory` separately. Raw events are never
  applied twice.
- **Engagement during MI-14 projects:** fed by explicit, episode-bounded progress milestones and
  terminals — **not** by lease age or `MiningProject` budget ticks (avoids MI-14C3 shadowing where
  project `>=` budget masks progress timeout).

### Boredom rise (example coefficients — tune in tests)

```text
if resting:
    boredom += baseRestRise * 0.25    // slow — PD-GAO-02
else if discretionaryIdleCandidate:
    boredom += baseIdleRise * (1 + personality.restlessness)
else if schedulerOccupied && !meaningfulProgressRecently:
    boredom += baseStalledRise * (1 + personality.restlessness)
    // affect only: existing authority remains non-preemptible
if SOCIAL_TRAVEL active:
    boredom -= socialDecayPerTick     // B-15
```

### Derived label (unchanged from three-layer topic)

Summary band drives **coarse** routing only; utility math uses components.

### `DiscretionaryIntent` lifecycle (`LOCKED` — supersedes B-19 TTL-only sketch)

Commitment is **adoption-anchored**, not scoring-anchored (B-27 / MI-14C2-M2).

```text
SCORED (ephemeral — same director tick only)
  ↓ top utility ≥ activationThreshold AND passes switch margin
PENDING
  ↓ executor validates hard gates (route, campfire path, config flags)
ADOPTED
  ↓ commitmentUntilTick = adoptedAt + minCommitmentTicks
RUNNING / CLAIMED
  ↓ RestSessionClaim open OR ExploringGoal MOVE active with adopted intent
TERMINAL:
  SUCCEEDED | FAILED | INTERRUPTED | INVALIDATED | EXPIRED | ABSTAINED
```

| State | Meaning | Clock starts |
| --- | --- | --- |
| `PENDING` | Intent issued; waiting for executor adoption | `issuedAtTick`; short pending TTL may expire if never adopted |
| `ADOPTED` | Executor accepted; voluntary yield may proceed | `adoptedAtTick` |
| `RUNNING` | Physical activity underway / claim live | adoption + executor `start()` or claim open |
| `EXPIRED` | Pending TTL elapsed with no adoption | `issuedAtTick` |
| `INVALIDATED` | Mandatory authority preempted intent | immediate |
| `ABSTAINED` | No activity met activation threshold | scoring tick |

**Invalidated immediately when any becomes active:**

- `MANDATORY_SAFETY` / `MANDATORY_COMMAND` / `MANDATORY_COMBAT` / `MANDATORY_SURVIVAL`
- `PROJECT_EXECUTION` with mandatory mining/progression authority
- combat target acquired
- `StayNearGoal` / player command anchor
- `UNKNOWN_ACTIVE` fail-safe (conservative)

**Must happen:** Intent consumed by executor adoption, not by scoring alone.

**Must not happen:** Stale `EXPLORE` intent fires after `FollowLovedOneGoal` starts; REST intent
opens shelter safety path; Explore starts from `readiness.eligible` alone when `opinion.enabled`.

---

## Topic: DiscretionaryIntent — data, not GoalSelector entries (`LOCKED` for GAO-4 contract)

**Status:** `LOCKED` contract; **NOT FOUND** in `src/` (implementation deferred)

When the Director selects an activity (above activation threshold and passing hysteresis), emit a
**real intent record** — not a bare `preferredActivity` enum write:

```text
DiscretionaryIntent {
  UUID intentId;
  DiscretionaryActivity activity;     // EXPLORE | REST (GAO-4 v1)
  IntentLifecycle lifecycle;          // see state machine below
  float selectedUtility;              // winning score at issue time
  float runnerUpUtility;              // for trace / switch-margin checks
  long scoredAtTick;
  long issuedAtTick;                  // PENDING entry
  long adoptedAtTick;                 // 0 until executor accepts
  long commitmentUntilTick;           // 0 until ADOPTED; starts at adoption
  Optional<UUID> episodeId;           // correlates experience + trace
  InvalidationCause lastInvalidation; // when terminal = INVALIDATED
}
```

**Consumers (GAO-4 v1 — executable pair only):**

| Intent | Executor | Hard gates (adoption) | Must never be |
| --- | --- | --- | --- |
| `EXPLORE` | `ExploringGoal` | adopted EXPLORE intent + route plan succeeds | sole authority for cave handoff / mandatory progression explore |
| `REST` | `CampfireGoal` → `RestSessionClaim` | adopted REST intent + campfire path viable | `SeekShelterGoal` (safety, p2) |

**Explicit correction:** discretionary REST is **not** `SeekShelterGoal`. Night/danger shelter
remains `MANDATORY_SAFETY` and is outside Director authority.

Directors (`MiningDirector`) **ignore** `DiscretionaryIntent` when assignment is mandatory.

---

## Topic: REST vs permanent idle

**Status:** `PROPOSED` — enriched by brainstorm B-05

| State | Meaning |
| --- | --- |
| **REST** | Intentional — safe base, night, friend nearby, post-expedition cooldown |
| **Permanent idle** | Failure — boredom rises until discretionary intent fires |

Acceptable REST while: `Opinion >= CONTENT` OR recent high stress OR short post-project cooldown.

Prolonged REST: stress falls → rest satisfaction falls → boredom rises → discretionary objective.

### Rest session lifecycle (`LOCKED`, B-28 / D-GAO-021)

```text
REST intent accepted OR shelter recovery adopts REST
  → existing CampfireGoal/SeekShelterGoal navigates
  → physical arrival is observed
  → RestSessionClaim opens
  → navigation goal may stop; claim remains
  → observer reports REST while validity holds
  → work/threat/order/invalid location/timeout closes claim
  → ordinary discretionary selection resumes
```

The claim records its source intent/commitment and authority kind in addition to anchor/type,
adopted-at/arrived-at ticks, last validity check, and close reason. It must not preserve a Minecraft
`Path`. Sleeping remains observable directly and does not need a parallel synthetic claim unless
implementation evidence shows lifecycle gaps.

### Occupied, progressing, and replaceable are independent (`LOCKED`, B-35 / D-GAO-024)

A running Goal proves the scheduler is occupied. It does not prove movement, progress, success, or
positive engagement. Unknown/host goals remain fail-safe **not-idle** and **not preemptible** for
compatibility, but they earn no positive opinion without an explicit progress/terminal event.

```text
schedulerOccupied = true
meaningfulProgressRecently = false
        ↓
boredom/restlessness may accumulate slowly
        ↓
discretionaryAuthorityAvailable = false
        ↓
Opinion MUST NOT preempt; the owning lifecycle/timeout still controls release
```

Therefore boredom accumulation and authority to replace an activity are deliberately different
predicates. When the stalled authority eventually releases, accumulated restlessness can influence
the next discretionary choice. This closes GAO-M11 without allowing Opinion to cancel mandatory or
unknown host behavior.

### Non-ticking lifecycle (`LOCKED`, PD-GAO-07)

| State | Unload/non-ticking behavior | Load behavior |
| --- | --- | --- |
| `AffectiveState` | freeze | resume without elapsed-time catch-up |
| `OpinionMemory` | freeze | resume; any future long-term decay must be separately bounded and observed |
| `DiscretionaryIntent` | invalidate | rescore current world state |
| `RestSessionClaim` | invalidate | revalidate from current authority/location; never resurrect by age alone |
| `ActivityEpisode` | suspend only when its persistent project/commitment is genuinely resumable; otherwise neutral interruption close | resume by persistent owner or create a fresh episode |

**Must happen:** emotion survives unload without instant boredom, while stale ephemeral choices are
discarded.

**Must not happen:** a days-old REST intent or invalid campfire claim resumes immediately when the
chunk starts ticking again.

---

## Topic: Observable expression (deferred UX)

**Status:** `DEFERRED` — not gen-1

Behavior communicates state without chat bubbles:

| State | Observable bias (examples) |
| --- | --- |
| VERY ENGAGED | Decisive movement; longer project stickiness |
| RESTLESS | More look-around; faster opportunity rescans |
| BORED | Seeks novelty; longer travel; activity switches |
| STRESSED | Favors familiar/safe tasks or shelter |

**Gen-0 expression without new goals (B-12):** Reuse `AnticsGoal` (mimicry, bunny-hop) as **output** of high sociability + RESTLESS — config-gated today; Opinion only biases eligibility (`mimicry` soft boost when player nearby and boredom high). No second greeting system (SPM-2).

---

## Topic: SPM compatibility bridge

**Status:** `PROPOSED` — **non-negotiable gate**

### SPM owns (do not duplicate)

- `PlayerMobEntity`, relationships, `feelingToward`, love/friendship/hostility
- Social goals: `FollowLovedOneGoal`, `StayNearGoal`, combat foundation
- Host `GoalSelector`, commands, inventory/equipment base behavior

### Opinion addon owns

- Activity / place / environment opinion memory
- AffectiveState (boredom, engagement, stress, …)
- `DiscretionaryActivityDirector`
- `SPMOpinionBridge` — maps SPM social state → activity utility; **never** second permanent friendship counter

```text
SPM feelingToward(Bob) + recent contextual experience
        ↓
OpinionBridge
        ↓
social activity utility
```

### Activity observation invariant (from MI-14C2-R2)

**`CONFIRMED` lesson:** Never conclude “idle” because only addon goals are inactive.

```text
ActivityObservation
        ↓
inspect entire GoalSelector (host + addon)
        ↓
SPM social / combat / recovery running? → NOT IDLE
addon mining / exploring running?       → NOT IDLE
actually standing with no meaningful goal → IDLE candidate
```

**Must not happen:** `FollowLovedOneGoal` active + Opinion concludes “bored, go explore” and preempts follow.

### Priority within free-choice space

```text
Mandatory / protected host intent
        >
active project
        >
social commitment (SPM)
        >
discretionary Opinion choice
```

### Compatibility matrix

| SPM feature | Opinion integration |
| --- | --- |
| `PlayerMobEntity` | Reuse; never duplicate entity |
| `GoalSelector` | Reuse; observe all running goals |
| `DispositionResolver` / `feelingToward` | Social authority; feed utility |
| `FollowLovedOneGoal` | Meaningful activity = SOCIAL_TRAVEL |
| Combat | Protected; Opinion cannot preempt |
| Recovery / safety | Protected |
| Commands | User authority beats Opinion |
| SPMScavenger mining | Opinion may *select* when discretionary; MI-14 when assigned |
| Future progression brain | Progression beats dislike when necessary |

### Parity gate (GAO-1)

**With Opinion disabled (`opinion.enabled = false`):** PlayerMob behavior must retain **stock SPM parity** (`PROPOSED` acceptance test).

---

## Topic: Lessons transferred from MI-14 (`CODE_CONFIRMED`, Agent_Claude)

The mining control plane finished its multi-mode MAIBS pass at 389 tests. It is the same architectural
shape as the Discretionary Activity Director one level down — a director choosing among *modes*
rather than among *activities* — and it produced five control-flow defects that this RFC can avoid by
construction rather than rediscover.

### B-21 — A director scoped to one activity cannot prove it is generic

`MiningProject` had seven modes in its enum and `MiningExecutionLease` stored a mode. Both looked
multi-mode. When the second executable mode arrived, **three hidden single-mode assumptions surfaced
at once**: `enforceLease` asked for `CONTROLLED_DESCENT` specifically, `ExecutionIntentPolicy` asked
`isControlledDescent()`, and nothing ever checked that a lease's mode matched its project's. All three
compiled and passed 361 tests, because with one executor they are indistinguishable from correct.

**Prediction:** `DiscretionaryActivityDirector` (GAO-4) built while only one discretionary activity is
genuinely executable will contain the same class of defect, and it will be invisible until the second
one lands. **Proposed rule — GAO-4 ships with at least two real, executable discretionary activities,
or its genericity is unfalsifiable.** This is a sequencing constraint, not extra scope: the second
activity is the test.

### B-22 — Utility is inert. Minecraft's scheduler does not read it.

The highest-value transfer. `WrappedGoal.canBeReplacedBy` yields only to **strictly lower** priority
numbers, so equal-priority goals cannot preempt one another. MI-14C2 shipped an arbiter row granting
`GATHER_RESOURCES` `ALLOW` under an active tunnel — architecturally correct and **behaviourally
circular**, because the incumbent never released its flags, so the winner could never run, so the state
that would have made the incumbent yield was never reached.

**A ranked utility score changes nothing on its own.** `IdleOpportunityPolicy` naming a winner is a
recommendation to a scheduler that cannot hear it. GAO-4 therefore needs an explicit **voluntary yield
protocol** — the incumbent discretionary activity stands aside when a live intent exists — exactly the
shape of the Exposure Opportunity Handoff. Without it the mob keeps wandering while a scoreboard
insists it should be mining.

### B-23 — Boredom must not accrue while the activity is being served

MI-14C3 learned that a no-progress watchdog ages against an executor that is being *helped*: while
gather mined the vein the tunnel exposed, the tunnel recorded no progress and was revoked **for
succeeding**. Repaired with `ExecutionBlocker.COOPERATIVE_WORK` / `BlockerClass.PROTECTED_PAUSE`,
which excludes that time from the progress window.

Affect clocks have the identical hazard one layer up. A mob whose chosen activity is *mining* but
whose current flag-holder is `GatherResourcesGoal` is **engaged**, not idle — yet a scheduler-wide
observer sees the chosen executor not running. B-16 already isolates MI-14 clocks; the stronger form
is that boredom must pause on the same signal, and
`MoveHolderClassification.COOPERATIVE_PROJECT_WORK` is a ready-made, already-shipped input.

### B-24 — The experience-event source already exists, and it closes a deferred loop

`MiningProjectEnd` is a set of outcomes with real affective meaning, and `MiningTransition` already
persists each with location, heading and tick:

| Outcome | Experience |
| --- | --- |
| `CAVE_FOUND` | reward — the dig found somewhere worth going |
| `DEMAND_SATISFIED` | success — the objective closed |
| `SEARCH_BUDGET_EXHAUSTED` | fruitless effort |
| `NO_PROGRESS` | frustration — physically stuck |
| `HAZARD` | fear — lava, gravel, a drop |
| `TOOL_FAILURE` | unpreparedness |

More importantly, **MI-14 Loop C** (repeated descent at a site already exhausted) was classified
`RUNTIME_QUESTION` and explicitly deferred to "MiningMemory". **GAO-5 PLACE opinion is that system.**
That reframes it: GAO-5 is not the fifth new feature, it is the consumer of an already-deferred loop —
which may argue for pulling it earlier than its current slot.

### B-25 — An activity with no executor must not be selectable

Two shipped instances. `HANDOFF_TUNNEL_SEARCH` named a destination with no executor for months (Loop
D, held honest only by refusing to grant it authority). `MiningProjectEnd.resumable()` has **zero
consumers**, and that gap became M5: a stored `RETRY` project permanently blocked all future
assignment because nothing could resume or retire it.

The user's sketch includes *"build/farm eventually"* — precisely this shape. **Proposed rule:** an
`ActivityClass` is admissible to selection only when a designated executor exists, mirroring
`ExecutionIntentPolicy.intentOf`, which returns empty for catalogued-but-unexecutable modes. Aspiration
belongs in the taxonomy; it must not reach the scorer.

### B-26 — Check the premise: idle is not currently empty

The framing *"nothing urgent → wander → wander → wander"* deserves a Gate SPM-1 style check before it
justifies architecture. Idle today already dispatches `TrackedLocalWanderGoal` (9), `AnticsGoal` (9),
`CampfireGoal` (7) and `ExploringGoal` (8), and `ExplorationReadiness` already gates exploration on
accumulated idle ticks.

So the gap is **not** "nothing happens". It is that the choice among existing idle behaviours is a
**fixed priority ladder evaluated in numeric order**, which cannot express "I have explored for
thirty-one minutes and would rather sit by the fire". GAO-3/GAO-4 are therefore *replacing an implicit
static ranking*, not filling a void — a materially easier and more falsifiable claim, and one that
makes the parity gate meaningful: with opinion off, the static ladder must be reproduced exactly.

### B-27 — Oscillation, and why commitment must be claim-anchored

Two activities with close utility, rescored every observation pass, produce flip-flop: walk toward the
cave, reconsider, walk toward camp, reconsider. `DiscretionaryIntent` TTL (B-19) is the right
mechanism, but MI-14C2-M2 proved the subtle part: the cave-continuation commitment expired on the
**discovery** clock rather than the **claim** clock, so a late claim received one tick of authority.

**A discretionary commitment must run from when the activity is adopted, not from when the intent was
scored**, and its lifetime must not be shorter than the activity's own legitimate duration. Where two
subsystems must agree on a lifetime, share the *predicate*, not the constant.

### B-34 — End-to-end decision trace (`LOCKED`, D-GAO-025)

A score-only trace cannot distinguish a bad policy choice from a failed handoff. Keep a bounded
per-mob ring buffer (initial implementation target: 16–32 decisions; exact capacity is a config and
performance choice) covering the complete control path:

```text
AVAILABLE candidates
  → normalized SCORES
  → SELECTED activity
  → INTENT issued/invalidated
  → CLAIM adopted/rejected
  → incumbent YIELD requested/completed/refused
  → EXECUTOR admitted/started/refused
  → TERMINAL success/interrupted/expired/failed + exact cause
```

Each transition records decision/intent/claim/episode IDs and ticks so the chain is correlatable.
Tracing is server-owned, bounded, and query-on-demand; it must not emit perpetual log lines or retain
live entity/path references.

**Must happen:** an operator can distinguish “REST won but no claim was adopted” from “REST executor
started and failed.”

**Must not happen:** a correct utility score is treated as proof that the selected behavior became
observable.

### B-32 — Multi-mob coordination boundary (`PROPOSED`)

Prevent accidental synchronization and exclusive-resource contention; do not forbid intentional
coordination. Deterministic staggering/stable tie-breaking may prevent lockstep rescoring, and
reservations apply only to exclusive physical targets such as one rest position or one break target.
Explicit companion/social activities may legitimately align several mobs on EXPLORE or REST.

---

## Topic: Hard architectural rules

| ID | Rule |
| --- | --- |
| **D-GAO-001** | `OPINION ≠ OBJECTIVE` — never replaces NEED/ProgressGoal/MiningProject assignment |
| **D-GAO-002** | `OPINION ≠ COMMAND` — user/command authority wins |
| **D-GAO-003** | `OPINION ≠ CAPABILITY` — dislike does not remove competence |
| **D-GAO-004** | Personality/opinion choose among **valid** solutions only |
| **D-GAO-005** | No `BoredGoal` — discretionary intent goes through directors |
| **D-GAO-006** | Scheduler-wide activity observation (host + addon) |
| **D-GAO-007** | SPM social graph is authoritative for entity relationships |
| **D-GAO-008** | Opinion disabled ⇒ SPM parity unchanged |
| **D-GAO-017** | **LOCKED (GAO-4):** An activity is selectable only when a designated executor exists | aspiration stays out of scorer/director |
| **D-GAO-018** | **LOCKED (GAO-4):** Discretionary selection requires a **voluntary yield protocol**; utility ranking alone cannot move a Minecraft goal at equal or weaker priority | B-22; Campfire p7 vs Explore p8 |
| **D-GAO-019** | *(candidate, B-23)* Affect clocks pause while a downstream executor serves the chosen activity, reusing `COOPERATIVE_PROJECT_WORK` rather than a second signal |
| **D-GAO-020** | **LOCKED (GAO-4):** GAO-4 ships with ≥2 executable discretionary activities (EXPLORE + REST) | B-21; PD-GAO-06 |
| **D-GAO-021** | **LOCKED:** Sustained REST is an arrival-anchored, condition-bound claim tied to the activity/authority that legitimately adopted REST; Goal liveness or proximity alone is insufficient |
| **D-GAO-022** | **LOCKED:** Experience is episode-scoped and frequency-normalized; bounded short-term affect pulses and normalized long-term `OpinionMemory` learning are separate outputs and cannot double-apply one event |
| **D-GAO-023** | **LOCKED:** Outcome class controls learning eligibility, not sign; exact terminal cause is preserved, and feasibility/safety/authority outcomes do not automatically imply dislike |
| **D-GAO-024** | **LOCKED:** Goal liveness proves occupancy only. Lack of progress may advance affect/restlessness, but cannot grant discretionary preemption authority |
| **D-GAO-025** | **LOCKED:** A bounded trace spans score → intent → claim → scheduler yield/handoff → executor start → exact terminal cause |

### Consensus — corrected GAO-0/0b/0c contracts

**Accepted:** prior-consensus D-GAO-011, D-GAO-012, D-GAO-015, and amended D-GAO-021…025;
PD-GAO-06 = Explore + Rest;
PD-GAO-07 = freeze persistent affect/opinion state while non-ticking but invalidate/revalidate
ephemeral intent/claim state.

**Why:** source inspection disproved Goal-liveness REST; peer review found the affect/learning
double-application risk, ambiguous voluntary-abandon sign, stalled-occupant boredom gap, and
score-only trace gap. Each now has explicit ownership and falsification criteria.

**Supporting contributors:** Agent_Codex source/MAIBS review; user-provided independent peer review
and product decisions (2026-08-09).

**Remaining objections:** none high-severity for GAO-0/0b/0c contracts. Runtime behavior and tuning
remain `UNVERIFIED`; D-GAO-017…020 and PD-GAO-01…05 remain outside this lock set.

**Rejected alternatives:** proximity-only REST; raw events applied directly to both affect and
memory; outcome class as emotional sign; stalled occupancy granting preemption; score-only tracing;
mandatory artificial diversity between cooperative mobs.

**Status:** `LOCKED`

---

## Topic: Relationship to existing RFCs

| RFC / system | Relationship |
| --- | --- |
| **RFC-VANILLA-AUTONOMOUS-PROGRESSION** | `RequirementResolver` / `WorkDemandPolicy` sit **above** Opinion in hierarchy |
| **RFC-MINING-INTELLIGENCE** | `MiningDirector` assigns when progression requires; Opinion chooses *route preference* among legitimate modes |
| **MI-14C** | Execution control unchanged; Opinion feeds **discretionary** intent only |
| **MI-11 Torch intelligence** | Discretionary or project-adjacent; does not override survival |

---

## Topic: Phased plan

**Status:** GAO-0 through GAO-3 `IMPLEMENTED / STATIC VERIFIED`; GAO-4 is nearest frontier

| Phase | Task | Deliverable | Depends on |
| --- | --- | --- | --- |
| **GAO-0** | Activity taxonomy + observation contract | **IMPLEMENTED:** `ActivityClass`; one `ActivityObservationService` scan wrapping `MoveHolderClassifier`; host/addon taxonomy and parity tests | MI-14C2-R2 pattern |
| **GAO-0b** | Schema vocabulary + inert ingress contract | **IMPLEMENTED:** `ExperienceKind`, `ExperienceCause`, `OutcomeClass`, `ActivityKind`, immutable `ExperienceEvent`, interface-only `ExperiencePipeline.accept` | GAO-0, D-GAO-026/027 |
| **GAO-0c** | Episode + rest-claim processing | **IMPLEMENTED:** `ActivityEpisode`, `EpisodeRoutingPipeline`, `RestSessionClaim`/`RestSessionCoordinator`, `OpinionExperienceRegistry`, mining/explore/rest emitters, observer REST integration | GAO-0, GAO-0b |
| **GAO-1** | `AffectiveState` + observation | **IMPLEMENTED:** per-mob mood channels, 10-tick observation, pulse wiring, rate-based boredom, REST/stalled/social semantics, decay, freeze-on-unload, `opinion.enabled` | GAO-0, GAO-0b, GAO-0c |
| **GAO-2** | `OpinionMemory` v1 (ACTIVITY only) | **IMPLEMENTED:** `ActivityOpinionMemory`, `OpinionMemory`, `OpinionLearningPolicy`, normalized-evidence wiring, PD-GAO-03 death reset | GAO-1 |
| **GAO-3** | `IdleOpportunityPolicy` | **IMPLEMENTED:** EXPLORE + REST utility scoring, normalized components, ranked `ScoringResult`, no execution | GAO-2, existing goals |
| **GAO-4** | `DiscretionaryActivityDirector` | **IMPLEMENTED:** intent lifecycle, abstention, voluntary yield, consumer gates, trace | GAO-3 |
| **GAO-5** | PLACE / ENVIRONMENT opinions | World gains meaning for choice | GAO-4, spatial memory TBD |
| **GAO-6** | ENTITY bridge | SPM `feelingToward` integration | GAO-4 |
| **GAO-7** | PersonalityModel | Trait-weighted experience scaling | GAO-2 |
| **GAO-8** | Observable expression | Movement/scan biases | GAO-4, deferred UX |

### GAO-0b implementation task (`IMPLEMENTED / STATIC VERIFIED`)

| Field | Contract |
| --- | --- |
| Status | `IMPLEMENTED / STATIC VERIFIED`; runtime behavior `UNVERIFIED` (inert by construction) |
| Objective | Establish the immutable, server-neutral experience vocabulary and ingress seam required by GAO-0c without changing runtime behavior |
| Files/systems | New `experience` package and focused unit tests only; RFC evidence update |
| Constraints | No production emitters, concrete queue/router, episode state, persistence, affect, OpinionMemory, RestSessionClaim, Goal changes, or scheduler changes |
| Must happen | Exact field preservation; null/non-finite rejection; distinct activity-vs-scheduler taxonomy; functional ingress forwards the identical event |
| Must not happen | No PlayerMob behavior, scan cadence, Goal priority/flags, readiness, mining lease, save data, or network behavior changes |
| Validation | Focused schema tests, full test suite, clean build, `git diff --check`, static scope/MAIBS review |
| Runtime | Not required for a genuinely inert schema slice; no Minecraft launch is included |

### GAO-0 implementation task

| Field | Contract |
| --- | --- |
| Status | `IMPLEMENTED / STATIC VERIFIED`; runtime behavior `UNVERIFIED` |
| Objective | Extract the existing 10-tick selector observation into one `ActivityObservationService` without changing behavior |
| Primary systems | `ExplorationActivityGoal`, `ExplorationReadiness`, `MoveHolderClassifier`, new observer contract and unit tests |
| Constraints | Opinion remains disabled/unimplemented; observer stays flagless and staggered; current unknown-goal fail-safe and director ordering remain intact; no second selector scan |
| Must happen | Existing idle/work/explore sequences produce identical `ExplorationReadiness` results before and after refactor; host + addon goal classifications are visible through one service |
| Must not happen | New affect state, discretionary intent, REST claim, preemption, scan cadence, goal priority, or observable mob behavior appears in GAO-0 |
| Static/unit gates | Unknown running Goal = occupied/meaningful-work fail-safe; wander/look/antics remain cosmetic-idle; Explore remains expedition; observer owns no flags; early-return/director ordering regression tests |
| Build gate | `gradlew.bat test build` passes; proves compile/tests only, not runtime behavior |
| Runtime state | Not required to implement the refactor, but behavior parity remains `UNVERIFIED` until a separately approved Minecraft launch |

### GAO-0 pre-implementation Behavioral Prediction

| Layer | Result |
| --- | --- |
| Design intent | Replace the observer's private type checks with one shared classification/observation substrate and change no Goal behavior |
| Existing mechanism | `ExplorationActivityGoal` scans running Goals every 10 ticks; Explore is separate, wander/look/antics/self are ignored, every other active Goal resets readiness; director bookkeeping follows the readiness update |
| Planned mechanism | `ActivityObservationService` performs that one scan, asks `MoveHolderClassifier` for every `ActivityClass`, and returns independent observation predicates; `ExplorationActivityGoal` consumes only the legacy expedition predicates |
| Predicted observable behavior | No visible change: the same Goals run at the same priorities/flags and readiness/director calls occur in the same order |
| Confidence | `CODE_CONFIRMED` old control flow; planned parity is `UNVERIFIED` until tests/runtime |

**Goal interaction expectation:** local wander remains idle; FollowLovedOne, combat, safety,
commands, mining/cooperative execution, Campfire approach, shelter, and unknown Goals remain
meaningful/non-idle; Explore neither resets nor increments idle; the observer remains flagless.

**Predicted weird behaviors / risks:**

1. `ARCHITECTURE_DEFECT` if the service repeats SPM suffix logic instead of delegating to
   `MoveHolderClassifier`.
2. `ARCHITECTURE_DEFECT` if a nominal `PASSIVE_HELPER` stops resetting readiness: current code treats
   all helpers outside the explicit wander/look/antics list as meaningful, so changing that in GAO-0
   would shift expedition timing.
3. `ARCHITECTURE_DEFECT` if collecting a full class set changes the old first-meaningful-result or
   moves `MiningDirector.enforceLease` behind an early return.
4. `RUNTIME_QUESTION` whether any third-party Goal has surprising runtime flags/lifecycle despite
   correct fail-safe classification; falsify with an approved unknown-Goal runtime scenario later.

**Alternatives:** (A) thin observation wrapper over the shared classifier — selected by D-GAO-011;
(B) put the live selector scan inside the mining classifier — rejected because it couples mining
lease context to general observation and creates another scan owner.

**Must happen:** deterministic parity tests give identical readiness actions for local idle,
Explore, meaningful work, and unknown active Goals.

**Must not happen:** GAO-0 changes Goal registration, flags, priorities, selection, REST behavior,
or introduces any Opinion/affect state.

**Pre-implementation MAIBS gate:** `PASS — BEHAVIORALLY_PLAUSIBLE`, conditional on the explicit
legacy-predicate parity tests above. Runtime remains `UNVERIFIED`.

### GAO-0-B1 — outer early return bypasses lease enforcement (`BLOCKER`, `CODE_CONFIRMED`)

`ExplorationActivityGoal.tick()` currently executes:

```text
if !ScavengerConfig.enabled:
    readiness.recordMeaningfulWork()
    return

... later ...
directorTick()
    → MiningDirector.enforceLease(...)
```

The only production call to `MiningDirector.enforceLease` is inside `directorTick()`. Therefore an
active mining project/lease is not observed or revoked while the top-level addon switch is disabled,
even though `MiningDirector.miningExecutionBlocker()` explicitly maps that state to
`FEATURE_DISABLED`. The inner “lease first” ordering is correct only after control reaches the inner
method; the outer return prevents that.

**Evidence:**

- `goal/ExplorationActivityGoal.java:86-89` — outer disabled return.
- `goal/ExplorationActivityGoal.java:114-121` — `directorTick()` occurs after the return.
- `goal/ExplorationActivityGoal.java:132-143` — sole observer-owned `enforceLease` call and inner
  feature early return.
- `mining/MiningDirector.java:153-170,247-302` — `FEATURE_DISABLED` blocker and lease enforcement.
- Repository-wide call-site probe found no second production `enforceLease` owner.

**Predicted observable failure (`GAME_MECHANICS_INFERRED`, runtime `UNVERIFIED`):** disable the addon
while a mining assignment is active; executors stop admitting work, but the stored project/lease
survives until the observer is allowed through again. Re-enabling may then retire/revoke stale state
rather than having the disable action take effect on the normal 10-tick control-plane cadence.

**Options:**

1. **Recommended — narrow prerequisite repair:** preserve readiness reset on disable, but invoke the
   lease/control-plane bookkeeping before the outer return. This changes defective disabled-state
   lifecycle behavior and therefore requires explicit authorization beyond a pure parity refactor.
2. Preserve the outer return. This keeps historical behavior but fails the user's explicit
   observer-ordering gate and leaves the control-plane invariant false.
3. Add a second always-on lease observer. Rejected: duplicates tick ownership and conflicts with the
   single-observer architecture.

**Must happen:** an active project encounters `FEATURE_DISABLED` on the next observer cadence even
when ordinary readiness/activity processing is disabled.

**Must not happen:** the repair assigns new mining work, changes Goal priority/flags, or runs
activity/Opinion selection while disabled.

**Resolution:** `IMPLEMENTED / STATIC VERIFIED`. The observer now runs existing
`MiningDirector.enforceLease` ownership before the disabled return, then records the historical
readiness reset and stops before activity observation, descent pressure, handoff, or assignment.
Post-build MAIBS found a second path: when a mob loaded while the addon was already disabled,
`installExploration()` installed no observer, so persisted authority still had no cleanup owner.
The same flagless observer is now installed in permanent cleanup-only mode in that case (and when
the host stroll shape is incompatible). Its construction-time authority bit prevents re-enabling
the config from turning that cleanup observer into an assignment path.

**Evidence:** `ExplorationDisabledCadenceTest` covers callback order, the absence of a new-work
surface, enabled-path preservation, stale-authority non-resurrection, and the cleanup-only observer
authority gate. `MiningExecutionC3R1Test` and `ExecutionLeasePolicyTest` preserve combat,
safety/recovery, player-order, and lease-clock behavior. Focused tests passed; final
`gradlew.bat clean build` passed with 405 tests, 0 failures/errors/skips.

**Remaining evidence limit:** no Minecraft runtime was launched. Actual config-toggle behavior,
save/reload cleanup, and observable parity are `UNVERIFIED` under Gate AV-1.

### GAO-0 implementation evidence and post-implementation MAIBS

| Layer | Result |
| --- | --- |
| Design intent | One observation substrate classifies current activity without changing scheduler authority or adding Opinion behavior |
| Actual implementation | `ActivityObservationService` owns the sole activity scan and reduces independently classified `ActivityClass` values; `ExplorationActivityGoal` consumes only the legacy readiness predicates; `MoveHolderClassifier` owns addon types and SPM suffixes |
| Test evidence | Focused GAO-0/B1/C3 suite passed; complete clean build passed **405 tests, 0 failures, 0 errors, 0 skipped**; `git diff --check` passed |
| Parity | `CODE_CONFIRMED` and `UNIT_CONFIRMED` for the old idle/work/explore reducer and registration/priority non-change; runtime parity is `UNVERIFIED` |
| Scope | No `ExperienceEvent`, episode, rest claim, affect, opinion memory, discretionary intent/director, scoring, or voluntary yield was added |

| MAIBS scenario | PERCEIVE → CHOOSE → PHYSICAL RESULT | Static result |
| --- | --- | --- |
| Pure local wandering | observer sees wander + look/observer noise → readiness accumulates idle → existing wander keeps MOVE | `PASS` |
| Follow loved one | suffix maps `SOCIAL_TRAVEL` → readiness resets as meaningful → host goal retains MOVE | `PASS` |
| Combat / safety / command | maps mandatory/protected activity → readiness resets; no priority, flag, or preemption change | `PASS` |
| Mining / cooperative work | designated executor maps project execution; participating chores can map productive cooperation → both remain meaningful | `PASS` |
| Exploration | maps expedition → neither resets nor increments readiness while route remains active | `PASS` |
| Campfire / shelter | Campfire remains approach/meaningful; shelter is safety and only reports REST while actually sleeping; no future claim semantics | `PASS` |
| Unknown host goal | maps `UNKNOWN_ACTIVE` → occupied/meaningful fail-safe → no expedition is started over it | `PASS` |
| Disabled during active lease | observer settles existing authority, resets readiness, then returns before pressure/handoff/assignment | `PASS` (static/unit) |
| Load while already disabled | cleanup-only observer is installed without replacing stroll or adding executors; re-enable cannot grant it new-work authority | `PASS` (static/unit) |

**Predicted weird behaviors:** a flagless helper still delays exploration because that is preserved
legacy behavior; a cleanup-only observer installed while disabled will not dynamically grow an
exploration executor stack after re-enable; and third-party Goal suffixes remain conservative
`UNKNOWN_ACTIVE`. These are accepted GAO-0 parity/compatibility choices, not newly claimed product
behavior. Runtime experiments that would falsify this pass are: a disabled reload retains a stale
lease, local wander no longer accumulates readiness, FollowLovedOne becomes idle, or an unknown Goal
permits an expedition.

**Static ownership probes:** activity suffixes for `FollowLovedOneGoal`, raid, harvest, and train are
not found outside `MoveHolderClassifier`; the old direct selector scan is not found in
`ExplorationActivityGoal`; and no Opinion/affect/intent implementation is present in the GAO-0 diff.
Other `getAvailableGoals()` calls remain for installation, mining arbitration, executor lookup, and
companion coordination—not competing activity semantics.

**Post-implementation gate:** `MAIBS_STATIC PASS — BEHAVIORALLY_PLAUSIBLE`.
`RUNTIME_CONFIRMED` remains false because no Minecraft launch was authorized.

### Product decisions

| ID | Status | Question | Options | Decision / recommendation |
| --- | --- | --- | --- | --- |
| **PD-GAO-01** | `LOCKED` | Should mood affect **only** discretionary ranking, or also **thresholds** (explore idle ticks)? | A thresholds only / B ranking only / **C both** | **C both** — user 2026-08-09 |
| **PD-GAO-02** | `LOCKED` | Is `CampfireGoal` idle REST or positive engagement? | REST / mild engagement | **REST with mild engagement** — user 2026-08-09 |
| **PD-GAO-03** | `LOCKED` | Persist `OpinionMemory` across death? | wipe / partial / full | **Partial persistence** — see death table below |
| **PD-GAO-04** | `LOCKED` | Config surface | `opinion.enabled` only / full trait sliders | **`opinion.enabled` now**; trait presets deferred until GAO-7 `PersonalityModel` |
| **PD-GAO-05** | `RESOLVED BY D-GAO-015` | Who owns `IdleOpportunityPolicy` tick? | fold into `ExplorationActivityGoal` / new flagless goal | Single refactored observer; affect/intent bookkeeping must precede early returns |
| **PD-GAO-06** | `LOCKED` | Which two executable activities prove GAO-4? | Explore + Rest / Explore + opportunistic Gather / other | **Explore + Rest** after the REST claim lifecycle exists |
| **PD-GAO-07** | `LOCKED` | What happens while unloaded/non-ticking? | freeze / lazy elapsed-time decay / full catch-up | **Freeze affect/opinion; invalidate intents; invalidate/revalidate rest claims; suspend only genuinely resumable episodes** |
| **PD-GAO-08** | `LOCKED` | Must the Director always pick the top score? | always pick / **abstain below threshold** | **ABSTAIN (`NO_SELECTION`)** when top utility is below activation threshold — neither activity appeals enough |
| **PD-GAO-09** | `LOCKED` | Who may start discretionary Explore/Rest when `opinion.enabled`? | legacy `canUse` / **adopted intent only** | **`opinion.enabled=true`:** discretionary `CampfireGoal` REST and discretionary `ExploringGoal` expedition start require an **adopted** matching intent; **`opinion.enabled=false`:** legacy parity unchanged |
| **PD-GAO-10** | `LOCKED (direction)` | Switch/hold policy | rescore every tick / **commitment + switch margin** | **Adoption-anchored minimum commitment** + meaningful switch margin before incumbent yields; exact ticks tuned in implementation |
| **PD-GAO-11** | `LOCKED` | REST executor for discretionary choice | Campfire / SeekShelter / both | **Campfire + `RestSessionClaim` only** — `SeekShelterGoal` (p2 safety) is never the discretionary REST executor |

#### PD-GAO-03 death semantics (`LOCKED` — GAO-2)

On mob death, `OpinionMemory.onDeath()` applies **partial reset**. `AffectiveState` is a separate
short-term layer and is **not** folded into `OpinionMemory` persistence semantics.

| Field | On death |
| --- | --- |
| `preference` | **Mostly survives** (unchanged) |
| `repetition` | **Cleared** |
| `recentDuration` | **Cleared** |
| `recentFailures` | **Cleared** |
| `recentReward` | **Heavily decayed** (×0.5) |
| `lastPerformed` | **Survives** (history anchor) |
| `AffectiveState` | Separate; not part of `OpinionMemory` death contract |

Disk save/load persistence for opinions remains **deferred** until a later phase; GAO-2 implements
in-memory learning plus runtime death reset only.

---

## Topic: Validation & gates

| Gate | Criterion |
| --- | --- |
| **GAO-PARITY** | Opinion off ⇒ indistinguishable from stock SPM + existing addon (within documented addon goals) |
| **GAO-HIERARCHY** | Survival/combat/command/progression always beat boredom in falsification scenarios |
| **GAO-OBSERVE** | `FollowLovedOneGoal` / combat / recovery never classified as idle |
| **GAO-COMPETENCE** | High negative `Opinion(MINING)` mob still mines when iron NEED active |
| **GAO-REPETITION** | Same activity long duration reduces utility without erasing long-term preference |
| **GAO-THRESHOLD** | Mood modulates `exploreIdleTicks` / REST cooldown; never mandatory NEED |
| **GAO-REST-LIFECYCLE** | Campfire/shelter arrival opens bounded REST that survives delivery-goal stop and closes on invalidation |
| **GAO-ATTRIBUTION** | Episode IDs and outcome classes prevent interrupt, command, frontier, and event-frequency mislearning |
| **GAO-TRACE** | Bounded per-mob trace covers candidates/scores → intent → claim → yield/handoff → executor admission/start → exact terminal cause |
| **MAIBS-1** | Multi-minute discretionary sessions look human-plausible (explore → rest → socialize → return) |

### MAIBS discretionary scenarios (`PROPOSED` — pre-implementation)

| ID | Setup | Must happen | Must not |
| --- | --- | --- | --- |
| **GAO-M1** | Iron NEED active; `Opinion(MINING) = −60` | Mob still mines/smelts per `WorkDemandPolicy` | Boredom cancels gather |
| **GAO-M2** | `FollowLovedOneGoal` running 5 min | Activity = `SOCIAL_TRAVEL`; boredom flat or falls | Explore intent preempts follow |
| **GAO-M3** | Safe night, campfire active, CONTENT | REST; boredom rises slowly | Instant expedition |
| **GAO-M4** | 8 min straight `TrackedLocalWanderGoal` | Boredom → `DiscretionaryIntent(EXPLORE)` | Permanent wander loop |
| **GAO-M5** | Diamond NEED + cave handoff + high `Opinion(CAVE)` | Prefer explore handoff over tunnel when both legal | Clairvoyant ore scan |
| **GAO-M6** | `DiscretionaryIntent(EXPLORE)` issued; combat target appears tick+1 | Intent invalidated; attack runs | Delayed explore after fight |
| **GAO-M7** | Adopted REST intent reaches fire; `CampfireGoal` reaches 200-tick cap | Matching arrival-bound claim keeps REST active | Post-arrival mob immediately becomes bored/Explore |
| **GAO-M8** | Mob merely crosses a campfire radius | No REST claim without adopted activity + arrival | Proximity creates false rest |
| **GAO-M9** | Explore route meets simulation frontier | Temporary confidence/cooldown only | Long-term dislike of exploration |
| **GAO-M10** | Two mobs reach discretionary threshold on same tick | Deterministic staggering; reserve exclusive positions/targets, but permit explicit cooperative alignment | Accidental lockstep oscillation or both claim the same exclusive spot |
| **GAO-M11** | Unknown Goal remains running without progress for minutes | Slow restlessness rises while scheduler remains occupied and non-preemptible; owning lifecycle controls release | Frozen affect or Opinion preempting unknown authority |
| **GAO-M12** | REST selected, then entity unloads for days | Affect/opinion freeze; intent/claim are invalidated; current state is rescored on load | Ancient intent or stale campfire claim resurrects |

Runtime probes require separate Minecraft launch approval.

### MAIBS behavioral prediction — GAO-M4 (`CODE_CONFIRMED` mechanism, `UNVERIFIED` runtime)

**Scenario:** Curious preset mob; iron NEED satisfied; no project; SPM stroll + addon `TrackedLocalWanderGoal` interleave; `ExplorationActivityGoal` accumulates `idleWorkTicks` every 10s of cosmetic-only activity.

| Minute | Predicted observable |
| ---: | --- |
| 0–2 | Local wander; boredom low; no expedition |
| 3–4 | Boredom crosses threshold; `exploreUnlockTicks` shortened (PD-GAO-01 C); `DiscretionaryIntent(EXPLORE)` issued |
| 5 | `ExploringGoal.canUse` true; expedition starts; `ExplorationReadiness.consume` fires `EXPEDITION_UNLOCKED` event |
| 6–8 | Directed travel; novelty/engagement rise; repetition on EXPLORE activity increases |
| 9+ | Stage complete or cooldown; return wander; boredom reset slower if satisfaction high |

**Failure mode (must not):** Permanent wander because `idleWorkTicks` threshold fixed at 600 while boredom system disabled — **GAO-PARITY** requires `opinion.enabled` path to modulate threshold; parity path unchanged.

**Weirdness watch:** SPM `WaterAvoidingRandomStrollGoal` (pri 8) and addon `TrackedLocalWanderGoal` (pri 9) may alternate; both count as idle — boredom should not reset on handoff between them.

---

## Topic: Rejected alternatives

| Alternative | Why rejected |
| --- | --- |
| Single `mood` scalar driving all behavior | Cannot express “like mining but done for now” |
| `BoredGoal` in GoalSelector | Fights MI-14C architecture; bypasses directors |
| Second friendship graph parallel to SPM | Conflicts with `feelingToward` |
| Opinion preempting `FollowLovedOneGoal` | Breaks SPM social design |
| LLM / ML mood | Out of scope; deterministic utility only |
| Opinion as mandatory objective source | Violates D-GAO-001 |
| Second activity observer beside `ExplorationActivityGoal` | Duplicates cadence; risks MI-14C2-R2-class drift (D-GAO-015) |

---

## Decision Registry

| ID | Decision | Status | Notes |
| --- | --- | --- | --- |
| D-GAO-001 | Opinion ≠ objective | `PROPOSED` | |
| D-GAO-002 | Opinion ≠ command | `PROPOSED` | |
| D-GAO-003 | Opinion ≠ capability | `PROPOSED` | |
| D-GAO-004 | Preference among valid solutions only | `PROPOSED` | |
| D-GAO-005 | No BoredGoal | `PROPOSED` | |
| D-GAO-006 | Scheduler-wide activity observation | `PROPOSED` | Evidence: MI-14C2-R2 |
| D-GAO-007 | SPM owns social relationships | `PROPOSED` | `DispositionResolver` |
| D-GAO-008 | Opinion disabled ⇒ SPM parity | `PROPOSED` | Debug + ship gate |
| D-GAO-009 | Three-layer model (Personality/Opinion/Mood) | `PROPOSED` | Agent_ChatGPT |
| D-GAO-010 | Typed OpinionMemory taxonomy | `PROPOSED` | ACTIVITY/PLACE/ENTITY/ENV/PROJECT |
| D-GAO-011 | Reuse `MoveHolderClassifier` for GAO-0 observation | `IMPLEMENTED` | One classifier owns addon types and SPM suffix taxonomy; clean build 2026-08-09 |
| D-GAO-012 | Existing terminals emit raw events into one pipeline; no parallel scanners/direct memory writes | `LOCKED` | Amended with D-GAO-022 separation; user peer review 2026-08-09 |
| D-GAO-013 | Mood modulates readiness thresholds; never owns `descentPressure` | `PROPOSED` | MI-5 lesson |
| D-GAO-014 | `DiscretionaryIntent` as data consumed by existing goals | `PROPOSED` | TTL + invalidation B-19 |
| D-GAO-015 | Single `ActivityObservationService`; refactor `ExplorationActivityGoal` scan | `IMPLEMENTED` | Legacy readiness parity tests plus single activity-scan ownership; runtime unverified |
| D-GAO-016 | Dual predicates: expedition meaningful-work ≠ affect idle/rest | `PROPOSED` | Campfire B-17 |
| D-GAO-017 | Selectable activity requires a designated executor | `PROPOSED` | B-25 |
| D-GAO-018 | Discretionary selection requires voluntary yield | `PROPOSED` | B-22; equal-priority scheduler evidence |
| D-GAO-019 | Affect pauses while selected downstream activity executes | `PROPOSED` | B-23 |
| D-GAO-020 | GAO-4 proves at least two executable activities | `PROPOSED` | B-21 |
| D-GAO-021 | Sustained REST claim requires legitimate adopted authority + arrival | `LOCKED` | Source kind distinguishes discretionary rest from shelter recovery |
| D-GAO-022 | Episode normalization separates bounded affect pulses from long-term learning | `LOCKED` | One raw event cannot double-apply |
| D-GAO-023 | Outcome controls learning eligibility; exact cause controls sign | `LOCKED` | `VOLUNTARY_ABANDON` is not inherently negative |
| D-GAO-024 | Occupancy, progress/restlessness, and discretionary preemption are independent | `LOCKED` | Closes stalled unknown-Goal affect hole without violating authority |
| D-GAO-025 | Bounded end-to-end decision trace | `LOCKED` | Score through terminal; 16–32 initial capacity target, tune by evidence |
| D-GAO-026 | GAO-0b owns schema vocabulary + interface-only ingress; GAO-0c owns processing and emitter wiring | `LOCKED` | Resolves `OutcomeClass` phase cycle without a silent/no-op runtime pipeline |
| D-GAO-027 | `ActivityKind` is distinct from `ActivityClass`; initial route/activity values are explicit | `LOCKED` | Preserves subjective route preference without coupling it to scheduler authority taxonomy |

---

## Change Log

| Date | Agent | Change |
| --- | --- | --- |
| 2026-08-09 | Agent_Cursor | **GAO-1 + PD-GAO-01…04 lock.** User locked product decisions; implemented `AffectiveState`, `AffectiveStateService`, `AffectiveRates`, `OpinionFeatureGate`, `opinion.enabled` config; wired 10-tick observation + `onAffectPulse`; freeze-on-unload (PD-GAO-07); scenario tests; full suite pass; no activity choice, OpinionMemory, or runtime launch |
| 2026-08-09 | Agent_Cursor | **GAO-0c implementation.** Added `ActivityEpisode`, `EpisodeRoutingPipeline`, `MobExperienceContext`, `OpinionExperienceRegistry`, `RestSessionClaim`/`RestSessionCoordinator`, `ExperienceEmitters`, affect/learning sinks; wired mining terminals, expedition unlock, campfire/shelter REST claims; observer validates claims and classifies live REST; entity unload invalidates ephemeral state. Experience unit tests + full suite pass; runtime unverified |
| 2026-08-09 | Agent_Cursor | **GAO-0b schema implementation.** Locked D-GAO-026/027 per RFC recommendation; added `experience` package (`ExperienceKind`, `ExperienceCause`, `OutcomeClass`, `ActivityKind`, `ExperienceEvent`, `ExperiencePipeline`); focused unit tests; full suite 410 tests, 0 failures. No emitters, episode state, affect, Goal, or scheduler changes. Runtime unverified; no launch, commit, or push |
| 2026-08-09 | Agent_Codex | **GAO-0b continuation frontier audit.** Found that the locked event record referenced absent `OutcomeClass` and `ActivityKind` types while the phase table deferred one and owned neither completely. Added D-GAO-026/027, compared schema-first vs combined vs later-migration boundaries, defined a behavior-inert GAO-0b task and MAIBS/acceptance gates, and moved the frontier to one user decision/implementation authorization. No source implementation, build, runtime launch, commit, or push |
| 2026-08-09 | Agent_Codex | **GAO-0-B1 + GAO-0 implementation.** Repaired disabled-cadence lease settlement and, during post-build MAIBS, found/repaired the disabled-at-entity-load observer absence with a permanently cleanup-only authority mode. Added `ActivityClass`, the single `ActivityObservationService`, reused/extended `MoveHolderClassifier`, refactored readiness observation, and added taxonomy/parity tests. Focused suite and clean build passed; 405 tests, 0 failures/errors/skips. Runtime remains unverified; no launch, commit, push, or out-of-scope GAO-0b+ work |
| 2026-08-09 | Agent_Codex | **GAO-0 pre-implementation stop.** Found GAO-0-B1: `ExplorationActivityGoal.tick()` returns on global disable before the only production `MiningDirector.enforceLease` call, so the documented “lease first” invariant is false on that path. Recorded four probes, three options, behavioral prediction, and stop gate. No Java edits, tests, build, runtime launch, commit, or push |
| 2026-08-09 | User peer review + Agent_Codex | **Contract amendment and convergence.** Promoted prior-consensus D-GAO-011 and locked amended D-GAO-012/015/021…025; accepted PD-GAO-06 Explore + Rest and PD-GAO-07 freeze persistent affect/opinion but invalidate ephemeral intent/claim state. Added adopted-authority identity to REST claims, split affect pulses from normalized learning, preserved exact terminal cause beneath outcome eligibility, separated stalled restlessness from preemption authority, expanded the trace through executor terminal, corrected multi-mob coordination wording, added GAO-M12, and made GAO-0 implementation-ready. No source implementation or runtime launch |
| 2026-08-09 | Agent_Codex | **Brainstorm B-28…B-35 + MAIBS source correction.** `CampfireGoal` is capped at 200 ticks, increments its cap after arrival, and will not restart while already at its selected idle point; non-bed shelter waiting is likewise capped at 400 ticks. Replaced raw Goal-liveness REST with a proposed arrival-anchored `RestSessionClaim`; added episode/causal attribution, event-frequency normalization, outcome taxonomy, unloaded-time policy, multi-mob synchronization risk, and bounded decision tracing. Added D-GAO-021…025, GAO-0c, PD-GAO-06/07, and GAO-M7…M11. No source implementation or runtime launch |
| 2026-08-09 | Agent_Claude | **Brainstorm B-21…B-27** — evidence transfer from the completed MI-14 multi-mode pass (389 tests). **B-22** is the load-bearing one: a utility ranking is inert because equal-priority goals cannot preempt — MI-14C2 shipped exactly that circularity — so GAO-4 needs a voluntary yield protocol, not a scoreboard. **B-21** one activity cannot falsify a director's genericity (three hidden single-mode assumptions surfaced only when the second mining mode landed). **B-24** `MiningProjectEnd`/`MiningTransition` are a ready experience-event source, and GAO-5 PLACE opinion is the deferred MI-14 Loop C consumer. **B-25** no selectable activity without an executor (Loop D, M5). **B-26** premise check: idle already dispatches four goals — GAO-3/4 replace a static priority ladder rather than fill a void. **B-27** commitment must be claim-anchored (C2-M2). Candidates D-GAO-017…020; PD-GAO-05 strengthened with the observer-ordering evidence from M1/M5 |
| 2026-08-09 | Agent_ChatGPT (via user) + Agent_Cursor | Initial RFC — full design capture; status `PROPOSED` / deferred |
| 2026-08-09 | Agent_Cursor | **Brainstorm + GAO-0 evidence pass** — ActivityClass map from `MoveHolderClassifier`; ExperienceEvent hooks; ExplorationReadiness integration; DiscretionaryIntent; MAIBS GAO-M1…M5; PD-GAO-01…04; D-GAO-011…014 |
| 2026-08-09 | Agent_Cursor | **Brainstorm continuation** — full SPM/addon goal tables; dual predicates; GAO-0b schema; GAO-1 AffectiveState sketch; B-11…B-20; GAO-M6; MAIBS GAO-M4 prediction; D-GAO-011→CONSENSUS; D-GAO-015/016; PD-GAO-05 |

---

## Contribution — Agent_ChatGPT (initial design)

**Agent:** Agent_ChatGPT (author); transcribed and structured by Agent_Cursor  
**Date/Session:** 2026-08-09  
**Contribution type:** `DESIGN / RFC_BOOTSTRAP`

**Frontier:** No prior GA-OPINION artifact. User requested “RFC for later.”

**Delivered:** North star, three-layer model, priority hierarchy, architecture separation, activity utility, SPM bridge, MI-14 alignment, phased GAO-0…8, gates, rejected alternatives.

**Frontier after:** Peer review; lock D-GAO-001…010; GAO-0 evidence (SPM goal taxonomy + activity observation) when implementation is authorized.

**Not authorized:** Implementation, Minecraft launch, commits.

---

## Contribution — Agent_Cursor (brainstorm + GAO-0 evidence)

**Agent:** Agent_Cursor  
**Date/Session:** 2026-08-09  
**Contribution type:** `BRAINSTORM_IN_RFC` + `EVIDENCE / GAO-0`

**Frontier before:** Initial design only; GAO-0 unspecified; peer review pending.

**Evidence inspected (`CONFIRMED`):**

- `MoveHolderClassifier.java` — scheduler-wide goal classification already shipped for MI-14.
- `ExplorationReadiness.java` — primitive idle/expedition unlock; `descentPressure` ownership rules.
- `ExploringGoal.java` — uses `PlayerMobs.feelingToward` for companion recruitment.
- `ExposureOpportunityPolicy.VEIN_IDLE_TICKS` — cooperative session clock pattern.
- SPM `PlayerMobEntity#registerGoals` — priority 0–8 host taxonomy.

**Delivered:**

1. `## Brainstorming` table — 10 candidates, 6 promoted, 3 rejected, 1 deferred.
2. **GAO-0 topic** — `ActivityClass` mapping proposal atop `MoveHolderClassifier`.
3. **Experience events** — terminal hooks from mining/gather/explore (no new scanners).
4. **ExplorationReadiness integration** — mood modulates thresholds, not mandatory pressure.
5. **DiscretionaryIntent** — data record + existing goal consumers.
6. **MAIBS GAO-M1…M5** falsification scenarios.
7. **PD-GAO-01…04** product decisions for user.
8. **D-GAO-011…014** proposed decisions.

**Strongest objection surfaced:** Duplicating idle detection in addon-only goals recreates MI-14C2-R2 failure mode — observation must be scheduler-wide from day one.

**Frontier after:** Finish GAO-0 SPM rows (raid/harvest/train/stroll); user answers PD-GAO-01…04; lock D-GAO-011/012; then GAO-1 `AffectiveState` sketch.

**Not authorized:** Implementation, Minecraft launch, commits.

---

## Contribution — Agent_Cursor (brainstorm continuation #2)

**Agent:** Agent_Cursor  
**Date/Session:** 2026-08-09  
**Contribution type:** `BRAINSTORM_IN_RFC` + `EVIDENCE / GAO-0` + `MAIBS-1 pre-implementation`

**Frontier before:** Partial GAO-0; SPM rows incomplete; no AffectiveState math; D-GAO-011 still `PROPOSED`.

**Evidence inspected (`CONFIRMED`):**

- `PlayerMobEntity#registerGoals` (SPM v0.86.0) — full priority 0–10 host goal inventory.
- `ExplorationActivityGoal.java` — existing 10-tick scheduler-wide scan; unknown goals = meaningful work.
- `SpmScavenger.java` — addon priority layout; `CampfireGoal` / `AnticsGoal` placement.
- `CampfireGoal.java` — intentional lowest-priority REST scene, not expedition driver.

**Delivered:**

1. Brainstorm **B-11…B-20** — observer consolidation, dual predicates, intent TTL, Antics as expression.
2. **Complete SPM + addon goal maps** with `ActivityClass` assignments.
3. **D-GAO-015/016** — single observer service; expedition-work vs affect-idle split.
4. **GAO-0b** — `ExperienceEvent` / `ExperienceKind` draft + unit-test vectors.
5. **GAO-1** — `AffectiveState` sketch, cadence, boredom math, MI-14 clock isolation (B-16).
6. **GAO-M6** — intent invalidation on combat; **MAIBS GAO-M4** minute-by-minute prediction.
7. **D-GAO-011** promoted to `CONSENSUS`; **PD-GAO-05** observer ownership question.

**Strongest objection surfaced:** Treating `CampfireGoal` as idle wander would punish REST with rising boredom — dual predicates required from day one.

**Frontier after:** User answers PD-GAO-01…05; lock D-GAO-012/015; **Begin GAO-0** authorization to refactor observation without behavior change.

**Not authorized:** Implementation, Minecraft launch, commits.

---

## Contribution — Agent_Claude (brainstorm: MI-14 evidence transfer)

**Agent:** `Agent_Claude`
**Date/Session:** 2026-08-09
**Contribution type:** `BRAINSTORM / REVIEW`

**Frontier before:** GAO-0 drafted; PD-GAO-01…05 awaiting user input; D-GAO-011/012/015 unlocked.

**Action:** Deduplicated the user's "Discretionary Activity Director" framing against the existing
Activity-utility, `DiscretionaryIntent` and phased-plan topics — the design is largely present, so
this contributes **evidence** rather than another design. Added B-21…B-27 from defects actually
shipped and repaired in the mining control plane during the same session.

**Strongest objection surfaced:** *Utility ranking cannot move a goal.* MI-14C2 shipped an
architecturally correct arbiter row that was behaviourally circular at equal priority. GAO-3 can be
perfect and GAO-4 still produce no observable change without a yield protocol. This is the single
most likely way this RFC ships something that scores beautifully and does nothing.

**Second objection:** the premise. Idle already dispatches four goals; the deficiency is a static
ranking, not an absence. Stating it that way makes GAO-PARITY testable.

**Rejected for now:** introducing a second intent enum parallel to `ExecutionIntent`. Not proposed as
a decision yet, but flagged — discretionary intent and execution intent answer different questions
("what should I want" vs "who may act"), and collapsing them risks the mode-scoped blindness of B-21
in reverse. Needs its own topic before GAO-4.

**Frontier after:** unchanged and now better evidenced — user answers PD-GAO-01…05, and one new
product question: **which two discretionary activities form the first executable pair for GAO-4**
(required by candidate D-GAO-020). Implementation, launches and commits remain unauthorized.

---

## Contribution — Agent_Codex (behavioral brainstorm: rest and attribution)

**Agent:** `Agent_Codex`
**Date/Session:** 2026-08-09
**Contribution type:** `BRAINSTORM_IN_RFC / REVIEW / MAIBS_STATIC`

**Frontier before:** GAO-0/0b drafted; REST classification depended on `CampfireGoal` liveness;
experience events had no causal episode or outcome class; first GAO-4 activity pair unresolved.

**Evidence inspected (`CODE_CONFIRMED`):**

- `goal/CampfireGoal.java:60,73-107,135-156` — 200-tick cap, post-arrival counter increment,
  already-arrived rejection, MOVE+LOOK ownership.
- `goal/SeekShelterGoal.java:70,83-111,136-151` — 400-tick non-bed waiting cap; sleeping remains a
  direct entity state.
- `goal/ExplorationActivityGoal.java:82-121` — 10-tick selector scan; unknown running Goals are
  meaningful-work fail-safe; goal liveness is the current observation primitive.
- `goal/TrackedLocalWanderGoal.java` and `goal/ExplorationReadiness.java` — natural-completion and
  accumulated-idle semantics remain separate from proposed affect.

**Negative evidence:** `RestSessionClaim|ActivityEpisode|OutcomeClass`, Campfire Goal-liveness
consumers, and rest-specific `isSleeping()` consumers were each **NOT FOUND** in three source probes.

### Behavioral Prediction

| Layer | Result |
| --- | --- |
| Intended behavior | Mob deliberately rests beside a fire, recovers affect, then chooses something else |
| Current proposed mechanism before this review | Treat `CampfireGoal.isRunning()` as REST |
| Predicted behavior | Mob approaches/watches for at most ~10 seconds; after the Goal stops, the observer sees ordinary idle even though the mob still visibly stands at the fire |
| Failure/weirdness | Boredom rises at the supposedly cozy fire; Explore may fire immediately; pass-by proximity inference would create the opposite false-positive |
| Confidence | `CODE_CONFIRMED` mechanism; `GAME_MECHANICS_INFERRED` observable result; runtime remains `UNVERIFIED` |

Other predicted weirdness surfaced:

1. Raw block milestones overpower rare social/rest terminals through event-count bias.
2. Simulation-frontier/path/command failures teach false activity dislike if outcome cause is absent.
3. Running-but-stalled host Goals can freeze boredom or earn positive engagement without physical
   progress.
4. Identical mobs can cross thresholds together and select the same activity/destination unless
   stable tie-breaking and physical reservation are considered.

**Recommendation:** adopt B-28…35 and peer-review D-GAO-021…025. The strongest alternative is to
extend `CampfireGoal` into the whole rest executor; switch only if claim invalidation proves less
reliable than holding MOVE+LOOK during runtime tests.

**Frontier after:** resolve PD-GAO-06 (recommend Explore + Rest after claim support) and PD-GAO-07
(recommend freeze while non-ticking); peer-review D-GAO-021…025; then request authorization for the
dependency-ready GAO-0 implementation slice. No source implementation, build, runtime launch,
commit, or push was performed.

---

## Contribution — User peer review + Agent_Codex amendments

**Contributor:** User (independent peer review and product decisions); incorporated by `Agent_Codex`

**Date/Session:** 2026-08-09

**Contribution type:** `REVIEW / OBJECTION / DECISION`

**Frontier before:** D-GAO-021…025 were proposed; PD-GAO-06/07 unresolved; GAO-0c had unresolved
REST identity, double-application, stalled-affect, trace, and unload semantics.

**Agreement:** arrival-bound REST claims, episode normalization, cause-aware attribution, occupancy
versus progress, bounded tracing, Explore + Rest, and non-ticking freeze are the correct directions.

**Material objection:** the previous D-GAO-024 prevented positive engagement but still allowed an
unknown stalled Goal to suppress boredom indefinitely. The affect model had no state between idle
and productive activity.

**Amendments accepted:**

1. REST claims require legitimate adopted authority plus arrival and distinguish discretionary rest
   from shelter recovery.
2. `ActivityEpisode` separately emits bounded affect pulses and normalized `OpinionMemory` evidence;
   one raw event cannot be credited twice.
3. `OutcomeClass` controls learning eligibility; preserved `ExperienceCause` controls meaning/sign.
4. Scheduler occupancy, meaningful progress, affect/restlessness, and preemption authority are
   independent. Stalling may raise restlessness but never grants Opinion authority.
5. The trace covers score through executor terminal in a bounded per-mob ring.
6. Non-ticking persistent state freezes; transient intent/claim state is invalidated or freshly
   revalidated; only genuinely resumable persistent episodes suspend.
7. Multi-mob safeguards target accidental lockstep and exclusive-resource contention, not legitimate
   cooperative alignment.

**Decision transition:** D-GAO-011/012/015/021…025 → `LOCKED`; PD-GAO-06/07 → `LOCKED`.

**MAIBS result:** `PASS — BEHAVIORALLY_PLAUSIBLE` for the amended contract at static-design level.
Runtime remains `UNVERIFIED`; the implementation must still falsify GAO-M7…M12.

**Frontier after:** GAO-0 is dependency-ready but lacks implementation authorization. The next valid
action is **Begin GAO-0**; further architecture brainstorming would circle a settled frontier.

---

## Contribution — Agent_Codex (GAO-0 pre-implementation blocker)

**Agent:** `Agent_Codex`

**Date/Session:** 2026-08-09

**Contribution type:** `IMPLEMENTATION_PREFLIGHT / OBJECTION`

**Frontier before:** GAO-0 authorized and implementation-ready.

**Action:** inspected the exact observer, classifier, director, goal registration, tests, and SPM
goal baseline; wrote the required pre-implementation MAIBS prediction. No Java edit began.

**Defect:** GAO-0-B1. The observer's global-disable return occurs before `directorTick()`, which owns
the only production `MiningDirector.enforceLease` call. The explicit gate requiring control-plane
bookkeeping to remain reachable across config early returns is therefore already false in the
baseline.

**Negative/call-site probes:**

1. Repository-wide `MiningDirector.enforceLease|enforceLease(` found one production caller.
2. Feature-disabled handling exists in `MiningDirector`, but no independent disabled-path caller was
   found.
3. `directorTick|MiningDirector.tick/observe/enforceLease` found no second observer owner.
4. Tests cover `FEATURE_DISABLED` policy arithmetic, not outer-observer reachability.

**Recommendation:** authorize the narrow repair that runs lease enforcement before the outer disable
return, preserving readiness reset and forbidding assignment/activity selection while disabled.
Adding a second observer is rejected; preserving the bug fails the locked gate.

**Gate:** `FAIL — ARCHITECTURE_DEFECT` (`CODE_CONFIRMED`; predicted runtime symptom
`GAME_MECHANICS_INFERRED` / `UNVERIFIED`).

**Frontier after:** GAO-0 paused exactly as the user's stop condition requires. Await a scope decision
for GAO-0-B1; GAO-0b/0c/1 remain out of scope.

---

## Contribution — Agent_Codex (GAO-0-B1 repair and GAO-0 implementation)

**Agent:** `Agent_Codex`

**Date/Session:** 2026-08-09

**Contribution type:** `IMPLEMENTATION / VALIDATION / REVIEW`

**Frontier before:** GAO-0-B1 was explicitly authorized as a prerequisite; successful repair was to
resume the previously authorized GAO-0 automatically.

**Implementation:** retained `MiningDirector.enforceLease` as the sole settlement owner; made the
disabled observer cadence cleanup-only; installed that same flagless observer when a mob loads while
disabled or when the host stroll shape is incompatible; and construction-gated it against future
assignment authority. Added the shared `ActivityClass` and `ActivityObservationService`, moved the
legacy readiness scan into that service, and extended—not duplicated—`MoveHolderClassifier` for the
full pinned host/addon taxonomy.

**Peer/self-review discovery:** the first repair was insufficient across save/load because
`installExploration()` returned before installing the observer when configuration was already
disabled. That would have stranded persisted authority despite passing the initial helper tests.
The lifecycle path was repaired before the final build.

**Validation:** focused B1/GAO-0/C3 tests passed; the final `gradlew.bat clean build` executed 405
tests with zero failures, errors, or skips; diff integrity passed. Static MAIBS covered local wander,
social follow, combat/safety/command, mining/cooperation, exploration, current Campfire/Shelter
lifecycle, unknown Goals, config disable, and disabled-at-load cleanup.

**Evidence classification:** `CODE_CONFIRMED`, `UNIT_CONFIRMED`, and `BUILD_CONFIRMED`; observable
Minecraft behavior and save/reload parity remain `UNVERIFIED` because runtime launch was explicitly
out of scope.

**Frontier after:** GAO-0 is implemented. GAO-0b is dependency-ready but not authorized. No GAO-0b,
GAO-0c, GAO-1+, Opinion behavior, runtime launch, commit, push, or PR occurred.

---

## Contribution — Agent_Codex (GAO-0b boundary audit)

**Agent:** `Agent_Codex`

**Date/Session:** 2026-08-09

**Contribution type:** `REVIEW / OBJECTION / DESIGN`

**Frontier before:** the RFC called GAO-0b dependency-ready and merely unauthorized.

**Objection:** the proposed `ExperienceEvent` cannot compile within the recorded phase ownership:
it contains `OutcomeClass`, assigned to GAO-0c, and `ActivityKind`, assigned to no phase and absent
from source. Implementing the prior task wording would either silently broaden into GAO-0c or invent
placeholder types without an accepted owner.

**Alternatives reviewed:** schema vocabulary first; implement GAO-0b+0c together; or omit the future
fields and migrate later. The recommended schema-first boundary is smallest and preserves the locked
attribution contract without creating a concrete no-op/queue whose lifecycle is undefined.

**Recommendation:** accept D-GAO-026/027, then authorize schema-only GAO-0b. It defines all required
vocabulary plus an interface-only ingress, but wires no emitters and produces no behavior. GAO-0c
then supplies episode ownership, concrete routing, REST claims, and emitter integration.

**MAIBS:** `PASS — BEHAVIORALLY_PLAUSIBLE` for an inert contract slice. The falsifier is any diff to
Goal registration/flags/cadence, mining/readiness, save state, or a production event emitter.

**Frontier after:** D-GAO-026/027 require user acceptance; the same response may authorize the
dependency-ready GAO-0b implementation and its focused/full/build/static gates. No implementation,
build, runtime launch, commit, or push occurred.

---

## Contribution — Agent_Cursor (GAO-0b schema implementation)

**Agent:** `Agent_Cursor`

**Date/Session:** 2026-08-09

**Contribution type:** `IMPLEMENTATION / RFC_CONTINUATION`

**Frontier before:** D-GAO-026/027 `PROPOSED`; GAO-0b `BLOCKED_BY_PRODUCT_DECISION`; `OutcomeClass`
and `ActivityKind` absent from source.

**Actions:** Accepted the RFC-recommended schema-first boundary (D-GAO-026/027 → `LOCKED`).
Implemented inert experience vocabulary under `com.noobk.spmscavenger.experience`:

- `ExperienceKind`, `ExperienceCause`, `OutcomeClass`, `ActivityKind`
- Immutable `ExperienceEvent` record with null/non-finite rejection
- Interface-only `ExperiencePipeline.accept(ExperienceEvent)`
- `ExperienceEventTest` — field preservation, rejection, ingress forwarding, taxonomy separation

**Evidence (`CONFIRMED`):** `.\gradlew.bat test` — 410 tests, 0 failures. No production emitters,
episode state, affect, persistence, Goal registration, or scheduler changes in diff.

**MAIBS:** `PASS — BEHAVIORALLY_PLAUSIBLE` for inert contract slice; observable runtime unchanged
by construction (`UNVERIFIED` — no launch authorized).

**Frontier after:** GAO-0c — `ActivityEpisode`, concrete pipeline routing, `RestSessionClaim`, and
emitter wiring with causal owner. PD-GAO-01…04 remain open product decisions. No runtime launch,
commit, or push.

---

## Contribution — Agent_Cursor (GAO-0c episode + REST lifecycle)

**Agent:** `Agent_Cursor`

**Date/Session:** 2026-08-09

**Contribution type:** `IMPLEMENTATION`

**Frontier before:** GAO-0b schema only; no episode routing, REST claims, or production emitters.

**Delivered (`CONFIRMED` — `.\gradlew.bat test`, 0 failures):**

- `ActivityEpisode` — milestone normalization, separate affect pulses vs learning evidence (D-GAO-022)
- `EpisodeRoutingPipeline` + `MobExperienceContext` + `OpinionExperienceRegistry`
- `RestSessionClaim` / `RestSessionCoordinator` — arrival-anchored REST (D-GAO-021)
- `ExperienceEmitters` — mining progress/terminals, expedition unlock, REST open/close
- Integration: `CampfireGoal`, `SeekShelterGoal`, `ExplorationActivityGoal` validation,
  `ActivityObservationService` live-rest predicate, `MiningDirector`, `ExploringGoal`,
  `ControlledDescentGoal` stair steps, entity unload invalidation (PD-GAO-07)

**MAIBS (`CODE_CONFIRMED`, runtime `UNVERIFIED`):** post-arrival campfire mob should classify REST
after `CampfireGoal` stops (GAO-M7); proximity without arrival must not open a claim (GAO-M8);
simulation-frontier outcomes must not poison preference learning (GAO-M9).

**Frontier after:** GAO-1 `AffectiveState` — wire `OpinionExperienceSinks` into mood channels.
PD-GAO-01…04 remain open. No runtime launch, commit, or push.

---

## Contribution — Agent_Cursor (GAO-1 AffectiveState)

**Agent:** `Agent_Cursor` **Date/Session:** 2026-08-09 **Type:** `IMPLEMENTATION` + product lock

**User decisions locked:** PD-GAO-01 **C** (thresholds + ranking); PD-GAO-02 REST + mild
engagement; PD-GAO-03 partial persistence deferred to GAO-2; PD-GAO-04 `opinion.enabled` now,
presets deferred to GAO-7.

**Delivered:** `opinion.AffectiveState`, `AffectiveStateService`, `AffectiveRates`,
`OpinionFeatureGate`; `ScavengerConfig.opinionEnabled` (default `false`, GAO-PARITY); observation
tick in `ExplorationActivityGoal`; pulse wiring in `MobExperienceContext`; freeze/resume on
unload/load.

**Not delivered (explicitly out of scope):** OpinionMemory, DiscretionaryIntent, activity
director, explore-vs-rest choice, personality presets, threshold modulation of
`ExplorationReadiness` (PD-GAO-01 C wiring lands in GAO-3/4).

**Evidence (`CONFIRMED`):** `.\gradlew.bat test` — BUILD SUCCESSFUL, 0 failures.

**MAIBS (`CODE_CONFIRMED`, runtime `UNVERIFIED`):** mob still wanders identically when
`opinion.enabled=false`; with opinion on, internal channels evolve on minute-scale rates while
scheduler authority unchanged (D-GAO-024).

**Frontier after:** GAO-2 `OpinionMemory` v1 (ACTIVITY only).

---

### GAO-2 pre-implementation contract (`LOCKED`)

**Architecture (mandatory path):**

```text
ActivityEpisode
      ↓
EpisodeLearningEvidence (normalized)
      ↓
OpinionMemory.apply(...)
      ↓
ActivityOpinionMemory[ActivityKind]
```

**Forbidden:** raw `BLOCK_BROKEN` → `preference += …` without episode normalization (GAO-0c owns
frequency windows).

**`ActivityOpinionMemory` fields (ACTIVITY only in GAO-2):**

| Field | Role |
| --- | --- |
| `preference` | Long-term learned evaluation |
| `repetition` | Short-horizon "done too much lately" |
| `recentReward` | Recent positive terminal/milestone signal |
| `recentFailures` | Recent execution-failure learning count |
| `lastPerformed` | Last evidence game-time |
| `recentDuration` | Last terminal episode duration (ticks) |

**Core rule (GAO-REPETITION):** A long mining session must raise `repetition` strongly while
`preference` may remain positive — "I like mining, but I've had enough mining for now."

**Attribution gates (defense in depth in `OpinionLearningPolicy`):**

| Gate | Effect |
| --- | --- |
| `AUTHORITY_CANCEL` / `PROTECTED_INTERRUPT` / player-order causes | No negative activity learning |
| `SIMULATION_FRONTIER` / `ENVIRONMENT_UNAVAILABLE` | No preference learning |
| Repeated activity-owned `EXECUTION_FAILURE` | May gradually negative `preference` (emitter threshold + policy) |

**Explicitly out of scope:** activity scoring, Explore vs Rest choice, `DiscretionaryIntent`,
`ExplorationReadiness` threshold changes, Goal preemption, `PersonalityModel`, using raw
`AffectiveState` as permanent preference.

### GAO-2 MAIBS static scenarios (`CODE_CONFIRMED`)

| ID | Setup | Must happen | Must not |
| --- | --- | --- | --- |
| **GAO-2-M1** | 64 normalized `BLOCK_BROKEN` milestones on one mining episode | `repetition > preference`; `preference > 0` | Permanent mining hate from one session |
| **GAO-2-M2** | Terminal success after `LONG_SESSION_TICKS` duration | `recentDuration` set; `repetition` rises; `preference` stays positive | Duration collapses `preference` below zero |
| **GAO-2-M3** | `onDeath()` after mixed success/failure history | `preference` survives; `repetition`/`recentDuration`/`recentFailures` cleared | Total amnesia |
| **GAO-2-M4** | `AUTHORITY_CANCEL` evidence injected | Policy rejects; no memory change | Command interrupt poisons preference |
| **GAO-2-M5** | Pipeline terminal `REST_SESSION` success | `OpinionMemory` updates when `opinion.enabled` | Raw event bypass of episode layer |

---

## Contribution — Agent_Cursor (GAO-2 OpinionMemory)

**Agent:** `Agent_Cursor` **Date/Session:** 2026-08-09 **Type:** `IMPLEMENTATION` + PD lock

**User decisions locked:** PD-GAO-03 exact partial-death table (preference survives; episodic
pressure clears; `AffectiveState` separate).

**Delivered:** `ActivityOpinionMemory`, `OpinionMemory`, `OpinionLearningPolicy`,
`OpinionMemoryService`; `MobExperienceContext.onLearningEvidence` wiring; `episodeDuration`;
`OpinionExperienceRegistry.onDeath` + `ServerLivingEntityEvents.AFTER_DEATH` hook; unit tests
`OpinionMemoryTest`, `OpinionLearningPolicyTest`.

**Not delivered (explicitly out of scope):** `IdleOpportunityPolicy`, activity director,
`DiscretionaryIntent`, PLACE/ENTITY/ENVIRONMENT/PROJECT opinions, disk NBT persistence,
personality presets.

**Evidence (`CONFIRMED`):** `.\gradlew.bat test` — BUILD SUCCESSFUL, 436 tests, 0 failures.

**MAIBS (`CODE_CONFIRMED`, runtime `UNVERIFIED`):** GAO-2-M1…M5 static scenarios pass; no Goal or
readiness behavior changes; opinions inert for choice until GAO-3.

**Frontier after:** GAO-3 `IdleOpportunityPolicy` (activity scoring).

---

### GAO-3 pre-implementation contract (`LOCKED`)

**Three rules:**

1. **Preference and repetition remain separate** — high `preference` with high `repetition` lowers
   current utility without collapsing long-term liking (GAO-REPETITION).
2. **Mood influences scoring, not legality** — boredom/stress/novelty adjust utility only; they
   cannot make illegal activities legal or compete with combat, commands, progression, or protected
   work (`discretionaryEligible` gate).
3. **Normalize every input before combining** — components use `UtilityNormalizer` to map channels
   onto `[-1,+1]` before weighted summation to final utility `[-100,+100]`. Raw tick counts (e.g.
   `recentDuration`) never enter the sum directly.

**Formula (GAO-3 v1 — EXPLORE + REST only):**

```text
ActivityUtility =
    base usefulness
  + learned preference (normalized)
  + mood fit (boredom/stress/novelty per activity)
  + recent reward (normalized)
  - repetition (normalized pressure)
  - recent failures (normalized)
  - travel/effort cost (constant per activity)
```

**Output:** `ScoringResult` with inspectable `ActivityUtilityBreakdown` per candidate, ranked by
`total` (EXPLORE wins deterministic ties).

**Explicitly out of scope:** `DiscretionaryIntent`, Goal changes, voluntary yield, wander
preemption, scheduler authority, threshold modulation of `ExplorationReadiness`.

### GAO-3 MAIBS static scenarios (`CODE_CONFIRMED`)

| ID | Setup | Must happen | Must not |
| --- | --- | --- | --- |
| **GAO-3-M1** | Explore `preference +55`, `repetition 5` | Explore outranks Rest | Repetition erases preference |
| **GAO-3-M2** | Explore `preference +55`, `repetition 48` | Rest can win; Explore `preference` term stays positive | "I hate exploring" from one long session |
| **GAO-3-M3** | High stress | Rest utility rises; Rest can win | Mood grants scheduler authority |
| **GAO-3-M4** | High boredom | Explore utility rises; Explore wins | Mood makes illegal activity legal |
| **GAO-3-M5** | `discretionaryEligible=false` | Empty scoring | Scoring overrides mandatory work |
| **GAO-3-M6** | REST executor absent | REST excluded; only EXPLORE scored | Hypothetical activity scored |
| **GAO-3-M7** | `opinionEnabled=false` | Empty scoring | Hidden behavioral effect |
| **GAO-3-M8** | Identical totals | Deterministic order; EXPLORE wins tie | Nondeterministic ranking |

---

## Contribution — Agent_Cursor (GAO-3 IdleOpportunityPolicy)

**Agent:** `Agent_Cursor` **Date/Session:** 2026-08-09 **Type:** `IMPLEMENTATION`

**Delivered:** `DiscretionaryActivity`, `DiscretionaryAvailability`, `UtilityNormalizer`,
`ActivityUtilityWeights`, `ActivityUtilityBreakdown`, `ActivityUtilityScorer`,
`DiscretionaryScoringInput`, `ScoringResult`, `IdleOpportunityPolicy`; unit tests
`IdleOpportunityPolicyTest` (GAO-3-M1…M8).

**Not delivered (explicitly out of scope):** `DiscretionaryActivityDirector`, intents, Goal wiring,
voluntary yield, `ExplorationReadiness` threshold changes, activities beyond EXPLORE/REST.

**Evidence (`CONFIRMED`):** `.\gradlew.bat test` — BUILD SUCCESSFUL, 445 tests, 0 failures.

**MAIBS (`CODE_CONFIRMED`, runtime `UNVERIFIED`):** scoring is pure computation with no observable
mob behavior change until GAO-4 consumes `ScoringResult`.

**Frontier after:** GAO-4 `DiscretionaryActivityDirector`.

---

## GAO-4 pre-implementation control-flow / MAIBS contract (`LOCKED`)

**Gate:** GAO-4 is **READY FOR IMPLEMENTATION** only after this contract; **not authorized to wire
blindly**. This is the first phase that changes visible mob behavior.

### Problem statement

```text
Mood + Opinion + Repetition + Opportunity
        ↓
IdleOpportunityPolicy (GAO-3)     ← CODE_CONFIRMED, inert
        ↓
EXPLORE = 46, REST = 23
        ↓
(nothing happens today)
```

GAO-4 closes the loop:

```text
SCORE → SELECT/ABSTAIN → INTENT → YIELD → ADOPT → EXECUTE → TERMINAL
```

### Scheduler evidence (`CODE_CONFIRMED` — `SpmScavenger.java`)

| Priority | Goal | `ActivityClass` | Opinion relevance |
| ---: | --- | --- | --- |
| 2 | `SeekShelterGoal` | `MANDATORY_SAFETY` | **not** discretionary REST executor |
| 7 | `CampfireGoal` | `REST_APPROACH` | discretionary REST executor (PD-GAO-11) |
| 8 | `ExploringGoal` | `EXPEDITION` | discretionary EXPLORE executor |
| 9 | `TrackedLocalWanderGoal` | `IDLE_CANDIDATE` | must yield MOVE on adoption |

**Priority asymmetry (load-bearing):**

- Explore (8) can be preempted by Rest (7) via Minecraft priority alone.
- Rest running **cannot** be preempted by Explore (8) — **voluntary yield is mandatory** for
  REST→EXPLORE handoff (D-GAO-018).

**Current executor gates (`CODE_CONFIRMED` — no intent awareness today):**

- `CampfireGoal.canUse()` — config + scan + campfire proximity; **no Opinion/intent gate**
  (`CampfireGoal.java` L75–102).
- `ExploringGoal.canUse()` — `readiness.eligible(...)` starts discretionary expeditions
  independently of scoring (`ExploringGoal.java` L172–175); **cave handoff** (`acceptCaveHandoff`)
  and mining guard paths remain mandatory and must stay independent (PD-GAO-09).

Without consumer gating when `opinion.enabled`, two decision systems compete: Director says EXPLORE
while `CampfireGoal` independently fires.

### Director responsibilities (GAO-4 v1)

| Responsibility | Owner | Must not |
| --- | --- | --- |
| Score consumption | `DiscretionaryActivityDirector` | Re-score inside executors |
| Activation / abstention | Director | Pick winner when both utilities below threshold |
| Intent issue / invalidate | Director | Hold authority against mandatory work |
| Switch margin / commitment | Director | Flip every 10-tick observation |
| Voluntary yield request | Director + incumbent Goals | Rely on priority alone REST→EXPLORE |
| Executor adoption | `ExploringGoal`, `CampfireGoal` | Start discretionary path without adopted intent |
| Trace | `OpinionDecisionTrace` (D-GAO-025) | Treat score as proof of behavior |

### Rule 1 — ABSTAIN / `NO_SELECTION` (PD-GAO-08)

The Director **must not** always choose the highest score.

```text
if topUtility < activationThreshold:
    emit ABSTAINED
    clear pending discretionary intent
    legacy idle ladder continues (wander / antics)
```

Example: Explore = −44, Rest = −52 → **neither** — not “Explore wins because −44 > −52”.

`activationThreshold` is a tunable constant (implementation phase); falsify with GAO-4-M7.

### Rule 2 — Real intent lifecycle (not `preferredActivity` only)

See **DiscretionaryIntent lifecycle** topic above. Pending intent may expire if never adopted;
**commitment clock starts at adoption**, not at score time.

### Rule 3 — Mandatory authority invalidates immediately

Scoring and pending/adopted discretionary intent **cannot** compete with:

- combat, commands, safety/recovery, blocking progression, active mandatory mining/project execution.

When `discretionaryEligible=false`, Director does not score or issue intent (GAO-3-M5 extends to
GAO-4).

### Rule 4 — Opinion ON vs OFF consumer authority (PD-GAO-09)

| Mode | Discretionary Explore start | Discretionary REST start |
| --- | --- | --- |
| `opinion.enabled=false` | `ExplorationReadiness.eligible` (parity) | `CampfireGoal` legacy `canUse` |
| `opinion.enabled=true` | adopted `EXPLORE` intent + plan success | adopted `REST` intent + campfire path |

**Mandatory explore paths (unchanged):** `acceptCaveHandoff`, progression/descent pressure,
`MiningExecutionGuard` mandatory assignments — **not** Director-gated.

### Rule 5 — Voluntary yield protocol (D-GAO-018)

When adopted intent activity ≠ incumbent discretionary activity:

| Transition | Mechanism |
| --- | --- |
| Wander → Explore/Rest | incumbent `canContinueToUse()` false when adopted intent matches winner |
| Explore → Rest | priority 7 may preempt 8 naturally |
| **Rest → Explore** | **`CampfireGoal.canContinueToUse()` must voluntarily release** — priority cannot |
| Explore → Explore (re-adopt) | commitment window prevents thrash |

Yield is **requested** at adoption; trace records `YIELD requested/completed/refused`.

### Rule 6 — Commitment + switch margin (PD-GAO-10)

Without hysteresis:

```text
tick 100: Explore 31.0, Rest 30.0 → Explore
tick 110: Explore 29.9, Rest 30.1 → Rest
tick 120: Explore 30.2, Rest 30.0 → Explore   ← oscillation defect
```

**Locked direction:**

- minimum commitment from `adoptedAtTick` (tunable; not guessed in contract)
- switch only when challenger beats incumbent by `switchMargin` utility points
- natural termination, invalidation, or mandatory interrupt still ends incumbent immediately

### Rule 7 — D-GAO-025 trace completion (GAO-4 delivers)

Bounded per-mob ring (16–32 entries). Each discretionary decision records:

```text
SCORE
  EXPLORE 46.2  (component breakdown ref)
  REST    23.1
↓
SELECT
  EXPLORE (margin over REST, threshold pass)
↓
INTENT
  EXPLORE issued  intentId=…
↓
YIELD
  TrackedLocalWander released MOVE
↓
CLAIM / ADOPT
  ExploringGoal adopted intentId=…
↓
EXECUTOR
  ExploringGoal started  expeditionId=…
↓
TERMINAL
  SUCCESS | FAILURE | INTERRUPTED | INVALIDATED
  cause = …
```

**Must happen:** distinguish “REST won but no claim adopted” from “REST executor started and failed.”

**Must not happen:** utility score treated as proof of observable behavior.

### MAIBS preflight — intent vs mechanism vs prediction

| Layer | GAO-4 prediction |
| --- | --- |
| **Intended** | Mob visibly chooses Explore vs Rest from mood + opinion; mandatory work always wins |
| **Mechanism** | Director issues intent; executors gate on adoption; Campfire voluntarily yields for REST→EXPLORE |
| **Predicted (opinion on)** | Wander gives way to expedition or campfire scene; fewer endless stroll loops when bored |
| **Predicted (opinion off)** | Identical to pre-GAO-4 behavior (D-GAO-008) |
| **Failure modes** | Dual decision systems; REST→EXPLORE stuck; intent oscillation; stale intent after combat; shelter mistaken for discretionary REST |
| **Confidence** | Control-flow `CODE_CONFIRMED`; observable outcomes `UNVERIFIED` until approved runtime |

### GAO-4 preflight scenarios (required before handoff)

| ID | Setup | Must happen | Must not |
| --- | --- | --- | --- |
| **GAO-4-M1** | Wander + Explore wins + above threshold | Wander releases MOVE; Explore adopts and starts | Score alone starts Explore |
| **GAO-4-M2** | Wander + Rest wins | Campfire REST path starts; claim can open | SeekShelter used for discretionary REST |
| **GAO-4-M3** | REST running + Explore wins strongly (margin) | Campfire **voluntarily yields**; Explore starts | REST blocks Explore via priority forever |
| **GAO-4-M4** | Explore running + Rest wins | Explore releases; Rest starts | Explore ignores Director |
| **GAO-4-M5** | Intent issued; combat target tick+1 | Intent `INVALIDATED`; combat runs | Delayed discretionary start |
| **GAO-4-M6** | Intent issued; Stay/command active | Intent `INVALIDATED` | Command competes with intent |
| **GAO-4-M7** | Both scores below `activationThreshold` | `ABSTAINED`; ordinary idle continues | “Less negative” wins |
| **GAO-4-M8** | Explore adopted; no route | Honest `FAILED`/`EXPIRED`; no stuck intent | Permanent pending intent |
| **GAO-4-M9** | `opinion.enabled=false` | Legacy Campfire/Explore behavior | Hidden Director effect |
| **GAO-4-M10** | Mandatory mining/progression active | No discretionary score/intent | Opinion preempts NEED |
| **GAO-4-M11** | Tiny utility deltas within margin | Incumbent holds; no Explore↔Rest oscillation | Flip every 10 ticks |
| **GAO-4-M12** | REST→EXPLORE handoff | Proves voluntary yield despite p7 vs p8 | Priority-only preemption |

### Implementation scope boundary (GAO-4 authorized slice)

**In scope when authorized:**

- `DiscretionaryIntent` + lifecycle store on `MobExperienceContext`
- `DiscretionaryActivityDirector` (observation cadence owner per PD-GAO-05)
- `OpinionDecisionTrace` ring buffer
- Consumer gates: `CampfireGoal`, `ExploringGoal` (discretionary path only), `TrackedLocalWanderGoal` yield
- Activation threshold + switch margin + min commitment (constants with tests)

**Out of scope (later phases):**

- Socialize, gather-opportunistic, personality presets
- `ExplorationReadiness` threshold modulation (PD-GAO-01 C wiring may land here or GAO-5 — product decision at implementation)
- SeekShelter discretionary REST
- Runtime launch without explicit approval

### Open tuning knobs (implementation phase — not guessed here)

| Knob | Purpose |
| --- | --- |
| `activationThreshold` | PD-GAO-08 abstention |
| `switchMargin` | PD-GAO-10 anti-oscillation |
| `minCommitmentTicks` | PD-GAO-10 hold after adoption |
| `pendingIntentTtlTicks` | expire never-adopted pending intents |

Each knob requires a unit test anchor and a MAIBS scenario mapping.

---

## Contribution — Agent_Cursor (GAO-4 preflight contract)

**Agent:** `Agent_Cursor` **Date/Session:** 2026-08-09 **Type:** `PLANNING` + MAIBS preflight (no code)

**User input locked:** abstention, full intent lifecycle, adoption-anchored commitment, voluntary
yield for REST→EXPLORE, opinion-on consumer gates, SeekShelter exclusion, mandatory invalidation,
D-GAO-025 trace completion, 12 preflight scenarios.

**Decisions promoted:** D-GAO-017/018/020 → `LOCKED (GAO-4)`; PD-GAO-08/09/11 → `LOCKED`;
PD-GAO-10 → `LOCKED (direction)`.

**Evidence (`CODE_CONFIRMED`):** `SpmScavenger.java` goal priorities 7/8/9; `CampfireGoal.canUse`
no intent gate; `ExploringGoal.canUse` readiness-independent discretionary start;
`SeekShelterGoal` at priority 2.

**Not delivered:** any GAO-4 implementation, Goal wiring, runtime probes, threshold constants.

**Gate status:**

| Phase | Static MAIBS | Unit | Behavior authority | Runtime |
| --- | --- | --- | --- | --- |
| GAO-3 Scoring | PASS | 445 PASS | none | UNVERIFIED |
| GAO-4 Director | **CONTRACT LOCKED** | — | **not wired** | UNVERIFIED |

**Frontier after:** user authorization to implement GAO-4 per this contract.

---

## Contribution — Agent_Cursor (GAO-4 DiscretionaryActivityDirector)

**Agent:** `Agent_Cursor` **Date/Session:** 2026-08-09 **Type:** `IMPLEMENTATION`

**Delivered:** `DiscretionaryIntent`, `IntentLifecycle`, `InvalidationCause`,
`DiscretionaryDirectorConstants`, `DiscretionaryDirectorState`, `DiscretionaryActivityDirector`,
`DiscretionaryEligibility`, `DiscretionaryAuthority`, `OpinionDecisionTrace`; consumer gates on
`CampfireGoal`, `ExploringGoal`, `TrackedLocalWanderGoal`; director tick in
`ExplorationActivityGoal`; tests `DiscretionaryActivityDirectorTest` (GAO-4-M1…M12),
`DiscretionaryDirectorConstantsTest`.

**Tuning constants (tunable, evidence-anchored):**

| Constant | Value | Rationale |
| --- | ---: | --- |
| `ACTIVATION_THRESHOLD` | `0f` | Top utility must be positive appeal; both negative abstain (M7) |
| `SWITCH_MARGIN` | `8f` | ~20% band on ±100 utility scale; blocks jitter (M11) |
| `MIN_COMMITMENT_TICKS` | `600` | Matches `ExploringGoal.COOLDOWN_TICKS` expedition horizon |
| `PENDING_INTENT_TTL_TICKS` | `200` | 20 × 10-tick observation passes (legacy B-19) |
| `TRACE_CAPACITY` | `24` | D-GAO-025 bounded ring |

**Evidence (`CONFIRMED`):** `.\gradlew.bat clean build` — BUILD SUCCESSFUL; 485 tests, 0 failures.

**GAO-4 repair (2026-08-09, static `CONFIRMED`):** Four architecture defects found post-461-green:
(1) running/pending intent split — yield callbacks target `intentId`, challenger survives REST→EXPLORE;
(2) production director always ticks state so `OPINION_DISABLED` invalidates before consumer gates relax;
(3) `CampfireGoal` delivery stop preserves REST director authority while `RestSessionClaim` live;
(4) switch margin compares challenger to incumbent's **current** scored utility, not adoption snapshot;
`adoptedAtTick==0` maps to tick 1 for commitment. Full-chain M3/M4/M12 + toggle/claim/margin tests.

**GAO-4 REST closure attribution (2026-08-09, static `CONFIRMED`):** `RestCloseAttribution` shared
policy; combat/mandatory/player-order/unload/environment closes no longer terminal as `SUCCEEDED` or
collapse to `VOLUNTARY_ABANDON` + `REST_SESSION_CLOSE`. Observer-order tests in
`RestCloseObserverOrderTest`.

**Experience episode boundary repair (2026-08-09, static `CONFIRMED`):** `EpisodeBoundaryPolicy`
(cause-aware: `REST_SESSION_OPEN` ≠ terminal, `REST_SESSION_CLOSE` = terminal); `ensureEpisode` with
real `openedAtGameTime` (REST `arrivedAt`, mining `startedGameTime`, explore `startedTick`);
`EXPEDITION_END` + `ExpeditionEndAttribution` wired from `ExploringGoal` complete/abandon. Falsification
in `EpisodeBoundaryRepairTest` (open-alone false-green guard, 600-tick duration at world-age 1M).

**Evidence (`CONFIRMED`):** `.\gradlew.bat clean build` — BUILD SUCCESSFUL; 499 tests, 0 failures.

**MAIBS (`CODE_CONFIRMED`, runtime `UNVERIFIED`):** GAO-4-M1…M12 static scenarios pass; voluntary
REST→EXPLORE full handoff chain proven in director tests; consumer gates compile against locked paths.
GAO-4 static frontier closed — next evidence is runtime validation.

**Experience substrate static frontier closed** — episode open/close boundaries, start-time ownership,
and explore terminal ownership verified statically; runtime `UNVERIFIED`.

**Not delivered:** runtime launch, disk persistence, PLACE/ENTITY opinions, PersonalityModel,
`ExplorationReadiness` threshold modulation.

**Frontier after:** runtime validation with `opinion.enabled=true`; optional GAO-5+ scope.
