# Task 6 brief: Repair the Phase 2 furnace validation datapack

## Target

`D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

## Source reference

`D:\Apps\Minecraft Port\Projects\references\SocialPlayerMobs-v0.86.0` (read-only host-mod oracle).

## Source evidence

- The host reference has no furnace/smelting implementation after the three RFC probes for `furnace`, `smelt`, and `AbstractFurnace`; this task validates addon-owned behavior rather than copying host behavior.
- Target evidence: `plans/RFC-FURNACE-SMELTING.md`, Topic: Validation and task FS-6.
- Command grammar evidence recorded in the RFC: mapped Minecraft 1.21.1 `DataCommands` requires the storage target path before `set value`.

## Binding constraints

- Correct only the Phase 2 furnace datapack and directly relevant validation/RFC documents.
- Do not launch Minecraft, commit, push, or edit the read-only source reference.
- Datapack remains a temporary test artifact and must not enter the mod JAR.

## Required files/API

- Repair `test-datapacks/phase2-furnace/data/spm_phase2/function/_init_scoreboard.mcfunction` to use `data modify storage spm_phase2:main initialized set value 1b`.
- Parse `pack.mcmeta` as JSON.
- Verify every `function spm_phase2:*` call resolves to a `.mcfunction` file.
- Reject the known malformed trailing-path form.

## Acceptance and verification

- **Must happen:** the initializer writes `initialized=1b` using target-path-before-operation grammar, and every internal function reference resolves.
- **Must not happen:** the malformed `set value 1b initialized` form remains, or the datapack is treated as runtime proof.
- Record exact static-check results in `.superpowers/sdd/task-6-report.md` and update FS-6 in the existing RFC.
