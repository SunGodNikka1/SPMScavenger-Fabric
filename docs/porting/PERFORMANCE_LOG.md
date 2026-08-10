# SPM Scavenger performance log

Canonical artifact for **PERF Slice 4** (Gate P4A). Slices 0A–2 static evidence:
`.superpowers/sdd/task-31-report.md`.

**RFC:** `plans/RFC-PERFORMANCE-AND-PERCEPTION.md`  
**Datapack:** `test-datapacks/phase4-perf/`  
**Status:** `UNVERIFIED` — no Spark runs recorded yet.

## Environment template (fill per session)

| Field | Value |
|-------|-------|
| Date | |
| Minecraft | 1.21.1 |
| Loader | Fabric 0.16.14 |
| SPM version | |
| Spark version | |
| Scavenger artifact | `build/libs/spmscavenger-1.9.2.jar` SHA-256: |
| JVM flags | |
| View/sim distance | |
| `opinionEnabled` | false (recommended baseline) |
| Config snapshot | paste `config/spmscavenger.json` or Mod Menu export |

## Population runs

| ID | Mobs | Warm-up | Profiler s | MSPT med | MSPT p95 | TPS min | Top server hotspot (%) | Spark export path | Notes |
|----|------|---------|------------|----------|----------|---------|------------------------|-------------------|-------|
| P4A-1 | 1 | 60s | 120 | | | | | | |
| P4A-10 | 10 | 60s | 120 | | | | | | |
| P4A-50 | 50 | 60s | 120 | | | | | | |
| P4A-100 | 100 | 60s | 120 | | | | | | |

### Hot method watchlist

Record inclusive % from Spark tree (server thread):

- `net.minecraft.world.entity.ai.navigation.PathNavigation.createPath`
- `com.noobk.spmscavenger.goal.ExploringGoal.planCurrentStage`
- `com.noobk.spmscavenger.goal.GatherResourcesGoal.findTarget`
- `com.noobk.spmscavenger.FurnaceStations.findUsable`
- `com.noobk.spmscavenger.goal.ExplorationActivityGoal.tick`
- `com.noobk.spmscavenger.experience.OpinionExperienceRegistry`

## Scan synchrony (qualitative)

| Population | Gather scans same-tick burst? | Furnace scans same-tick burst? | Evidence |
|------------|-------------------------------|--------------------------------|----------|
| 10 | | | |
| 50 | | | |
| 100 | | | |

Note: phased salts should spread scans; burst at N=100 falsifies stagger benefit.

## Client (optional integrated server)

| ID | FPS median | FPS 1% low | Frame spike notes |
|----|------------|------------|-------------------|
| P4A-10 | | | |
| P4A-50 | | | |

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

| Dominant evidence | Decision |
|-------------------|----------|
| | PERF-3 PlanningSession / defer / PERF-5B / no Scavenger rewrite |

**PERF-3 authorized?** yes / no / defer — reason:

## Historical reference

- v1.6.0 Spark: 32.10% tick in `GatherResourcesGoal.tick()` before protection moved (`DECISIONS.md`, pre-1.7.5).
