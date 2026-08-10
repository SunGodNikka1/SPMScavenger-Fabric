# SPM Scavenger performance log

Canonical artifact for **PERF Slice 4** (Gate P4A). Slices 0A–2 static evidence:
`.superpowers/sdd/task-31-report.md`.

**RFC:** `plans/RFC-PERFORMANCE-AND-PERCEPTION.md`  
**Datapack:** `test-datapacks/phase4-perf/` (workload-split fixtures; **no `playermob stay`**)  
**Status:** `UNVERIFIED` — no Spark runs recorded yet.

## Environment template (fill per session)

| Field | Value |
|-------|-------|
| Date | |
| Minecraft | 1.21.1 |
| Loader | Fabric 0.16.14 |
| SPM version | |
| Spark version | |
| Scavenger artifact | `build/libs/spmscavenger-*.jar` SHA-256: |
| JVM flags | |
| View/sim distance | |
| `opinionEnabled` | false (recommended baseline) |
| Config snapshot | paste `config/spmscavenger.json` or Mod Menu export |

## Workload profiles

| Profile | Function | Pop scale | Config notes |
|---------|----------|-----------|--------------|
| P4A-BASE | `profile/p4a_base/run_N` | 1 / 10 / 50 / 100 | all subsystems on |
| P4A-GATHER stress | `profile/p4a_gather_stress/run_N` | 10 / 50 | optional `smeltEnabled: false` |
| P4A-GATHER sparse | `profile/p4a_gather_sparse/run_N` | 10 / 50 | optional `smeltEnabled: false` |
| P4A-SMELT | `profile/p4a_smelt/run_N` | 10 / 50 | optional `gatherResources: false` |
| P4A-EXPLORE | `profile/p4a_explore/run_N` | 10 / 50 | `gatherResources: false`, `smeltEnabled: false`, `exploring: true` |
| RT-PERF-F1 | `scenario/furnace_duplicate_setup` | 2 | smelt + placeFurnaces |

## Population runs

| ID | Profile | Mobs | Warm-up | Profiler s | MSPT med | MSPT p95 | TPS min | Top hotspot (%) | Spark path | Notes |
|----|---------|------|---------|------------|----------|----------|---------|-----------------|------------|-------|
| P4A-BASE-1 | BASE | 1 | 60s | 120 | | | | | | |
| P4A-BASE-10 | BASE | 10 | 60s | 120 | | | | | | |
| P4A-BASE-50 | BASE | 50 | 60s | 120 | | | | | | |
| P4A-BASE-100 | BASE | 100 | 60s | 120 | | | | | | |
| P4A-GATHER-STRESS-10 | GATHER stress | 10 | 60s | 120 | | | | | | |
| P4A-GATHER-STRESS-50 | GATHER stress | 50 | 60s | 120 | | | | | | |
| P4A-GATHER-SPARSE-10 | GATHER sparse | 10 | 60s | 120 | | | | | | |
| P4A-SMELT-10 | SMELT | 10 | 60s | 120 | | | | | | |
| P4A-SMELT-50 | SMELT | 50 | 60s | 120 | | | | | | |
| P4A-EXPLORE-10 | EXPLORE | 10 | **90s** | 120 | | | | | | sanity gate |
| P4A-EXPLORE-50 | EXPLORE | 50 | **90s** | 120 | | | | | | sanity gate |

### Hot method watchlist

Record inclusive % from Spark tree (server thread):

- `net.minecraft.world.entity.ai.navigation.PathNavigation.createPath`
- `com.noobk.spmscavenger.goal.ExploringGoal.planCurrentStage`
- `com.noobk.spmscavenger.goal.GatherResourcesGoal.findTarget`
- `com.noobk.spmscavenger.FurnaceStations.findUsable`
- `com.noobk.spmscavenger.goal.ExplorationActivityGoal.tick`
- `com.noobk.spmscavenger.experience.OpinionExperienceRegistry`

## P4A-EXPLORE sanity gate (required for PERF-3)

Fill after warm-up, **before** interpreting explore Spark export:

| Check | P4A-EXPLORE-10 | P4A-EXPLORE-50 | Evidence |
|-------|----------------|----------------|----------|
| ≥1 mob created expedition | | | |
| `planCurrentStage` > 0% in Spark | | | |
| `createPath` from expedition planner observed | | | |

**Gate result:** PASS / **PROFILE INVALID FOR PERF-3 DECISION**

## Scan synchrony (qualitative)

| Profile | Population | Gather burst? | Furnace burst? | Evidence |
|---------|------------|---------------|----------------|----------|
| BASE | 50 / 100 | | | |
| GATHER stress | 50 | | | |
| SMELT | 50 | | | |

## Client (optional integrated server)

| ID | FPS median | FPS 1% low | Frame spike notes |
|----|------------|------------|-------------------|
| P4A-BASE-10 | | | |
| P4A-BASE-50 | | | |

## RT-PERF-F1 — furnace duplicate race

**Setup:** `/function spm_phase4:scenario/furnace_duplicate_setup`  
**Trigger:** after both mobs show smelt demand + ABSENT_RECENT window, `/function spm_phase4:scenario/place_shared_furnace`

| Observation | Result |
|-------------|--------|
| Scavenger-owned furnaces within 24b after 5 min | |
| Duplicate placements during 100-tick cooldown | |
| Both mobs eventually share one furnace | |

**Verdict:** `UNVERIFIED` | product-acceptable | defect — needs design change

## Slice 4A decision (fill after runs)

| Dominant evidence (profile-specific) | Decision |
|--------------------------------------|----------|
| | PERF-3 PlanningSession / defer / PERF-5B / no Scavenger rewrite |

**PERF-3 authorized?** yes / no / defer — reason:

**Explore sanity gate:** PASS / INVALID —

## Historical reference

- v1.6.0 Spark: 32.10% tick in `GatherResourcesGoal.tick()` before protection moved (`DECISIONS.md`, pre-1.7.5).
- Pre-2026-08-09 fixture: `playermob stay` + mixed log cube invalidated explore and BASE profiles (`INVALID`).
