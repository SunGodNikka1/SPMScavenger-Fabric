# Task 9 report: MI-1 gather intent consolidation

## Status

`DONE_WITH_CONCERNS` — source, unit, build, and packaging checks pass; Minecraft runtime behavior is
`UNVERIFIED` because no launch was authorized.

## Files created or changed

- `src/main/java/com/noobk/spmscavenger/GatherIntentPolicy.java`
- `src/main/java/com/noobk/spmscavenger/goal/GatherResourcesGoal.java`
- `src/test/java/com/noobk/spmscavenger/GatherIntentPolicyTest.java`
- `.superpowers/sdd/task-9-brief.md`, this report, and `progress.md`
- `plans/RFC-MINING-INTELLIGENCE-AND-WEALTH-SYSTEM.md`
- `docs/porting/DECISIONS.md`, `docs/porting/TEST_MATRIX.md`

## Summary

MI-1 adds one immutable aggregate intent containing the existing log, coal, cobble, raw-iron, and
diamond consumer needs. `GatherResourcesGoal` evaluates it once before a bounded target scan and
reuses it for candidate filtering and retained drops; crafting remains the cheaper immediate action.
No wealth, discovery, scoring, persistence, scanner, or config behavior was added.

## Commands and exact results

Working directory: `D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`.

- RED: `.\gradlew.bat test --tests com.noobk.spmscavenger.GatherIntentPolicyTest` failed at
  `compileTestJava` with 13 missing-`GatherIntentPolicy` errors.
- First GREEN attempt compiled; 3/4 tests passed. The craft-ready fixture used plank tags, which
  standalone Bootstrap leaves empty. Replaced it with an exact-item iron recipe fixture.
- GREEN: the focused command passed 4/4 tests.
- `.\gradlew.bat clean build`: `BUILD SUCCESSFUL`; 128 tests, 0 failures, 0 errors, 0 skipped.
- Final artifact: `build/libs/spmscavenger-1.9.2.jar`, 183,010 bytes, SHA-256
  `904C10BA1FA345A9CC0636CB726E300416FD6545BD1E85A43D3E66E73A895184`.
- `jar tf` confirms `GatherIntentPolicy` and `GatherResourcesGoal` classes are packaged.

## Evidence and self-review

- `CODE_CONFIRMED`: `GatherIntentPolicy.evaluate` derives all flags from the live container,
  main hand, config, Y coordinate, and existing consumer policies.
- `CODE_CONFIRMED`: `GatherResourcesGoal.wantsMore` installs one snapshot; its scan does not run the
  full policy per block.
- Must happen covered: torch log/coal intent, raw-iron intent, and deep diamond intent.
- Must not covered: surface diamond demand and gathering while a craft step is immediately ready.
- An exclusive resource enum was not used because MI-2, not MI-1, owns prioritization.

## Concerns

- Actual goal scheduling, target choice, retained drops, and interaction with crafting require an
  approved Minecraft session to become `RUNTIME_CONFIRMED`.
- MI-1 deliberately does not repair the pass-one buried-ore candidate issue; that remains MI-13.
