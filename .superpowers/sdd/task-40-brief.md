# Task 40 brief: GAO-8A passive physical expression

## Target

`D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

## Source reference

`D:\Apps\Minecraft Port\Projects\references\SocialPlayerMobs-v0.86.0`

## Source evidence

- `src/main/java/games/brennan/playermob/entity/PlayerMobEntity.java:756-855` — host GoalSelector
  priority/flag order; command, safety, social, eating, work, look-at, and random-look ownership.
- `src/main/java/games/brennan/playermob/entity/ObjectiveReadout.java:75-85` —
  `RandomLookAroundGoal` and `LookAtPlayerGoal` subclasses are filtered as cosmetic noise.
- Target `src/main/java/com/noobk/spmscavenger/goal/AnticsGoal.java:54-141` — existing flagless
  cosmetic goal directly writes `LookControl` during mimicry and therefore needs a narrow
  Opinion-on non-interference repair.

## Locked product boundary and proposed architecture accepted by authorization

- PD-GAO-13: passive LOOK/cosmetic output only; no activity choice, MOVE authority, priority
  changes, or command/combat/progression override.
- D-GAO-031…034 / Option A from the RFC: finite interruptible priority-8 `LOOK`-only goal;
  idle/rest/explore eligibility; no terrain-salience claim; no mood-driven bunny hopping; preserve
  legacy Antics gaze when Opinion is disabled.

## Required implementation

1. Add a pure passive-expression policy/profile. BORED shortens bounded cooldown, STRESSED shortens
   bounded holds, curiosity widens non-semantic gaze, and sociability controls preference for a
   nearby self-liked PlayerMob. Personality/affect must not create action authority.
2. Publish the profile from the existing 10-tick `ExplorationActivityGoal` observation cadence
   after affect observation. Do not scan GoalSelector again.
3. Store only ephemeral state in the existing bounded `MobExperienceContext`; invalidate it on
   freeze/unload and do not add it to persisted/frozen snapshots.
4. Add `PassiveExpressionGoal` as priority 8, subclassing `RandomLookAroundGoal` for SPM readout
   noise compatibility and owning exactly `LOOK`. It may call `LookControl` only; it must never call
   navigation, jump, target, block/item interaction, or mutate activity/opinion state.
5. Limit social candidate acquisition to an eight-block bounded query at episode start, require
   line of sight and `PlayerMobs.feelingToward(self, other) > PlayerMobs.neutralFeeling()`, and fail
   closed/fall back to non-social gaze when relationship access is unavailable.
6. Preserve Opinion-off behavior exactly. When Opinion is enabled, `AnticsGoal` crouch mimicry must
   not directly overwrite scheduled LOOK; bunny-hop behavior is not mood-wired.

## Acceptance

**Must happen:** deterministic policy tests prove bounded monotonic cadence/hold/breadth changes;
eligible idle/rest/explore observations publish a profile; the goal contract is priority 8 + LOOK
only + finite; self-liked candidates are eligible while neutral/hostile/unreadable candidates are
not; higher-priority LOOK ownership remains scheduler-authoritative.

**Must not happen:** a new selector scan/map/persisted commitment appears; expression owns MOVE,
navigates, jumps, starts/cancels activity, mutates OpinionMemory, scans terrain/resources, appears
in ObjectiveReadout, writes gaze with Opinion disabled through the new goal, or changes legacy
Antics behavior while Opinion is disabled.

## Required RED/GREEN tests

- `PassiveExpressionPolicyTest`: disabled/ineligible profile; boredom cooldown monotonicity; stress
  hold monotonicity; curiosity breadth monotonicity; every bound finite and valid.
- `PassiveExpressionStateTest`: publish/read/invalidate; frozen/unload state cannot resurrect.
- `PassiveExpressionGoalContractTest`: priority constant 8, required flags exactly LOOK, bounded
  episode/cooldown helpers, and `RandomLookAroundGoal` inheritance/readout-noise contract.
- `PassiveExpressionSocialPolicyTest`: self-liked strict-above-neutral only, neutral/hostile/null
  fail closed.
- `AnticsGoal` policy seam test: Opinion off retains legacy gaze permission; Opinion on forbids the
  flagless gaze write.
- Static negative probes for navigation/jump/world-action calls and a second GoalSelector scan.

## Verification

Run from the target root:

```powershell
.\gradlew.bat test --tests "com.noobk.spmscavenger.opinion.PassiveExpressionPolicyTest" --tests "com.noobk.spmscavenger.opinion.PassiveExpressionStateTest" --tests "com.noobk.spmscavenger.goal.PassiveExpressionGoalContractTest" --tests "com.noobk.spmscavenger.opinion.PassiveExpressionSocialPolicyTest" --tests "com.noobk.spmscavenger.goal.AnticsExpressionPolicyTest"
.\gradlew.bat test
.\gradlew.bat clean build
git diff --check
```

Update the RFC GAO-8A topic/decisions/gates, `.superpowers/sdd/progress.md`, and write
`.superpowers/sdd/task-40-report.md`.

## Binding constraints

- Strict RED-GREEN-REFACTOR: add focused tests first and record representative RED output.
- No Minecraft runtime launch, GameTest, commit, push, PR, dependency change, or unrelated cleanup.
- Runtime visual cadence and live GoalSelector preemption remain `UNVERIFIED` without a separately
  authorized targeted launch; report status must reflect Gate AV-1.
