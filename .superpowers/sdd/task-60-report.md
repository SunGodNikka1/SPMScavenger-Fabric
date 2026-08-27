# Task 60 report: V4-P0 Task-59 tooling extraction + General Debug

## Status

**DONE_WITH_CONCERNS — STATIC/PACKAGE ACCEPTED**

The authorized P0 extraction is implemented. Both source sets compile, both test suites pass, and
both remapped artifacts pass package audits. Minecraft was not launched, so production-only loading
and validation-sidecar initializer/command registration remain **UNVERIFIED** under AV-1.

## Scope delivered

- Moved all 18 temporary Task-59 `V3*` classes into
  `src/validation/java/com/noobk/spmscavenger/validation/`.
- Moved all 15 Task-59 tests into `src/validationTest/java/.../validation/`.
- Added validation mod id `spmscavenger_validation`, initializer, independent command/lifecycle
  wiring, Fabric metadata, and packaged `spm_vr` datapack resources.
- Removed Task-59 command/tick/unload/death/server-stop wiring from production `SpmScavenger`.
- Added narrow permanent `/spmscavenger debug inspect <mob>` production readout.
- Added passive `MandatoryOwnershipRegistry.peekLiveClaim` and
  `MiningProjectSavedData.peekReadOnly`; neither mutates or creates state.
- Configured normal `clean build` to compile/test/remap/audit production and validation.
- Added source/dependency/package boundary checks and synthetic negative controls.
- Updated RFC, Task-59 report, progress, runtime/evidence/test matrices, runtime environment, and
  README without changing any VR-T3 verdict.

No KnownVillager, `VillageInteractionDirector`, destination ranking, first-home promotion, or other
V4 product behavior was implemented. No production Goal, admission, scheduling, trade, gather, or
village-work semantics were changed.

## Dependency and artifact boundary

```text
spmscavenger_validation -> spmscavenger -> Minecraft / SPM
```

Production source has no validation imports/references. Validation classes use only the separate
`com.noobk.spmscavenger.validation.*` namespace and compile against production output. Validation
fixtures/resources remain validation-local.

## Verification

Focused command:

```text
.\gradlew.bat test --tests "com.noobk.spmscavenger.debug.GeneralDebugCommandsTest" --tests "com.noobk.spmscavenger.debug.ValidationArtifactBoundaryTest" validationTest
```

Result: **PASS**. This includes synthetic forbidden-production-entry and duplicate-validation-class
negative controls.

Final command:

```text
.\gradlew.bat clean build
```

Result: **BUILD SUCCESSFUL** in 44 seconds; 16 tasks executed.

| Suite | Tests | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: |
| Production | 1,624 | 0 | 0 | 0 |
| Validation | 57 | 0 | 0 | 0 |

One existing deprecation warning remains in `EpisodeRetentionTest`; it is unrelated to P0.

## Package audit

| Assertion | Result |
| --- | --- |
| Production mod id | `spmscavenger` |
| Production validation-namespace classes | 0 |
| Production legacy `debug/V3*` classes | 0 |
| Production `spm_vr` scenario resources | 0 |
| Production packaged upstream Trade Everything classes | 0 |
| Validation mod id | `spmscavenger_validation` |
| Validation depends on production | YES |
| Validation classes | 60, all under validation namespace |
| Validation scenario function resources | 24 |
| Validation/production duplicate class entries | 0 |

Artifacts:

| Artifact | Size | SHA-256 |
| --- | ---: | --- |
| `build/libs/spmscavenger-1.11.0.jar` | 1,160,278 bytes | `4A742B531C0518CA06E53045D7EB571FB7E50443BCC1C74CE289E42E2B1A99D0` |
| `build/libs/spmscavenger-1.11.0-validation.jar` | 135,814 bytes | `BB02D551AEED4733434A3756401A9B520091C4056477A7C347CD656CC5F546A0` |

## Negative evidence

1. Production import/reference to `com.noobk.spmscavenger.validation` — **NOT FOUND**.
2. Production reference to `V3RuntimeCampaignController` or `V3RuntimeWitnessCommands` — **NOT
   FOUND**.
3. Production JAR validation namespace, legacy `debug/V3*`, or `data/spm_vr` entry — **NOT FOUND**.

## Alternatives and objections

- Keep Task-59 in production: rejected because normal servers would retain temporary command/tick/
  lifecycle coupling.
- Delete the harness: rejected because V3 certification is still open and must remain resumable.
- Separate continuously compiled validation sidecar: implemented; cost is dual-artifact maintenance,
  controlled by the normal build and duplicate/package audits.

Strongest remaining objection: compile/package evidence cannot prove Fabric loads the two mods in the
intended order or that validation commands register at runtime. That claim remains explicitly
UNVERIFIED and requires a separately approved Minecraft validation launch, not a code change.

## MAIBS-1 / observable contract

**Must happen:** production alone retains normal AI and the passive General Debug command; installing
the matching validation sidecar restores Task-59 orchestration.

**Must not happen:** production schedules Task-59, General Debug creates/mutates state, validation
duplicates production classes, or P0 changes PlayerMob behavior.

Static/source/package evidence supports the boundary. Observable runtime loading is not claimed.

## Handoff

V4-P0 is complete to its authorized static/package proof class. V4-R0 may proceed. Remaining V3
runtime certification continues later with an exact approved production/validation artifact pair;
no row was awarded by this extraction.
