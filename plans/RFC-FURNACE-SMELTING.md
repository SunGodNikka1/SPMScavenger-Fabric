# RFC: Furnace smelting (charcoal + iron ingots)

## RFC Identity

| Field | Value |
| --- | --- |
| **Project root** | `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric` |
| **Mod** | Social Player Mobs: Scavenger (`spmscavenger`) |
| **Scope** | PlayerMob furnace use — charcoal branch, iron smelting, shared smelt policy |
| **Mode** | `PLANNING` (continuation — post-FS-8 reconcile; FS-10 readiness) |
| **Status** | FS-1–FS-9 `IMPLEMENTED` (unit/static/build); D-FSM-010/012/013 `LOCKED`; D-FSM-011 `SUPERSEDED` by FS-8; FS-10 `READY` pending launch approval; RT-F* `UNVERIFIED` |
| **Baseline version** | `1.9.2` (`gradle.properties` `mod_version`) |
| **Host mod** | Social Player Mobs `v0.86.0` (reference at `Projects/references/SocialPlayerMobs-v0.86.0/`) |
| **Owners** | User (product) |
| **Peer review** | `Agent_Cursor 4` · `Agent_Codex` · `Agent_Claude` · `Agent_PeerReviewer` · `Agent_Cursor` (FS-1–FS-3; FS-6 datapack; this reconcile) |
| **Last update** | 2026-08-08 ~18:50 PDT (Agent_Cursor Continue the RFC) |
| **Related** | `plans/RFC-TOOL-TIER-UPGRADES.md` Phase 2 (iron craft/gather); charcoal unblocks no-coal biomes |

---

## Executive Summary

Phase 1 tool tiers stop at **stone** because vanilla **iron tools require smelted ingots**, and
Scavenger has **no furnace goal** today. Torch crafting already accepts **coal or charcoal**
(`ScavengerCrafting.java`), but mobs cannot **produce** charcoal or smelt ore.

This RFC covers **furnace infrastructure and smelting behaviour** only. **Iron pick/axe crafting,
ore-gather policy extensions, and config tier caps** stay in `RFC-TOOL-TIER-UPGRADES.md` Phase 2 and
depend on this RFC shipping first.

**Proposed graph:**

```text
stone tools → (optional) iron ore gather → smelt at furnace → iron ingots
logs + furnace → charcoal (when coal absent, torch chain needs fuel)
```

Planning does **not** authorize implementation, Minecraft launch, commit, or push.

**Continuation result (`SOURCE_CONFIRMED`, Agent_Codex):** the initial architecture direction is
sound, but the first draft is not implementation-ready. MC 1.21.1 already supplies data-driven
`RecipeManager#getRecipeFor(RecipeType.SMELTING, SingleRecipeInput, Level)` and a live furnace fuel
map. Ordinary iron ore loot is `raw_iron`, not the ore block. Furnace ownership, interruption,
save/reload recovery, output capacity, and atomic transfers remain unresolved high-severity design
issues; FS-1+ must not start until D-FSM-002 and D-FSM-006–008 reach consensus.

**Continuation result (`CODE_CONFIRMED`, Agent_Cursor, 15:37):** peer-reviewed Codex/Claude blockers
against in-repo precedents. Promoted D-FSM-002/003/004/006/007/008/009 to `CONSENSUS` with concrete
contracts (owned furnaces + communal opt-in; live recipe/fuel; face-API I/O; insert/extract
transactions; `SavedData` job ticket). FS-1 still blocked on **user lock**, not missing research.

**Implementation (`CODE_CONFIRMED`, Agent_Cursor, FS-1):** `FurnacePolicy` + U-F1–U-F3.

**Implementation (`CODE_CONFIRMED`, Agent_Cursor, FS-2):** Config
(`smeltEnabled`/`furnaceSearchRadius`/`placeFurnaces`/`useCommunalFurnaces`) + Cloth UI;
`FurnaceStations` find/mayUse/walk-claim/place; `FurnaceJobSavedData` + `StackFingerprint`; U-F6
round-trip + fail-closed reclaim.

**Implementation (`CODE_CONFIRMED`, Agent_Cursor, FS-3; priority rechecked by Agent_Codex):**
`FurnaceTransfers` face-API insert/extract (U-F4/U-F5); `MAKE_FURNACE`;
`SmeltAtFurnaceGoal` registered at priority 3; tickets on insert.
`.\gradlew.bat test` BUILD SUCCESSFUL. Runtime smelt behaviour `UNVERIFIED`.

**Continuation review (`CODE_CONFIRMED`, Agent_Codex, current source):** FS-1–FS-5 are present, but
the iron producer is still enabled by a fixed target of 6 while no iron-tool consumer exists.
D-FSM-010's consumer-driven direction is supported, but its “trivial resolver” currently duplicates
recipe knowledge instead of sharing the specification used by actual crafting; that ownership issue
keeps D-FSM-010 `CONTESTED`. The face abstraction also remains incomplete because fuel insertion is
fixed to `Direction.NORTH`; D-FSM-012 defines horizontal-face negotiation. No implementation or
runtime launch occurred in this continuation.

**FS-6 correction (prior, Agent_Codex):** TEST_MATRIX named `test-datapacks/phase2-furnace/` while
the directory was absent (`NOT FOUND` ×3) — FS-6 was `PARTIAL`.

**FS-6 repair (`SOURCE_CONFIRMED`, Agent_Cursor, this continuation):** created
`test-datapacks/phase2-furnace/` (namespace `spm_phase2`) mirroring Phase 1 layout — README,
`pack.mcmeta` format 34, `help`/`quickstart`/`setup`, arena fixtures, and presets
`need_charcoal` / `need_iron_smelt` / `player_furnace_test` / `second_claimant` for RT-F1–RT-F5.
Datapack layout is complete; RT-F* behaviour remains `UNVERIFIED` until explicit launch approval
(FS-10). No Java or Minecraft launch in this continuation.

**D-FSM-010 peer resolve (`INFERRED`→`CONSENSUS`, Agent_Cursor):** Option **B** (consumer-owned
immutable recipe specification shared by craft `apply` and requirement emission) accepted over
Option A (separate trivial resolver duplicating 3+2). Aligns with Agent_Codex objection; Option C
remains deferred. FS-8 stays blocked on the tool-tier consumer task (`TT-2b`).

**Current continuation (`SOURCE_CONFIRMED` / `CODE_CONFIRMED`, Agent_Codex):** D-FSM-010 Option B
is narrowed to a single-frontier contract and reaches `LOCKED`: the tool policy selects one desired
upgrade recipe (pick before axe), that recipe owns both atomic craft inputs and emitted deficits,
and the furnace consumes only the currently selected processed-material demand. D-FSM-012 also
reaches `LOCKED` after independent peer support and explicit U-F10 acceptance criteria.

FS-6 is reopened. Its files and internal function targets exist and `pack.mcmeta` parses, but
`_init_scoreboard.mcfunction` contains `data modify storage spm_phase2:main set value 1b initialized`.
The mapped 1.21.1 `DataCommands` tree requires `targetPath → set → value`; the trailing
`initialized` cannot serve as the target path. Datapack presence therefore does not satisfy the
static command gate, and RT-F* remains blocked before launch approval is even considered.

**Implementation (`CODE_CONFIRMED`, Agent_Codex):** FS-6 now passes its static gate: the storage
target path precedes `set value`; `pack.mcmeta` parses at format 34; all 33 internal references
across 27 function files resolve. FS-7 replaces fixed NORTH fuel insertion with deterministic
horizontal preflight and a single-face atomic write; U-F10 proves EAST-only success and the
no-face test proves rollback. The user resolved D-FSM-011: `ironStockTarget` is configurable and
defaults to 0, while explicit 6 retains the test path. Focused tests and `clean build` passed; 101
tests reported zero failures/errors/skips. Runtime behavior remains `UNVERIFIED` because no
Minecraft launch was authorized.

**Continuation reconcile (`CODE_CONFIRMED`, Agent_Cursor):** independently re-audited current source
against Identity/Tasks/Gates. FS-8 + TT-2b are present (`WorkDemandPolicy`,
`ScavengerCrafting.ConsumerRecipeSpec` / `MAKE_IRON_*`, `CRAFTABLE_TIER_CAPS` includes IRON,
`ironStockTarget`/`needsIronIngot` `NOT FOUND` in `src`). Several RFC sections still described
pre-FS-8 state (Material demand `BLOCKED`, Gates unchecked, D-FSM-011 as live interim). This
continuation closes that documentation drift, marks D-FSM-011 `SUPERSEDED`, promotes FS-10 to
`READY` (launch still separately authorized), and aligns Phase 2 datapack tellraws with
consumer-driven RT-F2. No Java changes; no Minecraft launch.

---

## Collaboration Protocol

- This continuation is **`Agent_Cursor`** (post-FS-8 peer reconcile; FS-10 readiness).
- Prior Codex/PeerReviewer FS-6–FS-9 implementation evidence retained.
- Update stable topic slots; add Contribution blocks for substantive edits.
- Runtime RT-F* requires explicit launch approval — do not infer from unit/build success (AV-1).

---

## Baselines and Current Implementation

### What exists (`CODE_CONFIRMED`, current source)

| Component | Path | Behaviour |
| --- | --- | --- |
| Torch fuel | `ScavengerCrafting.java` | `count(COAL) + count(CHARCOAL)` for torches/campfire |
| Coal gather | `GatherResourcesGoal.java` | Mines exposed coal ore; direct-keep coal |
| Ore protection | `GatherProtection.isGatherableOre` | **Coal only** — iron ore not gatherable |
| Crafting station | `CraftTorchesGoal.java` | Find/place crafting table within radius |
| Tool policy | `ToolTierPolicy.java` + `ScavengerCrafting` | Ownership ranks + iron craft via shared `ConsumerRecipeSpec` |
| SPM furnace | `SocialPlayerMobs-v0.86.0` | **NOT FOUND** — three probes: `furnace`, `smelt`, `Smelt` in `src/` |
| Work demand | `WorkDemandPolicy.java` | One selector; charcoal SURVIVAL vs iron PROGRESSION; `MaterialDemand` payload |
| Smelt decision | `FurnacePolicy.java` | Live recipe/fuel; demand from `WorkDemandPolicy` only |
| Furnace execution | `SmeltAtFurnaceGoal.java` | Priority 3; resume `INSERTED` tickets before fresh demand |
| Furnace ownership | `FurnaceStations.java`, `FurnaceJobSavedData.java` | Owned stations, walk claims, persisted inserted-job tickets |
| Transfers | `FurnaceTransfers.java` | Atomic snapshot insert/extract; deterministic horizontal fuel-face preflight |
| Runtime presets | `test-datapacks/phase2-furnace/` | Format 34; consumer-driven RT-F2 docs; runtime behavior unverified |

### Historical defect — producer without consumer (`CLOSED` by FS-8 / TT-2b)

Prior to FS-8, iron demand was push-based (`needsIronIngot` / fixed stock). That dead end is closed
in current source: new iron batches require a live iron-tool frontier (`maxPickTier`/`maxAxeTier`
IRON + stone-tier ownership + deficit from the shared recipe spec). Evidence: `ironStockTarget` and
`needsIronIngot` `NOT FOUND` in `src`; U-F7 rejects raw-iron-only demand (`CODE_CONFIRMED`).

### Remaining gaps (`CODE_CONFIRMED` + `UNVERIFIED`)

1. Iron **ore gather** still tool-tier TT-2c — smelting consumes backpack raw iron, not world ore.
2. Horizontal fuel faces unit-proven; real third-party asymmetric furnaces `UNVERIFIED`.
3. RT-F1–RT-F5 blocked only on explicit Minecraft launch approval (FS-10).
4. 1/10/50/100-mob furnace contention/cost left `UNVERIFIED`.
5. Dedicated U-F9 ticket-vs-zero-demand unit test not found — resume-before-demand is
   `CODE_CONFIRMED` in `SmeltAtFurnaceGoal`, runtime reclaim still `UNVERIFIED`.

---

## Research Ledger

| Reference | Type | Technique | Applicability | Evidence |
| --- | --- | --- | --- | --- |
| Crafting table goal | In-repo | Search radius + optional place | Mirror for furnace | `CODE_CONFIRMED` `CraftTorchesGoal` |
| Coal direct-keep | In-repo | `wantedDrop` + harvest hand-off | Mirror for ingots/charcoal | `CODE_CONFIRMED` `GatherResourcesGoal` |
| Vanilla furnace | Game | 3 slots: input, fuel, output | Block entity tick smelt | `DOCUMENTATION_CONFIRMED` |
| SPM coal pickup gap | Host | `ItemPickupPolicy` ignores coal | Why direct-keep exists | `CODE_CONFIRMED` README |
| Tool-tier Phase 1 | Sibling RFC | Policy-first craft/gather | Iron craft blocked here | `plans/RFC-TOOL-TIER-UPGRADES.md` |
| MC 1.21.1 `minecraft-common` mapped JAR | Game/API | `RecipeManager#getRecipeFor(RecipeType.SMELTING, SingleRecipeInput, Level)` | Resolve datapack/modded recipes instead of copying recipe inputs | `SOURCE_CONFIRMED` `javap` |
| MC 1.21.1 furnace API | Game/API | `AbstractFurnaceBlockEntity` is a 3-slot `WorldlyContainer`; `getFuel()` exposes the live item→burn-tick map | Recipe/fuel planning and real block-entity execution | `SOURCE_CONFIRMED` `javap` |
| MC 1.21.1 iron loot + recipes | Game data | Normal iron ore drops `raw_iron`; smelting recipes accept raw iron and Silk-Touch ore | Correct gather→smelt hand-off | `SOURCE_CONFIRMED` packaged JSON |
| MC 1.21.1 charcoal recipe | Game data | `#minecraft:logs_that_burn` → charcoal, 200 ticks | Data-driven log support | `SOURCE_CONFIRMED` packaged JSON |
| MC 1.21.1 `DataCommands` mapped bytecode | Game/API | `data modify` parses target path before `set value` | Static datapack command validation | `SOURCE_CONFIRMED` `minecraft-common` `javap` |
| Phase 2 datapack reference scan | In-repo validation | Resolve every `function spm_phase2:*` target + parse `pack.mcmeta` + reject known malformed initializer | Reproducible runtime setup | Format 34; 27 files; 33/33 targets; initializer pass (`CODE_CONFIRMED`) |

---

## Brainstorming

- Recipe-backed `SmeltPlan` rather than a fixed recipe enum → **Topic: Smelting**.
- A persisted furnace job/claim may be necessary for save/reload recovery → **Topic: Persistence and contention**.
- Fuel should minimize reserved-resource loss and waste, not follow a copied item priority → **Topic: Smelting**.

---

## Topic Index

| Topic | Status | Summary |
| --- | --- | --- |
| [Smelting](#topic-smelting) | `IMPLEMENTED` | Policy + stations + `SmeltAtFurnaceGoal` (unit) |
| [Charcoal](#topic-charcoal) | `IMPLEMENTED` | Demand wired through goal via `FurnacePolicy` |
| [Iron production](#topic-iron-production) | `IMPLEMENTED` | Raw-iron plan + goal path (gather still tool-tier) |
| [Material demand](#topic-material-demand) | `IMPLEMENTED` | `WorkDemandPolicy` + shared iron `ConsumerRecipeSpec` (FS-8) |
| [Compatibility](#topic-compatibility) | `IMPLEMENTED` (unit) | Horizontal face negotiation + rollback; runtime modded-furnace matrix unverified |
| [Persistence and contention](#topic-persistence-and-contention) | `IMPLEMENTED` | SavedData tickets + session walk claims (FS-2) |
| [Validation](#topic-validation) | `PARTIAL` | U-F1–U-F8/U-F10 + datapack static; U-F9 code-only; RT-F* unverified |

---

## Topic: Smelting

**Status:** `IMPLEMENTED` (unit; runtime `UNVERIFIED`)

**Goal:** Mobs smelt items at a furnace the same way they craft at a table — walk to station, insert
input/fuel, wait for output, direct-keep result.

**Current implementation:** `FurnacePolicy` (FS-1); `FurnaceStations` + `FurnaceJobSavedData` +
config/UI (FS-2); `FurnaceTransfers` + `SmeltAtFurnaceGoal` + `MAKE_FURNACE` (FS-3).

**Candidate designs:**

| ID | Approach | Benefit | Cost |
| --- | --- | --- | --- |
| A | Inline smelt inside `CraftTorchesGoal` | One goal | Bloated; mixes craft + smelt state machines |
| B | Recipe-backed `FurnacePolicy` (pure decisions) + `SmeltAtFurnaceGoal` **(accepted)** | Testable; keeps vanilla/datapack recipes authoritative | New goal registration, job ownership, priority tuning |
| C | Simulate smelt in backpack (no world furnace) | Simple | Not playerlike; breaks parity |

**Accepted `FurnacePolicy` API sketch:**

```java
// Sketch only: recipe holder/id and assembled result come from RecipeManager, never a copied table.
record SmeltPlan(ResourceLocation recipeId, ItemStack input, ItemStack output,
                 int cookingTicks, int batchSize, ItemStack fuelChosen, int fuelBurnTicks) {}

Optional<SmeltPlan> plan(ServerLevel level, Container backpack, SmeltDemand demand);
```

Product demand may still distinguish charcoal from iron; recipe matching and output resolve from
live `RecipeManager`. Fuel duration comes from `AbstractFurnaceBlockEntity.getFuel()` /
`isFuel(ItemStack)`.

**Fuel selection (D-FSM-006):** among backpack stacks where `isFuel(stack)`:

1. Prefer the smallest burn-time item that still covers `cookingTicks * batchSize` (minimize waste).
2. Never choose a stack that would drop protected reserves below policy floors (logs needed for
   planks/sticks/table/campfire; charcoal/coal needed for the torch craft that charcoal is meant to unlock — charcoal job may consume **one** surplus log only).
3. Prefer non-reserve fuels (coal leftover, lava bucket if ever held, datapack fuels) before surplus logs.
4. If no fuel covers the job, refuse the plan (`Optional.empty`) — do not start a partial burn.

**Furnace discovery/placement (mirror crafting table search/place only):**

| Config key (proposed) | Default | Purpose |
| --- | --- | --- |
| `furnaceSearchRadius` | `24` | Match `craftingTableSearchRadius` |
| `placeFurnaces` | `true` | Craft 8 cobble furnace when none usable in range |
| `smeltEnabled` | `true` | Master toggle |
| `useCommunalFurnaces` | `false` | Opt-in: claim empty unowned furnaces (D-FSM-002) |

**Station I/O (D-FSM-009):** every insert/extract uses `WorldlyContainer` faces — UP input, SIDES
fuel, DOWN result — never hardcoded `0/1/2`.

**Trade-offs:** Real block-entity smelting is slower but observable and matches player behaviour.
Instant smelt would be faster but wrong for parity.

**Resolved wiring (`CODE_CONFIRMED`):** smelting is registered at priority 3 beside gather/craft;
torch placement is priority 4 and campfire priority 7. Whether this produces the intended runtime
share is still `UNVERIFIED`, but the registration question itself is closed.

**Decision:** [D-FSM-001](#d-fsm-001-policy--goal-split), [D-FSM-002](#d-fsm-002-furnace-station-mirror),
[D-FSM-006](#d-fsm-006-recipefuel-discovery), [D-FSM-009](#d-fsm-009-furnace-slot-access-contract).

---

## Topic: Charcoal

**Status:** `IMPLEMENTED` (unit; runtime `UNVERIFIED`)

**Goal:** When coal is unavailable and the torch chain needs fuel, smelt logs → charcoal.

**Current implementation:** `FurnacePolicy.needsCharcoal` + `SmeltAtFurnaceGoal` production path.

**Evidence:** Scenario Parity row "No coal biome" in tool-tier RFC (`PARTIAL` — stone helps felling only).

**Accepted rule (D-FSM-003):**

- Trigger when `count(COAL) == 0` AND torch/campfire craft is blocked on fuel AND surplus logs remain
  after `logReserveForCraftChain(backpack)` (minimum logs still needed for pending plank/stick/table
  /campfire steps — exact formula owned by `FurnacePolicy` unit tests).
- Smelt **one** log → one charcoal per job (vanilla recipe via live `RecipeManager`).
- **Must not** smelt all logs or start charcoal while coal already covers the torch demand.

**Trade-offs:**

| Choice | Benefit | Risk |
| --- | --- | --- |
| Charcoal before iron | Unblocks torch chain everywhere | Extra goal time; log budget pressure |
| Defer charcoal | Smaller RFC | No-coal worlds stay partial |

**Recommendation:** Include charcoal in this RFC — it shares furnace infrastructure and fixes a known parity gap.

---

## Topic: Iron production

**Status:** `IMPLEMENTED` (smelt path unit; ore gather still tool-tier)

**Goal:** Produce `iron_ingot` in the backpack for the tool-tier RFC to consume.

**Scope boundary:** **This RFC owns smelting ingots.** Tool-tier RFC owns iron ore gather rules,
`MAKE_IRON_*` craft steps, stone-tool disposal, and `CRAFTABLE_TIER_CAPS` expansion.

**Vanilla Phase 2 minimum:**

| Input | Output | Pick requirement |
| --- | --- | --- |
| `raw_iron` (normal drop) or Silk-Touch `iron_ore` / `deepslate_iron_ore` | `iron_ingot` | Stone+ pick to obtain the normal drop (`SOURCE_CONFIRMED`) |

**Deferred:** Deepslate iron gather (tool-tier RFC noted deepslate cobble deferred); blast furnace; raw iron block.

**Demand source (FS-8):** live consumer deficit from `WorkDemandPolicy` / shared iron recipe spec.
Interim `ironStockTarget` (D-FSM-011) is **removed** / `SUPERSEDED`.

---

## Topic: Material demand

**Status:** `IMPLEMENTED` (unit/build; runtime `UNVERIFIED`) — D-FSM-010/013 `LOCKED`

**Goal:** Start a furnace batch only because a live consumer needs its output, then stop new batches
as soon as that need disappears while still recovering any already-inserted job.

**Current implementation (`CODE_CONFIRMED`):** `FurnacePolicy.demand()` maps the single
`WorkDemandPolicy.select(...)` winner to `CHARCOAL` / `IRON` / `NONE`. Iron demand requires
`ScavengerCrafting.activeIronToolRecipe(...)` and a positive ingot deficit from that shared
`ConsumerRecipeSpec`. Charcoal remains SURVIVAL-class and wins ties against iron PROGRESSION.
`CRAFTABLE_TIER_CAPS` includes `IRON`; craft steps `MAKE_IRON_PICKAXE` / `MAKE_IRON_AXE` exist.

### Agreed boundary

- Consumers own the question “what result is needed?”
- The demand policy aggregates current deficits and arbitrates urgency; it owns no item recipes.
- Furnace policy decides whether a selected processed-material deficit can be produced from current
  input/fuel through a live smelting recipe.
- Demand is recomputed from current backpack/equipment state. It is not persisted or latched.
- An `INSERTED` ticket resumes independently of current demand; zero demand prevents only the next
  batch.

### Competing proposals — requirement source

| Option | Shape | Benefit | Risk | Position |
| --- | --- | --- | --- | --- |
| A | Separate trivial resolver: iron pick/axe → 3 ingots + 2 sticks | Very small first patch | Duplicates the quantities used by actual crafting; producer and consumer can drift | Rejected — drift class |
| B | Consumer-owned immutable recipe specification feeds both `apply` and emitted requirements | One source of truth; no reverse recipe scan; directly testable | Requires a narrow crafting-policy refactor | **Accepted (`CONSENSUS`)** |
| C | Shared reverse recipe index over all loaded recipes | Data-driven and extensible | Reload lifecycle, tag alternatives, cycles, recursion, and modpack-scale indexing before runtime proof | Deferred until a second real consumer requires it |

The selected architecture remains “generic demand format now, generic generation later.” **Accepted
requirement source:** Option **B** — the iron-tool consumer owns one immutable recipe specification
used by both atomic craft `apply` and requirement emission. Agent_Codex contested A; Agent_Cursor
peer-accepted B (2026-08-08). Option A is rejected for this RFC.

### Locked first-slice contract

```text
ConsumerRecipeSpec {
  output; ingredients; replacedItem; craftStep
}
        ├── canApply/apply (atomic craft)
        └── requirements(backpack + equipment)
                         ↓
MaterialDemandResolver derives current material deficits
                         ↓
FurnacePolicy plans only a demanded smeltable output
```

### Locked single-frontier semantics

The first slice does **not** emit both the future pick and axe recipes at once. `ToolTierPolicy`
selects the next desired upgrade frontier using the existing pick-before-axe progression order.
Only that one `ConsumerRecipeSpec` emits deficits. Once it is crafted—or invalidated because the mob
loots an equivalent/better tool—the next evaluation may select the next frontier.

```text
target IRON; owns stone pick + stone axe
        ↓
frontier = IRON_PICKAXE spec
        ↓
emit at most 3-ingot deficit from that spec
        ↓ craft pick
next evaluation → frontier = IRON_AXE spec
```

This avoids smelting six ingots before the first useful upgrade and prevents a downstream recipe
from reserving the eight-slot backpack early. The shared `WorkDemandPolicy` may arbitrate this
frontier against charcoal's real torch-fuel requirement, but it does not expose downstream recipes.

| Context | Discovery | Owner | Refresh / stale handling | Persistent? |
| --- | --- | --- | --- | --- |
| Desired tool frontier | Backpack + main hand + configured caps | `ToolTierPolicy` | Recompute on every bounded goal eligibility evaluation | No |
| Recipe ingredients | Selected immutable `ConsumerRecipeSpec` | Crafting consumer | Same object drives demand and atomic `apply` | No |
| Material deficit | Current owned count matched against spec | `MaterialDemandResolver` | Derived; never stored or latched | No |
| Inserted furnace job | Persisted ticket/fingerprints | `FurnaceJobSavedData` | Resume independently of later demand | Yes, until closed |

**Deterministic arbitration:** compare `DemandClass` first, then bounded within-class utility, then a
stable reason/material key. Do not depend on registration or branch order. The Phase 2 exact iron
ingredient may use an exact-item key; tag-backed matching remains supported by the format but need
not be generalized into arbitrary multi-alternative recipe resolution in this slice.

### Cross-RFC demand boundary — D-FSM-013 (`LOCKED` / `IMPLEMENTED`)

Agent_Claude correctly found that the autonomous-progression RFC's `WorkDemand` and this RFC's
`MaterialDemand` were becoming two independent arbitration records. The furnace needs a material
deficit, while the broader planner also needs non-material actions such as exploration, station use,
and shelter. They are related but not interchangeable.

| Option | Shape | Benefit | Risk |
| --- | --- | --- | --- |
| A | Collapse everything into one flat record with optional material/location fields | One name/type | Invalid null combinations; furnace facts become executor commands |
| B | `WorkDemand` envelope with typed payload; `MaterialDemand` is a pure payload/fact **(preferred)** | One arbitration policy; material facts remain reusable and testable | Requires an explicit ownership boundary across RFCs |
| C | Independent `WorkDemandPolicy` and `MaterialDemandPolicy` selectors | Each subsystem stays local | Conflicting winners, duplicate urgency fields, and goal churn |

**Proposed contract:**

```text
MaterialDemand { materialKey, derivedDeficit, consumerKey }
        ↓ payload
WorkDemand { workType, demandClass, derivedUtility, reason, payload }
        ↓ selected by the one WorkDemandPolicy
GATHER_BLOCK | SMELT_BATCH | CRAFT_STEP | ... existing executor
```

- `MaterialDemandResolver` derives facts; it does not arbitrate or schedule.
- `WorkDemandPolicy` is the only selector and owns demand class, utility band, stable tie-break, and
  executor mapping. Its canonical plan is `RFC-VANILLA-AUTONOMOUS-PROGRESSION.md`.
- This furnace RFC owns the material payload semantics and `SMELT_BATCH` consumption contract.
- `RFC-TOOL-TIER-UPGRADES.md` TT-2b owns the consumer recipe specification.
- FS-8/TT-2b use the consumer-owned spec directly and **must not wait for or implement** the generic
  reverse-recipe index. That index belongs to later bounded backward chaining and remains deferred.

**Must happen:** one selected `WorkDemand(SMELT_BATCH, payload=MaterialDemand(...))` reaches the
furnace; its deficit is recomputed after inventory/equipment changes.  
**Must not happen:** both policies select work independently, urgency exists in both records, a
non-material task fabricates an empty material key, or FS-8 scans every recipe by output.

`IngredientKey`/ingredient matching may represent a concrete item or tag, but it is ephemeral across
data reloads and must define canonical equality before demands can be aggregated. No storage count,
world scan, reverse recipe scan, or second equipment evaluator belongs in this slice.

### Validation required before implementation/verification

- **Must happen:** an iron-tool requirement emits the exact ingot deficit used by the craft
  transaction; satisfying or removing the requirement suppresses the next iron batch.
- **Must happen:** an already-`INSERTED` ticket remains resumable after demand becomes zero.
- **Must not happen:** recipe quantities exist in two mutable sources, charcoal loses its real torch
  consumer, or the aggregator contains `switch (material)` logic.
- Unit scenarios: no consumer; pick only; axe only; both; looted iron tool mid-job; config cap lowered;
  inserted ticket after demand disappears; charcoal and blocking iron competing.

### Consensus

**Accepted:** Option B with single-frontier emission and non-persisted derived deficits.  
**Why:** one recipe truth drives production and consumption; bounded frontier work fits the existing
goal architecture and backpack constraints without a reverse-recipe planner.  
**Supporting agents:** Agent_Codex (proposal/refinement), Agent_Cursor (independent acceptance).  
**Remaining objections:** none architectural; runtime RT-F* and dedicated U-F9 unit still open.  
**Rejected:** Option A duplicate resolver; Option C reverse-index planner before a second use case.  
**Evidence:** FS-8/TT-2b implementation `CODE_CONFIRMED`; gameplay behavior remains `UNVERIFIED`.  
**Status:** `LOCKED` / `IMPLEMENTED`.

**Implementation:** synchronized FS-8 + `RFC-TOOL-TIER-UPGRADES.md` TT-2b now provide the consumer
spec, typed payload, and one selector. The interim `ironStockTarget` is removed. Already-inserted
tickets still resume before fresh demand is evaluated.

---

## Topic: Compatibility

**Status:** `IMPLEMENTED` (unit; runtime integration `UNVERIFIED`)

| Risk | Mitigation | Evidence |
| --- | --- | --- |
| SPM floor pickup ignores ingots | Direct-keep smelt output (D-FSM-004) | Same as coal (`CODE_CONFIRMED`) |
| `mobGriefing false` | Smelt goal off when griefing disabled; no place | `GatherResourcesGoal` / `CraftTorchesGoal` precedent |
| Player furnace grief | Default skip unowned furnaces; communal opt-in only (D-FSM-002) | Not geometric “build radius” alone |
| PolyForm Shield | No SPM code vendored | `AGENTS.md` SPM-4 |
| Datapack/modded smelting changes | Live `RecipeType.SMELTING` + face API | Mapped 1.21.1 APIs (`SOURCE_CONFIRMED`) |
| Modded furnace layouts | `WorldlyContainer` faces (D-FSM-009) | Hopper contract (`CODE_CONFIRMED` Agent_Claude) |
| Side-asymmetric `WorldlyContainer` fuel access | Probe all horizontal faces; commit through one accepting face (D-FSM-012 / FS-7) | Unit EAST-only + no-face rollback `CONFIRMED`; live modded furnace `UNVERIFIED` |

**SPM probe (`NOT FOUND` ×3):** `furnace`, `smelt`, `AbstractFurnace` in `SocialPlayerMobs-v0.86.0/src/` — no host behaviour to reuse. Re-checked this continuation: still absent.

---

## Topic: Persistence and contention

**Status:** `IMPLEMENTED` (FS-2 unit; runtime reclaim `UNVERIFIED`)

**Goal:** No player or other mob loses items, and an interrupted/unloaded smelt can be recovered
without duplicating output.

**Current implementation:** `FurnaceJobSavedData` (dimension `SavedData`) stores scavenger-owned
positions and open `FurnaceJobTicket`s; `FurnaceStations` holds session walk claims
(`ConcurrentHashMap`, 600-tick expiry). U-F6 confirms NBT round-trip and fail-closed reclaim.

**Candidate designs:**

| Design | Benefit | Cost/risk |
| --- | --- | --- |
| Use any compatible furnace and infer ownership from its contents | No new persistence | **Rejected:** indistinguishable player/mob jobs; theft/merge risk |
| Use only empty furnaces with an in-memory claim | Simple during one session | Claim disappears on restart while items remain |
| Persist a bounded job ticket in world `SavedData` **(accepted)** | Recoverable across unload/restart; explicit ownership | New saved-data lifecycle |
| Abort on every goal interruption and return inserted items | No long-lived job | Furnace progress constantly resets under combat; shutdown/unload still needs recovery |

**Accepted ticket sketch (D-FSM-007):**

```text
FurnaceJobTicket {
  dimension, BlockPos furnacePos,
  UUID claimantMob,
  ItemStack inputFingerprint, int inputCount,          // what we inserted
  ItemStack fuelFingerprint, int fuelCount,
  ItemStack expectedOutputFingerprint, int reservedOutputSlots,
  long startedGameTime, ResourceLocation recipeId,
  JobPhase { RESERVED, INSERTED, EXTRACTING, CLOSED }
}
```

**Invariants:** one claimant per furnace; never touch pre-existing non-job stacks; reserve backpack
output space before insertion; insert transaction and extract transaction are separate atomic
snapshots (D-FSM-008); extraction takes only the job's produced delta; orphan tickets on reload
attempt reclaim if fingerprints match, else fail closed and close the ticket without inventing
stacks; broken furnace / death → close ticket (world drops own the items).

**Station policy (D-FSM-002 accepted):** Scavenger-placed furnaces are auto-claimable by the placer.
Default: skip existing unclaimed furnaces. `useCommunalFurnaces=true` allows claiming an **empty**
unowned furnace. Never merge into non-empty foreign contents.

**Session claim:** reuse the bed pattern (`ConcurrentHashMap` + expiry) for “walking to this furnace”
contention; the `SavedData` ticket owns the insert→extract lifespan.

---

## Topic: Validation

**Status:** unit/static layer `IMPLEMENTED`; runtime behavior `UNVERIFIED`

### Unit matrix (draft)

| ID | Class | Must happen | Must not |
| --- | --- | --- | --- |
| U-F1 | `FurnacePolicyTest` | Log+empty torch fuel → `demand(CHARCOAL)` | Smelt when coal present | `CONFIRMED` unit |
| U-F2 | `FurnacePolicyTest` | Raw iron+coal → iron `SmeltPlan`; iron alone refuses | Consume ore without fuel | `CONFIRMED` unit |
| U-F3 | `FurnacePolicyTest` | Fuel picker skips reserved logs; planks preferred | Burn last logs needed for sticks | `CONFIRMED` unit |
| U-F4 | `FurnaceTransfersTest` | Insert rolls back if output cannot fit / face reject | Partial insert left in furnace | `CONFIRMED` unit |
| U-F5 | `FurnaceTransfersTest` | Extract only job-matching output | Steal pre-existing output | `CONFIRMED` unit |
| U-F6 | `FurnaceStationsTest` | Ticket + owned pos survive save/load; reclaim closes bad fingerprints | Duplicate output on reclaim | `CONFIRMED` unit |
| U-F7 | `FurnacePolicyTest` (FS-8) | Raw iron without live consumer → no iron demand | Convert raw iron merely because it exists | `CONFIRMED` unit |
| U-F8 | `FurnacePolicyTest` / `ScavengerCraftingTest` (FS-8) | Pick/axe share one `ConsumerRecipeSpec`; pick frontier before axe | Recipe quantities drift; dual frontiers | `CONFIRMED` unit |
| U-F9 | `SmeltAtFurnaceGoal` resume path | Demand zero stops future batches; `INSERTED` ticket still resumes | Orphan committed input/fuel | `CODE_CONFIRMED` goal order; dedicated unit `NOT FOUND` |
| U-F10 | `FurnaceTransfersTest` (FS-7) | EAST-only fake fuel face accepts atomic insert; no-face case rolls back | Mutate rejected faces or leave partial stacks | `CONFIRMED` unit |

### Runtime matrix (files/static structure `SOURCE_CONFIRMED`; behaviour `UNVERIFIED`)

Path: `test-datapacks/phase2-furnace/` — namespace `spm_phase2`. Quick start:
`/function spm_phase2:quickstart`. Spec: `docs/agent-workflows/RUNTIME_TEST_DATAPACK.md`.
Cross-check: `docs/porting/TEST_MATRIX.md`. Layout repaired under FS-6 (Agent_Cursor); do **not**
treat datapack presence as RT-F* proof (AV-1).

**Static audit (`SOURCE_CONFIRMED`, Agent_Codex):** `pack.mcmeta` parses and every internal
`function spm_phase2:*` reference resolves to a file. However, `_init_scoreboard.mcfunction:2` is
not valid target 1.21.1 command grammar:

```mcfunction
# Current — target path omitted before "set"; trailing token cannot repair it
data modify storage spm_phase2:main set value 1b initialized

# Required grammar/repair target
data modify storage spm_phase2:main initialized set value 1b
```

Mapped `DataCommands` constructs `targetPath` before literal `set`, then the `value` argument.
**Repair evidence (Agent_Codex):** corrected the line to the required form. Static validation parsed
`pack.mcmeta` (format 34), scanned 27 `.mcfunction` files, resolved all 33 internal function calls,
and rejected the known malformed trailing-path pattern. This is reproducible setup evidence only;
RT-F* still requires explicit launch approval and runtime observation.

| ID | Setup | Spawn preset | Must happen | Must not |
| --- | --- | --- | --- | --- |
| RT-F1 | `quickstart` | `spawn/need_charcoal` | Charcoal at owned furnace → torches | Burn all logs |
| RT-F2 | Set `maxPickTier=IRON`, then `arena/build` | `spawn/need_iron_smelt` | Smelt 3 ingots → craft iron pick | Producer-only hoard; steal player furnace |
| RT-F3 | `arena/build` | `spawn/player_furnace_test` | Skip busy furnace at +6 | Steal player coal |
| RT-F4 | RT-F2 + interrupt | manual `/reload` | Reclaim or fail-closed | Duplicate stacks |
| RT-F5 | RT-F2 → wait → `spawn/second_claimant` | P2B | One furnace claimant | Double insert |

Evidence column remains `UNVERIFIED` until launch approval and pinned log/screenshot.

---

## Decision Registry

| ID | Title | Status | Summary |
| --- | --- | --- | --- |
| [D-FSM-001](#d-fsm-001-policy--goal-split) | Policy + goal split | `LOCKED` | Policy + `SmeltAtFurnaceGoal` shipped |
| [D-FSM-002](#d-fsm-002-furnace-station-mirror) | Furnace station | `LOCKED` | mayUse + find/place + communal opt-in (FS-2) |
| [D-FSM-003](#d-fsm-003-charcoal-branch) | Charcoal branch | `LOCKED` | Demand + surplus-log rules in `FurnacePolicy` (FS-1) |
| [D-FSM-004](#d-fsm-004-direct-keep-output) | Direct-keep output | `LOCKED` | Extract matching fingerprint only (FS-3) |
| [D-FSM-005](#d-fsm-005-fuel-priority) | Fuel priority | `SUPERSEDED` | Replaced by live fuel discovery and reserve-aware choice |
| [D-FSM-006](#d-fsm-006-recipefuel-discovery) | Recipe/fuel discovery | `LOCKED` | Live lookup SPI + `liveRecipes`/`liveFuels` (FS-1) |
| [D-FSM-007](#d-fsm-007-furnace-job-ownership) | Furnace job ownership | `LOCKED` | SavedData tickets + walk claims (FS-2) |
| [D-FSM-008](#d-fsm-008-atomic-transfer-and-capacity) | Atomic transfer and capacity | `LOCKED` | Insert/extract snapshots (FS-3) |
| [D-FSM-009](#d-fsm-009-furnace-slot-access-contract) | Furnace slot access | `LOCKED` | Face API in `FurnaceTransfers` (FS-3) |
| [D-FSM-010](#d-fsm-010-consumer-driven-material-demand) | Consumer-driven demand | `LOCKED` / `IMPLEMENTED` | Option B single-frontier shared recipe spec (FS-8 + TT-2b) |
| [D-FSM-011](#d-fsm-011-interim-disposition-of-the-iron-branch) | Iron branch interim | `SUPERSEDED` | `ironStockTarget` removed after FS-8 consumer shipped |
| [D-FSM-012](#d-fsm-012-horizontal-fuel-face-negotiation) | Horizontal fuel face negotiation | `LOCKED` / `IMPLEMENTED` | Preflight horizontal faces; commit through one accepting face |
| [D-FSM-013](#d-fsm-013-workmaterial-demand-boundary) | Work/material demand boundary | `LOCKED` / `IMPLEMENTED` | One `WorkDemandPolicy`; `MaterialDemand` is a typed deficit payload |

### D-FSM-001: Policy + goal split

**Status:** `LOCKED` (Begin implementation FS-1) — policy `IMPLEMENTED`; goal pending FS-3  
**Recommendation:** Proposal B — recipe-backed pure policy + dedicated smelt goal.  
**Alternatives:** Inline in craft goal (rejected — complexity); backpack-only simulation (rejected — parity).
**Supporting agents:** Agent_Cursor 4; Agent_Codex (revision/peer review); Agent_Cursor (FS-1).
**Evidence:** `FurnacePolicy.java`, `FurnacePolicyTest` U-F1–U-F3 (`CODE_CONFIRMED`).

### D-FSM-002: Furnace station mirror

**Status:** `LOCKED` (Begin implementation FS-2)  
**Accepted:** Search/place/navigation may mirror the table (`CraftTorchesGoal.findTable` /
`placeTable`), but ownership must not.  
**Evidence:** `FurnaceStations.mayUse` / `findUsable` / `tryPlaceFromBackpack`; config
`useCommunalFurnaces` default false; `FurnaceStationsTest`.

| Option | Benefit | Risk |
| --- | --- | --- |
| Any nearby compatible furnace | Most player-like reuse | Can insert into or extract from a player/other-mob furnace |
| Only Scavenger-placed and persistently claimed furnaces | Strong ownership safety | More furnace blocks; cannot reuse a village/player utility furnace |
| Owned by default + explicit communal-furnace opt-in **(accepted)** | Safe default with user-controlled flexibility | Extra config and both modes require contention tests |

**Supporting agents:** Agent_Codex (objection); Agent_Claude (table is not a precedent); Agent_Cursor (concrete config + ticket coupling).

### D-FSM-009: Furnace slot access contract

**Status:** `LOCKED` (Begin implementation FS-3)  
**Evidence:** `FurnaceTransfers` uses UP/SIDES/DOWN only; U-F4/U-F5.

`AbstractFurnaceBlockEntity.SLOT_INPUT` / `SLOT_FUEL` / `SLOT_RESULT` are **`protected static`** — an
addon cannot read them. That leaves two routes:

| Route | Mechanism | Cost |
| --- | --- | --- |
| Hardcode `0` / `1` / `2` | Works for vanilla furnace, blast furnace, smoker | Gate SPM-0 **level 7** hardcode; silently wrong for any modded furnace-like block with a different layout |
| **Public `WorldlyContainer` face API** | `getSlotsForFace(UP/DOWN/SIDES)`, `canPlaceItemThroughFace`, `canTakeItemThroughFace`, `canPlaceItem` | One indirection |

**Accepted:** drive every furnace read and write through the face API (hopper contract).  
**Supporting agents:** Agent_Claude (API proof); Agent_Cursor (peer accept).

### D-FSM-003: Charcoal branch

**Status:** `LOCKED` (Begin implementation FS-1)  
**Accepted:** Smelt when `coal==0` and torch stock needs fuel and at least one **surplus** log
after plank/stick/table/campfire reserves. One log per charcoal job.  
**Supporting agents:** Agent_Cursor 4; Agent_Cursor (reserve rule + FS-1).
**Evidence:** `FurnacePolicy.needsCharcoal` / `logReserveForCraftChain`; U-F1/U-F3.

### D-FSM-004: Direct-keep output

**Status:** `LOCKED` (Begin implementation FS-3)  
**Accepted:** Extract only the job-owned produced delta after output capacity was reserved.
Do not remove arbitrary furnace output and do not start a job that cannot accept its result.
**Evidence:** `FurnaceTransfers.tryExtract` + U-F5.

### D-FSM-005: Fuel priority

**Status:** `SUPERSEDED` by D-FSM-006  
**Rejected:** copied coal→charcoal→planks order. Select from the live fuel map using cooking time,
batch size, and protected resource reserves; bootstrap charcoal must not consume the materials that
make the torch/tool chain possible.

### D-FSM-006: Recipe/fuel discovery

**Status:** `LOCKED` (Begin implementation FS-1)  
**Accepted:** Resolve smelting via injectable `RecipeLookup` (production: live
`RecipeManager#getRecipeFor(RecipeType.SMELTING, SingleRecipeInput, Level)`); fuel via injectable
`FuelLookup` (production: `AbstractFurnaceBlockEntity.getFuel()` / `isFuel`). Fuel pick: smallest
sufficient burn among non-reserved stacks. No copied production recipe/fuel tables (test stubs only).  
**Evidence:** `FurnacePolicy.liveRecipes` / `liveFuels` / `chooseFuel`; U-F2/U-F3.  
**Supporting agents:** Agent_Codex (proposal); Agent_Claude (feasibility); Agent_Cursor (FS-1).

### D-FSM-007: Furnace job ownership

**Status:** `LOCKED` (Begin implementation FS-2)  
**Accepted:** World `SavedData` stores bounded `FurnaceJobTicket`s and scavenger-owned positions;
session walk claims for path contention. Fail-closed reclaim drops mismatched tickets without
inventing stacks.  
**Evidence:** `FurnaceJobSavedData`, `StackFingerprint`, U-F6.

### D-FSM-008: Atomic transfer and capacity

**Status:** `LOCKED` (Begin implementation FS-3)  
**Accepted:** Reuse TT-0R snapshot-transaction shape, applied **separately** to insert and extract.
**Evidence:** `FurnaceTransfers.tryInsert` / `tryExtract`; U-F4/U-F5.

1. **Insert:** reserve backpack output space → snapshot backpack + furnace faces → move input/fuel
   through faces → commit both or roll back both → write ticket `INSERTED`.
2. **Wait:** vanilla furnace tick; do not hold a cross-tick mega-transaction.
3. **Extract:** snapshot → take only job delta via DOWN face → commit into reserved backpack space
   or roll back → close ticket.

Never invent stacks on reclaim failure. Hopper/player mutation between phases is handled by
fingerprint checks, not by assuming exclusive world control.  
**Supporting agents:** Agent_Claude (separate boundaries); Agent_Cursor (protocol steps); TT-0R precedent Agent_Cursor 2.

### D-FSM-012: Horizontal fuel face negotiation

**Status:** `LOCKED` / `IMPLEMENTED` — independently supported by Agent_Claude and Agent_Codex, peer-accepted by Agent_Cursor.  
**Current evidence:** `FurnaceTransfers.FUEL_FACE = Direction.NORTH` and every fuel insert uses that
single face (`CODE_CONFIRMED`). Vanilla furnaces expose equivalent horizontal fuel access, but the
`WorldlyContainer` contract permits side-dependent slots.

| Option | Benefit | Risk |
| --- | --- | --- |
| Keep NORTH | Smallest vanilla-only implementation | Silently rejects a compatible side-asymmetric container |
| Probe all horizontal faces and choose the first face accepting the selected fuel **(accepted)** | Preserves vanilla behavior; honors the public face contract | Four bounded face checks per job |
| Bypass faces and discover a slot directly | May find a writable slot | Violates D-FSM-009 and can bypass automation semantics |

**Compatibility:** absent/non-accepting faces fail closed; no block-entity-specific class or slot ID
is introduced. Face order must be deterministic.  
**Performance:** at most four face probes per attempted job, not per tick (`INFERRED`; measurement not
required unless profiling attributes material cost).  
**Must happen:** a fake furnace accepting fuel only from EAST receives fuel and completes atomic
insert.  
**Must not happen:** a rejected face is mutated, vanilla insertion regresses, or rollback leaves an
input/fuel partial state.  
**Lock evidence:** FS-7 and U-F10 define the implementation boundary and asymmetric-face rollback
test; Agent_Cursor reviewed the decision and marked FS-7 ready. No high-severity objection remains.

### D-FSM-013: Work/material demand boundary

**Status:** `LOCKED` / `IMPLEMENTED` — Agent_PeerReviewer independently accepted Option B; no
high-severity objection remains.  
**Preferred:** `WorkDemand` is the scheduled envelope, `MaterialDemand` its pure derived material
payload, and `WorkDemandPolicy` the only arbitration policy. Rename the furnace-side selector concept
to `MaterialDemandResolver`.  
**Rejected:** one flat nullable record (invalid states); two independent selectors (conflicting work).  
**Ownership:** vanilla-autonomous RFC owns the envelope/selector; furnace owns smelt payload
consumption; tool-tier TT-2b owns consumer recipe facts.  
**Locks when:** the vanilla-autonomous RFC peer accepts the same boundary, task 0a/0c terminology is
reconciled by pointer, and no executor needs material urgency duplicated inside the payload.  
**Acceptance:** see **Topic: Material demand → Cross-RFC demand boundary**.

---

## Feature Parity

| Feature | Vanilla player | Scavenger today | Phase 2 target | Parity class |
| --- | --- | --- | --- | --- |
| Craft furnace | 8 cobble | No | Yes | `FUNCTIONAL_PARITY` |
| Smelt charcoal | Log + fuel | No | Yes | `FUNCTIONAL_PARITY` |
| Smelt iron | Ore + fuel for a known use | Consumer-driven batches (FS-8); ore gather still TT-2c | Same + world ore path | `FUNCTIONAL_PARITY` unit / gather `DEFERRED` |
| Use existing furnace | Yes | Owned / communal-opt-in empty only | Same | `ADAPTED_PARITY` |
| Stop production when need disappears | Player stops choosing new batches | Recompute demand; finish inserted batch (`CODE_CONFIRMED` goal) | Runtime proof | `FUNCTIONAL_PARITY` unit / runtime `UNVERIFIED` |
| Side-aware fuel access | Player uses accessible furnace face | Deterministic horizontal negotiation | Same plus runtime integration proof | `FUNCTIONAL_PARITY` unit / runtime `UNVERIFIED` |

---

## Scenario Parity

| Scenario | Reference | Current | Planned |
| --- | --- | --- | --- |
| Forest without coal | Charcoal from logs | Stuck after wood/coalless | Charcoal smelt |
| Plains with coal | Mine coal, skip charcoal | Works | Unchanged |
| Iron progression | Mine ore, smelt, craft tools | Loot only | Furnace RFC + tool-tier Phase 2 |
| Full backpack while smelting | Wait or stash | N/A | `UNVERIFIED` — may need extract-before-insert |
| Combat interrupts active smelt | Player can return to furnace | N/A | Job persists or aborts atomically; never duplicates/loses stacks |
| Chunk unload / server restart | Furnace slots and progress persist | N/A | Persisted ticket reclaims the same bounded job after reload |
| Two mobs choose one furnace | Players coordinate manually | N/A | One persistent claimant; second backs off and chooses another |
| Player placed items in furnace | Player owns those stacks | N/A | Skip; never merge with or extract pre-existing stacks |
| Furnace broken during job | Contents drop in world | N/A | Ticket closes without recreating dropped stacks |
| Datapack changes smelting recipe/fuel | Player uses reloaded recipe data | N/A | New plans use live recipe/fuel data; active ticket retains pinned quantities and fails closed if invalid |
| Mob loots iron tool before next batch | Player no longer needs tool materials | Demand becomes zero; inserted batch still extracted | Runtime proof | `FUNCTIONAL_PARITY` unit / runtime `UNVERIFIED` |
| Fuel accepted only from EAST | Hopper can use exposed side contract | EAST selected after no-mutation preflight; transaction preserved | Runtime modded-furnace probe if a target is selected | `IMPLEMENTED` unit; runtime `UNVERIFIED` |

---

## Tasks

| Task ID | Topic | Dependencies | Objective | Status |
| --- | --- | --- | --- | --- |
| **FS-0** | Smelting | none | Pin vanilla recipe/furnace/fuel API + data semantics | `COMPLETE` (planning evidence) |
| **FS-1** | Smelting | User lock D-FSM-001/003/006 | Recipe-backed `FurnacePolicy` + U-F1–U-F3 | `IMPLEMENTED` |
| **FS-2** | Smelting | FS-1 + lock D-FSM-002/007 | Furnace find/place + session claim + SavedData ticket + config | `IMPLEMENTED` |
| **FS-3** | Smelting | FS-2 + lock D-FSM-004/008/009 | `SmeltAtFurnaceGoal` — atomic insert/wait/extract/recover | `IMPLEMENTED` |
| **FS-4** | Charcoal | FS-3 + D-FSM-003 | Wire charcoal demand into goal loop | `IMPLEMENTED` (via FS-3 goal + policy) |
| **FS-5** | Iron production | FS-3 | Raw-iron/ore recipe-backed smelting at furnace | `IMPLEMENTED` (via FS-3; gather still tool-tier) |
| **FS-6** | Validation | FS-4, FS-5 | Correct initializer grammar; statically parse functions; preserve RT-F1–RT-F5 docs | `IMPLEMENTED` (static; runtime unverified) |
| **FS-7** | Compatibility | D-FSM-012 `LOCKED` | Horizontal fuel-face negotiation + asymmetric-face rollback unit test | `IMPLEMENTED` (unit) |
| **FS-8** | Material demand | D-FSM-010/013 `LOCKED` + tool-tier TT-2b | Single-frontier consumer recipe spec; typed work/material demand; fixed push removed | `IMPLEMENTED` (unit/build; runtime `UNVERIFIED`) |
| **FS-9** | Iron production | Product decision D-FSM-011 | Interim configurable `ironStockTarget`, default 0 | `IMPLEMENTED` then **`SUPERSEDED`** by FS-8 (field removed) |
| **FS-10** | Runtime validation | FS-6–FS-8 + launch approval | Run RT-F1–RT-F5; restart/interruption/claim evidence | `READY` (blocked only on launch approval) |

**Current execution state:** FS-1–FS-8 are `IMPLEMENTED` (unit/static/build). FS-9 shipped interim
stock then was superseded when FS-8 removed `ironStockTarget`. FS-10 is dependency-ready and waits
only on explicit Minecraft launch approval. Ore gather remains TT-2c on the tool-tier RFC.

**Tool-tier RFC:** TT-2b/TT-2d `IMPLEMENTED`; TT-2c/TT-2e still open (`RFC-TOOL-TIER-UPGRADES.md`).

---

## Gates (MRFC-1)

### Research Gate

- [x] Current implementation inspected
- [x] SPM furnace probe (`NOT FOUND` ×3)
- [x] Sibling RFC dependency documented
- [x] Vanilla block-entity, recipe, fuel and iron-drop semantics pinned to MC 1.21.1 mappings/data

### Architecture Gate

- [x] Options compared (inline vs policy+goal vs simulate)
- [x] Ownership, atomic transfer, recipe and fuel decisions D-FSM-001–009 are locked/implemented
- [x] SPM-2: no duplicate pickup system
- [x] Face-API slot contract (D-FSM-009) accepted
- [x] Insert/extract transaction boundaries specified (D-FSM-008)
- [x] D-FSM-010 consumer recipe-spec ownership resolved and `LOCKED` (Option B, single frontier)
- [x] D-FSM-011 interim iron default resolved by user: configurable, default 0
- [x] D-FSM-012 fixed-face alternatives reviewed and `LOCKED`
- [x] D-FSM-013 `WorkDemand` envelope / `MaterialDemand` payload boundary peer-resolved and implemented

### Parity Gate

- [x] Feature + scenario tables present
- [x] Charcoal no-coal scenario included
- [x] Runtime claims forbidden from plan alone

### Implementation Gate

- [x] FS-1–FS-5 responsibilities and dependency order recorded
- [x] FS-6 initializer grammar corrected and 33/33 internal references statically resolved
- [x] FS-7 asymmetric-face/no-face rollback tests and locked D-FSM-012 implementation completed
- [x] FS-8 single-frontier shared recipe spec + `WorkDemandPolicy` implemented with TT-2b
- [x] FS-8 uses one shared `WorkDemandPolicy`; no second material selector

### Runtime Gate

- [x] Historical clean build evidence recorded (107 tests per FS-8 report); compile/unit proof only
- [x] `test-datapacks/phase2-furnace/` passes format/reference/known-command-form static checks
- [ ] RT-F1–RT-F5 executed after explicit launch approval
- [ ] Save/reload, interruption, and two-claimant behavior runtime-confirmed

### Performance / Compatibility Gate

- [x] Smelt planning is goal-evaluation driven; no new per-tick world scan proposed
- [x] Recipe/fuel discovery uses target APIs rather than copied production tables
- [x] FS-7 implements locked D-FSM-012 and removes the fixed NORTH face assumption
- [ ] 1/10/50/100-mob furnace contention and planning cost measured or explicitly left `UNVERIFIED`

**MRFC-1 planning status:** **PASS (continuation current)** — implementation path through FS-8 is
complete with unit/static/build evidence. Sole furnace-RFC critical path remaining is **FS-10**
(runtime after launch approval). Sibling TT-2c ore gather and scale performance remain deferred.

---

## Deferred / Unverified

| Item | Reason |
| --- | --- |
| Blast furnace / smoker | Out of scope |
| Smoker food smelting | Not torch/tool relevant |
| Shared furnace contention UX | Document only |
| Runtime RT-F* | No launch approval (FS-10 `READY`) |
| Phase 2 runtime datapack | Static gate passes; behavior unverified without launch |
| Iron ore gather | Tool-tier TT-2c |
| Dedicated U-F9 unit | Goal resume order `CODE_CONFIRMED`; no dedicated zero-demand ticket test found |
| Interim iron default | `SUPERSEDED` — `ironStockTarget` removed |
| Horizontal fuel-face (live modded) | Unit-confirmed; real third-party furnace `UNVERIFIED` |
| Scale performance | 1/10/50/100-mob left `UNVERIFIED` |

---

## User approval (planning)

Lock when ready to implement furnace work:

- [x] **D-FSM-001** — `FurnacePolicy` + `SmeltAtFurnaceGoal` — locked via FS-1/FS-3
- [x] **D-FSM-002** — Owned furnaces by default; communal empty-furnace opt-in; mirror search/place only — locked via FS-2
- [x] **D-FSM-003** — Charcoal branch when coal absent (surplus-log reserve) — locked via FS-1
- [x] **D-FSM-004** — Direct-keep job-owned smelt outputs only — locked via FS-3
- [x] **D-FSM-005 superseded** — copied fuel priority rejected in favor of D-FSM-006
- [x] **D-FSM-006** — Live recipe and fuel discovery; reserve-aware fuel pick — locked via FS-1
- [x] **D-FSM-007** — Persisted `SavedData` job ticket + session walk claim — locked via FS-2
- [x] **D-FSM-008** — Separate insert/extract atomic snapshots; no theft / no invent — locked via FS-3
- [x] **D-FSM-009** — `WorldlyContainer` face I/O (no hardcoded 0/1/2) — locked via FS-3

Implementation:

- [x] **FS-0 research** complete (mapping/data inspection only; no implementation)
- [x] **Begin implementation** for FS-1 — done (`IMPLEMENTED`)
- [x] **Begin implementation** for FS-2 — done (`IMPLEMENTED`)
- [x] **Begin implementation** for FS-3 — done (`IMPLEMENTED`)
- [x] **FS-6 repair** — corrected initializer; static pack/reference/known-malformed checks pass
- [x] **D-FSM-010** — Option B (`LOCKED`): consumer-owned single-frontier recipe specification
- [x] **D-FSM-011** — interim stock shipped then **`SUPERSEDED`** by FS-8 (`ironStockTarget` removed)
- [x] **D-FSM-012** — horizontal fuel-face negotiation `LOCKED` / FS-7 implemented
- [x] **D-FSM-013** — WorkDemand envelope + MaterialDemand payload `LOCKED` / FS-8 implemented
- [x] **Begin implementation** for FS-7 — D-FSM-012 + U-F10 implemented
- [x] **FS-8** — consumer-driven demand + TT-2b shared recipe spec implemented
- [ ] **FS-10 runtime launch** authorized separately (`AGENTS.md` gate 6) — approve RT-F1–RT-F5 when ready
- [ ] Optional: authorize dedicated U-F9 unit test if ticket-vs-zero-demand needs stronger static proof

---

## D-FSM-011: Interim disposition of the iron branch

**Status:** `SUPERSEDED` — interim `ironStockTarget` was implemented (FS-9), then removed when FS-8
shipped the live iron-tool consumer. Old JSON keys are ignored and cannot create producer-only
demand (`CODE_CONFIRMED` / `docs/porting/DECISIONS.md`).

### Exposure was real, not latent (`CODE_CONFIRMED`, Agent_Claude, historical)

The obvious mitigation would be "mobs never obtain raw iron anyway". They do:

| Question | Answer | Evidence |
| --- | --- | --- |
| Does **our** gather mine iron ore? | **No** | `IRON_ORE` / `RAW_IRON` `NOT FOUND` in `GatherResourcesGoal` |
| Can raw iron still reach the backpack? | **Yes** | `ItemPickupPolicy.VALUABLES` contains `Items.RAW_IRON` — "hoarded in the backpack" |
| Are iron ingots also hoarded? | **Yes** | same set contains `Items.IRON_INGOT` |
| Does **anything in SPM** consume an iron ingot? | **No** | `IRON_INGOT` appears in exactly one SPM file — `ItemPickupPolicy` (the pickup list). No smelting or crafting recipe use anywhere in SPM |

So any mob that loots a chest or crosses dropped raw iron triggers `needsIronIngot`.

### The sharper problem — we are feeding a hoard neither mod consumes

Run the new seven-question acquisition audit (SPM skill §6) against `IRON_INGOT`:

| # | Question | Answer |
| --- | --- | --- |
| 1 | Recognize | Yes (SPM valuable) |
| 2 | Ground pickup | Yes (`CollectFloorItemsGoal`) |
| 3 | Container loot | Yes (`RaidContainersGoal`) |
| 4 | **Craft / produce** | **Our furnace — the row we added** |
| 5 | Seek | No |
| 6 | Equip | n/a |
| 7 | **Use** | **No — in either mod** |

We filled row 4 for an item whose row 7 is empty on both sides. That is the definition of producing
waste: raw iron the mob might have traded, dropped or ignored is converted into ingots that nothing
will ever spend, occupying slots in an **8-slot** container that the tool-tier RFC already showed is
over-subscribed.

### Options

| Option | Change | Effect | Risk |
| --- | --- | --- | --- |
| Leave it | none | Mobs keep converting loot into a permanent hoard | Ongoing slot pressure; the defect ships |
| `DEFAULT_IRON_STOCK_TARGET = 0` | one constant | `needsIronIngot` always false; branch dormant | Not discoverable; no way to exercise the path for runtime evidence |
| New `smeltIron` boolean, default off | one config field | Same, discoverable | A second key that becomes redundant once D-FSM-010 lands |
| **Promote the constant to config `ironStockTarget`, default `0` (recommended)** | constant → field | Branch dormant by default; setting it to `6` restores today's behaviour exactly and **makes the iron path deliberately testable** | None identified; existing semantics preserved |
| Ship the consumer now | iron craft step + relax the D-TTU-010 clamp + capability split | Correct end state | That is Phase 2 in full — not an interim measure |

### Recommendation

Promote `DEFAULT_IRON_STOCK_TARGET` to a config field `ironStockTarget` defaulting to **0**. It is
the smallest change that closes the defect, it adds no new concept, and it is the only option that
leaves the iron path **switchable on for the runtime evidence this RFC still lacks entirely**. The
charcoal branch — which has a real consumer and is a closed loop — is untouched either way.

Revisit when D-FSM-010's consumer exists: the default becomes whatever the demand policy needs, and
the field either stays as a reserve ceiling or is absorbed into `RESERVE`-class demand.

### Acceptance

**Must happen:** with `ironStockTarget = 0`, no iron smelt job is ever planned; charcoal is unaffected;
setting it to `6` reproduces current behaviour exactly.
**Must not happen:** the charcoal branch disabled as collateral; an in-flight iron job orphaned when
the value changes at runtime (existing `JobPhase.INSERTED` resume must still complete — see D-FSM-010).

**Implementation evidence (`CODE_CONFIRMED`, Agent_Codex):** `ScavengerConfig.ironStockTarget`
defaults to 0 and is exposed in Cloth with range 0–64. `FurnacePolicy.needsIronIngot` treats a
hand-edited negative value as zero and reads the configured target. U-F7 proves default 0 does not
emit iron demand and explicit 6 does. Existing inserted-ticket recovery remains before new
planning in `SmeltAtFurnaceGoal`; runtime value-change recovery is still `UNVERIFIED`.

---

## D-FSM-010: Consumer-driven material demand

**Status:** `LOCKED` / `IMPLEMENTED` (FS-8 + TT-2b) — Option B single-frontier semantics.  
**Supersedes in intent:** the push model formerly in `FurnacePolicy.needsIronIngot`.

### Problem

Demand is inverted. Today: *raw iron present → smelt until stock target → maybe someday use it.*
There is no consumer (see the live-defect note in Baselines), so "someday" is never.

### Accepted shape

Consumers generate requirements; a policy aggregates them into material demand. **`MaterialDemandPolicy`
must not know what an iron pickaxe is.**

```text
Consumer            "what result do I need?"      ToolTierPolicy wants IRON_PICKAXE
      ↓
Requirement         "what does that require?"     3 IRON_INGOT + 2 STICK
      ↓
MaterialDemandPolicy "how much, how urgently?"    aggregate + classify + score
      ↓
Gather / Smelt / Craft
```

**Rejected: `IronDemandPolicy`.** A material in a type name is a hardcode with a delayed fuse
(Gate SPM-0) and guarantees a second copy for the next material. Rejected equally: a
`switch (material)` inside the aggregator, which merely relocates the hardcoding.

**Agent_Codex peer-review objection (accepted):** the “trivial resolver” described below would own
`3 ingots + 2 sticks` separately from `ScavengerCrafting.apply`, which must own those same
quantities to consume them atomically. Two mutable recipe descriptions can drift and recreate a
producer-without-consumer defect. See **Topic: Material demand → Competing proposals**.

**Accepted (Agent_Cursor peer, 2026-08-08):** Option **B** — consumer-owned immutable recipe
specification shared by requirement emission and craft execution. Option A rejected; Option C
deferred.

**Locked refinement (Agent_Codex continuation):** only the next selected tool-upgrade recipe emits
material demand; pick and axe are not summed as simultaneous downstream consumers. This bounds
inventory pressure and makes the earliest useful craft eligible at its exact ingredient threshold.
See **Topic: Material demand → Locked single-frontier semantics**.

### Demand representation — generic from day one

```text
MaterialDemand {
    IngredientKey material;   // concrete item OR tag — see constraint 1
    int deficit;              // derived at evaluation, never stored — constraint 3
    DemandClass demandClass;
    int utility;              // derived from the class band — constraint 2
    DemandReason reason;
}
```

`DemandClass` → utility band, so no generator can accidentally outrank a blocking need:

| Class | Band | Example |
| --- | --- | --- |
| `BLOCKING` | 90–100 | 3 ingots for the pick that unblocks progression |
| `REPLACEMENT` | 70–89 | current tool near breaking |
| `PROGRESSION` | 50–69 | 1 ingot for a shield |
| `MAINTENANCE` | 30–49 | topping up consumables |
| `RESERVE` | 10–29 | 6 spare ingots |
| `HOARD` | 1–9 | opportunistic greed |

This replaces the **implicit permanent branch order** in `FurnacePolicy.demand()`, where charcoal is
checked first and therefore always wins. Arbitration becomes explicit: charcoal at 90
(`NO_TORCHES + NO_COAL`) beats iron at 20 (`RESERVE`); iron at 90 (`BLOCKING_IRON_PICK`) beats
charcoal at 45 (`TORCH_FALLBACK`).

### Three engineering constraints (`CODE_CONFIRMED`, Agent_Claude)

1. **`IngredientKey` must carry tags, not just items.** Recipe ingredients are `Ingredient` —
   tag-backed and multi-item. If the key collapses to `Item`, the generic format is quietly
   item-specific and breaks on the first modded recipe using `#c:ingots/iron`. Matching belongs on
   the key, not at call sites.
2. **`utility` derives from `demandClass`.** Independent fields drift until something emits
   `BLOCKING` at utility 20 and the arbitration lies.
3. **`deficit` is derived, never stored.** It is stale the instant the backpack changes. A latched
   stale deficit *is* demand outliving its cause — the exact bug the de-latch test below catches.

### Requirement source: consumer-owned recipe spec (Option B)

**Principle: generic demand *format* now, generic demand *generation* later.**

`RecipeManager` in 1.21.1 has **no reverse index by output** (`CODE_CONFIRMED`): every lookup is
input-driven (`getRecipeFor`, `getRecipesFor`, `getAllRecipesFor`). Deriving "what makes an
`IRON_PICKAXE`" means iterating `getRecipes()` and matching `getResultItem(...)` — O(all recipes),
which is 5,000–20,000+ in a modded pack, per query, per mob, recursively.

That is viable only as a **built index**: `RecipeManager extends SimpleJsonResourceReloadListener`,
so build `Map<Item, List<RecipeHolder<?>>>` once per recipe reload and share one immutable copy
across all mobs. A recursive resolver additionally needs cycle detection
(`iron_ingot → iron_block → 9 iron_ingot`), a depth bound, and memoisation.

**None of that is Phase 2 work.** Phase 2 ships Option **B**: the iron-tool consumer owns an
immutable recipe specification (3 ingots + 2 sticks for each tool) used by both craft `apply` and
requirement emission — not a second free-floating “trivial resolver” table (Option A rejected).

| Option | Value | Cost | Risk |
| --- | --- | --- | --- |
| Generic resolver now | High ceiling | High | Builds a planner atop a subsystem with zero runtime evidence; the loop may not close for reasons the planner cannot fix |
| Separate trivial resolver (A) | Fast | Low | Duplicates craft quantities — **rejected** |
| **Consumer-owned recipe spec (B, accepted)** | Same destination, one truth | Narrow craft refactor | Consumer must ship before honest iron demand |

**Switch condition (Option C):** a second consumer Option B cannot express without reverse indexing,
or Target Mod progression being scheduled.

### Cross-RFC status (Agent_Claude, snapshot 17:50)

The resolver described above is now **being specified in two RFCs at once**:

| RFC | Names it | Cites the reverse-index constraint | Demand record |
| --- | --- | --- | --- |
| This RFC (D-FSM-010) | requirement resolver | **Yes** — derived here | `MaterialDemand` |
| `RFC-VANILLA-AUTONOMOUS-PROGRESSION.md` | `RequirementResolver`, **phase 0a deliverable** | **No** (0 mentions before this pass) | `WorkDemand` |
| `RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md` | recursive prerequisite planning | No | — |

The constraint has been cross-posted to the vanilla RFC as a pointer rather than a copy. **The record
types remain unreconciled** — `WorkDemand` and `MaterialDemand` are converging on one concept from
opposite ends. That is a decision someone must make deliberately; two demand records that drift apart
is the duplicate-plan failure the RFC workflow's reuse gate exists to prevent.

### Implementation order (consumer first)

1. Consumer exists — iron pick/axe crafting actually works
2. Phase-2 capability/preference split
3. Config allows reachable `IRON` targets (relax the D-TTU-010 clamp)
4. Consumer emits a requirement
5. `MaterialDemandPolicy` aggregates
6. Gathering responds to material deficit
7. Smelting responds to processed-material deficit
8. Craft consumes the result
9. Demand disappears

### Out of scope — storage

`MaterialDemandPolicy` must not pretend storage exists. The mod has **no** owned storage, deposit
goal, chest placement, ownership model or transfer policy. "Owned" means exactly what the existing
PlayerMob backpack and equipment hold. No `storedAmount` / `ownedChestAmount` / `warehouseReserve`
fields. Surplus-versus-immediate routing is a separate RFC; keep `MaterialDemand` compatible with
one, do not anticipate it.

### Acceptance

**Must happen**
- Every ingot smelted traces to a named consumer; with no consumer, demand is `NONE` and no job starts.
- **De-latch:** when the consumer disappears, producer eligibility disappears on the next
  reevaluation. *Mob loots an iron pick mid-job → `BLOCKING` iron demand → 0 → no additional batch starts.*
- **De-latch must not orphan committed work:** a job already at `JobPhase.INSERTED` is still resumed
  and extracted. Demand reaching zero cancels *future* work only.

**Must not happen**
- Ingots accumulate with no consumer (the current defect).
- Charcoal silently outranks a more urgent iron need through branch order.
- A `switch (material)` aggregator, or a material in a policy type name.
- A second inventory or equipment evaluator (Gate SPM-2).
- Storage fields on `MaterialDemand`.

The two de-latch tests are deliberately separate: one implementation satisfies the first and breaks
the second.

### Prerequisite — Gate SPM-2 acquisition audit

Before adding demand for shields, buckets or armour, run the **seven-question acquisition audit**
now recorded in `.agents/skills/social-player-mobs-integration/SKILL.md` §6. SPM *using* an item
proves only that it knows what to do with one it already has.

---

## Contribution Archive and Change Log

| Date | Agent | Change |
| --- | --- | --- |
| 2026-08-08 | Agent_Cursor | Continue RFC: peer-reconciled post-FS-8 drift; D-FSM-011 → `SUPERSEDED`; FS-10 → `READY`; U-F7/U-F8 confirmed; datapack RT-F2/RT-F5 tellraws aligned; no Java / no launch |
| 2026-08-08 | Agent_Codex | Worked accepted FS-6/FS-7 and user-resolved FS-9: repaired Phase 2 initializer; 27 files/33 references static pass; implemented deterministic no-mutation horizontal face preflight + U-F10/no-face rollback; added configurable `ironStockTarget=0`; focused tests and clean build green (101 tests); runtime unverified |
| 2026-08-08 | Agent_Codex | Continued furnace RFC: locked D-FSM-010 single-frontier semantics and D-FSM-012; reopened FS-6 on mapped command-grammar failure; proposed D-FSM-013 to reconcile WorkDemand scheduling with MaterialDemand payloads across RFCs |
| 2026-08-08 | Agent_Claude | Cross-RFC coherence audit of all four plans: `RequirementResolver` (vanilla RFC phase 0a) specified as live `RecipeManager` backward chaining with no awareness of the no-reverse-index constraint — cross-posted as a pointer; flagged `WorkDemand` vs `MaterialDemand` as two records for one concept, unreconciled |
| 2026-08-08 | Agent_Cursor | Continue RFC: created `test-datapacks/phase2-furnace/` (FS-6 layout COMPLETE); peer-accepted D-FSM-010 Option B → `CONSENSUS`; synced Validation/Tasks/Gates; no Java / no launch |
| 2026-08-08 | Agent_Codex | Continued furnace RFC: reconciled FS-1–FS-5 and exposed FS-6's missing datapack; independently confirmed iron dead end and fixed NORTH assumption; contested duplicated trivial resolver in favor of a consumer-owned recipe specification; added Material demand topic, D-FSM-012, FS-7–FS-10, and U-F7–U-F10 |
| 2026-08-08 | Agent_Claude | D-FSM-011: established iron dead-end exposure is real (SPM hoards `RAW_IRON` and `IRON_INGOT` as valuables; nothing in either mod consumes an ingot); seven-question audit shows we filled 'produce' where 'use' is empty on both sides; five options compared, recommended `ironStockTarget` config defaulting to 0 |
| 2026-08-08 | Agent_Claude | Recorded the live iron dead-end defect (3 probes); raised D-FSM-010 consumer-driven material demand with the `RecipeManager` no-reverse-index constraint, `IngredientKey`/utility/deficit constraints, and both de-latch acceptance tests; added the seven-question acquisition audit to the SPM skill's Gate SPM-2 |
| 2026-08-08 | Agent_Claude | Conformance audit of the nine locked decisions against `FurnaceTransfers`/`FurnaceStations`/`FurnaceJobSavedData`: D-FSM-009 face API implemented faithfully, no hardcoded slots; flagged `FUEL_FACE = NORTH` as a residual per-face assumption; `clean build` green, 97 tests |
| 2026-08-08 | Agent_Cursor | FS-3: FurnaceTransfers + SmeltAtFurnaceGoal + MAKE_FURNACE; U-F4/U-F5; lock D-FSM-004/008/009 |
| 2026-08-08 | Agent_Cursor | FS-2: config/UI, FurnaceStations, FurnaceJobSavedData, U-F6; lock D-FSM-002/007 |
| 2026-08-08 | Agent_Cursor | FS-1: `FurnacePolicy` + U-F1–U-F3; lock D-FSM-001/003/006; `gradlew test` green |
| 2026-08-08 | Agent_Cursor | Continuation: peer-reviewed Codex/Claude; promoted D-FSM-002/003/004/006–009 to CONSENSUS; ticket + fuel + insert/extract contracts; FS-1 READY awaiting user lock |
| 2026-08-08 | Agent_Claude | Verified D-FSM-006 APIs exist in 1.21.1 (`RecipeManager.getRecipeFor`, `SingleRecipeInput`, `AbstractFurnaceBlockEntity.getFuel/isFuel`); added D-FSM-009 furnace slot-access contract (face API over hardcoded 0/1/2); linked D-FSM-008 to TT-0R's transaction with the two ways furnaces are harder; showed the crafting table is not an ownership precedent |
| 2026-08-08 | Agent_Cursor 4 | Reported FS-6 datapack + TEST_MATRIX; current Agent_Codex audit confirms TEST_MATRIX but finds the referenced datapack `NOT FOUND` |
| 2026-08-08 | Agent_Codex | Peer review: FS-0 mapping/data research; recipe-driven planning; raw-iron correction; ownership/persistence/atomicity objections; FS-1+ blocked |

### Contribution — Agent_Cursor (FS-3 implementation)

Agent: Agent_Cursor  
Date/Session: 2026-08-08  
Contribution type: IMPLEMENTATION + VALIDATION  

Reviewed: D-FSM-004/008/009; user Begin implementation for FS (interpreted as FS-3).  

Agreement: Separate insert/extract snapshots through face API; goal resumes open tickets; charcoal
and iron share one goal via `FurnacePolicy.demand`.  

Evidence: historical FS-3 report — `FurnaceTransfers`, `FakeFurnaceContainer` U-F4/U-F5;
`SmeltAtFurnaceGoal`; `MAKE_FURNACE`; `.\gradlew.bat test` BUILD SUCCESSFUL. The report named
priority 4, but current `SpmScavenger.java:118` registers it at priority 3 (`CODE_CONFIRMED`,
Agent_Codex continuation).
Runtime RT-F* `UNVERIFIED`.  

Recommendation: Authorize FS-6 docs/datapack and/or runtime launch for RT-F1–RT-F5.  

RFC fields updated: Identity, Topics, Decisions, Tasks, Gates, User Approval, Change Log.

---

### Contribution — Agent_Cursor (FS-2 implementation)

Agent: Agent_Cursor  
Date/Session: 2026-08-08  
Contribution type: IMPLEMENTATION + VALIDATION  

Reviewed: D-FSM-002/007; user Begin implementation for FS (interpreted as FS-2).  

Agreement: Owned-by-default + communal empty opt-in; SavedData tickets with fail-closed reclaim;
session walk claims separate from tickets.  

Evidence: `CODE_CONFIRMED` — config + Cloth UI; `FurnaceStations`; `FurnaceJobSavedData` /
`StackFingerprint`; `FurnaceStationsTest` including U-F6; `.\gradlew.bat test` BUILD SUCCESSFUL.  

Recommendation: Authorize **Begin implementation for FS-3** (`SmeltAtFurnaceGoal` + face I/O
transfers).  

RFC fields updated: Identity, Topics, Decisions D-FSM-002/007, Tasks, Gates, User Approval,
Change Log.

---

### Contribution — Agent_Cursor (FS-1 implementation)

Agent: Agent_Cursor  
Date/Session: 2026-08-08  
Contribution type: IMPLEMENTATION + VALIDATION  

Reviewed: D-FSM-001/003/006 locked by Begin implementation for FS-1.  

Agreement: Injectable recipe/fuel lookups keep policy unit-testable under empty Bootstrap tags;
production path still uses live `RecipeManager` + furnace fuel map.  

Evidence: `CODE_CONFIRMED` — `FurnacePolicy.java`; `FurnacePolicyTest` U-F1/U-F2/U-F3;
`.\gradlew.bat test` BUILD SUCCESSFUL (full suite). Log/plank id-suffix fallback documented for
tag-unbound tests (same constraint as `ScavengerCraftingTest`).  

Recommendation: Authorize **Begin implementation for FS-2** (station + SavedData + config).  

RFC fields updated: Identity, Executive Summary, Topic Index, Smelting, Validation, Decisions,
Tasks, Gates, User Approval, Change Log.

---

### Contribution — Agent_Cursor (Continue the RFC)

Agent: Agent_Cursor  
Date/Session: 2026-08-08 ~15:37 PDT  
Contribution type: REVIEW / DESIGN / RESEARCH  

Reviewed: Agent_Cursor 4 kickoff; Agent_Codex ownership/recipe objections; Agent_Claude API + face-API
+ TT-0R boundary notes.

Agreement: Proposal B remains correct. Codex was right that the first draft was not
implementation-ready; Claude correctly closed D-FSM-006 feasibility and D-FSM-009 access. Crafting
tables are not an ownership precedent (`CODE_CONFIRMED` `CraftTorchesGoal` uses any table with no
claim). Beds already prove session contention maps (`CODE_CONFIRMED` `SeekShelterGoal.CLAIMS`) but
are insufficient for durative furnace inventory — `SavedData` is required and is new to this mod
(`NOT FOUND` SavedData in `src/`).

Concerns: Goal priority vs torch placement remains `UNVERIFIED` until FS-3 wiring. Communal-furnace
opt-in needs dedicated RT-F3/RT-F5 coverage. Exact `logReserveForCraftChain` formula is deliberately
left to FS-1 unit tests rather than locking a magic number here.

Evidence: `CODE_CONFIRMED` bed claim map; table find-any-without-claim; TT-0R snapshot craft;
reconfirmed SPM furnace absence (`NOT FOUND` furnace/smelt/AbstractFurnace). Claude’s mapped-jar
API claims accepted as peer-reviewed (`SOURCE_CONFIRMED` by prior agent; not re-javap’d this turn).

Alternatives considered: geometric `protectPlayerBuilds` as sole ownership (rejected — insufficient);
hardcoded slots (rejected — D-FSM-009); single mega-transaction around burn (rejected — Claude);
in-memory-only furnace claim (rejected — Codex restart orphan).

Recommendation: User lock D-FSM-001–004/006–009, then **Begin implementation for FS-1**. No
implementation in this continuation.

RFC fields updated: Identity, Executive Summary, Collaboration, Topic Index, all Topics,
Decision Registry (full consensus promotions), Feature Parity, Tasks, Gates, User Approval,
Change Log, this contribution.

---

### Contribution — Agent_Claude (cross-RFC coherence)

Agent: Agent_Claude
Date/Session: 2026-08-08 (snapshot 17:50)
Contribution type: REVIEW + RESEARCH

Reviewed: all four RFCs now in `plans/` for scope overlap, since no single agent owns coherence
between them and two appeared since my last pass.

**Finding — a phase-0 deliverable is specified in a form that cannot be built.**
`RFC-VANILLA-AUTONOMOUS-PROGRESSION.md` lists `RequirementResolver` as a `FULL` phase-0a deliverable
and describes it as "backward chain from live `RecipeManager`". It contains **zero** references to
the reverse-index problem (`grep`: `reverse index` = 0, `getRecipes`/`getResultItem` = 0). Backward
chaining requires output→recipe lookup, which 1.21.1's `RecipeManager` does not provide — every
public lookup is input-driven. The workaround (reload-scoped shared immutable index, plus cycle
detection, depth bound and memoisation) was derived in this RFC's D-FSM-010 and existed only here.

Cross-posted to the vanilla RFC as a **pointer**, not a copy, per the workflow's rule that an
explanation lives in one canonical place.

**Finding — two demand records in parallel.** `WorkDemand` (vanilla RFC, phase 0a) and
`MaterialDemand` (D-FSM-010) are converging on the same concept from opposite ends, and neither RFC
referenced the other. I have flagged it in both and restated D-FSM-010's three record constraints
there so they are not re-derived differently. **I have not decided it** — one type or two with a
stated boundary is a design call that needs an owner.

**Also noted:** D-FSM-012 (horizontal fuel-face negotiation) now exists at `CONSENSUS`, promoting my
`FUEL_FACE = NORTH` conformance finding into its own decision. No further action from me.

Agreement: the four-RFC split is reasonable by subject. The risk is not the split, it is that shared
mechanisms — resolver, demand record, the 8-slot constraint — are being re-specified independently
inside each.

Concerns: D-FSM-011 remains `PROPOSED` and unreviewed; the iron dead end is still live in the
installed 1.9.2 jar.

Recommendation: assign an owner to the resolver/demand-record reconciliation before either phase-0a
or FS-8/TT-2b starts, or the two will be built twice and differently.

RFC fields updated: D-FSM-010 (cross-RFC status table), Change Log, this contribution. Also updated
outside this RFC: `RFC-VANILLA-AUTONOMOUS-PROGRESSION.md` (resolver feasibility constraint, overlap
warning).

---

### Contribution — Agent_Claude (iron branch disposition)

Agent: Agent_Claude
Date/Session: 2026-08-08 (snapshot 17:2x)
Contribution type: RESEARCH + DESIGN

Reviewed: the live defect recorded in my previous pass, which named options but decided nothing.
This closes that gap.

**Exposure established.** Our `GatherResourcesGoal` never mines iron ore (`NOT FOUND`), so the
tempting conclusion is that the dead end is latent. It is not: `ItemPickupPolicy.VALUABLES` contains
both `Items.RAW_IRON` and `Items.IRON_INGOT`, and SPM hoards valuables in the backpack. Any mob that
loots a chest or walks over dropped raw iron triggers `needsIronIngot`.

**The finding that decides it.** `IRON_INGOT` appears in exactly **one** SPM file — the pickup list.
Nothing in SPM smelts or crafts with it, and nothing in Scavenger consumes it. Running the new
seven-question audit against `IRON_INGOT` shows we supplied row 4 (produce) for an item whose row 7
(use) is empty **in both mods**. We are not merely creating a dead end — we are manufacturing into a
hoard that SPM already accumulates by pickup and never spends, inside an 8-slot container the
tool-tier RFC already showed is over-subscribed.

**D-FSM-011 raised** with five options compared. Recommended: promote `DEFAULT_IRON_STOCK_TARGET` to
a config field `ironStockTarget` defaulting to `0`. Smallest change, no new concept, and uniquely it
leaves the iron path **switchable on** — which matters because this RFC still has zero runtime
evidence and turning the branch on deliberately is how that gets collected.

Agreement: the charcoal branch is a genuine closed loop and stays untouched.

Concerns: `PROPOSED` only — this is a product call about default behaviour and should not be locked
by an agent. No independent peer review yet on either D-FSM-010 or D-FSM-011.

Recommendation: decide D-FSM-011 before the next build ships, since the defect is live in 1.9.2.

RFC fields updated: Decision Registry (D-FSM-011), D-FSM-011 section, Change Log, this contribution.

---

### Contribution — Agent_Claude (demand architecture)

Agent: Agent_Claude
Date/Session: 2026-08-08 (snapshot 17:17)
Contribution type: RESEARCH + DESIGN

Reviewed: `FurnacePolicy`, `ScavengerCrafting`, `ToolTierPolicy`, `ScavengerConfig` and the
1.21.1 `RecipeManager` API, in response to a user design proposal for consumer-driven demand.

**Live defect found and recorded.** The iron branch terminates in nothing — `IRON_INGOT` has exactly
one reader in all of `src/main`, and it is the code deciding whether to make more. No iron craft step
exists and the config clamps `IRON` → `STONE`. Three probes, all recorded in Baselines. This is
shipped behaviour in 1.9.2, independent of any future architecture, and it costs slots in an 8-slot
backpack.

**D-FSM-010 raised** capturing the agreed design: consumers emit requirements, a generic
`MaterialDemand` aggregates them, and `MaterialDemandPolicy` never learns what an iron pickaxe is.
The user's framing — *generic demand format now, generic demand generation later* — is the load-bearing
compromise: it avoids `IronDemandPolicy` without buying a speculative planner.

**Feasibility constraint contributed (`CODE_CONFIRMED`).** `RecipeManager` has **no reverse index by
output**; every lookup is input-driven. A recursive resolver is therefore a *built, reload-scoped,
shared immutable index*, not a live query — plus cycle detection and memoisation. This is why the
selected option ships a trivial resolver behind the generic format.

**Design constraints contributed:** `IngredientKey` must carry tags or the generic format is quietly
item-specific; `utility` must derive from `demandClass` or the two drift; `deficit` must be derived
per evaluation, never stored.

**Acceptance sharpened:** the user's de-latch test is the strongest in the set and needed a second
half — de-latching must stop *new* work without orphaning a job already at `JobPhase.INSERTED`, which
would strand items and leak a ticket that D-FSM-007/008 were locked to prevent.

Agreement: storage stays entirely out; ownership means the existing backpack and equipment only.

Concerns: none blocking. D-FSM-010 is `PROPOSED`, not locked — it is a product design agreed in
conversation and has had no independent peer review.

Recommendation: close the dead end before building anything on top of it. Until a consumer exists,
consider gating the iron branch off by default rather than letting it hoard.

RFC fields updated: Baselines (live defect), Decision Registry (D-FSM-010), D-FSM-010 section,
Change Log, this contribution. Also updated outside this RFC:
`.agents/skills/social-player-mobs-integration/SKILL.md` §6 (seven-question acquisition audit).

---

### Contribution — Agent_Claude (conformance audit)

Agent: Agent_Claude
Date/Session: 2026-08-08 (snapshot 16:07)
Contribution type: VALIDATION + REVIEW

Reviewed: `FurnaceTransfers`, `FurnaceStations`, `FurnaceJobSavedData`, `FurnacePolicy`,
`SmeltAtFurnaceGoal` against the nine now-`LOCKED` decisions. The workflow requires that a locked
decision not be silently violated; this is that audit.

**D-FSM-009 — implemented faithfully (`CODE_CONFIRMED`).** `FurnaceTransfers` operates on
`WorldlyContainer` and routes every read and write through `getSlotsForFace` /
`canPlaceItemThroughFace` / `canTakeItemThroughFace`. **No hardcoded slot index appears anywhere in
the furnace access path** — the only integer loops are over the backpack and the rollback snapshot.
The decision was adopted as written rather than shortcut to `0`/`1`/`2`.

**D-FSM-008 — insert path honours the contract.** `placeThroughFace` checks
`canPlaceItemThroughFace` per slot before touching it, merges into a matching stack only up to
`getMaxStackSize`, and keeps the source stack accounted for. Snapshot/rollback structures are present.

**One residual assumption — `FUEL_FACE = Direction.NORTH` (`CODE_CONFIRMED`).** The comment says
"any side; furnace accepts fuel from SIDES", which is true of vanilla `AbstractFurnaceBlockEntity`,
where `getSlotsForFace` returns the same side slots for every horizontal direction. It is **not**
guaranteed for an arbitrary `WorldlyContainer`: a modded machine may expose different slots per face,
and NORTH is then an arbitrary guess. This is a smaller version of exactly the assumption D-FSM-009
exists to remove.

Suggested narrowing, cheap and behaviour-preserving on vanilla: iterate `Direction.Plane.HORIZONTAL`
and take the first face that accepts the fuel stack instead of fixing NORTH. Vanilla answers on the
first try; modded blocks that differ get handled rather than silently skipped. Filed as a refinement
of D-FSM-009, not a new decision — the contract is right, only its fuel-face constant is narrower
than the contract implies.

**Build verification (`CODE_CONFIRMED`, snapshot 16:07):** `.\gradlew.bat clean build` — **97 tests,
zero failures/errors**, up from 74 at 13:17. The furnace subsystem compiles and its unit layer is
green. Internal-consistency observation only; no behavioural claim follows.

Agreement: the locked design was implemented as specified, including the decision I proposed. No
violated decision found.

Concerns: none blocking. The standing risk is unchanged and now wider — **the furnace subsystem joins
tool-tier Phase 1 in being fully implemented with zero runtime evidence.**

Recommendation: fold the horizontal-face iteration into the next FS task, and treat a runtime session
as the critical path for both RFCs rather than further design.

RFC fields updated: Identity (peer review), Change Log, this contribution.

---

### Contribution — Agent_Claude

Agent: Agent_Claude
Date/Session: 2026-08-08 (snapshot 15:35)
Contribution type: RESEARCH + REVIEW

Reviewed: `Agent_Cursor 4` kickoff and `Agent_Codex` dependency review, focusing on the three
blockers it names (D-FSM-002, D-FSM-006–008).

**D-FSM-006 — every API `Agent_Codex` cited exists in 1.21.1 (`CODE_CONFIRMED`, mapped jar).**
`RecipeManager.getRecipeFor(RecipeType<T>, I extends RecipeInput, Level)` is present and generic;
`SingleRecipeInput` is a record implementing `RecipeInput`; `AbstractFurnaceBlockEntity.getFuel()`
is a **public static** `Map<Item, Integer>` and `isFuel(ItemStack)` is public static. Live recipe and
fuel discovery is implementable exactly as proposed — this blocker is a design decision now, not a
feasibility question.

**New — D-FSM-009, furnace slot access.** `SLOT_INPUT` / `SLOT_FUEL` / `SLOT_RESULT` are
**`protected static`**, so the natural implementation hardcodes `0`/`1`/`2` — a Gate SPM-0 level-7
hardcode that is silently wrong for any modded furnace with a different layout. The public
`WorldlyContainer` face API (`getSlotsForFace`, `canPlaceItemThroughFace`, `canTakeItemThroughFace`,
`canPlaceItem`) is the hopper contract, is fully public on `AbstractFurnaceBlockEntity`, and works
against **any** modded block entity implementing it. Recommended as the access contract; it converts
the Compatibility topic from an assumption into a stated interface.

**D-FSM-008 — do not invent a transaction; reuse TT-0R's.** This is the same shape as the defect that
RFC-TOOL-TIER-UPGRADES already solved: take from the backpack, commit elsewhere, and lose the
ingredients when the commit cannot land. That RFC's answer — an atomic snapshot transaction across
every `apply()` path, with `u0a`/`u0b` full-pack tests — is the pattern here, extended across a
**block-entity boundary and a time gap**. Two things make the furnace case strictly harder and should
be stated in the decision: the commit is **not** instantaneous (the mob can die, unload, or be
interrupted mid-burn), and the counterparty is **not ours** (a player or hopper can mutate the furnace
between reserve and extract). A snapshot rollback cannot undo a burn that already happened, so the
transaction boundary belongs at *insert* and *extract* separately, not around the whole job.

**D-FSM-002 / D-FSM-007 — why the crafting table is not a precedent.** The RFC reasonably asks whether
furnace station handling can mirror the table. It cannot, and the reason is sharper than "ownership is
unclear": `CraftTorchesGoal` has **no claim concept at all** (`NOT FOUND` for claim/owner/reserved),
and it does not need one, because crafting is **instantaneous and stateless** — the mob walks to any
table, converts items it already holds, and leaves nothing behind. Smelting is **durative and
stateful**: it leaves our items in a block we do not own for many seconds. The table's design is
therefore evidence that *stations do not need ownership*, not evidence that furnaces do not. Mirror
the search and placement; do not mirror the absence of a claim.

Agreement: Proposal B (recipe-backed pure policy + dedicated goal) is right and matches what already
works in this codebase. `Agent_Codex`'s objection that the first draft was not implementation-ready is
correct.

Concerns: none blocking beyond the ones already recorded. D-FSM-009 should be resolved alongside
D-FSM-008 since both concern how we touch the block.

Recommendation: promote D-FSM-006 to `CONSENSUS` — feasibility is settled and only the selection
policy remains. Keep FS-1+ blocked until D-FSM-007/008/009 land.

RFC fields updated: Identity (peer review), Decision Registry (D-FSM-006 note, D-FSM-008 note, new
D-FSM-009), D-FSM-009 section, Change Log, this contribution.

---

### Contribution — Agent_Codex (furnace dependency review)

Agent: Agent_Codex  
Date/Session: 2026-08-08 ~15:28 PDT  
Contribution type: REVIEW / RESEARCH / OBJECTION / DESIGN

Reviewed: Agent_Cursor 4's complete furnace RFC; current gather/craft/tool policies; pinned SPM
v0.86.0 absence probes; mapped MC 1.21.1 furnace, recipe, fuel, loot and recipe data.

Agreement: A dedicated smelting goal with pure decision policy is the simplest architecture that
preserves real furnace behavior. Charcoal and iron should share it.

Concerns: The fixed recipe enum and copied fuel priority would ignore datapacks/modded recipes and
live fuel semantics. “Use nearby furnace” has no ownership proof. Input, fuel and output cross two
inventories without a transaction, and goal state alone cannot recover across unload/restart.
Ordinary iron mining yields raw iron, invalidating the original ore-only hand-off.

Evidence: `SOURCE_CONFIRMED` mapped signatures for `RecipeManager#getRecipeFor`,
`AbstractFurnaceBlockEntity` and `getFuel`; packaged `iron_ore` loot table and raw-iron/ore/charcoal
smelting JSON. `CODE_CONFIRMED` `ToolBox` already uses `ItemStack#isCorrectToolForDrops`; gather
currently keeps only cobble/coal/charcoal/logs.

Alternatives: Any-furnace inference (rejected theft/merge risk); in-memory-only claim (restart
orphan risk); persisted bounded job ticket (preferred research direction); backpack-only delayed
simulation (rejected because it does not use a real furnace).

Recommendation: Keep D-FSM-001 at consensus, resolve D-FSM-006–008 and D-FSM-002 ownership before
locking, then make FS-1 the first implementable task. No implementation was authorized or performed.

RFC fields updated: Identity, Executive Summary, Research Ledger, Brainstorming, Smelting,
Compatibility, Persistence and contention, decisions, tasks, gates, approval checklist, change log.

### Contribution — Agent_Cursor 4 (Phase 2 kickoff)

Agent: Agent_Cursor 4  
Date/Session: 2026-08-08  
Contribution type: DESIGN + RESEARCH  

Reviewed: `RFC-TOOL-TIER-UPGRADES.md` Phase 2 deferrals; `ScavengerCrafting`, `GatherResourcesGoal`,
`CraftTorchesGoal`, `GatherProtection`; SPM v0.86.0 furnace probe.

Agreement: Iron tools must not ship without smelting; charcoal shares furnace infra and fixes no-coal
parity. Split RFCs keep stone Phase 1 independent.

Evidence: `CODE_CONFIRMED` charcoal accepted in craft fuel counts; `CODE_CONFIRMED` iron ore not in
`isGatherableOre`; `NOT FOUND` furnace/smelt in SPM reference (3 probes).

Recommendation: User lock D-FSM-001–005, then authorize FS-0. Tool-tier Phase 2 (TT-2*) starts after
FS-3 merges.

RFC fields updated: Entire document (initial).

---

### Contribution — Agent_Codex (furnace RFC continuation: demand ownership)

**Agent:** Agent_Codex  
**Date/Session:** 2026-08-08  
**Contribution type:** `REVIEW / RESEARCH / DESIGN / OBJECTION`

**Reviewed:** the complete furnace RFC; Agent_Claude's D-FSM-010/011 contributions and conformance
audit; current `FurnacePolicy`, `FurnaceTransfers`, `SmeltAtFurnaceGoal`, `ScavengerCrafting`,
`ScavengerConfig`, registration, tests, runtime-datapack claims/filesystem, and pinned SPM
valuable-item policy.

**Agreement:** the current iron branch is an exposed producer without a consumer, not a theoretical
gap. SPM admits raw iron and ingots to the backpack, this addon plans iron up to a fixed six, and no
iron tool craft step exists. Consumer-driven, recomputed demand is the correct direction. An
inserted ticket must finish even when its initiating demand disappears.

**Objection:** the proposed separate “trivial resolver” would duplicate `3 ingots + 2 sticks` from
the future craft transaction. That introduces two mutable recipe truths and can reproduce
producer/consumer drift. Prefer a consumer-owned immutable recipe specification used both for
atomic craft execution and demand emission. The demand aggregator remains generic and knows no
iron-tool recipe. D-FSM-010 therefore moves from `PROPOSED` to `CONTESTED`, not backward in overall
direction but pending this ownership choice.

**Compatibility finding:** `FurnaceTransfers` honors face APIs but fixes fuel insertion to NORTH.
Agent_Claude's earlier concern is independently confirmed. D-FSM-012 reaches `CONSENSUS`: probe the
four horizontal faces deterministically and choose one that accepts the selected fuel. This is a
bounded per-job operation and preserves fail-closed behavior.

**Negative evidence:** `MaterialDemand`, a work-demand policy, owned storage/deposit integration,
and iron craft steps were each `NOT FOUND` in current `src/main`. The Phase 2 datapack directory,
README, and functions were also `NOT FOUND`; only its TEST_MATRIX references remain. These absences
constrain the first slice; they do not prove a full planner or storage layer is required.

**Recommendation:** repair FS-6 and resolve D-FSM-010 Option A versus B; either choose D-FSM-011's
interim default-0 switch or ship the consumer first. FS-6 and FS-7 are independently ready after
explicit implementation authorization. Do not run RT-F* until the datapack exists and the user
separately approves a Minecraft launch.

**Evidence labels:** current symbol and source claims are `CODE_CONFIRMED`; compatibility behavior,
goal-frequency balance, restart behavior, and gameplay parity remain `UNVERIFIED` without runtime
evidence.

**RFC fields updated:** Identity, Executive Summary, Collaboration Protocol, Baselines, Topic Index,
Smelting, Material demand, Compatibility, Validation, D-FSM-010, D-FSM-012, Feature/Scenario Parity,
Tasks, Gates, Deferred/Unverified, User Approval, Change Log, and this contribution.

---

### Contribution — Agent_Cursor (Continue the RFC: FS-6 + D-FSM-010)

**Agent:** Agent_Cursor  
**Date/Session:** 2026-08-08 ~17:50 PDT  
**Contribution type:** `REPAIR / DESIGN / REVIEW`

**Reviewed:** Codex FS-6 negative evidence; D-FSM-010 Option A vs B contest; Phase 1 datapack layout;
`docs/porting/TEST_MATRIX.md` RT-F* rows; `FurnaceStations.mayUse` ownership gate.

**Agreement:** Codex was correct that FS-6 was `PARTIAL` — TEST_MATRIX without the datapack is not
a reproducible runtime matrix. Consumer-driven demand direction stands; Option B is the safer
requirement-source ownership than a duplicated trivial resolver.

**Repair:** created `test-datapacks/phase2-furnace/` (namespace `spm_phase2`) with README,
`pack.mcmeta` format 34, setup/quickstart/help/cleanup, arena fixtures (table, place pad, empty
non-owned furnace at +4 for communal-only, log/cobble stashes, busy player furnace at +6 with coal),
and spawn presets for RT-F1–RT-F5. FS-6 layout marked `COMPLETE`. RT-F* behaviour remains
`UNVERIFIED` (AV-1).

**Decision:** promoted D-FSM-010 Option B to `CONSENSUS`. Cross-linked `RFC-TOOL-TIER-UPGRADES.md`
TT-2b dependency. Left D-FSM-011 as product decision; left FS-7 as `READY` pending Begin
implementation; no Java edits; no Minecraft launch.

**Recommendation:** (1) **Begin implementation for FS-7** (horizontal fuel faces + U-F10); (2)
product call on **D-FSM-011** default-0 interim vs wait for consumer; (3) authorize tool-tier
TT-2b / FS-8 for Option B; (4) separately approve RT-F* launch when ready.

**RFC fields updated:** Identity, Executive Summary, Collaboration, Baselines, Topic Index, Material
demand, Validation, Decision Registry, D-FSM-010, Tasks, Gates, Deferred, User Approval, Change Log,
this contribution. Sibling tool-tier Phase 2 TT-2b / D-TTU-016 notes updated.

---

### Contribution — Agent_Codex (Continue the RFC: locked slices + cross-RFC boundary)

**Agent:** Agent_Codex  
**Date/Session:** 2026-08-08 (after Agent_Cursor ~17:50 and Agent_Claude cross-RFC review)  
**Contribution type:** `REVIEW / RESEARCH / DESIGN / OBJECTION / VALIDATION`

**Reviewed:** current furnace RFC and latest contributions; tool-tier Phase 2 links; vanilla
autonomous-progression `RequirementResolver`/`WorkDemand` design; current Phase 2 datapack; mapped
1.21.1 `DataCommands`; every internal `spm_phase2` function reference; `pack.mcmeta` JSON.

**Agreement:** Agent_Cursor's Option B resolves the duplicate recipe-table objection. D-FSM-010 is
now precise enough to lock when narrowed to one upgrade frontier at a time. D-FSM-012 has two
independent supporting reviews, a bounded implementation, U-F10, compatibility handling, and no
remaining high-severity objection, so it is locked.

**New validation defect:** FS-6 file presence is real, and all internal function targets plus
`pack.mcmeta` pass static existence/JSON checks. The initializer command does not parse under the
mapped target grammar: it omits `targetPath` before `set` and appends `initialized` after the value.
FS-6 is `REOPEN_REQUESTED`; the required repair target is
`data modify storage spm_phase2:main initialized set value 1b`. No datapack file was changed because
this turn is PLANNING.

**Cross-RFC objection resolved into D-FSM-013:** `WorkDemand` and `MaterialDemand` must not become
independent selectors. Preferred boundary: `MaterialDemand` is a derived material payload/fact;
`WorkDemand` is the scheduled envelope; `WorkDemandPolicy` is the only arbitrator. Furnace owns
`SMELT_BATCH` payload consumption, tool-tier TT-2b owns consumer recipes, and the vanilla RFC owns
the envelope/selector. At the time of that contribution D-FSM-013 remained `PROPOSED`; the later
Agent_PeerReviewer contribution below accepted and locked it.

**Alternatives:** flat nullable record (rejected invalid states); independent policies (rejected
conflicting winners); typed envelope/payload (preferred). Generic output→recipe reverse indexing is
not an FS-8 dependency and remains deferred until a second consumer proves it necessary.

**Must happen:** one frontier emits one derived deficit; one WorkDemand selector chooses the
executor; an inserted ticket resumes after de-latch; the repaired datapack initializes exactly once.
**Must not happen:** simultaneous pick+axe preproduction, duplicate urgency fields, per-mob recipe
scans, two policies scheduling contradictory goals, or RT-F* launching with an unparseable setup.

**Evidence:** source and mapped-command claims are `CODE_CONFIRMED`/`SOURCE_CONFIRMED`; runtime
furnace behavior, compatibility with side-asymmetric machines, and scale remain `UNVERIFIED`.

**RFC fields updated:** Identity, Executive Summary, Collaboration, Baselines, Research Ledger,
Topic Index, Material demand, Validation, D-FSM-010/012/013, Tasks, Gates, Deferred, User Approval,
Change Log, and this contribution.

---

### Contribution — Agent_Codex (Work the RFC: FS-6/FS-7/FS-9)

**Agent:** Agent_Codex  
**Date/Session:** 2026-08-08  
**Contribution type:** `IMPLEMENTATION / VALIDATION`

**Implemented:** repaired the Phase 2 storage initializer; replaced fixed NORTH fuel access with a
deterministic NORTH/SOUTH/WEST/EAST no-mutation preflight followed by one-face commit; added EAST-only
and no-accepting-face transaction tests. Applied the user's D-FSM-011 product choice as a visible
`ironStockTarget` config defaulting to 0; explicit 6 preserves the focused iron-smelt test mode.

**Alternatives preserved:** fixed NORTH remains rejected as silently incompatible; mutating
face-by-face retry remains rejected because it can split one logical transfer; disabling all
smelting remains rejected because charcoal has a real consumer.

**Validation (`CODE_CONFIRMED`):** RED compile failure pinned the absent config seam; focused tests
then passed. `clean build` passed with 101 tests and zero failures/errors/skips. Static datapack
validation found format 34, 27 function files, 33/33 resolved internal calls, and a valid initializer.
The final JAR contains `fabric.mod.json` and no temporary datapack entries.

**Runtime boundary:** no Minecraft launch occurred. RT-F1–RT-F5, live ticket recovery after a config
change, and real third-party side-asymmetric furnace compatibility remain `UNVERIFIED`.

**RFC fields updated:** Identity, Executive Summary, Baselines, Iron production, Compatibility,
Validation, D-FSM-011/012, Feature/Scenario Parity, Tasks, Gates, Deferred, approvals, Change Log,
and this contribution.

---

### Contribution — Agent_PeerReviewer and Agent_Codex (D-FSM-013 / FS-8)

**Agent:** Agent_PeerReviewer (review), Agent_Codex (implementation)  
**Date/Session:** 2026-08-08  
**Contribution type:** `REVIEW / IMPLEMENTATION / VALIDATION`

Agent_PeerReviewer independently accepted D-FSM-013 Option B after five targeted `NOT FOUND`
probes for an existing demand/spec implementation. The review rejected a flat nullable record and
independent selectors, narrowed stale TT-2a, and identified IRON config reachability as mandatory.

Agent_Codex implemented `WorkDemandPolicy`, pure `MaterialDemand`, consumer-owned shared iron-tool
specs, main-hand-aware recomputation, and production call-site plumbing. `ironStockTarget` was
removed; `SmeltAtFurnaceGoal` still checks INSERTED tickets before fresh demand. Focused tests and
`clean build` passed; 107 tests reported zero failures/errors/skips. Runtime and 1/10/50/100-mob
performance remain `UNVERIFIED` because Minecraft was not launched.

---

### Contribution — Agent_Cursor (Continue the RFC: post-FS-8 reconcile)

**Agent:** Agent_Cursor  
**Date/Session:** 2026-08-08 ~18:50 PDT  
**Contribution type:** `REVIEW / REPAIR`

**Reviewed:** Identity/Tasks/Gates vs current `src`; FS-8 contribution; `WorkDemandPolicy`,
`ScavengerCrafting` iron specs; `FurnacePolicy.demand`; `SmeltAtFurnaceGoal` ticket resume;
`ScavengerConfig` (no `ironStockTarget`); Phase 2 datapack + TEST_MATRIX; sibling tool-tier
TT-2b status.

**Agreement:** Codex/PeerReviewer FS-8 claims are `CODE_CONFIRMED`. The iron producer-without-consumer
defect is closed in source. FS-10 is the only remaining furnace-RFC critical path and is blocked
solely by launch approval, not missing design.

**Objection / repair:** several RFC sections still described pre-FS-8 state (Material demand
`BLOCKED`, Gates unchecked for FS-8/D-FSM-013, D-FSM-011 as live interim, Baselines live-defect
paragraph, RT-F2 interim wording). Reconciled those to `IMPLEMENTED` / `SUPERSEDED` /
`READY`. Aligned datapack `need_iron_smelt` / `second_claimant` tellraws with consumer-driven
RT-F2. Noted U-F9 has goal-order evidence but no dedicated unit test (`NOT FOUND`).

**Recommendation:** authorize **FS-10** Minecraft launch for RT-F1–RT-F5 when ready; optionally add
a dedicated U-F9 unit; continue tool-tier **TT-2c** for iron ore gather outside this RFC.

**RFC fields updated:** Identity, Executive Summary, Collaboration, Baselines, Topic Index, Iron
production, Material demand, Compatibility, Validation, Decision Registry, Feature/Scenario Parity,
Tasks, Gates, Deferred, User Approval, D-FSM-010/011, Change Log, this contribution. Datapack
tellraws only (no Java).
