# Task 18 report: MI-6A + MI-6D + MI-6B + MI-6C

## Status

`DONE_WITH_CONCERNS`

## Summary

Repaired MI-6 MAIBS landing/classifier defects without MiningMemory or staircase:
local 3D cave floors (6A), combined `LandingMode` including `DESCENT_IN_CAVE` (6D),
local-rim ravine detection (6B), and per-candidate gather cave opportunity (6C).
Deferred MI-6E / MI-6F / MI-6G per user.

## Files

| File | Change |
| --- | --- |
| `CaveContextPolicy.java` | rim, `isCaveOrRavineLike`, `LandingMode`, `caveOpportunity`, mode keys |
| `CaveLandingResolver.java` | bounded X±4 / Z±4 / Y±6 standable floors (≤16 / ≤180 probes) |
| `ExploringGoal.java` | rim + 3D floors + unified mode sort |
| `GatherResourcesGoal.java` | per-candidate opportunity array |
| `GatherTargetPolicy.java` | opportunity overloads for priority/sort |
| Tests | expanded `CaveContextPolicyTest`; `CaveLandingResolverTest` |

## Verification

| Command | CWD | Result |
| --- | --- | --- |
| `.\gradlew.bat test` | `SPMScavenger-1.21.1-Fabric` | `BUILD SUCCESSFUL` — **178** tests, 0 failures (`CONFIRMED`) |

## Evidence labels

| Claim | Label |
| --- | --- |
| Open ravine via rim (feet≈column, rim high) | `CONFIRMED` — unit |
| `DESCENT_IN_CAVE` prefers under-surface deep over surface top | `CONFIRMED` — unit |
| Cave opportunity when candidate column cave-like (mob outside) | `CONFIRMED` — unit |
| Resolver admits standable floors / respects caps | `CONFIRMED` — unit |
| Runtime cave hop stays underground; ravine ore bonus | `UNVERIFIED` — no launch |

## Concerns

1. MI-6F commitment and MI-6G snapshot still deferred — branch thrash / roof-as-cave remain.
2. Flat +15 ore bonus kept (MI-6E deferred for MI-17).
3. Natural descent / MI-7 still need runtime proof before staircase.

## Self-review vs brief

- 6A/6B/6C/6D: done
- 6E/6F/6G: not implemented (deferred)
- Unbounded flood fill / buried ore / comparator redesign: avoided
