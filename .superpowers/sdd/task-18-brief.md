# Task 18 brief: MI-6A + MI-6D + MI-6B + MI-6C

## Target

`D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

## Binding

- Implement MI-6A, 6D, 6B, 6C only
- **Defer:** MI-6E, MI-6F, MI-6G
- No Minecraft launch, commit, or push
- No MiningMemory / staircase / clairvoyant ore

## Requirements

### MI-6B — local rim
- Sample surrounding heightmap columns (8 directions, radius 8)
- `localRimHeight` = upper-median of samples
- `isCaveOrRavineLike(feetY, columnSurface, rim)` = column depth ≥8 OR rim depth ≥8

### MI-6A — local 3D cave landings
- When mob is cave/ravine-like, around hop centre: X±4, Z±4, Y±6
- Collect standable floors (reuse ExploringGoal `safeStand` semantics via shared checks)
- Cap ≤16 cave landings; keep heightmap ring as fallback
- Bound total volume probes

### MI-6D — combined landing modes
- `NORMAL | DESCENT | CAVE_CONTINUATION | DESCENT_IN_CAVE`
- No if/else that drops cave when descending
- DESCENT_IN_CAVE: prefer under-surface/rim, then lower Y, then nearer elev

### MI-6C — candidate cave opportunity
- Per gather candidate: column cave-like and/or under mob rim while mob cave/ravine-like
- Ore bonus uses opportunity, not mob-only boolean

## Must happen
- Unit: rim detects open ravine (feet≈column surface, rim high)
- Unit: DESCENT_IN_CAVE key prefers under-surface deep over surface
- Unit: caveOpportunity true for underground ore when mob outside if candidate column cave-like
- Explore adds non-heightmap standable when volume has floor

## Must not
- 6E comparator redesign; 6F commitment; 6G snapshot enum
- Unbounded flood fill; buried ore targeting
