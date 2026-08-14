# Opinion System

The Opinion system gives PlayerMobs stable differences and experience-informed preferences while preserving Minecraft's authority, safety, and executor constraints.

It is deterministic/classical game AI. It does not use an LLM, machine learning model, or external AI service at runtime.

Its central rule is:

> **Preference affects choice. Preference does not create permission.**

Opinion may rank activities that are already legal and executable. It cannot override combat, environmental escape, player commands, nighttime shelter authority, progression ownership, path safety, simulation boundaries, or an executor's hard admission rules.

## Data model

### PersonalityModel

Each PlayerMob receives a stable, bounded profile with curiosity, sociability, risk tolerance, persistence, materialism, and adventurousness. Personality scales an already-eligible subjective learning delta. It cannot create learning from zero, invert a delta, make ineligible evidence eligible, or directly order an activity.

### AffectiveState

The short-term state tracks engagement, boredom, satisfaction, stress, novelty, time since meaningful progress, and frozen state. It reacts to bounded experience pulses and observation cadence. It supplies context to utility scoring; it does not own GoalSelector authority.

### OpinionMemory

Longer-term memory stores bounded preferences and recent experience pressure. Opinion is separated by subject:

- **Activity** — feelings about kinds of work or leisure, such as exploration, rest, or socializing.
- **Entity** — supplemental preference for a particular entity; this does not replace SPM's relationship graph.
- **Place** — bounded memory associated with coarse locations.
- **Environment** — weak, multi-label affinity for semantic environments such as forest, ocean, snowy terrain, Nether, and End.

Environment preference ranks already-valid routes only. Loving snowy terrain never weakens powder-snow avoidance or any other survival rule.

## Observation and causal learning

`ActivityObservationService` performs the shared scheduler-wide observation pass and reuses `MoveHolderClassifier` for semantic classification. An unknown running goal is fail-safe active, not idle. Observation reports what is happening; it does not claim ownership.

Raw `ExperienceEvent`s are routed into `ActivityEpisode`s. Episodes preserve causal ownership, normalize high-frequency milestones, provide bounded short-term affect pulses, and commit long-term learning only when the terminal outcome and cause make learning eligible. Commands, protected interruptions, simulation frontiers, and unrelated failures do not manufacture dislikes.

Learning is attributed at the actual terminal boundary. Host behavior that merely looks similar to an Opinion activity does not become Opinion-owned retroactively.

## Discretionary choice

`DiscretionaryActivityDirector` currently compares three activities:

- **EXPLORE** — a voluntary overland expedition.
- **REST** — discretionary downtime backed by a valid rest claim.
- **SOCIAL** — an optional SPM FriendlyGreet interaction with one exact subject.

Availability and admission are established before utility can win. Utility combines usefulness, learned preference, affect fit, novelty/reward/repetition/failure pressure, and cost. A high score is desire, not start permission.

### Candidate identity

Activity kind is not enough to identify every choice. `DiscretionaryCandidateKey` uses:

```text
EXPLORE      = (EXPLORE, no subject)
REST         = (REST, no subject)
SOCIAL/Bob   = (SOCIAL, Bob's UUID)
SOCIAL/Alice = (SOCIAL, Alice's UUID)
```

`SocialIntent` carries the exact immutable subject identity that participated in scoring. SOCIAL without a subject, or a non-SOCIAL activity with a subject, fails closed.

## Authority and lifecycle

The practical hierarchy is:

```text
world safety and immediate survival
        ↓
attributable combat threat
        ↓
explicit player authority
        ↓
nighttime shelter hold / project control
        ↓
executor feasibility and admission
        ↓
Opinion utility and learned preference
        ↓
idle fallback
```

Important lifecycle distinctions:

- **Request is not authority.** A candidate or intent cannot move the mob by itself.
- **Adoption is not continuation.** Starting requires new-work admission; an already-running executor uses a separate continuation contract.
- **Activity kind is not candidate identity.** SOCIAL/Bob and SOCIAL/Alice are different transactions.
- **Yield is transactional.** The incumbent, challenger, and causal intent are correlated exactly; stale or superseded requests cannot borrow authority.
- **Ownership begins at causal handoff.** It is never reconstructed afterward from similar visible behavior.

`SHELTER_HOLD` is mandatory nighttime safety that happens to provide rest benefits. It is not optional REST and cannot be abandoned because EXPLORE scored higher.

## SPM FriendlyGreet integration

The optional FriendlyGreet seam leaves SPM's native target unchanged when Opinion is disabled. When enabled, the host-selected subject must match an exact startable SOCIAL candidate. Adoption and running state bind mob UUID, intent UUID, subject UUID, and admission generation without retaining entity references.

SPM's exact DONE transition is treated as completion evidence only while the director still owns that exact running intent and subject. A stop without DONE is non-completion. Cleanup occurs on stop, unload, death, and server shutdown.

## Inspector and readout

The read-only Opinion Inspector requests one bounded immutable snapshot. The server validates permission and the targeted PlayerMob. The snapshot explains candidate availability, complete utility components, selection or abstention, intent and yield lifecycle, executor handoff, terminal evidence, learning receipts, affect, personality, and bounded memories. Refresh is manual; it is not a per-tick synchronization stream.

The causal trace retains whole decisions rather than loose events, so eviction cannot leave a convincing but incomplete half-decision.

## Disabled behavior

With Opinion disabled, SPM behavior remains host-owned. Opinion does not redirect FriendlyGreet, issue discretionary activity, or gain scheduler authority. Existing non-Opinion Scavenger behavior and required cleanup remain active according to their own contracts.

## Evidence boundary

The architecture above is source-confirmed and covered by static/unit/build evidence in the repository. In-world timing, feel, and cross-mod behavior still require the specific runtime evidence named by the project's test matrix; compilation alone is not behavioral proof.
