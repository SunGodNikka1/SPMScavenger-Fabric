# Task 22 report: MI-7B+C — budget usage + natural descent exhaustion

**Status:** `DONE_WITH_CONCERNS`

## Verification

| Command | CWD | Result |
| --- | --- | --- |
| `.\gradlew.bat test` | `SPMScavenger-1.21.1-Fabric` | **213 tests, 0 failures** (`CONFIRMED`) |

## Delivered

| File | Change |
| --- | --- |
| `MiningBudgetUsage.java` | Usage counters + increment helpers |
| `MiningBudget.java` | `naturalDescentSearchDefaults()`, axis exhaustion, spatial coverage constants |
| `NaturalDescentStatus.java` | Four-state enum (D-MIW-034) |
| `NaturalDescentExhaustionPolicy.java` | Pure `evaluate` + `mayStartControlledDescent` |
| `NaturalDescentSearchState.java` | Anchor, usage, tick/failure/progress recording |
| `ExploringGoal.java` | DESCENT search tracking; `naturalDescentStatus(level, now)` |
| `NaturalDescentExhaustionPolicyTest.java` | 7 tests incl. failure-only ≠ EXHAUSTED |
| `MiningBudgetTest.java` | 3 tests |

## Self-review vs brief

| Requirement | Status |
| --- | --- |
| MI-7B usage + exhaustion predicates | `CONFIRMED` |
| MI-7C status machine | `CONFIRMED` |
| EXHAUSTED needs budget + coverage + no cave opp | `CONFIRMED` — `NaturalDescentExhaustionPolicyTest` |
| Active CaveOpportunity blocks EXHAUSTED | `CONFIRMED` — test + MI-6F wire |
| No MI-7E executor | `CONFIRMED` — grep no CONTROLLED_DESCENT start in goals |
| No MI-5H heading | `CONFIRMED` |

## Concerns (`UNVERIFIED`)

- `naturalDescentStatus` not yet consumed by MiningProject / MI-7E gate
- Reachable/blocked flags derive from last plan tick only — may lag one replan
- Runtime: mob beside cave mouth must not reach EXHAUSTED without coverage (falsifying probe pending)

## Evidence labels

- Unit policy gates: `CONFIRMED`
- In-world exhaustion transitions: `UNVERIFIED`
