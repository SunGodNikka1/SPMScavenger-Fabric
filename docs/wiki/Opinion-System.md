# Opinion System

The Opinion system gives a PlayerMob a deterministic, experience-driven answer to:

> **"Nothing more important requires me right now. What do I feel like doing?"**

It is not a survival controller, combat AI, progression planner, or replacement for SPM. It is a **discretionary decision layer** that chooses among already-legitimate free-time activities.

## Mental model

```text
Personality
    ↓
Learned opinions
    ↓
Short-term affect
    ↓
Discretionary utility
    ↓
Director chooses an activity
    ↓
Existing executor performs it
    ↓
Outcome feeds experience back into Opinion
```

The implementation is deterministic/classical AI. It does not use an LLM or machine learning.

## Main layers

### Personality

`PersonalityModel` stores relatively stable traits such as curiosity, sociability, persistence, materialism, adventurousness, and risk tolerance.

Personality should influence how strongly a mob values or learns from an experience. It should not directly grant authority to perform an otherwise-illegal action.

### Opinion memory

Opinion memory stores learned preferences from attributable experiences.

Current target families include:

- **ACTIVITY** — e.g. exploring, resting, socializing, mining-related experiences;
- **ENTITY** — how experience with a particular entity affects later preference;
- **PLACE** — learned affinity for previously experienced locations;
- **ENVIRONMENT** — semantic affinity such as forest, ocean, snowy, Nether, and End contexts.

Learning is causal. A path failure, authority refusal, scheduler conflict, or missing executor must not automatically become "I dislike this activity."

### Affective state

`AffectiveState` tracks short-term channels such as boredom, engagement, stress, satisfaction, and novelty desire.

Affect changes the current utility of discretionary choices. It is not a second authority system.

### Discretionary director

`DiscretionaryDirectorState` owns the intent lifecycle for discretionary activities.

The director distinguishes:

- candidate selection;
- pending intent;
- adoption;
- running execution;
- safe yield to a challenger;
- terminal success/interruption/invalidation.

Important distinction:

> **Adoption is not continuation. Request is not authority. Desire is not start permission.**

## Current discretionary activities

### EXPLORE

The director may choose a real exploration expedition when exploration is legal and attractive.

Opinion can influence which legitimate route/environment is preferred, but it does not override route construction, chunk/ticking safety, environmental safety, commands, combat, or progression work.

### REST

Discretionary REST uses the camp/rest-session path. Nighttime shelter is not an Opinion choice: shelter is a higher safety authority.

Therefore:

```text
REST = "I feel like resting"
SHELTER = "I need to remain safe here"
```

These are deliberately separate.

### SOCIAL

SOCIAL is target-specific. The director does not merely choose `SOCIAL`; it chooses an exact candidate such as `SOCIAL/Bob`.

The lifecycle is:

```text
Opinion chooses SOCIAL/Bob
    ↓
Social target resolver supplies Bob
    ↓
SPM independently decides whether Bob is a legal GREET target
    ↓
exact target match establishes the binding
    ↓
SPM FriendlyGreetGoal executes the physical greeting
    ↓
host-produced completion evidence is accepted only while the exact Opinion intent still owns execution
    ↓
causal success may update Opinion
```

SPM remains authoritative for its own relationship/greeting legality. Scavenger does not recreate FriendlyGreet behavior.

An unbound native FriendlyGreet remains a host `SOCIAL_REFLEX`. An exact Opinion-owned running greeting is classified as `DISCRETIONARY_SOCIAL`.

## Candidate identity

Activity type alone is not enough for target-bearing decisions.

`SOCIAL/Bob` and `SOCIAL/Alice` are different candidates. A pending or running Bob intent must never be inherited by Alice merely because both are SOCIAL.

This principle applies to future target-bearing activities as well.

## Yield and switching

The director uses generic discretionary yield instead of pairwise activity flags.

A challenger may be selected while another discretionary activity is running, but the challenger does not physically start until the incumbent has yielded at a valid boundary.

This prevents a third activity from bypassing an already-running executor.

## Learning rules

Positive or negative Opinion learning should represent a meaningful attributable experience.

Good examples:

- a completed discretionary expedition;
- a real successful social greeting;
- a meaningful completed/rest experience;
- a directly experienced place/environment outcome.

Bad examples:

- "the scheduler would not let me start";
- "combat interrupted me";
- "the player ordered me elsewhere";
- "shelter authority blocked the activity";
- "the compatibility bridge was unavailable".

Those are feasibility or authority facts, not preferences.

## Observation purity

Read-only probes must stay read-only.

Inspection, scoring, admission observation, and compatibility checks should not consume cooldowns, assign host targets, start/stop goals, move navigation, mutate inventory, or warm host caches as a side effect.

If a host API is impure, prefer observing evidence that the host already produced instead of calling the predicate yourself.

## Feature disabled

When Opinion is disabled, existing SPM/Scavenger behavior should preserve legacy parity as closely as possible. Opinion-owned gates must not globally disable native SPM social behavior or other host features.

## Inspector/readout

The Opinion readout is explanatory, not an activity controller. It can expose what the director chose, why a candidate won, what was suppressed, current affect/opinion context, and recent outcome/learning information. It must not mutate runtime state.

## Source landmarks

The main implementation lives under:

```text
src/main/java/com/noobk/spmscavenger/opinion/
```

Important integration points also exist in the activity classifier, exploration/camp executors, SPM compatibility bridges, optional Mixins, and readout package.

For adding a new discretionary activity, see [Extending Opinion](Extending-Opinion.md).
