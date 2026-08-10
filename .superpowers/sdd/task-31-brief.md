# Task 31 brief: PERF Slices 0A–2 (install, allocation, furnace, gather)

## Target

`D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

## Source reference

`D:\Apps\Minecraft Port\Projects\references\SocialPlayerMobs-v0.86.0\` (SPM goal ownership baseline)

## RFC

`plans/RFC-PERFORMANCE-AND-PERCEPTION.md` — Slices 0A, 0B, 1, 2

## Binding constraints

- No Minecraft runtime launch in this task (static/unit evidence only).
- No commit unless user requests.
- Do **not** begin Slice 3 (`PlanningSession`).
- SPM remains authoritative when `enabled=false`; no mega-goal refactor.

## Required deliverables

### Slice 0A — install policy

- `SpmScavengerInstallPolicy.java` — testable install gates.
- `SpmScavenger.java` — `alreadyInstalled()` before combat chase; disabled → cleanup observer only;
  `exploring=false` → keep SPM `WaterAvoidingRandomStrollGoal`.
- Tests: `SpmScavengerInstallPolicyTest`.

### Slice 0B — non-allocating reads

- `OpinionExperienceRegistry.find()`, `hasLiveRestClaim()`.
- Read paths: `ActivityObservationService`, `RestSessionCoordinator`, `DiscretionaryActivityDirector`
  (disabled path uses `find()`; no allocate on cold mobs).
- Tests: `OpinionExperienceRegistryAllocationTest`.

### Slice 1 — furnace phased scan

- `PhasedScanClock` on `SmeltAtFurnaceGoal`.
- `FurnaceStations.isUsableAt()` cache revalidation.
- `FurnaceLookup` explicit `FOUND` / `DEFERRED` / `ABSENT_RECENT` — placement only after
  `ABSENT_RECENT`.
- Tests: `FurnaceLookupTest`.

### Slice 2 — gather phased scan

- Replace `scanCooldown` with `PhasedScanClock` (interval 60, salt 61).
- Cooperative admission and `findTarget()` semantics unchanged.
- Tests: `GatherScanCadenceTest`.

## Verification

```powershell
cd "D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric"
.\gradlew.bat clean build
```

Report: `.superpowers/sdd/task-31-report.md`
