# Task 6 report: Repair the Phase 2 furnace validation datapack

## Status

`DONE_WITH_CONCERNS` — the static setup gate passes; runtime behavior remains `UNVERIFIED` because
Minecraft was not launched.

## Files created or changed

- `test-datapacks/phase2-furnace/data/spm_phase2/function/_init_scoreboard.mcfunction`
- `test-datapacks/phase2-furnace/README.md`
- `docs/porting/TEST_MATRIX.md`
- `plans/RFC-FURNACE-SMELTING.md`
- `D:\Apps\Minecraft Port\docs\agent-workflows\RUNTIME_TEST_DATAPACK.md` (same malformed canonical example corrected)

## Summary

The storage target path now appears before `set value`, matching the mapped 1.21.1 command tree.
The Phase 2 kit parses as pack format 34, all internal function calls resolve, and the known
malformed trailing-path form is absent. The datapack remains a temporary non-shipping test kit.

## Commands and exact results

Working directory: `D:\Apps\Minecraft Port`

```text
PowerShell static validator
PASS pack_format=34; mcfunction_files=27; internal_function_refs=33; missing=0; initializer=valid
```

Working directory: `D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

```text
.\gradlew.bat clean build
BUILD SUCCESSFUL in 19s
101 tests; 0 failures; 0 errors; 0 skipped
```

## Source evidence

- `SOURCE_CONFIRMED`: the malformed and required command forms were pinned in Topic: Validation of
  `plans/RFC-FURNACE-SMELTING.md` from mapped 1.21.1 `DataCommands`.
- `CODE_CONFIRMED`: the repaired initializer and static validator results above.
- `UNVERIFIED`: `/reload`, `spm_phase2:help`, and RT-F1–RT-F5 in a live world.

## Self-review

- [x] Correct target-path-before-operation grammar.
- [x] `pack.mcmeta` JSON and pack format checked.
- [x] All internal namespace references resolve.
- [x] Known malformed form rejected.
- [x] Temporary datapack absent from final JAR.
- [x] No Minecraft launch, commit, push, or reference-tree edit.

## Concerns

Runtime command parsing and behavior need a separately approved Minecraft launch. The older
`phase1-tool-tier` datapack contains its own historical initializer copy and was deliberately not
edited because FS-6 is scoped to the Phase 2 furnace kit.
