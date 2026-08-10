# Task 8 report: TT-2b + FS-8 consumer-driven iron tools

## Outcome

`IMPLEMENTED` for code, unit tests, documentation, and packaging. Minecraft runtime behavior is
`UNVERIFIED` because no launch was authorized.

## Dependency and review resolution

- Agent_PeerReviewer independently accepted D-FSM-013 Option B and found five targeted absences:
  no prior `WorkDemand`, `MaterialDemand`, consumer recipe spec, or iron pick/axe craft steps.
- D-TTU-012 was narrowed: SPM already performs live block/tool validation through
  `ItemStack.isCorrectToolForDrops`; this task keeps `ToolTierPolicy` as ownership/upgrade rank.
  TT-2c must retain the live capability check.
- Minimum D-TTU-015 was included because IRON was otherwise rejected by config and the requested
  implementation would have been unreachable. Diamond/netherite remain out of scope.

## Implemented

- Added `WorkDemandPolicy`: one deterministic selector, typed `SMELT_BATCH` envelope, pure
  `MaterialDemand`, survival charcoal above progression iron.
- Added consumer-owned immutable iron pick/axe specs. Each owns output, step, 3 iron ingots,
  2 sticks, and the replaced stone tool. Both deficit calculation and craft mutation read them.
- Added pick-first single-frontier iron steps and fresh backpack+main-hand demand derivation.
- Extended the atomic craft transaction to dispose a replaced stone tool from backpack or main
  hand only after successful commit.
- Removed `ironStockTarget`; old Gson JSON keys are ignored and cannot trigger producer-only work.
- Added IRON to the config/UI cycle; DIAMOND/null clamp to IRON. Default caps remain STONE.
- Preserved INSERTED-ticket-first recovery in `SmeltAtFurnaceGoal`; fresh demand is evaluated only
  when there is no open inserted ticket.

## Verification

- TDD RED: focused compile failed on the intentionally absent consumer-spec, iron-step,
  `WorkDemandPolicy`, and main-hand-aware demand APIs.
- Focused GREEN: crafting, furnace, and config-tier tests passed.
- Final command: `.\gradlew.bat clean build` → `BUILD SUCCESSFUL`.
- JUnit XML: 107 tests, 0 failures, 0 errors, 0 skipped.
- Artifact: `build/libs/spmscavenger-1.9.2.jar`, 177,609 bytes.
- SHA-256: `EE44D1A86BF5B0C2A5EAED4C7CF198E8D18F1D5F331C33651683C1E10C0F90E6`.
- JAR audit: `fabric.mod.json` present, `WorkDemandPolicy.class` present, zero temporary
  `test-datapacks` entries.
- Negative probes after implementation: no `ironStockTarget` or `needsIronIngot` in `src/main` or
  `src/test`; no stale D-FSM-013 PROPOSED, FS-8 BLOCKED, TT-2b PLANNING, or D-TTU-015 PROPOSED state.

## Acceptance results

- **Must happen — unit/build CONFIRMED:** 3→1→0 iron deficit, pick-before-axe, looted/equipped iron
  pick moves the frontier, charcoal wins arbitration, shared 3+2 recipe application, backpack and
  main-hand stone disposal, no-fuel plan refusal, and IRON config reachability.
- **Must not happen — unit/static CONFIRMED:** producer-only raw-iron demand, iron demand before the
  stone prerequisite, simultaneous tool frontiers, duplicate recipe quantities, or full-pack
  ingredient loss.
- **Inserted ticket recovery — CODE_CONFIRMED:** `SmeltAtFurnaceGoal.canUse()` searches INSERTED
  tickets before invoking the fresh plan. Live reload/interruption behavior remains `UNVERIFIED`.

## Remaining scope

- TT-2c autonomous iron-ore discovery/mining and dedicated TT-2e tool-tier runtime kit remain.
- RT-F1–RT-F5, save/reload, multiplayer contention, visual behavior, and 1/10/50/100-mob performance
  require explicit Minecraft runtime approval. No runtime or performance claim is made here.
- No commit or push was performed; the pinned source reference was not modified.
