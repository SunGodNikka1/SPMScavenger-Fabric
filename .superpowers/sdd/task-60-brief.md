# Task 60 brief: V4-P0 Task-59 tooling extraction + General Debug

## Status and target

**Status:** AUTHORIZED by User, 2026-08-26.  
**Target:** `D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`  
**Host reference:** `D:\Apps\Minecraft Port\Projects\references\artifacts\playermob-fabric-0.89.0+1.21.1.jar`  
**Canonical architecture:** `plans/RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md`, `D-VR-096` / V4-P0.  
**Report:** `.superpowers/sdd/task-60-report.md`

This is an addon architecture extraction, not a source-port parity slice. The pinned SPM artifact is
relevant only to keeping the dormant Task-59 validation harness installable; no host behavior is
changed or reimplemented.

## Existing evidence

- `src/main/java/com/noobk/spmscavenger/SpmScavenger.java` registers the temporary V3 command and
  calls `V3RuntimeCampaignController` on tick, unload, death, and server stop.
- `src/main/java/com/noobk/spmscavenger/debug/V3RuntimeWitnessCommands.java` and the other
  `debug/V3*` classes are the removal-bound Task-59 surface.
- `src/main/java/com/noobk/spmscavenger/village/work/VillageWorkFactsService.java` exposes a
  non-creating passive `peekReadOnly` seam that must remain production.
- `build.gradle` currently defines only main + JUnit. Loom is pinned to 1.14.10.
- Fabric Loom supports a separate mod/source set; exact build syntax for this project remains
  `UNVERIFIED` until `clean build` and package audit pass.

## Binding architecture

```text
spmscavenger_validation -> spmscavenger -> Minecraft / SPM
```

1. Move all Task-59 controller/scenario/Gate-0/contamination/temporal-witness machinery to a
   separate validation/test-mod source set under `com.noobk.spmscavenger.validation.*`.
2. Validation has its own `fabric.mod.json`, initializer, command/lifecycle wiring, resources, and
   separately installable `spmscavenger-1.11.0-validation.jar`.
3. Production has zero imports/references/dependency on validation and contains zero validation
   resources/classes.
4. Validation may consume public passive production truth APIs. Do not widen production mutation or
   authority APIs for validation. Validation-local fixtures/mixins/accessors/datapacks own invasive
   test support.
5. Replace the V3-specific production inspector with a narrow permanent General Debug inspector.
   It reports passive current truth only; it may not run scenarios, create fixtures, force chunks,
   schedule interrupts, open evidence windows, or award runtime-matrix verdicts.
6. Do not implement KnownVillager, VillageInteractionDirector, V4 ranking, home promotion, or other
   V4 product behavior.

## Build and artifact contract

Normal `gradlew clean build` must:

- compile main and validation source sets;
- run production and validation tests;
- build `build/libs/spmscavenger-1.11.0.jar`;
- build `build/libs/spmscavenger-1.11.0-validation.jar`;
- execute structural/package assertions for both artifacts.

Production JAR must contain zero:

- `com/noobk/spmscavenger/validation/**`;
- `V3RuntimeCampaignController`, `V3CampaignScenario`, `V3Gate0*`, `V3Contamination*`, temporal
  witnesses, Task-59 fixture commands;
- validation initializer, mixin config, or validation `fabric.mod.json`.

Validation JAR must declare mod id `spmscavenger_validation`, depend on `spmscavenger`, and contain no
duplicate production class entries.

## Verification and negative controls

1. Add focused tests proving General Debug is passive and production has no Task-59 lifecycle wiring.
2. Add dependency-direction source assertions and package audits.
3. Demonstrate the package test fails in isolation when a synthetic forbidden production entry or
   duplicate validation entry is supplied.
4. Run focused production/validation structural tests.
5. Run `gradlew clean build`.
6. Audit both JAR entry lists, packaged upstream Trade Everything classes, artifact paths/sizes, and
   SHA-256.
7. Update RFC, Task-59 report, runtime/test matrices, progress, and maintainer build documentation
   that actually exists. Do not invent a missing generic port guide in this slice.

## Acceptance

**Must happen:** installing production alone provides normal AI plus passive General Debug; installing
validation beside production restores Task-59 command/lifecycle capability from the validation mod.

**Must not happen:** production references validation, validation duplicates production classes,
Task-59 machinery remains in production, General Debug mutates state, or any V4 product behavior is
introduced.

No Minecraft launch. No commit or push.
