# SPM Scavenger test matrix

## GAO-8B Task 42A causal trace — current 1.9.4 artifact

| Check | Must happen | Must not happen | Evidence |
| --- | --- | --- | --- |
| Identity | Every evaluation has a positive monotonic `decisionId`; a later intent has a separate UUID and carries the origin id | Fresh scores inherit a previous/null intent identity | `decisionIdExistsBeforeAndRemainsDistinctFromIntentId` + `supersedingDecisionNeverInheritsPreviousIntentIdentity` `CONFIRMED` |
| Full cause | Every candidate preserves all ten utility components and explicit suppression | Inspector infers causality from only total/preference/repetition or current mood | `preservesEveryUtilityComponentWithoutParsingStrings` + adoption-gate test `CONFIRMED` |
| Lifecycle | Origin record receives SELECT → INTENT → ADOPT → EXECUTOR/CLAIM → terminal lifecycle and exact cause | Handoff/terminal attaches to the newest or previous unrelated decision | `oneDecisionCorrelatesFullScoresIntentExecutorAndTerminal` + rest observer-order suite `CONFIRMED` |
| Learning receipt | Explore/Rest terminal records exact outcome/cause and actual activity/place/environment deltas before authority closes | UI infers learning from later memory or treats a protected interrupt as dislike | `restLearningReceiptAttachesToTheOriginatingDecision` + `protectedRestInterruptionRecordsNoInventedLearning` `CONFIRMED`; Explore runtime terminal `UNVERIFIED` |
| Abstain/hold/block | Below-threshold, commitment, switch margin, no executor, mandatory authority, combat, disabled and frozen paths remain explicit | No-intent decisions look like unexplained inactivity | focused `DiscretionaryActivityDirectorTest` dispositions `CONFIRMED` |
| Atomic retention | Ring holds at most 24 decisions, preserves a live origin while completed records exist, then evicts its whole record after terminal | Entry-by-entry eviction leaves only scores or only terminal | `evictionProtectsLiveOriginThenRemovesItAtomicallyAfterTerminal` `CONFIRMED` |
| Behavior parity | Scoring, thresholds, intent ownership, Goal flags/priorities, navigation and activity choice are unchanged | Observation changes physical PlayerMob behavior | diff/static MAIBS `CODE_CONFIRMED`; Minecraft runtime `UNVERIFIED` |
| Lifetime/performance | Context unload/death/server-stop paths clear the trace; per-mob history remains bounded | Per-intent global map, retained entity/world, or unbounded history | production lifecycle call-path + hard cap `CODE_CONFIRMED`; heap/tick profile `UNVERIFIED` |

Static MAIBS: `PASS — BEHAVIORALLY_PLAUSIBLE`. The code changes only evidence capture and
correlation; no physical feedback-loop stage changes. Full clean build: 628 tests, zero
failures/errors/skips. No Minecraft runtime launch was authorized.

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

## SCR-1 shelter commitment resume

Runtime datapack: `test-datapacks/shelter-commitment/` (namespace `spm_shelter`). User authorization
for these exact runtime scenarios was granted on 2026-08-11; visual/log evidence is still pending.

| Scenario | Setup | Must happen | Must not happen | Evidence |
| --- | --- | --- | --- | --- |
| SCR-1A occupied bed | `spm_shelter:scenario/occupied_bed` | Same commitment pauses for deliberate door use, replans, crosses, and completes at the covered interior | Repeated open/close/reselect loop | `USER_RUNTIME_CONFIRMED` after repair; code/unit/build `CONFIRMED` |
| SCR-1B free bed | `spm_shelter:scenario/free_bed` | Bed claim survives the short door interruption and the mob sleeps | Opposite bed halves or a rescan steal the same bed | Canonical claim test `CONFIRMED`; runtime `UNVERIFIED` |
| SCR-1C dawn/authority | Change to day or introduce combat/command during interruption | Commitment cancels and the higher authority owns behavior | Old shelter resumes after invalidation | Static policy/call path `CONFIRMED`; runtime `UNVERIFIED` |
| SCR-1D invalid/failing route | Break destination or make repaths fail | Destination cancels/rejects after bounded failure | Immortal retry or reset budget | Unit/static `CONFIRMED`; runtime `UNVERIFIED` |
| SCR-1E unload/death | Unload or kill owner mid-commitment | Per-entity commitment and static claim release | Reload resurrects stale shelter authority | Event wiring/static claim tests `CONFIRMED`; runtime `UNVERIFIED` |

Static gates: 13 focused shelter tests and the full 639-test suite pass with no failures, errors, or
skips. `clean build` passed. Final JAR contains the commitment/policy classes and excludes the
temporary datapack. Static MAIBS: `PASS — BEHAVIORALLY_PLAUSIBLE`; runtime gate remains open.

Runtime attempt evidence (2026-08-11): no test world or SPM runtime JAR exists in this project's
`run/` directory, and `D:/Minecraft/Instances` is empty in the current workspace. Building the
pinned SPM reference for a dedicated-server fixture failed during Gradle configuration at
`build.gradle.kts:153` (`file://D:\\.../repo` is an invalid Windows file URI). The reference was not
edited. No Minecraft process launched; SCR-1A/B remain `UNVERIFIED`.

## SCR-2 shelter interior and capacity intelligence

Runtime datapack: the same `test-datapacks/shelter-commitment/` kit now contains SCR-2A/B/C.
Minecraft was not launched under this authorization, so all physical classification/distribution
outcomes remain `UNVERIFIED`.

| Scenario | Setup | Must happen | Must not happen | Evidence |
| --- | --- | --- | --- | --- |
| SCR-2A one mob | `spm_shelter:scenario/interior_one` | Reachable interior outranks the nearer exterior eave | Covered porch is treated as equivalent to an interior room | Tier/unit/static `CONFIRMED`; runtime `UNVERIFIED` |
| SCR-2B four mobs | `spm_shelter:scenario/capacity_four` | Four commitments acquire separated interior sites | Same/adjacent standing cells or entrance pile | Reservation unit/static `CONFIRMED`; runtime `UNVERIFIED` |
| SCR-2C over capacity | `spm_shelter:scenario/over_capacity` | Interior capacity fills before separated lower-tier fallback | Porch displaces available room capacity or all mobs converge | Policy static `CONFIRMED`; runtime `UNVERIFIED` |
| SCR-2D unreachable leaders | Four highest-tier paths fail | At most four probes now; later scans skip recent failures and advance | Same four positions suppress fallback forever | Rejection-ledger unit/static `CONFIRMED`; runtime `UNVERIFIED` |
| SCR-2E lifecycle | Suspend, dawn, unload, death, server stop, expiry | Matching commitment retains/refreshes then conditionally releases its reservation | Old commitment releases newer ownership or static map grows forever | Unit/static `CONFIRMED`; heap trend `UNVERIFIED` |
| SCR-2R doorway depth | Closed-door house with deeper free room cells | After crossing, within-tier depth prefers the exact deeper reservation | Distance beats deeper equal-tier cells or two-block arrival tolerance completes at threshold; SCR-2R3 supersedes the old door-adjacent semantic demotion | User reproduced old failure; policy/contract tests `CONFIRMED`; repaired runtime `UNVERIFIED` |
| SCR-2R2 structural evidence | House plus nearer tree/eave; log-walled variant | Full-height non-leaf walls and structural roof classify the house above foliage fallback; logs remain eligible | Leaves create room walls or a blanket log blacklist breaks cabins | Policy + source-contract tests `CODE_CONFIRMED`; runtime `UNVERIFIED` |
| SCR-2R2 current satisfaction | Mob begins in a valid room with a free bed across exposed ground | Current interior is adopted; only a bed route with at most two exposed nodes may upgrade it | Global shelter scan makes the mob leave a safe house | Route-budget/policy/source-contract tests `CODE_CONFIRMED`; runtime `UNVERIFIED` |
| SCR-2R2 return | Arrived standing shelter, then benign moving social interruption | Rest becomes inactive-but-live; same commitment/reservation enters bounded `RETURNING` and resumes on exact re-arrival | Sticky historical arrival, duplicate rest opening, or reset/unbounded return budget | Commitment/rest/source-contract tests `CODE_CONFIRMED`; runtime `UNVERIFIED` |
| SCR-2R2 fallback upgrade | Mob arrives at tree/eave/deep-cover fallback; better tier later becomes reachable | Every 200 ticks at most, only a strictly higher tier can atomically replace the fallback | Per-tick scan, equal-tier churn, or losing the old reservation when replacement fails | Static call-path + contract test `CODE_CONFIRMED`; runtime `UNVERIFIED` |
| SCR-2R3 busy door request | SPM CLOSE operation is active when its flagless passage Goal detects a closed door/path | Optional host guard refuses the false start; after busy/recovery clears, stock SPM may retry normally | OPEN is silently discarded while objective still says `Using door`; addon opens/closes doors itself | Pinned SPM `4b80b5e849` source + optional-Mixin contract/package inspection `CODE_CONFIRMED`; runtime `UNVERIFIED` |
| SCR-2R3 tiny interior | One-room village house where usable cells are within one block of the door | Full structural evidence remains `INTERIOR_ROOM`; deeper equal-tier cells still rank first | Door clearance converts a real tiny room to porch | Policy tests `CODE_CONFIRMED`; runtime `UNVERIFIED` |
| SCR-2R3 mid-route capture | Lower-tier tree/eave commitment crosses a structural room | At most every 10 ticks, current room atomically replaces old trip and reaches ARRIVED; navigation stops | Mob walks back outside to complete the worse destination or loses old ownership if capture fails | Policy/source-contract/full-suite `CODE_CONFIRMED`; runtime `UNVERIFIED` |
| SCR-2R4 arrived house hold | Door-closing mob reaches exact interior settlement, then SPM starts its close-behind operation | Actual door action finishes without the scheduler wrapper evicting ARRIVED shelter; `Seek shelter` keeps MOVE until dawn | `Idle`/wander gains MOVE, mob exits, or the door helper is suppressed during approach | User reproduced pre-fix loop; authority/Mixin/full-suite `CODE_CONFIRMED`; repaired runtime `UNVERIFIED` |
| SCR-2R4 authority release | Arrived shelter is displaced, canceled by dawn/combat/command, unloaded, killed, or server stops | R5 supersedes displacement release: RETURNING retains the night hold but permits the door wrapper; actual cancellation and owner removal release | Stale hold suppresses SPM door scheduling after shelter no longer owns the night | R5 registry lifecycle/unit/source-contract `CODE_CONFIRMED`; runtime/heap trend `UNVERIFIED` |
| SCR-2R5 semantic authority | Arrived shelter with active rest claim and Opinion strongly preferring Explore | Observer reports `SHELTER_HOLD` and `resting=true`; discretionary eligibility is denied | Arrived shelter reports optional `REST` or issues voluntary Explore yield | Taxonomy/observer/eligibility tests `CODE_CONFIRMED`; runtime inspector `UNVERIFIED` |
| SCR-2R5 work envelope | House near logs, drops, crops, furnace, campfire and dark torch site | Settled/returning authority blocks all voluntary displacing addon and pinned host executors until dawn | Temporary ownership gap becomes Gather/Idle/Explore/loot/follow travel | Shared guard/Mixin/full-suite `CODE_CONFIRMED`; runtime `UNVERIFIED` |
| SCR-2R5 temporary resume | Settled mob is benignly displaced and needs a closed door to return | Hold becomes RETURNING, same commitment/reservation survives, door helper may operate, exact arrival restores SETTLED | Hold releases to work, or retained hold suppresses the needed door helper | Phase/correlation/door-contract tests + post-GREEN MAIBS `CODE_CONFIRMED`; runtime `UNVERIFIED` |
| SCR-2R5 target provenance | Safe sheltered hungry mob targets cow; separate runs use recent attacker, nearby visible hostile, and player attack order | Cow/unknown target stays sheltered; attributable danger/order overrides | Every non-null target is treated as emergency, or real self-defence is blocked | Pure provenance tests and pinned SPM HuntForFood source `CODE_CONFIRMED`; modded-hostile compatibility runtime `UNVERIFIED` |
| Shelter commitment authority continuity | Gather is active at dusk; SeekShelter adopts a reachable reserved house, then a finite door/helper interruption occurs before arrival | Authority phase is `APPROACHING`; Gather yields and cannot restart; door helper runs; same commitment resumes and becomes `SETTLED` | Work and shelter alternate because authority exists only after arrival, or approach authority suppresses the door needed to enter | User runtime report + authority/guard focused tests `CODE_CONFIRMED`; tested artifact identity and repaired runtime `UNVERIFIED` |
| SPM door passage — already open | Active navigation path/horizontal collision latches a wooden door whose OPEN property is already true | Stock navigation continues with no new deliberate OPEN episode or `Using door` animation | Idempotent OPEN runs for ten ticks and stops movement while changing no block | Pinned SPM/vanilla bytecode + pure/Mixin contract `CODE_CONFIRMED`; runtime `UNVERIFIED` |
| SPM door passage — crossing clock | Closer starts a deliberate 10-tick OPEN operation with a 20-tick passage budget | Operation ticks do not decrement passage budget; navigation receives the complete bounded crossing window afterward | Half the budget expires while MOVE is deliberately stopped | Source/timing audit + Mixin contract `CODE_CONFIRMED`; runtime `UNVERIFIED` |
| SPM door passage — terminal semantics | Door opens but mob does not cross before bounded timeout; separate scenario crosses door plane | Timeout leaves door open for path/repath recovery; actual crossing permits at most one close-behind | Timeout closes in front and manufactures reopen loop, or close happens without passage | Pure policy/Mixin contract/MAIBS `CODE_CONFIRMED`; physical runtime `UNVERIFIED` |
| SPM door passage — physical encounter identity | Navigation replaces its `Path` while the same mob remains at the same door and approach side | Replan remains the same encounter; completed encounter cannot reopen; incomplete encounter gets at most one recovery retry; physical separation/new door creates a generation | Exact `Path` identity treats a replan as a fresh OPEN episode or retry remains unbounded | Pure identity/retry/wrapping-generation tests + Mixin source contract `CODE_CONFIRMED`; runtime U-turn/multi-mob behavior `UNVERIFIED` |

SPM Door Passage Episode static gates: all 681 tests pass with zero failures/errors/skips and
`clean build` passes. The remapped JAR packages `DoorPassagePolicy` and the optional
`PlayerMobDoorGoalBusyMixin`; bytecode inspection confirms the protected vanilla door fields and
physical encounter policy are present without a navigation-`Path` lookup. Runtime Mixin application and physical crossing remain
`UNVERIFIED`. Final remapped JAR SHA-256:
`DB403E27F418303E3D800495C055477D32E02FC50828AA1848C5731ABE9187CF`.

Static gates after SCR-2R: 24 focused `Shelter*Test` tests and the full 652-test suite pass with zero failures,
errors, or skips. Selection is capped at 28 semantic evaluations and four path probes per scan;
failed-candidate state is per-goal and capped at 16, while the shared reservation registry is keyed
by owner UUID with production expiry/cancel/unload/death/server-stop eviction. `clean build` passes;
artifact `build/libs/spmscavenger-1.9.4.jar`, SHA-256
`4347449A866D88695E01E2A867C4467F1DB68A04C68B4DB034888740809C3552`, contains the SCR-2/R
classes and excludes the test datapack. Static MAIBS: `PASS — BEHAVIORALLY_PLAUSIBLE`; physical
outcome remains open.

SCR-2R2 static gates: 660 total tests pass with zero failures/errors/skips; focused `Shelter*` and
rest-coordinator tests pass; `clean build` passes. Semantic evaluation remains capped at 28, door
seeds consume at most four of those slots, path probes remain capped at four, discovered-door
retention is capped at eight per scan, and lower-tier upgrade scans run no more often than every
200 ticks. Static MAIBS: `PASS — BEHAVIORALLY_PLAUSIBLE`; Minecraft physical behavior remains
`UNVERIFIED` because no runtime launch was authorized for this implementation. Final remapped JAR:
`build/libs/spmscavenger-1.9.4.jar`, SHA-256
`78A95368046A66C06FD67D7D842BB41D3524BECB43A00F738BE050F984744725`.

SCR-2R3 static gates: 663 tests pass with zero failures/errors/skips and `clean build` passes. Final
JAR packages `PlayerMobDoorGoalBusyMixin` in the common optional-host Mixin list, uses `@Pseudo`
and `require=0`, and contains no SPM classes or test datapack. Absent-host and changed-host startup
remain runtime `UNVERIFIED`. The guard observes only SPM's public
`isOperatingDoor()` / `isRecovering()` state and never operates a door. Static MAIBS:
`PASS — BEHAVIORALLY_PLAUSIBLE`; runtime remains `UNVERIFIED`. Final remapped JAR SHA-256:
`DE8516AF3D52A1AD4D2E00713A61A28D28F89B4AB3A692451DF021FD1436DC7D`.

SCR-2R4 static gates: 667 tests pass with zero failures/errors/skips and `clean build` passes. The
final JAR contains `DoorOperationShelterHoldMixin` plus `ShelterNightAuthority`, contains no SPM
classes or runtime datapack, and retains optional-host `@Pseudo` / `require=0` fallback. Static
MAIBS: `PASS — BEHAVIORALLY_PLAUSIBLE`; the arrived-house/close-behind physical loop remains
runtime `UNVERIFIED`. Final remapped JAR SHA-256:
`0DF060DD6E1733A06ECB2DC172CBBF7F1C230954D612E883255D0D0BD3ED1E9D`.

SCR-2R5 static gates: 676 tests pass with zero failures/errors and `clean build` passes. Final JAR
packages `ShelterActivityEnvelope`, correlated `ShelterNightAuthority` phases,
`ShelterThreatPolicy`, and optional host travel/combat Mixins. No Goal priority, world scan, path
probe, or activity-selection rule was added. Static MAIBS: `PASS — BEHAVIORALLY_PLAUSIBLE` after
repairing the discovered RETURNING/door-wrapper deadlock. Runtime behavior, optional-host Mixin
application, and RET-1 heap trend remain `UNVERIFIED`. Final remapped JAR SHA-256:
`53475CCC0B2025012572492C07443E6609C070BFA61A52F284D499DA6C01BF48`.

Shelter Commitment Authority Continuity follow-up: the registry now publishes an `APPROACHING`
phase immediately after successful path admission plus reservation, then transitions to `SETTLED`
or `RETURNING`. The same O(1) envelope therefore covers the complete live commitment rather than
only post-arrival state. Runtime confirmation requires installing and hashing the fresh final JAR;
the binary used for the triggering session could not be recovered from the instance after launch.
All 677 tests and `clean build` pass with zero failures/errors/skips. Final remapped JAR SHA-256:
`913C2F65192E8EF9937BBD7A93452ECE3F149584192B151612D0B33323892F38`.

### GAO-10 Task 44C-R — candidate identity (static)

| Scenario | Must happen | Must not happen | Evidence |
| --- | --- | --- | --- |
| Executable SOCIAL wins | Pending intent and causal trace bind the exact scored subject | Subject-less `pending()` exception once the executor becomes present | `SocialCandidateBindingTest` `CODE_CONFIRMED` |
| Bob pending, Alice wins later | New Alice intent/key replaces Bob | Activity-only SOCIAL retention silently preserves Bob | `SocialCandidateBindingTest` `CODE_CONFIRMED` |
| Bob running/continuable, Alice adoption blocked | Alice is suppressed as non-adoptable | Alice borrows Bob's continuation exception | `SocialCandidateBindingTest` `CODE_CONFIRMED` |
| Bob → Alice switch | Yield challenger key is SOCIAL/Alice; Bob remains the named incumbent until safe yield | Yield transaction aliases both subjects as SOCIAL | `SocialCandidateBindingTest` `CODE_CONFIRMED` |
| EXPLORE/REST | Existing singleton identity and behavior remain unchanged | A losing SOCIAL subject attaches to non-social intent | Focused director tests + full suite `CODE_CONFIRMED` |

Focused tests and `clean build`: **800 tests, 0 failures, 0 errors, 0 skipped**. Static MAIBS:
`PASS — BEHAVIORALLY_PLAUSIBLE`. Task 44D physical execution and runtime target correlation remain
`UNVERIFIED` and unimplemented.

### GAO-10 Task 44D — FriendlyGreet executor binding (static)

| Scenario | Must happen | Must not happen | Evidence |
| --- | --- | --- | --- |
| Live SPM admission names Bob | Exact pending/running SOCIAL/Bob intent creates one generation-bound admission | Alice or activity-only SOCIAL borrows Bob authority | `SocialExecutionBindingTest` `CODE_CONFIRMED` |
| GoalSelector starts greet | Exact binding adopts/marks the same intent RUNNING; observer reports `DISCRETIONARY_SOCIAL` | Admission alone or unrelated native greet gains Opinion classification | Binding/taxonomy tests `CODE_CONFIRMED` |
| `tickGift` changes to FETCH | No completion marker | Any phase write is treated as success | exact `Phase.DONE` target + negative source test `CODE_CONFIRMED` |
| Host reaches a DONE branch | One completion marker; stop terminalizes SUCCEEDED and emits one social terminal | `stop()` alone or repeated marker produces duplicate positive learning | lifecycle tests `CODE_CONFIRMED`; runtime `UNVERIFIED` |
| Combat/command/world interruption | Missing DONE closes protected/neutral and releases binding | Interruption teaches dislike or calls `canContinueToUse`/`reactionToward` | emitter/source tests `CODE_CONFIRMED` |
| Opinion disabled | Redirect returns SPM's original target; SOCIAL is not issued | Existing SPM greeting behavior is suppressed | source contract + full suite `CODE_CONFIRMED`; runtime `UNVERIFIED` |
| Unload/death/server stop | Binding and admission observation are released | Per-mob runtime registry grows across entity/server lifetime | production hooks + registry tests `CODE_CONFIRMED`; heap trend `UNVERIFIED` |

Focused tests and full `clean build`: **807 tests, 0 failures, 0 errors, 0 skipped**. Final remapped
JAR packages the binding registry and optional common Mixin. Static MAIBS:
`PASS — BEHAVIORALLY_PLAUSIBLE`; target choice, bow/gift animation, interruption, optional-Mixin
application, and learning in a real world remain runtime `UNVERIFIED`. SHA-256:
`A9803703B020D8F2DD739BB11654AAE5B4809B0AB3412A57ECD17C286B4DEDE8`.

#### Task 44D-R1 — causal completion ownership

| Scenario | Must happen | Must not happen | Evidence |
| --- | --- | --- | --- |
| Mandatory authority ends SOCIAL/Bob before host DONE | DONE rejects/clears the stale binding | stale physical host progress becomes positive Bob learning | `SocialExecutionBindingTest` `CODE_CONFIRMED` |
| Exact Bob intent owns host at DONE, authority changes afterward | completed marker remains historical until stop | observer reports stale binding as currently running or erases valid history | `SocialExecutionBindingTest` `CODE_CONFIRMED` |
| FriendlyGreet admission vs continuation | admission is `SOCIAL_REFLEX`; exact running continuation is `DISCRETIONARY_SOCIAL` | shelter and observer invent separate binding semantics | binding + Mixin contract tests `CODE_CONFIRMED` |

Focused tests, full suite, and clean build: **809 tests, 0 failures/errors/skips**. Packaged JAR
inspection: pass. Static MAIBS: `PASS — BEHAVIORALLY_PLAUSIBLE`; physical SPM execution remains
runtime `UNVERIFIED`. SHA-256:
`DA017011A280DB38F390890B8104E64E59DBB213A33BA1BADBE2175C1430BF5C`.


#### V2-DEF-001 — pending human trade reputation (REPAIRED — runtime UNVERIFIED)

Recorded from the P0-2 source review, repaired separately as its own task (2026-08-17). Full defect record and gate:
`docs/porting/KNOWN_DEFECTS.md`.

| Scenario | Must happen | Must not happen | Evidence |
| --- | --- | --- | --- |
| Player trade, then PlayerMob trade, then villager level-up | player keeps `ReputationEventType.TRADE` | mob trade nulls `lastTradedPlayer` and drops it | `TradeAttributionPolicyTest` `CODE_CONFIRMED`; runtime gossip read `UNVERIFIED` |
| PlayerMob trades a villager no human has traded | field stays `null`, never written | the mob is credited with `TRADE` reputation | `TradeAttributionPolicyTest` `CODE_CONFIRMED` |
| A newer attribution appears during the notify | the newer value stands | a saved value is restored over it | `TradeAttributionPolicyTest` `CODE_CONFIRMED` |


#### V2-DEF-002 — discretionary displacement of pending progression (OPEN)

Runtime-observed during step 7B; deliberately **not** repaired inside that scenario. Full record and
gate: `docs/porting/KNOWN_DEFECTS.md`.

| Scenario | Must happen | Must not happen | Evidence |
| --- | --- | --- | --- |
| Unresolved progression demand, route owner not yet executable | EXPLORE refused admission | ~150-block expedition leaves the trade radius | `OPEN` |
| Demand unsatisfiable | discretionary activity resumes | mob frozen guarding an unservable demand | `OPEN` |


#### V2-DEF-003 — consumer-accurate gather frontier (REPAIRED — runtime CONFIRMED)

Full record: `docs/porting/KNOWN_DEFECTS.md`. `ConsumerAcquisitionFrontierTest` (7 rows) plus the
corrected `GatherIntentPolicyTest` suppression pair.

| Scenario | Must happen | Must not happen | Evidence |
| --- | --- | --- | --- |
| iron wanted, sticks sufficient | `RAW_IRON` required | `LOGS`/`COBBLESTONE` required | `CODE_CONFIRMED` |
| already stone, pursuing iron | cobble is wealth only | stock target carries mandatory authority | `CODE_CONFIRMED` |
| unrelated log nearby | iron search reaches its own conclusion | a log scan stands in for the iron route | `RUNTIME_CONFIRMED` by `V2-DEF-003c-R1`: `UNKNOWN/FEASIBLE -> PUBLISHED SEARCH_COMPLETED_EMPTY -> INFEASIBLE` |

#### V2-DEF-003c-R1 — published handoff controls scheduling (RUNTIME CONFIRMED)

Runtime evidence: user-captured `[TE3] step-7A autonomous readout`, 2026-08-19. Full causal record:
`docs/porting/KNOWN_DEFECTS.md`.

| Scenario | Must happen | Must not happen | Evidence |
| --- | --- | --- | --- |
| Gather route remains unresolved/feasible | gather keeps the deliberate-work slot | trade displaces a route that has not concluded | `RUNTIME_CONFIRMED`: `ROUTE iron_ingot UNKNOWN/FEASIBLE -> gather keeps ownership` |
| Empty covered scan publishes the handoff | `GATHER PUBLISHED -> GATHER YIELDING -> ROUTE INFEASIBLE -> PLAN #1 TE` | optional gather work consumes the reserved result slot | `RUNTIME_CONFIRMED`: first trade `22 oak_log -> 1 emerald`, logs `320->298`, emeralds `0->1` |
| Autonomous funding and purchase chain | 12 TE sells fund the exact vanilla Toolsmith purchase; iron pickaxe enters backpack | stop after funding, hoard emeralds, or leave route evidence retained | `RUNTIME_CONFIRMED`: `plans=13 (TE 12)`, `revals=13`, `trades=13`; emeralds `12->0`, iron pickaxe `0->1`, `routeEvidence tracked=0` |

`episodes=0` was also captured. It is a readout fact, not relationship-learning acceptance evidence
for this scenario.

## D-VR-084 / task-52 — MandatoryOwnership pending-claim authority (2026-08-20)

Canonical design: `plans/RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md` D-VR-084; brief
`.superpowers/sdd/task-52-brief.md` (amended QW-V3-1). Full clean build: **1354 tests, 0 failures/errors/skips**;
artifact `build/libs/spmscavenger-1.11.0.jar` SHA-256 `8AE2395B12FFDA7F02C636D0B0B87731C86788F42662DBDA781F9107E7F21925`.
Automated behavioural acceptance only; the runtime witness is deferred to the batched V3 campaign (AV-1).

| # | Scenario | Must happen | Must not happen | Evidence |
| --- | --- | --- | --- | --- |
| 1 | RUNNING mandatory work | discretionary **denied** | eligible while SCAVENGE_WORK runs | `MandatoryOwnershipTest.scenario1_*` `CONFIRMED` |
| 2 | LIVE pending claim | discretionary **denied** with `MANDATORY_PENDING_CLAIM` | claim ignored while live | `scenario2_*` `CONFIRMED` |
| 3 | demand exists, nobody claims | discretionary **allowed** | demand existence blocks | `scenario3_*` `CONFIRMED` |
| 4 | claim expires without progress | discretionary **becomes allowed** | expired claim still blocks | `scenario4_*` + `simulationA_expiryVariant*` `CONFIRMED` |
| 5 | same demand after expiry | claim does **NOT** self-renew | republish mints a successor | `scenario5_*` `CONFIRMED`; negative-control mutation verified (guard removed → test fails) |
| 6a | executor genuinely started | same identity may later use the NEXT generation | unrelated event mints | `scenario6a_*` `CONFIRMED`; producer-side mint only at `EXECUTOR_STARTED` release with live claim |
| 6b | canonical route identity genuinely changed | exactly one distinct successor may publish | repeats of the new pair accepted | `scenario6b_*` `CONFIRMED` |
| 6c | same identity + merely fresher observation | no successor claim in task-52 | fresh scan reauthorizes | `scenario6c_*` `CONFIRMED` |
| 7 | owner abandons or satisfies | claim released immediately | stale claim blocks | `ordinaryReleaseDeletesTheClaim` `CONFIRMED` |
| 8 | `VILLAGE_TRADE` running | discretionary **denied** | trade reads as discretionary-eligible | `scenario8_*` + `WiringTest.villageTradeBlocksDiscretionaryChoice` `CONFIRMED` |
| 9 | unknown running goal | **fail closed** | unknown reads as eligible | `scenario9_*` `CONFIRMED` |
| 10 | future owner forgets to publish | pending side **fails open** | missing publish blocks | `scenario10_*` + status line 3 in KNOWN_DEFECTS `CONFIRMED` |
| 11 | unload / dimension transfer / server stop | runtime claim disappears | stale claim survives | `scenario11_*` + `SpmScavenger` eviction wiring `CONFIRMED` |
| 12 | restart | no stale `MandatoryOwnership` resurrects | persisted claim | `scenario12_*` + runtime-only store `CONFIRMED` |

### Temporal simulations

| ID | Timeline | Must happen | Must not happen | Evidence |
| --- | --- | --- | --- | --- |
| A | T0 demand → T1 CLAIM → T40 progress → T80 impossible → T120 abandon → T121 EXPLORE legal | claim blocks until abandon, then permission returns | abandoned claim lingers | `simulationA_servableDemandAbandonsAndDiscretionaryResumes` `CONFIRMED` |
| B | T0 impossible demand → T1 no owner accepts → T2 no claim → T3 EXPLORE legal → still legal T400 | unservable demand never freezes the mob | demand existence blocks forever | `simulationB_unservableDemandNeverFreezesDiscretionary` `CONFIRMED` |

### Producer-side negative controls (Gather)

| # | Control | Expected | Evidence |
| --- | --- | --- | --- |
| P1 | same consumer/material + route across TTLs/scan intervals | no new generation | registry refuses same-route same-generation republish; producer counter advanced only at `EXECUTOR_STARTED` with live claim |
| P2 | increment generation per scan | **must fail** | generation field minted only in `start()` guarded by live claim; structural wiring test |
| P3 | explicitly authorized semantic episode input changes | exactly one distinct successor | `scenario6b_*` `CONFIRMED` |
| P4 | wealth-only intent, no canonical `MaterialDemand` | no pending claim | `ownedMandatoryRoute` empty → no publish (code path); `WiringTest.gatherPublisherUsesFactoredCanonicalRoute` |
| P5 | responsibility accepted while scan clock refuses | claim live immediately; EXPLORE cannot be admitted | publish call textually above `scanClock.claim(now)` in `canUse`; `WiringTest.gatherPublishesBeforeScanClockClaim` |
| P6 | repeated ABANDONED → unchanged demand | no further accepted claim | `routeHandoffAndAbandonDoNotAdvanceGeneration` `CONFIRMED` |
| P7 | ROUTE_HANDED_OFF → unchanged demand | no further accepted claim | exposed in task-52 (release site in `canUse` handoff/yield paths); `CONFIRMED` |

### Structural wiring (silent-revert protection)

| Contract | Assertion |
| --- | --- |
| director consumes the shared authority | `DiscretionaryActivityDirector` calls `MandatoryOwnership.evaluate`; no direct `DiscretionaryEligibility.isDiscretionaryEligible(` |
| running arm delegates | `MandatoryOwnership.java` calls `DiscretionaryEligibility.isDiscretionaryEligible` + `invalidationForObservation` |
| `VILLAGE_TRADE` blocks discretionary | `DiscretionaryEligibility.java` `blocksDiscretionaryChoice` contains `VILLAGE_TRADE` |
| cause exists | `InvalidationCause` contains `MANDATORY_PENDING_CLAIM` |
| RET-1 eviction wired | `SpmScavenger` releases on unload/death, clears on server stop; `removePermanently` on destroy |
| runtime-only | `PerMobSavedData` contains no `MandatoryOwnership` reference |
