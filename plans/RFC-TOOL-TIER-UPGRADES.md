# RFC: Tool tier upgrades (wood → stone → iron → diamond)

## RFC Identity

| Field | Value |
| --- | --- |
| **Project root** | `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric` |
| **Evidence** | `settings.gradle`, `gradle.properties`, `src/`, `fabric.mod.json` |
| **Mod** | Social Player Mobs: Scavenger (`spmscavenger`) |
| **Scope** | Progression chain — craft and gather toward higher tool tiers |
| **Mode** | `PLANNING` (Phase 3 diamond) + `VALIDATION` (Phase 1–2) |
| **Status** | Phase 1–2 code `IMPLEMENTED` (TT-2e runtime `PLANNING`); **Phase 3 diamond `PLANNING`** (user-authorized); runtime `UNVERIFIED` |
| **Baseline version** | `1.9.2` (`gradle.properties` `mod_version`) |
| **Host mod** | Social Player Mobs `v0.86.0` (reference at `Projects/references/SocialPlayerMobs-v0.86.0/`) |
| **Owners** | User (product); implementation TBD |
| **Peer review** | `Agent_Cursor` · `Agent_Claude` · `Agent_Codex` · `Agent_Cursor 2` · `Agent_Cursor 3` (TT-1b) · `Agent_Cursor 4` (validation) |
| **Last update** | 2026-08-08 ~19:20 PDT |
| **Supersedes** | `docs/porting/RFC-TOOL-TIER-UPGRADES.md` (pre-MRFC-1 draft, migrated here) |
| **Related** | `plans/RFC-FURNACE-SMELTING.md`; [`plans/RFC-MINING-INTELLIGENCE-AND-WEALTH-SYSTEM.md`](RFC-MINING-INTELLIGENCE-AND-WEALTH-SYSTEM.md) (**deferred**); `plans/RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md`; `plans/RFC-VANILLA-AUTONOMOUS-PROGRESSION.md` |

---

## Executive Summary

Extend the scavenger torch-chain so PlayerMobs **craft and gather toward better tool tiers**, not only wooden pick/axe. **Phase 1** stone, **Phase 2** iron (smelt + ore gather), **Phase 3** diamond (direct ore gather + craft — no smelt).

**Phase 3 (`PLANNING`, user-authorized):** diamond pick + axe via the same `ConsumerRecipeSpec` + consumer-pulled gather pattern as iron; iron-pick capability gate; exposed-ore-first mining with optional descent heuristic. Netherite and enchanting remain **out of scope**.

Decisions live in pure policy (`ScavengerCrafting`, `ToolTierPolicy`, `WorkDemandPolicy`); goals stay thin — same architecture that fixed v1.0–1.3 crafting stalls.

**Continuation state (`CODE_CONFIRMED`, snapshot 2026-08-08 ~19:10 PDT):** Phase 1 + TT-2b/c/d implemented; `WorkDemandPolicy.java` arbitrates charcoal + iron-tool smelt demands; `rawIronDeficit` consumer-pulls ore gather (TT-2c). Build evidence: **113** unit tests, zero failures. Runtime TT-1–TT-6 and iron scenarios remain `UNVERIFIED`. **No diamond craft steps or `diamondDeficit` exist in source yet** (`NOT FOUND` — three probes: `ScavengerCrafting.Step`, `WorkDemandPolicy`, `GatherResourcesGoal.wantsDiamond`).

**Post-tier direction:** mining intelligence and **wealth system** → [`RFC-MINING-INTELLIGENCE-AND-WEALTH-SYSTEM.md`](RFC-MINING-INTELLIGENCE-AND-WEALTH-SYSTEM.md) (after Phase 3). Phase 3 here = craft + break only.

---

## Collaboration Protocol

- Every contributor identifies itself; this continuation is **`Agent_Cursor 2`** (peer review of D-TTU-011).
- Update **stable topic slots** below; do not add `V2`, `Agent 2 Ideas`, or parallel plan layers.
- Every substantive edit adds a **Contribution** block and updates RFC fields listed there.
- Planning triggers do **not** authorize implementation, Minecraft launch, commit, or push.
- `Work the Plan` may implement one **accepted, dependency-ready** task only when implementation is separately authorized.

### Concurrent-edit hazard (`CODE_CONFIRMED`, Agent_Claude, 2026-08-08)

Four agents have now edited this RFC and its target files, at times **simultaneously**. Measured
cost: one independent review raised three objections against code that was already being fixed in
parallel, and its drift warning was true when written and false within the hour. During that review
`ScavengerCrafting.makeTool` was refactored away between two reads of the same file, and this RFC's
own anchor text changed under an in-flight edit twice.

Working rules, cheap and sufficient:

1. **Timestamp code claims** — "snapshot HH:MM" beside any RFC-vs-code assertion. A claim without
   one cannot be audited later.
2. **Re-read immediately before writing**, not at the start of the review. Anchors move.
3. **Verify in one atomic pass**, not a sequence of greps — a multi-step audit can straddle another
   agent's write and report a state that never existed.
4. **Prefer verifying the build over asserting file contents** when the tree is hot. `clean build`
   is one consistent observation; ten file reads are not.

---

## Baselines and Current Implementation

### Progression today (`CODE_CONFIRMED`)

```text
logs → planks → sticks → [crafting table] → wooden pick + wooden axe → coal → torches → (surplus) campfire
```

| Component | Path | Behavior |
| --- | --- | --- |
| Crafting steps | `ScavengerCrafting.java` | `MAKE_PICKAXE` / `MAKE_AXE` → **wooden** only |
| Craft goal | `CraftTorchesGoal.java` | `owns()` lists stone–netherite picks from **loot**; crafts wood only |
| Gather | `GatherResourcesGoal.java` | Logs + coal ore; `needsTool()` = any pick for coal |
| Equip | `ToolBox.java` | `equipFor()` already picks **best owned** destroy speed |
| Mining timing | `MiningPolicy.java` | Shared break/drop rules with gather |

### Baseline drift warning — resolved (`CODE_CONFIRMED`, Agent_Cursor 4, snapshot 15:15 PDT)

On 2026-08-08 a parallel session briefly reverted `CraftTorchesGoal` and `GatherResourcesGoal`.
**Resolved:** TT-1b/TT-1bR re-landed stone hooks — `GatherResourcesGoal` calls
`GatherProtection.isGatherableStone` and `ToolTierPolicy.cobbleBelowTarget(backpack, mainHand, cfg)`;
`GatherProtectionTest` U-6/U-7 pass. `.\gradlew.bat clean build` green at `1.9.2`. Retain the
concurrent-edit hazard rules in Collaboration Protocol when multiple agents edit hot files.

### Gaps (`CODE_CONFIRMED` + `INFERRED`)

1. Wooden tools are slow for felling — more stuck-at-tree exposure (`INFERRED` from break timing).
2. No upgrade path when mob only has wood — looted stone pick ends craft pressure (`CODE_CONFIRMED` from `CraftTorchesGoal.owns()`).
3. Iron unreachable without smelting RFC (`INFERRED` from vanilla recipes).
4. Lambda-Player progression fantasy stops at “first hour” (`INFERRED` product goal).

### Continuation audit — implemented source versus packaged artifact (`CODE_CONFIRMED`, Agent_Cursor 4)

| Evidence | Result |
| --- | --- |
| `.\gradlew.bat clean build` (2026-08-08 ~15:15 PDT) | `BUILD SUCCESSFUL` |
| `build/test-results/test/*.xml` | **84** tests, 0 failures/errors/skips (includes 10 `ScavengerCraftingTest`, 12 `ToolTierPolicyTest`, 2 `GatherProtectionTest`) |
| `build/libs/spmscavenger-1.9.2.jar` | Last modified 2026-08-08 15:15 PDT — current packaging evidence |
| `test-datapacks/phase1-tool-tier/` | 36 files, namespace `spm_phase1`; spec `docs/agent-workflows/RUNTIME_TEST_DATAPACK.md` |
| `isGatherableStone` | `CODE_CONFIRMED` `GatherProtection.java` |
| `GatherProtectionTest` | `CODE_CONFIRMED` U-6/U-7 |
| `needsCobbleStock` | `NOT FOUND` in `src/**/*.java` (use `ToolTierPolicy.cobbleBelowTarget`) |

Conclusion: Phase 1 code + TT-1c + TT-1P + TT-RT are `IMPLEMENTED` with build/unit evidence; runtime TT-1–TT-6 remain `UNVERIFIED` (AV-1).

---

## Research Ledger

| Reference | Type | Technique | Problem solved | Applicability | Limitations | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| `ScavengerCrafting` v1.3+ | In-repo | Policy-first `nextStep()` | Crafting stall after priority fix | **Direct** | Wood-only steps | `CODE_CONFIRMED` |
| `MiningPolicy` + `FellingPolicy` | In-repo | Pure policy tests | Testable gather/craft gates | **Direct** | No tier concept yet | `CODE_CONFIRMED` |
| SPM `HarvestCropsGoal` | Host | Keep wanted drops | Coal hand-off pattern | **Borrowed** | PolyForm Shield | `CODE_CONFIRMED` reflection log |
| Vanilla 1.21.1 stone mining | Game | Wood pick drops cobble | Stone tier without furnace | **Phase 1** | Deepslate deferred | `DOCUMENTATION_CONFIRMED` |
| SPM `ItemPickupPolicy` | Host | `isBuildingBlock` + cap 64 | Cobble *can* floor-pickup | **Supplement only** | Competes with backpack; coal precedent | `CODE_CONFIRMED` |
| Minecraft 1.21.1 `incorrect_for_*_tool` tags | Game data | Capability-based harvest restrictions | Prevent false linear material ranking | **Direct** | Does not alone rank axe speed/durability | `SOURCE_CONFIRMED` from Loom `minecraft-common.jar` |
| Cloth Config 15.0.140 builder bytecode | Dependency API | Typed dropdown with explicit `setSelections` | Hide unreachable Phase-1 tier caps | **Direct** | Enum selector itself cannot filter values | `CODE_CONFIRMED` `javap` of resolved Fabric JAR |
| Gson 2.9.1 enum adapter bytecode | Dependency API | Normalize unknown/null enum values after decode | Prevent delayed config null crash | **Direct** | Normalization and warning still need implementation | `CODE_CONFIRMED` `TypeAdapters$EnumTypeAdapter.read` |
| `ToolBox.equipFor` + gather demand call sites | In-repo | Treat main hand and backpack as one ownership view | Stop false cobble demand after drawing a tool | **Direct** | D-TTU-011 `IMPLEMENTED` (TT-1bR) | `CODE_CONFIRMED` |
| `test-datapacks/phase1-tool-tier` | In-repo | Marker anchor + spawn presets | Repeatable runtime setup for TT-0R–TT-6 | **Direct** | Datapack proves setup only, not behaviour | `CODE_CONFIRMED` layout |
| Charcoal/furnace RFC | Deferred | Smelt branch | Iron tier | **Phase 2** | Not written | `UNVERIFIED` |
| Addon goal graph (`SpmScavenger`) | In-repo | Existing gather/craft/smelt goals at priority 3; explore at 8; local activity at 9 | Explain why exploration dominates after fixed demand is satisfied | **Direct** | Runtime frequency not measured | `CODE_CONFIRMED` `SpmScavenger.java:108-151` |
| SPM `PlayerMobEntity` goal graph | Pinned host source | Reuse native food, loot, crop, follow, equipment, and combat ownership | Avoid duplicate AI systems | **Direct** | Host runtime behavior remains separately testable | `CODE_CONFIRMED` `PlayerMobEntity.java:825-853` |
| Work coordination probes | In-repo | Three negative probes: storage/deposit goal; owned chest/barrel; graded demand/work queue | Establish missing coordination surface | **Direct** | Absence does not prove a particular replacement is best | `NOT FOUND` via source-wide `rg` probes |

---

## Brainstorming

Early ideas captured here; serious design lives under **Topic** slots.

- Stone tier only first; iron waits on furnace RFC → **Topic: Progression** (`IMPLEMENTED` P1)
- Looted tools skip redundant crafts → **Topic: Crafting**
- Cobble direct-keep (not SPM floor pickup) → **Topic: Compatibility**
- `maxPickTier` / `maxAxeTier` config caps → **Topic: Progression**
- Optional tie-break in `ToolBox` by tier → **Topic: Crafting** (deferred)
- Preparedness targets, player-useful work, and multi-mob reservations → **Topic: Resource Economy and Worksites**
- Consumer-pulled iron (`WorkDemandPolicy.rawIronDeficit`) → **Phase 2** (`IMPLEMENTED`)
- Trade as acquisition strategy → **RFC-VILLAGE-RAID** + **Topic: Acquisition strategies** (below)

### Brainstorm continuation — `Agent_Cursor` (2026-08-08)

Grounded in **implemented** `WorkDemandPolicy` + iron frontier (`CODE_CONFIRMED` `WorkDemandPolicy.java`). Not authorized for implementation.

#### A. Expand `WorkDemandPolicy` without becoming a planner

`WorkDemandPolicy` v1 already proves the pattern: **derived deficits**, **stable ordering**, **no stored scheduler**. Next demands to add as pure functions (not new goal scans):

| Candidate demand | `WorkType` | `DemandClass` | Executor | Notes |
| --- | --- | --- | --- | --- |
| Torch comfort band | `CRAFT_STEP` | SURVIVAL | `CraftTorchesGoal` | Hysteresis: refill at 4, stop at 8 |
| Cobble comfort band | `GATHER_BLOCK` | PROGRESSION | `GatherResourcesGoal` | Only when upgrade wanted |
| Tool durability reserve | `CRAFT_STEP` | SURVIVAL | `CraftTorchesGoal` | Replace at 10% remaining if materials fit |
| Charcoal (existing) | `SMELT_BATCH` | SURVIVAL | `SmeltAtFurnaceGoal` | Already wired |
| Iron ingot (existing) | `SMELT_BATCH` | PROGRESSION | `SmeltAtFurnaceGoal` | Consumer-keyed |
| Arrow stock (SPM combat) | `CRAFT_STEP` / loot | SURVIVAL | SPM + craft | Only if `requireArrows` and ranged main |
| Emeralds for trade | `TRADE` | PROGRESSION | Village RFC | `MaterialDemand` from village adapter |
| Bone meal / compost | `INTERACT` | OPTIONAL | Future compost goal | Low utility filler |

**Must not:** `WorkDemandPolicy` navigates, opens menus, or owns tick loops — only **selects** and **labels** (`reason` string → future `DescribableGoal`).

#### B. Acquisition strategies (unified graph)

```text
Need material X
  ├─ gather (logs, coal, cobble, iron ore)
  ├─ craft (sticks, tools, torches)
  ├─ smelt (charcoal, iron ingot)
  ├─ loot (SPM chest/floor — existing)
  ├─ trade (village — RFC-VILLAGE)
  └─ defer / explore (honest idle)
```

Each strategy exposes: `canSatisfy(deficit)`, `estimatedCost(backpack)`, `executorGoal()`. **No hardcoded profession lists** — trade evaluation uses offer outputs vs `MaterialDemand`.

#### C. Phase 3 — Diamond tier

**Scope (user-locked):** break diamond ore + craft diamond pick/axe only. Mining intelligence + greed → [`RFC-MINING-INTELLIGENCE-AND-WEALTH-SYSTEM.md`](RFC-MINING-INTELLIGENCE-AND-WEALTH-SYSTEM.md).

#### D. Believable / silly / inefficient behaviors (post-tier)

These reuse executors; personality weights only:

| Behavior | Executor | Personality gate |
| --- | --- | --- |
| Keep one "lucky" wooden axe as trophy | `ToolTierPolicy` ignore | high sentiment |
| Over-gather cobble "just in case" | gather | hoarder trait |
| Craft stone axe before pick when axe trait | craft order swap | misconfigured / chaotic |
| Smelt one extra charcoal "for later" | smelt | cautious |
| Visit camp every N expeditions | exploration anchor | homing |
| Stand at table swinging arm after craft | craft ticks | already happens |
| Drop surplus cobble at player's feet | gift/drop | ally feeling |
| Refuse iron upgrade until torch stock maxed | demand ordering | torch-obsessed |
| Mine iron visible on cliff, ignore safer vein | gather path | greedy / no planner |
| Trade browse with no purchase | village RFC | curiosity |

#### E. Wear-aware maintenance (D-TTU-019 precursor)

```text
mainHand tool durability < threshold
  AND replacement craft affordable in 8 slots
  AND no higher-priority SURVIVAL demand
→ preemptive duplicate craft (wood/stone/iron)
→ drop old tool after commit (mirror D-TTU-013)
```

**Risk:** wastes ingots if mob dies immediately — mitigate with higher threshold for iron than wood.

#### F. Expedition haul budget

While `ExploringGoal` active:

- opportunistically gather **one** surface coal/cobble/log if path deviation ≤ 8 blocks
- never start a new tree-felling session mid-expedition
- deposit nothing until worksite exists (D-TTU-018)

#### G. Visible intent chain (diagnostic)

Expose selected `WorkDemand.reason` + executor to SPM readout when `debugWorkDemand=true`:

```text
Need iron ingot (iron_tool_frontier) → Smelt at furnace
```

Stops "Gather resources" vs idle confusion during playtests (`UNVERIFIED` player value).

#### H. Anti-patterns to reject

| Idea | Why reject |
| --- | --- |
| Per-tick `RequirementResolver` over all recipes | Planner creep |
| Auto-strip-mine corridors | Griefing + performance |
| Force 8-slot backpack expansion | SPM host constraint |
| Diamond gear by default | Config + Phase 3 gate |
| Duplicate SPM `RaidContainersGoal` for "deposit" | Theft risk (D-TTU-018) |

---

## Topic Index

| Topic | Status | Summary |
| --- | --- | --- |
| [Progression](#topic-progression) | `IMPLEMENTED` (P1–2 code) / `PLANNING` (P3, TT-2e) | Stone + iron done; **diamond authorized** |
| [Resource Economy and Worksites](#topic-resource-economy-and-worksites) | `RESEARCHING` | `WorkDemandPolicy` v1 live; graded bands + worksites open |
| [Acquisition strategies](#topic-acquisition-strategies) | `PROPOSED` | gather / craft / smelt / loot / trade unified graph |
| [Crafting](#topic-crafting) | `IMPLEMENTED` | TT-0R atomic craft transaction in source; gold+config follow-ups remain |
| [Gathering](#topic-gathering) | `IMPLEMENTED` | TT-1b + iron ore (TT-2c); diamond break in Phase 3 |
| [Compatibility](#topic-compatibility) | `CONSENSUS` | SPM probe done; direct-keep cobble |
| [Performance](#topic-performance) | `PROPOSED` | Gather scan budget at scale; profiling unverified |
| [Validation](#topic-validation) | `VALIDATION` | TT-1c + TT-RT datapack done; runtime evidence pending |

---

## Topic: Progression

**Status:** `CONSENSUS`

**Goal:** PlayerMobs visibly progress tools while preserving torches as the primary objective.

**Current implementation:** TT-1a/TT-1b add stone policy, crafting, and cobble gathering. Gold ranks as `WOOD` (TT-1aR). Craft targets UI/load limited to NONE/WOOD/STONE with clamp+warn (TT-1aC).

**Original/reference behavior:** Vanilla survival: wood → stone → iron; SPM off-train has no mining progression (`CODE_CONFIRMED` from prior port study).

**Evidence:** `ScavengerCrafting.java` lines 47–48, 173–174; `gradle.properties` `mod_version=1.9.2`; `CraftTorchesGoal.java` lines 142–145 already accept looted stone+ tools in `owns()`.

**Progression nodes (Phase 1):**

| Node ID | Objective | Preconditions | Resources | Actions | Unlocks | SPM support | Missing AI |
| --- | --- | --- | --- | --- | --- | --- | --- |
| P1-wood-tools | Wooden pick + axe | Table, planks, sticks | Logs | `ScavengerCrafting` | Coal mining | Loot/equip pick | **Implemented** |
| P1-cobble | Cobble stock | Wooden pick, upgrade wanted | Stone/cobble | `GatherResourcesGoal` | Stone craft | Floor pickup *possible* | **TT-1b `IMPLEMENTED`** |
| P1-stone-tools | Stone pick + axe | 3 cobble + 2 sticks each | Cobble | `ScavengerCrafting` | Faster felling/mining | Loot skip via policy | **TT-1a + TT-0R + TT-1b + TT-1aR implemented** |
| P1-coal-torches | Torch stock | Pick (any tier) | Coal + sticks | Existing chain | Campfire (optional) | Coal direct-keep | **Implemented** |
| P2-iron-tools | Iron pick + axe | 3 ingots + 2 sticks each | Ingots | `ScavengerCrafting` | **TT-2b `IMPLEMENTED`** |
| P2-smelt | Iron ingots | Furnace + fuel | Raw iron | `SmeltAtFurnaceGoal` + `WorkDemandPolicy` | **IMPLEMENTED** (FS-8) |
| P3-diamond | Diamond tools | Iron pick, 3 diamonds + 2 sticks each | Diamonds from ore | `ScavengerCrafting` + gather | **Phase 3** (`PLANNING`) |
| P3-diamond-ore | Diamond stock | Iron pick, consumer deficit | `#minecraft:diamond_ores` exposed (gen-1) | `GatherResourcesGoal` | **Phase 3** (TT-3b) |

**Candidate designs:**

| Phase | Graph | Furnace? |
| --- | --- | --- |
| **1 — stone** | wood tools → cobble gather → stone tools → coal → torches | No |
| **2 — iron** | stone → iron ore → smelt → iron tools | Yes |
| **3 — diamond** | iron tools → diamond ore → diamond tools | No |

**Trade-offs:**

| Choice | Benefit | Cost |
| --- | --- | --- |
| Stone before iron | Ships without furnace goal | Less end-game progression |
| Config tier caps | Server tuning | More config surface |
| Cobble stock cap (12) | Stops strip-mining | May need retune |

**Agent contributions:**

### Contribution — Prior session (pre-MRFC-1)

Agent: (unattributed draft)  
Date/Session: 2026-08-08  
Contribution type: DESIGN  

Reviewed: Initial problem analysis and Phase 1 graph.  
Agreement: Scope and stone-first phasing are sound.  
Concerns: Document was not MRFC-1 shaped; wrong path (`docs/porting/`).  
Evidence: `CODE_CONFIRMED` from source inspection.  
Recommendation: Migrate to `plans/` with parity tables.  
RFC fields updated: Migrated into this topic.

### Contribution — Agent_Cursor

Agent: Agent_Cursor  
Date/Session: 2026-08-08  
Contribution type: DESIGN  

Reviewed: Prior draft + tightened MRFC-1 rule migration.  
Agreement: Phase 1 stone-only; iron explicitly deferred.  
Concerns: Open product questions on tier caps and pick-before-axe.  
Evidence: `CODE_CONFIRMED` baseline at `1.9.2`.  
Recommendation: Lock pick-before-axe and keep-wooden-tool disposal after user answers open questions.  
RFC fields updated: Progression, Decision Registry D-TTU-001, Tasks, Gates.

### Contribution — Agent_Cursor (RFC continuation)

Agent: Agent_Cursor  
Date/Session: 2026-08-08  
Contribution type: RESEARCH + DESIGN  

Reviewed: Prior migration; `ItemPickupPolicy.java` (SPM v0.86.0); `GatherResourcesGoal`, `CraftTorchesGoal`, `ToolBox`, `MiningPolicy`.  
Agreement: Direct-keep cobble matches coal precedent; SPM building-block pickup is insufficient alone.  
Concerns: `wantsMore()` must gain a cobble branch or stone gather never starts; 8-slot backpack still a partial risk.  
Evidence: `CODE_CONFIRMED` — SPM `TOOL_CRAFTABLE_STONE` includes `Items.COBBLESTONE`; `wantsBuildingBlock` cap 64; `GatherResourcesGoal.wantedDrop()` lines 317–319 excludes cobble today.  
Recommendation: Advance decisions to `CONSENSUS` with documented defaults; lock on user checkbox.  
RFC fields updated: Progression nodes, Crafting API sketch, Compatibility probe, Performance topic, D-TTU-005–007, gates, tasks.

**Open questions (agent recommendations — lock on user approval):**

1. **Default tier caps:** `maxPickTier` / `maxAxeTier` = `ToolTier.STONE` when `craftTools=true`. Wood-only servers set both to `WOOD`. → **D-TTU-006**
2. **`cobbleStockTarget=6`:** Exactly one stone pick + one stone axe (3+3 cobble), no hoard buffer. → **D-TTU-007**
3. **Phase 2:** Separate charcoal/furnace RFC — stone ships independently. → **D-TTU-001** (unchanged)

**Current preferred design:** Phase 1 stone + Phase 2 iron (TT-2b/c/d) as documented; post-tier expansion via `WorkDemandPolicy` v2 (craft/gather bands, acquisition graph).

**Rejected alternatives:**

| Alternative | Why rejected |
| --- | --- |
| Craft to netherite | Smithing + ancient debris | Scope explosion |
| NBT tool level | Fights item state / loot | Rejected |
| Merge with charcoal RFC | Stone needs no furnace | Rejected |

**Decision:** See [D-TTU-001](#d-ttu-001-phase-1-scope-stone-tier-only).

**Implementation tasks:** Phase 1 through TT-1P/TT-RT `IMPLEMENTED`. Phase 2 TT-2b/c/d `IMPLEMENTED`; TT-2e runtime `PLANNING`. **Phase 3 TT-3a–TT-3e `PLANNING`** (user-authorized).

**Validation:** TT-1–TT-6 (Phase 1); TT-2e runtime iron matrix (Phase 2).

**Parity:** See Feature Parity table.

---

## Phase 2 — Iron tools (`IMPLEMENTED` — runtime `UNVERIFIED`)

**Prerequisite:** `RFC-FURNACE-SMELTING.md` FS-3+ and D-FSM-010 consumer spec — **met** for TT-2b/c (`CODE_CONFIRMED`).

**Status:** Code `IMPLEMENTED`; **TT-2e** runtime kit `PLANNING`.

### Progression nodes (Phase 2)

| Node ID | Objective | Preconditions | Resources | Actions | Owner RFC |
| --- | --- | --- | --- | --- | --- |
| P2-iron-input | Smeltable iron stock | Stone+ pick, upgrade wanted | Exposed iron ore → normally `raw_iron`; Silk Touch may retain ore | `GatherResourcesGoal` + actual block loot | **This RFC** (TT-2c) |
| P2-smelt | Iron ingots | Furnace + fuel | Raw iron or recipe-valid ore + fuel | Recipe-backed `SmeltAtFurnaceGoal` | **Furnace RFC** (FS-5) |
| P2-iron-tools | Iron pick + axe | 3 ingots + 2 sticks each | Ingots | `ScavengerCrafting` | **This RFC** (TT-2b) |

**Graph:** `stone tools → iron ore gather → smelt → iron tools → (existing) coal/torches`

### Phase 2 design decisions (`PROPOSED`)

| ID | Title | Status | Summary |
| --- | --- | --- | --- |
| D-TTU-012 | Capability split (Proposal C) | `NARROWED` | Upgrade rank remains separate from SPM's existing live `isCorrectToolForDrops`; TT-2c must use the live check |
| D-TTU-013 | Stone disposal on iron craft | `IMPLEMENTED` | Drop backpack or main-hand stone tool only after atomic iron craft commits |
| D-TTU-014 | Smeltable iron gather | `RESEARCHING` | Mine `#minecraft:iron_ores`, keep actual valid drop (`raw_iron` normally); exposed + build-protected |
| D-TTU-015 | Config caps expand to IRON | `IMPLEMENTED` | `IRON` in `CRAFTABLE_TIER_CAPS`; DIAMOND/null clamp to IRON |
| D-TTU-016 | `ironStockTarget=6` | `SUPERSEDED` | Removed; live consumer deficit is the only iron demand truth |

**Cross-RFC (`CONSENSUS` on furnace side):** iron demand must come from a **consumer-owned immutable
recipe specification** shared by craft `apply` and requirement emission (`RFC-FURNACE-SMELTING.md`
D-FSM-010 Option B). TT-2b must not invent a second `3+2` table beside the craft step.

### D-TTU-012: Capability split (from D-TTU-009 Phase 2 destination)

**Status:** `NARROWED` for TT-2b; live capability remains binding for TT-2c.

Gold must not use a single ordinal `ToolTier` as proof of **harvest capability**. Independent review
confirmed SPM already calls `ItemStack.isCorrectToolForDrops` at the live block seam, while this
addon's `ToolTierPolicy` only ranks ownership/upgrade preference. TT-2b therefore needs no unused
capability abstraction. TT-2c must retain the live check and may introduce a named abstraction only
if its ore-gathering implementation has more than one real consumer.

**Must happen:** Golden pick cannot satisfy iron-capability ore gate; iron pick does.  
**Must not:** Reintroduce gold=`IRON` ranking.

### Phase 2 tasks

| Task ID | Dependencies | Objective | Status |
| --- | --- | --- | --- |
| **TT-2a** | D-TTU-012 | Explicit live-capability seam for TT-2c, only if needed beyond SPM's existing check | `CLOSED — NOT NEEDED` (TT-2c has exactly one consumer; see below) |
| **TT-2b** | FS-5 + narrowed D-TTU-012 + furnace D-FSM-010/013 | Shared-spec `MAKE_IRON_PICKAXE` / `MAKE_IRON_AXE`, pick-first frontier, stone disposal | `IMPLEMENTED` (unit/build; runtime `UNVERIFIED`) |
| **TT-2c** | TT-2a + furnace recipe hand-off contract | Iron-ore mining + actual-drop retention + protection | `IMPLEMENTED` (unit/build; runtime `UNVERIFIED`) — Agent_Claude |
| **TT-2d** | D-TTU-015 | Minimum reachability: expand `CRAFTABLE_TIER_CAPS` and UI to IRON | `IMPLEMENTED` |
| **TT-2e** | TT-2b–TT-2d | Docs + `test-datapacks/phase2-tool-tier/` + runtime matrix | `PLANNING` |

**Execution state:** FS-1→FS-8, TT-2b and **TT-2c** are implemented. TT-2e owns the remaining
dedicated tool-tier runtime kit/documentation.

### TT-2c implementation record (Agent_Claude, snapshot 18:5x)

**Files changed**

| File | Change |
| --- | --- |
| `WorkDemandPolicy.java` | `+ rawIronDeficit(Container, ItemStack, ScavengerConfig)` — consumer-pulled ore demand |
| `GatherProtection.java` | `+ isGatherableOreType(BlockState)`; iron ore joins coal under the **same** exposure and built-nearby rules |
| `goal/GatherResourcesGoal.java` | `+ wantsIron()`; iron ore in `isCandidate` / `isWanted`; `wantedDrop` retains actual drops |
| `test/RawIronDemandTest.java` | **new** — 6 tests, must-happen and must-not-happen |

**Consumer-pulled, not push (D-FSM-010).** `rawIronDeficit` derives from the same
`ConsumerRecipeSpec` that drives `ironToolDemand`, minus raw iron already carried. With no active
iron-tool consumer it returns `0`, so ore is left in the ground — this is what keeps TT-2c dormant
while the iron tier is unreachable, and it is the opposite of the `needsIronIngot` push model that
produced the iron dead end (`RFC-FURNACE-SMELTING.md` Baselines).

**D-TTU-012 honoured.** The ore gate calls `ToolBox.ownsToolFor(mob, state)`, which routes through
`ItemStack.isCorrectToolForDrops` — the **live** capability check. No `ToolTier` ordinal appears in
the gate. A golden pick (wood-level capability, fastest speed in the game) is therefore refused for
iron ore while a stone or iron pick passes.

**TT-2a closed as not needed.** D-TTU-012 permitted a named capability abstraction "only if its
ore-gathering implementation has more than one real consumer". It has exactly one — the ore gate —
so introducing an abstraction would have been the unused indirection that decision warned against.

**Actual-drop retention.** `harvest()` already called `Block.getDrops(...)` with the live main-hand
stack, so drops were never assumed; the gap was `wantedDrop` discarding them. It now retains
`RAW_IRON` (ordinary pick) **and** `IRON_ORE` / `DEEPSLATE_IRON_ORE` (Silk Touch) — both smelt to an
iron ingot, satisfying the P2-iron-input row without branching on enchantment.

**Verification**

- `.\gradlew.bat clean build` — **113 tests, zero failures/errors** (97 before this task).
- `RawIronDemandTest` covers: deficit exists when wanted; carried raw iron reduces it; enough carried
  stops it; unreachable iron tier yields none; `craftTools=false` yields none; **de-latch** — a
  looted iron pick ends demand on the next evaluation.

**Honest gap — the D-TTU-012 assertion is not unit-tested.** `isCorrectToolForDrops` resolves through
the `#minecraft:incorrect_for_*_tool` **block tags**, and `Bootstrap.bootStrap()` binds tags **empty**
without a datapack — the same constraint `ScavengerCraftingTest` documents for `ItemTags`. A unit test
would assert against empty tags and could pass for the wrong reason. The gold-refused / iron-accepted
gate is **runtime-verifiable only** and is recorded here rather than papered over with a test that
proves nothing. It belongs in TT-2e's runtime matrix.

---

## Phase 3 — Diamond tools (`PLANNING` — minimal scope)

**User-locked scope (2026-08-08):** Phase 3 does **only** two things:

1. **Break** `#minecraft:diamond_ores` (consumer-pulled, mirror TT-2c iron)
2. **Craft** diamond pick + diamond axe (`ConsumerRecipeSpec`, iron disposal)

**Explicitly deferred** to [`RFC-MINING-INTELLIGENCE-AND-WEALTH-SYSTEM.md`](RFC-MINING-INTELLIGENCE-AND-WEALTH-SYSTEM.md):

- Mining intelligence (WHERE to gather, target priority, depth band, explore bias)
- Resource greed (comfort stock — e.g. mine 10 iron when 3 would craft the pick)
- `GatherIntentPolicy` / wealth → [`RFC-MINING-INTELLIGENCE-AND-WEALTH-SYSTEM.md`](RFC-MINING-INTELLIGENCE-AND-WEALTH-SYSTEM.md)

**Prerequisite:** Phase 2 TT-2b/c `IMPLEMENTED`; iron pick capability (`D-TTU-012`).

**Status:** `PLANNING` — no implementation until **Work the Plan** / **Begin**.

**Out of scope:** netherite, enchanting, greed, strip mines, descent, Fortune tuning.

### What ships

| Step | Mechanism | Task |
| --- | --- | --- |
| Craft diamond tools | `MAKE_DIAMOND_*` + iron disposal | **TT-3a** |
| Break diamond ore | `diamondDeficit` + gather hooks (same shape as `wantsIron`) | **TT-3b** |
| Config `max*Tier=DIAMOND` | `CRAFTABLE_TIER_CAPS` | **TT-3d** |
| Runtime proof | `test-datapacks/phase3-tool-tier/` | **TT-3e** |

**Graph:** `iron tools → diamond ore (in scan) → diamond pick → more diamonds → diamond axe`

### Known limitation until mining RFC (`INFERRED`)

Phase 3 uses **nearest-block scan only** (no band gate, no priority). Deep diamond may be unreachable at surface; mobs with `diamondDeficit > 0` and no ore in radius may scan periodically — **mining RFC MI-2** adds band gate + greed. Test datapacks should place **exposed diamond in scan range** (cave/ravine preset).

### Progression nodes (Phase 3)

| Node ID | Objective | Actions | Task |
| --- | --- | --- | --- |
| P3-diamond-input | Diamond stock | `GatherResourcesGoal` + `diamondDeficit` | TT-3b |
| P3-diamond-tools | Diamond pick + axe | `ScavengerCrafting` | TT-3a |

Unlike iron, **no smelt**. `WorkDemandPolicy` does not need a smelt demand for diamonds.

### Phase 3 decisions

| ID | Title | Status | Summary |
| --- | --- | --- | --- |
| [D-TTU-021](#d-ttu-021-iron-disposal-on-diamond) | Iron disposal on diamond craft | `IMPLEMENTED` | `replacedItem(DIAMOND, …)` → iron tool handed to the disposal sink after commit |
| [D-TTU-022](#d-ttu-022-diamond-ore-gather) | Diamond ore gather | `IMPLEMENTED` | Consumer-pulled `diamondDeficit` + live capability gate + actual-drop retention |
| [D-TTU-023](#d-ttu-023-config-caps-diamond) | Config caps → DIAMOND | `IMPLEMENTED` | Caps include DIAMOND; clamp now **derived from the caps** so UI and load agree |
| [D-TTU-025](#d-ttu-025-phase-3-ceiling) | Phase 3 ceiling | `CONSENSUS` | Break ore + craft tools only |
| [D-TTU-028](#d-ttu-028-phase-3-scope-lock) | Phase 3 scope lock | `CONSENSUS` | No mining intelligence in this RFC |
| [D-TTU-024](#d-ttu-024-deferred-mining-rfc) | Mining intelligence | `DEFERRED` | → mining RFC |
| [D-TTU-026](#d-ttu-026-deferred-gather-architecture) | Gather sub-decision | `DEFERRED` | → mining RFC D-MRG-001 |
| [D-TTU-027](#d-ttu-027-deferred-gather-intent) | GatherIntentPolicy | `DEFERRED` | → mining RFC MI-1 |

### D-TTU-028: Phase 3 scope lock

**Status:** `CONSENSUS` (user 2026-08-08)  
**In scope:** diamond ore break + diamond tool craft + config + runtime datapack.  
**Out of scope:** WHERE-to-mine, greed, comfort stock, explore descent — **`RFC-MINING-INTELLIGENCE-AND-WEALTH-SYSTEM.md`**.

### Reachability note (audit only)

Deep diamond + surface scan mismatch documented in prior review — **not solved in Phase 3**; mining RFC adds band gate (D-MRG-003). Phase 3 runtime tests use cave/exposed ore presets.

### TT-3a / TT-3b / TT-3d implementation record (Agent_Claude, snapshot 19:40)

Authorized by the user with an explicit scope: *recognize/harvest diamond ore correctly, craft
diamond pick and axe, tool-tier policy reaches DIAMOND, prove the consumer/demand loop* — with
**seeking deferred to `RFC-MINING-INTELLIGENCE-AND-WEALTH-SYSTEM.md`**. That split is what resolved
the D-TTU-024 reachability objection: the reachable half shipped, the unreachable half moved.

**Files changed**

| File | Change |
| --- | --- |
| `ScavengerCrafting.java` | `MAKE_DIAMOND_PICKAXE` / `_AXE`, `DIAMOND_*_RECIPE`, `activeDiamondToolRecipe`, `DIAMOND_PER_TOOL`; `towardIronTool` → generic `towardConsumerTool` |
| `ToolTierPolicy.java` | `replacedItem` now covers STONE→wood, IRON→stone, DIAMOND→iron |
| `ScavengerConfig.java` | `DIAMOND` in caps; `DEFAULT_CRAFT_TIER`; sanitiser derived from caps |
| `WorkDemandPolicy.java` | `diamondDeficit(...)` + `DIAMOND_GENERATION_CEILING_Y` |
| `GatherProtection.java` | diamond ores in `isGatherableOreType` |
| `goal/GatherResourcesGoal.java` | `wantsDiamond()`; diamond ore in `isCandidate` / `isWanted`; `Items.DIAMOND` retained |
| `test/DiamondTierTest.java` | **new** — 10 tests |
| `test/ScavengerConfigTierTest.java` | guards updated + `everyOfferedCapSurvivesSanitisation` added |

**The plausibility gate.** `diamondDeficit` returns `0` above `DIAMOND_GENERATION_CEILING_Y` (16).
A surface mob carries no diamond demand, so `wantsMore()` still goes false and the **"nothing to
gather" resting state survives** — the specific harm the D-TTU-024 objection identified. A mob that
wanders into a ravine mines diamond opportunistically, which needs no descent behaviour.

### Three defects the tests caught (`CODE_CONFIRMED`)

1. **A hardcode inside a "generic" helper.** `towardIronTool` accepted a `ConsumerRecipeSpec` but
   hardcoded `Items.IRON_INGOT` — it would have silently checked *iron* stock for a diamond recipe.
   Now `towardConsumerTool`, satisfying whatever the spec declares. A future tier needs a spec and
   nothing else here. (Gate SPM-0: the signature promised generality the body did not deliver.)
2. **The UI would have offered a lie.** `CRAFTABLE_TIER_CAPS` gained DIAMOND while
   `sanitizeCraftTarget` still clamped it to IRON, so the dropdown would offer diamond and `load()`
   would silently remove it. New invariant `everyOfferedCapSurvivesSanitisation` failed immediately;
   the sanitiser is now **derived from the caps**, so the two cannot drift again.
3. **Corrupt config would have failed open.** The first fix resolved `null` to the *highest*
   craftable tier — after Phase 3 that silently grants DIAMOND from an unparseable config. The
   existing `u10b` guard caught it. `null` now resolves to `DEFAULT_CRAFT_TIER` (STONE). This is a
   deliberate change from Phase 2's null→IRON and is commented as such in code and test.

The two stale Phase-1 guards were **renamed, not deleted** (`u10a_ironAndDiamondCapsClampToStone` →
`u10a_supportedCapsSurviveUnchanged`): their job is to make any change to the reachable tier set a
conscious one, and that is exactly what they did.

**Verification:** `.\gradlew.bat clean build` — **124 tests, zero failures/errors** (113 before).

**Not covered — same limit as TT-2c.** The D-TTU-012 live capability gate (iron pick accepted,
stone/gold refused for diamond ore) is **not** unit-tested: it resolves through the
`#minecraft:incorrect_for_*_tool` block tags and `Bootstrap.bootStrap()` binds tags empty, so an
assertion could pass for the wrong reason. Runtime-verifiable only; belongs in TT-3e's matrix
alongside TT-2e's.

**Concurrent-edit note.** A parallel agent reverted `sanitizeCraftTarget` at 19:38 mid-task; I
re-snapshotted and reapplied atomically per the concurrent-edit rules in the furnace RFC. If that
revert was deliberate — someone holding the DIAMOND clamp — this change contradicts it and should be
reviewed.

### D-TTU-021: Iron disposal on diamond craft

**Status:** `PROPOSED`  
**Accepted pattern:** Mirror D-TTU-013 / iron `ConsumerRecipeSpec.replacedItem` — `Items.IRON_PICKAXE` / `Items.IRON_AXE` dropped at mob feet **only after** atomic diamond craft commits.  
**Evidence:** `CODE_CONFIRMED` iron specs use `STONE_PICKAXE`/`STONE_AXE` as `replacedItem`; diamond specs would use iron equivalents.  
**Must happen:** Backpack + main-hand iron tool disposed on upgrade. **Must not:** Dispose before commit or while diamonds still needed for the other tool.

### D-TTU-022: Diamond ore gather

**Status:** `PROPOSED`  
**Accepted:** `WorkDemandPolicy.diamondDeficit(backpack, mainHand, cfg)` derived from active `DIAMOND_*_RECIPE` minus diamonds already carried — same consumer-pulled contract as `rawIronDeficit` (D-FSM-010).  
**Gate:** `ToolBox.ownsToolFor(mob, state)` on `#minecraft:diamond_ores` — iron pick passes; stone/gold refuse (`D-TTU-012`).  
**Protection:** Reuse `GatherProtection.isGatherableOre` — diamond ores already in `isNaturallyGeneratedOre` (`CODE_CONFIRMED` `GatherProtection.java:363`).  
**Drops:** `wantedDrop` retains `Items.DIAMOND` from `Block.getDrops` — do not assume ore block retention unless Silk Touch (defer Fortune optimization).  
**Must not:** Mine diamond ore when `diamondDeficit == 0` (loot-only diamonds stay SPM's job).

### D-TTU-023: Config caps expand to DIAMOND

**Status:** `PROPOSED`  
**Accepted:** Add `ToolTier.DIAMOND` to `CRAFTABLE_TIER_CAPS` and Cloth cycle; `normalizeCraftTargets()` clamps only `null` → previous default (IRON or STONE per product choice).  
**Default recommendation:** `maxPickTier` / `maxAxeTier` remain **STONE** or **IRON** for torch-chain servers; diamond is **opt-in** cap.  
**Rejected:** Diamond as silent default — 6-gem backpack pressure and long gather loops.

### D-TTU-024: Deferred — mining intelligence

**Status:** `DEFERRED` → [`RFC-MINING-INTELLIGENCE-AND-WEALTH-SYSTEM.md`](RFC-MINING-INTELLIGENCE-AND-WEALTH-SYSTEM.md) (D-MRG-002, D-MRG-003, MI-2)

### D-TTU-025: Phase 3 ceiling

**Status:** `CONSENSUS` — break diamond ore + craft diamond tools only; greed and WHERE-to-mine are out of scope here.

### D-TTU-026: Deferred — gather sub-decision architecture

**Status:** `DEFERRED` → mining RFC D-MRG-001

### D-TTU-027: Deferred — GatherIntentPolicy

**Status:** `DEFERRED` → mining RFC MI-1

### Phase 3 tasks

| Task ID | Dependencies | Objective | Status |
| --- | --- | --- | --- |
| **TT-3a** | D-TTU-021 + D-TTU-023 | `MAKE_DIAMOND_*` specs, pick-first, iron disposal | `IMPLEMENTED` (unit/build; runtime `UNVERIFIED`) — Agent_Claude |
| **TT-3b** | TT-3a + D-TTU-022 | `diamondDeficit` + diamond ore gather (mirror TT-2c) | `IMPLEMENTED` (unit/build; runtime `UNVERIFIED`) — Agent_Claude; **opportunistic only**, seeking → mining RFC |
| ~~TT-3c~~ | — | ~~Mining intelligence~~ | **`MOVED`** → mining RFC MI-1–MI-5 |
| **TT-3d** | D-TTU-023 | `CRAFTABLE_TIER_CAPS` + UI → DIAMOND | `IMPLEMENTED` (unit/build; runtime `UNVERIFIED`) — Agent_Claude |
| **TT-3e** | TT-3a, TT-3b, TT-3d | Docs + `phase3-tool-tier` datapack + TT-7–TT-10 | `PLANNING` |

**Implementation order:** TT-3d → TT-3a → TT-3b → unit tests → TT-3e runtime. **Then** mining RFC.

### API sketch (`PROPOSED` — Phase 3 only)

```java
// ScavengerCrafting.java — mirror IRON_*_RECIPE
public static final ConsumerRecipeSpec DIAMOND_PICKAXE_RECIPE = ...;
public static Optional<ConsumerRecipeSpec> activeDiamondToolRecipe(...);

// WorkDemandPolicy or ScavengerCrafting helper — craft consumer only
public static int diamondDeficit(Container backpack, ItemStack mainHand, ScavengerConfig cfg);

// GatherResourcesGoal.java — mirror wantsIron()
private boolean wantsDiamond() { return diamondDeficit(...) > 0; }
// isCandidate / isWanted / wantedDrop for DIAMOND_ORES + Items.DIAMOND
```

### Runtime acceptance (TT-7–TT-10)

| ID | Must happen | Must not happen |
| --- | --- | --- |
| TT-7 | At `maxPickTier=DIAMOND`, mob crafts diamond pick after 3 diamonds + sticks | Mines diamond ore with stone pick |
| TT-8 | Consumer deficit zero → ignores diamond ore | Strip-mines deepslate corridor |
| TT-9 | Looted diamond pick → skip redundant craft | Disposes iron pick before diamond craft commits |
| TT-10 | Iron pick + exposed diamond ore → mines and retains diamond drop | Breaks player-placed ore (protection) |

**Datapack:** `test-datapacks/phase3-tool-tier/` (`PROPOSED`) — preset `maxPickTier=DIAMOND`, iron tools pre-equipped, `spawn/exposed_diamond` arena with single exposed ore column.

### Cross-RFC links

| RFC | Link |
| --- | --- |
| `RFC-VANILLA-AUTONOMOUS-PROGRESSION.md` | Phase 3 overworld power row — align VP-3a scenarios |
| `RFC-VILLAGE-RAID` | Optional trade acquisition (toolsmith/armorer) per D-TTU-020 — **not** primary path |
| `RFC-FURNACE-SMELTING` | No change — diamonds do not use furnace |

### Contribution — Agent_Claude (Phase 3 implementation)

Agent: Agent_Claude
Date/Session: 2026-08-08 (snapshot 19:40)
Contribution type: IMPLEMENTATION + VALIDATION

Implemented TT-3a, TT-3b and TT-3d at the scope the user authorized, with seeking deferred to the
mining RFC. Full record above.

The part worth reviewing is not the diamond code — it mirrors TT-2b/TT-2c almost exactly — but the
three defects the test suite caught, two of which were **mine**, introduced while implementing this
task: a UI that offered a cap the loader silently removed, and a null-config path that failed open to
the most aggressive tier. Both were caught by invariants rather than by review, which is the argument
for writing the invariant before the feature.

The third was pre-existing: a helper whose signature promised generality while its body hardcoded
iron. It would have shipped a diamond recipe that checked iron stock.

Agreement: D-TTU-024's resolution to `DEFERRED` → mining RFC is the right call and matches the user's
own framing.

Concerns: Phase 3 is the fourth consecutive subsystem to reach `IMPLEMENTED` with **zero runtime
evidence**. The diamond loop sits on the iron loop, which sits on the furnace loop, and none of the
three has been observed working. Unit coverage is now 124 tests and cannot substitute for one
session.

Recommendation: TT-3e should carry the gold/stone-refused capability case explicitly, and a runtime
session should exercise iron before diamond — a diamond failure would otherwise be ambiguous between
three untested layers.

RFC fields updated: Phase 3 task rows (TT-3a/3b/3d), both decision registries (D-TTU-021/022/023 →
`IMPLEMENTED`), implementation record, Change Log, this contribution.

---

### Contribution — Agent_Claude (Phase 3 review)

Agent: Agent_Claude
Date/Session: 2026-08-08 (snapshot 19:15)
Contribution type: REVIEW + RESEARCH

Reviewed: the full Phase 3 section and D-TTU-021–025, as the independent reviewer the locking
criteria require.

**Verified from vanilla data (1.21.1 jar), not from the RFC's own tests.** `#minecraft:diamond_ores`
exists with both variants; both sit in `#minecraft:needs_iron_tool`. D-TTU-022's capability gate is
therefore correct at the source — iron passes, stone and gold refuse — and the pattern is a clean
reuse of TT-2c's live check. No objection to D-TTU-021, D-TTU-022's gate/drops, D-TTU-023 or
D-TTU-025.

**Historical note (superseded):** Agent_Claude objected to original D-TTU-024 "exposed only" gen-1. User direction + D-TTU-024 revision adopted **band-gated mining intelligence inside gather** (`CONSENSUS`). Original objection text retained for audit:

diamonds generate below Y≈16 peaking near Y−59, the mob scans 10–20 blocks, `isGatherableOre`
requires air exposure, mobs work at the surface, and gen-1 explicitly had **no descent**. The
consumer was real and the input unreachable without a band gate.

Agreement: backpack budget arithmetic right; pick-first frontier mirrors D-TTU-013; D-TTU-025 ceiling sensible.

RFC fields updated: Phase 3 reachability review, Change Log, this contribution (historical).

---

### Contribution — Agent_Claude (TT-2c implementation)

Agent: Agent_Claude
Date/Session: 2026-08-08 (snapshot 18:5x)
Contribution type: IMPLEMENTATION + VALIDATION

Reviewed before implementing: RFC summary, Phase 2 task table, D-TTU-012 (`NARROWED`, live check
binding for TT-2c), D-TTU-004/005, the P2-iron-input progression row, and the furnace RFC's
D-FSM-010 consumer-pulled demand contract.

Implemented TT-2c as recorded above. Three choices worth flagging for review:

1. **Reused `WorkDemandPolicy` rather than adding an iron demand.** The ore pull derives from the
   same `ConsumerRecipeSpec` that TT-2b/FS-8 already use, so there is one demand model, not two.
   Adding a second would have re-created the defect the furnace RFC is currently trying to close.
2. **Closed TT-2a as not needed**, on D-TTU-012's own condition — one consumer, so no abstraction.
   If a second consumer for ore capability appears, TT-2a should be reopened rather than the
   abstraction being smuggled in.
3. **Declined to unit-test the capability gate.** Bootstrap binds block tags empty, so an
   `isCorrectToolForDrops` assertion would pass for the wrong reason. Recorded as runtime-only
   rather than claimed as covered (AV-1).

Evidence: `CODE_CONFIRMED` for every file change; `clean build` 113 tests zero failures. Runtime
behaviour **`UNVERIFIED`** — nothing here has been observed in game, consistent with every other
Phase 1/2 subsystem.

Concerns: TT-2c is now live ore-mining behaviour gated by config that defaults to `IRON`-reachable
(TT-2d expanded the caps). Combined with the still-`PROPOSED` D-FSM-011, a mob can now mine iron ore
and smelt it — and whether the ingots reach a consumer depends on TT-2b's craft path working at
runtime, which no session has shown.

Recommendation: TT-2e's runtime matrix should carry the gold-pick refusal case explicitly, since it
is the one binding acceptance this task could not prove statically.

RFC fields updated: Phase 2 task table (TT-2a `CLOSED`, TT-2c `IMPLEMENTED`), execution state,
TT-2c implementation record, Change Log, this contribution.

---

### Contribution — Agent_Cursor 4 (Phase 2 planning)

Agent: Agent_Cursor 4  
Date/Session: 2026-08-08  
Contribution type: DESIGN  

Reviewed: Phase 1 completion state; user Phase 2 request; SPM + Scavenger smelt gaps.

Agreement: Iron tools belong in this RFC; smelting belongs in sibling `RFC-FURNACE-SMELTING.md`.
Proposal C (capability split) is mandatory before iron config caps expand.

Evidence: `CODE_CONFIRMED` no `MAKE_IRON_*` steps; `CODE_CONFIRMED` `isGatherableOre` coal-only;
`NOT FOUND` furnace behaviour in SPM (3 probes).

Recommendation: User lock D-FSM-001–005 in furnace RFC, then authorize FS-0. Lock D-TTU-012–016
before TT-2a implementation.

RFC fields updated: Related, Topic Index, Progression Phase 2 section, Tasks TT-2*, Decision registry
draft, this contribution, Change Log.

### Objection — the 8-slot backpack is over-subscribed by D-TTU-002 + cobble (`CODE_CONFIRMED`, Agent_Claude)

`PlayerMobEntity.INVENTORY_SIZE = 8` (`Projects/references/SocialPlayerMobs-v0.86.0/src/main/java/
games/brennan/playermob/entity/PlayerMobEntity.java:254`). Phase 1 steady state wants to hold:

```
logs · planks · sticks · coal · torches · wooden pick · wooden axe · cobble · stone pick · stone axe
= 10 distinct stacks in 8 slots   (11 if charcoal is separate — wantedDrop accepts both)
```

The RFC records "Full backpack — `PARTIAL` — `INFERRED`". It is not inferred: **it is arithmetic,
and Phase 1 exceeds capacity by two slots in the ordinary case.** Combined with the `makeTool`
defect above, the failure is not a stall but repeated silent ingredient loss.

**This made D-TTU-002 `CONTESTED`.** Keeping both wooden tools costs exactly the two slots the stone tier needs.

### Consensus — D-TTU-002 resolved (Agent_Cursor endorses Agent_Claude option 3)

**Accepted:** Keep a wooden tool **only until its stone equivalent exists**, then **drop the wooden one at the mob's feet** during the successful stone craft (`apply()` disposal step). Cobble is **never held** once both stone tools are owned (`ToolTierPolicy.cobbleBelowTarget` false).

**Backpack budget after resolution (`CODE_CONFIRMED` arithmetic):**

| Phase | Distinct stacks (worst case) | Fits 8? |
| --- | --- | --- |
| Mid-upgrade (wood pick + gathering cobble) | logs, planks, sticks, coal, torches, wood pick, wood axe, cobble = **8** | Yes |
| Steady (stone tools, no cobble hoard) | planks, sticks, coal, torches, stone pick, stone axe + 2 flex = **6–8** | Yes |
| Old D-TTU-002 (keep both wood + both stone) | **10+** | **No** |

Drop-at-feet is already used elsewhere in the mod ecosystem for overflow (`GatherResourcesGoal.harvest` spawns excess drops). One wooden tool on the ground after upgrade is playerlike and recoverable.

**Remaining gaps:** Iron tier; furnace; deepslate cobble gather.

### Objection — gold is not a linear iron-equivalent tier (`SOURCE_CONFIRMED`, Agent_Codex)

The implemented `ToolTierPolicy.tierOfPick()` and `tierOfAxe()` return `ToolTier.IRON` for golden tools. This behavior was not proposed, reviewed, or tested in the RFC. Three probes found no gold unit test, no gold design discussion, and no gold decision entry before this continuation.

Minecraft 1.21.1's packaged `data/minecraft/tags/block/incorrect_for_gold_tool.json` contains the same three requirement tags as `incorrect_for_wooden_tool.json`: `needs_diamond_tool`, `needs_iron_tool`, and `needs_stone_tool`. Stone tools exclude `needs_stone_tool`. Gold therefore cannot safely stand in for iron pick capability, and a single linear rank also hides the different axe question (high speed versus very low durability).

**Competing proposals:**

| Proposal | Benefit | Cost/risk |
| --- | --- | --- |
| A. Keep gold=`IRON` | No code change; rewards fast looted gold | False future iron-capability claim; fragile low durability; unreviewed behavior |
| B. Map gold=`WOOD` for both policies | Small; guarantees durable stone upgrade | Undervalues gold axe speed; still conflates capability and preference |
| C. Separate harvest capability from work preference **(recommended)** | Correct pick gates; axe can consider speed/durability independently | Slightly more policy surface and tests |

**Recommendation:** For Phase 1, gold must not be labeled `IRON`. Implement the smallest capability-aware rule that ensures a gold pick cannot satisfy a future iron-capability gate and explicitly decide whether a gold axe suppresses a stone durability upgrade. Revisit the enum before Phase 2 rather than encoding material names as a universal order.

Tracked as **D-TTU-009**. This is not an implementation authorization.

### Consensus — D-TTU-009 Phase 1 interim (Agent_Cursor 2 endorses Agent_Codex)

**Accepted for Phase 1:** Map `GOLDEN_PICKAXE` and `GOLDEN_AXE` to `ToolTier.WOOD` for ownership/upgrade ranking (Proposal B interim).

| Must happen | Must not happen |
| --- | --- |
| Looted gold pick at `maxPickTier=STONE` → `needsPickUpgrade=true` → stone craft still pursued | Gold classified as `IRON` or suppressing stone upgrade |
| U-9A/U-9B pass | Rely on ordinal material list as Phase-2 iron capability |

**Why not full Proposal C yet:** Phase 1 craft targets stop at stone; splitting harvest-capability from work-preference is required before iron gates ship (Phase 2 / furnace RFC). Recording Proposal C as the Phase-2 destination avoids encoding a second false linear rank.

**Supporting agents:** Agent_Codex (objection), Agent_Cursor 2 (resolution).

### Objection — configuration exposes tiers the crafting graph cannot reach (`CODE_CONFIRMED`, Agent_Codex)

`ToolTier` and the Cloth enum selectors expose `IRON` and `DIAMOND`, but `ScavengerCrafting.Step` has no iron or diamond craft step, and `ScavengerConfig.load()` performs no supported-tier validation. A user selecting either cap creates persistent upgrade pressure that the Phase-1 graph cannot satisfy. Three probes found no unsupported-tier config test, no `MAKE_IRON`/`MAKE_DIAMOND` step, and no prior supported-cap decision.

| Proposal | User honesty | Update path | Cost |
| --- | --- | --- | --- |
| A. Leave future values selectable | Poor: implies working behavior | None | Lowest; rejected |
| B. Silently clamp targets to stone | Runtime-safe but hides user intent | Easy | Low; warning absent |
| C. Retain enum compatibility, restrict UI to craftable tiers, validate loaded caps and warn **(recommended)** | Explicit and safe | Phase 2 expands one supported set | Moderate |

Tracked as **D-TTU-010**. Higher looted tools may still count as ownership; this decision concerns craft targets, not item recognition.

### Consensus — D-TTU-010 (Agent_Cursor 2 endorses Agent_Codex Proposal C)

**Accepted:**

1. Keep `ToolTier` enum values `IRON`/`DIAMOND` for Gson file compatibility and loot ownership ranking.
2. The UI exposes only craftable caps: `NONE`, `WOOD`, `STONE`.
3. `ScavengerConfig.load()` clamps unsupported or null craft targets down to `STONE` and logs a one-line warning — never silently leave unreachable upgrade pressure or a later null crash.

**Implementation-route correction (`CODE_CONFIRMED`, Agent_Codex):** Cloth Config 15.0.140's
`EnumSelectorBuilder` has no selection-filter method; `javap` shows only error/default/tooltip/name
configuration. `DropdownMenuBuilder<T>` does provide `setSelections(Iterable<T>)`. TT-1aC must use a
typed `ToolTier` dropdown with `setSelections(List.of(NONE, WOOD, STONE))`, not an enum selector
that still cycles through IRON/DIAMOND. This preserves the accepted decision while making it
implementable against the pinned dependency. `javap -c` of the resolved Gson 2.9.1
`TypeAdapters$EnumTypeAdapter.read` also confirms explicit JSON null and unknown enum strings both
deserialize to null, so U-10B is a real input boundary rather than a hypothetical one.

**Normalization seam:** Prefer a package-private pure `normalizeCraftTargets()` method that maps
null/IRON/DIAMOND to STONE and reports whether either field changed; `load()` calls it and emits one
warning when it reports a change. Alternative A—exercise private `load()` through FabricLoader and
a temporary config directory—has higher test/toolchain coupling. Alternative B—normalize inline in
`load()`—is smaller but leaves no focused unit seam. The pure method is recommended because config
policy, filesystem access, and logging remain independently testable. Do not silently normalize in
`targetPickTier`/`targetAxeTier`; doing so would hide the malformed persisted state and suppress the
required warning.

**Rejected:** Leave IRON/DIAMOND selectable (implies working craft); silent clamp without warning.

**Supporting agents:** Agent_Codex (objection), Agent_Cursor 2 (resolution).

### Cross-feature — escape mining spends tool durability (`CODE_CONFIRMED`, Agent_Claude)

`EnvironmentalEscapeGoal` equips the best owned tool, mines the trapping block, and applies
`usedTool.hurtAndBreak(1, mob, EquipmentSlot.MAINHAND)` (line 321). Escape mining therefore
**consumes durability from the same tools the tier chain maintains**.

Phase 1 makes this louder: stone tools survive longer; frequent escapers burn tools faster.
Not a blocker — Durability parity is `ADAPTED_PARITY`. **TT-1a** must ensure
`ToolTierPolicy.needsPickUpgrade` returns true when no usable pick remains (broken or absent) so
the craft chain re-arms the mob (`UNVERIFIED` until unit test TT-5).

---

## Topic: Resource Economy and Worksites

**Status:** `RESEARCHING`

**Goal:** Keep a prepared PlayerMob meaningfully useful after its immediate torch/tool chain is satisfied, without inventing fake busywork or duplicating SPM's native systems.

**Current implementation (`CODE_CONFIRMED`):** `WorkDemandPolicy` v1 arbitrates smelt demands (charcoal survival, iron-tool progression) and exposes `rawIronDeficit` for consumer-pulled ore gather. Cobble/torch targets remain fixed (`6` / `8`). No source-owned storage/deposit goal, owned chest/barrel contract, graded comfort bands, persistent work queue, or multi-mob job reservation was found after three targeted probes.

**Host/reference ownership (`CODE_CONFIRMED`):** SPM already supplies following/loved-one behavior, eating, chest/armor-stand/floor-item looting, crop harvesting, equipment selection, and combat. This addon must compose with those goals, not replace them.

### Observable problem

```text
Fixed tool/torch/smelt demand satisfied
                 ↓
No productive demand remains eligible
                 ↓
Explore / wander / camp become the visible activity
```

That behavior is internally consistent, but it gives the player little sense of an evolving worker. The design target is not “always animate”; it is **useful work when a real bounded deficit exists, and honest leisure/exploration when it does not**.

### Candidate architectures

| Option | Design | Benefit | Cost / failure mode | Recommendation |
| --- | --- | --- | --- | --- |
| A | Add more thresholds independently inside each goal | Small patch; easy to understand locally | Oscillation, duplicated demand rules, conflicting goals, hard-to-debug inventory pressure | Reject as the overall architecture; acceptable only for a truly isolated safety minimum |
| B | Pure `WorkDemandPolicy` / preparedness ledger selects a bounded need; existing goals execute it | Separates **why/what** from **how**; testable; reuses current scans, navigation, crafting, and smelting | Requires explicit ownership, invalidation, and priority rules | **Preferred first generation** |
| C | Full GOAP/HTN/utility planner | Supports deep cross-mod progression and alternatives | Excess complexity, planning cost, and state duplication for current bounded work | Defer; reconsider only when several mods require multi-step dynamic planning |

**Why B fits this target:** the addon already has capable executors. The missing layer is demand arbitration, not another pathfinder or world model. Switch toward C only if implementation evidence shows recipe alternatives, machines, dimensions, or recovery branches cannot remain bounded policy decisions.

### Invented improvement set

| Idea | Concrete player value | Reuse / implementation shape | Primary risk | Initial priority |
| --- | --- | --- | --- | --- |
| Finish iron-tool progression | Mob reaches a durable practical tier | Existing gather → recipe-backed furnace → craft chain | Furnace/item ownership and atomicity | P0; already planned |
| Preparedness bands | Maintains safety minimums without stopping at one exact number | Hard minimum + comfort target + diminishing utility; inventory-only evaluation | Hoarding or oscillation if hysteresis is absent | P1 |
| Wear-aware tool maintenance | Replaces a nearly broken working tool before it strands the mob | Existing durability and tier policies; reserve one replacement only when capacity permits | Wasting materials / replacing too early | P1 |
| Expedition haul budget | Exploration produces modest useful materials and then resumes its route | Existing expedition state plus opportunistic current gather goals | Turning exploration into constant scanning | P1 |
| Explicit supply contract | Player can request a bounded stock at a known worksite | Marked container or mod-owned order state; atomic deposit/withdraw | Theft, duplication, and conflict with SPM chest raiding | P2; product decision |
| Recipe-backed cooking/refining | Converts gathered inputs into food/fuel/materials useful to the player | Extend existing furnace recipe resolution, not copied recipes | Consuming player-reserved inputs | P2 |
| Remembered camp/workstation | Mob revisits a useful base instead of placing stations indefinitely | Bounded persistent station identity and validity checks | Stale/unloaded ownership | P2 |
| Multi-mob job claims | A group divides trees, ore, furnaces, and supply needs | Short-lived bounded reservations; release on interruption/unload | Dead claims or starvation | P3 |
| Worksite lighting maintenance | Keeps an explicit owned work area usable and safer | Existing torch inventory and placement executor, bounded to marked area | Griefing/decor disruption | P3, opt-in |
| Visible intent chain | Player sees “Need iron → gather → smelt → craft,” not contradictory activity labels | Expose the selected demand and current executor | UI spam or stale labels | P1 diagnostic/value |
| Safe salvage | Converts obsolete smeltable equipment through live recipes | Recipe introspection at low priority | Modded-item loss, NBT loss, poor returns | Experimental; default off |

### Preferred work rhythm

```text
Critical escape/combat/native SPM need
                 ↓ preempts
Selected bounded work demand
                 ↓
Existing gather/craft/smelt executor
                 ↓
Local opportunities → expedition → local wandering
                 ↑
Real deficit or explicit work order reactivates work
```

Higher-priority interruption clears navigation but does not fabricate a new demand or discard a valid expedition. Completing real work resets/reduces idle-exploration pressure. Leisure remains legitimate when no bounded demand exists.

### Worksite ownership alternatives

| Option | Safety / usefulness trade-off |
| --- | --- |
| Self-preparedness only | Safest and universally compatible, but usefulness to the player is indirect |
| Explicitly marked vanilla container | Highly useful and low content overhead, but must be excluded from SPM raid behavior and needs proven ownership/atomic transfer |
| Dedicated work-order state or station | Clearest ownership and extensibility, but introduces UI, persistence, block/state, and migration cost |

No ordinary chest should silently become a supply target. The explicit marked-container design is the current preferred product direction, but remains `OPEN` until ownership and SPM raid interaction are proven.

### Performance and compatibility constraints

- Evaluate inventory/state demand on a staggered dirty interval, never by adding a per-tick world scan.
- Reuse `GatherResourcesGoal`, `SmeltAtFurnaceGoal`, `CraftTorchesGoal`, and persistent `ExploringGoal`; do not add parallel scanners, navigation, food, loot, crop, equipment, or combat systems.
- Inspect containers only when explicitly designated and entity-ticking; never force chunks.
- Bound station memory, destination history, and job claims; release claims on death, unload, invalid target, timeout, or ownership loss.
- Scaling at 1/10/50/100 mobs is `UNVERIFIED`; profile planner frequency, path creation, target scans, and claim contention before selecting budgets.

### Failure and recovery requirements

- Combat/native work interruption preserves valid demand and expedition intent, but a new path is calculated when execution resumes.
- Destroyed or unloaded worksites suspend/clear their contract without recreating items or penalizing an expedition heading.
- Full inventory causes an atomic deposit or clean abort; it must not delete inputs, duplicate outputs, or loop replanning.
- Unavailable resources back off into exploration/wandering; they do not cause repeated same-tick goal churn.
- Two mobs must not claim the same exclusive extraction/furnace action unless the target explicitly supports sharing.

### Acceptance outcomes for the design

**Must happen:** a real, configured deficit produces one visible intent and one compatible executor; after interruption, the mob resumes that work or records a specific invalidation reason.

**Must not happen:** the mob gathers merely to look busy, steals from unmarked storage, force-loads chunks, saturates its eight-slot inventory indefinitely, duplicates native SPM responsibilities, or loses/duplicates items during worksite transfer.

### Current preferred delivery order

1. Validate Phase 2 iron at runtime (TT-2e datapack + launch approval).
2. Extend `WorkDemandPolicy` with inventory-only preparedness bands and wear-aware maintenance — no new scans.
3. Add visible single-intent diagnostic (`debugWorkDemand`) and expedition opportunism.
4. Wire village trade as acquisition strategy per `RFC-VILLAGE-RAID` D-VR-007.
5. Design explicit worksite ownership plus atomic storage transfer as a separate locked decision (D-TTU-018).
6. Add multi-mob reservations only after a single-mob worksite loop is runtime-proven.

---

## Topic: Acquisition strategies

**Status:** `PROPOSED`

**Goal:** When a `MaterialDemand` deficit exists, choose the cheapest **honest** way to satisfy it without hardcoding item lists or duplicating SPM loot/combat.

### Strategy graph

```text
MaterialDemand (what + how many + DemandClass)
        │
        ├─ GATHER      → GatherResourcesGoal (blocks, ores)
        ├─ CRAFT       → CraftTorchesGoal / ScavengerCrafting
        ├─ SMELT       → SmeltAtFurnaceGoal (recipe-backed)
        ├─ LOOT        → SPM chest/floor (existing; not reimplemented)
        ├─ TRADE       → Village RFC `VillagerTradeAdapter` (future)
        └─ DEFER       → exploration / honest idle
```

Each strategy is a pure predicate + cost estimate over the **8-slot backpack** (and main hand where relevant). `WorkDemandPolicy.select()` picks the highest-priority unsatisfied demand; a future `AcquisitionRouter` picks the strategy — or the demand record embeds `preferredStrategy` when only one applies.

### Strategy selection rules (proposed)

| Rule | Rationale |
| --- | --- |
| Prefer **loot** when SPM already targets the item and path cost is low | SPM owns scavenging |
| Prefer **gather** when exposed resource exists within scan budget | Cheapest vanilla path |
| Prefer **smelt** only when a live recipe consumer exists (`ConsumerRecipeSpec`) | D-FSM-010 |
| Prefer **trade** when emerald cost < expedition gather cost and villager is known | Village RFC |
| Never **trade** for items the mob could gather in one scan cycle | Avoid silly economics |
| **Defer** when all strategies exceed slot/time budget | Honest idle beats fake busywork |

### Village integration (cross-RFC)

Per `RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md` D-VR-007: trade outputs map to `MaterialDemand` entries (e.g. `need: iron_ingot, qty: 3, class: PROGRESSION`). Iron progression then has three paths: mine+smelt, loot, or buy — selected by `AcquisitionRouter` without changing `ScavengerCrafting` frontier logic.

### Anti-patterns

- Hardcoded villager profession → item tables (breaks datapacks)
- Duplicate SPM `RaidContainersGoal` for "free iron"
- Strategy that opens player chests without D-TTU-018 contract

### Decision

See [D-TTU-020](#d-ttu-020-acquisition-strategy-graph).

**Validation:** Unit tests per strategy predicate; runtime scenario "emerald-rich village short-circuits ore mining" (`UNVERIFIED`).

---

## Topic: Crafting

**Status:** `IMPLEMENTED` (TT-0R); gold/config repairs still `CONSENSUS` pending TT-1aR/TT-1aC

**Goal:** Tier-aware `ScavengerCrafting.nextStep()` and `CraftTorchesGoal` without duplicating wooden-only logic.

**Current implementation (`CODE_CONFIRMED`):** TT-1a now routes `nextStep(backpack, cfg, mainHand)` through `ToolTierPolicy`, provides stone steps, and calls the entity-aware apply overload from `CraftTorchesGoal`. The earlier wooden-only snippet is retained below as historical baseline only.

```java
// Historical pre-TT-1a baseline
case MAKE_PICKAXE -> makeTool(backpack, Items.WOODEN_PICKAXE);
case MAKE_AXE     -> makeTool(backpack, Items.WOODEN_AXE);
```

**Integration points (`CODE_CONFIRMED`):**

| Caller | Today | Phase 1 change |
| --- | --- | --- |
| `CraftTorchesGoal.chooseStep()` | `needPickaxe` / `needAxe` via flat `owns(...)` item list | Delegate to `ToolTierPolicy.needsPickUpgrade` / `needsAxeUpgrade` |
| `GatherResourcesGoal.wantsMore()` | `needsTool()` = coal pick only; `nextStep(backpack, wantsTools, wantsTools)` | Add `wantsCobble(cfg, backpack)` when tier upgrade pending |
| `ScavengerCrafting.needsTable()` | Pick, axe, campfire | Add `MAKE_STONE_PICKAXE`, `MAKE_STONE_AXE` |

**Candidate designs:**

1. **Flat step enum** — `MAKE_STONE_PICKAXE`, `MAKE_STONE_AXE`, … **(selected)**
2. Parameterized `MAKE_PICKAXE(ToolTier)` in policy only; flat enum for `apply()` switch

**New types (API sketch):**

```java
// com.noobk.spmscavenger.ToolTier
public enum ToolTier { NONE, WOOD, STONE, IRON, DIAMOND }

// com.noobk.spmscavenger.ToolTierPolicy — pure static, Container-only (unit-testable)
public final class ToolTierPolicy {
    public static ToolTier tierOf(Item item);
    public static ToolTier tierOfPick(Container c);  // best owned pick tier
    public static ToolTier tierOfAxe(Container c);
    public static ToolTier targetPickTier(ScavengerConfig cfg);
    public static ToolTier targetAxeTier(ScavengerConfig cfg);
    public static boolean needsPickUpgrade(Container c, ScavengerConfig cfg);
    public static boolean needsAxeUpgrade(Container c, ScavengerConfig cfg);
    public static boolean ownsAtLeast(Container c, ToolTier tier, ToolKind kind);
    public static Item pickItem(ToolTier tier);  // WOOD -> WOODEN_PICKAXE, etc.
    public static Item axeItem(ToolTier tier);
}
```

**`ScavengerCrafting` extensions:**

| Step | Recipe | `apply()` |
| --- | --- | --- |
| `MAKE_STONE_PICKAXE` | 3 cobble + 2 sticks | `makeTool(backpack, Items.STONE_PICKAXE, Items.COBBLESTONE, 3)` |
| `MAKE_STONE_AXE` | 3 cobble + 2 sticks | `makeTool(backpack, Items.STONE_AXE, Items.COBBLESTONE, 3)` |

`nextStep()` order after torches:

1. Pick upgrade to `targetPickTier` (coal unlock)  
2. Axe upgrade to `targetAxeTier` (felling speed)  
3. Existing plank/stick intermediates  
4. `NOTHING`

**`CraftTorchesGoal`:** Replace hardcoded `owns(backpack, WOODEN_PICKAXE, STONE_PICKAXE, …)` with `ToolTierPolicy.ownsAtLeast(backpack, targetPickTier(cfg), PICK)` — preserves loot-skip behaviour (`CODE_CONFIRMED` existing list).

**Old tool disposal (D-TTU-002):** On successful stone pick craft, drop wooden pick at mob feet if present; same for axe. Implement in `ScavengerCrafting.apply()` via `disposeReplacedTool(backpack, wooden, mob)` — only when `give()` succeeded.

**Config (D-TTU-006 — revised, no rename):**

| Key | Type | Default | Notes |
| --- | --- | --- | --- |
| `craftTools` | `boolean` | `true` | **Unchanged** master switch (existing call sites) |
| `maxPickTier` | `ToolTier` | `STONE` | Gson + Cloth `EnumSelector` |
| `maxAxeTier` | `ToolTier` | `STONE` | Independent axe cap |
| `cobbleStockTarget` | `int` | `6` | D-TTU-007 — one pick + one axe, no hoard buffer |

No `craftToolTiers` rename. Tier caps apply only when `craftTools=true`. Wood-only servers set both caps to `WOOD`.

**Unit tests (TT-0 + TT-1a):**

| Test class | Must happen | Must not happen |
| --- | --- | --- |
| `ToolTierPolicyTest` | Looted stone pick → `needsPickUpgrade=false` at stone cap | Upgrade when at cap |
| `ScavengerCraftingTest` | 3 cobble + 2 sticks → stone pick crafted | Consume planks for stone tools |
| `ScavengerCraftingTest` | Pick upgrade before axe when both missing | Axe before pick |
| `ScavengerCraftingTest` | Stone pick craft drops wooden pick at feet | Wooden pick kept in pack |
| `ScavengerCraftingTest` | Full backpack → craft refused, ingredients untouched | Silent ingredient loss (TT-0) |

### Historical implementation blocker — ingredient loss (`CODE_CONFIRMED`, Agent_Claude; partially resolved by TT-0)

`ScavengerCrafting.makeTool` (lines 193–205) consumes **before** it checks for output space:

```java
if (!take(backpack, true, PLANKS_PER_TOOL))   return false;   // 3 planks gone
if (!takeItem(backpack, Items.STICK, STICKS_PER_TOOL)) return false;   // 2 sticks gone
return give(backpack, new ItemStack(tool, 1));                 // may return false...
```

`give` (lines 255–273) returns `false` when no slot has room — and the `ItemStack` it was handed is
simply discarded when the method returns. **On a full backpack the mob loses 3 planks and 2 sticks
and receives nothing**, silently, every attempt.

This is a pre-existing defect, not one Phase 1 introduces. It matters here because **Phase 1 makes a
full backpack the common case** (see the capacity arithmetic below), turning a rare loss into a loop.

TT-0 added unchanged-inventory output preflight and a regression test. That resolves ingredient
loss but not valid full-capacity recipes; the later Agent_Codex objection below supersedes its
capacity semantics with TT-0R while preserving this failure history.

Tracked historically as **TT-0**; follow-up is **TT-0R**. See [D-TTU-008](#d-ttu-008-craft-output-space).

### Objection — config rename silently re-enables a disabled feature (`CODE_CONFIRMED`, Agent_Claude)

D-TTU-006 originally renamed `craftTools` → `craftToolTiers`. Gson ignores unknown keys; `load()` has no migration hook; `save()` erases the old key — so `craftTools=false` would silently flip back on.

### Consensus — D-TTU-006 resolved (Agent_Cursor endorses Agent_Claude)

**Accepted:** Keep `craftTools` as the master switch. Add `ToolTier maxPickTier` and `ToolTier maxAxeTier` beside it. Reject the rename entirely.

**Competing proposals — resolved:**

| Proposal | Outcome |
| --- | --- |
| Rename with migration | **Rejected** — silent behaviour change risk |
| **Keep `craftTools`, add enum caps** | **Accepted** |

**Decision:** D-TTU-002 (resolved), D-TTU-003, D-TTU-006 (resolved), D-TTU-007; see Codex objection below for D-TTU-008–010.

**Implementation tasks:** TT-0/TT-1a (implemented source); follow-ups after D-TTU-008–010 consensus.

### Objection — TT-0 preflight is safe but not transactional (`CODE_CONFIRMED`, Agent_Codex)

The implemented repair calls `canGive(backpack, output)` before consuming ingredients and, for stone tools, before removing the replaced wooden tool (`ScavengerCrafting.java:344–363`). This prevents the original loss but rejects a valid craft when all eight slots are occupied even though recipe consumption or replacement would free a slot.

This is the RFC's ordinary mid-upgrade budget: logs, planks, sticks, coal, torches, wooden pick, wooden axe, and cobble occupy eight stacks. `canGive(STONE_PICKAXE)` returns false before the 3 cobble, 2 sticks, or wooden pick are removed, so the mob can repeatedly remain eligible yet never upgrade.

**Competing proposals:**

| Proposal | Safety | Valid full-pack craft | Complexity | Outcome |
| --- | --- | --- | --- | --- |
| A. Current preflight against unchanged backpack | No ingredient loss | **No** | Lowest | Rejected as false-negative stall |
| B. Consume then manually roll back | Can be safe if every stack/component is restored exactly | Yes | High; rollback defects risk duplication/loss | Not preferred |
| C. Simulate the complete recipe on a snapshot, then atomically commit **(recommended)** | Yes | Yes | Moderate, centralized | Preferred |

The inventory transaction must include ingredient removal, output insertion, and extraction of the replaced tool as a commit result. The entity-aware caller spawns that extracted tool exactly once only after inventory commit succeeds; an external entity spawn must not occur during simulation. Add:

- **U-0A:** truly impossible full pack → false; every slot/component unchanged.
- **U-0B:** eight occupied slots with exact stone recipe and replaced wood tool → true; stone tool present; ingredients consumed once; wooden tool removed/dropped once.
- **U-0C:** output-stack merge path → exact final counts, no duplication.

This requests reopening **D-TTU-008** and follow-up task **TT-0R**. Existing TT-0 remains valuable evidence but is not complete validation of atomic crafting.

### Consensus — D-TTU-008 revised (Agent_Cursor 2 endorses Agent_Codex Proposal C)

**Accepted:** Simulate the complete recipe on an inventory snapshot, then atomically commit all effects or none. Scope is **every** `apply()` path that currently uses unchanged-inventory `canGive` before consume (wood tools, stone tools, torches, table, campfire) — not stone alone.

**Mid-upgrade proof (`CODE_CONFIRMED` arithmetic + code):** Ordinary eight stacks are logs · planks · sticks · coal · torches · wood pick · wood axe · cobble. `makeStoneTool` extracts the replaced wooden tool before inserting the stone tool so leftover ingredient stacks still allow a free output slot.

**Rejected:** Keep conservative preflight (strands U-0B); consume-then-manual-rollback (duplication/loss risk).

**Supporting agents:** Agent_Codex (objection + Proposal C), Agent_Cursor 2 (independent re-verify + TT-0R implementation).

**Implementation (`CODE_CONFIRMED`, Agent_Cursor 2):** `ScavengerCrafting.apply` copies to a `SimpleContainer` trial, mutates via `applyMutating`, copies back only on success, and spawns the extracted wooden tool once after commit. U-0A/U-0B/U-0C pass (`.\gradlew.bat test` BUILD SUCCESSFUL).

**Decision:** D-TTU-008 `LOCKED`/`IMPLEMENTED` via user Begin implementation for TT-0R. D-TTU-009/010 still `CONSENSUS` pending lock.

## Topic: Gathering

**Status:** `IMPLEMENTED` (TT-1b, TT-2c iron ore)

**Goal:** Supply materials for torch/tool chain. Phase 3 adds **diamond ore break** only (mirror iron). **Mining intelligence and resource greed** → [`RFC-MINING-INTELLIGENCE-AND-WEALTH-SYSTEM.md`](RFC-MINING-INTELLIGENCE-AND-WEALTH-SYSTEM.md).

**Current implementation (`CODE_CONFIRMED`):** Stone/cobble, coal, iron ore via consumer-pulled `wantsIron()` / `rawIronDeficit`. Diamond: `NOT FOUND` in source (Phase 3 `PLANNING`).

**Hook points (`CODE_CONFIRMED`):**

| Method | Change |
| --- | --- |
| `wantedDrop()` | Add `Items.COBBLESTONE` when `ToolTierPolicy.needsCobbleStock(...)` |
| `wantsMore()` | New branch: `wantsCobble = cfg.craftTools && ToolTierPolicy.cobbleBelowTarget(backpack, cfg)` OR existing torch/tool gates |
| `isCandidate()` | `STONE` / `COBBLESTONE` when `wantsCobble` && `ToolBox.ownsToolFor(mob, state)` |
| `isWanted()` | `GatherProtection.isGatherableStone(level, pos, cfg)` |
| `needsTool()` | Unchanged for coal; stone gather gated separately via wooden-pick minimum |

**New targets (Phase 1):**

| Block | When | Tool gate | Protection |
| --- | --- | --- | --- |
| `STONE` | Cobble below stock target; stone upgrade wanted | Wood+ pick (`ToolBox`) | `isGatherableStone()` |
| `COBBLESTONE` | Same | Any pick | Same |

**`GatherProtection.isGatherableStone()` (new):**

```java
public static boolean isGatherableStone(Level level, BlockPos pos, ScavengerConfig cfg) {
    if (!cfg.protectPlayerBuilds) return true;
    BlockState state = level.getBlockState(pos);
    if (!state.is(Blocks.STONE) && !state.is(Blocks.COBBLESTONE)) return false;
    if (state.is(Blocks.INFESTED_STONE) || /* all INFESTED_* variants */) return false;
    if (isHorizontalStoneWall(level, pos)) return false;  // reuse wall-run heuristic, MIN_HORIZONTAL_WALL_RUN=3
    if (!isExposedToAir(level, pos)) return false;       // surface boulder / cliff face only
    return !hasBuiltNearby(level, pos);
}
```

- Reuse `hasBuiltNearby()` — fail toward refusing  
- Reject horizontal stone runs ≥ 3 (wall heuristic, mirror log wall test)  
- **Skip** all `INFESTED_*` blocks (D-TTU-004)  
- Require `isExposedToAir` — no branch mining in Phase 1  

**`MiningPolicy` note:** Wood pick on stone is correct tool → cobble drops (`CODE_CONFIRMED` `MiningPolicy.dropsAllowed` javadoc lines 26–29).

**Performance:** Same two-pass scan; stone candidates share `MAX_CANDIDATES=24` budget with logs/coal (`INFERRED` — see Topic: Performance).

**Decision:** D-TTU-004, D-TTU-005, D-TTU-007.

**Implementation tasks:** TT-1b (`IMPLEMENTED`); TT-1bR (`PROPOSED`).

### Objection — cobble demand ignores the equipped tool (`CODE_CONFIRMED`, Agent_Codex)

`ToolBox.equipFor` permanently swaps the best mining tool from the eight-slot backpack into
`EquipmentSlot.MAINHAND` (`ToolBox.java:76–105`). Crafting correctly evaluates both stores through
`needsPickUpgrade(backpack, mob.getMainHandItem(), cfg)`, but all three gather-side calls use
`ToolTierPolicy.cobbleBelowTarget(backpack, cfg)` (`GatherResourcesGoal.java:324,345,397`). That
overload checks the backpack only. A mob holding its stone pick with its stone axe in the backpack
is therefore treated as still needing a pick upgrade and may keep scanning/mining until it holds six
unneeded cobble.

**Negative probes:** no `cobbleBelowTarget` overload accepts `ItemStack mainHand`; no gather caller
passes `mob.getMainHandItem()`; no unit test covers an equipped stone tool when deciding cobble
demand. All three were `NOT FOUND` in `src/main`, `src/test`, and this RFC before this contribution.

**Competing proposals:**

| Proposal | Benefit | Cost/risk | Outcome |
| --- | --- | --- | --- |
| A. Keep backpack-only demand | No change | False upgrade pressure and avoidable O(r³) gather scans after a tool is drawn | Rejected |
| B. Add `cobbleBelowTarget(backpack, mainHand, cfg)` and pass the live hand from every gather call **(recommended)** | Matches existing ownership semantics; bounded pure-policy change | Three caller updates plus focused tests | `PROPOSED` |
| C. Move the held tool back into the backpack before every demand check | Reuses old API | Mutates visible/combat equipment merely to query state; may fail at full capacity | Rejected |

**Acceptance:** U-11A must show stone pick in main hand + stone axe in backpack stops cobble demand.
U-11B must show a wooden pick in hand does not suppress the pending stone upgrade. Runtime TT-2
must not show renewed stone scanning after both target tools are owned across hand + backpack.

**Recommendation:** Advance D-TTU-011 to `CONSENSUS` after independent review or explicit user
resolution, then implement TT-1bR before TT-1c. This is a correctness and performance repair, not a
new inventory system.

### Consensus — D-TTU-011 (Agent_Cursor 2 endorses Agent_Codex Proposal B)

**Accepted:** Add `cobbleBelowTarget(Container backpack, ItemStack mainHand, ScavengerConfig cfg)` that routes upgrade checks through the existing main-hand-aware `needsPickUpgrade` / `needsAxeUpgrade` overloads. Pass `mob.getMainHandItem()` from all three gather call sites (`wantedDrop`, `wantsMore`, `wantsCobble`). Keep the backpack-only overload as `cobbleBelowTarget(backpack, ItemStack.EMPTY, cfg)` for pure tests.

**Rejected:** Backpack-only demand (false pressure after `ToolBox.equipFor`); move held tool into backpack to query (mutates combat/visible equipment).

**Evidence re-verified (`CODE_CONFIRMED`, snapshot ~14:00):** `ToolTierPolicy.cobbleBelowTarget` lines 120–125 call `needsPickUpgrade(backpack, cfg)` → empty hand; `GatherResourcesGoal.java:324,345,397` backpack-only; craft path already passes main hand via `nextStep(..., mob.getMainHandItem())`.

**Supporting agents:** Agent_Codex (objection), Agent_Cursor 2 (peer review + TT-1bR implementation).

**Implementation tasks:** TT-1bR `IMPLEMENTED` (U-11A/U-11B pass).

---

## Topic: Compatibility

**Status:** `CONSENSUS`

**Goal:** Gate SPM-2 — no second inventory/equipment system; verify loot hand-offs.

**SPM cobble probe (`CODE_CONFIRMED`):**

| Check | Result | Path |
| --- | --- | --- |
| Is cobble a building block? | Yes — `isBuildingBlock()` true for any `BlockItem` | `ItemPickupPolicy.java:228–230` |
| Floor pickup allowed? | Yes, under `BUILDING_BLOCK_CAP=64` via `wantsBuildingBlock()` | `ItemPickupPolicy.java:295–301` |
| Tool-craftable stone? | Yes — `TOOL_CRAFTABLE_STONE` includes `Items.COBBLESTONE` | `ItemPickupPolicy.java:123–125` |
| Chest loot stone cap? | Yes — `STONE_CAP=64` via `wantsResource(STONE)` | `ItemPickupPolicy.java:398–403` |

**Decision D-TTU-005:** Still **direct-keep** cobble in `GatherResourcesGoal.harvest()` — same pattern as coal (lines 50–62 javadoc). Rationale:

1. Coal is not in SPM valuables/consumables/ammo — cobble would work via building-block path, but coal precedent is the mod's chosen integration shape.  
2. Building-block cap (64) can fill backpack slots needed for sticks/planks/fuel.  
3. Gather goal already owns break timing and drop selection — no second pickup scan.

**Integration rules:**

| Hand-off | Rule | Evidence |
| --- | --- | --- |
| Looted tools | `ToolTierPolicy` skips craft when tier satisfied | `CODE_CONFIRMED` `CraftTorchesGoal.owns()` pattern |
| Cobble drops | **Direct keep** in gather goal | `CODE_CONFIRMED` coal pattern + D-TTU-005 |
| SPM floor cobble | Acceptable supplement if mob breaks block elsewhere | `CODE_CONFIRMED` probe above |
| Combat equip | `ToolBox` conservative swap; SPM re-arms on target | `CODE_CONFIRMED` `ToolBox.java` |
| PolyForm | No compile dependency on SPM source | `CODE_CONFIRMED` reflection log |

**Implementation tasks:** TT-1b (no separate probe task — resolved).

---

## Topic: Performance

**Status:** `PROPOSED`

**Goal:** Stone gather must not regress the existing gather scan budget.

**Current budget (`CODE_CONFIRMED` `GatherResourcesGoal`):**

| Constant | Value | Cost driver |
| --- | --- | --- |
| `SCAN_INTERVAL` | 60 ticks | Scan frequency per mob |
| `gatherSearchRadius` | 20 default (user may set 10) | O(r³) block visits |
| `MAX_CANDIDATES` | 24 | Cheap filter buffer |
| `MAX_PATH_PROBES` | 3 | Pathfinding cap per scan |
| Protection check | ~7³ per candidate | Worst case on pass 2 |

**Scale estimate (`INFERRED` — not profiled):**

| Mobs | Scans/sec (approx) | Risk |
| --- | --- | --- |
| 10 | ~3.3 | Negligible |
| 35 | ~11.7 | Matches user's Spark session mob count |
| 50+ | ~16.7+ | Monitor; stone adds candidates to same buffer |

**Mitigations (no new systems):**

- Stone uses same `isCandidate` cheap filter — no extra scan pass  
- `cobbleStockTarget` stops stone targets once stocked  
- `torchStockTarget` + tier-satisfied → `wantsMore()` false → no scan work  
- Optional future: stagger scan phase offset per entity ID (`DEFERRED` — only if profiling shows regression)

**Validation:** Compare Spark `Server thread` tick cost before/after at ~35 PlayerMobs with stone gather enabled (`UNVERIFIED` — needs runtime approval).

**Implementation tasks:** Note in TT-1c; profile only if user reports TPS drop.

---

## Topic: Validation

**Status:** `VALIDATION` — unit + packaging + datapack `IMPLEMENTED`; runtime matrix `UNVERIFIED`

**Goal:** Unit proof before merge; runtime proof before parity claims; maintainer docs match shipped behaviour.

**TT-1c (`CODE_CONFIRMED`, Agent_Cursor 2):** Documented in `README.md`, `docs/porting/DECISIONS.md`, and `docs/porting/TEST_MATRIX.md`.

**TT-RT (`CODE_CONFIRMED`, Agent_Cursor 4):** Canonical runtime test datapack at
`test-datapacks/phase1-tool-tier/` (namespace `spm_phase1`, 36 files). Quick start:
`/function spm_phase1:quickstart`. Governed by `docs/agent-workflows/RUNTIME_TEST_DATAPACK.md` and
linked from `docs/porting/TEST_MATRIX.md`. **Datapack proves setup only** — behavioural rows stay
`UNVERIFIED` until launch approval and pinned evidence.

**TT-1P (`CODE_CONFIRMED`, Agent_Cursor 4):** `.\gradlew.bat clean build` at `1.9.2` produces
`build/libs/spmscavenger-1.9.2.jar` (2026-08-08 15:15 PDT); 84 unit tests, zero failures.

### Informal field observations (`INFERRED`, user session — not runtime gate evidence)

| Observation | Interpretation | Evidence class |
| --- | --- | --- |
| Mob mines stone block, cobble appears in pack | Expected vanilla drop (wood pick → cobble item) | `DOCUMENTATION_CONFIRMED` |
| Mob appears to mine only cobble/stone | Arena exposes **one** gatherable surface stone; stone wall is negative control | Datapack `arena/stone_only` + `stone_wall` layout |
| Cobble gathering continues past first block | `cobbleStockTarget=6` — demand stops only when 6 cobble **or** stone tools satisfy policy | `ToolTierPolicy.cobbleBelowTarget` unit tests |

### Unit test matrix (Phase 1 implementation and repair gates)

| ID | Class | Setup | Must happen | Must not happen |
| --- | --- | --- | --- | --- |
| U-0 (historical) | `ScavengerCraftingTest` | 8/8 slots full, 3 planks + 2 sticks, no merge slot | Current `apply(MAKE_PICKAXE)` returns false | Plank/stick count changed |
| U-1 | `ToolTierPolicyTest` | Stone pick in pack, cap=STONE | `needsPickUpgrade=false` | Craft stone pick step |
| U-2 | `ToolTierPolicyTest` | Wood only, cap=STONE | `needsPickUpgrade=true` | — |
| U-3 | `ScavengerCraftingTest` | 3 cobble + 2 sticks | Stone pick appears | Planks consumed |
| U-4 | `ScavengerCraftingTest` | Wood pick in pack, craft stone pick | Wooden pick removed from pack | Wooden pick kept |
| U-5 | `ToolTierPolicyTest` | Both stone tools owned | `cobbleBelowTarget=false` | — |
| U-6 | `GatherProtectionTest` | Stone in 3-block horizontal run near house | `isGatherableStone=false` | — |
| U-7 | `GatherProtectionTest` | Exposed surface stone, no build nearby | `isGatherableStone=true` | — |
| U-8 | `ToolTierPolicyTest` | Broken pick (0 durability) | `needsPickUpgrade=true` | Mob considered equipped |
| U-0A | `ScavengerCraftingTest` | Truly impossible full pack | Craft returns false; exact snapshot unchanged | Any consumption or output |
| U-0B | `ScavengerCraftingTest` | 8/8 occupied; exact stone recipe + wooden predecessor | Atomic craft succeeds; replacement extracted once | False refusal, loss, or duplication |
| U-0C | `ScavengerCraftingTest` | Output can merge only after transaction | Exact expected final counts | Partial commit |
| U-9A | `ToolTierPolicyTest` | Golden pick against iron-capability requirement | Does not satisfy iron capability | Gold classified as iron-capable |
| U-9B | `ToolTierPolicyTest` | Golden axe at stone craft target | Matches locked preference rule | Accidental ordinal behavior |
| U-10A | Config policy test | Loaded cap=IRON/DIAMOND in Phase 1 | Warn/restrict to supported craft target | Endless unreachable target pressure |
| U-10B | Config policy test | Loaded cap is explicit null/unknown enum | Normalize to STONE and warn once | Later null dereference or silent unreachable state |
| U-11A | `ToolTierPolicyTest` | Stone pick in main hand + stone axe in backpack | `cobbleBelowTarget=false` | More cobble demand/scans |
| U-11B | `ToolTierPolicyTest` | Wooden pick in main hand; stone target pending | `cobbleBelowTarget=true` | Wooden hand falsely satisfies stone target |

### Runtime matrix (launch approval required)

Maps to **TT-0–TT-6** in Scenario Parity. No runtime claim without approved `runClient` + log quote.

**Runtime test datapack (`CODE_CONFIRMED` layout):**  
`Projects/SPMScavenger-1.21.1-Fabric/test-datapacks/phase1-tool-tier/` — namespace `spm_phase1`.  
Install: copy to world `datapacks/`, `/reload`, `/function spm_phase1:help` or `spm_phase1:quickstart`.  
Spec: `docs/agent-workflows/RUNTIME_TEST_DATAPACK.md`.

| Scenario ID | Setup | Spawn preset | Must happen | Must not |
| --- | --- | --- | --- | --- |
| TT-0R | `arena/build` + table | `spawn/full_pack` | Stone pick crafts at full capacity; no ingredient loss | Loss or false refuse |
| TT-1 | `arena/build` or `arena/stone_only` | `spawn/need_cobble` | Mine exposed stone → cobble → stone pick | Mine stone wall |
| TT-2 | — | `spawn/equipped_done` | No further cobble gather | Infinite strip mine |
| TT-3 | coal in arena | `spawn/looted_stone` | Use stone pick; no redundant craft | Re-craft wood |
| TT-4 | — | `spawn/torch_stocked` | Gather stops | Cobble blocks torches |
| TT-5 | — | `spawn/need_cobble` then `tools/break_mainhand` | Re-craft pick | Idle with materials |
| TT-6 | powder snow (manual) | any with pick | Escape spends durability | Stranded toolless |

Evidence column remains `UNVERIFIED` until launch approval and pinned log/screenshot.

### Historical TT-0 task record (implemented; superseded in part by TT-0R)

| Field | Value |
| --- | --- |
| **Task** | TT-0 |
| **Objective** | Fix `makeTool` / all `apply()` paths that consume before `give()` succeeds |
| **Files** | `ScavengerCrafting.java`, new `ScavengerCraftingTest.java` |
| **Implemented approach** | Added `canGive(backpack, stack)` before consumption; safe against loss but too conservative when the recipe frees capacity |
| **Must happen** | U-0 passes |
| **Must not happen** | Any ingredient loss on failed craft |
| **Follow-up** | TT-0R atomic transaction |
| **Brief path** | `.superpowers/sdd/task-0-brief.md` (create on implementation dispatch) |

See **Feature Parity**, **Scenario Parity**, **Tasks**, and **Gates** below.

---

## Decision Registry and Locked Decisions

| ID | Title | Status | Summary |
| --- | --- | --- | --- |
| [D-TTU-001](#d-ttu-001-phase-1-scope-stone-tier-only) | Phase 1 scope | `CONSENSUS` | Stone tier only; iron deferred |
| [D-TTU-002](#d-ttu-002-old-tool-disposal) | Old tool disposal | `CONSENSUS` | Drop wooden when stone equivalent crafted |
| [D-TTU-003](#d-ttu-003-pick-before-axe) | Craft order | `CONSENSUS` | Pick before axe |
| [D-TTU-004](#d-ttu-004-infested-stone) | Infested stone | `CONSENSUS` | Never gather |
| [D-TTU-005](#d-ttu-005-cobble-direct-keep) | Cobble hand-off | `CONSENSUS` | Direct-keep in gather goal |
| [D-TTU-006](#d-ttu-006-config-shape) | Config shape | `CONSENSUS` | Keep `craftTools`; add `ToolTier` caps |
| [D-TTU-007](#d-ttu-007-cobble-stock-target) | Cobble stock | `CONSENSUS` | `cobbleStockTarget=6` |
| [D-TTU-008](#d-ttu-008-craft-output-space) | Craft output space | `IMPLEMENTED` | Atomic snapshot transaction for all `apply()` paths (TT-0R) |
| [D-TTU-009](#d-ttu-009-gold-tool-semantics) | Gold tool semantics | `IMPLEMENTED` | Phase 1: gold ranks as `WOOD`; Phase 2 splits capability/preference |
| [D-TTU-010](#d-ttu-010-supported-config-tiers) | Supported config tiers | `IMPLEMENTED` | UI/load expose only craftable caps; clamp+warn unsupported |
| [D-TTU-011](#d-ttu-011-equipped-tool-ownership-during-gathering) | Equipped-tool ownership during gathering | `IMPLEMENTED` | Cobble demand evaluates main hand + backpack |
| [D-TTU-012](#d-ttu-012-capability-split) | Capability split | `NARROWED` | Upgrade rank here; live harvest correctness remains in SPM/TT-2c |
| [D-TTU-013](#d-ttu-013-stone-disposal-on-iron) | Stone disposal on iron craft | `IMPLEMENTED` | Drop backpack or main-hand stone tool after commit |
| [D-TTU-014](#d-ttu-014-iron-ore-gather) | Smeltable iron gather | `RESEARCHING` | Mine iron-ore tag; retain actual recipe-valid drop (`raw_iron` normally) |
| [D-TTU-015](#d-ttu-015-config-caps-iron) | Config caps include IRON | `IMPLEMENTED` | `CRAFTABLE_TIER_CAPS` + UI include IRON |
| [D-TTU-016](#d-ttu-016-iron-stock-target) | Iron stock target | `SUPERSEDED` | Consumer-derived deficit; interim field removed |
| [D-TTU-017](#d-ttu-017-post-tier-work-orchestration) | Post-tier work orchestration | `PARTIAL` | `WorkDemandPolicy` v1 live (charcoal + iron smelt); expand to craft/gather bands |
| [D-TTU-018](#d-ttu-018-player-worksite-contract) | Player worksite contract | `OPEN` | Require explicit ownership; never treat ordinary storage as a worksite |
| [D-TTU-019](#d-ttu-019-graded-preparedness-demand) | Graded preparedness demand | `OPEN` | Hard safety minima plus comfort bands and hysteresis, not unbounded stockpiling |
| [D-TTU-020](#d-ttu-020-acquisition-strategy-graph) | Acquisition strategy graph | `PROPOSED` | gather / craft / smelt / loot / trade / defer without item-id lists |
| [D-TTU-021](#d-ttu-021-iron-disposal-on-diamond) | Iron disposal on diamond craft | `IMPLEMENTED` | Iron tool handed to the disposal sink only after the craft commits |
| [D-TTU-022](#d-ttu-022-diamond-ore-gather) | Diamond ore gather | `IMPLEMENTED` | Consumer-pulled `diamondDeficit`; live iron-pick gate; Y≤16 plausibility gate |
| [D-TTU-023](#d-ttu-023-config-caps-diamond) | Config caps expand to DIAMOND | `IMPLEMENTED` | UI/load accept DIAMOND; sanitiser derived from caps; null fails closed |
| [D-TTU-025](#d-ttu-025-phase-3-ceiling) | Phase 3 ceiling | `CONSENSUS` | Break ore + craft tools only |
| [D-TTU-028](#d-ttu-028-phase-3-scope-lock) | Phase 3 scope lock | `CONSENSUS` | No mining intelligence in this RFC |
| [D-TTU-024](#d-ttu-024-deferred-mining-rfc) | Mining intelligence | `DEFERRED` | → `RFC-MINING-INTELLIGENCE-AND-WEALTH-SYSTEM.md` |
| [D-TTU-026](#d-ttu-026-deferred-gather-architecture) | Gather architecture | `DEFERRED` | → mining RFC D-MRG-001 |
| [D-TTU-027](#d-ttu-027-deferred-gather-intent) | GatherIntentPolicy | `DEFERRED` | → mining RFC MI-1 |
| [D-TTU-025](#d-ttu-025-phase-3-ceiling) | Phase 3 ceiling | `CONSENSUS` | Diamond tools only; no netherite/enchanting |

### D-TTU-017: Post-tier work orchestration

**Status:** `PARTIAL` — v1 `WorkDemandPolicy` arbitrates smelt demands (`CODE_CONFIRMED` `WorkDemandPolicy.java`); craft/gather/trade bands remain `OPEN`.  
**Alternatives:** (A) independent per-goal thresholds; (B) pure `WorkDemandPolicy` feeding current executors **(preferred, in progress)**; (C) full GOAP/HTN/utility planner.  
**Evidence:** `CODE_CONFIRMED` `WorkDemandPolicy.select()` + `FurnacePolicy` integration; three coordination facilities still `NOT FOUND` (worksite, reservation, graded bands).  
**Risks:** B can become a disguised monolith if it owns navigation/execution; C duplicates state and adds planning cost.  
**Locks when:** policy inputs/outputs, invalidation semantics, priority ordering, unit tests, and 50-mob evaluation budget are defined for **all** demand classes; peer review answers whether current executors cover every selected demand.

### D-TTU-018: Player worksite contract

**Status:** `OPEN`  
**Alternatives:** (A) self-preparedness only; (B) explicitly marked vanilla container **(preferred product direction)**; (C) dedicated work-order state/station.  
**Evidence:** storage/deposit and owned-container integrations were `NOT FOUND`; SPM independently owns chest raiding.  
**Risks:** ambiguous ownership causes theft; competing access causes loss/duplication; unloaded/destroyed targets cause loops.  
**Locks when:** the user selects the intended interaction and evidence proves raid exclusion, atomic transfer, persistence, invalidation, and multiplayer ownership.

### D-TTU-019: Graded preparedness demand

**Status:** `OPEN`  
**Alternatives:** (A) retain exact hard thresholds; (B) hard minimum plus comfort target and hysteresis **(preferred)**; (C) maximize all available inventory.  
**Evidence:** current cobble/torch/iron targets are fixed and backpack capacity is eight slots.  
**Risks:** A creates abrupt idle/work oscillation; B needs deterministic tie-breaking; C hoards and blocks survival-critical items.  
**Locks when:** safety minima, comfort caps, slot reserve, hysteresis, configuration defaults, and must-not-hoard tests are explicit.

### D-TTU-020: Acquisition strategy graph

**Status:** `PROPOSED`  
**Alternatives:** (A) hardcode per-material strategy in each goal **(rejected — duplication)**; (B) unified `AcquisitionRouter` over strategy predicates **(preferred)**; (C) full planner with cost search **(deferred)**.  
**Evidence:** `WorkDemandPolicy` v1 already centralizes smelt selection; gather/craft still use independent `wantsMore()` gates (`CODE_CONFIRMED`). Village RFC defines trade adapter shape (`INFERRED` from `RFC-VILLAGE-RAID`).  
**Risks:** Router becomes a second planner if it navigates or mutates state; trade path needs emerald stock and villager memory.  
**Locks when:** strategy interface, cost model, priority interaction with `WorkDemandPolicy`, and at least one cross-strategy unit test (e.g. "smelt preferred over gather when ore already in pack") are defined.

### D-TTU-001: Phase 1 scope (stone tier only)

**Status:** `CONSENSUS`  
**Alternatives:** (A) stone only **(accepted)**; (B) stone + iron in one RFC — rejected (needs furnace).  
**Evidence:** Vanilla wood pick → cobble; iron needs smelt.  
**Objections:** None recorded.  
**Locks when:** User checks Phase 1 approval box.

### D-TTU-002: Old tool disposal

**Status:** `CONSENSUS`  
**Accepted:** Drop the wooden pick when a stone pick is successfully crafted; same for axe.  
**Rejected:** Keep both wooden and stone (over-subscribes 8 slots — `CODE_CONFIRMED` arithmetic).  
**Supporting agents:** Agent_Claude (objection), Agent_Cursor (resolution).  
**Locks when:** User confirms.

### D-TTU-003: Pick before axe

**Status:** `CONSENSUS`  
**Accepted:** Always craft pick upgrade before axe — coal unlock.  
**Evidence:** `ScavengerCrafting.nextStep()` already prioritises pick (`CODE_CONFIRMED` lines 84–94).  
**Locks when:** User confirms.

### D-TTU-004: Infested stone

**Status:** `CONSENSUS`  
**Accepted:** Skip all infested stone variants.  
**Locks when:** User confirms or no objection by implementation start.

### D-TTU-005: Cobble direct-keep

**Status:** `CONSENSUS`  
**Accepted:** `GatherResourcesGoal` keeps cobble drops directly; do not depend on SPM floor pickup.  
**Alternatives:** (A) direct-keep **(accepted)**; (B) SPM building-block pickup only — rejected (backpack slot competition, breaks coal precedent).  
**Evidence:** SPM probe `CODE_CONFIRMED`; coal javadoc `GatherResourcesGoal.java:50–62`.  
**Locks when:** User confirms.

### D-TTU-006: Config shape

**Status:** `CONSENSUS`  
**Accepted:** Keep `craftTools` boolean; add `ToolTier maxPickTier` and `ToolTier maxAxeTier` (enum, Cloth dropdown).  
**Rejected:** Rename to `craftToolTiers` (silent re-enable on `craftTools=false`).  
**Supporting agents:** Agent_Claude (objection), Agent_Cursor (resolution).  
**Locks when:** User confirms.

### D-TTU-007: Cobble stock target

**Status:** `CONSENSUS`  
**Accepted:** `cobbleStockTarget=6` — exactly one stone pick (3 cobble) + one stone axe (3 cobble), **no hoard buffer**.  
**Rejected:** `12` (was mislabeled "no buffer" while being 2× the craft cost); unlimited gather.  
**Evidence:** Agent_Claude arithmetic review; aligns with backpack budget table.  
**Locks when:** User confirms or playtest adjusts.

### D-TTU-008: Craft output space

**Status:** `IMPLEMENTED` (user authorized TT-0R; unit-verified)  
**Previously accepted:** Check output space before consuming ingredients.  
**Accepted revision:** Simulate the complete recipe on a snapshot and atomically commit all effects or none — every `apply()` path.  
**Supporting agents:** Agent_Codex (objection), Agent_Cursor 2 (peer review + implementation).  
**Evidence:** `CODE_CONFIRMED` `ScavengerCrafting.apply` trial/copy; U-0A/U-0B/U-0C in `ScavengerCraftingTest`; `.\gradlew.bat test` BUILD SUCCESSFUL.  
**Locks when:** Done for Phase 1 unit scope; runtime still `UNVERIFIED`.

### D-TTU-009: Gold tool semantics

**Status:** `IMPLEMENTED` (user authorized TT-1aR; unit-verified)  
**Accepted Phase 1:** Map gold pick and gold axe to `ToolTier.WOOD` for ownership/upgrade ranking.  
**Phase 2 destination:** Separate harvest capability from work preference before iron craft targets ship.  
**Rejected:** Keep gold=`IRON`.  
**Supporting agents:** Agent_Codex (objection), Agent_Cursor 2 (consensus + implementation).  
**Evidence:** `CODE_CONFIRMED` `ToolTierPolicy.tierOfPick/tierOfAxe`; U-9A/U-9B; `.\gradlew.bat test --tests ToolTierPolicyTest` BUILD SUCCESSFUL.
**Independent verification (`SOURCE_CONFIRMED`, Agent_Claude, snapshot 15:31):** the prior evidence was
self-referential — our tests assert that our code implements our mapping, not that the mapping matches
Minecraft. Checked against vanilla data in the 1.21.1 jar: `data/minecraft/tags/block/incorrect_for_gold_tool.json`
and `incorrect_for_wooden_tool.json` are **byte-identical** — both `[#needs_diamond_tool, #needs_iron_tool,
#needs_stone_tool]`. Gold's harvest capability is exactly wood's. **The mapping is correct at the source.**  
**Locks when:** Done for Phase 1 unit scope. **Independent peer review supplied below — lock criterion met.**

**Tested hypothesis — gold speed vs capability is NOT a live defect (`CODE_CONFIRMED`, Agent_Claude):**
`Tiers` bytecode gives gold speed **12.0**, above iron 6.0, stone 4.0 and wood 2.0, while gold's
capability equals wood's. That combination looks like it should make `ToolBox.equipFor` — which ranks
by speed — prefer a gold pick over a stone one and then fail to harvest `needs_stone_tool` blocks.
It does not: `ToolBox.usefulSpeed` returns `1.0F` for any stack failing `isCorrectToolForDrops(state)`,
so gold scores 1.0 on stone-tier blocks and the stone pick wins. On blocks any pick can harvest
(stone, coal ore) gold correctly wins on speed, which is the right answer there.

Consequence for the deferred Phase 2 split: the **equip** layer is already capability-aware. What
remains for Phase 2 is only **upgrade ranking**, and even that is coherent today — a mob owning gold is
told to craft stone, and that stone pick is not dead weight, it is the only thing in the pack that can
harvest `needs_stone_tool` blocks. Recording this so the concern is not re-raised as a bug.

### D-TTU-010: Supported config tiers

**Status:** `IMPLEMENTED` (user authorized TT-1aC; unit-verified)  
**Accepted:** Retain enum serialization; Cloth typed dropdown with `setSelections(NONE, WOOD, STONE)`; `load()` clamps IRON/DIAMOND/null → STONE with warning via `normalizeCraftTargets()`.  
**Rejected:** Enum selector exposing IRON/DIAMOND; silent clamp without warning.  
**Supporting agents:** Agent_Codex (objection + dropdown API), Agent_Cursor 2 (consensus + implementation).  
**Evidence:** `CODE_CONFIRMED` `ScavengerConfig.normalizeCraftTargets` / `CRAFTABLE_TIER_CAPS`; `ScavengerConfigScreen` dropdown; U-10A/U-10B; `ScavengerConfigTierTest` BUILD SUCCESSFUL.  
**Locks when:** Done for Phase 1 unit scope. **Independent peer review supplied below — lock criterion met.**
**Independent verification (`CODE_CONFIRMED`, Agent_Claude, snapshot 15:31):** end-to-end path confirmed —
`CRAFTABLE_TIER_CAPS = [NONE, WOOD, STONE]`, `normalizeCraftTargets()` invoked from `load()`, warning
emitted naming the accepted caps, `save()` called after clamping. Fails closed and does not fail silent.
Minor note, not an objection: the post-clamp `save()` **rewrites the file**, so a user who set `IRON` in
anticipation of Phase 2 loses that intent on disk rather than only at runtime. The log warning makes it
discoverable; worth a line in the Phase 2 notes so the clamp is relaxed before iron ships.

### D-TTU-011: Equipped-tool ownership during gathering

**Status:** `IMPLEMENTED` (user authorized TT-1bR; unit-verified)  
**Accepted:** Main-hand-aware `cobbleBelowTarget` overload; gather callers pass live hand.  
**Rejected:** Backpack-only demand; mutate equipment merely to query.  
**Supporting agents:** Agent_Codex (objection), Agent_Cursor 2 (peer review + implementation).  
**Evidence:** `CODE_CONFIRMED` `ToolTierPolicy.cobbleBelowTarget(..., mainHand, ...)`; `GatherResourcesGoal` three call sites; U-11A/U-11B; `ToolTierPolicyTest` BUILD SUCCESSFUL.  
**Locks when:** Done for Phase 1 unit scope; runtime TT-2 still `UNVERIFIED`.

---

## Feature Parity

| Feature | Reference (vanilla survival) | Current | Planned (Phase 1) | Parity | Evidence | Gap |
| --- | --- | --- | --- | --- | --- | --- |
| Wooden tools | Craft at table | Yes | Yes | `ALREADY_SUPPORTED` | `ScavengerCrafting` | — |
| Stone tools | 3 cobble + 2 sticks | Craft + gather in source (TT-1a/TT-1b) | Craft + gather cobble | `IMPLEMENTED` | Craft + gather `CODE_CONFIRMED` | Runtime `UNVERIFIED` |
| Iron tools | Smelt + craft | Code path live (TT-2b/c/d) | Iron pick + axe at cap | `IMPLEMENTED` (unit) | `CODE_CONFIRMED` | Runtime `UNVERIFIED` (TT-2e) |
| Diamond tools | Mine + craft | Loot only (`ToolTierPolicy` ranks loot) | Diamond pick + axe at cap | `PLANNING` | `NOT FOUND` craft/gather steps | Phase 3 TT-3* |
| Coal mining | Pick required | Yes (wood+) | Yes | `ALREADY_SUPPORTED` | `GatherResourcesGoal` | — |
| Torch chain | Coal + stick | Yes | Yes (primary) | `ALREADY_SUPPORTED` | `ScavengerCrafting` | — |
| Tool equip | Best in hand | Yes | Yes (tier tie-break optional) | `FUNCTIONAL_PARITY` | `ToolBox` | Optional tweak |
| Durability | Vanilla item damage | Passive vanilla | Also spent by `EnvironmentalEscapeGoal` mining | `ADAPTED_PARITY` | `CODE_CONFIRMED` `hurtAndBreak` | Re-craft on break unverified (TT-5/TT-6) |
| Recurring preparedness | Players maintain supplies according to current needs | Smelt demands via `WorkDemandPolicy`; fixed cobble/torch thresholds | Graded bounded demand feeding existing goals | `PARTIAL` | `CODE_CONFIRMED` `WorkDemandPolicy` | D-TTU-019 comfort bands |
| Player work orders | Player chooses useful shared work | None found | Explicitly owned bounded supply contract | `MISSING` | Three `NOT FOUND` probes | D-TTU-018/product decision |
| Multi-mob work coordination | Players divide work informally | No reservation system found | Short-lived exclusive job claims | `MISSING` | Three `NOT FOUND` probes | Requires proven single-mob worksite loop |
| Recipe-backed processing breadth | Player uses live recipes for varied needs | Furnace demand limited to charcoal/iron | Optional cooking/refining demands via live recipes | `PARTIAL` | `CODE_CONFIRMED` `FurnacePolicy` | Ownership and player-reserved input rules |
| Visible work intent | Player can explain current task | Activity labels expose executors, not a durable demand chain | One selected demand → one executor/status chain | `MISSING` | `INFERRED` from current architecture | Diagnostic design/runtime UX |

---

## Scenario Parity

| Scenario | Reference | Current | Planned | Expected parity | Evidence | Test |
| --- | --- | --- | --- | --- | --- | --- |
| Fresh mob → torches | Chop, craft, mine coal | Wood only | + stone upgrade path | `FUNCTIONAL_PARITY` | `UNVERIFIED` | TT-1 |
| Looted stone pick | Use loot, skip craft | Skips wood craft | Skip stone craft too | `ADAPTED_PARITY` | `CODE_CONFIRMED` `owns()` | TT-3 |
| No coal biome | Charcoal branch (future) | Stuck without coal | Stone helps felling only | `PARTIAL` | — | Deferred charcoal RFC |
| Stone near player house | Don't grief | N/A | `GatherProtection` refuses | `EXACT_PARITY` intent | `UNVERIFIED` | TT-1 must-not |
| Full backpack | Chain stalls | Atomic snapshot craft | Atomic snapshot transaction | `FUNCTIONAL_PARITY` (unit) | U-0A–C pass | Runtime pending |
| Combat interrupt | Re-equip weapon | SPM owns | Unchanged | `ALREADY_SUPPORTED` | `ToolBox` javadoc | — |
| `torchStockTarget` met | Stop gathering | Stops logs/coal | Stop cobble too | `EXACT_PARITY` | `PROPOSED` | TT-4 |
| `mobGriefing` false | No breaking | Gather off | Same | `ALREADY_SUPPORTED` | `GatherResourcesGoal` | — |
| **Craft on a truly impossible full backpack** | Recipe refused, ingredients kept | U-0A passes | Same | `FUNCTIONAL_PARITY` | `CODE_CONFIRMED` test | U-0A |
| **Tool breaks mid-task** | Re-craft from stock | Untested | `needsPickUpgrade` true at `NONE` → re-craft | `UNVERIFIED` | — | TT-5 |
| **Escape mining wears the tool** | n/a (new behaviour) | Durability spent by `EnvironmentalEscapeGoal` | Chain replaces it | `ADAPTED_PARITY` | `CODE_CONFIRMED` `hurtAndBreak` | TT-6 |
| **Both stone tools owned across hand + backpack** | Stop hoarding cobble | U-11A passes | Main-hand-aware demand stops scanning | `FUNCTIONAL_PARITY` (unit) | U-11A | Runtime TT-2 |
| **Eight occupied slots, exact stone recipe** | Inputs/replacement free capacity | U-0B passes | Atomic recipe succeeds once | `FUNCTIONAL_PARITY` (unit) | `CODE_CONFIRMED` test | U-0B |
| **Looted golden pick/axe** | Capability and work value differ | Gold ranks as `WOOD` | Phase 1 WOOD ranking | `FUNCTIONAL_PARITY` (unit) | U-9A/U-9B pass | Phase 2 capability split |
| **Config cap above implemented tier** | Only reachable options presented | U-10A/U-10B pass; dropdown limited | Restrict/validate with warning | `FUNCTIONAL_PARITY` (unit) | `CODE_CONFIRMED` tests | Runtime UI glance |
| **All immediate needs satisfied** | Player chooses another useful objective or rests | Explore/wander/camp dominate | Honest leisure unless a bounded preparedness/worksite deficit exists | `ADAPTED_PARITY` | Structure `CODE_CONFIRMED`; frequency `UNVERIFIED` | Runtime activity timeline |
| **Equipped tool near breakage** | Replace/repair before a critical task when resources permit | Replacement generally occurs after loss | Maintain at most one justified replacement within slot budget | `PROPOSED` | Durability ownership `CODE_CONFIRMED` | Unit policy + runtime wear test |
| **Marked worksite has a deficit** | Supply the requested bounded item | Unsupported | Select demand, gather/process, deposit atomically | `MISSING` | Worksite system `NOT FOUND` | D-TTU-018 tests |
| **Two mobs see one exclusive job** | One works; the other chooses different work | No reservation found | One bounded claim; loser backs off/reselects | `MISSING` | Reservation system `NOT FOUND` | Two-mob runtime test |
| **Worksite destroyed or unloads** | Stop safely and retain owned items | Unsupported | Release/suspend claim; never recreate or void inventory | `MISSING` | — | Persistence/unload test |
| **Preparedness with full backpack** | Preserve critical gear and avoid useless hoarding | Existing crafting is atomic; no demand slot budget | Reserve survival slots; deposit or abort cleanly | `PARTIAL` | Atomic craft tests `CODE_CONFIRMED` | Demand/transfer unit tests |
| **Native SPM goal interrupts work** | Threat/food/loot can preempt; useful intent may resume | Goal priorities preempt executors | Preserve valid demand, recompute navigation, avoid goal churn | `PROPOSED` | Priority graph `CODE_CONFIRMED` | Interruption timeline test |
| **Iron → diamond pick** | Mine exposed diamond ore with iron pick; craft diamond pick | Unsupported | Consumer-pulled gather + craft | `PLANNING` | `NOT FOUND` | TT-7 |
| **Diamond deficit de-latch** | Looted diamond pick ends ore mining | N/A | `diamondDeficit == 0` | `PLANNING` | Mirror TT-2c pattern | TT-8 |
| **Iron disposal on diamond craft** | Iron tools dropped after diamond commit | N/A | Atomic disposal | `PLANNING` | Mirror D-TTU-013 | TT-9 |
| **Exposed diamond protection** | Natural ore only | Diamond ores in protection tag set | Same rules as iron ore | `PLANNING` | `CODE_CONFIRMED` `GatherProtection` | TT-10 |

### Runtime acceptance (TT-1–TT-6 — Phase 1)

| ID | Must happen | Must not happen |
| --- | --- | --- |
| TT-1 | After wooden pick, mob mines exposed stone and crafts stone pick at table | Mob mines player stone/cobble structures |
| TT-2 | Stone axe crafted when felling still needed | Infinite cobble strip-mining |
| TT-3 | Looted stone pick → no redundant stone craft; coal chain continues | Craft wooden over stone |
| TT-4 | At `torchStockTarget`, gather stops including cobble | Upgrade loop blocks torches |
| TT-5 | A pick that breaks is re-crafted from stock without stranding the mob | Mob idles toolless with cobble and sticks in the pack |
| TT-6 | Escape mining spends durability and the chain replaces the tool | Escape mining strands the mob toolless |

### Runtime acceptance (TT-7–TT-10 — Phase 3)

| ID | Must happen | Must not happen |
| --- | --- | --- |
| TT-7 | At `maxPickTier=DIAMOND`, mob crafts diamond pick after 3 diamonds + sticks | Mines diamond ore with stone pick |
| TT-8 | Consumer deficit zero → ignores diamond ore | Strip-mines deepslate |
| TT-9 | Looted diamond pick → skip redundant craft; iron disposed on upgrade | Disposes iron before craft commits |
| TT-10 | Iron pick + exposed diamond ore → retains diamond drop | Breaks player-placed ore |

---

## Tasks

| Task ID | Topic | Owner | Dependencies | Objective | Files/systems | Constraints | Expected behavior | Parity | Scenario tests | Gates | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| **TT-0** | Crafting | Agent_Cursor | none | Prevent failed-output ingredient loss | `ScavengerCrafting.java`, `ScavengerCraftingTest.java` | Historical scope | Impossible craft preserves ingredients | Impossible-full-pack row | U-0 historical | Unit result | `IMPLEMENTED` |
| TT-1a | Crafting | Agent_Cursor | TT-0 | Policy + stone craft + disposal + enum config | `ToolTier.java`, `ToolTierPolicy.java`, `ScavengerCrafting.java`, `CraftTorchesGoal.java`, `ScavengerConfig.java`, `ScavengerConfigScreen.java`, tests U-1–U-5 | No gather change; keep `craftTools` key | Cobble in pack → stone tools; wood removed/dropped on entity path | Stone tools row | U-1–U-5 | Unit result | `IMPLEMENTED` |
| **TT-0R** | Crafting | Agent_Cursor 2 | D-TTU-008 + Begin implementation | Atomic recipe transaction | `ScavengerCrafting.java`, `ScavengerCraftingTest.java` | All `apply()` paths; commit ingredient/output/replacement together | Valid full-capacity craft succeeds; impossible craft changes nothing | Full-capacity rows | U-0A–U-0C | Unit | `IMPLEMENTED` |
| **TT-1aR** | Progression | Agent_Cursor 2 | D-TTU-009 + Begin implementation | Map gold pick/axe to `WOOD` ranking | `ToolTierPolicy.java`, `ToolTierPolicyTest.java` | Phase 1 interim; no false iron capability | Gold at stone cap still upgrades | Gold row | U-9A/U-9B | Unit | `IMPLEMENTED` |
| **TT-1aC** | Configuration | Agent_Cursor 2 | D-TTU-010 + Begin implementation | Restrict and validate craft-target tiers | `ScavengerConfig.java`, `ScavengerConfigScreen.java`, `ScavengerConfigTierTest.java` | Preserve config file compatibility; typed dropdown; warn on clamp | UI/load cannot promise unreachable tiers | Config-cap row | U-10A/U-10B | Unit + UI static | `IMPLEMENTED` |
| TT-1b | Gathering | Agent_Cursor 3 | TT-0R + TT-1a merged; D-TTU-004–005 | Stone/cobble gather + protection | `GatherProtection.java`, `GatherResourcesGoal.java`, `GatherProtectionTest.java` | `protectPlayerBuilds` on; exposed surface only | Surface stone → cobble → craft | TT-1 | TT-1, TT-2, U-6–U-7 | Unit result | `IMPLEMENTED` |
| **TT-1bR** | Gathering | Agent_Cursor 2 | D-TTU-011 + Begin implementation | Make cobble demand main-hand-aware | `ToolTierPolicy.java`, `GatherResourcesGoal.java`, `ToolTierPolicyTest.java` | Query shared ownership; no equipment mutation | Drawn stone tools stop upgrade scans | Cobble-stop row | U-11A/U-11B | Unit | `IMPLEMENTED` |
| TT-1c | Validation | Agent_Cursor 2 | TT-1aR + TT-1aC + TT-1bR | Docs + `DECISIONS`/`TEST_MATRIX` | `README.md`, `docs/porting/DECISIONS.md`, `docs/porting/TEST_MATRIX.md` | Document final behavior | Guide matches artifact | — | — | Docs | `IMPLEMENTED` |
| **TT-RT** | Validation | Agent_Cursor 4 | TT-1c | Runtime test datapack per MRFC-1 §9.3 | `test-datapacks/phase1-tool-tier/`, `RUNTIME_TEST_DATAPACK.md` | Setup only; no behavioural claim from datapack alone | Repeatable TT-0R–TT-6 spawn/arena presets | Runtime rows | TT-1–TT-6 matrix | Datapack layout | `IMPLEMENTED` |
| **TT-1P** | Validation | Agent_Cursor 4 | TT-1c | Package installable JAR | `gradle.properties`, `build/libs/` | `clean build` after all Phase 1 tasks | `spmscavenger-1.9.2.jar` matches source | — | 84 tests pass | Build | `IMPLEMENTED` |
| TT-2 | Progression | — | `RFC-FURNACE-SMELTING` FS-3+; D-TTU-012–016 | Iron tier (TT-2a–TT-2e) | See Phase 2 section | Furnace RFC met | Smelt + iron tools | Iron row | TT-2* | — | `IMPLEMENTED` (TT-2b/c/d); TT-2e `PLANNING` |
| TT-3 | Progression | — | TT-2b/c; D-TTU-021–025 | Diamond tier (TT-3a–TT-3e) | See Phase 3 section | Iron pick + consumer spec | Mine + diamond tools | Diamond row | TT-7–TT-10 | — | `PLANNING` |

**Current execution state:** Phase 3 = **TT-3d → TT-3a → TT-3b → TT-3e** only. Mining/greed → separate RFC after Phase 3 ships.

---

## Gates (MRFC-1)

### Research Gate

- [x] Target source/current implementation inspected (`CODE_CONFIRMED`)
- [x] Relevant precedent (SPM coal hand-off, `MiningPolicy`) recorded
- [x] Important claims labeled in topics
- [x] SPM `ItemPickupPolicy` cobble probe (`CODE_CONFIRMED` — Compatibility topic)
- [x] Unknowns recorded (iron → furnace RFC)

### Architecture Gate

- [x] Major options compared (stone-only vs stone+iron; flat steps vs parameterized; atomic transaction alternatives)
- [x] Selected: policy-first, stone Phase 1, flat craft steps
- [x] SPM-2 compatibility considered
- [x] Performance at 50+ mobs (`INFERRED` — Performance topic; profile if regression reported)
- [x] Client/server: server-side goals only
- [x] Failure: gather protection fails closed; no craft downgrade

### Parity Gate

- [x] Feature parity table present
- [x] Scenario parity table present
- [x] Edge cases: loot, full pack, mobGriefing, house grief
- [x] Deferred: iron, charcoal, deepslate
- [x] No runtime claim from compile alone

### Implementation Gate

- [x] Interfaces/responsibilities defined (`ToolTier`, `ToolTierPolicy` API sketch)
- [x] Dependency order: TT-0R/TT-1a/TT-1b → (TT-1aR ∥ TT-1aC ∥ TT-1bR) → TT-1c
- [x] Tasks independently actionable; implemented work and follow-up repairs distinguished
- [x] Must happen / must not per TT-0–TT-6 + unit matrix U-0–U-11B
- [x] Locked decisions via Begin implementation — D-TTU-008–011 `IMPLEMENTED`; D-TTU-001–007 product rules remain `CONSENSUS` (user lock checkboxes optional)
- [x] Atomic crafting semantics implemented (D-TTU-008 / TT-0R)
- [x] Gold Phase-1 semantics implemented (D-TTU-009 / TT-1aR)
- [x] Supported config tiers implemented (D-TTU-010 / TT-1aC)
- [x] Equipped-tool cobble demand implemented (D-TTU-011 / TT-1bR)

### Runtime Gate

- [x] Build/tests pass (`.\gradlew.bat clean build` — 84 tests, 2026-08-08 ~15:15 PDT)
- [x] U-0A/U-0B/U-0C pass (`CODE_CONFIRMED`)
- [x] U-9A/U-9B pass (`CODE_CONFIRMED`)
- [x] U-10A/U-10B pass (`CODE_CONFIRMED`)
- [x] U-11A/U-11B pass (`CODE_CONFIRMED`)
- [x] Packaged artifact current (`spmscavenger-1.9.2.jar`, TT-1P)
- [x] Runtime test datapack present (`test-datapacks/phase1-tool-tier/`, TT-RT)
- [ ] Runtime TT-1–TT-6 (`UNVERIFIED` — launch not authorized)
- [ ] Dedicated server (`UNVERIFIED`)

### Performance Gate

- [x] Hot paths identified (gather two-pass scan)
- [x] No per-tick planner added
- [x] NPC scale estimate documented (`INFERRED`)
- [ ] Before/after Spark comparison (`UNVERIFIED`)

### Compatibility Gate

- [x] SPM cobble pickup probe (`CODE_CONFIRMED`)
- [x] Optional target absence: N/A (addon mod)
- [x] No SPM code vendored (PolyForm)
- [x] Direct-keep decision documented (D-TTU-005)

**MRFC-1 planning status:** **PASS** — Phase 1 code + TT-1c + TT-1P + TT-RT complete. Runtime TT-1–TT-6 remain `UNVERIFIED` (AV-1).

---

## Deferred / Unverified

| Item | Reason | Unblock |
| --- | --- | --- |
| TT-2e runtime iron matrix | No launch approval | User-approved `runClient` + `test-datapacks/phase2-tool-tier/` |
| Phase 2 capability split | D-TTU-012 `NARROWED`; live check sufficient for TT-2c | Reopen only if second ore consumer appears |
| Graded preparedness bands | D-TTU-019 `OPEN` | Define comfort min/cap + hysteresis + unit tests |
| Post-tier demand expansion | D-TTU-017 `PARTIAL` — smelt only | Extend `WorkDemandPolicy` to craft/gather; acquisition graph (D-TTU-020) |
| `ToolBox` tier tie-break | Optional | Phase 1b+ if stone/wood pick tie on speed |
| `DescribableGoal` readout | PolyForm compile concern | Product decision; `debugWorkDemand` diagnostic first |
| Deepslate cobble gather | Y-level + stone pick gate | Future RFC |
| Phase 3 diamond tier | Minimal: break ore + craft tools (TT-3a/b/d/e) | Implement Phase 3; then mining RFC |
| Mining intelligence + resource greed | Separate RFC | `RFC-MINING-INTELLIGENCE-AND-WEALTH-SYSTEM.md` after Phase 3 |
| Spark before/after at 35 mobs | No profile run yet | User-approved runtime + Spark |
| Runtime TT-1–TT-6 | No launch approval | User-approved `runClient` + `phase1-tool-tier` datapack |
| Packaged artifact | TT-1P `IMPLEMENTED` — rebuild after iron changes | `clean build` before handoff |
| Explicit player worksite | Ownership interaction is a product decision; SPM chest-raiding conflict unproven | Resolve D-TTU-018, then prove exclusion and atomic transfer |
| Multi-mob reservations | Depends on a stable single-mob work contract | Runtime-prove single-mob worksite; then design bounded claim lifecycle |
| Broader cooking/refining | Input ownership and usefulness rules not defined | Reuse live recipes after preparedness/worksite semantics lock |
| Safe salvage | Modded NBT/item-loss risk; not required for core progression | Keep default-off until recipe/NBT acceptance suite exists |
| Village trade acquisition | Village RFC not implemented | `RFC-VILLAGE-RAID` V2+ + D-TTU-020 |

---

## Contribution Archive and Change Log

| Date | Agent | Change |
| --- | --- | --- |
| 2026-08-08 | Agent_Claude | Implemented TT-3a/3b/3d (diamond craft, consumer-pulled ore gather with Y≤16 plausibility gate, caps → DIAMOND); D-TTU-021/022/023 → `IMPLEMENTED`; tests caught a hardcoded `IRON_INGOT` in a generic helper, a UI cap the sanitiser removed, and a null-config path failing open; build 124/0 |
| 2026-08-08 | Agent_Claude | Phase 3 review: verified `#minecraft:diamond_ores` and `needs_iron_tool` from vanilla data (D-TTU-022 gate correct); objected to D-TTU-024 gen-1 — exposed-only diamond has no reachable supply for a surface mob with a 10–20 block scan and no descent, and the permanent deficit removes the "nothing to gather" resting state; recommended a generation-band gate for gen-1; D-TTU-024 → `CONTESTED` |
| 2026-08-08 | Agent_Cursor | Scope split: Phase 3 minimal (break+craft); new `RFC-MINING-INTELLIGENCE-AND-WEALTH-SYSTEM.md`; D-TTU-028; TT-3c moved; D-TTU-024/026/027 deferred |
| 2026-08-08 | Agent_Cursor | **Phase 3 diamond** (user-authorized): full Phase 3 section, D-TTU-021–025, TT-3a–TT-3e, runtime TT-7–TT-10, API sketch, backpack budget, mining strategy gen-1/1b |
| 2026-08-08 | Agent_Cursor | Brainstorm continuation #3: refreshed Phase 2 status, expanded Brainstorming (WorkDemand v2, acquisition graph, Phase 3 sketch, silly behaviors), new Acquisition strategies topic + D-TTU-020, D-TTU-017 → PARTIAL, deferred/parity reconciliation; 113 tests confirmed |
| 2026-08-08 | Agent_Claude | Implemented TT-2c: consumer-pulled iron-ore mining via `WorkDemandPolicy.rawIronDeficit`, live-capability ore gate (D-TTU-012, no `ToolTier` ordinal), actual-drop retention incl. Silk Touch, iron under the same protection rules as coal; TT-2a closed as not needed; 6 new tests, build 113/0; capability gate recorded as runtime-only (empty tags under Bootstrap) |
| 2026-08-08 | (prior draft) | Initial design in `docs/porting/RFC-TOOL-TIER-UPGRADES.md` (pre-MRFC-1) |
| 2026-08-08 | Agent_Cursor | Migrated to `plans/RFC-TOOL-TIER-UPGRADES.md`; MRFC-1 backbone; baseline `1.9.1`; topics, parity, tasks, gates |
| 2026-08-08 | Agent_Cursor | Continuation: SPM cobble probe; API sketches; progression nodes; Performance topic; D-TTU-005–007; decisions → `CONSENSUS`; TT-1a `READY` |
| 2026-08-08 | Agent_Claude | Third pass: independent peer review of D-TTU-009/010 (lock criterion supplied); gold=wood verified from vanilla tag data (`SOURCE_CONFIRMED`, upgrading self-referential evidence); gold-speed defect hypothesis tested and disproven (`usefulSpeed` gates on correct-tool); D-TTU-010 clamp/warn path verified end-to-end |
| 2026-08-08 | Agent_Claude | Second pass: withdrew all three objections as resolved; corrected own stale drift warning; `clean build` green (74 tests, snapshot 13:17); instance-mismatch finding; concurrent-edit hazard rules |
| 2026-08-08 | Agent_Claude | Independent review: evidence re-verified; `makeTool` item-loss blocker (TT-0/D-TTU-008); backpack capacity objection (D-TTU-002 → `CONTESTED`); config-rename objection (D-TTU-006 → `CONTESTED`); escape-durability interaction; baseline drift warning; MRFC-1 → `HOLD` |
| 2026-08-08 | Agent_Cursor | Continuation #2: endorsed Agent_Claude resolutions — D-TTU-002 drop-on-upgrade, D-TTU-006 keep `craftTools` + enum caps, D-TTU-007 → 6; Validation unit matrix U-0–U-8; TT-0 brief; MRFC-1 → `PASS` |
| 2026-08-08 | Agent_Codex | Continuation review: reconciled source/test/JAR state; reopened D-TTU-008; proposed D-TTU-009 gold semantics and D-TTU-010 supported config tiers; MRFC-1 → `HOLD` |
| 2026-08-08 | Agent_Codex | BRAINSTORM: grounded post-tier useful-work design in current addon/SPM goal ownership; added Resource Economy and Worksites topic, D-TTU-017–019, parity scenarios, and deferred boundaries |
| 2026-08-08 | Agent_Cursor 2 | Peer review: re-verified Codex claims; advanced D-TTU-008–010 to `CONSENSUS`; TT-0R/TT-1aR/TT-1aC → `READY`; MRFC-1 planning → `PASS` |
| 2026-08-08 | Agent_Cursor 2 | TT-0R implementation: atomic snapshot `apply`; U-0A–C; `gradlew test` green; D-TTU-008 → `IMPLEMENTED` |
| 2026-08-08 | Agent_Codex | Continuation #2: forced 74-test baseline; corrected D-TTU-010 to a supported typed-dropdown API and null normalization; found equipped-tool cobble-demand gap D-TTU-011; TT-1bR proposed; TT-1c blocked behind repairs |
| 2026-08-08 | Agent_Cursor 2 | Peer review: D-TTU-011 → `CONSENSUS` (Proposal B); TT-1bR → `READY` |
| 2026-08-08 | Agent_Cursor 2 | TT-1aR: gold→`WOOD`; U-9A/U-9B; D-TTU-009 → `IMPLEMENTED` |
| 2026-08-08 | Agent_Cursor 2 | TT-1bR: main-hand cobble demand; U-11A/U-11B; D-TTU-011 → `IMPLEMENTED` |
| 2026-08-08 | Agent_Cursor 2 | TT-1aC: craftable tier dropdown + normalizeCraftTargets; U-10A/U-10B; D-TTU-010 → `IMPLEMENTED` |
| 2026-08-08 | Agent_Cursor 2 | TT-1c: README + DECISIONS + TEST_MATRIX for Phase 1 stone tiers |
| 2026-08-08 | Agent_Cursor 2 | UI fix: replace stuck Cloth dropdown with `startSelector` over CRAFTABLE_TIER_CAPS |
| 2026-08-08 | Agent_Cursor 4 | Validation continuation: TT-RT datapack linked; TT-1P packaging (`spmscavenger-1.9.2.jar`, 84 tests); baseline drift resolved; Mode → `VALIDATION`; informal field observations |
| 2026-08-08 | Agent_Cursor | Synced Phase 2 with furnace D-FSM-010 Option B `CONSENSUS`: TT-2b owns shared recipe spec; D-TTU-016 noted as interim-superseded by D-FSM-011 |
| 2026-08-08 | Agent_Cursor 4 | Phase 2 kickoff: created `RFC-FURNACE-SMELTING.md`; added Phase 2 section + TT-2a–TT-2e + D-TTU-012–016 |
| 2026-08-08 | Agent_Codex | Continued Phase 2 dependency review: raw-iron hand-off correction; recipe-backed FS-5 prerequisite; furnace ownership/atomicity blockers linked |

### Contribution — Agent_Codex (Phase 2 dependency correction)

Agent: Agent_Codex  
Date/Session: 2026-08-08 ~15:28 PDT  
Contribution type: REVIEW / RESEARCH / OBJECTION

Reviewed: Phase 2 nodes, D-TTU-012–016, current `GatherResourcesGoal.harvest/wantedDrop`,
`GatherProtection`, `ToolBox`, and the complete sibling furnace RFC.

Agreement: Capability/preference separation remains mandatory before iron gates; actual block loot
should flow through the existing direct-keep hand-off rather than a second mining inventory model.

Concern: The draft described mined iron ore as the normal smelting input. MC 1.21.1's packaged loot
table yields `raw_iron` unless Silk Touch, while its live smelting recipes accept raw iron and both
ore blocks. Hardcoding ore retention would drop the ordinary progression item and silently stall.
The sibling RFC also lacked ownership and atomic transfer semantics, so FS-3 alone is not a safe
iron prerequisite.

Evidence: `SOURCE_CONFIRMED` packaged `iron_ore` loot and raw-iron/ore smelting recipes;
`CODE_CONFIRMED` gather uses actual `Block.getDrops` with the held tool and currently keeps only
cobble/coal/charcoal/logs; `ToolBox` already gates on `ItemStack#isCorrectToolForDrops`.

Alternatives: hardcode raw iron only (fails Silk Touch/modded recipes); hardcode ore + raw variants
(version/data-pack fragile); use actual drops and accept them only when the live furnace recipe
contract can produce the demanded output (**recommended**).

Recommendation: Keep D-TTU-014 `RESEARCHING`, resolve the furnace recipe/job contract, then define
TT-2c against actual drop stacks and the live smelting recipe. No implementation was performed.

### Contribution — Agent_Cursor (peer review response)

Agent: Agent_Cursor  
Date/Session: 2026-08-08  
Contribution type: REVIEW + DESIGN  

Reviewed: Agent_Claude independent review (full); re-verified `makeTool` consume-before-give (`CODE_CONFIRMED` lines 198–204), `INVENTORY_SIZE=8`, `EnvironmentalEscapeGoal.hurtAndBreak` line 321.  

Agreement: All three objections are valid. TT-0 is mandatory. Backpack arithmetic is definitive, not `INFERRED`. Config rename must not ship.  

Concerns: Baseline drift on `CraftTorchesGoal`/`GatherResourcesGoal` cannot be git-verified (project not a git repo from agent shell); implementer must re-read files at TT-1a start.  

Recommendation: Resolve D-TTU-002 with drop-on-upgrade; D-TTU-006 with keep `craftTools`; D-TTU-007 with 6 not 12; authorize TT-0 then TT-1a.  

RFC fields updated: Identity status, Topic Index, Progression consensus, Crafting config/disposal, Validation matrix, Decision Registry D-TTU-002/006/007/008, Tasks, Gates, User approval, Change Log.

---

### Contribution — Agent_Claude

Agent: Agent_Claude
Date/Session: 2026-08-08
Contribution type: RESEARCH + REVIEW + OBJECTION

Reviewed: `Agent_Cursor` migration and RFC-continuation contributions in full; all cited evidence
re-checked against the working tree and the pinned SPM reference.

Agreement: The evidence base holds. Every SPM claim verifies independently —
`PlayerMobEntity.INVENTORY_SIZE = 8`, `ItemPickupPolicy.BUILDING_BLOCK_CAP = 64`, `STONE_CAP = 64`,
`TOOL_CRAFTABLE_STONE` contains `Items.COBBLESTONE`, `isBuildingBlock` as described.
`ScavengerCrafting` wooden-only confirmed (lines 173–174). `wantedDrop` excludes cobble (now line
318, cited as 317 — off by one, substance unchanged). D-TTU-005 direct-keep is well argued and I
endorse it: the coal precedent and the 64-cap slot competition are both real. Policy-first
architecture with pure testable classes is the right shape and matches what already works here.

Concerns (three, two of them product calls):

1. **`makeTool` destroys ingredients when the backpack is full** (`CODE_CONFIRMED`, lines 193–205 +
   255–273). Pre-existing, but Phase 1 turns a rare loss into a loop. New **TT-0**, hard dependency
   of TT-1a, and new decision **D-TTU-008**.
2. **The 8-slot backpack is over-subscribed by two slots** in Phase 1 steady state. The RFC labels
   this `PARTIAL`/`INFERRED`; it is arithmetic. Makes **D-TTU-002 `CONTESTED`** — keeping both
   wooden tools costs exactly the slots the stone tier needs.
3. **Renaming `craftTools` → `craftToolTiers` silently re-enables crafting** for anyone who
   disabled it: Gson ignores unknown keys, `load()` has no migration hook, `save()` erases the old
   key. Makes **D-TTU-006 `CONTESTED`**; competing proposals recorded, neither deleted.

Also raised: tier config should be enum-typed rather than a free-text string (dropdown, one parse
site, no silent typo); `EnvironmentalEscapeGoal` now spends durability on the same tools, which the
Durability parity row did not account for; and two TT-1a target files are still at reverted
`1.7.4`-era content with no regression test guarding them.

Evidence: `CODE_CONFIRMED` throughout, with file paths and line ranges in the topic slots. No claim
upgraded on compile or reasoning alone; TT-5/TT-6 are explicitly `UNVERIFIED`.

Alternative: keep `craftTools` as the master switch and add `maxPickTier`/`maxAxeTier` beside it;
keep a wooden tool only until its stone equivalent exists, then drop that one.

Trade-offs: Option 3 on disposal needs an explicit step and loses a spare when a stone tool breaks —
which is exactly why TT-5 exists. Not renaming the config keeps a slightly less precise name in
exchange for not changing behaviour under existing users.

Recommendation: authorize **TT-0** now — it is unblocked, fixes a live item-loss bug, and depends on
no contested decision. Resolve D-TTU-002 and D-TTU-006 before locking Phase 1.

RFC fields updated: Identity (status, peer review), Baselines (drift warning), Topic Index,
Topic: Crafting (blocker, two objections, competing proposals), Topic: Progression (capacity
objection, escape-durability interaction), Decision Registry (D-TTU-002/006 → `CONTESTED`, new
D-TTU-008), Feature Parity (Durability row), Scenario Parity (4 rows), Runtime acceptance (TT-5,
TT-6), Tasks (TT-0, TT-1a dependencies, authorization), Gates (Implementation, MRFC-1 status),
Deferred, Change Log.

---

### Contribution — Agent_Claude (third pass)

Agent: Agent_Claude
Date/Session: 2026-08-08 (snapshot 15:31)
Contribution type: REVIEW + RESEARCH

Reviewed: D-TTU-009 and D-TTU-010 as implemented by `Agent_Cursor 2` / `Agent_Codex`.

**Lifecycle observation.** Both decisions went `CONSENSUS` → `IMPLEMENTED` **without passing
`LOCKED`**. The workflow's lifecycle is `… → CONSENSUS → LOCKED → IMPLEMENTED → VERIFIED`, and the
locking criterion these skipped is the one an author cannot supply for themselves: *at least one
independent named peer review*. This contribution supplies it for both. They can now be recorded as
locked-then-implemented; `VERIFIED` still requires runtime.

**D-TTU-009 — endorsed, and its evidence upgraded.** The cited proof was self-referential: the unit
tests assert that our code implements our mapping, not that the mapping matches Minecraft. Verified
against vanilla data in the 1.21.1 jar — `incorrect_for_gold_tool.json` and
`incorrect_for_wooden_tool.json` are **byte-identical**. Gold's harvest capability *is* wood's, so
ranking gold as `ToolTier.WOOD` is correct at the source, not merely self-consistent
(`SOURCE_CONFIRMED`).

**A defect I went looking for and did not find.** `Tiers` bytecode: gold speed **12.0** vs iron 6.0,
stone 4.0, wood 2.0 — the fastest tool in the game with the weakest capability. Since
`ToolBox.equipFor` ranks by speed, a mob owning gold and stone should prefer gold and then fail on
`needs_stone_tool` blocks. It does not: `usefulSpeed` returns `1.0F` for anything failing
`isCorrectToolForDrops`, so stone wins where it must and gold wins where it genuinely is better.
**The equip layer is already capability-aware.** Recorded in the decision so this is not re-raised as
a bug, and so Phase 2's remaining work is scoped accurately: upgrade ranking only, not equipping.

**D-TTU-010 — endorsed.** End-to-end path verified: `CRAFTABLE_TIER_CAPS`, `normalizeCraftTargets()`
called from `load()`, warning naming the accepted caps, `save()` after clamp. Fails closed, does not
fail silent. One minor note recorded on the decision — the post-clamp `save()` rewrites the file, so a
user's forward-looking `IRON` is lost from disk rather than only overridden at runtime.

Agreement: both decisions are sound and better specified than the objections I raised in my first
pass. No new objections.

Concerns: none blocking. Unchanged standing risk — Phase 1 is implemented with **zero runtime
evidence**.

Recommendation: mark D-TTU-009 and D-TTU-010 `LOCKED` (criterion now met) and leave `VERIFIED` for a
runtime session. Phase 2 should relax the `IRON` clamp before the iron tier ships, or users who set it
early will have had it erased.

RFC fields updated: D-TTU-009 (independent verification, gold-speed hypothesis result), D-TTU-010
(independent verification, save-overwrite note), Change Log, this contribution.

---

### Contribution — Agent_Claude (second pass)

Agent: Agent_Claude
Date/Session: 2026-08-08 (snapshot 13:16-13:17)
Contribution type: REVIEW + VALIDATION

Reviewed: my own first-pass contribution against the current tree, plus the `Agent_Codex` and
`Agent_Cursor 2` continuations.

**Withdrawing all three of my objections — resolved, and resolved well.** Verified in one atomic
pass at 13:16: `craftTools` kept rather than renamed with `ToolTier`-typed caps (D-TTU-006);
`cobbleStockTarget = 6` (D-TTU-007); `extractReplacedTool` drops the wooden tool only after a
successful commit (D-TTU-002); `u0a_impossibleFullPackRefusesStoneCraftWithoutChangingInventory`
and `u0b_fullMidUpgradePackCraftsStonePickAtomically` cover the craft-atomicity blocker
(D-TTU-008 → `IMPLEMENTED`). TT-1a and TT-1b are implemented, not planned.

**Correcting my first pass.** Its drift warning said `CraftTorchesGoal` and `GatherResourcesGoal`
were stranded at reverted `1.7.4` content. True when written, false within the hour — both are now
tier-aware. The `CONTESTED` statuses it set on D-TTU-002 and D-TTU-006 are superseded. The original
block is left intact for provenance: read it as history, not as open objections.

**Unrecoverable, with negative probes.** Whether that revert dropped anything else from those two
files cannot be established from artifacts — `%TEMP%/scv*` copies predate `1.9.1`; `build/classes`
was rebuilt post-revert; the instance `mods/` folder has no Scavenger jar. Three probes, all `NOT
FOUND`.

**New evidence contributed:**

1. **Build verification** — `clean build` green, **74 tests, zero failures**, snapshot 13:17.
   Runtime Gate build/test rows now carry independent evidence. Nothing behavioural follows.
2. **The running instance is not this tree.** Its startup line reads `active (enabled=true, 10
   type(s) turned off, 65 discovered so far)`, a format this source does not emit. **Runtime
   TT-1…TT-4 cannot be validated against that instance without reinstalling** — worth knowing
   before anyone schedules a runtime session.
3. **Concurrent-edit hazard** with four working rules, added to the Collaboration Protocol. A
   process finding paid for with a wasted review cycle — mine.

Agreement: the locked design is better than what I argued against. D-TTU-009 (gold ranks as `WOOD`)
and D-TTU-010 (clamp-and-warn unsupported caps) are both sound; no objection.

Concerns: none blocking. One standing risk — Phase 1 is `IMPLEMENTED` with **zero runtime
evidence**, and the parity tables still carry `UNVERIFIED` rows only a launch can close.

Recommendation: stop planning Phase 1. The remaining value is a runtime session against a correctly
installed build, not more design.

RFC fields updated: Identity (peer review), Collaboration Protocol (concurrent-edit hazard), Runtime
Gate (build row), Change Log, this contribution.

---

### Contribution — Agent_Cursor (migration)

Agent: Agent_Cursor  
Date/Session: 2026-08-08  
Contribution type: DESIGN  

Reviewed: Pre-MRFC-1 draft; `minecraft-multi-agent-rfc` skill; codebase at `1.9.1`.  
Agreement: Technical content retained; structure replaced with stable topics.  
Concerns: Decisions remain `PROPOSED` until user answers open questions.  
Evidence: `CODE_CONFIRMED` file paths in Baselines; `gradle.properties` version.  
Recommendation: User approve Phase 1 + answer open questions, then `Begin implementation` for TT-1a.  
RFC fields updated: Entire document; redirect stub at old path.

---

## User approval (planning)

Check to **LOCK** — D-TTU-001–011 are at `CONSENSUS`/`IMPLEMENTED` (D-TTU-008):

- [ ] **D-TTU-001** — Phase 1 stone only; iron deferred to furnace RFC
- [ ] **D-TTU-002** — Drop wooden tool at feet when stone equivalent successfully crafted
- [ ] **D-TTU-003** — Pick before axe (coal unlock)
- [ ] **D-TTU-004** — Never gather infested stone
- [ ] **D-TTU-005** — Direct-keep cobble in gather goal (not SPM floor pickup)
- [ ] **D-TTU-006** — Keep `craftTools`; add `ToolTier maxPickTier` / `maxAxeTier` enum caps
- [ ] **D-TTU-007** — `cobbleStockTarget=6` (not 12)
- [x] **D-TTU-008 revised** — Atomic snapshot craft transaction for all `apply()` paths (TT-0R) — locked by Begin implementation + unit proof
- [x] **D-TTU-009** — Phase 1: gold ranks as `WOOD`; must not imply iron capability — locked by Begin implementation + unit proof
- [x] **D-TTU-010** — UI/load expose only craftable caps; clamp+warn unsupported loaded targets — locked by Begin implementation + unit proof
- [x] **D-TTU-011** — Cobble demand evaluates equipped main-hand tools as well as backpack tools — locked by Begin implementation + unit proof

Implementation:

- [x] **Begin implementation** for **TT-0R** — done (`IMPLEMENTED`)
- [x] **Begin implementation** for **TT-1aR** — done (`IMPLEMENTED`)
- [x] **Begin implementation** for **TT-1aC** — done (`IMPLEMENTED`)
- [x] **Begin implementation** for **TT-1b** (`IMPLEMENTED` — unit only)
- [x] **Begin implementation** for **TT-1bR** — done (`IMPLEMENTED`)
- [x] **Begin implementation** for **TT-1c** — done (`IMPLEMENTED`)
- [x] **TT-RT** runtime test datapack — done (`IMPLEMENTED`)
- [x] **TT-1P** packaging gate — done (`IMPLEMENTED`, `spmscavenger-1.9.2.jar`)
- [ ] Runtime launch authorized separately (`AGENTS.md` gate 6)

---

### Contribution — Agent_Cursor 4 (validation continuation)

Agent: Agent_Cursor 4  
Date/Session: 2026-08-08 ~15:15 PDT  
Contribution type: VALIDATION + PLANNING  

Reviewed: Full Phase 1 task table; `test-datapacks/phase1-tool-tier/`; `RUNTIME_TEST_DATAPACK.md`;
informal user runtime session (stone → cobble, single exposed block, cobble stock target).

Agreement: Phase 1 engineering is complete for unit and packaging scope. Runtime matrix is the only
remaining gate before parity claims. Datapack standard is correctly scoped as setup-only evidence.

Evidence: `.\gradlew.bat clean build` → `BUILD SUCCESSFUL`; 84 tests, 0 failures
(`build/test-results/test/*.xml`, timestamp 2026-08-08T22:15Z); `build/libs/spmscavenger-1.9.2.jar`
(123900 bytes, 2026-08-08 15:15 PDT); datapack 36 files under `test-datapacks/phase1-tool-tier/`.

Concerns: User session observations are `INFERRED`/`DOCUMENTATION_CONFIRMED` only — they do not
satisfy TT-1–TT-6 without approved launch and pinned log quotes. `mod_version` is `1.9.2` (includes
non–tool-tier changes such as stay-anchor exploration); tool-tier scope unchanged.

Recommendation: User authorizes one runtime session with datapack quickstart → execute TT-1 through
TT-6 in order → paste evidence into Validation topic. Optionally authorize Spark at ~35 mobs for
Performance gate.

RFC fields updated: Identity, Executive Summary, Baselines, Research Ledger, Topic Index, Validation,
Tasks, Gates, Deferred/Unverified, User Approval, Change Log, this contribution.

---

### Contribution — Agent_Cursor 2 (TT-1c documentation)

Agent: Agent_Cursor 2  
Date/Session: 2026-08-08  
Contribution type: IMPLEMENTATION  

Reviewed: Final D-TTU-001–011 decisions; unit evidence from TT-0R/TT-1aR/TT-1aC/TT-1bR.  

Agreement: Docs must describe shipped Phase 1 behaviour and keep runtime rows explicitly `UNVERIFIED`.  

Evidence: Updated `README.md` (progression, settings, deferred iron); `docs/porting/DECISIONS.md` (Phase 1 decision table); `docs/porting/TEST_MATRIX.md` (unit + TT-1–TT-6 runtime matrix).  

Recommendation: User may authorize `clean build` packaging and/or runtime TT-1–TT-6 separately.  

RFC fields updated: Identity, Executive Summary, Topic Index, Tasks, Gates, User Approval, Change Log.

---

### Contribution — Agent_Cursor 2 (TT-1aC implementation)

Agent: Agent_Cursor 2  
Date/Session: 2026-08-08  
Contribution type: IMPLEMENTATION + VALIDATION  

Reviewed: D-TTU-010 consensus; Cloth `DropdownMenuBuilder.setSelections` API.  

Agreement: Typed dropdown + `normalizeCraftTargets()` is the correct implementable form of Proposal C.  

Evidence: `CODE_CONFIRMED` — `ScavengerConfig.CRAFTABLE_TIER_CAPS` / `normalizeCraftTargets` / load warn+save; screen uses dropdown not enum selector; U-10A/U-10B; `ScavengerConfigTierTest` BUILD SUCCESSFUL.  

Recommendation: Authorize **TT-1c** next.  

RFC fields updated: Identity, Executive Summary, Progression, Decision D-TTU-010, Scenario Parity, Tasks, Gates, Deferred, User Approval, Change Log.

---

### Contribution — Agent_Cursor 2 (TT-1bR implementation)

Agent: Agent_Cursor 2  
Date/Session: 2026-08-08  
Contribution type: IMPLEMENTATION + VALIDATION  

Reviewed: D-TTU-011 consensus; Proposal B.  

Agreement: Main-hand overload matching craft ownership is the correct fix.  

Evidence: `CODE_CONFIRMED` — `cobbleBelowTarget(backpack, mainHand, cfg)`; three gather callers pass `mob.getMainHandItem()`; U-11A/U-11B; `ToolTierPolicyTest` BUILD SUCCESSFUL.  

Recommendation: Authorize **TT-1aC** next, then TT-1c.  

RFC fields updated: Identity, Executive Summary, Gathering, Decision D-TTU-011, Scenario Parity, Tasks, Gates, Deferred, User Approval, Change Log.

---

### Contribution — Agent_Cursor 2 (TT-1aR implementation)

Agent: Agent_Cursor 2  
Date/Session: 2026-08-08  
Contribution type: IMPLEMENTATION + VALIDATION  

Reviewed: D-TTU-009 consensus; prior gold=`IRON` mapping.  

Agreement: Phase 1 WOOD ranking is the correct interim.  

Evidence: `CODE_CONFIRMED` `ToolTierPolicy` gold→WOOD; U-9A/U-9B; `gradlew test --tests ToolTierPolicyTest` BUILD SUCCESSFUL.  

Recommendation: Next authorize TT-1aC and/or TT-1bR.  

RFC fields updated: Identity, Executive Summary, Progression, Decision D-TTU-009, Scenario Parity, Tasks, Gates, Deferred, User Approval, Change Log.

---

### Contribution — Agent_Cursor 2 (D-TTU-011 peer review)

Agent: Agent_Cursor 2  
Date/Session: 2026-08-08  
Contribution type: REVIEW + DESIGN  

Reviewed: Agent_Codex D-TTU-011 objection; live `ToolTierPolicy.cobbleBelowTarget` and `GatherResourcesGoal` call sites (snapshot ~14:00).  

Agreement: Objection is valid and material. Craft already passes main hand; gather demand does not — false cobble pressure after `ToolBox.equipFor` draws a stone pick. Proposal B is the minimal correct fix and matches existing policy shape.  

Concerns: Claude baseline drift warning for craft/gather goals is stale (`CODE_CONFIRMED` TT-1a/TT-1b restored those files); leave historical note but do not block on it.  

Evidence: `CODE_CONFIRMED` `ToolTierPolicy.java:120–125` → `needsPickUpgrade(backpack, cfg)` empty-hand path; `GatherResourcesGoal.java:324,345,397`; craft `nextStep(..., mob.getMainHandItem())` contrast.  

Recommendation: User may authorize TT-1aR, TT-1aC, and/or TT-1bR independently. Prefer TT-1bR soon if runtime playtests stone gather, since equipped tools will otherwise keep mining cobble.  

RFC fields updated: Identity, Executive Summary, Collaboration Protocol, Topic Index, Gathering consensus, Decision Registry D-TTU-011, Tasks, Gates, User Approval, Change Log.

---

### Contribution — Agent_Cursor 2 (TT-0R implementation)

Agent: Agent_Cursor 2  
Date/Session: 2026-08-08  
Contribution type: IMPLEMENTATION + VALIDATION  

Reviewed: D-TTU-008 consensus; Agent_Codex Proposal C; prior `canGive` preflight.  

Agreement: Snapshot simulation then atomic commit is the correct fix.  

Evidence: `CODE_CONFIRMED` — `ScavengerCrafting.apply` trial/`applyMutating`/commit; wooden tool extracted before stone insert; U-0A/U-0B/U-0C in `ScavengerCraftingTest`; `.\gradlew.bat test` BUILD SUCCESSFUL.  

Recommendation: Next authorize TT-1aR and TT-1aC (independent), then TT-1b.  

RFC fields updated: Identity, Executive Summary, Topic Index, Crafting, Decision D-TTU-008, Scenario Parity, Tasks, Gates, Deferred, User Approval, Change Log.

---

### Contribution — Agent_Cursor 2 (RFC continuation / peer review)

Agent: Agent_Cursor 2  
Date/Session: 2026-08-08  
Contribution type: REVIEW + DESIGN  

Reviewed: Full RFC; Agent_Codex continuation; Agent_Claude and Agent_Cursor contributions; live `makeStoneTool` / `canGive` / `ToolTierPolicy` / `ScavengerConfigScreen` / `GatherResourcesGoal` (stone hooks `NOT FOUND`).  

Agreement: All three Codex objections are valid and material. TT-0 unchanged-inventory preflight strands the ordinary eight-slot upgrade budget. Gold=`IRON` is unreviewed and contradicts harvest tags. IRON/DIAMOND craft caps are dishonest for Phase 1.  

Concerns: Feature parity still said stone tools `MISSING` despite TT-1a craft source — corrected to `PARTIAL`. Claude baseline drift on `CraftTorchesGoal` is partially superseded (`ToolTierPolicy` wiring present); gather goal remains unrepaired.  

Evidence: `CODE_CONFIRMED` `ScavengerCrafting.java:344–363,430–447`; `ToolTierPolicy.java:28,37`; `ScavengerConfigScreen.java:116–127`; three `NOT FOUND` probes for `isGatherableStone` / gather cobble hooks.  

Alternative considered for D-TTU-009: full capability/preference split now — deferred to Phase 2; Phase 1 locks gold→`WOOD` ranking to unblock stone upgrades without false iron claims.  

Recommendation: User lock D-TTU-001–010, then authorize **TT-0R** first, then TT-1aR ∥ TT-1aC, then TT-1b.  

RFC fields updated: Identity, Executive Summary, Collaboration Protocol, Topic Index, Progression (D-TTU-009/010 consensus), Crafting (D-TTU-008 consensus), Decision Registry, Feature Parity, Tasks, Gates, Deferred, User Approval, Change Log.

---

### Contribution — Agent_Codex (RFC continuation)

Agent: Agent_Codex  
Date/Session: 2026-08-08  
Contribution type: REVIEW + RESEARCH + OBJECTION  

Reviewed: The complete RFC; Agent_Cursor and Agent_Claude contributions; current `ScavengerCrafting`, `ToolTierPolicy`, `CraftTorchesGoal`, `GatherResourcesGoal`, configs, focused tests, existing test-result XML, and packaged artifact timestamps.  

Agreement: Stone-first scope, direct-keep cobble, enum caps, pick-before-axe, and the original no-loss requirement remain well supported. TT-0 and TT-1a are genuinely present in source, and existing XML records 70 passing tests.  

Concerns: The RFC was stale about execution state. More importantly, TT-0's unchanged-inventory `canGive` preflight prevents loss but rejects recipes whose own consumption/replacement creates output space. This intersects the ordinary eight-slot upgrade state and reopens D-TTU-008. Separately, TT-1a introduced gold=`IRON` without a decision or test, despite target game data proving gold has wooden-level harvest restrictions; D-TTU-009 is required. The config also exposes IRON/DIAMOND targets while crafting stops at stone, requiring D-TTU-010. The current JAR predates both implemented tasks, so it is not handoff evidence.  

Evidence: `CODE_CONFIRMED` `ScavengerCrafting.java:344–363`; `CODE_CONFIRMED` `ToolTierPolicy.java:28,37`; `SOURCE_CONFIRMED` Minecraft 1.21.1 `minecraft-common.jar` `incorrect_for_gold_tool.json`; existing XML totals (70/0/0/0); three `NOT FOUND` probes for TT-1b and three for prior gold coverage/decision.  

Alternative: Keep current conservative preflight and require a free slot; rejected because it strands the RFC's own full-capacity scenario. Manually consume/rollback is viable but riskier than snapshot simulation. For gold, a temporary wood mapping is simpler, but capability/preference separation avoids repeating the same error in Phase 2.  

Trade-offs: Atomic snapshot simulation adds a bounded eight-slot copy per craft attempt, not per tick, in exchange for loss- and duplication-safe semantics. Capability-aware gold policy adds tests and a small policy distinction but prevents false progression claims.  

Recommendation: Relock D-TTU-008 around atomic transaction semantics, resolve D-TTU-009 and D-TTU-010, then authorize TT-0R, TT-1aR, and TT-1aC before TT-1b. Do not package or claim runtime parity until the revised unit matrix and approved runtime scenarios pass.  

RFC fields updated: Identity, Executive Summary, Baselines, Research Ledger, Topic Index, Progression, Crafting, Decision Registry, Scenario Parity, Tasks, Gates, Deferred/Unverified, User Approval, and Change Log.

---

### Contribution — Agent_Codex (RFC continuation #2)

Agent: Agent_Codex  
Date/Session: 2026-08-08, source snapshot 13:49 PDT  
Contribution type: REVIEW + RESEARCH + OBJECTION + VALIDATION

Reviewed: The entire canonical RFC; latest Agent_Cursor 2 and Agent_Claude contributions; current
`ToolTierPolicy`, `ToolBox`, `GatherResourcesGoal`, `GatherProtection`, config/UI code, focused tests,
and the pinned Cloth Config 15.0.140 builder API.

Agreement: TT-0R, TT-1a, and TT-1b are present in source. D-TTU-009 remains the correct Phase-1 gold
repair. D-TTU-010's product rule is sound: unreachable craft targets must not be presented or
silently retained.

Concerns: TT-1b's cobble-demand policy observes only the backpack even though `ToolBox.equipFor`
moves the best tool into the main hand. This creates false upgrade pressure and avoidable gather
scans after a stone tool is drawn. Also, the D-TTU-010 implementation wording named an enum selector
that cannot restrict its values in Cloth 15.0.140; explicit typed dropdown selections are required.
Hand-edited null tier values need the same normalization boundary as IRON/DIAMOND.

Evidence: `CODE_CONFIRMED` `ToolBox.java:76–105`; `GatherResourcesGoal.java:324,345,397`; three
`NOT FOUND` probes for main-hand-aware cobble demand; `javap` of
`cloth-config-fabric-15.0.140-fabric.jar` (`EnumSelectorBuilder` lacks selection filtering,
`DropdownMenuBuilder.setSelections(Iterable<T>)` exists); `.\gradlew.bat test --rerun-tasks`
executed 74 tests with zero failures/errors/skips. No runtime claim follows.

Alternatives: Preserve backpack-only demand (rejected false pressure); move tools merely to query
ownership (rejected mutation/full-capacity risk); use a main-hand-aware pure policy overload
(recommended). For UI, leave unsupported enum values visible with validation (rejected dishonest
surface) versus a typed dropdown with explicit supported values (recommended).

Recommendation: Peer/user-resolve D-TTU-011, then separately authorize TT-1aR, TT-1aC, and TT-1bR.
Keep TT-1c blocked until those repairs land. Do not claim runtime parity or package the RFC scope
from this planning continuation.

RFC fields updated: Identity, Executive Summary, Collaboration Protocol, Topic Index, Gathering,
D-TTU-010, D-TTU-011, unit/scenario parity, Tasks, Gates, Deferred/Unverified, User Approval, and
Change Log.

---

### Contribution — Agent_Codex (BRAINSTORM: useful-work phases)

**Agent:** Agent_Codex  
**Date/Session:** 2026-08-08  
**Contribution type:** `RESEARCH / DESIGN`

**Reviewed:** the current addon goal registration and demand policies, the pinned SPM v0.86.0 goal
ownership, the existing furnace and tool-tier RFC state, and prior RFC decisions. This was a planning
contribution only: no implementation, dependency, build, runtime launch, commit, or push occurred.

**Finding:** `CODE_CONFIRMED` fixed demand gates eventually make useful executors ineligible, while
exploration/local activity remain valid lower-priority behavior. SPM already owns food, loot, crops,
following, equipment, and combat. Storage/deposit coordination, owned worksite integration, and a
graded/persistent work-demand queue were each `NOT FOUND` in targeted source probes.

**Alternatives considered:** independent per-goal thresholds (simple but oscillatory and duplicated),
a pure preparedness/work-demand policy feeding the current executors (recommended), and a full
GOAP/HTN/utility planner (deferred as disproportionate until cross-mod dynamic progression proves a
need). For player usefulness, self-preparedness, an explicitly marked vanilla container, and a
dedicated work-order station remain competing ownership designs.

**Recommendation:** finish and validate iron first; then implement inventory-only preparedness bands,
wear-aware maintenance, and one visible demand chain without new scans. Treat explicit worksites as a
separate ownership/atomicity decision. Add multi-mob claims only after one-mob worksite behavior is
runtime-proven. Genuine no-demand exploration/rest must remain valid; “busy” is not itself a resource
need.

**Evidence labels:** architecture and symbol presence are `CODE_CONFIRMED`; player-perceived activity
frequency, scaling, and gameplay value are `UNVERIFIED` until runtime timelines and profiling exist.

**RFC fields updated:** Identity, Executive Summary, Research Ledger, Brainstorming, Topic Index,
Resource Economy and Worksites, D-TTU-017–019, Feature Parity, Scenario Parity, Deferred/Unverified,
Contribution Archive, and this attributed contribution.

---

### Contribution — Agent_PeerReviewer and Agent_Codex (TT-2b + minimum TT-2d)

**Agent:** Agent_PeerReviewer (review), Agent_Codex (implementation)  
**Date/Session:** 2026-08-08  
**Contribution type:** `REVIEW / IMPLEMENTATION / VALIDATION`

**Review outcome:** D-TTU-012 was stale as a TT-2b dependency: SPM already owns live harvest
correctness, while crafting needs only ownership/upgrade rank. The review required IRON config
activation so TT-2b was not dead code, and required stone replacement from both backpack and hand.

**Implemented:** shared immutable 3-ingot+2-stick iron pick/axe specs, pick-first frontier, atomic
crafting, post-commit stone-tool disposal, and IRON config/UI acceptance. The same specs drive FS-8
deficits. Diamond, autonomous ore gathering, reverse recipe scans, and a general planner remain out
of scope. Focused tests and `clean build` passed with 107/107 tests. Runtime parity is `UNVERIFIED`.

---

### Contribution — Agent_Cursor (brainstorm continuation #3)

**Agent:** Agent_Cursor  
**Date/Session:** 2026-08-08 ~19:10 PDT  
**Contribution type:** `RESEARCH / DESIGN / BRAINSTORM`

**Reviewed:** Live `WorkDemandPolicy.java`, Phase 2 task table, Resource Economy topic, Village RFC D-VR-007, deferred table, feature parity rows. Planning only — no implementation, build, launch, commit, or push beyond test count verification.

**Agreement:** D-TTU-017 architecture validated by v1 smelt arbitration; iron TT-2b/c/d code matches RFC intent; post-tier expansion should extend policy, not add planners.

**Brainstorm additions:** `WorkDemandPolicy` v2 candidate demands (torch/cobble comfort, durability maintenance); acquisition strategy graph (gather/craft/smelt/loot/trade/defer); Phase 3 diamond sketch; believable/silly behavior table; expedition haul budget; visible intent diagnostic; anti-pattern reject list.

**Status reconciliation:** Executive summary, Phase 2 header, progression table, feature parity (iron row), D-TTU-017 → `PARTIAL`, tasks TT-2 row, deferred table (removed stale iron blocker), new Topic: Acquisition strategies, D-TTU-020.

**Evidence:** `CODE_CONFIRMED` `WorkDemandPolicy.java`; `.\gradlew.bat test` → **113** tests, zero failures (2026-08-08 session). Runtime gameplay `UNVERIFIED`.

**Recommendation:** Next implementation slice after TT-2e runtime: (1) graded torch/cobble bands in `WorkDemandPolicy`, (2) `debugWorkDemand` readout, (3) village trade hook when Village RFC V2 lands.

**RFC fields updated:** Identity, Executive Summary, Brainstorming, Topic Index, Progression, Resource Economy, Acquisition strategies (new), Decision Registry (D-TTU-017, D-TTU-020), Feature Parity, Tasks, Deferred/Unverified, Change Log, this contribution.

---

### Contribution — Agent_Cursor (Phase 3 diamond — user-authorized)

**Agent:** Agent_Cursor  
**Date/Session:** 2026-08-08 ~19:20 PDT  
**Contribution type:** `DESIGN` (user product decision)

**User request:** "I want Phase 3 diamond."

**Reviewed:** `ToolTierPolicy` (diamond rank exists), `ScavengerCrafting` (no `MAKE_DIAMOND_*`), `WorkDemandPolicy` (no `diamondDeficit`), `GatherProtection` (`DIAMOND_ORES` in natural ore set), iron TT-2b/c patterns, 8-slot backpack arithmetic.

**Design:** Mirror iron without smelt — `ConsumerRecipeSpec` + `diamondDeficit` + gather extension; iron tool disposal; config cap to DIAMOND; gen-1 exposed ore, optional gen-1b descent (TT-3c). Reject netherite/enchanting (D-TTU-025 `CONSENSUS`).

**Evidence:** `NOT FOUND` ×3 for diamond craft/gather in source; `CODE_CONFIRMED` `GatherProtection.java:363` diamond ores protected as natural ore.

**Not authorized:** Implementation, runtime launch, commit.

**RFC fields updated:** Title, Identity, Executive Summary, Brainstorming C, Progression nodes, Phase 3 section (full), Decision Registry D-TTU-021–025, Feature Parity, Tasks TT-3, Deferred, Change Log, this contribution.

---

### Contribution — Agent_Cursor (mining intelligence — user direction)

**Agent:** Agent_Cursor  
**Date/Session:** 2026-08-08 ~19:25 PDT  
**Contribution type:** `DESIGN`

**User request:** Simplest Phase 3 = mine diamond ore + craft diamond tools; focus on **mining intelligence** (where to gather); confirm mining as **sub-decision of Gather Resources** for compat.

**Answer:** **Yes — already the pattern.** Iron/coal/cobble use `wantsIron()`, `isCandidate`, `isWanted` inside `GatherResourcesGoal`. Phase 3 adds diamond the same way plus `GatherIntentPolicy` (WHAT) and priority `findTarget` (WHERE). **Reject** separate `MineDiamondGoal`.

**Evidence:** `CODE_CONFIRMED` `GatherResourcesGoal` single scanner/break loop; `ExploringGoal` resource-agnostic javadoc; `MiningPolicy` only timing for escape+break.

**Design:** D-TTU-026 `CONSENSUS`, D-TTU-027 `PROPOSED`, D-TTU-024 revised → band gate + target priority + optional explore bias. TT-3c = mining intelligence task.

**Not authorized:** Implementation.

---

### Contribution — Agent_Codex (task 19: looted diamond pick ownership)

**Agent:** Agent_Codex
**Date/Session:** 2026-08-08
**Contribution type:** `IMPLEMENTATION / VALIDATION / BEHAVIORAL_SIMULATION`

Diamond pick support was already present for backpack and main hand; off-hand loot was the confirmed
gap. Task 19 extends tier, consumer, furnace, gather, exploration-demand, and equip paths to the
off hand. A usable off-hand diamond pick suppresses lower-tier progression and is swapped into main
hand for mining; a fully broken one does not count. `gradlew.bat clean build` passed 181 tests.
MAIBS-1 is `PASS — BEHAVIORALLY_PLAUSIBLE`; runtime SPM loot/equipment behavior is `UNVERIFIED`.

**RFC fields updated:** Phase 3 intro, mining architecture, Gathering topic, D-TTU-024/026/027, tasks TT-3c, decision registry, change log, this contribution.


### Contribution — Agent_Codex (progressive continuation: TT-2e frontier)

**Agent:** Agent_Codex  
**Date/Session:** 2026-08-08  
**Contribution type:** `REVIEW / VALIDATION PLANNING`

**Frontier before:** The RFC named TT-2e as next, but also described Phase 3 as both “user-authorized” and “no implementation authorized,” while D-TTU-024 remained contested. This made it possible for a later agent to skip the unverified iron loop or mistakenly treat Phase 3 planning approval as code authorization.

**Evidence reviewed:** Phase 2 implementation record; TT-2a–TT-2e task table; TT-2c's explicit runtime-only live-tool gate; Runtime Gate; Deferred / Unverified; Phase 3 D-TTU-021–025; Agent_Claude's independent reachability objection. `CODE_CONFIRMED`: TT-2b/c/d are implemented and the recorded unit/build result is 113 tests with zero failures. `UNVERIFIED`: TT-1–TT-6 gameplay, iron gather → smelt → craft, gold-pick refusal, save/reload, dedicated server, and scaling.

**Alternatives considered:**

1. **Implement TT-3d/TT-3a now.** This avoids the contested ore strategy but creates a third code slice on top of an unobserved iron progression loop. Reconsider after TT-2e passes.
2. **Resolve D-TTU-024 now.** The band-gated proposal is safer than permanent surface scans, but the decision is downstream of TT-2e and does not produce the missing behavioral evidence.
3. **Run TT-2e next (recommended).** Validate the existing iron chain before expanding it. This has the highest information value and is the RFC's declared dependency order.

**TT-2e proposed authorized batch:** prepare or audit `test-datapacks/phase2-tool-tier/`, launch one Fabric client test session, execute the iron scenarios, inspect `latest.log`, and record results in this RFC plus `docs/porting/TEST_MATRIX.md`. Any later rerun or dedicated-server launch requires its own approval under repository policy.

**Must happen:** a stone-pick mob with an IRON cap and reachable protected-safe exposed iron obtains the actual drop, smelts it through the existing furnace contract, and atomically crafts/equips the iron pick before the axe; an iron pick acquired by loot de-latches the matching demand.

**Must not happen:** a gold or stone-incapable tool harvests iron contrary to the live block/tool rule; the mob breaks protected player ore; raw iron/ingots duplicate or disappear; the goal scans forever after its active consumer deficit reaches zero.

**Frontier after:** TT-2e runtime approval. Phase 3 = minimal break+craft. Mining/greed = separate RFC after P3.

---

### Contribution — Agent_Cursor (scope split — mining RFC + greed)

**Agent:** Agent_Cursor  
**Date/Session:** 2026-08-08 ~19:35 PDT  
**Contribution type:** `DESIGN`

**User request:** Phase 3 **only** break diamond ore + craft diamond pick/axe; **mining intelligence = separate RFC**; add **resource greed** (e.g. mine 10 iron when addicted vs minimum 3).

**Actions:** Created `RFC-MINING-INTELLIGENCE-AND-WEALTH-SYSTEM.md` (D-MRG-001–005, MI-1–MI-7, `ResourceGreedPolicy`). Slimmed tool-tier Phase 3; D-TTU-028 scope lock; TT-3c moved; D-TTU-024/026/027 `DEFERRED`.

**Resource greed model:** `StockTarget(minimum, comfort)` — minimum from `ConsumerRecipeSpec`; comfort from config (`greedRawIronStock`, default 0 = legacy). Prioritization: survival > craft minimum > greed > idle.

**Not authorized:** Implementation.
