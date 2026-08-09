# Task 16 brief: MI-5 — explore downward bias (D-MIW-031)

## Target

`D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

## Source evidence

- RFC: `plans/RFC-MINING-INTELLIGENCE-AND-WEALTH-SYSTEM.md` — F-2 / D-MIW-031
- `WorkDemandPolicy.diamondDeficit` currently zeros above Y=16 (blocks both gather *and* descent signal)
- `ExplorationReadiness` / `ExploringGoal` / `ExplorationActivityGoal`

## Binding constraints

- No Minecraft launch, commit, or push
- Do not implement MiningDirector, MiningMemory, staircase, vein frontier, or F-6
- Must not reintroduce surface diamond gather scanning (local eligibility stays Y-gated)
- Preserve greed=0 consumer gather parity

## Requirements

1. Split signals:
   - `diamondProgressionDemand(backpack, mainHand, cfg)` — craft deficit, **no Y gate**
   - `isDiamondLocalGatherEligible(mobBlockY)` — `mobBlockY <= DIAMOND_GENERATION_CEILING_Y`
   - `diamondDeficit(...)` — progression demand only when local-eligible (gather resting state)
2. Pure `DescentPressurePolicy.wantsDescentExplore(progressionDemand, localEligible, hasSighting)`
   - true when progression > 0 && !localEligible && !sighting
3. `ExplorationReadiness.recordDescentPressure()` unlocks `eligible` (still respects cooldown)
4. `ExplorationActivityGoal` evaluates pressure from backpack + Y
5. `ExploringGoal.landingCandidates` prefers lower Y when descent pressure active
6. Unit tests for split + descent policy + readiness; run `.\gradlew.bat test`
7. Update progress.md, task-16-report.md, RFC

## Must happen

- Surface + iron pick + diamond tier → progression demand > 0, local deficit 0, wantsDescent true
- Deep Y ≤ 16 → local deficit > 0, wantsDescent false
- Surface gather intent still omits DIAMOND

## Must not happen

- Surface eternal diamond gather scan
- Clairvoyant buried targeting
- MiningDirector / staircase this task
