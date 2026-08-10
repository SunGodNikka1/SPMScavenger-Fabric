# Task 22 brief: MI-7B+C — budget usage + natural descent exhaustion

## Target

`D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

## Source evidence

- RFC: `plans/RFC-MINING-INTELLIGENCE-AND-WEALTH-SYSTEM.md` — D-MIW-034, D-MIW-036, NaturalDescentStatus
- MI-7A: `src/main/java/com/noobk/spmscavenger/mining/MiningBudget.java`

## Binding

- Policy-only package under `com.noobk.spmscavenger.mining`
- Light wire: `NaturalDescentSearchState` on `ExploringGoal` during DESCENT expeditions
- No MI-7E executor, no MiningProject goal wiring
- Depends on MI-6F `hasActiveCaveCommitment`
- No Minecraft launch, commit, or push

## Requirements

### MI-7B
- `MiningBudgetUsage` — counters + increment helpers
- `MiningBudget` — `naturalDescentSearchDefaults()`, per-axis `isExhausted(usage)`, `isSearchBudgetConsumed(usage)`

### MI-7C
- `NaturalDescentStatus` enum (SEARCHING, AVAILABLE, TEMPORARILY_BLOCKED, EXHAUSTED)
- `NaturalDescentExhaustionPolicy.evaluate(...)` — pure; EXHAUSTED requires budget consumed + no route + no active CaveOpportunity + spatial coverage
- `NaturalDescentSearchState` — tracks usage, anchor, horizontal/vertical progress during descent search
- `ExploringGoal` — tick/progress/failure hooks; `naturalDescentStatus(ServerLevel, long)` when descent pressure active

## Must happen

- Standing still with path failures alone cannot yield EXHAUSTED
- Active cave commitment blocks EXHAUSTED

## Must not

- Start CONTROLLED_DESCENT (MI-7E)
- Change macro heading selection (MI-5H)

## Verification

`.\gradlew.bat test`

## Report

`.superpowers/sdd/archive/task-22-report.md`
