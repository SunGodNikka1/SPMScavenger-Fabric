# Task 21 report: MI-6F — CaveOpportunity live wiring

**Status:** `DONE_WITH_CONCERNS`

## Verification

| Command | CWD | Result |
| --- | --- | --- |
| `.\gradlew.bat test` | `SPMScavenger-1.21.1-Fabric` | **213 tests, 0 failures** (`CONFIRMED`) |

## Delivered

| File | Change |
| --- | --- |
| `CaveOpportunitySelection.java` | Preference-key → policy score; `commitBestScored` + reorder |
| `ExploringGoal.java` | `ExpeditionState.caveCommitment`; landing arbitration when ≥2 cave/descent candidates; `hasActiveCaveCommitment(level, now)` |
| `CaveOpportunitySelectionTest.java` | 3 unit tests |

## Self-review vs brief

| Requirement | Status |
| --- | --- |
| Wire policy to explore landing branch choice | `CONFIRMED` — `landingCandidates` |
| Gather unchanged | `CONFIRMED` — no gather edits |
| Invalidated commitment releases | `CONFIRMED` — `heldStillValid` uses `safeStand` |
| Prerequisite for MI-7C | `CONFIRMED` — `hasActiveCaveCommitment` |

## Concerns (`UNVERIFIED`)

- Runtime branch commitment at cave junctions not observed in-game
- Resolved-target prepended landings may lack preference keys until next ring build (edge case)

## Evidence labels

- Compile/tests: `CONFIRMED` (gradle output)
- Observable anti-thrash behaviour: `UNVERIFIED` (no launch)
