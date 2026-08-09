# Task 17 brief: MI-6 — cave opportunistic ore

## Target

`D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

## Source evidence

- RFC: `plans/RFC-MINING-INTELLIGENCE-AND-WEALTH-SYSTEM.md` — Cave opportunism MI-6; decision flow “in cave/ravine → opportunistic ore”
- D-MIW-008: only exposed/legitimate ore
- Existing: `GatherTargetPolicy`, `GatherProtection.isExposedToAir`, `ExploringGoal` landings

## Binding constraints

- No Minecraft launch, commit, or push
- No MiningMemory / MiningDirector / staircase (MI-7/14/15)
- Must not target buried ore; must not surface diamond local gather above band

## Requirements

1. Pure `CaveContextPolicy`:
   - `isCaveLike(mobY, surfaceY)` — under surface by ≥ `MIN_DEPTH_BELOW_SURFACE` (8)
   - `orePriorityBonus(caveLike, isOre)` — positive bonus only in cave for ore resources
   - `landingPreferenceKey(landingY, mobY, surfaceY, preferCaveContinuation)` — prefer staying under surface when already cave-like
2. `GatherTargetPolicy.priority` accepts cave context; adds ore bonus
3. `GatherResourcesGoal.findTarget` computes surface heightmap → cave flag → sort
4. `ExploringGoal.landingCandidates` uses cave-continuation preference when mob is already cave-like
5. Unit tests; `.\gradlew.bat test`; report + progress + RFC

## Must happen

- mobY 10, surfaceY 70 → cave-like; ore priority > same ore on surface context
- Cave-like landing under surface preferred over sky-heightmap peer when continuing cave
- Buried/UNDISCOVERED still rejected

## Must not happen

- Clairvoyant buried ore
- MiningMemory / director / dig staircase
