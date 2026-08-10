# Task 31 report: PERF Slices 0A–2

## Status

`DONE_WITH_CONCERNS` — static/unit evidence complete; Spark runtime profiling deferred to Task 32
(Slice 4A). PERF RFC is **not** finished.

## Files created or changed

### Slice 0A

- `src/main/java/com/noobk/spmscavenger/SpmScavengerInstallPolicy.java`
- `src/main/java/com/noobk/spmscavenger/SpmScavenger.java`
- `src/test/java/com/noobk/spmscavenger/SpmScavengerInstallPolicyTest.java`

### Slice 0B

- `src/main/java/com/noobk/spmscavenger/experience/OpinionExperienceRegistry.java`
- `src/main/java/com/noobk/spmscavenger/activity/ActivityObservationService.java`
- `src/main/java/com/noobk/spmscavenger/experience/RestSessionCoordinator.java`
- `src/main/java/com/noobk/spmscavenger/opinion/DiscretionaryActivityDirector.java`
- `src/main/java/com/noobk/spmscavenger/opinion/OpinionFeatureGate.java` (test override helpers)
- `src/test/java/com/noobk/spmscavenger/experience/OpinionExperienceRegistryAllocationTest.java`

### Slice 1

- `src/main/java/com/noobk/spmscavenger/goal/FurnaceLookup.java`
- `src/main/java/com/noobk/spmscavenger/goal/SmeltAtFurnaceGoal.java`
- `src/main/java/com/noobk/spmscavenger/FurnaceStations.java` (`isUsableAt`)
- `src/test/java/com/noobk/spmscavenger/goal/FurnaceLookupTest.java`

### Slice 2

- `src/main/java/com/noobk/spmscavenger/goal/GatherResourcesGoal.java`
- `src/test/java/com/noobk/spmscavenger/goal/GatherScanCadenceTest.java`

### Planning

- `plans/RFC-PERFORMANCE-AND-PERCEPTION.md`

## Summary

Slices 0A–2 implement the RFC's bounded-perception foundation without a mega-goal or
`RestClaimRegistry`. Master disable now installs only the lease-cleanup observer and does not
retune SPM combat. Observer/rest read paths no longer allocate `MobExperienceContext` on cold
mobs. Furnace and gather world scans use `PhasedScanClock` with explicit furnace lookup semantics
fixing the DEFERRED-vs-ABSENT_RECENT placement bug.

## Commands and exact results

Working directory: `D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

```text
.\gradlew.bat clean build
BUILD SUCCESSFUL in ~21s
527 tests, 0 failures (build/reports/tests/test/index.html)
```

Artifact: `build/libs/spmscavenger-1.9.2.jar` (`CONFIRMED` from gradle `mod_version=1.9.2`).

## Source evidence (CONFIRMED / INFERRED / UNVERIFIED)

| Claim | Label | Evidence |
|-------|-------|----------|
| `enabled=false` installs 1 Scavenger goal only | `CONFIRMED` | `SpmScavengerInstallPolicyTest`, `SpmScavenger.install()` |
| Combat chase gated when disabled | `CONFIRMED` | `SpmScavengerInstallPolicy.appliesCombatChaseSpeed`, install order |
| `exploring=false` keeps host stroll | `CONFIRMED` | `SpmScavengerInstallPolicy.replacesHostStroll`, `installExploration()` |
| Observer validate/unload no allocate | `CONFIRMED` | `OpinionExperienceRegistryAllocationTest` |
| Director disabled + no context → no allocate | `CONFIRMED` | `OpinionExperienceRegistryAllocationTest` |
| Director disabled + existing context → invalidate | `CONFIRMED` | `DiscretionaryActivityDirectorTest.opinionDisableInvalidatesIntentBeforeConsumerGatesRelax` |
| DEFERRED does not authorize placement | `CONFIRMED` | `FurnaceLookupTest.mustNotHappen_deferredScanDoesNotAuthorizeFurnacePlacement` |
| ABSENT_RECENT may authorize placement | `CONFIRMED` | `FurnaceLookupTest.mustHappen_completedScanWithNoFurnaceMayAuthorizePlacement` |
| Gather cooperative path bypasses scan clock | `CONFIRMED` | `GatherResourcesGoal.canUse()` — `tryCooperativeAdmission` before `scanClock.claim` |
| Gather scan semantics unchanged | `INFERRED` | `findTarget()` body untouched; only cadence gate changed |
| MSPT/TPS/FPS improvement | `UNVERIFIED` | No Spark capture (Task 32) |
| PERF-3 PlanningSession necessary | `UNVERIFIED` | Requires `createPath` dominance in Spark |

## Self-review (mapped to brief)

| Requirement | Met |
|-------------|-----|
| Slice 0A install policy | Yes |
| Slice 0B non-allocating reads | Yes |
| Slice 1 furnace phased + lookup states | Yes (incl. parity fix) |
| Slice 2 gather phased clock only | Yes |
| No Slice 3 | Yes |
| No runtime in this task | Yes |

## Concerns

1. **Runtime proof gap** — all performance claims remain `UNVERIFIED` until Slice 4A Spark runs.
2. **Furnace duplicate race** — `ABSENT_RECENT` + `placeFurnaces` may still produce duplicate
   furnaces in multiplayer; static tests cannot falsify; RT-PERF-F1 in phase4-perf datapack.
3. **`DiscretionaryAuthority` / gated `contextFor()`** — not fully audited; not blocking per user.
4. **Config reload** — install policy applies at `ENTITY_LOAD` only (`INFERRED`).

## Acceptance tests (static)

| Must happen | Must not happen | Result |
|-------------|-----------------|--------|
| 527 tests pass | Regressions in director disable invalidation | `CONFIRMED` pass |
| DEFERRED ≠ placement auth | Placement on deferred scan | `CONFIRMED` `FurnaceLookupTest` |
| Cold observer paths no context | Unload creates context | `CONFIRMED` allocation tests |

## Next

Task 32 — Slice 4A profiling checkpoint (`docs/porting/PERFORMANCE_LOG.md`).
