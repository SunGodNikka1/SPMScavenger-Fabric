# Task 13 brief: MI-4R candidate-aware wealth repair

## Target

`D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

## Source reference

`D:\Apps\Minecraft Port\Projects\references\SocialPlayerMobs-v0.86.0` at
`4b80b5e849ccabd69e7c9c2f44dc25f7233c7796` (read-only host oracle).

## Source evidence and defect

`GatherIntentPolicy.wealthWants` evaluates every category at acquisition cost zero and adds it to a
global intent. This bypasses the Y≤16 diamond plausibility gate and turns the nonzero saturation
floor into permanent scans. Log stock is counted as `Items.OAK_LOG` only. Existing SPM inventory and
goal execution remain owned by the host/addon's current executor; do not add another scanner.

## Binding implementation

- Preserve separate NEED resources and immutable wealth contexts in one gather-intent snapshot.
- `shouldGather` may activate wealth scanning only when at least one category is positive at a
  conservative minimum discovery cost; high saturated stock must not activate a global scan.
- Pass-one candidate admission must evaluate wealth using normalized actual candidate distance.
- Diamond wealth context is absent above `DIAMOND_GENERATION_CEILING_Y`.
- Count logs through `ItemTags.LOGS`, with an injectable matcher only for isolated tag-empty tests.
- Preserve exposure, tool, build-protection, craft-first, and default-zero parity gates.
- Do not add stock-target config, persistence, another goal/scanner, Minecraft launch, commit, push,
  or source-reference edits.

## Alternatives

- **Selected:** candidate-aware utility in the existing two-pass scan. Correct opportunity ownership
  and bounded cost; a slightly richer immutable intent snapshot.
- **Rejected:** hard stop at saturation in global intent. Smaller, but discards the accepted
  nonzero-floor opportunity behavior and recreates a target threshold.

## Acceptance and verification

- Must: nearby legitimate resources can be wealth-positive; farther candidates can be rejected;
  birch/other tagged logs count; default-zero behavior remains unchanged.
- Must not: wealth-enabled surface diamond, saturated-stock permanent scans, buried/protected/tool-
  invalid bypass, or per-block inventory/profile recomputation.
- Add RED tests before implementation; run focused tests and `gradlew.bat clean build`.
- Record evidence in `.superpowers/sdd/task-13-report.md`, progress, RFC, decisions, and test matrix.
