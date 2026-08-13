# Task 44D Brief — FriendlyGreet Executor Binding

## Scope

Implement the locked GAO-10 executor boundary for discretionary SOCIAL:

- exact `DiscretionaryCandidateKey(SOCIAL, subject)` admission through SPM's existing
  `FriendlyGreetGoal` redirect;
- a bounded per-mob execution binding carrying mob, intent, subject, and admission generation;
- start/terminal lifecycle correlation;
- dynamic `DISCRETIONARY_SOCIAL` activity classification only for the exact live binding;
- host-produced completion evidence from the pinned `Phase.DONE` writes;
- fail-closed behavior when the optional host seam changes.

Do not implement a new social goal, rescore relationships at terminal time, change SPM's goal
priority/flags/phases, or broaden Opinion activity selection.

## Locked decisions

- **D-GAO-053:** exact live binding owns classification; an unbound native greet remains
  `SOCIAL_REFLEX`.
- **D-GAO-059:** completion consumes host-produced evidence. Addon code must not call
  `canContinueToUse()` or `reactionToward()` as a terminal probe.
- Activity-only SOCIAL admission/adoption remains fail-closed.
- Opinion disabled returns SPM's original selected target unchanged.
- Opinion enabled admits only when the current host result exactly equals the startable intent's
  subject.

## Evidence baseline

- Host artifact: `run/.fabric/processedMods/playermob-0.86.0-64b5720b4b825f21.jar`.
- Pinned source reference: `D:/Apps/Minecraft Port/Projects/references/SocialPlayerMobs-v0.86.0`.
- Unfiltered `javap` confirms six execution-time `phase = DONE` branches: three in `tickGift()` and
  three in `tickFetch()`. `tickGift()` also writes `FETCH`, so the hook must target the exact
  `Phase.DONE` constant loads, not every phase write. Constructor/start/stop are not evidence.
- `canContinueToUse()` calls transitively impure `reactionToward`; it is prohibited as a probe.

## Alternatives and decision

1. **Recommended — exact optional-host lifecycle seam:** bind at the live redirect, confirm at
   `start`, observe the six DONE writes, consume at `stop`. It preserves SPM behavior and fails
   closed if bytecode changes. Risk: a host update can suppress positive evidence until the adapter
   is updated.
2. **Rejected — infer success in `stop()`:** fewer injections, but it must call impure relationship
   logic or treat every interruption as success. That creates false causality.
3. **Rejected — custom SocializeGoal:** easier internal state ownership, but duplicates SPM's native
   social phases and changes behavior beyond GAO-10.

Switch away from option 1 only if SPM exposes a stable, observationally pure terminal event/accessor.

## Acceptance

**Must happen:** SOCIAL/Bob starts only when the live SPM admission also returns Bob, exact start
creates a live binding, the observer reports `DISCRETIONARY_SOCIAL`, and one pinned DONE event yields
one completed terminal.

**Must not happen:** Alice executes Bob's intent; an unbound greet is classified as discretionary;
`stop()` alone creates success; terminal classification invokes `canContinueToUse()` or
`reactionToward()`; Opinion-off changes SPM behavior; a stale binding survives stop/unload/death or
server stop.

## Verification

1. RED tests for exact admission, wrong-subject refusal, Opinion-off parity, binding lifecycle,
   dynamic classification, single completion consumption, non-completed stop, and retention cleanup.
2. Focused tests.
3. Full test suite.
4. `gradlew.bat clean build`.
5. Inspect the remapped JAR and optional Mixin targets.
6. Static MAIBS over exact subject, host interruption, completion, unbound host greet, unload, and
   multiple candidates.

Minecraft runtime remains `UNVERIFIED`; no launch is authorized.
