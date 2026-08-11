# Task 41 brief: GAO-9 overland environment affinity

## Target

`D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

## Source reference

`D:\Apps\Minecraft Port\Projects\references\SocialPlayerMobs-v0.86.0`

## Source evidence

- `D:\Apps\Minecraft Port\Projects\references\SocialPlayerMobs-v0.86.0\src\main\java\games\brennan\playermob\entity\PlayerMobEntity.java`, especially `registerGoals`: host Goal ownership remains unchanged.
- `C:\Users\noobk\.gradle\caches\fabric-loom\1.21.1\loom.mappings.1_21_1.layered+hash.2198-v2\mappings.tiny`: pinned 1.21.1 mappings for `BiomeTags` and `Biome.coldEnoughToSnow(BlockPos)`.
- `plans\RFC-ADAPTIVE-OPINION-MOOD-AND-ENGAGEMENT.md`, D-GAO-035 through D-GAO-038 and Task 41.

## Locked decisions

- D-GAO-035: no independent PROJECT memory. Stable type is ACTIVITY, location is PLACE, and one instance belongs to EPISODE/TRACE.
- D-GAO-036: finite multi-label environment profile: `FOREST`, `OCEAN`, `SNOWY`, `NETHER`, `END`; classify only positions already examined by an experience or valid route.
- D-GAO-037: environment learning follows the existing normalized episode pipeline and is eligible only for `EXPEDITION_COMPLETE`. Divide one learning delta across labels; generic failures teach nothing.
- D-GAO-038: environment affinity is a soft route tie-breaker capped at ±10, below PLACE ±15, visited −20, and anti-fixation −100.
- Safety clarification: semantic affinity never modifies navigation malus, terrain safety, powder-snow avoidance/escape, ticking, reachability, hazard gates, or mandatory cave/descent authority.

## Required implementation

1. Add immutable `EnvironmentKind` / `EnvironmentProfile` and a classifier using vanilla biome tags plus positional snow suitability.
2. Extend raw and normalized episode evidence with an optional environment profile while retaining source compatibility for existing constructors/tests.
3. Add enum-bounded `EnvironmentOpinionMemory` to `MobExperienceContext` and its park/restore snapshot.
4. Apply a personality-scaled preference delta only for eligible successful expedition terminal evidence, divided across labels.
5. Add an `EnvironmentOpinionRouteRanker` with maximum absolute route bias 10 and mean aggregation.
6. In `ExploringGoal`, classify only final destinations of routes that already passed the entity-ticking guard; do not add scanning or make invalid routes valid.
7. Update the RFC, test matrix, task report, and progress ledger.

## Forbidden changes

- No new Goal, Goal flag/priority, navigation behavior, path malus, environmental escape behavior, terrain scanner, forced chunk access, project/biome-key map, client behavior, Opinion activity selection, or mandatory-work authority change.
- No Minecraft runtime launch, commit, push, or PR.
- Do not rewrite unrelated dirty files.

## RED/GREEN verification

Add failing tests first for:

- finite multi-label classification and neutral unknown profiles;
- multi-label learning normalization and completion-only eligibility;
- snapshot/freeze/death lifecycle and enum bound;
- disabled parity;
- route bias mean/cap and inability to erase the −20/−100 penalties;
- guard-before-classification/no-load call order through a pure seam or source-contract test;
- negative architecture probes for PROJECT storage and path/safety mutation.

Then run focused tests, the complete suite, and `gradlew.bat clean build`. Perform post-code MAIBS static review.

## Acceptance

- **Must happen:** one completed forest expedition updates bounded FOREST affinity and modestly changes a later comparable valid forest route score.
- **Must not happen:** path/frontier/order/combat/stale failures update environment affinity; liking SNOWY changes powder-snow/path safety; Opinion-off changes route ordering; environment creates unbounded maps or new world scans.

## Report

Write `.superpowers/sdd/task-41-report.md` and mark runtime route distribution/performance `UNVERIFIED`.
