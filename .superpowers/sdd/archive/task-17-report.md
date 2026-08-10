# Task 17 report: MI-6 — cave opportunistic ore

## Status

`DONE_WITH_CONCERNS`

## Summary

Added subterranean/ravine context detection and prefer exposed ore while cave-like.
Gather priority boosts ore underground; explore landings prefer staying under the surface
when already subterranean. No MiningMemory or staircase.

## Files

| File | Change |
| --- | --- |
| `CaveContextPolicy.java` | `isCaveLike`, ore bonus, landing key |
| `GatherTargetPolicy.java` | caveLike overloads for priority/sort |
| `GatherResourcesGoal.java` | heightmap → caveLike → sort/priority |
| `ExploringGoal.java` | cave-continuation landing preference |
| Tests | `CaveContextPolicyTest` + gather cave ore priority |

## Verification

| Command | CWD | Result |
| --- | --- | --- |
| `.\gradlew.bat test` | `SPMScavenger-1.21.1-Fabric` | `BUILD SUCCESSFUL` — **169** tests (`CONFIRMED`) |

## Evidence labels

| Claim | Label |
| --- | --- |
| Depth ≥8 under surface → cave-like | `CONFIRMED` — unit |
| Cave ore priority > surface ore / cave logs | `CONFIRMED` — unit |
| Runtime ravine/cave opportunism | `UNVERIFIED` — no launch |

## Concerns

1. Explore still resolves landings via heightmap tops — cannot follow true cave branches (MI-14/15).
2. Cave heuristic is depth-only (no air-volume / skylight yet).
3. No MiningMemory cave entrances.

## Self-review vs brief

- CaveContextPolicy + gather wire + explore continuation: done
- Buried ore still illegitimate: preserved
- Memory/director/staircase omitted: correct
