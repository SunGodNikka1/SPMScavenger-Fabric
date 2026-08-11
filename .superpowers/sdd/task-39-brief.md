# Task 39 brief: GAO-7 PersonalityModel

## Target

`D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

## Source reference

`D:\Apps\Minecraft Port\Projects\references\SocialPlayerMobs-v0.86.0`

## Source evidence

- `src/main/java/games/brennan/playermob/entity/PlayerMobEntity.java:1788` — public persisted
  `fightFlight()` accessor.
- `src/main/java/games/brennan/playermob/entity/PlayerMobEntity.java:1792` — public persisted
  `friendliness()` accessor.
- `src/main/java/games/brennan/playermob/entity/DispositionTraits.java:29` — host owns exactly
  those two stable disposition dimensions, stored on a bounded 0–10 scale.

## Locked decisions

- D-GAO-028: personality interprets normalized evidence; it never selects or commands activity.
- D-GAO-029: Option A — immutable host-anchored profile plus deterministic UUID latent traits.
- D-GAO-030: subjective preference/reward multipliers are bounded to `[0.75,1.25]`; objective
  repetition, duration, timestamps, eligibility, and causal attribution remain unchanged.
- New UUID means a new gen-1 personality.
- Personality may scale an existing subjective delta but never create, invert, or make one eligible.

## Required implementation

1. Add immutable six-trait `PersonalityModel` and semantic bounded response API.
2. Read SPM `fightFlight`/`friendliness` through the existing optional, read-only reflection bridge;
   fail to neutral host anchors with one warning.
3. Bind the profile at the single live context and preserve it through the bounded RET-GAO-1 park
   snapshot; do not add a new static per-mob collection.
4. Apply the response exactly once at `OpinionMemoryService` → `OpinionMemory` after normalized
   evidence and existing eligibility checks.
5. Preserve the legacy overload with `PersonalityLearningResponse.NEUTRAL` as exact GAO-2 parity.

## Acceptance

**Must happen:** equal eligible evidence produces bounded, same-sign preference divergence for
different relevant traits; neutral response reproduces the exact pre-GAO-7 memory snapshot.

**Must not happen:** zero or ineligible evidence gains subjective learning; signs invert; objective
episode facts differ; Personality changes Goals, navigation, safety, readiness, mandatory work,
or Opinion-off behavior; unload changes a live identity's profile.

## Verification

Run from the target root:

```powershell
.\gradlew.bat test --tests "com.noobk.spmscavenger.opinion.PersonalityModelTest" --tests "com.noobk.spmscavenger.opinion.OpinionMemoryTest" --tests "com.noobk.spmscavenger.experience.OpinionExperienceRegistryRetentionTest" --tests "com.noobk.spmscavenger.experience.SocialCompanionEpisodeRepairTest"
.\gradlew.bat test
.\gradlew.bat clean build
git diff --check
```

Update the GAO-7 RFC topic, decision/task/gate state, `.superpowers/sdd/progress.md`, and write
`.superpowers/sdd/task-39-report.md`.

## Binding constraints

- No Minecraft runtime launch.
- No commit, push, PR, dependency change, or unrelated cleanup.
- Runtime observable differentiation remains `UNVERIFIED`; static semantic behavior may be accepted
  under PD-GAO-12 when no `RUNTIME_QUESTION` remains.
