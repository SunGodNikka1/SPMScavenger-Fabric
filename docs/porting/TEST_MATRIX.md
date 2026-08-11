# SPM Scavenger test matrix

## Stay-near/exploration arbitration — current 1.9.4 artifact

| Check | Must happen | Must not happen | Evidence |
|---|---|---|---|
| Existing anchor | A PlayerMob with a confirmed stay anchor remains available to SPM's native `StayNearGoal`; Scavenger does not start or accept an expedition | Alternation between `Staying near` and `Exploring` | `ExplorationPolicyTest.onlyConfirmedAbsenceOfAStayAnchorPermitsExploration` `CONFIRMED`; runtime readout `UNVERIFIED` |
| Anchor assigned mid-route | The active expedition is abandoned with `reason=STAY_ANCHOR` and its outward waypoints are discarded | Returning inside the tether radius resumes the old outward waypoint | `ExploringGoal.yieldToStayAnchor` static inspection `CONFIRMED`; runtime log/readout `UNVERIFIED` |
| Transient interruption | Combat, gathering, looting, or another transient higher-priority goal still preserves the expedition | The stay-anchor repair destroys routes after every ordinary interruption | `ExploringGoal.stop` state boundary inspection `CONFIRMED`; runtime resume `UNVERIFIED` |
| Changed SPM API | Missing `getStayAnchor` warns once and disables exploration only | Crash, warning spam, or silently ignoring a possibly active player order | `PlayerMobs.stayAnchorState` inspection and compile `CONFIRMED`; changed-version runtime `UNVERIFIED` |

Runtime acceptance requires a separately approved Minecraft launch: start an expedition, issue a
stay-near order while it is moving, and observe one `STAY_ANCHOR` end followed by no further
`Exploring` objective while the anchor exists. Clear the order and confirm exploration can later
start again. Repeat with combat interruption and confirm the remaining route resumes.

## GAO-9 overland environment affinity — 1.9.4

| Check | Must happen | Must not happen | Evidence |
| --- | --- | --- | --- |
| Classification | Existing inspected positions may carry any subset of `FOREST`, `OCEAN`, `SNOWY`, `NETHER`, `END`; unknown is neutral | Name-based biome guessing or a mutually-exclusive fake category | `EnvironmentProfileTest` `CONFIRMED` |
| Attribution | Only `OVERLAND_EXPLORATION` + `EXPEDITION_END` + `VOLUNTARY_SUCCESS` + `EXPEDITION_COMPLETE` learns | Path/frontier/order/combat/stale failure teaches environment dislike | `EnvironmentOpinionLearningTest` `CONFIRMED` |
| Multi-label normalization | One personality-scaled delta is divided across labels | FOREST+SNOWY doubles total learning | `completedExpeditionDividesOneLearningDeltaAcrossLabels` `CONFIRMED` |
| Route authority | Mean environment preference contributes at most ±10 after route/ticking validity | Affinity erases visited −20/anti-fixation −100 or makes an invalid route valid | `EnvironmentOpinionRouteRankerTest` + integration contract `CODE_CONFIRMED`; runtime distribution `UNVERIFIED` |
| Terrain safety | Liking `SNOWY` is semantic only | Snow affinity alters powder-snow malus, escape, hazards, navigation, or survival | negative source-contract scan `CODE_CONFIRMED`; runtime cross-mod safety `UNVERIFIED` |
| Opinion disabled | No environment learning, classification, or route bias occurs | Opinion-off route order changes | gate tests/call-path `CODE_CONFIRMED` |
| Lifetime | Five-entry enum memory survives park/resume and partial-death preference semantics | Per-biome/project map, minted ID, or unbounded retention | retention/cardinality tests `CONFIRMED`; heap trend `UNVERIFIED` |

Static MAIBS: `PASS — BEHAVIORALLY_PLAUSIBLE`. No Goal, flag, priority, path, scan cadence, or
mandatory-work ownership changed. Long-duration route distribution and performance remain
`UNVERIFIED`; no Minecraft launch was authorized.

## Tool tier upgrades — Phase 1 stone (1.9.1+)

Canonical design: `plans/RFC-TOOL-TIER-UPGRADES.md`. Runtime rows stay `UNVERIFIED` until an
approved Minecraft launch.

| Check | Must happen | Must not happen | Evidence |
| --- | --- | --- | --- |
| Atomic craft (TT-0R) | Full mid-upgrade pack crafts stone pick once recipe frees a slot | Ingredient loss on failed craft; false refuse of a valid full-capacity recipe | U-0A/U-0B/U-0C `CONFIRMED`; runtime `UNVERIFIED` |
| Stone craft + disposal | 3 cobble + 2 sticks → stone tool; wooden predecessor removed/dropped | Planks consumed for stone tools; both wood and stone kept forever | `ScavengerCraftingTest` `CONFIRMED`; runtime drop visual `UNVERIFIED` |
| Gold ranking (TT-1aR) | Gold pick/axe rank as `WOOD`; stone upgrade still pursued | Gold classified as `IRON` or suppressing stone craft | U-9A/U-9B `CONFIRMED` |
| Config caps (TT-1aC/TT-2d min) | Cycle selector exposes NONE/WOOD/STONE/IRON; load clamps DIAMOND/null → IRON with warn | Unimplemented DIAMOND pressure; stuck Cloth dropdown | U-10A/U-10B `CONFIRMED`; UI glance `UNVERIFIED` |
| Cobble gather + protection | Exposed surface stone/cobble when upgrade pending; infested skipped | Player stone structures mined when protection on | U-6/U-7 `CONFIRMED`; runtime TT-1 must-not `UNVERIFIED` |
| Equipped ownership (TT-1bR) | Stone pick in hand + stone axe in pack stops cobble demand | Wooden pick in hand suppresses pending stone upgrade | U-11A/U-11B `CONFIRMED`; runtime TT-2 `UNVERIFIED` |
| Torch primacy | At `torchStockTarget`, gather (including cobble) stops | Upgrade loop blocks torch crafting forever | Policy/static `INFERRED`; runtime TT-4 `UNVERIFIED` |

**Runtime test datapack:** `test-datapacks/phase1-tool-tier/` (namespace `spm_phase1`).  
Spec: `docs/agent-workflows/RUNTIME_TEST_DATAPACK.md`. Quick start: `/function spm_phase1:quickstart`.

### Runtime acceptance (requires launch approval)

| ID | Datapack setup | Spawn preset | Must happen | Must not happen |
| --- | --- | --- | --- | --- |
| TT-0R | `arena/build` | `spawn/full_pack` | Stone pick crafts at full capacity; no ingredient loss | Loss or false refuse |
| TT-1 | `arena/build` or `arena/stone_only` | `spawn/need_cobble` | Mine exposed stone → cobble → stone pick at table | Mine stone wall |
| TT-2 | — | `spawn/equipped_done` | No further cobble gather when both stone tools owned | Infinite cobble strip-mining |
| TT-3 | coal in `arena/build` | `spawn/looted_stone` | Use stone pick; no redundant stone craft | Craft wooden over stone |
| TT-4 | — | `spawn/torch_stocked` | Gather stops including cobble | Upgrade loop blocks torches |
| TT-5 | — | `spawn/need_cobble` + `tools/break_mainhand` | Re-craft pick from stock | Idle toolless with materials |
| TT-6 | manual powder snow | any with pick | Escape spends durability; chain replaces tool | Stranded toolless |

## Furnace smelting — Phase 2 (1.9.2+)

Canonical design: `plans/RFC-FURNACE-SMELTING.md`. Runtime rows stay `UNVERIFIED` until an approved
Minecraft launch.

| Check | Must happen | Must not happen | Evidence |
| --- | --- | --- | --- |
| Charcoal demand (U-F1) | No coal + surplus logs + torch fuel need → `SmeltDemand.CHARCOAL` | Smelt when coal present | `FurnacePolicyTest` `CONFIRMED` |
| Consumer iron smelt (U-F2/U-F7/FS-8) | IRON-capped stone-tool owner derives 3→1→0 ingot deficit and plans only with input+fuel | Raw iron alone creates demand; both frontiers emit; consume ore without fuel | `FurnacePolicyTest` `CONFIRMED` |
| Iron craft transaction (TT-2b) | Shared 3-ingot+2-stick spec crafts pick before axe and disposes backpack/main-hand stone tool | Duplicate quantities or lose ingredients/output on failure | `ScavengerCraftingTest` `CONFIRMED` |
| Atomic insert/extract (U-F4/U-F5) | Roll back failed insert; extract only job output | Steal pre-existing furnace stacks | `FurnaceTransfersTest` `CONFIRMED` |
| Horizontal fuel face (U-F10) | EAST-only fuel face is selected without mutating rejected faces | Assume NORTH, split a transfer across faces, or lose stacks when no face accepts | `FurnaceTransfersTest` `CONFIRMED` |
| Furnace ownership (U-F6) | Tickets survive save/load | Duplicate output on reclaim | `FurnaceStationsTest` `CONFIRMED` |

**Runtime test datapack:** `test-datapacks/phase2-furnace/` (namespace `spm_phase2`).  
Spec: `docs/agent-workflows/RUNTIME_TEST_DATAPACK.md`. Quick start: `/function spm_phase2:quickstart`.

### Runtime acceptance (requires launch approval)

| ID | Datapack setup | Spawn preset | Must happen | Must not happen |
| --- | --- | --- | --- | --- |
| RT-F1 | `quickstart` | `spawn/need_charcoal` | Charcoal → torches at owned furnace | Burn all logs |
| RT-F2 | Set `maxPickTier=IRON`, then `arena/build` | `spawn/need_iron_smelt` | Smelt three ingots, craft iron pick, drop stone pick | Producer-only hoard or axe before pick |
| RT-F3 | `arena/build` | `spawn/player_furnace_test` | Skip busy furnace at anchor+6 | Steal player coal |
| RT-F4 | RT-F2 + interrupt | manual `/reload` | Reclaim or fail-closed | Duplicate stacks |
| RT-F5 | RT-F2 then wait | `spawn/second_claimant` | One mob claims furnace | Double insert |

## Environmental escape 1.9.1

| Check | Must happen | Must not happen | Evidence |
|---|---|---|---|
| Hazard ownership | Powder snow and `isInWall()` activate priority-0 escape; fire makes it yield to SPM's `FireBucketGoal` | A duplicate fire, water-bucket, drowning, or general survival system | Mapped API and goal predicates `CONFIRMED`; runtime `UNVERIFIED` |
| Hazard-specific grace | Powder Snow gets the configured 8-tick movement grace; true `isInWall()` suffocation starts mining immediately | A universal delay leaves a suffocating mob waiting for impossible movement | Goal branch/policy inspection `CONFIRMED`; runtime `UNVERIFIED` |
| Player-like mining | Mob faces and swings; crack stages advance for hardness/tool-derived ticks | Instant block deletion or an unbounded mining loop | `MiningPolicyTest` and integration inspection `CONFIRMED`; runtime visual proof `UNVERIFIED` |
| Best owned tool | Lowest calculated break time across actual main hand + `InventoryCarrier` backpack is temporarily equipped | Conjured tool, copied stack, second inventory, or permanent combat-equipment replacement | Pinned SPM `getInventory()` plus swap inspection `CONFIRMED`; runtime `UNVERIFIED` |
| Equipment transaction | Previous main hand is parked in the selected backpack slot and restored; interruption clears cracks and restores it | Item loss, duplication, stale crack overlay, or overwriting a concurrently changed slot | Static state paths `CONFIRMED`; runtime interruption/save proof `UNVERIFIED` |
| Tool mechanics | Chosen stack controls loot and receives one durability use | Generic empty-hand loot or free tool use | `Block.dropResources(..., usedTool)` and `hurtAndBreak` inspection `CONFIRMED`; runtime `UNVERIFIED` |
| Exact obstruction | Only a powder-snow/suffocating block intersecting the mob's current AABB is eligible | Nearby wall, floor, or unrelated block selected by a scan | AABB-bounded candidate stream/policy test `CONFIRMED`; runtime `UNVERIFIED` |
| Mutation safety | Config + `mobGriefing` + no block entity + tag policy + natural/allowlisted material + hardness + incident cap all pass | Chest, unbreakable/hard, deny-tagged, non-natural, non-intersecting block, or fourth block breaks | `EnvironmentalEscapePolicyTest` `CONFIRMED`; runtime `UNVERIFIED` |
| Chunk/TPS safety | Candidate standing positions are entity-ticking; search radius ≤8 and planning occurs only on start/replan | Chunk loading, world scan, or path creation in `canUse()` | Static inspection `CONFIRMED`; profiler/runtime `UNVERIFIED` |
| Completion | Goal stops and clears incident state immediately after the mob is safe | Continued excavation after escape | State transition inspection `CONFIRMED`; runtime `UNVERIFIED` |

Runtime acceptance: place a PlayerMob in one and several layers of powder snow, then repeat inside
falling sand/gravel. Give it competing tools in its backpack and interrupt it with fire mid-crack.
It must attempt movement immediately, select the fastest owned tool, visibly mine at the calculated
duration, restore equipment on success/interruption, remove at most one eligible intersecting block
at each decision, and never exceed three in the continuous incident. Negative cases:
`mobGriefing=false`, breaking disabled, chest at eye level, hardness above the configured cap,
deny-tagged block, and fire beginning mid-recovery. Minecraft execution requires explicit approval.

## Purposeful exploration 1.8.0

### Objective-readout regression (1.8.1)

| Check | Must happen | Must not happen | Evidence |
|---|---|---|---|
| Background observer visibility | SPM displays only the mob's real visible objective (`Idle`, `Wandering`, `Exploring`, or higher-priority work) | The always-running readiness observer appears as `Exploration activity` | `ExplorationActivityVisibilityTest`; runtime visual confirmation remains `UNVERIFIED` |
| Movement arbitration | `ExploringGoal` at priority 8 or local wander at priority 9 owns `MOVE` | Wandering and actual exploration navigate concurrently | Static goal flags/priorities `CONFIRMED`; runtime visual confirmation remains `UNVERIFIED` |

### Objective-readout contrast compatibility (1.9.2+)

| Check | Must happen | Must not happen | Evidence |
|---|---|---|---|
| Dark-area readability | SPM decision glyphs use `LightTexture.FULL_BRIGHT`; secondary lines use `#E6E6E6` | World block light makes nominally white text nearly black | `DecisionReadoutContrastTest.darkWorldLightCannotDimDecisionText` and `primaryStaysWhiteAndSecondaryBecomesLighter` `CONFIRMED`; in-game visual result `UNVERIFIED` |
| Backdrop ownership | SPM/Minecraft's original plate and user opacity pass through unchanged | Scavenger forces a darker plate or ignores the user setting | `backdropRemainsOwnedByTheHostAndUserSetting` `CONFIRMED`; post-repair shader visual `UNVERIFIED` |
| Optional-host safety | Client-only `@Pseudo` Mixin uses `require=0`; packaged injection targets only SPM's renderer | Dedicated-server classloading or absence of SPM prevents startup | Clean build/package inspection `CONFIRMED`; absent-host runtime bootstrap `UNVERIFIED` |
| Scope boundary | Host Creative-only gate, 24-block range, focus-selected lines, text, and Goal authority remain host-owned | Scavenger changes AI or invents objective text while repairing contrast | Host capture call-path `CODE_CONFIRMED`; exact GPU depth parity remains `UNVERIFIED` |
| Vanilla/Sodium backend | Shader-off rendering stays in SPM's world-space path with full-bright packed glyphs and host background | Shader compatibility replaces the normal backend unconditionally | Iris-state branch inspection `CODE_CONFIRMED`; runtime retest `UNVERIFIED` |
| Iris + Photon backend | Exactly one captured SPM line is drawn after world post-processing at the mob billboard's screen position using `projection × position/view × billboard` | Photon directionally darkens the label, the dark host pass remains, or the HUD copy appears detached at the left edge | Empty-string host suppression + target-derived full matrix chain + `ShaderReadoutOverlayTest.worldProjectionIsAppliedAfterTheEntityBillboardTransform` `CODE_CONFIRMED`; two prior screenshots are `RUNTIME_CONFIRMED FAILURE`; user-confirmed rebuilt 1.9.3 brightness, attachment, and single-copy result `RUNTIME_CONFIRMED` |
| Solid-terrain occlusion | A terrain-blocked shader-overlay line uses SPM's faint `0x20` see-through alpha and keeps the host background rather than remaining fully bright | HUD replacement ignores terrain because it has no depth buffer | `ClipContext.Block.VISUAL` anchor ray + `terrainOcclusionUsesTheHostsFaintSeeThroughAlphaInsteadOfFullHudBrightness` `CODE_CONFIRMED`; translucent/entity occlusion and runtime visual `UNVERIFIED` |
| Shadow pass | Iris shadow rendering neither captures nor emits an overlay line | Duplicate/incorrect coordinates from the shadow camera | `isRenderingShadowPass` guard and main-world projection snapshot `CODE_CONFIRMED`; runtime `UNVERIFIED` |
| Optional Iris | Reflective Iris API absence returns to the shader-off path | Iris becomes a declared dependency or missing Iris crashes client bootstrap | dependency/config inspection `CODE_CONFIRMED`; absent-Iris runtime `UNVERIFIED` |
| Capture lifetime | At most 512 lines exist for one frame and both frame start and HUD completion clear them | Labels ghost across frames or an unbounded list grows with play time | hard cap + two production eviction call sites `CODE_CONFIRMED`; heap/runtime trend `UNVERIFIED` |

### Structured crafting objective compatibility (1.9.2+)

| Check | Must happen | Must not happen | Evidence |
| --- | --- | --- | --- |
| Exhaustive recipe labels | Every `ScavengerCrafting.Step` maps to a stable `Crafting — <recipe>` line; `NOTHING` safely maps to `Crafting` | Raw enum names, missing tiers, or an empty readout | `CraftingReadoutTest.everyCraftingStepHasAnExplicitStableLabel` `CONFIRMED` |
| Observation purity | Reading the selected label leaves ingredients and output untouched | Readout evaluation crafts, consumes, or reevaluates work | `CraftingReadoutTest.readingTheSelectedRecipeDoesNotMutateTheCraftingInputs` `CONFIRMED` |
| Common-side ownership | SPM's server-built objective string receives the recipe label for both menu and billboard synchronization | A client-only replacement disagrees with the server/right-click screen | `ObjectiveReadoutMixinContractTest` + source call-path `CODE_CONFIRMED`; runtime sync `UNVERIFIED` |
| Optional-host safety | Common `@Pseudo` bridge uses `require=0`; incompatible formatter falls back to `Craft torches` | Missing/changed SPM prevents addon or dedicated-server startup | Compile/package inspection `CONFIRMED`; absent/changed-host runtime bootstrap `UNVERIFIED` |
| Scheduler parity | Existing Goal flags, priorities, timers, inventory policy, and navigation are unchanged | A cosmetic label changes PlayerMob behavior | Diff/static MAIBS `CODE_CONFIRMED`; several-minute runtime observation `UNVERIFIED` |

Runtime visual probe: watch a crafting chain transition through planks, sticks, torches, and at
least one table-required tool. Labels may trail a step change by SPM's existing five-tick refresh
cadence. Confirm the intended recipe remains displayed during table approach. Separate Minecraft
launch approval is required.

### Route interest scoring (1.8.5)

| Check | Must happen | Must not happen | Evidence |
|---|---|---|---|
| Wilderness is never punished | An empty or unknown chunk scores exactly 0 | Absence of block entities treated as a penalty, biasing mobs away from unexplored terrain | `anEmptyChunkIsWorthNothingAndIsNeverAPenalty` `CONFIRMED` |
| Presence, not quantity | 180 chests score what one chest scores | Count-weighted attractiveness | `presenceIsScoredAndQuantityIsNotRepresentable` (set-typed API) `CONFIRMED` |
| Interest cannot defeat anti-repetition | `ROUTE_CAP` < the 100-point recent-destination penalty | A mob returning to the same rewarding chunk expedition after expedition | `interestCannotOverrideTheRecentDestinationPenalty` `CONFIRMED` |
| Interest still breaks ties | Beats a repeated heading (-35) and a visited region (-20) | An interest term too small to change any decision | `interestOutweighsTheWeakerNoveltyTermsSoItCanActuallyBreakTies` `CONFIRMED` |
| No chunk loading | Only `getChunkNow`; `null` scores 0 | Any accessor that can load or generate a chunk during scoring | Static inspection of `ChunkInterest.inspect` `CONFIRMED` |
| Bounded sampling | ≤32 entries per chunk, early exit on a saturating signal | Iterating a base's full block-entity map | `SAMPLE_LIMIT` + `onlyTheStrongestSignalSaturatesAChunk` `CONFIRMED` |
| Cold path only | Scoring runs solely inside `createExpedition` | Any call from `canUse`, `tick`, or another per-tick path | Static inspection `CONFIRMED`; profiler confirmation `UNVERIFIED` |
| Per-call cache | A chunk crossed by several candidate routes is inspected once | Eight inspections of the same chunk in one planning call | `ChunkInterest` cache inspection `CONFIRMED` |

### Travelling companions (1.8.4)

| Check | Must happen | Must not happen | Evidence |
|---|---|---|---|
| Companion eligibility | Both mobs strictly above SPM's neutral feeling (5.0) before they travel together | Neutral strangers recruited, or a one-sided regard treated as mutual | `ExplorationPolicyTest.neutralRegardIsNotFriendshipAndBothSidesMustAgree` `CONFIRMED` |
| Fails closed | An unreadable `feelingToward` disables companions and warns once | A missing SPM method silently treated as friendship | `anUnreadableFeelingIsNeverTreatedAsFriendship` `CONFIRMED` |
| No SPM state written | Feeling is only ever read through SPM's public accessor | Reflection into `FeelingLedger`, or any bond raised by this mod | Static inspection `CONFIRMED` |
| No duplicated following | Companions build their own parallel routes | A follow/group goal reimplementing `FollowLovedOneGoal` (priority 2, 64-block scan) | Static inspection `CONFIRMED` (Gate SPM-2) |
| Invitation cannot bypass safety | Cooldown, combat, sleeping, passenger and frontier checks still refuse | A mob recruited mid-fight, asleep, or inside its replan cooldown | `acceptCompanionInvitation` inspection `CONFIRMED`; runtime `UNVERIFIED` |
| Departure is visible | `exploration departed … companions=N` in `latest.log` | Company that cannot be confirmed from a log | Post-fix log **pending** |

### Path-range and standing regression (1.8.3)

| Check | Must happen | Must not happen | Evidence |
|---|---|---|---|
| Path request length | Every `createPath` target is within `maxPathStep(FOLLOW_RANGE)` of the mob | A request longer than the mob's follow range, which vanilla A* cannot expand to and always answers with an unreachable partial path | `ExplorationPolicyTest.oneHopNeverExceedsWhatThePathfinderWillExpand` `CONFIRMED`; `PathFinder.findPath` cutoff and `PlayerMobEntity` `FOLLOW_RANGE=32` read from bytecode `CONFIRMED` |
| Hop progression | A distant stage is walked as successive hops on the same line; only the waypoint completes the stage | Hop arrival advances `waypointIndex`, regenerates the heading, or clears the route | `ExplorationPolicyTest` hop interpolation `CONFIRMED`; runtime route proof `UNVERIFIED` |
| Failure releases movement | A failed plan ends the activation so local wander owns `MOVE` for the 20-tick wait | A mob standing motionless while the readout says `Exploring` | `canUse`/`canContinueToUse` inspection `CONFIRMED`; runtime visual proof `UNVERIFIED` |
| Landing selection | Same-level ground is probed before roofs and cliff tops; landings beyond 16 blocks of elevation are skipped | The 20-probe budget spent on a standable rooftop no path can reach | Static inspection `CONFIRMED`; city-world runtime proof `UNVERIFIED` |
| Outcome diagnosability | `exploration completed … hops=N` appears in `latest.log` | Only `exploration ended` lines, as in the 1.8.2 session (0 completed of 140) | Prior log `CONFIRMED`; post-fix log **pending** |

### Background decorator and exploration replan regression (1.8.2)

| Check | Must happen | Must not happen | Evidence |
|---|---|---|---|
| Antics visibility | Mimicry and bunny-hop continue as flagless decoration | `Antics` appears as an objective or claims `MOVE`/`LOOK` | Cosmetic-class regression test and constructor inspection `CONFIRMED`; runtime visual proof `UNVERIFIED` |
| Navigation completion grace | A prematurely completed path receives 20 ticks to enter arrival range | One `navigation.isDone()` observation immediately fails the stage | `ExplorationPolicyTest` boundary cases `CONFIRMED`; runtime terrain proof `UNVERIFIED` |
| Replan presentation | A retryable path failure remains visibly `Exploring` during the 20-tick replan delay | The internal retry window is presented as `Idle` or `Wandering` | State-transition inspection `CONFIRMED`; runtime visual proof `UNVERIFIED` |
| Bounded recovery | A viable retry resumes the retained expedition; final completion/abandonment logs a reason | Retrying regenerates the heading or continues forever | Existing failure-policy tests plus final-reason logging `CONFIRMED`; runtime route proof `UNVERIFIED` |

| Scenario | Must happen | Must not happen | Evidence status |
| --- | --- | --- | --- |
| Activation by walking | Two naturally completed local trips unlock exploration | A started or interrupted stroll counts as completed | Pure-policy test `CONFIRMED`; runtime `UNVERIFIED` |
| Activation by time | Sustained idle time independently unlocks exploration | Look-around activity resets idle time | Pure-policy test `CONFIRMED`; runtime `UNVERIFIED` |
| Real work | Combat, loot, farms, gathering, orders and unknown work reset activation signals | Cosmetic/look/local wander goals count as work | Static classification `CONFIRMED`; runtime `UNVERIFIED` |
| Forward route | One heading produces 2–4 forward-biased intended centres | Each waypoint chooses an independent heading | Pure geometry `CONFIRMED`; runtime `UNVERIFIED` |
| Interruption | Work preempts; resumption creates a new path to the same remaining centre | Old `Path` survives or remaining waypoints regenerate | Static state split `CONFIRMED`; runtime `UNVERIFIED` |
| Displacement | Forward displacement skips an obsolete stage; sideways displacement creates a rejoin on the original heading | Rebase changes the expedition heading | Pure-policy test `CONFIRMED`; runtime `UNVERIFIED` |
| Terrain | Exact safe standing position resolves near intended X/Z when the stage begins | Intended route is rewritten for ordinary terrain variation | Static bounds `CONFIRMED`; runtime `UNVERIFIED` |
| Path failures | Maximum three planning failures per waypoint and six per expedition; a bad non-final stage may be skipped | Infinite re-path loop | Pure-policy test `CONFIRMED`; runtime `UNVERIFIED` |
| Simulation frontier | Deduplicated path chunks and their 3×3 guards are entity-ticking; frontier ends cleanly | `hasChunk` substitutes for ticking, chunks are forced, or frontier penalizes heading/destination | Mapped API/static path `CONFIRMED`; runtime `UNVERIFIED` |
| Region memory | Reached stages are weakly visited; successful final areas are strongly penalized as destinations | Crossing a region marks it completed exploration | Static collections `CONFIRMED`; runtime `UNVERIFIED` |
| Goal compatibility | Priorities 0–7 preempt exploring at 8; local wander remains at 9 | Exploration duplicates combat, loot, farms, POIs or scanning | Static goal setup `CONFIRMED`; runtime `UNVERIFIED` |
| Scale | Planning is staggered and full validation runs only on new paths | Full corridor/guard scan repeats every travel tick | Static bounds `CONFIRMED`; 1/10/50/100-mob profile `UNVERIFIED` |

Runtime execution requires separate user approval under repository policy. Record Minecraft version,
simulation distance, mob count, route duration, interruptions, frontier outcomes, path failures and
Spark median/p95/p99 MSPT before upgrading any runtime or performance row to `CONFIRMED`.

## Mining intelligence MI-1 — gather intent consolidation

| Check | Must happen | Must not happen | Evidence |
| --- | --- | --- | --- |
| Torch need | One snapshot requests logs and coal | Separate scanners or stored demand | `GatherIntentPolicyTest` `CONFIRMED` |
| Iron need | Existing consumer deficit requests raw iron | New iron stock target | `GatherIntentPolicyTest` `CONFIRMED` |
| Diamond plausibility | Diamond intent exists at depth and not at surface | Eternal surface diamond scan | `GatherIntentPolicyTest` `CONFIRMED` |
| Craft boundary | Ready craft suppresses another gather trip | Gather competes with an immediately committable craft | `GatherIntentPolicyTest` `CONFIRMED` |
| Scan cost | One immutable intent is reused through the target scan | Full recipe/inventory evaluation for every scanned block | Static inspection `CONFIRMED`; profiler `UNVERIFIED` |

Runtime acceptance: observe a mob transition through log/coal, iron, and deep-diamond demands, then
confirm demand de-latches after craft/loot and `latest.log` contains no goal failure. Separate launch
approval is required.

## Mining wealth MI-3/MI-23 — NEED allocation

| Check | Must happen | Must not happen | Evidence |
| --- | --- | --- | --- |
| Blocking priority | Immediate, replacement, and project receive stock before reserve | Reserve consumes blocking stock | `ResourceWealthPolicyTest` `CONFIRMED` |
| Single allocation | Each carried unit satisfies at most one layer | Shortfalls double-count one stack | `ResourceWealthPolicyTest` `CONFIRMED` |
| Input safety | Negative quantities fail fast | Negative utility silently propagates | `ResourceWealthPolicyTest` `CONFIRMED` |
| Scope boundary | NEED utility contains no wealth score | Example curve values become defaults | Static inspection `CONFIRMED` |

## Mining wealth MI-4R — candidate-aware gather integration

| Check | Must happen | Must not happen | Evidence |
| --- | --- | --- | --- |
| Candidate cost | A nearby profitable resource may enter pass one | A distant negative-utility wealth candidate enters the bounded buffer | `GatherIntentWealthTest` `CONFIRMED` |
| Scan activation | Low-stock wealth can start a bounded scan | Saturated inventory causes perpetual global scanning | `GatherIntentWealthTest` `CONFIRMED` |
| Plausibility | Diamond wealth exists only at the established generation depth | Surface mobs scan for diamond solely from wealth | `GatherIntentWealthTest` `CONFIRMED` |
| Resource category | Every item in the log tag contributes to log holdings | Wealth accounting treats only oak as wood | Injectable tag-equivalent regression `CONFIRMED`; packaged tag runtime `UNVERIFIED` |
| Existing safety | Tool and exposure remain pass-one gates; protection remains pass two | Wealth bypasses existing safety filters | Static call-path inspection `CONFIRMED`; runtime `UNVERIFIED` |

Build evidence: `gradlew.bat clean build` passed with 148 tests and no failures, errors, or skips.
Runtime mining and performance remain `UNVERIFIED` pending separate launch approval.

## Looted tool equipment locations — task 19 / MAIBS-1

| Check | Must happen | Must not happen | Evidence |
| --- | --- | --- | --- |
| Ownership | Usable diamond pick in backpack, main hand, or off hand satisfies diamond tier | Off-hand loot triggers redundant iron/diamond progression | `ToolTierPolicyTest`, `GatherIntentPolicyTest` `CONFIRMED` |
| Broken tool | Fully broken off-hand pick is rejected | Broken item suppresses replacement | `ToolTierPolicyTest` `CONFIRMED` |
| Draw | Best off-hand tool swaps losslessly to main hand before mining | Tool or previous held item is dropped/deleted | Static `ToolBox` inspection `CONFIRMED`; runtime `UNVERIFIED` |
| Goal composition | Existing gather/craft/smelt/explore policies receive one three-location view | A second loot/equipment Goal competes with SPM | Static call-path inspection `CONFIRMED` |

Build evidence: clean build passed 181 tests. Runtime loot placement, combat interruption/re-arm,
animation, save/reload, and multi-mob behavior remain `UNVERIFIED`.

## MI-14C3 controlled-descent progress lease

| Check | Must happen | Must not happen | Evidence |
| --- | --- | --- | --- |
| Started then stuck | Revoke `NO_PROGRESS` after more than 400 admissible ticks | Eternal ACTIVE assignment | `MiningExecutionC3Test.c3A_*`, C3-F6 `CONFIRMED` (unit); runtime `UNVERIFIED` |
| Combat pause | Exclude the exact TEMPORARY episode from progress age | Timeout during/promptly after a short combat interruption | `c3B_*` `CONFIRMED`; C1's separate 1200-tick combat grace remains |
| Observable refresh | Successful planned break and completed step call the progress marker | Goal tick, path replan, rejection, or executor start counts as progress | C3-C test + `ControlledDescentGoal` call-site inspection `CODE_CONFIRMED`; runtime `UNVERIFIED` |
| Never started | C1 start lease remains the only expiry clock | C3 double revoke or invented progress | `c3D_*` `CONFIRMED` |
| Contention pause | A completed CONTENTION episode contributes exact paused ticks | Immediate C3 expiry when a required-flag holder yields | `c3E_*` `CONFIRMED`; runtime `UNVERIFIED` |
| Protected safety pause | Observable safety/recovery pauses start + progress clocks without mining-side expiry | Safety is preempted or ages C1/C3 | C3-F1/F2/F4/F5 `CONFIRMED` (unit); runtime `UNVERIFIED` |
| Player authority | Stay anchor prevents assignment; commanded action prevents/revokes `PLAYER_ORDER` | Zombie assignment or revoke→reassign loop | C3-F3 + admission call-path `CODE_CONFIRMED`; runtime `UNVERIFIED` |
| Complete flag set | LOOK-only eating is a bounded blocker for MOVE+LOOK descent | MOVE-only scan returns `NONE` | C3-F7 `CONFIRMED` (unit); real scheduler order `UNVERIFIED` |
| Persistence | v4 round-trip preserves progress/pre-start pauses; v2/v3 migration invents neither | Reload resets or invents progress | C3 persistence/migration tests `CONFIRMED` |

Build evidence: `gradlew.bat clean build` passed 321 tests with no failures/errors. Runtime
falsification requires separate launch approval; see `.superpowers/sdd/archive/task-30-report.md`.

**MAIBS-1 static integration result: PASS — BEHAVIORALLY_PLAUSIBLE.** R1 makes the progress timeout
reachable before the 2400-tick total budget, maps protected owners explicitly, and checks the full
required flag set. Runtime remains `UNVERIFIED`:

| Check | Must happen | Must not happen | Current |
| --- | --- | --- | --- |
| Integrated active stall | Denied planned break reaches `NO_PROGRESS` before total-budget end | C3-A passes only as an isolated policy test | **PASS — C3-F6 CODE_CONFIRMED; runtime UNVERIFIED** |
| Protected owner before start | Stay/order either prevents assignment or produces explicit blocker | Unstarted assignment authorized forever while required flags are occupied | **PASS — C3-F2/F3 CODE_CONFIRMED; runtime UNVERIFIED** |
| Protected owner after start | Escape/recovery/order has explicit pause/revoke semantics | C3 ages under blocker `NONE` while executor cannot run | **PASS — C3-F1/F4/F5 CODE_CONFIRMED; runtime UNVERIFIED** |
| LOOK-only owner | Eating blocks full MOVE+LOOK admission and later releases it | MOVE-only scan authorizes descent | **PASS — C3-F7 CODE_CONFIRMED; runtime UNVERIFIED** |

Runtime probe must cover: long environmental escape, never-started recovery, persistent
StayNear/player command, protected→combat→protected, an admissible >400-tick stall, and priority-3
LOOK-only `EatFoodGoal` blocking then releasing a never-started MOVE+LOOK descent.
