# Extending Opinion

This guide explains how to add a new discretionary activity to the finished Opinion architecture **without reopening the original GA-OPINION RFC**.

The current built-in discretionary activities are:

```text
EXPLORE
REST
SOCIAL
```

A future activity should join this system only if it is genuinely discretionary: something the mob may choose because it appeals to them when nothing more important requires action.

## 1. First ask whether the activity belongs in Opinion

Opinion is the wrong layer for:

- emergency survival;
- combat/self-defense;
- explicit player orders;
- mandatory shelter/environmental safety;
- required progression/work;
- competence repair.

If the behavior is required for survival or progression, implement it in the owning subsystem and let Opinion influence only optional preferences among valid alternatives.

## 2. Model the candidate identity

If the activity has no subject, a singleton activity key may be enough.

If it targets an entity, place, project, resource, structure, route, or other specific subject, the candidate identity must include that subject.

Examples:

```text
EXPLORE
REST
SOCIAL/Bob
VISIT/Place-17
BUILD/Project-4
```

Do not let activity type alone stand in for a target-bearing candidate.

## 3. Add scoring, not permission

The utility scorer answers:

> "How appealing is this legal candidate right now?"

It does **not** answer:

> "Am I allowed to do this?"

A score may use:

- stable personality traits;
- short-term affect;
- learned activity preference;
- learned entity/place/environment affinity when causally appropriate;
- repetition/novelty effects.

Keep authority and feasibility in admission/continuation logic.

## 4. Define admission and continuation separately

A fresh candidate may be adoptable while a currently-running episode has different continuation rules.

Do not reuse a fresh `canUse`-style probe as a running continuation predicate unless the host API is explicitly pure and semantically correct for both.

The director should know:

- whether a candidate can be selected/adopted;
- whether the exact incumbent can continue;
- when an incumbent may safely yield;
- whether the challenger may actually start.

## 5. Reuse an executor when possible

Opinion should usually decide **what to want**, while an existing goal/system performs **how**.

Preferred shape:

```text
Opinion chooses candidate
    ↓
small adapter resolves exact execution subject
    ↓
existing executor starts
    ↓
terminal evidence returns to Opinion
```

Avoid creating a broad `DoEverythingForThisActivityGoal` if several existing executors already perform the physical behavior.

## 6. Establish ownership at the handoff

If the executor is shared with native/host behavior, create exact ownership only when the host accepts the exact active intent/candidate.

Never infer ownership later because both systems happened to be interested in the same activity.

For target-bearing activities, verify exact target identity at that boundary.

## 7. Preserve generic yield

Do not add pairwise flags such as:

```text
restYieldToSocial
socialYieldToExplore
exploreYieldToBuild
```

Use the generic discretionary yield transaction and exact challenger identity.

The expected flow is:

```text
incumbent running
→ challenger wins by sufficient margin
→ yield requested
→ incumbent reaches a safe yield point
→ incumbent terminates/yields
→ challenger receives start permission
```

A selected challenger must not physically preempt an incumbent before yield acknowledgment.

## 8. Define causal completion

Before implementation, specify exactly what host/world evidence means:

- success;
- interruption;
- invalidation;
- neutral termination.

Do not use `stop()` alone as success.

If the executor belongs to another mod, prefer host-produced terminal evidence over calling impure host predicates yourself.

## 9. Define learning eligibility

Ask what the mob actually experienced.

A meaningful successful or negative episode may update Opinion. Authority failures generally should not.

Examples that usually should **not** teach dislike:

- player command superseded the activity;
- combat interrupted it;
- shelter authority blocked it;
- executor unavailable;
- path could not be started;
- optional host compatibility was missing.

## 10. Extend observation and Inspector last

Once execution semantics are stable, expose the activity through shared classification and readout.

The Inspector should explain:

- candidate utility;
- exact subject when relevant;
- admission blocker;
- incumbent/challenger relationship;
- running/terminal state;
- whether any learning occurred and why.

The readout remains read-only.

## 11. Preserve disabled-mode parity

When Opinion is disabled, the new adapter must not accidentally suppress or claim equivalent host/native behavior.

If the executor existed before Opinion, it should normally return to its legacy behavior when Opinion is off.

## 12. Keep the change proportional

A new activity should not require another GAO-0 through GAO-10 program.

A normal extension should be a focused feature slice:

```text
candidate model
+ utility
+ admission/continuation
+ executor adapter
+ terminal evidence
+ learning eligibility
+ classifier/readout
+ focused tests
```

Create a new RFC only if the activity introduces genuinely new architecture, not merely because the existing Opinion framework is being used again.

## Definition of done

A new discretionary activity is ready when:

- its candidate identity is unambiguous;
- scoring cannot create permission;
- admission and continuation are distinct where needed;
- executor ownership is causal;
- yield cannot be bypassed;
- success evidence is real and attributable;
- interruption cannot manufacture preference learning;
- feature OFF preserves parity;
- cleanup and retained state are bounded;
- ordinary gameplay can proceed without mandatory diagnostic rituals.

For generic host-mod integration rules, see [Compatibility Contracts](Compatibility-Contracts.md).
