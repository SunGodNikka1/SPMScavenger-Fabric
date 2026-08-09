# Task 15 report: MI-4S — D-MIW-028 Option A

## Status

`DONE_WITH_CONCERNS`

## Summary

Applied locked Option A: wealth admission uses `acquisitionUtility = desire × proximity`
with no raw `− acquisitionCost`. D-MIW-026 profile constants unchanged. Explicit
`isSaturated` gate replaces the old accidental saturated-scan kill that depended on the
broken formula.

## Files

| File | Change |
| --- | --- |
| `ResourceWealthPolicy.java` | `acquisitionUtility()` / Option A `netUtility`; `detourBudget`/`proximity`/`isSaturated` |
| `GatherIntentPolicy.java` | Admit via `acquisitionUtility`; wealth-only `hasDemand` skips saturated contexts |
| `ResourceWealthPolicyTest.java` | Option A nearby/far/no-raw-cost + saturation marker tests |
| `GatherIntentWealthTest.java` | Cost 3 admits; cost 35 rejects |

## Verification

| Command | CWD | Result |
| --- | --- | --- |
| `.\gradlew.bat test` | `SPMScavenger-1.21.1-Fabric` | `BUILD SUCCESSFUL` — **158** tests, 0 failures (`CONFIRMED`) |

## Evidence labels

| Claim | Label |
| --- | --- |
| Cost 3 iron greed 0.55 → utility > 0 | `CONFIRMED` — unit test |
| Cost 35 → utility 0 | `CONFIRMED` — unit test |
| Saturated stock does not start wealth-only scan | `CONFIRMED` — `isSaturated` + GatherIntentWealthTest |
| Runtime nearby-ore behaviour | `UNVERIFIED` — no Minecraft launch |

## Concerns

1. Tiny positive desire below saturation can still start wealth-only scans — intentional for greed play; F-6 may later budget that.
2. Saturated mobs still admit nearby wealth candidates if a NEED scan is already running (`wants` does not re-check saturation) — opportunity semantics; product may want to tighten later.
3. Runtime / performance still `UNVERIFIED`.

## Self-review vs brief

- Option A formula: done
- D-MIW-026 profiles untouched: done
- Acceptance table tests: done
- No MI-5/director/F-2/F-6: done
- Build/tests + report + progress + RFC: done
