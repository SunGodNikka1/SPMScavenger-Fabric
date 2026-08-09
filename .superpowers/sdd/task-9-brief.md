# Task 9 brief: MI-1 gather intent consolidation

## Target

`D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

## Source reference

`D:\Apps\Minecraft Port\Projects\references\SocialPlayerMobs-v0.86.0` at pinned host baseline
`4b80b5e849ccabd69e7c9c2f44dc25f7233c7796` (read-only).

## Source evidence

- SPM owns the eight-slot backpack and goal scheduling; this addon must reuse that container and
  its existing `GatherResourcesGoal`, not add a second scanner or inventory.
- Current target evidence is `GatherResourcesGoal.wantsMore/wantsIron/wantsDiamond/wantsCobble`,
  `WorkDemandPolicy.rawIronDeficit/diamondDeficit`, and `ScavengerCrafting.nextStep`.
- Three negative probes in the RFC found no `GatherIntentPolicy`, `ResourceWealthPolicy`, or
  `MiningDirector` in `src/main`; MI-1 adds only the first seam.

## Binding constraints

- Implement RFC task MI-1 only: one immutable, pure gather-intent snapshot derived from the live
  backpack, main hand, config, and Y position.
- Preserve existing consumer deficits and craft-first boundary. Do not add wealth values, stock
  targets, a second goal/scanner, discovery modes, target scoring, persistence, or configuration.
- The intent may contain multiple resource needs, but it is one evaluation object and one source of
  truth for `wantsMore`, candidate filtering, and retained drops.
- Evaluate once per target scan; do not recalculate inventory recipes for every scanned block.
- No Minecraft launch, commit, push, or source-reference edit.

## Alternatives

- **Selected:** one immutable aggregate intent with resource flags. This preserves simultaneous
  torch/tool prerequisites while removing scattered predicates.
- **Rejected:** one exclusive resource enum. It would silently serialize independent needs and
  change current nearest-resource behavior before MI-2 owns prioritization.
- **Rejected:** fold intent into `WorkDemandPolicy`. That policy arbitrates furnace work; mining
  acquisition is a separate responsibility and later receives wealth/discovery inputs.

## Acceptance and verification

- **Must happen:** torch shortage exposes log/coal intent; iron and deep-diamond consumer deficits
  expose their ore intents; all checks use one scan snapshot.
- **Must not happen:** surface diamond intent, gathering while a craft step is immediately ready,
  new stock targets, per-block full policy recomputation, or wealth behavior.
- Add focused tests first and record RED, then implement and run `gradlew.bat clean build`.
- Record exact results and runtime gaps in `.superpowers/sdd/task-9-report.md`; update the RFC and
  `.superpowers/sdd/progress.md`. Runtime remains `UNVERIFIED` without separate launch approval.
