# Task 13 Report — MI-4R candidate-aware wealth repair

## Outcome

MI-4R is implemented and statically verified. Gather intent now retains consumer NEED separately
from immutable wealth contexts. NEED always admits a matching candidate; wealth admits it only when
its utility exceeds the candidate's normalized distance cost. Wealth alone starts a scan only when
at least one category clears a conservative discovery cost.

## Implementation

- `GatherIntentPolicy`: separated required resources from wealth contexts; added scan activation
  gating, candidate-cost evaluation, tag-aware log counting, coal/charcoal accounting, iron input
  accounting, and the established diamond-generation-height plausibility boundary.
- `GatherCandidatePolicy`: added candidate-cost-aware pass-one admission while retaining the old
  overload for consumer-only callers/tests.
- `GatherResourcesGoal`: computes normalized distance cost before adding a block to its bounded
  nearest-candidate buffer.
- `GatherIntentWealthTest`: added surface-diamond, saturated-scan, non-oak-log, and near/far wealth
  regressions.

## TDD evidence

- RED: focused test compilation failed on the deliberately missing candidate-aware `evaluate`,
  `countResource`, and `wants(resource, cost)` seams.
- GREEN: `gradlew.bat test --tests com.noobk.spmscavenger.GatherIntentWealthTest` passed.
- Full verification: `gradlew.bat clean build` passed; 148 tests, 0 failures, 0 errors, 0 skipped.

## Acceptance

- Must happen: nearby eligible raw iron passes wealth utility; non-oak logs count as log wealth.
- Must not happen: surface diamond wealth intent, saturated-stock global scans, or changed
  default-zero consumer behavior.

## Artifact

- `build/libs/spmscavenger-1.9.2.jar`
- SHA-256: `002CB160E8C64D5D6C127950484336740FCA89942578F37215610A0FB680B2AC`

## Remaining evidence gap

Runtime behavior and performance remain `UNVERIFIED`; no Minecraft runtime was launched because
repository policy requires separate explicit approval.
