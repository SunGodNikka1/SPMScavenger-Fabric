# Compatibility Contracts

This page is the generic integration contract for Scavenger compatibility with **Social Player Mobs and future optional mods**.

The goal is to avoid re-solving the same integration bugs for every host system.

## 1. Host authority stays with the host

When another mod already owns a mechanic, Scavenger should normally **adapt to it rather than recreate it**.

Examples:

- SPM owns PlayerMob relationship legality and FriendlyGreet execution;
- SPM owns its entity framework, combat/flee/order behavior, backpack and native goals;
- Minecraft owns navigation/path semantics, world rules, inventory semantics and entity lifecycle.

Scavenger may add policy around those systems, but it should not silently fork their semantics.

## 2. Preference does not create permission

A lower-level preference system can choose among legal options. It cannot make an illegal or higher-priority action legal.

```text
preference → choose among permitted alternatives
permission → comes from the owning authority
```

This applies well beyond Opinion.

## 3. Desire, request, admission, start, continuation and completion are different facts

Do not collapse lifecycle states.

A system wanting an action does not mean:

- the target is still legal;
- the host accepted it;
- the executor started;
- the executor is still running;
- the action completed successfully.

Use the narrowest fact available at each boundary.

## 4. Exact identity must survive handoffs

When an action is target-specific or project-specific, carry its identity through the whole control path.

Examples:

- SOCIAL/Bob must not become SOCIAL/Alice;
- one mining project must not inherit another project's completion;
- one reserved furnace/door/shelter claim must not be credited to an unrelated episode.

Activity kind alone is not enough when the candidate has a subject.

## 5. Ownership must be established at the causal handoff

Do not reconstruct ownership afterward from coincident state.

Bad pattern:

```text
host behavior happened
+ Opinion also wanted that activity
= therefore Opinion owned it
```

Good pattern:

```text
exact intent is startable
+ host returns the exact target/resource
→ establish binding
→ host start confirms adoption/running
```

This prevents native host behavior from being credited to Scavenger after the fact.

## 6. Observation must be observational

Inspection and feasibility probes should not alter the world or the host AI.

Avoid read paths that:

- decrement cooldowns;
- assign targets;
- mutate relationship caches;
- start or stop goals;
- issue navigation;
- modify inventory;
- allocate/rehydrate runtime control state merely because a UI opened.

If a host predicate is impure, observe evidence produced by the host's own normal evaluation instead.

## 7. Optional compatibility fails closed

An absent or changed optional API should disable only the affected optional integration.

It must not:

- crash the whole mod;
- invent authority or success;
- assume a default target/relationship;
- globally disable unrelated host behavior.

Where possible, preserve vanilla/host behavior when the bridge cannot prove compatibility.

## 8. Feature OFF should preserve host parity

If a Scavenger feature is disabled, its integration hooks should preserve the host's original behavior unless another independent Scavenger safety rule applies.

For example, disabling Opinion must not turn native SPM greetings into Opinion-owned behavior or globally suppress them.

## 9. Optional Mixins must be genuinely optional

For host-mod Mixins:

- prefer `@Pseudo` when the target class may be absent;
- use non-required injection where appropriate (`require = 0`);
- do not place ordinary helper classes inside packages that the Mixin runtime treats as Mixin classes;
- account for the actual shipped namespace/mappings, not just named development symbols;
- when required by the target environment, support both readable and intermediary method names;
- fail closed if an injection point disappears in a host update.

A successful compile does not prove an optional Mixin actually applied to the shipped host JAR.

## 10. Runtime classification should use one semantic source

If multiple systems need to know what a host goal means, centralize that interpretation.

Examples include scheduler observation, shelter arbitration, movement ownership and diagnostics.

Do not let each subsystem independently decide that the same goal is `SOCIAL_REFLEX`, `DISCRETIONARY_SOCIAL`, work, safety, etc. A shared classifier prevents policy drift.

## 11. Completion evidence must be causal

Success should be credited only when the exact owned episode produced real terminal evidence.

Do not equate:

- `stop()` with success;
- disappearance of a goal with success;
- elapsed time with success;
- host behavior that continued after ownership ended with success.

If valid completion occurs while ownership is still live, that completion can remain historical evidence even if authority changes afterward.

## 12. Interruptions are not automatically preferences

Combat, commands, shelter, world invalidation, unloads, scheduler loss and compatibility failure are control-plane outcomes.

They should not teach "I dislike this activity" unless the mob actually experienced a meaningful negative outcome attributable to the activity itself.

## 13. Clean up every runtime binding

Temporary compatibility state must have explicit cleanup for the relevant lifecycle:

- normal executor stop;
- entity unload/removal;
- death;
- dimension/lifecycle replacement when applicable;
- server shutdown.

Never retain entity object references when UUID/value state is sufficient.

## 14. Bound work must remain bounded

Compatibility scans, reflection, target resolution, diagnostics and arbitration should be bounded in radius, candidates, cadence, retries and retained history.

Do not turn optional integration into an unbounded per-tick world scan.

## 15. Reuse host execution before creating a mega-goal

Before adding a new goal that reimplements another mod's behavior, ask:

1. Can Scavenger decide **what/why** while the host still performs **how**?
2. Can a small adapter supply exact intent/target/resource information?
3. Can host terminal evidence be observed without probing impure methods?

A thin adapter is usually safer than a parallel copy of host AI.

## 16. Escalation rule for repeated patches

If the same integration keeps needing narrow repairs, stop adding one-off checks and identify the shared invariant.

Typical escalation:

```text
narrow symptom repair
→ shared invariant
→ centralized arbitration / binding / classifier
→ subsystem-boundary redesign only if still necessary
```

Do not create a new subsystem just because one edge case exists.

## Checklist for a new compatibility adapter

Before considering a new adapter complete, answer:

- Who owns legality?
- Who chooses the exact subject/resource?
- Where is causal ownership established?
- What proves START/RUNNING/COMPLETION?
- Are observation probes pure?
- What happens when the host is absent or changed?
- Does feature OFF preserve host parity?
- How is state cleaned up?
- Are target identity and episode identity exact?
- Is scanning/reflection/history bounded?
- Can the host executor be reused instead of copied?

These contracts are intended to be reused for SPM integration and future mod compatibility work.
