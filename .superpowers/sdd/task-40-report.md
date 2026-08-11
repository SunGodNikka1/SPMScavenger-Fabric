# Task 40 report: GAO-8A passive physical expression

## Status

`DONE_WITH_CONCERNS` — code, unit semantics, static MAIBS, full tests, and clean build pass. Exact
visual naturalness and live GoalSelector preemption remain `UNVERIFIED` because Minecraft was not
launched or authorized.

## Files created or changed

- `src/main/java/com/noobk/spmscavenger/goal/PassiveExpressionGoal.java`
- `src/main/java/com/noobk/spmscavenger/opinion/PassiveExpressionPolicy.java`
- `src/main/java/com/noobk/spmscavenger/opinion/PassiveExpressionProfile.java`
- `src/main/java/com/noobk/spmscavenger/opinion/PassiveExpressionService.java`
- `src/main/java/com/noobk/spmscavenger/opinion/PassiveExpressionSocialPolicy.java`
- `src/main/java/com/noobk/spmscavenger/opinion/PassiveExpressionTone.java`
- `src/main/java/com/noobk/spmscavenger/SpmScavenger.java`
- `src/main/java/com/noobk/spmscavenger/experience/MobExperienceContext.java`
- `src/main/java/com/noobk/spmscavenger/goal/AnticsGoal.java`
- `src/main/java/com/noobk/spmscavenger/goal/ExplorationActivityGoal.java`
- `src/test/java/com/noobk/spmscavenger/goal/AnticsExpressionPolicyTest.java`
- `src/test/java/com/noobk/spmscavenger/goal/PassiveExpressionGoalContractTest.java`
- `src/test/java/com/noobk/spmscavenger/opinion/PassiveExpressionPolicyTest.java`
- `src/test/java/com/noobk/spmscavenger/opinion/PassiveExpressionSocialPolicyTest.java`
- `src/test/java/com/noobk/spmscavenger/opinion/PassiveExpressionStateTest.java`
- `plans/RFC-ADAPTIVE-OPINION-MOOD-AND-ENGAGEMENT.md`
- `.superpowers/sdd/task-40-brief.md`
- `.superpowers/sdd/task-40-report.md`
- `.superpowers/sdd/progress.md`

## Summary

GAO-8A now expresses affect/personality only through bounded server-side gaze. The existing
activity observer publishes an ephemeral profile into the already bounded mob context; a priority-8
`LOOK`-only goal consumes it without navigation, world action, persistence, activity selection, or
a second selector scan. BORED changes cooldown, STRESSED changes hold time, curiosity changes gaze
breadth, sociability may choose a nearby self-liked PlayerMob, and dominant ENGAGED exploration
abstains so the executor keeps attention.

## Commands and exact results

Working directory:
`D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

1. Focused RED command from the brief
   - Exit 1 at `compileTestJava`; missing `PassiveExpression*`, context, goal, and Antics seams.
   - Two incorrect draft taxonomy names were corrected to existing `PROJECT_EXECUTION` and
     `EXPEDITION` before production implementation.
2. Focused GREEN command
   - Exit 0; `BUILD SUCCESSFUL` for the five Task 40 test classes.
3. Full `gradlew.bat test`
   - Exit 0; `BUILD SUCCESSFUL` before the final repair pass.
4. Final focused regression command after MAIBS repairs
   - Exit 0; `BUILD SUCCESSFUL`.
5. Final `gradlew.bat clean build`
   - Exit 0; `BUILD SUCCESSFUL`; artifact `build/libs/spmscavenger-1.9.2.jar`.
6. Final XML aggregation
   - `tests=593 failures=0 errors=0 skipped=0 suites=95`.
7. `git diff --check`
   - Exit 0.
8. Static negative probes
   - `NOT FOUND`: navigation/jump/target/world-action calls in `PassiveExpression*`.
   - `NOT FOUND`: second GoalSelector scan in `PassiveExpression*`.
   - `NOT FOUND`: new long-lived map in `PassiveExpression*`.

## Source evidence

- `CONFIRMED`: SPM reference `PlayerMobEntity.java:756-855` defines higher-priority command,
  safety, combat, eating, and work LOOK owners plus cosmetic priorities 9/10.
- `CONFIRMED`: SPM reference `ObjectiveReadout.java:75-85` filters
  `RandomLookAroundGoal` subclasses as noise.
- `CONFIRMED`: `PassiveExpressionGoal` owns exactly LOOK at priority 8 and contains no navigation,
  jump, target, inventory, or world-action call.
- `CONFIRMED`: `MoveHolderClassifier` maps the subclass to `PASSIVE_COSMETIC`; tests prove it
  preserves idle/readiness observation.
- `CONFIRMED`: profile lifetime is owned by `MobExperienceContext`, invalidated on ephemeral
  cleanup/freeze, and omitted from snapshots.
- `UNVERIFIED`: exact visual cadence, scheduler timing in a live SPM instance, and 100-mob scan cost.

## Post-GREEN defects found and repaired

1. Lost/dead/out-of-range social target fell through to default zero coordinates, making the mob
   look toward world origin. It now creates a fresh bounded cosmetic point; regression seam added.
2. Master addon disable could leave a stale profile eligible because Opinion has a separate switch.
   Goal continuation/admission now requires both master and Opinion switches; truth-table test added.
3. ENGAGED was documented but not implemented for exploration. Dominant positive engagement now
   abstains during expedition, preserving executor attention; regression test added.

## MAIBS review

`PERCEIVE → INTERPRET → CHOOSE → PATH → MOVE → INTERACT` stops at cosmetic LOOK: no path, movement,
or world interaction is created. Work/combat/commands with higher-priority LOOK preempt through the
host scheduler; meaningful work also publishes an inactive profile on the existing 10-tick cadence.
Idle/rest/explore may express; unload invalidates; reload does not resurrect a gaze episode.

Predicted weirdness: multiple mobs may briefly look at the same liked mob; extreme boredom can make
head shifts visually frequent; a newly started MOVE-only unknown Goal can overlap harmless gaze for
up to the observation cadence. None grants authority, but visual quality remains runtime-unverified.

Gate: `PASS — BEHAVIORALLY_PLAUSIBLE / RUNTIME_UNVERIFIED`.

## Self-review

- [x] LOOK only, priority 8, finite, interruptible, readout noise.
- [x] No MOVE/navigation/jump/world action/activity selection.
- [x] One existing observer cadence; no selector rescan.
- [x] Existing bounded context; no new map or persistence.
- [x] Strict self-liked, visible, eight-block social candidates.
- [x] Opinion-off legacy Antics behavior preserved.
- [x] Master disable prevents stale admission/continuation.
- [x] ENGAGED executor attention preserved.
- [x] Focused RED/GREEN, full suite, clean build, negative probes, and MAIBS completed.

## Concerns

- Runtime visual naturalness and exact live preemption are unverified; a separately authorized
  targeted launch would seed each profile and observe idle/explore/eat/command/combat for minutes.
- Eight-block social query cost is bounded/staggered but not profiled at 100 mobs.
- Existing Gradle deprecation warnings and `EpisodeRetentionTest.episodeFor` warning are pre-existing.
- No Minecraft launch, commit, push, or PR occurred.
