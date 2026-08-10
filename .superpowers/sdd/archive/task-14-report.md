# Task 14 report: MI-13 + MI-2

## Status

`DONE_WITH_CONCERNS`

## Summary

Implemented discovery classification and gather target priority for the gen-1 mining intelligence
slice. Blocking consumer demand now outranks optional wealth when choosing among pass-one
candidates before expensive path probes.

## Files

| File | Change |
| --- | --- |
| `DiscoveryMode.java` | New enum — `VISIBLE`, `NEWLY_EXPOSED`, `MEMORY`, `LOCAL_SEARCH`, `LOOT`, `UNDISCOVERED` |
| `DiscoveryPolicy.java` | Classify ore/logs/stone; 40-tick harvest reveal window |
| `GatherTargetPolicy.java` | Legitimacy gate + priority sort (blocking 100 > wealth 50) |
| `goal/GatherResourcesGoal.java` | `lastHarvest` tracking; priority-sorted pass-two probes |
| `DiscoveryPolicyTest.java` | 4 tests |
| `GatherTargetPolicyTest.java` | 3 tests |

## Verification

| Command | CWD | Result |
| --- | --- | --- |
| `.\gradlew.bat test` | `SPMScavenger-1.21.1-Fabric` | `BUILD SUCCESSFUL` — 155 tests (`CONFIRMED`) |

## Evidence labels

| Claim | Label |
| --- | --- |
| Discovery modes classify exposed/buried/adjacent ore | `CONFIRMED` — `DiscoveryPolicyTest` |
| Blocking iron beats wealth coal in sort order | `CONFIRMED` — `GatherTargetPolicyTest` |
| Runtime vein follow / side-ore behaviour | `UNVERIFIED` — no Minecraft launch |
| F-2 `ProgressionDemand` vs `LocalGatherEligibility` split | `DEFERRED` — not in this task |

## Concerns

1. **F-1 utility scale** — wealth net utility still negative at realistic acquisition costs; MI-2
   priority uses blocking tier, not wealth net utility, so blocking>wealth is `CONFIRMED` in tests
   but wealth-vs-wealth tie cases remain scale-sensitive.
2. **F-2 band split** — MI-2 band gate extension (descent motivation above Y=16) not implemented;
   only target priority among legitimate local candidates.
3. **NEWLY_EXPOSED** — classification and +5 priority bonus only; no `VeinFrontier` queue (MI-16).

## Self-review vs brief

- Discovery enum + policy: done
- GatherTargetPolicy priority: done
- GatherResourcesGoal wire: done
- Tests + build: done
- Director/memory/portfolio: correctly omitted
