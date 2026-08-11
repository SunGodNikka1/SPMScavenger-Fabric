# Task 41 report: GAO-9 overland environment affinity

## Status

`DONE_WITH_CONCERNS` — static implementation, unit/build/package evidence, and MAIBS pass are
complete. Long-duration in-game route distribution and multi-mob performance remain `UNVERIFIED`.

## Files created or changed

- `src/main/java/com/noobk/spmscavenger/opinion/EnvironmentKind.java`
- `src/main/java/com/noobk/spmscavenger/opinion/EnvironmentProfile.java`
- `src/main/java/com/noobk/spmscavenger/opinion/EnvironmentClassifier.java`
- `src/main/java/com/noobk/spmscavenger/opinion/EnvironmentOpinionMemory.java`
- `src/main/java/com/noobk/spmscavenger/opinion/EnvironmentOpinionService.java`
- `src/main/java/com/noobk/spmscavenger/opinion/EnvironmentOpinionRouteRanker.java`
- `src/main/java/com/noobk/spmscavenger/experience/{ExperienceEvent,EpisodeLearningEvidence,ActivityEpisode,ExperienceEmitters,MobExperienceContext,MobExperienceSnapshot}.java`
- `src/main/java/com/noobk/spmscavenger/opinion/OpinionMemoryService.java`
- `src/main/java/com/noobk/spmscavenger/goal/ExploringGoal.java`
- focused environment tests and retention test additions
- RFC, README, version metadata, decisions, test matrix, task brief/report, and progress ledger

## Summary

Task 41 closes the ENVIRONMENT taxonomy with five enum-bounded, multi-label semantic contexts and
explicitly rejects independent PROJECT memory. Only successful overland expedition terminals add a
personality-scaled preference delta, divided across labels; future already-valid destinations receive
at most ±10 mean affinity. Environment preference has no pathfinding, malus, hazard, escape,
GoalSelector, or mandatory-descent authority.

## Commands and exact results

Working directory for every command:
`D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

1. RED: `.\gradlew.bat test --tests '*Environment*'`
   - failed at `compileTestJava` with 64 missing-symbol errors for the not-yet-created environment
     contracts; expected RED.
2. First GREEN: `.\gradlew.bat test --tests '*Environment*'`
   - 16 tests, one source-contract failure caused by a newline-sensitive search string.
   - test repaired to match the semantic call token rather than formatting.
3. Focused GREEN: `.\gradlew.bat test --tests '*Environment*'`
   - `BUILD SUCCESSFUL`.
4. Full suite: `.\gradlew.bat test`
   - `BUILD SUCCESSFUL`.
5. Final package after 1.9.4 version bump: `.\gradlew.bat clean build`
   - `BUILD SUCCESSFUL`; 618 tests, zero failures/errors/skips.
   - one existing deprecation warning in `EpisodeRetentionTest` for `episodeFor(UUID)`.
6. Package inspection:
   - `build/libs/spmscavenger-1.9.4.jar`, 536,818 bytes.
   - SHA-256 `34B312717E452AB0F2C132536B0415E8FE3D593A225C8164EA512A2AA7A389AE`.
   - all six GAO-9 environment classes and `fabric.mod.json` are present in the remapped JAR.
7. `git diff --check`
   - pass; line-ending conversion warnings only.

## Source evidence

- `CONFIRMED`: pinned SPM `PlayerMobEntity.registerGoals` remains host-owned and was not changed.
- `CONFIRMED`: pinned 1.21.1 mappings expose `BiomeTags.IS_FOREST`, `IS_OCEAN`, `IS_NETHER`,
  `IS_END`, and `Biome.coldEnoughToSnow(BlockPos)`.
- `CODE_CONFIRMED`: route classification occurs after the existing entity-ticking guard and only
  in non-forced route scoring.
- Three negative probes: `ProjectOpinionMemory`/biome-key map `NOT FOUND`; path/safety mutation in
  environment packages `NOT FOUND`; new Goal/scanner ownership `NOT FOUND`.

## Self-review

- [x] PROJECT ownership remains ACTIVITY / PLACE / EPISODE-TRACE.
- [x] Profile is immutable and cardinality is bounded by five enum constants.
- [x] Raw event → episode evidence → single `OpinionMemoryService` seam carries environment context.
- [x] Only `EXPEDITION_COMPLETE` is eligible; generic failures and interruptions cannot learn.
- [x] Multi-label evidence divides one delta; route aggregation uses mean.
- [x] ±10 cannot independently erase visited −20 or destination −100.
- [x] Opinion disabled prevents terminal classification, learning, and route classification/bias.
- [x] Snapshot/park/restore and partial-death preference semantics are tested.
- [x] No terrain-safety or scheduler authority changed.

## Post-implementation MAIBS

`PASS — BEHAVIORALLY_PLAUSIBLE` at static evidence level.

`PLANNED → IMPLEMENTED → PREDICTED RUNTIME`: A successful forest expedition records modest FOREST
affinity. On a later cooldown-gated expedition, only candidates that already survived ticking and
route construction receive the tie-breaker. The mob still follows the same vanilla path, yields to
the same higher-priority goals, escapes powder snow identically, and terminates/resumes expeditions
under the existing rules.

Predicted weird behaviors:

1. Border destinations may flip one label — `ACCEPTABLE_STEPPING_STONE`, bounded to ±10.
2. Untagged modded biomes remain neutral — `ACCEPTABLE_STEPPING_STONE`.
3. Several candidates sharing the same labels receive no relative change — expected neutral result.
4. Long-duration preference salience and candidate-distribution shift — `RUNTIME_QUESTION`.

## Concerns

- No Minecraft launch was authorized. Route distribution, modded-biome tag quality, and many-mob
  biome/height lookup cost are `UNVERIFIED`.
- Exact negative environment learning remains intentionally absent. Safety/recovery experiences do
  not create dislike; a future negative-affinity design needs an attributable semantic terminal.
- No commit, push, or PR was performed.
