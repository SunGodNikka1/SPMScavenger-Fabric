# RFC: Mining intelligence and wealth system

## RFC Identity

| Field | Value |
| --- | --- |
| **Project root** | `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric` |
| **Host platform** | Social Player Mobs (`playermob`) v0.86.0 — reference `Projects/references/SocialPlayerMobs-v0.86.0/` |
| **Target progression** | **Vanilla Minecraft 1.21.1 mining + resource wealth** (overworld ore tiers through diamond/deepslate; not Nether/endgame mining in gen-1) |
| **Scope** | Autonomous *where* to mine, *how much* to stockpile (wealth), prerequisite planning hooks, capability gaps, integration methods, phased plan, validation — **design until implementation authorized** |
| **Mode** | `PROGRESSIVE_CONTINUATION` (Agent_Cursor — MI-13 + MI-2) |
| **Status** | Gen-1 through MI-4R + MI-13 + MI-2 `IMPLEMENTED`; 155 tests and clean build pass; runtime `UNVERIFIED` |
| **User constraint** | No Minecraft launch, commit, or push unless separately asked; implementation only after explicit Begin authorization |
| **Baseline version** | `1.9.2` |
| **Related** | `RFC-TOOL-TIER-UPGRADES.md`; `RFC-VANILLA-AUTONOMOUS-PROGRESSION.md`; `RFC-FURNACE-SMELTING.md`; stubs `progression/ProgressGoal.java`, `TaskLifecycle.java` |
| **Former name** | `RFC-MINING-INTELLIGENCE-AND-RESOURCE-GREED.md` — merged into this file (2026-08-08); “resource greed” → **wealth system** |
| **Owners** | User (product) |
| **Peer review** | `Agent_Cursor` · `Agent_ChatGPT` · `Agent_Cursor 2` · `Agent_Codex` · `Agent_Claude` |
| **Last update** | 2026-08-08 ~21:40 PDT |
| **Gate** | MRFC-1 |

### Naming

| Term | Meaning |
| --- | --- |
| **Interactive Player Mobs** | User brief wording → **Social Player Mobs** (`CONFIRMED` — only reference tree in workspace) |
| **Target Mod** | **Vanilla 1.21.1** mining progression (wiki-aligned), executed via SPM + Scavenger addon — not a separate tech mod |
| **Wealth system** | `ResourceWealthPolicy` — marginal utility, need layers, opportunity bonus (not hard stock targets) |
| **ResourceWealthPolicy** | Generic policy: evaluates *how valuable is one more* of a resource right now |
| **Greed trait** | Persistent `greed ∈ [0.0, 1.0]` — modifies wealth params only, not progression minimums |
| **Mining intelligence** | `MiningDirector` + `MiningProject` + legitimate discovery; physical dig in `GatherResourcesGoal` |
| **MiningProject** | Bounded mining session (mode, budget, anchors) — policy state, not a registered Goal |
| **DiscoveryMode** | How an ore became a legitimate target (`VISIBLE`, `NEWLY_EXPOSED`, …) |

---

## Executive Summary

PlayerMobs should progress through **vanilla mining** using **deterministic classical AI** (bounded backward chaining + policy layers), not LLMs.

**Today (`CODE_CONFIRMED`):** Scavenger implements surface gather (logs, coal, iron ore when deficit), craft, smelt, explore — all with **nearest-block targeting** and **exact consumer deficits** (iron stops at craft minimum). SPM provides combat, loot, crops, doors, dig-through **readout only on Dungeon Train** — not general mining.

**This RFC delivers:**

1. **Mining progression dependency graph** (wood → stone → iron → diamond/deepslate).
2. **Prerequisite planning architecture** (Goal → Requirements → Subgoals → Executors).
3. **Wealth system** — separate NEED from WEALTH; marginal utility + opportunity bonus (not `target=6 → worthless`).
4. **Mining intelligence** — legitimate discovery, `MiningProject` modes, cave memory, veins, hazards.
5. **Capability + integration matrix** with feasibility labels.
6. **Phased plan** and runtime test specification.

**Wealth foundation (`CONSENSUS`, Agent_ChatGPT D-MIW-015):** progression **need** (blocking/replacement/project) is separate from **wealth desire** (diminishing marginal utility + `OpportunityBonus`). `ResourceWealthPolicy` is generic — not `IronGreedPolicy` / `DiamondGreedPolicy`.

**Mining architecture (`CONSENSUS`, D-MIW-001):** `MiningDirector` / `MiningProject` / `MiningMemory` are policy + session state; `GatherResourcesGoal` owns physical dig; no clairvoyant ore map.

**Continuation result (`CODE_CONFIRMED`, Agent_Cursor):** MI-13 adds `DiscoveryMode` classification
(`VISIBLE`, `NEWLY_EXPOSED`, `UNDISCOVERED`, …) with harvest-reveal tracking in
`GatherResourcesGoal`. MI-2 adds `GatherTargetPolicy` so blocking consumer demand outranks optional
wealth among pass-one candidates before path probes. Seven new unit tests; full suite 155/155 pass.
Runtime behavior, vein follow, and F-2 progression-demand split remain `UNVERIFIED` / deferred.

---

## Collaboration Protocol

- This continuation is **`Agent_Codex`** (MI-4R implementation and static validation).
- Evidence: `CONFIRMED` / `INFERRED` / `UNVERIFIED` (Gate AV-1).
- Reuse SPM + Scavenger executors; no duplicate scanners (Gate SPM-2).
- **Anti-clairvoyance (D-MIW-008 `CONSENSUS`):** undiscovered ore behind solid stone is never an exact path target from server block query alone.
- Physical break: `GatherResourcesGoal` (or thin `DigAction` helper it calls). Session intent: `MiningProject`. Orchestration: `MiningDirector`.
- Record frontier transitions in Contribution blocks.

---

## Topic Index

| Topic | Status | Summary |
| --- | --- | --- |
| [Progression graph](#topic-progression-graph) | `CONFIRMED` structure | Vanilla mining tiers + edges |
| [Prerequisite planning](#topic-prerequisite-planning) | `CONSENSUS` | Bounded backward chain, not one script (D-MIW-002) |
| [Wealth system](#topic-wealth-system) | `CONSENSUS` gen-1 | NEED vs WEALTH; `ResourceWealthPolicy` (D-MIW-015/016) |
| [Marginal utility](#topic-marginal-utility-and-wealth-curves) | `CONSENSUS` | Diminishing value; no hard target (D-MIW-017) |
| [Opportunity bonus](#topic-opportunity-bonus) | `CONSENSUS` | Local acquisition makes wealth worthwhile |
| [Greed trait](#topic-greed-trait) | `CONSENSUS` config / `DEFERRED` SPM hook | Config `greed` gen-1; SPM trait map deferred |
| [Resource profiles](#topic-resource-wealth-profiles) | `CONSENSUS` | Gen-1 v1 constants locked (D-MIW-026) |
| [Legitimate discovery](#topic-legitimate-ore-discovery) | `CONSENSUS` | MI-13a perception fix + anti-clairvoyance |
| [Resource portfolio](#topic-resource-portfolio) | `DEFERRED` gen-1 | After wealth curves ship |
| [Scarcity memory](#topic-scarcity-and-consumption) | `DEFERRED` gen-1 | After MI-24 |
| [Mining intelligence](#topic-mining-intelligence) | `CONSENSUS` skeleton | Director later; dig stays in gather |
| [MiningProject modes](#topic-miningproject-modes) | `CONSENSUS` catalog | Modes exist; gen-1 starts SURFACE + CAVE only |
| [MiningMemory](#topic-miningmemory) | `PROPOSED` | After DiscoveryMode |
| [Vein extraction](#topic-vein-extraction) | `PROPOSED` | After MiningProject |
| [Ore utility](#topic-ore-utility-while-mining) | `CONSENSUS` formula | Delegates to wealth policy |
| [Hazards and tools](#topic-hazards-tool-durability-and-switching) | `CONSENSUS` capability | `canHarvest` live check (D-MIW-011) |
| [MiningDirector](#topic-miningdirector-and-site-selection) | `PROPOSED` | After cave memory |
| [How humans mine](#topic-how-humans-mine-cave-vs-dig-down) | `CONSENSUS` | Caves first, not dig to Y=−1 |
| [Capabilities](#topic-capabilities) | `CONFIRMED` refresh | Diamond + furnace craft present |
| [Integration methods](#topic-integration-methods) | `CONSENSUS` | Per-capability ladder; SPI deferred |
| [Task lifecycle](#topic-task-lifecycle) | `CONSENSUS` | RUNNING/SUCCESS/FAILURE/… |
| [Phased plan](#topic-phased-implementation-plan) | `CONSENSUS` order | MI-4R gather-wealth repair next |
| [Validation](#topic-validation) | `PARTIAL` | Policy units green; gather wealth + runtime open |
| [Deferred](#topic-deferred-and-unverified) | — | Nether, branch mines, portfolio gen-1 |

---

## Topic: Progression graph

**Status:** `CONFIRMED` structure (vanilla 1.21.1 data); Scavenger coverage `CODE_CONFIRMED` per row.

### Mining-focused dependency graph

```text
[T0] Spawn
  logs → planks → sticks → crafting table
  ↓
[T1] Surface stone age
  wooden pick → stone/cobble → stone pick → coal ore (surface/exposed)
  furnace (8 cobble) → charcoal branch
  ↓
[T2] Iron age
  iron ore (#minecraft:iron_ores) → raw iron → smelt → iron ingot
  iron pick + iron axe (3 ingot + 2 stick each)
  ↓
[T3] Diamond / deepslate age
  reach mining band (Y ≤ 16, optimal ≈ Y −59)
  iron pick → diamond ore (#minecraft:diamond_ores) → diamond
  diamond pick + diamond axe
  ↓
[T4] Overworld power (OUT OF SCOPE gen-1)
  obsidian cast, enchanting, nether portal frame
  ↓
[T5] Nether / End (NOT PRACTICAL — see RFC-VANILLA)
```

### Node table (mining path)

| Node ID | Requires | Produces | Scavenger today | Feasibility |
| --- | --- | --- | --- | --- |
| M-wood-tools | table, planks | wood pick | `IMPLEMENTED` | **FULL** |
| M-stone-tools | wood pick, cobble | stone pick | `IMPLEMENTED` | **FULL** |
| M-coal | stone+ pick | coal | `IMPLEMENTED` (surface) | **FULL** |
| M-furnace | 8 cobble | furnace item | `IMPLEMENTED` (`MAKE_FURNACE` + place) | **FULL** |
| M-iron-ore | stone+ pick, **reachable ore** | raw iron | `IMPLEMENTED` (TT-2c) | **PARTIAL** (surface bias) |
| M-iron-ingot | furnace, fuel | ingot | `IMPLEMENTED` | **FULL** |
| M-iron-tools | ingots, sticks | iron tools | `IMPLEMENTED` (TT-2b) | **FULL** |
| M-reach-depth | navigation | Y ≤ 16 | **PARTIAL** — `diamondDeficit` zeros above Y16; no descent seek | **PARTIAL** (this RFC MI-4/MI-5) |
| M-diamond-ore | iron pick, exposed/deep ore | diamond | `IMPLEMENTED` consumer-pull + Y gate | **PARTIAL** (no seek; needs exposure in range) |
| M-diamond-tools | 3 diamond + sticks | diamond tools | `IMPLEMENTED` (`MAKE_DIAMOND_*`) | **FULL** (craft; runtime `UNVERIFIED`) |
| M-branch-mine | iron pick, torches, Y band | sustained ore | `NOT FOUND` | **NOT PRACTICAL** gen-1 |
| M-nether-mining | portal, nether pick | ancient debris | `NOT FOUND` | **NOT PRACTICAL** |

### Cross-link: vanilla + mod progression

When a `ProgressGoal` needs diamonds, the resolver must walk **backward through vanilla**:

```text
DIAMOND_PICKAXE
  → 3× diamond + 2 sticks
  → diamond ore + iron pick
  → iron ingot ×3 (pick) + smelt + furnace + fuel
  → raw iron + stone pick
  → … → wood → logs
```

Single graph — not separate “vanilla script” and “mining script” (`CONSENSUS`, aligns `RFC-VANILLA` D-VP-001).

---

## Topic: Prerequisite planning

**Status:** `CONSENSUS` (D-MIW-002 — Agent_Cursor 2 peer)

**Reject:** one giant scripted sequence from spawn to diamond.

**Accept:** bounded backward chaining:

```text
ProgressGoal (config / wealth target)
    ↓
RequirementResolver.resolve(goal, backpack, recipes, tool gates)
    ↓
List<MaterialNeed> (item, qty, DemandClass)
    ↓
WorkDemandPolicy + ResourceWealthPolicy (prioritize one live demand)
    ↓
Executor (Gather / Craft / Smelt / Explore / …)
    ↓
TaskLifecycle state on active session
```

### Example trace

```text
Goal: CRAFT diamond pickaxe

Requires:
  - 3 diamond (BLOCKING immediate need)
  - 2 sticks
  - iron pick disposed on upgrade

Wealth (post-craft, separate):
  - diamond wealth desire from marginal curve + greed
  - no dedicated expedition unless net utility > 0

Subgoals (resolver output):
  1. diamond × effectiveDeficit
  2. sticks (if low)
  3. smelt chain only if iron tools missing

Executable today:
  - Gather iron ore (if iron pick needed)     → GatherResourcesGoal
  - Smelt raw iron                            → SmeltAtFurnaceGoal
  - Craft iron/diamond tools                  → CraftTorchesGoal / ScavengerCrafting
  - Break diamond ore (P3)                    → GatherResourcesGoal

Blocked until this RFC:
  - Reach deepslate band without surface ore  → MiningDirector MI-14…MI-19
  - Legitimate discovery / anti-clairvoyance   → DiscoveryMode MI-13
  - Bank 10 iron while pick satisfied          → Wealth system MI-3
```

### Resolver constraints (D-MIW-002)

| Rule | Rationale |
| --- | --- |
| Finite node catalog | No open-ended mod item search gen-1 |
| Live `RecipeManager` for smelt/craft edges | Gate 4.11 integrity |
| `ConsumerRecipeSpec` for tool upgrades | D-FSM-010 single truth |
| Max recursion depth 12 | Prevent planner creep |
| No omniscience | Ores discovered via `DiscoveryMode` only (D-MIW-008) |

**Stubs (`CODE_CONFIRMED`):** `progression/ProgressGoal.java`, `progression/TaskLifecycle.java` — resolver not implemented (`NOT FOUND` in `src/main`).

---

## Topic: Wealth system

**Status:** `CONSENSUS` gen-1 (D-MIW-015/016/017/003/020; Agent_Cursor 2 peer)

### Separate NEED from WEALTH (D-MIW-015 `CONSENSUS`)

**Reject:** robotic hard target — `need iron → target=6 → have 6 → iron worthless`.

**Accept:** five-layer resource value model:

```text
RESOURCE VALUE
├── Immediate Need        — blocking craft/smelt/survival right now
├── Replacement Need      — tool about to break; preemptive restock
├── Working Reserve       — "I expect to need this soon"
├── Project Demand        — active MiningProject / ProgressGoal commitment
└── Wealth Desire         — marginal utility of one more (diminishing)
```

**Example:** mob owns 10 iron ingots.

| Layer | Value |
| --- | --- |
| Immediate need | 0 |
| Replacement need | 0 |
| Working reserve | 0 |
| Project demand | 0 |
| Wealth desire | +18 |

→ **No emergency iron expedition.** But exposed iron vein beside path → **"Yeah, I'm taking that."**

### Core policy: `ResourceWealthPolicy` (D-MIW-016 `CONSENSUS`)

**Not:** `IronGreedPolicy`, `DiamondGreedPolicy`.

**One generic evaluator** per resource category:

```java
public final class ResourceWealthPolicy {
    public WealthUtility evaluate(ResourceWealthContext ctx);
}

public record ResourceWealthContext(
    ResourceCategory category,
    int currentAmount,
    int committedDemand,
    int workingReserve,
    float acquisitionCost,      // detour + dig + danger
    float inventoryPressure,
    float scarcityMultiplier,
    float greed,                // mob trait — wealth params only
    ResourceWealthProfile profile
) {}
```

**Question answered:** not *"Do I want iron?"* but *"How valuable is ONE MORE iron right now?"*

### Unified utility formula (architecture — numbers tuned later)

```text
ResourceUtility =
    blockingDemand
  + replacementDemand
  + projectDemand
  + reserveValue
  + wealthValue              // marginal utility curve
  + opportunityBonus           // cheap acquisition right now
  - acquisitionCost
  - dangerCost
  - inventoryPressure
```

Feeds `GatherIntentPolicy`, `GatherTargetPolicy`, and [Ore utility](#topic-ore-utility-while-mining).

### Reserve vs Wealth (D-MIW-020 `CONSENSUS`)

| Concept | Meaning |
| --- | --- |
| **RESERVE** | "I expect to need this." — working stock, project buffer |
| **WEALTH** | "More is intrinsically useful/desirable." — diminishing marginal utility |

**Iron example:**

| Band | Amount | Behaviour |
| --- | --- | --- |
| Critical | 0–3 | Immediate need dominates |
| Active | 3–9 | Reserve + wealth both pull |
| Opportunistic | 9–32 | Wealth only; no dedicated expedition |
| Greed-dependent | 32+ | Weak unless high greed or opportunity |

No single hard `ironStockTarget = 6`.

### Comfortable range vs target (D-MIW-017 `CONSENSUS`)

**Move away from:**

```java
ironStockTarget = 6;  // hit → ignore forever
```

**Toward per-resource profile:**

| Field | Meaning |
| --- | --- |
| `minimum` | Consumer-derived (craft/smelt blocking) |
| `comfortable` | Actively desirable band top |
| `saturation` | Wealth utility near floor |

**Example (tune by test, not canonical):**

| Resource | minimum | comfortable | saturation |
| --- | --- | --- | --- |
| Iron | consumer | 16 | 64 |
| Diamond | consumer | 8 | 32 |
| Coal | consumer | 32 | 128 |
| Cobble | craft | 16 | 64 |

| Band | Behaviour |
| --- | --- |
| below minimum | **urgent** |
| minimum → comfortable | **useful** |
| comfortable → saturation | **wealth only** |
| above saturation | **weak greed / opportunity only** |

### Prioritization stack (D-MIW-003 `CONSENSUS`)

```text
1. SURVIVAL           — food, torches, immediate danger
2. BLOCKING NEED      — immediate + replacement + project minimum
3. RESERVE            — working reserve shortfall
4. WEALTH             — marginal utility + opportunity (diminishing)
5. IDLE               — explore / camp when net utility ≤ threshold
```

**Must happen:** craft at blocking minimum before wealth-driven side mining.  
**Must not:** wealth fills pack and blocks survival chain.

### Gen-1 stepping stone (legacy compat)

**Do not reintroduce** `ironStockTarget` / `wealthRawIron` push knobs (`CODE_CONFIRMED` removed by FS-8).
Transitional `WealthTarget(minimum, comfort)` is allowed **only** as an internal helper while
`ResourceWealthPolicy` lands — it must not become a second public demand truth beside
`ConsumerRecipeSpec` deficits.

```java
// Transitional internal helper — never a second iron demand source
public record WealthTarget(int minimum, int comfort) { … }
```

Consumer NEED remains exact-spec deficits (`WorkDemandPolicy` / iron / diamond recipes). Wealth only
adds **optional opportunistic** desire **after** blocking/reserve layers (D-MIW-004 `CONSENSUS`
revised).

### Config (D-MIW-004 `CONSENSUS`)

| Field | Default | Purpose |
| --- | --- | --- |
| `greed` | 0.0 gen-1 (recommended) or 0.55 later | Wealth params only; **0 = exact-consumer parity** |
| `wealthLevel` | 0 | Global scaler; 0 disables wealth expeditions |
| Per-category profiles | Java constants → datapack later | `minimum`/`comfortable`/`saturation` |

**Rejected:** shipping a new `wealthRawIron` integer that recreates producer-without-consumer hoarding.
**SPM trait → greed:** `DEFERRED` — disposition/hoard trait `NOT FOUND` in SPM v0.86.0 (three probes:
`greed`, `hoard`, `disposition` in reference `src/main` — verify at implement time).

---

## Topic: Utility scale and policy boundaries (5 blocking findings)

**Status:** `CONTESTED` — raised by the user, arithmetic verified by `Agent_Claude` (snapshot 21:31).
**Blocks:** MI-4 acceptance, MI-17, MI-24/25 tuning.

### F-1 — Wealth utility and acquisition cost are on different scales (`CODE_CONFIRMED`)

The locked gen-1 profiles produce utilities in `0…~3`; acquisition cost is described in `0…40+`
with an exposed vein costed at `3`. Executed against the shipped formulas
(`ResourceWealthPolicy.wealthValue` / `opportunityBonus`), iron at the recommended `greed = 0.55`,
`wealthLevel = 1`:

| Held | Cost | wealth | bonus | **net** |
| ---: | ---: | ---: | ---: | ---: |
| 0 | 0 | 0.227 | 0.227 | **+0.454** |
| 0 | **3** | 0.227 | 0.180 | **−2.593** |
| 0 | 10 | 0.227 | 0.071 | **−9.702** |
| 12 | 3 | 0.125 | 0.099 | **−2.776** |

**Maximum achievable iron net utility is ≈ 0.45, at zero cost.** Any real cost — including the
RFC's own exposed-vein example of `3` — makes optional iron acquisition permanently negative. Even
DIAMOND at `greed = 1.0`, `wealthLevel = 1` (the most valuable profile: base 0.90, hoardability 0.95,
rarityAppeal 0.85) nets `+0.15` at cost 3 and goes negative by cost ≈ 5.

The worked example elsewhere in this RFC assumes `wealth 15, opportunity 18, cost 3`. **The locked
constants and the worked examples describe two different systems**, and only the examples describe
the behaviour this RFC exists to create.

**This already affects merged code.** MI-4 wires wealth through `GatherIntentPolicy` and passes
acquisition cost `0.0F` — it functions *only* because of that. The newer distance-aware
`GatherIntent.wants(resource, acquisitionCost)` exposes the break directly: its own test asserts
`wants(RAW_IRON, 0.125F)` true and `wants(RAW_IRON, 3.0F)` false. At a cost of three — an exposed
vein — wealth is already dead.

**Required before MI-4 can be accepted:** pick one normalisation and apply it to every term —
utilities `0…1` with costs normalised into the same range, or utilities `0…100` with costs rescaled.
Do not tune individual constants around the mismatch; that hides it.

### F-2 — The Y-band gate destroys the signal needed to descend (`CODE_CONFIRMED`)

MI-2 specifies *"no deep-ore intent above Y=16 without sighting"*, and MI-4/MI-5 depend on
*"deficit + zero local ore → nudge `ExploringGoal`"*. These contradict: the shipped
`WorkDemandPolicy.diamondDeficit` returns **0** above `DIAMOND_GENERATION_CEILING_Y`, so a surface
mob has no diamond demand for the director to act on. **Nothing can ever motivate a descent.**

The plausibility gate was introduced (Phase 3, sibling RFC) to stop permanent surface scanning, and
it is correct *for that purpose* — but it is currently doing two jobs with one value.

**Required split — two signals, not one:**

| Signal | Above the band | Drives |
| --- | --- | --- |
| `ProgressionDemand(DIAMOND)` | **> 0** — the mob still wants diamond | exploration / descent pressure, `MiningDirector` |
| `LocalGatherEligibility(DIAMOND)` | **false** unless legitimately sighted | candidate scanning, target selection |

The surface-scan harm came from the *second*; the descent motivation needs the *first*. Collapsing
them was the error.

### F-3 — IRON wealth does not distinguish raw iron from ingots (`CODE_CONFIRMED`)

The progression graph correctly separates `iron ore → raw_iron → smelt → iron_ingot`, but
`ResourceWealthContext` carries one `ResourceCategory` and a single `currentAmount`. A mob holding
6 raw iron and 0 ingots is indistinguishable from one holding 0 raw and 6 ingots, though only the
second can craft and only the first needs a furnace. Wealth accounting must either carry the stage
or track the two stacks separately.

### F-4 — `ResourceWealthPolicy` answers two different questions (`CODE_CONFIRMED`)

Its stated job is *"how valuable is one more iron right now?"*, but its context and results carry
`acquisitionCost` and `opportunityBonus` — *"is this particular acquisition worthwhile?"*. Those are
different questions with different lifetimes: desire changes with inventory, acquisition worth
changes with every candidate block.

**Recommended boundary:**

```text
ResourceWealthPolicy   → ResourceDesire          (inventory-only; how much do I want one more)
AcquisitionUtilityPolicy → desire + opportunity − path/dig/danger/inventory cost
```

**Evidence this is not theoretical:** implementing MI-4 I passed acquisition cost `0.0F`
deliberately, precisely to avoid counting distance twice once the candidate scorer also applies it.
That workaround is the boundary asking to exist. When MI-17 (ore utility scoring) arrives, the same
cost will otherwise be applied inside wealth *and* again at target scoring.

### F-5 — Wealth-only expeditions are self-contradictory

One locked rule states wealth alone does not start a dedicated expedition without MEMORY/PROJECT
context; the Cave Exploration topic states *"need diamonds (BLOCKING **or wealth**) can lead to
`ExploringGoal` pressure ↑"*. Both cannot hold.

**Recommended invariant, stated explicitly:** wealth may make a mob *take* nearby or easy resources
and *return to a remembered sighting*. A **dedicated expedition requires `BLOCKING`, `REPLACEMENT`,
`PROJECT` or `RESERVE` demand.** Feeling rich is not a reason to launch a cave trip.

### F-6 — There is no perception budget, only a mining budget (`CODE_CONFIRMED`)

The performance budget caps blocks mined, excavation distance, ticks and failed branches — all
*after* a target is chosen. Nothing caps the search itself, which is where the cost already is:

| Perception cost today | Value |
| --- | --- |
| Scan cadence | every `SCAN_INTERVAL` **60** ticks, per mob |
| Positions per scan | `(2r+1)² × 9` — **~3,969** at radius 10, **~15,129** at radius 20 |
| Candidate buffer | `MAX_CANDIDATES` **24** |
| Path probes per scan | `MAX_PATH_PROBES` **3** |
| Per-mob stagger / jitter | **none** — `NOT FOUND` |
| Cross-tick continuation | none; the scan is synchronous |

`MiningDirector` will increase how many mobs are interested at once, and every interested mob runs
that full synchronous scan. A budget that bounds only the trip leaves the search unbounded.

**Required:** a separate **Perception Budget** decision covering cadence, maximum positions per
evaluation, maximum path probes, **per-mob stagger** (absent today), and whether a scan may continue
across ticks.

### Disposition

| Finding | Blocks | Owner |
| --- | --- | --- |
| F-1 scale | MI-4 acceptance, MI-17, MI-24/25 tuning | product decision on the normalisation target |
| F-2 band split | MI-2, MI-4, MI-5, MI-14 | design |
| F-3 iron stage | MI-24 accounting | design |
| F-4 boundary | MI-17 | design |
| F-5 expedition rule | MI-14 | product decision |
| F-6 perception budget | MI-14 and anything raising scan frequency | design |

**MI-4 is `IMPLEMENTED` but should not be marked accepted** until F-1 resolves: its parity and
additive invariants hold, but its wealth path is only reachable at zero acquisition cost.

---

## Topic: Marginal utility and wealth curves

**Status:** `CONSENSUS` (D-MIW-017 — Agent_Cursor 2 peer)

Wealth is **marginal utility** — value of one more unit **decreases** with stock but rarely hits exactly zero.

### Iron (fast saturation)

```text
0 iron    ████████████████████  extremely valuable
3 iron    ██████████████████    very valuable
10 iron   ██████████████        valuable
32 iron   ████████              nice to have
64 iron   ████                  opportunistic
128 iron  ██                    mostly ignore
512 iron  ▏                     why bother
```

### Diamond (slow saturation — rarity)

```text
0 diamond    ████████████████████
3 diamonds   ███████████████████
16 diamonds  ██████████████
64 diamonds  ████████
256 diamonds ███
```

**Rarity appeal (D-MIW-021):** rare resources retain wealth longer — *"I don't need this diamond, but it's diamond."*

```text
WealthValue = baseUsefulness + rarityAppeal
```

64 cobble → meh. 64 iron → still nice. 64 diamonds → still picking up more.

### Future usefulness

`ResourceWealthProfile.generalUtility` encodes broad consumer potential (iron: tools, armor, bucket, rails…) without enumerating every future project. Full `RequirementResolver` can refine later.

---

## Topic: Opportunity bonus

**Status:** `CONSENSUS` (D-MIW-018 — Agent_ChatGPT)

**Most important piece for human-looking behaviour:** take resources because you're already there.

```text
Mob has 18 iron. No iron consumer.

Dedicated 300-block iron expedition?  NO.

Walking through cave, 8 exposed iron beside path?  YES.
```

```text
OpportunityBonus = f(acquisitionCost, visibility, detour, veinSize)
```

The **cheaper** acquisition is right now, the stronger wealth becomes.

| Scenario | acquisitionCost | iron wealth=15 | Decision |
| --- | --- | --- | --- |
| Dedicated search | 35 | +15 − 35 | **No** |
| Visible vein 2 blocks away | 3 | +15 + 18 − 3 | **Yes** |

### "Mine 10 because addicted" worked example

Mob has 6 iron (old minimum). Blocking = 0.

| At 6 iron | Utility component |
| --- | --- |
| Reserve utility | 10 |
| Wealth utility | 22 |
| Opportunity bonus | 18 |
| Mining cost | −5 |
| Inventory penalty | −3 |
| **Net** | **positive → continue vein** |

At 14 iron, wealth utility declined; inventory penalty rose → **probably stop**. Greedy mob (`greed=0.95`) may continue.

Connects to [Ore utility](#topic-ore-utility-while-mining) and `DiscoveryMode.VISIBLE` / `NEWLY_EXPOSED`.

### Gen-1 opportunity formula (MI-25 — pairs with D-MIW-026)

```text
acquisitionCost = pathDistance + digPenalty + dangerPenalty   // blocks/ticks normalized 0…40+
opportunityBonus = wealthValue * max(0, 1 - acquisitionCost / detourBudget)

detourBudget = 8 + greed * 12    // 8 blocks minimalist → 20 blocks goblin
```

Mine when `blockingDemand + reserveValue + wealthValue + opportunityBonus - acquisitionCost - inventoryPressure > 0`.
Dedicated expeditions require positive **blocking** or **reserve** shortfall; wealth alone never starts a trip without `MEMORY`/`PROJECT` (D-MIW-008).

---

## Topic: Greed trait

**Status:** `CONSENSUS` for config float; SPM personality map `DEFERRED` (D-MIW-019)

Persistent mob personality — **not boolean**:

```text
greed ∈ [0.0, 1.0]

0.10  Minimalist
0.35  Practical
0.55  Normal accumulator
0.75  Greedy
0.95  Goblin
```

### What greed modifies (wealth only)

| Parameter | Effect |
| --- | --- |
| `wealthMultiplier` | Scales marginal wealth curve |
| `comfortableStockMultiplier` | Higher comfortable band |
| `optionalDetourBudget` | Willingness for side resources |
| `saturationDecay` | How fast utility falls off |
| Inventory discard threshold | Replace low-value slots |

### What greed does NOT modify

```text
Progression: Need 3 diamonds
```

**Identical for every mob.** Greed affects post-minimum wealth only:

| Trait | Comfortable diamonds (example) |
| --- | --- |
| Minimalist (0.10) | 3–5 |
| Collector (0.55) | 12 |
| Goblin (0.95) | 32+ |

**Reject:** `0.95 × diamond` global multiplier → absurd endless obsession.

### Same vein, three mobs

| Trait | Behaviour |
| --- | --- |
| Minimalist | "Already have enough." → leaves |
| Normal | "Could use some more." → mines convenient section |
| Greedy | "IRON." → takes whole accessible vein |

Low AI cost; high personality differentiation. Distinct from mining mode personalities (D-MIW-014 CAVER/TUNNELER).

---

## Topic: Resource wealth profiles

**Status:** `CONSENSUS` shape + **gen-1 v1 constants locked** (D-MIW-021, D-MIW-026)

Every resource needs a profile — prefer **tags/categories** over item-id switches.

```java
public record ResourceWealthProfile(
    float baseValue,
    int comfortableAmount,
    int saturationAmount,
    float scarcityWeight,
    float hoardability,       // slow vs fast saturation curve
    float generalUtility,
    float rarityAppeal
) {}
```

### Gen-1 v1 locked defaults (D-MIW-026 `CONSENSUS`)

**Parity rule:** when `greed == 0` **or** `wealthLevel == 0`, `wealthValue` and `opportunityBonus` are **exactly zero** — behaviour must match today's consumer-only gather (`U-MIW-1`).

**Curve (gen-1):** piecewise linear marginal utility from stock `amount`:

```text
if amount < comfortable:
    wealthFactor = 1.0
else if amount >= saturation:
    wealthFactor = 0.05        // never exactly zero — opportunity can still win
else:
    wealthFactor = 1.0 - 0.95 * (amount - comfortable) / (saturation - comfortable)

wealthValue = baseValue * hoardability * wealthFactor * greed * wealthLevel
              + rarityAppeal * greed * wealthLevel * wealthFactor
```

`minimum` is **never** stored in the profile — it always comes from `ConsumerRecipeSpec` / `WorkDemandPolicy` deficits (D-MIW-004).

| Category | baseValue | comfortable | saturation | hoardability | rarityAppeal | generalUtility |
| --- | --- | --- | --- | --- | --- | --- |
| `LOGS` | 0.35 | 8 | 32 | 0.6 | 0.0 | 0.5 |
| `COAL` | 0.40 | 16 | 64 | 0.7 | 0.0 | 0.6 |
| `COBBLESTONE` | 0.20 | 12 | 48 | 0.5 | 0.0 | 0.4 |
| `IRON` | 0.55 | 12 | 48 | 0.75 | 0.0 | 0.9 |
| `DIAMOND` | 0.90 | 6 | 24 | 0.95 | 0.85 | 0.7 |

**Rationale (conservative):** bands sit modestly above typical craft minimums (iron pick = 3 ingots) so `greed=0.55` + `wealthLevel=1` can mine past 6 without matching the old illustrative 16/64 table. Diamonds keep slow saturation via high `hoardability` + `rarityAppeal`.

**Tuning policy:** change these only with a new decision row + updated `U-MIW-*` bounds — not silent drift.

### Category table (behavioural reference — superseded for implementation by v1 table above)

| Resource | Base | Saturation speed | Behaviour |
| --- | --- | --- | --- |
| Diamond | Very high | Very slow | Almost always interesting |
| Emerald | High | Slow | Wealth / trading |
| Iron | High | Medium | Extremely reusable |
| Gold | Medium-high | Medium | Situational |
| Coal | Medium | Faster | Useful until stocked |
| Logs | Medium | Medium | General-purpose |
| Cobble | Low-medium | Fast | Abundant utility |
| Dirt | Very low | Very fast | Mostly ignore |
| Rotten flesh | Tiny | Very fast | Little appeal |

### Tag-first mod compatibility

```text
#c:ingots/iron
#c:gems/diamond
#minecraft:logs
```

Internal categories: `PRECIOUS`, `METAL`, `FUEL`, `BUILDING`, `FOOD`, `UTILITY`, `JUNK`.

Mod registers: `modid:mythril_ingot → METAL + RARE → high wealth profile` without editing central AI.

Item-specific overrides allowed where justified. Aligns with D-MIW-005 `WealthCapability` SPI.

---

## Topic: Resource portfolio

**Status:** `DEFERRED` gen-1 (D-MIW-022) — ship after NEED/WEALTH split proves useful

Evaluate resources **relative to pack composition**, not in isolation.

```text
Pack: 32 iron, 0 coal, 0 food

marginal iron value ↓
coal / food value ↑
```

→ *"I have tons of iron. I need coal now."*

```java
// Internal to ResourceWealthPolicy or separate ResourcePortfolioPolicy
float portfolioAdjustment(ResourceCategory cat, ResourcePortfolioSnapshot portfolio);
```

Portfolio imbalance shifts marginal utility without new goals. Feeds `WorkDemandPolicy` arbitration.

---

## Topic: Scarcity and consumption

**Status:** scarcity `DEFERRED` gen-1 (D-MIW-023); velocity `DEFERRED` (D-MIW-024)

### Scarcity pressure (D-MIW-023 `PROPOSED` — gen-1 feasible)

Bounded memory:

```text
lastAcquired(resourceCategory)
recentAcquisitionRate
```

Haven't seen iron in a long time → scarcity memory ↑ → when iron finally found: *"take more while I'm here."*

No ML — moving statistic only.

### Consumption velocity (D-MIW-024 `DEFERRED`)

```text
expectedDemand = recentConsumptionRate × planningHorizon
```

High torch placement → coal consumption ↑ → desired coal reserve rises.  
Never used redstone → hoard desire = greed/rarity only.

**Mark later** — promising extension; not Phase 3 / early wealth rollout.

---

## Topic: Mining intelligence

**Status:** `CONSENSUS` skeleton (D-MIW-001); director/project later phases

### Layered architecture (D-MIW-001 `CONSENSUS`, refined)

```text
ProgressGoal / ResourceWealthPolicy     ← WHAT & HOW MUCH (marginal utility)
        ↓
MiningDirector                       ← site selection, project start/stop
        ↓
MiningProject (session)              ← mode, budget, anchors, VeinFrontier
        ↓
GatherIntentPolicy + GatherTargetPolicy  ← legitimate next break target
        ↓
GatherResourcesGoal (DigAction)      ← ONE physical excavation + drop keep
        ↓
TaskLifecycle (RUNNING…INTERRUPTED)
```

| Component | Role | Separate Goal? |
| --- | --- | --- |
| `MiningDirector` | Chooses cave vs tunnel vs return; raises explore pressure | **No** — pure policy |
| `MiningProject` | Session: mode, budget, `origin`, `lastSafeAnchor`, `returnRoute` | **No** — `SavedData` or mob NBT slice |
| `MiningMemory` | Bounded cave/ore/hazard recollections | **No** — policy store |
| `GatherResourcesGoal` | Break loop, protection, drops | **Yes** — existing executor |
| `ExploringGoal` | Travel when director requests opportunity | **Yes** — unchanged resource-agnostic |
| `EnvironmentalEscapeGoal` | Trapped / gravel / suffocation | **Yes** — mining does not duplicate |

**Rejected:** `ExploreForDiamondsGoal`, `DiamondVeinGoal`, `LavaEscapeGoal`, Baritone-style `ActionAwareNavigation` gen-1.

**Deferred:** full `ActionAwareNavigation` (walk / jump / break / place edges).

| Feature | ID | Purpose |
| --- | --- | --- |
| Intent consolidation | MI-1 `IMPLEMENTED` | One immutable snapshot for iron, diamond, coal, cobble, logs |
| Perception legitimacy | MI-13a `IMPLEMENTED` | Ore exposure in pass-one `GatherCandidatePolicy` |
| Wealth curves (policy) | MI-24/25 `IMPLEMENTED` | Profiles + opportunity; gather wire = MI-4 |
| Discovery classification | MI-13 | `DiscoveryMode` enum after MI-4 |
| MiningDirector + project | MI-14 | Session modes, budgets, interrupts |
| MiningMemory | MI-15 | Cave entrances, branches, sightings |
| Vein frontier | MI-16 | Generic `ResourceTarget` vein follow |
| Ore utility scoring | MI-17 | Side-ore detour while blocking search |
| Hazards + durability | MI-18 | Lava, gravel, tool swap, preemptive replace |
| Band gate | MI-2 | No deep-ore intent above Y=16 without sighting |
| Target priority | MI-3 | Ore deficit > distance among **legitimate** candidates |
| Explore downward bias | MI-4 | Deficit + zero local ore → nudge `ExploringGoal` |
| Cave opportunism | MI-5 | Prefer exposed ore in ravine/cave during explore |
| Bounded staircase | MI-6 | Last resort: 1×2 staircase max N blocks, torch check |
| Torch pairing underground | MI-11 | `PlaceTorchGoal` + coal demand loop |
| Search budget | MI-19 | Cap distance/blocks/time per trip |

**Rejected gen-1:** strip mines, chunk carving, dig-to-bedrock, clairvoyant ore map.

---

## Topic: Legitimate ore discovery

### Blocking finding — perception is already clairvoyant; only the policy is honest (`CODE_CONFIRMED`, Agent_Claude, snapshot 20:29)

MI-13 is specified as *"`DiscoveryMode` gate on every target"*, and gen-1 rejects a "clairvoyant ore
map". **The existing scan is already that map**, bounded only by radius.

`GatherResourcesGoal.findTarget` pass one iterates **every** position in a
`(2r+1) × (2r+1) × 9` box — ~3,969 positions at the live radius 10, ~15,129 at radius 20 — calling
`level.getBlockState(pos)` and testing `isCandidate(state)`. For ore, `isCandidate` checks only
**block type + live tool capability**. There is **no exposure test in pass one**. Exposure is applied
in pass two (`isWanted` → `GatherProtection.isGatherableOre` → `isExposedToAir`).

So a mob perceives ore sealed behind solid stone and *declines* to mine it. That is honest at the
decision layer and dishonest at the perception layer, and MI-13 as written inherits the dishonesty:
a discovery gate applied to "every target" still receives a candidate list built from omniscient
perception.

### The consequence is not theoretical — the buffer starves

Pass one keeps only the **`MAX_CANDIDATES` = 24 nearest** candidates, and buried ore competes for
those slots on distance alone. Underground — exactly where deep-ore mining happens — buried ore
vastly outnumbers exposed ore. The failure mode:

```text
24 nearest candidates  →  all buried  →  pass two rejects all 24  →  target = none
   …while a legitimately exposed vein sits at distance 25, never considered
```

**A mob can therefore fail to find visible ore it is standing next to**, and no log line reports it
because "no candidate passed" and "no candidate existed" are indistinguishable today. This is worst
precisely in the diamond band that MI-2 targets.

### What this changes

| Item | Impact |
| --- | --- |
| **MI-13** | Must gate in **pass one**, not "on every target". A `DiscoveryMode` applied after the buffer is filled cannot restore legitimacy the buffer already lost |
| **MI-16 vein frontier** | A frontier built from omniscient neighbours is an X-ray vein follower. Frontier expansion must be seeded from *broken/exposed* faces only |
| **MI-3 target priority** | "Ore deficit > distance among legitimate candidates" is unreachable while legitimacy is decided after the distance cull |
| **MI-19 search budget** | Budget cannot be reasoned about while the candidate set is silently truncated by illegitimate entries |

### Recommended correction (gen-1, cheap)

Move the legitimacy predicate into `isCandidate` for ore — i.e. pass one tests **exposure**, not just
block type. The cost is one `isExposedToAir` per *ore-type match* (rare — ore is sparse), not per
scanned position, so the pass-one budget the current javadoc defends is preserved. Pass two keeps the
expensive `hasBuiltNearby` protection check.

That single move makes the buffer contain only legitimately discoverable ore, at which point MI-13,
MI-16 and MI-3 all become implementable as written, and `DiscoveryMode` becomes a *classification* of
an already-honest set rather than a filter trying to undo an omniscient one.

**Must happen:** the 24-slot buffer contains only ore the mob could legitimately know about.
**Must not happen:** a buried vein displacing an exposed one from the candidate set.

### Scheduled fix — MI-13a (D-MIW-027 `CONSENSUS`)

**Task:** move `GatherProtection.isExposedToAir` (or equivalent) into `isCandidate` for all ore types **before** pass-one distance culling.

**Prerequisite for:** MI-4 gather wealth wire, MI-13 `DiscoveryMode` enum, MI-16 vein frontier, MI-17 ore utility, MI-25 opportunity bonus.

**Diagnostic (same task):** distinguish log/reason codes:
- `NO_CANDIDATES_IN_RADIUS`
- `CANDIDATES_ALL_REJECTED_PROTECTION`
- `CANDIDATES_ALL_BURIED` (pre-13a only — should become unreachable)

**Acceptance (U-MIW-19):** exposed ore at distance 25 must be found when buried ore fills the 24 nearer slots.

**Status:** `CONSENSUS` (D-MIW-008 + D-MIW-027 — Agent_Claude finding accepted)

### Anti-pattern (REJECT)

```text
scan huge radius → locate diamond 40 blocks away → path directly
```

That is **clairvoyant**. The server can query blocks; the mob must not act on undiscovered ore.

### Rule

> **Undiscovered ore behind ordinary solid terrain does not become an exact target merely because the server can query the block.**

### Discovery modes (`DiscoveryMode` enum)

| Mode | Meaning | Valid target? |
| --- | --- | --- |
| `VISIBLE` | Ore exposed to air / cave surface | **YES** |
| `NEWLY_EXPOSED` | Mob's own break revealed adjacent ore | **YES** |
| `MEMORY` | Ore previously seen, not yet mined; return if detour justified | **YES** (bounded) |
| `LOCAL_SEARCH` | Short-range continuation of already-discovered vein | **YES** |
| `LOOT` | Ore in container via SPM loot path | **YES** (SPM owns pickup) |

`GatherTargetPolicy` may only enqueue breaks whose discovery mode ≠ `UNDISCOVERED`.

**Gap (`NOT FOUND`):** `DiscoveryMode` classification in `GatherResourcesGoal` today — scan is local radius (`CODE_CONFIRMED` `gatherSearchRadius`) but treats all hits equally.

---

## Topic: MiningProject modes

**Status:** `CONSENSUS` catalog; gen-1 activates `SURFACE_EXPOSED` + `CAVE_EXPLORATION` only (D-MIW-025)

```java
public enum MiningProjectMode {
    CAVE_EXPLORATION,   // enter/continue cave, unexplored branches
    SURFACE_EXPOSED,    // hillside / ravine outcrop
    TUNNEL_SEARCH,      // controlled search heading when caves dry up
    VEIN_EXTRACTION,    // follow legitimately exposed vein
    TARGETED_RETURN,    // return to MEMORY sighting worth detour
    EMERGENCY_EXIT      // hazard / tool / food — seek safe anchor
}
```

### Cave exploration (preferred first intelligent method)

```text
Need diamonds (BLOCKING or wealth)
  → known cave in MiningMemory?
      YES → enter / continue
      NO  → ExploringGoal pressure ↑
  → follow traversable unexplored branches
  → inspect newly visible surfaces (VISIBLE / NEWLY_EXPOSED)
  → mine useful exposed ores
```

Combines `ExploringGoal` with mining-specific **branch memory**.

### Search tunneling (`TUNNEL_SEARCH`)

When cave opportunities dry up **and** demand still blocking:

```text
Select mining heading
  → dig main tunnel (staircase preferred — D-MIW-009)
  → periodically expose new surfaces
  → inspect legitimately revealed blocks only
  → ore found? → interrupt tunnel → VEIN_EXTRACTION
  → else continue until search budget exhausted
```

**MiningProject owns search intention.** `GatherResourcesGoal` owns each dig.

**Rejected:** random stone breaking forever; vertical suicide shaft without recovery.

### Terminal / interrupt reasons (`MiningProjectEnd`)

| Reason | Lifecycle | Resume? |
| --- | --- | --- |
| `DEMAND_SATISFIED` | `SUCCESS` | No |
| `TOOL_FAILURE` | `INTERRUPTED` | Yes — replacement demand |
| `LOW_FOOD` | `INTERRUPTED` | Yes |
| `INVENTORY_PRESSURE` | `BLOCKED` / `SUCCESS` | Maybe discard low-value |
| `NO_PROGRESS` | `RETRY` | Reposition |
| `HAZARD` | `INTERRUPTED` | Alternate route |
| `SEARCH_BUDGET_EXHAUSTED` | `SUCCESS` partial | Explore elsewhere |
| `COMBAT` | `INTERRUPTED` | Yes — restore tool after |
| `PLAYER_ORDER` | `INTERRUPTED` | SPM command |

### Search budget (D-MIW-010 `CONSENSUS`)

Per trip limits — **performance + believability**:

| Budget | Example cap |
| --- | --- |
| `maxBlocksMined` | 64–128 |
| `maxDistanceExcavated` | 48 blocks |
| `maxTicks` | 2400 (~2 min) |
| `maxFailedBranches` | 3 |

Exhausted → leave / reposition / `ExploringGoal` — not infinite tunnel.

---

## Topic: MiningMemory

**Status:** `PROPOSED` (bounded, coarse — Agent_ChatGPT)

```text
MiningMemory
├── caveEntrances: Queue<BlockPos>              (cap N)
├── exploredBranchRegions: Set<region-id>       (cap N)
├── deadEnds: Queue<region-id>
├── oreSightings: Queue<OreSighting>
├── hazardLocations: Queue<Hazard>
├── lastMiningOrigin: BlockPos
├── lastSafeAnchor: BlockPos
└── knownReturnRoute: Deque<BlockPos> optional coarse
```

**Must not:** store every mined block. Use **chunk-coarse regions** + small significant-point queues.

`OreSighting` in MEMORY may justify `TARGETED_RETURN` only when `utility(detour) > cost` (see [Ore utility](#topic-ore-utility-while-mining)).

---

## Topic: Vein extraction

**Status:** `PROPOSED` (Agent_ChatGPT — not cheating)

Once ore is **legitimately exposed**:

```text
mine exposed block
  → check NEWLY_EXPOSED neighbors
  → matching ResourceTarget?
      YES → enqueue on VeinFrontier
      NO  → vein exhausted → end VEIN_EXTRACTION
```

```java
public record ResourceTarget(ResourceLocation materialKey, TagKey<Block> oreTag) {}

public final class VeinFrontier {
    private final Set<BlockPos> queue;  // cap e.g. 16 — adjacent matching ore only
}
```

**Do not make `DiamondVeinGoal`.** Generic for coal, iron, copper, gold, redstone, lapis, diamond, emerald, quartz; ancient debris and modded tags deferred.

Executor remains `GatherResourcesGoal` breaking the head of `VeinFrontier`.

---

## Topic: Ore utility while mining

**Status:** `CONSENSUS` formula ownership (delegates to `ResourceWealthPolicy`)

While searching for diamond, exposed side ores scored via unified [ResourceUtility](#topic-wealth-system) formula:

```text
ResourceUtility =
    blockingDemand + replacementDemand + projectDemand
  + reserveValue + wealthValue + opportunityBonus
  - acquisitionCost - dangerCost - inventoryPressure
```

**Example:** diamond blocking=100, iron reserve satisfied, iron wealth=15, opportunity at path=18, detour cost=3.

| Sighting | Decision |
| --- | --- |
| Exposed iron, tiny detour (cost≈3) | **Take** — opportunity bonus dominates |
| Coal vein 20 blocks back (cost≈35) | **Ignore** |
| Exposed diamond, blocking=0, wealth+rarity high | **Mine** |
| MEMORY diamond 300 blocks away | **Ignore** — acquisition cost too high |

**Opportunistic wealth:** blocking=0 but `wealthValue + opportunityBonus > acquisitionCost` → mine exposed resource. No dedicated expedition without positive net utility.

---

## Topic: Hazards, tool durability, and switching

**Status:** `CONSENSUS` capability rule (D-MIW-011/012); full hazard suite phased

### Tool capability (D-MIW-011 `CONSENSUS` — aligns D-TTU-012 / live `isCorrectToolForDrops`)

Ask **can harvest**, not **owns iron pick**:

```java
// Today: ToolBox.ownsToolFor → isCorrectToolForDrops (CODE_CONFIRMED)
ToolCapability {
    boolean canHarvest(BlockState);
    float destroySpeed(BlockState);
    int durabilityRemaining();
}
```

Handles looted/modded tools without `if (pick == Items.IRON_PICKAXE)`.

### Durability intelligence

```text
pick durability critical + deep + blocking demand
  → replacement available? → preemptive swap/craft
  → else reduce risky depth → return toward anchor

unexpected break:
  MiningProject = INTERRUPTED (TOOL_LOST)
  → MaterialDemandPolicy replacement
  → resume project
```

### Tool switching while mining

| Block family | Tool |
| --- | --- |
| Stone / ore | Pickaxe |
| Gravel / sand | Shovel if available |
| Wood obstruction | Axe |
| Combat | Weapon → restore pick after |

### Gravel / falling blocks

Local falling-block check before/during break. On fall → invalidate target → re-evaluate. Trapped → `EnvironmentalEscapeGoal` — **mining does not duplicate**.

### Lava / water (D-MIW-012 `CONSENSUS`)

| Hazard | Behaviour |
| --- | --- |
| Lava visible / newly exposed | Stop forward dig; step back; alternate route; bucket later |
| Water flow | Swim through / reroute / ignore if shallow |

**Emergency safety preempts mining** — do not merge into gather goal.

### Returnability (D-MIW-009 `CONSENSUS`)

Before repeated downward dig: can return via walkable route / jumpable steps / known exit? If **no**, do not commit to deep shaft.

`MiningProject` tracks `origin`, `lastSafeAnchor`, `currentDepth`, coarse `knownReturnRoute`.

**Prefer:** natural cave descent + **staircase tunnel**. **Reject:** straight-down 30-block shaft without recovery.

### Torch intelligence (D-MIW-013 `PROPOSED`)

Underground dark path + torches in pack → `PlaceTorchGoal` at sensible intervals (memory: recent placements). Torch depletion → `MaterialDemandPolicy` → coal/charcoal chain. Messy human tunnels OK.

### Inventory pressure underground

Pack nearing full: discard/ignore low-value; preserve tools, food, torches, blocking materials, high-utility ores. Storage/worksite: **deferred**.

### Cobble reserve types (wealth extension)

| Reserve | Purpose |
| --- | --- |
| `CRAFT_RESERVE` | Tool crafting minimum |
| `UTILITY_RESERVE` | Bridge, hazard block, emergency |
| `HOARD` | Wealth comfort |

Generic `WealthPolicy` per material — not one magic `cobbleStockTarget`.

### Mining personalities (deferred — D-MIW-014)

Weights only, no ML: `CAVER`, `TUNNELER`, `GREEDY`, `FOCUSED`, `CAUTIOUS`, `RECKLESS`.

### Diamond pick as capability unlock

```text
Diamond Pick → harvest obsidian → Nether portal path (RFC-VANILLA Tier 4)
```

Prioritize **pick before axe** in consumer graph. Consumer-driven demand decides tools — not automatic full armor milestone.

---

## Topic: MiningDirector and site selection

**Status:** `PROPOSED` (Agent_ChatGPT)

**Do not make `ExploreForDiamondsGoal`.**

```text
Progression: need diamond
  ↓
MiningDirector: need mining opportunity
  ↓
Exploration pressure rises (ExploringGoal hook)
  ↓
Cave / sighting appears → MiningProject.start(mode)
```

### Site selection decision tree

```text
Diamond demand active?
  ├─ Known legitimate MEMORY target worth detour? → TARGETED_RETURN
  ├─ Known promising cave? → CAVE_EXPLORATION
  ├─ Already underground in useful terrain? → LOCAL_SEARCH / VEIN / TUNNEL
  └─ Else → explore for opportunity → then project
```

Clean separation: **progression** names deficit; **director** names expedition; **explore** moves; **gather** digs.

---

## Topic: How humans mine (cave vs dig down)

**Status:** `CONSENSUS` design answer (user question)

> Do they just dig dirt until Y=−1? Or find a cave?

**Answer: neither literally — human-optimal play is cave-first, then branch at target depth.**

| Strategy | Human practice | Mob design |
| --- | --- | --- |
| **Dig straight down / through dirt to Y=−1** | **Avoided** — lava, gravel, caves; Y=−1 is not diamond band in 1.18+ | **REJECT** gen-1 |
| **Find cave / ravine** | **Primary** — exposed ores, less digging, natural paths ([Mining tutorial](https://minecraft.fandom.com/wiki/Tutorials/Mining) — cave exploration, branch mining at optimal Y) | **PREFERRED** MI-5 |
| **Staircase / shaft** | When no cave — controlled descent with torches | **BOUNDED** MI-6 (last resort) |
| **Branch mine at Y≈−59** | Endgame efficiency after reaching depth | **NOT PRACTICAL** gen-1 (griefing + scope) |

### Vanilla depth facts (`SOURCE_CONFIRMED` — 1.21 worldgen)

| Ore | Typical band |
| --- | --- |
| Coal | Surface → Y 0–256 |
| Iron | Y −64 → 72 (peaks ~16) |
| Diamond | Y −64 → 16 (**peak ≈ −59**) |

A surface mob **cannot** satisfy diamond demand by digging dirt to Y=−1; it must **enter the mining band** via cave, ravine, or bounded descent.

### Decision flow (proposed — aligns `MiningDirector`)

```text
Need deep ore (diamond / deepslate iron)?
  │
  ├─ Legitimate target (VISIBLE / MEMORY / LOOT)? ──YES──► mine (GatherResourcesGoal)
  │
  ├─ Known promising cave (MiningMemory)? ──YES──► CAVE_EXPLORATION project
  │
  ├─ In cave/ravine (air volume heuristic)? ──YES──► explore branches + opportunistic ore
  │
  ├─ Y > prospectMaxY? ──YES──► explore bias downward (ExploringGoal hook)
  │
  ├─ Caves dry, demand blocking? ──YES──► TUNNEL_SEARCH (budgeted)
  │
  └─ Still no path? ──► bounded staircase MI-6 OR SEARCH_BUDGET_EXHAUSTED — never infinite dig
```

**Phase 3 (tool-tier)** ships **only** “break diamond ore if in scan” — intelligence in **this RFC** after P3.

---

## Topic: Capabilities

**Status:** `CONFIRMED` refresh (Agent_Cursor 2, 2026-08-08)

### Existing — Social Player Mobs (`CODE_CONFIRMED` reference v0.86.0)

| Capability | SPM goal / system | Reuse for mining? |
| --- | --- | --- |
| Combat | `WeaponAwareAttackGoal`, ranged goals | **YES** — cave mobs |
| Food | `EatFoodGoal`, `HuntForFoodGoal` | **YES** — survival interrupt |
| Loot / wealth pickup | `CollectFloorItemsGoal`, `RaidContainersGoal` | **YES** — not primary ore |
| Crops | `HarvestCropsGoal` | N/A |
| Doors | `DoorOperationGoal`, `PlayerMobDoorGoal` | **YES** — cave doors rare |
| Dig through | `DigThroughGoal` | **NO** — Dungeon Train readout only |
| Follow / social | `FollowLovedOneGoal`, … | Interrupt only |
| Equipment | `ToolBox` pattern in Scavenger | **YES** |

### Existing — Scavenger addon (`CODE_CONFIRMED`)

| Capability | Class | Mining wealth? |
| --- | --- | --- |
| Surface gather + break | `GatherResourcesGoal` | **YES** — core executor |
| Craft chain | `ScavengerCrafting`, `CraftTorchesGoal` | **YES** |
| Smelt | `SmeltAtFurnaceGoal`, `FurnacePolicy` | **YES** |
| Demand arbitration | `WorkDemandPolicy` | **PARTIAL** — smelt only today |
| Explore | `ExploringGoal` | **YES** — travel to caves |
| Escape dig | `EnvironmentalEscapeGoal` | **YES** — not mining |
| Place torch | `PlaceTorchGoal` | **PARTIAL** — not tied to mine shaft |
| Progress stubs | `ProgressGoal`, `TaskLifecycle` | Planner hooks only |

### Missing for mining endgame

| Capability | Feasibility | Notes |
| --- | --- | --- |
| Perception legitimacy (ore exposure in pass one) | **BUG** `CODE_CONFIRMED` | MI-13a — buried ore starves buffer |
| `DiscoveryMode` classification | **FULL** | MI-13 after MI-13a |
| `MiningDirector` / `MiningProject` | **PARTIAL** | MI-14 session state |
| `MiningMemory` | **PARTIAL** | MI-15 bounded store |
| `VeinFrontier` | **FULL** | MI-16 generic tags |
| Ore utility scoring | **FULL** | MI-17 policy |
| Depth navigation / cave seek | **PARTIAL** | MI-4…MI-6, MI-14 |
| `ResourceWealthPolicy` | **PARTIAL** | MI-3, MI-23…MI-27 |
| Marginal utility curves | **FULL** | MI-24 profiles |
| `OpportunityBonus` | **FULL** | MI-25 + MI-17 |
| Greed trait | **PARTIAL** | MI-26 config; SPM hook deferred |
| Resource portfolio | **PARTIAL** | MI-27 |
| Scarcity memory | **PARTIAL** | MI-28 |
| Consumption velocity | **DEFERRED** | D-MIW-024 |
| `RequirementResolver` | **PARTIAL** | Bounded catalog |
| Tool durability preempt | **PARTIAL** | MI-18 |
| Lava/water bucket mining | **REQUIRES API** | Bucket place/use goal |
| Action-aware pathmaking | **DEFERRED** | Not Baritone gen-1 |
| Branch mining | **NOT PRACTICAL** | Griefing |
| Nether mining | **NOT PRACTICAL** | Portal RFC |
| Mining personalities | **DEFERRED** | D-MIW-014 weights |
| Structure/boss mining | **NOT PRACTICAL** | Stronghold, etc. |

---

## Topic: Integration methods

**Status:** `CONSENSUS` ladder; SPI `DEFERRED` (D-MIW-005)

| Capability | Preferred method | Fallback | Avoid |
| --- | --- | --- | --- |
| Ore gather | Scavenger `GatherResourcesGoal` extension | — | New scan goal |
| Wealth targets | `ResourceWealthPolicy` + `ResourceWealthProfile` | Transitional `WealthTarget` | Per-ore `*GreedPolicy` classes |
| Recipe edges | `RecipeManager` + `ConsumerRecipeSpec` | — | Copied JSON tables |
| Tool gates | `ToolBox.ownsToolFor` live check | — | `ToolTier` ordinal only |
| Cave seek | `MiningDirector` + `ExploringGoal` bias hook | Memory return | Omniscient ore map |
| Vein follow | `VeinFrontier` inside gather | — | Per-ore goals |
| Discovery gate | `DiscoveryMode` on `GatherTargetPolicy` | — | Long-radius ore scan |
| Machine processing | Existing furnace goal | — | Fake GUI |
| Modded ores (future) | Tag `#c:ores` + capability SPI | Compat addon | Item ID lists |

### Capability SPI (future, D-MIW-005 `PROPOSED`)

```text
ProcessingRecipeCapability  — smelt/blast/smoker resolution
ToolCapability              — harvest gate per block
WealthCapability            — mod-specific profiles + category registration
InteractableCapability      — levers, buttons (deferred)
```

Gen-1 uses **Java policy records** only; SPI when a second mod needs hooks.

---

## Topic: Task lifecycle

**Status:** `CONSENSUS` (stub exists)

| State | Meaning | Mining example |
| --- | --- | --- |
| `RUNNING` | Executor active | `MiningProject` TUNNEL_SEARCH + breaking stone |
| `SUCCESS` | Need satisfied | Ore in pack; `DEMAND_SATISFIED` |
| `FAILURE` | Unrecoverable this tick | Protected block |
| `BLOCKED` | Missing prerequisite | No pick for ore |
| `INTERRUPTED` | Combat/food/tool preempt | Creeper; pick broke at 4% |
| `RETRY` | Backoff then re-plan | Path failed; new approach |

`MiningProject` persists across `INTERRUPTED` (combat, `TOOL_LOST`, `HAZARD`) and resumes same mode when safe.

**Must happen:** interrupt preserves `ResourceWealthContext` / `ProgressGoal` intent.  
**Must not:** duplicate ore on resume.

---

## Topic: Phased implementation plan

**Status:** `CONSENSUS` order (Agent_Cursor 2)

| Phase | Deliverable | Depends | Feasibility |
| --- | --- | --- | --- |
| **P0** | Tool-tier Phase 3 (break diamond + craft) | TT-2 | **`CODE_CONFIRMED` done in source** (sibling RFC text may lag) |
| **P1** | `GatherIntentPolicy` (MI-1) | P0 | **`IMPLEMENTED`** |
| **P2** | `ResourceWealthPolicy` need layers (MI-3, MI-23) | P1 | **`IMPLEMENTED`** (NEED; wealth curves MI-24) |
| **P3a** | Perception legitimacy — exposure in pass-one (MI-13a) | P1 | **`IMPLEMENTED`** |
| **P2b** | Marginal curves + profiles (MI-24) | P2, P3a | **`IMPLEMENTED`** (policy) |
| **P2c** | `OpportunityBonus` + config greed (MI-25, MI-26) | P2b | **`IMPLEMENTED`** — formula, config, and gather wire; SPM trait deferred |
| **P2d** | Portfolio + scarcity (MI-27, MI-28) | P2c | **DEFERRED** gen-1 |
| **P3** | Target priority + deepen Y gate (MI-2) | P1, P3a | **READY** after MI-4 or parallel design |
| **P4** | Wire wealth into gather (MI-4) | P2b, P3a | **`IMPLEMENTED`** — MI-4R candidate-aware repair (task 13) |
| **P5** | Explore downward bias (MI-5) | P3 | **PARTIAL** |
| **P6** | Cave opportunism + `MiningMemory` (MI-5, MI-15) | P5 | **PARTIAL** |
| **P7** | Bounded staircase (MI-6) | P5 | **PARTIAL** |
| **P7b** | `DiscoveryMode` gate (MI-13) | P3 | **FULL** |
| **P8** | `MiningDirector` + `MiningProject` (MI-14) | P6, P7b | **PARTIAL** |
| **P9** | `VeinFrontier` + ore utility (MI-16, MI-17, MI-21) | P8 | **FULL** |
| **P10** | Hazards + durability + tool switch (MI-18, MI-20) | P8 | **PARTIAL** |
| **P11** | Search budget + abandon reasons (MI-19) | P8 | **FULL** |
| **P12** | `RequirementResolver` v1 (MI-8) | P4, P9 | **PARTIAL** |
| **P13** | Unit tests + runtime datapack (MI-9, MI-10) | P4 | **FULL** |
| **P14** | Torch-gated shaft lighting (MI-11) | P7 | **PARTIAL** |
| **P15** | Cross-RFC vanilla resolver merge (MI-12) | `RFC-VANILLA` | **PARTIAL** |
| **P16** | Mining personalities (MI-22, deferred) | P9 | **DEFERRED** |
| **P17** | Nether/deepslate branch | Portal RFC | **NOT PRACTICAL** |

### Gen-1 slice (D-MIW-025 `CONSENSUS`, revised)

```text
DONE:     MI-1 → MI-3/23 → MI-13a → MI-24/25 (policy)
DONE:     MI-4R (candidate cost + resource plausibility + regressions)
NEXT:     MI-13 DiscoveryMode enum → MI-2 priority → MI-9/MI-10
DEFER:    MI-26 SPM trait, MI-27/28 portfolio/scarcity, MI-14 director, vein, staircase, personalities
```

`greed=0` / `wealthLevel=0` must reproduce today's exact-consumer behaviour (must-not regress iron/diamond craft).

### MI-4 acceptance (must / must-not)

**Must happen**
- `ScavengerConfig.greed` + `wealthLevel` (defaults **0**) + Cloth UI
- When both are 0: gather matches consumer-only intent (no opportunistic wealth mining)
- When wealth enabled: exposed ore with `netUtility > 0` may extend beyond craft deficit without inventing a stock target
- Consumer `ConsumerRecipeSpec` / `GatherIntentPolicy` NEED remains the blocking source of truth

**Must not happen**
- Reintroduce `wealthRawIron` / `ironStockTarget` push knobs
- Wealth intent that bypasses MI-13a exposure / tool / protection gates
- Smelt demand invented solely for wealth hoarding

### MI-4 implementation audit and repair — `IMPLEMENTED` (Agent_Codex)

`CODE_CONFIRMED` defects:

1. `GatherIntentPolicy.wealthWants` calls `evaluateWealth(..., 0.0F)`. This turns opportunity into a
   global resource flag before a candidate block or distance exists. With the 0.05 saturation floor,
   every mapped category remains positive indefinitely when wealth is enabled.
2. The wealth loop adds `DIAMOND` independently of `diamondDeficit(..., mobBlockY)`, so a surface mob
   can resume impossible diamond scans despite the Y≤16 safety boundary.
3. `LOGS` stock counts only `Items.OAK_LOG`; other log species are invisible to wealth accounting.

Mandatory repair alternatives:

| Option | Design | Trade-off |
| --- | --- | --- |
| **A — candidate-scored wealth (recommended)** | Intent exposes wealth eligibility, but `GatherResourcesGoal` evaluates actual candidate distance/cost and plausibility before admission | Correct ownership and opportunity semantics; requires a small target-policy seam |
| B — bounded category thresholds in intent | Stop wealth flags at saturation and keep distance out | Smaller patch, but contradicts the nonzero-floor/opportunity design and recreates hard targets |

MI-4R acceptance:

- **Must happen:** a nearby exposed eligible ore may pass positive wealth utility after actual
  distance/cost; log holdings count the relevant log tag/category.
- **Must not happen:** wealth adds surface diamond intent, saturated stock causes perpetual global
  scans, buried/protected/incapable ore bypasses existing gates, or default-zero behavior changes.
- Add explicit regressions for surface diamond with wealth enabled, high-stock scan cessation,
  non-oak logs, and candidate distance changing the decision.

Implementation evidence: task 13 separates NEED from wealth contexts, gates wealth-only scan
activation at a conservative normalized discovery cost, scores actual candidates by distance,
counts logs through `ItemTags.LOGS`, and excludes diamond wealth context above the established Y=16
boundary. Focused tests and `gradlew.bat clean build` pass (148/148); runtime remains `UNVERIFIED`.

### MI-13 + MI-2 implementation — `IMPLEMENTED` (Agent_Cursor, task 14)

- `DiscoveryMode` + `DiscoveryPolicy` classify pass-one candidates; buried ore is `UNDISCOVERED`.
- `GatherTargetPolicy` sorts the 24-slot buffer before path probes: blocking need (tier 100) beats
  wealth (tier 50); `NEWLY_EXPOSED` adjacent ore gets +5 within 40 ticks of a harvest.
- `GatherResourcesGoal` records `lastHarvest` after each break for vein-follow classification.
- Seven new unit tests; full suite **155/155** (`CONFIRMED` via `gradlew.bat test`).
- Runtime behaviour, F-2 progression-demand split, and `VeinFrontier` remain `UNVERIFIED` / deferred.

### Task IDs

| Task | Phase | Objective | Status |
| --- | --- | --- | --- |
| MI-1 | P1 | `GatherIntentPolicy` — one intent from need layers + existing deficits | `IMPLEMENTED` (unit/build; runtime `UNVERIFIED`) |
| MI-2 | P3 | `GatherTargetPolicy` + band/priority (extend Y≤16 gate) | `IMPLEMENTED` — priority sort + blocking>wealth; F-2 band split deferred |
| MI-3 | P2 | `ResourceWealthPolicy` need layers | `IMPLEMENTED` (task 10) |
| MI-4 | P4 | Gather + config wire wealth without replacing consumer specs | `IMPLEMENTED` — MI-4R/task 13; runtime `UNVERIFIED` |
| MI-5 | P5 | Explore downward bias | `BLOCKED` |
| MI-6 | P6 | Cave opportunistic ore | `BLOCKED` |
| MI-7 | P7 | Bounded staircase | `BLOCKED` |
| MI-8 | P12 | `RequirementResolver` v1 | `BLOCKED` |
| MI-9 | P13 | Unit tests U-MIW-* | `PARTIAL` — MI-13/MI-2 policy tests added; full U-MIW matrix open |
| MI-10 | P13 | Runtime datapack | `BLOCKED` |
| MI-11 | P14 | Shaft torch pairing | `BLOCKED` |
| MI-12 | P15 | Vanilla RFC integration | `BLOCKED` |
| MI-13a | P3a | Exposure in pass-one for ore | `IMPLEMENTED` (task 11) |
| MI-13 | P7b | `DiscoveryMode` classification enum + diagnostics | `IMPLEMENTED` (task 14) |
| MI-14 | P8 | `MiningDirector` + `MiningProject` | `BLOCKED` |
| MI-15 | P6 | `MiningMemory` store | `BLOCKED` |
| MI-16 | P9 | `VeinFrontier` + `ResourceTarget` | `BLOCKED` |
| MI-17 | P9 | Ore utility scoring | `BLOCKED` |
| MI-18 | P10 | Hazards, durability, tool switch | `BLOCKED` |
| MI-19 | P11 | Search budget + terminal reasons | `BLOCKED` |
| MI-20 | P10 | Cobble reserve types (`CRAFT`/`UTILITY`/`HOARD`) | `BLOCKED` |
| MI-21 | P9 | Opportunistic wealth while blocking=0 | `BLOCKED` on MI-4 |
| MI-22 | P16 | Mining personalities (deferred weights) | `DEFERRED` |
| MI-23 | P2 | Need layers | `IMPLEMENTED` for NEED layers |
| MI-24 | P2b | `ResourceWealthProfile` + marginal curves | `IMPLEMENTED` (task 12) |
| MI-25 | P2c | `OpportunityBonus` wired to gather | `IMPLEMENTED` — MI-4R candidate cost |
| MI-26 | P2c | Greed config (+ SPM hook when available) | Config with MI-4; SPM hook `DEFERRED` |
| MI-27 | P2d | `ResourcePortfolio` imbalance adjustment | `DEFERRED` gen-1 |
| MI-28 | P2d | Scarcity pressure memory | `DEFERRED` gen-1 |

---

## Topic: Validation

### Unit tests (`PROPOSED`)

| ID | Must happen | Must not |
| --- | --- | --- |
| U-MIW-1 | `greed=0`, blocking only → consumer minimum | Extra wealth mining |
| U-MIW-2 | `greed=0.75`, iron at comfortable → no expedition | Dedicated iron trip |
| U-MIW-3 | Craft before wealth mining | Greed blocks craft |
| U-MIW-4 | Y>16, diamond need → no diamond intent | Eternal scan |
| U-MIW-5 | Ravine + exposed ore → mine | Stone pick on diamond |
| U-MIW-6 | Cave bias prefers air-linked path | Dig through 50 dirt |
| U-MIW-7 | INTERRUPTED combat → resume same wealth target | Lost intent |
| U-MIW-8 | Buried ore in scan radius, not exposed → not targeted | Clairvoyant path |
| U-MIW-9 | NEWLY_EXPOSED neighbor enqueues `VeinFrontier` | Scan through walls |
| U-MIW-10 | Side iron utility > detour while diamond blocking | Ignore all side ore |
| U-MIW-11 | Pick 4% durability → preemptive replace or shallow | Break and strand |
| U-MIW-12 | `SEARCH_BUDGET_EXHAUSTED` ends tunnel | Infinite dig |
| U-MIW-13 | MEMORY diamond 300 blocks, hoard only → no return | Clairvoyant travel |
| U-MIW-14 | 10 iron, blocking=0, exposed vein cost=3 → mine | Ignore at "target" |
| U-MIW-15 | 14 iron, same vein, inventory pressure high → stop | Mine forever |
| U-MIW-16 | `greed=0.10` vs `0.95` same vein → different take amount | Identical behaviour |
| U-MIW-17 | 32 iron + 0 coal portfolio → coal utility ↑ vs iron | Ignore coal need |
| U-MIW-18 | Long scarcity + exposed iron → higher take than stocked | Flat wealth |
| U-MIW-19 | Exposed ore beyond 24 buried slots → still targeted after MI-13a | Buffer starvation |
| U-MIW-20 | `greed=0` → `wealthValue=0` at any stock | Wealth mining |
| U-MIW-21 | Iron at saturation (48+) → wealthFactor≈0.05 unless opportunity high | Flat high stock |

### Runtime stages (`PROPOSED`)

| Stage | Setup | Must happen |
| --- | --- | --- |
| RT-MW-1 | 6 iron + greedy mob + exposed vein | Mines past 6, stops by utility |
| RT-MW-8 | Minimalist vs Goblin same vein | Different take amounts |
| RT-MW-9 | Iron-rich, coal-poor pack in cave | Prioritizes coal opportunistically |
| RT-MW-2 | Cave datapack diamond exposed | Crafts diamond pick |
| RT-MW-3 | Surface only, diamond cap | Idles (no eternal scan) |
| RT-MW-4 | Combat interrupt mid-mine | Resumes after kill/flee |
| RT-MW-5 | Buried diamond behind stone | No path to hidden ore |
| RT-MW-6 | Exposed vein, break one block | Follows adjacent ore only |
| RT-MW-7 | Pick breaks mid-tunnel | Replacement demand + resume |

Datapack: `test-datapacks/phase-mining-wealth/`.

---

## Topic: Deferred and unverified

| Item | Label | Unblock |
| --- | --- | --- |
| Branch mining at Y=−59 | **NOT PRACTICAL** gen-1 | Product + griefing review |
| Clairvoyant ore scan | **REJECTED** | D-MIW-008 |
| `ActionAwareNavigation` / Baritone clone | **DEFERRED** | Product |
| Mining personalities (CAVER, TUNNELER, …) | **DEFERRED** | D-MIW-014 |
| Nether ancient debris | **NOT PRACTICAL** | Portal RFC |
| Consumption velocity reserves | **DEFERRED** | D-MIW-024 |
| Hard `ironStockTarget` only | **SUPERSEDED** | D-MIW-017 / FS-8 consumer demand |
| `wealthRawIron` push config | **REJECTED** | D-MIW-004 — recreates hoard without consumer |
| Portfolio / scarcity gen-1 | **DEFERRED** | D-MIW-022/023 after MI-1…MI-3 |
| SPM disposition → greed | **DEFERRED** | Trait API `NOT FOUND` in SPM v0.86.0 |
| Full dragon endgame | **NOT PRACTICAL** | `RFC-VANILLA` |
| Modpack ores (Create, etc.) | **DEFERRED** | Per-mod capability SPI |

---

## Decision Registry

| ID | Title | Status | Summary |
| --- | --- | --- | --- |
| D-MIW-001 | Mining inside gather | `CONSENSUS` | Physical dig in `GatherResourcesGoal`; director/project are policy |
| D-MIW-002 | Bounded resolver | `CONSENSUS` | Finite catalog, no HTN gen-1 |
| D-MIW-003 | Wealth prioritization | `CONSENSUS` | Survival > blocking > reserve > wealth |
| D-MIW-004 | Config-first wealth | `CONSENSUS` | Config greed/`wealthLevel`; **no** `wealthRawIron` push |
| D-MIW-005 | Capability SPI | `DEFERRED` | Until second mod consumer |
| D-MIW-006 | Cave-first mining | `CONSENSUS` | Not dig-to-Y=−1 |
| D-MIW-007 | Phase boundary | `CONSENSUS` | Tool-tier P3 = break+craft only (code present) |
| D-MIW-008 | Legitimate discovery | `CONSENSUS` | No clairvoyant ore targeting |
| D-MIW-009 | Returnability | `CONSENSUS` | Staircase / cave descent; no suicide shaft |
| D-MIW-010 | Search budget | `CONSENSUS` | Cap blocks/distance/time per trip |
| D-MIW-011 | Tool capability | `CONSENSUS` | Live harvest check, not item-id lists |
| D-MIW-012 | Hazard preempt | `CONSENSUS` | Lava/water stops dig; escape separate |
| D-MIW-013 | Torch loop | `PROPOSED` | Underground placement → coal demand |
| D-MIW-014 | Mining personalities | `DEFERRED` | Weight presets only, no ML |
| D-MIW-015 | Separate NEED from WEALTH | `CONSENSUS` | Five value layers; foundation |
| D-MIW-016 | `ResourceWealthPolicy` | `CONSENSUS` | Generic policy; not per-ore classes |
| D-MIW-017 | Marginal utility curves | `CONSENSUS` | minimum/comfortable/saturation bands |
| D-MIW-018 | Opportunity bonus | `CONSENSUS` | Cheap local acquisition enables wealth |
| D-MIW-019 | Greed trait | `CONSENSUS` config / SPM `DEFERRED` | Wealth params only |
| D-MIW-020 | Reserve vs wealth | `CONSENSUS` | Expected need vs desirable hoard |
| D-MIW-021 | Tag/category profiles | `CONSENSUS` | Data-driven shape; mod SPI later |
| D-MIW-022 | Resource portfolio | `DEFERRED` gen-1 | Pack imbalance |
| D-MIW-023 | Scarcity pressure | `DEFERRED` gen-1 | Bounded last-acquired memory |
| D-MIW-024 | Consumption velocity | `DEFERRED` | Adaptive reserves from use rate |
| D-MIW-025 | Gen-1 slice | `CONSENSUS` | MI-13a→MI-24→MI-25→MI-4R; director later |
| D-MIW-026 | Gen-1 profile v1 | `CONSENSUS` | Locked constants + marginal curve; parity at greed=0 |
| D-MIW-027 | Perception legitimacy | `CONSENSUS` | Exposure in `isCandidate` before MI-13/24/25 |

---

## Gates (MRFC-1)

### Research Gate

- [x] Current Scavenger gather/craft/smelt/demand inspected (`CODE_CONFIRMED`)
- [x] SPM mining probe — Dungeon Train readout only; no general mining (`CODE_CONFIRMED` prior)
- [x] P0 diamond craft/break presence rechecked (`MAKE_DIAMOND_*`, `diamondDeficit`, `wantsDiamond`)
- [x] Negative: `wealthRawIron`, `MiningDirector` `NOT FOUND` in `src/main`
- [x] `ResourceWealthPolicy` / `GatherCandidatePolicy` / `GatherIntentPolicy` present (`CODE_CONFIRMED`)
- [ ] Runtime mining/wealth behaviour (launch not authorized)

### Architecture Gate

- [x] Director/project vs dig ownership compared (D-MIW-001)
- [x] NEED vs WEALTH compared and accepted (D-MIW-015–020)
- [x] Anti-clairvoyance accepted (D-MIW-008)
- [x] Gen-1 slice narrowed (D-MIW-025, revised)
- [x] Perception legitimacy scheduled and implemented (D-MIW-027 / MI-13a)
- [x] Wealth profile v1 locked (D-MIW-026)
- [x] Client/server: policy server-side with gather goal
- [ ] Full interruption recovery for MiningProject (design present; code absent)

### Parity Gate

- [x] Progression node table covers wood→diamond mining path
- [ ] Scenario parity runtime evidence
- [x] Exact-consumer parity preserved when `wealthLevel=0` (policy + gather-intent unit)

### Implementation Gate

- [x] MI tasks dependency-ordered
- [x] MI-1 authorized and implemented (`GatherIntentPolicy`; task 9)
- [x] MI-3/MI-23 NEED allocation implemented (`ResourceWealthPolicy`; task 10)
- [x] MI-13a perception fix implemented (task 11)
- [x] MI-24/MI-25 policy curves + opportunity implemented (task 12)
- [x] **MI-4R** gather wealth repair (task 13)
- [x] U-MIW gather-wealth tests green with MI-4R (148-test full suite)

### Runtime Gate

- [ ] Approved launch + RT matrix for mining/wealth
- [ ] Dedicated-server smoke

**MRFC-1 status:** **PASS (continuation)** — policy stack through MI-4R implemented and statically verified; runtime `UNVERIFIED`.

---

## User approval

- [x] **Lock D-MIW-025** gen-1 slice (revised — MI-13a first)
- [x] **Lock D-MIW-026** gen-1 profile v1 constants
- [x] **Accept D-MIW-027** — MI-13a perception prerequisite (Agent_Claude)
- [x] **MI-1** — `GatherIntentPolicy` + unit tests
- [x] **MI-3 / MI-23** — NEED layers
- [x] **MI-13a** — exposure in pass-one
- [x] **MI-24 / MI-25 policy** — marginal curves + opportunity formula
- [x] **Authorize MI-4R** — implemented and statically verified as task 13
- [ ] Runtime launch (separate)

---

## Contribution Archive

### Contribution — Agent_Codex (MI-1 implementation)

**Agent:** Agent_Codex
**Date/Session:** 2026-08-08
**Contribution type:** `IMPLEMENTATION / VALIDATION`

**Frontier before:** MI-1 was the first accepted dependency-ready task and blocked all gen-1 wealth work.

**Action:** Added pure immutable `GatherIntentPolicy`, integrated one snapshot into
`GatherResourcesGoal`, and added focused tests. The aggregate can contain simultaneous resources;
exclusive selection was rejected because MI-2 owns prioritization. RED failed on the missing class;
GREEN passed 4 focused tests and a clean build passed 128 tests. Artifact SHA-256:
`904C10BA1FA345A9CC0636CB726E300416FD6545BD1E85A43D3E66E73A895184`.
Full evidence: `.superpowers/sdd/task-9-report.md`.

**Frontier after:** MI-1 is `IMPLEMENTED` with runtime `UNVERIFIED`; MI-3/MI-23 are now the nearest
dependency-ready slice. MI-13 remains downstream and owns the pass-one buried-ore legitimacy repair.

---

| Date | Agent | Change |
| --- | --- | --- |
| 2026-08-08 | Agent_Claude | Verified the user's five wealth objections: F-1 scale mismatch confirmed numerically (iron max net +0.454 at zero cost, −2.59 at cost 3; examples assume 15/18/3), F-4 confirmed from MI-4's deliberate zero-cost workaround, F-6 quantified (no stagger, ~3,969–15,129 positions/scan/mob); F-2/F-3/F-5 endorsed; MI-4 marked implemented-but-not-accepted pending F-1 |
| 2026-08-08 | Agent_Cursor | Continue RFC: reconciled MI-13a/24/25 done; MI-4 → `READY` with acceptance tests; gates/approval fixed; no Java |
| 2026-08-08 | Agent_Cursor | Continue RFC: D-MIW-026 profile lock, D-MIW-027/MI-13a perception prerequisite, revised gen-1 slice |
| 2026-08-08 | Agent_Claude | Perception-legitimacy finding → promoted D-MIW-027 |
| 2026-08-08 | Agent_Cursor 2 | P0 diamond evidence; unlocked false Phase-3 block; promoted D-MIW-002/003/004/010/011/016/017/019/021/025; deferred portfolio/scarcity/SPM greed; MI-1 READY; Gates + approval |
| 2026-08-08 | Agent_ChatGPT | NEED vs WEALTH foundation, `ResourceWealthPolicy`, marginal utility, opportunity bonus, greed trait, profiles, portfolio, scarcity |
| 2026-08-08 | Agent_Cursor | Integrated wealth architecture; MI-23…MI-28, D-MIW-015…024 |
| 2026-08-08 | Agent_ChatGPT | Legitimate discovery, MiningProject modes, MiningMemory, vein frontier, ore utility, hazards, director, search budget |
| 2026-08-08 | Agent_Cursor | Integrated Agent_ChatGPT mining architecture; expanded MI-13…MI-22, D-MIW-008…014 |
| 2026-08-08 | Agent_Cursor | Renamed from resource-greed RFC; full mining+wealth RFC; user deliverable template; cave-vs-dig answer |
| 2026-08-08 | Agent_Cursor | Initial greed/mining split from tool-tier Phase 3 |

### Contribution — Agent_Claude (utility scale verification)

Agent: Agent_Claude
Date/Session: 2026-08-08 (snapshot 21:31)
Contribution type: REVIEW + VALIDATION

Reviewed: five design objections raised by the user against the locked wealth model, after
implementing MI-4.

**F-1 verified numerically, and it is worse than described.** I executed the shipped
`wealthValue`/`opportunityBonus` formulas against the locked gen-1 iron profile at the recommended
`greed = 0.55`. Maximum achievable net utility is **+0.454 at zero cost**; at the RFC's own
exposed-vein cost of 3 it is **−2.593**. Diamond at maximum greed survives only to cost ≈ 5. The
worked example assumes wealth 15 / opportunity 18 / cost 3. The constants and the examples are two
different systems, and only the examples describe the intended behaviour.

**This lands on code I merged.** MI-4 passes acquisition cost `0.0F`, which is the only reason its
wealth path fires at all; the newer distance-aware `wants(resource, cost)` already asserts wealth is
dead at cost 3. I have marked MI-4 implemented-but-not-accepted pending F-1.

**F-4 confirmed from my own implementation.** I passed zero cost *deliberately* to avoid double-
counting distance once the candidate scorer applies it too. That workaround is the missing boundary
asking to exist, and MI-17 will collide with it.

**F-6 quantified.** No per-mob stagger exists (`NOT FOUND`); the scan is synchronous and visits
~3,969 positions at the live radius 10, ~15,129 at radius 20, every 60 ticks per interested mob,
with `MAX_PATH_PROBES` 3. The existing budget bounds only the trip after a target is chosen.

Agreement: F-2, F-3 and F-5 are all correct as stated. F-2 in particular is a consequence of the
Phase 3 plausibility gate I implemented — that gate was right for suppressing surface scans and wrong
as the *only* diamond signal; the two-signal split resolves it without reintroducing the permanent
scan.

Concerns: F-1 is a normalisation decision with product consequences (how greedy should a mob look),
not something an agent should pick unilaterally. Tuning individual constants around the mismatch
would hide it.

Recommendation: resolve F-1 before any further wealth work; F-2 before MI-5/MI-14; F-6 before
anything that raises scan frequency.

RFC fields updated: new Topic: Utility scale and policy boundaries (F-1…F-6 with disposition),
Change Log, this contribution.

---

### Contribution — Agent_Claude (perception legitimacy)

Agent: Agent_Claude
Date/Session: 2026-08-08 (snapshot 20:29)
Contribution type: RESEARCH + OBJECTION

Reviewed: the mining-intelligence layered architecture, MI-13/MI-16/MI-3/MI-19, and the "no
clairvoyant ore map" rejection, against the scan those layers will sit on. I implemented the Phase 3
diamond slice this session, so the code below is freshly verified rather than recalled.

**Finding — the clairvoyant ore map the RFC rejects already exists.** `findTarget` pass one visits
every position in a `(2r+1)²×9` box and tests only block type plus tool capability; exposure is not
consulted until pass two. Perception is omniscient within the radius; only the decision is honest.
MI-13 gating "every target" therefore filters a list that was already built dishonestly.

**Consequence — candidate starvation.** Pass one keeps the 24 nearest candidates and buried ore
competes purely on distance. Underground, buried ore vastly outnumbers exposed ore, so all 24 slots
can fill with ore pass two will reject, while a legitimately exposed vein just outside that set is
never considered. The mob then finds nothing — indistinguishably from there being nothing. This is
worst in the Y≤16 band MI-2 targets.

**Recommended correction:** move the exposure predicate into `isCandidate` for ore. Cost is one
`isExposedToAir` per ore-type match, not per scanned position, so pass one's budget survives. After
that, `DiscoveryMode` classifies an already-legitimate set instead of trying to undo an omniscient
one, and MI-3/MI-16/MI-19 become implementable as specified.

Agreement: the layering (`MiningDirector` / `MiningProject` as policy, dig staying in
`GatherResourcesGoal`) is right, and matches how the tool-tier and furnace work landed. Separating
NEED from WEALTH (D-MIW-015) is the correct decomposition and I have no objection to the wealth
model.

Concerns: this is a **prerequisite** for MI-13/16/3/19, not a parallel task. Implementing them on the
current scan produces a system whose legitimacy claim is false at the perception layer, which is
harder to unwind later than to fix now while the scan has one ore consumer per tier.

Recommendation: schedule the `isCandidate` correction ahead of MI-13, and add a diagnostic that
distinguishes "no candidate existed" from "all candidates rejected" — today they are the same
silence, which is why this was invisible.

RFC fields updated: Topic: Legitimate ore discovery (blocking finding, impact table, recommended
correction), Change Log, this contribution.

---

### Contribution — Agent_ChatGPT (NEED vs WEALTH foundation)

**Agent:** Agent_ChatGPT  
**Date/Session:** 2026-08-08  
**Contribution type:** `DESIGN`

**User request:** Add separate NEED from WEALTH architecture — marginal utility, opportunity bonus, greed trait, resource profiles, portfolio imbalance, scarcity.

**Key proposals:**

- **Five value layers:** immediate need, replacement need, working reserve, project demand, wealth desire (D-MIW-015).
- **`ResourceWealthPolicy`:** generic evaluator — not per-ore greed classes (D-MIW-016).
- **Marginal utility:** diminishing curves; minimum/comfortable/saturation bands — no hard `target=6 → worthless` (D-MIW-017).
- **`OpportunityBonus`:** local cheap acquisition makes wealth worthwhile — "found it anyway" behaviour (D-MIW-018).
- **Greed trait** `∈ [0.0, 1.0]`: modifies wealth params only — progression minimums identical for all mobs (D-MIW-019).
- **Reserve vs wealth** kept distinct (D-MIW-020).
- **`ResourceWealthProfile`:** tag/category-first; rarity appeal; mod registration (D-MIW-021).
- **`ResourcePortfolio`:** pack imbalance shifts marginal value (D-MIW-022).
- **Scarcity pressure:** bounded `lastAcquired` memory (D-MIW-023).
- **Consumption velocity:** deferred adaptive reserves (D-MIW-024).

**Architecture fit:** Supersedes flat `WealthTarget` comfort integers as end state; transitional compat preserved. Ore utility and mining intelligence consume unified `ResourceUtility` formula.

**Not authorized:** Implementation.

### Contribution — Agent_ChatGPT (mining intelligence architecture)

**Agent:** Agent_ChatGPT  
**Date/Session:** 2026-08-08  
**Contribution type:** `DESIGN`

**User request:** Add legitimate ore discovery, mining modes, memory, vein following, tool/durability/hazard intelligence, and player-like mining behaviour to Mining Intelligence RFC.

**Key proposals:**

- **Anti-clairvoyance:** `VISIBLE`, `NEWLY_EXPOSED`, `MEMORY`, `LOCAL_SEARCH`, `LOOT` — never target buried ore from server query alone (D-MIW-008).
- **`MiningProject` modes:** `CAVE_EXPLORATION`, `SURFACE_EXPOSED`, `TUNNEL_SEARCH`, `VEIN_EXTRACTION`, `TARGETED_RETURN`, `EMERGENCY_EXIT`.
- **`MiningMemory`:** bounded cave entrances, branches, ore sightings, hazards, return anchors.
- **`VeinFrontier` + `ResourceTarget`:** generic vein follow — not `DiamondVeinGoal`.
- **Ore utility:** blocking + demand + greed + rarity − detour − inventory − danger; opportunistic wealth when blocking=0.
- **`ToolCapability`:** harvest/speed/durability; durability preempt; tool switching; gravel/lava/water; returnability.
- **`MiningDirector`:** site selection + explore pressure — not `ExploreForDiamondsGoal`.
- **Search budget** per trip; terminal/interrupt reasons; mining personalities deferred as weights.
- **Diamond pick** as obsidian/nether capability unlock.

**Architecture fit:** Refines D-MIW-001 — `MiningDirector`/`MiningProject`/`MiningMemory` are policy/session state; `GatherResourcesGoal` still owns physical dig.

**Not authorized:** Implementation.

### Contribution — Agent_Cursor (full mining + wealth RFC)

**Agent:** Agent_Cursor  
**Date/Session:** 2026-08-08 ~19:45 PDT  
**Contribution type:** `DESIGN`

**User request:** Full RFC for Mining Intelligence + Wealth System; autonomous vanilla mining progression; prerequisite planning; capabilities; integration methods; cave vs dig-down question.

**Target mod:** Vanilla 1.21.1 mining via SPM + Scavenger (`CONFIRMED` workspace). Not a third-party tech mod.

**Key decisions:** D-MIW-006 cave-first; wealth system replaces hardcoded exact-consumer as optional comfort layer; resolver bounded backward chain aligns `RFC-VANILLA` D-VP-001.

**Cave vs dig:** **Reject** dirt dig to Y=−1. **Prefer** exposed ore in caves/ravines, then explore bias, then bounded staircase.

**Not authorized:** Implementation.

**RFC fields updated:** Full document; merged former `RFC-MINING-INTELLIGENCE-AND-RESOURCE-GREED.md` (redirect removed 2026-08-08).

---

### Contribution — Agent_Cursor 2 (Continue the RFC)

**Agent:** Agent_Cursor 2  
**Date/Session:** 2026-08-08 ~20:30 PDT  
**Contribution type:** `REVIEW / DESIGN`

**Frontier before:** `RESEARCHING`, falsely blocked on tool-tier Phase 3; wealth/mining core still
mostly `PROPOSED`; no Gates/User-approval section; MI-1 not marked ready.

**Evidence probes (CODE_CONFIRMED):**
- `MAKE_DIAMOND_PICKAXE` / `MAKE_DIAMOND_AXE` + `ConsumerRecipeSpec` in `ScavengerCrafting`
- `WorkDemandPolicy.diamondDeficit` + `DIAMOND_GENERATION_CEILING_Y`
- `GatherResourcesGoal.wantsDiamond`
- `CRAFTABLE_TIER_CAPS` includes `DIAMOND`
- `MAKE_FURNACE` craft/place path present
- `NOT FOUND`: `ResourceWealthPolicy`, `MiningDirector`, `wealthRawIron` in `src/main`

**Agreement:** NEED vs WEALTH, opportunity bonus, anti-clairvoyance, cave-first, dig-in-gather remain sound.

**Objections / repairs:**
1. P0 is not an open code blocker — Identity status was stale.
2. Reject reintroducing `wealthRawIron` / stock-target push (D-MIW-004).
3. Defer portfolio, scarcity, and SPM greed mapping from gen-1 (D-MIW-022/023/019 hook).
4. Narrow gen-1 slice D-MIW-025: MI-1 → wealth layers → DiscoveryMode before MiningDirector.

**Promoted to `CONSENSUS`:** D-MIW-002, 003, 004 (revised), 010, 011, 012, 016, 017, 019 (config), 021, 025.

**Frontier after:** Gen-1 architecture `CONSENSUS`; **MI-1 `READY`** awaiting `Begin implementation for MI-1`.

**Not authorized this turn:** Java edits, Minecraft launch, commit/push.

**RFC fields updated:** Identity, Executive Summary, Collaboration, Topic Index, Progression nodes,
Wealth/Marginal/Greed/Profiles/Portfolio/Scarcity/Mining topics, Phased plan + task statuses,
Deferred, Decision Registry, Gates, User approval, Change Log, this contribution.

---

### Contribution — Agent_Codex (MI-3/MI-23 implementation)

**Agent:** Agent_Codex  
**Date/Session:** 2026-08-08  
**Contribution type:** `IMPLEMENTATION / VALIDATION`

**Frontier before:** MI-1 completion unlocked MI-3/MI-23.

**Action:** Added generic staged NEED allocation. Current stock is allocated once to immediate,
replacement, project, then reserve; independent shortfall calculations were rejected because they
double-count stock. Focused tests passed 3/3; clean build passed 131 tests. Evidence:
`.superpowers/sdd/task-10-report.md`.

**Frontier after:** MI-3/MI-23 are implemented at pure-policy level. MI-24 is not yet
implementation-ready: its iron/diamond/coal/cobble profile values are explicitly illustrative
(“tune by test, not canonical”). Conservative gen-1 profiles or a data-driven source must be locked
before marginal curves can receive falsifiable acceptance values.

---

### Contribution — Agent_Cursor (Continue the RFC — frontier advance)

**Agent:** Agent_Cursor  
**Date/Session:** 2026-08-08 ~21:00 PDT  
**Contribution type:** `REVIEW / DESIGN / FRONTIER`

**Frontier before:** MI-24 `NEEDS_DECISION` (illustrative profile numbers); Agent_Claude perception finding documented but not scheduled; D-MIW-025 slice still listed MI-13 before perception fix.

**Actions:**
1. **Promoted D-MIW-027 `CONSENSUS`:** exposure must be tested in `isCandidate` for ore (MI-13a) — prerequisite for MI-4/13/16/17/25, not parallel polish.
2. **Locked D-MIW-026:** gen-1 v1 `ResourceWealthProfile` constants + piecewise marginal curve; `greed=0` parity rule.
3. **Revised D-MIW-025 slice:** `MI-13a → MI-24 → MI-25 → MI-4 → MI-13 enum → MI-9 tests`.
4. **Marked READY:** MI-13a, MI-24; added U-MIW-19…21.

**Agreement:** Agent_Claude finding is `CODE_CONFIRMED` against `GatherResourcesGoal.isCandidate` / `findTarget` two-pass design.

**Frontier after:** **MI-13a `READY`** (perception fix) is the recommended next implementation task, then MI-24 marginal curves.

**Not authorized this turn:** Java edits, Minecraft launch, commit/push.

**RFC fields updated:** Identity, Executive Summary, Topic Index, Resource profiles (v1 table), Legitimate discovery (MI-13a), Phased plan, Task statuses, Validation, Decision Registry (D-MIW-026/027), Gates, User approval, Change Log, this contribution.

---

### Contribution — Agent_Cursor (Continue the RFC: MI-4 readiness)

**Agent:** Agent_Cursor  
**Date/Session:** 2026-08-08 ~21:10 PDT  
**Contribution type:** `REVIEW / REPAIR`

**Frontier before:** Identity claimed MI-4 next, but task table still said MI-4 blocked on MI-13a/MI-24;
User approval still asked to implement MI-13a/MI-24; Gates still marked those READY-not-done.
Research gate still claimed `ResourceWealthPolicy` `NOT FOUND`.

**Evidence (CODE_CONFIRMED):**
- `GatherIntentPolicy` + gather integration (MI-1)
- `GatherCandidatePolicy.isPassOneCandidate` ore exposure (MI-13a)
- `ResourceWealthPolicy` profiles/curves/opportunity (MI-24/25 policy)
- `GatherResourcesGoal` does not call `evaluateWealth`
- `greed` / `wealthLevel` `NOT FOUND` in `ScavengerConfig`
- Progress ledger: MI-1, MI-3/23, MI-13a, MI-24/25 policy complete

**Action:** Marked MI-4 `READY` with must/must-not acceptance; updated gen-1 slice, phases, gates,
user approval. No Java; no launch.

**Frontier after:** **MI-4** is the single nearest implementable frontier. Requires explicit
`Begin implementation for MI-4`.

---

### Contribution — Agent_Codex (Continue the RFC: MI-4 implementation audit)

**Agent:** Agent_Codex  
**Date/Session:** 2026-08-08  
**Contribution type:** `REVIEW / OBJECTION / VALIDATION PLANNING`

**Frontier before:** MI-4 was marked `READY`, while unreported MI-4 source and tests already existed.

**Action:** Reconciled source against MI-4 acceptance. Confirmed config/UI and additive intent
wiring, then found zero-cost global opportunity, Y-gate bypass for wealth diamond, and oak-only log
accounting. Negative evidence: `NOT FOUND` candidate-distance `evaluateWealth` call in
`GatherResourcesGoal`; `NOT FOUND` wealth-enabled surface-diamond regression; `NOT FOUND`
`.superpowers/sdd/task-13-brief.md` and `task-13-report.md`.

**Alternatives:** Candidate-scored wealth is recommended over hard saturation cutoffs because it
preserves D-MIW-018 opportunity semantics without creating permanent scans or a new stock target.

**Frontier after:** MI-4 moved to `REOPEN_REQUESTED` as MI-4R with explicit must/must-not tests.
Source was not edited, built, launched, committed, or pushed. The next action requires implementation
authorization for MI-4R.

**RFC fields updated:** Identity, Executive Summary, Collaboration, Topic Index, Mining intelligence
capability rows, Phased plan, Tasks, Gates, User approval, Change Log, this contribution.

---

### Contribution — Agent_Codex (MI-4R implementation)

**Agent:** Agent_Codex
**Date/Session:** 2026-08-08
**Contribution type:** `IMPLEMENTATION / VALIDATION`

**Action:** Implemented the accepted candidate-aware option. `GatherIntent` now keeps required
resources separate from wealth contexts. `GatherResourcesGoal` supplies normalized candidate
distance before pass-one admission; saturated wealth cannot independently activate scanning.
Inventory accounting uses `ItemTags.LOGS`, coal plus charcoal, and all accepted iron smelting
inputs. Diamond wealth context is omitted above Y=16.

**Evidence:** Focused MI-4R tests passed, followed by `gradlew.bat clean build`: 148 tests, zero
failures/errors/skips. Artifact SHA-256 is
`002CB160E8C64D5D6C127950484336740FCA89942578F37215610A0FB680B2AC`.

**Remaining objection:** acquisition cost currently models discovery distance only. Path cost,
dig time, hazards, runtime behavior, and scale performance remain `UNVERIFIED` and belong to later
RFC tasks/runtime gates rather than being silently inferred from the build.

**Frontier after:** MI-4R is `IMPLEMENTED`; the dependency-ready planned frontier is MI-13
`DiscoveryMode`, then MI-2 target priority.
