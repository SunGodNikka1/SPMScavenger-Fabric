# Task 11 Report — MI-13a perception legitimacy

**Status:** `DONE_WITH_CONCERNS`  
**Brief:** RFC MI-13a — exposure in pass-one `isCandidate` for ore; scan diagnostics  
**Date:** 2026-08-08

## Summary

Moved air-exposure into pass-one gather candidate selection so buried ore cannot fill the
24-slot nearest buffer and hide legitimately exposed veins.

## Changes

| File | Change |
| --- | --- |
| `GatherProtection.java` | Public `isExposedToAir` |
| `GatherCandidatePolicy.java` | New pass-one rules + `ScanFailureReason` |
| `GatherResourcesGoal.java` | Delegate to policy; diagnostics on failed scan |
| `GatherCandidatePolicyTest.java` | U-MIW-19 buffer starvation scenario |

## Verification

```text
cd Projects/SPMScavenger-1.21.1-Fabric
.\gradlew.bat test
BUILD SUCCESSFUL — 138 tests
```

## Concerns

- Runtime gather behaviour `UNVERIFIED` (no launch approval).
- Stone/cobble still uses exposure only in pass two (ore-only scope per RFC).

## Self-review

- [x] Ore pass-one requires `isExposedToAir`
- [x] Buried ore excluded from candidate buffer (unit test)
- [x] `NO_CANDIDATES_IN_RADIUS` / `CANDIDATES_ALL_REJECTED_PROTECTION` distinguished
- [ ] Full `DiscoveryMode` enum (MI-13) still downstream
