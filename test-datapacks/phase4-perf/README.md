# Slice 4A — performance checkpoint datapack

Namespace: `spm_phase4`

## Purpose

Repeatable PlayerMob populations for Spark profiling at **1 / 10 / 50 / 100** mobs after PERF
Slices 0A–2. Does **not** declare PERF complete — it supplies a controlled world fixture only.

## Prerequisites

- Minecraft **1.21.1** Fabric dev client or disposable test instance
- **Social Player Mobs** (`playermob`) **0.86.0+1.21.1** in `mods/`
- **Spark** (`spark`) Fabric build for 1.21.1 in `mods/`
- **SPM Scavenger** artifact under test (`build/libs/spmscavenger-*.jar`)
- Copy this folder to the instance `datapacks/` directory, or symlink from repo

## Quick start

```text
/function spm_phase4:setup
/function spm_phase4:arena/build
/function spm_phase4:spawn/count_10
```

Wait **60s** warm-up, then start Spark (`/spark profiler start --timeout 120`).

## Population presets

| Function | PlayerMobs |
|----------|------------|
| `spm_phase4:spawn/count_1` | 1 |
| `spm_phase4:spawn/count_10` | 10 |
| `spm_phase4:spawn/count_50` | 50 |
| `spm_phase4:spawn/count_100` | 100 |

Each mob is named `P4PerfNN`, tagged `spm_p4_perf`, and issued `playermob stay … here 64` so they
remain in the arena.

## Scavenger config for profiling

Use Mod Menu or `config/spmscavenger.json`:

- `enabled`: **true**
- `opinionEnabled`: **false** (isolate PERF slices; opinion is a separate variable)
- `gatherResources`: **true**
- `smeltEnabled`: **true**
- `exploring`: **true**
- `placeFurnaces`: **true** (required for furnace-duplicate edge case)

Record the exact config JSON in `docs/porting/PERFORMANCE_LOG.md` per run.

## Edge case — furnace duplicate race (RT-PERF-F1)

After mob A completes a phased furnace search with **ABSENT_RECENT** (no furnace found), place a
furnace during the 100-tick negative cooldown window while mob B (or the player) is also eligible
to smelt.

```text
/function spm_phase4:scenario/furnace_duplicate_setup
# wait until logs show ABSENT_RECENT / smelt goal active without furnace
/function spm_phase4:scenario/place_shared_furnace
```

**Must observe:** whether multiple mobs place duplicate furnaces vs reuse the shared one after
cooldown expires.

**Must not happen (product decision TBD):** N identical scavenger-owned furnaces within 16 blocks
when one communal furnace would suffice.

## Spark capture checklist

Per population size (1, 10, 50, 100):

1. `/spark tps` — baseline before profiler
2. `/spark profiler start --timeout 120`
3. Play idle 2 minutes (no player interaction except spectator)
4. Export: `run/spark/` profiler tree + `/spark health` if available
5. Record in `docs/porting/PERFORMANCE_LOG.md`:
   - median / p95 MSPT
   - top 10 server hot methods (especially `createPath`, `findUsable`, `findTarget`, `planCurrentStage`)
   - scan synchrony note (do many mobs hit gather/furnace scans same tick?)
   - client FPS lows if profiling integrated server + client

## Decision rubric (RFC Slice 4A)

| Dominant hotspot | Next action |
|------------------|-------------|
| `ExploringGoal` / `createPath` / `planCurrentStage` | PERF-3 PlanningSession justified |
| `GatherResourcesGoal.findTarget` / `FurnaceStations.findUsable` | Repair scan cadence further before PERF-3 |
| `OpinionExperienceRegistry` / GC / retained maps | PERF-5B retention policy |
| SPM / vanilla navigation only | Do not rewrite Scavenger; investigate SPM/modpack |
