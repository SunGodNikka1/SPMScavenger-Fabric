# Task 7 report: Negotiate horizontal furnace fuel faces atomically

## Status

`DONE_WITH_CONCERNS` — implementation, unit tests, config safety, and packaging pass; live vanilla
and modded-furnace behavior remains `UNVERIFIED` without an approved runtime session.

## Files created or changed

- `src/main/java/com/noobk/spmscavenger/FurnaceTransfers.java`
- `src/main/java/com/noobk/spmscavenger/FurnacePolicy.java`
- `src/main/java/com/noobk/spmscavenger/ScavengerConfig.java`
- `src/main/java/com/noobk/spmscavenger/client/ScavengerConfigScreen.java`
- `src/test/java/com/noobk/spmscavenger/FakeFurnaceContainer.java`
- `src/test/java/com/noobk/spmscavenger/FurnaceTransfersTest.java`
- `src/test/java/com/noobk/spmscavenger/FurnacePolicyTest.java`
- `README.md`
- `docs/porting/DECISIONS.md`
- `docs/porting/TEST_MATRIX.md`
- `plans/RFC-FURNACE-SMELTING.md`

## Summary

Fuel insertion now preflights NORTH/SOUTH/WEST/EAST without mutation, selects one face that can
accept the complete stack, and commits through only that face under the existing snapshot rollback.
U-F10 proves EAST-only support; a no-face test proves lossless rollback. The user-selected interim
`ironStockTarget` is configurable, defaults to 0, and explicit 6 retains the furnace test path.

## Commands and exact results

Working directory: `D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

```text
.\gradlew.bat test --tests com.noobk.spmscavenger.FurnaceTransfersTest --tests com.noobk.spmscavenger.FurnacePolicyTest
RED: compileTestJava failed with three missing ScavengerConfig.ironStockTarget symbols.
GREEN: BUILD SUCCESSFUL in 18s after implementation.
```

```text
.\gradlew.bat clean build
BUILD SUCCESSFUL in 19s
101 tests; 0 failures; 0 errors; 0 skipped
```

```text
Artifact: build/libs/spmscavenger-1.9.2.jar
Size: 162473 bytes
SHA-256: 8804903EDA89A6D13041D25A0A1BF07398B774740F890E4C15DBD5E2F8C13CA5
fabric.mod.json: present
temporary datapack entries: 0
```

```text
Old fixed-face negative probes in src/main:
NOT FOUND: \bFUEL_FACE\b
NOT FOUND: any side; furnace accepts fuel
NOT FOUND: placeThroughFace\(furnace, FUEL_FACE
```

## Source evidence

- `CODE_CONFIRMED`: fixed `FUEL_FACE = NORTH` removed; deterministic horizontal preflight and
  one-face commit are in `FurnaceTransfers`.
- `CODE_CONFIRMED`: U-F10 EAST-only success and no-face atomic rollback pass.
- `CODE_CONFIRMED`: U-F7 default 0 suppresses producer-only iron demand; explicit 6 enables it.
- `UNVERIFIED`: visual/live furnace behavior, save/reload during a configured iron job, and a real
  side-asymmetric third-party furnace.

## Self-review

- [x] Uses `WorldlyContainer` faces, not slot indexes.
- [x] Face selection does not mutate rejected faces.
- [x] One selected face owns the write.
- [x] Failure restores both inventories.
- [x] Default all-horizontal fake behavior remains green.
- [x] Interim target is UI-visible and safe for negative hand edits at policy evaluation.
- [x] No runtime launch, commit, push, or reference-tree edit.

## Concerns

Compilation/unit tests prove the transaction logic, not compatibility with every modded furnace.
The iron producer remains intentionally dormant by default until FS-8/TT-2b supplies a real
consumer. Inserted-ticket behavior is unchanged and statically resumes before new planning, but a
runtime config-change interruption probe is still required for behavioral confirmation.
