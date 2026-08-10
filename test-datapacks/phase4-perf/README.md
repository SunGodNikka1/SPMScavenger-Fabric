# Slice 4A — performance checkpoint datapack

Namespace: `spm_phase4`

## Purpose

Repeatable **workload-separated** PlayerMob populations for Spark profiling after PERF Slices
0A–2. Does **not** declare PERF complete — it supplies controlled world fixtures only.

**Validity fixes (2026-08-09):**

- **No `playermob stay`** on profiling mobs. Stay sets `StayAnchorState=PRESENT`; Scavenger's
  `ExplorationPolicy.allowsExpedition()` requires `ABSENT`, so stay would zero out
  `ExploringGoal.planCurrentStage` and falsely justify skipping PERF-3.
- **No mixed forest cube in BASE/EXPLORE/SMELT.** The old 13×13×5 oak-log mass is isolated to
  `P4A-GATHER (stress)` only.

## Prerequisites

- Minecraft **1.21.1** Fabric dev client or disposable test instance
- **Social Player Mobs** (`playermob`) **0.86.0+1.21.1** in `mods/`
- **Spark** (`spark`) Fabric build for 1.21.1 in `mods/`
- **SPM Scavenger** artifact under test (`build/libs/spmscavenger-*.jar`)
- Copy this folder to the instance `datapacks/` directory, or symlink from repo

## Quick start (P4A-BASE)

```text
/function spm_phase4:setup
/function spm_phase4:anchor/set
/function spm_phase4:profile/p4a_base/run_10
```

Stand in **spectator** at the anchor. Wait **60s** warm-up, then Spark
(`/spark profiler start --timeout 120`).

Re-run a profile after `/function spm_phase4:spawn/count_10` (or `count_N`) only if the arena is
already built; full profiles rebuild arena + spawn.

## Workload profiles

| Profile | Function | Populations | Purpose |
|---------|----------|-------------|---------|
| **P4A-BASE** | `profile/p4a_base/run_{1,10,50,100}` | 1 / 10 / 50 / 100 | Overall Scavenger overhead; sparse distributed trees; barrier rim + chunk forceload |
| **P4A-GATHER (stress)** | `profile/p4a_gather_stress/run_{10,50}` | 10 / 50 | Slice 2 validation; 13×13×5 log cube NW |
| **P4A-GATHER (sparse)** | `profile/p4a_gather_sparse/run_{10,50}` | 10 / 50 | Realistic gather scans; distributed single trees |
| **P4A-SMELT** | `profile/p4a_smelt/run_{10,50}` | 10 / 50 | Slice 1 validation; smelt pad + oak_log demand, no furnace |
| **P4A-EXPLORE** | `profile/p4a_explore/run_{10,50}` | 10 / 50 | PERF-3 go/no-go; open pad, no gather/smelt bait |
| **RT-PERF-F1** | `scenario/furnace_duplicate_setup` | 2 | Furnace duplicate race |

Low-level spawn-only (after arena built): `spawn/count_{1,10,50,100}`.

## Containment without stay orders

Profiling mobs are **not** issued SPM stay commands. Containment uses:

- `forceload` on the pad (3×3 chunks BASE/GATHER/SMELT; 5×5 EXPLORE)
- Low **barrier rim** on BASE/GATHER/SMELT pads only (not EXPLORE — expeditions need outbound travel)
- `spreadplayers` after summon (`spawn/_finalize`)
- Optional fixed anchor: `anchor/set` then run profiles at the marker

Kill perf mobs between runs: each `spawn/count_N` starts with `kill @e[...,tag=spm_p4_perf]`.

## Scavenger config per profile

Record exact JSON in `docs/porting/PERFORMANCE_LOG.md`.

### P4A-BASE (default mixed overhead)

```json
{
  "enabled": true,
  "opinionEnabled": false,
  "gatherResources": true,
  "smeltEnabled": true,
  "exploring": true,
  "placeFurnaces": true
}
```

### P4A-GATHER / P4A-SMELT

Same as BASE, or disable the other subsystem if isolating:

- GATHER profiles: `smeltEnabled: false` optional
- SMELT profiles: `gatherResources: false` optional (arena has no trees)

### P4A-EXPLORE (required for PERF-3 decision)

```json
{
  "enabled": true,
  "opinionEnabled": false,
  "gatherResources": false,
  "smeltEnabled": false,
  "exploring": true,
  "placeFurnaces": false
}
```

Default eligibility: `exploreLocalTripsThreshold=2`, `exploreIdleTicks=600` (30s idle). Use **≥90s**
warm-up before Spark on EXPLORE profiles.

## P4A-EXPLORE sanity gate (mandatory before PERF-3)

Run `/function spm_phase4:profile/p4a_explore/sanity_gate` and verify **before** interpreting Spark:

| Check | Pass criterion |
|-------|----------------|
| Expedition created | At least one mob starts outbound expedition (logs / observation) |
| `planCurrentStage` | Spark shows `ExploringGoal.planCurrentStage` > 0% |
| `createPath` | Expedition planner `createPath` activity observed |

If any check fails: **PROFILE INVALID FOR PERF-3 DECISION** — absence of explore hotspots is not
evidence that PERF-3 is unnecessary.

## RT-PERF-F1 — furnace duplicate race

```text
/function spm_phase4:scenario/furnace_duplicate_setup
# wait until logs show ABSENT_RECENT / smelt goal active without furnace
/function spm_phase4:scenario/place_shared_furnace
```

**Must observe:** duplicate furnace placement vs shared reuse after cooldown.

## Spark capture checklist

Per profile row in `PERFORMANCE_LOG.md`:

1. `/spark tps` — baseline before profiler
2. `/spark profiler start --timeout 120`
3. Idle 2 minutes (spectator; no player interaction)
4. Export `run/spark/` profiler tree
5. Record MSPT, top hot methods, scan synchrony note

## Decision rubric (RFC Slice 4A)

| Dominant hotspot (in matching profile) | Next action |
|----------------------------------------|-------------|
| `ExploringGoal` / `createPath` / `planCurrentStage` in **P4A-EXPLORE** (sanity gate passed) | PERF-3 PlanningSession justified |
| `findTarget` in **P4A-GATHER** | Repair gather scan cadence before PERF-3 |
| `findUsable` / `FurnaceLookup` in **P4A-SMELT** | Repair furnace scan before PERF-3 |
| `OpinionExperienceRegistry` / GC / retained maps | PERF-5B retention policy |
| SPM / vanilla navigation only | Do not rewrite Scavenger; investigate SPM/modpack |

Do **not** use P4A-BASE or P4A-GATHER (stress) alone to defer PERF-3.
