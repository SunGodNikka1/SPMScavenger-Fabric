# Task 7 brief: Negotiate horizontal furnace fuel faces atomically

## Target

`D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

## Source reference

`D:\Apps\Minecraft Port\Projects\references\SocialPlayerMobs-v0.86.0` (read-only host-mod oracle).

## Source evidence

- Host reference probes recorded in `plans/RFC-FURNACE-SMELTING.md` found no furnace/smelting behavior to reuse.
- Target evidence: `src/main/java/com/noobk/spmscavenger/FurnaceTransfers.java` fixes fuel access to `Direction.NORTH`.
- Locked design: `plans/RFC-FURNACE-SMELTING.md`, D-FSM-012 and task FS-7.

## Binding constraints

- Preserve D-FSM-008 atomic backpack/furnace rollback and D-FSM-009 face APIs.
- Probe only horizontal faces in a deterministic order; do not hardcode modded slot indexes.
- Select a face without mutation, then write through only that face.
- Do not launch Minecraft, commit, push, or edit the read-only source reference.

## Required files/API

- Replace the fixed NORTH fuel face in `FurnaceTransfers` with deterministic horizontal-face negotiation.
- Extend `FakeFurnaceContainer` so tests can expose fuel through selected horizontal faces.
- Add U-F10 to `FurnaceTransfersTest`: EAST-only acceptance succeeds atomically.
- Add a no-accepting-face regression proving backpack and furnace rollback.
- Apply the user-selected interim D-FSM-011 default: add `ironStockTarget=0` config/UI support and make `FurnacePolicy` use it; explicit positive values remain available for testing.

## Acceptance and verification

- **Must happen:** an EAST-only fake furnace receives input and fuel; explicit `ironStockTarget=6` still permits iron demand.
- **Must not happen:** rejected faces are mutated, failed negotiation loses/duplicates stacks, vanilla all-side behavior regresses, or default config starts a new iron batch without a consumer.
- Run focused tests, then `gradlew.bat clean build`.
- Record exact results in `.superpowers/sdd/task-7-report.md`; update RFC decisions/tasks/gates and directly relevant test/config documentation.
