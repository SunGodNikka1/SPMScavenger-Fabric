# Task 16 report: MI-5 — explore downward bias (D-MIW-031)

## Status

`DONE_WITH_CONCERNS`

## Summary

Split diamond progression demand from local gather eligibility. Surface mobs keep
progression pressure that unlocks explore and biases landings downward, without
reintroducing surface diamond gather scans.

## Files

| File | Change |
| --- | --- |
| `WorkDemandPolicy.java` | `diamondProgressionDemand`, `isDiamondLocalGatherEligible`; deficit stays local-gated |
| `DescentPressurePolicy.java` | `wantsDescentExplore` + landing sort key |
| `ExplorationReadiness.java` | descent pressure unlocks `eligible` |
| `ExplorationActivityGoal.java` | evaluates pressure from backpack + Y |
| `ExploringGoal.java` | lower-Y landing preference under pressure |
| Tests | `DescentPressurePolicyTest`, DiamondTier + Readiness updates |

## Verification

| Command | CWD | Result |
| --- | --- | --- |
| `.\gradlew.bat test` | `SPMScavenger-1.21.1-Fabric` | `BUILD SUCCESSFUL` — **165** tests (`CONFIRMED`) |

## Evidence labels

| Claim | Label |
| --- | --- |
| Surface progression > 0, local deficit 0 | `CONFIRMED` — DiamondTierTest |
| wantsDescentExplore on surface progression | `CONFIRMED` — DescentPressurePolicyTest |
| Descent pressure unlocks explore readiness | `CONFIRMED` — ExplorationReadinessTest |
| Runtime cave/ravine descent behaviour | `UNVERIFIED` — no Minecraft launch |

## Concerns

1. Heightmap landings are still surface/roof-biased; preference only reorders within
   `MAX_LANDING_ELEVATION` of current Y — not a cave seeker (MI-6 / MI-14).
2. Sighting path always `false` until MiningMemory (MI-15).
3. No staircase / dig-down (MI-7).

## Self-review vs brief

- Signal split: done
- Descent policy + readiness + landing bias: done
- Surface gather still omits diamond: preserved
- Director/memory/staircase omitted: correct
