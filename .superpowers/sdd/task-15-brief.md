# Task 15 brief: MI-4S — D-MIW-028 Option A scale repair

## Target

`D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

## Source reference / evidence

- RFC: `plans/RFC-MINING-INTELLIGENCE-AND-WEALTH-SYSTEM.md` — D-MIW-028 Option A `LOCKED`, D-MIW-029
- Policy under repair: `src/main/java/com/noobk/spmscavenger/ResourceWealthPolicy.java`
- Admission callers: `GatherIntentPolicy.wants(resource, cost)`, `hasDemand`
- Prior bug evidence: Claude F-1 table; `GatherIntentWealthTest.mustHappen_candidateDistanceChangesWealthDecision` asserted cost 3 false

## Binding constraints

- No Minecraft launch, commit, or push
- Keep D-MIW-026 profile constants unchanged
- Do not reintroduce `wealthRawIron` / stock targets
- Do not implement MI-5, director, F-2 band split, F-6 perception budget

## Requirements

1. Change admission utility to Option A:
   - `desire = wealthValue(inventory)` (no path cost)
   - `detourBudget = 8 + greed * 12`
   - `proximity = max(0, 1 - cost / detourBudget)`
   - `acquisitionUtility = desire * proximity`
   - Admit wealth when `acquisitionUtility > 0`
   - **Drop** raw `wealthValue + opportunityBonus - acquisitionCost`
2. Preserve greed=0 / wealthLevel=0 exact-consumer parity
3. Update unit tests to Option A acceptance table (nearby cost 3 yes; far cost 35 no)
4. Run `.\gradlew.bat test` (or `clean build` if needed); write `task-15-report.md`; append `progress.md`; update RFC

## Must happen

- Iron greed=0.55 wealthLevel=1 held=0 cost=3 → acquisitionUtility > 0
- Same context cost=35 → acquisitionUtility == 0
- Defaults greed/wealthLevel 0 → desire 0

## Must not happen

- Subtract raw acquisitionCost from sub-1 utilities
- Change D-MIW-026 profile numbers
- Wealth-only surface diamond intent / bypass MI-13a exposure
