# Task 12 Report — MI-24 marginal wealth curves + MI-25 opportunity bonus (policy)

**Status:** `DONE_WITH_CONCERNS`  
**Brief:** RFC MI-24/MI-25 — `ResourceWealthProfile` v1 + marginal utility + opportunity formula  
**Date:** 2026-08-08

## Summary

Extended `ResourceWealthPolicy` with D-MIW-026 locked gen-1 profiles, piecewise marginal
`wealthFactor`, `wealthValue`, and `opportunityBonus`. Policy-only — not yet wired into
`GatherResourcesGoal` (MI-4).

## Changes

| File | Change |
| --- | --- |
| `ResourceWealthPolicy.java` | Profiles, wealth curve, opportunity bonus, `WealthUtility` |
| `ResourceWealthPolicyTest.java` | U-MIW-20/21 parity and proximity tests |

## Verification

```text
.\gradlew.bat test — 138 tests BUILD SUCCESSFUL
```

## Concerns

- `greed` / `wealthLevel` not in `ScavengerConfig` yet — MI-4 wire pending.
- Acquisition-cost scale vs wealth magnitude needs calibration at gather integration.
- Runtime `UNVERIFIED`.

## Self-review

- [x] `greed=0` → `wealthValue=0`
- [x] Saturation floor 0.05 at profile saturation amount
- [x] Opportunity bonus higher for lower acquisition cost
- [ ] Gather loop does not call `evaluateWealth` yet (MI-4)
