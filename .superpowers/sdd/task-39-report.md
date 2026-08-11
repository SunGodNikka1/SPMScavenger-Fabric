# Task 39 report: GAO-7 PersonalityModel

## Status

`DONE_WITH_CONCERNS` — static semantics and build are confirmed; long-term visual differentiation
in Minecraft remains `UNVERIFIED` because no runtime launch was authorized or required by PD-GAO-12.

## Files created or changed

- `src/main/java/com/noobk/spmscavenger/opinion/PersonalityModel.java`
- `src/main/java/com/noobk/spmscavenger/opinion/PersonalityFactory.java`
- `src/main/java/com/noobk/spmscavenger/opinion/PersonalityLearningResponse.java`
- `src/main/java/com/noobk/spmscavenger/opinion/OpinionLearningPolicy.java`
- `src/main/java/com/noobk/spmscavenger/opinion/OpinionMemory.java`
- `src/main/java/com/noobk/spmscavenger/opinion/OpinionMemoryService.java`
- `src/main/java/com/noobk/spmscavenger/PlayerMobs.java`
- `src/main/java/com/noobk/spmscavenger/SpmScavenger.java`
- `src/main/java/com/noobk/spmscavenger/experience/OpinionExperienceRegistry.java`
- `src/main/java/com/noobk/spmscavenger/experience/MobExperienceContext.java`
- `src/main/java/com/noobk/spmscavenger/experience/MobExperienceSnapshot.java`
- `src/main/java/com/noobk/spmscavenger/experience/ExperienceEmitters.java`
- `src/main/java/com/noobk/spmscavenger/experience/RestSessionCoordinator.java`
- `src/main/java/com/noobk/spmscavenger/goal/ExplorationActivityGoal.java`
- `src/main/java/com/noobk/spmscavenger/opinion/AffectiveStateService.java`
- `src/test/java/com/noobk/spmscavenger/opinion/PersonalityModelTest.java`
- `src/test/java/com/noobk/spmscavenger/experience/OpinionExperienceRegistryRetentionTest.java`
- `plans/RFC-ADAPTIVE-OPINION-MOOD-AND-ENGAGEMENT.md`
- `.superpowers/sdd/task-39-brief.md`
- `.superpowers/sdd/task-39-report.md`
- `.superpowers/sdd/progress.md`

## Summary

Task 39 implements the accepted Option A personality model: SPM's persisted friendliness and
fight/flight traits anchor a deterministic six-trait immutable profile, with UUID-derived latent
individuality and neutral fallback. Personality scales only already-eligible subjective preference
and reward deltas through positive `[0.75,1.25]` multipliers at the single normalized-learning seam.
The profile lives in the existing bounded mob context and frozen snapshot; no new registry, Goal,
scan, navigation, scheduler authority, or action selector was added.

## Commands and exact results

Working directory for every command:
`D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

1. `./gradlew.bat compileJava compileTestJava`
   - Exit 0; `BUILD SUCCESSFUL`; compile and test sources accepted.
2. Focused test command from the brief
   - Exit 0; `BUILD SUCCESSFUL` for `PersonalityModelTest`, `OpinionMemoryTest`,
     `OpinionExperienceRegistryRetentionTest`, and `SocialCompanionEpisodeRepairTest`.
3. `./gradlew.bat test`
   - Exit 0; `BUILD SUCCESSFUL`.
4. `./gradlew.bat clean build`
   - Exit 0; `BUILD SUCCESSFUL`; 8 actionable tasks executed.
5. Test XML aggregation
   - `tests=581 failures=0 errors=0 skipped=0 suites=90`.
6. `git diff --check`
   - Exit 0 after documentation finalization; no whitespace errors.

The clean build produced:
`build/libs/spmscavenger-1.9.2.jar`.

## Source evidence

- `CONFIRMED`: SPM reference
  `PlayerMobEntity.java:1788-1793` exposes public `fightFlight()` and `friendliness()`.
- `CONFIRMED`: SPM reference `DispositionTraits.java:29-89` owns and persists those two bounded
  host dimensions.
- `CONFIRMED`: `OpinionMemoryService.java` is the only new production call to
  `PersonalityModel.learningResponse`.
- `CONFIRMED`: static reference scan found no Personality reads in goal, mining, readiness, or
  director-selection code.
- `CONFIRMED`: tests prove exact neutral snapshot parity, same-sign bounded scaling, zero/ineligible
  non-creation, objective episode-fact preservation, host fallback determinism, and unload profile
  preservation.
- `UNVERIFIED`: how quickly ordinary players notice divergent long-term activity choice in a live
  modpack session.

## Self-review

- [x] Six immutable finite `[0,1]` traits.
- [x] SPM host anchors plus deterministic UUID latent traits.
- [x] New UUID yields a new gen-1 profile.
- [x] Positive response multipliers stay in `[0.75,1.25]`.
- [x] Neutral response exactly reproduces legacy GAO-2 memory mutation.
- [x] Personality cannot create, invert, or change eligibility of learning.
- [x] Repetition, duration, timestamps, failure counts, and causes remain objective.
- [x] Unsupported semantic pairs remain neutral.
- [x] No new long-lived collection; existing bounded snapshot owns unload lifetime.
- [x] Opinion-off gate remains before learning mutation.
- [x] No Goal/scheduler/navigation changes.
- [x] Focused tests, full tests, clean build, static MAIBS, and diff check completed.

## MAIBS semantic-drift review

`PLANNED → IMPLEMENTED → PREDICTED RUNTIME` matches the RFC. The mob performs the same activity,
path, interaction, interruption, and terminal sequence; personality changes only the subjective
memory delta after a normalized learning record. A later idle decision may rank activities
differently. Gate result: `PASS — BEHAVIORALLY_PLAUSIBLE`.

Predicted weirdness is bounded: preference can saturate at the existing ±100 clamp; a renamed SPM
trait API falls back to neutral host anchors with one warning; a reincarnated new UUID receives a
new personality by accepted gen-1 policy.

## Concerns

- Runtime visual salience is `UNVERIFIED`; an optional future experiment would run equal scripted
  episodes for two opposite profiles and compare decision traces over several idle opportunities.
- Gradle reports one pre-existing deprecation warning in `EpisodeRetentionTest` and Gradle-10
  compatibility warnings; neither originated in Task 39.
- Process deviation: implementation began before the numbered-task brief skill was loaded. The
  brief was created from the already locked RFC/user contract before final validation. Scope and
  acceptance were not retroactively broadened.
- No commit, push, PR, or Minecraft launch occurred.
