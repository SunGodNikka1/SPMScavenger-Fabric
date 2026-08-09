# Task 21 brief: MI-6F — CaveOpportunity live wiring

## Target

`D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

## Source evidence

- RFC: `plans/RFC-MINING-INTELLIGENCE-AND-WEALTH-SYSTEM.md` — MI-6F, CaveContext vs CaveOpportunity
- Policy: `src/main/java/com/noobk/spmscavenger/CaveOpportunityPolicy.java`
- Consumer: `src/main/java/com/noobk/spmscavenger/goal/ExploringGoal.java` — `landingCandidates`

## Binding

- Wire `CaveOpportunityPolicy` to explore landing branch arbitration only (gather unchanged)
- Prerequisite for MI-7C exhaustion (`active CaveOpportunity` gate)
- No Minecraft launch, commit, or push

## Requirements

1. `CaveOpportunitySelection` — pure helper: invert landing preference key to policy score; reorder candidate list to try committed landing first
2. `ExpeditionState.caveCommitment` — short-lived `CaveOpportunityPolicy.CaveOpportunity`
3. `ExploringGoal.landingCandidates` — when cave/ravine-like or descent intent and ≥2 candidates, arbitrate and reorder
4. `ExploringGoal.hasActiveCaveCommitment(long now)` — for MI-7C input
5. Unit tests for selection helper (preference inversion + reorder)

## Must happen

- Committed branch stays first choice across marginal re-ranks (delegates to policy)
- Invalidated commitment releases immediately

## Must not

- Change gather `caveOpportunity` boolean scoring (MI-6C)
- MI-7B/C or MI-5H heading changes

## Verification

`.\gradlew.bat test`

## Report

`.superpowers/sdd/task-21-report.md`
