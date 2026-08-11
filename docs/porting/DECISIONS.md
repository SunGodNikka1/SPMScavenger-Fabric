# SPM Scavenger decisions

## 2026-08-10 — structured recipe-only crafting readout · 1.9.2+

- **Problem:** SPM's class-name fallback renders `CraftTorchesGoal` as `Craft torches`, even when
  the same goal is making planks, sticks, a furnace, or a tool upgrade.
- **Selected:** a pure exhaustive `ScavengerCrafting.Step` presentation mapping plus an optional
  common-side `@Pseudo` Mixin at SPM's server-owned `ObjectiveReadout.describe(Goal)`. The goal
  exposes only its already-selected recipe label; observation does not reevaluate policy or mutate
  inventory, timers, navigation, flags, or scheduler state.
- **Recipe-only product decision:** while finding, placing, or walking to a crafting table, the
  readout continues to name the intended recipe. A physical-phase label was more literal but was
  rejected by the user because the requested contract is `Crafting — <current recipe>`.
- **Alternative — implement SPM's `DescribableGoal`:** simpler host integration, rejected because
  it adds a compile-time SPM dependency to an addon deliberately designed to load without SPM.
- **Alternative — patch/copy the host formatter or renderer:** rejected because it modifies or
  duplicates host ownership, can diverge between server-synced menu text and client billboard
  text, and increases redistribution/version-coupling risk.
- **Failure behavior:** `require=0` preserves SPM's `Craft torches` fallback if the private host
  formatter changes. This is compatibility degradation, not a startup failure.
- **Must happen:** every crafting enum value has an explicit stable label, including
  `Crafting — torches` and `Crafting — diamond pickaxe`.
- **Must not happen:** the readout bridge changes Goal flags/priorities, crafting decisions,
  inventory, navigation, or startup behavior when SPM is absent/incompatible.
- **Evidence:** exhaustive/purity and common-mixin contract tests plus the full 600-test clean build
  are `CONFIRMED`. Final-JAR inspection confirms the common bridge, `@Pseudo`, bare host-owned
  `describe` selector, remapped `Goal` callback type, and `require=0`. Runtime visual behavior is
  `UNVERIFIED`; see `TEST_MATRIX.md`.

## 2026-08-10 — optional decision-readout contrast compatibility · 1.9.2+

- **Problem:** SPM's renderer passes the PlayerMob's world light into otherwise-white objective
  glyphs. In caves the Creative decision label therefore appears dark and fails as an AI diagnostic.
- **Selected (corrected after runtime failure):** a Scavenger-owned, client-only `@Pseudo` Mixin
  adjusts only glyph lighting and the solid secondary colour in SPM 0.86.x's two
  `Font.drawInBatch` calls. The translucent pass and background are returned byte-for-byte to SPM
  and Minecraft's user setting.
- **Alternative:** modify/fork SPM's renderer directly. Rejected because Scavenger can repair this
  narrow presentation seam without redistributing or maintaining host source.
- **Alternative:** draw a second Scavenger label. Rejected because duplicate billboards can disagree
  with SPM's authoritative objective text and overlap nameplates.
- **Rejected repair:** forcing a minimum 50% black backdrop. The installed artifact loaded without
  a Mixin error, but the user observed the decision render as completely black under the active
  Iris/Sodium stack. Code/bytecode confirms the override raised SPM's 25% base plate to 50%; exact
  shader causality remains `INFERRED` until the rebuilt visual is retested.
- **Compatibility trade-off:** `require=0` prevents a future SPM renderer change from crashing the
  client, but can turn the repair into a silent no-op. The exact supported host is SPM 0.86.x; a
  runtime screenshot remains the verification probe after upgrades.
- **Must happen:** decision glyphs remain readable in an unlit cave after installing the rebuilt JAR.
- **Must not happen:** the host JAR/source, objective contents, Creative/range/focus gates, AI,
  navigation, dedicated-server classloading, or user-controlled background alpha changes.
- **Evidence:** focused contrast tests cover full-bright glyphs, lighter solid secondary text, and
  exact host background/translucent-pass preservation; remapped JAR
  inspection confirms the Mixin, policy, config, and intermediary `Font.drawInBatch` target are
  packaged. The full 600-test clean build passes. Minecraft visual behavior remains `UNVERIFIED`.

## 2026-08-08 — consumer-driven iron tools (TT-2b + FS-8) · 1.9.2+

Canonical RFCs: `plans/RFC-TOOL-TIER-UPGRADES.md` and `plans/RFC-FURNACE-SMELTING.md`.

- **Selected:** consumer-owned immutable iron-pick/axe recipe specifications are shared by craft
  application and material-deficit derivation. `WorkDemandPolicy` is the only arbitrator;
  `MaterialDemand` is a pure typed payload. Pick is the sole frontier before axe.
- **Selected dependency resolution:** D-TTU-012 is narrowed because SPM already checks live
  block/tool correctness. `ToolTierPolicy` is ownership/upgrade rank; later TT-2c must retain
  `ItemStack.isCorrectToolForDrops`. Minimal D-TTU-015 adds IRON to config/UI for reachability.
- **Superseded:** `ironStockTarget`. It was an interim producer-test knob and is removed now that a
  real consumer exists; an old JSON field is ignored and cannot create ingot hoarding.
- **Rejected:** a second material selector, duplicate 3+2 requirement tables, and a reverse-recipe
  scan. These add conflicting truth or unbounded work without helping this vanilla iron slice.
- **Must happen:** an IRON-capped stone-tool owner derives the remaining ingot deficit, smelts, and
  atomically crafts pick before axe while dropping the replaced tool.
- **Must not happen:** raw iron alone triggers smelting, both frontiers emit, failed craft loses
  ingredients, or an already-inserted ticket is abandoned when demand later disappears.
- Unit/build behavior is `CONFIRMED` by 107 passing tests and a clean build. Minecraft runtime,
  multiplayer, scale, and visuals remain `UNVERIFIED` pending separate launch approval.

## 2026-08-08 — furnace compatibility and interim iron demand · 1.9.2+

Canonical RFC: `plans/RFC-FURNACE-SMELTING.md` (D-FSM-011/012; FS-6/FS-7/FS-9).

- **Selected:** preflight horizontal furnace faces in deterministic NORTH/SOUTH/WEST/EAST order,
  choose one face that accepts the complete fuel stack, then mutate only that face. This preserves
  atomic rollback and supports side-asymmetric `WorldlyContainer` implementations.
- **Rejected:** fixed NORTH (silently incompatible) and mutating face-by-face retries (can split a
  transfer across different side contracts).
- **Superseded by TT-2b/FS-8:** `ironStockTarget` existed only while no consumer was implemented.
  Consumer-derived demand now owns new iron batches. Existing inserted tickets still resume
  independently of new demand.
- **Must happen:** EAST-only fuel access succeeds; default config emits no new iron demand.
- **Must not happen:** rejected faces receive partial fuel, failed negotiation loses/duplicates
  inventory, or the charcoal path is disabled.
- Runtime behavior remains `UNVERIFIED`; unit/static and build evidence are recorded in the RFC and
  `docs/porting/TEST_MATRIX.md`.

## 2026-08-08 — tool tier upgrades Phase 1 (stone) · 1.9.1+

Canonical RFC: `plans/RFC-TOOL-TIER-UPGRADES.md` (MRFC-1).

### Scope

- **Accepted:** wood → cobble gather → stone pick/axe → existing coal/torch chain.
- **Deferred:** iron tools and furnace/charcoal (separate RFC). Deepslate gather deferred.

### Locked product rules (D-TTU-001–011)

| ID | Decision |
| --- | --- |
| D-TTU-001 | Phase 1 stone only |
| D-TTU-002 | Drop wooden tool at feet when its stone equivalent is successfully crafted |
| D-TTU-003 | Pick upgrade before axe |
| D-TTU-004 | Never gather infested stone |
| D-TTU-005 | Direct-keep cobble in `GatherResourcesGoal` (coal precedent; not SPM floor pickup) |
| D-TTU-006 | Keep `craftTools` master switch; add `ToolTier maxPickTier` / `maxAxeTier` (no rename) |
| D-TTU-007 | `cobbleStockTarget=6` (one pick + one axe, no hoard buffer) |
| D-TTU-008 | Atomic snapshot craft transaction for all `apply()` paths (TT-0R) |
| D-TTU-009 | Phase 1: golden pick/axe rank as `WOOD` (not IRON) |
| D-TTU-010 | Phase-1 historical cap; expanded by D-TTU-015 to NONE/WOOD/STONE/IRON; DIAMOND/null → IRON |
| D-TTU-011 | Cobble demand evaluates main hand + backpack |

### Architecture notes

- Policy-first: `ToolTier` / `ToolTierPolicy` / `ScavengerCrafting`; goals stay thin.
- Crafts mutate a trial inventory then commit (no ingredient loss; valid full packs succeed).
- Cloth uses `startSelector` over `CRAFTABLE_TIER_CAPS` (cycle button), not a full `ToolTier` enum
  selector and not `startDropdownMenu`. Dropdown expand/collapse is focus-coupled in Cloth 15.0.140
  and can stick open until the config screen is recreated.
- `normalizeCraftTargets()` is the load seam; do not silently clamp inside `targetPickTier`.

### Verification

- **Must happen (unit):** U-0A–C, U-1–U-5, U-6–U-7, U-9A/B, U-10A/B, U-11A/B as applicable.
- **Must not happen:** silent full-pack ingredient loss; gold=`IRON`; unreachable IRON craft pressure; cobble demand ignoring an equipped stone tool; house grief under protection.
- **`CONFIRMED` unit:** focused tests under `ScavengerCraftingTest`, `ToolTierPolicyTest`,
  `GatherProtectionTest`, `ScavengerConfigTierTest` (Agent_Cursor 2 sessions 2026-08-08).
- **`UNVERIFIED` runtime / packaging:** Minecraft launch and a fresh release JAR for this combined
  tree were not authorized in the TT-1c docs pass. Follow TT-1–TT-6 in `TEST_MATRIX.md` after
  approval. Do not treat compile success as parity.

## 2026-08-08 — generic purposeful exploration (1.8.0)

### Evidence and selected design

- **`CONFIRMED` source:** SPM v0.86.0 registers only vanilla
  `WaterAvoidingRandomStrollGoal(this, 0.6)` at priority 8 for general idle travel. No wandering
  configuration key, general-purpose explore goal, or village/POI/structure exploration goal was
  found after three targeted probes.
- **`CONFIRMED` mapped API:** this target uses Loom official Mojang mappings for 1.21.1.
  `ServerLevel.isPositionEntityTicking(BlockPos)` exists and calls both
  `PersistentEntitySectionManager.canPositionTick` and
  `DistanceManager.inEntityTickingRange`. `Path#getNodeCount`, `getNode`, `getNextNodePos` and
  `canReach` are present.
- **`UNVERIFIED` behavioural premise:** speeds, distances, idle thresholds and history sizes are
  first-generation tuning values. Code/build evidence cannot prove that they look human in play.

| Option | Benefit | Risk/cost | Decision |
| --- | --- | --- | --- |
| Replace the vanilla stroll only with the same goal at a higher speed | Smallest compatibility surface | Faster but still aimless | Retained as local fallback, not the feature |
| Add a generic low-priority expedition goal and keep local wandering below it | Meaningful displacement while SPM retains ownership of work | Requires route/recovery state and bounded path probes | **Selected by user** |
| Add POI/resource/structure-aware exploration or a planner | More explicit objectives | Duplicates SPM discovery/work systems and adds scans/coupling | Rejected for generation one |

### Hard state and recovery boundaries

- `ExpeditionState` owns the latched heading, immutable intended waypoint centres, current index,
  per-waypoint/expedition failures, timestamps, resolved landing and coarse histories. It contains
  no `Path`.
- `NavigationState` owns only the current `Path`, resolved target, current distance and last-progress
  tick. Every `ExploringGoal.stop()` discards it while preserving the expedition.
- Resumption recalculates a new path to the same waypoint. It never regenerates the remaining route.
  A mob displaced forward can skip obsolete stages; a mob displaced sideways receives one temporary
  rejoin waypoint on the original heading.
- Intended X/Z centres are generated once. A small local landing search resolves safe Y/standing
  positions lazily. Three failed planning cycles skip one non-final waypoint; six expedition
  failures or a failed final waypoint abandon. No stage can re-path forever.
- `PATH_FAILURE` and `SIMULATION_FRONTIER` are separate outcomes. The latter ends cleanly, applies a
  shorter cooldown, and records no visited destination or completed heading.
- Reached stage regions enter weak `recentVisitedRegions`; only a successful final endpoint enters
  strong `recentExpeditionDestinations`. Traversed regions are not marked merely for being crossed.
- Exploration activates after either two naturally completed, meaningfully displaced local strolls
  or 600 observed no-work ticks. Unknown running goals count as work and reset both signals; look and
  local idle goals do not. A stop/interruption is never counted as a completed stroll.

### Simulation and performance boundary

- Route centres require a 3×3 entity-ticking chunk guard. At path creation, path nodes are reduced
  to unique `ChunkPos` values before checking each guard ring; the loop does not multiply every node
  by nine duplicate checks.
- Travel checks only current position, next path node and a forward guard chunk. Full corridor
  validation occurs only on planning/replanning.
- No chunk ticket, `hasChunk` proxy, resource scan, structure lookup or POI query is used. Candidate
  standing resolution is bounded to a four-block local radius and 20 path probes per planning pass.
  The activity observer is staggered by entity ID every ten ticks.
- An abrupt player teleport can remove the mob's current chunk from simulation before any AI gets
  another tick; no goal can recover while it is not ticking. The one-chunk guard is risk reduction,
  not an absolute guarantee against that server-level event.

### Acceptance and verification

- **Must happen:** eligible idle mobs retain one heading across 2–4 forward-biased stages, discard
  only their path on interruption, calculate a new path to the same remaining waypoint, and let all
  higher-priority SPM work preempt them.
- **Must not happen:** independent per-waypoint headings, route regeneration after interruption,
  old `Path` reuse, unbounded re-pathing, chunk forcing, entry into a planned non-entity-ticking
  corridor, destination penalties for merely crossed regions, or direction penalties caused by a
  simulation frontier.
- **`CONFIRMED` unit/static:** `ExplorationPolicyTest` covers forward projection, forward skip,
  sideways rejoin, local-trip semantics, coarse negative-coordinate regions, heading sectors,
  frontier/path distinction and bounded waypoint failure actions. `ExplorationReadinessTest` covers
  both activation triggers, work reset and cooldown.
- **`CONFIRMED` build/package:** `gradlew clean build --stacktrace` passed on 2026-08-08 with 26
  tests, zero failures/errors/skips. Final artifact:
  `build/libs/spmscavenger-1.8.0.jar`, SHA-256
  `6C7F21209F32EDA8921A1B4B950CC4EAA80B1B75D5E0FD47E68534A0B1BC7DDE`. Final-JAR inspection
  confirmed metadata version `1.8.0`, the exploration classes, one `fabric.mod.json`, and no test
  classes.
- **`UNVERIFIED` runtime/performance:** Minecraft was not launched. Required scenarios are recorded
  in `README.md` and `docs/porting/TEST_MATRIX.md`; multi-mob MSPT and path-probe measurements remain
  required before any performance claim.

## 2026-08-08 — entity-ID phased environmental scans (1.7.5)

### Evidence and decision

- **`CONFIRMED` measured historical hotspot:** the v1.6.0 Spark capture attributed 32.10% of a
  sampled server tick to `GatherResourcesGoal.tick()` before protection work was removed from the
  walking tick. That measurement does not quantify 1.7.4 or this change.
- **`CONFIRMED` current scan shape:** gathering can visit 41×41×9 = 15,129 positions per eligible
  scan and perform up to three native path probes. Shelter, campfire, torch and table goals also
  contain bounded block-volume searches. Their per-goal cooldowns did not establish a deliberate
  entity-specific initial phase.
- **`CONFIRMED` duplicate work:** `CraftTorchesGoal#atTable` called `findTable`; when it returned
  null, `placeTable` immediately called the identical full-radius `findTable` again in the same call
  stack.

| Option | Benefit | Risk/cost | Decision |
| --- | --- | --- | --- |
| Initial integer cooldown derived from entity ID | Very small patch | GoalSelector may skip exact slots; cooldown advances by polling frequency rather than game ticks; phases can drift or be missed | Rejected |
| Absolute game-tick phase clock with late-poll tolerance | Stable entity/goal phases, works with irregular `canUse` polling, deterministic pure tests | Eligible work can wait up to one scan interval | **Selected by user** |
| Server-wide token scheduler | Hard aggregate budget | Larger architecture and queue/fairness decisions; beyond the selected change | Deferred candidate |

### Implementation and limits

- `PhasedScanClock` maps sequential entity IDs across each interval and uses different salts for
  gather, shelter, campfire, torch and crafting-table scans, reducing cross-goal alignment.
- The first poll at or after a phase claims exactly one turn and schedules the next phase. Goals
  that stop reset to the next assigned phase instead of rescanning immediately.
- Master feature checks remain cheap. Shelter and campfire clocks advance before time/combat
  eligibility so many mobs becoming eligible together do not inherit stale synchronized turns.
- Torch placement's existing gameplay cooldown remains independent and decrements before the scan
  clock; it is not multiplied by the new scan interval.
- Table crafting records that its phased search completed. The small placement/crafting state can
  progress every tick without repeating the large search. After crafting a table, the next phased
  search preserves the existing preference to reuse a bench another mob placed meanwhile.

### Acceptance and verification

- **Must happen:** sequential entity IDs occupy distinct phases across one full interval; different
  goal salts separate scan types for the same mob; a poll after its exact phase still claims once;
  reset waits for the next assigned phase; all environmental behaviors retain a path to execution.
- **Must not happen:** a missed GoalSelector poll suppresses scanning forever; one tick claims the
  same clock twice; torch gameplay cooldown is stretched by scan gating; crafting performs two
  identical table scans in one call stack; combat/safety navigation is scheduled by this clock.
- **`CONFIRMED` unit/static:** `PhasedScanClockTest` covers phase distribution, goal separation,
  missed polling ticks, reset behavior and invalid intervals. All 16 project tests passed under
  `gradlew clean build --stacktrace` on 2026-08-08.
- **`CONFIRMED` compile/package:** the clean build completed successfully. Final artifact:
  `build/libs/spmscavenger-1.7.5.jar`, SHA-256
  `CBCC413AB153A6A76FDE24DBD1EAB7CA84AA34FE5EDD0A2FC2DABEFFF8E1190E`. Final-JAR inspection
  confirmed metadata version `1.7.5`, the phased clock class, one `fabric.mod.json`, and no packaged
  test classes.
- **`UNVERIFIED` performance/runtime:** no Minecraft launch or post-change Spark capture was run.
  Required comparison: matching world/settings at 1, 10, 50 and 100 PlayerMobs, reporting median,
  p95 and p99 MSPT plus scan/path frames. A build pass does not prove saved TPS.

## 2026-08-08 — path-aware trunk acquisition and bounded leaf recovery (1.7.4)

### Failure evidence

- **`INFERRED` runtime cause:** the user observed a PlayerMob take one log, remain on `Gathering
  Resources`, then stare at a tree. They proposed that it was targeting an inner log hidden behind
  leaves. There is no instrumented path trace yet, so that exact target in the reported world is
  not proven.
- **`CONFIRMED` static defect:** `GatherResourcesGoal#findBlock` treated every log block as an
  independent candidate, ordered candidates only by Euclidean distance, performed no path or
  standing-position test, ignored the boolean result of `PathNavigation#moveTo`, and backed off only
  one exact log coordinate. A failed tree could therefore offer another interior log immediately.
- **`CONFIRMED` negative evidence:** searches found no path construction/reachability call in the
  goal, no reachability regression test in this project, and no path construction in the installed
  1.7.3 goal bytecode. `latest.log` contained no relevant Scavenger exception.

### Options considered

| Option | Benefit | Risk/cost | Decision |
| --- | --- | --- | --- |
| Strictly skip any tree without a complete path to a clear standing cell | Lowest world-damage risk and simplest state | Dense but natural foliage can make valid trees permanently unusable | Rejected as incomplete for the reported case |
| Base-only targets + bounded native path probes + tightly constrained leaf recovery after measured stall | Handles dense natural foliage, avoids interior-log cycling, and keeps work/destruction bounded | May remove decorative leaves when build protection is disabled; adds a small pathfinding cost | **Selected by user** |
| General-purpose digging/path-clearing goal | Handles many obstructions | High griefing and performance risk; duplicates broader navigation behaviour | Rejected |

**Borrowed:** the one-soft-block-per-recovery-cycle concept was observed in Social Player Mobs'
train-only `TrainRecoveryGoal`. It fits because both cases recover a PlayerMob stopped by a local,
soft obstruction. The train implementation itself was not copied: it is train-specific and under
PolyForm Shield. The risk is treating decorative foliage as disposable, so this implementation is
leaf-only, tree-only, direct-cell-only, build-protection-aware, delayed by 20 no-progress ticks,
and capped at three leaves.

### Implemented boundary

- Initial tree targets must be logs with no log directly below; approved vertical continuation
  remains owned by the existing felling session and retains the exact 12-log cap.
- Up to 24 cheap/protected candidates may be shortlisted, but only three receive a native path
  probe in one scan. A reachable path wins; one partial path can serve as the recovery fallback.
- Candidate destinations require a sturdy floor and two passable cells. Leaves may occupy those
  cells only for a tree when leaf recovery is enabled; non-leaf collision and all ore recovery are
  rejected.
- A direct leaf is eligible only after 20 ticks without at least 0.25 squared-block improvement,
  within five blocks of the approved base, below the three-leaf limit, out of combat, with
  `mobGriefing=true`, and with no nearby built block when protection is enabled.
- Timeout backs off the base/ore key for 200 ticks. Adjacent bottom logs (for example a 2×2 jungle
  base) are canonicalised to one key with a bounded one-block scan. Each mob retains at most eight
  keys.

### Acceptance and verification

- **Must happen:** select a trunk base, prefer a genuinely reachable standing position, retain a
  partial path only as recovery fallback, remove at most one direct leaf per stall interval, and
  re-path after removal.
- **Must not happen:** acquire an interior log, clear for coal, clear before the stall threshold,
  clear a non-leaf or more than three leaves, clear near a protected build, clear during combat, or
  clear when the setting/`mobGriefing` is off. Pathfinding must not exceed three candidate probes
  per scan.
- **`CONFIRMED` unit/static:** `GatherApproachPolicyTest` covers base-only selection, meaningful
  progress, recovery destinations, stall threshold, exact cap, ore/non-leaf/build/config/game-rule
  rejection. `FellingPolicyTest` retains the atomic-session and 12-log assertions. All 11 tests
  passed under `gradlew clean build --stacktrace` on 2026-08-08.
- **`CONFIRMED` compile/package:** the same clean build completed successfully. Final artifact:
  `build/libs/spmscavenger-1.7.4.jar`, SHA-256
  `F78F8E8698195D182A4604ADB05264F9F285A28F1E250E82139A1AFFF6DE1916`. Final-JAR inspection
  confirmed metadata version `1.7.4`, the gather goal and approach policy, one `fabric.mod.json`,
  and no packaged policy-test class.
- **`CONFIRMED` runtime for the reported reproduction:** immediately after the 1.7.4 handoff, the
  user reported “Worked.” This confirms the repaired stuck-tree scenario in their live instance.
  The exact world layout and an instrumented path trace were not captured.
- **`UNVERIFIED` runtime edges:** the complete negative matrix (ore, protected build, disabled
  setting, `mobGriefing=false`, combat, exact three-leaf cap, multi-column backoff) and multi-mob
  performance still require deliberate live scenarios. Do not infer those results from the single
  successful reproduction.

## 2026-08-08 — atomic approved-tree sessions (1.7.3)

### Failure evidence

- **`INFERRED` runtime symptom:** the user reported a PlayerMob removing one log, retaining the
  `Gather resources` objective, and staring at the remaining trunk. No instrumented runtime replay
  has yet been captured for 1.7.2 or the repair.
- **`CONFIRMED` environment:** `D:\Minecraft\Instances\Fabulously Optimized\logs\latest.log`
  loaded `spmscavenger 1.7.2` and logged gathering active at line 1242.
- **`CONFIRMED` static cause:** after the first harvest, `GatherResourcesGoal#continueFelling`
  called `wantsMore`; one carried log makes `ScavengerCrafting#nextStep` return
  `LOGS_TO_PLANKS`, and `wantsMore` returns false whenever a crafting step exists. On the next tick,
  vanilla `GoalSelector` evaluates `canContinueToUse` before `tick`, cancelling the new upper-log
  target. The remaining trunk cannot be selected again because its lowest log is now above air and
  `GatherProtection` correctly requires growing ground.
- **`CONFIRMED` negative evidence:** three probes found no Scavenger exception: mod/symptom-token
  search in `latest.log`, generic error/exception search around the reproduction, and the newest
  crash-report search for Scavenger frames. Classification: runtime-logic/state-machine failure.

### Options considered

| Option | Benefit | Risk/cost | Decision |
| --- | --- | --- | --- |
| Finish the already-approved trunk before a soft crafting handoff | Small state-machine change; matches the documented whole-trunk behaviour; no new scan, inventory, or SPM subsystem | Crafting begins a few seconds later | **Selected** |
| Stop for crafting and persist enough tree identity to resume afterward | Crafting can start immediately | Stale targets across combat/chunk unload, additional persistence/recovery logic, and more coupling | Rejected for this repair |

The selected rule distinguishes **hard interruptions** from a **soft acquisition boundary**.
Combat, disabled gathering, `mobGriefing=false`, approach timeout, or a missing target stop the goal.
Making a crafting step available prevents starting another tree but does not cancel the approved
one already in progress.

The former remaining-counter also allowed 13 total logs despite a documented cap of 12. Version
1.7.3 counts harvested logs directly and refuses the next transition once the count reaches 12.
Coal/non-log harvesting cannot open a tree session.

### Verification

- **Must happen:** with hard conditions valid, an approved tree continues when acquisition is no
  longer needed because log 1 made a crafting step available.
- **Must not happen:** a hard interruption, non-log harvested block, ended trunk, or 12-log boundary
  permits another felling transition.
- **`CONFIRMED` unit policy:** `gradlew test` passed all five `FellingPolicyTest` cases on
  2026-08-08.
- **`CONFIRMED` compile/package:** `gradlew clean build --stacktrace` completed successfully on
  2026-08-08. Final artifact: `build/libs/spmscavenger-1.7.3.jar`, SHA-256
  `01FB1C388B26B99AB50F4B3338E8BD8838790F78155FEAF72FED799CDF5D6108`.
  Final-JAR inspection confirmed metadata version `1.7.3`, both repaired classes, one
  `fabric.mod.json`, and no JUnit/test or source-reference classes.
- **`UNVERIFIED` runtime:** Minecraft was not launched because repository policy requires separate
  user approval. Confirm using a natural 4+ log trunk and a 13+ log test column, with
  `mobGriefing=true`, then repeat the hard-interruption cases.
## 2026-08-08 — hide the exploration readiness observer (1.8.1)

- **Observed failure:** SPM 0.86.0 displayed `Exploration activity` beside real goals because
  `ExplorationActivityGoal` is intentionally flagless and always running.
- **Root cause (`CONFIRMED`):** SPM's `ObjectiveReadout` includes every running goal except
  `LookAtPlayerGoal` and `RandomLookAroundGoal`, then humanises unknown class names. The observer
  was a plain `Goal`, so its class name became user-facing text.
- **Selected repair:** retain the observer and subclass SPM's already-filtered vanilla
  `RandomLookAroundGoal` contract, while clearing all flags and never invoking look behaviour.
  This is a presentation-only compatibility adaptation; readiness and navigation are unchanged.
- **Alternative considered:** move bookkeeping to a global server-tick registry. Rejected for this
  bugfix because it adds entity load/unload ownership, cleanup and cross-world lifecycle state.
- **Risk:** a future SPM version could change its cosmetic filter. The pinned regression test catches
  loss of the required class relationship; an upstream update must also re-check
  `ObjectiveReadout#isNoise` in the packaged SPM version.
- **Must happen:** the objectives UI shows the real active action or `Idle`.
- **Must not happen:** `Exploration activity` is displayed, or the observer claims `MOVE`/`LOOK`.
- Runtime visual confirmation is **`UNVERIFIED`** until the user installs 1.8.1 and observes a mob;
  building and static tests do not prove the client display.
- **Build evidence:** `gradlew.bat clean test build` passed with 27 tests, zero failures/errors;
  packaged `build/libs/spmscavenger-1.8.1.jar` declares version 1.8.1 and contains the repaired
  observer class. SHA-256: `D92E4723B61DFDCEDFF9EFE4C72AB13CA6ED6287265130677CE754DF582C8E0D`.
## 2026-08-08 — route interest scoring, and reversing the "deliberately generic" pin (1.8.5)

### The reported observation

Expedition destinations are empty — no cave, no trees, no portal, no villager — and the mob then
idles for about a minute.

### Why (`CONFIRMED`, radii read from both jars)

Every sensing radius in the assembled mod set:

| Goal | Radius |
| --- | --- |
| `RaidContainersGoal`, `RaidArmorStandsGoal` (SPM) | 12 |
| `SeekAmmoGoal` (SPM) | 10 |
| `CollectFloorItemsGoal`, `HarvestCropsGoal` (SPM) | 8 |
| Shelter / gather / torches (this mod) | 12 / 10 / 6 |
| Crafting table (this mod) | 24 |

**Nothing in either mod looks further than 24 blocks, and almost everything looks 8-12.** Every
interesting behaviour both mods own is a twelve-block bubble waiting for something to enter it, and
exploration is the only system that can move that bubble. Moving it to a uniformly random bearing
lands it on nothing almost every time. The idle minute is separate and also by design:
`completeExpedition` consumes a flat 600-tick cooldown, after which two local trips or another 600
idle ticks are still required.

### Reversal recorded

The 1.8.0 entry rejected POI/resource/structure awareness as duplicating "SPM discovery/work
systems". That reasoning was **wrong at range**: SPM's discovery is entirely reactive inside 8-12
blocks and it has **no long-range target selection at all**. Choosing where to put the bubble is
unowned; reacting to what is in it is SPM's. Gate SPM-2 is not engaged by a destination-biasing
term. The 1.8.0 decision stands for everything it actually covers (no scanning planner, no
POI/structure goal); only the "no destination bias" part is superseded, on the user's evidence.

### Selected design

`routeScore = novelty + headingNovelty - recentRegionPenalty + blockEntityInterest`, interest last
and smallest.

- **Bonus only, never a penalty.** Villages, mineshafts, caves, forests and ridges are ordinary
  blocks and register nothing. A chunk with no block entities scores 0, and so does an unknown one.
  Penalising absence would teach the mob to avoid unexplored wilderness — the entire point of the
  goal.
- **Presence, not quantity.** `ExplorationInterest.chunkScore` takes a `Set<Signal>`, so 180 chests
  are arithmetically indistinguishable from one. Quantity cannot leak in even by mistake.
- **Signals name the evidence, not a conclusion.** A bed is not a village; a chest is not a
  dungeon. Recognition (`likely village` / `likely player base`) is deliberately deferred to a
  separate later system rather than burdening `ExploringGoal` now.
- **Deviation from the proposed magnitudes, deliberate.** The proposal suggested spawner +100,
  chest +25. Novelty spans 0-19 and the recent-destination penalty is -100, so +100 would make a
  known spawner exactly cancel the anti-repetition term and a mob would walk back to the same chunk
  every expedition — the quantity problem wearing a different hat. The ordering is kept; the scale
  is capped: `CHUNK_CAP` 40 (= `SPAWNER`), `ROUTE_CAP` **60**, which beats a repeated heading (-35)
  and a visited region (-20) but never a repeated destination (-100). Encoded as a test, not a
  comment.

### The five performance invariants (in `ChunkInterest`'s javadoc and enforced in code)

1. Never use a chunk accessor that can load or generate — `getChunkNow` only; `null` is
   **unknown**, scores 0, and is never read as "bad candidate".
2. Never enumerate an unbounded block-entity collection — `SAMPLE_LIMIT` 32, with early exit the
   moment a signal saturates `CHUNK_CAP`. Setting `CHUNK_CAP = SPAWNER.weight` makes that exit
   exact rather than merely a heuristic.
3. Never score from `canUse`, `tick`, or any per-tick path — only from cooldown-gated expedition
   construction.
4. Never inspect the same chunk twice in one `createExpedition` — per-call `Map<Long, Integer>`,
   discarded on return. No persistent index, no invalidation, no save/load surface.
5. Never let count decide attractiveness — enforced by the `Set` signature in (2).

### Cost

`INFERRED`, not profiled. Adds ≤32 `getChunkNow` + `HashMap` reads per expedition — before the
per-call cache, which the eight overlapping candidate routes make substantial — against the ≤288
`isPositionEntityTicking` calls the same method already makes, and against up to 20 A* searches of
512 nodes each per subsequent plan attempt. Observed expedition rate in `latest.log` was 0.26/s
world-wide (140 in ~9 min), peaking at ~8 in 2 s. Confirmation would be a spark comparison of
`createExpedition` self-time, or no change in `Can't keep up!` frequency.

### Acceptance

- **Must happen:** a route past a spawner, chest cluster or village beats an equally novel empty
  route.
- **Must not happen:** wilderness penalised; count-weighted scoring; a chunk loaded or generated by
  scoring; the same rewarding chunk chosen repeatedly; scoring called from a per-tick path.
- **Build evidence:** `gradlew.bat clean build` passed; **44 tests, zero failures/errors**.
  Artifact `spmscavenger-1.8.5.jar`, SHA-256
  `82342ACCD952D482910F306E353268E4BA57864DEF40CE86C34AA7A3379CE784`.
- **Not implemented:** the proposed `routeRisk` term. No risk signal is defined yet and inventing
  one would be unfounded; left out rather than guessed.
- Runtime behaviour **`UNVERIFIED`** — still no session log for 1.8.3, 1.8.4 or 1.8.5.

## 2026-08-08 — travelling companions, and why SPM friends never form off a train (1.8.4)

### The reported observation

Two PlayerMobs are almost never seen travelling together; the only company on show is being chased
or a brief greeting.

### Why (`CONFIRMED`, read from `playermob-fabric-0.86.0+1.21.1.jar`)

SPM already supports mob-to-mob company and it is **not** starved by priority:

- `FollowLovedOneGoal` is registered at **priority 2** — above every scavenging goal (3).
- `PlayerMobEntity.findFollowTarget()` scans **every `LivingEntity` within 64 blocks** and
  explicitly branches on `instanceof PlayerMobEntity`. Mobs are eligible company, not just players.
- Its one gate is `feelingToward(candidate) >= 7.0f`.

The bond cannot get there in an ordinary world:

- `FeelingRecord.DEFAULT` / `FeelingLedger.DEFAULT` = **5.0** on a 0-10 scale. Every untouched pair
  starts exactly neutral, needing **+2.0** to become followable.
- The accrual events are `CROUCH_STEP` 0.1 (budget `CROUCH_CAP_BASE` 2.0), `DEFEND_STEP` 1.0
  (`DEFEND_CAP` 2, and `DefendLovedOneGoal.DEFEND_FRIENDLINESS` = 6 gates who may), gifts
  0.5-3.0, and `TRAVEL_STEP` **0.2**.
- **The travel bond is dead off a Dungeon Train.** `FeelingLedger.travel(uuid, index)` delegates to
  `FeelingRecord.afterCarriageAdvance(int)`, which returns `this` unchanged unless the index
  *differs* from `lastCarriageIndex`; the first observation only records the index and pays
  nothing. Off a train `TrainConfinement.carriageIndex` is always `NO_CARRIAGE`, so the value never
  changes and no journey ever counts.

So in survival play a pair must exchange roughly **20 greeting crouches**, defend each other
**twice**, or receive a gift, before SPM will let them walk together. Greeting (priority 1, no
feeling gate) and fleeing are ungated, which is exactly the subset the user sees.

### Gate SPM-1 ladder

Level 2 (existing SPM policies) already owns *following*; reimplementing it would breach SPM-2.
The gap is not the goal, it is that nothing off a train marks a shared journey. Stopping at the
first correct level therefore means: **do not build following, build departing together.**

| Option | Benefit | Risk/cost | Decision |
| --- | --- | --- | --- |
| Write to SPM's ledger so co-travel raises the bond to 7.0 | Fixes grouping *generally* — following, defending, greeting all switch on | `FeelingLedger feelings` is a **private final field with no public mutator**; needs reflection or an accessor mixin into another mod, and makes this addon a silent author of SPM's social economy | **Rejected for now** — raised with the user as a separate decision |
| Reimplement a follow/group goal here | Self-contained | Duplicates `FollowLovedOneGoal` outright (Gate SPM-2) | Rejected |
| Invite nearby well-regarded mobs onto the same heading | No SPM state written, no following reimplemented, company is emergent like the campfire | Companions can drift apart under interruption; they are travelling together, not bonded | **Selected** |

### Selected design

- `PlayerMobs.feelingToward(Mob, LivingEntity)` reads SPM's **public** `feelingToward` by cached
  reflection. Read-only. **Fails closed**: a missing method warns once and disables companions,
  because an unreadable relationship must never become an assumed friendship.
- `ExplorationPolicy.travelsTogether` requires **both** sides *strictly above* neutral. Neutral is
  the starting value, so accepting it would make every mob in earshot a companion and the choice
  would mean nothing. Above neutral, in a normal world, means "we have greeted each other" — which
  gives `FriendlyGreetGoal` a consequence it did not have.
- On a leader's first successful plan, `inviteCompanions` scans `exploreCompanionRadius` (10) and
  offers up to `exploreCompanionMax` (2) mobs the heading, the leader's route length and an
  alternating lateral offset. Each guest builds its **own** route beside the leader's line, so they
  depart abreast rather than in single file.
- An invitation **bypasses only the readiness thresholds** — a mob goes because a friend is going.
  Cooldown, combat, sleeping, passenger and simulation-frontier conditions all still apply, so
  being asked can never route around a safety condition.

### Acceptance

- **Must happen:** `exploration departed … companions=N` appears; two mobs that have greeted leave
  together and walk abreast.
- **Must not happen:** a companion recruited while in combat or asleep; a mob invited past its
  cooldown; company forming between mobs at neutral regard; any write to SPM state.
- **Build evidence:** `gradlew.bat clean build` passed; **37 tests, zero failures/errors**.
  Artifact `spmscavenger-1.8.4.jar`, SHA-256
  `03398F549BFA138C6F5F80818C875AA5D29360FC378D32251A0DAC2DDC77AEFD`.
- **Cost (`INFERRED`, not profiled):** one `getEntitiesOfClass` over a 20-block box plus at most a
  few reflective calls, once per expedition — roughly once per 40 s per mob. Not measured (AV-1).
- Runtime behaviour **`UNVERIFIED`** until a session logs a `companions=` line.

## 2026-08-08 — hop-limited exploration paths and releasing MOVE on failure (1.8.3)

### Runtime evidence (`CONFIRMED`)

Instance `D:\Minecraft\Instances\Fabulously Optimized`, `logs/latest.log`, Scavenger 1.8.2 with
`playermob-fabric-0.86.0+1.21.1`, session 02:08-02:18, world `newcityworld`:

```
exploration completed : 0
exploration ended     : 140   (136 PATH_FAILURE, 4 SIMULATION_FRONTIER)
waypoint=2/2 58   waypoint=2/3 51   waypoint=2/4 21
waypoint=3/3  4   waypoint=3/4  2   waypoint=4/4  2
```

130 of 140 read `waypoint=2/N waypointFailures=3 expeditionFailures=6` — three failures at stage 1,
`SKIP_WAYPOINT`, three at stage 2, `ABANDON_PATH`. **No waypoint was ever reached.** The user
reported mobs standing still while the readout said `Exploring`, which is the same event seen from
in front of the mob.

### Root cause (`CONFIRMED`)

`getNavigation().createPath(pos, accuracy)` resolves to
`PathNavigation.createPath(Set,int,boolean,int)`, which passes `Attributes.FOLLOW_RANGE` to
`PathFinder.findPath` as `maxRange`. `PathFinder.findPath` skips `getNeighbors` for any node where
`node.distanceTo(startNode) >= maxRange` (verified at bytecode offset 210 of the loom-mapped 1.21.1
`PathFinder`). `PlayerMobEntity.createAttributes()` sets `FOLLOW_RANGE` (`field_23717`) to **32.0**
(verified in `playermob-fabric-0.86.0+1.21.1.jar`). Stage distance was **24-48 blocks**, so most
stage targets were geometrically unreachable: `canReach()` false on every one of the 20 landing
probes, every attempt.

Secondary (`INFERRED`): landings resolved at `MOTION_BLOCKING_NO_LEAVES` height, which in a city
world is a rooftop — standable, not reachable, and it consumed the probe budget first.

The standing itself was 1.8.2's own presentation fix: on plan failure the goal set `yieldForRetry`,
`canUse()` returned true with no path, and the goal held `Flag.MOVE` at priority 8 for the whole
20-tick wait. `TrackedLocalWanderGoal` at 9 can never take a flag from a lower priority number
(`WrappedGoal.canBeReplacedBy`), so the mob was pinned in place for roughly five seconds per
expedition.

| Option | Benefit | Risk/cost | Decision |
| --- | --- | --- | --- |
| Pass a larger `followRange` to the public `createPath(BlockPos,int,int)` overload | One-line fix, keeps whole-stage paths | `maxVisitedNodes` is fixed at navigation construction to `floor(FOLLOW_RANGE * 16)` = 512 and does **not** grow, so long searches still return partial paths; the `PathNavigationRegion` snapshot grows as `(followRange + 8)^3` — 145³ at 64 | **Rejected** — more allocation for the same failure |
| Clamp configured stage distance to the follow range | Trivial | Caps expeditions at ~30 blocks and silently ignores the user's config | Rejected |
| Walk each stage in hops the pathfinder can honour | Keeps 24-48 block stages and the 150-block route; every request is small, cheap and usually reachable | Replans on each hop arrival; a hop is a new concept in the goal | **Selected** |
| Keep holding `MOVE` during the replan wait (1.8.2 behaviour) | The `Exploring` label never flickers | The mob is visibly frozen | **Reverted** — movement beats a label |

### Selected repair

- `ExplorationPolicy.maxPathStep(followRange)` = `clamp(followRange / 2, 8, 24)` — 16 for a
  PlayerMob, leaving 16 blocks of slack for the detours the straight-line cutoff does not forgive.
- `planCurrentStage` tries the longest honourable hop, then halves it down to `MIN_PATH_STEP` (6),
  sharing one 20-probe budget across the ladder. Only a path that ends at the waypoint completes a
  stage; hop arrivals call `advanceHop`, which replans from closer without touching the heading,
  the waypoint list or the index.
- A hop or waypoint arrival resets `expeditionFailures`. Ground actually covered earns back the
  budget; `MAX_EXPEDITION_TICKS` (2400) still bounds the whole expedition.
- `landingCandidates` drops candidates more than 16 blocks above or below the mob and probes the
  rest nearest-own-level first (stable sort, so ring order survives inside a band).
- `yieldForRetry` is deleted. A failed plan sets `retryAfterTick` and lets the activation end, so
  local wandering owns `MOVE` for those 20 ticks. This deliberately reverses the 1.8.2 decision;
  that decision is preserved above rather than removed, and its reasoning still holds for the case
  it was written for — it was simply outweighed once the wait became the normal path.
- Diagnostics: `hops=` added to both the completed and ended log lines, so the next log
  distinguishes "never moved" from "moved and ran out of route".

### Acceptance

- **Must happen:** expeditions log `exploration completed … hops=N`; a mob in the `Exploring`
  state is walking.
- **Must not happen:** a motionless mob holding `MOVE` while labelled `Exploring`; a path request
  longer than the mob's follow range; a rooftop landing probed before same-level ground.
- **Build evidence:** `gradlew.bat clean build` passed; **34 tests, zero failures/errors**.
  Artifact `spmscavenger-1.8.3.jar`, SHA-256
  `93596989B138485320E5362911572BAC9EF8E75A6CC60BD92016973578CCFC4A`.
- Runtime behaviour remains **`UNVERIFIED`** until 1.8.3 is installed and the log shows a non-zero
  `exploration completed` count.
- The `Can't keep up!` warnings in the same log (worst: 14 793 ms / 295 ticks behind at 02:11:04,
  four seconds before the first failure line) are **`INFERRED`** to be related — 1.8.2 could fire
  up to 120 full A* searches per failed expedition. Not attributed without a profiler (Gate AV-1).

## 2026-08-08 — hide Antics and retain Exploring through replans (1.8.2)

- **Runtime evidence:** active `latest.log` loaded Scavenger 1.8.1 and logged normal activation;
  four probes found no Scavenger error, exception, exploration reason or crash frame. The active
  config had `mimicry=true`, which made flagless `AnticsGoal.canUse()` continuously true.
- **Root cause (`CONFIRMED`):** SPM humanised the always-running decorator class as `Antics`, exactly
  as it previously humanised the readiness observer. Separately, a retryable path failure set
  `yieldForRetry`, stopped `ExploringGoal`, and exposed idle/local wander for 20 ticks.
- **Selected repair:** classify Antics under SPM 0.86.0's filtered cosmetic vanilla goal contract;
  retain no flags and disable the superclass start behavior. Keep `ExploringGoal` running during its
  bounded replan wait, and require 20 continuous navigation-done ticks before path failure.
- **Alternative:** move both background behaviors into a global server tick registry and expose a
  separate synchronized activity state. Rejected because it adds entity/world lifecycle ownership
  without improving the reported behavior. Weakening simulation-frontier or retry caps was also
  rejected because no evidence shows those safety limits caused this reproduction.
- **Diagnostics:** successful completion logs entity, stage count and endpoint. Final abandonment
  logs `PATH_FAILURE`, `SIMULATION_FRONTIER`, or `STALE`, waypoint position and failure counts.
- **Must happen:** Antics works invisibly; a retryable path gap remains `Exploring` and resumes the
  retained expedition; final outcomes are diagnosable from the next `latest.log`.
- **Must not happen:** false `Antics`, immediate path-done failure, regenerated headings, unbounded
  retry, forced chunks, or weakened frontier safety.
- Runtime behavior remains **`UNVERIFIED`** until the user installs 1.8.2 and repeats the scenario.
- **Build evidence:** `gradlew.bat clean test build` passed; 30 tests, zero failures/errors/skips.
  The packaged metadata reports 1.8.2. Artifact SHA-256:
  `092596575D4E0C2F589E108CD433EB7CF1DD48AE554F2DC5DD5367CA46FC7340`.
# 2026-08-08 — movement-first EnvironmentalEscapeGoal (1.9.0)

- **Reported failure:** a PlayerMob remains stuck in Powder Snow. The active log confirms
  `spmscavenger 1.8.5` loaded and contains no Scavenger exception or Powder Snow diagnostic; this is
  a silent behavior gap, not a crash.
- **Host evidence (`CONFIRMED`):** pinned SPM v0.86.0 registers priority-0 `FloatGoal` and
  `FireBucketGoal`, but three source probes found no Powder Snow, `isInWall`, suffocation, freezing,
  or air-recovery goal. Mojang-mapped 1.21.1 exposes `Entity.isInPowderSnow` and `isInWall()`.
- **Selected option:** a priority-0, `MOVE`-only addon goal uses semantic hazard state, immediate
  jumping, a bounded safe-ground path, and delayed exact-obstruction removal. It yields on fire so
  SPM keeps its real bucket lifecycle. This is a compatibility-first `AI_EXTENSION`, not a copied
  survival stack.
- **Alternative:** movement-only recovery. It has zero world-damage risk but cannot reliably free a
  mob embedded in multiple Powder Snow/falling-block cells. It remains available by disabling
  `environmentalEscapeBreakBlocks` (or setting the cap to zero).
- **Mutation policy:** 30-tick movement grace; one intersecting block per re-evaluation; three per
  continuous incident; config and `mobGriefing`; no block entity; natural or explicitly allow-tagged;
  deny tag wins; hardness 0..2 by default. Planning uses only entity-ticking positions and a radius
  capped at eight. No path or environmental scan runs from `canUse()`.
- **Borrowed:** the existing gather goal's `mobGriefing`, conservative natural-block classification,
  and one-block-then-repath discipline. They fit because the failure is another bounded recovery
  mutation. Risk: natural blocks can still be player-placed, so exact intersection, grace, hardness,
  tags, and the incident cap remain mandatory.
- **Must happen:** trapped mobs immediately attempt jump/path recovery and stop when safe; after the
  grace period, Powder Snow or falling natural suffocation may be cleared one intersecting cell at a
  time.
- **Must not happen:** duplicate fire handling, chunk loading, immediate digging, chest/hard/denied/
  non-natural block removal, nearby unrelated damage, or more than three removals per incident.
- **Verification:** `gradlew.bat clean test build` passed with 47 tests, zero failures/errors/skips.
  Final metadata reports 1.9.0; artifact SHA-256
  `0175F9ED4F490B0A88E3922703FEC7941134EB7AC2858FFA9CBF952DFBA9F314`. Runtime Powder
  Snow/falling-block behavior remains `UNVERIFIED` because no Minecraft launch was authorized.

## 2026-08-08 — real-tool timed environmental mining (1.9.1)

- **Gap in 1.9.0 (`CONFIRMED`):** last-resort removal called `destroyBlock` immediately and never
  consulted the PlayerMob's equipment. The same 30-tick movement grace applied to Powder Snow and
  true head-block suffocation even though the latter cannot normally be solved by navigation.
- **SPM evidence:** v0.86.0 `PlayerMobEntity` implements vanilla `InventoryCarrier`; its public
  `getInventory()` returns the authoritative eight-slot `SimpleContainer`. Its best-of-category
  helpers are private and category/combat-oriented, not a public per-block tool selector.
- **Selected option:** inspect the real main hand and `InventoryCarrier` backpack, rank them using
  Minecraft's `ItemStack.getDestroySpeed`, correct-tool requirement, and vanilla base 30/100-divisor
  timing; swap the winning stack, render swing/crack progress, use it for loot and durability, then
  restore the displaced hand stack.
- **Rejected option:** a FakePlayer reproduces more player-only hooks and status modifiers, but adds
  a manufactured player identity, claim/permission ambiguity, and another inventory/action
  lifecycle. Switch only if runtime evidence proves a required target block works exclusively
  through player break hooks.
- **Transaction rule:** the displaced hand stack occupies the selected tool's exact backpack slot.
  Restoration writes only when that slot still contains the same parked object; otherwise the goal
  warns and leaves current state untouched rather than overwriting a concurrent inventory edit.
- **Hazard timing:** Powder Snow default grace is eight ticks; `isInWall()` uses zero grace. Blocks
  retain every 1.9.0 mutation gate and the three-block cap.
- **Known adaptation:** timing follows vanilla base hardness/tool correctness but does not simulate
  player-only Haste, Mining Fatigue, underwater, or airborne modifiers because the actor is a Mob,
  not a Player. Runtime remains `UNVERIFIED` until the tool choice, cracks, drops, durability,
  interruption restoration, and no-loss/no-duplication cases are observed in Minecraft.
- **Must happen:** fastest owned tool is visibly equipped; required time and cracks elapse; tool
  durability and tool-aware loot apply; prior equipment returns after success or interruption.
- **Must not happen:** instant deletion, conjured/copied tools, a parallel inventory/evaluator,
  stale cracks, item loss/duplication, or overwriting a concurrently changed backpack slot.
- **Build evidence:** `gradlew.bat clean test build` passed with 50 tests, zero
  failures/errors/skips. Final JAR metadata reports 1.9.1; SHA-256
  `147B2AAA577101355FC92D6D729A98AA8B01BF68FCF9800FAB511F7B7F20C86B`.
  That was the environmental-only build. A later parallel exploration update rebuilt the same
  1.9.1 version; the current combined source reproducibly builds SHA-256
  `738130B4FB121CFE2C5DFC14FC211FA2B95D4352E251E5F0245C2127FE44B8E2` (50 tests, zero
  failures/errors/skips). Preserve both hashes as chronology; use the latter for the current tree.
## 2026-08-08 — persistent-order arbitration and goal-loop audit (1.9.2)

### Confirmed failure

SPM v0.86.0 registers `StayNearGoal` at priority 2 and exposes the persistent order through public
`PlayerMobEntity#getStayAnchor()`. Scavenger's priority-8 `ExploringGoal.stop()` intentionally kept
its expedition across all interruptions. The combination formed a deterministic cycle: explore
outside the tether, return under `StayNearGoal`, then resume the same outward waypoint.

### Options

| Option | Benefit | Risk | Decision |
|---|---|---|---|
| Discard every interrupted expedition | Smallest state machine | Breaks the required resume-after-combat/work behavior | Rejected |
| Detect `StayNearGoal` by implementation class/name | Directly matches the visible goal | Brittle hardcode; misses another goal enforcing the same semantic order | Rejected |
| Read SPM's public stay-anchor state and classify it as a persistent constraint | Uses the same source of truth as SPM, preserves transient resume, survives goal implementation changes | Cached reflection must fail safely if the API changes | **Selected** |

`PlayerMobs.stayAnchorState` now distinguishes `ABSENT`, `PRESENT`, and `UNAVAILABLE`. Only confirmed
absence permits exploration. Presence abandons an active route with `STAY_ANCHOR`; unavailable API
state warns once and disables exploration rather than silently violating a player order. Anchored
mobs are also excluded from companion recruitment. The addon never clears or mutates the anchor.

### Broader goal-loop audit

| Goal | Persistent state after `stop()` | Progress/failure bound | Loop finding |
|---|---:|---|---|
| `ExploringGoal` | Yes, by design | stale timer, waypoint/expedition failure caps, frontier abandonment | Confirmed stay-anchor cycle fixed; transient resume retained |
| `GatherResourcesGoal` | No | 140-tick approach cap, whole-tree backoff, bounded target/path probes | No equivalent persistent preemption cycle found |
| `CraftTorchesGoal` | No | 200-tick table approach cap | Repeated inaccessible-table attempts remain possible, but do not retain a route across preemption; runtime frequency `UNVERIFIED` |
| `PlaceTorchGoal` | No | 100-tick approach cap and post-placement cooldown | Repeated inaccessible-position attempts remain possible; no persistent cross-goal cycle found |
| `SeekShelterGoal` | No; bed claim released | 400-tick cap and phased scan | No persistent cross-goal cycle found |
| `CampfireGoal` | No | 200-tick cap and phased scan | No persistent cross-goal cycle found |
| `EnvironmentalEscapeGoal` | Incident only, deliberately | bounded path attempts, block cap, ends after sustained safety | Continuous while physically trapped is safety ownership, not a competing-goal loop |
| `TrackedLocalWanderGoal` | No | vanilla navigation lifecycle | No loop found |
| `AnticsGoal`, `ExplorationActivityGoal` | Cosmetic/readiness counters only | flagless; no navigation ownership | Continuously eligible by design, cannot compete for `MOVE` |

Three absence probes were recorded before the broader conclusion: no other addon goal preserves a
navigation target/path through `stop()`; no other addon goal combines persistent intent with a
higher-priority return-to-origin behavior; and no second stay-anchor integration existed in source,
tests, or project documentation before this repair.

Acceptance: **must happen** — one anchor assignment ends one expedition and later `StayNearGoal`
owns the return; ordinary transient interruptions can still resume. **Must not happen** — the old
outward waypoint resumes after the mob returns inside its tether, the addon changes the anchor, or
an SPM API rename crashes the game. Runtime behavior remains `UNVERIFIED` pending user launch.

Build/package evidence: `gradlew.bat clean build` passed with 84 tests, zero failures/errors/skips.
The final JAR metadata reports 1.9.2. Artifact SHA-256:
`5A2EE82AF2226860249FAF31E03D89F4CA06393641FB7F7D74843EB383F1736E`.

## 2026-08-08 — MI-3/MI-23 staged NEED allocation

`ResourceWealthPolicy` allocates stock once in order: immediate, replacement, project, then working
reserve. Independent per-layer shortfalls were rejected because they count the same stack more than
once. This slice intentionally reports no marginal wealth; MI-24 owns curves and MI-4 integration.

## 2026-08-08 — MI-1 aggregate gather intent

Gather demand is now represented by one immutable `GatherIntentPolicy.GatherIntent` snapshot.
It may contain several resource flags because torch and tool prerequisites can coexist; making it
an exclusive resource enum would introduce prioritization before MI-2 owns that decision. The
snapshot is evaluated once per bounded scan and reused for candidate filtering and retained drops.
`WorkDemandPolicy` remains furnace arbitration, and no wealth stock target or second scanner was
introduced. Runtime behavior remains `UNVERIFIED`.

## 2026-08-08 — MI-4R candidate-aware wealth admission

Selected candidate-aware wealth scoring over a hard saturation cutoff. Consumer NEED remains an
unconditional resource set; optional wealth is retained as immutable category context and evaluated
against normalized candidate distance before pass-one admission. This preserves marginal utility
and allows genuinely nearby opportunities without letting the nonzero saturation floor keep every
resource scanner active forever.

The alternative hard cutoff was smaller but rejected because it would recreate stock targets and
discard the already accepted opportunity-cost model. The current cost covers discovery distance
only; path, dig-time, and danger costs remain later RFC work and must not be claimed implemented.
Diamond wealth is bounded by the existing generation-height rule, and log stock uses the Minecraft
log tag rather than a species list. Static tests/build are `CONFIRMED`; runtime and performance are
`UNVERIFIED`.

## 2026-08-08 — Looted tools use one three-location ownership view

Selected backpack + main hand + off hand ownership over requiring manual inventory placement or
intercepting SPM loot. Manual placement creates unexplained slot specificity. Loot interception
duplicates host behavior and risks full-inventory loss. The selected design lets every progression
consumer see the same usable tool and lets `ToolBox` swap an off-hand winner directly into main hand
without requiring backpack capacity. Broken tools remain excluded by tier policy. Static tests and
build are `CONFIRMED`; live SPM loot placement and combat equipment arbitration are `UNVERIFIED`.

## 2026-08-09 — MI-14C3 progress time excludes completed blocker episodes

The progress lease persists both the last observable progress time and accumulated paused ticks for
the current progress window. The effective baseline is `executorStartedAt` until the first real
progress event; starting the goal therefore never invents progress.

Two smaller alternatives were rejected. Merely skipping evaluation while blocked includes that
wall-clock time and causes immediate expiry after resume. Mutating `executorStartedAt` destroys the
historical start-lease fact. A mutable remaining-budget counter is viable but has more tick-update
and save/reload surface than the selected timestamp + exact-pause accumulator.

Only successful planned block removal, completed stair steps, and terminal handoffs refresh the
clock. Failed `destroyBlock` attempts stay on the same cell and make no budget/progress claim, so the
bounded lease can expose the stall. Must happen: an admissibly stuck started descent ends once with
`NO_PROGRESS`. Must not happen: ticks/replans create progress, or combat/contention time causes an
immediate post-resume timeout. Static/unit/build evidence is `CONFIRMED` (310 tests); observable
Minecraft behavior remains `UNVERIFIED` pending launch approval.

**MAIBS correction:** the clock representation remains valid, but the integrated timeout ordering
does not. Controlled descent's total project clock also ends at 2400 ticks using `>=`; C3 uses
`>2400`, so an active stall cannot reach the intended `NO_PROGRESS` branch first. Furthermore,
protected MOVE holders are deliberately excluded from CONTENTION without being represented as a
different blocker. This decision is therefore **not accepted behaviorally** until timeout ordering
and protected-interrupt ownership are repaired. See `.superpowers/sdd/archive/task-28-report.md`.

## 2026-08-09 — MI-14C3-R1 separates scheduler arbitration from lease availability

**Decision D-MIW-040 — IMPLEMENTED.** A running goal is now tested against every flag required by
the designated executor (`MOVE + LOOK` for controlled descent), then mapped independently to a
lease blocker. Arbitration still decides whether mining may force a goal to yield; it no longer
stands in for whether mining can physically acquire its flags.

Safety/recovery maps to condition-bound `SAFETY_RECOVERY`: start and progress clocks pause for the
exact observable episode, mining never preempts it, and the 1200-tick combat grace does not apply.
Combat retains `COMBAT_TARGET`; eating and short host reflexes remain bounded temporary blockers.
Persistent stay anchors prevent assignment, and running commanded actions prevent or revoke with
`PLAYER_ORDER`. Lease NBT v4 persists `startPausedTicks`; historical `assignedAt` is not mutated.

The progress lease is 400 admissible ticks while the total project budget remains 2400 executor
ticks. A successful block removal marks progress immediately; 400 conservatively exceeds the
known <=200-tick break operation and leaves tolerance for one-step navigation, replanning, observer
cadence, and server-tick irregularity. Alternative 600 delayed stall recovery without a known legal
operation requiring it. A general two-axis scheduler-effect record remains an option if more
executors make the taxonomy materially larger; mapping all protected work to CONTENTION was
rejected because it erases safety and player-authority semantics.

Must happen: long observable safety interruption resumes with the exact remaining lease window,
and an admissibly stuck descent reaches `NO_PROGRESS` before the total cap. Must not happen:
LOOK-only eating resolves to `NONE`, a player command creates a revoke/reassign loop, or blocker
changes duplicate pause time. C3-F1…F7 and the full 321-test clean build are `CONFIRMED`; Minecraft
runtime behavior remains `UNVERIFIED`. Evidence: `.superpowers/sdd/archive/task-30-report.md`.
