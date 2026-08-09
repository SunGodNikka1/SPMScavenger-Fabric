# RFC: Adaptive Opinion, Mood & Engagement (GA-OPINION)

## RFC Identity

| Field | Value |
| --- | --- |
| **Project root** | `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric` |
| **Host platform** | Social Player Mobs (`playermob`) v0.86.0 — reference `Projects/references/SocialPlayerMobs-v0.86.0/` |
| **Codename** | **GA-OPINION** (General Autonomy — Adaptive Opinion) |
| **Scope** | Cross-cutting discretionary intelligence layer: personality, learned opinions, short-term affect, and idle-time activity choice — **design for later**; not mining-specific |
| **Mode** | `PLANNING` |
| **Status** | `CONSENSUS` for GAO-0/0b/0c contracts — first implementation slice is dependency-ready; implementation not yet authorized |
| **User constraint** | Addon architecture only; **must not** fork or replace SPM; Opinion disabled ⇒ SPM parity unchanged |
| **Related** | `RFC-VANILLA-AUTONOMOUS-PROGRESSION.md`; `RFC-MINING-INTELLIGENCE-AND-WEALTH-SYSTEM.md` (MI-14 execution control); `MoveHolderClassifier` (MI-14C2-R1 activity taxonomy seed); SPM `DispositionResolver`, `FollowLovedOneGoal` |
| **Owners** | User (product) |
| **Primary author** | **Agent_ChatGPT** (user-provided design, 2026-08-09) |
| **Peer review** | Agent_Cursor; Agent_Claude; Agent_Codex; user-provided contract review (2026-08-09) |
| **Last update** | 2026-08-09 (peer-review amendments and contract locks) |
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

**Nearest frontier:** D-GAO-011/012/015/021…025 and PD-GAO-06/07 are locked. GAO-0 is the
dependency-ready first implementation slice: refactor the shared activity observer with Opinion
disabled and prove no behavior change. Implementation remains unauthorized until explicit
**Begin GAO-0** (or equivalent).

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

**Status:** `PROPOSED`

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

## Topic: GAO-0 — Activity taxonomy & observation (`RESEARCHING`)

**Status:** `RESEARCHING` — SPM + addon goal map drafted 2026-08-09 (Agent_Cursor); consolidation design pending

GAO-0 must answer: *what activity is this mob doing right now, and is that IDLE, REST, MANDATORY, or DISCRETIONARY?*

### Seed: MI-14 already classifies running goals (`CONFIRMED`)

`MoveHolderClassifier` (`mining/MoveHolderClassifier.java`) already walks the live `GoalSelector` and maps running goals to lease/arbitration meaning without compiling against SPM:

| `MoveHolderClassification` | Example goals (suffix / type) | Opinion `ActivityClass` (proposed) |
| --- | --- | --- |
| `NOT_MOVE_HOLDER` | Flagless helpers (`PlayerMobDoorGoal`, `DigThroughGoal`) | `PASSIVE_HELPER` — does not count as meaningful work |
| `PROTECTED_SAFETY_RECOVERY` | `EnvironmentalEscapeGoal`, `SeekShelterGoal`, `FireBucketGoal`, `FleeFromCategoryGoal`, `TrainRecoveryGoal` | `MANDATORY_SAFETY` |
| `PROTECTED_PLAYER_ORDER` | `CommandedActionGoal`, `StayNearGoal` | `MANDATORY_COMMAND` |
| `PROTECTED_LOW_FOOD` | `EatFoodGoal` | `MANDATORY_SURVIVAL` |
| `PROTECTED_FINITE` | `SkepticalWatchGoal`, `FriendlyGreetGoal`, `DoorOperationGoal` | `SOCIAL_REFLEX` — meaningful, not idle |
| `PROTECTED_COMBAT` | `WeaponAwareAttackGoal`, TNT/crystal combat | `MANDATORY_COMBAT` |
| `ORDINARY_HOST_WORK` | `FollowLovedOneGoal`, `SeekAmmoGoal` | `SOCIAL_TRAVEL` / `COMBAT_PREP` |
| `COOPERATIVE_PROJECT_WORK` | `GatherResourcesGoal` during tunnel handoff | `PRODUCTIVE_COOP` — not idle |
| `UNKNOWN_MOVE_HOLDER` | Unmapped addon/host goal | `UNKNOWN_ACTIVE` — fail safe, not idle |
| Designated mining consumer | `ControlledDescentGoal`, `TunnelSearchGoal`, cave handoff explore | `PROJECT_EXECUTION` |

**Recommendation (D-GAO-011):** GAO-0 defines `ActivityObservationService` as a **thin wrapper** over `MoveHolderClassifier` + addon goal-kind mapping — do **not** fork a second classifier.

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

**Locked decision (D-GAO-015):** GAO-0 **refactors** this loop into
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

## Topic: Experience events — mood inputs without new goals (`LOCKED CONTRACT`)

**Status:** D-GAO-012/022/023 `LOCKED`; emitters remain unimplemented

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

## Topic: GAO-1 — AffectiveState sketch (`PROPOSED`)

**Status:** `PROPOSED` — pre-implementation math only; no Java authorized

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

### `DiscretionaryIntent` lifecycle (B-19)

```text
issued when boredom crosses threshold AND IdleOpportunityPolicy picks activity
TTL = 200 ticks (10 s) unless consumed
invalidated immediately when:
  - any MANDATORY_* class becomes active
  - new player command / StayNear anchor
  - combat target acquired
```

**Must happen:** Intent consumed by `ExploringGoal` / readiness sets expedition without new GoalSelector entry.

**Must not happen:** Stale `EXPLORE` intent fires after `FollowLovedOneGoal` starts.

---

## Topic: DiscretionaryIntent — data, not GoalSelector entries (`PROPOSED`)

**Status:** `PROPOSED` (Agent_Cursor)

When `IdleOpportunityPolicy` picks an activity, emit:

```text
DiscretionaryIntent {
  ActivityKind preferred;     // EXPLORE, SOCIALIZE, REST, CAMPFIRE, MIMICRY, …
  float urgency;              // boredom-driven
  long issuedAtTick;
  Optional<UUID> socialTarget;
}
```

**Consumers (existing executors):**

| Intent | Consumer | Notes |
| --- | --- | --- |
| `EXPLORE` | `ExploringGoal` / `ExplorationReadiness` | Sets readiness or biases stage heading |
| `REST` | `SeekShelterGoal` / `CampfireGoal` | Intentional downtime |
| `SOCIALIZE` | Bias toward `FriendlyGreetGoal` eligibility window | Does not preempt follow/combat |
| `GATHER_OPPORTUNISTIC` | Soft boost to `GatherResourcesGoal` when wealth allows | Never overrides NEED |

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
| **D-GAO-017** | *(candidate, B-25)* An `ActivityClass` is selectable only when a designated executor exists — aspiration stays in the taxonomy, out of the scorer |
| **D-GAO-018** | *(candidate, B-22)* Discretionary selection requires a **voluntary yield protocol**; a utility ranking alone cannot move a Minecraft goal at equal priority |
| **D-GAO-019** | *(candidate, B-23)* Affect clocks pause while a downstream executor serves the chosen activity, reusing `COOPERATIVE_PROJECT_WORK` rather than a second signal |
| **D-GAO-020** | *(candidate, B-21)* GAO-4 ships with ≥2 executable discretionary activities; one activity cannot falsify a director's genericity |
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

**Status:** GAO-0 `READY_FOR_IMPLEMENTATION` — **authorization required**

| Phase | Task | Deliverable | Depends on |
| --- | --- | --- | --- |
| **GAO-0** | Activity taxonomy + observation contract | `ActivityClass` enum; `ActivityObservationService` wrapping `MoveHolderClassifier`; SPM goal map table | MI-14C2-R2 pattern |
| **GAO-0b** | `ExperienceEvent` schema | Immutable raw events + `ExperiencePipeline.accept`; exact cause retained | GAO-0 |
| **GAO-0c** | Episode + rest-claim contracts | `ActivityEpisode`, `OutcomeClass`, `RestSessionClaim`; affect-vs-learning and unload lifecycle tests | GAO-0, GAO-0b |
| **GAO-1** | `AffectiveState` + observation | Boredom/engagement from experience events; scheduler-wide idle detection | GAO-0, GAO-0b, GAO-0c |
| **GAO-2** | `OpinionMemory` v1 (ACTIVITY only) | Learned activity preferences + repetition | GAO-1 |
| **GAO-3** | `IdleOpportunityPolicy` | Score available discretionary activities | GAO-2, existing goals |
| **GAO-4** | `DiscretionaryActivityDirector` | Emit intents to existing directors/goals | GAO-3 |
| **GAO-5** | PLACE / ENVIRONMENT opinions | World gains meaning for choice | GAO-4, spatial memory TBD |
| **GAO-6** | ENTITY bridge | SPM `feelingToward` integration | GAO-4 |
| **GAO-7** | PersonalityModel | Trait-weighted experience scaling | GAO-2 |
| **GAO-8** | Observable expression | Movement/scan biases | GAO-4, deferred UX |

### GAO-0 implementation-ready task

| Field | Contract |
| --- | --- |
| Status | `READY_FOR_IMPLEMENTATION` — authorization missing |
| Objective | Extract the existing 10-tick selector observation into one `ActivityObservationService` without changing behavior |
| Primary systems | `ExplorationActivityGoal`, `ExplorationReadiness`, `MoveHolderClassifier`, new observer contract and unit tests |
| Constraints | Opinion remains disabled/unimplemented; observer stays flagless and staggered; current unknown-goal fail-safe and director ordering remain intact; no second selector scan |
| Must happen | Existing idle/work/explore sequences produce identical `ExplorationReadiness` results before and after refactor; host + addon goal classifications are visible through one service |
| Must not happen | New affect state, discretionary intent, REST claim, preemption, scan cadence, goal priority, or observable mob behavior appears in GAO-0 |
| Static/unit gates | Unknown running Goal = occupied/meaningful-work fail-safe; wander/look/antics remain cosmetic-idle; Explore remains expedition; observer owns no flags; early-return/director ordering regression tests |
| Build gate | `gradlew.bat test build` passes; proves compile/tests only, not runtime behavior |
| Runtime state | Not required to implement the refactor, but behavior parity remains `UNVERIFIED` until a separately approved Minecraft launch |

**Frontier when resumed:** request authorization for GAO-0. Its scope is only the shared observer
refactor with Opinion disabled and current `ExplorationReadiness` behavior preserved. After its
static/unit/build gates, proceed to GAO-0b/0c only under continued implementation authority. Do not
reopen the locked contracts without contradictory implementation evidence.

### Product decisions

| ID | Status | Question | Options | Decision / recommendation |
| --- | --- | --- | --- | --- |
| **PD-GAO-01** | `OPEN` | Should mood affect **only** discretionary ranking, or also **thresholds** (explore idle ticks)? | A thresholds only / B ranking only / C both | Recommend **C both** |
| **PD-GAO-02** | `OPEN` | Is `CampfireGoal` idle REST or positive engagement? | REST / mild engagement | Recommend **REST with mild engagement** |
| **PD-GAO-03** | `OPEN` | Persist `OpinionMemory` across death? | wipe / partial / full | Recommend **Partial** |
| **PD-GAO-04** | `OPEN` | Config surface | `opinion.enabled` only / full trait sliders | Recommend **enabled + 3 presets** |
| **PD-GAO-05** | `RESOLVED BY D-GAO-015` | Who owns `IdleOpportunityPolicy` tick? | fold into `ExplorationActivityGoal` / new flagless goal | Single refactored observer; affect/intent bookkeeping must precede early returns |
| **PD-GAO-06** | `LOCKED` | Which two executable activities prove GAO-4? | Explore + Rest / Explore + opportunistic Gather / other | **Explore + Rest** after the REST claim lifecycle exists |
| **PD-GAO-07** | `LOCKED` | What happens while unloaded/non-ticking? | freeze / lazy elapsed-time decay / full catch-up | **Freeze affect/opinion; invalidate intents; invalidate/revalidate rest claims; suspend only genuinely resumable episodes** |

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
| D-GAO-011 | Reuse `MoveHolderClassifier` for GAO-0 observation | `LOCKED` | Prior consensus; no peer objection; prerequisite accepted with GAO-0 frontier 2026-08-09 |
| D-GAO-012 | Existing terminals emit raw events into one pipeline; no parallel scanners/direct memory writes | `LOCKED` | Amended with D-GAO-022 separation; user peer review 2026-08-09 |
| D-GAO-013 | Mood modulates readiness thresholds; never owns `descentPressure` | `PROPOSED` | MI-5 lesson |
| D-GAO-014 | `DiscretionaryIntent` as data consumed by existing goals | `PROPOSED` | TTL + invalidation B-19 |
| D-GAO-015 | Single `ActivityObservationService`; refactor `ExplorationActivityGoal` scan | `LOCKED` | Preserve readiness behavior and early-return ordering; user peer review 2026-08-09 |
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

---

## Change Log

| Date | Agent | Change |
| --- | --- | --- |
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
