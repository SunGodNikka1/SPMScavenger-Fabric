# Task 14 brief: MI-13 discovery classification + MI-2 target priority

## Target

`D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

## Source reference

`plans/RFC-MINING-INTELLIGENCE-AND-WEALTH-SYSTEM.md` — gen-1 slice D-MIW-025 frontier after MI-4R.

## Binding implementation

- Add `DiscoveryMode` enum and `DiscoveryPolicy` to classify pass-one candidates as
  `VISIBLE`, `NEWLY_EXPOSED`, or `UNDISCOVERED` (MEMORY/LOCAL_SEARCH/LOOT reserved).
- Add `GatherTargetPolicy` with blocking consumer demand outranking optional wealth; distance
  tie-breaker; reject `UNDISCOVERED`.
- Wire `GatherResourcesGoal.findTarget` pass-two to sort the 24-slot buffer by priority before
  path probes; track `lastHarvest` for `NEWLY_EXPOSED` vein follow.
- Do not implement `MiningDirector`, `MiningMemory`, portfolio/scarcity, F-2 progression-demand
  split, runtime datapack, Minecraft launch, commit, or push.

## Acceptance

- Must: buried ore → `UNDISCOVERED`; exposed ore → `VISIBLE`; adjacent ore within 40 ticks of
  break → `NEWLY_EXPOSED`; blocking iron outranks wealth coal even when coal is nearer.
- Must not: clairvoyant buried ore targets; wealth-only candidate displacing blocking need.
- Run `gradlew.bat test`; record in task-14-report, progress, RFC.
