# RFC: Mining intelligence and wealth system

## RFC Identity

| Field | Value |
| --- | --- |
| **Project root** | `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric` |
| **Host platform** | Social Player Mobs (`playermob`) v0.86.0 — reference `Projects/references/SocialPlayerMobs-v0.86.0/` |
| **Target progression** | **Vanilla Minecraft 1.21.1 mining + resource wealth** (overworld ore tiers through diamond/deepslate; not Nether/endgame mining in gen-1) |
| **Scope** | Autonomous *where* to mine, *how much* to stockpile (wealth), prerequisite planning hooks, capability gaps, integration methods, phased plan, validation — **design until implementation authorized** |
| **Mode** | `PROGRESSIVE_CONTINUATION` (User — Continue the RFC) |
| **Status** | MI-14C3-R1 `IMPLEMENTED` (task-30; 321 tests); MAIBS static re-pass `PASS — BEHAVIORALLY_PLAUSIBLE`; runtime `UNVERIFIED` |
| **User constraint** | No Minecraft launch, commit, or push unless separately asked; implementation only after explicit Begin authorization |
| **Baseline version** | `1.9.2` |
| **Related** | `RFC-TOOL-TIER-UPGRADES.md`; `RFC-VANILLA-AUTONOMOUS-PROGRESSION.md`; `RFC-FURNACE-SMELTING.md`; `RFC-ADAPTIVE-OPINION-MOOD-AND-ENGAGEMENT.md` (discretionary layer — deferred); stubs `progression/ProgressGoal.java`, `TaskLifecycle.java` |
| **Former name** | `RFC-MINING-INTELLIGENCE-AND-RESOURCE-GREED.md` — merged into this file (2026-08-08); “resource greed” → **wealth system** |
| **Owners** | User (product) |
| **Peer review** | `Agent_Cursor` · `Agent_ChatGPT` · `Agent_Cursor 2` · `Agent_Codex` · `Agent_Claude` |
| **Last update** | 2026-08-09 ~03:15 PDT |
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
| **CaveContextSnapshot** | Classifies **current position** (enclosure, rim depth, sky) — MI-6G direction |
| **CaveOpportunity** | Classifies a **possible route/opening** to commit to — MI-6F; not mob posture alone |

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

**Continuation result (`CONSENSUS`, user 2026-08-09):** MI-7A done. **Next package:**
**MI-6F live wiring** → **MI-7B+C** (budget accounting + `NaturalDescentStatus` — one semantic unit)
→ **MI-5H** (`DescentHeadingPolicy` — macro heading, not landing micro-sort) → **MI-7D** → **MI-7E**.
`CaveContextSnapshot` ≠ `CaveOpportunity`. Primary open descent defect is **heading chosen by
novelty before terrain** (MI-5H), not MI-7 session types alone.

---

## Collaboration Protocol

- This continuation is **`User` + `Agent_Cursor`** (MI-7B+C bundle, MI-5H, dependency reorder).
- Evidence: `CONFIRMED` / `INFERRED` / `UNVERIFIED` (Gate AV-1); behavior gate **MAIBS-1**.
- Reuse SPM + Scavenger executors; no duplicate scanners (Gate SPM-2).
- **Anti-clairvoyance (D-MIW-008 `CONSENSUS`):** undiscovered ore behind solid stone is never an exact path target from server block query alone.
- Physical break: `GatherResourcesGoal`. Session intent: `MiningProject`. Orchestration: `MiningDirector`.
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
| [Utility scale (F-1…F-6)](#topic-utility-scale-and-policy-boundaries-5-blocking-findings) | F-1 Option A `LOCKED` + MI-4S `IMPLEMENTED` | Scale repair landed |
| [MI-5 behavioural prediction](#topic-mi-5-behavioural-prediction-gate-maibs-1) | `FAIL` heading blindness | **MI-5H `READY`** — `DescentHeadingPolicy` |
| [MI-6 behavioural prediction](#topic-mi-6-behavioural-prediction-gate-maibs-1) | 6A/D/B/C `IMPLEMENTED`; runtime `UNVERIFIED` | **MI-6F wire before MI-7B+C** |
| [MI-7 controlled excavation descent](#topic-mi-7-controlled-excavation-descent-gate-maibs-1) | MI-7R `IMPLEMENTED` | MI-14C control plane active |
| [MI-14C execution control](#topic-mi-14c--execution-control-plane-proposed-user--agent_claude) | C3-R1 implemented; static MAIBS pass | **Next: approved runtime falsification or Loop-D product decision** |
| [Phased plan](#topic-phased-implementation-plan) | `CONSENSUS` order (revised) | **Next: C2 repair → MAIBS re-pass → C3** |
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
| M-reach-depth | navigation | Y ≤ 16 | **PARTIAL** — MI-5 descent pressure; cave seek still weak | **PARTIAL** (MI-6/MI-14) |
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

## Topic: MI-14 reconciliation + prolonged-loop pass (Gate MAIBS-1)

**Analyst:** `Agent_Claude`, snapshot 2026-08-09 01:5x. Source-verified.
**Gate result:** `FAIL — ARCHITECTURE_DEFECT` (**MI-14-M1**), plus four classified loops.

### Reconciliation — task-table state vs shipped code

| Task | RFC said | Reality |
| --- | --- | --- |
| MI-14A transition protocol | not started | **`IMPLEMENTED`** — `MiningTransition`, pending store, atomic `completeProject(…, transition)` |
| MI-14A-T contract tests | — | **`IMPLEMENTED`** — 14 tests |
| MI-14-R2a…R2e | — | **`IMPLEMENTED`** — see the R2 family table below; suite now **266 tests** |
| MI-14A-R1 `CAVE_FOUND` restart lock | — | **`IMPLEMENTED`** — corrected a wrong invariant, see below |
| MI-14B ownership extraction | not started | **`IMPLEMENTED`** — `MiningDirector` owns admission/start/completion; executor runs assigned work only |
| MI-6F / MI-6G | `DEFERRED` | **`IMPLEMENTED`** — `CaveOpportunityPolicy`, `CaveContextSnapshot` + `classify` |
| MI-7A "current", MI-6F/7B+C "next" | current | **stale** — superseded by the above |

Suite: **249 tests, zero failures.** Everything below runtime remains `UNVERIFIED`.

**MI-14A-R1 correction, recorded because the first version was wrong.** MI-14A-T originally asserted
that `CAVE_FOUND` does *not* block a fresh controlled descent, as "reason isolation". That invariant
starved its own consumer: the rebase lives in `ExploringGoal` at priority **8**, a new descent in
`ControlledDescentGoal` at priority **3**, so with descent pressure still live the executor
reacquires `MOVE` before the rebase can run. Every handoff reason now holds the descent lock, released
by consumption or by the 400-tick expiry.

### MI-14-M1 — false `CAVE_FOUND` from self-dug geometry (`CODE_CONFIRMED`)

`ControlledDescentCaveHandoff.openedTraversableCave` opens with:

```java
if (isSubterraneanAt(heights, feet)) {
    return true;          // ← being underground is treated as having opened a cave
}
```

`isSubterraneanAt` builds a `CaveContextSnapshot` and calls `CaveContextPolicy.isSubterranean`, which
requires `belowLocalTerrain()` — rim depth ≥ 8. Trace a staircase from surface Y=70:

```text
Y=69 … Y=63   rimDepth < 8      → no trigger
Y=62          localRim ≈ 70, rimDepth = 8, sky = false (covered stair)
              → classify = CAVE → isSubterranean = true
              → openedTraversableCave = TRUE
              → finish(CAVE_FOUND)
```

**The mob opened nothing. It dug its own hole and declared it a cave.**

This is *context ≠ opportunity* again, the same distinction MI-6 already established: `CaveContext`
answers "where am I standing", `CaveOpportunity` answers "is there somewhere legitimate to continue".
Line 41 uses the first as proof of the second.

**MI-6G does not rescue this, and it is worth saying why.** `ENCLOSED_STRUCTURE` separates a cellar
from a cave by noticing the local rim sits at the cellar's own ground level. A staircase cut into a
hillside has a genuinely higher rim, so it classifies as `CAVE` correctly — the classifier is right
and the *question being asked of it* is wrong.

The second branch is sound: it probes `AHEAD_PROBE` blocks ahead, which for a descending staircase is
unexcavated rock, so `collectStandable` finds nothing. The defect is the early return alone.

**Interaction with MI-14A makes it costly rather than merely wrong:**

```text
false CAVE_FOUND → descent ends → transition persisted → fresh descent LOCKED
   → ExploringGoal rebases toward a cave that does not exist
   → rebase fails or wanders → 400 ticks → expiry → lock released → descent may restart
```

So a false positive now costs a cancelled descent plus up to 400 ticks of confused exploration.

**Repair (MI-14-R2), not a threshold change.** Raising 8 → 12 or 16 only moves the depth at which the
mob lies to itself. Change the signature to return evidence:

```java
Optional<CaveOpening> findOpenedCave(...)
```

where an opening requires: a standable 2-high destination, **outside the staircase's own excavated
corridor**, in legitimate subterranean context, reachable from the current stair position. The result
then populates the transition's `target` and a real continuation `heading` — which is what makes
MI-14A's payload worth having. Today it ships `target = unresolved, heading = staircase heading`.

### Prolonged-loop classifications

| ID | Loop | Class |
| --- | --- | --- |
| **A** | **Zombie assignment.** *(Contention is only one trigger — MI-14C1 shows any hard precondition failing after assignment produces the same deadlock.)* Director assigns `CONTROLLED_DESCENT`; a priority-3 chore already holds `MOVE`; the executor never starts; the project stays `RUNNING`; `mayStartControlledDescent` refuses a new one because `projectOf` is present. Budget ticks only during execution, so an unexecuted assignment consumes none and **cannot time out**. | `ARCHITECTURE_DEFECT` — needs an assignment TTL or MI-14C admission |
| **B** | **Handoff vs chores.** The `CAVE_FOUND` lock stops another *staircase*, but `GatherResourcesGoal` / `CraftTorchesGoal` / `SmeltAtFurnaceGoal` at priority 3 still outrank the priority-8 rebase. The cave can be lost to a wood-chopping errand. | `ARCHITECTURE_DEFECT` — MI-14C is the intended fix |
| **C** | **Repeated-site descent.** Exhaust → lock → 400 ticks → expiry → same demand, same area still `EXHAUSTED` → another descent. Immediate looping is impossible; **long-term repetition is not excluded.** | `RUNTIME_QUESTION` — needs site/sector rejection, folds into MiningMemory |
| **D** | **Tunnel dead leaf.** `HANDOFF_TUNNEL_SEARCH` preserved with no executor. Honestly blocked rather than pretending to work. | `ACCEPTABLE_STEPPING_STONE` — while nothing claims tunnel search functions |

**A is the one that changes MI-14C's scope.** An arbiter that only chooses *who may act* does not
clear a stale assignment; the director also needs to be able to revoke one.

### Acceptance for MI-14-R2

**Must happen:** a staircase reaching rimDepth ≥ 8 with **no external opening** does **not** fire
`CAVE_FOUND`; a genuine breakthrough yields a transition whose `target` is the actual landing.
**Must not happen:** the mob's own corridor counting as the opening; a threshold change substituting
for evidence.

**Falsifying probe:** scripted solid-stone hill, surface Y=70, no caves within 32 blocks. Force a
controlled descent. Prediction under current code: `CAVE_FOUND` fires at Y≈62. Under R2: descent
continues to budget exhaustion with no `CAVE_FOUND`.

### MI-14-R2 family — `IMPLEMENTED`, `CODE/UNIT_CONFIRMED` (266 tests)

Five revisions, each caused by falsifying the previous one. Recorded in order because the sequence is
the lesson: every repair exposed the next defect, and three of them were invisible while the tests and
the production caller disagreed about *which step* was the evidence.

| Rev | Defect | Repair |
| --- | --- | --- |
| **R2a** | `isSubterraneanAt` early return — a staircase is subterranean by construction | `SELF_CORRIDOR` exclusion + `CaveOpening` payload (`landing`, `continuation`, `kind`) replacing the boolean |
| **R2b** | "standable floor nearby" is not "we broke into it"; the probe read world state through unbroken stone | Bounded connected flood seeded from the cells the step actually excavated; an intact wall is impassable, so the volume behind it is never visited |
| **R2c** | **Wiring.** `completeStep` passed `mob.blockPosition()`, and the overload planned `S1 -> S2` — the *future* step. The invariant "seed only from what this step excavated" was violated at the call site | Public API requires the `StairStepPlan`; executor passes its live `currentStep`; seeds must additionally be passable *now*, so a solid wall seeds nothing regardless of caller error |
| **R2c-b** | `SELF_CORRIDOR` built 2-high columns; a step cuts **three** cells (`stand`, `+1`, `+2`). Each step's own headroom sat outside the exclusion set, so the flood found the staircase's dug ceiling and called it a cave | Full step height in `addColumn` |
| **R2d** | Planned-but-undug cells counted as self-created, **masking a cave already open directly ahead** — the opposite failure to R2b | `SELF_CORRIDOR` = excavation history only. Self-created means *dug*, not *intended*; the R2c seed guard already covers solid future geometry |
| **R2e** | The flood proved connected **air**, not connected **mob space**. A 1-block slit connects two chambers a 2-block-tall PlayerMob cannot pass | Flood over `occupiable = passable(cell) && passable(cell.above())`; standability still decides where it may stop |

**MAIBS falsification set (all green):**

| ID | Case | Expected |
| --- | --- | --- |
| R2-C1 | Self corridor only | no opening |
| R2-C2 | Cave behind intact future wall | no opening |
| R2-C3 | Cave touching completed excavation | opening |
| R2-C4 | Natural cave **already open** directly ahead | opening |
| R2-C5 | Connected only through a 1-high slit | no opening |

**C4 was masking C5.** The slit occupied future-step cells, so the same over-exclusion that hid a real
cave also hid the traversability defect. Stating both cases before repairing either is what surfaced
it — repairing C4 alone would have shipped C5 silently.

**Method lesson (`PROVEN`, promote):** a unit test that calls a low-level overload *self-consistently*
proves the algorithm and **nothing** about the wiring. R2c, R2c-b and R2d were all invisible while the
test paired a completed step with the corridor of the previous stand. `CaveOpeningEvidenceTest` now
mirrors the production pairing exactly.

**Continuation semantics:** measured from the **nearest excavated cell**, not the mob's feet. From the
stand cell a diagonal landing ties and resolves along the staircase axis — pointing back down the
tunnel instead of into the discovery.

**Still `UNVERIFIED`:** no staircase has been observed breaking into a real cave in a running game.

---

## Topic: MI-14C — Execution Control Plane (`PROPOSED`, User + Agent_Claude)

MI-14C was originally scoped as "arbitration". Loop A proves that insufficient: an arbiter that
chooses *who may act* cannot clear an assignment nobody is acting on. Split into three tasks.

```text
        MiningDirector  "WHAT next?"
                 |
        +--------+--------+
        v                 v
  MiningProject     MiningTransition
        +--------+--------+
                 v
          ExecutionIntent
                 v
     MI-14C Execution Control
     (lease / arbitration / suspension / staleness / revocation)
                 v
            GoalSelector
```

### MI-14C1 — Assignment Lease & Revocation (fixes Loop A)

`canUse` checks config, combat target, `mobGriefing` and `hasUsablePick()` **before** asking whether
an assignment exists. Lose the pick after assignment and the executor returns `false` before the
lookup, the project stays `RUNNING` forever, and `mayStartControlledDescent` refuses every future
assignment because `projectOf` is present. **Deadlock, with no TTL anywhere.**

A TTL alone is the wrong fix — it deletes without distinguishing *why*. Blockers are classified:

| Class | Examples | Response |
| --- | --- | --- |
| `TEMPORARY` | combat target, short interruption | **suspend**, keep the project for bounded recovery |
| `HARD` | `mobGriefing` off, feature disabled, capability lost (no pick) | **revoke** with a reason, let prerequisite systems restore capability |
| `CONTENTION` | executor admissible, another non-critical goal owns `MOVE` | **arbitrate** (MI-14C2) |

**Reordering `canUse` is not the fix.** The executor should stop owning the consequences of failure:
the lease layer asks "do I have an assignment / is the executor admissible / if not, why", and
`canUse` becomes "do I hold a valid lease for this mode, and can I physically execute right now".

**Invariant (testable):** no `RUNNING` `MiningProject` may exist indefinitely without either
execution progress or an explicit suspension reason.

### MI-14C2 — Execution Intent & Arbitration (fixes Loop B) — `IMPLEMENTED` (task-27)

**Status:** `IMPLEMENTED` — `MiningExecutionC2Test` C2-A…G green; **292 tests** (`CONFIRMED`, 2026-08-09).
**Prerequisite:** MI-14C1 + MI-14C1-R1 `IMPLEMENTED` (commit `a6e9793` verified: episode clock,
`blockedSince`, `NEVER_STARTED` sentinel).

**Purpose:** Make `MiningDirector` decisions enforceable through `GoalSelector` **without**
replacing `GoalSelector` and **without** allowing mining to override combat/survival.

**Shipped artifacts (`CODE_CONFIRMED`):**

| File | Role |
| --- | --- |
| `ExecutionIntent.java` | `CONTROLLED_DESCENT`, `CAVE_HANDOFF`, `TUNNEL_HANDOFF_PENDING`, `NONE` |
| `ExecutionIntentPolicy.java` | Derives intent from project + pending transition |
| `ArbitrationDecision.java` | `ALLOW` / `YIELD` / `NEUTRAL` |
| `MiningGoalKind.java` | Goal classification incl. `EXPLORING_CAVE_HANDOFF` |
| `MiningExecutionArbiter.java` | Pure permission matrices |
| `MiningExecutionGuard.java` | Shared `canUse` + `canContinueToUse` gate |
| `MoveContentionPolicy.java` | Detects yielding MOVE holders |
| `MiningDirector.resolveControlledDescentBlocker` | Maps scheduler contention → `CONTENTION` |

**Goal wiring (`CODE_CONFIRMED`):** `ControlledDescentGoal`, `GatherResourcesGoal`,
`SmeltAtFurnaceGoal`, `CraftTorchesGoal`, `ExploringGoal` — arbiter consulted in **both**
`canUse()` and `canContinueToUse()`.

#### MAIBS C2 static pass (Gate MAIBS-1, post-repair task-29)

**Verdict:** **`PASS_WITH_RUNTIME_UNVERIFIED`** — M1–M3 repaired; full report in
`.superpowers/sdd/task-29-report.md`. Prior FAIL documented in `task-27-maibs-report.md`.

| ID | Repair | Static re-pass |
| --- | --- | --- |
| **M1** | `MiningExecutionCommitment` + `claimCaveContinuation` | C2-A/G **PASS** |
| **M2** | `MoveHolderClassifier` + `hasBlockingMoveHolder` | C2-F **PASS** static |
| **M3** | `shouldPersistExecutorCheckpoint` + guarded `stop()` | C1-R2 tests **PASS** |

**Do not claim runtime CONFIRMED** without launch approval probes (RT-C2-A/F in task-27-maibs-report).

#### Separation of concerns (`CONSENSUS` — D-MIW-037)

| Concept | Question it answers | Must not |
| --- | --- | --- |
| `ExecutionIntent` | What **should** have execution authority? | Merge with blocker classification |
| `ExecutionBlocker` | Why is the authorized executor **not receiving** authority? | Become a second intent system |
| `ArbitrationDecision` | May this goal run / continue given current intent? | Change Minecraft priority integers |

`ExecutionBlocker.CONTENTION` is **not** an intent. It is the lease-layer observation:

```text
CONTROLLED_DESCENT assigned
  → no combat / pick exists / mobGriefing / feature enabled (C1 admissible)
  → executor still hasn't started (or actionable intent exists)
  → another running Goal owns MOVE
  → CONTENTION
```

Without a real `CONTENTION` producer, C1 sees `NONE → AUTHORIZE` while the scheduler never runs the
authorized goal — a narrower zombie assignment survives.

Combat remains `ExecutionBlocker.COMBAT_TARGET` (`TEMPORARY`), **not** ordinary contention.

#### 1. `ExecutionIntent` derivation (`CONSENSUS`)

Derive from **actual persistent state only** — never from Goal priority:

| Persistent state | Intent |
| --- | --- |
| Active `CONTROLLED_DESCENT` project | `CONTROLLED_DESCENT` |
| Pending `CAVE_FOUND` transition | `CAVE_HANDOFF` |
| Pending `HANDOFF_TUNNEL_SEARCH` | `TUNNEL_HANDOFF_PENDING` |
| Otherwise | `NONE` |

#### 2. `ArbitrationDecision` (`CONSENSUS`)

| Decision | Meaning |
| --- | --- |
| `ALLOW` | This executor is the designated consumer for the current actionable intent |
| `YIELD` | This ordinary/non-critical executor conflicts with an actionable mining intent |
| `NEUTRAL` | Mining has no authority over this activity |

Critical combat/survival remain **outside** mining authority (`NEUTRAL` from mining's perspective —
those goals keep their own preemption).

#### 3. Unsupported / non-exclusive intents (`CONSENSUS` — D-MIW-038)

`TUNNEL_HANDOFF_PENDING` has **no executor**. Until `TunnelSearchGoal` exists:

- **Observable** pending state only
- **Blocks** fresh controlled descent through existing transition semantics (`mayStartControlledDescent`)
- **Does NOT** claim exclusive `MOVE` authority
- Arbitration: **`NEUTRAL`** — do **not** force unrelated goals to `YIELD`
- **Do NOT** consume or clear the transition to make tests green (Loop D honesty)

Same rule for `SEARCH_BUDGET_EXHAUSTED` as outcome/reconsideration lock: arbitration `NEUTRAL`.

**Wrong (forbidden):**

```text
TUNNEL_HANDOFF_PENDING → yield Gather / Smelt / Explore → deny Descent → ALLOW nobody → deadlock
```

**Actionable intents** (may cause `YIELD` in other goals): `CONTROLLED_DESCENT`, `CAVE_HANDOFF` only.

#### 4. `CONTROLLED_DESCENT` matrix (`LOCKED`)

| Goal / activity | Decision |
| --- | --- |
| `ControlledDescentGoal` | `ALLOW` |
| `GatherResourcesGoal` (ordinary) | `YIELD` |
| `SmeltAtFurnaceGoal` | `YIELD` |
| `CraftTorchesGoal` (ordinary) | `YIELD` |
| `ExploringGoal` (ordinary) | `YIELD` |
| Combat / survival | `NEUTRAL` |

If controlled descent loses a required capability, **C1 revokes first** — do not retain descent
authority merely because intent existed.

#### 5. `CAVE_HANDOFF` matrix (`LOCKED`)

| Goal / activity | Decision |
| --- | --- |
| `ExploringGoal` acting on `acceptCaveHandoff` | `ALLOW` |
| `ControlledDescentGoal` | `YIELD` |
| `GatherResourcesGoal` | `YIELD` |
| `SmeltAtFurnaceGoal` | `YIELD` |
| `CraftTorchesGoal` | `YIELD` (unless a future explicit prerequisite intent says otherwise) |
| Combat / survival | `NEUTRAL` |

Transition remains pending until handoff is **actually accepted successfully**.

#### 6. Admission **and** continuation (`LOCKED` — C2 hard requirement)

Every participating executor consults the **same** arbitration policy in **both**:

- `canUse()`
- `canContinueToUse()`

`canUse`-only wiring is a **FAIL** (Loop B survives).

Required sequence:

```text
GatherResourcesGoal running
  → CAVE_FOUND appears
  → canContinueToUse asks arbiter → YIELD
  → goal stops → MOVE released
  → ExploringGoal can acquire MOVE → acceptCaveHandoff runs
```

#### 7. Contention producer (`LOCKED`)

C2 must create a real path to `ExecutionBlocker.CONTENTION`:

When an assigned/actionable executor is otherwise C1-admissible but cannot obtain `MOVE` because
another **running** goal owns it, the lease layer must classify `CONTENTION` (start lease remains
bounded via C1).

Do **not** classify combat as contention when combat already has `TEMPORARY` classification.

#### 8. Non-goals (`LOCKED`)

- Do **not** change Minecraft priority integers as the solution
- `GoalSelector` remains the physical scheduler; mining arbitration is permission/yield semantics above it
- `LOW_FOOD` enum exists for future director classification — **not wired in C2** merely because the enum exists

#### C2 falsification scenarios (required before `DONE`)

| ID | Scenario | Must happen | Must not |
| --- | --- | --- | --- |
| **C2-A** | Gather running → `CAVE_FOUND` | Gather yields; cave consumer becomes eligible | Gather keeps `MOVE` |
| **C2-B** | Smelt running → controlled descent assigned | Smelt yields; descent may start | Smelt blocks descent indefinitely |
| **C2-C** | Combat interrupts descent | Combat not suppressed; lease `TEMPORARY` suspend | Combat classified as contention |
| **C2-D** | Combat ends | Valid mining intent resumes | Assignment wrongly revoked |
| **C2-E** | `TUNNEL_HANDOFF_PENDING` | Ordinary behavior continues; transition untouched | Global yield freeze / transition consumed |
| **C2-F** | Executor authorized; unrelated `MOVE` owner persists | `CONTENTION` observable; start lease bounded | Eternal `AUTHORIZE` with no execution |
| **C2-G** | Intent changes while executor running | `canContinueToUse` reacts; goal yields | Wait for natural goal completion |

**Implementation sketch (non-binding):** `ExecutionIntentPolicy.derive(store, mobId)` →
`MiningExecutionArbiter.decide(intent, goalKind)` → goals call arbiter in `canUse` + `canContinueToUse`;
`MiningDirector` observes `MOVE` ownership for `CONTENTION` when lease would otherwise `AUTHORIZE`.

### MI-14C3 — Progress Lease (fixes stale-active Loop A) — **`IMPLEMENTED` (task-28)**

**Status:** code `IMPLEMENTED`, but **MAIBS-1 `FAIL — ARCHITECTURE_DEFECT`**. The 310-test clean
build proves unit/static behavior, not integrated reachability of C3-A.
**Prerequisite:** MI-14C2 repair package (task-29) `IMPLEMENTED`.

Two clocks — start lease (C1) and progress lease (C3) — because an executor that starts once and
then starves forever is still a zombie assignment:

| Clock | Measures | Timeout constant | Revoke reason |
| --- | --- | --- | --- |
| **Start lease** (C1, shipped) | `assignedAt` → first `executorStartedAt` | `START_LEASE_TICKS` (600) | `LEASE_EXPIRED` via `CONTENTION` / never-started path |
| **Progress lease** (C3) | `lastExecutionProgressAt` → `now` while executor should advance | `PROGRESS_LEASE_TICKS` (**2400** / 120s) | `NO_PROGRESS` |

#### Separation (D-MIW-039)

| Concept | Question | Must not |
| --- | --- | --- |
| Start lease | Has the executor **ever begun** this assignment? | Conflate with "made dig progress" |
| Progress lease | Has the executor produced **observable dig progress** recently? | Refresh on mere `tick()` or path replan |

#### Observable progress signals (`LOCKED` — gen-1 `CONTROLLED_DESCENT` only)

Call `MiningDirector.markExecutionProgress(level, mob)` only when:

1. A planned break cell is **actually broken** (block removed).
2. A stair step is **completed** (`completeStep` — stand cell advanced / project anchor updated).
3. A terminal handoff is **emitted** (`CAVE_FOUND`, `HANDOFF_TUNNEL_SEARCH`, budget exhaustion).

**Forbidden progress refreshers:** goal `tick()` with no world change, path recalculation, plan
rejection/replan, `markExecutorStarted` alone (starts the start clock; does not satisfy progress).

#### Progress lease evaluation (`LOCKED`)

Evaluate progress timeout **only when all hold**:

- `lease.everStarted()` is true;
- current blocker is `NONE` (executor is admissible — not combat/hard/contention suspend);
- `now - lastExecutionProgressAt > PROGRESS_LEASE_TICKS`.

When blocker is `TEMPORARY` or `CONTENTION`, **pause** the progress clock (do not count suspended
time toward stall). Rationale: the mob cannot be expected to dig while combat blocks execution or
while C2 is still yielding a chore.

When progress timeout fires → `REVOKE` with `MiningProjectEnd.NO_PROGRESS`.

#### Lease field additions (`LOCKED`)

```text
MiningExecutionLease.lastExecutionProgressAt
  NEVER_PROGRESS = -1L sentinel (mirrors NEVER_STARTED pattern)
  set on first observable progress event
  persisted in lease NBT v3

MiningExecutionLease.progressPausedTicks
  exact completed TEMPORARY / CONTENTION episode time for the current progress window
  resets only on observable progress; persisted in lease NBT v3
```

`markExecutorStarted` does **not** set `lastExecutionProgressAt`.

#### C3 falsification scenarios (required before `DONE`)

| ID | Scenario | Must happen | Must not |
| --- | --- | --- | --- |
| **C3-A** | Descent started, one step completed, then path-stuck with goal still running | Revoke after `PROGRESS_LEASE_TICKS` with `NO_PROGRESS` | Eternal ACTIVE lease |
| **C3-B** | Combat suspends mid-descent | Progress clock paused; resume after combat without immediate revoke | Progress timeout during combat |
| **C3-C** | Active breaking / step completion | `lastExecutionProgressAt` refreshes; lease survives | `tick()` alone refreshes |
| **C3-D** | Never started (C1 domain) | Start lease still governs; progress lease inactive | Double-revoke races |
| **C3-E** | CONTENTION starved after start | Progress clock paused while `CONTENTION` blocker active | Revoke while waiting for C2 yield |

**Static/unit result:** C3-A…E **PASS** in `MiningExecutionC3Test`; persistence and v2→v3
migration regressions pass. Full evidence: `.superpowers/sdd/task-28-report.md`. Runtime rows remain
`UNVERIFIED` because no Minecraft launch was authorized.

#### Full MAIBS integration audit — `FAIL` (Agent_Codex, explicit skill invocation)

The active executor increments project `ticksElapsed` before acting and ends at
`ticksElapsed >= MiningBudget.maxTicks` (**2400**). C3 ends only when admissible progress age is
strictly `> PROGRESS_LEASE_TICKS` (**2400**). Consequently an actively stuck descent emits
`SEARCH_BUDGET_EXHAUSTED` first; if 2300 project ticks were already spent before the last progress,
the supposed 2400-tick progress window can be shadowed after only ~100 more ticks. C3-A is green as
a pure policy test but unreachable on that integrated active-goal path (`CODE_CONFIRMED`).

A second gap remains: `MoveHolderClassifier` excludes `PROTECTED_INTERRUPT` from contention, while
`controlledDescentBlocker` only recognizes combat among protected higher-priority work. A running
`StayNearGoal`, `TrainRecoveryGoal`, shelter, or environmental escape can therefore physically own
MOVE while the lease sees blocker `NONE`. Never-started work can evade C1 indefinitely under a
persistent stay tether; started work can consume C3 time while physically preempted.

Three probes recorded: protected/stay handling `NOT FOUND` in `controlledDescentBlocker`;
`PLAYER_ORDER` use `NOT FOUND` outside its enum; stay-anchor assignment prevention `NOT FOUND` in
the director/flagless observer (it exists only in `ExploringGoal`). Full trace and options:
`.superpowers/sdd/task-28-report.md`.

**Repair gate:** make progress expiry reachable before total-budget termination and explicitly map
protected MOVE ownership to prevent, pause, or revoke assignment. Do not proceed to the Loop-D
tunnel consumer while this high-severity C3 objection remains unresolved.

#### MI-14C3-R1 — Protected Interruption Lease Semantics — `IMPLEMENTED` (task-30)

**User review accepted:** C2's `PROTECTED_INTERRUPT` answer is correct for arbitration and
incomplete for lease accounting. The classifier currently collapses two independent questions:

| Question | Protected answer |
| --- | --- |
| May mining force this holder to yield? | **No** |
| Is mining physically available while this holder owns MOVE? | **Also no** — must become a lease fact |

Do not convert all protected work to ordinary CONTENTION. Preserve two dimensions:

| Holder | Arbitration | Proposed lease meaning |
| --- | --- | --- |
| Controlled descent | `ALLOW` | executing |
| Gather/smelt/follow/unknown ordinary MOVE | `YIELD` or non-protected | `CONTENTION` |
| Environmental escape / shelter / train recovery | `PROTECTED` | `SAFETY_RECOVERY` explicit pause |
| StayNear / persistent player command | `PROTECTED` | prevent assignment or hard `COMMAND_CONSTRAINT → PLAYER_ORDER` revoke |
| Combat | `PROTECTED` | existing `COMBAT_TARGET`; do not duplicate |

`SAFETY_RECOVERY` cannot simply reuse the current TEMPORARY policy unchanged: TEMPORARY revokes
after 1200 ticks, so C3-F1's >2400-tick recovery could never resume. It needs an explicit pause
lifetime policy (or a separately justified safety bound) distinct from combat grace.

The mapping audit must also inspect host safety goals not currently labelled
`PROTECTED_INTERRUPT`: `FireBucketGoal` and `FleeFromCategoryGoal` are registered above mining and
currently fall through to unknown/CONTENTION. That pauses a started C3 clock but can expire a
never-started C1 lease after 600 ticks. Do not claim the R1 taxonomy complete until every priority
0–2 MOVE holder in the pinned SPM goal registration has an intentional lease meaning.

The conflict scan must not stop at MOVE. `ControlledDescentGoal` owns `MOVE + LOOK`, while pinned
SPM `EatFoodGoal` is priority 3 and owns `LOOK` only. If eating already runs, equal-priority
replacement is not guaranteed and descent can fail admission while `MoveContentionPolicy` sees no
holder (`CODE_CONFIRMED` flags/registration; equal-priority scheduler result
`GAME_MECHANICS_INFERRED` pending mapped `WrappedGoal` body/runtime). R1 must evaluate intersection
with the executor's complete required flag set, while ignoring truly flagless readout helpers.

**Required invariants:**

1. `PROTECTED != PREEMPTIBLE`: mining never forces safety, recovery, combat, or commands to yield.
2. `PROTECTED != EXECUTION_AVAILABLE`: a protected MOVE owner cannot resolve to blocker `NONE`.
3. Safety/recovery pauses both the progress clock and, before first start, the start-admission clock.
4. A persistent command restriction cannot retain an incompatible assignment indefinitely.
5. Combat retains its existing blocker and temporary-grace semantics.
6. Blocker transitions settle each pause episode once; protected→combat→protected neither loses nor
   duplicates time.

The pre-start clause requires explicit accounting. Reusing only `progressPausedTicks` is
insufficient because `MiningExecutionLease.recordBlocker` deliberately accumulates it only after
`everStarted`. Candidate implementation: add persisted `startPausedTicks`, and evaluate the C1
contention/start window as `now - assignedAt - startPausedTicks`. Mutating historical `assignedAt`
is rejected.

**Competing repair options:**

| Option | Design | Benefit | Risk / decision |
| --- | --- | --- | --- |
| **A — typed lease blockers (recommended)** | Add `SAFETY_RECOVERY` with explicit pause semantics and `COMMAND_CONSTRAINT` with assignment prevention / `PLAYER_ORDER` revoke; keep arbitration classification separate | Preserves meaning and diagnoses player commands correctly | More enum/policy branches; every protected class must be mapped and tested |
| B — scheduler effect record | Return `{preemptibility, leaseImpact}` from one classifier | Compiler-enforces the two axes and scales to more goal families | Larger C2 refactor for a small current taxonomy |
| C — all protected → CONTENTION | Reuses existing pause | Small | **Rejected:** erases safety/command semantics and makes arbitration diagnostics misleading |

**Progress-vs-total-budget companion repair (`LOCKED`):** the lease arithmetic remains sound, and
the progress window is 400 admissible ticks—conservatively above the maximum 200-tick block break
with navigation/replan/cadence tolerance, yet strictly below the 2400-tick absolute cap. The total
cap is preserved and may legitimately win only when less than one full progress window remains.
Removing the cap was rejected unless another absolute lifetime bound replaces it.

**Falsification matrix:**

| ID | Scenario | Must happen | Must not |
| --- | --- | --- | --- |
| C3-F1 | ACTIVE descent; environmental escape owns MOVE beyond the progress window | C3 pauses; clear escape resumes remaining window | `NO_PROGRESS` during safety recovery |
| C3-F2 | ASSIGNED, never started; protected recovery owns MOVE beyond 600 ticks | Explicit suspended blocker and paused start clock | perpetual `NONE/AUTHORIZE` or immediate stale C1 expiry after clear |
| C3-F3 | Persistent StayNear/player command | Mining does not preempt; assignment prevented or ends `PLAYER_ORDER` | zombie project or return↔dig loop |
| C3-F4 | Protected interruption → NONE | Duration excluded exactly once | double pause credit |
| C3-F5 | Protected → combat → protected | Separate blocker episode/grace clocks remain correct | lost/duplicated pause or combat reclassified |
| C3-F6 | Early active denied break with ample total budget remaining | `NO_PROGRESS` occurs before total-budget terminal | isolated policy pass with unreachable integrated outcome |
| C3-F7 | Priority-3 LOOK-only `EatFoodGoal` runs before never-started descent | Explicit scheduler blocker/pause and later clean admission | blocker `NONE` because the scan inspects MOVE only |

**Pinned priority 0–2/3 conflict evidence:** FireBucket, CommandedAction, TrainRecovery,
FleeFromCategory, SkepticalWatch, FriendlyGreet, DoorOperation, TNT/end-crystal/weapon combat,
SeekAmmo, FollowLovedOne, and StayNear declare MOVE+LOOK; PlayerMobDoor/DigThrough/BlockArrows are
flagless; EatFood declares LOOK only. Final mapping must distinguish safety, explicit command,
combat, social reflex, and ordinary work rather than infer meaning solely from priority.

**Locked complete mapping for the pinned host version:**

| Goal family | Executor flag conflict | Lease impact | Reason |
| --- | --- | --- | --- |
| Float | JUMP only | none | Can coexist with descent's MOVE+LOOK |
| FireBucket, EnvironmentalEscape, Flee, SeekShelter, TrainRecovery | MOVE/LOOK | protected pause | Immediate safety/recovery; mining never preempts |
| CommandedAction, StayNear | MOVE/LOOK | command prevent/revoke `PLAYER_ORDER` | Explicit or persistent player authority outranks autonomous mining |
| SkepticalWatch, FriendlyGreet, DoorOperation | MOVE/LOOK | protected finite pause | Host priority-1 reflex deliberately interrupts ordinary tasks |
| Weapon/TNT/crystal combat | MOVE/LOOK | existing `COMBAT_TARGET` when target exists; protected combat pause fallback otherwise | Preserve combat semantics |
| SeekAmmo, FollowLovedOne, ordinary host/addon work | MOVE/LOOK | CONTENTION | Non-safety work may be released by the bounded start lease |
| EatFood | LOOK | protected finite pause | Survival action conflicts with descent LOOK despite no MOVE |
| PlayerMobDoor, DigThrough readout, BlockArrows | none | none | No scheduler conflict |

**Locked progress window: 400 ticks.** A successful block removal marks progress immediately; the
known worst physical break is capped at 200 ticks. The second 200 ticks are conservative tolerance
for one-step navigation, replanning, observer cadence, and server-tick irregularity—not a claim that
every legal interval is a 200+200 sequence. This remains strictly below the 2400 absolute project
cap. Alternative 600 delayed recovery without a known legitimate operation requiring it; dynamic
remaining-budget windows were rejected as harder to diagnose.

**Implementation result:** `SchedulerConflictPolicy` now evaluates every required flag (`MOVE +
LOOK`) separately from arbitration. Typed protected classifications map safety/recovery to a
condition-bound `SAFETY_RECOVERY` pause, commands/stay anchors to `PLAYER_ORDER`, eating and finite
host reflexes to bounded blockers, and combat to its existing bounded semantics. Lease NBT v4 adds
`startPausedTicks`; progress remains independently paused. Persistent stay anchors prevent
assignment, and the intended-authority admission scan prevents `CommandedActionGoal` revoke→reassign
loops. C3-F1…F7 pass in `MiningExecutionC3R1Test`; 321-test clean build passes.

**Post-implementation MAIBS static result:** `PASS — BEHAVIORALLY_PLAUSIBLE`. The earlier three
cross-layer defects are closed in code: protected holders cannot become `NONE`, LOOK-only eating is
visible, and the 400-tick local-stall timeout can fire while the 2400-tick total budget still has
room. Runtime animation/timing and real GoalSelector ordering remain `UNVERIFIED` pending separately
approved Minecraft launch. Evidence: `.superpowers/sdd/task-30-report.md`.

#### Non-goals (`LOCKED`)

- No progress lease for modes without an executor (tunnel search deferred).
- Do not treat `enforceLease` observer ticks as progress.
- Do not change C2 matrices or priority integers.

**Implemented:** `MiningExecutionLease` + `ExecutionLeasePolicy.evaluate`;
`MiningDirector.markExecutionProgress`; successful-break, complete-step, and terminal-handoff marks
in `ControlledDescentGoal`; C3-A…E plus persistence/migration tests.

### Loop D stays outside MI-14C (`LOCKED`)

`HANDOFF_TUNNEL_SEARCH` means "descent is done, begin tunnel search" and **no tunnel-search executor
exists** — verified: the constant appears only in the enum, the transition, and the goal that emits
it. An arbiter cannot answer "who should run" when the answer is "nobody implements it". It stays
`UNSUPPORTED / PENDING_MODE`. MI-14C must **not** consume or clear it to make tests green — that
recreates precisely the producer-without-consumer defect this effort removed.

Order: MI-14C -> `TUNNEL_SEARCH` executor -> the reason gains a real consumer -> second executable
mode -> genuine multi-mode `MiningDirector` selection over modes that correspond to executable
behaviour.

### MI-14C delivery state (reconciled 2026-08-09, suite **310 tests** at pass time;
**321** after Protected Interruption Handling, **327** after MI-14C2-R2)

| Task | State | Evidence |
| --- | --- | --- |
| MI-14C1 lease + revocation | `IMPLEMENTED` | `ExecutionBlocker`, `ExecutionLeasePolicy`, `MiningExecutionLease`, director `enforceLease`/`authorizeExecution`; `canUse` reduced to lease query |
| **MI-14C1-R1 episode clock** | `IMPLEMENTED` (repair of a defect in C1) | grace now measured from `blockedSince`, not assignment age |
| MI-14C2 arbitration | `IMPLEMENTED` | `ExecutionIntent`, `ExecutionIntentPolicy`, `MiningExecutionArbiter`, `MiningExecutionGuard`, `MiningGoalKind`, `MoveHolderClassifier`, `MoveContentionPolicy`, `SchedulerConflictPolicy`; guard wired into all five participating goals |
| MI-14C2-R1 commitment | `IMPLEMENTED` | `MiningExecutionCommitment` + `claimCaveContinuation` — authority survives transition consumption |
| MI-14C3 progress lease | `IMPLEMENTED` | `PROGRESS_LEASE_TICKS`, `markProgress`, `progressPausedTicks` excluding blocked time |

**C1 defect, recorded because it was mine (`Agent_Claude`).** `ExecutionLeasePolicy` measured the
`TEMPORARY` grace as `now - assignedAt`. A healthy staircase assigned 5000 ticks earlier met its
first zombie and was revoked instantly — every long-running dig destroyed by the first mob that
looked at it. My own exhaustive invariant test passed, because the invariant was framed as *nothing
is held forever* and never asked *is anything released too early*. Repaired by another agent with a
per-episode clock (`recordBlocker`/`blockedSince`). Same failure class as R2c: **a test built from
the implementation's own framing proves self-consistency, not correctness.**

**Loop D correctly stayed out.** `TUNNEL_HANDOFF_PENDING` maps to `NEUTRAL` for every goal kind, so
the arbiter grants no authority to a mode with no executor. Verified in `MiningExecutionArbiter`.

**Bounded authority holds.** `MiningGoalKind.classify` returns empty for combat, survival and SPM
host goals, so arbitration cannot suppress them. `EnvironmentalEscapeGoal` (0), `SeekShelterGoal`
(2) and `PlaceTorchGoal` (4) are outside the matrix by construction.

### MAIBS control-plane pass — `FAIL: MI-14C2-M1` (`CODE_CONFIRMED`)

**The continuation commitment grants no additional protected time.**

```text
discovery tick T
   |<---------------- 400 ticks: handoff ADMISSIBLE ---------------->|
   |                                        ^ accept at T+399        |
   |                                        |<-- 1 tick authority -->|
                                                          commitment expires T+400
```

- `ExploringGoal.acceptCaveHandoff` admits while `now - transition.tick() < 400`
  (`MiningTransition.expired` is `>=`, so the window is **strictly** under 400; the last
  admissible claim is discovery+399, which receives exactly one tick of authority).
- `MiningExecutionCommitment.caveContinuation` sets `expiresAt = handoff.tick() + 400` — **the same
  instant the admission window closes**.
- Protected travel is therefore `400 - ageAtAcceptance`, which can be ~0 ticks.
- `claimedAt = now` is stored and **never read** — the field the design needed and did not use.

The stated purpose of C2-R1 is authority that "survives transition consumption until the executor
finishes". It survives consumption, but expires on the *discovery* clock, so it cannot outlive the
window it was built to outlive.

**Observable failure.** The continuation route is `CAVE_HANDOFF_ROUTE_BLOCKS = 48`. Underground
pathing over 48 blocks is marginal inside 400 ticks (20s) even from a fresh handoff. On expiry the
intent falls to `NONE`, `ExploringGoal` is reclassified `EXPLORING_ORDINARY` (`NEUTRAL`), and
priority-3 chores become admissible again and outrank priority 8 — **Loop B returns mid-walk**.

**Second-order, worse than losing the cave.** `hasActiveCaveContinuation` is also the only clause
stopping `mayStartControlledDescent` from re-assigning a descent during the walk. When the
commitment expires early the mob does not merely abandon the cave it found — it may start digging a
brand-new staircase while standing next to one.

### MI-14C2-R2 — Cave Handoff Authority Clock (`IMPLEMENTED`, 327 tests)

Separate admission from authority, the same two-clock split C3 already applies to progress.

```text
admission  400 ticks from DISCOVERY   unchanged: is this find still fresh enough to act on?
authority  MAX_EXPEDITION_TICKS from CLAIM   how long may an accepted expedition stay alive?
```

`expiresAt = now + authorityTicks`, and `authorityTicks` is **supplied by the owner of the
continuation's lifetime** (`ExploringGoal.MAX_EXPEDITION_TICKS`, 2400) rather than invented in the
mining package.

**Why the expedition lifetime and not a route-derived number (User, `LOCKED`).** Deriving a smaller
budget from "48 blocks therefore N ticks" would repeat the defect in miniature: authority could
expire while the expedition it protects is still legally running. The continuation *is* an
expedition, its lifetime already exists, and both the completion and abandonment paths clear the
commitment — verified at `ExploringGoal.completeExpedition` and `ExploringGoal.abandon`. So 2400 is a
**ceiling**, and the normal path releases far earlier. A smaller cave-specific bound is admissible
only if it is *proved* to exceed the continuation's own legitimate lifetime.

The constant was made `public` rather than copied, so the two windows cannot drift apart silently
(SPM-0: constants belong to whoever defines them). `claimCaveContinuation` now **requires** the
window as a parameter — no defaulted overload, because a default is exactly how the discovery clock
would creep back.

**Tests (6):** a claim at discovery+399 keeps full authority *after* admission closes; the inverse
— an unclaimed handoff at discovery+400 can never be claimed, so widening authority does not widen
admission; authority still expires for a mob that never arrives; `mayStartControlledDescent` stays
false for the whole protected walk and true once it lapses; intent and goal-kind classification
track the same clock, so arbiter and director cannot disagree; and authority is never shorter than
admission.

Six pre-existing call sites were updated explicitly. One,
`r1_commitmentExpiresWithoutKeepingTransitionPending`, was semantically affected — it measured expiry
from the admission window — and now measures from the authority window with its behaviour under test
unchanged.

### MI-14C2-M2 — predicate drift at the shared boundary (`IMPLEMENTED`, 330 tests)

Found by the MAIBS re-pass over C2-R2, on the tick after it shipped. **Sharing the constant did not
share the lifetime.**

```text
commitment   now < claimedAt + 2400        dead at claim+2400
expedition   now - startedTick > 2400      alive at claim+2400, stale at +2401
```

At exactly `claim + 2400` the expedition was still legally running and its authority had already
lapsed. In that one tick the intent falls to `NONE`, `ExploringGoal` reverts to `EXPLORING_ORDINARY`,
and `mayStartControlledDescent` reopens — both outcomes C2-R2 exists to prevent. Whether the
`GoalSelector` does anything observable in a single cycle is runtime-dependent, and irrelevant:
**MAIBS does not accept an architectural invariant that is false for even one scheduler cycle**
(User).

**Repair — share the semantics, not the number.** `ExploringGoal.expeditionExpired(startedTick, now,
lifetimeTicks)` is now the single definition, called by both expedition-staleness sites and by
`MiningExecutionCommitment.isActive`. The record stores `authorityTicks` (the window) instead of
`expiresAt` (a derived deadline), so there is no second place for a boundary to live. Pre-M2 saves
recover the window from `expiresAt - claimedAt`.

The window stays an explicit parameter, so the predicate is shared without an implicit default —
the property C2-R2 deliberately established.

**Tests (4 new):** the exact `claim + 2400` tick, asserting expedition-alive implies
authority-alive *and* no descent admissible; both must also end together at +2401; an exhaustive
agreement sweep over windows `{0, 1, 40, 400, 2400}` and every age through `window + 2`, so a future
local comparison fails at build time instead of in a one-tick runtime window; and legacy-save window
recovery.

**Lesson (`PROVEN`, promote).** *Deduplicating a constant does not deduplicate a boundary.* Two
correct-looking comparisons over the same shared number produced an off-by-one invariant violation.
Where two subsystems must agree on a lifetime, share the **predicate**. This is the third instance in
this workstream of a defect that survives because two places agree on data and disagree on
interpretation (R2c wiring, C1 grace clock, C2-M2).

### Design question for the user (not a defect)

`CraftTorchesGoal` `YIELD`s under `CONTROLLED_DESCENT`. Torch supply is a descent prerequisite, so a
mob that runs dry mid-staircase cannot craft more until the descent ends. Not a deadlock — the
descent terminates — but it is the "maybe compatible if deliberately required" case raised when the
matrix was specified, and it is still unresolved.

---

## Topic: MI-14 design review (Gate MAIBS-1, pre-implementation)

**Status:** `FAIL — ARCHITECTURE_DEFECT`. The layered design is right; its **entry point is wrong**.
**Analyst:** `Agent_Claude`, snapshot 01:00. Source-verified against the shipped MI-7 stack.

### Reconciliation — what MI-14 actually inherits (`CODE_CONFIRMED`)

| Primitive | Exists | Note |
| --- | --- | --- |
| `MiningProjectMode` | ✅ 7 modes | Only `CONTROLLED_DESCENT` is *activated* by code |
| `MiningProjectEnd` | ✅ 11 reasons → `TaskLifecycle` | See defect 1 |
| `MiningProject` / `MiningProjectSavedData` | ✅ persisted per mob UUID | `projectOf(UUID)` |
| `MiningBudget` / `MiningBudgetUsage` | ✅ | Descent + natural-search defaults |
| `NaturalDescentStatus` / `NaturalDescentExhaustionPolicy` | ✅ `SEARCHING`/`AVAILABLE`/`TEMPORARILY_BLOCKED`/`EXHAUSTED` | `mayStartControlledDescent` |
| `DescentHeadingPolicy`, `DescentPressurePolicy` | ✅ | Heading + pressure |
| `CaveOpportunityPolicy`, `CaveContextPolicy` | ✅ (MI-6F/G) | Commitment + `SpaceKind` classifier |
| `ControlledDescentGoal` | ✅ priority **3**, `MOVE`+`LOOK` | **Already starts and ends projects** |
| `TUNNEL_SEARCH` executor | ❌ | 3 probes: no goal file, no references outside the two enums and the emitter |

**The premise needs correcting.** MI-14 is not new orchestration over inert primitives —
`ControlledDescentGoal` already calls `MiningProject.startControlledDescent(...)`, reads
`projectOf(mobId)`, and emits **seven** distinct `MiningProjectEnd` values. Orchestration exists; it
is embedded in one executor and covers one mode.

### Defect 1 — every terminal handoff is orphaned (`CODE_CONFIRMED`)

| Terminal state | Consumers outside the emitter |
| --- | --- |
| `HANDOFF_TUNNEL_SEARCH` | **0** |
| `CAVE_FOUND` | **0** |
| `SEARCH_BUDGET_EXHAUSTED` | **0** |

`ControlledDescentGoal` ends its project with a precisely named reason and **nothing reads it**. The
mob stops descending, the project record closes, and the GoalSelector falls through to whatever is
next — ordinary gather or explore. `CAVE_FOUND` does not cause cave exploration;
`HANDOFF_TUNNEL_SEARCH` names a destination with no executor.

This is the iron dead end in control flow: **a producer with no consumer.** The handoffs are
currently decoration.

That reframes the task. MI-14's first job is not "choose the next mode" — it is to become **the
missing consumer of terminal states that already exist**. Adding modes before that adds more orphans.

### Defect 2 — a pure-policy director cannot enforce its decision (`CODE_CONFIRMED`)

Priority 3 currently holds **four** `MOVE` goals:

| Goal | Priority | Flags |
| --- | ---: | --- |
| `CraftTorchesGoal` | 3 | MOVE |
| `GatherResourcesGoal` | 3 | MOVE |
| `SmeltAtFurnaceGoal` | 3 | MOVE |
| `ControlledDescentGoal` | 3 | MOVE + LOOK |
| `ExploringGoal` | 8 | MOVE |
| `EnvironmentalEscapeGoal` | 0 | MOVE + LOOK |

`WrappedGoal.canBeReplacedBy` yields a flag only to a **strictly lower priority number**, so equal
priorities cannot preempt each other — whichever starts first holds `MOVE` until it stops on its own
terms. A `MiningDirector` that selects `CAVE_EXPLORATION` while `GatherResourcesGoal` already holds
`MOVE` has made a decision the world will not honour, and nothing reports the discrepancy.

**A director must either own a single executor, or the executors must carry distinct priorities that
encode the mode hierarchy.** The proposed design specifies neither.

### Threshold audit — project ends, who owns the next tick?

| Question | Answer today |
| --- | --- |
| Turns off | `ControlledDescentGoal.canContinueToUse` → releases `MOVE` |
| Turns on | Any priority-3 goal that wants it, by registration order |
| Owns next action | **Undefined** — not the terminal reason |
| Immediate reactivation? | Yes, if descent pressure still holds — the same mode can restart with no memory that it just ended |
| Gap where nothing progresses? | Worse: **wrong-mode progress**. `CAVE_FOUND` ends descent and the mob wanders off instead of entering the cave it just opened |

### Predicted weird behaviours

1. **Cave opened, cave ignored.** `ControlledDescentCaveHandoff.openedTraversableCave` fires,
   project ends `CAVE_FOUND`, no consumer exists, the mob walks away from the opening it just mined.
   `ARCHITECTURE_DEFECT`.
2. **Descent loop.** Ends `SEARCH_BUDGET_EXHAUSTED`; pressure is still true because demand is
   unchanged; re-qualifies and restarts with a fresh budget. Bounded only by cooldown, not by
   memory. `ARCHITECTURE_DEFECT` unless the director records the outcome.
3. **Director talking to itself.** Selects a mode, the executor cannot take `MOVE`, the project
   record says `CAVE_EXPLORATION` while the mob is visibly gathering wood. Diagnostics lie.
   `ARCHITECTURE_DEFECT`.
4. **Tunnel handoff into the void.** `HANDOFF_TUNNEL_SEARCH` with no executor.
   `ACCEPTABLE_STEPPING_STONE` **only** while nothing claims tunnel search works.
5. **Two mobs, one opening.** Projects are per-UUID with no contention model; both commit to the
   same cave mouth. `RUNTIME_QUESTION`.

### Design options

| Option | Content | Trade-off |
| --- | --- | --- |
| **A — director as mode chooser** (as proposed) | New `MiningDirector` policy picks the next mode from demand/band/opportunity | Matches the diagram; but duplicates decisions already inside `ControlledDescentGoal`, and cannot enforce a choice it wins on paper |
| **B — director as terminal-state consumer first** | MI-14a: one place that reads `MiningProjectEnd` and decides what happens next, wiring the three orphans. MI-14b: extract the start/stop logic out of `ControlledDescentGoal`. MI-14c: mode selection, once ≥2 modes have executors | Smaller, ships value immediately (`CAVE_FOUND` finally means something), and each step is independently testable |

**Recommended: B.** Option A's mode table is only meaningful when more than one mode has an executor
— today exactly one does. Building the chooser first produces a system that selects among modes it
cannot run, on top of handoffs nothing consumes.

### Repair to the proposed decision tree

The user's tree is correct in shape and premature in one branch:

```text
blocking diamond demand
  ├ above band?  ── YES ─ natural descent AVAILABLE? ─ YES → explore pressure (exists)
  │                                                   └ EXHAUSTED → CONTROLLED_DESCENT (exists)
  └ in band? ── cave opportunity? ─ YES → CAVE_EXPLORATION   (executor: MISSING - see below)
                                   └ NO  → TUNNEL_SEARCH     (executor: MISSING)
```

`CAVE_EXPLORATION` has no dedicated executor either — cave *continuation* is landing-sort behaviour
inside `ExploringGoal`, not a project mode. So two of the four leaves currently terminate in nothing.
Wire `CAVE_FOUND` → `ExploringGoal` cave continuation first; that leaf becomes real without a new
goal. `TUNNEL_SEARCH` stays deferred until MI-7's stair planner is promoted into an executor.

`VEIN_EXTRACTION`, `TARGETED_RETURN` and `EMERGENCY_EXIT` depend on MI-16, MI-15 and hazard work
respectively; catalogue them, do not branch to them.

### Acceptance

**Must happen:** every `MiningProjectEnd` has exactly one consumer that decides the next action;
the mode recorded in `MiningProjectSavedData` matches the goal actually holding `MOVE`; a project
ending `CAVE_FOUND` results in the mob entering the opening.
**Must not happen:** a mode selected whose executor cannot obtain `MOVE`; a terminal state with no
consumer; the same mode restarting immediately with no record of why the last attempt ended.

**Falsifying runtime probe:** scripted stone shaft, diamond-demanding mob, `ControlledDescentGoal`
active. Log `(tick, mode, end reason, goal holding MOVE, y)` for 2400 ticks. Prediction: at least one
`CAVE_FOUND` or `SEARCH_BUDGET_EXHAUSTED` followed by a goal unrelated to mining, and at least one
descent restart with no intervening progress.

---

## Topic: MI-5 behavioural prediction (Gate MAIBS-1)

**Status:** `FAIL — ARCHITECTURE_DEFECT` (heading blindness remains; defect 1 **repaired** in code).
**Analyst:** `Agent_Claude` + user MAIBS review (2026-08-09).
**Repair task:** **MI-5H** — `DescentHeadingPolicy` (before MI-7D executor).

### Repaired since original review (`CODE_CONFIRMED`)

- **Defect 1 (consume cleared pressure):** `ExplorationReadiness.consume` no longer clears
  `descentPressure`; `ExpeditionState` captures `ExplorationIntent.DESCENT` at creation
  (`ExploringGoal` ~441–446).
- **Defect 2 (no descent identity):** partially addressed — `ExplorationIntent` on expedition;
  completion/handoff semantics still open.

### Primary open defect — heading blindness (`CODE_CONFIRMED` + user `CONSENSUS`)

| Layer | Result |
| --- | --- |
| Intended | Under diamond progression pressure, choose a **macro direction** with legitimate descent opportunity |
| Implemented | `createExpedition` scores headings by novelty/interest/randomness; MI-5 only biases **landing** sort along an already-chosen heading |
| Predicted | "Descend" → novelty heading **EAST** → nearest 1-block depression along that heading |
| Confidence | `CODE_CONFIRMED` |

```text
Need diamonds → descentPressure
      ↓
choose novelty heading EAST          ← MI-5H missing here
      ↓
within heading, prefer Y69 over Y60  ← MI-5 landing sort (depression-hunt)
```

**MI-5H (`DescentHeadingPolicy`) — `READY`:**

```text
DescentPressure
      ↓
candidate headings (N, NE, E, …)
      ↓
bounded geometry evidence per heading (no ore clairvoyance)
      ↓
score: meaningful descent + cave/ravine opportunity + reachability + unexplored value
       − path cost − hazard
      ↓
commit heading (shared by natural descent, CONTROLLED_DESCENT, TUNNEL_SEARCH later)
```

**Must not:** score buried ore; replace MI-6F opportunity commitment.

### Landing-sort defect (secondary — tuning, not heading)

`DescentPressurePolicy.landingPreferenceKey` still prefers **smallest** descent step (Y69 beats Y60).
`ACCEPTABLE_STEPPING_STONE` until MI-5H provides macro direction; may revisit landing key after 5H
probes.

### Original defect notes (historical)

| Layer | Result |
| --- | --- |
| Intended | "I should generally seek lower opportunities" — fallback pressure, not cave knowledge |
| Implemented | `landingPreferenceKey = (landingY < mobY ? 0 : 1) * 1000 + abs(landingY - mobY)`, applied as a landing-candidate sort when `readiness.hasDescentPressure()` |
| Predicted | Mob drifts into the **nearest one-block depression** and calls it descent |
| Confidence | `CODE_CONFIRMED` mechanism; `GAME_MECHANICS_INFERRED` observable |

At Y=70 the keys are: Y69 → **1**, Y66 → **4**, Y60 → **10**. Lowest key wins, so **Y69 beats Y60**.
"Prefer lower terrain" is implemented as "prefer the smallest possible descent". `MAX_LANDING_ELEVATION`
(16) already rejects anything deeper, so the deep-drop risk the conservative key guards against is
**already bounded elsewhere** — the key is paying a second time for protection it does not need.

### Defect 1 — the first stage of a descent expedition never sorts for descent (`CODE_CONFIRMED`)

`ExploringGoal.canUse` calls `readiness.consume(...)` immediately after `createExpedition`, and
`ExplorationReadiness.consume` sets `descentPressure = false`. `planCurrentStage` — and therefore
`landingCandidates` — runs **after** that, in the same tick.

```text
T0    observer sets descentPressure = true      (ExplorationActivityGoal, every 10 ticks)
T0    canUse → eligible → createExpedition
T0    readiness.consume(...)  → descentPressure = FALSE
T0    planCurrentStage → landingCandidates → hasDescentPressure() == false → NORMAL sort
T+10  observer re-sets pressure = true
T+n   hop arrival replans → descent sort finally applies
```

**The one stage that sets the expedition's heading is sorted without descent preference.** Every
later hop gets it. So the mob commits to a direction chosen with no descent input, then shuffles
downhill along it.

### Defect 2 — descent intent lives in the wrong object (`CODE_CONFIRMED`)

Pressure is a field on `ExplorationReadiness`; the journey is `ExpeditionState`, which carries **no
descent intent** (`NOT FOUND`). At Y≤16 the observer clears pressure, but the expedition continues
with its original heading and waypoints, now behaving as ordinary exploration. There is no
completion semantics for "I was descending", so a descent cannot succeed, fail, or hand off — it can
only dissolve.

### Adversarial geometry

| Scenario | Predicted |
| --- | --- |
| Rolling hills, Y70 | Picks Y69 repeatedly; ~1 block per stage; hundreds of blocks travelled for ~10 blocks of depth |
| Flat plains | Almost no candidate is below; sort is a no-op; mob explores normally while "descending" |
| Cliff/ravine edge | A Y=55 landing 15 below is legal (within 16) but loses to any Y=69; the ravine is passed by |
| Cave mouth at Y=68 with surface still Y=70 | Heightmap landing is the **surface**, not the cave floor — invisible to this key entirely (MI-6's job) |
| Already subterranean | `CaveContextPolicy.isCaveLike` branch takes over — descent sort is not the active comparator |

### Threshold audit — Y=17 → Y=16

| Question | Answer |
| --- | --- |
| Turns off | `descentPressure` (observer, next 10-tick tick) |
| Turns on | `isDiamondLocalGatherEligible` → diamond enters gather intent |
| Owns next action | `GatherResourcesGoal` if ore is visible; otherwise the **same** expedition continues |
| Immediate reactivation? | No — but no completion either; the expedition simply loses its reason |
| Gap where nothing progresses? | **Yes** — at depth with no visible ore, the mob explores generically with diamond demand unsatisfied and no descent identity |

### Predicted weird behaviours

1. **Depression-hunting** — mob walks 200 blocks to descend 10, preferring every 1-block dip.
   `ARCHITECTURE_DEFECT` for the stated intent; bounded and non-corrupting.
2. **Heading blindness** — heading is picked from novelty/interest/randomness *before* any terrain
   input, so a mob can commit east while terrain trends down west, then "descend" east.
   `ARCHITECTURE_DEFECT`.
3. **Vertical oscillation** — no descent history exists (`lowestYReached` `NOT FOUND`). Y70→67→70→66→69
   satisfies "below me *right now*" at each replan. `RUNTIME_QUESTION` — frequency unknown, needs a
   flat-world probe.
4. **Companion vertical divergence** — an invited companion inherits the leader's *heading* but sorts
   landings by **its own** pressure, which is false if it wants no diamond. Leader sinks, companion
   stays high, the pair separates. `ACCEPTABLE_STEPPING_STONE` — visible, harmless, arguably charming.
5. **Silent descent death** — pressure clears at Y≤16 mid-expedition and nothing records that a
   descent ever happened. `ARCHITECTURE_DEFECT` (defect 2).

### Design options

| Option | Content | Trade-off |
| --- | --- | --- |
| **A — full proposal** | descent window scoring, `ExplorationIntent`, `lowestYReached`, stall detector, heading terrain-trend bonus | Behaviour looks genuinely purposeful; five coupled changes, and the trend bonus adds surface sampling per route candidate — must be costed against F-6 |
| **B — minimal correctness first** | fix the `consume()` ordering; add `ExplorationIntent{NORMAL,DESCENT}` to `ExpeditionState` | Removes both defects, changes no scoring; the mob still depression-hunts, but descent becomes a *named, terminable* task the later scoring can attach to |

**Recommended: B then A.** The two defects are correctness, not tuning — and A's scoring lands on
firmer ground once a descent expedition is a thing that can be identified and ended. Doing A first
would tune a journey that still cannot report success or failure.

### Boundary with MI-6 (**revised after MI-6 MAIBS**)

`MI-6 concrete opportunity` must **outrank** the MI-5 heuristic when a real underground/ravine
walkable exists. Earlier text claimed the comparators merely “separate”; live code is still
`if (DESCENT) … else if (continueCave)` — so **DESCENT disables cave continuation** (`CODE_CONFIRMED`
`ExploringGoal.landingCandidates`). That is **MI-6D**: combine into `DESCENT_IN_CAVE`, do not if/else.
Without **MI-6A** (non-heightmap cave floors), MI-6 has almost nothing useful to rank anyway.

---

## Topic: MI-6 behavioural prediction (Gate MAIBS-1)

**Status:** `FAIL — ARCHITECTURE_DEFECT` (landing preference no-op + classifier gaps).
**Analyst:** User critique + `Agent_Cursor` (minecraft-ai-behavioral-simulation), 2026-08-08 ~22:35.
**Scope:** Post task-17 semantic-drift review — not a new feature brainstorm in isolation.

### Intent vs mechanism vs predicted behaviour

| Layer | Result |
| --- | --- |
| Intended | Already cave-like → when exploring, prefer landings that **remain underground**; prefer exposed ore in cave/ravine |
| Implemented (landings) | Candidates from `getHeight(MOTION_BLOCKING_NO_LEAVES)` then sorted by `landingPreferenceKey(landingY, mobY, surfaceY)` where `surfaceY` is **the same heightmap** (`CODE_CONFIRMED` `ExploringGoal` ~623–660) |
| Implemented (context) | `isCaveLike = surfaceY - mobY >= 8` at mob column only; gather applies one mob-origin `caveLike` to all 24 candidates |
| Predicted | Inside a true cave, heightmap landings sit on **roof/surface**; `surfaceY - landingY = 0` → every ordinary candidate fails `isCaveLike` → cave sort degrades to “nearest elevation to mob among surface points” — **stay-underground is a no-op** |
| Confidence | Mechanism `CODE_CONFIRMED`; observable `GAME_MECHANICS_INFERRED` / runtime `UNVERIFIED` |

### Defect A — heightmap landings cannot be “under surface” (`CODE_CONFIRMED`)

```text
candidate.y = heightmap(x,z)     // surface / roof top
surfaceY    = heightmap(x,z)     // identical query
isCaveLike(candidate.y, surfaceY) ⇔ (surfaceY - surfaceY) >= 8 ⇔ false
```

MI-6’s “prefer under-surface landing” has **no under-surface candidates** in the ordinary ring.
Task-17 report already admitted heightmap tops cannot follow cave branches — this is that admission
as an architecture defect, not a soft limitation.

Gate MAIBS-1: selected coordinates represent **surface tops**, not cave floors →
`FAIL — ARCHITECTURE_DEFECT`.

### Defect B — open ravine miss (`CODE_CONFIRMED` classifier math)

At the open-sky column of a ravine floor, heightmap ≈ mobY → delta ≈ 0 → **not cave-like**, so neither
gather ore bonus nor explore cave branch activates. Wording “subterranean/ravine” overstates the
heuristic (`ACCEPTABLE_STEPPING_STONE` only if documented; currently oversold → treat as defect for
ravine-first mining story).

### Defect C — mob-only cave context for gather (`CODE_CONFIRMED`)

`GatherResourcesGoal` computes `caveLike` once at origin and passes it into every candidate’s
priority. Ore just inside a cave mouth while the mob stands outside gets **no** bonus; hillside ore
while the mob stands inside gets the cave bonus. Bonus describes mob posture, not opportunity.

### Defect D — MI-5 disables MI-6 landing preference (`CODE_CONFIRMED`)

```text
if (descending) { MI-5 sort }
else if (continueCave) { MI-6 sort }
```

Diamond need + already underground → DESCENT wins → cave continuation ignored — exactly when both
should apply (`DESCENT_IN_CAVE`).

### Geometry simulation (coordinate-level)

| Scenario | Mob | Heightmap candidates | MI-6 sort effect |
| --- | --- | --- | --- |
| Enclosed cave Y=32, surface Y=70 | caveLike true | landings ≈ Y70 (± elev clamp) | all `isCaveLike(landing)=false`; preference useless |
| Open ravine Y=25, rim Y=68 | caveLike **false** (delta 0 at column) | surface/floor tops | no cave path at all |
| Surface plains | caveLike false | normal | MI-6 inert (OK) |
| Cave mouth at Y=68, ore inside | mob may be `caveOrRavine=true` via **local rim** on hillside geometry — not guaranteed (`LIKELY_ARCHITECTURE_GAP`, not proven for every mouth) | gather `caveOpportunity` per-candidate helps; **route/opening** still needs `CaveOpportunity` (MI-6F) |

### Time loop (prolonged)

```text
T0     mob deep, explore hop → heightmap landing toward surface/roof
T+60   gather may fire if exposed ore in radius (priority-3) — this part of MI-6 still helps
T+200  explore resumes → again only surface landings → climbs / exits cave
T+1200 oscillation: mine a bit, walk to heightmap “lower” surface point, lose depth
```

Gather ore-bonus while caveLike remains the only **partial** useful MI-6 piece
(`ACCEPTABLE_STEPPING_STONE` for gather sorting only).

### GoalSelector interaction

| Goal | Pri | Flags | Interaction |
| --- | ---: | --- | --- |
| GatherResourcesGoal | 3 | MOVE | Can preempt explore when ore in radius — OK |
| ExploringGoal | 8 | MOVE | Owns travel; landing set is the defect |
| ExplorationActivityGoal | 9 | none | Sets descent pressure / cave observer only |

### Predicted weird behaviours

1. **Cave exit magnet** — heightmap-only landings — **mitigated in code by MI-6A** (runtime `UNVERIFIED`).
2. **Ravine blindness** — **mitigated in code by MI-6B** (runtime `UNVERIFIED`).
3. **Branch thrash without commitment** — no short-lived CaveOpportunity → left/right/surface flicker when multiple openings. `RUNTIME_QUESTION` / mitigated by MI-6F (**deferred**).
4. **False cave under house** — Y60 under roof Y70 → caveLike true → basement treated as cave. `ACCEPTABLE_STEPPING_STONE` until MI-6G snapshot (**deferred**).
5. **DESCENT ignores real cave floor** — **mitigated in code by MI-6D** (`DESCENT_IN_CAVE`; runtime `UNVERIFIED`).

### Repair package (ordered)

| ID | Change | MAIBS role | Status |
| --- | --- | --- | --- |
| **MI-6A** | Local 3D cave landing resolver (X±4, Z±4, Y±6 around hop; 2-high air + solid floor + fluids + ticking + path) | Supplies real underground candidates | **`IMPLEMENTED`** (task 18; unit; runtime `UNVERIFIED`) |
| **MI-6D** | Combine MI-5+MI-6 → `NORMAL` / `DESCENT` / `CAVE_CONTINUATION` / `DESCENT_IN_CAVE` | Stops if/else exclusion | **`IMPLEMENTED`** (task 18) |
| **MI-6B** | Local-rim / open-ravine detection (sample N/E/S/W…; rim − mobY) | Fixes ravine classifier | **`IMPLEMENTED`** (task 18) |
| **MI-6C** | Candidate-specific cave context (not only mob origin) | Honest ore opportunity ranking | **`IMPLEMENTED`** (task 18) |
| **MI-6F** | Short-lived `CaveOpportunity` commitment | Stops branch thrash; **prerequisite for MI-7C** | **`READY`** — wire before MI-7B+C |
| **MI-6G** | `CaveContextSnapshot` (belowLocalTerrain, skyVisible, enclosureDepth, localRimDepth) | Extensible classifier | **`DEFERRED`** (user) |
| **MI-6E** | Replace flat +15 with ranked comparator dimensions | Prep for MI-17 | **`DEFERRED`** (MI-17; user) |

**Rejected for this package:** full cave mapper, MiningMemory, x-ray, Baritone.

### Behavioral Prediction — MI-6A (Gate before implement)

**Probable physical behavior:** When `isCaveLike(mob)`, hop planning adds up to N local standable cave-floor positions near the intended X/Z; vanilla `createPath` must reach them; heightmap tops remain fallback. Player sees mob walk to nearby cave floor, not pop to sky.

**Over time:** T0 cave-like → 3D candidates; T+path → gather may preempt on exposed ore; if path fails → try next candidate / heightmap fallback; leave cave → stop 3D resolver.

**Must happen:** ≥1 non-heightmap landing with `landingY + 8 ≤ localSurface` admitted when such floor exists in the ±4/±6 volume and path succeeds.
**Must not happen:** buried ore targeting; scanning beyond local volume; committing to unticking chunks; preferring lava/water stands.

**Weird risks:** picking one-block alcoves; pathing to cave roof air mistaken as stand; probe budget explosion — bound probes (e.g. ≤16) and reuse `safeStand`.

**Falsifying runtime probe:** place PlayerMob in scripted cave at Y=32 under Y=70 stone; log chosen landing Y for 3 hops. Prediction without 6A: landings ≥~54 or surface. With 6A: landings near 32±6.

**Options:** (A) 3D resolver as above — recommended; (B) only bias heightmap by sampling lower columns — still mostly surface; **reject B** as insufficient for MAIBS-1.

**Gate for MI-6A plan:** `BEHAVIORALLY_PLAUSIBLE` if resolver + path + bounds shipped; current MI-6 landings remain `FAIL` until then.

### Acceptance for the package

**Must happen:** subterranean explore can select a standable floor with `surface − y ≥ 8`; open ravine recognized via rim; DESCENT_IN_CAVE prefers deeper *cave* floors; gather scores candidate cave context.
**Must not happen:** clairvoyant ore; unbounded 3D flood fill; MI-7 excavation before natural descent **EXHAUSTED** (not merely `candidate == null`).

---

## Topic: MI-7 controlled excavation descent (Gate MAIBS-1)

**Status:** `CONSENSUS` redesign (user critique + Agent_Cursor, 2026-08-09). Supersedes prior
“MI-7 = 1×2 staircase max N blocks” scheduling. **Not authorized for implementation** until explicit
`Begin implementation for MI-7A` (or full package).

### Purpose (player-like, bounded)

When a **blocking deep-resource demand** still requires depth and **legitimate natural descent has
been exhausted**, create a **safe, bounded, reversible downward route** — not strip mining, not
dig-to-bedrock, not clairvoyant ore search.

```text
Need deeper resource (blocking progression demand)
        ↓
MI-5: descent still needed?
        ↓ YES
NaturalDescentStatus (MI-7C)
  AVAILABLE / SEARCHING        → MI-6 natural cave/ravine continuation
  TEMPORARILY_BLOCKED          → wait / reposition (path, combat, hazard) — NOT MI-7
  EXHAUSTED                    → MI-7 ControlledDescentProject (MI-7E)
        ↓
  choose heading (MI-7D / shared with TUNNEL_SEARCH policy)
        ↓
  validate next StairStepPlan → safe break → move → verify
        ↓
  re-evaluate each step
        ├─ useful cave found      → CAVE_EXPLORATION / MI-6
        ├─ target band reached    → TUNNEL_SEARCH if demand still blocking
        ├─ exposed resource       → gather / VEIN_EXTRACTION
        ├─ budget exhausted       → STOP / reposition / explore elsewhere
        └─ hazard                 → EMERGENCY_EXIT / INTERRUPTED
```

**Boundary (`CONSENSUS`):** MI-7 reaches an **environment** where finding diamond becomes reasonable
(exposed surfaces, cave branches, deepslate band). It does **not** satisfy diamond demand by targeting
buried ore (D-MIW-008 unchanged).

### Why the old MI-7 schedule failed

| Defect | Consequence | Fix |
| --- | --- | --- |
| `candidate == null` treated as “no caves” | Dig through floor beside cave entrance | `NaturalDescentStatus.EXHAUSTED` only after bounded search budget (MI-7C) |
| MI-7 before `MiningProject` (MI-14) | Duplicate session state or rewrite | **MI-7A** minimal project slice first |
| `maxBlocks` only (MI-19 later) | 64-block suicide geometry | **MI-7B** shared multi-axis `MiningBudget` (D-MIW-010) |
| Full MI-18 hazards later | Lava/gravel/fall deaths on stairs | **MI-7D** minimum pre-break safety gate |
| “1×2 staircase” underspecified | Wrong break order → bad footing/headroom | **MI-7D** `StairStepPlan` primitive |
| No heading policy | Random downward dig | Shared heading selection with `TUNNEL_SEARCH` |
| Depth without search | Y=−52, no exposed diamond, “success” | Handoff to `TUNNEL_SEARCH` / `CAVE_EXPLORATION` |

### CaveContext vs CaveOpportunity (`CONSENSUS` — user 2026-08-09)

| Concept | Answers | Owner | Status |
| --- | --- | --- | --- |
| **CaveContextSnapshot** | "What kind of space am I standing in?" | `CaveContextPolicy` | Partial in code; MI-6G deferred |
| **CaveOpportunity** | "Which traversable opening/route should I commit to?" | `CaveOpportunityPolicy` + MI-6F wire | Policy exists; **not wired** to explore |

**Do not conflate.** Position classification (`mob outside → caveOrRavine=false`) is insufficient for
mouth geometry in all cases; opportunity classification must evaluate **candidate routes/openings**.

### NaturalDescentStatus (`CONSENSUS` — D-MIW-034, revised user 2026-08-09)

Bundled with **MI-7B+C** — budget counters alone are meaningless without exhaustion evidence.

```java
public enum NaturalDescentStatus {
    SEARCHING,            // valid descent expedition still has unexplored opportunities
    AVAILABLE,            // legitimate reachable natural descent exists right now
    TEMPORARILY_BLOCKED,  // opportunity exists but combat/path/hazard blocks use
    EXHAUSTED             // may start CONTROLLED_DESCENT (MI-7E) — all gates below
}
```

| Status | Meaning | MI-7 may start? |
| --- | --- | --- |
| **SEARCHING** | Active descent expedition; unexplored headings/landings remain within search budget | No |
| **AVAILABLE** | Reachable natural descent (cave floor, ravine continuation, committed `CaveOpportunity`) | No — use MI-6 |
| **TEMPORARILY_BLOCKED** | Candidate exists; path/combat/hazard/interrupt prevents use | No — backoff |
| **EXHAUSTED** | All below satisfied | Yes (MI-7E) |

**`EXHAUSTED` requires ALL (`CONSENSUS`):**

1. Bounded **search budget** consumed (`MiningBudget` / natural-descent budget — MI-7B).
2. **No** valid natural descent found (no `AVAILABLE` route under evidence rules).
3. **No** active `CaveOpportunity` commitment (MI-6F must be live before exhaustion runs).
4. **Spatial progress / coverage** occurred — not merely N path failures while standing still.

**Rejected exhaustion triggers:**

| Trigger | Why rejected |
| --- | --- |
| `candidate == null` on one tick | Perception failure ≠ area exhausted |
| `maxFailedSteps` alone while mob barely moved | Declares exhaustion without search |
| elapsed time only | No evidence about what was searched |

**Must not:** mob stands still, fails path creation 5×, declares `EXHAUSTED`, digs through floor.

### MI-7B+C — one semantic unit (`CONSENSUS` — D-MIW-036)

| Part | Deliverable |
| --- | --- |
| **MI-7B** | `MiningBudget` usage counters + per-axis exhaustion predicates |
| **MI-7C** | `NaturalDescentExhaustionPolicy` — status machine above; feeds MI-7E gate |

**Why bundle:** MI-7B counters are only meaningful when MI-7C interprets them as *"we looked enough;
natural descent is genuinely exhausted."* Neither alone changes observable behaviour until MI-7E.

**Depends on:** MI-7A `IMPLEMENTED`; **MI-6F live wiring** (exhaustion must see committed opportunity).

### MiningProject slice (MI-7A — `IMPLEMENTED`)

Minimal session state **before** any deliberate dig (not full MI-14 director):

```java
// Policy record — SavedData or mob NBT slice; not a registered Goal
MiningProject {
    MiningProjectMode mode;          // CONTROLLED_DESCENT | TUNNEL_SEARCH | …
    BlockPos origin;
    BlockPos lastSafeAnchor;
    int currentDepth;                // feet Y or delta from origin
    Direction heading;
    MiningBudget budget;
    MiningProjectEnd startReason;
    MiningProjectEnd stopReason;     // nullable while RUNNING
    Deque<BlockPos> coarseReturnRoute; // optional
}
```

`GatherResourcesGoal` still owns each physical break; project owns **intent and budgets**.

### MiningBudget (MI-7B — part of MI-7B+C package)

Shared abstraction (D-MIW-010 constants; MI-19 **generalizes**, does not invent):

| Field | Example cap | Notes |
| --- | --- | --- |
| `maxBlocksMined` | 64–128 | Per trip |
| `maxDistanceFromAnchor` | 48 | Horizontal + vertical from `origin` |
| `maxTicks` | 2400 | ~2 min |
| `maxFailedSteps` | 3 | Path/dig/safety rejections |
| `maxVerticalProgress` | optional | Prevent runaway depth in one trip |

Exhausted → `SEARCH_BUDGET_EXHAUSTED` / reposition — never infinite tunnel.

### StairStepPlan (MI-7D — `READY`)

Geometry primitive — not “break these blocks” ad hoc:

```java
StairStepPlan {
    BlockPos standCell;
    BlockPos nextStandCell;
    List<BlockPos> requiredBreaks;   // ordered
    int resultingHeadroom;             // must be ≥ 2
    int resultingDrop;                 // must be ≤ 1 (configurable)
}
```

**Executor loop (`CONSENSUS`):** `VALIDATE` → `BREAK` (headroom, then forward) → establish footing →
`MOVE` → `VERIFY` arrival. Reject if post-break footing/return path invalid.

**Minimum pre-break safety (MI-7D; full MI-18 extends later):**

- Liquid immediately behind target?
- Falling block hazard (gravel/sand)?
- Unsupported drop > 1?
- Unbreakable / protected?
- Tool `canHarvest`?
- Safe standing cell + 2-high headroom after step?
- Return path to `lastSafeAnchor` preserved (D-MIW-009)?

### Heading selection (`CONSENSUS` — shared primitive)

**MI-5H** owns macro heading for descent expeditions. **MI-7D** reuses the same primitive for
`CONTROLLED_DESCENT` and later `TUNNEL_SEARCH` — do not bury heading only inside staircase executor.

```text
candidate headings (cardinal + continue travel vector)
  → bounded geometry evidence per heading
  → score: descent opportunity + cave/ravine opportunity + reachability + novelty
           − path cost − hazard
  → deterministic tie-break → commit
```

### Revised implementation order (`CONSENSUS` — user 2026-08-09)

```text
DONE     MI-7A  MiningProject foundation
         ↓
NEXT     MI-6F  CaveOpportunity live wiring (policy exists; explore/gather integration)
         ↓
NEXT     MI-7B+C  budget accounting + NaturalDescentExhaustionPolicy (one package)
         ↓
         MI-5H  DescentHeadingPolicy (before executor geometry)
         ↓
         MI-7D  StairStepPlan + min excavation safety
         ↓
         MI-7E  Controlled-descent executor
```

| ID | Deliverable | Depends | Status |
| --- | --- | --- | --- |
| **MI-7A** | Minimal `MiningProject` + `CONTROLLED_DESCENT` | MI-6A/D | **`IMPLEMENTED`** (task 20) |
| **MI-6F** | `CaveOpportunityPolicy` wired to explore branch choice | MI-6A/D | **`READY`** — **before MI-7B+C** |
| **MI-7B+C** | `MiningBudget` usage + `NaturalDescentExhaustionPolicy` | MI-7A, MI-6F | **`READY`** — one Begin package |
| **MI-5H** | `DescentHeadingPolicy` — macro heading under descent pressure | MI-5, MI-6 | **`READY`** — before MI-7D |
| **MI-7D** | `StairStepPlan` + min excavation safety | MI-7B+C, MI-5H | **`READY`** |
| **MI-7E** | Controlled staircase executor | MI-7A, MI-7B+C, MI-5H, MI-7D | **BLOCKED** |

**Later:** MI-11 torch; MI-18 hazards; MI-19 budget expansion; MI-14 director; MI-6G snapshot.

### Task split (superseded table — retained for traceability)

### Behavioral Prediction — MI-7E (Gate before implement)

**Probable physical behavior:** Mob beside cave with `SEARCHING` status continues MI-6; only after
bounded failed natural attempts does it start a visible 1×2 stair at a **committed heading**, pausing
on lava/gravel, stopping at budget or on breaking into a cave.

**Must happen:** no `CONTROLLED_DESCENT` when `NaturalDescentStatus != EXHAUSTED`; each step leaves
walkable 2-high route; project records `lastSafeAnchor`; handoff to `TUNNEL_SEARCH` when band reached
with demand still blocking.

**Must not happen:** dig through floor on first null landing; straight vertical shaft; strip mine;
clairvoyant ore target; infinite dig past budget.

**Falsifying runtime probe:** PlayerMob at surface near known cave mouth; force MI-6 path fail for
3 hops — must **not** start digging. Move to flat area with no caves; after exhaustion policy
fires — **may** start staircase; log `NaturalDescentStatus` transitions and `MiningProject` fields.

**Gate:** `BEHAVIORALLY_PLAUSIBLE` only after MI-7A–E + exhaustion policy + runtime probe pass.

### Rejected alternatives

| Option | Why rejected |
| --- | --- |
| MI-7 as standalone Goal with local counters | Duplicates MI-14; rewrite risk |
| Start MI-7 on `candidate == null` | Cave-entrance floor-dig failure mode |
| `maxBlocks` only | Insufficient — needs distance/ticks/failures |
| MI-7 before `MiningProject` | Architecture violation (D-MIW-001) |
| MI-7 finds buried diamond | Anti-clairvoyance violation |

---


**Status:** F-1 **Option A `LOCKED`** + **MI-4S `IMPLEMENTED`** (task 15); F-2/F-4/F-5 `CONSENSUS`; F-3/F-6 still open.
**Blocks:** none for MI-4 acceptance on scale; runtime still `UNVERIFIED`.

### F-1 — Wealth utility and acquisition cost are on different scales (`CODE_CONFIRMED`)

The locked gen-1 profiles produce utilities in `0…~3`; acquisition cost is described in `0…40+`
with an exposed vein costed at `3`. Executed against the shipped formulas
(`ResourceWealthPolicy.wealthValue` / `opportunityBonus`), iron at the recommended `greed = 0.55`,
`wealthLevel = 1`:

| Held | Cost | wealth | bonus | **net (pre-A, BROKEN)** |
| ---: | ---: | ---: | ---: | ---: |
| 0 | 0 | 0.227 | 0.227 | **+0.454** |
| 0 | **3** | 0.227 | 0.180 | **−2.593** |
| 0 | 10 | 0.227 | 0.071 | **−9.702** |
| 12 | 3 | 0.125 | 0.099 | **−2.776** |

**Maximum achievable iron net utility under the broken formula is ≈ 0.45, at zero cost.** Any real
cost made optional iron permanently negative. Worked-example absolutes (15/18/3) are **illustrative
ratios only** under Option A.

**This already affects merged code** until MI-4S: candidate admission uses
`evaluateWealth(...).netUtility() > 0` with raw cost subtract.

#### Decision D-MIW-028 — F-1 normalisation (`LOCKED` — Option A)

**User lock (2026-08-08):** **Option A — Unit desire × proximity.**

| Option | Formula | Keeps D-MIW-026 profiles? | Status |
| --- | --- | --- | --- |
| **A — Unit desire × proximity** | `desire = wealthValue(inventory)` (no cost). `detourBudget = 8 + greed×12`. `proximity = max(0, 1 − cost/detourBudget)`. **`acquisitionUtility = desire × proximity`** (admit if `> 0`). Drop raw `− acquisitionCost` from net. | **Yes** | **`LOCKED`** |
| B — Scale utilities to 0…100 | Multiply desire so iron ≈15; keep desire+bonus−cost | No | **Rejected** (not chosen) |
| C — Raise profile constants until cost 3 nets + | Keep broken formula; bump constants | Nominally | **Rejected** |

**Must-happen acceptance (unit tests on policy after MI-4S):**

| Case | greed / wealthLevel | held iron | cost | Must |
| --- | --- | --- | --- | --- |
| Nearby vein | 0.55 / 1 | 0 | 3 | `acquisitionUtility > 0` |
| Far expedition | 0.55 / 1 | 0 | 35 | `acquisitionUtility == 0` (proximity floor) |
| Defaults | 0 / 0 | any | any | desire 0; consumer-only parity |
| Saturated | 0.55 / 1 | ≥ saturation | 3 | desire near floor; MI-4R scan gate stays |

**Must-not:** reintroduce `wealthRawIron`; subtract raw block-distance from sub-1 utilities; apply cost inside desire *and* again as a second full subtract (obey D-MIW-029).

**Switch evidence:** if playtests show greed=0.55 never takes exposed iron at cost≈3 under Option A, reopen for Option B. If Option A causes constant background wealth scans, tighten with F-6.

**Repair task:** **MI-4S** — `IMPLEMENTED` (task 15, 158 tests). Runtime still `UNVERIFIED`.

### F-2 — The Y-band gate destroys the signal needed to descend (`CODE_CONFIRMED`) → D-MIW-031 `CONSENSUS`

MI-2 specifies *"no deep-ore intent above Y=16 without sighting"*, and MI-5 depends on
*"deficit + zero local ore → nudge `ExploringGoal`"*. These contradict: the shipped
`WorkDemandPolicy.diamondDeficit` returns **0** above `DIAMOND_GENERATION_CEILING_Y`, so a surface
mob has no diamond demand for the director to act on. **Nothing can ever motivate a descent.**

**Locked split — two signals, not one:**

| Signal | Above the band | Drives |
| --- | --- | --- |
| `ProgressionDemand(DIAMOND)` | **> 0** — the mob still wants diamond | exploration / descent pressure, `MiningDirector` |
| `LocalGatherEligibility(DIAMOND)` | **false** unless legitimately sighted | candidate scanning, target selection |

MI-2 task 14 delivered priority sort; **MI-5 (task 16) applied the two-signal split in code** —
progression unlocks explore descent pressure; local gather remains Y-gated.

### F-3 — IRON wealth does not distinguish raw iron from ingots (`CODE_CONFIRMED`)

Still open design (stage vs dual stack). Does **not** block F-1 lock or MI-4S. Track for MI-24 accounting follow-up.

### F-4 — `ResourceWealthPolicy` answers two different questions (`CODE_CONFIRMED`) → D-MIW-029 `CONSENSUS`

**Locked boundary:**

```text
ResourceWealthPolicy   → ResourceDesire          (inventory-only; how much do I want one more)
AcquisitionUtility     → desire × proximity(cost) − danger/inventory pressures
                         (is this particular acquisition worthwhile?)
```

Desire must not embed path distance. Candidate scorers and MI-17 consume `AcquisitionUtility`, not a
second raw cost subtract on the same term. Option A of D-MIW-028 is the gen-1 expression of this
boundary; Option B must still obey it.

### F-5 — Wealth-only expeditions are self-contradictory → D-MIW-030 `CONSENSUS`

**Locked invariant:** wealth may make a mob *take* nearby/easy resources and *return to a remembered
sighting*. A **dedicated expedition requires `BLOCKING`, `REPLACEMENT`, `PROJECT` or `RESERVE`
demand.** Feeling rich is not a reason to launch a cave trip. Aligns Cave Exploration wording with
the earlier no-wealth-expedition rule.

### F-6 — There is no perception budget, only a mining budget (`CODE_CONFIRMED`)

Still open (D-MIW-032 `PROPOSED`). Required before anything that raises scan frequency (director).
Does not block F-1 / MI-4S.

| Perception cost today | Value |
| --- | --- |
| Scan cadence | every `SCAN_INTERVAL` **60** ticks, per mob |
| Positions per scan | `(2r+1)² × 9` — **~3,969** at radius 10, **~15,129** at radius 20 |
| Candidate buffer | `MAX_CANDIDATES` **24** |
| Path probes per scan | `MAX_PATH_PROBES` **3** |
| Per-mob stagger / jitter | **none** — `NOT FOUND` |
| Cross-tick continuation | none; the scan is synchronous |

### Disposition

| Finding | Status | Blocks | Owner |
| --- | --- | --- | --- |
| F-1 scale | **D-MIW-028 Option A `LOCKED`** + MI-4S done | — | Locked + applied |
| F-2 band split | **D-MIW-031 `CONSENSUS`** | MI-5 / MI-14 implementation shape | Design locked; code later |
| F-3 iron stage | Open | Later accounting | Design |
| F-4 boundary | **D-MIW-029 `CONSENSUS`** | MI-4S / MI-17 | Design locked |
| F-5 expedition rule | **D-MIW-030 `CONSENSUS`** | MI-14 | Design locked |
| F-6 perception budget | D-MIW-032 `PROPOSED` | Director / scan growth | Design |

**MI-4 / MI-4R / MI-4S:** wealth path **accepted** under Option A at unit level. Runtime `UNVERIFIED`.

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

### Gen-1 opportunity formula (MI-25 — pairs with D-MIW-026; **D-MIW-028 Option A `LOCKED`**)

```text
desire = wealthValue(inventory)                         // no path cost inside desire
detourBudget = 8 + greed * 12
proximity = max(0, 1 - acquisitionCost / detourBudget)
acquisitionUtility = desire * proximity                 // Option A — admit if > 0

// REMOVED (broken pre-A):
// netUtility = wealthValue + opportunityBonus - acquisitionCost
```

Live cost today: `sqrt(distSq)/8` in `GatherTargetPolicy`. Worked-example absolutes (15/18/3) are
**illustrative ratios only**.

Mine when `blockingDemand + reserveValue + acquisitionUtility - inventoryPressure > 0` (NEED layers still dominate).
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
| Discovery classification | MI-13 `IMPLEMENTED` | `DiscoveryMode` + harvest reveal |
| MiningDirector + project | MI-14 | Session modes, budgets, interrupts |
| MiningMemory | MI-15 | Cave entrances, branches, sightings |
| Vein frontier | MI-16 | Generic `ResourceTarget` vein follow |
| Ore utility scoring | MI-17 | Side-ore detour while blocking search |
| Hazards + durability | MI-18 | Lava, gravel, tool swap, preemptive replace |
| Band gate | MI-2 + MI-5 `IMPLEMENTED` | Local gather Y-gated; progression drives descent |
| Target priority | MI-2 `IMPLEMENTED` | Blocking > wealth among legitimate candidates |
| Explore downward bias | MI-5 `IMPLEMENTED` | Descent pressure unlocks explore + lower landings |
| Cave opportunism | MI-6A/D/B/C `IMPLEMENTED` | 3D floors + rim + modes; runtime `UNVERIFIED`; 6E/F/G deferred |
| Controlled excavation descent | MI-7A…E | `MiningProject` + `EXHAUSTED` gate; `StairStepPlan` |
| Torch pairing underground | MI-11 | `PlaceTorchGoal` + coal demand loop |
| Search budget | MI-7B / MI-19 | `MiningBudget` in MI-7B; MI-19 generalizes |

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
    CONTROLLED_DESCENT, // bounded staircase when natural descent EXHAUSTED (MI-7E)
    TUNNEL_SEARCH,      // controlled search heading when caves dry up
    VEIN_EXTRACTION,    // follow legitimately exposed vein
    TARGETED_RETURN,    // return to MEMORY sighting worth detour
    EMERGENCY_EXIT      // hazard / tool / food — seek safe anchor
}
```

Gen-1 activates `SURFACE_EXPOSED` + `CAVE_EXPLORATION` today; `CONTROLLED_DESCENT` activates with MI-7E.

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
  ├─ NaturalDescentStatus?
  │     AVAILABLE / SEARCHING     → MI-6 / CAVE_EXPLORATION
  │     TEMPORARILY_BLOCKED       → reposition; do not MI-7
  │     EXHAUSTED                 → CONTROLLED_DESCENT (MI-7E) if still blocking
  │
  ├─ Caves dry, demand blocking, in target band? ──YES──► TUNNEL_SEARCH (budgeted)
  │
  └─ Budget exhausted / no safe step? ──► SEARCH_BUDGET_EXHAUSTED — never infinite dig
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
| **P5** | Explore downward bias (MI-5) | P3 | **`IMPLEMENTED`** (task 16) |
| **P6** | Cave opportunism + `MiningMemory` (MI-6, MI-15) | P5 | **PARTIAL** — MI-6A/D/B/C done; MI-15 deferred |
| **P7a** | Minimal `MiningProject` session (MI-7A) | P6 | **`IMPLEMENTED`** (task 20) |
| **P6f** | `CaveOpportunity` live wiring (MI-6F) | P6 | **`READY`** — before MI-7B+C |
| **P7bc** | Budget + natural-descent exhaustion (MI-7B+C) | P7a, P6f | **`READY`** |
| **P5h** | `DescentHeadingPolicy` (MI-5H) | P5, P6 | **`READY`** — before MI-7D |
| **P7d** | `StairStepPlan` + min excavation safety (MI-7D) | P7bc, P5h | **`READY`** |
| **P7e** | Controlled staircase descent (MI-7E) | P7a, P7bc, P5h, P7d | **BLOCKED** |
| **P8** | `MiningDirector` orchestration (MI-14) | P7e | **PARTIAL** — C1/C2/C3 control plane implemented; tunnel-search executor absent |
| **P9** | `VeinFrontier` + ore utility (MI-16, MI-17, MI-21) | P8 | **FULL** |
| **P10** | Hazards + durability + tool switch (MI-18, MI-20) | P7d, P8 | **PARTIAL** — MI-7D min safety first |
| **P11** | Search budget expansion + abandon reasons (MI-19) | P7b, P8 | **FULL** — extends MI-7B `MiningBudget` |
| **P12** | `RequirementResolver` v1 (MI-8) | P4, P9 | **PARTIAL** |
| **P13** | Unit tests + runtime datapack (MI-9, MI-10) | P4 | **FULL** |
| **P14** | Torch-gated shaft lighting (MI-11) | P7e | **PARTIAL** |
| **P15** | Cross-RFC vanilla resolver merge (MI-12) | `RFC-VANILLA` | **PARTIAL** |
| **P16** | Mining personalities (MI-22, deferred) | P9 | **DEFERRED** |
| **P17** | Nether/deepslate branch | Portal RFC | **NOT PRACTICAL** |

### Gen-1 slice (D-MIW-025 `CONSENSUS`, revised)

```text
DONE:     MI-7A (task 20; 200 tests)
NEXT:     MI-6F live wiring → MI-7B+C (one package) → MI-5H → MI-7D → MI-7E
DEFER:    MI-6E/G; MI-15 MiningMemory; full MI-14 director
BLOCKED:  MI-7E until 6F + 7B+C + 5H + 7D
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
| MI-4 | P4 | Gather + config wire wealth without replacing consumer specs | `IMPLEMENTED` — accepted after MI-4S |
| MI-4S | P4b | Apply locked D-MIW-028 Option A (+ D-MIW-029 boundary) | **`IMPLEMENTED`** (task 15; 158 tests) |
| MI-5 | P5 | Explore downward bias | **`IMPLEMENTED`** (task 16; 165 tests) |
| MI-6 | P6 | Cave opportunistic ore (gather bonus + heightmap cave sort) | `IMPLEMENTED` — MAIBS defects → 6A package |
| MI-6A | P6a | Local 3D cave landing resolver | **`IMPLEMENTED`** (task 18; 178 tests; runtime `UNVERIFIED`) |
| MI-6D | P6a | `DESCENT_IN_CAVE` combined sort (not if/else) | **`IMPLEMENTED`** (task 18) |
| MI-6B | P6b | Local-rim open-ravine detection | **`IMPLEMENTED`** (task 18) |
| MI-6C | P6b | Candidate-specific cave context for gather | **`IMPLEMENTED`** (task 18) |
| MI-6F | P6f | `CaveOpportunityPolicy` wired to explore | **`READY`** — before MI-7B+C |
| MI-6G | P6c | `CaveContextSnapshot` fields | **`DEFERRED`** |
| MI-6E | P6d | Replace +15 with ranked comparator | **`DEFERRED`** (MI-17 prep) |
| MI-7A | P7a | Minimal `MiningProject` + `CONTROLLED_DESCENT` mode | **`IMPLEMENTED`** (task 20) |
| MI-7B+C | P7bc | Budget usage + `NaturalDescentExhaustionPolicy` | **`READY`** — one package |
| MI-5H | P5h | `DescentHeadingPolicy` — macro descent heading | **`READY`** — before MI-7D |
| MI-7D | P7d | `StairStepPlan` + min excavation safety | **`READY`** |
| MI-7E | P7e | Controlled staircase executor | **BLOCKED** until 6F + 7B+C + 5H + 7D |
| MI-7B | — | *(merged into MI-7B+C)* | — |
| MI-7C | — | *(merged into MI-7B+C)* | — |
| MI-7 | — | *(superseded)* | Split into MI-7A…E (2026-08-09) |
| MI-8 | P12 | `RequirementResolver` v1 | `BLOCKED` |
| MI-9 | P13 | Unit tests U-MIW-* | `PARTIAL` — MI-13/MI-2 policy tests added; full U-MIW matrix open |
| MI-10 | P13 | Runtime datapack | `BLOCKED` |
| MI-11 | P14 | Shaft torch pairing | `BLOCKED` |
| MI-12 | P15 | Vanilla RFC integration | `BLOCKED` |
| MI-13a | P3a | Exposure in pass-one for ore | `IMPLEMENTED` (task 11) |
| MI-13 | P7b | `DiscoveryMode` classification enum + diagnostics | `IMPLEMENTED` (task 14) |
| MI-14 | P8 | `MiningDirector` orchestration (extends MI-7A project) | `BLOCKED` until MI-7A |
| MI-14C1 | P8 | Assignment/start lease and revocation | **`IMPLEMENTED`** |
| MI-14C2 | P8 | Intent arbitration + scheduler contention | **`IMPLEMENTED`** (task-29 repair; runtime `UNVERIFIED`) |
| MI-14C3 | P8 | Observable-progress lease | `IMPLEMENTED`; R1 static MAIBS repair complete; runtime `UNVERIFIED` |
| MI-14C3-R1 | P8 | Protected/conflicting scheduler lease semantics + reachable progress timeout | **`IMPLEMENTED`** — task-30; C3-F1…F7; 321 tests |
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
| D-MIW-010 | Search budget | `CONSENSUS` | Cap blocks/distance/time/failures; **implement in MI-7B**; MI-19 extends |
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
| D-MIW-025 | Gen-1 slice | `CONSENSUS` | MI-13a→MI-24→MI-25→MI-4R→MI-13→MI-2; MI-4S after F-1 |
| D-MIW-026 | Gen-1 profile v1 | `CONSENSUS` | Locked constants + marginal curve; parity at greed=0 |
| D-MIW-027 | Perception legitimacy | `CONSENSUS` | Exposure in `isCandidate` before MI-13/24/25 |
| D-MIW-028 | F-1 utility/cost scale | **`LOCKED` Option A** | desire×proximity; keep D-MIW-026; no raw −cost; B/C rejected |
| D-MIW-029 | F-4 desire vs acquisition | `CONSENSUS` | Desire inventory-only; acquisition applies cost once |
| D-MIW-030 | F-5 wealth expeditions | `CONSENSUS` | Wealth opportunism only; expeditions need NEED layers |
| D-MIW-031 | F-2 band signal split | `CONSENSUS` | ProgressionDemand vs LocalGatherEligibility |
| D-MIW-032 | F-6 perception budget | `PROPOSED` | Cadence, positions, probes, stagger, cross-tick |
| D-MIW-033 | Controlled excavation descent | **`CONSENSUS`** | MI-7 is `MiningProject` mode; MI-7A…E order; not standalone dig goal |
| D-MIW-034 | Natural descent exhaustion | **`CONSENSUS` revised** | Evidence-based `EXHAUSTED`; spatial coverage required; MI-6F prerequisite |
| D-MIW-035 | Descent heading policy | **`CONSENSUS`** | MI-5H before MI-7D; shared heading primitive |
| D-MIW-036 | MI-7B+C bundle | **`CONSENSUS`** | Budget counters + exhaustion policy ship together |
| D-MIW-037 | Intent vs blocker separation | **`CONSENSUS`** | `ExecutionIntent` ≠ `ExecutionBlocker`; `CONTENTION` is scheduler observation, not intent |
| D-MIW-038 | Non-exclusive handoffs | **`CONSENSUS`** | `TUNNEL_HANDOFF_PENDING` arbitration `NEUTRAL` until executor exists; do not consume transition |
| D-MIW-039 | Start vs progress lease | **`CONSENSUS`** | `lastExecutionProgressAt` + observable dig events only; pause during `TEMPORARY`/`CONTENTION`; revoke `NO_PROGRESS` |
| D-MIW-040 | Protected arbitration vs lease availability | **`IMPLEMENTED`** | Required-flag scheduler resolver; condition-bound safety pause; command prevent/revoke; NBT v4 pre-start pause; 400-tick progress window |

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
- [x] **MI-13 + MI-2** DiscoveryMode + priority (task 14; 155 tests)
- [x] **MI-4S** Option A formula (task 15; 158 tests)
- [x] **MI-5** descent pressure / D-MIW-031 (task 16; 165 tests)
- [x] **MI-6** cave opportunistic ore (task 17; 169 tests) — MAIBS FAIL on landings → repair package
- [x] **MI-6A + MI-6D + MI-6B + MI-6C** (task 18; 178 tests) — code repair; runtime `UNVERIFIED`
- [x] **Accept MI-7 redesign** — Controlled Excavation Descent MI-7A…E; D-MIW-033/034 (user 2026-08-09)
- [x] **MI-14C3 integration repair** — task-30; protected/LOOK conflicts and budget reachability repaired; static MAIBS pass; runtime unverified
- [ ] **Begin implementation for MI-6F or MI-7B+C** (6F first per dependency)
- [ ] U-MIW matrix / runtime datapack (MI-9/MI-10)
- [ ] **MI-7E** controlled staircase (blocked until MI-7A–D + MI-6 runtime probe)

### Runtime Gate

- [ ] Approved launch + RT matrix for mining/wealth
- [ ] Dedicated-server smoke
- [ ] MI-6A falsifying probe (scripted cave Y=32 under Y=70; log landing Y)
- [ ] MI-7E falsifying probe (`NaturalDescentStatus` transitions; no dig beside cave mouth)
- [ ] MI-14C3 runtime probe: >2400-tick safety pause and CONTENTION resume, then >400 admissible
      obstruction ticks → one `NO_PROGRESS`; LOOK-only eating must delay clean admission

**MRFC-1 status:** **PASS for implemented/static scope; runtime remains UNVERIFIED** — MI-14C3-R1
closes protected ownership, LOOK-only conflict, pre-start pause, command-authority, and timeout
reachability defects. Runtime falsification still requires separate launch approval.

Historical pre-R1 result: the pure policy passed while the active-stall outcome was shadowed by the
total budget and protected ownership was invisible. Task-30 supersedes that failed static state;
Tunnel Search remains a separate, unimplemented product scope.

---

## User approval

- [x] **Lock and begin MI-14C3-R1** — typed required-flag blockers, condition-bound safety pause,
      player-order prevention/revocation, pre-start pause NBT, 400-tick progress lease, C3-F1…F7
- [x] **Lock D-MIW-025** gen-1 slice (revised — MI-13a first)
- [x] **Lock D-MIW-026** gen-1 profile v1 constants
- [x] **Accept D-MIW-027** — MI-13a perception prerequisite (Agent_Claude)
- [x] **MI-1** — `GatherIntentPolicy` + unit tests
- [x] **MI-3 / MI-23** — NEED layers
- [x] **MI-13a** — exposure in pass-one
- [x] **MI-24 / MI-25 policy** — marginal curves + opportunity formula
- [x] **Authorize MI-4R** — implemented and statically verified as task 13
- [x] **MI-13 + MI-2** — DiscoveryMode + GatherTargetPolicy (task 14)
- [x] **Accept D-MIW-029 / 030 / 031** — F-4 boundary, F-5 expedition rule, F-2 band split (Agent_Cursor continuation)
- [x] **Lock D-MIW-028 Option A** — desire × proximity; keep D-MIW-026 profiles (user 2026-08-08)
- [x] **MI-4S** — Option A applied (task 15; Continue the Plan)
- [x] **Begin implementation for MI-5** — ProgressionDemand vs LocalGatherEligibility (task 16)
- [x] **Begin implementation for MI-6** — cave opportunistic ore (task 17)
- [x] **Accept MI-6 MAIBS finding** — heightmap landing preference no-op (user + Agent_Cursor)
- [x] **Begin implementation for MI-6A, 6D, 6B, 6C** — task 18; defer 6E/6F/6G
- [x] **Accept MI-7 redesign** — Controlled Excavation Descent; MI-7A…E; exhaustion gate (user 2026-08-09)
- [x] **Begin implementation for MI-7A** — minimal `MiningProject` (task 20; 200 tests)
- [x] **Accept MI-7B+C bundle** + revised `NaturalDescentStatus` + dependency order (user 2026-08-09)
- [x] **Accept MI-5H** — `DescentHeadingPolicy` before MI-7D (user 2026-08-09)
- [x] **Begin implementation for MI-14C3** — implemented as task-28; runtime probe remains separate
- [ ] **Begin implementation for MI-6F** — live `CaveOpportunity` wiring (**before MI-7B+C**)
- [ ] **Begin implementation for MI-7B+C** — budget + exhaustion (recommended next after 6F)
- [ ] **Begin implementation for MI-5H** — descent heading selection
- [ ] Runtime launch / MI-6A + MI-7E falsifying probes (separate)

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
| 2026-08-09 | User + Agent_Claude | **MI-14C2-M2 `IMPLEMENTED`** (330 tests). MAIBS re-pass found predicate drift surviving the C2-R2 constant unification: commitment `now < claimedAt + 2400` vs expedition `now - startedTick > 2400` left one tick where the expedition was alive and its authority gone, reopening both `CAVE_HANDOFF -> NONE` and `mayStartControlledDescent`. Repaired by sharing the predicate (`ExploringGoal.expeditionExpired`) and storing `authorityTicks` instead of a derived `expiresAt`; legacy saves recover the window. Lesson `PROVEN`: deduplicating a constant does not deduplicate a boundary |
| 2026-08-09 | Agent_Claude + User | **MI-14C2-R2 `IMPLEMENTED`** (327 tests): cave-handoff authority now runs from the claim (`now + ExploringGoal.MAX_EXPEDITION_TICKS`), not from discovery. User `LOCKED` the authority bound to the expedition's own lifetime rather than a route-derived estimate — a smaller invented budget could expire while the expedition it protects is still legally running. Constant made public rather than copied; `claimCaveContinuation` requires the window explicitly (no defaulted overload). Two corrections to my MAIBS report: admission is strictly `< 400` (`expired()` is `>=`), and the suite was 321 after Protected Interruption Handling, not 310 |
| 2026-08-09 | Agent_Claude | MAIBS control-plane pass over shipped MI-14C1/C1-R1/C2/C2-R1/C3 (310 tests). Gate **FAIL: MI-14C2-M1** — the cave-continuation commitment expires on the *discovery* clock (`handoff.tick() + 400`), the same instant admission closes, so it grants ~0 protected travel for a 48-block route and Loop B returns mid-walk; `claimedAt` is stored and never read. Second-order: expiry also unblocks `mayStartControlledDescent`, so the mob can start a new staircase beside the cave it just found. Repair MI-14C2-R2 = separate admission (from discovery) from authority (from claim). Also recorded my own C1 defect (TEMPORARY grace measured from assignment age) repaired by another agent as C1-R1. Loop D confirmed correctly `NEUTRAL`; bounded authority confirmed — combat/survival unclassified |
| 2026-08-09 | Agent_Codex | **MI-14C3 MAIBS-1 `FAIL`** — active 2400-tick total budget shadows strict >2400 progress timeout; protected MOVE owners bypass contention without another blocker; three NOT FOUND probes; repair required before Loop D |
| 2026-08-09 | User + Agent_Codex | **MI-14C3-R1 `IMPLEMENTED`** — user locked required-flag taxonomy, condition-bound safety pause, player-order authority, pre-start pause, and 400-tick window; task-30 C3-F1…F7 + revoke→reassign repair; 321-test clean build; static MAIBS `PASS`, runtime `UNVERIFIED` |
| 2026-08-09 | User + Agent_Codex | **MI-14C3-R1 `PROPOSED / REOPEN_REQUESTED`** — split arbitration preemptibility from lease availability; safety pause, command prevent/revoke, separate pre-start pause accounting; all conflicting flags incl. LOOK; C3-F1…F7; evidence-derived 400-tick window proposed, not locked |
| 2026-08-09 | Agent_Codex | **MI-14C3 `IMPLEMENTED`** — observable break/step/handoff progress; exact TEMPORARY/CONTENTION pause accumulator; NBT v3 + v2 migration; C3-A…E pass; 310-test clean build; runtime `UNVERIFIED`; task-28-report |
| 2026-08-09 | User + Agent_Cursor | **MAIBS C2 FAIL** — M1 handoff authority lifetime, M2 host MOVE invisible to contention, M3 stop() resurrects revoked project; task-27-maibs-report; task-29-brief (R1/R2/R2); C3 blocked |
| 2026-08-09 | Agent_Cursor | **MI-14C2 `IMPLEMENTED`** (task-27; MAIBS C2 static `PASS_WITH_CONCERNS` superseded); **MI-14C3 contract LOCKED** (D-MIW-039; C3-A…E; task-28-brief); frontier → Begin MI-14C3 |
| 2026-08-09 | User + Agent_Cursor | **MI-14C2 contract LOCKED** — D-MIW-037 (intent ≠ blocker; CONTENTION producer required); D-MIW-038 (`TUNNEL_HANDOFF_PENDING` arbitration NEUTRAL until executor); admission+continuation mandatory; C2-A…G falsification; task-27-brief |
| 2026-08-09 | Agent_Claude | MI-14-R2 family closed for static/unit semantics (266 tests): R2c wiring (`completeStep` handed the detector the *future* step), R2c-b `SELF_CORRIDOR` height (2-high vs the 3 cells a step cuts), R2d planned-cells masking an already-open cave, R2e connected-air vs mob-occupiable space. MAIBS set R2-C1…C5 all green; **C4 was masking C5**. Method lesson: self-consistent low-level unit tests prove the algorithm and nothing about the wiring. Added MI-14C decomposition (C1 lease/revocation, C2 arbitration, C3 progress lease); Loop D locked outside MI-14C — no tunnel-search executor exists |
| 2026-08-09 | Agent_Claude | Reconciled task table (MI-14A/A-T/A-R1/14B + MI-6F/G shipped; 249 tests); MAIBS prolonged-loop pass: **MI-14-M1** false `CAVE_FOUND` from self-dug staircase geometry (`isSubterraneanAt` early return) `FAIL`; loops A zombie assignment + B handoff-vs-chores `ARCHITECTURE_DEFECT`, C repeated-site `RUNTIME_QUESTION`, D tunnel dead leaf `ACCEPTABLE_STEPPING_STONE`; recommended MI-14-R2 before MI-14C |
| 2026-08-09 | Agent_Claude | MI-14 pre-implementation review (MAIBS-1): `ControlledDescentGoal` already orchestrates; all three terminal handoffs (`CAVE_FOUND`, `HANDOFF_TUNNEL_SEARCH`, `SEARCH_BUDGET_EXHAUSTED`) have zero consumers; four `MOVE` goals share priority 3 so a policy director cannot enforce a mode; recommended consumer-first repair (MI-14a/b/c) over mode-chooser-first; gate `FAIL — ARCHITECTURE_DEFECT` |
| 2026-08-09 | User + Agent_Cursor | **MI-7B+C bundle**, revised `NaturalDescentStatus`, **MI-5H**, MI-6F→7B+C dependency; cave-mouth downgrade |
| 2026-08-09 | Agent_Cursor | **MI-7A implemented** (task 20; 200 tests) |
| 2026-08-08 | Agent_Cursor | **MI-6A/D/B/C implemented** (task 18; 178 tests); defer 6E/F/G; MI-7 still blocked on runtime |
| 2026-08-08 | Agent_Cursor | MAIBS-1 on MI-6: landing no-op `FAIL`; package MI-6A…G; MI-6A READY; MI-7 blocked behind 6A |
| 2026-08-08 | Agent_Claude | MI-5 behavioural simulation (MAIBS-1): confirmed smallest-descent-first and descent-intent leak; found the first stage never sorts for descent (`consume()` clears pressure before the first plan) and that `MAX_LANDING_ELEVATION` already bounds deep drops; 5 weird behaviours classified; gate `FAIL — ARCHITECTURE_DEFECT`, correctness-before-scoring recommended |
| 2026-08-08 | Agent_Cursor | **MI-6 implemented** (CaveContextPolicy + cave ore priority); 169 tests; MI-7 READY design |
| 2026-08-08 | Agent_Cursor | **MI-5 implemented** (D-MIW-031 + descent pressure); 165 tests; MI-6 READY |
| 2026-08-08 | Agent_Cursor | **MI-4S implemented** (Option A + saturated scan gate); 158 tests; MI-5 READY |
| 2026-08-08 | Agent_Cursor | User **Lock D-MIW-028 Option A**; MI-4S → `READY`; no Java |
| 2026-08-08 | Agent_Cursor | Continue RFC: F-1 → D-MIW-028 A/B/C (recommend A); lock F-2/F-4/F-5 as D-MIW-031/029/030; MI-4S READY after lock; no Java |
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

### Contribution — Agent_Claude (reconciliation + prolonged-loop pass)

Agent: Agent_Claude
Date/Session: 2026-08-09 (snapshot 01:5x)
Contribution type: IMPLEMENTATION + REVIEW (Gate MAIBS-1)

Shipped since the last contribution: MI-14A (transition protocol), MI-14A-T (14 contract tests),
MI-14A-R1 (restart-lock correction), MI-14B (`MiningDirector` ownership extraction), and MI-6F/6G.
Suite at **249 tests, zero failures**. Task table reconciled above — it still described MI-7A as
current.

**Two of my own errors corrected, both found by review rather than by tests:**

1. MI-14A-T asserted `CAVE_FOUND` must not block a fresh descent. That starved its own consumer
   across a priority gap (rebase at 8, descent at 3). Fixed in R1; the corrected test carries the
   reason so it cannot be "simplified" back.
2. My MI-14 review said `ControlledDescentGoal` emitted seven terminal reasons. It emits **five** —
   I had counted call sites.

**MI-14-M1 confirmed at source.** `openedTraversableCave` returns true the moment the mob is
subterranean, so a covered staircase at rimDepth 8 fires `CAVE_FOUND` having opened nothing. My own
MI-6G classifier does not catch it and I do not think it should: a hillside staircase genuinely *is*
below local terrain, so the classifier answers correctly and the question is wrong. Context is not
opportunity. Repair is an evidence-returning `findOpenedCave`, not a bigger threshold — a threshold
only moves the depth at which the mob deceives itself.

**Loop A is the finding that changes plan scope.** A director assignment that never gets execution
time consumes no budget and cannot expire, while blocking every future assignment. MI-14C as scoped
("who may act") does not fix it — the director also needs revocation.

Agreement: B is MI-14C's job, C folds into MiningMemory, D is honestly blocked.

Concerns: MI-14-M1 should land before MI-14C. Building arbitration on top of a handoff that fires
spuriously means tuning who may act on a signal that is wrong to begin with.

RFC fields updated: new reconciliation + prolonged-loop topic, Change Log, this contribution.

---

### Contribution — Agent_Claude (MI-14 design review)

Agent: Agent_Claude
Date/Session: 2026-08-09 (snapshot 01:00)
Contribution type: REVIEW (Gate MAIBS-1, pre-implementation)

Reviewed MI-14 against the **shipped** MI-7 stack rather than the RFC's description of it, per the
user's step 2. Steps 1–3 done; step 4 (implement) deliberately not started — the review says the
design needs repair first.

**Reconciliation:** the MI-7 primitives all exist as described. One premise does not hold —
`ControlledDescentGoal` **already** starts projects (`MiningProject.startControlledDescent`), reads
`projectOf(mobId)`, and emits seven `MiningProjectEnd` values. Orchestration is not missing; it is
embedded in one executor, for one mode.

**Defect 1 — all three terminal handoffs are orphaned.** `HANDOFF_TUNNEL_SEARCH`, `CAVE_FOUND` and
`SEARCH_BUDGET_EXHAUSTED` have **zero** consumers outside the emitter. A descent that breaks into a
cave ends with the reason `CAVE_FOUND` and the mob walks away. This is the iron dead end in control
flow — a producer with no consumer — and it reframes MI-14: its first job is to be the missing
consumer, not a new mode chooser.

**Defect 2 — a pure-policy director cannot enforce a decision.** Priority 3 holds four `MOVE` goals
(craft, gather, smelt, controlled descent). Equal priorities cannot preempt, so a director selecting
`CAVE_EXPLORATION` while gather holds `MOVE` has decided nothing, and the project record will
disagree with what the mob is visibly doing. The proposed design specifies neither a single owned
executor nor a priority hierarchy.

**Repair:** option B — consumer first (MI-14a), extract start/stop from the goal (MI-14b), mode
selection last (MI-14c). Two of the four leaves in the proposed decision tree currently terminate in
nothing: `TUNNEL_SEARCH` has no executor at all, and `CAVE_EXPLORATION` is landing-sort behaviour
inside `ExploringGoal` rather than a project mode. Wiring `CAVE_FOUND` → cave continuation makes one
leaf real without writing a new goal.

Agreement: the layering is right and the primitives are good. The five-step frontier is right; step 3
is not optional.

Concerns: MI-15/16/17 all assume a director that can enforce its choices. Building them on option A
would compound defect 2.

RFC fields updated: new Topic: MI-14 design review (MAIBS-1), Change Log, this contribution.

---

### Contribution — Agent_Claude (MI-5 behavioural simulation)

Agent: Agent_Claude
Date/Session: 2026-08-08 (snapshot 21:5x)
Contribution type: REVIEW + VALIDATION (Gate MAIBS-1)

Reviewed: MI-5 as implemented — `DescentPressurePolicy`, `ExplorationReadiness`,
`ExplorationActivityGoal.updateDescentPressure`, `ExploringGoal.landingCandidates` — against the
user's design critique. Mechanism read from source per MAIBS-1; nothing inferred from names.

**Confirmed as reported:** the sort key is `belowFirst*1000 + |Δy|`, so at Y=70 a Y69 landing (key 1)
beats Y60 (key 10). "Prefer lower" is implemented as "prefer the smallest descent". Also confirmed:
descent pressure lives on `ExplorationReadiness` while the journey lives on `ExpeditionState`, which
carries no descent intent, so clearing pressure cannot end the expedition it started.

**Two defects the evidence pass added:**

1. **The first stage never sorts for descent.** `readiness.consume(...)` clears `descentPressure`
   immediately after `createExpedition`, and `planCurrentStage` runs after it in the same tick. The
   observer re-arms pressure 10 ticks later, so hops 2+ get the descent sort but the stage that
   *sets the heading* does not. This compounds the heading-blindness point: direction is chosen with
   no descent input at all.
2. **`MAX_LANDING_ELEVATION` (16) already bounds deep drops**, so the conservative key is paying a
   second time for protection that exists elsewhere. That weakens the main argument for
   smallest-descent-first.

**Endorsed without change:** the descent window, `lowestYReached`, the stall detector owning
detection but not the fallback, Y≤16 meaning *gather eligible* rather than *descent succeeded*,
route-level terrain trend, and MI-6 outranking MI-5. `diamondProgressionDemand` /
`isDiamondLocalGatherEligible` show F-2's two-signal split has already landed.

**One prediction worth keeping:** an invited companion inherits the leader's heading but sorts
landings by **its own** pressure — false if it wants no diamond — so the leader sinks and the
companion stays high. Classified `ACCEPTABLE_STEPPING_STONE`: visible, harmless, and probably good
to watch.

Gate result: **`FAIL — ARCHITECTURE_DEFECT`** on defects 1 and 2. Recommended sequencing is
correctness first (option B: `consume()` ordering + `ExplorationIntent` on `ExpeditionState`), then
the scoring work (option A). Tuning a journey that cannot yet report success or failure would put
the scoring on ground that shifts underneath it.

Concerns: option A's route-level terrain-trend bonus samples surface heights per route candidate and
must be costed against F-6 (no perception budget, no per-mob stagger) before it ships.

RFC fields updated: new Topic: MI-5 behavioural prediction (MAIBS-1), Change Log, this contribution.

---

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

---

### Contribution — Agent_Cursor (Continue the RFC: F-1 lock package)

**Agent:** Agent_Cursor
**Date/Session:** 2026-08-08 ~21:45 PDT
**Contribution type:** `REVIEW / DECISION`

**Frontier before:** MI-4R + MI-13 + MI-2 implemented (155 tests). Claude left F-1…F-6 contested;
MI-4 not accepted. Identity/slice still pointed at MI-13/MI-2 as next.

**Evidence (CODE_CONFIRMED):**
- `ResourceWealthPolicy.netUtility = wealth + bonus - cost` dead at cost 3 for iron (Claude table)
- Live cost `sqrt(distSq)/8` in `GatherTargetPolicy`
- `SCAN_ACTIVATION_COST = 0.25F` still allows wealth scan flags
- Progress: MI-4R, MI-13, MI-2 complete

**Action:** Formalized D-MIW-028 Options A/B/C (recommend A — keep D-MIW-026, desire×proximity);
locked D-MIW-029 (F-4), D-MIW-030 (F-5), D-MIW-031 (F-2); proposed D-MIW-032 (F-6); added task
**MI-4S** READY after F-1 lock. No Java; no launch.

**Frontier after:** User must **Lock D-MIW-028** (Option A recommended). Then authorize
`Begin implementation for MI-4S`.

**RFC fields updated:** Identity, Executive Summary, Collaboration, Topic Index, Utility scale topic,
Opportunity formula note, Phased plan, Tasks, Decision Registry, Gates, User approval, Change Log,
this contribution.

---

### Contribution — Agent_Cursor (Lock D-MIW-028 Option A)

**Agent:** Agent_Cursor
**Date/Session:** 2026-08-08 ~21:50 PDT
**Contribution type:** `DECISION`

**Frontier before:** D-MIW-028 `AWAITING_LOCK`; MI-4S blocked on product choice.

**Action:** Recorded user lock of **Option A** (desire × proximity; keep D-MIW-026; drop raw
`- acquisitionCost`). Options B/C remain rejected. Updated Identity, Utility scale topic,
Opportunity formula, gen-1 slice, Decision Registry, Gates, User approval. No Java.

**Frontier after:** **MI-4S `READY`**. Requires explicit `Begin implementation for MI-4S`.

**RFC fields updated:** Identity, Executive Summary, Collaboration, Topic Index, F-1 decision,
Opportunity formula, Phased plan, Tasks, Decision Registry, Gates, User approval, Change Log,
this contribution.

---

### Contribution — Agent_Cursor (MI-4S / Continue the Plan)

**Agent:** Agent_Cursor
**Date/Session:** 2026-08-08 ~21:55 PDT
**Contribution type:** `IMPLEMENTATION / VALIDATION`

**Frontier before:** D-MIW-028 Option A locked; MI-4S READY.

**Action:** Task 15 — `netUtility`/admission = `desire × proximity`; added `isSaturated` for
wealth-only scan activation (replaces broken `-cost` saturated kill). Updated wealth tests so
cost 3 admits and cost 35 rejects. `gradlew.bat test`: 158/158 pass.

**Frontier after:** **MI-5 `READY`** (D-MIW-031). Ask `Begin implementation for MI-5`.

**RFC fields updated:** Identity, Executive Summary, Collaboration, Topic Index, F-1/Disposition,
Phased plan, Tasks, Gates, User approval, Change Log, this contribution.

---

### Contribution — Agent_Cursor (MI-5 implementation)

**Agent:** Agent_Cursor
**Date/Session:** 2026-08-08 ~22:00 PDT
**Contribution type:** `IMPLEMENTATION / VALIDATION`

**Frontier before:** MI-5 READY after MI-4S; user authorized Begin implementation for MI-5.

**Action:** Task 16 — split `diamondProgressionDemand` / local eligibility; `DescentPressurePolicy`;
readiness unlock + `ExplorationActivityGoal` wire; `ExploringGoal` lower-Y landing bias.
`gradlew.bat test`: 165 pass.

**Frontier after:** **MI-6 `READY`**. Ask `Begin implementation for MI-6`.

**RFC fields updated:** Identity, Executive Summary, Collaboration, capability rows, Phased plan,
Tasks, Gates, User approval, Change Log, this contribution.

---

### Contribution — Agent_Cursor (MI-6 implementation)

**Agent:** Agent_Cursor
**Date/Session:** 2026-08-08 ~22:05 PDT
**Contribution type:** `IMPLEMENTATION / VALIDATION`

**Frontier before:** MI-6 READY after MI-5; user authorized Begin implementation for MI-6.

**Action:** Task 17 — `CaveContextPolicy`; gather ore priority when cave-like; explore
under-surface landing preference. `gradlew.bat test`: 169 pass.

**Frontier after:** **MI-7 `READY` (design)**. Ask `Begin implementation for MI-7` (or MI-9/10).

**RFC fields updated:** Identity, Executive Summary, Collaboration, capability rows, Phased plan,
Tasks, Gates, User approval, Change Log, this contribution.

---

### Contribution — Agent_Cursor (MI-6 MAIBS-1 + repair package)

**Agent:** Agent_Cursor
**Date/Session:** 2026-08-08 ~22:35 PDT
**Contribution type:** `REVIEW / BEHAVIORAL_SIMULATION`
**Skill:** `minecraft-ai-behavioral-simulation` (Gate MAIBS-1)

**Frontier before:** MI-6 marked implemented; MI-7 next. User demonstrated heightmap landing
preference is mathematically a no-op.

**Evidence (CODE_CONFIRMED):**
- `landingCandidates` sets `y = getHeight(MOTION_BLOCKING_NO_LEAVES)`
- `landingPreferenceKey` calls `isCaveLike(landingY, surfaceY)` with the same heightmap
- `surfaceY - landingY = 0` → never cave-like for ordinary candidates
- `if (descending) else if (continueCave)` excludes cave sort under diamond DESCENT
- Open-ravine: mob-column heightmap ≈ floor → not cave-like

**Action:** Wrote Topic MI-6 behavioural prediction; repair order MI-6A→D→B→C→F→G (E deferred);
blocked MI-7 behind natural descent fix; Behavioral Prediction for MI-6A. No Java.

**Frontier after:** **MI-6A `READY`**. Ask `Begin implementation for MI-6A`.

**RFC fields updated:** Identity, Executive Summary, Collaboration, Topic Index, MI-5 boundary note,
new MI-6 MAIBS topic, Phased plan, Tasks, Gates, User approval, Change Log, this contribution.

---

### Contribution — Agent_Cursor (MI-6A/D/B/C implementation)

**Agent:** Agent_Cursor
**Date/Session:** 2026-08-08 ~22:50 PDT
**Contribution type:** `IMPLEMENTATION / VALIDATION`

**Frontier before:** User authorized Begin for MI-6A, 6D, 6B, 6C; defer 6E/6F/6G; keep +15 until MI-17.

**Action:** Rewrote `CaveContextPolicy` (rim, modes, opportunity); added `CaveLandingResolver`;
wired `ExploringGoal` + `GatherResourcesGoal` / `GatherTargetPolicy`. Unit suite **178** tests
`BUILD SUCCESSFUL`. Artifacts: `.superpowers/sdd/task-18-brief.md`, `task-18-report.md`.
No Minecraft launch; no commit.

**Frontier after:** Code repair for MAIBS defects 1/2/5; runtime probe open; MI-6F next when wanted;
MI-7 still blocked until natural descent proof.

**RFC fields updated:** Identity, Executive Summary, Topic Index, repair table, gen-1 slice, tasks,
gates, approval, change log, this contribution.

---

### Contribution — Agent_Codex (looted diamond pick equipment repair)

**Agent:** Agent_Codex
**Date/Session:** 2026-08-08
**Contribution type:** `IMPLEMENTATION / VALIDATION / BEHAVIORAL_SIMULATION`

Diamond/netherite tier recognition already existed, and backpack/main-hand loot was supported. The
off hand was absent from `ToolTierPolicy`, `ToolBox`, and every live progression caller, explaining
the observed slot specificity. Task 19 adds one backpack/main/off-hand ownership view through
gather, craft, smelt, exploration demand, and tool drawing. `ToolBox` swaps an off-hand best tool
into main hand without dropping either stack. Broken off-hand tools do not satisfy progression.

Clean build passed 181 tests. MAIBS-1: `PASS — BEHAVIORALLY_PLAUSIBLE`; live SPM loot placement,
combat re-arm/redraw, and equipment visuals remain `UNVERIFIED`.

---

### Contribution — Agent_Cursor (MI-7 Controlled Excavation Descent redesign)

**Agent:** Agent_Cursor
**Date/Session:** 2026-08-09 ~00:10 PDT
**Contribution type:** `REVIEW / DESIGN / DECISION`

**Frontier before:** MI-7 listed as “bounded staircase max N blocks” after MI-14/MI-19/MI-18 in
phase order; `natural descent exhausted` undefined; risk of floor-dig beside cave entrances.

**Evidence (user critique + RFC cross-check):**
- D-MIW-001 already assigns session state to `MiningProject`; MI-7 as standalone duplicates MI-14
- D-MIW-010 budget dimensions exist but were scheduled MI-19 after MI-7
- `MiningDirector` flow line 1521 typo referenced “MI-6” for staircase (`CODE_CONFIRMED` in prior revision)
- Anti-clairvoyance (D-MIW-008) means depth alone cannot satisfy diamond demand — handoff to
  `TUNNEL_SEARCH` required

**Action:** Added Topic MI-7 controlled excavation descent; locked **D-MIW-033** (project-owned
descent) and **D-MIW-034** (`EXHAUSTED` gate); split MI-7 → **MI-7A…E**; reordered P7a–P7e before
full MI-14; added `CONTROLLED_DESCENT` to `MiningProjectMode`; updated decision flow, tasks, gen-1
slice, gates, approval. No Java.

**Frontier after:** **MI-7A `READY`**. Requires `Begin implementation for MI-7A` (or parallel
approved MI-6A runtime probe). MI-7E blocked until 7A–D + natural-descent runtime evidence.

**RFC fields updated:** Identity, Executive Summary, Collaboration, Topic Index, new MI-7 topic,
MiningProject modes, capabilities, MiningDirector flow, Phased plan, Tasks, Decision Registry,
Gates, User approval, Change Log, this contribution.

---

### Contribution — User + Agent_Cursor (MI-7B+C bundle + MI-5H frontier)

**Agent:** User (product) + Agent_Cursor
**Date/Session:** 2026-08-09 ~00:25 PDT
**Contribution type:** `REVIEW / DESIGN / DECISION`

**Frontier before:** MI-7A `IMPLEMENTED`; MI-7B and MI-7C listed separately; MI-6F deferred; MI-5
heading blindness noted in MAIBS but no task ID.

**Action:** Locked revised frontier and dependencies:

```text
MI-7A DONE → MI-6F wire → MI-7B+C → MI-5H → MI-7D → MI-7E
```

- **D-MIW-036:** MI-7B+C one semantic unit (budget + exhaustion evidence).
- **D-MIW-034 revised:** `EXHAUSTED` requires search budget + no natural route + no active
  `CaveOpportunity` + spatial coverage — not path-fail count alone.
- **D-MIW-035:** MI-5H `DescentHeadingPolicy` before MI-7D; shared heading for descent/staircase/tunnel.
- **CaveContextSnapshot** ≠ **CaveOpportunity** naming locked.
- Cave-mouth blindness downgraded to `LIKELY_ARCHITECTURE_GAP` (hillside rim can classify subterranean).
- Primary descent defect elevated: novelty heading before terrain (MI-5H), not MI-7A.

**Frontier after:** **MI-6F `READY`** (before MI-7B+C). Ask `Begin implementation for MI-6F` or
`Begin implementation for MI-7B+C` after 6F.

**RFC fields updated:** Identity, Naming, Executive Summary, Collaboration, Topic Index, MI-5/MI-6/MI-7
topics, Phased plan, Tasks, Decision Registry, Gates, User approval, Change Log, this contribution.

---

### Contribution — User + Agent_Cursor (MI-14C2 contract lock)

**Agent:** User (product) + Agent_Cursor
**Date/Session:** 2026-08-09 ~02:35 PDT
**Contribution type:** `DECISION / CONTRACT_LOCK`

**Frontier before:** MI-14C1-R1 verified (`a6e9793`); C2 could ship arbiter without CONTENTION
producer or with `TUNNEL_HANDOFF_PENDING` as exclusive intent (Loop D deadlock).

**Action:** Locked MI-14C2 contract in RFC Topic MI-14C2; added **D-MIW-037** (intent vs blocker
separation; CONTENTION as scheduler observation) and **D-MIW-038** (non-exclusive
`TUNNEL_HANDOFF_PENDING`); wrote `.superpowers/sdd/task-27-brief.md` with C2-A…G falsification.
No Java.

**Frontier after:** **MI-14C2 `READY`**. Requires `Begin implementation for MI-14C2`.

**RFC fields updated:** Identity, MI-14C2 topic, Decision Registry, Change Log, progress ledger,
task-27-brief, this contribution.

---

### Contribution — Agent_Cursor (MI-14C2 complete + MI-14C3 contract lock)

**Agent:** Agent_Cursor
**Date/Session:** 2026-08-09 ~02:50 PDT
**Contribution type:** `IMPLEMENTATION_REVIEW / MAIBS / CONTRACT_LOCK`

**Frontier before:** MI-14C2 implemented (task-27, 292 tests); runtime/MAIBS C2 unverified; C3 sketch only.

**Action:**

1. Recorded MI-14C2 `IMPLEMENTED` with shipped artifact table and goal wiring evidence.
2. Ran **MAIBS C2 static pass** — `PASS_WITH_CONCERNS`; C2-A…G mapped to mechanism/prediction table;
   runtime probes listed as `UNVERIFIED`.
3. Locked **MI-14C3** contract: D-MIW-039 (start vs progress lease separation), observable progress
   signals, pause during `TEMPORARY`/`CONTENTION`, `NO_PROGRESS` revoke, C3-A…E falsification.
4. Wrote `.superpowers/sdd/task-28-brief.md`.

**Frontier after:** **MI-14C3 `READY`**. Requires `Begin implementation for MI-14C3`. Then
`TunnelSearchGoal` executor (Loop D consumer).

**RFC fields updated:** Identity, Topic Index, MI-14C2/C3 topics, Decision Registry (D-MIW-039),
Change Log, progress ledger, task-28-brief, this contribution.

---

### Contribution — User + Agent_Cursor (MAIBS C2 FAIL — lifecycle defects M1–M3)

**Agent:** User (peer review) + Agent_Cursor (verification + RFC)
**Date/Session:** 2026-08-09 ~03:00 PDT
**Contribution type:** `MAIBS / ARCHITECTURE_DEFECT / CONTRACT`

**Frontier before:** MI-14C2 unit green; prior MAIBS `PASS_WITH_CONCERNS`; MI-14C3 ready.

**Action:** Multi-cycle static trace confirms three defects (`CODE_CONFIRMED`):

1. **M1** — `acceptCaveHandoff` consumes transition before expedition runs → `CAVE_HANDOFF` → `NONE`.
2. **M2** — `MoveContentionPolicy` skips host SPM goals → false `AUTHORIZE` with MOVE held elsewhere.
3. **M3** — `ControlledDescentGoal.stop()` `putProject` after director revoke → dual truth.

Wrote `task-27-maibs-report.md`, `task-29-brief.md`. Blocked MI-14C3. Revised C2-A…G verdict table.

**Frontier after:** **MI-14C repair package `READY`** (R1 → R2 → C1-R2 → MAIBS re-pass → C3).

**RFC fields updated:** Identity, Topic Index, MI-14C2 MAIBS section, MI-14C3 blocked status,
Change Log, progress ledger, task-27-maibs-report, task-29-brief, this contribution.

---

### Contribution — Agent_Codex (MI-14C3 implementation)

**Agent:** Agent_Codex
**Date/Session:** 2026-08-09 ~03:15 PDT
**Contribution type:** `IMPLEMENTATION / VALIDATION / BEHAVIORAL_SIMULATION`

**Frontier before:** MI-14C2 repair and MAIBS re-pass complete; MI-14C3 contract locked and user
authorized implementation.

**Action:** Added the persisted observable-progress timestamp and exact blocker-pause accumulator,
the 2400-admissible-tick policy, director marker, and successful-break/complete-step/terminal wiring.
Starting or ticking the executor does not create progress. A failed `destroyBlock` no longer advances
the plan or budget. C3-A…E plus persistence and v2 migration tests were written RED-first.

**Validation:** focused C1/C2/C3 suites green; `gradlew.bat clean build` passed 310 tests with zero
failures/errors/skips. Artifact SHA-256
`F0F14E9A6C5B33A241848805275A0E4419E73140872C00E70E336856413F03D3`. MAIBS verdict:
**BEHAVIORALLY_PLAUSIBLE / RUNTIME_UNVERIFIED**. No Minecraft launch, commit, or push.

**Frontier after:** MI-14C3 static implementation complete. Next execution-control subsystem is the
missing `TunnelSearchGoal` consumer for Loop D; C3's live contention/resume/stall probe remains open
under separate runtime approval.

**RFC fields updated:** Identity, Topic Index, MI-14C3 topic, phased plan, task registry, gates,
approval ledger, change log, this contribution; task-28 report and porting test/decision docs.

---

### Contribution — Agent_Codex (MI-14C3 full MAIBS audit)

**Agent:** Agent_Codex
**Date/Session:** 2026-08-09
**Contribution type:** `REVIEW / OBJECTION / VALIDATION`

The explicit behavioral-simulation skill traced the integrated executor rather than stopping at
the lease policy. It found the active C3-A outcome unreachable: `ControlledDescentGoal.tick()`
increments the project clock and terminates at `>=2400`, whereas C3 requires progress age `>2400`.
It also found that protected priority-0/1/2 MOVE owners are excluded from CONTENTION but not mapped
to a different blocker. A stay tether can therefore strand a never-started assignment, and other
protected interruptions can age a started progress clock while the executor cannot physically run.

**Objection:** high severity; MAIBS-1 `FAIL — ARCHITECTURE_DEFECT`. Pure C3 tests and the clean build
remain valid but cannot support the behavioral claim. Three negative probes and two repair options
are recorded in `task-28-report.md`. No code, runtime launch, commit, or push was performed during
this audit.

---

### Contribution — User + Agent_Codex (MI-14C3-R1 repair contract)

**Agent:** User (independent review) + Agent_Codex
**Date/Session:** 2026-08-09
**Contribution type:** `REVIEW / DESIGN / OBJECTION`

**Frontier before:** C3 code/unit green; MAIBS failed on budget shadow and protected MOVE invisibility.

**Agreement:** the progress timestamp, exact-once pause settlement, blocker-change handling,
save/load, and no-fake-progress rules are sound. The protected defect lives at the
scheduler-classification→lease-blocker boundary. `PROTECTED` correctly means “mining cannot force
yield”; it must not imply “mining can execute.”

**Refinement:** proposed D-MIW-040 and MI-14C3-R1. Safety/recovery gains explicit pause semantics;
StayNear/player commands prevent or hard-end incompatible mining; combat remains unchanged. Added
the missing pre-start pause requirement and C3-F1…F7. The pinned priority audit added LOOK-only
`EatFoodGoal`, proving the lease must inspect all executor-conflicting flags, not MOVE alone.
Preserved the separate total-budget objection:
C3-A passes in isolation but needs an early-stall integrated scenario with enough remaining total
budget. Exact progress timeout remains contested pending a bound derived from break/navigation cost.

**Frontier after:** R1 architecture direction is strongly supported but not locked. No source
implementation is authorized by this review. Next decision: choose/justify the progress window and
lock the exact protected-goal→blocker mapping; only then create the implementation brief.

**RFC fields updated:** MI-14C3 stable topic, task registry, D-MIW-040, C3-F1…F7, progress ledger,
test matrix, task-28 report, change log, this contribution.

---

### Contribution — User + Agent_Codex (MI-14C3-R1 implementation)

**Agent:** User (lock/product decision) + Agent_Codex
**Date/Session:** 2026-08-09
**Contribution type:** `DECISION / IMPLEMENTATION / VALIDATION / MAIBS`

**Frontier before:** MI-14C3 arithmetic green; MAIBS failed on protected scheduler ownership,
LOOK-only conflicts, and a progress timeout shadowed by the total project budget.

**Decision:** User locked D-MIW-040, the pinned goal-family mapping, condition-bound
`SAFETY_RECOVERY`, hard `PLAYER_ORDER`, NBT-backed pre-start pause accounting, the 400-tick
admissible progress window, and C3-F1…F7.

**Implementation:** Added `SchedulerConflictPolicy` to inspect intersection with the designated
executor's complete required flags while leaving C2 preemption arbitration independent. Expanded
the host taxonomy, added typed lease blockers, persisted `startPausedTicks` in lease NBT v4,
prevented stay/command assignment, and retained the 2400-tick absolute project cap. A semantic
review found and repaired a same-observer revoke→reassign loop by evaluating the intended
controlled-descent authority during pre-assignment scanning.

**Validation:** Targeted RED failed on all missing R1 APIs. Targeted GREEN passed. `clean test` and
`clean build` each passed 321 tests; C3-R1 has 11 tests with zero failures/errors. Post-code MAIBS
static result is `PASS — BEHAVIORALLY_PLAUSIBLE`; runtime remains `UNVERIFIED` because no Minecraft
launch was authorized.

**Frontier after:** MI-14C3-R1 is `IMPLEMENTED`; the next control-plane frontier is approved runtime
falsification or a separate Loop-D/Tunnel Search product decision. No Tunnel Search work occurred.

**RFC fields updated:** identity, MI-14C3/R1 topic, D-MIW-040, tasks, gates, user approval, change
log, and this contribution. Evidence: `.superpowers/sdd/task-30-report.md`.

---

### Contribution — Agent_Claude (MAIBS control-plane pass, MI-14C)

**Agent:** `Agent_Claude`
**Date/Session:** 2026-08-09
**Contribution type:** `REVIEW / GATE`

**Frontier before:** RFC listed MI-14C1/C2/C3 as `PROPOSED`. Reality: all three shipped, plus two
repairs (C1-R1 episode clock, C2-R1 commitment). The RFC was behind the code, again.

**Action:** Reconciled the MI-14C topic against source and ran the control-plane MAIBS pass.

- **`MI-14C2-M1` (`CODE_CONFIRMED`, gate `FAIL`)** — continuation commitment expires on the
  discovery clock, granting ~0 protected travel; `claimedAt` unused; Loop B returns mid-walk;
  second-order re-assignment of a fresh descent beside the found cave.
- Confirmed Loop D stayed out of arbitration (`TUNNEL_HANDOFF_PENDING` -> `NEUTRAL`).
- Confirmed bounded authority (combat/survival/shelter/torch-placement unclassified).
- Recorded my own C1 grace-clock defect and its repair rather than quietly inheriting the fix.
- Corrected one of my own probes mid-pass: `putCommitment` has no direct callers, which looked like
  dead wiring until `claimCaveContinuation` was found. Reported only after the narrower probe
  failed, per AV-1.

**Frontier after:** **MI-14C2-R2 `READY`** (two-clock split for continuation authority). Then
`TUNNEL_SEARCH` executor, then multi-mode director selection. Runtime remains `UNVERIFIED`
throughout — no lease, arbitration decision or commitment has been observed in a running game.
