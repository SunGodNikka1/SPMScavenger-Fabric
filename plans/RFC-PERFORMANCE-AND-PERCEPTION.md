# RFC — Performance and bounded perception

**Status:** `LOCKED` (2026-08-09)  
**Scope:** `Projects/SPMScavenger-1.21.1-Fabric`  
**North star:** SPM remains the entity/behavior substrate; Scavenger remains multiple independent executors; expensive perception and planning become **bounded shared infrastructure** — not one mega-goal.

## Problem statement

Removing the Scavenger JAR improves memory use and reduces FPS spikes (`CONFIRMED` user playtest). Root cause is not “13 Java goal objects” but **scheduler participants that perform large world scans or path planning on `canUse()`**, plus **retained JVM state** and an **allocating read API** on observation paths.

## Non-goals

- Collapsing Scavenger into one `ScavengerBrainGoal`.
- Introducing `RestClaimRegistry` before profiling proves `MobExperienceContext` rest bookkeeping is insufficient.
- Introducing `ScanBudget` abstraction before furnace/gather migrate to proven `PhasedScanClock`.
- Runtime config rewiring on already-loaded mobs (v1 documents: **entity reload / respawn required**).

## Architecture

```text
PlayerMob scheduler
   ├── SPM combat / social / loot / stroll / …
   ├── Scavenger executors (gather, smelt, mine, explore, shelter, …)
   └── ExplorationActivityGoal (flagless observer)
             │
             ▼
      bounded infrastructure
        ├── PhasedScanClock (existing — campfire, shelter, torch)
        ├── scan failure cooldown (per goal)
        ├── incremental path probes (explore — later slice)
        └── non-allocating registry reads (PERF-5A)
```

## Work packages

### PERF-4 — Config respects SPM ownership (`Slice 0A`)

| Rule | Behavior |
|------|----------|
| `enabled=false` | Do **not** replace `WeaponAwareAttackGoal`. Do **not** install ordinary Scavenger executors. Install **only** `ExplorationActivityGoal(allowNewMiningWork=false)` for lease cleanup. |
| `exploring=false` | Do **not** remove/replace SPM `WaterAvoidingRandomStrollGoal`. Skip `ExploringGoal` + `TrackedLocalWanderGoal`. Mining executors + observer may remain. |
| `ENTITY_LOAD` idempotency | `alreadyInstalled()` before **any** goal mutation including combat chase. |
| Runtime toggle | Document reload requirement for v1. |

**Bug fixed:** `SpmCombatChaseSpeed.apply()` ran before `cfg.enabled` and before `alreadyInstalled()`.

### PERF-5A — Non-allocating experience reads (`Slice 0B`)

| API | Semantics |
|-----|-----------|
| `find(UUID)` | `CONTEXTS.get(id)` — never allocates |
| `hasLiveRestClaim(UUID)` | `find` + live claim check — never allocates |
| `contextFor(UUID)` | `computeIfAbsent` — **only** for experience-producing paths |

**Read paths that must not allocate:**

- `ActivityObservationService.externalRestState()`
- `RestSessionCoordinator.validate()` — absent context → no-op
- `RestSessionCoordinator.invalidateOnUnload()` — absent context → no-op
- `DiscretionaryActivityDirector.tick()` — return when `!OpinionFeatureGate.isEnabled()` before `contextFor`

When opinion is disabled but a context **already exists**, director still ticks via `find()` to
invalidate stale intents (`OPINION_DISABLED`) without allocating new contexts.

**Deferred:** PERF-5B retention policy; PERF-5C `RestClaimRegistry` only if profiling/design requires.

### PERF-1 — Furnace scan scheduling (`Slice 1`)

- `PhasedScanClock` on `SmeltAtFurnaceGoal` (`canUse` search path).
- Failed-search cooldown after `findUsable` returns null.
- Per-mob cached candidate; invalidate when not `isUsableAt`; **always** `tryClaimWalk` at use time.
- Explicit lookup outcomes: `FOUND`, `DEFERRED`, `ABSENT_RECENT` — placement only after `ABSENT_RECENT`.
- No dimension furnace index in v1.

### PERF-2 — Gather staggering (`Slice 2`) — `IMPLEMENTED`

- `scanCooldown` replaced with `PhasedScanClock` (interval 60, salt 61).
- Cooperative admission, two-pass candidates, backoff, and discovery semantics unchanged.

### PERF-3 — Exploration planner budget (`Slice 3`)

- Persistent `PlanningSession` survives `canUse() == false`.
- **≤2–3 `createPath()` probes per planner slice** (`canUse` poll), not per server tick.
- Session cap remains 20 total probes.
- Parity test: unlimited budget vs sliced budget → same landing under deterministic inputs.
- While planning incomplete: **MOVE remains available to local wandering** (`TrackedLocalWanderGoal` when exploration installed; SPM stroll when not).

### PERF-6 — Performance gate (`Slice 4`)

- Scenarios: 1 / 10 / 50 / 100 PlayerMobs.
- Spark evidence, scan synchrony check, path-probe counters.
- Artifact: `docs/porting/PERFORMANCE_LOG.md`.

## Implementation order

1. **Slice 0A** — install policy + combat chase guard  
2. **Slice 0B** — non-allocating reads  
3. **Slice 1** — furnace phased scan  
4. **Slice 2** — gather phased scan  
5. **Slice 3** — incremental explore planner  
6. **Slice 4** — Spark gate  

## Acceptance (Slice 0 + 1)

| Must happen | Must not happen |
|-------------|-----------------|
| `enabled=false` → 1 Scavenger goal (cleanup observer) only | Combat goal replaced while disabled |
| `exploring=false` → SPM stroll retained | `TrackedLocalWanderGoal` installed |
| Observer/validate/unload with no prior context → `contextCount` unchanged | Unload creates new context |
| Smelt search respects phased clock + failed cooldown | 16,807-position scan every `canUse` poll |
| Deferred furnace scan does not authorize placement | `DEFERRED` treated as confirmed absence |
| Gather phased scan | Cooperative/mine paths bypass global scan clock |

## Change log

| Date | Change |
|------|--------|
| 2026-08-09 | Slice 2 gather phased scan; PERF-1 `FurnaceLookup` state parity fix |
