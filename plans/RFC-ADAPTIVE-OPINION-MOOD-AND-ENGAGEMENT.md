# RFC: Adaptive Opinion, Mood & Engagement (GA-OPINION)

## RFC Identity

| Field | Value |
| --- | --- |
| **Project root** | `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric` |
| **Host platform** | Social Player Mobs (`playermob`) v0.86.0 — reference `Projects/references/SocialPlayerMobs-v0.86.0/` |
| **Codename** | **GA-OPINION** (General Autonomy — Adaptive Opinion) |
| **Scope** | Cross-cutting discretionary intelligence layer: personality, learned opinions, short-term affect, and idle-time activity choice — **design for later**; not mining-specific |
| **Mode** | `PLANNING` |
| **Status** | `PROPOSED` — deferred; no implementation authorized |
| **User constraint** | Addon architecture only; **must not** fork or replace SPM; Opinion disabled ⇒ SPM parity unchanged |
| **Related** | `RFC-VANILLA-AUTONOMOUS-PROGRESSION.md`; `RFC-MINING-INTELLIGENCE-AND-WEALTH-SYSTEM.md` (MI-14 execution control); SPM `DispositionResolver`, `FollowLovedOneGoal` |
| **Owners** | User (product) |
| **Primary author** | **Agent_ChatGPT** (user-provided design, 2026-08-09) |
| **Peer review** | Pending |
| **Last update** | 2026-08-09 |
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

## Topic: REST vs permanent idle

**Status:** `PROPOSED`

| State | Meaning |
| --- | --- |
| **REST** | Intentional — safe base, night, friend nearby, post-expedition cooldown |
| **Permanent idle** | Failure — boredom rises until discretionary intent fires |

Acceptable REST while: `Opinion >= CONTENT` OR recent high stress OR short post-project cooldown.

Prolonged REST: stress falls → rest satisfaction falls → boredom rises → discretionary objective.

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

## Topic: Hard architectural rules (candidate LOCK)

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

---

## Topic: Relationship to existing RFCs

| RFC / system | Relationship |
| --- | --- |
| **RFC-VANILLA-AUTONOMOUS-PROGRESSION** | `RequirementResolver` / `WorkDemandPolicy` sit **above** Opinion in hierarchy |
| **RFC-MINING-INTELLIGENCE** | `MiningDirector` assigns when progression requires; Opinion chooses *route preference* among legitimate modes |
| **MI-14C** | Execution control unchanged; Opinion feeds **discretionary** intent only |
| **MI-11 Torch intelligence** | Discretionary or project-adjacent; does not override survival |

---

## Topic: Phased plan (deferred)

**Status:** `PROPOSED` — **not authorized**

| Phase | Task | Deliverable | Depends on |
| --- | --- | --- | --- |
| **GAO-0** | Evidence + SPM bridge spec | `SPMOpinionBridge` interface; activity observation contract | MI-14C2-R2 pattern |
| **GAO-1** | `AffectiveState` + observation | Boredom/engagement from activity events; scheduler-wide idle detection | GAO-0 |
| **GAO-2** | `OpinionMemory` v1 (ACTIVITY only) | Learned activity preferences + repetition | GAO-1 |
| **GAO-3** | `IdleOpportunityPolicy` | Score available discretionary activities | GAO-2, existing goals |
| **GAO-4** | `DiscretionaryActivityDirector` | Emit intents to existing directors/goals | GAO-3 |
| **GAO-5** | PLACE / ENVIRONMENT opinions | World gains meaning for choice | GAO-4, spatial memory TBD |
| **GAO-6** | ENTITY bridge | SPM `feelingToward` integration | GAO-4 |
| **GAO-7** | PersonalityModel | Trait-weighted experience scaling | GAO-2 |
| **GAO-8** | Observable expression | Movement/scan biases | GAO-4, deferred UX |

**Frontier when resumed:** GAO-0 — pin SPM activity taxonomy + observation API before any scoring code.

---

## Topic: Validation & gates

| Gate | Criterion |
| --- | --- |
| **GAO-PARITY** | Opinion off ⇒ indistinguishable from stock SPM + existing addon (within documented addon goals) |
| **GAO-HIERARCHY** | Survival/combat/command/progression always beat boredom in falsification scenarios |
| **GAO-OBSERVE** | `FollowLovedOneGoal` / combat / recovery never classified as idle |
| **GAO-COMPETENCE** | High negative `Opinion(MINING)` mob still mines when iron NEED active |
| **GAO-REPETITION** | Same activity long duration reduces utility without erasing long-term preference |
| **MAIBS-1** | Multi-minute discretionary sessions look human-plausible (explore → rest → socialize → return) |

Runtime probes require separate Minecraft launch approval.

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

---

## Change Log

| Date | Agent | Change |
| --- | --- | --- |
| 2026-08-09 | Agent_ChatGPT (via user) + Agent_Cursor | Initial RFC — full design capture; status `PROPOSED` / deferred |

---

## Contribution — Agent_ChatGPT (initial design)

**Agent:** Agent_ChatGPT (author); transcribed and structured by Agent_Cursor  
**Date/Session:** 2026-08-09  
**Contribution type:** `DESIGN / RFC_BOOTSTRAP`

**Frontier:** No prior GA-OPINION artifact. User requested “RFC for later.”

**Delivered:** North star, three-layer model, priority hierarchy, architecture separation, activity utility, SPM bridge, MI-14 alignment, phased GAO-0…8, gates, rejected alternatives.

**Frontier after:** Peer review; lock D-GAO-001…010; GAO-0 evidence (SPM goal taxonomy + activity observation) when implementation is authorized.

**Not authorized:** Implementation, Minecraft launch, commits.
