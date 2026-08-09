# Task 26 brief: MI-7R — Controlled Descent Semantic Repair

## Target
`D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

## Requirements
- **R1** `StairStepSafety.validatePlan` — projected post-break geometry (not pre-break air at destination)
- **R2** `ToolBox.ownsToolFor` / `BreakCapability` — not main-hand-only at plan time
- **R3** `ControlledDescentCaveHandoff` — MI-6 subterranean + `CaveLandingResolver` continuation
- **R4** `HANDOFF_TUNNEL_SEARCH` when in diamond band + progression demand remains
- Unit tests for R1/R2 (`StairStepSafetyTest`), R3 (`ControlledDescentCaveHandoffTest`)
- Update task-25 MAIBS static re-check

## Verification
`.\gradlew.bat test`
