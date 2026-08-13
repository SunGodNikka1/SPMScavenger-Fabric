# RFC: Adaptive Opinion, Mood & Engagement (GA-OPINION)

## RFC Identity

| Field | Value |
| --- | --- |
| **Project root** | `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric` |
| **Host platform** | Social Player Mobs (`playermob`) v0.86.0 — reference `Projects/references/SocialPlayerMobs-v0.86.0/` |
| **Codename** | **GA-OPINION** (General Autonomy — Adaptive Opinion) |
| **Scope** | Cross-cutting discretionary intelligence layer: personality, learned opinions, short-term affect, and idle-time activity choice — **design for later**; not mining-specific |
| **Mode** | `PROGRESSIVE_CONTINUATION` — GAO-10 proposed for review; stabilize GAO-4R/GAO-4R1 before implementation |
| **Status** | GAO-0 through GAO-9 (**CLOSED / STATIC ACCEPT**) + GAO-4.1 + **RET-GAO-1** + **GAO-4R** (`IMPLEMENTED / STATIC ACCEPT`); GAO-8B Task 42A/42B `IMPLEMENTED / STATIC ACCEPT`; D-GAO-043 `IMPLEMENTED / STATIC ACCEPT`; D-GAO-044 `LOCKED`; **GAO-10** `PROPOSED / DISCUSSION` |
| **User constraint** | Addon architecture only; **must not** fork or replace SPM; Opinion disabled ⇒ SPM parity unchanged |
| **Related** | `RFC-VANILLA-AUTONOMOUS-PROGRESSION.md`; `RFC-MINING-INTELLIGENCE-AND-WEALTH-SYSTEM.md` (MI-14 execution control); `MoveHolderClassifier` (MI-14C2-R1 activity taxonomy seed); SPM `DispositionResolver`, `FollowLovedOneGoal` |
| **Owners** | User (product) |
| **Primary author** | **Agent_ChatGPT** (user-provided design, 2026-08-09) |
| **Peer review** | Agent_Cursor; Agent_Claude; Agent_Codex; user-provided contract review (2026-08-09) |
| **Last update** | 2026-08-12 (Task 43 / GAO-4R1 CLOSED — STATIC ACCEPT; D-GAO-050/051 LOCKED / IMPLEMENTED; 745 tests; runtime UNVERIFIED) |
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

**Nearest frontier:** **GAO-10 SOCIAL** — GAO-4R1 is `IMPLEMENTED / STATIC ACCEPT` and Task 43 is `CLOSED` (745 tests, runtime `UNVERIFIED`). Evaluate D-GAO-045…056 against the finished generic APIs rather than designing SOCIAL around EXPLORE/REST machinery. Previously: **lock GAO-4R1** (adoption vs continuation + generic yield API) → authorize **Task 43**
implementation → then finalize gen-1 **GAO-10** executor contract. Runtime verification of GAO-8B
inspector (`RQ-GAO-SHELTER-01` + SCR-2R2+ shelter physics) remains a separate launch-approved probe.

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
| B-36 | GAO-8B must show `SHELTER_HOLD` suppression separately from discretionary REST and from `resting=true` | **PROMOTE** → GAO-8B shelter readout; becomes D-GAO-044 |
| B-37 | Failed shelter path / door churn must not teach environment or REST dislike | **PROMOTE** → experience eligibility; extends D-GAO-023; no new learning sink |
| B-38 | Inspector exposes read-only `ShelterNightAuthority` phase (`APPROACHING`/`SETTLED`/`RETURNING`) when present | **PROMOTE** → Task 42B snapshot fields; helps explain door loops without rescanning goals |
| B-39 | Third discretionary activity `SOCIAL` with target-specific `SocialIntent`, separate activity vs target utility, GAO-4R admission, existing SPM executor — not a mega `SocializeGoal` | **PROMOTE** → GAO-10 topic (Agent_ChatGPT, 2026-08-12) |
| B-40 | Replace pairwise `restYieldRequested` / `exploreYieldRequested` with activity-generic `requestYield(incumbent, challenger)` before a third discretionary activity ships | **PROMOTE** → GAO-4R1 topic (Agent_Cursor, 2026-08-12) |
| B-41 | Split GAO-4R admission into **ADOPTABLE** (selection) vs **CONTINUABLE** (incumbent execution) — fresh probe failure must not kill a live run | **PROMOTE** → GAO-4R1 topic |
| B-42 | Gen-1 SOCIAL executor = SPM `FriendlyGreetGoal` lifecycle via minimal `SocialIntent` target adapter, not a new socialize mega-goal | **PROMOTE** → GAO-10 gen-1 research (Agent_Cursor, 2026-08-12) |
| B-43 | Bounded target resolver: alive/loaded/same-dimension/distance + SPM `Reaction.GREET` legality + no combat/command conflict | **PROMOTE** → GAO-10 target resolver |
| B-44 | Discretionary SOCIAL must not preempt SPM priority-1 `SOCIAL_REFLEX` goals (`FriendlyGreetGoal`, `SkepticalWatchGoal`, …) — desire yields to host reflex | **PROMOTE** → GAO-10 + priority hierarchy |
| B-45 | Wire existing `ActivityKind.SOCIALIZING` + `ExperienceCause.SOCIAL_GREET` terminals on attributable social completion — do not invent parallel social learning | **REFINEMENT** → GAO-10 experience section |
| B-46 | Add read-only `PlayerMobs.reactionToward` bridge for eligibility (alongside existing `feelingToward`) | **PROMOTE** → GAO-10 research / GAO-6 extension |
| B-47 | Invalidate pending `SocialIntent` when chosen target fails eligibility before adoption; running social uses continuation probe not fresh adoption | **PROMOTE** → GAO-4R1 + GAO-10 lifecycle |
| B-48 | `SocialIntent` pending TTL bounded by `FriendlyGreetGoal` worst-case tick budget (~400t: follow 100 + crouch 100 + fetch 100 + margin) | **PROMOTE** → GAO-10 lifecycle |
| B-49 | Gen-1 adapter gates SPM `FriendlyGreetGoal.canUse()` only when Opinion-issued `SocialIntent` target matches — do **not** replace global `nearestWhereReaction` (preserves SPM autonomous greets when Opinion abstains) | **PROMOTE** → GAO-10 executor |
| B-50 | SOCIAL continuation probe mirrors greet `canContinueToUse`: target alive, `reactionToward==GREET`, distance ≤ `range+6`, no combat target | **PROMOTE** → GAO-4R1 + GAO-10 |
| B-51 | `YieldRequest` record + `mustYield(incumbentActivity)` replaces pairwise booleans before third activity | **PROMOTE** → GAO-4R1 (duplicate of B-40 — **merge at implementation**) |
| B-52 | Explicit contract: director `NO_CANDIDATES` while `runningIntent` active ⇒ `RETAINED` disposition — already in code (`retainedDisposition`); needs GAO-4R1 regression tests | **PROMOTE** → GAO-4R1 tests |
| B-53 | Inspector day-one: extend `ActivityAdmissions` + snapshot with `social` admission row and bound `SocialIntentView` (target id/name, TTL, suppression detail) | **PROMOTE** → GAO-10 + GAO-8B |
| B-54 | `ActivityUtilityScorer.scoreSocial` uses `OpinionMemory.memoryOf(SOCIALIZING)` symmetric to EXPLORE/REST — activity score has **no** per-target affinity term | **PROMOTE** → GAO-10 utility |
| B-55 | `PlayerMobs.reactionToward` reflection bridge (public SPM API) — eligibility gate alongside `feelingToward`; fails closed like existing bridges | **PROMOTE** → GAO-10 + GAO-6 extension (extends B-46) |

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

### Opinion target closure: Environment and project — GAO-9

**Status:** `IMPLEMENTED / STATIC ACCEPT` — Task 41; 618 tests

### Gap and evidence

The original D-GAO-010 taxonomy named five target families. The implemented system currently has
ACTIVITY, PLACE, and ENTITY memory, but no ENVIRONMENT or PROJECT target.

`CODE_CONFIRMED` evidence:

- `ExperienceEvent` carries activity, place, and entity context; `EpisodeLearningEvidence` carries
  activity only.
- `ExploringGoal.createExpedition` already constructs at most eight routes inside its entity-ticking
  chunk guard and applies `PlaceOpinionRouteRanker` to each final waypoint.
- `BiomeTags` in the pinned 1.21.1 mapped source exposes `IS_FOREST`, `IS_OCEAN`, `IS_NETHER`, and
  `IS_END`; `Biome.coldEnoughToSnow(BlockPos)` is the target-native positional snow predicate.
- `ExpeditionEndAttribution` distinguishes complete, simulation frontier, path failure, authority
  interruption, and stale abandonment. Existing policy learns positively from completion and
  refuses preference learning from frontier/authority/path failures or an unspecified stale close.

Required negative probes:

1. `EnvironmentOpinionMemory` / `ProjectOpinionMemory` — **NOT FOUND**.
2. `EnvironmentKind` / `EnvironmentProfile` in raw or normalized evidence — **NOT FOUND**.
3. Biome/environment classification or environment bias in expedition scoring — **NOT FOUND**;
   only PLACE destination bias exists.

### PROJECT target decision

Individual `MiningProject` and expedition identities are finite causal instances, not durable
preference subjects. Their stable semantic types are already represented by `ActivityKind`
(`CONTROLLED_DESCENT`, `TUNNEL_SEARCH`, `CAVE_EXPLORATION`, `OVERLAND_EXPLORATION`), while
`ActivityEpisode` and the decision trace own per-instance history.

| Option | Benefit | Strongest objection / failure mode | Verdict |
| --- | --- | --- | --- |
| Per-project UUID opinion map | Can remember one named attempt | Freshly minted IDs create low-reuse entries, duplicate episode state, and risk Gate RET-1 growth | **REJECT** |
| Stable project-type opinion via `ActivityKind` | Already bounded, learned, persisted, and consumed | Cannot say “project instance 12 was bad” long-term; that belongs in episode/trace evidence | **RECOMMEND** |
| Bounded project-location memory | Can distinguish a troublesome site | This is already PLACE memory keyed by chunk | **REUSE EXISTING** |

**D-GAO-035 (`IMPLEMENTED`):** remove PROJECT as an independent `OpinionMemory` family. Stable project
preference is ACTIVITY; spatial project evaluation is PLACE; per-instance causality is EPISODE /
TRACE. Reopen only if a persistent named project gains a stable identity and a real consumer that
cannot be represented by those three owners.

### ENVIRONMENT candidate designs

| Option | Design | Benefit | Strongest objection / failure mode | Gate |
| --- | --- | --- | --- | --- |
| **A — recommended** | Small project-owned multi-label `EnvironmentProfile`; classify only existing event positions and already-valid route destinations | General preference, bounded enum storage, modded-biome tag compatibility, no second scan | Untagged modded biomes remain neutral; correlated labels can over-credit one success | Decision-ready |
| B | Memory keyed by exact biome registry key | Naturally data-driven and distinguishes every modded biome | High-cardinality, over-specific learning; upgrades/renames fragment memory; weak generalization | Reject for gen-1 |
| C | Reuse PLACE only | No schema or route-classification work | A forest preference cannot transfer to another forest; leaves original ENVIRONMENT capability absent | Keep as fallback if environment learning proves noisy |

Option A gen-1 labels are deliberately limited to contexts with target-native evidence and an
immediate consumer: `FOREST`, `OCEAN`, `SNOWY`, `NETHER`, and `END`. A profile may hold multiple
labels. `DEEP_UNDERGROUND`, `CAVE`, and `NIGHT` remain deferred:

- `DEEP_UNDERGROUND` needs a target-derived depth contract, not a copied numeric Y threshold.
- `CAVE` needs topology/exposure evidence and a corresponding cave/tunnel choice consumer; a roof
  or indoor room must not become a cave merely because sky is hidden.
- `NIGHT` is transient and identical for all simultaneous route candidates, so it cannot influence
  destination choice without a separate time/activity consumer.

**D-GAO-036 (`IMPLEMENTED`):** environment context is an immutable multi-label value captured at the
existing experience/route seams. It is never a world reference, scanner, Goal, objective, or
capability gate. Classification uses biome tags and the actual surface position for the final
waypoint; unknown/untagged contexts are neutral.

### Learning and consumption contract

```text
existing expedition terminal
        ↓
EnvironmentProfile at actual terminal position
        ↓
ExperienceEvent → ActivityEpisode → EpisodeLearningEvidence
        ↓
EnvironmentOpinionMemory (enum-bounded)
        ↓
future expedition construction
        ↓
classify each already-valid final waypoint once
        ↓
soft route tie-breaker only
```

This extends D-GAO-012's single evidence pipeline. Task 41 must not add another direct memory write
like the older PLACE/ENTITY emitter bridges, and it must not add a terrain scan. A per-planning-call
cache may deduplicate destination classification just as `ChunkInterest` deduplicates chunk work.

**D-GAO-037 (`IMPLEMENTED`):** environment learning uses the existing normalized episode weight and
eligibility, but only when the terminal cause is environment-relevant. Gen-1 allows
`EXPEDITION_COMPLETE` positive learning. `SIMULATION_FRONTIER`, `AUTHORITY_CANCEL`, path/no-progress,
tool failure, combat/protected interruption, and stale/unspecified closure produce **zero**
environment learning. One personality-scaled evidence delta is divided across the profile's labels,
so adding a correlated label does not manufacture more total learning. Negative affinity waits for
an explicit, attributable environment terminal; it must not be inferred from generic execution
failure.

**D-GAO-038 (`IMPLEMENTED`):** environment opinion ranks among routes that already passed simulation,
history, and construction gates. It never makes an unticking, unsafe, or unreachable route valid,
cannot by itself erase a recent-destination penalty, and never affects mandatory descent/cave
handoff. Combine multiple
labels by their mean rather than their sum. **Environment affinity is semantic preference, never
terrain-safety preference:** it cannot alter powder-snow/path malus, environmental escape, hazard
handling, or survival behavior. Initial route bias is capped at **±10**: lower than the
existing PLACE ±15 because ENVIRONMENT is broader and less specific, below the `-20` visited-region
penalty by itself, and far below the `-100` recent-destination anti-fixation term. This is a
target-specific starting budget, not a universal balance value; switch only if deterministic route
distribution evidence shows it is inert or dominates novelty/history.

### Performance, compatibility, and retention

- Classification is O(1) at an emitted terminal and O(route candidates) during cooldown-gated
  expedition creation (currently at most eight), never per tick.
- Candidate biome/height lookup occurs only after `chunkGuardTicking`; it must not load, generate,
  or force-tick chunks.
- Memory is bounded by the finite enum, belongs to the existing `MobExperienceContext`, and uses the
  existing freeze/death/snapshot lifecycle. No new UUID registry is permitted.
- Modded biomes participate through vanilla biome tags. Missing tags mean neutral, not disliked.
- No client-only types, packets, Goal flags, navigation calls, block scans, or scheduler scans are
  introduced.
- **Still Life 0.1.1 static compatibility (2026-08-10):** the verified Modrinth artifact
  (`SHA-1 ef980781480a4034d336a447a8a0d9fd4dbe5c5b`) contains 108 custom biomes, zero classes,
  zero Mixins, and no exact datapack-path collision with Scavenger. Its tags cover nine custom
  `minecraft:is_forest` biomes and all ten custom oceans through `is_ocean`/`is_deep_ocean`.
  Forest-like biomes assigned only to other families remain neutral under D-GAO-036; this is
  incomplete semantic coverage, not an incompatibility. Still Life requires Lithosphere, warns
  against other Overworld biome replacements, and documents potentially slow world generation.
  Coexistence behavior/TPS remain `UNVERIFIED` because no installed artifact or runtime log was
  available. Source: `https://modrinth.com/datapack/still-life` and inspected 0.1.1 JAR.

### Behavioral Prediction (MAIBS-1)

| Layer | Result |
| --- | --- |
| Intended behavior | A mob gradually favors familiar environment families when several valid expedition destinations compete |
| Implemented mechanism target | Completed expeditions update bounded environment memory; future already-valid candidates receive a soft destination score |
| Predicted behavior | First expedition is unchanged; after success in a forest, later comparable forest routes win slightly more often, while novelty/history still prevent immediate repetition |
| Failure/weirdness | Tagged borders can flip labels; modded untagged biomes stay neutral; correlated FOREST+SNOWY evidence can overfit; all candidates may share one label and therefore remain unchanged |
| Confidence | Route/event seams and mapped APIs `CODE_CONFIRMED`; resulting long-duration route distribution `GAME_MECHANICS_INFERRED`; runtime `UNVERIFIED` |

Several-minute loop: ordinary readiness unlocks EXPLORE → existing route generation validates
ticking candidates → environment is only a tie-breaker → vanilla navigation executes → combat/work
may interrupt and resume unchanged → only successful terminal evidence updates affinity → a later
expedition may rank differently. No new activity is created.

Adversarial scenarios required:

| Scenario | Must happen | Must not happen |
| --- | --- | --- |
| Forest success, later mixed candidates | Comparable tagged forest destination receives a bounded positive bias | Environment bias alone cancels the recent-destination penalty or bypasses the ticking guard |
| Simulation frontier / command / path failure | Environment memory remains byte-for-byte unchanged | The unseen biome is learned as disliked |
| Forest + snowy profile | One event is normalized once; label aggregation is averaged/clamped | Two labels double the episode's learning magnitude or route dominance |
| Modded biome without vanilla tags | Candidate remains neutral and usable | Unknown biome is rejected or classified by name guessing |
| Opinion disabled | Route score and snapshots retain pre-GAO-9 parity | Biome lookup changes selection or creates memory |
| Multiple mobs | Each uses its bounded context independently | Shared mutable affinity or route reservation appears |

### Task 41 — GAO-9 Overland Environment Affinity (`IMPLEMENTED / STATIC ACCEPT`)

| Field | Contract |
| --- | --- |
| Objective | Close the usable ENVIRONMENT taxonomy gap end-to-end without adding world scans or scheduler authority |
| Scope | `EnvironmentKind/Profile/Classifier`, event/evidence schema, enum-bounded memory and snapshot lifecycle, conservative learning policy, expedition destination ranker, tests, RFC evidence |
| Must happen | A completed forest expedition changes future comparable forest destination ranking through the single normalized pipeline |
| Must not happen | Generic failure teaches dislike; environment selects an invalid route; Opinion-off behavior changes; per-project/per-biome unbounded maps appear |
| Required tests | schema preservation; tag/profile classification; multi-label normalization; cause eligibility; neutral/disabled parity; snapshot/death/unload; route ordering below anti-fixation; no-load/ticking order; PROJECT negative architecture scan |
| Static gates | D-GAO-012, GAO-COMPETENCE, GAO-HIERARCHY, GAO-ATTRIBUTION, GAO-PARITY, RET-1, AV-1, MAIBS-1 |
| Runtime | Not a default gate under PD-GAO-12; long-duration distribution remains `UNVERIFIED` unless a later `RUNTIME_QUESTION` is filed |

**Result:** user accepted D-GAO-035…038 with the explicit terrain-safety clarification and
authorized Task 41. Implementation, focused tests, full suite, clean build, package inspection, and
post-code MAIBS passed. Runtime route distribution and performance remain `UNVERIFIED`.

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

### Nighttime shelter authority clarification — cross-RFC SCR-2R5

**Status:** `IMPLEMENTED / STATIC ACCEPT / D-GAO-043`

Current source dynamically reports an arrived `SeekShelterGoal` as `ActivityClass.REST`, even
though the same observer already reports affective rest independently from the correlated shelter
`RestSessionClaim`. Because `DiscretionaryEligibility` does not block `REST`, this contradicts the
locked rule below that `SeekShelterGoal` is mandatory safety and is not the discretionary REST
executor.

Locked correction: approach remains `MANDATORY_SAFETY`; exact arrived nighttime authority becomes
`ActivityClass.SHELTER_HOLD`; `Observation.resting=true` remains the independent affective signal.
`SHELTER_HOLD` blocks discretionary eligibility and is not included by `ActivityClass.isRest()`.
Only `CampfireGoal`'s `DISCRETIONARY_REST` authority may consume Opinion's REST→EXPLORE
voluntary-yield request. `SHELTER_RECOVERY`/`SHELTER_HOLD` may not.

`ActivityClass` expresses observational meaning only. Physical interruption is decided separately
by the centralized cross-RFC shelter envelope: stationary helpers may run in place, finite required
helpers may suspend/resume the same commitment, immediate danger or explicit player authority may
override, and voluntary displacement is blocked. Opinion evaluates preferences only after this
legal envelope and never supplies shelter override authority.

The mandatory test must use real authority provenance rather than the current generic
`combatTarget = mob.getTarget() != null`: SPM also assigns passive animals as targets for
`HuntForFoodGoal`, so a non-null target can mean proactive food acquisition rather than immediate
danger. Opinion remains downstream of the resolved legal envelope either way.

The inspector should normally show `suppressed: mandatory night shelter`. It should not compute or
display an illegal candidate as though it were a causal selection; any future counterfactual score
must be explicitly labelled non-causal and calculated by a side-effect-free diagnostic path.

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
  Optional<BlockPos> place;          // GAO-5 — schema field; consumer is GAO-5B route ranker, not EXPLORE utility
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

## Topic: Observable expression — GAO-8A passive physical expression

**Status:** `IMPLEMENTED / STATIC ACCEPT` — Task 40; runtime visual cadence `UNVERIFIED`

### Goal and hard boundary

Expose affect/personality through harmless physical attention while keeping expression separate
from activity choice and authority.

| GAO-8A may | GAO-8A must not |
| --- | --- |
| Rotate head / choose a LOOK target | Start exploration, REST, following, gathering, or any other activity |
| Change bounded cosmetic attention cadence/hold time | Cancel or delay mining, progression, commands, combat, eating, or safety/recovery |
| Emit tiny idle-only pose effects after separate review | Claim `MOVE`, operate navigation, jump, break/place/use blocks, or change inventory |
| Expose read-only expression state to later debug/UI | Change Goal priorities, modify SPM relationships, or write OpinionMemory |

**User product contract (PD-GAO-13):** BORED shifts attention more often; CURIOUS uses broader
non-semantic gaze variation; SOCIABLE prefers nearby PlayerMobs the mob already likes; STRESSED uses
shorter vigilant holds; ENGAGED preserves steadier task attention. These are expressions, not
decisions.

### Evidence from the current host/addon

`CODE_CONFIRMED`:

- SPM registers `LookAtPlayerGoal` at priority 9 and `RandomLookAroundGoal` at 10; its command,
  safety, combat, social, eating, loot, farming, and train goals also own `LOOK` where needed
  (`PlayerMobEntity.registerGoals`, pinned source reference).
- Addon work goals generally own `MOVE+LOOK`; `ExploringGoal` owns `MOVE` only
  (`SpmScavenger.installExploration`; `ExploringGoal` constructor).
- `ObjectiveReadout.isNoise` filters subclasses of `RandomLookAroundGoal`, providing a host-owned
  way to keep a cosmetic expression out of the objective line.
- `AnticsGoal` is flagless but calls `mob.getLookControl().setLookAt(player, ...)` during mimicry.
  Its navigation-done/no-target checks do **not** prove another running goal is not using `LOOK`.

Required negative evidence:

1. Dedicated `PassiveExpression` / `ExpressionGoal` / `AttentionPolicy` owner — **NOT FOUND**.
2. Terrain-salience / interesting-terrain provider — **NOT FOUND**.
3. Mood/personality debug or synchronized UI output — **NOT FOUND**.

Therefore GAO-8A must not claim true terrain-interest recognition or debug UI parity, and it must
not reuse the current flagless `AnticsGoal` look write as its authority mechanism.

### Candidate designs

| Option | Design | Benefit | Strongest objection / failure mode | Gate |
| --- | --- | --- | --- | --- |
| **A — implemented** | Dedicated priority-8 `LOOK`-only `PassiveExpressionGoal`, subclassing `RandomLookAroundGoal` only for SPM noise/readout compatibility; bounded episodes; higher-priority LOOK owners preempt normally | Minecraft's scheduler owns gaze arbitration; coexists with priority-8 `ExploringGoal` because flags are disjoint; never owns MOVE | While active it intentionally replaces SPM's priority-9/10 cosmetic look goals; a bad continuation bound could make gaze look robotic | **IMPLEMENTED / STATIC ACCEPT** |
| B | Keep the flagless observer and call `LookControl` directly | No new scheduled Goal | Repeats the proven `AnticsGoal` flaw: direct writes can overwrite eating, commands, combat, or task gaze; tick order becomes hidden arbitration | **REJECTED** |
| C | Remove/replace SPM's vanilla look goals with opinion-aware variants | Complete control of gaze | Brittle host fork-by-addon; changes behavior when Opinion is off and increases update conflicts | **REJECTED** |

**Switch condition:** choose a host-provided cosmetic attention API instead of Option A if a future
SPM version exposes one with explicit scheduler ownership. No such API is present in v0.86.0.

### Proposed GAO-8A architecture

```text
existing 10-tick ActivityObservationService scan
        ↓ (no second selector scan)
AffectiveState + PersonalityModel + activity observation
        ↓
PassiveExpressionPolicy (pure; no world/action authority)
        ↓
ephemeral PassiveExpressionSnapshot in existing bounded MobExperienceContext
        ↓
priority-8 PassiveExpressionGoal [LOOK only, bounded, interruptible]
        ↓
LookControl only
```

`PassiveExpressionSnapshot` is ephemeral: it is not persisted, not learned, and is invalidated on
freeze/unload. The existing bounded `OpinionExperienceRegistry` remains the lifetime owner; GAO-8A
must not add another UUID map.

Eligibility is limited to `discretionaryIdleCandidate`, `resting`, or `exploring`. Any meaningful
work classification suppresses a new expression episode. Live target/combat is an immediate veto;
higher-priority `LOOK` goals remain the final physical veto through GoalSelector arbitration.

| Signal | GAO-8A expression | Explicit non-claim |
| --- | --- | --- |
| BORED | Shorter bounded cooldown between idle gaze changes | Does not unlock Explore or force an activity switch |
| CURIOUS personality/novelty | Wider random gaze envelope while idle/exploring | Does not detect caves, POIs, resources, or “interesting terrain” |
| SOCIABLE | Prefer a nearby PlayerMob with self `feelingToward > neutral`; bounded radius/cadence | Does not follow, greet, reserve, or require reciprocal companionship |
| STRESSED | Shorter attention holds / more frequent safe-idle vigilance | Does not acquire a target, flee, shelter, or override combat |
| ENGAGED / active work | **No injected gaze**; task Goal retains its own stable LOOK | Does not invent a task target when the executor exposes none |

The initial social radius may reuse SPM's 8-block `LookAtPlayerGoal` range (**Borrowed**): it fits
the existing visual-attention envelope and bounds entity queries; the risk is that a liked mob just
outside eight blocks receives no expression. Scan only when an expression episode is eligible and
stagger by entity id—never per tick. Cadence/hold constants remain tuning candidates, but every
episode must be finite and immediately interruptible.

### Existing `AnticsGoal` interaction

GAO-8A must not mood-wire bunny hopping: jumping can change collision, combat pursuit, fall timing,
and navigation, so it is not passive expression. Crouch mimicry may remain, but when Opinion is
enabled its direct `LookControl` write must not bypass the scheduled LOOK owner. Preserve the exact
legacy path when `opinion.enabled=false` for GAO-PARITY. This is a narrow prerequisite inside Task
40, not authorization for a broader Antics redesign.

### Pre-implementation behavioral prediction (MAIBS-1)

| Layer | Result |
| --- | --- |
| Intended behavior | Mood/personality becomes visible as harmless head attention |
| Implemented mechanism (planned) | Bounded priority-8 LOOK-only episodes reading an ephemeral policy snapshot |
| Predicted behavior | Idle/resting mobs occasionally look around differently; curious mobs may glance more broadly while exploring; liked nearby PlayerMobs receive gaze; work/combat gaze wins immediately |
| Failure/weirdness | Repetitive head snapping; several mobs staring at the same friend; expression never runs because a host cosmetic LOOK goal is already active; stale snapshot produces up to one cadence of mismatched expression |
| Confidence | Architecture `CODE_CONFIRMED`; exact visual cadence `UNVERIFIED` until runtime |

**Acceptable stepping stones:** bounded repetition and occasional shared social gaze. **Architecture
defects:** any navigation write, persistent LOOK capture, visible objective label, scheduler rescan,
or one tick of expression surviving a higher-priority LOOK owner.

**Falsifying runtime experiment:** spawn idle, exploring, eating, commanded, mining, and fighting
PlayerMobs together; mood-seed each expression profile; observe head motion/objective readout for
several minutes. A task gaze delayed or overwritten falsifies the design. This is a targeted
`RUNTIME_QUESTION`, not automatically authorized.

### Task 40 — GAO-8A passive physical expression (`COMPLETE / STATIC ACCEPT`)

| Field | Contract |
| --- | --- |
| Owner | Agent_Codex |
| Dependencies | GAO-0, GAO-1, GAO-6, GAO-7 complete |
| Files/systems | New pure expression policy/snapshot + LOOK-only goal; existing observer cadence/context; narrow `AnticsGoal` non-interference seam; focused tests; RFC evidence |
| Constraints | No MOVE/navigation/jump/world interaction; no second selector scan/map; no OpinionMemory mutation; no new activity/intent; no UI/network sync; Opinion-off parity |
| Must happen | BORED/STRESSED alter bounded gaze cadence; CURIOUS alters non-semantic gaze breadth; SOCIABLE can select a nearby self-liked PlayerMob; higher-priority LOOK owner preempts; expression remains ObjectiveReadout noise |
| Must not happen | Expression starts/cancels work, appears as an objective, overwrites combat/eat/command/task gaze, scans terrain/resources, or changes behavior with Opinion disabled |
| Tests | Pure policy bounds/determinism; zero-signal/neutral behavior; activity eligibility; self-liked vs neutral/hostile social candidates; Goal flags/priority/finite continuation; objective-noise inheritance; Antics opinion-on guard + opinion-off parity; registry/unload invalidation |
| Verification | Focused tests, full suite, clean build, `git diff --check`, post-implementation MAIBS; no Minecraft launch without separate approval |

### Accepted implementation

Implemented: passive expression is LOOK-only scheduler output; Option A; no true terrain salience in
8A; ENGAGED uses non-interference; no bunny-hop mood wiring; no second observer scan.

Why: it uses Minecraft's real flag arbitration, preserves SPM authority, and keeps expression from
silently becoming another decision system.

Implementer/reviewer: Agent_Codex. The user authorized GAO-8A after the hard product boundary and
recommended Option A were recorded.

Remaining objection: visual naturalness and precise cadence cannot be proven statically. This does
not weaken the hierarchy gate, but may create a narrow post-build runtime tuning question.

Status: `IMPLEMENTED / STATIC ACCEPT`.

---

## Topic: Observable expression — GAO-8B read-only inspection

**Status:** `TASK 42A IMPLEMENTED / STATIC ACCEPT`; `TASK 42B IMPLEMENTED / STATIC ACCEPT` —
causal evidence is structured and atomic, and PD-GAO-14 now locks entry/access/refresh/authority

### Goal and boundary

Make the AI understandable: expose the already-existing Personality, affect, learned preferences,
discretionary authority, and causal decision history to a player/operator without turning
observation into behavior.

**User product direction (PD-GAO-15):** GAO-8B is an explanation surface, not a telemetry dump.
For every claimed decision it should answer, from evidence captured at decision time:

1. **What am I doing now?**
2. **Why did this option win?**
3. **Why did the alternative lose or why was no option chosen?**
4. **Did the intent reach the executor, or where did handoff stop?**
5. **What exact outcome occurred and what, if anything, was learned?**

Raw channel values remain available under progressive disclosure, but the primary screen must not
make the player mentally reverse-engineer utility arithmetic.

| GAO-8B may | GAO-8B must not |
| --- | --- |
| Build an immutable bounded snapshot on an explicit inspection request | Call `contextFor`, create a context, learn, rescore, issue an intent, or refresh affect |
| Render an addon-owned client screen | Patch SPM's inventory layout or append debug state to the world billboard/objective label |
| Show current affect/personality, existing activity/environment opinions, authority phase, and trace | Expose live mutable maps, world references, arbitrary NBT, inventory, or a control/edit button |
| Validate target/range/access server-side and fail closed | Trust a client-supplied UUID, inspect an unloaded entity, or crash when SPM is absent/changed |

`CODE_CONFIRMED` current evidence:

- SPM's `PlayerMobScreen` is a Creative inventory/editor with a fixed disposition panel and
  objectives gutter. It exposes no addon panel API; integrating there requires a client Mixin into
  host layout/lifecycle code.
- Scavenger has no networking implementation, per-mob Opinion screen/payload, SPM screen Mixin, or
  keybind/client command (four `NOT FOUND` probes).
- `OpinionExperienceRegistry.find(UUID)` is the existing non-allocating live lookup. In contrast,
  `contextFor` allocates/rehydrates and is forbidden on a readout path.
- `AffectiveState`, immutable `PersonalityModel`, memory snapshots, director authority, and the
  current 24-**entry** bounded `OpinionDecisionTrace` contain useful read-only data. No second
  activity/GoalSelector scan is necessary. The trace does **not yet** satisfy the causal explanation
  contract below.
- SPM remains optional through `PlayerMobs.isPlayerMob`; the addon already depends on Fabric API,
  so a project-owned request/response payload does not require another dependency.

### Entry-point alternatives

| Option | Benefit | Strongest objection / failure mode | Verdict |
| --- | --- | --- | --- |
| A — optional button/panel in SPM `PlayerMobScreen` | Most discoverable while viewing the mob; target already known | Version-locked `@Pseudo` UI Mixin, Creative-only host screen, fixed-layout collisions, silent loss after host rename | Keep as a later optional adapter only if SPM exposes a supported screen-extension API |
| **B — addon-owned screen opened by a configurable inspect key while targeting a PlayerMob** | No host-screen/layout mutation; screen and packet contract are owned here; SPM-absent path can fail closed | Keybind discovery/conflicts; requires one bounded request/response pair; server must reject stale/spoofed targets | **SELECTED / LOCKED (PD-GAO-14)** |
| C — operator command/chat dump | Lowest code and no custom screen | Poor readability, no real UI, long traces spam chat, awkward target selection | Diagnostic fallback, not the GAO-8B product |

**D-GAO-039 (`LOCKED`):** GAO-8B reads one immutable on-demand view from
`OpinionExperienceRegistry.find`; it never allocates state, invokes policy, scans goals/world, or
streams background updates. A missing context renders `No Opinion state yet` rather than creating
neutral memory. Refresh is explicit; stale-but-labelled data is safer than hidden periodic work.

**D-GAO-040 (`LOCKED`):** the frontend is an addon-owned screen and bounded common
DTO. The server resolves the supplied entity id in the requesting player's level, validates a live
PlayerMob, distance, and Creative-or-operator access policy, and copies only finite
enum/channel/trace data. Client-only
screen types stay out of common packet/snapshot signatures. Responses carry a request id/entity id
so a late response cannot populate a different or already-closed inspection.

### GAO-8B-B1 — current trace cannot prove its own explanation (`CODE_CONFIRMED`)

The existing D-GAO-025 implementation is useful logging, but it is not an end-to-end causal record:

- `recordScores` and `SELECT` call `traceIntentId()` **before** `issuePending` creates the new
  intent. A fresh decision therefore records a null id; while another intent exists it may inherit
  that previous id.
- `SCORE` stores only `total`, `preference`, and `repetition` in a string. The real
  `ActivityUtilityBreakdown` also contains base usefulness, boredom/stress/novelty fit, recent
  reward, failure pressure, and cost; those causes are discarded.
- The ring bounds 24 individual events, not 16–32 decisions. A multi-stage decision can lose its
  scores while retaining its later terminal, producing a misleading partial chain.
- Early exits such as disabled/frozen/mandatory authority/no scoring result do not consistently
  leave a structured current suppression reason when no intent exists.
- Current tests assert that stages exist and that ADOPT/EXECUTOR share the issued intent id. They do
  not assert one correlatable `SCORE → SELECT → INTENT → ADOPT → EXECUTOR → TERMINAL` chain.

This is not currently proven to change mob behavior. It is an **observability architecture defect**:
a GAO-8B UI would either expose gaps honestly or invent a causal story after the fact.

| Repair option | Benefit | Strongest objection / failure mode | Verdict |
| --- | --- | --- | --- |
| Parse strings and recompute “why” from current mood/memory | Smallest diff | State may have changed; explanation can disagree with the historical decision; parsing is brittle | **REJECT** |
| Keep event ring, add a decision id and every score component, raise entry cap | Incremental | Entry eviction can still cut one decision in half; capacity means events, not decisions | Acceptable fallback only if decision records prove too invasive |
| **Bounded decision records with structured candidates + transitions** | One causal owner; retains or evicts a whole decision; UI does not infer history | Requires a narrow trace-model migration and lifecycle tests before UI work | **RECOMMENDED** |

**D-GAO-041 (`IMPLEMENTED / STATIC ACCEPT`):** explainability evidence is captured at the decision/transition that
created it, never recomputed from later affect or memory. Each bounded decision record owns a local
monotonic `decisionId`, full immutable candidate breakdowns, selection/suppression reason, optional
intent id, and later claim/yield/executor/terminal transitions with exact causes. The intent carries
its originating decision id so later callbacks cannot attach to the wrong evaluation.

**D-GAO-042 (`IMPLEMENTED / STATIC ACCEPT`):** retention is bounded by complete **decisions**, not raw events. Eviction
removes one oldest complete record atomically; no per-intent/global lookup map or minted persistent
identity is added. A separately stored current `DecisionDisposition` may state disabled, frozen,
mandatory authority, no candidates, below threshold, commitment hold, switch-margin hold, pending,
running, or terminal. It is observability only and grants no scheduler authority.

### Proposed readout

| Section | Initial contents | Explicit omission |
| --- | --- | --- |
| Summary | Plain-language “Doing / Because / Held by / Last outcome” plus mob name/id and Opinion state | No scheduler rescan, inferred history, or claim-control buttons |
| Affect | Engagement, boredom, satisfaction, stress, novelty, meaningful-progress age | No derived value edits or forced mood |
| Personality | Six immutable GAO-7 traits | No trait sliders; SPM remains disposition owner |
| Preferences | Existing activity and environment entries; bounded place/entity counts | No zero-entry creation; raw place/entity identities deferred unless a debugging need proves value |
| Why | Whole bounded decisions: candidates/components → winner/suppression → handoff → terminal/learning eligibility | No string parsing, perpetual log, disk export, or unbounded history |

### Access product decision — PD-GAO-14

| Option | Compatibility/safety | Product effect |
| --- | --- | --- |
| **Creative/operator only — selected for gen-1** | Matches the diagnostic nature and SPM's Creative editor; avoids exposing internal relationship/place history in multiplayer | Survival non-operators cannot inspect |
| Any nearby player | Most accessible | Turns debug state into gameplay information and needs a deliberate privacy/gameplay policy |
| Server-configurable disabled / privileged / all | Most flexible | Adds configuration, synchronization, and test surface before the basic screen is proven |

**LOCKED product contract (2026-08-11):**

- A client-configurable **Inspect Opinion** key acts only while the crosshair targets a PlayerMob.
- Access is allowed when the requesting player is **Creative OR a server operator**. The server is
  authoritative; a client-side key or screen cannot grant access.
- Scavenger owns the screen. No Mixin is added to SPM's Creative inventory screen.
- Each open or explicit **Refresh** performs one bounded request. The server revalidates the live
  target and permission, then returns one immutable bounded snapshot. There is no per-tick or
  background synchronization.
- The inspector is strictly read-only: no sliders, mood forcing, opinion edits, activity controls,
  or other AI mutation paths.

Revisit server-configurable/all-player access only after the readout has a deliberate player-facing
design rather than a privileged diagnostic surface.

### Behavioral prediction and adversarial review (MAIBS-1)

```text
player targets live PlayerMob
        → presses inspect key
        → server validates target/range/permission
        → copies existing bounded state
        → client opens static read-only screen
        → mob's GoalSelector and physical behavior continue unchanged
```

| Scenario | Must happen | Must not happen |
| --- | --- | --- |
| Context exists | Snapshot displays the same stored channel/trait/opinion/trace values | Opening or refreshing changes any value |
| No context / Opinion disabled | Clear unavailable/disabled state | `contextFor` allocates neutral state |
| Target moves/dies or id is spoofed | Server rejects; client keeps/clears labelled stale view safely | Inspect another/unloaded entity or throw |
| SPM absent/API changed | Feature no-ops or reports unavailable once; addon remains loadable | Hard client/server classloading failure |
| Screen closes before reply | Late request id is discarded | Old data opens or contaminates the next target |
| Many mobs/players | Work occurs only per explicit bounded request | Per-tick sync, Goal scan, or retained per-viewer map |

Predicted weird behaviors: values can be one request old (`ACCEPTABLE_STEPPING_STONE`); a mob may
change objective while the static screen is open (`ACCEPTABLE_STEPPING_STONE`, explicit Refresh);
a rejected target can make the screen appear briefly unavailable (`RUNTIME_QUESTION`). Any change
in physical behavior, memory, or scheduler state is an `ARCHITECTURE_DEFECT`.

**MAIBS preflight:** `PASS — BEHAVIORALLY PLAUSIBLE` for the proposed contract. It introduces no
Goal, flag, navigation, scan, inventory/world interaction, or authority path. Runtime GUI layout
and request/response timing remain `UNVERIFIED` until implementation and a separately approved
targeted launch.

### Task 42A — GAO-8B causal trace repair (`IMPLEMENTED / STATIC ACCEPT`)

| Field | Contract |
| --- | --- |
| Dependencies | D-GAO-025 implementation; GAO-3/4 utility and authority complete |
| Scope | Replace the event-string ring with bounded whole-decision records; carry decision id through intent/handoff; structured score/suppression/terminal evidence; focused migration tests; no UI/network |
| Must happen | One query can distinguish policy winner, abstention/hold, unclaimed intent, failed handoff, running executor, and exact terminal without consulting current state |
| Must not happen | Trace recomputes decisions, changes scorer/director results, retains live entities, grows per tick without bound, or grants activity authority |
| Tests | Full component preservation; fresh/overlapping decision correlation; abstain/commitment/switch/no-candidate/mandatory reasons; complete-record eviction; intent lifecycle; neutral behavior parity |
| Verification | Focused tests, full suite, clean build, RET-1/static MAIBS; no runtime required for pure trace semantics |

Implementation evidence (2026-08-11): `OpinionDecisionTrace` now stores at most 24 whole
decisions, protects live intent-origin records while completed evaluations are available for
eviction, and removes one entire record at a time. `DiscretionaryIntent.decisionId` carries a local
monotonic evaluation identity that exists before and remains distinct from `intentId`. Candidate
records preserve the complete immutable `ActivityUtilityBreakdown`, executor/adoption suppressions,
and exact decision disposition/cause. The originating record receives SELECT, INTENT, ADOPT,
EXECUTOR, CLAIM, YIELD and terminal lifecycle/cause transitions. Explore/Rest terminal emitters also
attach the actual bounded activity/place/environment before/after learning receipt before closing
authority, so Task 42B need not infer learning from later memory. No scorer, threshold, intent,
GoalSelector, navigation, or authority rule changed.

Focused causal/retention tests, all 628 tests, and `gradlew.bat clean build` pass with zero
failures/errors/skips. Final artifact: `build/libs/spmscavenger-1.9.4.jar`, SHA-256
`91321D9C8CD14BFA5581E52BF3D24269118182A877736831CC1BA4CB1C41CBEC`.
Static MAIBS: `PASS — BEHAVIORALLY PLAUSIBLE`; physical behavior parity is `CODE_CONFIRMED`, while
runtime/performance remain `UNVERIFIED` because no Minecraft launch or profiler run was authorized.

### Task 42B — GAO-8B understandable Opinion inspector (`IMPLEMENTED / STATIC ACCEPT`)

| Field | Contract |
| --- | --- |
| Dependencies | GAO-0 through GAO-9 and RET-GAO-1 complete; Task 42A static-accepted; D-GAO-039/040 and PD-GAO-14 locked; D-GAO-043 shelter-authority semantics implemented; D-GAO-044 shelter readout contract reviewed before causal UI handoff |
| Scope | Pure immutable `OpinionReadoutSnapshot`; plain-language explanation projection over Task 42A evidence; non-allocating factory; bounded request/response payload; addon-owned client screen/keybind; focused tests; documentation |
| Constraints | Stock/optional SPM; configurable inspect key; crosshair-targeted PlayerMob; Creative OR operator access; no host-screen or objective-billboard mutation; no state allocation/write; manual refresh only; common APIs expose no client types; access/range/live target validated server-side; strictly read-only |
| Must happen | A permitted player can answer what/why/alternative/handoff/outcome/learning from captured evidence, inspect raw values secondarily, and manually refresh |
| Must not happen | Inspecting creates state, changes AI, leaks an unbounded payload, trusts spoofed ids, or crashes without SPM |
| Tests | Snapshot exactness/non-allocation/immutability/bounds; permission/range/type/death rejection; payload round-trip and caps; late-response token; optional-SPM path; screen smoke/static layout; negative Goal/config/state-write scan |
| Verification | Focused tests, full suite, clean build, final-JAR packet/client packaging, static MAIBS; runtime requires separate approval |

#### GAO-8B shelter readout contract — D-GAO-044 (`LOCKED`)

**Status:** `PROPOSED` — blocks misleading Task 42B copy; does not authorize shelter physics changes

**Evidence (2026-08-12, user runtime report + code inspection):** mobs still choose trees/roof
eaves while a house exists, often reach shelter only via beds, and may reopen doors and leave under
another shelter cycle. These are primarily **selection/navigation** defects tracked under vanilla
progression `SCR-2R2` (structural satisfaction), `SCR-2R3` (interior capture + door arbitration),
and `SCR-2R5` (authority envelope). They nevertheless produce **Opinion observability failures**
if the inspector implies the mob "wanted" to rest in a tree or voluntarily left a house because
Explore scored higher.

**Locked separation (extends D-GAO-043):**

| Signal | Meaning for GAO-8B | Must not imply |
| --- | --- | --- |
| `ActivityClass.SHELTER_HOLD` | Mandatory nighttime shelter authority is occupying the scheduler | Discretionary REST choice; Opinion selected shelter |
| `Observation.resting=true` | Affective/rest claim is live (sleep or shelter-recovery stand) | That discretionary Explore/Rest was blocked unfairly |
| `DecisionDisposition.MANDATORY_AUTHORITY` / shelter suppression | Director abstained because safety envelope holds | "No opinion yet" or a lost utility contest |
| Counterfactual REST/EXPLORE scores while `SHELTER_HOLD` | Diagnostic only; labelled **non-causal** | That the mob would have picked them if shelter were absent |

**Proposed readout strings (gen-1 examples):**

```text
Doing: Seeking night shelter (mandatory)
Because: Dusk shelter authority is active — not a discretionary mood choice
Held by: SHELTER_HOLD / APPROACHING|SETTLED|RETURNING
Resting: yes/no (independent affective claim)
Suppressed: Explore intent would have ranked #1 but mandatory shelter blocks discretionary work
```

**Task 42B scope addition (read-only):** copy bounded fields from `ShelterNightAuthority.Hold`
when present (`phase`, `commitmentId`, anchor block if already exposed elsewhere). No second
GoalSelector scan; no shelter policy execution from the UI.

**Alternatives:**

| Option | Benefit | Failure mode | Verdict |
| --- | --- | --- | --- |
| Hide shelter entirely in Opinion UI | Smaller screen | User cannot tell mandatory shelter from boredom/REST failure — exactly the current confusion | **Rejected** |
| Recompute "what would Opinion have chosen?" on open | Feels explanatory | Violates D-GAO-041; state may have changed | **Rejected** |
| **Snapshot authority + suppression reason + optional shelter phase** | Truthful, side-effect free, matches D-GAO-039/041 | Requires one extra DTO section and focused snapshot tests | **Recommended** |

**Must happen:** opening the inspector during `SHELTER_HOLD` shows mandatory suppression, not a
discretionary winner. **Must not happen:** UI labels tree/eave shelter as Opinion REST; UI shows
Explore as the causal winner while shelter MOVE is active.

**Cross-RFC:** physical repair and runtime falsification remain in
`RFC-VANILLA-AUTONOMOUS-PROGRESSION.md` (`SCR-2R2+` runtime matrix). File **RQ-GAO-SHELTER-01**
only to verify inspector copy against a known `SHELTER_HOLD` session — not to re-litigate tier
ranking.

---

## Topic: GAO-4R1 — Adoption vs continuation stabilization

**Author:** Agent_Cursor (brainstorm evidence, 2026-08-12)

**Status:** `LOCK RECOMMENDED` — **blocks GAO-10 implementation**; does not require Minecraft runtime

### Observable problem

GAO-4R fixed a real failure mode: REST could win utility while `CampfireGoal` was not adoptable,
producing misleading `PENDING` intents and false "I want to rest" causality. The fix correctly
uses `ActivityAdmission.adoptionReady()` only at **selection** time.

A third discretionary activity exposes a second failure mode if we stop there:

```text
SOCIAL is RUNNING (greeting Bob)
        ↓
next tick: no *other* eligible greet target / fresh adoption probe fails
        ↓
director treats SOCIAL as globally not adoptable
        ↓
running greet loses authority mid-sequence   ← must not happen
```

**Borrowed lesson (`CONFIRMED`, MI-14C2-R2 / cave handoff):** authority protecting a live
continuation must not expire merely because a fresh admission probe would fail. The continuation's
own validity predicate owns RUNNING retention.

### Code evidence (`CONFIRMED`)

| Finding | Path |
| --- | --- |
| Pairwise yield flags only cover EXPLORE↔REST | `DiscretionaryDirectorState.updateYieldRequests()` — no third activity hook |
| Yield API is activity-pair specific | `DiscretionaryAuthority.mustYieldDiscretionaryRest/Explore()` |
| Selection suppresses on `!admission.adoptionReady()` | `DiscretionaryDirectorState.evaluateCandidates()` |
| `ActivityAdmission` has no continuation field | `ActivityAdmission.java` — `executorPresent` + `adoptionReady` only |
| Running vs pending tracked separately | `pendingIntent` / `runningIntent` in `DiscretionaryDirectorState` |

### Proposed contract

Split executor readiness into two probes per activity:

| Probe | Used when | Question |
| --- | --- | --- |
| **Adoption** | utility competition, new `PENDING` issue | Can this activity **start now** from idle/discretionary context? |
| **Continuation** | incumbent `ADOPTED`/`RUNNING` retention | Is the **current** intent/execution still valid? |

```text
evaluateCandidates()
  → suppress candidate when !adoptionReady()

retainIncumbent()
  → keep RUNNING when continuationReady(incumbentIntent)
  → do NOT re-check adoptionReady() against a challenger world
```

**GAO-4R1 must happen before GAO-10:**

1. Document continuation rules for EXPLORE and REST (retrofit tests).
2. Introduce `ActivityContinuation` (or extend `ActivityAdmission` with optional incumbent context).
3. Replace pairwise yield booleans with a generic yield request record.

### Generic yield API (`PROPOSED`)

```text
YieldRequest {
  UUID releasingIntentId;
  DiscretionaryActivity incumbent;
  DiscretionaryActivity challenger;
  long requestedAtTick;
}

requestYield(incumbentIntent, challengerActivity)
  → consumer goals poll mustYield(mobId, incumbentActivity)
```

**Why now:** adding `socialYieldRequested` + three pairwise matrices scales as O(n²) and will be
wrong for EXPLORE↔SOCIAL and REST↔SOCIAL pairs.

| Option | Benefit | Failure mode | Verdict |
| --- | --- | --- | --- |
| Keep pairwise flags; add SOCIAL pairs | Minimal diff | 6 directed pairs at 3 activities; untestable matrix | **Rejected** |
| Generic yield + activity-tagged consumers | Scales to N activities; one test surface | Requires REST/EXPLORE consumer refactor | **Recommended** |
| No yield; rely on Goal priority only | No director API | Equal-priority scheduler fights; D-GAO-018 violated | **Rejected** |

### Must happen

- Running REST survives `SCAN_COOLDOWN` / transient `NO_CAMPFIRE_ITEM` on **adoption** probe while
  `RestSessionClaim` or adopted campfire execution remains valid.
- Running EXPLORE survives explore-readiness dip while expedition is live (existing commitment
  semantics preserved).
- Future running SOCIAL survives "no second eligible target nearby" while greet sequence is live.

### Must not happen

- Fresh adoption failure terminates an unrelated valid RUNNING intent.
- Continuation probe scans unbounded world state or force-loads chunks.
- GAO-10 ships on top of pairwise-only yield API.

### Dependencies

| Prerequisite | Status |
| --- | --- |
| GAO-4R `ActivityAdmission` | `IMPLEMENTED` |
| GAO-4 intent lifecycle | `IMPLEMENTED` |
| GAO-10 | blocked until GAO-4R1 accepted |

**Implementation authorization:** none until D-GAO-050/051 **LOCKED** and Task 43 brief accepted.

### MAIBS-1 — behavioral prediction (GAO-4R1, pre-implementation)

**Gate (predicted):** `PASS — BEHAVIORALLY_PLAUSIBLE` once continuation probes ship with tests.

| Layer | Intended | Mechanism | Predicted observable | Confidence |
| --- | --- | --- | --- | --- |
| REST continuation | Campfire REST survives transient `NO_CAMPFIRE_ITEM` / scan cooldown on **adoption** probe | `RestContinuation.inspect(incumbentIntent, claim, campfire)` | Mob keeps sitting at adopted campfire through item-scan gaps; director trace `RETAINED` | `CODE_CONFIRMED` path exists for empty scoring + running intent |
| EXPLORE continuation | Live expedition survives explore-readiness dip | `ExploreContinuation.inspect(expedition != null, discretionary path)` | Leader keeps walking waypoints when idle counters dip mid-expedition | `CODE_CONFIRMED` (`ExploringGoal` commitment + cave-handoff lesson) |
| Future SOCIAL continuation | Greet sequence survives "no second GREET target" on adoption scan | Continuation uses bound `SocialIntent` + greet predicates (B-50) | Bob greet completes crouch/gift even if Alice enters range later | `INFERRED` — requires GAO-10 |
| Yield generalization | Third activity without O(n²) flags | `YieldRequest` polled by REST/EXPLORE/SOCIAL consumers | Voluntary handoff when switch margin + commitment satisfied | `INFERRED` |

**Weird behaviors (pre-mortem):**

| # | Risk | Class | Mitigation |
| --- | --- | --- | --- |
| 1 | Continuation probe re-scans world every tick | `ARCHITECTURE_DEFECT` if unbounded | Continuation reads **bound intent context** only (claim, expedition ref, social target id) |
| 2 | Adoption failure clears `runningIntent` when scoring empty | Would be `ARCHITECTURE_DEFECT` | **Already avoided** — `NO_CANDIDATES` early-return retains running intent (B-52); add explicit tests |
| 3 | Generic yield fires while incumbent in mandatory shelter hold | `ARCHITECTURE_DEFECT` | Yield consumers must respect `SHELTER_HOLD` + mining lease (existing guards) |

**Falsifying runtime probes (`RUNTIME_QUESTION`, post-Task 43):** REST adopted then campfire item briefly absent — session completes; expedition mid-route with boredom dip — no false yield; inspector shows `RETAINED` when adoption suppressed but continuation valid.

### API sketch (`LOCK RECOMMENDED`)

```text
ActivityContinuation {
  boolean continuable();
  ActivityAdoptionBlocker blocker();  // when false
  String detail();
}

ActivityAdmissions {
  ActivityAdmission explore;
  ActivityAdmission rest;
  // GAO-10: ActivityAdmission social;
}

// Adoption: existing ActivityAdmission.adoptionReady() — selection only
// Continuation: new probe on incumbent intent + executor context

DiscretionaryYield {
  UUID releasingIntentId;
  DiscretionaryActivity incumbent;
  DiscretionaryActivity challenger;
  long requestedAtTick;
}

DiscretionaryAuthority.requestYield(mobId, incumbentIntentId, challenger)
DiscretionaryAuthority.mustYield(mobId, incumbentActivity)  // replaces pairwise booleans
```

**Task 43 (proposed):** extend `ActivityAdmission`/`ActivityAdmissions`; add continuation inspectors for REST + EXPLORE; generic yield; tests for B-52 + REST/EXPLORE continuation; **no SOCIAL enum yet**.

---

## Topic: GAO-10 — Discretionary Social Choice & Social Intent

**Author:** Agent_ChatGPT (user-provided design, 2026-08-12)

**Status:** `PROPOSED / DISCUSSION` — **do not implement** until **GAO-4R** and **GAO-4R1**
(adoption-vs-continuation stabilization) are accepted. Next major Opinion capability after the
EXPLORE + REST discretionary pair.

### Goal

Extend the discretionary Opinion decision space from:

```text
EXPLORE
REST
```

to:

```text
EXPLORE
REST
SOCIAL
```

**SOCIAL** means: the PlayerMob has discretionary freedom and *subjectively wants* to spend time
interacting with another entity/person.

This is an **optional desire**, not friendship authority, command authority, mandatory behavior, or
a replacement for SPM social AI.

### Architectural rule (hard)

**Do not** create a generic mega `SocializeGoal` merely to satisfy Opinion.

Opinion should decide: *"I feel like doing something social."*

Existing SPM/social executors should continue to decide and perform the actual physical interaction
wherever possible.

```text
Personality / Affect / Opinions
        ↓
DiscretionaryActivityDirector
        ↓
     SOCIAL
        ↓
Social Target Resolver
        ↓
SocialIntent(target)
        ↓
Existing finite SPM/social executor
        ↓
Physical interaction
        ↓
Terminal evidence
        ↓
Social experience / learning
```

### Existing intelligence to activate (reuse, do not fork)

| System | Role in GAO-10 |
| --- | --- |
| `PersonalityModel.sociability` | Trait channel for activity-level social desire |
| SPM `feelingToward` | Relationship authority / host truth — **read-only** |
| `EntityOpinionMemory` | Supplemental learned entity affinity |
| `SpmEntityOpinionBridge.utilitySupplement(...)` | Soft social utility contribution at the existing seam |
| `AffectiveState` | Boredom / engagement / stress / satisfaction |
| `OpinionDecisionTrace` | Causal selection/adoption/terminal explanation |
| GAO-4R `ActivityAdmission` | `AVAILABLE` / `ADOPTABLE` distinction |

**Authority boundary (`LOCKED` direction):** SPM relationship state remains authoritative. Opinion
**must never** write or replace `feelingToward`. `EntityOpinionMemory` is subjective supplemental
preference only and must not override hostility, player authority, safety, or SPM relationship
legality.

### Activity vs target separation (hard)

Keep **one** finite discretionary activity:

```text
DiscretionaryActivity.SOCIAL
```

Do **not** expand the enum into per-target values (`SOCIAL_WITH_BOB`, `SOCIAL_WITH_ALICE`, …).
Target identity belongs to a separate intent record:

```text
SocialIntent {
  UUID intentId;
  UUID originDecisionId;
  UUID targetEntityId;
  float selectedActivityUtility;
  float targetScore;
  String targetRationale;          // trace/debug only; bounded length
  long issuedAtGameTime;
  long expiresAtGameTime;          // B-48: ≤ greet worst-case (~400t from issue)
}
```

Exact field set is **lock-ready** for gen-1; `expiresAtGameTime` mandatory (B-47/B-48).

**Gen-1 adapter (`LOCK RECOMMENDED` — B-49):** mixin or thin gate on `FriendlyGreetGoal.canUse()` —
only when mob has active Opinion `SocialIntent` for target `T`, allow greet start if
`reactionToward(T)==GREET`; otherwise defer to SPM's native `nearestWhereReaction` path when Opinion
abstains or has no intent.

GAO-10 must explicitly separate three questions — **do not** mix them into one giant utility function:

1. **Do I want to socialize?** (activity score)
2. **If yes, who do I want to socialize with?** (target score)
3. **Is there a real executor capable of interacting with that target now?** (admission)

**Proposed pipeline:**

```text
SOCIAL activity utility
        ↓
SOCIAL becomes desirable
        ↓
enumerate bounded eligible social targets
        ↓
target ranking
        ↓
admission validation
        ↓
issue target-specific SocialIntent
```

This allows:

```text
SOCIAL utility high
but
no eligible person nearby
        ↓
SOCIAL suppressed / not adoptable
```

instead of producing a permanently pending social intent with no valid target.

**Strong constraint:** relationship/entity affinity should **not** entirely determine whether the
mob wants `SOCIAL` as a category. Prefer two levels:

| Level | Question |
| --- | --- |
| **Activity score** | "Do I feel social?" |
| **Target score** | "Who do I want to spend time with?" |

This avoids Bob's friendship score artificially making the entire `SOCIAL` activity globally
attractive when Bob is not a valid target.

### SOCIAL utility model (`LOCK RECOMMENDED` — B-54)

**Activity score** (`scoreSocial`) — symmetric to EXPLORE/REST; **no per-target affinity**:

```text
base social usefulness
+ PersonalityModel.sociability
+ affective social desire / boredom relief
+ OpinionMemory.memoryOf(SOCIALIZING)
- repetition / recent negative SOCIAL activity outcome
- cost
```

**Target score** (resolver stage only) — who to greet:

```text
SpmEntityOpinionBridge.utilitySupplement(target)
+ SPM feelingToward (read-only)
+ EntityOpinionMemory supplemental affinity
+ distance tie-breaker
- recent per-target repetition
```

Do **not** fold `utilitySupplement(target)` into `scoreSocial()` — that reintroduces Bob-bias
making the whole SOCIAL category attractive when Bob is offline (B-54).

### Gen-1 executor scope (`PROPOSED` — requires SPM source research)

Research pinned SPM source and choose **one** finite existing social behavior as the first executor.
Do **not** support every social behavior in GAO-10 gen-1.

#### SPM executor survey (`CONFIRMED` — pinned reference `SocialPlayerMobs-v0.86.0`)

| Goal | Finite terminal? | Target ownership | MOVE/LOOK | Relationship gate | Verdict for gen-1 |
| --- | --- | --- | --- | --- | --- |
| **`FriendlyGreetGoal`** | **Yes** — `Phase.DONE` after FOLLOW→CROUCH→GIFT/FETCH; disengage cooldown 200–400 ticks | **Self-selects** via `nearestWhereReaction(GREET, range)` | MOVE+LOOK | `Reaction.GREET`; combat self-gate | **Recommended** with `SocialIntent` adapter |
| `FollowLovedOneGoal` | No — catch-up while `tooFarFrom`; releases when close | `findFollowTarget()` | MOVE+LOOK | feeling ≥ LOVE | **Reject** — travel companion, not discretionary visit |
| `StayNearGoal` | No — tether while beyond radius | `StayAnchor` (player command) | MOVE+LOOK | command authority | **Reject** — not discretionary desire |
| `SkepticalWatchGoal` | Short LOOK burst | reactive target | LOOK | hostile/skeptical | **Reject** — reflex, not socialize |
| `DoorOperationGoal` | Finite helper | social context | MOVE+LOOK | helper reflex | **Reject** — not social content |

**Recommended gen-1 path (`INFERRED`, pending product ack):**

```text
SocialIntent(targetUuid)
        ↓
SocialGreetAdmission.inspect(target)   // Reaction.GREET + distance + loaded + ...
        ↓
minimal adapter (mixin or gate on FriendlyGreetGoal)
        ↓
only start/continue when intent target matches + Reaction.GREET holds
        ↓
terminal → SOCIAL_COMPLETED / TARGET_LEFT / COMBAT_INTERRUPTED / ...
        ↓
ExperienceKind.SOCIAL_INTERACTION + ActivityKind.SOCIALIZING
```

**Adapter necessity (`CONFIRMED`):** `FriendlyGreetGoal.canUse()` always picks
`mob.nearestWhereReaction(Reaction.GREET, range)` — it cannot consume an Opinion-issued target
without a small bridge. The bridge should **not** reimplement greet phases; it should constrain
*when* the host goal may start and *which* `LivingEntity` is eligible.

**Existing addon hooks:** `FriendlyGreetShelterHoldMixin` already participates in mandatory
shelter envelope — any GAO-10 adapter must respect the same `SHELTER_HOLD` authority (D-GAO-043).

**Anti-duplication (`CONFIRMED`):** `AnticsGoal` explicitly avoids crouch-bowing because SPM's
`FriendlyGreetGoal` owns that gesture — discretionary SOCIAL must not stack cosmetic expression on
the same greet.

Inspect actual lifecycle: `canUse`, `start`, continuation, target ownership, MOVE/LOOK flags,
completion, interruption, relationship requirements, player-command interaction, combat
interaction.

If no existing executor cleanly supports discretionary `SOCIAL`, discuss the **smallest**
adapter/executor necessary. Do not immediately invent a large new Goal.

### Social target resolver (`PROPOSED` — bounded)

Answer: *Is this entity a legitimate social target right now?*

| Check | Source |
| --- | --- |
| alive + loaded + same dimension | vanilla entity state |
| within bounded greet range | config constant; no chunk forcing |
| `mob.getTarget() == null` | combat yields |
| no conflicting player command / stay anchor | `PlayerMobs` stay/attack order probes |
| SPM `reactionToward(target) == GREET` | **requires B-55** `PlayerMobs.reactionToward` bridge (`NOT FOUND` in addon today — only `feelingToward` exists) |
| optional: `feelingToward` above hostility threshold | supplemental, not sole gate |
| executor-specific: target still in range at adoption | `FriendlyGreetGoal` distance + reaction continuation |

**Candidate enumeration:** bounded nearby set from SPM's existing reaction scan pattern
(`nearestWhereReaction`), not a world-wide entity opinion scan. Rank with activity score separate
from `SpmEntityOpinionBridge.utilitySupplement()` at target-scoring stage only.

**Must not happen:** unbounded `EntityOpinionMemory` iteration; force-loaded chunks; opinion target
overrides SPM `Reaction.WATCH` / hostility.

### Target eligibility (`PROPOSED`)

Bounded **Social Target Resolver** answering: *Is this entity a legitimate social target?*

Potential evidence (all bounded — no arbitrary world scans, no force-loaded chunks):

| Check | Purpose |
| --- | --- |
| alive | basic validity |
| loaded | no stale UUID resurrection |
| same dimension | cross-dimension social is out of scope gen-1 |
| bounded distance | local candidate set only |
| not hostile | SPM relationship legality |
| SPM relationship allows interaction | host authority |
| no conflicting player command | command hierarchy |
| executor-specific requirements | gen-1 executor contract |
| target not invalid/busy where relevant | adoption, not wishful thinking |

Use a bounded nearby candidate set or host-provided relationship/social information.

### Target scoring (`PROPOSED`)

Once eligibility is established, rank eligible targets using soft preference:

- SPM `feelingToward`
- learned `EntityOpinionMemory`
- recent social repetition
- recent positive/negative interaction history
- personality contribution where appropriate
- distance/cost as a weak tie-breaker

Evaluate `SpmEntityOpinionBridge.utilitySupplement()` at the existing seam — do not duplicate.
SPM relationship remains the stronger authority channel; learned entity affinity remains supplemental.

### Admission semantics (must reuse GAO-4R)

| State | Meaning |
| --- | --- |
| `AVAILABLE` | executor/support exists |
| `ADOPTABLE` | at least one legitimate target + executor can meaningfully start **now** |
| `RUNNING` | a social intent was actually adopted |
| `CONTINUABLE` | existing social execution remains valid |

**GAO-4R lesson (hard):** executor installed ≠ executor currently adoptable.

**GAO-4R1 requirement:** adoption-versus-continuation rules must apply. A running `SOCIAL`
interaction must **not** suddenly become invalid merely because new social adoption is currently
unavailable.

### Intent lifecycle (same control-plane principles as EXPLORE/REST)

```text
SELECT
  ↓
PENDING
  ↓
ADOPTED
  ↓
RUNNING
  ↓
TERMINAL
```

Must support:

- bounded pending TTL
- mandatory-authority invalidation
- player-command invalidation
- combat/safety interruption
- target-invalid interruption
- successful completion
- voluntary switch/yield

Do not let a stale target UUID keep `SOCIAL` alive indefinitely.

### Experience terminals (`LOCK RECOMMENDED` — B-45)

Learning fires on **attributable completion**, not greet start:

| Terminal | `ExperienceCause` | `ActivityKind` | Episode |
| --- | --- | --- | --- |
| Greet phases complete (`Phase.DONE`) | `SOCIAL_GREET` (existing) | `SOCIALIZING` | dedicated social episode id (GAO-6R pattern) |
| Target left / unloaded | `SOCIAL_INTERRUPTED` or existing interrupt cause | `SOCIALIZING` | terminal episode |
| Combat preemption | `COMBAT_INTERRUPT` (existing hierarchy) | prior activity | no false SOCIAL dislike |
| Adoption failed / no target | **no learning** | — | D-GAO-023 pattern |
| Voluntary yield to EXPLORE/REST | `VOLUNTARY_SWITCH` (existing discretionary) | incumbent | no double credit |

Reuse `ExperienceEmitters` + `EpisodeBoundaryPolicy` — do not invent parallel social learning bus.

`SOCIAL` becomes a peer discretionary activity:

```text
EXPLORE ↔ REST ↔ SOCIAL
```

Existing minimum commitment and switch-margin semantics should **generalize** rather than adding
pair-specific spaghetti (`restYieldRequested`, `exploreYieldRequested`, `socialYieldRequested`, …).

**Discussion item:** GAO-10 may expose that the current pairwise EXPLORE↔REST yield API needs to
become activity-generic before a third candidate is added. Investigate before implementation.

**Desired future concept:**

```text
requestYield(currentIntent, challengerActivity)
```

rather than an if/else matrix for every activity pair.

### Social experience and learning

`SOCIAL` needs causal episode attribution. Existing RFC work established that social companion
sub-episodes must not corrupt exploration episodes (GAO-6R).

Research whether to introduce/activate `SOCIAL_INTERACTION` experience evidence. Positive/negative
learning should come from **meaningful terminals**, not every proximity tick.

**Valid semantic outcomes (examples):**

| Outcome | Teaches subjective preference? |
| --- | --- |
| `SOCIAL_COMPLETED` | yes |
| `TARGET_LEFT` | contextual |
| `TARGET_BECAME_INVALID` | no |
| `PLAYER_AUTHORITY_INTERRUPTED` | no |
| `COMBAT_INTERRUPTED` | no |
| `EXECUTOR_FAILED` | no |

Authority/feasibility failures must **not** teach *"I dislike socializing."* Subjective learning
should only occur from attributable social experience (extends D-GAO-023 / B-30).

### Anti-lockstep / group behavior

Multiple sociable mobs must not automatically become synchronized clones. Discuss reuse of:

- deterministic per-mob staggering
- individual personality
- individual relationship scores
- individual learned entity opinions
- bounded target ranking

Do **not** add reservations merely to prevent multiple mobs from intentionally socializing together.
Reservations are for actually exclusive resources, not ordinary friendship (extends B-32).

### Opinion Inspector requirements (day-one, read-only)

GAO-10 must be visible through the existing inspector from implementation start. Capture
decision-time causality in the trace — do not reconstruct target choice from later world state.

**Example — running:**

```text
SOCIAL total=37.4
target=Bob

social components:
  sociability +8.2
  affect +4.0
  activityPreference +1.0

target components:
  feelingToward +7.1
  learnedAffinity +2.3

admission:
  installed=true
  adoptable=true

lifecycle:
  SOCIAL (RUNNING)
target UUID/name: Bob
```

**Example — suppressed:**

```text
SOCIAL suppressed:
NO_ELIGIBLE_SOCIAL_TARGET
```

Use per-candidate `suppressionDetail` (GAO-4R pattern) — do not extend a single
`lastExploreReadiness`-style side channel for SOCIAL.

### Dependencies

| Prerequisite | Status | Why |
| --- | --- | --- |
| GAO-4 `DiscretionaryActivityDirector` | `IMPLEMENTED` | intent lifecycle, yield, trace |
| GAO-4R `ActivityAdmission` | `IMPLEMENTED` | AVAILABLE vs ADOPTABLE |
| GAO-4R1 adoption-vs-continuation | `PROPOSED` | running SOCIAL must not false-invalidate |
| GAO-6 ENTITY bridge | `IMPLEMENTED` | `SpmEntityOpinionBridge`, `EntityOpinionMemory` |
| GAO-7 PersonalityModel | `IMPLEMENTED` | `sociability` trait |
| GAO-8B inspector | `IMPLEMENTED` | causal readout surface |
| Generic yield API research | `PROPOSED` | third activity peer switching |

### Must happen (acceptance direction)

- `SOCIAL` competes as a third discretionary activity with separate activity vs target scoring.
- At least one existing finite SPM social executor (or minimal adapter) performs physical interaction.
- Admission suppresses `SOCIAL` when no eligible target exists (`ADOPTION_NOT_READY` + detail).
- Inspector shows activity utility, target, components, admission, and lifecycle from decision-time trace.
- SPM `feelingToward` remains read-only; supplemental entity opinions never override host legality.

### Must not happen (hard rejects)

- Replace SPM's relationship system or write `feelingToward`.
- Add `SOCIAL` as mandatory work or let sociability override combat/survival/commands.
- Unbounded entity-opinion scan or force-loaded chunks.
- Giant `SocializeGoal` without proving it is necessary.
- Learn dislike from authority/feasibility failures.
- Let `PENDING` social intents survive invalid targets indefinitely.
- Introduce pairwise yield spaghetti for every activity combination.
- Per-target `DiscretionaryActivity` enum values.

### Alternatives considered

| Option | Benefit | Failure mode | Verdict |
| --- | --- | --- | --- |
| Mega `SocializeGoal` owning all social behavior | One place to wire | Duplicates SPM; Opinion owns execution not desire | **Rejected** |
| Per-target activity enum (`SOCIAL_WITH_BOB`) | Simple director mapping | Combinatorial explosion; breaks utility model | **Rejected** |
| Single combined utility (activity + target + admission) | Fewer pipeline stages | Bob's score inflates global SOCIAL; pending intents without targets | **Rejected** |
| **Activity choice → target resolver → `SocialIntent` → existing executor** | Reuses SPM; separates desire from execution; GAO-4R admission fits | Requires yield API generalization and gen-1 executor research | **Recommended** |

### Open research items (before implementation lock)

1. ~~Which pinned SPM executor is the best gen-1 finite social behavior?~~ → **preliminary:**
   `FriendlyGreetGoal` + adapter (pending product ack).
2. Exact `SocialIntent` field set and TTL bounds.
3. Activity-generic `requestYield` vs interim pairwise extension → **GAO-4R1** (recommended before code).
4. Whether `SOCIAL_INTERACTION` experience emitter activates in gen-1 or gen-2 — substrate exists
   (`ExperienceKind`, `ActivityKind.SOCIALIZING`, `ExperienceCause.SOCIAL_GREET`); emitter wiring
   `NOT FOUND` in production greet path today.
5. GAO-4R1 continuation rules for in-flight social execution when adoption becomes unavailable.
6. Read-only `PlayerMobs.reactionToward` bridge for resolver eligibility (B-46).

### GAO-10 pre-implementation Behavioral Prediction (`CODE_CONFIRMED` design slice)

| Minute | Predicted observable behavior (if implemented per RFC) |
| --- | --- |
| 0–1 | Bored sociable mob near a GREET-eligible friend; SOCIAL utility rises; target resolver ranks Bob; `SocialIntent(Bob)` pending→adopted |
| 1–2 | Mob approaches, crouch-bows, may gift if feeling ≥ LOVE — same visible sequence as stock SPM greet |
| 2–3 | Greet completes (`Phase.DONE`); discretionary director may switch to EXPLORE/REST after commitment; positive `SOCIALIZING` preference only on attributable terminal |
| Failure | Bob leaves range mid-greet → `TARGET_LEFT` terminal; no "I hate socializing" learning |
| Failure | Combat target acquired → greet stops; authority interrupt; no subjective dislike learning |
| Anti-pattern | Two mobs lockstep-greet same tick without staggering → mitigated by existing per-mob decision staggering + individual scores (B-32) |

**Confidence:** policy shape `CODE_CONFIRMED`; runtime `UNVERIFIED` until separately approved launch.

**Implementation authorization:** none. Peer review **GAO-4R1** first, then lock gen-1 executor choice.

---

## Topic: SPM compatibility bridge

**Status:** `IMPLEMENTED` (GAO-6 MVP) — read-only bridge + supplemental memory; full SOCIAL discretionary scoring deferred to **GAO-10** (`PROPOSED / DISCUSSION`)

### SPM owns (do not duplicate)

- `PlayerMobEntity`, relationships, `feelingToward`, love/friendship/hostility
- Social goals: `FollowLovedOneGoal`, `StayNearGoal`, combat foundation
- Host `GoalSelector`, commands, inventory/equipment base behavior

### Opinion addon owns

- Activity / place / environment opinion memory
- AffectiveState (boredom, engagement, stress, …)
- `DiscretionaryActivityDirector`
- `SPMOpinionBridge` — maps SPM social state → activity utility; **never** second permanent friendship counter
  (**shipped as `SpmEntityOpinionBridge`** — read-only `feelingToward`; supplemental `EntityOpinionMemory`)

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

## Topic: Experience integrity — a LOCKED decision is violated in the place-opinion path

**Status:** `CODE_CONFIRMED` defect against **D-GAO-023**, found via RET-1 runtime evidence
(Agent_Claude, 2026-08-09).

### B-28 — The churn loop taught the mob, and it taught it dislike

A real session log showed one mob run 117 cycles of
`assign CONTROLLED_DESCENT → CAPABILITY_MISSING → revoke → retire → assign`, every cycle with
`everStarted=false`. **No block was ever broken.** Traced at source, every cycle did this:

```text
MiningDirector.completeProject(..., TOOL_FAILURE, at)
  └─ ExperienceEmitters.miningTerminal(...)              unconditional
       ├─ ensureMiningEpisode(...)                        creates an episode  → fed the leak
       ├─ PROJECT_END, outcomeFor(TOOL_FAILURE)
       │    = INTERRUPTED → PROTECTED_INTERRUPT             D-GAO-023 respected here
       └─ PlaceOpinionService.applyMiningTerminal(...)
            └─ preferenceDelta(TOOL_FAILURE) = −6f          D-GAO-023 BYPASSED here
                 → recorded against the chunk the mob stood in, 117 times
```

**D-GAO-023 is implemented for activity opinion and not for place opinion.** The activity path
correctly classifies `TOOL_FAILURE` as a protected interrupt — a feasibility outcome that must not
imply dislike. The place path uses an independent static table keyed directly on
`MiningProjectEnd`, and assigns it −6f.

So the locked rule *"feasibility/safety/authority outcomes do not automatically imply dislike"* holds
in one subsystem and is contradicted two files away. Same defect shape as the retention work: the
principle was understood in one place and re-implemented, differently, in another.

**Aggravating factor:** `PlaceOpinionMemory` is an access-ordered LRU of 32 chunks. A single spinning
controller does not merely add a false negative — it can **evict 32 chunks of genuinely earned place
opinion** in one session.

### B-29 — Two independent repairs, not one

| # | Repair | Why separate |
| --- | --- | --- |
| 1 | Route the place path through the same outcome classification the activity path uses | Closes the D-GAO-023 violation. A second enum table over the same terminals is the bug. |
| 2 | Suppress **all** experience for a terminal the executor never began | Closes the class. `MiningExecutionLease.everStarted()` already records it and is already persisted. |

Repair 1 alone still lets a started-then-failed project teach; repair 2 alone still lets the place
table disagree with the activity table for real failures. Both are needed.

**Candidate D-GAO-024:** an experience event must be evidence of a **physical outcome**, never of a
bookkeeping transition. The same `MiningProjectEnd` is learnable or not depending on `everStarted`,
so this cannot be a static enum table — which is precisely why the existing static table got it wrong.

### B-30 — Opinion amplifies control-plane defects

This reframes RET-1c (*never assign work the executor is guaranteed to refuse*) as a **learning-
correctness prerequisite**, not only a memory rule. A spinning controller writes learned state faster
than real play can correct it: 117 poisoning events in one session against a handful of genuine
mining outcomes per hour.

**Every director in this RFC inherits the hazard.** A GAO-4 director oscillating between two
close-utility activities (B-27) would emit an abandon per flip and teach the mob to dislike exactly
the activities it was choosing between — a defect that presents as a personality.

### B-31 — Credit, and the instructive contrast

| Collection | Bound | Outcome |
| --- | --- | --- |
| `PlaceOpinionMemory.byChunk` | LRU 32, declared up front | correct |
| `MobExperienceContext.episodes` | none, keyed by `randomUUID()` | unbounded — repaired (RET-1b) |
| `OpinionExperienceRegistry.CONTEXTS` | none | unbounded per session — **still open** |

Place memory got it right; the file two doors down did not. That is the argument for RET-1 as a
per-collection checklist rather than an assumed principle.

### B-32 — "Opinion survives unload" is currently implemented as retention

`ENTITY_UNLOAD` calls `freeze`, keeping the whole context resident, so PD-GAO-03's *"preference
survives death"* rests on never releasing the object rather than on persistence.
`PlaceOpinionMemory.captureSnapshot()` / `restoreFromSnapshot(...)` already exist — the primitive is
half-built. The entity-lifetime boundary this RFC needs:

```text
ENTITY_UNLOAD   serialize durable (OpinionMemory, place, personality)
                destroy transient (episodes, REST claim, intents, affect)
                remove context from the registry
ENTITY_LOAD     reconstruct context, restore durable state
```

### B-33 — GAO-5/6/7 must declare bounds before implementation

| Task | Collection implied | Question to settle first |
| --- | --- | --- |
| **GAO-5** PLACE / ENVIRONMENT | chunk → opinion | is 32 chunks the intended horizon? A mob that mines, rests and explores cycles that in one evening |
| **GAO-6** ENTITY bridge | **other mobs' UUIDs** → feeling | unbounded by population; cap it, or make SPM's graph the sole owner |
| **GAO-7** PersonalityModel | fixed fields per mob | bounded by construction — state it and move on |

GAO-6 is the dangerous one: keyed by *other* entities, so a busy server grows it quadratically.

---

## Topic: Task 43 / GAO-4R1 — CLOSED, STATIC ACCEPT (745 tests)

**Status:** `IMPLEMENTED / STATIC ACCEPT`. Runtime `UNVERIFIED`.

### What shipped

| Item | Outcome |
| --- | --- |
| 1–2 Continuation model + production wiring | `ActivityContinuation` / `ActivityContinuations`; `DiscretionaryActivityDirector.tick` **requires** them; `ExploringGoal.inspectContinuation` and `CampfireGoal.inspectContinuation` read bound state only |
| 3–4 Retention on real utility | A running incumbent with blocked adoption competes on its actual score instead of being deleted from the comparison |
| 5–7 Generic yield | `YieldRequest` + `onDiscretionaryYielded`; pairwise callbacks removed; mandatory authority still invalidates rather than negotiating |
| 8 Trace causality | `ExecutionEvidence` per candidate, typed `YieldEvent` (REQUESTED / ENDED), Inspector projection, network codec, screen section |
| 9 Acceptance matrix | Retention, stale acknowledgement, REST mirror, opinion-disabled revocation |
| 10 MAIBS + closure | This section |

### Defects found *during* the task, not before it

Each was found by the next repair rather than by review of the previous one:

1. **Framework without production** — continuation existed while `tick` defaulted to
   `ActivityContinuations.none()`, so the retention branch was unreachable and the original defect
   was still live.
2. **Half-generic yield** — the request was generic while acknowledgement stayed pairwise, which
   forced every executor to know the whole activity set.
3. **Allocating observer** — `inspectContinuation` used `contextFor(...)`, which rehydrates or
   creates: an observer that brings state into existence by looking at it.
4. **Wrong causal origin** — the request recorded the decision that created the *incumbent*, not
   the one that chose the *challenger*.
5. **Five termination paths** — collapsed into one seam; mandatory authority previously left a
   request behind for a later read to misreport as `STALE`.
6. **Immortal sliding timeout** — re-raising on every qualifying decision refreshed the 200-tick
   bound and moved the origin; and an obsolete challenger stayed executable when the incumbent won
   again.
7. **Reconciler unreachable** — correct semantics called only on the success path, so
   `SWITCH_MARGIN_HOLD`, `COMMITMENT_HOLD`, `NO_CANDIDATES` and `BELOW_ACTIVATION_THRESHOLD` each
   left a live request behind.
8. **Recorded but not inspectable** — trace, snapshot and codec all carried the transaction; the
   screen rendered none of it.
9. **Acknowledgement asymmetry** — `mustYield` validated against the live `runningIntent` and
   `acknowledgeYield` did not, so a replaced execution could complete a transaction its successor
   was never party to. Invisible to any enum-based check because the replacement was the *same
   activity*.

### MAIBS closure pass — all six negative conditions verified at source

| Must never happen | Verified by |
| --- | --- |
| Fresh-adoption failure invalidates a valid running execution | retention branch keys on `retainsIncumbent(...)`, not on `adoptionReady` |
| Continuation grants permission to start a fresh execution | `input.continuations()` is read in exactly two places, both inside the retention path |
| A `YieldRequest` becomes movement authority | the record carries identity, activities, ids and clocks only — no target, path or command |
| A stale request or acknowledgement affects a replacement | both read and write paths validate `appliesTo(runningIntent, now)` |
| Mandatory authority negotiates through voluntary yield | mandatory returns before any directive is produced, and `invalidateAll` finishes the transaction as `MANDATORY_INVALIDATION` |
| Opinion-disabled leaves Opinion authority behind | disabled path invalidates all intents and terminates the request before returning |

### Evidence labels

```text
CODE_CONFIRMED             yes
UNIT_CONFIRMED             yes   745 tests
DIRECTOR_BEHAVIOURAL       yes   tick-driven acceptance matrix
INSPECTOR_PRESENTATION     yes   body composition regression
RUNTIME_CONFIRMED          NO
```

**Recorded limitation, not a blocker (item 9.3).** `CampfireGoal.inspectContinuation` is
`CODE_CONFIRMED` — it asks only about bound session state (`firePos`, `restClaimOpened`, live claim),
runs no fresh feasibility scan, and uses the non-allocating query. The director-side behaviour is
also `CODE_CONFIRMED`. What is **not** covered is a real-`Mob` integration test of the REST inspector
and any runtime gameplay observation. Both remain `UNVERIFIED`.

### Testing lesson (`PROVEN` — promote)

**Source-shape tests are useful structural guards and do not substitute for behavioural control-flow
tests.** In this task they were green while:

- the reconciler was never called from four of five paths;
- the trace recorded a transaction the screen never rendered;
- a stale acknowledgement could terminalize a live execution.

Two of my own structural tests also broke on a pure rename with no behaviour change, which is the
other half of the lesson: they couple to names, not to conduct. Keep them for *invariants a test
cannot otherwise reach* — "this file must not contain a second copy of the formula", "every consumer
routes through the shared owner" — and never as the proof that a control path executes.

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
| **D-GAO-024** | *(candidate, B-28)* Experience must be evidence of a **physical outcome**, never a bookkeeping transition. A terminal the executor never began (`everStarted == false`) teaches nothing — cannot be a static enum table, since the same terminal is learnable or not depending on whether execution happened |
| **D-GAO-025** | *(candidate, B-33)* Every opinion collection declares its bound in this RFC **before** the task is implemented — Gate RET-1a applied at design time rather than at review |
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

**Status:** GAO-0 through GAO-9 + RET-GAO-1 `IMPLEMENTED / STATIC ACCEPT`; GAO-4R `IMPLEMENTED / STATIC ACCEPT`; GAO-8B Task 42A/42B `IMPLEMENTED / STATIC ACCEPT`; D-GAO-043 `IMPLEMENTED / STATIC ACCEPT`; **GAO-10** `PROPOSED / DISCUSSION` (unauthorized)

| Phase | Task | Deliverable | Depends on |
| --- | --- | --- | --- |
| **GAO-0** | Activity taxonomy + observation contract | **IMPLEMENTED:** `ActivityClass`; one `ActivityObservationService` scan wrapping `MoveHolderClassifier`; host/addon taxonomy and parity tests | MI-14C2-R2 pattern |
| **GAO-0b** | Schema vocabulary + inert ingress contract | **IMPLEMENTED:** `ExperienceKind`, `ExperienceCause`, `OutcomeClass`, `ActivityKind`, immutable `ExperienceEvent`, interface-only `ExperiencePipeline.accept` | GAO-0, D-GAO-026/027 |
| **GAO-0c** | Episode + rest-claim processing | **IMPLEMENTED:** `ActivityEpisode`, `EpisodeRoutingPipeline`, `RestSessionClaim`/`RestSessionCoordinator`, `OpinionExperienceRegistry`, mining/explore/rest emitters, observer REST integration | GAO-0, GAO-0b |
| **GAO-1** | `AffectiveState` + observation | **IMPLEMENTED:** per-mob mood channels, 10-tick observation, pulse wiring, rate-based boredom, REST/stalled/social semantics, decay, freeze-on-unload, `opinion.enabled` | GAO-0, GAO-0b, GAO-0c |
| **GAO-2** | `OpinionMemory` v1 (ACTIVITY only) | **IMPLEMENTED:** `ActivityOpinionMemory`, `OpinionMemory`, `OpinionLearningPolicy`, normalized-evidence wiring, PD-GAO-03 death reset | GAO-1 |
| **GAO-3** | `IdleOpportunityPolicy` | **IMPLEMENTED:** EXPLORE + REST utility scoring, normalized components, ranked `ScoringResult`, no execution | GAO-2, existing goals |
| **GAO-4** | `DiscretionaryActivityDirector` | **IMPLEMENTED:** intent lifecycle, abstention, voluntary yield, consumer gates, trace, explore adoption control plane | GAO-3 |
| **GAO-4R** | Executor adoption readiness | **IMPLEMENTED:** `ActivityAdmission` (`AVAILABLE`/`ADOPTABLE`), explore + REST blockers, inspector `ActivityAdmissionView`, `ADOPTION_NOT_READY` suppression with `suppressionDetail` | GAO-4 |
| **GAO-4R1** | Adoption vs continuation stabilization | **LOCK RECOMMENDED:** `ADOPTABLE` vs `CONTINUABLE` split; generic `YieldRequest`; Task 43; blocks GAO-10 | GAO-4R |
| **GAO-4.1** | PD-GAO-01 C threshold wiring | **IMPLEMENTED:** `ExploreIdleThresholdPolicy`, `ExploreReadinessThresholds`; wired in `ExplorationActivityGoal` + `ExploringGoal` | GAO-4, GAO-1 |
| **GAO-5** | PLACE memory + learning | **IMPLEMENTED:** `PlaceOpinionMemory`, `PlaceOpinionService`, mining terminal hooks | GAO-4 |
| **GAO-5B** | PLACE destination ranking | **IMPLEMENTED:** `PlaceOpinionRouteRanker`; `ExploringGoal` route score; current-position PLACE removed from `ActivityUtilityScorer` | GAO-5, RET-GAO-1 |
| **RET-GAO-1** | Registry lifetime | **IMPLEMENTED / STATIC ACCEPT:** live + frozen snapshot store (128 LRU + TTL sweep-on-park); `parkOnUnload`/`resumeOnLoad` | GAO-0c |
| **RT-GAO-1** | Targeted runtime falsification | **NARROWED** — not a default feature gate; file `RUNTIME_QUESTION` probes only | PD-GAO-12 |
| **GAO-6** | ENTITY bridge | **CLOSED:** `SpmEntityOpinionBridge`, `EntityOpinionMemory`, GAO-6R `SocialExperienceEpisodes` | GAO-4, RET-GAO-1 |
| **GAO-7** | PersonalityModel | **CLOSED / STATIC ACCEPT:** immutable six-trait model; SPM-host anchors + deterministic UUID latent traits; bounded subjective learning at the single normalized seam; snapshot lifecycle; 581-test clean build | GAO-2, GAO-6 |
| **GAO-8A** | Passive physical expression | **CLOSED / STATIC ACCEPT:** bounded scheduler-owned LOOK expression; Task 40; 593 tests | GAO-0, GAO-1, GAO-6, GAO-7 |
| **GAO-8B** | Understandable Opinion inspection | **IMPLEMENTED / STATIC ACCEPT:** Task 42A causal trace + Task 42B inspector UI; D-GAO-044 locked; runtime verification `UNVERIFIED` | GAO-0 through GAO-9, RET-GAO-1, Task 42A, D-GAO-039/040/043/044, PD-GAO-14, SCR-2R5 |
| **GAO-9** | Overland environment affinity | **CLOSED / STATIC ACCEPT:** finite multi-label context, completion-only normalized learning, enum-bounded snapshot memory, and ±10 valid-route ranking; PROJECT memory superseded; 618 tests | GAO-0c, GAO-2, GAO-5B, RET-GAO-1 |
| **GAO-10** | Discretionary Social Choice & Social Intent | **PROPOSED / DISCUSSION:** third discretionary activity `SOCIAL`, `SocialIntent` target binding, bounded target resolver, gen-1 finite SPM executor research, GAO-4R admission, inspector day-one — **no implementation authorization** | GAO-4R, GAO-4R1, GAO-6, GAO-7, GAO-8B |

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
| **PD-GAO-12** | `LOCKED` | When is runtime required vs static ACCEPT? | runtime default gate / static-first / hybrid | **Static-first:** `CODE + TESTS + MAIBS` → confident → **ACCEPT STATIC**; runtime only when uncertainty is Minecraft engine, SPM `GoalSelector`, mod interaction, or perf/heap — not utility arithmetic |
| **PD-GAO-13** | `LOCKED` | What authority may observable mood/personality expression have? | passive LOOK/cosmetic output / activity-driving behavior | **Passive expression only:** head/look, harmless idle cadence, tiny cosmetic output, later debug/UI; never activity choice, MOVE authority, priority changes, or command/combat/progression override — user 2026-08-10 |
| **PD-GAO-14** | `LOCKED` | GAO-8B entry point, access, refresh, and authority | SPM-screen adapter / addon inspect key; privileged / all / configurable | **Addon-owned screen opened by configurable Inspect Opinion key while crosshair-targeting a PlayerMob; server-authoritative Creative OR operator access; one immutable bounded snapshot per open/manual refresh; strictly read-only** — user 2026-08-11 |
| **PD-GAO-15** | `LOCKED (PRODUCT DIRECTION)` | Is GAO-8B a raw telemetry viewer or causal explanation? | raw numeric dump / **plain-language causal explanation with progressive disclosure** | **Make the AI understandable:** what, why, rejected alternative/suppression, handoff, outcome, and learning; raw values secondary — user 2026-08-10 |

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
| **GAO-PERSONALITY** | Personality modifies only bounded subjective learning deltas at one normalized-evidence seam; neutral personality preserves GAO-2 exactly and no trait grants scheduler authority |
| **GAO-EXPRESSION** | Passive expression owns LOOK only through GoalSelector, is finite/interruptible/noise-filtered, uses no navigation/world action, and cannot overwrite higher-priority authority |
| **GAO-TRACE** | Bounded per-mob trace covers candidates/scores → intent → claim → yield/handoff → executor admission/start → exact terminal cause |
| **GAO-READOUT** | Inspection is server-authoritative, on-demand, bounded, non-allocating/read-only, optional-SPM-safe, and introduces no background sync or AI authority |
| **GAO-EXPLAIN** | Every explanation is derived from one structured decision-time causal record; no later-state recomputation, string parsing, cross-intent attachment, or partial-record eviction |
| **MAIBS-1** | Multi-minute discretionary sessions look human-plausible (explore → rest → socialize → return) |
| **AV-STATIC** | Utility math, parity paths, registry lifetime, and route-bias arithmetic provable from code + deterministic tests without launch |

### Static acceptance workflow (`LOCKED`, PD-GAO-12)

```text
CODE + TESTS + MAIBS
        ↓
Can behavior be determined confidently?
       YES → ACCEPT STATIC
       NO  → Is uncertainty caused by Minecraft / SPM / mod interaction?
              YES → TARGETED RUNTIME TEST (narrow RUNTIME_QUESTION)
              NO  → Improve code / static analysis / tests
```

**Static ACCEPT (`CONFIRMED` — Tasks 34–36, 556 tests):**

| Claim | Evidence |
| --- | --- |
| GAO-PARITY paths | `OpinionFeatureGate`; opinion-off director/scorer; zero `PlaceOpinionRouteRanker` bias |
| GAO-5B route arithmetic | `PlaceOpinionRouteRankerTest`; ±15 cap; -100 anti-fixation dominates in `ExploringGoal` scoring stack |
| GAO-5A inversion removed | No current-position PLACE in `ActivityUtilityScorer.scoreExplore` |
| RET-GAO-1 lifecycle | `OpinionExperienceRegistryRetentionTest`; `parkOnUnload`/`resumeOnLoad` wired in `SpmScavenger` |
| PD-GAO-01 C threshold | `ExploreIdleThresholdPolicyTest`, `ExploreReadinessThresholds` |

**Legitimate `RUNTIME_QUESTION`s (not checklist launches):**

| Class | Example |
| --- | --- |
| Navigation engine | `PathNavigation.createPath` success/fail on real geometry |
| SPM goal contention | Dynamic `GoalSelector` state another mod injects |
| Physical AI sequences | Cave handoff, tunneling, descent interruption/rejoin |
| Perf / heap | Modpack TPS, registry growth under real session churn |

### MAIBS discretionary scenarios (`STATIC VERIFIED` where noted)

| ID | Setup | Must happen | Must not |
| --- | --- | --- | --- |
| **GAO-M1** | Iron NEED active; `Opinion(MINING) = −60` | Mob still mines/smelts per `WorkDemandPolicy` | Boredom cancels gather |
| **GAO-M2** | `FollowLovedOneGoal` running 5 min | Activity = `SOCIAL_TRAVEL`; boredom flat or falls | Explore intent preempts follow |
| **GAO-M3** | Safe night, campfire active, CONTENT | REST; boredom rises slowly | Instant expedition |
| **GAO-M4** | 8 min straight `TrackedLocalWanderGoal` | Boredom → `DiscretionaryIntent(EXPLORE)` | Permanent wander loop |

_Static note:_ director path `CONFIRMED` in unit tests; PD-GAO-01 C threshold `STATIC VERIFIED` (`ExploreIdleThresholdPolicyTest`). Full minute-table plausibility is MAIBS `INFERRED`; not a default runtime gate per PD-GAO-12.
| **GAO-M5** | Diamond NEED + cave handoff + high `Opinion(CAVE)` | Prefer explore handoff over tunnel when both legal | Clairvoyant ore scan |
| **GAO-M6** | `DiscretionaryIntent(EXPLORE)` issued; combat target appears tick+1 | Intent invalidated; attack runs | Delayed explore after fight |
| **GAO-M7** | Adopted REST intent reaches fire; `CampfireGoal` reaches 200-tick cap | Matching arrival-bound claim keeps REST active | Post-arrival mob immediately becomes bored/Explore |
| **GAO-M8** | Mob merely crosses a campfire radius | No REST claim without adopted activity + arrival | Proximity creates false rest |
| **GAO-M9** | Explore route meets simulation frontier | Temporary confidence/cooldown only | Long-term dislike of exploration |
| **GAO-M10** | Two mobs reach discretionary threshold on same tick | Deterministic staggering; reserve exclusive positions/targets, but permit explicit cooperative alignment | Accidental lockstep oscillation or both claim the same exclusive spot |
| **GAO-M11** | Unknown Goal remains running without progress for minutes | Slow restlessness rises while scheduler remains occupied and non-preemptible; owning lifecycle controls release | Frozen affect or Opinion preempting unknown authority |
| **GAO-M12** | REST selected, then entity unloads for days | Affect/opinion freeze; intent/claim invalidated; snapshot park on unload; rescored on load | Ancient intent or stale campfire claim resurrects |
| **GAO-M13** | Two mobs receive the same repeated successful exploration/social/mining evidence but have opposed relevant traits | Preference deltas diverge within the configured bound; later discretionary ranking may diverge | Immediate forced action, mandatory-work refusal, different repetition/duration, or a blocked outcome becoming learnable |
| **GAO-M14** | BORED/CURIOUS idle mob; only host cosmetic look goals active | Bounded expression LOOK episode changes harmless gaze cadence/breadth | MOVE/navigation, new activity, terrain/resource scan, or objective label |
| **GAO-M15** | Expression active; EatFood/command/combat/mining LOOK goal becomes eligible | Higher-priority owner preempts expression immediately and completes normally | Expression overwrites or delays task gaze |
| **GAO-M16** | Three sociable mobs like the same nearby PlayerMob | Each may look briefly; starts/cadence are staggered and finite | Lockstep permanent staring, following, greeting, or resource contention |
| **GAO-M17** | `opinion.enabled=false`, identical seed/config | Existing Antics + SPM look behavior remains on the legacy path | GAO-8 goal starts or mood/personality changes gaze |

Unload/reload snapshot semantics: **STATIC ACCEPT** (`RET-GAO-1`, Task 35). Manual reload adds confidence only; not required to accept feature per PD-GAO-12.

### RT-GAO-1 — targeted runtime (`NARROWED`, PD-GAO-12)

**Status:** `NARROWED` — not a blanket launch gate. File a `RUNTIME_QUESTION` with a single falsification probe when static evidence is insufficient.

| Former probe | Static disposition | Runtime when |
| --- | --- | --- |
| GAO-PARITY (`opinion.enabled=false`) | **ACCEPT STATIC** — gated call graph + tests | SPM/mod interaction suspected |
| GAO-5B place bias at equal routes | **ACCEPT STATIC** — arithmetic in `PlaceOpinionRouteRankerTest` | Mob fails to path away despite winning route (`RUNTIME_QUESTION`) |
| RET-GAO-1 unload/reload | **ACCEPT STATIC** — registry tests + event wiring | Fabric lifecycle diverges from API contract (unlikely) |
| GAO-M2/M6 combat hierarchy | Partial static; hierarchy code `CONFIRMED` | SPM goal injection changes live selector |
| GAO-M7 REST lifecycle | Partial static; claim code `CONFIRMED` | Physical campfire arrival/delivery sequence |
| **RQ-GAO-SHELTER-01** | Static D-GAO-043/044 policy `CONFIRMED` | Inspector during `SHELTER_HOLD` shows mandatory suppression, not discretionary REST winner; paired with SCR-2R2+ physical shelter runtime |
| GAO-M1 competence | Static policy `CONFIRMED` | Regression under real NEED + mining stack |

**Pre-launch static gaps (closed):**

| Gap | Status |
| --- | --- |
| PD-GAO-01 C — boredom modulates `exploreIdleTicks` | **IMPLEMENTED** — GAO-4.1 |
| GAO-5B heading consumer | **IMPLEMENTED** — expedition destination ranking |
| RET-GAO-1 outer registry | **IMPLEMENTED** — bounded live + frozen store (Task 35) |
| GAO-5A semantic inversion | **FIXED** — PLACE removed from EXPLORE utility (Task 36) |
| Episode RET-1b tombstones | **IMPLEMENTED** — `EpisodeRetentionTest` |
| Cold-path context allocation (PERF 0B) | **IMPLEMENTED** — task-31 |

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

### MAIBS post-implementation — GAO-4.1 + GAO-5 + RET-GAO-1 + GAO-5B (`task-34-maibs-report.md`, Tasks 35–36)

**Date:** 2026-08-10 **Revised:** 2026-08-10 (Tasks 35–36; static acceptance per PD-GAO-12)

**Gate result:** `STATIC ACCEPT` for core GA-OPINION discretionary slice; `RUNTIME_QUESTION` only for engine/SPM/perf

| Finding | Classification |
| --- | --- |
| GAO-4.1 mechanics wired; max boredom @600 base → 375 idle ticks | `CODE_CONFIRMED` — **PASS** |
| GAO-4.1 weak in normal idle ramp (readiness ~30s; boredom intent ~3–4 min) | `CODE_CONFIRMED` — non-binding |
| Inner `PlaceOpinionMemory` 32-chunk LRU | `CODE_CONFIRMED` — **PASS** |
| Outer `OpinionExperienceRegistry` bounded live + frozen (128 LRU) | **`RET-1 PASS`** — Task 35 |
| GAO-5A current-chunk PLACE in EXPLORE utility | **REMOVED** — Task 36 |
| GAO-5B destination ranking (±15 vs -100 anti-fixation) | **`CODE_CONFIRMED` — PASS** |
| `DescentHeadingPolicy` place tie-break | **DEFERRED** — documented in `PlaceOpinionRouteRanker` |

**Fix order (completed):** (1) `OpinionExperienceRegistry` lifetime ✅ (2) GAO-5B heading consumer ✅ (3) RT-GAO-1 narrowed per PD-GAO-12 ✅

**Updated GAO-M4 minute table (threshold leg now wired):**

| Minute | Predicted observable |
| ---: | --- |
| 0–0.5 | Local wander; `exploreAdoptionReady` may flip true at ~30s idle (600 ticks) |
| 0.5–3 | Wander continues; director likely abstains or picks REST — boredom still low |
| 3–4 | Boredom ~50+; EXPLORE utility wins → `DiscretionaryIntent(EXPLORE)`; GAO-4.1 threshold already ≤488 |
| 5 | `ExploringGoal` adopts if plan succeeds |
| 6–8 | Directed travel; novelty/engagement rise |
| 9+ | Cooldown / return wander |

**Must-not-happen (static):** Permanent wander because current-chunk PLACE lowers EXPLORE — **disproven** (PLACE removed from utility). Permanent wander from boredom/director failure remains a `RUNTIME_QUESTION` only if static path is challenged.

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
| D-GAO-010 | Typed OpinionMemory taxonomy | `SUPERSEDED / RESOLVED` | ACTIVITY/PLACE/ENTITY/ENVIRONMENT implemented; PROJECT explicitly rejected by D-GAO-035 |
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
| D-GAO-028 | Personality interprets normalized experience; it never selects or commands an activity | `LOCKED` | User-confirmed GAO-7 boundary; preserves director and GoalSelector ownership |
| D-GAO-029 | Hybrid immutable personality uses SPM friendliness/fight-flight anchors plus deterministic UUID latent traits | `IMPLEMENTED` | `PersonalityFactory`; neutral host fallback; profile retained in bounded park snapshot |
| D-GAO-030 | Scale subjective preference/reward only, initially within `[0.75,1.25]`; objective repetition/duration and eligibility remain unchanged | `IMPLEMENTED` | Exact neutral parity plus no-create/no-invert/no-eligibility tests |
| D-GAO-031 | GAO-8A expression uses a finite, interruptible priority-8 `LOOK`-only Goal; it never owns MOVE or writes navigation | `IMPLEMENTED` | `PassiveExpressionGoal`; Option A accepted by Task 40 authorization |
| D-GAO-032 | Expression eligibility is idle/rest/explore only; meaningful work suppresses injection and ENGAGED means preserving the executor's gaze | `IMPLEMENTED` | `PassiveExpressionPolicy`; engaged-expedition abstention regression test |
| D-GAO-033 | GAO-8A curiosity is bounded non-semantic gaze variation; terrain/activity salience remains deferred until a real provider exists | `IMPLEMENTED` | Bounded angular envelope; static negative resource/terrain probes |
| D-GAO-034 | GAO-8A does not mood-wire bunny hopping; flagless Antics gaze cannot bypass scheduled LOOK when Opinion is enabled, while Opinion-off retains legacy parity | `IMPLEMENTED` | `AnticsGoal.mayWriteMimicLook`; parity test |
| D-GAO-035 | PROJECT is not an independent memory family: project type = ACTIVITY, site = PLACE, instance = EPISODE/TRACE | `IMPLEMENTED` | No project identity memory/storage exists; avoids minted-ID RET-1 risk |
| D-GAO-036 | ENVIRONMENT is an immutable multi-label context captured only at existing event/valid-route seams | `IMPLEMENTED` | `EnvironmentProfile/Classifier`; five enum labels; no scanner |
| D-GAO-037 | Environment learning requires an attributable environment terminal; gen-1 learns from expedition completion, not generic failure/frontier/authority/stale closure | `IMPLEMENTED` | `EnvironmentOpinionService`; one personality-scaled delta divided across labels |
| D-GAO-038 | Environment affinity is a ±10 soft tie-breaker among already-valid routes; mean multi-label score; never terrain-safety or mandatory descent/handoff authority | `IMPLEMENTED` | Below PLACE ±15, visited -20, anti-fixation -100; path/safety mutation negative scan |
| D-GAO-039 | GAO-8B snapshots existing state on explicit request through non-allocating lookup; missing state stays missing; no policy/scan/background refresh | `LOCKED` | Task 42 read-side purity/non-allocation gate; PD-GAO-14 manual refresh |
| D-GAO-040 | GAO-8B uses a bounded server-validated common DTO and addon-owned screen; no client type in common API and no host UI/billboard mutation | `LOCKED` | PD-GAO-14 resolved entry/access/refresh/authority contract |
| D-GAO-041 | Explanation evidence is captured at decision time in one structured record and carried through intent/handoff/terminal/learning receipt; never reconstructed from later state | `IMPLEMENTED / STATIC ACCEPT` | Task 42A; 628 tests + clean build |
| D-GAO-042 | Trace retention is bounded by whole decisions with explicit current suppression disposition; no partial-chain eviction or authority side effect | `IMPLEMENTED / STATIC ACCEPT` | Task 42A; active-origin retention test + RET-1 static review |
| D-GAO-043 | Arrived nighttime shelter is `SHELTER_HOLD` mandatory authority while affective rest is an independent observation; only discretionary campfire REST may yield to Opinion; physical displacement legality is decided by the separate centralized shelter envelope | `IMPLEMENTED / STATIC ACCEPT` | Cross-RFC `SCR-2R5`; taxonomy/observer/eligibility tests; 676-test clean build |
| D-GAO-044 | GAO-8B explains mandatory `SHELTER_HOLD` separately from discretionary REST and from `resting=true`; counterfactual scores during shelter are non-causal; optional read-only `ShelterNightAuthority` phase in snapshot | `LOCKED` | Task 42B readout contract; user shelter runtime cross-link 2026-08-12 |
| D-GAO-045 | GAO-10 `SOCIAL` is discretionary desire only; Opinion decides *want* to socialize; existing SPM/social executors perform physical interaction — no mega `SocializeGoal` | `PROPOSED` | GAO-10 topic; Agent_ChatGPT 2026-08-12 |
| D-GAO-046 | `DiscretionaryActivity.SOCIAL` is one finite activity; target identity lives in `SocialIntent`, not per-target enum values | `PROPOSED` | GAO-10 topic |
| D-GAO-047 | SOCIAL utility separates activity score ("feel social?") from target score ("who?"); entity affinity must not globally inflate SOCIAL when target invalid | `PROPOSED` | GAO-10 topic |
| D-GAO-048 | GAO-10 reuses GAO-4R admission: suppress SOCIAL when no eligible target (`ADOPTION_NOT_READY` + `suppressionDetail`); no indefinite pending on stale UUID | `PROPOSED` | GAO-10 topic; depends GAO-4R |
| D-GAO-049 | Opinion never writes SPM `feelingToward`; `EntityOpinionMemory` is supplemental only and cannot override hostility, commands, safety, or relationship legality | `PROPOSED` | GAO-10 topic; extends D-GAO-007 |
| D-GAO-050 | GAO-4R1 splits adoption probe (selection) from continuation probe (incumbent RUNNING); fresh adoption failure must not terminate valid live execution | **LOCKED / IMPLEMENTED** (Task 43, 745 tests) | GAO-4R1 topic; cave-handoff lesson; B-52 |
| D-GAO-051 | Generic discretionary yield API replaces pairwise REST↔EXPLORE flags before a third activity ships | **LOCKED / IMPLEMENTED** (Task 43, 745 tests) | GAO-4R1 topic; B-40/B-51 |
| D-GAO-052 | Gen-1 discretionary SOCIAL uses SPM `FriendlyGreetGoal` lifecycle via minimal `SocialIntent` target adapter — no mega `SocializeGoal` | `PROPOSED` | GAO-10 SPM survey 2026-08-12 |
| D-GAO-053 | Discretionary SOCIAL yields to SPM priority-1 `SOCIAL_REFLEX` goals; Opinion does not preempt host greet/watch reflex | `PROPOSED` | GAO-10; B-44 |
| D-GAO-054 | Social target eligibility requires SPM `Reaction.GREET` legality via read-only bridge; `feelingToward` alone is insufficient | `PROPOSED` | GAO-10; B-46/B-55 |
| D-GAO-055 | `SocialIntent` carries explicit `expiresAtGameTime` bounded by greet worst-case tick budget; expired pending invalidates before adoption | `PROPOSED` | GAO-10; B-48 |
| D-GAO-056 | Gen-1 greet adapter gates Opinion-targeted greets only — does not globally override SPM `nearestWhereReaction` selection | `PROPOSED` | GAO-10; B-49 |

---

## Change Log

| Date | Agent | Change |
| --- | --- | --- |
| 2026-08-09 | User + Agent_Claude | **Task 43 / GAO-4R1 CLOSED — STATIC ACCEPT** (745 tests). **D-GAO-050** and **D-GAO-051** → `LOCKED / IMPLEMENTED`. Nine defects found and repaired during the task, each surfaced by the next repair: framework-without-production wiring, half-generic yield, allocating observer, wrong causal origin, five termination paths, immortal sliding timeout, unreachable reconciler, recorded-but-not-inspectable trace, and an acknowledgement asymmetry that let a replaced execution complete its successor's transaction. MAIBS closure verified all six negative conditions at source. Runtime `UNVERIFIED`; item 9.3 real-Mob REST-inspector integration recorded as unverified, not a blocker. Lesson `PROVEN`: source-shape tests guard structure, never control flow. No SOCIAL |
| 2026-08-09 | Agent_Claude | **Brainstorm B-28…B-33** from RET-1 runtime evidence. **`CODE_CONFIRMED` defect against LOCKED D-GAO-023**: the rule *"feasibility/safety/authority outcomes do not automatically imply dislike"* is honoured by the activity path (`outcomeFor(TOOL_FAILURE)` → `PROTECTED_INTERRUPT`) and **bypassed by the place path**, which uses an independent static table giving `TOOL_FAILURE` = −6f. A 117-cycle assign→`CAPABILITY_MISSING`→revoke loop therefore wrote 117 negative place deltas for a mob that never broke a block — enough to evict all 32 LRU entries of genuinely earned place opinion. Two independent repairs required (route place through the shared classification; suppress all experience when `everStarted == false`). New candidates **D-GAO-024** physical outcome vs bookkeeping transition, **D-GAO-025** declare bounds at design time. Also: opinion **amplifies** control-plane defects, so RET-1c is a learning-correctness prerequisite; "opinion survives unload" is currently implemented as retention; GAO-6 entity opinion is keyed by other mobs' UUIDs and unbounded by population |
| 2026-08-12 | Agent_Cursor | **RFC Opinion brainstorm continuation (2).** Code inspection: `DiscretionaryDirectorState` retains `runningIntent` on `NO_CANDIDATES`; pinned `FriendlyGreetGoal` continuation predicates. Added B-48…B-55; GAO-4R1 MAIBS prediction + API sketch + Task 43 proposal; GAO-10 `SocialIntent` lock-ready fields + B-49 adapter rule; D-GAO-055/056. **No implementation authorization.** |
| 2026-08-12 | Agent_Cursor | **GAO-4R1 + GAO-10 brainstorm continuation.** Inspected `DiscretionaryDirectorState` pairwise yield API, `ActivityAdmission` shape, and pinned SPM `FriendlyGreetGoal` / `FollowLovedOneGoal` / `StayNearGoal`. Added stable topic **GAO-4R1** (adoption vs continuation + generic yield), enriched GAO-10 with executor survey (recommend greet + adapter), social target resolver, MAIBS prediction, B-40…B-47, D-GAO-050…054. **No implementation authorization.** No Java edit, build, runtime launch, commit, push, or PR |
| 2026-08-12 | Agent_Cursor | **GAO-10 proposed (Agent_ChatGPT design capture).** Added stable topic *Discretionary Social Choice & Social Intent*: third discretionary activity `SOCIAL`, `SocialIntent` target binding, activity-vs-target utility separation, GAO-4R admission reuse, gen-1 finite SPM executor research, inspector day-one requirements, yield API generalization discussion, B-39, phased rows GAO-4R/GAO-4R1/GAO-10, and D-GAO-045…049. **No implementation authorization.** No Java edit, build, runtime launch, commit, push, or PR |
| 2026-08-12 | Agent_Cursor | **Shelter runtime cross-link + D-GAO-044 proposed.** User reported tree/roof shelter while houses exist, house reach failures except via bed, and inside→outside door loops. Classified as primarily `SCR-2R2+` physical selection/navigation (vanilla RFC) with Opinion observability risk if Task 42B mislabels mandatory shelter as discretionary REST. Added B-36…B-38, GAO-8B shelter readout contract, RQ-GAO-SHELTER-01, and Task 42B DTO guidance. Task 42B remains unauthorized. No Java edit, build, runtime launch, commit, push, or PR |
| 2026-08-11 | Agent_Codex | **D-GAO-043 implemented with SCR-2R5.** Added truthful mandatory `SHELTER_HOLD` while shelter rest remains an independent claim, blocked discretionary eligibility, preserved campfire `REST`, and integrated the physical authority envelope. All 676 tests and clean build pass; Task 42B is dependency-ready again. Runtime remains unverified; no launch, commit, push, or PR |
| 2026-08-11 | Agent_Codex | **D-GAO-043 locked with SCR-2R5.** Accepted the user's two-layer correction: arrived SeekShelter becomes blocking `SHELTER_HOLD`, rest remains an independent affective predicate, and ActivityClass does not decide physical displacement. Locked a centralized four-effect interruption envelope and blocked Task 42B until implementation evidence prevents false shelter causality. No Java edit, test/build, runtime launch, commit, push, or PR |
| 2026-08-11 | Agent_Codex | **D-GAO-043 proposed from shelter runtime follow-up.** Confirmed the observer currently weakens arrived SeekShelter from mandatory safety to `REST` even though a shelter rest claim already provides the affective predicate; proposed independent authority/rest semantics and blocked Task 42B from presenting false shelter causality until review. Full enforcement design lives in vanilla progression `SCR-2R5`; no Java edit, test/build, runtime launch, commit, push, or PR |
| 2026-08-11 | Agent_Codex | **PD-GAO-14 locked.** Selected a configurable Inspect Opinion key targeting a PlayerMob, a Scavenger-owned screen, server-authoritative Creative-or-operator access, one immutable bounded snapshot per open/manual refresh, and a strictly read-only surface. Locked D-GAO-039/040 and advanced Task 42B to dependency-ready; implementation remains unauthorized. No Java edit, tests/build, runtime launch, commit, push, or PR |
| 2026-08-11 | Agent_Codex | **Task 42A causal trace implemented.** Replaced the loose event ring with 24 whole structured decisions; added separate monotonic decision identity carried by intents; full candidate components, structured suppression/disposition/cause, lifecycle transitions, and actual terminal learning receipts; protected live origins during eviction. Focused tests, 628-test full suite, clean build, JAR inspection, RET-1/static MAIBS pass. Task 42B remains blocked by PD-GAO-14. No Minecraft launch, commit, push, or PR |
| 2026-08-10 | Agent_Codex | **GAO-8B understandability review.** User defined the product as “make the AI understandable.” Static trace audit found GAO-8B-B1: score/select precede new intent identity, can inherit an incumbent id, discard most utility components into strings, and use an event rather than decision bound; existing tests do not prove complete causal correlation. Locked PD-GAO-15 direction; proposed D-GAO-041/042 and split Task 42A causal trace repair from Task 42B UI. No Java edit, test/build, runtime launch, commit, push, or PR |
| 2026-08-10 | Agent_Codex | **GAO-8B continuation and Still Life compatibility evidence.** Inspected pinned SPM screen/menu/readout and current addon state owners; recorded four absent-surface probes; compared host-screen, addon-screen, and command frontends; proposed D-GAO-039/040 and Task 42 with an on-demand non-allocating snapshot and server validation. Recommended addon inspect key + privileged access in PD-GAO-14. Added verified Still Life 0.1.1 tag/resource compatibility and its runtime/performance limits. No Java edit, build, runtime launch, commit, push, or PR |
| 2026-08-10 | Agent_Codex | **Task 41 GAO-9 implementation.** User accepted D-GAO-035…038 and explicitly locked semantic-affinity ≠ terrain-safety. Added five-label immutable classification, raw/normalized evidence context, completion-only divided learning, enum-bounded snapshot/death lifecycle, and ±10 mean affinity after existing ticking/route validity. RED focused suite, full suite, clean build, package inspection, and static MAIBS pass: 618 tests. Runtime route distribution/performance remain UNVERIFIED; no Minecraft launch, commit, push, or PR |
| 2026-08-10 | Agent_Codex | **GAO-9 decision-ready closure review.** Found original ENVIRONMENT/PROJECT taxonomy gap; rejected per-project memory as ACTIVITY/PLACE/EPISODE duplication and RET-1 risk; verified target biome tags, snow predicate, expedition attribution, and the existing ticking-guarded route seam; compared three environment models; proposed D-GAO-035…038 and Task 41 with conservative success-only gen-1 learning, no second scan, bounded route bias, parity/performance/MAIBS gates. No Java edits, build, runtime launch, commit, push, or PR |
| 2026-08-10 | Agent_Codex | **Task 40 GAO-8A implementation.** Added pure expression policy/profile/tone, ephemeral bounded-context publication, priority-8 LOOK-only passive goal, strict self-liked social gaze, Opinion-on Antics gaze guard, and 12 focused tests. Post-GREEN MAIBS found/repaired lost-social-target world-origin gaze, master-disable stale eligibility, and missing ENGAGED abstention. Focused tests, full suite, and clean build pass: 593 tests. Runtime visual cadence remains UNVERIFIED; no launch, commit, push, or PR |
| 2026-08-10 | Agent_Codex | **GAO-8A RFC/MAIBS design.** Converted the user passive-expression boundary into Task 40; inspected SPM/addon LOOK flags and ObjectiveReadout; found the existing flagless Antics direct-gaze hazard; compared scheduler-owned LOOK vs direct observer writes vs host-goal replacement; proposed D-GAO-031…034 for user acceptance; added GAO-M14…M17 and three NOT FOUND probes. No Java edits, tests, build, runtime, commit, push, or PR |
| 2026-08-10 | Agent_Codex | **Task 39 GAO-7 implementation.** Added immutable `PersonalityModel`, deterministic host-anchored factory, positive bounded `PersonalityLearningResponse`, read-only SPM disposition access, one learning-seam integration, bounded snapshot lifecycle, and invariants for neutral parity/no-create/no-invert/no-eligibility/objective-fact preservation. Focused tests, full suite, and clean build pass: 581 tests, zero failures/errors/skips. Static MAIBS PASS; runtime visual differentiation remains UNVERIFIED and non-blocking under PD-GAO-12. No Minecraft launch, commit, push, or PR |
| 2026-08-10 | Agent_Codex | **GAO-7 decision-ready contract.** Confirmed GAO-6R closure evidence; located the single context-aware learning seam and SPM's two public stable traits; rejected wholesale evidence scaling; compared hybrid, UUID-only, and stored-profile designs; added D-GAO-028…030, GAO-M13, Task 39, MAIBS prediction, parity/RET gates, and one implementation decision. No Java edits, tests, build, runtime, commit, or push |
| 2026-08-10 | Agent_Cursor | **Tasks 35–36 + PD-GAO-12.** RET-GAO-1 bounded registry; GAO-5B destination ranking; static acceptance workflow; RT-GAO-1 narrowed; 556 tests; MAIBS post-impl reconciled |
| 2026-08-10 | Agent_Cursor | **GAO-4.1 + GAO-5 + RT-GAO minimal** — threshold wiring, PLACE opinion MVP, static sanity tests; 548 tests/clean build; runtime unverified |
| 2026-08-10 | Agent_Cursor | **Post-GAO-4 continuation** — mode VALIDATION; RT-GAO-1 frontier; GAO-4.1 gap; GAO-5 planning topic |
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
| `TRACE_DECISION_CAPACITY` | `24` | D-GAO-041/042 bounded whole decisions; live origins protected from normal completed-record eviction |

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

**Not delivered:** disk persistence, ENTITY opinions, PersonalityModel,
`DescentHeadingPolicy` place tie-break (deferred; primitive documented).

**Frontier after:** **GAO-6** ENTITY bridge or **GAO-7** PersonalityModel; runtime only on filed `RUNTIME_QUESTION`.

---

## Topic: GAO-5 / GAO-5B — PLACE opinion (`IMPLEMENTED / STATIC ACCEPT`)

**Status:** `IMPLEMENTED / STATIC ACCEPT` — Tasks 34–36; 556 tests

**Why (B-24):** MI-14 Loop C (`SEARCH_BUDGET_EXHAUSTED`) deferred to place memory — learned opinions bias
*where to go*, not mandatory admission.

### GAO-5A — memory + learning (Task 34)

```text
PlaceOpinionMemory (chunk LRU, max 32)
        ↑
PlaceOpinionService ← mining terminals (CAVE_FOUND, NO_PROGRESS, …)
        ↓
ExperienceEmitters.miningTerminal
```

**Delivered:** `PlaceOpinionMemory`, `PlaceOpinionService`, death clear via `OpinionExperienceRegistry.onDeath`.

**Removed (Task 36):** current-position `placeAnchor` in `ActivityUtilityScorer` — caused semantic inversion.

### GAO-5B — destination ranking (Task 36)

```text
ExploringGoal route candidates (8)
        ↓
existing score (novelty, anti-fixation, interest)
        +
PlaceOpinionRouteRanker.routeBias(final waypoint)  →  ±15 max
```

**Route scoring stack (`CONFIRMED` — `ExploringGoal.createExpedition`):**

| Term | Range |
| --- | ---: |
| Random variation | 0..19 |
| Recent heading | −35 |
| Recent visited region | −20 |
| Recent expedition destination | **−100** |
| PLACE opinion | **−15..+15** |

Favorite place can win a tie; cannot overpower −100 anti-fixation.

**Must happen:** Negative destination preference lowers that route's rank vs equal neutral candidate.

**Must not happen:** PLACE vetoes mandatory work; lowers EXPLORE utility at current chunk; unbounded path probes.

**Tests:** `PlaceOpinionMemoryTest`, `PlaceOpinionServiceTest`, `PlaceOpinionRouteRankerTest`.

**Deferred:** `DescentHeadingPolicy` soft tie-break (same primitive); intermediate-waypoint dislike; disk persistence.

---

## Topic: RET-GAO-1 — registry lifetime (`IMPLEMENTED / STATIC ACCEPT`)

**Status:** Task 35 — `STATIC ACCEPT`

```text
LIVE_CONTEXTS (loaded mob)
        ↓ parkOnUnload
prepareForUnloadPark() → MobExperienceSnapshot
        ↓
FrozenContextStore (max 128 LRU; TTL eligible on park sweep)
        ↓ resumeOnLoad / contextFor
rehydrated MobExperienceContext
```

**Ephemeral (discarded on park):** episodes, REST claims, director intent, tombstones.

**Snapshot (survives ordinary unload):** affect, `OpinionMemory`, `PlaceOpinionMemory`, `EntityOpinionMemory` (session MVP).

**Wiring:** `SpmScavenger` `ENTITY_UNLOAD` → `parkOnUnload`; `ENTITY_LOAD` → `resumeOnLoad`.

**Tests:** `OpinionExperienceRegistryRetentionTest`.

**Minor note:** `placeMemoryForRouteRanking()` may alloc empty `PlaceOpinionMemory` when no context — ephemeral, not RET-1.

---

---

## Topic: GAO-6 — ENTITY bridge (`CLOSED` — Tasks 37 + GAO-6R)

**Status:** `STATIC ACCEPT` — **574 tests**; phase **CLOSED** after GAO-6R episode repair

```text
PlayerMobs.feelingToward (read-only, SPM authority)
        ↓
SpmEntityOpinionBridge.feelingChannel / travelsTogether
        ↓
EntityOpinionMemory (supplemental LRU, max 16) ← EntityOpinionService
        ↑
ExperienceEmitters.socialCompanionJoined ← ExploringGoal.inviteCompanions
        ↓
SocialExperienceEpisodes.companionInviteEpisodeId (GAO-6R — per-companion terminal sub-episode)
```

**Delivered:**

- `SpmEntityOpinionBridge` — SPM 0–10 → opinion scale; mutual-above-neutral companion gate; `utilitySupplement` (±12 max, 75% SPM / 25% learned) for future SOCIAL scoring.
- `EntityOpinionMemory` — bounded supplemental affinity; snapshot + death clear (mirrors PLACE).
- `SOCIAL_EXPEDITION` on companion invite (+8 learned delta); **GAO-6R:** dedicated social sub-episode id — exploration episode stays open until `EXPEDITION_END`.

**GAO-6R episode ownership (`CONFIRMED` — `SocialCompanionEpisodeRepairTest`):**

| Proof | Result |
| --- | --- |
| Companion join does not close exploration episode | PASS |
| `EXPEDITION_END` commits OVERLAND_EXPLORATION learning after companion | PASS |
| Two companions → separate social sub-episodes + learning each | PASS |
| Duplicate same-companion invite idempotent | PASS |

**Must happen:** SPM read-only; supplemental memory never overrides `feelingToward`.

**Must not happen:** SPM ledger writes; entity opinion vetoes mandatory work; SOCIAL discretionary director in this slice.

**Deferred (acceptable stepping stones):** `SOCIAL_INTERACTION` emitters; `utilitySupplement` consumer; runtime SPM reflection.

**Tests:** `EntityOpinionMemoryTest`, `SpmEntityOpinionBridgeTest`, `EntityOpinionServiceTest`, `SocialCompanionEpisodeRepairTest`; retention entity round-trip.

### MAIBS-1 (GAO-6R re-pass, 2026-08-10)

**Gate:** **`PASS — BEHAVIORALLY_PLAUSIBLE`** (movement + episode ownership)

**Physical loop (`CODE_CONFIRMED`):** unchanged — parallel companion walk, no SPM writes.

**Episode ownership (`CODE_CONFIRMED` post-GAO-6R):** social terminals compact independently; expedition span survives companion joins until `EXPEDITION_END`.

**Stepping stones retained:** `utilitySupplement` unwired; supplemental entity memory does not gate invites.

**Frontier:** **GAO-7** PersonalityModel.

---

## Topic: GAO-7 — PersonalityModel

**Status:** `CLOSED / IMPLEMENTED / STATIC ACCEPT` — Task 39; 581 tests

**Goal:** Make two mobs interpret the same normalized experience differently without allowing
personality to select, start, cancel, or veto an activity.

### Current implementation and evidence

- `OpinionMemoryService.apply(context, evidence)` is the single context-aware seam immediately
  before `OpinionMemory.apply`; it can see the mob identity without changing immutable
  `EpisodeLearningEvidence`.
- `OpinionLearningPolicy.apply` currently uses normalized `repetitionWeight` for preference,
  recent reward, and repetition. Scaling that raw value wholesale would incorrectly make objective
  repetition/duration depend on temperament.
- SPM exposes stable `PlayerMobEntity.fightFlight()` and `friendliness()` values on its public API
  (`Projects/references/SocialPlayerMobs-v0.86.0/.../PlayerMobEntity.java:1788` and `:1792`).
  `DispositionTraits` contains exactly those two host personality dimensions, each persisted by
  SPM on its entity.
- `NOT FOUND 1`: no addon `PersonalityModel` or six-trait fields in `src/main/java`.
- `NOT FOUND 2`: no third stable host disposition dimension in `DispositionTraits`; state such as
  hunger, orders, recovery, or equipment is mutable context, not personality.
- `NOT FOUND 3`: no personality/trait multiplier in the current `opinion` or `experience` learning
  path.

### Candidate designs

| Option | Design | Benefit | Failure risk | Gate |
| --- | --- | --- | --- | --- |
| **A — recommended** | Immutable hybrid profile: SPM `friendliness` anchors sociability, SPM `fightFlight` anchors risk tolerance, UUID-derived latent values provide curiosity/persistence/materialism and a residual adventurousness term; semantic methods expose bounded learning responses | Coherent with the host mob's authored archetype while remaining deterministic and addon-owned | Correlation/weights can stereotype mobs if too strong; SPM accessor drift needs neutral fallback | Property tests + host bridge failure tests |
| **B** | Six independent UUID-derived values | Small, deterministic, zero persistence | Pure noise can contradict SPM's visible friendliness/fight/flight behavior; weak semantic coherence | Reject for gen-1 |
| **C** | Generate/store an explicit six-trait profile in addon persistence | Fully editable and can survive identity migrations | Adds schema/UI/migration scope; current frozen store may evict it; risks duplicate personality authority beside SPM | Reconsider when explicit presets/UI are authorized |

**Recommendation:** Option A, conservatively bounded. What would cause a switch: evidence that
SPM's two public traits are unavailable/unstable in the supported version, or a product decision
that addon personality must be independently editable and survive reincarnation identity changes.

### Proposed contract

```text
same normalized EpisodeLearningEvidence
        +
immutable PersonalityModel for this mob
        ↓
PersonalityLearningResponse
        ├ preferenceMultiplier
        ├ rewardMultiplier
        └ failurePreferenceMultiplier
        ↓
OpinionMemory mutation
        ↓
future utility may differ
```

`PersonalityModel` has six finite values in `[0,1]`:

| Trait | Gen-1 semantic responsibility |
| --- | --- |
| curiosity | positive exploration-stage/novelty interpretation |
| sociability | positive social-experience interpretation |
| riskTolerance | reduces negative preference response to explicit hazard outcomes; never permits unsafe navigation |
| persistence | reduces negative preference response to execution failure; never extends leases or overrides abandonment |
| materialism | positive resource/mining reward interpretation |
| adventurousness | positive completed exploration interpretation; coherently blended with curiosity/risk plus a bounded residual |

**Hard boundaries:**

1. Multipliers are initially bounded to **`[0.75, 1.25]`**; neutral trait response is `1.0`.
2. Personality scales **subjective preference/reward deltas only**. It does not alter normalized
   milestone frequency, repetition count, duration, `lastPerformed`, cause, outcome eligibility,
   affect pulses, Goal priority, readiness, pathfinding, safety, or mandatory work.
3. Unsupported activity/cause pairs are neutral. Do not invent a personality effect merely to use
   every trait on every event.
4. The model exposes semantic methods (`learningResponse(evidence)`, or narrower positive/failure
   response functions). Callers do not read six floats and create local equations.
5. The profile is resolved/bound once per live context, not regenerated per event. It is derivable
   from UUID + host anchors and therefore does not add another long-lived registry.
6. If host trait access fails, use neutral host anchors (`0.5`) plus deterministic latent values,
   warn once, and never disable learning or infer friendliness/aggression.
7. Gen-1 identity is the PlayerMob UUID/lifetime. Whether a reincarnated echo inherits personality
   is explicitly deferred; do not silently treat a new UUID as the same personality.

### Behavioral Prediction (MAIBS-1 pre-implementation)

| Layer | Result |
| --- | --- |
| Intended behavior | Repeated experience causes different mobs to develop measurably different preferences |
| Implemented mechanism target | Same accepted normalized evidence receives a bounded subjective multiplier at the single learning seam |
| Predicted observable behavior | No immediate action difference on the first event; after repeated successful episodes, the director increasingly ranks activities differently for different mobs |
| Failure/weirdness | Trait amplification can saturate preference; host/UUID contradictions can look incoherent; multiplying repetition would make one mob appear to have performed more work; negative multipliers applied by sign alone can teach hazard aversion from unrelated failure |
| Confidence | `CODE_CONFIRMED` seam/host traits; `GAME_MECHANICS_INFERRED` future activity divergence; runtime appearance `UNVERIFIED` |

**Several-minute feedback loop:** perceive/execute activity unchanged → normalized terminal evidence
arrives → personality modifies only subjective learning → memory changes → later idle opportunity is
rescored by the existing director → existing executor and GoalSelector still own physical behavior.
There is no new Goal and no new preemption path.

### Implementation task — Task 39 (`COMPLETE / STATIC ACCEPT`)

| Field | Contract |
| --- | --- |
| Objective | Add immutable bounded personality and apply it once to normalized opinion learning |
| Likely files | `opinion/PersonalityModel`, `PersonalityFactory`, `PersonalityLearningResponse`; `PlayerMobs` read-only trait accessors; `MobExperienceContext`; `OpinionMemoryService`/`OpinionLearningPolicy`; focused tests; this RFC |
| Must happen | Same event yields a stronger exploration preference delta for high-adventurousness than low-adventurousness; neutral response exactly preserves current GAO-2 math |
| Must not happen | Personality starts EXPLORE, changes Goal priority/readiness, scales repetition/duration, bypasses `OpinionLearningPolicy.accepts`, changes Opinion-off parity, or allocates unbounded per-mob state |
| Required tests | deterministic identity; `[0,1]` bounds; semantic correlation; neutral baseline parity; positive and negative response bounds; blocked outcomes stay blocked; repetition/duration unchanged; host accessor missing fallback; snapshot/unload recomputation parity; two-mob divergence after equal evidence |
| Static gates | GAO-COMPETENCE, GAO-HIERARCHY, GAO-ATTRIBUTION, GAO-PARITY, RET-1, AV-1, MAIBS-1 |
| Runtime | Not required by default under PD-GAO-12; visible long-term differentiation remains `UNVERIFIED`, but no unresolved engine/goal ownership question requires a launch |

### Implemented result and semantic-drift review

```text
PLANNED
host-anchored immutable traits scale normalized subjective learning only
        ↓
IMPLEMENTED
PlayerMobs.disposition → PersonalityFactory → MobExperienceContext
→ OpinionMemoryService → PersonalityLearningResponse → OpinionLearningPolicy
        ↓
PREDICTED RUNTIME
identical physical activity initially; repeated equal experiences produce bounded preference
divergence; the existing director may choose differently on a later idle decision
```

| Gate | Evidence | Result |
| --- | --- | --- |
| Deterministic/bounded identity | `PersonalityModelTest.optionA_isDeterministicBoundedAndHostAnchored` | PASS |
| Neutral pre-GAO-7 parity | `neutralResponse_isExactPreGao7Parity` | PASS — exact snapshot equality |
| No create/invert/eligibility change | `personalityScalesButNeverCreatesInvertsOrChangesEligibility` | PASS |
| Objective facts unchanged | `objectiveEpisodeFactsDoNotDependOnPersonality` | PASS |
| Failure/hazard semantics | `persistenceAndRiskOnlyBoundNegativeInterpretation` | PASS; both deltas remain negative |
| RET-GAO-1 lifecycle | `mustHappen_personalitySurvivesUnloadWithoutRecomputationDrift` | PASS; no new registry |
| Scheduler/action boundary | static reference scan: no Personality reads in goals, mining, readiness, or director | PASS |
| Build | `gradlew.bat clean build`; 581 tests, 0 failures/errors/skips | PASS |

**Post-implementation MAIBS:** `PASS — BEHAVIORALLY_PLAUSIBLE`.

- No coordinates, navigation, Goal flags/priorities, scans, cooldowns, inventory, safety, or
  interruption/resume ownership changed.
- T0 through the current episode remains physically identical. Only the normalized learning delta
  changes; later existing utility scoring consumes the resulting preference.
- Multiple mobs share no mutable personality state; the calculation is O(1) per accepted learning
  record and host reflection occurs only on first context binding/load, not every tick.

**Predicted weird behaviors:**

1. Strong repeated success can still saturate the existing ±100 preference channel —
   `ACCEPTABLE_STEPPING_STONE`, bounded by both multiplier and memory clamp.
2. If SPM renames its disposition accessors, personalities lose host coherence but remain stable
   through neutral anchors + UUID latent traits — `ACCEPTABLE_STEPPING_STONE`, warning emitted once.
3. A reincarnated entity with a new UUID receives a new gen-1 personality — explicitly accepted
   product behavior, not silent identity carry-over.

**Parity:** `FUNCTIONAL / STATIC ACCEPT`. Exact neutral GAO-2 mutation is confirmed. What a player
visually perceives after many episodes is `UNVERIFIED`; a runtime launch was neither required nor
authorized.

### Open questions

1. Explicit editable personality presets/UI remain deferred to a later product phase.
2. Carrying personality through reincarnation identity changes remains deferred; gen-1 treats a
   new UUID as a new personality.

**Decision:** Option A delivered; D-GAO-028…030 implemented. **Frontier:** GAO-8 observable
expression.

---

## Contribution — Agent_Cursor (GAO-6R — Task 38)

**Agent:** Agent_Cursor **Date:** 2026-08-10 **Type:** `REPAIR`

**Delivered:** `SocialExperienceEpisodes` — per-companion terminal social sub-episodes; expedition episode preserved; idempotent duplicate invites.

**Evidence (`CONFIRMED`):** `SocialCompanionEpisodeRepairTest`; `.\gradlew.bat clean build` — 574 tests, 0 failures. MAIBS re-pass: **PASS**.

**GAO-6 phase CLOSED.** Frontier: **GAO-7**.

---

## Contribution — Agent_Cursor (Task 37 — GAO-6)

**Agent:** Agent_Cursor **Date:** 2026-08-10 **Type:** `IMPLEMENTATION`

**Delivered:** GAO-6 ENTITY bridge MVP — `SpmEntityOpinionBridge`, `EntityOpinionMemory`, `EntityOpinionService`, `socialCompanionJoined`, snapshot/death lifecycle, `ExploringGoal` wiring.

**Evidence (`CONFIRMED`):** `.\gradlew.bat clean build` — BUILD SUCCESSFUL; 569 tests, 0 failures.

**Not authorized:** commits; Minecraft launch.

---

## Contribution — Agent_Cursor (Tasks 35–36 + validation workflow)

**Agent:** Agent_Cursor **Date:** 2026-08-10 **Type:** `PROGRESSIVE_CONTINUATION`

**Frontier before:** RET-1 FAIL on outer registry; GAO-5A semantic inversion; RT-GAO-1 as default launch gate.

**Evidence inspected (`CONFIRMED`):**

- Task 35 — `OpinionExperienceRegistryRetentionTest`; `FrozenContextStore` 128 cap; `parkOnUnload`/`resumeOnLoad` in `SpmScavenger.java`
- Task 36 — `PlaceOpinionRouteRanker`; PLACE removed from `ActivityUtilityScorer`; `PlaceOpinionRouteRankerTest`
- `.\gradlew.bat clean build` — 556 tests, 0 failures
- User validation policy — static-first; runtime only for `RUNTIME_QUESTION`

**Delivered:**

1. **RET-GAO-1** — bounded live + frozen snapshot lifecycle; MAIBS abandoned-episode policy on park.
2. **GAO-5B** — destination route ranking; GAO-5A inversion removed.
3. **PD-GAO-12** — static acceptance workflow locked; RT-GAO-1 narrowed.
4. Phased table, validation topic, MAIBS post-impl section reconciled.

**Frontier after:** **GAO-6** or **GAO-7** (product choice). No further Opinion features required for static ACCEPT of core discretionary slice.

**Not authorized:** commits; blanket Minecraft launch.

---

## Contribution — Agent_Cursor (post-GAO-4 continuation)

**Agent:** Agent_Cursor **Date/Session:** 2026-08-10 **Type:** `PROGRESSIVE_CONTINUATION`

**Frontier before:** Executive summary still listed GAO-4 as unauthorized; phased table stale.

**Evidence inspected (`CONFIRMED`):**

- `progress.md` — GAO-0…4 static complete; 499 tests at last green build.
- `ExploreAdoptionControlPlaneTest` — director EXPLORE gated on readiness adoption.
- `MobExperienceContext` + `EpisodeRetentionTest` — RET-1b tombstone compaction shipped.
- `ExplorationActivityGoal` — `cfg.exploreIdleTicks` passed unchanged (PD-GAO-01 C gap).
- task-31-report — PERF 0B cold-path allocation for opinion registry.

**Delivered:**

1. Reconciled RFC identity → mode `VALIDATION`; frontier → **RT-GAO-1**.
2. Added phased rows **GAO-4.1** (threshold wiring gap) and **RT-GAO-1**.
3. Added **RT-GAO-1** validation topic with prioritized runtime probes.
4. Marked GAO-M4 static vs threshold evidence split.
5. Opened **GAO-5** planning topic (B-24 / MI Loop C consumer).

**Strongest open objection:** PD-GAO-01 C is `LOCKED` but unwired — runtime GAO-M4 may pass via
director-only path while **GAO-THRESHOLD** gate remains formally open.

**Frontier after:** user approves **RT-GAO-1** launch **or** authorizes **GAO-4.1** threshold wiring
before runtime.

**Not authorized:** Minecraft launch, GAO-5 implementation, commits.

---

## Contribution — Agent_Codex (GAO-7 PersonalityModel contract)

**Agent:** Agent_Codex

**Date/Session:** 2026-08-10

**Contribution type:** `REVIEW / DESIGN / MAIBS_STATIC`

**Frontier before:** GAO-6R was closed; GAO-7 named trait-weighted learning but did not define
trait ownership, generation, the exact mutation seam, bounds, or the facts personality must not
change.

**Evidence and review:**

- Confirmed GAO-6R ownership repair in commit `92e3dce`, production emitters, and
  `SocialCompanionEpisodeRepairTest`; accepted GAO-6 closure at the static proof level.
- Confirmed `OpinionMemoryService.apply(context, evidence)` is the one context-aware learning seam.
- Confirmed SPM exposes only two stable disposition traits (`fightFlight`, `friendliness`); mutable
  state such as hunger/orders/recovery is not personality.
- Recorded three negative probes for absent addon personality, absent additional host disposition
  dimensions, and absent learning multipliers.

**Strongest objection:** A six-value UUID hash would create difference but not coherent character;
conversely, strong host-derived correlations could stereotype every friendly mob as socially
identical. The recommended hybrid keeps host-visible anchors, deterministic individuality, neutral
fallback, and conservative response bounds.

**Result:** D-GAO-028 locked from the user's explicit architecture constraint. D-GAO-029/030 and
Task 39 are decision-ready. No implementation authority was inferred from `Continue the RFC`.

**Frontier after:** accept D-GAO-029/030 and authorize Task 39, or amend the personality source,
multiplier bound, or reincarnation identity policy.

---

## Contribution — Agent_Codex (Task 39 implementation)

**Agent:** Agent_Codex

**Date/Session:** 2026-08-10

**Contribution type:** `IMPLEMENTATION / VALIDATION / MAIBS_STATIC`

**Frontier before:** D-GAO-029/030, Option A, new-UUID semantics, bounds, and Task 39 were explicitly
accepted and authorized by the user.

**Delivered:** immutable six-trait model and semantic response; SPM public disposition bridge;
deterministic UUID latent traits; single normalized-learning integration; profile park-snapshot
lifecycle; exact neutral parity and the explicit no-create/no-invert/no-eligibility invariant.

**Validation:** focused suite PASS; full suite PASS; clean build PASS; 581 tests, zero
failures/errors/skips; `git diff --check` PASS. Static reference scan found no Personality consumer
in Goal, mining, readiness, or director-selection code.

**Process correction:** Task 39 implementation began before the numbered-task brief workflow was
loaded in this turn. The brief was then created from the already locked RFC contract before final
validation. This sequencing deviation did not change scope, but it is recorded rather than hidden.

**MAIBS result:** `PASS — BEHAVIORALLY_PLAUSIBLE`; physical behavior and scheduler ownership are
unchanged until learned preference influences a later existing idle decision. Runtime appearance
remains `UNVERIFIED` and is not a default gate under PD-GAO-12.

**Frontier after:** GAO-8 observable expression is the next optional product/design phase. No
implementation authorization for GAO-8 is implied.

---

## Contribution — Agent_Codex (GAO-9 taxonomy closure)

**Agent:** Agent_Codex

**Date/Session:** 2026-08-10

**Contribution type:** `RESEARCH / REVIEW / DESIGN / MAIBS_STATIC`

**Frontier before:** GAO-8A was closed, but D-GAO-010 still claimed ENVIRONMENT and PROJECT target
families that had no implementation or explicit disposition.

**Evidence and challenge:** The raw/normalized event schema, memory owners, route generator,
mapped 1.21.1 biome API, and expedition terminal attribution were inspected. Three negative probes
confirmed there is no environment/project memory, evidence context, classifier, or route consumer.
Per-project memory was challenged as an attractive but structurally wrong interpretation: stable
type, place, and causal instance already have separate owners, while minted project IDs would add a
low-reuse retention surface.

**Recommendation:** Supersede independent PROJECT memory. Implement only an end-to-end overland
ENVIRONMENT slice: finite multi-label profile, existing episode pipeline, conservative attributable
learning, existing snapshot lifetime, and soft ranking of already-valid expedition destinations.
Defer CAVE/DEEP_UNDERGROUND/NIGHT until each has a defensible classifier and consumer.

**MAIBS result:** `BEHAVIORALLY_PLAUSIBLE / DESIGN ONLY`. The proposed slice changes no Goal,
navigation, readiness, interruption, or mandatory-work authority. The main predicted failure is
false environmental attribution; D-GAO-037 prevents it by refusing generic failures. Long-duration
route distribution remains `UNVERIFIED`.

**Frontier after:** accept/amend D-GAO-035…038 and authorize Task 41. No implementation, test,
build, runtime launch, commit, push, or PR occurred.

---

## Contribution — Agent_Codex (Task 41 GAO-9 implementation)

**Agent:** Agent_Codex

**Date/Session:** 2026-08-10

**Contribution type:** `IMPLEMENTATION / VALIDATION / MAIBS_STATIC`

**Frontier before:** the user accepted D-GAO-035…038, authorized Task 41, and clarified that
semantic SNOWY affinity may never weaken powder-snow or other terrain safety.

**Action:** implemented the five-label immutable profile/classifier, raw-to-normalized episode
context, completion-only divided learning, enum-bounded context snapshot, and ±10 mean route bias
after existing route/ticking validity. PROJECT memory remains absent. RED focused tests preceded the
implementation; full suite, clean package build, JAR inspection, negative architecture probes, and
post-code MAIBS followed.

**Evidence:** 618 tests pass with zero failures/errors/skips. Final artifact
`build/libs/spmscavenger-1.9.4.jar` has SHA-256
`34B312717E452AB0F2C132536B0415E8FE3D593A225C8164EA512A2AA7A389AE`.

**MAIBS:** `PASS — BEHAVIORALLY_PLAUSIBLE / STATIC ACCEPT`. No Goal, path, malus, hazard,
environmental escape, scheduler, or mandatory-work authority changed. Runtime route distribution
and many-mob classification cost remain `UNVERIFIED` under PD-GAO-12.

**Frontier after:** GAO-9 is closed. The next optional design frontier is GAO-8B read-only debug/UI
expression, or a separately filed runtime question for environment route salience/performance.

---

## Contribution — Agent_Codex (GAO-8B decision-ready continuation)

**Agent:** Agent_Codex

**Date/Session:** 2026-08-10

**Contribution type:** `RESEARCH / REVIEW / DESIGN / MAIBS_STATIC`

**Frontier before:** GAO-9 was statically closed; GAO-8B existed only as an optional read-only
UI/debug direction with no entry point, data boundary, access policy, task, or acceptance gate.

**Action:** verified the pinned SPM v0.86.0 screen/menu/readout and the addon's actual registry,
snapshot, trace, client, dependency, and optional-host seams. Recorded four negative probes for
networking, Opinion UI/payload, SPM-screen integration, and key/command entry points. Compared three
frontends and promoted the addon-owned on-demand inspector as the compatibility-first design.
Defined D-GAO-039/040, GAO-READOUT, MAIBS scenarios, and Task 42; preserved access as PD-GAO-14.
Also integrated the completed Still Life 0.1.1 artifact/tag compatibility finding into GAO-9.

**Strongest objection:** a key-opened custom screen is less discoverable than modifying SPM's
existing inventory screen and adds one packet round-trip. The host-screen alternative loses because
it is Creative-only, has no extension API, and requires a version-sensitive UI Mixin. Switch if SPM
later exposes a supported addon-panel API.

**Evidence:** SPM reference tag `v0.86.0` / commit
`4b80b5e849ccabd69e7c9c2f44dc25f7233c7796`; `PlayerMobScreen`, `PlayerMobMenu`,
`ObjectiveReadout`; `OpinionExperienceRegistry.find`; bounded context/memory/trace owners; clean
working tree before this RFC-only edit. `git diff --check` passes after the edit.

**MAIBS:** `PASS — BEHAVIORALLY PLAUSIBLE` for the proposed read-only contract. The design adds no
Goal, selector scan, navigation, world/inventory interaction, learning, intent, or scheduler
authority. GUI layout and packet timing remain `UNVERIFIED` until implementation/runtime evidence.

**Frontier after:** PD-GAO-14 is the only unresolved product choice. Recommended resolution:
addon-owned inspect key plus Creative/operator-only gen-1 access. If accepted and Task 42 is
authorized, implementation is dependency-ready; no further architecture round is needed.

---

## Contribution — Agent_Codex (GAO-8B understandability and causal-trace objection)

**Agent:** Agent_Codex

**Date/Session:** 2026-08-10

**Contribution type:** `REVIEW / OBJECTION / DESIGN / MAIBS_STATIC`

**Reviewed:** the user's “make the AI understandable” product direction, D-GAO-025, the actual
`DiscretionaryDirectorState` trace call order, `OpinionDecisionTrace`, full utility breakdown,
intent lifecycle, trace constants, and current tests.

**Agreement:** an addon-owned on-demand screen remains the compatibility-first frontend. Showing
Personality, affect, preferences, authority, and bounded history is useful.

**Objection:** the existing trace cannot yet support a truthful causal explanation. New scores and
selection are written before the new intent id is minted; they may have null or incumbent identity,
most utility components are discarded, early suppression has incomplete structured evidence, and
the 24-event ring may retain a terminal after evicting that decision's scores. Current tests prove
individual stages, not one complete chain. Treating the existing trace as sufficient would create
confident but potentially false UI prose.

**Alternatives:** (1) recompute/parse later state — rejected as historically false; (2) enrich the
event ring — possible fallback but still vulnerable to partial eviction; (3) bounded whole-decision
records with decision id carried by the intent — recommended.

**Decision contribution:** PD-GAO-15 records the user's causal-understandability direction.
D-GAO-041/042 and Task 42A define the narrow prerequisite; Task 42B remains the screen. PD-GAO-14
still decides entry/access.

**MAIBS:** `PASS — BEHAVIORALLY PLAUSIBLE` for the proposed trace repair because it is
observability-only. **Must happen:** one stored decision independently explains winner/suppression,
handoff and terminal. **Must not happen:** tracing changes scoring, timing, intent ownership,
GoalSelector, or physical behavior.

**Frontier before:** Task 42 appeared decision-ready except for entry/access.

**Frontier after:** peer-review/accept D-GAO-041/042 and authorize Task 42A. Task 42B must remain
blocked until the trace can prove the explanations it will render.

---

## Contribution — Agent_Codex (Task 42A causal trace implementation)

**Agent:** Agent_Codex

**Date/Session:** 2026-08-11

**Contribution type:** `IMPLEMENTATION / VALIDATION / MAIBS_STATIC`

**Frontier before:** D-GAO-041/042 and Task 42A were user-accepted; the event ring could not safely
support an authoritative inspector.

**Implementation:** replaced per-event retention with bounded whole-decision records; introduced a
local monotonic `decisionId` distinct from `intentId`; carried the origin id through
`DiscretionaryIntent`; retained complete candidate breakdowns and structured executor/adoption
suppression; added exact decision disposition/cause, lifecycle transitions, and synchronous actual
learning receipts for discretionary Explore/Rest terminals. Existing scoring,
thresholds, intent issuance, voluntary yield, GoalSelector, navigation, and physical executors were
not changed.

**Post-implementation objection and repair:** the first bounded implementation evicted the oldest
whole record blindly. A normal 600-tick commitment can outlive 24 ten-tick evaluations, so that
would remove a still-running intent's origin before its terminal. Eviction now prefers completed
decisions and protects live origins while completed records exist; a regression test runs the
origin through terminal and proves subsequent eviction removes the entire record.

**Negative probes:** static searches found `NOT FOUND` for the removed `trace.record` event API,
`Stage.SCORE`, `traceIntentId`, `recordScores`, old `OpinionDecisionTrace.Entry` consumers, and the
old `TRACE_CAPACITY` name in main/test source.

**Validation:** focused trace/director/rest tests pass; the full suite and
`gradlew.bat clean build` pass 628 tests with zero failures/errors/skips. The remapped JAR contains
the structured trace/decision/candidate/transition classes. Artifact SHA-256:
`91321D9C8CD14BFA5581E52BF3D24269118182A877736831CC1BA4CB1C41CBEC`.

**MAIBS semantic-drift review:** `PLANNED → IMPLEMENTED → PREDICTED RUNTIME` remains observation
only. At T0/T+10/T+60/T+200/T+1200 the same director winner, pending/adopted authority, Goal flags,
movement and executor transitions occur; only copied evidence differs. Credible weirdness: a long
run retains only the latest 24 decisions (`ACCEPTABLE_STEPPING_STONE`), a currently active origin can
displace older completed history (`ACCEPTABLE_STEPPING_STONE`), and allocation/tick cost at 100+
mobs is `RUNTIME_QUESTION` pending profiling. Any activity-choice, path, timing, or Goal ownership
change is an `ARCHITECTURE_DEFECT`; focused parity tests and static diff found none.

**Gate:** `PASS — BEHAVIORALLY_PLAUSIBLE` for static semantics. Runtime behavior and performance
remain `UNVERIFIED`; no Minecraft launch occurred.

**Frontier after:** Task 42A is `IMPLEMENTED / STATIC ACCEPT`. Task 42B is technically ready but
remains blocked by PD-GAO-14 (entry/access policy); no UI/network work was started.

---

## Contribution — Agent_Codex (PD-GAO-14 gen-1 inspector product lock)

**Agent:** Agent_Codex

**Date/Session:** 2026-08-11

**Contribution type:** `DESIGN / PRODUCT_DECISION / RFC`

**Frontier before:** Task 42A had made the evidence trustworthy, but Task 42B could not proceed
without an explicit entry, access, refresh, and authority policy.

**Accepted decision:** lock the configurable **Inspect Opinion** key while crosshair-targeting a
PlayerMob; a Scavenger-owned screen; server-authoritative access for Creative players **or** server
operators; one immutable bounded response per open/manual refresh; and a strictly read-only UI.

**Strongest objection:** operator access includes operators who are currently in Survival, while
ordinary Survival players remain excluded. This is intentional privileged diagnostic access, not a
gameplay mechanic. If the inspector later becomes player-facing gameplay, PD-GAO-14 must be reopened
rather than silently widening access.

**Alternatives retained:** an SPM-screen adapter remains a possible later optional integration only
if SPM exposes a supported extension seam; a command dump remains a diagnostic fallback. Neither is
the gen-1 product.

**Acceptance:** must open only after a permitted requester targets a live PlayerMob and must return
one bounded immutable snapshot that changes only on explicit refresh. It must not trust client
permission/identity, synchronize every tick, create Opinion state, alter AI, or expose editing and
control actions.

**Evidence state:** `PRODUCT_DECISION_CONFIRMED`; implementation and runtime behavior remain
`UNVERIFIED`. No source implementation, build, runtime launch, commit, push, or PR occurred.

**Frontier after:** Task 42B is dependency-ready. Its next action is implementation authorization,
not another design/research cycle.

---

## Contribution — Agent_Cursor (shelter runtime cross-link + D-GAO-044)

**Agent:** Agent_Cursor

**Date/Session:** 2026-08-12

**Contribution type:** `PROGRESSIVE_CONTINUATION` / `BRAINSTORM_IN_RFC` / `CROSS_RFC`

**Frontier before:** Task 42B was dependency-ready but unauthorized; D-GAO-043 fixed taxonomy but
not how players would interpret tree/house/door shelter weirdness through the future inspector.

**User evidence (`RUNTIME_REPORTED`, physical behavior `UNVERIFIED` without paired log/JAR pin in
this session):** PlayerMobs shelter in trees or on roof eaves while a house exists; generic shelter
often fails to enter the house except when a bed is the target; mobs that get inside may leave,
re-enter Seek Shelter, and flap the door.

**Code/RFC reconciliation (`CONFIRMED`):**

- Selection/navigation repairs are already scoped in vanilla progression `SCR-2R2` (structural
  satisfaction, leaf vs log walls, interior latch, `RETURNING`), `SCR-2R3` (interior capture, door
  busy guard), and `SCR-2R5` (authority envelope). Opinion must not fork duplicate shelter logic.
- Opinion risk is **mis-explanation**: showing discretionary REST/Explore causality while
  `SHELTER_HOLD` is active would blame mood for mandatory safety work.

**Delivered:** B-36…B-38; proposed **D-GAO-044** GAO-8B shelter readout contract with example
copy, Task 42B DTO guidance (`ShelterNightAuthority` phase), and **RQ-GAO-SHELTER-01** inspector
runtime probe distinct from SCR-2 physical falsification.

**Strongest objection:** fixing inspector copy does not stop tree shelter or door loops; users may
expect Opinion RFC continuation to implement shelter physics. Cross-RFC pointer is mandatory.

**Frontier after:** lock **D-GAO-044** (product ack) → authorize **Task 42B** implementation. Physical
shelter runtime remains vanilla RFC `SCR-2R2+` matrix with separate launch approval.

---

## Contribution — Agent_Cursor (GAO-10 topic capture — Agent_ChatGPT design)

**Contribution type:** `BRAINSTORM_IN_RFC` / `PROGRESSIVE_CONTINUATION`

**Author credit:** Agent_ChatGPT (user-provided design, 2026-08-12)

**Frontier before:** EXPLORE + REST discretionary pair implemented; GAO-4R adoption readiness
implemented; GAO-6/7 social substrate present but no third discretionary activity; SPM bridge
deferred "full SOCIAL discretionary scoring."

**Delivered:** stable topic **GAO-10 — Discretionary Social Choice & Social Intent** with
architecture pipeline, reuse table, admission/lifecycle contracts, gen-1 executor research scope,
inspector examples, must/must-not gates, alternatives table, open research items, B-39, phased
rows (GAO-4R, GAO-4R1, GAO-10), and proposed decisions D-GAO-045…049.

**Strongest objection:** adding a third activity before GAO-4R1 yield/adoption stabilization may
recreate EXPLORE↔REST pairwise spaghetti unless `requestYield(currentIntent, challengerActivity)`
is researched first.

**Frontier after:** peer review GAO-10 → define/accept **GAO-4R1** → authorize gen-1 SPM executor
research task only (read-only SPM inspection) or full GAO-10 implementation separately. No code in
this contribution.

---

## Contribution — Agent_Cursor (GAO-4R1 + GAO-10 brainstorm — 2026-08-12)

**Contribution type:** `BRAINSTORM_IN_RFC` / `PROGRESSIVE_CONTINUATION`

**Frontier before:** GAO-10 captured at high level; GAO-4R implemented for EXPLORE/REST adoption;
gen-1 SPM executor, target resolver, and yield generalization still open.

**Code evidence (`CONFIRMED`):**

- `DiscretionaryDirectorState.updateYieldRequests()` — only REST↔EXPLORE pairwise flags.
- `ActivityAdmission` — adoption-only; no continuation channel.
- SPM `FriendlyGreetGoal` — finite phased greet with self-selected GREET target; best gen-1
  candidate pending `SocialIntent` adapter.
- `PlayerMobs` — `feelingToward` bridge exists; `reactionToward` **NOT FOUND** (eligibility gap).

**Delivered:** stable topic **GAO-4R1**; GAO-10 executor survey + target resolver + MAIBS table;
B-40…B-55; D-GAO-050…056; preliminary gen-1 recommendation (`FriendlyGreetGoal` + adapter);
GAO-4R1 API sketch + Task 43 proposal; `SocialIntent` lock-ready fields; experience terminal map.

**Strongest objection:** greet adapter via mixin touches host goal lifecycle — integration risk is
real; alternative is a thin addon-owned greet executor that copies only the finite phase machine
(duplication cost vs mixin fragility).

**Viable alternative:** defer gen-1 to "approach + stay-near" custom micro-executor if product
rejects mixin on `FriendlyGreetGoal`; higher duplication, lower SPM fidelity.

**Frontier after:** **lock D-GAO-050/051** (recommended) → authorize **Task 43** (GAO-4R1:
continuation + generic yield, no SOCIAL enum) → static-accept → then authorize GAO-10 gen-1.
