# Task 19 report: looted diamond pickaxe across equipment slots

## Status

`DONE_WITH_CONCERNS` — source/unit/build/package confirmed; runtime remains unverified.

## Changes and verification

Added backpack/main/off-hand ownership through tier, crafting, demand, gather, furnace, exploration,
and tool-drawing paths. `ToolBox` swaps an off-hand winner into main hand without deleting either
stack. Usable and broken off-hand regressions were added.

- RED: compile failed on missing off-hand-aware raw-iron/diamond deficit contracts.
- Focused tier/gather-intent tests passed.
- `gradlew.bat clean build`: 181 tests, 0 failures/errors/skips.
- JAR: `build/libs/spmscavenger-1.9.2.jar`
- SHA-256: `7B4CB209035CF11959F3C5E115D11F9A6A434D8061906F91358A35B2D8330F32`

MAIBS-1: `PASS — BEHAVIORALLY_PLAUSIBLE`. Runtime falsification requires three equipment-location
cases plus combat interrupt/re-arm/redraw and duplication/loss checks.
