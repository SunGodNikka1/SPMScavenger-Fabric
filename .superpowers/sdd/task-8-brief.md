# Task 8 brief: TT-2b + FS-8 consumer-driven iron tools

## Target

`D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

## Source reference

`D:\Apps\Minecraft Port\Projects\references\SocialPlayerMobs-v0.86.0` at commit
`4b80b5e849ccabd69e7c9c2f44dc25f7233c7796` (read-only host-mod oracle).

## Source evidence

- SPM inventory is eight slots (`PlayerMobEntity.INVENTORY_SIZE = 8`) and its existing pickup
  policy already recognizes iron ingots, raw iron, and tools. This task extends production policy;
  it must not duplicate SPM acquisition or inventory systems.
- `ToolBox` already validates live harvesting with `ItemStack.isCorrectToolForDrops`; therefore
  D-TTU-012 is narrowed: `ToolTierPolicy` remains ownership/upgrade rank, while later TT-2c must
  keep live block/tool capability checks.
- Independent Agent_PeerReviewer accepted D-FSM-013 Option B: one typed work-demand selector with
  a pure material-deficit payload. Five targeted probes found no existing WorkDemand,
  MaterialDemand, ConsumerRecipeSpec, or iron craft steps.

## Binding decisions and scope

- Implement TT-2b and FS-8 together from `plans/RFC-TOOL-TIER-UPGRADES.md` and
  `plans/RFC-FURNACE-SMELTING.md`.
- One consumer-owned immutable recipe specification must own each iron tool's output, craft step,
  three iron ingots, two sticks, and replaced stone tool. Requirement emission and atomic craft
  application must consume that same specification.
- Pickaxe is the single frontier before axe. Demand is derived from backpack plus main hand and is
  never persisted. An already-inserted furnace ticket remains recoverable after demand disappears.
- Replace the interim `ironStockTarget` push/hoard policy; a positive legacy value must not bypass
  consumer demand.
- Include the minimum D-TTU-015 activation needed for reachability: allow IRON in config and UI.
  Do not add diamond/netherite, iron-ore gathering (TT-2c), reverse-recipe indexing, or a general
  autonomous planner.
- Preserve SPM compatibility and the existing eight-slot atomic snapshot contract.
- Do not launch Minecraft, commit, push, or edit the source reference.

## Alternatives considered

- **Selected:** shared consumer spec + typed demand envelope. It prevents recipe/demand drift and
  gives one arbitration point. Risk: modest new policy types; keep the slice bounded to smelting.
- **Rejected:** leave `ironStockTarget` active. Simpler, but creates ingot hoarding without a live
  consumer and a second demand truth.
- **Rejected:** full TT-2a abstraction now. It adds an unused layer; live harvest capability is
  already checked by SPM and belongs to later TT-2c.

## Acceptance and verification

- **Must happen:** IRON-configured mobs derive a 3→1→0 pick-ingot deficit, smelt only the selected
  frontier, atomically craft the iron pick/axe, and dispose of the corresponding stone tool from
  backpack or main hand after success.
- **Must not happen:** both tool frontiers emit simultaneously; legacy stock targets trigger
  producer-only smelting; failed/full-pack crafting loses ingredients; charcoal arbitration
  regresses; a de-latched inserted ticket becomes unrecoverable.
- Add focused unit tests first and observe the intended RED failure; then implement and rerun.
- Run `gradlew.bat clean build`, inspect XML test totals and final JAR contents, and record the
  SHA-256. Runtime behavior remains `UNVERIFIED` until a separately approved Minecraft launch.
- Record exact results in `.superpowers/sdd/task-8-report.md`; update both RFCs, progress,
  decisions, test matrix, README/config docs, and no unrelated files.
