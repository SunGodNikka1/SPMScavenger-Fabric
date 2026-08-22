# RFC: Village & Raid autonomous progression (PlayerMob parity)

## RFC Identity

| Field | Value |
| --- | --- |
| **Project root** | `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric` |
| **Host platform** | Social Player Mobs (`playermob`) v0.86.0 |
| **Target system** | **Vanilla Minecraft 1.21.1** — Village / Villager economy + **Raid** event (not SPM “raiding chests”) |
| **Reference AI** | **Mineflayer** (bot stack: pathfinder, inventory, plugins) + **human player** interaction parity |
| **Mode** | `RFC_DESIGN_WORK_ARTIFACT_ONLY` — **V1 + V1-D + V1.5 CLOSED**; **V2 + V2-TE CLOSED**; V3-A/B/C/D1/E/F **CLOSED (static)**; broad V3-D2 workstation awareness **DEFERRED**; **D58-1…D58-12 LOCKED** |
| **Status** | Tasks 52–58 **`IMPLEMENTED / STATIC-BEHAVIORAL ACCEPT`** (**1589 tests** at V3-F closure). Shipped V3-D1 = population/HOME facts only; V3-F = `ComposterWorkFacts` + `CompostGoal` @ P5. Runtime VR-T3a–m **UNVERIFIED**. |
| **Nearest frontier** | **Task-59 / V3-G integration and closure — NEXT but HOLD** until separately authorized. Reopen V3-F only if integration/runtime evidence falsifies a locked invariant. |
| **Last update** | 2026-08-22 (`User` RFC review + `Agent_Cursor` — Task-58 closure sync; reserve-authority reconciliation; VR-T3f exclusion from V3-G closure; Task-59 Gate-0 disposition) |
| **Related** | `RFC-VANILLA-AUTONOMOUS-PROGRESSION.md`, `RFC-TOOL-TIER-UPGRADES.md`, `RFC-FURNACE-SMELTING.md`, `RFC-ACTION-TRANSITIONS.md`, `docs/wiki/Opinion-System.md` |
| **Gate** | MRFC-1, SPM-1 … SPM-5 |
| **Peer review** | `Agent_Cursor` · `Agent_ChatGPT` · `Agent_Claude` |

### Critical terminology (`CONFIRMED`)

| Term in SPM | Meaning | Village/Raid relevance |
| --- | --- | --- |
| `RaidContainersGoal` | **Loot player/village chests** | Antagonistic to villagers; not raid-event defense |
| `Raider` (vanilla) | Pillager faction mobs | SPM mixin makes them **hunt PlayerMobs** like players |
| `RaiderTargetsPlayerMobMixin` | Illagers target `PlayerMobEntity` | PlayerMob is **raid victim**, not vanilla raid initiator |
| Village companion spawn | `WorldGenRegionMixin` | Social spawn flavour; **not** economic integration |

---

## Executive Summary

**Player-interaction parity with a human player for village/raid content is `PARTIAL` at best today and `NOT PRACTICAL` for full economic + raid-initiation parity without substantial new systems.**

Social Player Mobs (`CONFIRMED` — source audit v0.86.0):

- **Can** fight illagers when engaged; use bows, shields, TNT, crystals; flee; eat; loot containers; greet villagers; sleep in beds; open doors; use commanded fake-player item use.
- **Can now** perceive and remember bounded loaded village POIs and autonomously execute demand-owned vanilla/Trade Everything trade chains through the shipped V1/V2 systems (`RUNTIME_CONFIRMED` only for the recorded VR-T1/VR-T2/V2-TE scenarios).
- **Can now in production code** enforce ally storage safety, run a committed managed
  harvest→replant episode, observe bounded population/HOME facts, and deliver bounded population
  food through tasks 52–58 (`STATIC-BEHAVIORAL ACCEPT`; live V3 behavior remains `UNVERIFIED`).
- **Still cannot** autonomously consume reputation/discounts, operate `MerchantMenu`, acquire `Bad
  Omen`, trigger or lead raid defense as a first-class citizen, ring bells tactically, cure zombie
  villagers, assign workstations, expose canonical read-only **broad** workstation awareness (V3-D2
  deferred), or extract READY composter bone meal (V3-F gen-1 is input-only).
- **Actively conflicts** with “good villager citizen” play: `RaidContainersGoal` loots village chests at priority 3 (`CONFIRMED` — `PlayerMobEntity#registerGoals`).

**Mineflayer comparison:** Mineflayer achieves **scripted** parity for trading, pathing, and combat via plugins (`mineflayer-villager`, `mineflayer-pathfinder`). SPM achieves **reactive** combat and **scavenging** without a planner. Neither equals a human’s full menu/GUI literacy out of the box.

**Recommended integration:** Extend **`spmscavenger`** with a **`VillageInteractionDirector`** (see Topic below) that orchestrates perception, demand, personality, and task memory into executor goals — **without** turning `PlayerMobEntity` into a villager. Trading uses **`VillagerTradeAdapter`** (server-side, no fake GUI). Datapacks: **tags + test fixtures only**.

**Practical endgame for this RFC:** Autonomous **village ally** — defend during raids, trade via demand evaluation, bell alarm, population support, restock awareness — is **PARTIAL** feasible. Autonomous **full Hero-of-the-Village economy** (all professions, discounted hero trades, raid farming) is **NOT PRACTICAL** gen-1 without raid-credit bridges (`UNVERIFIED` for `Raid.addHero(Entity)` path).

---

## Topic: Reference parity — SPM vs Mineflayer vs human player

Comparison under **equivalent scenarios** (behaviour, not feature names).

### Scenario A — Discover village while exploring

| Behaviour | Human | Mineflayer | SPM today | Feasibility to extend |
| --- | --- | --- | --- | --- |
| Path into village | Yes | `pathfinder` goals | `WaterAvoidingRandomStrollGoal` / Scavenger `ExploringGoal` | **FULL** |
| Recognize village without `/locate` | Visual + structures | Chunk scan / POI plugin | Bounded loaded-POI perception + persistent `KnownVillage` | **IMPLEMENTED / VR-T1A PASS** |
| Avoid trampling crops | Sometimes | Configurable | `HarvestCropsGoal` breaks crops | **FULL** (disable harvest near village) |

### Scenario B — Trade with librarian for enchanted book

| Behaviour | Human | Mineflayer | SPM today | Feasibility |
| --- | --- | --- | --- | --- |
| Right-click villager | Opens `MerchantMenu` | `villager.trade()` plugin | `FriendlyGreetGoal` only (crouch/gift) | **PARTIAL** — `VillagerTradeAdapter` server-side (`D-VR-005`, `053`; no fake GUI) |
| Evaluate trade offer | GUI + knowledge | Scripted offer index | `TradeEvaluationPolicy` + demand-owned selection | **IMPLEMENTED / VR-T2 PASS** for scoped routes |
| Pay emeralds + items | Click slots | Plugin | 8-slot backpack | **PARTIAL** (inventory limit) |
| Refresh trades | Break/replace workstation | Plugin | None | **REQUIRES API** (workstation access) |

### Scenario C — Raid defense (village under attack)

| Behaviour | Human | Mineflayer | SPM today | Feasibility |
| --- | --- | --- | --- | --- |
| Illagers target you | Yes | Yes | **Yes** (`RaiderTargetsPlayerMobMixin`) | **FULL** (already) |
| Fight wave mobs | Yes | `pvp` / equip | `WeaponAwareAttackGoal` | **FULL** |
| Protect villagers | Intentional | Scripted priorities | No ally filter; may hit villager | **PARTIAL** (target category + disposition) |
| Ring bell | Yes | Block activate | No autonomous bell use | **PARTIAL** (`InteractableCapability`) |
| Hide in house | Yes | Path to shelter | `SeekShelterGoal` (bed scoring) | **PARTIAL** |
| Win raid → Hero | Yes | If present as Player | **No** — not `Player` class | **REQUIRES MIXIN** (effect + raid Omen) |

### Scenario D — Start raid in 1.21.1 (captain → bottle → Bad Omen → Raid Omen)

| Behaviour | Human | Mineflayer | SPM today | Feasibility |
| --- | --- | --- | --- | --- |
| Kill patrol captain | Combat; captain drops Ominous Bottle outside a raid | Combat | Combat works | **FULL** combat; pickup policy pending |
| Pick up and intentionally consume bottle | Inventory + self-use | Inventory/use plugin | Pickup/self-drink planning absent | **PARTIAL** — finishing applies Bad Omen to any `LivingEntity`; initiating self-use needs an executor |
| Enter intended village | Bad Omen transforms to 30-second Raid Omen | Plugin | Vanilla effect body rejects non-`ServerPlayer` | **REQUIRES BRIDGE** |
| Commit or abort during omen window | Wait; milk can cancel before expiry | Script policy | No raid-intent state | **PARTIAL** planner + bridge |
| Trigger raid when Raid Omen expires | Vanilla `Raids#createOrExtendRaid` | Plugin | Vanilla effect body is `ServerPlayer`-owned | **REQUIRES BRIDGE** |
| Farm raid loot | Yes | Scripted | Can fight; no wave orchestration | **PARTIAL** |

### Scenario E — TACZ-style firearms (pattern example, not village target)

| Behaviour | Human | Mineflayer | SPM today | Feasibility |
| --- | --- | --- | --- | --- |
| Recognize mod gun | Item knowledge | Plugin registry | `ModdedRangedWeapons` config | **FULL** (config tags) |
| Reload via item use | Yes | `bot.activateItem()` | `ModdedRangedAttackGoal` + fake player | **FULL** (proven pattern) |
| Avoid allies | Yes | Friendly-fire filter | `DispositionResolver` + loved ones | **PARTIAL** |
| Seek ammo | Yes | `bot.collectBlock` | `SeekAmmoGoal` | **FULL** |

**Parity verdict:** SPM **matches or exceeds** Mineflayer for **reactive combat + modded item use lifecycle**. SPM **lags** Mineflayer for **trading, raid orchestration, and menu-driven progression**. Full human parity is **NOT PRACTICAL** without a dedicated trade adapter subsystem (not a client GUI).

---

## Topic: Human-player parity vs villager lifecycle (`D-VR-004`)

**Author:** `Agent_ChatGPT`  
**Status:** `LOCKED`

### What we want

```text
PlayerMob
    interacts with
         ↓
 Vanilla Village System
         ↓
Villagers / Golems / Bells / POIs / Raids
```

…like **Steve** would — **player-interaction parity**, not copying villager internal AI.

### What we explicitly reject

```text
PlayerMob
  → acquire profession
  → claim workstation
  → sleep like villager
  → gossip like villager
```

That is **entity-lifecycle parity** (becoming a villager). Out of scope.

### In-scope human-like capabilities

A PlayerMob can potentially (`INFERRED` product goal; implementation per phase):

| Capability | Notes |
| --- | --- |
| Discover a village | `VillageMemory` / `VillagePerception` |
| Trade | `VillagerTradeAdapter` + `TradeWithVillagerGoal` |
| Bring supplies | Gift / drop / trade inputs |
| Ring bells | `RingVillageBellGoal` — **FULL** (`BellBlock.ring(Entity, …)`) |
| Help villagers breed | Player actions (food + beds), not villager `canBreed()` calls |
| Move villagers | Boat/minecart push — **PARTIAL**; no lead AI gen-1 |
| Build/repair infrastructure | Scavenger place + future build goals — **PARTIAL** |
| Defend village | Composed raid support (bell + combat) |
| Trigger/avoid raids | **REQUIRES MIXIN** for Omen; avoid via utility policy |
| Benefit from trades | Demand-driven acquisition |
| Seek particular professions | `KnownVillager` registry |
| Cause restock (indirectly) | Workstation reachability assistance |
| Cure zombie villagers | `CureVillagerGoal` + adapter |
| Loot abandoned resources | SPM loot with `StorageOwnership` gate |
| Establish base nearby | `KnownVillage` affinity + shelter |
| Return later | Persistent `VillageMemory` |

---

## Topic: VillageInteractionDirector (`Agent_ChatGPT`)

**Author:** `Agent_ChatGPT`  
**Status:** `CONSENSUS` — preferred orchestration layer; supersedes ad-hoc village goals.

Central coordinator in **`spmscavenger`**. **No Brain migration.** Does not replace SPM `GoalSelector`; feeds it.

### Architecture

```text
  [Gates — Village / SPM / Shelter / Trade policy / Navigation]
                 VillagePerception  →  facts + legal candidates
                        │
       ┌────────────────┼────────────────┐
       ▼                ▼                ▼
 KnownVillagers    KnownVillage      ActiveRaid
       │                │                │
       └────────────────┼────────────────┘
                        ▼
              VillageInteractionDirector
              (factual utility among VALID options)
                        │
        ┌───────────────┼────────────────┐
        ▼               ▼                ▼
   WorkDemand      TaskMemory      VillageDayNightContext
   (trade need)    (interrupt)     (read-only)
        │               │                │
        └───────────────┼────────────────┘
                        ▼
         Opinion layer (soft rank only — D-VR-025)
         SettlementOpinionBias.request(...)  ← bounded int; Opinion owns math
                        ▼
                   Utility choice
                        │
   ┌──────────┬─────────┼─────────┬───────────┐
   ▼          ▼         ▼         ▼           ▼
 Trade      Farm      Social    Village      Raid
 Goal       Goal       Goal     Support      Support
   │          │         │         │           │
   └──────────┴─────────┼─────────┴───────────┘
                        ▼
                 SPM GoalSelector (priorities)
                        ▼
           Navigation / interaction executors
```

**Authority rule (`CONFIRMED` — `docs/wiki/Opinion-System.md`):** *Preference affects choice. Preference
does not create permission.* Opinion ranks **already-legal** village candidates; it cannot declare a
settlement valid, override raid/shelter mandates, or invent trade permission.

### Memory models (not `VillagerBrain`)

**`KnownVillage`** — settlement graph, not vanilla village object clone:

```text
KnownVillage
├── anchor / approximate center
├── bell positions
├── beds seen
├── workstations seen
├── known villagers (→ KnownVillager)
├── known containers (+ StorageOwnership)
├── crop areas
├── current danger / raid link
├── last visit tick
└── (no affinity in V1 — factual tier only; see Opinion↔Village topic)
```

How a particular mob relates historically to a settlement (for example, *helped found*) is observer
history, not settlement identity. A future `SettlementHistory`/relationship record may carry that
fact; `KnownVillage` is created only by ordinary `VillagePerception` after world truth satisfies the
locked village predicate (D-VR-032).

**V1 code (`CONFIRMED`):** `KnownVillage` ships anchor, tier, observation quality only — explicitly **no**
villagers, offers, or affinity (`KnownVillage.java` class javadoc). `MobVillageMemory.designateHome()`
is the factual **HOME_VILLAGE** designation (`MobVillageMemory.java` L87–105).

**`KnownVillager`** — profession hunting registry (**V4+ / deferred from V2** — see D-VR-056):

```text
KnownVillager
├── UUID
├── profession + level
├── lastKnownPos
├── lastSeenTick
├── interestingOffers (cached snapshot)
└── restockBlocked? (workstation reach heuristic)
```

**Detection (`INFERRED`):** Several villagers + beds + bell/workstations → settlement; optionally consult vanilla POI via addon (`PARTIAL`). No `/locate` unless cheat profile.

### Mineflayer layering (copy abstraction, not implementation)

| Mineflayer | PlayerMob |
| --- | --- |
| API action → packets | `Task` → `Goal` → entity navigation |
| `openVillager()` / `trade()` | `VillagerTradeAdapter.performTrade()` |
| `find` / `pathfinder` | `VillagePerception` + SPM navigation |
| Compose jobs | `VillageInteractionDirector` utility choice |

**Do not** turn `PlayerMobEntity` into a fake networked player.

### Mixin scope (minimal) — **V2 trade amended (`D-VR-053`)**

| Needs mixin / bridge | Does not need mixin |
| --- | --- |
| Hero discount / player-session special prices (V6) | **V2 core trade** (`MerchantOffer` + `notifyTrade`) |
| Raid trigger eligibility (Bad Omen) | Bell ring |
| Raid reward/credit (`heroesOfTheVillage`) | ~~Crop harvest/replant~~ **`SUPERSEDED` by D-VR-079-A1** — *performing* harvest+replant needs no mixin, but *preventing* the destructive host `HarvestCropsGoal` inside the managed crop domain does (continuous veto, `*ShelterHoldMixin` shape) |
| Zombie-villager conversion attribution | Door walk |
| Advancement/stat parity (optional) | Village memory, inventory, social greet |

---

## Topic: Advanced village site selection & home anchoring (`Agent_Cursor`)

**Author:** `Agent_Cursor` (brainstorm continuation, 2026-08-14)
**Status:** `PROPOSED` — extends V1 `KnownVillage` + V4 persistent relationship; **not** vanilla `/locate` cheat.

Parallels mining **advanced site selection** (`RFC-VANILLA-AUTONOMOUS-PROGRESSION.md` — `MiningDirector`
chooses cave vs tunnel vs return). Here the director chooses **which settlement** and **which in-village
anchor** to act on.

### Problem (observable)

A PlayerMob that stumbles into three bed clusters has no principled way to pick a **home village**,
a **trade run target**, or a **raid-defend priority** without hardcoding nearest chunk. Humans pick by
safety, remembered traders, distance, and prior outcomes — not Euclidean distance alone.

### Settlement tiers (`PROPOSED`)

```text
PASSING_THROUGH   — seen once; no repeat utility
TRADING_POST      — known useful villager(s); commute acceptable
HOME_VILLAGE      — defend + storage ally profile; highest interrupt priority
AVOID             — looted chests, hit villagers, golem hostility signals
```

Tier promotion/demotion is **utility-driven**, not a quest flag.

### Representation gate before V4/V5 (`OPEN`)

`SettlementTier` currently compresses several independent facts into one enum. A settlement can be
the mob's home, a valuable trading destination, and temporarily unsafe at the same time. Before V4
or V5 persists more assumptions around this field, compare:

| Option | Shape | Benefit | Failure mode |
| --- | --- | --- | --- |
| Keep one tier enum | Existing `PASSING_THROUGH / TRADING_POST / HOME_VILLAGE / AVOID` | No migration; simple ranking | Mutually excludes states that can coexist; demotion may erase unrelated home/economic meaning |
| Decompose factual dimensions (recommended for review) | `HomeDesignation`, `EconomicRole`, `SafetyStanding`, later capabilities | Truthful combinations and narrower owners | Migration/serialization work; more predicates if introduced prematurely |

Do **not** change V1 merely from aesthetic concern. Gate the decomposition on V4/V5 consumer mapping,
saved-data migration design, and tests showing independent state transitions. **Must happen:** a home
can become temporarily unsafe without ceasing to be home. **Must not happen:** adding a trading role
silently removes home designation.

### Village site score — factual vs opinion split (`REVISED` — User + Agent_Cursor, 2026-08-14)

D-VR-009 locked the **tier vocabulary** and home designation. V4 utility-driven promotion must **not**
conflate village facts with personal preference — Opinion already owns the latter.

**Layer 1 — Village director (facts + legality only):**

```text
FactualVillageUtility =
    tradeNeedFit(MaterialDemand, knownOffers?)   // can this village satisfy a deficit?
  + safetyFacts(recentRaidAtAnchor)              // is it dangerous right now?
  + homeTierWeight(SettlementTier)               // HOME_VILLAGE factual designation
  - travelCost(pathDistance)
  - legalityPenalty(AVOID tier, blocked trade)
```

**Layer 2 — Opinion (soft rank among legal candidates; Village consumes, does not compute):**

```text
settlementBias = SettlementOpinionBias.request(village, opinionInput, ctx)
  // Opinion package owns Place chunk preference, personality, affect, Entity trader prefs
  // Returns int clamped to ±PlaceOpinionRouteRanker.MAX_ROUTE_BIAS (15)
```

**Combined ranking (V4+):**

```text
foreach village in MobVillageMemory.remembered():     // persisted; unload-safe
  if village.tier == AVOID: continue                    // factual gate
  if blockingDemand && !tradeReachable(village): continue
  if ShelterAuthority.mustHold(): continue
  factual = FactualVillageUtility(village, rememberedAnchor)
  score = factual + SettlementOpinionBias.request(...)
pick max score

// Refresh pass (when mob present + loaded chunks):
VillagePerception.observe → MobVillageMemory.remember
```

**Must happen:** mob far from a remembered `HOME_VILLAGE` with unloaded chunks between can still select
it for commute/defend when gates pass.
**Must not happen:** empty `VillagePerception.observe` (unloaded) **removes** the village from candidacy.

**Example (User):**

| System | Output |
| --- | --- |
| Village | A, B, C are **valid** trading destinations; C is **HOME_VILLAGE** (factual) |
| Opinion | "I like A more"; "bad experiences at B"; sociable → prefers populated; stressed → prefers familiar |
| Director | Chooses among **valid** options after gates; Opinion does not make B illegal |

**Must not happen:** negative Place opinion at village B **blocks** trade when `MaterialDemand` is
blocking and B is the only legal source. Opinion may prefer A when both are legal.

### In-village anchor selection (micro site selection)

Once a village is chosen, pick **which POI** to path to:

| Intent | Anchor candidates | Ranking |
| --- | --- | --- |
| Alarm | Known bells | Nearest reachable + line-of-sight to raiders |
| Trade | `KnownVillager` with restock | Offer utility − walk cost |
| Farm support | Crop clusters | Deficit-driven (`CropDemand`) |
| Raid hide (`EVACUATE`) | Shelter cells (SCR-2R5) | Interior tier + not villager-owned bed |
| Post-raid recovery | Safe retreat point from `RaidTask` | Last known interior during victory |

**Rejected:** flood-fill entire village POI graph gen-1; `/locate village`; chunk-global offer cache.

### Detection (`SUPERSEDED` — V1 shipped)

**Historical:** pre-V1 brainstorm proposed a bed-cluster heuristic. **D-VR-019 `LOCKED`:** detection is
`VillagePerception` bounded POI query reproducing vanilla raid-centre derivation (`VillagePerception.java`,
`VillageAnchorPolicy.java`). **`CODE_CONFIRMED`:** `KnownVillage`, `MobVillageMemory`, `SettlementTier`
ship in `com.noobk.spmscavenger.village`.

### Unblock

V1 **`IMPLEMENTED`** — detection + tier enum + home designation. **V1.5** adds mob-owned
`SettlementRelationship` + return/social slice (`D-VR-034`). **V4** ships factual utility scoring +
**Place-opinion bridge** at anchor (D-VR-025/026). V5 consumes `HOME_VILLAGE` for raid interrupt.

---

## Topic: Opinion ↔ Village boundary (`User` + `Agent_Cursor`)

**Author:** User (architecture); `Agent_Cursor` (RFC capture, 2026-08-14)
**Status:** `CONSENSUS` — aligns with shipped Opinion central rule (`docs/wiki/Opinion-System.md`).

### Division of labour

| Question | Owner | Opinion may not |
| --- | --- | --- |
| Is this actually a village? | `VillagePerception` | Invent settlements |
| Is this POI visible / loaded? | `VillagePerception` | Clairvoyant POI |
| Is this my home? | `MobVillageMemory` / `SettlementTier` | Override `HOME_VILLAGE` designation |
| Is a raid occurring? | `Raid` / `level.getRaidAt` | Ignore active raid |
| Can I legally trade this offer? | `TradeEvaluationPolicy` / `WorkDemandPolicy` | Create affordance |
| Must I seek shelter? | Shelter authority (`SHELTER_HOLD`) | Rank REST above mandatory hold |
| Must I obey a player order? | SPM command goals | — |
| Can I path there safely? | Navigation / executor admission | Skip hard blockers |
| **Which legal village do I prefer?** | **Opinion** (soft) + Director (pick) | — |

Opinion comes **after** those gates.

### What Opinion may influence (V4+ discretionary village time)

- Which **legal** village to visit for trade
- Which **legal** trader to prefer among several
- Whether to spend discretionary time **socializing** in a village (`SOCIAL` + `FriendlyGreetGoal`)
- Whether to **revisit** a pleasant settlement (Place preference at anchor)
- Whether to **explore** a newly discovered settlement (`adventurousness` / `curiosity`)
- How strongly to prefer **familiar vs novel** settlements (`stress` + Place memory)
- Soft weight when **adopting** a home (factual designation remains `designateHome()`; Opinion does not
  auto-promote tier without product rule)

### Investigation — should villages become Opinion subjects?

**Question:** Does `KnownVillage` fit under existing **Place** opinion, or justify a new **SETTLEMENT**
subject type?

| Need | Place @ **current** `KnownVillage.anchor` chunk | Dedicated SETTLEMENT subject |
| --- | --- | --- |
| "I like this geography" / bad experience at a place | **Yes** — `PlaceOpinionMemory` keyed by `ChunkPos` | Wrong abstraction |
| "I like this settlement" across anchor moves | **No** — geographic Place does not follow identity (D-VR-026 **HELD**) | **Yes** — settlement-persistent pref |
| Soft route bias to village | **Yes** — `PlaceOpinionRouteRanker` via `SettlementOpinionBias` (±15) | Overkill until identity gap proven |
| HOME_VILLAGE / TRADING_POST tier | **No** — factual `MobVillageMemory` | Wrong layer |
| Trader profession / offer memory | **No** — V2 `KnownVillager` + Entity opinion optional | Could blur subjects |
| Raid history at anchor | **No** — village factual record | Either works; keep factual |
| Sociability → populated settlements | **Yes** — inside `SettlementOpinionBias` (Opinion package) | New subject unnecessary gen-1 |

**Decision (`D-VR-026` **HELD** — User amendment, 2026-08-14):** **Do not add SETTLEMENT gen-1.** Place
learning and ranking use **current** `KnownVillage.anchor()` geography (`ChunkPos` of current anchor) via
`SettlementOpinionBias` → `PlaceOpinionRouteRanker`. **Rejected:** frozen `placeOpinionChunkKey` — that
turns geographic Place memory into a settlement-ID store.

**Accepted limitation:** when anchor supersede moves across chunks, Place preference does **not** follow
settlement identity; factual `MobVillageMemory` (tier, trade, home) still does. If runtime needs *"I like
this same village regardless of anchor movement"*, that is evidence to reopen **SETTLEMENT** — not to
encode settlement identity in an old chunk key.

**Evidence threshold to reopen SETTLEMENT:** Place@current-anchor + Entity trader prefs cannot express
settlement-persistent preference that (a) cannot live in `KnownVillage`/`KnownVillager` facts and (b)
cannot use Entity opinion for named traders — **after** V2/V4 ship and runtime proves the gap (including
anchor-cross-chunk preference continuity).

### Authority stack (village slice)

```text
world safety / combat threat
        ↓
player command
        ↓
nighttime SHELTER_HOLD / raid mandatory defend (profile-dependent)
        ↓
trade legality + navigation admission
        ↓
VillageInteractionDirector factual utility
        ↓
Opinion Place/Entity/Activity bias (≤ route tie-breaker magnitude)
        ↓
idle fallback
```

**Cross-link:** GAO-10 discretionary `SOCIAL` is the correct executor for "browse village socially" —
Opinion picks **who** to greet; Village supplies **where** the settlement is.

---

## Topic: Village attachment & settlement relationship (`User`)

**Author:** `User` (architecture + brainstorm, 2026-08-14)
**Status:** `PROPOSED` — **V1.5** slice; not implementation authorization
**Peer review:** `Agent_Cursor` (dedup + RFC capture, 2026-08-14)

### Problem (observable)

With V1 shipped, a PlayerMob can **know** a village exists (`KnownVillage`) but still treats it as
**POI cluster → (future) trade machine → leave**. Humans and the broader capability list already
assume something stronger: home nearby, return visits, discretionary social time, preferential trade,
bringing supplies, village manners, raid defense, and later personal-base establishment. The RFC has
most pieces scattered across V2–V7, but **"this place matters to me"** is not yet an explicit,
mob-owned layer between world truth and behavior.

### Layer model (`CONSENSUS` — aligns with Opinion central rule)

```text
KnownVillage
  = "I know this settlement exists."          // factual; per settlement row
        ↓
Settlement experience
  = "Things have happened to me here."      // events: visits, trades, raids, gifts, harm
        ↓
SettlementRelationship
  = "This place matters to me."             // mob-owned attachment + history
        ↓
Opinion / VillageInteractionDirector
        ↓
Behavior (return, socialize, work, trade, defend, camp nearby)
```

**Locked invariant (reinforces V1 + Opinion boundary):** *Preference affects choice. Preference does
not create permission.* Attachment may bias return or social time; it may **not** override combat
safety, player commands, shelter holds, or illegal trade/storage actions.

### What stays factual — `KnownVillage` (`LOCKED` — no change)

`KnownVillage` remains world-truth memory only:

```text
KnownVillage
├─ anchor
├─ observation quality / coverage
├─ factual tier (SettlementTier — home designation is factual, not affection)
└─ first/last seen
```

**Rejected:** storing `attachment`, `familiarity`, `socialHistory`, or personality-specific affection
inside `KnownVillage` — that would reintroduce the epistemic leak V1-R1/R4 paid to prevent.

**`CODE_CONFIRMED`:** `KnownVillage.java` javadoc explicitly excludes villagers, offers, containers,
and affinity; `MobVillageMemory.designateHome()` is the existing factual **HOME_VILLAGE** gate
(`MobVillageMemory.java` L87–105).

### Mob-owned relationship (`PROPOSED` — `SettlementRelationship`)

Attachment belongs on the **mob**, keyed to settlement identity (anchor merge via
`VillageIdentityPolicy`), conceptually:

```text
SettlementRelationship          // mob-owned; keyed by canonical anchor (Option A map)
├─ familiarityScore             // persisted; capped int
├─ lastVisitTick
├─ socialEventCount             // greets / GAO SOCIAL episodes credited at this settlement
└─ (derived, not persisted) attachmentBand()  // LOW | MEDIUM | HIGH from familiarityScore only

HOME is NOT stored here. Read home via MobVillageMemory.home() / KnownVillage.isHome() only.
helpfulHistory / meaningfulEvents → V3+ (no gen-1 producer).
```

**Storage shape (options — pick one at V1.5 lock):**

| Option | Shape | Benefit | Failure mode |
| --- | --- | --- | --- |
| **A — parallel map on `MobVillageMemory`** | `Map<anchorKey, SettlementRelationship>` beside `List<KnownVillage>` | Minimal new persistence file; clear split from factual rows | Two structures must stay in sync on merge/evict |
| **B — embed relationship handle on `KnownVillage`** | relationship fields adjacent but namespaced | One list to iterate | Violates "attachment not in KnownVillage" unless fields are strictly mob-prefixed and reviewed as migration |
| **C — separate `MobSettlementRelationships` saved-data** | Own codec keyed by mob UUID | Cleanest boundary | Extra saved-data surface; RET-1 for both |

**Recommendation (`Agent_Cursor`):** **Option A** for V1.5 — relationship map keyed by settlement
identity on `MobVillageMemory`, evicted with the same LRU rules as non-home villages. Revisit **C**
only if relationship event logs outgrow per-mob bounds.

### Attachment must accumulate (`PROPOSED`)

**Rejected:** `enter village once → LOVE +100`.

**Accepted accumulation signals (non-exhaustive):**

| Signal | Attachment effect | Owner |
| --- | --- | --- |
| Meaningful time in village bounds | `+` familiarity | Relationship |
| Sleep / camp nearby (not villager bed theft) | `+` attachment | Relationship + shelter |
| Discretionary `FriendlyGreetGoal` / GAO SOCIAL | `+` socialHistory | Opinion executor + relationship |
| Successful trades (V2+) | `+` economic familiarity | Relationship episode; persistent trader memory only after a separate consumer is approved |
| Helpful acts (replant, gift food, ring bell) | `+` helpfulHistory | V3+ executors |
| Raid defense / rescue | `+++` helpfulHistory | V5 |
| Repeated return visits | `+` familiarity | Relationship |
| `designateHome()` | factual `HOME_VILLAGE` tier (`MobVillageMemory` only) + relationship familiarity **floor** via `onHomeDesignated` | **Factual:** `designateHome()` · **Relationship:** service bump |

**Personality modulation (`INFERRED` — fits existing Opinion stack):** sociable mobs attach via
villagers; materialistic mobs via economy first; adventurous mobs oscillate leave/return; coward
mobs may attach because the settlement reads as **safe home** — all via `SettlementOpinionBias` /
personality inputs, not hardcoded tier jumps.

### What attachment should change (behavioral contract)

Strong attachment should make villages **visibly matter**:

| Behavior | Phase | Gate |
| --- | --- | --- |
| Return after exploration / mining | **V1.5** | Relationship + remembered anchor |
| Prefer camping / sleeping nearby | **V1.5** partial | Shelter + attachment; no bed theft |
| Greet / discretionary social time | **V1.5** | GAO SOCIAL + village-aware `FriendlyGreetGoal` |
| Prefer this village when multiple legal options | **V2+** | Trade legality first; attachment biases |
| Bring food/resources back | **V3** | `GiftPolicy` + storage ownership |
| Replant / village work | **V3** | Farm goals + manners |
| Avoid village-owned storage theft | **V1.5** precursor | VR-20 ally gate when HOME or high attachment |
| Defend during raids | **V5** | `HOME_VILLAGE` + raid matrix |
| Interrupt lesser work when endangered | **V5** | D-VR-010 |
| Personal base nearby | **V4** | Site utility + Place opinion |

**Must happen (V1.5):** Bob with `HOME_VILLAGE` and high familiarity **paths back** after a long
mining trip instead of treating all remembered clusters as equal noise.
**Must not happen:** attachment causes looting village chests, ignoring mandatory shelter, or
abandoning combat safety.

### Relationship to existing RFC pieces (dedup)

| Existing | Relationship to attachment |
| --- | --- |
| `SettlementTier` / `HOME_VILLAGE` | **Factual** designation — attachment **reads** it, does not replace it |
| `SettlementOpinionBias` (D-VR-025) | **Soft rank** among legal villages — consumes relationship + Place/Entity opinion |
| V4 return visits / home designation | **Split:** factual home stays V1; **return commute + attachment history → V1.5** |
| B-VR-17 GAO SOCIAL browse | **→ V1.5** executor slice |
| B-VR-40 stress → familiar anchor | **→ Opinion package**; attachment supplies "familiar" signal |
| SETTLEMENT Opinion subject (B-VR-39) | **Still DEFERRED** — relationship history is factual/experiential, not a new Opinion subject type |
| `SettlementTier` decomposition gate | **Still OPEN** — attachment does not remove need to split home/economic/safety **facts** before V5 |

### Phase insert — **V1.5** before V2 Trading (`PROPOSED` — `D-VR-034`)

User proposal: small village/social features **before** full `VillagerTradeAdapter` — makes trade
*meaningful* ("I trade **here** because I care") instead of nearest-offer machine.

```text
V1   ✅ Perceive villages (IMPLEMENTED; VR-T1A PASS)
V1.5 Settlement attachment & return (NEXT)
├─ SettlementRelationship persistence + accumulation rules
├─ recognize factual home + familiarity bands
├─ return / commute-to-home-or-familiar goal
├─ village-aware FriendlyGreet / discretionary SOCIAL weighting
└─ (V1.5-E / VR-T1.5d **DEFERRED** → V3 `StorageOwnership`)
V2   Trading (VillagerTradeAdapter, TradeEvaluationPolicy, TradeWithVillagerGoal)
V3   Village Work
V4+  Reputation-aware site utility / raid / player-parity bridges
```

**Strongest objection:** V1.5 scope creep absorbs V3 manners + V4 return + part of V5 ally logic.
**Mitigation:** V1.5 MVP is **relationship + return + social weighting only**; ally storage gate,
replant, full gift loops, and raid interrupt remain later phases.

**Viable alternative:** keep original plan (V2 trade before attachment). **Rejected for gen-1** per
User — trade without attachment reads as machine-like; attachment without trade still produces
visible "my village" play.

### V1.5 runtime matrix (`PROPOSED`)

| ID | Must happen | Must not happen | Result |
| --- | --- | --- | --- |
| VR-T1.5a | After 10+ min away, mob with HOME paths toward home anchor | Treats home same as never-seen cluster | **PASS** (User, Bob, 2026-08-15) |
| VR-T1.5b | Repeated visits increase familiarity in saved data | Single visit maxes attachment; standing-still HIGH farm | **CLOSED PASS** (User, God, 2026-08-15) |
| VR-T1.5c | Village-aware greet fires more near familiar settlement | Greet mistaken for trade completion | **CLOSED PASS** (User, God, 2026-08-15) — `350→390`, social events `0→1`; taxonomy repair |
| ~~VR-T1.5d~~ | **DEFERRED → VR-T3** (`D-VR-052`) | — | — |

### Decisions

| ID | Decision | Status |
| --- | --- | --- |
| **D-VR-034** | Insert **V1.5** (attachment + return + village social) **before V2 Trading** | **`LOCK RECOMMENDED`** — User intent + VR-T1A foundation |
| **D-VR-035** | `SettlementRelationship` is mob-owned, separate from factual `KnownVillage` | **`LOCK RECOMMENDED`** — Option **A** (parallel map on `MobVillageMemory`) |
| **D-VR-036** | Attachment accumulates from events; no single-visit max | **`LOCK RECOMMENDED`** — gen-1 bands below |
| **D-VR-037** | V1.5 MVP excludes `VillagerTradeAdapter` and raid interrupt | **`LOCKED`** — scope guard |
| **D-VR-038** | Return commute via **ExploringGoal / discretionary bias**, not priority-3 gather competitor | **`LOCK RECOMMENDED`** — see implementation contract |
| **D-VR-039** | `designateHome()` needs a **production caller** for VR-T1.5a (debug command minimum) | **`LOCK RECOMMENDED`** — **CONFIRMED** gap: API exists, zero production callers |
| **D-VR-040** | Presence/familiarity bounds = **64-block** perception radius (`SettlementBoundsPolicy`) | **`LOCK RECOMMENDED`** |
| **D-VR-041** | Single `SettlementRelationshipService` write path (record / presence / social / home-designated) | **`LOCKED`** |
| **D-VR-042** | Auto-home when HIGH familiarity + sleeps (config default off) | **`PRODUCT DECISION`** — manual debug ships first |
| **D-VR-043** | Commute blocked during mining/cave handoff; forced-heading `ExploringGoal` seed | **`LOCK RECOMMENDED`** |
| **D-VR-044** | Merge relationship rows when `remember()` merges village identity | **`LOCK RECOMMENDED`** |
| **D-VR-045** | Village SOCIAL via `SettlementSocialBias`, not global FriendlyGreet hack | **`LOCK RECOMMENDED`** |
| **D-VR-046** | Settlement return requires `ScavengerConfig.exploring` | **`LOCKED`** |
| **D-VR-047** | COMMUTE = non-discretionary `ExpeditionKind`; admitted before discretionary explore gate | **`LOCKED`** |
| **D-VR-048** | Multi-leg commute until inside `SettlementBoundsPolicy` (150-block legs) | **`LOCKED`** |
| **D-VR-049** | Option A relationship `rekey` on anchor supersede + merge on `remember()` + evict sync | **`LOCKED`** |
| **D-VR-050** | Social credit requires `settlementAnchorAtStart` on greet binding at admission | **`LOCKED`** |
| **D-VR-051** | No permanent village debug commands; temporary `designate-home` removed post VR-T1.5 | **`LOCKED`** |
| **D-VR-052** | V1.5-E ally loot gate — **REJECT for V1.5**; defer to V3 `StorageOwnership` + VR-T3 | **`LOCKED`** — User 2026-08-14; HOME/HIGH proof predicate explicitly **SUPERSEDED** by D-VR-017 on 2026-08-19 |

**Authorized:** task-46 / V1.5 slices A–D + temporary F → **1.11.0**. **Not authorized:** V1.5-E,
Minecraft launch.

**Historical frontier (SUPERSEDED):** this section originally pointed to V2 Trading / task-47.
V2 is now **CLOSED — VR-T2 RUNTIME PASS**. Task-52 (shared `MandatoryOwnership` / `V2-DEF-002`
repair, `D-VR-084`) is **IMPLEMENTED / STATIC-BEHAVIORAL ACCEPT + R1** (2026-08-20). Tasks 53–57
subsequently delivered V3-A/B/C/D1/E static slices; **task-58/V3-F CLOSED** (2026-08-22). The
canonical frontier is **Task-59 / V3-G — NEXT but HOLD** until separately authorized.

### V2 implementation contract (`IMPLEMENTED + CLOSED` — historical contract retained)

**Evidence baseline (`CODE_CONFIRMED`, pinned 1.21.1 jar):**

- `MerchantOffer#satisfiedBy` / `#take` mutate payment stacks without `MerchantMenu`. **Wording reconciled 2026-08-15 (task-47):** this line established that a menu is not required; it is **not** a mandate to use `#take` for payment. `#take` is menu-shaped (it shrinks only the two stacks passed) and is **superseded for payment** by `D-VR-061`/`D-VR-071`'s staged multi-slot allocator. **V2-A as shipped calls `take` zero times**; `satisfiedBy`-equivalent affordability is performed by `TradeTransaction.debit`'s pre-check.
- `AbstractVillager#notifyTrade(MerchantOffer)` increments uses, awards villager XP, plays sound — **no `Player` parameter**; `CriteriaTriggers.TRADE` and NeoForge `TradeWithVillagerEvent` fire only when `tradingPlayer instanceof ServerPlayer` (absent for PlayerMob — acceptable gen-1).
- `Villager#setTradingPlayer` / `#updateSpecialPrices(Player)` remain **player-typed** — hero discount stays **V6** (B-VR-34); gen-1 does **not** call `setTradingPlayer`.
- Production inventory seam: `PlayerMobs.backpack(mob)` (`ExplorationActivityGoal.java`).


**Evidence addendum (`Agent_Claude`, 2026-08-15) — three stack-identity facts the baseline did not pin.**
Additions to the baseline above, not corrections to it.

| Accessor | Returns | Safe to hand to a container? |
| --- | --- | --- |
| `MerchantOffer#getResult()` | the **live `result` field** | **no** |
| `MerchantOffer#assemble()` | `result.copy()` | yes |
| `MerchantOffer#getCostA()` | `baseCostA.itemStack().copyWithCount(n)` | yes |
| `MerchantOffer#getBaseCostA()` | the **live `ItemCost.itemStack` field** | **no** |

**The asymmetry is the trap.** The cost accessor copies and the result accessor does not, so an
implementer who checks `getCostA()` and finds it safe will reasonably assume `getResult()` is too.
Inserting `getResult()` into the backpack aliases the villager's own offer stack: any later count
change corrupts that villager's offer permanently, the same instance can reach two mobs' backpacks,
and it persists to the world. Vanilla's own `MerchantResultSlot` uses `assemble()`.

**V2-A must therefore call `assemble()` and `getCostA()`**, never `getResult()` / `getBaseCostA()`,
and a structural test should say so — the wrong call compiles, runs, and only shows up as an offer
quietly changing.

**`take(a, b)` is menu-shaped, and a backpack is not a menu.** Bytecode (offsets 0–44):

```java
if (!satisfiedBy(a, b)) return false;
a.shrink(getCostA().getCount());
if (!getCostB().isEmpty()) b.shrink(getCostB().getCount());
return true;
```

It mutates **exactly the two stacks handed to it** and no others, because `MerchantMenu` guarantees
payment sits consolidated in two slots. The SPM backpack has 8 slots and no such guarantee: a
20-wheat cost is routinely `16 + 4` across two of them. So `take` cannot express the payment, and
V2-A's staged transaction must debit across slots itself and use `satisfiedBy` only as the
*validation* half. **Failure shape:** compiles, passes any single-slot test, silently under-pays on
the first bulk offer.

**Checked and rejected — offer indices are stable across a villager level-up.**
`Villager#updateTrades` calls `addOffersFromItemListings(getOffers(), …)`, which **appends** to the
existing `MerchantOffers`; it does not clear or rebuild. Existing offer objects and their positions
survive, so a level-up mid-chain does not invalidate a cached index. V2-D's re-resolution therefore
does **not** need a level-up trigger, and no work is required here.

**VR-T2 uncontaminated proof chain (`LOCKED` — `D-VR-069`):** the **first** runtime proof for V2 core
(task-47 / VR-T2…VR-T2h) must exercise **only** this stack — no Trade Everything, no synthetic offers,
no player-session offer injection:

```text
spmscavenger (VillagerTradeAdapter + TradeWithVillagerGoal)
        ↓
real vanilla Villager (live entity; getOffers() as persisted + restocked)
        ↓
real vanilla MerchantOffer (from that villager's offer list — not index 0 session injection)
        ↓
PlayerMob backpack pays exact ItemCost A (+ B when present)
        ↓
PlayerMob backpack receives exact offer result stack
        ↓
that MerchantOffer uses +1 (via notifyTrade once — no double increment)
```

**Runtime mod set for VR-T2…VR-T2h:** `playermob` + `spmscavenger` + Fabric API (+ test datapack
`V2-H`). **`tradeeverything` must be absent.** Trade Everything v0.3.0 temporarily injects a
synthetic offer at offer index 0 during normal **player** trade sessions (`setTradingPlayer` mixin);
even if that path probably does not affect every PlayerMob automation route, it adds unmeasurable
uncertainty to the baseline and is **excluded on purpose**. VR-T2k/l (`V2-TE`) run only **after**
VR-T2 **PASS** in the vanilla-only instance (`D-VR-068`).

**Scope (in):**

| Slice | Deliverable |
| --- | --- |
| **V2-A** | `VillagerTradeAdapter` + immutable `OfferSnapshot`; **joint two-cost slot allocation** (`D-VR-071`) over backpack copies; staged `SlotDelta` built only at commit instant (`D-VR-072`); preflight result insertion; exact live-offer revalidation; `notifyTrade` once after inventory commit. **`assemble()` / `getCostA()` only** (never `getResult()` / `getBaseCostA()` — live stacks); **multi-slot debit**, with `take` used only as `satisfiedBy` validation (B-VR-90/91) |
| **V2-B** | `TradeEvaluationPolicy` — score `MerchantOffer` vs `WorkDemandPolicy.MaterialDemand` (D-VR-015 / B-VR-20 facade; no rename blocker) |
| **V2-C** | `TradeDemandRegistrar` + **feasibility filter before `select()` wins** (`D-VR-015`); parallel acquisition candidates per `ConsumerRecipeSpec` (CRAFT/SMELT vs TRADE); no autonomous emerald-hoarding loop |
| **V2-D** | transient bounded `TradeChainPlan` SELL → BUY ticket (D-VR-029); **`SellExpendabilityPolicy`** disposable-quantity math (`D-VR-058`); re-evaluate before every transaction; expiry; re-resolve current offer/villager after interruption or reload |
| **V2-E** *(CLOSED 2026-08-16 — STATIC ACCEPT after R1–R8; integrated execution path **runtime-covered by VR-T2**)* | Introduces **`TradeWithVillagerGoal`** @ priority **3** (MOVE+LOOK) with one selected `TradeDemandGate` owner, the bounded `TradeCandidateRound`, the greet interlock, and the full executor: exact-quote binding, execute-time SELL reauthorization, cross-villager `TradeAttemptFunding`, and post-SELL chain continuation. The `ActivityClass.VILLAGE_TRADE` **enum value and its classifier pin belong to V2-F** (`D-VR-073`: the goal type must exist before anything classifies it) |
| **V2-F** *(CLOSED 2026-08-16)* | Introduces **`ActivityClass.VILLAGE_TRADE`**, the `MoveHolderClassifier.staticActivityClass` pin `TradeWithVillagerGoal` → `VILLAGE_TRADE`, and the `classify()` mapping → **`ORDINARY_HOST_WORK`** (`D-VR-074`). **Co-lands with V2-E or immediately follows in the same task batch** (`D-VR-073`); the classifier cannot precede the goal type it classifies |
| **V2-G** *(CLOSED 2026-08-16)* | `SettlementRelationshipService.onTradeEpisode` familiarity bump once per completed visit/chain; no persistent `KnownVillager` or offer-index memory in gen-1. **If any part persists per-mob, it must register in `PerMobSavedData.forgetAll`** or `PerMobRemovalContractTest` fails the build (B-VR-93, Gate RET-1e) |
| **V2-H** *(CLOSED)* | VR-T2 fixture/proof support completed its runtime purpose and was removed after evidence capture; permanent route-contract tests remain |
| **V2-I** *(DEFERRED / NON-BLOCKING)* | Optional O-panel trade inspector row (`B-VR-79`): active `consumerKey`, anchor, villager id, `TradeBlockedReason` — no new debug command; not a V2 or V3 gate |
| **V2-TE** *(positive path RUNTIME CONFIRMED; phase CLOSED)* | Trade Everything v0.3.0 Fabric compatibility through `TradeOpportunitySource`; no fake player/menu/session; exact quote revalidation; fail closed when absent/incompatible (`D-VR-068`). `V2-DEF-003c-R1`: 12 exact TE funding sells autonomously funded one vanilla Toolsmith iron-pickaxe purchase. The separate absent/incompatible `VR-T2l` run is **DEFERRED / NON-BLOCKING**, not an open V2 phase gate. |

**Implementation dependency order (`LOCKED` sequencing — amended pass 2):**

```text
V2-A (adapter) → V2-B (evaluation) → V2-C (demand registrar) → V2-D (chain ticket)
        ↓
V2-E (TradeWithVillagerGoal + TradeDemandGate + executor)
        ↓
V2-F (VILLAGE_TRADE enum + MoveHolderClassifier pin + ORDINARY_HOST_WORK mapping)
        — same task batch as V2-E or next commit; never before E (D-VR-073)
        ↓
V2-G (onTradeEpisode) → V2-H (datapack fixture)
V2-I (inspector) — DEFERRED / NON-BLOCKING; not a release or V3 gate
V2-TE (Trade Everything compat) — CLOSED positive path; VR-T2l negative remains deferred
```

**Scope (out):** client `MerchantMenu` / fake GUI (`D-VR-005`); hero discount mixin (V6);
read-only restock/workstation awareness (V3); workstation placement/claim and door-clear construction
(deferred advanced work); discretionary trade browse without demand (V2+ backlog);
`RaidContainersGoal` ally-storage gate (V3, `D-VR-052`); full `VillageInteractionDirector` (V5);
persistent `KnownVillager` / offer memory; wandering-trader TTL merchant (B-VR-16 deferred);
reputation **read** accessor consumer (V4).

**Admission (`LOCKED` — amended peer review 2026-08-15):**

```text
derive consumer need (e.g. ConsumerRecipeSpec / external demand)
        ↓
derive acquisition candidates
   ├─ SMELT / CRAFT feasible now?
   └─ TRADE feasible only if loaded evidence supports a route (D-VR-015)
        ↓
WorkDemandPolicy.select() among FEASIBLE candidates only
        ↓
TradeDemandGate.activeDemand(mob)     // mutual exclusion @ P3; only if TRADE won
        ↓
TradeSettlementPicker                 // legality/offer fit BEFORE home/familiarity rank (D-VR-070)
        ↓
TradeWithVillagerGoal
   WALK → FACE → re-fetch live villager/offer/backpack
        → build fresh SlotDelta → simulate → revalidate → APPLY IMMEDIATELY (D-VR-072)
        ↓
(on chain) TradeChainPlan.advanceStep()   // disposable sell qty re-checked each hop (D-VR-058)
```

**Villager / settlement pick (gen-1):** scan **remembered settlements that are currently
loadable/observable**; **hard-filter** for a useful offer matching the active demand, legal villager
availability (`VillagerTradeAvailability`), and plausible path; **then** rank by HOME / familiarity /
offer utility / travel cost (`D-VR-070`). **Do not** call `SettlementReturnPolicy.commuteTarget()` —
that answers *"where do I return?"*, not *"where can I satisfy this trade?"* An unloaded remote village
with unknown offers is **honestly unselectable** (good epistemics, not a V2 gap). Nearest **reachable**,
alive, non-sleeping `Villager` with a currently useful offer inside `SettlementBoundsPolicy`. Offer
utility may create a soft profession preference, but the picker does not hardcode `librarian = good`
(`D-VR-007`). No live match → bounded `TaskLifecycle.BLOCKED`, not a random-villager loop. A villager
with `getTradingPlayer() != null` is temporarily unavailable.

**Trade vs greet (`MUST NOT` repeat VR-T1.5c) — `D-VR-067` is mandatory, not defensive plumbing:**

`SOURCE_CONFIRMED` (SPM v0.86.0): `FriendlyGreetGoal` owns **MOVE + LOOK**, actively searches for
`Reaction.GREET` targets; villagers are classified **VILLAGERS**; friendly PlayerMobs resolve
villagers to **GREET**. Real collision sequence:

```text
PlayerMob wants to trade with Villager A
        ↓
reaches FACE phase (MOVE + LOOK)
        ↓
SPM FriendlyGreetGoal also sees A as GREET
        ↓
both want MOVE/LOOK on the same villager
```

| Guard | Mechanism |
| --- | --- |
| Activity taxonomy | `TradeWithVillagerGoal` → `ActivityClass.VILLAGE_TRADE` (not `SOCIAL_REFLEX`) |
| Concurrent greet | `canUse()` false while `FriendlyGreetGoal` running **or** `SocialExecutionBindingRegistry` holds SOCIAL binding |
| Trade face phase | **`TradeSessionClaimWindow` mandatory** (`D-VR-067`): same villager only, FACE/EXECUTE only, bounded expiry — blocks greet admission symmetric to `SocialGreetClaimWindow` |
| Credit separation | `onTradeEpisode` ≠ `onSocialEpisode`; trade does not increment `socialEventCount` |
| Post-commit presentation | Optional villager-facing linger **after** commit is **presentation** (`RFC-ACTION-TRANSITIONS.md` `TRADE_COMPLETE`) — distinct from `TradeSessionClaimWindow` permission guard |

**`TradeChainPlan` gen-1 contract (D-VR-029):**

```text
TradeChainPlan
  consumerKey          // stable owner, e.g. spmscavenger:trade_chain/mending_book
  desiredOutput/demand // durable meaning; not a MerchantOffer/index identity
  steps[]              // SELL then BUY; current offer is resolved live per attempt
  sellQuantity         // disposable count authorized by SellExpendabilityPolicy (D-VR-058)
  openedAtTick / expiryTick
  anchorHint           // optional BlockPos — settlement hint, not commuteTarget()
```

Gen-1 stores this ticket only while the entity session is live. Save/reload closes it neutrally;
the still-valid external demand may construct a fresh plan from current offers. This is safer than
serializing a volatile offer index, price, use count, or villager UUID as durable authority.

**Disposable lifetime rule (`LOCKED`):**

| Artifact | May survive interruption / ticks? |
| --- | --- |
| `OfferSnapshot` | Yes — evaluation evidence for one attempt |
| `TradeChainPlan` | Yes — durable demand meaning + step index |
| `Path` | **No** — discard on interruption (`D-VR-062`) |
| `SlotDelta` / staged inventory mutation | **No** — must not survive a scheduler yield (`D-VR-072`) |

Between WALK and FACE the backpack can change (SPM `EatFoodGoal`, loot, gift, pickup). **Safer
execution (`D-VR-072`):**

```text
WALK
  ↓
FACE (+ TradeSessionClaimWindow on same villager)
  ↓
re-fetch live villager, live offer, live backpack
  ↓
build fresh staged transaction from CURRENT slots
  ↓
joint-allocate costs (D-VR-071) → simulate debit → simulate result insertion
  ↓
revalidate demand / customer / offer / disposable sell quantity
  ↓
APPLY IMMEDIATELY (one server-thread SlotDelta)
  ↓
notifyTrade exactly once
```

**Transaction boundary (`LOCKED` design — `D-VR-061` + `D-VR-071` + `D-VR-072`):** copy all
backpack slots at **commit instant**; run a bounded **joint** allocation for cost A and optional cost B
(**one item count may satisfy at most one cost allocation** — stacks may be **partitioned** across costs,
e.g. one stack of 2 emeralds paying cost A=1 and cost B=1; find a valid partition when one exists —
greedy pay-A-then-B can falsely reject overlapping predicates, e.g. generic diamond + component-specific
diamond in different slots); simulate removal and result insertion on copies; revalidate live offer; apply
one slot-index delta; then `notifyTrade` once. `MerchantOffer#take` validates only — not the allocator.
Mineflayer's menu consolidates payment into two slots; our 8-slot `SimpleContainer` cannot assume that.

**Interruption/arbitration:** a temporary P1 greet/door helper discards the current navigation path,
not the demand/chain meaning; resume re-resolves a fresh path and offer. Player command, combat,
night shelter, death/unload, expiry, or invalid demand closes/yields according to existing authority.
An already-running SPM `RaidContainersGoal` may complete its bounded episode; its 20-tick post-visit
cooldown should then expose P3 to trade. This is `CODE_INFERRED`, not runtime proof. VR-T2e is a
release gate; if trade remains starved, use a thin centralized *exact admitted-trade* suppression
hook rather than raising trade above command/shelter or prematurely shipping full V3 storage policy.

**Must happen (static):** offer scoring unit tests; staged transaction tests for split costs, **joint two-cost partition** (D-VR-071 negative controls: (1) generic + component-specific diamonds in different slots; (2) **one stack of 2 emeralds** paying cost A=1 + cost B=1), full/near-full backpack, component mismatch, stale price/use, human customer, and two sequential mobs; successful trade removes exact cost, inserts exact result, and increments uses once; **SlotDelta built only after FACE with fresh backpack**; taxonomy regression for `VILLAGE_TRADE`; chain step advance + **SellExpendability** negative control (SPM may eat; trade replans with reduced disposable qty); **trade candidate blocked from winning `select()` when no feasible route**; `TradeDemandGate` excludes concurrent Scavenger P3 work when chain active; one relationship episode for a multi-click visit.

**Must not happen:** mutate costs before result capacity is known; **persist SlotDelta across ticks**; greedy two-cost allocator **double-counting item counts** or false reject when stack partition is valid; double `increaseUses`; use a stale offer index after restock/reload; automate a villager while a real player is its customer; greet completion credited as trade; one ten-trade visit grants ten familiarity episodes; **global food lock against SPM EatFoodGoal**; selling below disposable quantity; create emeralds with no external consumer; **trade wins `select()` then suppresses feasible smelt/craft path**; call `commuteTarget()` for trade settlement choice; raise trade priority over command/combat/shelter to hide P3 arbitration.

**Verification:** `.\gradlew.bat test --tests "*trade*"` + `*village*` structural extensions; VR-T2 /
VR-T2b runtime after launch approval in a **vanilla-only** instance (`D-VR-069` — no
`tradeeverything`); God/Bob taiga fixture or `V2-H` datapack preset.

**Artifacts:** `.superpowers/sdd/task-47-brief.md` + `task-47-report.md` (implemented history; V2 is
closed, so this is no longer an authorization frontier).

### V2 runtime matrix (`CLOSED`; residual rows explicitly deferred)

| ID | Must happen | Must not happen | Notes |
| --- | --- | --- | --- |
| **VR-T2** | **Vanilla-only instance** (`D-VR-069`): mob paths to farmer; pays **exact** carrot cost; receives **exact** emerald result; offer **uses +1** | `tradeeverything` installed; synthetic/session offer; client menu; dupe/void; greet as trade | First proof = uncontaminated vanilla chain |
| **VR-T2b** | Deterministic fixture: sell expendable input → acquire emeralds → buy the externally demanded tool/output within expiry | Sells protected survival stock; resumes stale BUY after reload without revalidate; hoards emeralds with no consumer | D-VR-029 / D-VR-065 |
| **VR-T2c** | Trade deferred when villager sleeping / night window (`VillageDayNightContext` helper) | Busy-waits all night blocking shelter | Pure read helper ships in V2; full director matrix remains V5 |
| **VR-T2d** | `onTradeEpisode` bumps familiarity separate from greet | Trade mistaken for social event in inspector | Extends V1.5 relationship service |
| **VR-T2e** | Visible village chest may delay one admitted trade only by the current bounded raid-container episode; trade then starts during cooldown | Infinite `Raiding chest ↔ Trading` churn or permanent trade starvation | Decides whether a narrow host-goal admission hook is actually necessary |
| **VR-T2f** | Friendly greet/door helper interrupts travel; the exact external demand survives and trade resumes with a fresh path/current offer | Preserve old `Path`; credit trade before commit; restart arbitrary emerald plan | Path state is disposable; demand identity is not |
| **VR-T2g** | Two PlayerMobs choose the same nearly exhausted offer; first commits, second revalidates and replans/blocks | Both consume final use; duplicate result; permanent villager reservation | Gen-1 uses server-thread revalidation, no persistent trader claim |
| **VR-T2h** | Backpack costs split over slots and result only fits after payment; staged simulation commits exactly once | Reject affordable split stack; void result; partial payment | Gate 4.18-style transaction case |
| **VR-T2i** | Night window or all reachable villagers sleeping → `TaskLifecycle.BLOCKED` with `TradeBlockedReason` | Busy-spin at bed; trade goal owns MOVE+LOOK all night | `D-VR-059` + `D-VR-066`; extends VR-T2c |
| **VR-T2j** *(stretch)* | Component-predicate cost (enchanted/custom-data offer) commits once when fixture provides one | Reject affordable enchanted payment; match wrong component only | Optional if `V2-H` fixture includes one; not gen-1 release gate |
| **VR-T2k** *(Trade Everything optional)* | **Only after VR-T2 PASS** (`D-VR-069`). With `tradeeverything` v0.3.0 installed, an eligible surplus stack produces its exact synthetic quote without opening a menu or setting `tradingPlayer` | Synthetic offer absent merely because no human session exists; fake `ServerPlayer`; saved synthetic offer | **PASS — `V2-DEF-003c-R1`, 2026-08-19.** `plans=13 (TE 12)`, `revals=13`, `trades=13`; 12 TE `22 oak_log -> 1 emerald` sells funded vanilla Toolsmith `12 emerald -> 1 iron_pickaxe`; `routeEvidence tracked=0` |
| **VR-T2l** *(Trade Everything negative; DEFERRED / NON-BLOCKING)* | Missing/incompatible Trade Everything disables only that source; vanilla trades continue | Contaminating VR-T2 baseline with `tradeeverything` present; startup/classloading crash; reimplemented approximate pricing | `UNVERIFIED` — positive Step-7A does not exercise absence/incompatibility; this does not reopen V2 or block V3 |

### Task-46 peer review — User P1 closure (`AUTHORIZED`, 2026-08-14)

**Review state (`CONFIRMED`, User):**

| Item | Status |
| --- | --- |
| V1.5 concept | **APPROVED** |
| Phase placement (before Trading) | **APPROVED** |
| Architecture direction | **APPROVED** |
| Task-46 implementation | **AUTHORIZED** — D-VR-052 REJECT V1.5-E |

#### P1-1 — V1.5-F vs VR-T1A cleanup (`CLOSED`)

**Finding:** Re-adding `village-memory` contradicts `mustHappen_vrT1aDiagnosticsRemoved` and the VR-T1A
permanent-removal invariant.

**Resolution (`LOCKED`):**

- **No** `village-memory`, `village-probe`, or `village-driver` in task-46 or 1.11.0 release.
- VR-T1.5 probes use **unit tests + save/reload NBT inspection** (or logger at `INFO` on relationship
  change). Bob session is **overworld-only**; cross-dimension retest **not** required (VR-T1A already
  PASS).
- **Temporary** operator command `/spmscavenger designate-home <PlayerMob>` is allowed **only** for
  VR-T1.5a until runtime PASS, then **removed** in the same cleanup pass as VR-T1A diagnostics
  (task-46 report must list removal; contract test continues to forbid `village-memory`).

#### P1-2 — Home ownership (`CLOSED`)

**Finding:** `home?` in relationship sketch + “factual only” designate-home + `onHomeDesignated`
missing from D-VR-041 write path.

**Resolution (`LOCKED`):**

| Fact | Single owner |
| --- | --- |
| Is this my home? | `MobVillageMemory.designateHome()` → `KnownVillage.tier == HOME_VILLAGE` |
| Familiarity floor on designation | `SettlementRelationshipService.onHomeDesignated(mob, anchor, tick)` |

`designate-home` command calls **both** (factual tier + relationship floor). Relationship stores **no**
home flag.

#### P1-3 — Presence radius stale text (`CLOSED`)

**Finding:** Accumulation table said “raid-association radius” while D-VR-040 locks presence @ **64**.

**Resolution (`LOCKED`):** all presence/familiarity heartbeat ticks use
`SettlementBoundsPolicy.within(mobPos, anchor)` → **64²**. Raid association **96²** is V5 only.

#### P1-4 — Return-commute admission sequence (`CLOSED`)

**Finding:** Forced heading specified; admission point in `ExploringGoal.canUse()` was not pinned.

**Resolution (`LOCKED` — `D-VR-047`):** commute is a **non-discretionary** expedition kind
(`ExpeditionKind.COMMUTE`). Admission in `ExploringGoal.canUse()` **after** `MiningExecutionGuard` and
**after** cave-handoff check, **before** `mayStartDiscretionaryExplore()`:

```text
canUse():
  … enabled, exploring, no target …
  MiningExecutionGuard.permits(…)
  if acceptCaveHandoff → true          // commute blocked while handoff active
  if retry window → false
  if expedition == null:
      if SettlementReturnPolicy.trySeedCommute(…) → expedition COMMUTE, return true
      … eligibility + mayStartDiscretionaryExplore for ordinary explore …
```

COMMUTE expeditions **do not** call `DiscretionaryAuthority.onExploreAdopted` — SOCIAL/REST arbitration
unchanged. `SettlementSocialBias` remains separate (V1.5-D).

#### P1-5 — 150-block expedition cap vs long return (`CLOSED`)

**Finding:** `MAX_EXPEDITION_DISTANCE = 150` (`ExploringGoal.java` L81) cannot reach a home 400+ blocks
away in one leg.

**Resolution (`LOCKED` — `D-VR-048`):** COMMUTE uses **multi-leg chaining**:

- Each leg: `routeBudget = min(distanceToAnchor, COMMUTE_LEG_MAX)` where `COMMUTE_LEG_MAX` defaults to
  **150** (reuse constant).
- On leg completion (waypoints exhausted) **or** `expeditionExpired`: if still outside
  `SettlementBoundsPolicy` and `SettlementReturnPolicy.shouldCommute` still true → seed **next** COMMUTE
  leg toward same anchor.
- VR-T1.5a **PASS** when mob **enters bounds** of home anchor after one or more legs, not after a
  single 150-block walk.

#### P1-6 — `cfg.exploring` dependency (`CLOSED`)

**Finding:** Return routed through `ExploringGoal`, which requires `ScavengerConfig.exploring`.

**Resolution (`LOCKED` — `D-VR-046`):** settlement return **requires** `cfg.exploring == true`. Disabling
generic exploration also disables autonomous return-to-village. No silent fallback in V1.5.

#### P1-7 — SOCIAL settlement anchor at episode start (`CLOSED`)

**Finding:** `SocialExecutionBindingRegistry` records intent/subject/tick but not settlement context.

**Resolution (`LOCKED` — `D-VR-050`):** at greet **admission** (same seam as
`FriendlyGreetAdmissionSeamMixin`), snapshot `Optional<BlockPos> settlementAnchorAtStart` =
nearest remembered village anchor within `SettlementBoundsPolicy`, or empty. Persist on `Binding`.
`onSocialEpisode` credits relationship **only** when `settlementAnchorAtStart.isPresent()` at
**admission** and terminal `COMPLETED` — not location-at-stop alone.

#### P1-8 — Option A rekey / sync (`CLOSED`)

**Finding:** D-VR-044 merge on `remember()` insufficient for anchor supersede orphans.

**Resolution (`LOCKED` — `D-VR-049`):** production call sites:

| Event | Action |
| --- | --- |
| `remember()` merges into existing village | fold relationship rows (D-VR-044) |
| `withObservation()` replaces anchor (new `KnownVillage` instance) | `rekeyRelationship(oldAnchor, newAnchor)` |
| LRU evicts non-home village | `relationships.remove(canonicalKey)` |
| `designateHome()` | no new relationship key; floor bump on existing row |

Structural contract test required before task-46 authorization.

#### P1-9 — V1.5-E coarse ally gate (`CLOSED` — User REJECT)

**Finding:** Global `RaidContainersGoal#canUse` cancel for HOME/HIGH may suppress legitimate scavenging.
Attachment answers “this settlement matters”; it does not classify container ownership.

**Resolution (`LOCKED` — `D-VR-052`):** **REJECT V1.5-E for this release.**

| Item | Decision |
| --- | --- |
| V1.5-E | **DEFER → V3** `StorageOwnership` |
| VR-T1.5d | **DEFER → VR-T3** — prove manners with container classification, not blanket cancel |
| V1.5 ships | A + B + C + D + temporary F only |

**V3 proof contract — SUPERSEDED predicate, evidence preserved.** The original
`HOME/HIGH + VILLAGE_PUBLIC` wording conflicts with locked `D-VR-012` and repeats the exact category
error this review rejected: attachment says a settlement matters; it does not grant ally authority.
The canonical V3 predicate is now `D-VR-017`:

```text
VillageScenarioProfile == VILLAGE_ALLY
AND StorageOwnership does NOT carry explicit mob loot permission
→ RaidContainersGoal admission and continuation are refused
```

`VILLAGE_PUBLIC`, `FOREIGN`, and `UNKNOWN` all lack explicit mob loot permission in V3 and therefore
fail closed for an ally. Only `MOB_OWNED` or `EXPLICITLY_SHARED_WITH_MOB` may permit the existing host
loot executor. HOME/HIGH remains relationship evidence only and can never substitute for the profile
or ownership fact. VR-T3g/h/i own this proof.

**Rejected alternatives:** coarse HOME/HIGH blanket (throws away container information); narrow
`VILLAGE_PUBLIC` tag in V1.5 (starts `StorageOwnership` incompletely, migration debt for V3).

#### P2 cleanup (`CLOSED`)

- `attachmentBand`: **derived only** at read time from `familiarityScore`; not NBT field.
- `helpfulEventCount`: **removed** from gen-1 schema (no consumer).

### V1.5 implementation contract (`AUTHORIZED` — task-46 / **1.11.0**)

**Scope (in):**

| Slice | Deliverable |
| --- | --- |
| **V1.5-A** | `SettlementRelationship` record + parallel map on `MobVillageMemory`; NBT codec; RET-1 evict with village LRU |
| **V1.5-B** | Gen-1 accumulation (`D-VR-036`): presence via `SettlementBoundsPolicy` @ 64, visit on `record`, social via `SettlementRelationshipService` |
| **V1.5-C** | `SettlementReturnPolicy` + forced-heading `ExploringGoal` commute (`D-VR-043`); blocked during mining/cave handoff |
| **V1.5-D** | Village-aware discretionary SOCIAL weighting near familiar anchor (GAO bridge; no trade confusion) |
| **V1.5-F** | **Temporary** `/spmscavenger designate-home` for VR-T1.5a only; **remove post-VR-T1.5**. **No** `village-memory` |

**Scope (out):** **V1.5-E** (`RaidContainersGoal` suppression — **DEFERRED V3**, `D-VR-052`);
`VillagerTradeAdapter`, raid interrupt, replant, full `StorageOwnership`, `SettlementOpinionBias`
(V4), SETTLEMENT Opinion subject.

**Storage — Option A (`LOCK RECOMMENDED`, D-VR-035):**

```text
MobVillageMemory
├─ List<KnownVillage> villages          // factual — unchanged
└─ Map<BlockPos, SettlementRelationship> relationships  // mob-owned; key = merged anchor
```

- Key: canonical anchor from `at(anchor)` merge (`VillageIdentityPolicy`), not a minted id.
- Eviction: when `remember()` LRU evicts a non-home village, drop the matching relationship row.
- `relationships` never written inside `KnownVillage` codec.

**`SettlementRelationship` gen-1 fields (persisted):**

```text
familiarityScore   // int, capped
lastVisitTick
socialEventCount
// attachmentBand() derived from familiarityScore — NOT stored in NBT
```

**Accumulation (`D-VR-036` gen-1 — provisional, VR-T1.5b tunes):**

| Signal | Rule | Cap |
| --- | --- | --- |
| Perception `record` while inside bounds | `+VISIT` on **bootstrap** or **re-entry** after `lastOutsideTick > lastVisitTick` | routine re-scan while resident |
| Observer heartbeat while mob inside **`SettlementBoundsPolicy` (64²)** of remembered anchor | `+5` familiarity / 200t via `lastPresenceTick`, **presence channel capped at 250** | same cap |
| Observer heartbeat while mob **outside** bounds | updates `lastOutsideTick` only | — |
| GAO SOCIAL / greet `COMPLETED` with `settlementAnchorAtStart` present (`D-VR-050`) | `+SOCIAL_BUMP` + socialEventCount | band thresholds |
| `designateHome()` | factual `HOME_VILLAGE` + `onHomeDesignated` familiarity floor | — |

Bands: LOW `< 200`, MEDIUM `200–599`, HIGH `≥ 600` (names only; thresholds are tuning constants).

**Return commute (`D-VR-038`):**

**Rejected:** orphan `ReturnToSettlementGoal` at priority **3** beside gather/smelt — loses forever to `RaidContainersGoal` / gather (`SpmScavenger.java` priority table).

**Accepted (`D-VR-047` / `D-VR-048`):**

```text
SettlementReturnPolicy.shouldCommute(…)
        ↓
ExploringGoal.trySeedCommute → ExpeditionKind.COMMUTE (non-discretionary)
        ↓
multi-leg forced heading toward home().anchor() else highest familiarity anchor
        ↓
repeat legs until SettlementBoundsPolicy satisfied or policy revokes
```

Admission: `HOME_VILLAGE` **or** familiarity HIGH; mob farther than `COMMUTE_MIN_DISTANCE` (e.g. 128);
mining/cave handoff idle; `cfg.exploring` true (`D-VR-046`); no mandatory combat/shelter hold.

**`designateHome` gap (`CONFIRMED`):** `VillageMemorySavedData.designateHome` and `MobVillageMemory.designateHome` are **test-only** today (grep `src/main` — no production caller). VR-T1.5a **cannot pass** without **V1.5-F** debug command or an auto-home policy (`D-VR-039` defers auto policy to product review; debug command is minimum).

**Ally storage / VR-T1.5d (`DEFERRED` — `D-VR-052`):** V1.5 does **not** mixin `RaidContainersGoal`.
`FriendlyGreetShelterHoldMixin` gates shelter only — loot suppression belongs in V3 when
`StorageOwnership` can classify containers (`VILLAGE_PUBLIC` vs explicitly permitted). VR-T3g/h/i
prove the canonical `D-VR-017` predicate; HOME/HIGH attachment alone grants no storage authority.

**Must happen (static):** relationship persists across save/reload; evict sync; return policy unit tests; commute does not call `VillagePerception.observe` from debug paths; Option A rekey contract test (`D-VR-049`).

**Must not happen:** attachment fields inside `KnownVillage` NBT; return goal at priority 3; trade adapter; perception refresh from debug read commands; `RaidContainersGoal` mixin in 1.11.0; reintroduction of `village-memory` / probe / driver.

**Verification:** `.\gradlew.bat test --tests "*village*"` + structural contract extensions; VR-T1.5a–c runtime after launch approval (overworld-only; Bob taiga fixture).

**Artifact:** `.superpowers/sdd/task-46-brief.md` (**written** 2026-08-14).

### Brainstorm continuation 5 — V1.5 binding gaps (`Agent_Cursor`, 2026-08-14)

Deduplicated against V1.5 implementation contract, D-VR-038/039, B-VR-61…65, and pinned source.
Advances the **nearest unresolved** V1.5 frontiers: presence geometry, write-path hooks, commute
arbitration, relationship merge, and auto-home product choice.

#### Settlement bounds for presence (`D-VR-040`)

Three radii, three jobs — do not collapse:

| Radius | Source | Job |
| --- | --- | --- |
| 48² | `VillageIdentityPolicy` | Merge remembered settlements |
| 64 | `VillagePerception.VILLAGE_QUERY_RADIUS` | **Presence / familiarity accumulation** |
| 96² | `RaidAssociationPolicy` | Raid association (V5) |

Add `SettlementBoundsPolicy.within(mobPos, anchor)` → `distSqr ≤ 64²`.

**Must not happen:** use identity radius for presence and stall attachment because the mob stands in
a valid POI field 50 blocks from anchor.

#### Single relationship write path (`D-VR-041`)

```text
SettlementRelationshipService
  ├─ onVillageRecorded(mob, KnownVillage, tick)
  ├─ onPresenceHeartbeat(mob, anchor, tick)      // SettlementBoundsPolicy @ 64
  ├─ onSocialEpisode(mob, anchorAtStart, tick)   // D-VR-050; only if present at admission
  └─ onHomeDesignated(mob, anchor, tick)           // familiarity floor; factual tier elsewhere
```

**`CODE_CONFIRMED` hooks:** `VillageMemorySavedData.record` L139–148;
`VillagePerceptionObserver.tick`; `SocialExecutionBindingRegistry` terminal `COMPLETED` L221–224.

#### Commute vs mining / cave handoff (`D-VR-043`)

`SettlementReturnPolicy.shouldCommute` must refuse when:

- `MiningExecutionCommitment` active
- Cave handoff claimed on `ExploringGoal` (`acceptCaveHandoff`, L514+)
- `readiness.hasDescentPressure()` true (descent route wins L586–588)

Commute uses **forced heading** toward anchor (`createExpedition` L570–572) — same as companion
expeditions. Terminate when mob enters `SettlementBoundsPolicy` around target.

#### Relationship merge on village merge (`D-VR-044`)

When `remember()` merges via `at(anchor)`, fold relationship rows: `max` familiarity,
`sum` event counts (capped), `max` lastVisit, re-derive band.

#### Village-aware SOCIAL (`D-VR-045`)

**Rejected:** global `FriendlyGreetGoal` mixin (VR-T1.5c trade confusion).

**Accepted:** `SettlementSocialBias` in Opinion package — bounded bump to discretionary `SOCIAL`
when within bounds of MEDIUM+ familiarity settlement. `onSocialEpisode` only when greet
`COMPLETED` **and** mob was in bounds at episode start.

#### Auto-home (`D-VR-042` — `PRODUCT DECISION`)

Ship **V1.5-F** debug `designate-home` for VR-T1.5a. Auto-familiar (HIGH + sleeps, config **off**
by default) is **V1.5-G stretch**, not MVP.

#### Camp near home (`B-VR-66` — stretch)

`SeekShelterGoal` bias toward shelter within bounds of home when attachment HIGH — after commute
slice lands.

#### No full director in V1.5 (`B-VR-67`)

`SettlementReturnPolicy` + `SettlementRelationshipService` + `ExploringGoal` + optional
`SettlementSocialBias` suffice. Full `VillageInteractionDirector` ranking waits for V4.

### MAIBS — V1.5 (implementation-ready)

| Minute | Predicted observable | Failure mode |
| --- | --- | --- |
| 0–3 | Paths through village; greets villagers | Ignores villagers (unchanged V1 gap if GAO not wired) |
| 3–8 | `KnownVillage` + rising familiarity on repeat presence | Attachment stuck at zero |
| 8–15 | Leaves for mining; later **chooses** return heading to home/familiar anchor | Picks random explore forever |
| 15–20 | Camps near village (shelter/torch), not inside villager beds | Steals bed → anger |

**Implementation binding:** return must route through `ExploringGoal` expedition seed (`D-VR-038`), not a new priority-3 executor.

**Weirdness watch:** without `designateHome` production caller, VR-T1.5a needs debug home designation (`D-VR-039`) until auto-home policy exists (`D-VR-042`). Commute must yield to active mining/cave handoff (`D-VR-043`).

---

## Topic: Pre-lock review — D-VR-025/026 (`User` + `Agent_Cursor`)

**Author:** User (review criteria); `Agent_Cursor` (code evidence, 2026-08-14)
**Status:** `D-VR-025` **LOCKED**; `D-VR-026` **HELD** (User amendment, 2026-08-14)

### Finding 1 — Remembered villages while unloaded/unperceived (`CONFIRMED` gap in draft)

**User requirement:** remembered villages must remain **selectable** when chunks are not loaded and
`VillagePerception` cannot run.

**Evidence (`CODE_CONFIRMED`):**

- `MobVillageMemory` persists across unload (`MobVillageMemory.java` javadoc — V1-R1; unload no longer
  deletes).
- `VillagePerception.observe` admits POIs only when `level.hasChunk(...)` (`VillagePerception.java`
  L110–112) — **no perception without loaded chunks**.
- Draft ranking loop incorrectly gated on `VillagePerception.isValid(candidate)` — that would drop every
  remembered village the mob is not currently standing in.

**Amendment (D-VR-025):** three-phase model:

| Phase | Source | Unloaded OK? |
| --- | --- | --- |
| **Candidate pool** | `MobVillageMemory.villages()` | **Yes** — travel/defend intent |
| **Refresh** | `VillagePerception.observe` when present | No — updates anchor/quality only |
| **Execution** | trade/villager/path gates on arrival | Partial — needs loaded destination |

Raid interrupt (`D-VR-010`) uses **remembered** `home.anchor()` with `getRaidAt` — does not require
current perception (`INFERRED` — raid state is world-scoped).

**Must-not-happen test (VR-T4b):** mob with two remembered villages, both chunks unloaded → director
still returns a ranked commute target using remembered anchors + cached trade facts.

### Finding 2 — Village must consume bounded Opinion bias, not own personality/affect math (`DISAGREE` with draft formula)

**User requirement:** Village code calls a bounded bias; **Opinion package** owns sociability,
stress, curiosity, Place memory composition.

**Evidence (`CODE_CONFIRMED`):** `PlaceOpinionRouteRanker` javadoc — *"Place memory ranks among
already-valid candidate destinations. It must never veto mandatory mining..."* (`PlaceOpinionRouteRanker.java`
L8–9). `DiscretionaryScoringInput` already bundles `AffectiveState`, `OpinionMemory`, personality
fields for discretionary scoring (`DiscretionaryScoringInput.java`).

**Amendment (D-VR-025):** add **`SettlementOpinionBias`** (Opinion package, name TBD):

```java
/** Bounded soft bias for a remembered settlement. Village director adds; never vetoes. */
public static int request(
        KnownVillage village,
        DiscretionaryScoringInput opinionInput,
        SettlementOpinionContext ctx) {
    // internally: PlaceOpinionRouteRanker + personality/affect/trader prefs
    // clamp to ±PlaceOpinionRouteRanker.MAX_ROUTE_BIAS
}
```

`VillageInteractionDirector` **must not** import `PersonalityModel` / `AffectiveState` math directly
(SPM-2 boundary + single owner for discretionary composition).

**Rejected:** `OpinionVillageBias` formula inlined in village package (superseded draft).

### Finding 3 — Place key vs anchor drift (`CONFIRMED` tradeoff; frozen key **REJECTED**)

**User question:** Is `KnownVillage.anchor()` safe as the Place opinion key when anchor recomputation
crosses chunks?

**Technical answer (`CODE_CONFIRMED`):** using **current** anchor chunk means Place preference is
**geographic** — when `withObservation` moves the anchor across chunks, prior Place learning on the old
chunk does not automatically apply to the new geography (`PlaceOpinionMemory` keys by `ChunkPos` only;
`KnownVillage.java` replacement semantics; V1-R2 400-block drift).

**User amendment (D-VR-026 held):** that behaviour is **correct for Place**, not a bug to paper over.
Frozen `placeOpinionChunkKey` was **rejected** because it encodes settlement identity in Place storage.
Keep learning/ranking tied to **actual/current** anchor geography. Settlement-persistent liking across
anchor moves is **SETTLEMENT-subject** evidence, not a chunk-key workaround.

**V4 wiring:**

- **Learning:** village terminal / outcome wrapper records at `ChunkPos(current anchor)`.
- **Ranking:** `SettlementOpinionBias` → `PlaceOpinionRouteRanker.destinationBias(places, anchor.x, anchor.z)`.
- **Navigation:** path target remains current `anchor()` (raid-centre agreement, D-VR-019).
- **Identity / tiers / home:** factual `MobVillageMemory` — unchanged.

**Rejected:** immutable `placeOpinionChunkKey`; Place-entry migration on every anchor supersede (defer unless
VR-T4 shows geographic split is player-visible and SETTLEMENT is still deferred).

### Lock outcome

| Decision | Prior | After review |
| --- | --- | --- |
| **D-VR-025** | `LOCK RECOMMENDED` | **`LOCKED`** — remembered pool + `SettlementOpinionBias` consumer |
| **D-VR-026** | `LOCK RECOMMENDED` (frozen key) | **`HELD`** — Place@current anchor; SETTLEMENT if settlement-persistent pref needed |

**Blockers before V4 implementation:** `SettlementOpinionBias` ships in Opinion package before director
wiring (`D-VR-025`). Place key policy (`D-VR-026`) remains open until SETTLEMENT evidence or re-lock.

---

## Topic: Cross-system reuse — shelter, mining interrupt, activity admission (`Agent_Cursor`)

**Author:** `Agent_Cursor` (brainstorm continuation, 2026-08-14)
**Status:** `CONSENSUS` direction — reuse shipped Scavenger primitives; **no** parallel raid mega-state.

### Shipped primitives to reuse (`CODE_CONFIRMED`)

| Primitive | Location | Village/raid use |
| --- | --- | --- |
| `TaskLifecycle` + interrupt snapshot | `progression/TaskLifecycle.java`, `MiningProject` | `RaidTask.previousTask` resume after victory |
| `MiningProjectEnd.COMBAT` | `MiningProjectEnd.java` | Precedent for raid → `INTERRUPTED` → resume iron demand |
| `ShelterCommitment` / SCR-2R5 | `SeekShelterGoal`, shelter RFC in Vanilla | `EVACUATE` profile hide during wave; not new `HideInHouseGoal` |
| `ShelterActivityEnvelope` | `FriendlyGreetShelterHoldMixin` | Already suppresses `RaidContainersGoal` during shelter hold |
| `WorkDemandPolicy` | `WorkDemandPolicy.java` | Trade evaluation input (`D-VR-007`); diamond/iron chain cross-RFC |
| `ActivityAdmission` pattern | Opinion RFC / `ActivityAdmissions` | Raid defense as **high-priority voluntary activity** with yield rules |
| `PlaceOpinionRouteRanker` | `PlaceOpinionRouteRanker.java` | Soft bias at **current** village anchor chunk (D-VR-026 **HELD**) |
| `PlaceOpinionService` | `PlaceOpinionService.java` | Terminal learning from village outcomes at **current** anchor chunk |
| `PersonalityModel` + `AffectiveState` | Opinion package | Composed inside `SettlementOpinionBias`, not village director (D-VR-025) |

### Raid interrupt admission (`PROPOSED` — D-VR-010)

Borrow MI-14 **lease + resume** semantics without copying mining geometry:

```text
Acquire iron (TaskLifecycle.RUNNING)
  → getRaidAt(homeVillageAnchor) != null
  → snapshot previousTask + demand ticket
  → RaidTask.state = ACTIVE_WAVE
  → DEFEND / SUPPORT / EVACUATE via utility (existing raid topic)
  → raid ends SUCCESS
  → revalidate MaterialDemand
  → resume previousTask if still valid
```

**Must not happen:** raid ends → mob immediately loots village chest (`RaidContainersGoal`) before
re-checking `StorageOwnership` + ally profile.

### `RaidContainersGoal` ally gate (`CONFIRMED` need — D-VR-012)

`PlayerMobEntity#registerGoals` adds `RaidContainersGoal` at priority **3** (`CONFIRMED` —
`PlayerMobEntity.java` L835). Scavenger already mixin-blocks it during shelter hold
(`FriendlyGreetShelterHoldMixin` L20–22). **Gen-1 ally play** still needs a **predicate** on the goal
or a scavenger-side admission. Canonical V3 rule: `VILLAGE_ALLY` + anything other than explicit
`MOB_OWNED` / `EXPLICITLY_SHARED_WITH_MOB` permission → refuse in both admission and continuation.
`UNKNOWN` is denial, never permission. Full storage RFC remains deferred; the minimum classifier,
profile source, and continuous gate are V3-A/B.

### Raider hostility side effect (`CONFIRMED`)

`RaiderTargetsPlayerMobMixin` adds `NearestAttackableTargetGoal` for `PlayerMobEntity` at priority **2**
on all `Raider` subclasses — **including Witch** (`RaiderTargetsPlayerMobMixin.java` L37–39). Village
defense planning must assume witches hunt PlayerMobs on sight during raids, not only pillagers.

### Shelter ↔ raid combat coupling (`CONFIRMED` — B-VR-21)

`ShelterThreatPolicy` (`ShelterThreatPolicy.java`) classifies `mob.getTarget() instanceof Enemy` with
nearby active threat as `NEARBY_HOSTILE`, which **overrides** shelter hold (`overridesShelter` → true).
Vanilla `Raider` subclasses implement `Enemy` (`INFERRED` — standard Monster hierarchy). **Effect:**
coward `EVACUATE` profile hiding in SCR-2R5 interior is **automatically ejected** when a raider
acquires the mob as target (`RaiderTargetsPlayerMobMixin` makes this likely during waves).

**Must happen:** coward profile reaches interior before first raider aggro.
**Must not happen:** mob oscillates door ↔ bed every tick because threat classification flaps without
hysteresis — consider raid-wave grace window (`PROPOSED`).

**Design note:** Do **not** fork a parallel threat policy for raids gen-1; extend `ShelterThreatPolicy`
only if raider-specific hysteresis is required (D-VR-016).

### Iron golem neutrality (`CONFIRMED` design rationale)

PlayerMob avoids `Enemy` marker so golems do not auto-attack (`RaiderTargetsPlayerMobMixin` L24–26).
Golem anger still applies if mob hits villager — **friendly fire during raid AOE is a reputation risk**
(brainstorm catalogue L910); target filter must prefer `Raider` over `Villager` (`VR-7`).

---

## Topic: Day/night director arbitration (`Agent_Cursor`)

**Author:** `Agent_Cursor` (user-requested topic, 2026-08-14)
**Status:** `PROPOSED` — extends `VillageInteractionDirector` + cross-system shelter/raid topics; **no** new night mega-goal.

### Problem (observable)

Village play is **time-gated** in ways the director must reconcile:

| Clock | Vanilla / SPM behaviour | Village RFC demand |
| --- | --- | --- |
| **Dusk → dawn** | `SeekShelterGoal` runs from tick **11500** (`DUSK`), not `isNight()` (`SeekShelterGoal.java` L249–271) | Mob paths home / village interior before dark |
| **Villager work hours** | Trades + workstation restock require villager awake and reachable | `TradeWithVillagerGoal` may fail at night |
| **Active `Raid`** | `level.getRaidAt(anchor)` independent of sun position | DEFEND / bell / EVACUATE can start at dusk |
| **Coward EVACUATE** | SCR-2R5 shelter hold | Conflicts with DEFEND unless profile + arbitration say otherwise |
| **Mandatory combat** | `ShelterInterruptionPolicy` → `OVERRIDE_AND_CANCEL` for `MANDATORY_COMBAT` | Raider aggro already ejects shelter (B-VR-21) |

Without a single arbitration contract, predictable failures include: mob beds through a home-village raid;
abandons shelter at dusk to path to a distant trader; or rings bell at dawn while villagers still sleep.

### Architectural rule (hard)

**Do not** add a parallel `NightDirector` or duplicate `SeekShelterGoal` scheduling inside village code.

`VillageInteractionDirector` publishes **time-aware admission** and **activity class** for village/raid
executors; existing shelter and SPM goals remain executors.

```text
World clock + dimension rules
        ↓
VillageDayNightContext (read-only)
        ↓
VillageInteractionDirector.arbitrate(...)
        ↓
ActivityAdmission per intent (trade / raid / shelter commute / farm)
        ↓
ShelterInterruptionPolicy + TaskLifecycle (existing)
        ↓
SPM GoalSelector executors
```

### `VillageDayNightContext` (`PROPOSED` — pure, impure-probe-free)

Bounded read-only facts — **no** goal mutation during observation (Compatibility Contract #12):

| Field | Source |
| --- | --- |
| `shelterWindowActive` | `SeekShelterGoal.shelterTime(level)` semantics — dusk 11500 → dawn 23000 |
| `villagerWorkWindow` | `!level.isDay()` defer trade; optional stricter “villagers sleeping” heuristic |
| `raidActiveAtHome` | `getRaidAt(homeVillageAnchor) != null` |
| `raidActiveNearby` | `getRaidAt(mob.blockPosition())` |
| `shelterHoldActive` | `ShelterNightAuthority` / `ShelterActivityEnvelope` |
| `profile` | `VillageScenarioProfile` (ALLY / COWARD / …) |

Fixed-time dimensions (Nether/End): shelter window **false** — director must not schedule village night
commute there (`CONFIRMED` — `SeekShelterGoal` L257–258).

### Priority matrix (`PROPOSED` — gen-1)

Higher row wins when intents conflict. Reuse `ShelterInterruptionPolicy` for shelter hold vs displacement;
this matrix is **director-level** intent selection before goals start.

| Priority | Condition | Winning intent | Notes |
| --- | --- | --- | --- |
| 1 | `MANDATORY_COMBAT` / player attack order | Combat (SPM P2) | Already overrides shelter |
| 2 | `raidActiveAtHome` + `HOME_VILLAGE` + `DEFEND` utility | Raid DEFEND / SUPPORT | D-VR-010 interrupt snapshot |
| 3 | `raidActiveAtHome` + `COWARD` profile | EVACUATE (SCR-2R5) | Not DEFEND; may still lose hold to aggro (B-VR-21) |
| 4 | `shelterWindowActive` + no home raid + no mandatory combat | Night shelter / commute to village anchor | Defer discretionary trade |
| 5 | `villagerWorkWindow` + trade demand urgent | Trade run (day) | Defer “browse” trades |
| 6 | `shelterWindowActive` + `raidActiveNearby` (not home) | Profile-dependent: TRADER flees; ALLY may assist | B-VR-14 product boundary |
| 7 | Default discretionary | Farm / social / explore per utility | |

**Must happen:** raid at **home** during dusk → mob does **not** enter new bed sleep until raid resolved or
EVACUATE profile explicitly chosen (raid intent preempts shelter **adoption**).
**Must not happen:** mob sleeps through wave 1 at home village bell while raiders kill villagers.

**Must happen:** trade for emeralds deferred until day when villager asleep and no raid interrupt.
**Must not happen:** infinite “wait for morning” if raid lasts multiple nights — raid hold persists (D-VR-010).

### Integration with existing systems (`CODE_CONFIRMED` seams)

| System | Arbitration hook |
| --- | --- |
| `SeekShelterGoal` | Shelter window already uses dusk, not `isNight()` — align `VillageDayNightContext` with same constants or shared helper |
| `ShelterInterruptionPolicy` | `MANDATORY_COMBAT` → `OVERRIDE_AND_CANCEL` — raid defense should classify as mandatory combat when `RaidTask` active at home |
| `TaskLifecycle` / `RaidTask` | Night does not clear `INTERRUPTED` mining/trade tickets (D-VR-010) |
| `RingVillageBellGoal` | Allowed during raid regardless of shelter window; **blocked** during voluntary bed sleep |
| Restock deferral (trade topic) | “night → defer” becomes director admission `BLOCKED` with reason `VILLAGER_SLEEPING` |

### Coward vs defender at dusk (`MAIBS pre-mortem`)

| Minute | DEFEND profile | COWARD profile |
| --- | --- | --- |
| 0–1 | Dusk; raid horn; paths bell or edge | Paths SCR-2R5 interior |
| 1–3 | Fights raiders; shelter hold cancelled via combat | In bed / interior |
| 3–5 | If low health: eat, not sleep | Raider aggro → ejected from shelter (B-VR-21) |
| 5+ | Raid ends → may sleep if still shelter window | Re-hide or flee village |

**Strongest objection:** classifying raid DEFEND as `MANDATORY_COMBAT` may over-preempt **Opinion**
discretionary REST at night — village director must own **village-scoped** raid admission, not global
combat reclassification.

**Viable alternative:** raid-at-home sets `VillageRaidAuthority` hold (mirror `ShelterNightAuthority`)
that blocks new shelter adoption but does not cancel in-progress eating/healing.

### Phased delivery

| Phase | Scope |
| --- | --- |
| **V1** | `VillageDayNightContext` read model + inspector field; no new goals |
| **V2** | Trade admission respects `villagerWorkWindow` |
| **V5** | Full matrix rows 1–4 + raid-vs-shelter preempt; VR-T5 dusk-raid scenario |

### Open product decisions

- **Nearby raid, not home** (matrix row 6): does `VILLAGE_ALLY` commute to defend a `TRADING_POST` tier village at night?
- **Multi-night raid:** sleep deprivation is human-realistic — allow bed only `BETWEEN_WAVES` if raid state permits?

---

## Topic: Vanilla API mapping verification (`Agent_Cursor`)

**Author:** `Agent_Cursor` (brainstorm continuation 2, 2026-08-14)
**Status:** `CONFIRMED` — pinned 1.21.1 Mojmap via project Loom cache
**Evidence:** `.gradle/loom-cache/source_mappings/1425f5a1b73d8da53d978a43e065a7bbd26518ca.tiny`

| RFC / colloquial name | Mojmap symbol | Signature | Implementation note |
| --- | --- | --- | --- |
| `BellBlock.ring` | **`BellBlock.attemptToRing`** | `(Entity, Level, BlockPos, Direction) → boolean` | Entity initiator **not** `Player`-typed; use block interaction executor |
| `Raid.addHero` | **`Raid.addHeroOfTheVillage`** | `(Entity) → void` | Heroes stored in `heroesOfTheVillage` set — **may** accept `PlayerMobEntity`; reward path still `UNVERIFIED` |
| Trade UI | **`MerchantMenu`** | player-gated inventory | `VillagerTradeAdapter` must bypass menu (`D-VR-005`) |
| Bad Omen | **`BadOmenMobEffect`** + `Raid.absorbRaidOmen(ServerPlayer)` | player-centric | PlayerMob bridge required (`D-VR-002`) |

**NOT FOUND** (3 probes each, Scavenger `src` + SPM `src`): `BadOmen`, `HeroOfTheVillage`, `MerchantMenu`.

**Unblock:** D-VR-008 can move from CONSENSUS → **LOCK RECOMMENDED** with `attemptToRing` naming;
D-VR-002 evidence upgraded from `INFERRED` to **mapping CONFIRMED** for hero *registration* only
(effect/discount path still runtime `UNVERIFIED`).

---

## Topic: Vanilla player-gate audit — where the `Player` type actually blocks us (`Agent_Claude`)

**Author:** `Agent_Claude` (brainstorm continuation 3, 2026-08-14)
**Status:** `CODE_CONFIRMED` — bytecode read from the pinned 1.21.1 Mojmap-named merged jar
**Evidence:** `.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-1425f5a1b7/1.21.1-loom.mappings.1_21_1.layered+hash.2198-v2/…jar`, via `javap -p -c`

### Why this audit exists

The RFC repeatedly reasons from **API names and signatures** — "`absorbRaidOmen(ServerPlayer)` is
player-centric → REQUIRES MIXIN". A signature is where a gate is *declared*; it is not necessarily
where the gate *is*, nor how wide it is. Three of the four village subsystems turn out to be gated in
a **different place and at a different width** than the RFC assumed, and the difference is worth
several phases of work.

Method bodies were read, not signatures. Each claim cites its opcode offset.

| Subsystem | RFC assumption | What the bytecode says | Verdict |
| --- | --- | --- | --- |
| Hero of the Village | `REQUIRES MIXIN` — "award effect on raid win" (VR-11) | **One `EntityType` comparison** in `Raider#die`; the award loop accepts any `LivingEntity` | **Much cheaper** |
| Villager reputation | `REQUIRES API` — "map PlayerMob UUID" (VR-4) | Gossip write path is **already entity-agnostic** and running today | **Already native** |
| Village detection | Hand-rolled cluster heuristic (D-VR-009) | Vanilla defines it as a **POI tag query**, and the raid system uses that definition | **Mirror it** |
| Raid initiation | `REQUIRES MIXIN` (VR-9/VR-10) | `ServerPlayer` all the way down, including entity-level omen state | **Confirmed hard** |

---

### F1 — Hero of the Village: a single type comparison, not a reward reimplementation

`Raider#die(DamageSource)` (offsets 39–58):

```java
Entity killer = source.getEntity();
Raid raid = this.getCurrentRaid();
if (raid != null) {
    if (this.isPatrolLeader()) raid.removeLeader(this.getWave());
    if (killer != null && killer.getType() == EntityType.PLAYER) {   // <-- the ONLY gate
        raid.addHeroOfTheVillage(killer);
    }
}
```

`Raid#addHeroOfTheVillage(Entity)` (offsets 0–14) is a bare `heroesOfTheVillage.add(entity.getUUID())`.
The victory award loop in `Raid#tick` (offsets 617–742) then does:

```java
for (UUID uuid : heroesOfTheVillage) {
    Entity e = level.getEntity(uuid);                            // getEntity, NOT getPlayerByUUID (656)
    if (e instanceof LivingEntity hero && !e.isSpectator()) {    // LivingEntity, NOT Player       (663)
        hero.addEffect(new MobEffectInstance(
                MobEffects.HERO_OF_THE_VILLAGE, 48000, raidOmenLevel - 1, …));                  // (684–710)
        if (hero instanceof ServerPlayer p) {                    // player-only AFTER the award   (713)
            p.awardStat(Stats.RAID_WIN);
            CriteriaTriggers.RAID_WIN.trigger(p);
        }
    }
}
```

**Consequence.** Everything downstream of that type check — UUID storage, `setDirty()` persistence,
the 48000-tick duration, the amplifier derived from `raidOmenLevel`, spectator exclusion, and the
effect application itself — **already works for a `PlayerMobEntity`**. Only `awardStat` and the
advancement are player-only, and both are meaningless for a mob.

So VR-11 is not "reimplement the hero reward". It is: widen one comparison in `Raider#die` so a
`PlayerMobEntity` killer also reaches `addHeroOfTheVillage`. A `@Redirect` on that single
`getType()` / `if_acmpne` pair — or an `@Inject` at `TAIL` that calls `addHeroOfTheVillage` when our
own predicate matches — is the whole feature.

**Why this is an unusually good mixin target:** it is a public override on a vanilla class the addon
*already* mixes into for raider hostility; the gate is a **single comparison against a registry
constant** rather than a control-flow shape; and failure degrades to *vanilla behaviour* — no hero
credit — rather than to a broken raid.

**Objection (mine, unresolved).** `HERO_OF_THE_VILLAGE`'s actual *benefit* is the trade discount,
applied by `Villager#updateSpecialPrices(Player)` — **player-typed**. The mob therefore receives a
genuine, persistent, visible effect instance that vanilla will never convert into cheaper trades.
That is acceptable here **only** because D-VR-005 already owns the trade path: `VillagerTradeAdapter`
can read the mob's own `HERO_OF_THE_VILLAGE` amplifier and apply the discount arithmetic itself. Had
we chosen a fake-GUI trade design, this effect would have been purely decorative. Recording that as a
non-obvious dependency: **F1's gameplay value is contingent on D-VR-005 holding.**

---

### F2 — Villager reputation is already entity-agnostic, and already running

`Villager#onReputationEventFrom(ReputationEventType, Entity)` (offsets 0–116) — every branch keys on
`entity.getUUID()` with **no** `Player` check:

| Event | Gossip written |
| --- | --- |
| `ZOMBIE_VILLAGER_CURED` | `MAJOR_POSITIVE +20`, `MINOR_POSITIVE +25` |
| `TRADE` | `TRADING +2` |
| `VILLAGER_HURT` | `MINOR_NEGATIVE +25` |
| `VILLAGER_KILLED` | `MAJOR_NEGATIVE +25` |

Dispatch is equally ungated. `Villager#setLastHurtByMob(LivingEntity)` (offsets 21–50):

```java
level.onReputationEvent(ReputationEventType.VILLAGER_HURT, attacker, this);  // unconditional   (26)
if (this.isAlive() && attacker instanceof Player) {
    level.broadcastEntityEvent(this, (byte) 13);      // angry particles — COSMETIC ONLY     (36–50)
}
```

The **only** `instanceof Player` in the entire `Villager` class is that particle broadcast.
`Villager#die` → `tellWitnessesThatIWasMurdered(Entity)` is likewise ungated, spreading
`VILLAGER_KILLED` to every nearby `ReputationEventHandler` witness.

**Consequence — this is present-tense, not future-tense.** A PlayerMob that clips a villager with an
AOE **today, with zero addon code**, already writes `MINOR_NEGATIVE 25` into that villager's gossip
under the mob's own UUID, and already tells the witnesses. B-VR-13 "witness resentment on villager
AOE" is therefore **not a feature to build** — it is an **existing consequence to measure and
expose**.

This also reclassifies VR-4. The *write* path needs nothing at all. The *read* path,
`Villager#getPlayerReputation(Player)`, is a `Player`-typed convenience wrapper over the UUID-keyed
`GossipContainer` — so what V4/V6 consumer work needs is a **`gossips` field accessor**, not a
"reputation bridge". Village Work V3 does not consume reputation.
Accessor mixins are the cheapest and most update-stable class of mixin available.

**Open question (evidence needed before V4 reputation-aware selection or V6 discounts, not V3).**
Does anything *consume* that reputation for a
non-player? Golem aggression and trade discount are the two consumers, and both are expected to be
player-typed. If both are, the mob accrues a real reputation the world never acts on — accurate
bookkeeping with no gameplay consequence until our own systems read it. That is still worth having
(it is the honest input for `KnownVillage` affinity, B-VR-25), but it must not be *described* as
"villagers remember you" until a consumer exists.

---

### F3 — Vanilla already defines "village", and the raid system uses that definition

`Raids#createOrExtendRaid` (offsets 44–65) determines the raid's village and centre as:

```java
PoiManager.getInRange(
        holder -> holder.is(PoiTypeTags.VILLAGE),     // a TAG                            (offset 51)
        pos, 64, PoiManager.Occupancy.IS_OCCUPIED)    // claimed ⇒ actually inhabited     (57–62)
    .toList()                                         // → centroid → raid centre
```

`data/minecraft/tags/point_of_interest_type/village.json`, read from the same jar:

```json
{ "values": ["#minecraft:acquirable_job_site", "minecraft:home", "minecraft:meeting"] }
```

Every workstation, plus beds, plus the bell — exactly the RFC's intended signal set, already expressed
as a **datapack-extensible tag** rather than a block list.

**Under Gate SPM-0's ladder**, the RFC's proposed heuristic ("≥2 villagers + ≥3 beds OR bell +
workstation within radius R, merge clusters by anchor distance") sits near the bottom: it enumerates
what a tag already expresses. `PoiTypeTags.VILLAGE` + `IS_OCCUPIED` is **level 3–4** — it mirrors the
consuming system's own predicate, and any datapack or mod registering a village-ish POI becomes a
village to our mob for free.

**The correctness argument is stronger than the elegance one.** D-VR-010's entire interrupt trigger is
`getRaidAt(homeVillageAnchor) != null`, and `Raids#getNearbyRaid` compares
`raid.getCenter().distSqr(pos)` against a radius. If `KnownVillage.anchor` is a hand-rolled bed
centroid while the raid centre is a POI centroid, the two disagree by an unbounded amount in any
village with an off-centre bed cluster — and **D-VR-010 silently never fires**. Not a crash, not a log
line: the mob keeps mining while its home village burns, which is precisely the failure D-VR-010
exists to prevent. Deriving our anchor from the same query makes the two agree **by construction**.

Cost drops too: `PoiManager` is a sectioned spatial index exposing `getCountInRange` and
`findClosestWithType`, so V1 perception becomes an indexed lookup rather than a block scan.

**Objection I must raise against my own finding — fake-intelligence risk.** `PoiManager` extends
`SectionStorage` and will happily read **persisted POI sections for chunks that are not loaded**.
Queried naively, the mob gains knowledge of villages it has never been near — exactly the omniscience
the Brainstorm skill forbids, and *worse* than the cluster heuristic it replaces, because the cluster
heuristic could only ever see loaded blocks. The query **must** be bounded to loaded / simulation-
distance chunks, and discovery must still require the mob to have been present. `PoiManager` supplies
the **predicate and the anchor**; it must not supply the **discovery**. This is a must-not-happen test,
not a design note.

---

### F4 — Raid initiation: confirmed hard, and hard at the entity level

The RFC is right about V6 initiation, and the reason is deeper than one signature:

| Symbol | Type | Access |
| --- | --- | --- |
| `Raids#createOrExtendRaid(ServerPlayer, BlockPos)` | `ServerPlayer` | `public` |
| `Raids#getOrCreateRaid(ServerLevel, BlockPos)` | player-free | **`private`** |
| `Raid#absorbRaidOmen(ServerPlayer)` | `ServerPlayer` | `public` |
| `ServerPlayer#setRaidOmenPosition` / `getRaidOmenPosition` / `clearRaidOmenPosition` | — | declared on **`ServerPlayer`**, not `Entity` |

1.21's omen rework is **two-stage** — `BadOmenMobEffect` → (on entering a village) `RaidOmenMobEffect`
→ raid — and the intermediate state lives in fields **on `ServerPlayer` itself**. A PlayerMob has
nowhere to hold it. V6 initiation therefore needs an accessor onto the private `getOrCreateRaid` plus
our own omen state, or a broader `ServerPlayer`-shaped shim. Both are `MIXIN_FRAGILE`; neither is
V1–V5 work.

**This is the one place the RFC understated the difficulty** — and it is worth stating plainly that the
two ends of raid parity have **opposite** difficulty from what the phase ordering assumes: *winning*
credit is nearly free, *starting* a raid is the hardest thing in this RFC.

---

### MAIBS-1 — behavioural prediction for the F3 anchor change

Ordinary village, bell at the south edge, bed cluster to the north; mob's home anchor set on arrival.

| Minute | Hand-rolled bed centroid (D-VR-009 as written) | POI-tag anchor (F3) |
| --- | --- | --- |
| 0–2 | Anchor lands in the bed cluster, ~30 blocks north of the bell | Anchor lands on the POI centroid, within metres of the raid centre |
| 3 | Raid spawns; `Raid.getCenter()` is the POI centroid | Same centre |
| 3–4 | `getRaidAt(homeAnchor)` — **may return null**; mob keeps mining, no log, no error | Returns the raid; D-VR-010 snapshot fires |
| 4–8 | Villagers die; mob arrives only if a raider happens to path to it and aggro fires | Mob paths to bell / engages |
| 8+ | Post-mortem indistinguishable from "raid interrupt not implemented" | Interrupt/resume observable |

**Must happen:** `KnownVillage.anchor` and `Raid.getCenter()` for the same settlement agree within the
`getNearbyRaid` radius.
**Must not happen:** the mob knows about a village whose chunks it has never had loaded.

---

## Topic: V1-R1 — memory lifecycle, settlement identity, anchor evidence (`Agent_Claude` + User)

**Status:** `IMPLEMENTED` (static) — 852 tests, 3 negative controls
**Origin:** User review of V1, 2026-08-14. One blocker, two redesigns. All three confirmed in code
before repair.

---

### P0 — generic unload deleted persistent memory (**BLOCKER**)

`SpmScavenger` called `VillageMemorySavedData.get(world).forget(mob.getUUID())` from
`ServerEntityEvents.ENTITY_UNLOAD`, and `forget` removes the mob's entry from persisted `SavedData`.
Fabric defines `ENTITY_UNLOAD` as **any** entity leaving a server world — a chunk unloading, a player
walking away. Not death.

So the lifecycle contract was backwards: a PlayerMob could remember villages through NBT and have
the record erased by wandering out of range, before the memory ever had a chance to matter.

**Root cause worth recording.** The call was written by copying the shape of its neighbours —
`SocialAdmissionSeam.release`, `OpinionExperienceRegistry.parkOnUnload` — without checking their
semantics. Those release **runtime** state, which genuinely should die on unload. This is persisted
`SavedData`. The rule now written into the class:

> **Generic unload parks or releases runtime state. Only permanent removal deletes semantic memory.**

**The test was part of the defect.** `VillagePerceptionContractTest` asserted *exactly two* `forget`
call sites — unload and death — so the structural test **enforced** the bug it was meant to guard.
A structural test locks in a wrong invariant exactly as firmly as a right one. The assertion now
encodes the semantics (*only permanent removal deletes*), not the shape that happened to be in the
file when it was written. This generalises beyond this repo and is a candidate for Reflection.

**RET-1 still has to hold without that call site.** Death alone cannot reach a mob removed without a
death event (discarded, killed while unloaded, removed by another mod). Two load-time bounds close
it, both with production callers:

| Bound | Value | Rationale |
| --- | --- | --- |
| `MEMORY_TTL_TICKS` | 30 in-game days | long enough to outlive the absence unload causes — the whole point of the repair — short enough that a vanished mob does not persist forever |
| `MAX_TRACKED_MOBS` | 256 per dimension | hard ceiling, stalest evicted first |

Pruned at `SERVER_STARTED`, not per tick: a stale entry belongs to a mob that is not ticking, so a
per-tick sweep would be looking for something that cannot appear between loads. **Residual, stated
honestly:** such an entry survives until the next world load, bounded by the cap.

---

### P1a — raid association had been promoted into village identity

`sameSettlement()` treated anchors within 96 blocks as one `KnownVillage`, justified by vanilla's
raid lookup radius. The User's counter-example is decisive and was unrepresentable:

```text
Village A  — HOME_VILLAGE, where the mob sleeps and stores
Village B  — 85 blocks away, TRADING_POST, has the good librarian
```

One entry cannot hold two tiers. Every later feature keyed on identity — affinity, storage ownership,
trade routing, community projects — would have inherited the collapse.

**Vanilla considering two centres one raid neighbourhood is a statement about raids.** It is not a
statement about what the mob should treat as one place. Split:

| Policy | Question | Radius | Owner |
| --- | --- | --- | --- |
| `VillageIdentityPolicy` | "is this the same village I remember?" | **48** (ours, `UNVERIFIED`) | cognitive model |
| `RaidAssociationPolicy` | "is vanilla's raid here my village's raid?" | **96** (`9216`, must not drift) | vanilla compatibility |

`RaidAssociationPolicy.associatedVillages` returns **every** match rather than the nearest, so one
raid covering a HOME_VILLAGE and a nearby TRADING_POST is now representable. The natural consumer
rule — highest tier wins — is a decision the collapsed model could not even pose.

**The 48 is a judgement, labelled as one.** There is no vanilla constant for "one settlement" because
vanilla has no settlement identity; villages are emergent POI density, which is exactly why the value
has to be ours. It is village-scale and sits below the 64-block query radius so two views of one
settlement converge rather than fork (regression: same settlement seen from two sides, 25 blocks
apart, still merges). **Too small** produces duplicate entries inside one village — visible and
cheap to fix. **Too large** collapses distinct settlements irreversibly and is invisible. Erring
small is the recoverable direction.

**The evidence-backed upgrade, deferred:** identity by admitted-POI-set overlap rather than anchor
distance — exact and radius-free. Deferred because it means storing POI positions per remembered
village rather than a count, and there is no runtime evidence yet that a radius is insufficient.

---

### P1b — POI quantity was the only confidence score

`withStrongerObservation` accepted a new anchor only when `newPoiCount > oldPoiCount`. That protected
a good anchor from an edge glance — the real risk it was written for — but froze the anchor of any
settlement that genuinely changed:

| Case | Old rule | Consequence |
| --- | --- | --- |
| village loses buildings, 20 POIs → 16 | `16 <= 20` → rejected | anchor **never** updates again |
| village rebuilt in place, 20 → different 20 | `20 <= 20` → rejected | same |

Both drift out of agreement with `Raid.getCenter()` — D-VR-019's failure reached from the opposite
direction. *"More POIs"* is a proxy for *"better view"* that stops being true the moment the
settlement is the thing that changed.

**The right signal was already being computed — but V1-R1 shipped a P0 epistemic leak (User review,
2026-08-14).** `VillagePerception.Observation` carries `withheldPoiCount` — POIs returned by
`PoiManager` whose chunks the boundary refused — and `ObservationQuality.completeness()` uses
`admitted / (admitted + withheld)` in `supersedes()`. That lets **unperceived world truth** alter
cognition: two mobs with identical loaded-chunk views can get different completeness when the server
persists different unloaded POI counts nearby. See **V1-R4** and **Topic: D-VR-033 implementation
review** — **must fix before V1-D.**

**Intended signal (post-V1-R4):** observation confidence from **`PerceptionCoverage`** — what fraction
of the 64-block search footprint's chunk columns were **loaded and perceivable** at observation time.
**Not** admitted POI count (that resurrects the V1-R1 quantity-freezing bug) and **not** hidden POI
counts (epistemic leak). **V1-R4 `ACCEPTED`** — see decision block and implementation lock below.

```text
64-block POI search footprint
        ↓
which chunk columns intersect the footprint?
        ↓
how many are already loaded (hasChunk)?
        ↓
PerceptionCoverage = loadedColumns / totalColumns
```

**Data model (V1-R4 — `LOCKED`):**

```text
PerceptionCoverage
├── loadedColumns   // int — chunk columns in footprint with hasChunk == true
└── totalColumns    // int — chunk columns intersecting 64-block footprint

Observation
├── anchor
├── admittedPoiCount      // settlement detection + diagnostics only
└── coverage              // PerceptionCoverage

ObservationQuality
├── loadedColumns         // persisted; ratio is derived view only
└── totalColumns
```

**`supersedes()` compare (deterministic — no persisted float):**

```text
new.loaded * old.total  vs  old.loaded * new.total
// equal → newer tick replaces
```

Percentages (100%, 45%, …) are **derived views** for docs/logging — not serialized float truth.

**`supersedes()` (conceptual):**

```text
new coverage > old coverage  → replace
new coverage < old coverage  → keep
equal coverage               → newer observation replaces
```

**Preserves V1-R1 without the leak:**

| Old | New | Result |
| --- | --- | --- |
| coverage 100%, 20 POIs | coverage 100%, 16 POIs (village shrank) | **replace** — equal opportunity, newer evidence |
| coverage 100%, 10 POIs | coverage 45%, 18 POIs (rim glance) | **keep** — worse legitimate window despite more POIs |

**NBT migration (V1-R4):** pre-R4 rows load observation quality as **optimistic full coverage** — do
not reinterpret persisted `withheld` from old saves (would preserve the hidden-world signal through
migration).

**Rejected (V1-R4 draft regression):** `supersedes()` on admitted POI count — `"village got smaller"`
must not mean `"my observation got worse"`.

---

### V1-R2 — memory age is not an owner-liveness signal (**P0**, User review)

The V1-R1 repair removed the unload deletion and replaced it with a staleness TTL: prune, at load,
any entry whose newest sighting was over 30 in-game days old. **That was the same mistake in a new
place.**

```text
lastTouchedTick  measures  how fresh the memory is
                 NOT       whether the mob still exists
```

An alive PlayerMob that spends thirty in-game days mining, then crosses a server restart, loses every
settlement it knows — including its `HOME_VILLAGE`. The first version deleted memory on the wrong
*event*; the second deleted it on the wrong *clock*. Both came from reaching for whatever signal was
nearest to hand to satisfy Gate RET-1, rather than asking what the gate actually requires: a bound,
not a lifecycle violation.

**The real seam exists and vanilla publishes it.**

| `RemovalReason` | `shouldDestroy()` | Village memory |
| --- | --- | --- |
| `KILLED` | `true` | delete |
| `DISCARDED` | `true` | delete |
| `UNLOADED_TO_CHUNK` | `false` | **keep** |
| `UNLOADED_WITH_PLAYER` | `false` | **keep** |
| `CHANGED_DIMENSION` | `false` | **keep** (and memory is per-dimension anyway) |

`Entity#setRemoved` assigns `removalReason` at offset 9 and invokes `levelCallback.onRemove` at
offset 45, and Fabric's `ENTITY_UNLOAD` fires downstream of that callback — so the reason is populated
when the handler reads it (`CODE_CONFIRMED`, pinned jar). `ENTITY_UNLOAD` is therefore usable after
all; what was wrong was treating the *event* as the decision instead of the *reason*.

**What remains, and what it is honestly for.** `MAX_TRACKED_MOBS` (256/dimension) survives purely as a
safety valve for a mob that vanishes without any lifecycle event reaching us. It should never fire in
normal play, so it now logs a **warning** when it does, and its victim ordering (least-recently-active)
is documented as a known-imperfect last resort rather than a correctness mechanism — because least
recently active still is not the same as gone. Reaching the cap is a signal that something is wrong,
not a routine maintenance event.

**Locked as a principle:** if village forgetting is ever wanted, it is a **cognition feature** — a
memory-decay policy with its own design, tests and player-visible behaviour — not a side effect of
garbage collection. Deleting a mob's home because it was busy elsewhere is not memory management.

---

### V1-R2 — adversarial anchor stability (User-raised, D-VR-024)

The equal-completeness-and-newer rule replaces an anchor, so a sequence of individually legal
sub-48-block observations could in principle walk it across the map. Tested rather than assumed:

| Sequence | Result |
| --- | --- |
| alternating opposite sides ×40, equal completeness | **no accumulated drift** — the anchor oscillates between the two reported positions and returns exactly to each |
| the same, checked every step against a raid centre | **stays inside the raid-association radius throughout** — D-VR-010's trigger cannot become intermittent from oscillation |
| repeated edge glances ×50 against a stored complete view | **anchor never moves** |
| monotone 20-block steps ×20 | **the anchor follows, ending 400 blocks away** |

The first three are `must` assertions. **The fourth is a real limitation, recorded rather than papered
over.** Replacement means the anchor tracks the most recent equally-good observation — correct when a
settlement is genuinely rebuilt progressively, wrong when an observation sequence merely looks like
that. Distinguishing them requires the POI-set-overlap identity already deferred under D-VR-022.

Why no drift in the alternating case: `withObservation` **replaces** the stored anchor rather than
blending toward the new one. An averaging or nudging implementation would have drifted; replacement
cannot. That is a property worth knowing was load-bearing, not a lucky accident.

**VR-T1 must report** whether real observation sequences produce the monotone shape at all — the mob's
POI set depends on where it stands, so the answer is empirical.

---

### V1-R2 — vertical settlement exposure (User-raised, D-VR-022)

`BlockPos.distSqr` is 3D. A mountainside village 30 blocks horizontally and 40 vertically apart is
`2500 > 2304` — **one village to a player, two to us**. Characterised in
`AnchorStabilityTest`, not fixed: going 2D would diverge from `RaidAssociationPolicy`, which is 3D
because vanilla's `getNearbyRaid` is. Added as a named VR-T1 runtime scenario instead of changing the
model on speculation.

---

### V1-R3 — permanent removal must clear every dimension (**P0**, User review)

Memory is per-dimension. A mob is not. Nothing reconciled the two, so the ordinary sequence leaked:

```text
Overworld : perceives villages       -> entry written to the OVERWORLD store
-> Nether : CHANGED_DIMENSION        -> shouldDestroy() false, memory preserved   [correct]
-> Nether : KILLED                   -> forget() ran against the NETHER store
                                        which never had an entry
Overworld : entry survives forever, owner permanently gone
```

**This was the common path, not an edge case.** Villages are overwhelmingly an Overworld feature and
PlayerMobs die in the Nether and End, so a long-running server accumulated one immortal Overworld
entry per such death. Worse, it would have made the `MAX_TRACKED_MOBS` warning added in V1-R2 fire
for a completely ordinary cause — destroying the signal value of a warning whose entire purpose is to
mean *something abnormal has happened*.

**The UUID survives the transition**, which is both why preserving memory on `CHANGED_DIMENSION` is
right and why the eventual deletion has to be global (`CODE_CONFIRMED`, pinned jar):
`Entity#changeDimension` creates a new entity and calls `restoreFrom`, which copies the full NBT via
`saveWithoutId` and removes only `"Dimension"`; `saveWithoutId` writes `"UUID"`.

**Fix:** `VillageMemorySavedData.forgetEverywhere(server, uuid)` sweeps `server.getAllLevels()`,
wired to both permanent-removal call sites. `forget(UUID)` survives as the single-dimension primitive
and is now banned from production by a structural test.

**The sweep must not create what it is sweeping.** `computeIfAbsent` across every dimension would
materialise village-memory files for the Nether and End of a world that never had one — cleaning up
by writing files. `DimensionDataStorage#get` returns the cached instance, else reads from disk *only
if the file exists*, else returns and caches `null` (pinned jar, offsets 0–58). Enforced by
`mustHappen_theSweepIsNonCreating`.

**Residual:** a dimension absent from `getAllLevels()` at removal time retains its entry. Vanilla
creates all registered levels at startup so this should not arise; it is covered by the
`MAX_TRACKED_MOBS` valve, which is now once again reserved for genuinely abnormal causes.

**Third repair in the same family, and the pattern is worth naming.** Each time, an eviction was
written against the *dimension the code happened to be holding* rather than against *the thing whose
lifetime it is*: the wrong event (R1), the wrong clock (R2), the wrong scope (R3). Gate RET-1 asks
who evicts and when; it does not ask **over what extent**, and all three defects lived in that gap.

---

### Verification

864 tests, 0 failures. Three negative controls, each restoring the original defect:

| Control | Fails |
| --- | --- |
| ungate the unload deletion (delete on any unload) | `mustNotHappen_unloadDeletesMemoryWithoutCheckingTheReason` |
| reinstate the staleness TTL | `mustNotHappen_memoryAgeIsUsedAsAnOrphanCollectionSignal` |
| revert to per-level eviction | `mustHappen_permanentRemovalSweepsEveryDimension`, `mustNotHappen_unloadDeletesMemoryWithoutCheckingTheReason` |
| sweep with `computeIfAbsent` | `mustHappen_theSweepIsNonCreating` |
| identity radius back to `9216` | `mustHappen_twoSettlements85BlocksApartStaySeparate`, `mustHappen_oneRaidCanCoverBothRememberedVillages`, `mustHappen_identityIsTighterThanRaidAssociation` |
| acceptance rule back to `admitted > stored.admitted()` | `mustHappen_shrunkenVillageUpdatesItsAnchor`, `mustHappen_rebuiltVillageUpdatesItsAnchor`, `mustHappen_moreCompleteViewWinsOverLargerCount` |

Still `STATIC_CONFIRMED` only — no PlayerMob has perceived a village in a running world.

---

## Topic: V2-E Behavioral Prediction — physical trade executor (`Agent_Claude`, Gate MAIBS-1)

**Status:** `DESIGN LOCKED` — prediction accepted with amendments; implementation not yet authorized.
**Gate result: `FAIL — ARCHITECTURE_DEFECT` as first briefed → `PASS — BEHAVIORALLY_PLAUSIBLE` after
the User's contract amendments below.** The original three defects are resolved by design, plus four
corrections the User found in this prediction itself — two of which were errors of mine (claim timing
and seam ordering). Read this topic together with *V2-E contract amendments and lock*.

### Evidence baseline

| Fact | Value | Label |
| --- | --- | --- |
| `FriendlyGreetGoal` priority / flags | **1**, MOVE+LOOK | `CODE_CONFIRMED` (repo-recorded; **re-verify from the pinned jar at implementation**) |
| `RaidContainersGoal` priority | **3** | `CODE_CONFIRMED` (`PlayerMobEntity#registerGoals`) |
| SPM combat / flee | preempt at **0–2** | `CODE_CONFIRMED` |
| Addon shelter goal | **2**, MOVE | `CODE_CONFIRMED` (`SpmScavenger.java:214`) |
| Addon P3 band | craft-torches · gather · smelt · descent · tunnel, all MOVE+LOOK | `CODE_CONFIRMED` (`SpmScavenger.java:221–281`) |
| `EnvironmentalEscapeGoal` | **0** | `CODE_CONFIRMED` |
| Planned `TradeWithVillagerGoal` | **3**, MOVE+LOOK | design |
| Villager wander speed vs PlayerMob | villagers stroll ~0.5, mob paths ~1.0 | `GAME_MECHANICS_INFERRED` |

---

### 1 — The defect, stated first

**A priority-3 goal cannot hold a claim against a priority-1 goal.** `GoalSelector` re-evaluates
every tick; when `FriendlyGreetGoal.canUse()` returns true it takes MOVE+LOOK and the P3 trade goal
is **stopped**, not queued. A `TradeSessionClaimWindow` owned by the trade goal therefore cannot
protect anything — the goal holding it has already lost.

Worse, the collision is *likely* rather than incidental: `FriendlyGreetGoal.canUse` calls
`nearestWhereReaction(GREET, range)` and takes the **nearest greetable entity**. A mob that has just
walked into interaction range of a villager has made that villager the nearest greetable entity. So
the approach itself creates the preemption.

**The only mechanism that can express this claim is the one we already own:**
`FriendlyGreetAdmissionSeamMixin` redirects that exact call. Returning `null` for the claimed
villager makes `canUse` false and the greet never starts.

**But that is the veto removed in 44D-R2** for good reason — it silently deleted native greeting.
The resolution must therefore be *narrow*, and the difference is real:

| 44D-R2 removed | V2-E needs |
| --- | --- |
| a **global** veto: any greet without a bound SOCIAL intent | a **targeted, expiring** suppression: this one villager, while a trade session is live |

**Required shape:** the seam consults a claim keyed by `(mobUUID → villagerUUID, expiresAtTick)`,
suppresses only that pairing, and expires on a hard tick bound. Every other greet, including this
mob's greet of any *other* villager, proceeds untouched. Without that, V2-E as briefed produces
WEIRD-4 on the first trade.

**Rejected alternative:** raise `TradeWithVillagerGoal` above priority 1. That inverts trade above
combat and shelter, which is unacceptable — the mob would trade through a skeleton fight.

---

### 2 — Exact observable loop

```text
external demand exists            (WorkDemandPolicy, live — not a cached flag)
        ↓
TRADE wins acquisition admission  (V2-C, existing route infeasible)
        ↓
filter candidate set to currently legal AND reachable      ← §6
        ↓
rank remaining by V2-B utility, choose best remaining
        ↓
path toward villager                                        MOVE claimed
        ↓
villager strolls / door closes / world changes
        ↓
repath on a bounded cadence; give up after N failures for THIS candidate
        ↓
enter interaction range
        ↓
FACE (LOOK)                        claim (mob → villager) opens here, not earlier
        ↓
re-fetch LIVE villager, offers, backpack, demand
        ↓
recompute EVERY attempt-bound fact:
    affordability for THIS offer   (V2-C carried obligation)
    sell uses for THIS offer       (V2-D carried obligation)
    consumer still wants it        (live selection, not cached boolean)
    result capacity
        ↓
commit (V2-A executeAgainst)      inventory + MerchantOffer.uses change
        ↓
release claim immediately
        ↓
re-perceive → chain step advances / completes / replans
```

**The claim opens at FACE and not at path start.** A claim held across a 30-second walk suppresses
greeting for a villager the mob may never reach, and is the difference between a bounded interlock
and a village-wide greet outage.

---

### 3 — GoalSelector interaction table

| Goal | Priority | Flags | Interrupts trade? | State retained | Observable result |
| --- | ---: | --- | --- | --- | --- |
| `EnvironmentalEscapeGoal` | 0 | MOVE | **yes** | chain survives (transient, re-evaluated) | mob abandons approach to escape fire/lava; resumes if demand persists |
| SPM combat / flee | 0–2 | MOVE+LOOK | **yes, immediately** | chain survives; claim must be released on `stop()` | mob breaks off mid-approach; **claim must not outlive the goal** |
| Player command | SPM | MOVE | **yes** | demand survives, revalidates | commanded movement wins; trade re-admits afterwards if still legal |
| Addon shelter | 2 | MOVE | **yes** | chain survives | at dusk, shelter outranks trade; trade cannot start during a shelter hold |
| **`FriendlyGreetGoal`** | **1** | MOVE+LOOK | **yes — and this is the defect** | claim ineffective without the seam | see §1 |
| `DoorOperationGoal` | 1 | stationary, finite | suspends travel | path becomes stale | door opens, **path must be discarded and rebuilt**, not resumed |
| `EatFoodGoal` (SPM) | SPM | — | yes | chain revalidates smaller stock | survival stays authoritative; V2-D already reports `sellBlocked` rather than locking out |
| `RaidContainersGoal` | **3** | MOVE+LOOK | **same band — alternates** | both retain intent | §5 WEIRD-2 |
| Addon gather/smelt/craft P3 | 3 | MOVE+LOOK | same band | `TradeDemandGate` mutual exclusion | one P3 owner at a time by design |
| `VillagePerceptionObserver` | 9 | **no flags** | no | — | flagless; cannot contend |

**Two MOVE owners never run concurrently** — every row above that claims MOVE stops the trade goal
outright rather than sharing.

---

### 4 — Time simulation (normal scenario)

```text
T0      demand: 1 book. Existing route infeasible. TRADE admitted.
        Candidate set: farmer A (best offer, 18 blocks), librarian B (worse, 9 blocks).

T+10    A chosen and reachable. Path built. MOVE owned by trade goal.
        Villager A is strolling; path target is an entity, so it must be re-issued, not set once.

T+60    A has walked behind a house. Path invalidated.
        Repath #1 succeeds. (Bound: N failures for THIS candidate, then demote it — §6.)

T+200   Mob arrives within interaction range. FACE.
        FriendlyGreet.canUse fires against A — the nearest greetable entity is now A.
        WITH the targeted claim: greet suppressed for A only; trade proceeds.
        WITHOUT it: greet takes MOVE+LOOK, trade goal stops. → WEIRD-4.
        Live re-fetch: offer still matches, affordable for THIS offer, room for result.
        Commit. uses 0→1. Claim released.

T+201   Re-perceive. Chain: emeralds spent, book held. Demand satisfied.
        TradeChainPolicy → TARGET_OBTAINED (held ≥ desired). Chain terminates.
        V2-C re-runs: no demand → EXISTING_WORK. P3 band returns to gather/smelt.

T+1200  Twenty minutes. No demand → no trade goal activity beyond the flagless
        perception observer. THE FAILURE TO WATCH FOR: `canUse` returning false
        every tick while an unsatisfiable demand persists — cheap, but if it
        re-runs candidate ranking each tick it is a per-tick scan (§5 WEIRD-5).
```

---

### 5 — Adversarial suite

| # | Scenario | Prediction | Class |
| --- | --- | --- | --- |
| A | villager moves during approach | entity-target path re-issued on cadence; bounded repaths | plausible |
| B | villager sleeps before FACE | vanilla merchant refuses; must classify as *candidate unavailable*, demote, try next — **not** BLOCKED | plausible **if** §6 holds |
| C | human player trading the target | `tradingPlayer != null`; our adapter never sets it, so we could trade *underneath* a player session. **Must refuse while `getTradingPlayer() != null`** — not currently in the V2-A adapter | `RUNTIME_QUESTION` → becomes a V2-E requirement |
| D | another PlayerMob consumes the final use | live re-fetch sees `isOutOfStock` → `OUT_OF_STOCK`, no mutation (V2-A proven) | plausible |
| E | FriendlyGreet on the same villager | **§1 defect** without the targeted claim | `ARCHITECTURE_DEFECT` |
| F | combat during WALK | P0–2 preempts; claim not yet open; chain survives | plausible |
| G | combat during FACE | preempts **with the claim open** → claim must release on `stop()`, or greeting stays suppressed for a villager nobody is trading with | `ARCHITECTURE_DEFECT` if the claim has no `stop()` release |
| H | SPM eats a planned sell input | V2-D recalculates, reports `sellBlocked`; no lockout | plausible (proven statically) |
| I | backpack full before commit | V2-A preflight returns `NO_ROOM`, nothing spent | plausible (proven statically) |
| J | desired output acquired during travel | chain terminates `TARGET_OBTAINED_ELSEWHERE` mid-walk; goal must check this on `canContinueToUse`, not only at commit | plausible **if** the check is in continue |
| K | best candidate unreachable, #2 reachable | **§6** — the central rule | `ARCHITECTURE_DEFECT` if unhandled |
| L | `RaidContainersGoal` wants the same P3 slot | same band; alternation, not starvation, **unproven** | `RUNTIME_QUESTION` (VR-T2e) |
| M | two PlayerMobs on one nearly-exhausted offer | both re-fetch live; one gets `OUT_OF_STOCK`; no shared reservation exists | `ACCEPTABLE_STEPPING_STONE` |
| N | repeated path failure | must demote the candidate after N attempts (§6) | plausible **if** bounded |
| O | no valid trade for minutes | `canUse` false cheaply; must not re-rank per tick | `RUNTIME_QUESTION` (§5 WEIRD-5) |

**Omitted deliberately:** cave/ravine/elevation geometry — trade targets are entities inside a
settlement on walkable ground, and the descent goals own vertical navigation.

---

### 6 — The candidate-selection rule (must be explicit before coding)

> **"best-ranked offer is unreachable" ≠ "trade is unreachable".**

```text
candidate set (bounded, from V2-C ranking)
        ↓
filter: villager alive · awake · not player-occupied · reachable
        ↓
rank remaining by V2-B utility
        ↓
attempt best REMAINING
        ↓
path fails N times → demote THIS candidate for this decision cycle
        ↓
next legal candidate, or BLOCKED only when the set is empty
```

Without the demotion step the loop is: choose A → path fails → re-evaluate → A still ranks best →
choose A → … That is technically correct and visibly idiotic, and it is the exact shape MAIBS exists
to catch. **No persistent blacklist is needed** — demotion within the decision cycle is sufficient,
and it keeps the policy stateless in the V2-C sense.

---

### 7 — Predicted weird behaviours

| # | Behaviour | Class | Resolution / probe |
| --- | --- | --- | --- |
| **WEIRD-1** | villager strolls just beyond interaction range; mob follows indefinitely | `ACCEPTABLE_STEPPING_STONE` **iff** bounded by a pursuit tick budget or total attempt cap; **`ARCHITECTURE_DEFECT`** unbounded | villagers stroll slower than the mob paths, so convergence is normal; the risk is a villager pathing through a door loop. **Bound it.** |
| **WEIRD-2** | `RaidContainersGoal` (P3) finishes, trade gets one window, chest raiding reacquires immediately, forever | `RUNTIME_QUESTION` | Same band, so the selector alternates rather than starves — but SPM's goal has a 12-block reactive trigger and ours needs a walk. **VR-T2e:** place a chest cluster and a trade candidate in range and count completed trades over 5 minutes. |
| **WEIRD-3** | best candidate path fails; picker re-selects it every cycle; approach→fail→approach thrash | `ARCHITECTURE_DEFECT` **as briefed** | §6 demotion. Falsified by K in the adversarial suite. |
| **WEIRD-4** | FACE claim absent/expired; P1 greet wins; trade/greet oscillation on the same villager | `ARCHITECTURE_DEFECT` | §1 targeted seam claim, released on `stop()`, hard-expiring. |
| **WEIRD-5** | trade infeasible but demand persists → `canUse` churn, candidate re-ranking every tick | `ACCEPTABLE_STEPPING_STONE` **iff** `canUse` is cheap and ranking is cadenced; `ARCHITECTURE_DEFECT` if ranking runs per tick | Reuse the existing failed-search cooldown pattern (`SmeltAtFurnaceGoal` `FAILED_SEARCH_COOLDOWN_TICKS`). **Log-frequency sample** is the probe (RET-1d). |

---

### 8 — Two design options

| | **Option 1 — seam-claim interlock** (recommended) | **Option 2 — trade as a SOCIAL sub-mode** |
| --- | --- | --- |
| Mechanism | targeted, expiring greet suppression through the existing admission redirect | trade runs *inside* `FriendlyGreetGoal`'s slot as an Opinion-bound SOCIAL execution |
| Priority conflict | resolved: greet never starts for the claimed villager | resolved: only one goal involved |
| Cost | reintroduces suppression logic 44D-R2 removed — must be narrow, keyed, expiring | couples trade to the GAO-10 binding machinery; a trade failure becomes a social-learning event |
| Risk | a leaked claim silently suppresses greeting (mitigated by hard expiry + `stop()` release) | conflates two activities; `ActivityClass.VILLAGE_TRADE` (V2-F) would then be a lie |
| Verdict | **recommended** — narrow, testable, keeps trade and social separable | rejected for gen-1 |

---

### 9 — Acceptance

**Must happen**
- with a live demand, a reachable villager and an affordable matching offer, the mob **walks to that
  villager, faces it, and completes exactly one trade**, with `uses` +1 and the exact result in the
  backpack;
- when the best-ranked candidate is unreachable and a second legal candidate exists, the mob trades
  with the **second**;
- every attempt-bound fact (affordability, sell uses, consumer, capacity) is recomputed against the
  **offer actually being attempted**.

**Must not happen**
- greeting suppressed for any villager other than the claimed one, or after the claim's expiry, or
  after the trade goal stops;
- approach→path-fail→approach thrash on one candidate;
- a trade committed while a human player holds the merchant session;
- candidate re-ranking every tick while no trade is possible.

### 10 — Falsifying runtime experiment (VR-T2 family, not yet authorized)

Vanilla-only instance (`tradeeverything` absent, `D-VR-069`). One PlayerMob, one demand, a village
with **two** matching villagers — the better offer walled off behind geometry, the worse one
reachable. A chest cluster in range to engage `RaidContainersGoal`. Run 5 minutes.

**Falsifies the prediction if:** the mob never trades · it trades with the unreachable candidate's
villager after a wall-clip · greeting stops working for unrelated villagers · the log shows candidate
ranking at tick frequency · zero completed trades while chests remain.

---

### V2-E contract amendments and lock (User, 2026-08-15) — gate flips to `PASS`

**Option decision: `TARGETED SEAM CLAIM` `LOCKED`. `TRADE AS SOCIAL SUB-MODE` `REJECTED`.**

Rejected because the FriendlyGreet integration is SOCIAL-specific end to end — admission, binding,
`start()`, DONE observation, `stop()`, learning evidence, all through
`SocialExecutionBindingRegistry`. Putting trade inside that executor would make *"external
progression demand → acquisition strategy → merchant interaction"* arrive as *social completion
evidence*, contaminating Opinion learning, activity classification, interruption semantics and
telemetry — and would make `ActivityClass.VILLAGE_TRADE` (V2-F) fight the architecture rather than
describe it. **SOCIAL is why a mob engages an entity; VILLAGE_TRADE is why it acquires resources
through a merchant.** Both involving walking toward a villager is not sufficient reason to merge them.

**This is not the 44D-R2 veto.** That was *no SOCIAL intent → suppress native greeting*, which let
Opinion globally erase SPM behaviour. This is *active trade with Bob + SPM wants to greet Bob →
suppress that exact collision*. Alice untouched, Charlie untouched, another PlayerMob greeting Bob
untouched, and Bob greetable again the moment the attempt ends. An arbitration interlock, not social
ownership.

---

#### Correction 1 — the claim opens at attempt start, not at FACE (`Agent_Claude` was wrong)

The prediction above put the claim at FACE and argued a claim held across the walk was too broad.
**That leaves V2-E's correctness resting on an unproven scheduler-ordering fact**: that the trade
goal transitions WALK → FACE and publishes its claim *before* `FriendlyGreetGoal.canUse()` next
evaluates the now-close villager. Nothing proves that ordering, and the same prediction states the
selector re-evaluates the P1 greet every tick. Correctness must not rest on a sequencing detail we
neither modelled nor pinned.

```text
candidate Bob selected
        ↓
navigation attempt admitted
        ↓
claim(mob, Bob)          ← here, before WALK can enter greet range
        ↓
WALK → FACE → EXECUTE
        ↓
release
```

The claim means only: *while I am deliberately trying to reach Bob to trade, I will not interrupt
myself to greet Bob.* The "30-second walk suppresses greeting" objection is answered by bounding the
**attempt**, which WEIRD-1 independently requires — not by letting trade self-destruct by greeting
its own target. A lease/interlock, never a villager reservation.

#### Correction 2 — the interlock runs before SOCIAL admission is published (`Agent_Claude` missed this)

`FriendlyGreetAdmissionSeamMixin` records the observation on its **fourth line**, before any
downstream logic. An interlock bolted onto the end would produce:

```text
trade owns Bob → SPM says Bob is greetable → SocialAdmissionSeam records Bob
              → Opinion forms SOCIAL/Bob → interlock finally returns null
```

The physical greet never happens, but cognitive work has been manufactured for an executor we
deliberately made unavailable. Required order:

```text
original = SPM nearestWhereReaction(...)
if (original == null)                      -> existing handling
if (TradeSessionClaimWindow.claims(mob, original)) return null;   // BEFORE publication
recordObservation(...)                     // only now is native admission genuinely available
existing Opinion / social-binding machinery
```

`SocialAdmissionSeam` must **not** become a trading abstraction. The trade interlock stays a separate
type; the mixin is the integration boundary that consults both.

#### Correction 3 — a bounded candidate-attempt ROUND, not a "decision cycle"

*"Demote for this decision cycle"* was ambiguous: if the cycle ends when `canUse()` returns, the
demotion vanishes exactly when it is needed.

```text
discover candidate set → rank A > B > C
attempt A → path budget exhausted → mark A attempted FOR THIS ROUND
attempt B → fails            → mark B attempted
attempt C → …
all exhausted → round ends → failed-search cooldown → fresh round (A eligible again)
```

No permanent blacklist, no `SavedData`. **V2-C's policy stays stateless; the physical executor holds
transient attempt state because movement unfolds over time.** That distinction is the point.

#### Correction 4 — evidence split on the busy merchant, and sleep is ours

| Claim | Label |
| --- | --- |
| `VillagerTradeAdapter.performTrade` has **no** `getTradingPlayer()` check — it guards `backpack != null`, `villager != null`, `villager.isAlive()` only | **`CODE_CONFIRMED`** (verified in the shipped file) |
| What racing a live human session actually looks like at runtime | `RUNTIME_QUESTION` |

Two layers, the second mandatory: candidate selection ignores a merchant a human currently occupies,
**and** `performTrade` re-checks `getTradingPlayer() != null` at the transaction boundary, returning
an explicit `MERCHANT_BUSY`. A human may begin trading during the mob's walk — *planning permission
does not authorize execution*.

**Sleeping villagers likewise.** The prediction said "vanilla merchant refuses"; the adapter has no
asleep check either, so nothing is enforcing it on this no-menu path. Awake/not-sleeping is a
**V2-E candidate and attempt legality rule**, not something a lower layer handles magically.

---

#### Locked constraints

1. Targeted seam interlock; no SOCIAL sub-mode.
2. Claim identity is exactly `(mob UUID, villager UUID)`.
3. Claim begins when the concrete trade attempt starts — before WALK can enter greet range.
4. The claim confers no authority over the villager and blocks no other entity's interaction.
5. Release on `stop()`, successful transaction, abandoned/demoted target, demand disappearance,
   target loss, mob removal/unload/death and server shutdown, with a hard expiry as backstop.
6. The greet seam checks the SPM-selected entity against the trade claim **before** publishing it as
   SOCIAL admission evidence.
7. Candidate failures live in one transient candidate-attempt **round**.
8. Exhausted round → failed-search cooldown → fresh round.
9. Player-occupied merchant rejected at selection **and again** by the adapter at execution.
10. Sleeping/unavailable merchant is explicit V2-E legality, not assumed from V2-A.

Constraint 5 is a Gate RET-1e surface in miniature: the claim is runtime-only, but its release set
must be complete or a leaked claim silently suppresses greeting for a villager nobody is trading
with — the `stop()` case being the one the original prediction missed.

#### Gate MAIBS-1 — result after amendment

`PASS — BEHAVIORALLY_PLAUSIBLE`, carrying WEIRD-1 (bounded pursuit), WEIRD-2 (`RaidContainersGoal`
alternation) and WEIRD-5 (cadenced re-ranking) as bounded stepping stones with named runtime probes.
Runtime `UNVERIFIED` until VR-T2.


### Gate MAIBS-1 result

`FAIL — ARCHITECTURE_DEFECT` for V2-E **as briefed**: the greet interlock (WEIRD-4/scenario E) cannot
work at priority 3, the claim has no defined release on `stop()` (scenario G), and candidate
demotion (WEIRD-3/scenario K) is unspecified.

**All three are resolvable in design, before code.** With §1, §6 and a `stop()`-released expiring
claim adopted, the predicted result is `PASS — BEHAVIORALLY_PLAUSIBLE` with WEIRD-1, WEIRD-2 and
WEIRD-5 carried as bounded stepping stones plus two named runtime probes. Scenario C
(player-occupied merchant) becomes a new V2-E requirement on the V2-A adapter.


---

## Topic: Trade demand integration — `WorkDemandPolicy` facade (`Agent_Cursor`)

**Author:** `Agent_Cursor` (brainstorm continuation 2, 2026-08-14)
**Status:** `PROPOSED` — reconciles D-VR-007 with production code

`MaterialDemandPolicy` is cited across RFCs (`D-TTU-017`) but **NOT FOUND** in Scavenger `src/main`
(3× probe: glob, `rg MaterialDemandPolicy`, package scan). Production arbitrator:

```text
WorkDemandPolicy.java
  → record MaterialDemand(materialKey, derivedDeficit, consumerKey)
  → select() among charcoal / iron-tool smelt demands today
```

**Gen-1 trade integration (`PROPOSED` — B-VR-20):**

```text
TradeEvaluationPolicy
  → scores MerchantOffer against MaterialDemand tickets
  → emerald consumerKey: "trade:<profession>:<offerIndex>" or "wealth:emerald"
  → VillagerTradeAdapter.performTrade mutates villager offers + mob backpack atomically
```

Do **not** block V2 on renaming `WorkDemandPolicy` → `MaterialDemandPolicy`; add a thin
`TradeDemandFacade` or extend `consumerKey` vocabulary. Cross-RFC iron/diamond chains stay on
existing `WorkDemandPolicy` paths.

**Must happen:** "need 27 emeralds" creates a demand ticket trade evaluation can satisfy.
**Must not happen:** parallel emerald-hoarding goal fights `TradeWithVillagerGoal` without director arbitration.

---

## Topic: Trading — `VillagerTradeAdapter` (`Agent_ChatGPT`)

**Author:** `Agent_ChatGPT` (design); `Agent_Cursor` (V2 brainstorm continuation 6, 2026-08-15)
**Status:** `CONSENSUS` + **V2 contract `LOCKED`** — core path **mixin-free** (`D-VR-053`); hero discount remains V6.

### Hard boundary (`CONFIRMED` design constraint)

Vanilla merchant contract is **player-centric**: `Merchant` stores a player customer; menu APIs expect `Player`. Mineflayer exposes explicit `openVillager()` / `trade()` — SPM has neither.

| Approach | Trading parity |
| --- | --- |
| Datapack | **No** |
| KubeJS | Partial / fake |
| Events only | Partial |
| **Compat addon (`spmscavenger`)** | **Yes — preferred** |
| Addon + Mixins/accessors | **Strongest** |
| Direct SPM source edit | Yes, unnecessary (licence) |

### Recommended design

**Do not create a fake GUI.** Server-controlled mob needs no client menu.

**`VillagerTradeAdapter`** (pure + bridge):

```text
inspectOffers(villager, mob)     // copy MerchantOffers snapshot; apply gen-1 price view (no hero discount)
evaluateOffers(...)              ← TradeEvaluationPolicy / MaterialDemand
planTransaction(backpack, offer) // pure staged slot allocation + result-capacity simulation
performTrade(...)                // exact live revalidation → apply slot delta → notifyTrade once
                                 // notifyTrade already increaseUses — adapter must not double-count
```

`MerchantOffer#satisfiedBy/#take` accept exactly two aggregate payment stacks; they are not a
multi-slot backpack transaction API. The adapter therefore uses each public `ItemCost` predicate to
allocate payment across copied backpack slots, invokes vanilla matching semantics on staged values,
and commits only after output capacity and the live offer are revalidated. Directly shrinking live
slots and attempting rollback afterward is rejected: a partial insert, component-bearing cost, or
offer mutation can make rollback ambiguous.

The offer snapshot is evaluation evidence, not durable identity. A chain stores its external demand;
each execution attempt resolves a current live offer. It never persists `lastOfferIndex` as authority.
If a human is currently trading with that villager, automation defers until `getTradingPlayer()` is
null so player-specific mutable `specialPriceDiff` cannot leak into a PlayerMob transaction.

### Optional compatibility — Trade Everything v0.3.0 (`D-VR-068` / `D-VR-069`)

**Prerequisite:** VR-T2 **PASS** in a vanilla-only instance (`D-VR-069`). This section does **not**
define the first proof.

**Runtime status (`V2-DEF-003c-R1`, `RUNTIME_CONFIRMED`, 2026-08-19):** the Step-7A autonomous
readout observed the complete shared-authority and economic chain:

```text
ROUTE UNKNOWN/FEASIBLE -> GATHER PUBLISHED -> GATHER YIELDING
  -> ROUTE INFEASIBLE -> PLAN #1 TE -> TRADE #1 logs 320->298
  -> 12 TE funding sells -> 12 emeralds
  -> vanilla Toolsmith BUY -> 1 iron_pickaxe
  -> routeEvidence tracked=0
```

Counters: `plans=13 (TE 12)`, `revals=13`, `trades=13`, `episodes=0`. This confirms the V2-TE
positive opportunity/revalidation/execution path and its composition with the vanilla purchase
source. It does **not** confirm `VR-T2l` (Trade Everything absent/incompatible), and `episodes=0` is
not promoted into a relationship-learning result. The full defect and acceptance record is in
`docs/porting/KNOWN_DEFECTS.md`; the compact scenario result is in
`docs/porting/TEST_MATRIX.md`.

**Source-confirmed baseline:** [`bh679/tradeeverything-mc@fe305e6`](https://github.com/bh679/tradeeverything-mc/tree/fe305e663052c637dfeae2c9a8294c7748c611b0), Minecraft 1.21.1, Fabric mod id
`tradeeverything`. `AbstractVillagerTradingMixin` prepends its synthetic offer only during
`setTradingPlayer(player)` and removes it on `setTradingPlayer(null)`; the save redirect filters it
from NBT. Therefore ordinary no-menu `villager.getOffers()` cannot discover it.

Do not fake a player session. Preserve one transaction owner and separate opportunity discovery:

```text
TradeOpportunitySource
  ├─ VanillaTradeSource          → live MerchantOffers
  └─ TradeEverythingTradeSource  → exact synthetic quote when optional mod is compatible
                         ↓
                 TradeEvaluationPolicy
                         ↓
              VillagerTradeAdapter transaction
```

`TradeOpportunitySource` supplies immutable, revalidatable quotes; it does **not** mutate the
backpack or call `notifyTrade`. V2's staged slot transaction remains the only execution path.

The advertised `TradeEverythingApi` exposes valuation/config/provider hooks but **not** exact quote
construction. Exact behavior also depends on payout selection, exemptions, buyback, margins, caps,
and `SyntheticOfferFactory` marking. Options:

| Integration | Stability | Fidelity | Decision |
| --- | --- | --- | --- |
| Upstream adds official quote API | `API_STABLE` | Exact | **Preferred** |
| Bounded reflective call to internal `OfferQuoter.quote` at pinned v0.3.0 | `VERSION_LOCKED / API_DEPENDENT` | Exact for supported version | Acceptable fallback; fail closed on signature/linkage mismatch |
| Recreate quote from `getValueSixteenths` | Locally stable, behavior-drifting | Inexact | Rejected — duplicates Trade Everything's economy |

Trade Everything may pay coal, wheat, paper, iron, emeralds, or another selected buy item—not
necessarily emeralds. The planner evaluates the **actual quoted result** against the external demand.
Before quoting, `SurplusDispositionPolicy` must authorize the exact input stack: economic value never
overrides equipped-tool, survival-stock, protected-chain-input, progression-reserve, or unique-item
protection. Exact component matching is preserved for damaged, enchanted, named, and trimmed stacks.

**Performance:** no per-tick economy scan. Only an admitted downstream demand at a reached villager
may inspect the bounded PlayerMob backpack (currently eight slots) and quote eligible surplus. Mod
absence or incompatibility returns zero Trade Everything opportunities and leaves vanilla V2 intact.

**Author overlap is provenance, not an API guarantee.** BrennanHatton owning both projects makes the
integration relevant, but only pinned API/source and compatibility tests justify support claims.

**Mixin scope (`CODE_CONFIRMED`, 2026-08-15 audit):** gen-1 **does not require** `MerchantMenu` or
`setTradingPlayer` mixins. **Optional / later:** hero discount (`updateSpecialPrices`), raid gift
bridges (V6). Amends phased-plan **REQUIRES MIXIN** label to **PARTIAL** for V2 core; V6 bridges
unchanged.

**`TradeWithVillagerGoal`** executor:

```text
pick villager
  → walk over
  → face villager
  → inspect offers
  → choose useful offer (utility, not hardcoded profession)
  → execute one trade (atomic inventory)
  → pause
  → optionally trade again
  → leave
```

### Demand-driven evaluation (not “librarian = good”)

Plug into future **`MaterialDemandPolicy`** (`RFC-TOOL-TIER-UPGRADES` D-TTU-017):

```text
MaterialDemand: need 27 emeralds

Villager offers:
  20 wheat → 1 emerald
  15 carrots → 1 emerald
  coal → 1 emerald

Policy computes: stock, acquisition cost, restock state, demand
→ decision: "Sell carrots."
```

Reverse chain:

```text
Demand: specific useful item (e.g. mending book)
  → villager trade can satisfy
  → acquire emeralds (gather/craft/loot/trade)
  → return to known librarian
```

**Acquisition strategies** (unified):

```text
Need X → gather | craft | loot | process | trade
```

### Restock awareness (emergent)

Villagers restock at workstation (up to twice per day; must **reach** POI — Mojang workstation logic).

```text
desired trade unavailable
  → villager needs restock?
  → workstation reachable?
      blocked → open door / clear obstruction (if ALLY + mobGriefing)
      missing → optionally place workstation (policy)
      night → defer
  → wait → return when restocked
```

Example emergent sequence (`UNVERIFIED` runtime): *"That librarian isn't working."* → inspect lectern → open door → wait → trade.

### Trade curiosity (personality)

Not every trade must be optimal (`Agent_ChatGPT`):

- Unfamiliar profession → inspect offers → leave
- Villager leveled up → re-check offers
- Wandering Trader → browse → mild purchase
- Idle browsing when no urgent `MaterialDemand`

---

## Topic: Bells, farming, population, golems (`Agent_ChatGPT`)

### Bells — **FULL** practical parity

`BellBlock.attemptToRing(Entity, Level, BlockPos, Direction)` — initiator **not** restricted to
`Player` (`CONFIRMED` — Mojmap pin; see API mapping topic).

**`RingVillageBellGoal`** triggers:

| Trigger | Rationale |
| --- | --- |
| Raid detected | Alert villagers; reveal raiders (vanilla bell behaviour) |
| Large nearby threat | Alarm |
| Curiosity trait | Ring once for no reason |
| Another mob rang bell | Look toward bell |
| Raid ended | Celebration ring (unnecessary = human) |

**Intelligent defense composition** (prefer over monolithic `DefendVillageGoal`):

```text
exposed villagers + active raid + known bell
  → go to bell → ring → villagers retreat
  → PlayerMob moves to village edge → existing combat engages raiders
```

### Population support — **FULL** practical parity

**Canonical disposition:** **IN V3** as bounded food support only. V3 does not command breeding,
claim beds, or manipulate villager Brain state. Admission is discretionary, requires population
need plus disposable food after survival/progression reserves, and revalidates both facts at handoff.

**Not** calling villager `wantsToStartBreeding()` directly. **Player parity:**

```text
recognize low population (bed count vs villager count heuristic)
  → collect food (HarvestCropsGoal / hunt)
  → throw food to villagers (GiftPolicy / drop)
  → ensure accessible beds (observe, not claim villager beds)
  → leave villagers to breed
```

**`SupportVillagePopulationGoal`** — reuse SPM gift + harvest executors.

Personality variants: over-feed proudly; bring bread because it worked once (`InteractionMemory`: action → outcome → utility modifier — deterministic, not ML).

### Farming — generalize `HarvestCropsGoal` motive

SPM today (`CONFIRMED`): harvest ripe crops when mob wants food; **no replant** (`ForagePolicy` issue #5 area).

**`CropDemand` motives:**

| Motive | Executor |
| --- | --- |
| Personal food | `HarvestCropsGoal` |
| Trade stock | Same executor, different policy |
| Villager support | Same |
| Hoarding / seed reserve | Same |

**Canonical ownership (`D-VR-079`): committed atomic harvest→replant episode.** The former loose
alternative — a separate independently admitted `ReplantCropGoal` — is rejected for managed village
fields because interruption between two goals can turn a successful harvest into routine barren
farmland. The episode owns target, crop/seed representation, and completion together:

```text
harvest mature crop → retain seed → replant → keep surplus
```

Personality: responsible always replants; greedy leaves; chaotic half-farm; village-ally replants.

**Composting — IN V3** as a low-priority side activity: excess compostables → known loaded composter
→ bone meal. It may consume only explicitly disposable surplus after survival, progression,
replant, and population-food reserves; it creates no independent appetite for seeds.

**Workstation awareness — IN V3, read-only only.** Extend existing bounded village perception facts
with loaded/perceivable job-site availability and villager reach/restock relevance. V3 does not
place, claim, break, or reassign workstations and does not add a second world scanner. Restock facts
may inform whether an already-authorized trade route is transiently blocked; they do not authorize
trade or displace mandatory progression.

### Golem relationship

SPM `PathfinderMob` (not `Enemy`) — iron golems don't auto-attack PlayerMobs (`CONFIRMED` — `RaiderTargetsPlayerMobMixin` javadoc).

| Behaviour | Feasibility |
| --- | --- |
| Treat golem as neutral protector | **FULL** |
| Assist when golem fights raid threat | **PARTIAL** (shared target) |
| Wait/repath if golem blocks door | **FULL** |
| Disengage if golem angry at PlayerMob | **PARTIAL** |
| Hang near golem (high village affinity) | **FULL** (social) |

### Zombie-villager curing

**Canonical disposition: DEFERRED TO V6.** Curing needs a weakness/apple interaction executor and
player-credit/relationship decisions that are outside Village Work. Earlier `P5 Cure → V3` and
Appendix-D `CureVillagerGoal | V3` rows are explicitly superseded by `D-VR-078`; V3 has no curing
task or closure scenario.

`ZombieVillagerEntity` stores converter UUID (`INFERRED` — verify field at implementation). **`CureVillagerGoal`:**

```text
identify curable zombie villager
  → gather weakness + golden apple
  → legitimate interaction sequence via adapter
  → wait for conversion
  → register relationship / reputation read
```

**Feasibility:** **REQUIRES MIXIN / ADDON** for player-credit path; not instant entity swap.

### Storage ownership (separate concern, prerequisite)

Before village residency (`Agent_ChatGPT`):

```text
StorageOwnership:
  MOB_OWNED | EXPLICITLY_SHARED_WITH_MOB | VILLAGE_PUBLIC | FOREIGN | UNKNOWN
```

SPM `RaidContainersGoal` treats all chests alike — **dangerous** in villages. V3-A/B implement the
minimum `D-VR-017` profile + explicit-permission predicate. `UNKNOWN` fails closed for
`VILLAGE_ALLY`; non-allies retain host behavior. Full personal/village storage remains deferred.

---

## Topic: Raid orchestration (`Agent_ChatGPT`)

### Composed defense (not one mega-goal)

```text
RaidAwareness
  + RingBellGoal
  + existing SPM combat (WeaponAwareAttackGoal)
  + VillagerProtectionUtility (target filter)
  + retreat/recovery
```

### `Raid` object awareness — **PARTIAL** / **FULL** for read

Query `level.getRaidAt(pos)` / `Raid` instance (`INFERRED` — vanilla exposes center, status, raiders, waves).

**`RaidTask` state:**

```text
RaidTask
├── raidId / village anchor
├── startedTick
├── state: PRE_RAID | ACTIVE_WAVE | BETWEEN_WAVES | VICTORY | DEFEAT
├── previousTask (TaskMemory resume)
├── villagersAtStart
├── knownBell
└── safeRetreatPoint
```

Maps to `TaskLifecycle`: `RUNNING`, `INTERRUPTED`, `RETRY`, `SUCCESS`, `FAILURE`.

### Interrupt / resume progression

```text
Current project: Acquire iron
  → raid at home village recognized
  → project INTERRUPTED
  → participate / survive
  → raid ends
  → revalidate demand
  → resume Acquire iron
```

### Raid utility (not always fight)

| Output | Factors |
| --- | --- |
| `DEFEND` | affinity, gear, health |
| `SUPPORT` | ring bell, close doors |
| `EVACUATE` / `HIDE` | coward profile, low health |
| `LEAVE_VILLAGE` | low affinity, severe raid |

No LLM — utility scoring only.

### Hero of the Village — promising but `UNVERIFIED`

`Raid` stores `heroesOfTheVillage` as `Set` with **`addHeroOfTheVillage(Entity)`** — **may** accept
`PlayerMobEntity` (`CONFIRMED` mapping; runtime reward/effect path `UNVERIFIED`). Reward/effect path
may still assume player semantics → **MIXIN-assisted** target: meaningful participation credit +
trade discount equivalent, not only vanilla status effect clone.

### After-raid cleanup (busy PlayerMob content)

```text
victory → collect drops → check villagers → check golem
  → optional celebration bell → eat → repair → trade → resume old work
```

---

## Topic: Opportunistic village jobs & curiosity (`Agent_ChatGPT`)

Idle / side-activity catalogue (executor reuse):

| Job | Executor |
| --- | --- |
| Harvest / replant field | Farm goals |
| Compost | `InteractableCapability` |
| Trade browse | `TradeWithVillagerGoal` (low urgency) |
| Inspect workstation | Walk + look |
| Bring food | Gift |
| Ring bell | Bell goal |
| Fix path blockage | Door / break (griefing gated) |
| Light dark common area | `PlaceTorchGoal` |
| Visit golem / friend | Social goals |
| Wandering Trader browse | Trade adapter (mobile merchant) |
| Loot **permitted** chest | `RaidContainersGoal` + ownership |

**Curiosity examples:** new bell → ring once; baby villager → watch; villager working → stand nearby; villagers sleeping → don't bother (or annoy); cat → follow briefly; empty house → inspect if personality allows.

**Trade arbitrage:** No exploit-hall AI — planner may naturally notice cheap→valuable sequences if math works.

**Wandering Trader:** `temporary mobile Merchant` in `KnownVillager` with short TTL.

---

## Topic: Mineflayer-inspired maximum practical AI architecture

Adapt Mineflayer’s separation of concerns to **entity-bound** classical AI (no LLMs):

```text
Perception (bounded, local)
  → World facts: visible entities, block classes, POI hints, raid status, backpack
  → No chunk-global omniscience unless cheat profile

Decision (WorkDemandPolicy)
  → ProgressGoal / scenario profile (ALLY, TRADER, RAIDER, COWARD)
  → RequirementResolver backward chain
  → Priority vs SPM goals (combat 2, chores 3, …)

Navigation (existing + extensions)
  → Vanilla pathfinder + door goals + Scavenger approach policies

Inventory (8 slots + equipment)
  → SPM backpack + EquipmentEvaluator
  → Trade staging / junk drop policy

Execution (executors)
  → SPM: combat, eat, flee, loot, greet
  → Addon: trade, bell, workstation, raid-post, cure zombie villager

Lifecycle
  → TaskLifecycle per executor; interrupt by combat/flee/death
```

### What maps cleanly from Mineflayer

| Mineflayer module | Entity AI equivalent | Status |
| --- | --- | --- |
| `pathfinder` | `Navigation` + door goals | **FULL** |
| `collectBlock` / dig | Scavenger gather (limited blocks) | **PARTIAL** |
| `craft` | Scavenger `ScavengerCrafting` | **PARTIAL** (small recipe set) |
| `equip` | `EquipmentEvaluator` | **FULL** for combat gear |
| `pvp` | `WeaponAwareAttackGoal` | **FULL** |
| `villager` plugin | **Missing** | **REQUIRES MIXIN** |
| `state` machine | `TaskLifecycle` + saved tickets | **PARTIAL** (design stub) |

### Behaviours: full / partial / not at all

| Class | Examples |
| --- | --- |
| **FULL** (today or thin wrapper) | Melee/ranged combat vs raiders; flee fire; eat; pick up emeralds (`ItemPickupPolicy` valuables); greet villagers; sleep in unclaimed bed; open doors; follow loved player; commanded use |
| **PARTIAL** (addon + policy) | Defend village during active `Raid`; trade selected offers; ring bell when raid active; don’t loot village chests when `ALLY` profile; cure zombie villager (golden apple + weakness); workstation discover |
| **NOT PRACTICAL** gen-1 | Full profession leveling; gossip-optimized reputation; hero-trade min-max; evoker fang dancing; autonomous raid farming with Bad Omen; iron golem army coordination; multi-village economy |

---

## Topic: Vanilla village/raid progression dependency graph

**Status: CONCEPTUAL / LEGACY / SUPERSEDED AS A PHASE MAP.** This graph describes gameplay
prerequisite tiers only. Its old `V0…V5` labels were renamed `L0…L5` on 2026-08-19 because they
collided with the canonical implementation phases. It grants no task order or implementation
authorization. In particular, legacy `Tier V3 — Reputation & discounts` is **not** implementation
phase V3; canonical **V3 always means Village Work** in `Topic: Phased implementation plan`.

Progression for **village ecosystem participation**, not item-name guessing.

### Legacy conceptual tier L0 — World entry

```text
Spawn
  → find village (bed/workstation cluster) OR patrol road
  → safe shelter (bed) — SPM SeekShelterGoal partial
```

### Legacy conceptual tier L1 — Passive coexistence

```text
Coexistence
  requires: not killing villagers (DispositionResolver IGNORE/GREET)
  enables: iron golem neutrality (PlayerMob not Enemy — CONFIRMED design)
  conflict: RaidContainersGoal may steal from village chests (ANTAGONISTIC)
```

### Legacy conceptual tier L2 — Economic entry

```text
Emeralds
  requires: trade goods OR loot (raider drops, chests, mining)
  requires: villager with locked profession + workstation
  requires: MerchantMenu interaction (2 slots trade)
Workstation graph
  librarian ← lectern
  armorer ← blast furnace
  toolsmith ← smithing table
  … (all 1.21.1 professions via POI)
```

### Legacy conceptual tier L3 — Reputation & discounts (`SUPERSEDED` phase label)

```text
Reputation (per villager + gossip)
  requires: successful trades, not hitting villagers
  unlocks: price discounts, priest cures, poppy gossip
Zombie villager cure
  requires: weakness (witch/splash) + golden apple + villager safe spot
```

### Legacy conceptual tier L4 — Raid participation (defender)

```text
Raid active (vanilla Raid instance)
  requires: village center + raiders spawned
  defender loop:
    fight Raider subclasses (SPM combat)
    optional: ring bell (Raider.setCanJoinRaid false when bell)
    protect villagers (target selection policy)
  success: Hero of the Village on nearby Players — NOT PlayerMob today
```

### Legacy conceptual tier L5 — Raid initiation (aggressor)

```text
Bad Omen
  requires: kill RaidCaptain (PatrolLeader banner)
  requires: Player-equivalent effect holder
Enter village
  triggers: Raid.createOrExtendRaid
  waves: pillager → vindicator → evoker → ravager → witch
Endgame loop: farm totems/emeralds/raid loot
```

**PlayerMob gap (`CONFIRMED` / `INFERRED`):** conceptual L4/L5 include player-typed boundaries.
`PlayerMobEntity extends PathfinderMob` — **not** a `Player` (`CONFIRMED` —
`RaiderTargetsPlayerMobMixin` javadoc). Exact hero/omen dispositions live in canonical V6, not in
this legacy graph.

### Consolidated graph

```mermaid
flowchart TD
  subgraph L0["L0 Discovery"]
    EXPLORE[Explore / stumble on village]
    SHELTER[Bed shelter]
    EXPLORE --> SHELTER
  end

  subgraph L1["L1 Coexist"]
    GREET[Greet / ignore villagers]
    NOLOOT[Respect village chests - policy]
    GREET --> NOLOOT
  end

  subgraph L2["L2 Economy"]
    WS[Find workstation]
    PROF[Profession locked]
    TRADE[MerchantMenu trade]
    EMER[Emeralds]
    WS --> PROF --> TRADE --> EMER
  end

  subgraph L3["L3 Reputation"]
    REP[Gossip + reputation]
    CURE[Zombie villager cure]
    DISC[Trade discounts]
    REP --> DISC
    CURE --> REP
  end

  subgraph L4["L4 Defense"]
    RAID_ACTIVE[Raid instance active]
    FIGHT[Fight raiders]
    BELL[Ring bell optional]
    HERO[Hero of the Village]
    RAID_ACTIVE --> FIGHT
    FIGHT --> BELL
    FIGHT --> HERO
  end

  subgraph L5["L5 Aggression"]
    CAPTAIN[Kill patrol captain]
    OMEN[Bad Omen effect]
    TRIGGER[Enter village → start raid]
    WAVES[Clear waves]
    CAPTAIN --> OMEN --> TRIGGER --> WAVES
  end

  L0 --> L1 --> L2 --> L3
  L3 --> L4
  L3 --> L5
```

---

## Topic: Autonomous prerequisite planning

Reuse **D-VP-001** from `RFC-VANILLA-AUTONOMOUS-PROGRESSION.md`:

```text
ProgressGoal (e.g. TRADE_LIBRARIAN_MENDING, DEFEND_VILLAGE, HERO_BUFF)
  → RequirementResolver
  → WorkDemand (EMERALDS, BOOK, WEAPON, SAFE_BED, …)
  → Executor goals
  → TaskLifecycle
```

### Example — Goal: Buy mending book from librarian

```text
BUY_MENDING_BOOK
  requires: emeralds ≥ price, book trade slot, librarian with mending offer
LIBRARIAN
  requires: villager + lectern POI, profession level
EMERALDS
  requires: prior trades OR raid loot OR scavenger mine/trade chain
LECTERN
  requires: discover village library OR place lectern (griefing)
TRADE_EXECUTION
  requires: VillagerTradeAdapter.performTrade (no GUI — D-VR-005)
```

Cross-link **vanilla survival** prerequisites (wood → stone → iron) when trades demand emeralds from ore/equipment — single graph (`RFC-VANILLA-AUTONOMOUS-PROGRESSION.md`).

### Scenario profiles (not one script)

Director admission uses **`VillageScenarioProfile`** (`D-VR-017` semantics **LOCKED**, production
source/default still absent and assigned to V3-A) — same pattern as Opinion `ActivityAdmission`:
profile must be active before utility assigns village/raid executors.

| Profile | Primary goals | SPM conflict to resolve |
| --- | --- | --- |
| `VILLAGE_ALLY` | Defend, trade fairly, no chest theft | Disable/suppress `RaidContainersGoal` near village |
| `VILLAGE_RAIDER` | Loot chests, flee golems | Default SPM behaviour |
| `TRADER` | Demand-owned trade now; discretionary Wealth branch later | Executor exists; V2-W motive is DEFERRED / NON-BLOCKING |
| `RAID_HUNTER` | Bad Omen + wave clear | Needs Player effect bridge |
| `COWARD` | Hide during raid | `SeekShelterGoal` + flee |

---

## Topic: Existing capabilities (reuse map)

### SPM native (`CONFIRMED`)

| Capability | Village/raid use |
| --- | --- |
| `WeaponAwareAttackGoal` | Kill pillagers/vindicators/evokers |
| `BlockArrowsGoal` | Ranged raid defense |
| `RaiderTargetsPlayerMobMixin` | Raider aggro (you are a target) |
| `FriendlyGreetGoal` | Social approach to villagers |
| `DispositionResolver` VILLAGERS | Never proactive attack (`GREET`/`IGNORE`) |
| `SeekShelterGoal` + bed sleep | Night + raid hideout |
| `PlayerMobDoorGoal` | Village pathing |
| `EatFoodGoal` / `HuntForFoodGoal` | Sustain during long raids |
| `CommandedUse` + `FakePlayerSource` | **Prototype** for trade/cure/bell |
| `ModdedRangedAttackGoal` pattern | Template for any item-use lifecycle |
| `CollectFloorItemsGoal` | Raid drops (crossbows, banners, totems) |
| Village companion spawn | Flavour only |

### SPM Scavenger addon (`CODE_CONFIRMED`)

| Capability | Village/raid use |
| --- | --- |
| Torches / shelter | Village lighting at night |
| Gather/craft/smithing | Tools for combat; **not** villager workstations |
| Village perception + memory | Bounded loaded POI discovery, persistent KnownVillage, relationship/return |
| Demand-owned trading | Vanilla + optional Trade Everything opportunity sources through one executor |
| Exploration | Find and return to villages |

### Explicit non-capabilities (`CONFIRMED` NOT FOUND)

- Client `MerchantMenu`; autonomous reputation/discount consumer; V3 work/profile/storage authority
- `BadOmen`, `HeroOfTheVillage` on PlayerMob
- `Raid` wave coordination goals
- Bell activation goal
- Workstation linking / villager brain interaction

---

## Topic: Missing behaviours + integration method

| ID | Behaviour | Feasibility | Integration method | Notes |
| --- | --- | --- | --- | --- |
| VR-1 | Village POI discovery | **IMPLEMENTED / VR-T1A PASS** | `PoiManager` query on `PoiTypeTags.VILLAGE` + `IS_OCCUPIED`, bounded to loaded chunks | No `/locate`; unloaded records excluded from perception |
| VR-2 | Trade execution | **IMPLEMENTED / VR-T2 PASS** for scoped demand-owned routes | `VillagerTradeAdapter` + `TradeWithVillagerGoal` (no fake GUI; `D-VR-053`) | Exact staged transaction, one `notifyTrade`; V2-TE positive source path also runtime-confirmed |
| VR-3 | Offer scoring | **IMPLEMENTED** | Pure `TradeEvaluationPolicy` on snapshots; execution revalidates live quote | Runtime-confirmed only inside recorded VR-T2 routes |
| VR-4 | Reputation awareness | **ALREADY NATIVE (write)** / accessor (read) | Gossip write path is entity-agnostic and running today; read needs a `Villager.gossips` accessor, not a bridge | `Agent_Claude` F2 — `onReputationEventFrom(…, Entity)` has no `Player` check |
| VR-5 | Raid state detection | **PARTIAL** | `level.getRaidAt(pos)` poll | No planner needed |
| VR-6 | Raid defense goal | **PARTIAL** | Addon goal priority 2; target `Raider` only | Reuse combat |
| VR-7 | Friendly fire avoidance | **PARTIAL** | `TargetCategory` + `FeelingLedger` loved villagers | |
| VR-8 | Bell ringing | **FULL** (`INFERRED`) | `RingVillageBellGoal` → `BellBlock.ring(Entity, …)` | Verify mapping at V1 |
| VR-9 | Captain-kill Bad Omen acquisition | **SUPERSEDED (1.21.1)** | Captains outside raids drop Ominous Bottles; do not apply Bad Omen on kill | Replaced by VR-27/29 + omen bridge |
| VR-10 | Raid trigger as initiator | **REQUIRES BRIDGE** | Extend the `ServerPlayer`-gated Bad Omen → Raid Omen and Raid Omen → raid-creation path for exact PlayerMob ownership | Bottle finishing itself is not this gate |
| VR-11 | Hero of the Village | **REQUIRES MIXIN (narrow)** | Widen the single `killer.getType() == EntityType.PLAYER` gate in `Raider#die`; vanilla awards the effect to any `LivingEntity` | `Agent_Claude` F1; the discount half still needs `VillagerTradeAdapter` |
| VR-12 | Suppress village chest loot | **FULL** | Config profile + `RaidContainersGoal` predicate | Policy only |
| VR-13 | Cure zombie villager | **PARTIAL** | `CommandedUse` weakness potion + golden apple | Needs splash timing |
| VR-14 | Workstation craft-for-villager | **NOT PRACTICAL** | Would need villager AI coupling | |
| VR-15 | Iron golem summon (village-driven) | **NOT PRACTICAL** | Village defender spawn is village-driven | Distinct from **manual construction** (VR-28) |
| VR-16 | Advanced village site selection | **PARTIAL** | Factual `FactualVillageUtility` + `SettlementOpinionBias` (D-VR-025); tiers on `KnownVillage` | V4 after V2 traders |
| VR-17 | In-village anchor pick (bell/trader/shelter) | **PARTIAL** | Micro ranking inside `VillageInteractionDirector` | Depends VR-16 |
| VR-18 | Raid task interrupt/resume | **PARTIAL** | `RaidTask` + `TaskLifecycle` snapshot | Reuse `MiningProject` pattern |
| VR-19 | Raid shelter (`EVACUATE`) | **PARTIAL** | SCR-2R5 `SeekShelterGoal` + interior tier | Not new hide goal |
| VR-20 | Ally chest loot suppression | **FULL** | `RaidContainersGoal` predicate + `StorageOwnership` min | V3 blocker; mixin optional |
| VR-21 | Distinct `RaidTask` activity taxonomy | **PARTIAL** | `ActivityClass.VILLAGE_RAID` (not `SCAVENGE_LOOT`) | B-VR-27; avoids SPM naming collision |
| VR-22 | Day/night director arbitration | **PARTIAL** | `VillageDayNightContext` + director priority matrix | V1 read model; V2/V5 admission; D-VR-018 |
| VR-23 | Anchor agreement with `Raid.getCenter()` | **FULL** | Derive `KnownVillage.anchor` from the same POI query the raid system uses | `Agent_Claude` F3; prerequisite for D-VR-010 firing at all |
| VR-24 | Reputation readout (gossip accessor) | **PARTIAL** | Accessor mixin on `Villager.gossips`; no reputation bridge | `Agent_Claude` F2; consumer still `UNVERIFIED` |
| VR-25 | Place opinion at village anchor | **PARTIAL** | `SettlementOpinionBias` at **current** anchor chunk (D-VR-026 **HELD**); no frozen chunk key | V4 |
| VR-26 | Remembered-village candidacy while unloaded | **PARTIAL** | Candidate pool = `MobVillageMemory`; perception refresh separate (D-VR-025) | VR-T4b |
| VR-33 | Village perception scheduler (B2) | **IMPLEMENTED** (V1-D) | `VillagePerceptionScheduler` + observer + service | VR-T1 |
| VR-27 | Ominous Bottle pickup & retention | **PARTIAL** | Strategic pickup value + separately bounded inventory retention | V5; D-VR-027 lock candidate |
| VR-28 | Manual iron golem construction | **PARTIAL** | Defense policy + structured placement, pumpkin last; repair existing first when comparable | V7; D-VR-030 lock candidate |
| VR-29 | Ominous Event intent and bottle consumption | **PARTIAL + REQUIRES BRIDGE** | Cross-domain `OminousEventPolicy`; Village contributes RAID intent; self-use executor plus exact effect bridges | V6; D-VR-028 redesigned |
| VR-30 | Two-step trade chain (sell → buy) | **PARTIAL** | Demand-owned multi-step `AcquisitionPlan`; exact ticket class remains open | V2; D-VR-029 concept lock candidate |
| VR-31 | Hero villager gift receipt | **REQUIRES MIXIN (narrow)** | Widen `GiveGiftToHero` hero-target discovery; reuse SPM floor-item collection | V6; D-VR-031 lock candidate |
| VR-32 | Village founding vs discovery | **PARTIAL** | Founding changes world truth; ordinary V1 perception creates memory; founder history separate | V7; D-VR-032 held/redesign |

### Integration option comparison (village trade case)

| Method | Capability | Reliability | Compatibility | Maintainability | MP safety | API access |
| --- | --- | --- | --- | --- | --- | --- |
| **Datapack** | Trade offer tags for tests only | Low alone | High | High | Safe | No menu |
| **KubeJS** | Rescript offers | Med | Pack-dependent | Low | Safe | None on SPM |
| **Addon + fake player** | Drive `MerchantMenu` | Med–High | Mod-sensitive | Med | **Risk** dupes if sloppy | Item use events |
| **Mixin `MerchantMenu`** | Server-side slot clicks | High | Version-pin | Low | Must validate | Full |
| **SPM source change** | Native trade goal | High | **Blocked licence** | N/A | — | PolyForm |

**Recommendation:** **`VillagerTradeAdapter`** in **`spmscavenger`** (`Agent_ChatGPT`); mixin only for customer/reputation bridge where adapter cannot reach vanilla merchant contract. **Do not** fork SPM.

---

## Topic: Village feature roles (`Agent_ChatGPT`)

**`VillageFeatureRegistry`** — interpret blocks by **role**, not id alone:

| Block / entity | Role |
| --- | --- |
| Lectern | Workstation → librarian trades |
| Composter | Workstation + processing |
| Bell | Alarm / gathering point |
| Bed | Population infrastructure (ownership-aware) |
| Crop blocks | Farm area |
| Iron golem | Protector |

Feeds `VillagePerception` scans and restock heuristics.

---

## Topic: Capability interfaces (addon)

```java
/** Server-side villager trade without client GUI (D-VR-005). */
public interface VillagerTradeAdapter {
    OfferSnapshot inspectOffers(Villager villager, Mob mob);
    Optional<MerchantOffer> evaluateOffers(OfferSnapshot offers, MaterialDemand demand);
    boolean canAfford(Container backpack, MerchantOffer offer);
    TaskLifecycle performTrade(Mob mob, Villager villager, MerchantOffer offer);
}

/** Village context: POI cluster, raid active, ally profile. */
public interface VillageCapability {
    Optional<KnownVillage> scan(Level level, BlockPos origin, int radius);
}

/** Block/entity use: bell, lever, workstation, bed claim. */
public interface InteractableCapability {
    TaskLifecycle useBlock(Mob mob, BlockPos pos, InteractionHand hand);
}

/** Combat target filter for raid defense. */
public interface RaidDefenseCapability {
    boolean shouldEngage(LivingEntity target);
}
```

Legacy alias: `TradeCapability` → `VillagerTradeAdapter` in implementation docs.

---

## Topic: Brainstorm — plausible PlayerMob behaviours (village/raid)

Includes efficient, silly, antagonistic, and emergent — all **technically plausible** if built.

### Social / economic

- Bow to villager after trade (greet reuse)
- Follow wandering trader caravan
- Stare at villager until trade UI opens (player meme behaviour)
- Gift emeralds back to villager (`GiftPolicy` inverse — **PARTIAL**)
- Hoard emeralds without spending (`ItemPickupPolicy` valuables)
- Steal from villager chest while crouching (`RaidContainersGoal` + sneak — **already**)
- Refuse to trade until feeling ≥ love threshold
- Compete with player for same villager trade slot
- Camp outside village; commute for trades

### Raid — defender

- Sprint to bell and ring during raid
- Hide in house and peek through window (shelter + look goal)
- Body-block ravager from villager
- Shoot evoker before fang line
- Accidentally shoot villager → reputation crash
- Flee raid entirely (coward profile)
- Chase raider outside village (leash break)
- Collect totem from evoker drop
- Pick up ominous banner as trophy

### Raid — aggressor / chaos

- Hunt patrols for Bad Omen (needs VR-9)
- Start raid on village that wronged them (feeling hate)
- Join raid **against** village that looted them
- Die to raid; drop gear; player retrieves

### Environmental / emergent

- Light village with scavenger torches
- Break workstation accidentally while gathering
- Plant flowers in village square (gift fetch flower reuse)
- Sleep in villager bed → anger (`SeekShelter` must check ownership)
- Open all doors during raid (door goal)
- Sit in boat at village dock (no vehicle AI — **NOT PRACTICAL**)
- Milk cow in village pen (`CommandedUse`)
- Feed animals (`INFERRED` — not autonomous today)

### Cross-system combos

- Trade for bread → eat during raid
- Loot raid chest → craft arrows → defend
- Hero buff + scavenger torch placement spree
- Reincarnated player mob returns to defend home village (`PlayerReincarnation` snapshot + village anchor)

### Brainstorm continuation (`Agent_Cursor`, 2026-08-14)

Early candidates — promoted to stable topics above or deferred below:

| ID | Idea | Disposition | Notes |
| --- | --- | --- | --- |
| B-VR-09 | **Advanced village site selection** | **→ Topic** | Settlement tiers + `VillageSiteScore`; D-VR-009 |
| B-VR-10 | **Raid interrupt via TaskLifecycle** | **→ Topic** | MiningProject precedent; D-VR-010 |
| B-VR-11 | **EVACUATE reuses SCR-2R5 shelter** | **→ Topic** | No `HideInHouseGoal`; D-VR-011 |
| B-VR-12 | **Iron golem as moving village centroid** | **DEFERRED** | Golem position hints anchor when bell unknown |
| B-VR-13 | **Witness resentment on villager AOE** | **CONSENSUS risk** | `DispositionResolver.witnessedAttackDelta` if mob hurts villager |
| B-VR-14 | **Autonomous Ominous Bottle use for raid intent** | **PRODUCT DECISION** | 1.21 captains drop bottles; decide whether RAID intent may target `HOME_VILLAGE` (default no) |
| B-VR-15 | **Multi-bell villages — pick alarm bell** | **→ VR-17** | Nearest reachable + raider LOS |
| B-VR-16 | **Wandering trader as ephemeral `KnownVillager`** | **Already in RFC** | TTL merchant row |
| B-VR-17 | **GAO SOCIAL browse village** | **DEFERRED** | Opinion discretionary `SOCIAL` + `FriendlyGreetGoal` executor |
| B-VR-18 | **Post-raid arrow craft loop** | **CROSS-RFC** | Raid drops → `ScavengerCrafting` → resume defend |
| B-VR-19 | **Mojmap pin bell + hero APIs** | **→ Topic** | `attemptToRing` / `addHeroOfTheVillage`; D-VR-013/014 |
| B-VR-20 | **Trade demand via `WorkDemandPolicy` facade** | **→ Topic** | Reconcile `MaterialDemandPolicy` name; emerald `consumerKey` |
| B-VR-21 | **ShelterThreatPolicy ejects cowards on raider aggro** | **→ Topic** | `Enemy` + `NEARBY_HOSTILE`; EVACUATE self-limiting |
| B-VR-22 | **Ally gate extends shelter-hold mixin pattern** | **→ VR-20** | Profile-based `RaidContainersGoal` block, not shelter-only |
| B-VR-23 | **`RaidDefenseCapability` Raider-first target filter** | **PROMOTE** | Collateral villager hits → golem anger (B-VR-13) |
| B-VR-24 | **`VillageScenarioProfile` admission** | **PROMOTE** | ALLY/TRADER/RAIDER/COWARD gates director like ActivityAdmission |
| B-VR-25 | **Witness villager hurt → tier demote AVOID** | **PROMOTE** | Operationalizes B-VR-13 via `KnownVillage` affinity |
| B-VR-26 | **Bell ring = block interaction path** | **CONSENSUS** | Path to bell + `attemptToRing`; not melee bell block |
| B-VR-27 | **`RaidTask` distinct activity class** | **PROMOTE** | Avoid `MoveHolderClassifier` SCAVENGE_LOOT collision with SPM `RaidContainersGoal` naming |
| B-VR-28 | **VR-T1 datapack village fixture** | **PROMOTE** | `test-datapacks/phase-village-raid/` — bell, beds, villager preset |
| B-VR-29 | **Day/night director arbitration** | **→ Topic** | `VillageDayNightContext` + dusk raid vs shelter vs trade; D-VR-018 |

### Brainstorm continuation 3 (`Agent_Claude`, 2026-08-14)

Deduplicated against every row above, the rejected list, the deferred table, and locked decisions.

| ID | Idea | Class | Disposition | Notes |
| --- | --- | --- | --- | --- |
| B-VR-30 | **Hero credit by widening one `EntityType` check** | `REFINEMENT` of B-VR-19 / VR-11 | **→ Topic** | `Raider#die` is the only gate; `Raid#tick` awards to any `LivingEntity`. D-VR-020 |
| B-VR-31 | **Villager reputation is already native — measure it, don't build it** | `CONFLICT` with VR-4 as written | **→ Topic** | Gossip write path is entity-agnostic **today**; B-VR-13 is an existing consequence, not a feature. D-VR-021 |
| B-VR-32 | **Village = `PoiTypeTags.VILLAGE` + `IS_OCCUPIED`, not a bed-cluster heuristic** | `ALTERNATIVE` to D-VR-009 detection | **→ Topic; contests D-VR-009** | Makes `KnownVillage.anchor` agree with `Raid.getCenter()` by construction. D-VR-019 |
| B-VR-33 | **Bound every POI query to loaded chunks** | `NEW` (safety) | **PROMOTE — must-not-happen test** | `PoiManager` reads persisted sections for unloaded chunks; naive use is omniscience |
| B-VR-34 | **Hero discount arithmetic lives in `VillagerTradeAdapter`** | `NEW` (dependency) | **DEFERRED V6** | `updateSpecialPrices(Player)` player-typed; not V2 MVP (`D-VR-060`) |
| B-VR-35 | **Phase order is inverted at the raid end** | `NEW` (planning) | **PRODUCT DECISION** | Winning credit is nearly free; *starting* a raid is the hardest item in the RFC. Consider pulling hero credit forward, pushing initiation out of V6 |
| B-VR-36 | **Reputation without a consumer is bookkeeping, not memory** | `NEW` (honesty) | **DEFERRED — probe before V4/V6 consumer work; not a V3 gate** | Golem anger + trade discount are both expected player-typed; do not describe as “villagers remember you” until a consumer exists |
| B-VR-37 | **Village facts vs Opinion preference split** | User architecture | **→ Topic** | Director ranks legal candidates; Opinion soft-bias only. D-VR-025 |
| B-VR-38 | **KnownVillage → Place opinion at anchor chunk** | User architecture | **→ D-VR-026 (HELD)** | Place@**current** anchor geography; no settlement-ID chunk key |
| B-VR-39 | **SETTLEMENT Opinion subject** | User investigation | **DEFERRED** | Reopen when Place@current-anchor cannot express settlement-persistent pref across anchor moves |
| B-VR-40 | **Stress → prefer familiar village anchor** | `NEW` | **PROMOTE → Opinion package** | Lives inside `SettlementOpinionBias`, not village director |
| B-VR-41 | **Remembered pool vs perception refresh** | User pre-lock review | **→ D-VR-025** | Unloaded villages stay selectable |
| B-VR-42 | **Frozen `placeOpinionChunkKey`** | User pre-lock review | **REJECTED** | Turns Place into settlement-ID store; User amendment D-VR-026 |
| B-VR-43 | **`SettlementOpinionBias` facade** | User pre-lock review | **→ D-VR-025** | Village consumes ±15 bias only |
| B-VR-44 | **Ominous Bottle strategic pickup + bounded retention** | User request + review | **→ D-VR-027 lock candidate** | Pickup value is not indefinite retention; five amplifier variants can compete for an 8-slot backpack |
| B-VR-45 | **Cross-domain Ominous Event intent** | User request + review | **→ D-VR-028 redesigned** | Village contributes RAID intent; bottle use is not globally village-owned |
| B-VR-46 | **Demand-owned sell resources → buy target plan** | User request + review | **→ D-VR-029 `LOCK RECOMMENDED`** | `TradeChainPlan` in V2 contract |
| B-VR-47 | **Repair or construct iron golem manually** | User request + review | **→ D-VR-030 concept lock candidate** | Repair-first when comparable; vanilla final-pumpkin path preserves player-created flag |
| B-VR-48 | **Receive Hero-of-the-Village villager gifts** | User request + review | **→ D-VR-031 lock candidate** | Bridge target recognition only; reuse host floor pickup |
| B-VR-49 | **Found village by changing world truth** | User request + review | **→ D-VR-032 held/redesign** | Ordinary perception creates `KnownVillage`; founding history is observer-relative |

### Brainstorm continuation 4 (`Agent_Codex`, 2026-08-14)

Deduplicated against B-VR-09…49, V1-R1…R3, the deferred table, and the current source. These ideas
advance the existing **V1 perception-driver** frontier; they do not add V2/V5 behaviour.

| ID | Idea | Class | Disposition | Why it matters |
| --- | --- | --- | --- | --- |
| B-VR-50 | **Individual flagless village observer** | `NEW` | **PROMOTE → V1-D / D-VR-033** | Each PlayerMob records only its own physical observations; no MOVE/LOOK authority and no shared omniscient result cache |
| B-VR-51 | **Hybrid dirty + heartbeat cadence** | `REFINEMENT` | **PROMOTE → D-VR-033** | Chunk transition prevents a fast crossing from falling between slow scans; heartbeat catches POI claims/changes while stationary |
| B-VR-52 | **Strict per-server-tick POI-query budget** | `PERFORMANCE_PATTERN` | **PROMOTE → D-VR-033** | Staggering smooths normal load but is not a hard burst bound; a one-query safety budget makes 1/10/50/100-mob cost falsifiable |
| B-VR-53 | **Partial-observation recheck pressure** | `REFINEMENT` | **DEFER until V1-R4 ships** | `coverage < 100%` → earlier re-observation (legitimate chunk-availability signal) |
| B-VR-55 | **`withheldPoiCount` epistemic leak** | User review | **→ V1-R4 `ACCEPTED`** | Dual pipeline; coverage independent of `getInRange` |
| B-VR-56 | **Dirty request ≠ prompt service** | User review | **→ D-VR-033 P1** | Conditional Must happen; VR-T1 measures traversal vs latency separately |
| B-VR-57 | **Queue admission fairness under saturation** | User review | **→ D-VR-033 P1** | Prefer fair admission; consider ticking-mob-bound queue; not `MAX_QUEUE >= 100` alone |
| B-VR-59 | **Admitted-count supersede regression** | User review | **REJECTED** | V1-R4 draft would freeze anchor on village shrink — use coverage only |
| B-VR-58 | **POI query cost vs loaded admission** | User review | **→ VR-T1b** | `getInRange` may scan persisted unloaded sections; measure edge cases |
| B-VR-60 | **Ticking-mob-bound queue + round-robin admission** | P1 closure | **→ D-VR-033** | Normal capacity = one pending slot per ticking observer; emergency cap only |
| B-VR-54 | **Explicit social transfer of village knowledge** | `NEW` | **DEFERRED** | Useful later, but silently copying one mob's observation to companions would violate individual perception; any transfer needs an observable social event |
| B-VR-61 | **SettlementRelationship mob layer** | User architecture | **→ Topic (V1.5)** | Attachment/history separate from `KnownVillage`; D-VR-035 |
| B-VR-62 | **V1.5 before V2 Trading** | User phase reorder | **→ D-VR-034 PROPOSED** | Social/return/manners before `VillagerTradeAdapter` |
| B-VR-63 | **Accumulating attachment (no one-visit max)** | User design | **→ D-VR-036 PROPOSED** | Personality-modulated familiarity |
| B-VR-64 | **Return / commute-to-home goal** | User behavior | **→ V1.5-C** (`D-VR-038`) | `SettlementReturnPolicy` + `ExploringGoal` seed, not priority-3 goal |
| B-VR-65 | **Village-aware FriendlyGreet weighting** | User + B-VR-17 | **→ V1.5** | GAO SOCIAL + settlement context |
| B-VR-66 | **Camp near home (shelter bias)** | `NEW` | **V1.5 stretch** | SeekShelter within bounds of home anchor |
| B-VR-67 | **V1.5 without full VillageInteractionDirector** | `CONSENSUS` | **ACCEPTED** | Policy + ExploringGoal sufficient gen-1 |
| B-VR-68 | **`SettlementBoundsPolicy` @ 64** | continuation 5 | **→ D-VR-040** | Presence ≠ identity ≠ raid radii |
| B-VR-69 | **`SettlementRelationshipService`** | continuation 5 | **→ D-VR-041** | Single write path |
| B-VR-70 | **Forced-heading commute expedition** | continuation 5 | **→ D-VR-043** | Reuse `ExploringGoal` companion pattern |
| B-VR-71 | **Auto-home on HIGH familiarity** | continuation 5 | **→ D-VR-042** | Config default off; debug home first |

**Rejected alternatives:** put the 64-block POI query in the existing 10-tick
`ExplorationActivityGoal` observer (unnecessarily couples expensive perception to control-plane
bookkeeping); cache one village observation globally and hand it to nearby mobs (cheap, but creates
knowledge without an individual observation); create a shared result cache/deduplicator before
profiling proves it necessary. Scheduling individual requests centrally remains a distinct option.

### Brainstorm continuation 4 — V2 Trading (`Agent_Claude`, 2026-08-15)

Deduplicated against the V2 implementation contract, its Scope (out) list, and every prior B-VR row.
The contract is `LOCKED` and thorough; these are additions to it, not a re-plan.

| ID | Idea | Class | Disposition | Notes |
| --- | --- | --- | --- | --- |
| B-VR-90 | **`assemble()` / `getCostA()`, never `getResult()` / `getBaseCostA()`** | `NEW` (evidence) | **→ V2-A** | Result accessor returns the live field while the cost accessor copies; aliasing corrupts the villager's offer and persists |
| B-VR-91 | **Multi-slot debit; `take` is menu-shaped** | `NEW` (evidence) | **→ V2-A** | `take` shrinks only the two stacks passed; an 8-slot backpack pays 20 wheat as 16+4. Under-pays silently on the first bulk offer |
| B-VR-92 | **`SellExpendabilityPolicy` (FuelExpendability pattern)** | `REFINEMENT` of V2-D | **→ D-VR-058** | Disposable quantity math; permission before preference; SPM may still eat |
| B-VR-93 | **Any persisted V2 per-mob state registers in `PerMobSavedData.forgetAll`** | `NEW` (Gate RET-1e) | **→ V2-G** | The rule added 2026-08-15 after the same defect appeared in three stores; `PerMobRemovalContractTest` already enforces it, so this is a build failure rather than a review item |
| B-VR-94 | Villager level-up invalidates a cached offer index | `REJECTED` (checked) | **no action** | `updateTrades` appends via `addOffersFromItemListings`; indices are stable. Recorded so it is not re-raised |

**Rejected (dedup):** `ExploreForVillageGoal` (director + perception); villager profession brain clone
(`D-VR-004`); client menu bot for trade (`D-VR-005`).

### Brainstorm continuation 6 — V2 Trading (`Agent_Cursor`, 2026-08-15)

Deduplicated against B-VR-09…71, D-VR-005/007/029/034, V1.5 contract, pinned 1.21.1 trade APIs, and
three `src/` probes (`VillagerTradeAdapter`, `TradeWithVillagerGoal`, `TradeEvaluationPolicy` — all
**NOT FOUND**). Advances **V2** only; does not authorize implementation.

| ID | Idea | Class | Disposition | Notes |
| --- | --- | --- | --- | --- |
| B-VR-72 | **Mixin-free core trade path** | `REFINEMENT` of VR-2 | **→ D-VR-053** | `notifyTrade` sufficient for gen-1; amends **REQUIRES MIXIN** label |
| B-VR-73 | **`ActivityClass.VILLAGE_TRADE`** | `NEW` (safety) | **→ D-VR-054** | Prevents greet/trade taxonomy collision (V1.5 lesson) |
| B-VR-74 | **Demand-only trade admission** | `CONSENSUS` | **→ D-VR-055** | No browse-without-demand in V2 MVP |
| B-VR-75 | **Familiar-anchor villager pick** | `REFINEMENT` | **→ V2 contract** | Soft profession match; HOME/familiarity rank |
| B-VR-76 | **Atomic trade rollback** | `NEW` (safety) | **→ V2-A** | Partial backpack insert → restore cost stacks |
| B-VR-77 | **`onTradeEpisode` familiarity** | `REFINEMENT` of B-VR-61 | **→ D-VR-057** | Parallel to social credit, not shared counter |
| B-VR-78 | **Minimal `KnownVillager` row** | `REFINEMENT` of deferred table | **→ D-VR-056** | Not full B-VR-16 wandering-trader TTL |
| B-VR-79 | **Trade inspector readout** | `NEW` | **→ V2-I optional** | O-panel line: `consumerKey`, anchor, villager id, `TradeBlockedReason`; no resurrected debug command |
| B-VR-80 | **Night defer helper only** | `REFINEMENT` of B-VR-29 | **→ D-VR-059** | Full director matrix still V5 |
| B-VR-81 | **Sell expendability vs smelt/eat race** | `NEW` (safety) | **→ D-VR-058** | Replan when disposable qty drops; no SPM mixin |
| B-VR-82 | **`consumerKey` trade namespace** | `REFINEMENT` of B-VR-20 | **→ V2-C, amended by D-VR-065** | `spmscavenger:trade_chain/<id>`; no ownerless `wealth/emerald` loop |
| B-VR-83 | **Post-trade greet bow** | `COSMETIC` | **DEFERRED V3** | Reuse greet after `SUCCESS` — not VR-T2 gate |
| B-VR-84 | **Trade interrupt snapshot** | `REFINEMENT` of D-VR-010 | **DEFERRED V5** | V2 records `TradeChainPlan` expiry only |
| B-VR-85 | **Emerald open-loop guard** | `NEW` (closed loop) | **→ V2-C** | `wealth/emerald` consumer must pair with buy step or tool demand |

**Strongest objection:** priority-3 `TradeWithVillagerGoal` still competes with `RaidContainersGoal`
and gather when demand is active — same band as smelt today. **Mitigation:** `TradeDemandGate` +
`WorkDemandPolicy.select()` single winner; trade ticket expires; night defer returns `BLOCKED`.

**Viable alternative:** Opinion discretionary TRADE intent (GAO-owned) instead of P3 executor.
**Rejected for V2 MVP** — adds director surface before first hop is proven; revisit if P3 arbitration
churns in VR-T2.

**MAIBS V2 — expanded (`Agent_Cursor`, 2026-08-15):**

| Minute | Predicted observable (God fixture, familiar taiga village) | Failure mode |
| --- | --- | --- |
| 0–2 | `WorkDemandPolicy` emits carrot→emerald demand; mob paths toward settlement | No demand registered; wanders explore |
| 2–4 | Picks farmer over unrelated villager at same anchor | Hardcoded librarian bias |
| 4–6 | Faces villager; trade sound; backpack −carrots +emeralds | GUI packet; double `increaseUses` dupe |
| 6–8 | `onTradeEpisode` familiarity bump; inspector shows `VILLAGE_TRADE` | Credited as `SOCIAL_REFLEX` greet |
| 8–12 | Chain step 2: paths to librarian; buys book | Sells last bread; smelt consumes sell stock |
| 12–15 | Chain completes; demand cleared; resumes explore/commute | Emerald hoard with no consumer |

**Must not happen:** greet claim window starts during active trade face phase; `VillagePerceptionObserver`
misclassified (already fixed V1.5); trade at night while villagers sleep without `BLOCKED` defer.

### Brainstorm continuation 7 — V2 causal transaction and scheduler review (`Agent_Codex`, 2026-08-15)

**Contribution type:** `REVIEW / BRAINSTORM_IN_RFC / MAIBS_STATIC`. Deduplicated against B-VR-72…85,
the V2 contract, current Scavenger source, SPM v0.86.0 source, and the pinned mapped 1.21.1 jar.

| ID | Idea | Class | Disposition | Evidence / consequence |
| --- | --- | --- | --- | --- |
| B-VR-86 | **Stage the complete backpack transaction before mutation** | `SAFETY / DATA` | **→ D-VR-061** | `MerchantOffer#take` mutates only two supplied stacks; it is not an atomic multi-slot container operation |
| B-VR-87 | **Demand identity outlives offer identity** | `LIFECYCLE` | **→ D-VR-062** | Restock, price, uses, customer, and entity availability can change; offer index is evidence for one attempt only |
| B-VR-88 | **Human customer exclusion** | `COMPATIBILITY` | **→ D-VR-061** | `Villager#updateSpecialPrices(Player)` mutates offers for the live player session; `stopTrading()` later resets them |
| B-VR-89 | **No persistent `KnownVillager` in gen-1** | `SCOPE / RETENTION` | **D-VR-056 HELD** | Current picker can re-resolve loaded villagers from `KnownVillage`; no V2 consumer justifies durable UUID/offer memory |
| B-VR-90 | **Normalize familiarity per trade visit/chain** | `CAUSAL LEARNING` | **→ D-VR-063** | Ten clicks in one visit must not teach ten independent village relationships |
| B-VR-91 | **Finish bounded incumbent P3 episode, then trade** | `SCHEDULER` | **→ D-VR-064 + VR-T2e** | SPM `RaidContainersGoal` is P3 MOVE+LOOK, but has bounded phases and a 20-tick post-visit cooldown |
| B-VR-92 | **Two-mob commit-time revalidation before reservation machinery** | `MULTI_MOB` | **GEN-1 ACCEPT** | Server-thread sequencing can safely reject the second stale offer; persistent villager claims add RET-1 cost before runtime proves crowding harmful |
| B-VR-93 | **Concrete external consumer, not generic emerald appetite** | `CLOSED LOOP` | **→ D-VR-065** | Existing `WorkDemandPolicy` has real tool/progression consumers; an unowned emerald target is another autonomous hoarding loop |

#### Options reviewed

| Choice | Option A | Option B | Recommendation / switch evidence |
| --- | --- | --- | --- |
| Backpack mutation | Shrink live slots, insert result, rollback on failure | Stage copied slots, apply one validated slot delta | **B.** Switch only if a proven transactional container API provides the same component-aware semantics |
| P3 conflict | Raise trade to P2 | Keep P3; single demand owner; incumbent finishes; runtime-gate starvation | **Keep P3.** P2 can outrank/contend with command, follow, and shelter. Add a narrow exact-trade host hook only if VR-T2e fails |
| Trader coordination | Persistent per-villager reservation | Commit-time revalidation, bounded replan | **Revalidation for gen-1.** Add a short claim only if multi-mob runtime shows crowding/thrash, with RET-1 cleanup |
| Chain persistence | Serialize villager/offer steps | Close neutrally on load and rebuild from external demand | **Rebuild.** Persist only later if a real consumer cannot reconstruct intent without loss |

#### Predicted Weird Behaviors (MAIBS)

| Weird behavior | Classification | Required response |
| --- | --- | --- |
| Mob pauses behind one chest-raiding episode before trading | Acceptable gen-1 stepping stone | Must remain bounded; VR-T2e falsifies starvation assumption |
| Two mobs walk to the same last-use villager; second leaves/replans | Acceptable but visually imperfect | No duplicate final use; consider short claim only after runtime evidence |
| P1 greeting interrupts approach and the mob later returns | Acceptable host social behavior | Preserve demand, discard old path, no premature trade credit |
| Offer price changes while walking and mob pays stale price | Architecture defect | Exact live revalidation must abort/replan before any slot mutation |
| Full backpack loses payment but receives no result | Release blocker | Staged post-payment insertion simulation must prevent commit |
| One rapid trade burst instantly maxes settlement familiarity | Learning defect | One bounded episode terminal per visit/chain |

#### Temporal prediction

```text
T0      external tool/progression demand selects TRADE
T+10    live familiar-anchor villagers/offers resolved; path begins
T+60    greet/door or bounded current P3 work may temporarily own MOVE+LOOK
T+200   trade resumes with fresh path and current offer; staged transaction commits once
T+1200  demand completed, explicitly blocked/expired, or replanned — never endless emerald work
```

**MAIBS verdict:** `BEHAVIORALLY_PLAUSIBLE` only with D-VR-061…065 and VR-T2e…T2h. Runtime remains
`UNVERIFIED`; no Minecraft launch occurred. The strongest remaining uncertainty is whether the
host P3 cooldown reliably exposes an admission window in the integrated GoalSelector ordering.

### Brainstorm continuation 8 — V2 demand wiring and bilateral guards (`Agent_Cursor`, 2026-08-15)

**Contribution type:** `BRAINSTORM_IN_RFC / CODE_AUDIT / MAIBS_STATIC`. Deduplicated against
B-VR-72…93, continuation 7 verdict, V1.5 `SettlementReturnPolicy` / `SocialGreetClaimWindow`, and
three `src/` probes (`ActivityClass.VILLAGE_TRADE`, `TradeSessionClaimWindow`, `WorkDemandPolicy.TRADE`
— all **NOT FOUND**).

| ID | Idea | Class | Disposition | Evidence / consequence |
| --- | --- | --- | --- | --- |
| B-VR-94 | **Extend `WorkDemandPolicy` without rename** | `INTEGRATION` | **→ D-VR-015 LOCKED** | Production has `MaterialDemand` + `consumerKey` today (`torch_chain`, diamond tool); trade registers as another `select()` candidate, not a parallel emerald goal |
| B-VR-95 | **`WorkType.TRADE_CHAIN` or `consumerKey` namespace gate** | `INTEGRATION` | **→ D-VR-015** | `spmscavenger:trade_chain/*` distinguishes trade tickets from smelt batches; `TradeDemandGate` admits only when the winning demand is trade-owned |
| B-VR-96 | **`TradeSettlementPicker` — not `commuteTarget()`** | `REFINEMENT` | **REJECTED literal reuse → D-VR-070** | `commuteTarget()` = return commute (HOME first); trade needs offer-fit **before** familiarity rank |
| B-VR-97 | **`VillagerTradeAvailability` predicate pack** | `SAFETY` | **→ D-VR-066** | Pure checks: baby, zombified, sleeping, no useful offer, out of `SettlementBoundsPolicy`, `getTradingPlayer() != null`, unreachable — each maps to `TradeBlockedReason` |
| B-VR-98 | **`TradeSessionClaimWindow` (greet symmetry)** | `SAFETY` | **→ D-VR-067** | `SocialGreetClaimWindow` already defers greet until Opinion binds; trade FACE/EXECUTE must defer greet on the **same villager** to avoid VR-T1.5c taxonomy replay |
| B-VR-99 | **Component-predicate payment matrix** | `DATA` | **→ VR-T2j stretch** | Staged slot-delta must honor `ItemCost` component tests; static matrix mandatory; runtime enchanted fixture optional |
| B-VR-100 | **Discretionary trade browse without demand** | `COSMETIC` | **DEFERRED V3+** | Same family as wandering-trader TTL (`B-VR-16`); demand-only admission (`D-VR-055`) stays gen-1 |

#### Options reviewed

| Choice | Option A | Option B | Recommendation / switch evidence |
| --- | --- | --- | --- |
| Demand owner | New `MaterialDemandPolicy` rename | Extend `WorkDemandPolicy.select()` | **B.** `MaterialDemandPolicy` still **NOT FOUND** in Scavenger `src`; RFC-TOOL-TIER-UPGRADES `D-TTU-017` is planning vocabulary, not a compile blocker |
| Trade anchor | New `TradeAnchorPolicy` | Reuse `SettlementReturnPolicy.commuteTarget` | **TradeSettlementPicker (`D-VR-070`).** Reuse ranking *concepts*, not return function |
| Greet during trade | Only `canUse()` false on trade goal | Bilateral claim windows | **Bilateral.** Trade blocks greet on same villager during face phase; existing greet guards still block trade start |
| Blocked defer | Silent `canUse()` false | `TradeBlockedReason` + `TaskLifecycle.BLOCKED` | **Explicit reason.** Inspector (`V2-I`) and VR-T2i need a stable enum, not log-only narrative |

#### Predicted Weird Behaviors (continuation 8)

| Weird behavior | Classification | Required response |
| --- | --- | --- |
| Mob paths to village at dusk; all villagers sleep | Acceptable defer | `BLOCKED` + `ALL_VILLAGERS_SLEEPING`; shelter may preempt — not a trade spin |
| Greet pulse fires while mob faces villager for trade | Taxonomy defect (VR-T1.5c replay) | `TradeSessionClaimWindow` must defer greet on bound villager |
| Trade demand wins `select()` but no villager route | Closed-loop defect | Feasibility filter before `select()` (`D-VR-015`); must not suppress smelt/craft |
| Trade demand wins `select()` but smelt still runs | Closed-loop defect | `TradeDemandGate` + `SellExpendabilityPolicy` (`D-VR-058`) while chain active |
| Inspector shows `UNKNOWN_ACTIVE` during trade | Taxonomy defect | `VILLAGE_TRADE` pinned before goal ships (`D-VR-054`) |

**MAIBS verdict (continuation 8):** `BEHAVIORALLY_PLAUSIBLE` with D-VR-015/066/067 added to the
continuation 7 contract. **V2 design frontier is closed** for implementation planning; runtime
(`VR-T2e`…`T2j`) remains `UNVERIFIED` until explicit launch approval.

### Brainstorm continuation 9 — Trade Everything optional economy source (`User` + `Agent_Codex`, 2026-08-15)

**Contribution type:** `RESEARCH / REVIEW / COMPATIBILITY DESIGN / MAIBS_STATIC`. New evidence does
not reopen vanilla V2; it creates an optional post-core source behind the existing transaction.

| ID | Idea | Class | Disposition | Evidence / consequence |
| --- | --- | --- | --- | --- |
| B-VR-101 | **Trade Everything synthetic offer is session-scoped** | `COMPATIBILITY FACT` | **→ D-VR-068** | `setTradingPlayer` mixin inserts/removes index 0 and filters it from save NBT; core no-menu inspection cannot see it |
| B-VR-102 | **`TradeOpportunitySource` discovery seam** | `ARCHITECTURE` | **→ D-VR-068** | Known second source justifies a narrow interface; central adapter still owns execution/atomicity |
| B-VR-103 | **Prefer official quote API; bounded internal bridge only as fallback** | `UPDATE RESILIENCE` | **→ D-VR-068** | Public API exposes value, not complete quote; `OfferQuoter` is public Java but internal package |
| B-VR-104 | **Disposition before valuation** | `INVENTORY SAFETY` | **LOCKED IN V2-TE** | A high item value cannot authorize selling primary gear, survival stock, chain inputs, progression reserves, or unique items |
| B-VR-105 | **Score actual payout, not assumed emeralds** | `CAUSAL ECONOMY` | **LOCKED IN V2-TE** | Trade Everything selects a villager buy item and can pay commodities other than emeralds |

**Before:** downstream emerald/tool demand sees only persistent vanilla offers; Trade Everything's
temporary row is invisible without a human session. **After:** once vanilla V2 is proven, an optional
source quotes eligible surplus exactly, the same demand policy ranks its real payout, and the same
staged transaction commits it—without opening a GUI or changing scheduler authority.

**Predicted weirdness:** a valuable but protected diamond ranks economically high (`ARCHITECTURE_DEFECT`
if sold); payout is an irrelevant commodity (`ACCEPTABLE` only when scorer rejects it); internal API
changes (`RUNTIME_QUESTION`, must disable compat without crashing); multiple cheap stacks cause repeated
quotes (`PERFORMANCE_RISK`, bounded to admitted demand + eight backpack slots).

**MAIBS verdict:** `BEHAVIORALLY_PLAUSIBLE` as a separate V2-TE follow-up with VR-T2k/l. No movement,
priority, or GoalSelector change is introduced. **Update 2026-08-19:** the positive autonomous path is
`RUNTIME_CONFIRMED` by `V2-DEF-003c-R1`; absent/incompatible binary behavior (`VR-T2l`) remains
`UNVERIFIED`, **DEFERRED / NON-BLOCKING**, and does not reopen V2.

### Brainstorm continuation 10 — User peer review pre-task-47 (`User` + `Agent_Cursor`, 2026-08-15)

**Contribution type:** `REVIEW / CONTRACT TIGHTENING`. No scope expansion. Confirms prior architecture;
pins four transaction/arbitration traps before implementation.

| Finding | Status after review | Decision |
| --- | --- | --- |
| V2 overall architecture | **SOUND** | unchanged |
| `D-VR-067` trade↔greet guard | **SOURCE-CONFIRMED mandatory** | SPM `FriendlyGreetGoal` MOVE+LOOK + villager GREET collision |
| `D-VR-061` staged transaction | **SOUND** — tighten lifetime | `D-VR-072` commit-instant `SlotDelta` |
| `D-VR-058` protected inputs | **SEMANTIC FIX** | `SellExpendabilityPolicy`; SPM may eat; replan |
| Two-cost payment allocator | **NEEDS explicit rule** | `D-VR-071` joint partition |
| `D-VR-015` trade arbitration | **NEEDS feasibility-first** | TRADE cannot win without route evidence |
| `B-VR-96` `commuteTarget` reuse | **REJECT literal** | `D-VR-070` `TradeSettlementPicker` |
| Trade Everything separation | **SOUND** | `D-VR-068` / `069` unchanged |
| `KnownVillager` deferral | **SOUND** | `D-VR-056` HELD |
| Vanilla-only VR-T2 baseline | **SOUND** | `D-VR-069` unchanged |
| Early RFC doc debt (mixin/menu/KnownVillager) | **SWEPT** | Scenario B, mixin table, KnownVillager header |

**Verdict:** V2 design **ready for task-47** after these contract amendments. Superseded by
continuation 11 pass-2 repairs.

### Brainstorm continuation 11 — User peer review pass 2 (`User` + `Agent_Cursor`, 2026-08-15)

**Contribution type:** `REVIEW / CONTRACT REPAIR`. Four findings; no scope expansion.

| ID | Severity | Finding | Repair |
| --- | --- | --- | --- |
| P1 | **BLOCKING** | `D-VR-071` stack exclusivity too strict — falsely rejects valid stack partition (2 emeralds → A=1, B=1) | **Amended:** invariant is **no double-counting of item counts**; stacks may partition across costs |
| P1 | **BLOCKING** | Locked order `V2-F` before `V2-E` — classifier cannot pin goal that does not exist | **`D-VR-073`:** E introduces goal class; F co-lands or immediately follows |
| P2 | Minor | `D-VR-067` missing server-stop cleanup | **`shutdownServerState()`** — mirror `SocialGreetClaimWindow` |
| P3 | Doc debt | `D-VR-066` `protected-input conflict` stale vs `SellExpendabilityPolicy` | **`INSUFFICIENT_DISPOSABLE_QUANTITY`**; reject `PROTECTED_INPUT` enum |

**Verdict:** **LOCK-CLEAN for task-47** — architecture PASS; contract defects repaired.

---

## Topic: Ominous Bottle pickup & inventory knowledge (`User`)

**Author:** `User` (topic request, 2026-08-14)
**Status:** `LOCK RECOMMENDED` — strategic pickup value and bounded retention are separate; extends
inventory policy, not a new GoalSelector mega-goal.

### Problem (observable)

Raid and trial loot can drop **`Ominous Bottle`** items (`OminousBottleItem` — `CONFIRMED` in 1.21.1 Mojmap).
Today SPM `ItemPickupPolicy` / Scavenger gather paths prioritize ammo, food, and a curated valuables
set (diamond/emerald/ingot tier — `GatherResourcesGoal.java` comment). Ominous bottles are **not**
first-class inventory knowledge: mob may ignore them, treat them as junk, or consume backpack slots
without a retention policy.

### Architectural rule (hard)

**`OminousBottlePolicy`** is a **pure inventory-knowledge + pickup-ranking** module — not a drink
executor and not a raid planner. Consumption timing is a **separate topic** (below).

```text
Raid/trial drop or floor item
        ↓
ItemPickupPolicy / CollectFloorItemsGoal eligibility
        ↓
OminousBottleValue.rank(stack, mob, demand)
        ↓
InventoryRetentionPolicy.keep / replace / discard
        ↓
InventoryKnowledge slot (tagged ominous_bottle, amplifier, source)
        ↓
(later) cross-domain OminousEventPolicy
```

### `OminousBottlePolicy` (`PROPOSED`)

| Responsibility | Detail |
| --- | --- |
| **Pickup value** | Strategic/high when a valid Ominous Event intent can use the bottle; this is not permission to retain every bottle |
| **Retention budget** | Separately bounded by profile, amplifier value, demand, and backpack pressure; replacement/discard is legal when the budget is exceeded |
| **Amplifier awareness** | Read bottle amplifier component (loot function `SetOminousBottleAmplifierFunction` exists in mappings) |
| **Slot budget** | Cap retained stacks/count (exact number is a later product/tuning decision) — five component variants may occupy separate stacks in an 8-slot backpack |
| **Ally conflict** | `VILLAGE_ALLY` default: retain but **do not** auto-drink near `HOME_VILLAGE` (consumption topic) |

**Must happen:** mob picks up ominous bottle from raid floor when space available.
**Must not happen:** bottle picked up then immediately dropped by unrelated junk policy.

**NOT FOUND** (3 probes): `OminousBottle`, `ominous_bottle` in Scavenger `src` and SPM `src`.

### Phased delivery

| Phase | Scope |
| --- | --- |
| **V5** | Pickup rank + `InventoryKnowledge` record |
| **V6** | Profile gates tied to raid-initiation policy |

**Decision:** **D-VR-027** (`LOCK RECOMMENDED`, amended): strategic pickup classification and
retention are two policies. Exact count/amplifier replacement rules remain implementation-time
product decisions.

---

## Topic: Ominous Event intent — when to consume a bottle (`User` + peer review)

**Author:** `User` (topic request, 2026-08-14)
**Status:** `RESEARCHING / REDESIGNED` — cross-domain owner identified; vanilla/SPM source audit
completed; exact bridge and autonomous self-use executor remain unimplemented.

### Problem (observable)

In 1.21.1 Bad Omen is the entry point to multiple Ominous Events, including raids and Ominous Trials.
Village/Raid therefore must not globally own bottle consumption. A human chooses the intended event,
site and abort policy before drinking; autonomous use needs explicit admission, not "drink on pickup."

### Cross-domain contract (`PROPOSED`)

```text
Ominous Bottle
      ↓
OminousEventPolicy
      ├── RAID intent  ← Village/Raid contributes this candidate
      └── TRIAL intent ← future Trial Chamber subsystem
      ↓
capability + site + inventory + safety admission
      ↓
consume bottle
```

| Profile / state | Default |
| --- | --- |
| `RAID_HUNTER` | Emit RAID intent only when a target village/farm site and abort plan are selected; never consume merely because a bottle exists |
| `VILLAGE_ALLY` | **Refuse** autonomous drink gen-1 unless product overrides B-VR-14 |
| `VILLAGE_RAIDER` | May drink near disliked village (`AVOID` tier) — **PRODUCT DECISION** |
| Active raid at home | **Block** drink (already in raid) |
| Low health / fleeing | **Defer** drink |

### Source-audited execution boundary (`CODE_CONFIRMED`)

- `OminousBottleItem#finishUsingItem` applies Bad Omen and consumes the stack for any
  `LivingEntity`; only advancement/stat side effects are `ServerPlayer`-specific. Do **not** duplicate
  effect application or add a mixin at this layer.
- `OminousBottleItem#use` is `Player`-typed, and SPM `CommandedUse` performs entity/block-targeted use;
  it does not prove a targetless self-drink lifecycle. Add or reuse a bounded autonomous self-use
  executor that reaches vanilla `finishUsingItem` normally.
- `BadOmenMobEffect#applyEffectTick` is hard-gated to `ServerPlayer` before village detection and
  Raid Omen creation. `RaidOmenMobEffect#applyEffectTick` is also hard-gated and calls
  `Raids#createOrExtendRaid(ServerPlayer, pos)`. Those are the actual compatibility bridge seams.

**Negative executor probes:** (1) SPM `CommandedUse.perform` does nothing when both entity and block
targets are null; (2) SPM `EatFoodGoal#finishEating` manually applies food semantics and never calls
the held item's `finishUsingItem`; (3) Scavenger/SPM source searches found no Ominous Bottle-specific
self-use executor. Therefore “SPM already knows item use” is not proof of autonomous bottle drinking.

**Primary references:** [official Java 1.21 notes](https://feedback.minecraft.net/hc/en-us/articles/27547857163917-Minecraft-Java-Edition-1-21-Tricky-Trials),
[Yarn OminousBottleItem API](https://maven.fabricmc.net/docs/yarn-1.21%2Bbuild.9/net/minecraft/item/OminousBottleItem.html),
plus method bodies from the project's pinned named 1.21.1 jar.

### Raid-Omen commitment window

```text
Drink bottle → Bad Omen → enter intended village → Raid Omen (600 ticks)
                                             ├── COMMIT: remain eligible until raid starts
                                             └── ABORT: command/danger/wrong site → leave or milk
```

The intent must retain target village, source bottle/amplifier, authority owner, commit/abort reason,
and expiry. A command, critical health, or wrong village during the 30-second window must not become
an accidental raid.

**Must happen:** an admitted `RAID_HUNTER` consumes one chosen bottle, receives Bad Omen, enters the
intended village, obtains Raid Omen, and either deliberately commits or aborts during the 600-tick
window.
**Must not happen:** Village code consumes a bottle reserved for a Trial intent; an ally mob drinks
while casually crossing home; a failed/aborted intent starts a raid anyway.

**Strongest objection:** the vanilla raid lifecycle stores player-specific omen state and raid
ownership. Widening only the first `instanceof ServerPlayer` can create a half-working lifecycle;
the bridge must preserve exact PlayerMob intent/position ownership across both effects without
reimplementing raid rules.

**Decision:** **D-VR-028** (`REDESIGNED / SOURCE AUDIT COMPLETE`, not locked): use a cross-domain
`OminousEventPolicy`; Village/Raid contributes RAID intent and owns raid-specific commitment, not
global bottle consumption. Implementation waits on a complete bridge design.

---

## Topic: Two-step villager trade chains — sell for emeralds, then buy (`User`)

**Author:** `User` (topic request, 2026-08-14)
**Status:** `PROPOSED` — extends `VillagerTradeAdapter` + `RequirementResolver`; not a second trade GUI.

### Problem (observable)

Human trade progression is rarely one hop:

```text
Want: mending book
Have: carrots, coal, wheat
Path: sell surplus → emeralds → buy book
```

RFC already sketches reverse chain in `VillagerTradeAdapter` topic (`BUY_MENDING_BOOK` example) but
does not treat **sell-then-buy** as a **first-class planner contract** with ticket lifecycle.

### Behavioural contract (`CONCEPT LOCK RECOMMENDED`)

```text
ProgressGoal: ACQUIRE_ITEM(book_mending)
  → RequirementResolver
  → sub-goal: WEALTH_EMERALDS(deficit)
  → TradeEvaluationPolicy:
        phase SELL — pick best input→emerald offer (farm surplus, mine loot)
        phase BUY  — pick emerald→target offer at known trader
  → TaskLifecycle per phase (interruptible by D-VR-010 raid)
```

| Required plan field | Purpose |
| --- | --- |
| Originating demand + consumer | Prevent autonomous arbitrage from becoming its own objective |
| Required output + disposable-quantity bounds | Preserve demanded output; sell only above `SellExpendabilityPolicy` reserves (`D-VR-058`) |
| Budget + expiration | Bound emerald/input spending and stale plans |
| Current step + revalidation evidence | Recheck villager, offer, inventory and whether the output was acquired elsewhere before every step |
| `consumerKey` | `"trade:chain:<goalId>"` on `WorkDemandPolicy.MaterialDemand` (D-VR-015) |
| Backpack staging | Reserve slots for sell inputs **and** expected emerald output — 8-slot risk |

`TradeChainTicket` is one viable representation, but not locked. If the existing progression RFC's
generic `AcquisitionPlan` can express dependent SELL → BUY steps without losing interruption,
reservation or revalidation semantics, reuse it rather than introducing a trade-only planner type.

**Must happen:** mob with carrots but no emeralds completes sell then buy in one directed visit or two commutes.
**Must not happen:** buys book offer without emerald budget; sells last food leaving starvation;
continues stale step 2 after the offer changes or the demanded output was acquired elsewhere; trades
for profit without an external demand.

**Cross-RFC:** `RFC-VANILLA-AUTONOMOUS-PROGRESSION` `RequirementResolver`; farm surplus from V3 harvest.

**Decision:** **D-VR-029** (`CONCEPT LOCK RECOMMENDED`): demand-owned, revalidated multi-step
acquisition is required. The exact `TradeChainTicket` abstraction remains `PROPOSED`.

---

## Topic: Manual iron golem construction (`User`)

**Author:** `User` (topic request, 2026-08-14)
**Status:** `CONCEPT LOCK RECOMMENDED` — **distinct from VR-15** (village-driven golem spawn is
`NOT PRACTICAL`); vanilla creator semantics source-audited.

### Problem (observable)

A human **builds** an iron golem: place iron block pattern + carved pumpkin. This is player parity
**construction**, not waiting for village mechanics to spawn one. RFC golem section only covers
**relationship** with existing golems (neutral, assist, door repath).

### Feasibility (`CODE_CONFIRMED` trigger; executor `PROPOSED`)

| Step | Integration |
| --- | --- |
| Acquire 4 iron blocks + pumpkin | `WorkDemandPolicy` / gather / smelt chain |
| Place pattern | Scavenger block placement (`PlaceTorchGoal` pattern) + griefing gate |
| Trigger spawn | Place the carved pumpkin/jack-o'-lantern **last** through the normal block-placement lifecycle; `CarvedPumpkinBlock#trySpawnGolem` recognizes the iron pattern and unconditionally calls `IronGolem#setPlayerCreated(true)` |
| Post-spawn | Existing golem relationship table — hang near / assist vs raid |

Before building, `VillageDefensePolicy` must compare repairing a damaged existing golem with spending
four iron blocks on a new one. Prefer `REPAIR_EXISTING` when it provides comparable defense value;
an active home raid may authorize construction mandatorily, while safe surplus improvement may be
soft-ranked by Opinion.

**Must happen:** mob with materials constructs a player-created golem near `HOME_VILLAGE` when defense
utility justifies it, placing the pumpkin last and verifying the entity actually spawned.
**Must not happen:** griefing pumpkins on player builds; raw-spawning/bypassing the vanilla pattern;
building a new golem while a cheap repair satisfies the same defense demand; infinite iron sink.

**Strongest objection:** 8-slot backpack + 5 blocks + pathing alignment is fiddly; failure modes
(pattern off-by-one) look stupid without retry policy.

**Phased delivery:** **V7** (advanced community) or late **V5** if raid defense demands it.

**Decision:** **D-VR-030** (`CONCEPT LOCK RECOMMENDED`): defense policy selects repair/build and a
structured construction executor uses vanilla placement semantics. Exact site planner remains open.

---

## Topic: Hero-of-the-Village villager gifts (`User`)

**Author:** `User` (topic request, 2026-08-14)
**Status:** `LOCK RECOMMENDED` (amended after source audit) — bridge villager recipient recognition;
reuse host pickup; depends on hero effect credit (D-VR-020 / F1).

### Problem (observable)

After raid victory, villagers **throw gifts** at the hero. Source audit confirms
`GiveGiftToHero#getNearestTargetableHero` reads `NEAREST_VISIBLE_PLAYER` and returns `Player`; its
start/tick/range lifecycle is Player-typed, while `throwGift(Villager, LivingEntity)` is general after
a recipient is selected. The missing seam is therefore hero **recognition/targeting**, not gift
generation and not a new item-collection system.

### Design (`PROPOSED`)

```text
Raid VICTORY + hero effect active on mob
        ↓
Narrow GiveGiftToHero compatibility bridge:
villager recognizes eligible PlayerMob hero
        ↓
vanilla villager throws normal gift
        ↓
SPM CollectFloorItemsGoal
        ↓
InventoryKnowledge / demand re-evaluation
```

A bounded post-raid `AFTERMATH` state may keep the hero in the village long enough to be noticed, but
it does not own floor-item collection and must not duplicate SPM's pickup executor.

**Must happen:** defender mob with hero credit receives thrown items within ~1 minute of victory.
**Must not happen:** gifts despawn while mob raids chests (`VR-20` ally gate).

**Compatibility boundary:** narrow optional mixin/adapter on `GiveGiftToHero` hero lookup and its
Player-typed lifecycle, preserving vanilla gift tables and throw behavior. Do not introduce a
`HeroGiftCollectionGoal`.

**Phased delivery:** **V6** with hero credit; static tests mock `throwGift` eligibility.

**Decision:** **D-VR-031** (`LOCK RECOMMENDED`, amended): bridge recipient recognition; reuse SPM
pickup; optional `AFTERMATH` presence hold only.

---

## Topic: Village founding vs discovery (`User`)

**Author:** `User` (topic request, 2026-08-14)
**Status:** `HELD / REDESIGN` — founding is a valid future project, but it must not mint settlement
memory or encode observer history inside `KnownVillage`.

### Problem (observable)

V1 (`D-VR-019`) answers: *"What village is here?"* via `PoiManager` / occupied village POI.
Humans also **found** settlements: place beds, workstations, bells; transport/cure villagers; create
a new POI cluster that vanilla later recognizes. RFC treats villages as **discovered objects**, not
**authored projects**.

### Founding vs discovery (`REDESIGNED` ownership)

| Concern | Owner |
| --- | --- | --- |
| Construct beds/workstations, transport/cure villagers | `VillageFoundingProject` / generic progression executors |
| Determine whether world now satisfies village truth | Existing `VillagePerception` + D-VR-019 predicate |
| Create/update remembered settlement | Existing `VillageMemorySavedData.record`; no founding shortcut |
| Remember “I helped found this place” | Future observer-relative `SettlementHistory` / relationship record, not `KnownVillage` identity |

**Founding pipeline (high level):**

```text
VillageFoundingIntent (site selected — cave base, plains camp, etc.)
        ↓
RequirementResolver: beds, workstation, bell, housing, optional villager (cure/transport)
        ↓
Place blocks (griefing + StorageOwnership)
        ↓
Wait for POI registration / villager assignment
        ↓
ordinary VillagePerception.observe
        ↓
ordinary KnownVillage record/update
        ↓
optional founder relationship evidence recorded separately
```

**Must happen:** after the project changes the world enough to satisfy vanilla-compatible village
conditions, the exact ordinary perception path recognizes the settlement.
**Must not happen:** `VillageFoundingProject` inserts a fake `KnownVillage`, assigns `HOME_VILLAGE`, or
records founder credit before the D-VR-019 predicate succeeds.

**Strongest objection:** overlaps `RFC-VANILLA-AUTONOMOUS-PROGRESSION` base-building; village RFC should
own **POI/villager/economy** founding semantics only, delegating generic shelter to SCR-2.

**Phased delivery:** **V7** primary. No V4 `FOUNDED` source tag; a later history model needs its own
evidence and decision.

**Decision:** **D-VR-032** (`HELD / REDESIGN`): founding project concept retained; `KnownVillage.origin`
rejected; ordinary perception is the only memory-creation authority.

---

## Topic: D-VR-033 implementation review — V1-D authorization blockers (`User`)

**Author:** `User` (implementation review, 2026-08-14)  
**Status:** `REVIEW` — architecture direction (B2 bounded UUID lanes + global budget) **accepted**;
**V1-D implementation authorization BLOCKED** until P0 + P1 items below are resolved

**P0 — CLOSED** (V1-R4 1.9.5 APPROVED). **P1 scheduler contracts — CLOSED** in RFC (see V1 perception
driver topic). Remaining blocker: **explicit V1-D implementation authorization**.

### P0 — `withheldPoiCount` violates the perception boundary (`CODE_CONFIRMED`)

**Claim in code:** `VillagePerception` javadoc — *"every record passes `withinPerception` before it can
influence anything."*

**Actual behaviour (`VillagePerception.java` L82–100):**

```text
PoiManager.getInRange(...) → materialize all records in radius
        ↓
per record: loaded? → admit : withheld++
        ↓
ObservationQuality.completeness = admitted / (admitted + withheld)
        ↓
supersedes() → anchor replacement decision
```

Unloaded POI **positions** are excluded from the anchor, but their **count** still alters cognition.

**Epistemic leak example:** two PlayerMobs with identical loaded-chunk views:

| Mob | admitted | withheld (unloaded persisted) | completeness |
| --- | --- | --- | --- |
| A | 10 | 0 | 1.0 |
| B | 10 | 20 | 0.333 |

Same legitimate perception; different confidence and different `supersedes()` outcomes because the
server knows about different unperceived POIs. That violates:

```text
WORLD TRUTH → perception boundary → observation → knowledge
```

Hidden world truth must not jump the boundary through `withheld`.

**Not:** "the mob knows where hidden beds are."  
**But:** "hidden world truth alters the mob's confidence and therefore its memory."

**Required fix (V1-R4):** remove hidden POI information from cognition. Confidence = **`PerceptionCoverage`**
(loaded chunk columns in the 64-block footprint ÷ total columns in footprint). The mob legitimately
knows whether surrounding chunks are available to perception; it must not know what hidden POIs those
chunks contain.

```text
ObservationQuality.coverage = PerceptionCoverage only
supersedes: coverage first, then tick on equality
admittedPoiCount: isSettlement + diagnostics — NOT supersede ordering
withheld: removed from persisted quality / supersede (telemetry optional)
```

**Must-not-happen tests:**

1. Two observers at same position, identical loaded views → identical supersede-relevant quality
   regardless of nearby unloaded persisted POI storage.
2. coverage 100%/20 POIs → coverage 100%/16 POIs (village shrank) → **newer replaces** (no freeze).
3. coverage 100%/10 POIs → coverage 45%/18 POIs → **keep old** (worse window wins over more POIs).

**Why this blocks V1-D:** the driver makes the dormant leak **live** for every ticking PlayerMob.
**V1-R4 design is now `ACCEPTED`** — implement when separately authorized; V1-D remains blocked until
R4 lands and D-VR-033 scheduler P1s close.

### P1 — chunk-transition dirtying ≠ prompt observation (`CONTRACT`)

B2 correctly queues `(dimension, UUID)` and observes at **current** position on service — not stale
position. Good.

But dirtying only creates a **request**; service may arrive **later** after the mob has left the
64-block village radius:

```text
T0   enter village → dirty queued
T1…80 global budget busy; mob walks through
T81  scheduler services UUID → observe(currentPosition) → empty → village missed
```

At one query/server tick, 100 pending UUIDs can need ~100 ticks before a given mob is serviced again.
Chunk dirtying **reduces** traversal misses; it does **not** eliminate them. Prior RFC wording that
treated dirtying as fixing the architecture defect (not merely tuning) **overpromised**.

**Amendment (scheduler contract):** bounded service latency is **conditional**, not unconditional:

> **If** a PlayerMob remains within a perceivable occupied-village observation region until its
> pending request is serviced, **then** it **must** record that observation within the scheduler's
> bounded service latency.

A finite service bound does **not** guarantee the mob stays inside the 64-block radius for the whole
wait. Fast traversal + backlog can still produce empty observe-at-exit — that is a **cadence/budget
evidence** problem (VR-T1), not a violation of an impossible architectural guarantee.

**VR-T1 separately measures:** whether normal compact-village traversal duration is long enough
relative to actual service latency at 1 / 10 / 50 / 100 mobs. If mobs regularly traverse entire
villages before service, that is evidence the budget/cadence is too conservative — not proof the
contract is broken.

### P1 — queue saturation reintroduces unfair **admission** (`CONTRACT`)

B2's FIFO/round-robin fairness applies **inside** the queue. When the queue is full, new requests are
refused while mobs retain a cheap pending marker and retry later. **Who wins the next open slot** is
undefined if retries follow GoalSelector poll order — recreating B1's emergent contention at the
queue entrance.

**Amendment required before lock (User, 2026-08-14):**

- **Prefer explicit fair admission** over `MAX_QUEUE >= 100` as a semantic magic ceiling — 101 mobs
  brings starvation back unless 100 is declared the **supported concurrency ceiling** for expansion.
- **Alternative under consideration:** queue size structurally bounded by **currently ticking
  PlayerMobs** (one pending UUID each); lifecycle cleanup + stale-entry validation provide RET-1
  discipline; a numerical emergency cap (like `MAX_TRACKED_MOBS`) exists for abnormal cases but is not
  normal admission behaviour.

Tests must prove **no starvation at admission**, not only eventual service once admitted. Do **not**
re-lock D-VR-033 on `MAX_QUEUE >= 100` alone.

### Performance note — loaded filter ≠ cheap query (`UNVERIFIED`)

`hasChunk` bounds **knowledge admission** but `PoiManager.getInRange` may still materialize persisted
unloaded sections before filtering. VR-T1b must measure:

- loaded dense village
- unloaded persisted village edge
- fresh/cold POI storage
- 1 / 10 / 50 / 100 mobs

Do not equate "no chunk generation" with "cheap query."

### D-VR-027…032 review snapshot (same session)

| Decision | Verdict |
| --- | --- |
| **D-VR-027** | Lockable after P2 title/wording — pickup **value** ≠ unconditional HIGH retention |
| **D-VR-028** | Correctly not locked — `OminousEventPolicy` RAID/TRIAL split; bridge open |
| **D-VR-029** | Concept lock good — demand-owned SELL→BUY; ticket class flexible |
| **D-VR-030** | Concept lock good — `setPlayerCreated(true)` is vanilla classification, not creator ownership |
| **D-VR-031** | Lock candidate — recipient bridge + host `CollectFloorItemsGoal` |
| **D-VR-032** | Correctly held — founding project depends on future construction capabilities |
| **D-VR-033** | Return to **REVIEW** — block V1-D until P0/P1 resolved |

**SettlementTier decomposition gate:** no objection — defer enum churn until V4/V5 consumers force it.

### V1-R4 — `PerceptionCoverage` replaces withheld in cognition (`APPROVED` — 1.9.5)

**Status:** **`APPROVED`** (User review of pushed 1.9.5, 2026-08-14)

| Closure | Status |
| --- | --- |
| P0 epistemic leak (`withheld` → cognition) | **CLOSED** |
| Admitted-count supersede regression (B-VR-59) | **CLOSED** |
| Runtime village perception (VR-T1A) | **PASS** — autonomous discovery, observer→scheduler→service→record, same-village identity, save/reload, cross-dimension persistence **CONFIRMED** (Bob, User 2026-08-14) |
| Runtime multi-mob / POI cost (VR-T1b) | **DEFERRED** — 10/50/100-mob profiling + B-VR-58; performance validation backlog, not a V1 gate |

**User review (`CONFIRMED` against pushed 1.9.5):** `PerceptionCoverage.compute()` derives coverage solely
from the 64-block chunk-column footprint via `hasChunk`, independent of POI results;
`VillagePerception.observe()` computes coverage before `PoiManager`; `ObservationQuality.supersedes()`
delegates exclusively to coverage (admitted POI count = diagnostics/settlement evidence only); comparison
uses exact integer cross-multiplication; pre-R4 quality and pre-R1 `poiCount` rows migrate optimistically
to full coverage; `VillageMemorySavedData.record()` persists coverage-based quality. Regression tests pin
100%/20→100%/16 replaces and 100%/10→45%/18 does not replace. Structural contract enforces
`PerceptionCoverage.compute()` before `getInRange()` and `withinPerception()` on raw POI results.

**P2 test hardening (deferred — not 1.9.5):** extend
`mustNotHappen_theBoundaryCheckCanLoadAChunk()` structural invariant to **both** `VillagePerception.java`
and `PerceptionCoverage.java` — forbid `getChunk`, `getChunkAt`, `addRegionTicket`, `forceLoad`, and
chunk generate/load calls. Current `PerceptionCoverage` is clean (`hasChunk` only); guard against future
regression when Pipeline A lives outside `VillagePerception`.

**Cosmetic (deferred):** `KnownVillage` Javadoc duplicated word — not a release blocker.

**Next gate:** ~~V1-D~~ **DONE** → ~~VR-T1A~~ **PASS** → ~~task-46 / 1.11.0~~ **DONE** → ~~VR-T1.5a–c~~ **CLOSED** → **V2 Trading**.

**Status (2026-08-14 continuation):** P1 contracts **CLOSED** in RFC — see `Topic: V1 perception driver`
scheduler contracts section. D-VR-033 → **`LOCK RECOMMENDED`**. **Awaiting V1-D implementation authorization.**

**Fixes both prior failures:**

| Failure | Mechanism | V1-R4 repair |
| --- | --- | --- |
| **A** — epistemic leak | hidden unloaded POIs → `withheld` → `completeness()` | coverage from **loaded chunk columns only** |
| **B** — quantity freeze | admitted POI count → supersede | coverage only; 100%/20→100%/16 **newer wins** |

**Locked epistemic model:**

```text
loaded observation opportunity → PerceptionCoverage → quality → supersede
```

**Implementation lock (part of acceptance):** coverage is computed **independently** of POI records
returned by `getInRange()`. No property of an unloaded `PoiRecord` — count, position, type, or
existence — may contribute to coverage.

```text
PIPELINE A — coverage (no PoiManager)
origin + radius 64
        ↓
derive intersecting chunk columns
        ↓
hasChunk() each
        ↓
PerceptionCoverage(loadedColumns, totalColumns)

PIPELINE B — settlement facts (separate)
PoiManager.getInRange(...)
        ↓
admit only records in loaded chunks
        ↓
anchor + admittedPoiCount
```

**`supersedes()`:** cross-multiply `loadedColumns`/`totalColumns`; equal → newer tick replaces.
**`admittedPoiCount`:** `isSettlement()` + diagnostics only — **not** supersede ordering.

**Required tests (must ship with R4):**

1. Same position, identical loaded views, different nearby unloaded persisted POI storage → identical
   supersede-relevant coverage.
2. 100% / 20 POIs → 100% / 16 POIs (village shrank) → **newer replaces**.
3. 100% / 10 POIs → 45% / 18 POIs → **keep old** (worse window beats more POIs).
4. Coverage computation does not call or depend on `getInRange()` result cardinality for unloaded chunks.

**NBT migration:** pre-R4 quality → optimistic **full coverage** (`loadedColumns == totalColumns`);
do not reinterpret saved `withheld`. A partial post-upgrade glance cannot degrade an old anchor; a
later full-coverage observation can still replace via equal-coverage-newer-wins.

**Deferred (post-R4):** B-VR-53 — `coverage < 100%` may justify earlier re-observation.

**Rejected:** withheld in supersede; admitted-count supersede (B-VR-59); float coverage in NBT;
deriving coverage from `getInRange()` withheld/unloaded record counts.

**Next gate:** ~~V1-R4~~ **DONE (1.9.5)** → ~~scheduler P1 closure~~ **DONE (RFC)** → **authorize V1-D (1.10.0)** → VR-T1 runtime.

---

## Topic: V1 perception driver and observation budget (`Agent_Codex`)

**Status:** `LOCK RECOMMENDED` — B2 direction accepted; **V1-D implementation authorization pending**
(V1-R4 **`APPROVED`** / 1.9.5; scheduler P1 contracts closed below)

**Goal:** connect the implemented V1 perception/identity substrate to real PlayerMobs without giving
the observer scheduler authority, manufacturing shared knowledge, or running a 64-block POI query
per mob per tick.

### Current implementation

`CODE_CONFIRMED`:

- `VillagePerception.observe(ServerLevel, BlockPos)` performs one radius-64
  `PoiTypeTags.VILLAGE` + `IS_OCCUPIED` query and filters every result through the locked loaded-chunk
  boundary.
- `VillageMemorySavedData.record(UUID, Observation, tick)` is non-allocating for an empty
  observation and persists a real settlement observation.
- `ExplorationActivityGoal` demonstrates a flagless observer installed on each PlayerMob and a
  deterministic ten-tick phase, but its cadence owns mining/readiness/Opinion control-plane work.
- `PhasedScanClock` already handles GoalSelector polling that misses an exact modulo tick.

Production caller audit:

| Probe | Result |
| --- | --- |
| `VillagePerception.observe` under `src/main/java` | **NOT FOUND** |
| production call to `VillageMemorySavedData.record` | **NOT FOUND** |
| village perception/driver registration in `SpmScavenger.install` | **NOT FOUND** (only lifecycle cleanup references village memory) |

Therefore V1 currently has a tested perception function, not a mob perception lifecycle. A build or
unit test cannot prove that a PlayerMob ever learns a village.

### Candidate designs

| Option | Design | Benefit | Strongest objection / failure mode |
| --- | --- | --- | --- |
| **A — extend `ExplorationActivityGoal`** | Run village observation from the existing 10-tick flagless observer | Fewest classes and no new Goal registration | Couples a large POI query to unrelated control-plane cadence; changing one cadence changes mining, affect, and village cost together |
| **B1 — per-mob flagless driver + budget gate** | Each observer marks itself pending and contends for a strict server-tick permit | Preserves individual knowledge, no movement authority, small gen-1 surface | Fairness is emergent from contention; collision/starvation proof is harder |
| **B2 — per-level bounded UUID queue (peer-review alternative)** | Per-mob cheap eligibility enqueues UUID; level scheduler grants a fixed number of POI queries/tick; result is written only to that mob | Explicit FIFO/round-robin fairness and a hard global cap without sharing facts | Long-lived queue must satisfy RET-1 and reconcile unload/death/dimension changes; more lifecycle code |
| **C — central result cache/deduplication** | Queue mobs and reuse spatial village query results | Lowest repeated-query potential | Risks shared omniscience/stale facts; not justified without profile evidence |

**Recommended resolution: B2.** A is rejected and C remains deferred. B1's permit race needs a
fairness mechanism to prove eventual service; once that mechanism remembers pending UUIDs and orders
them, it has effectively recreated a less explicit queue. B2 makes the ownership honest. The queue
schedules *when* a specific mob may observe, but never reuses or broadcasts *what* another mob
observed.

### Proposed V1-D contract

```text
entity-ticking PlayerMob
        ↓
flagless eligibility observer
        ↓
observation requested by either:
  - changed chunk since last successful observation
  - bounded heartbeat elapsed
        ↓
enqueue deduplicated (dimension, mob UUID) request
        ↓
server scheduler services per-level lanes round-robin
under one global POI-query budget
        ↓
VillagePerception.observe(level, mob.blockPosition())
        ↓
settlement?
  no  → no memory row created
  yes → VillageMemorySavedData.record(mob UUID, observation, gameTime)
```

Provisional gen-1 tuning for implementation review:

- heartbeat: **200 ticks** (ten-second maximum stationary refresh target);
- movement dirtying: **chunk transition**, coalesced rather than queried immediately;
- retry/debounce floor: **20 ticks** after a denied/dirty request;
- hard burst budget: **one POI query per server tick** across the server.

These are proposed safety/timing values, not performance claims or copied vanilla constants. They
must remain named constants (or configuration only if players benefit from tuning), and VR-T1 must
measure actual query frequency/cost. The hard budget limits bursts; it does not prove that one query
per tick is cheap. A denied permit leaves the observation pending, so successful contenders move to
their next heartbeat and the backlog can drain instead of one entity monopolising every tick.

### Ownership and lifecycle

| Concern | Owner / invariant |
| --- | --- |
| Scheduler flags | Empty; observation may run beside combat/work but cannot move or look |
| Knowledge | Per-mob `VillageMemorySavedData`; no companion/global result sharing |
| Query budget | Server-scoped hard cap; no observation cache or cross-mob result reuse |
| Queue retention | Key = `(dimension, mob UUID)`; at most one pending request/key; hard configured cap; no entity references; dequeue on service; remove on unload/death/dimension transition; clear on server stop |
| Queue saturation | Refuse duplicate/over-cap insertion without clearing the mob's cheap pending marker; bounded retry allows later admission rather than losing perception forever |
| Cross-level fairness | Per-level lanes serviced round-robin under one **server-global** cap; multiple dimensions must not multiply the budget or starve a quieter level |
| Entity unload/death | Observer object dies with entity; persistent memory follows V1-R2/R3 semantics |
| Dimension change | New observer instance; UUID memory remains; first eligible local observation is dirty |
| Addon disabled | No new query or memory write; existing persisted memory remains unchanged |
| Empty result | Does not create/erase memory; absence at one moment is not evidence the old settlement ceased to exist |

### Predicted behaviour (MAIBS pre-implementation)

| Layer | Result |
| --- | --- |
| Intended behaviour | A PlayerMob notices villages it physically passes through and remembers them |
| Implemented mechanism (planned) | Flagless, budgeted POI observation at chunk changes/heartbeat; only loaded POIs admitted |
| Predicted observable behaviour | No visible goal/readout/movement change; village memory appears within a bounded delay after entry |
| Failure/weirdness | Small village crossed between requests; partial boundary observation updates anchor; many mobs create a pending backlog; teleport/dimension change dirties immediately |
| Confidence | `CODE_CONFIRMED` substrate; `GAME_MECHANICS_INFERRED` cadence; runtime `UNVERIFIED` |

**Strongest objection:** a 200-tick heartbeat alone can miss a fast traversal through a small village.
That is an architecture defect, not tuning; chunk-transition dirtying **requests** prompt observation
but does **not** guarantee prompt **service** under a global budget (see implementation review P1).

**Alternative:** query on every chunk transition without a heartbeat. It lowers traversal misses but
fails when villagers claim POIs around a stationary mob, and multiple mobs crossing one boundary can
burst together. Rejected in favour of the hybrid.

### Acceptance and falsification

**Must happen (conditional):** **if** a ticking PlayerMob remains within a perceivable occupied-village
observation region until its pending request is serviced, it records a `KnownVillage` within the
scheduler's bounded service latency, without acquiring MOVE/LOOK or changing its current goal.

**Must not happen:** disabled addon, empty observation, unloaded-only POIs, or another mob's
observation creates village memory for this mob; aggregate POI queries exceed the configured
per-server-tick budget; **hidden POI counts or admitted POI quantity alter supersede ordering**
(V1-R4 `PerceptionCoverage` only).

**Runtime falsifiers:** (1) mob **stays in village** through service but records nothing — contract
violation; (2) 100 ticking PlayerMobs produce a same-tick query burst above the budget; (3) one denied
mob starves at **admission** or remains pending indefinitely once admitted; (4) observation changes
objective labels or interrupts movement; (5) an empty follow-up erases a valid remembered village;
(6) two mobs at same position, identical loaded views → identical supersede-relevant coverage (V1-R4);
(7) coverage 100%/20 POIs → coverage 100%/16 POIs newer → anchor **updates** (no quantity freeze).

**VR-T1 (empirical, separate):** measure whether normal traversal through compact villages completes
before service at 1/10/50/100 mobs — informs budget tuning, not the conditional Must happen above.

**Locked architecture (pending review closure):** B2 owns pending requests; cheap per-mob eligibility
owns pending marker. Tests must prove deduplication, global cap, round-robin service fairness,
**explicit fair queue admission** (not `MAX_QUEUE >= 100` alone), eventual service, lifecycle cleanup.
Consider queue bound = ticking PlayerMobs (one UUID each) + emergency cap. 200/20/1 values provisional.

### Scheduler contracts — P1 closure (`LOCK RECOMMENDED`, 2026-08-14)

User implementation review identified two P1 gaps after V1-R4 approval. **P0 is closed.** These
amendments close the P1 **contract** gaps so D-VR-033 can re-lock; runtime VR-T1 still measures
whether tuning is adequate.

#### B-VR-56 — conditional service latency (`LOCK RECOMMENDED`)

**Amended Must happen (replaces any “prompt observation” wording):**

> If a ticking PlayerMob remains within a perceivable occupied-village observation region until its
> pending request is serviced, it **must** record that observation within the scheduler's bounded
> service latency.

**Must not happen:** claiming a finite global budget *guarantees* observation for mobs that traverse
a village faster than backlog drain.

**Documented service bound (gen-1):**

```text
worstCaseTicks ≈ (pendingQueueDepth / GLOBAL_QUERY_BUDGET_PER_TICK) + HEARTBEAT_DEBOUNCE
```

With `GLOBAL_QUERY_BUDGET_PER_TICK = 1` and queue depth ≤ ticking PlayerMob count, worst-case is
**O(n)** server ticks from dirty to service — not instantaneous.

**VR-T1 role:** empirical — does normal compact-village traversal exceed worst-case at 1/10/50/100
mobs? Tuning evidence only; not a contract violation if a sprinting mob misses a hamlet.

#### B-VR-57 — fair queue admission (`LOCK RECOMMENDED`)

**Rejected:** `MAX_QUEUE >= 100` as the primary fairness story — at 101+ ticking mobs starvation
returns at the **admission** gate unless 100 is declared the supported concurrency ceiling.

**Accepted admission model (B2 amended):**

| Rule | Detail |
| --- | --- |
| **Normal capacity** | One pending slot per **currently ticking** `PlayerMob` observer — queue depth structurally ≤ ticking mob count |
| **Dedup** | Key `(dimension, mobUuid)` — re-dirty coalesces to one pending entry |
| **Saturation** | If admission refuses (emergency cap only), mob retains **dirty flag**; next eligibility uses **round-robin insertion index** — not GoalSelector poll order |
| **Service fairness** | Per-dimension lanes drained round-robin under one **server-global** query budget |
| **Emergency cap** | `MAX_EMERGENCY_PENDING` (e.g. 256) — abnormal only; log warning; not normal multiplayer posture |

**Required static tests (V1-D):**

1. N mobs dirty same tick → all admitted within N enqueue attempts (no permanent refuse at admission).
2. Saturated emergency cap → mob stays dirty; services rotate without one UUID monopolizing re-entry.
3. Dequeue on service; remove on death/unload/dimension change; no stale UUID observe.

#### Scheduler API sketch (`LOCK RECOMMENDED`)

```text
VillagePerceptionObserver          // per PlayerMob, flagless — no MOVE/LOOK
  markDirty(reason)                // chunk transition | heartbeat
  boolean isDirty()
  void clearDirtyAfterEnqueue()

VillagePerceptionScheduler         // server singleton
  boolean requestObservation(ServerLevel level, UUID mobId)
  void onServerTick(MinecraftServer server)  // service ≤ GLOBAL_QUERY_BUDGET_PER_TICK

VillagePerceptionService           // package-private
  observeAndRecord(ServerLevel, UUID mobId)  // mob.blockPosition() at service time
```

**Integration:** register observer in `SpmScavenger` beside `ExplorationActivityGoal`; scheduler
hook on server tick end (or shared phased clock — **not** inside `ExplorationActivityGoal` cadence).

**RET-1:** queue entries keyed by `(dimension, uuid)`; evict on death (`forgetEverywhere`), unload
(observer dies), dimension change (re-dirty on new observer); clear lanes on server stop.

---

## Topic: Phased implementation plan

**Author synthesis:** `Agent_Cursor` + `Agent_ChatGPT` (V1–V7 replaces earlier P0–P5 labels; map in table).

| Phase | Scope | Feasibility | Runtime proof |
| --- | --- | --- | --- |
| **V1** | ~~Village awareness~~ → **Village perception & identity** (narrowed by review): `VillagePerception`, `VillageAnchorPolicy`, `KnownVillage`, `SettlementTier`, `MobVillageMemory`, `VillageMemorySavedData` | **IMPLEMENTED** | VR-T1A **PASS** |
| **V1-D** | Bounded production perception driver (D-VR-033) | **IMPLEMENTED** (1.10.0) | VR-T1A **PASS**; VR-T1b **DEFERRED** |
| **V1.5** | **Settlement attachment & return:** `SettlementRelationship`, familiarity/visit history, commute-to-home/familiar, village-aware social | **IMPLEMENTED + RUNTIME CLOSED** — task-46 / 1.11.0 (A–D) | VR-T1.5a–c **CLOSED** (2026-08-15) |
| ~~V1 (dropped from V1)~~ | `KnownVillager`, `RingVillageBellGoal`, `VillageSiteScore` | `KnownVillager` held until V4+ consumer; other work moved to V4 | V1 got *smaller* under review — it ships the ontology every later phase depends on, and nothing that acts on it |
| **V2** | Trading: `VillagerTradeAdapter`, `TradeEvaluationPolicy`, `TradeWithVillagerGoal`, **two-step sell→buy chains**, relationship credit, finished-output projection, optional Trade Everything source | **IMPLEMENTED + CLOSED** — VR-T2 vanilla path and V2-TE positive path runtime-confirmed to recorded scope | **VR-T2 PASS**; **VR-T2k PASS (`V2-DEF-003c-R1`)**. VR-T2l, V2-I, and profiling are **DEFERRED / NON-BLOCKING** |
| **V3** | **Village Work (canonical):** committed harvest→replant, composting, population food support, read-only workstation awareness, and ally/public storage safety | A/B/C/D1/E/F **`IMPLEMENTED / STATIC-BEHAVIORAL ACCEPT`** (tasks 52–58; 1589 tests). Broad V3-D2 workstation awareness **DEFERRED**. V3-G **NEXT but HOLD**. | VR-T3a–m below; runtime **UNVERIFIED** until batched campaign |
| **V4** | Factual site utility + **Place opinion bridge** (`D-VR-025` **LOCKED**; `D-VR-026` **HELD**), known traders, utility-driven home promotion and return preference beyond shipped V1.5 return | **PARTIAL** | VR-T4: prefer liked legal village; blocking demand still reaches B when only legal source |
| **V5** | Raid awareness: `RaidTask` state, bell alarm, **TaskLifecycle interrupt/resume**, shelter EVACUATE, **day/night arbitration**, **`OminousBottlePolicy` pickup** | **PARTIAL** | VR-T5: iron demand interrupted → defend → resume; **VR-T5b:** dusk raid vs shelter |
| **V6** | Player-parity bridges: cross-domain Ominous Event RAID intent, self-drink executor, Bad Omen/Raid Omen bridges, participation credit, hero recognition gift bridge + host pickup, **zombie-villager curing** | **REQUIRES MIXIN/BRIDGE** | VR-T6: bottle → Bad Omen → Raid Omen commit/abort → raid; VR-T6b: villager gift recognition + host pickup; curing scenarios to be defined in V6 |
| **V7** | Advanced community: rescue, repair, transport, settlement projects, group coop, founding through world truth + ordinary perception, repair/build golem | **NOT PRACTICAL** gen-1 | Deferred |

### Canonical V3 implementation contract (A/B/C/D1/E/F static-accepted; G next)

**Status:** tasks 52–58 are shipped and statically accepted (**1589 tests** at V3-F closure).
Runtime VR-T3 rows remain **UNVERIFIED**. Task-56 shipped **D1 population/HOME facts** only;
task-58 shipped **minimal `ComposterWorkFacts`** on the existing perception/work cadence — **not**
a retrofit of `VillageWorkFacts`, **not** broad V3-D2, and **not** an executor-local world scanner.

#### Dependency drift correction (`CONFIRMED` — shipped code vs stale RFC assumption)

```text
STALE RFC ASSUMPTION:
    V3-D workstation awareness → V3-F gets known composter

SHIPPED REALITY (task-56):
    VillageWorkFacts = settlement identity + adult villagers + HOME capacity/claimed/free
                       + observedAtTick + completeness + freshness
    VillageWorkObservationKernel = HOME POIs + adult villagers only

CORRECTED ARCHITECTURE:
    existing VillagePerception / work refresh cadence
            ↓
    ComposterWorkFacts (transient, settlement-keyed, bounded loaded positions)
            ↓
    CompostGoal (opportunistic VILLAGE_WORK — no manufactured demand)
```

**Rejected pattern:**

```text
CompostGoal.canUse() → scan around for composters → find one → operate it
```

V3 explicitly rejected another independent village-world scanner (`D-VR-085-R1` Option B/C).

#### Exact scope and disposition

| Capability | V3 disposition | Canonical boundary | Closure owner |
| --- | --- | --- | --- |
| Crop harvesting/replanting | **IN** | One committed harvest→replant episode (`D-VR-079`); no loosely related second goal | V3-C; VR-T3a–c/k |
| Composting | **IN** | Opportunistic: disposable surplus already held → loaded known composter → one vanilla attempt; **no** independent seed/bone-meal appetite | V3-F; VR-T3d |
| Population food | **IN** | Support by offering disposable food; no breeding command, bed claim, or Brain mutation | V3-E; VR-T3e |
| Workstation awareness (broad) | **DEFERRED** | Generic read-only `PoiTypes.FARMER` workstation evidence closes VR-T3f independently; **not** a gen-1 V3-F prerequisite | Post-V3-F / V4 |
| Composter position evidence | **IN (minimal)** | `ComposterWorkFacts`: transient, bounded, loaded-only, settlement-identity-bound, freshness-aware; executor revalidates live block at COMMIT | V3-F; VR-T3d |
| Ally/public storage interaction | **IN, SAFETY GATE** | `D-VR-017`; continuous admission + continuation guard; explicit permission only | V3-A/B; VR-T3g–i |
| Zombie-villager curing | **DEFERRED TO V6** | Weakness/apple execution and player-credit/relationship bridge are not Village Work | V6; not a VR-T3 closure item |

#### Shared authority and lifecycle

V3 does not create another activity brain. Existing urgent authority and live mandatory progression
remain above discretionary village work. The implemented V3-A admission seam consumes the same
authoritative pending/running work truth used by progression; observing that no executor is active is
not evidence that mandatory work is idle.

```text
URGENT / command / combat / shelter authority
        ↓
live or pending mandatory progression?
    YES → village work not admitted
    NO  → bounded V3 village-work candidate may be selected
        ↓
harvest mutates managed crop?
    NO  → ordinary discretionary interruption/reacquisition
    YES → same-tick replant commit; failed post-mutation repair remains mandatory cleanup
```

Storage safety is different from discretionary work: its `D-VR-017` predicate is checked
continuously regardless of which activity owns movement. It cannot be bypassed by Opinion utility,
familiarity, an active loot goal, or absence of a V3 work candidate.

#### V3 tasks

| Task | Dependencies | Objective | Must happen | Must not happen | Scenarios | Status |
| --- | --- | --- | --- | --- | --- | --- |
| **V3-A — authority/profile contract** | V1.5 relationship; D-VR-080/**082-A1**/**084**; **V2-DEF-002 repair (task-52, IMPLEMENTED)** | Cross-dimension `VillageScenarioProfile` policy store + `VillageWorkAdmission` that **consumes** the shared discretionary-permission seam + optional `VillageWorkSelector` among V3 intents | Profile explicit, inspectable, cross-dimension consistent; admission refuses through the **same** authority `DiscretionaryActivityDirector` consumes | Profile in dimension-local village memory; HOME/HIGH → ally; `MaterialDemand` alone defines mandatory; **a village-local reconstruction of "mandatory work exists"** | VR-T3j | **IMPLEMENTED / STATIC-BEHAVIORAL ACCEPT** — **task-53** (1386 tests) |
| **V3-B — minimum StorageOwnership + host guard** | V3-A; D-VR-012/017/081 | `GlobalPos`-keyed permission registry + classifier; continuous `RaidContainersGoal` guard | Ally uses only mob-owned/shared storage; permissions survive unload/restart | Evict grants on chunk unload/dimension change; naked `BlockPos`; ambiguous double-chest halves | VR-T3g–i | **IMPLEMENTED / STATIC-BEHAVIORAL ACCEPT** — **task-54** (1435 tests) |
| **V3-C — committed crop episode** | V3-A; pinned host `HarvestCropsGoal` mechanics; **D-VR-079-A1** | One target-bound committed episode over the **managed crop domain**, plus a continuous host-harvest veto inside that domain and direct banking of the episode's own replant-capable drops | Managed mature crop ends replanted or in mandatory bounded repair; episode banks its own replant drops; host destructive harvest cannot bypass the contract inside the domain | Successful managed harvest routinely leaves farmland barren after preemption; **planting supply depends on floor-item pickup**; **veto fires when the domain cannot be positively established** | VR-T3a–c/k, **VR-T3l/m** | **IMPLEMENTED / STATIC-BEHAVIORAL ACCEPT** — **task-55** (1478 tests) |
| **V3-D1 — population/HOME facts** | V3-A; existing `VillagePerception` scheduler/budget | Settlement-bound adult-villager and HOME-capacity evidence | Facts invalidate on anchor supersede, remain bounded, and never load chunks | Facts become permission or mutate POIs; absorb unrelated facility fields | VR-T3e foundation | **IMPLEMENTED / STATIC-BEHAVIORAL ACCEPT** — **task-56** (1499 tests) |
| **V3-D2 — read-only workstation evidence (broad)** | V3-D1 | Generic bounded loaded `PoiTypes.FARMER` workstation evidence for future consumers | Provenance/age/completeness; executor revalidates live block | Becomes gen-1 V3-F prerequisite; executor-local scan | VR-T3f | **DEFERRED** — not required for V3-F gen-1 |
| **V3-E — population food support** | V3-A; **V3-D1** (villager count + HOME capacity facts); disposable-resource policy; existing gift/drop seam | Offer bounded food surplus when population evidence requests support | Revalidate population, target, inventory reserve, and path at handoff | Consume personal/progression reserve; command breeding; loop gifts with no deficit | VR-T3e/j | **CLOSED / STATIC-BEHAVIORAL ACCEPT** — **task-57** (1543 tests; **DO NOT REOPEN** unless runtime falsifies locked invariant) |
| **V3-F — composting** | V3-A; V3-C/E; **D-VR-085-A2** + **D-VR-086-A2** + **D-VR-087-A1** + **D-VR-087-TX1**; **`D58-1…D58-12`** | Opportunistic: spend one explicitly disposable compostable at one loaded known composter in one vanilla attempt | Replant/population/progression reserves survive; one attempt terminates; READY output not extracted gen-1 | Double debit; scan every tick; manufactured seed/bone-meal demand; unmodelled stock composted | VR-T3d/j | **CLOSED / STATIC-BEHAVIORAL ACCEPT** — **task-58** (1589 tests; Gate 0 PASS; **DO NOT REOPEN** unless runtime falsifies locked invariant) |
| **V3-G — integration and closure** | V3-A…F | Static/build gates plus temporary `spm_vr` V3 presets and approved runtime matrix | **Applicable** VR-T3 rows record must/must-not evidence and semantic-drift review (see closure rule) | Replant + one chest row close the whole phase; compile is called behavior proof; **VR-T3f pulled in via closure wording** | VR-T3a–m (**VR-T3f non-applicable** while V3-D2 deferred) | **NEXT FRONTIER — HOLD** until separate Task-59 authorization |

Tasks 52–58 are complete (static). **V3-G (task-59)** is the canonical next slice but **HOLD**
until separately authorized.

**Dependency sequence (amended 2026-08-22):** `V2-DEF-002 repair / D-VR-084 → V3-A`; `V3-A → V3-B/C/D1`;
`V3-D1 → V3-E`; **`V3-D1 + V3-C + V3-E → V3-F`**; `V3-A…F → V3-G`. Broad V3-D2 is **not** on the
V3-F critical path. Executor-local POI query (Option B) and cubic block scan (Option C) remain
**rejected** — see `D-VR-085-R1`.

**Recommended task sequence (`LOCKED` 2026-08-22, amended):**

```text
task-58 = V3-F composting (ComposterWorkFacts + CompostGoal + reserve/transaction policies) — CLOSED
task-59 = V3-G integration/runtime closure — NEXT but HOLD
```

Splitting composter facts into a separate “V3-D2 task” before V3-F is **rejected** — the facts are a
**small V3-F dependency** inside the existing perception/work cadence, not a generic workstation
subsystem. Combining facts + executor in one task is **accepted** because both are narrow and
composter-specific.

#### V3-F implementation contract (`CLOSED`; task-58 unified slice)

**Status:** **CLOSED / STATIC-BEHAVIORAL ACCEPT** (task-58; Gate 0 PASS; 1589 tests;
`.superpowers/sdd/task-58-report.md`). `D58-1…D58-12`, `D-VR-085-A2`, `D-VR-086-A2`, and
`D-VR-087-A1`+`TX1` remain **locked**. **DO NOT REOPEN** unless runtime falsifies a locked invariant.

**Evidence baseline (`CODE_CONFIRMED` unless noted):**

| Probe | Result |
| --- | --- |
| Compost executor in `src/main/java` | **CONFIRMED** — `CompostGoal`, `CompostTransaction`, `ComposterWorkFacts*` shipped (task-58) |
| `VillageWorkFacts` fields | Population/HOME capacity only — **no composter positions** (`VillageWorkFacts.java`) |
| Composter as villager POI | **CONFIRMED** — pinned `PoiTypes.java:110` registers `Blocks.COMPOSTER` as `PoiTypes.FARMER`; `VillagerProfession.java:35–40` binds Farmer to it. Task-58 observes composter positions via `ComposterWorkFacts`, not `VillageWorkFacts`. |
| Vanilla insertion | **CONFIRMED** — pinned `ComposterBlock.insertItem`: when `LEVEL < 7` and item is compostable, `addItem(...)` is attempted and the supplied stack is then shrunk by exactly one even when the probabilistic level roll fails |
| Vanilla production/extraction | **CONFIRMED** — level 7 schedules a 20-tick transition to level 8; bone meal is created only by separate `extractProduce(...)`, not by insertion |
| Existing reserve APIs | **CONFIRMED** — `CompostReserveModel` (wheat/beetroot seed surplus after replant reserve = 1); `CompostExpendabilityPolicy` composes sell/nutrition/population layers |
| P4 registration order (`SpmScavenger.java`) | `PlaceTorchGoal` → `VillageHarvestEpisodeGoal` → `PopulationFoodSupportGoal` → **`CompostGoal` @ P5** |
| Profile gate | `VillageWorkAdmission` permits **`VILLAGE_ALLY` only** (`VillageWorkAdmission.java:40`) |
| Bounded enumeration lesson (task-57) | **bounded K sample → deterministic ordering within returned sample** — not globally nearest K |
| `SellReserveModel` posture | **modelled-only** — applies to logs/planks/sticks; `empty()` = unmodelled for **sell**, not compost veto (see `D-VR-086-A2`) |
| `VillagePerception` public surface | Anchor/count/coverage only — composter positions via `ComposterWorkFacts`, not `VillagePerception` |

**Depends on (CLOSED / STATIC-BEHAVIORAL ACCEPT):** task-52 (`MandatoryOwnership`) · task-53
(`VillageWorkAdmission`, `VILLAGE_WORK`) · task-54 (storage guard — orthogonal) · task-55
(`HarvestCropTargetSelector`, `CropReplantSemantics`, replant reserve) · task-56 (settlement anchor +
bounds — read-only population/HOME) · task-57 (`PopulationFoodExpendabilityPolicy`, episode shape,
`mobGriefing` gate).

**Deliverable (task-58):** `CompostGoal` at **P5 (provisional)** + `ComposterWorkFacts` +
`CompostReserveModel` + `CompostExpendabilityPolicy` + `CompostTargetSelector` + admission wrapper +
episode cooldown (RET-1).

```text
VillagePerceptionScheduler / existing work refresh
               │
               ▼
      ComposterWorkFactsCache
      transient / bounded / settlement-keyed
               │
               ▼
       CompostGoal (P5 provisional)
               │
      VillageWorkAdmission
               │
               ▼
   CompostExpendabilityPolicy
       ├─ mechanical compostability (vanilla — not spend authority)
       ├─ CompostReserveModel (gen-1 narrow seed surplus)
       ├─ progression / sell / fuel protection
       ├─ population-food / nutrition protection
       └─ unknown → DENY
               │
               ▼
        ONE input + ONE composter + PATH
               │
               ▼
        INTERACT_PREPARE (current-truth recheck)
               │
               ▼
            COMMIT (one vanilla attempt)
               │
               ▼
             DONE / cooldown
```

**Opportunistic authority (`D58-1`, `D58-10`):** V3-F never asks “how can I obtain compostables?”
It asks only: “Do I **already** possess compostable material every higher authority declared
disposable?” **No** Gather demand · **No** Trade demand · **No** Craft demand · **No**
`MandatoryOwnership` publisher · **No** bone-meal progression consumer · **No** seed acquisition
route · **No** `VillageWorkSelector`.

**Compostability ≠ expendability (`D58-4`, `D58-5`):**

```text
Mechanical truth:  Can vanilla composter accept this item?     → compostability check
Ownership truth:   Is PlayerMob allowed to sacrifice this item?  → CompostExpendabilityPolicy
```

**Gen-1 expendability is deliberately narrow (`D58-5`, `D-VR-086-A2`):**

| Material class | Gen-1 V3-F |
| --- | --- |
| Explicitly reserve-modelled crop seed surplus | **candidate** — wheat seeds + beetroot seeds (Gate 0 PASS) |
| Villager breeding food | **deny** |
| Player nutrition stock | **deny** unless future shared reserve proves surplus |
| Progression/trade/crafting material | **deny** unless explicitly modelled |
| Held/equipped material | **deny** |
| Unknown/modded compostable | **deny by default** |
| Merely vanilla-compostable | **not sufficient authority** |

**Episode shape (`D-VR-087-A1`, `D58-7`, `D58-8`, `D58-9`):**

```text
IDLE → SELECT → PATHING → INTERACT_PREPARE → COMMIT → DONE
```

- Bind at SELECT: `SettlementIdentity`, current composter fact, one disposable input, route.
- **No RNG** during SELECT/PATHING/PREPARE — COMMIT is the single vanilla compost RNG/mutation attempt
  (same anti-reroll discipline as crop loot).
- **One composter, one compostable item (count 1)** per activation — no `while inventory has seeds: spam interact`.
- **INTERACT_PREPARE / COMMIT** revalidate **current truth** (task-57 lesson — no stale SELECT snapshot):

```text
VillageWorkAdmission still permits
AND no combat/shelter/mandatory takeover
AND exact SettlementIdentity still current
AND composter fact still fresh/valid
AND target chunk still loaded
AND block at P is STILL a composter within settlement bounds
AND mob within interaction distance
AND planned stack still exists
AND same quantity still disposable
AND composter state accepts the intended operation
→ else ABORT with 0 inventory debit and 0 block mutation
```

- `mobGriefing` hard gate at `canUse`, `canContinueToUse`, and `COMMIT` (task-57 precedent).
- **Vanilla owns mechanics** at COMMIT — Scavenger decides permission/target/quantity; vanilla decides
  success/failure/level/effects. **No** project-owned compost percentage table (Gate 0 pins primitive).
- **Bone meal (`D58-11`):** gen-1 **input-only** — add disposable material; **never** extract READY
  output. Gate 0 **PASS** — input-only locked; no extraction code shipped.
- **One mutation owner (`D-VR-087-TX1`):** vanilla `insertItem(...)` shrinks the supplied stack.
  Unchanged level after eligible insert = completed vanilla attempt — terminate and backoff.

**Priority (`D58`):** **`CompostGoal` @ P5** — below P4 harvest/population/torch, above P7 campfire
and P8/9 explore/wander. P5 vs P4 torch contention remains **`RUNTIME_QUESTION`** only.

**Gate 0 (`LOCKED checklist` — PASS 2026-08-22):**

1. Minecraft 1.21.1 composter state machine: input levels, full/READY transition, failed-chance
   consumption, scheduling, extraction/reset behavior.
2. Canonical mutation primitive: exact safe vanilla call for one PlayerMob input attempt and
   inventory/world atomicity.
3. Compostability registry: vanilla + Fabric/modded mechanical truth — **not** spend authority.
4. Farmer POI truth: composter POI identity, occupancy semantics, invalidation/replacement behavior.
5. Java farmer parity: `WorkAtComposter` inputs/reserves/extraction — evidence only, not automatic
   authority.
6. Reserve delegation: enumerate every Scavenger claimant overlapping compostables; unknown → fail closed.
7. Observation integration: prove bounded loaded composter facts via existing perception/work cadence.
8. Scheduler interference: P5 vs existing P4 harvest/population/torch and lower idle work.
9. Commit atomicity: one inventory unit cannot be lost/duplicated if composter changes SELECT→COMMIT.
10. READY ownership: decide input-only vs public extraction before any extraction code exists.

**Must happen (static acceptance):** with proven disposable surplus and a reachable level `< 7`
composter, exactly one eligible unit is consumed in one insertion attempt; both level-advanced and
level-unchanged outcomes terminate; all higher reserves survive.

**Must not happen:** double debit; unchanged level treated as failure/retry; reserved or unmodelled
stock composted; bone meal attributed to insertion; unloaded/unknown target used; independent cubic
block scan or per-tick discovery without backoff; manufactured compostable/bone-meal demand.

**Authorization ladder (architecture locked 2026-08-22, amended):**

| Gate | User phrase | Status |
| --- | --- | --- |
| Architecture | **Work the RFC** — dependency drift correction + `D58-1…12` | **DONE** |
| Brief design | **BEGIN task-58 / V3-F — BRIEF DESIGN ONLY** | **DONE** |
| Gate 0 | Read-only source audit before implementation | **PASS** — `.superpowers/sdd/task-58-gate0-report.md` |
| Implementation | Separate authorization | **CLOSED** — STATIC-BEHAVIORAL ACCEPT (1589 tests) |

**Task-59 Gate 0 disposition (`LOCKED` 2026-08-22):** Task-58 Gate 0 is **not repeated**. Task-59
requires static/build regression pass, temporary `spm_vr` preset manifest, approved runtime matrix
document, and **explicit runtime-launch approval per AGENTS.md**. No additional read-only source-audit
gate unless a new subsystem is introduced (none planned for current V3-G scope).

**Out of scope for task-58:** `VillageWorkSelector` · broad V3-D2 workstation framework · bone-meal
extraction/application goal · V3-G runtime datapack · `MandatoryOwnership` publisher · PlaceTorch
contention fix (document as `RUNTIME_QUESTION`).

#### V3-F — MAIBS behavioural prediction (pre-implementation; `UNVERIFIED`)

| Time | Predicted observable | Failure / falsifier |
| --- | --- | --- |
| `T0` | Ally holds explicit seed surplus after replant reserves; `ComposterWorkFacts` lists loaded composter within settlement bounds | Compost admits with only seeds in backpack; facts load chunks or refresh every tick |
| `T+10` | Mob paths to one composter while no mandatory owner active | Mandatory gather/trade still running but compost wins; harvest episode preempted incorrectly |
| `T+60` | At reach, exactly one eligible unit is consumed; level may advance or remain unchanged; episode terminates in both cases | Double debit; unchanged level causes immediate retry/orbit; reserve broken |
| `T+200` | Episode ends; cooldown prevents immediate re-insert; reserves re-evaluated | Endless compost loop with no surplus; bone meal registered as new demand |
| `T+1200` | Composting remains rare; a level-8 composter may remain ready until a vanilla farmer/player extracts it | Compost starves crop/population work; insertion is falsely credited with producing bone meal |

**Strongest objection:** P5 compost may lose to P4 harvest/population/torch indefinitely — acceptable
for gen-1 (compost is a low-priority side activity); document in VR-T3d notes.

**Alternatives considered for target discovery (`D-VR-085-R1`, amended):**

| Option | Benefit | Failure | Disposition |
| --- | --- | --- | --- |
| **A — shared `ComposterWorkFacts` on existing perception/work cadence** | No executor scanner; reuses task-56 scheduler/cache pattern; composter-specific not generic D2 | Cache age/completeness surface | **`LOCKED` (`D-VR-085-R1` + `D-VR-085-A2`)** |
| **B — executor-local bounded POI/block query** | Smaller slice | Duplicates discovery; violates V3 no-scanner rule | **REJECTED** |
| **C — executor-local cubic block scan** | Direct block truth | Volumetric cost; ignores POI cadence | **REJECTED** |
| **D — broad V3-D2 `VillageWorkstationFacts` prerequisite** | Closes VR-T3f generically | Over-engineers gen-1 V3-F; stale assumption after task-56 | **REJECTED for V3-F gen-1** |

#### V3 scenario parity and closure matrix

| ID | Scenario | Must happen | Must not happen | Evidence/status |
| --- | --- | --- | --- | --- |
| **VR-T3a** | Managed mature crop, seed available, reachable farmland | Mob paths, harvests, and leaves the same managed position replanted in one committed episode | A visible successful harvest ends as bare farmland | `UNVERIFIED` — V3-C |
| **VR-T3b** | Combat/command/shelter interruption before interaction | No world mutation occurs; retained candidate is discarded/revalidated after interruption | Old path/target resumes blindly; crop removed before authority permits commit | `UNVERIFIED` — V3-C |
| **VR-T3c** | Seed/support/crop changes before commit or replant write fails | Preflight aborts without harvest; if failure follows mutation, mandatory bounded repair/reacquisition owns cleanup | Discretionary explore/trade starts while managed farmland remains an owned repair gap | `UNVERIFIED` — V3-C |
| **VR-T3d** | Proven disposable compostable and loaded known composter at level `< 7` | Exactly one unit is consumed; both advanced and unchanged levels terminate/back off; later level-8 readiness remains vanilla world truth | Double debit; unchanged level loops; higher reserve is composted; insertion is claimed to produce bone meal; manufactured compostable demand | `UNVERIFIED` — V3-F (task-58) |
| **VR-T3e** | Population food deficit and eligible villager | Disposable food is delivered once, then deficit/target/inventory are re-resolved | Direct breeding command, bed claim, reserve violation, or endless gifting | `UNVERIFIED` runtime — V3-E **STATIC-BEHAVIORAL ACCEPT** (task-57) |
| **VR-T3f** | Farmer workstation claimed/unclaimed, villager sleeps/restocks, POI unloads | *(Broad D2 deferred)* When implemented, shared bounded evidence records provenance/age/completeness | Chunk load, parallel block scanner, workstation mutation | `DEFERRED` — broad V3-D2 post-V3-F |
| **VR-T3g** | `VILLAGE_ALLY` + `VILLAGE_PUBLIC` container | Host `RaidContainersGoal` cannot admit or continue looting it | HOME/HIGH alone is used as permission; container opens/continues looting | `UNVERIFIED` — V3-A/B; supersedes old VR-T1.5d wording |
| **VR-T3h** | `VILLAGE_ALLY` + `UNKNOWN` ownership | Fail closed and leave container untouched | Missing evidence is interpreted as public access or permission | `UNVERIFIED` — V3-B |
| **VR-T3i** | Explicit mob-owned/shared storage and non-ally control | Explicitly permitted ally access may proceed; non-ally host behavior remains unchanged | Blanket goal strip or ally denial despite positive permission | `UNVERIFIED` — V3-B |
| **VR-T3j** | Live/pending mandatory progression while village work is available | Mandatory work retains authority; village work waits and later re-resolves | Idle observation or Opinion preference displaces pending mandatory work | `UNVERIFIED` — V3-A/E/F |
| **VR-T3k** | Two mobs select one crop; first changes it | First commits; second detects invalidation and reacquires/abandons without mutation | Double break/replant, stale target loop, or persistent global crop reservation | `UNVERIFIED` — V3-C |
| **VR-T3l** | Managed-domain crop, mob hungry (`wantsFood()`), V3 admission refused | Host `HarvestCropsGoal` is vetoed at that position; the field stays planted; the mob's own food behaviour (`HuntForFoodGoal`, foraging) is unaffected | Host destructive harvest runs inside the managed domain; or the veto extends to wilderness crops and suppresses stock SPM food behaviour | `UNVERIFIED` — V3-C (D-VR-079-A1) |
| **VR-T3m** | Repeated managed harvest episodes over many cycles | Replant stock is sustained by the episode's **own** banked drops; a crop whose pinned drops cannot guarantee a planting item pauses managed harvest instead of draining the reserve | Planting supply is recovered via floor-item pickup; a field is harvested down to a barren state because the reserve ran out mid-episode | `UNVERIFIED` — V3-C (F8) |

**Phase closure (`LOCKED` 2026-08-22):** V3 requires V3-A…G plus all **applicable** VR-T3a–m rows.
**VR-T3f is NOT applicable** to V3-G closure while broad V3-D2 workstation awareness remains
**DEFERRED** — Task-59 must not pull D2 back in via closure wording. No subset consisting only of
replant + storage may close the phase.

#### Pre-lock decisions — `LOCKED` (`D-VR-080…083`, User peer review 2026-08-19), amended 2026-08-19

The four architecture blockers are resolved. See [Topic: Decisions](#topic-decisions) for full text.
**Two of the four were amended the same day** by a code-evidenced review pass (`Agent_Claude` +
User): `D-VR-082` → `D-VR-082-A1`, `D-VR-079` → `D-VR-079-A1`, plus new `D-VR-084`
(`MandatoryOwnership`). D-VR-080 and D-VR-081 stand unchanged.

| ID | Summary |
| --- | --- |
| **D-VR-080** | `VillageScenarioProfile` is **one cross-dimension policy per mob** — not stored in `MobVillageMemory` / `VillageMemorySavedData`. Default `NEUTRAL`; `VILLAGE_ALLY` only via explicit config-at-spawn or operator command; HOME/HIGH never auto-promote; existing worlds migrate to `NEUTRAL`. New store must register in `PerMobSavedData.forgetAll()`. |
| **D-VR-081** | Storage permission keyed by **`GlobalPos`**; **preserve** on chunk unload, mob dimension change, and server restart; **delete** on explicit revoke, container destroyed/replaced, mob permanent removal. Double chests canonicalize to one logical container key. Continuous ally guard on `RaidContainersGoal`. |
| **D-VR-082** | V3 executor goals at **priority 4**. `VillageWorkAdmission` blocks when **any live mandatory owner** exists (not merely `MaterialDemand`). Optional `VillageWorkSelector` chooses among V3 intents — **not** a parallel `VillageWorkDirector`; subordinate to village orchestration (`VillageInteractionDirector` when shipped). |
| **D-VR-083** | **Budget contract `LOCKED`**; numeric constants **`PROVISIONAL` / `UNVERIFIED`** until profiling. Population food support candidate when `adultVillagerCount ≥ 2` **and** `currentFreeHomeCapacity > 0` on **FRESH + COMPLETE** facts (**D-VR-083-A1** — vanilla vacancy, not `totalBeds − villagers`). |
| **D-VR-084** | **NEW.** `MandatoryOwnership` — one shared discretionary-permission authority with two inputs (running-activity truth + **published** pending claims) and two consumers (`DiscretionaryActivityDirector`, `VillageWorkAdmission`). Demand never creates authority; a claim does, and **a claim may never be refreshed by the continued existence of the same demand**. |
| **D-VR-082-A1** | **AMENDS D-VR-082.** Admission **consumes** `DiscretionaryEligibility` rather than re-deriving mandatory truth from a five-source list. `VILLAGE_TRADE` joins `blocksDiscretionaryChoice`. New `ActivityClass.VILLAGE_WORK` blocks a fresh discretionary selection while running. Priority 4 is **shared with `PlaceTorchGoal`**, and the `MAINTENANCE`/`VILLAGE_WORK` blocking asymmetry is deliberate. |
| **D-VR-079-A1** | **AMENDS D-VR-079.** Defines the **managed crop domain** without `SettlementRelationship`; requires a continuous host-`HarvestCropsGoal` veto inside it that **fails toward stock** when the domain cannot be positively established; requires the episode to bank its own replant-capable drops (F8), with crop-specific reserve accounting. |

**Phase architecture status:** shared authority and V3-A/B/C/D1/E/F are **IMPLEMENTED /
STATIC-BEHAVIORAL ACCEPT** in tasks 52–58. Canonical V3 runtime closure (V3-G) remains
**UNVERIFIED** until the batched campaign.

**Task numbering history (User, 2026-08-19; synchronized 2026-08-22).** The shared authority repair
remained its own task because it has two consumers. Tasks 52–58 are completed static slices:

```text
task-52 = MandatoryOwnership / V2-DEF-002 repair
task-53 = V3-A profile + admission
task-54 = V3-B storage ownership/guard
task-55 = V3-C committed crop episode
task-56 = V3-D1 population/HOME facts (not workstation/composter positions)
task-57 = V3-E population food support
task-58 = V3-F composting (ComposterWorkFacts + CompostGoal) — CLOSED
task-59 = V3-G integration/runtime closure — NEXT but HOLD
```

**Runtime sequencing (User, 2026-08-19; amended 2026-08-22).** D-VR-084 gets **automated behavioural
acceptance now and no dedicated Minecraft session**:

```text
tasks 52–58 static acceptance (DONE)
        -> ONE batched runtime campaign (task-59 / V3-G), including D-VR-084 witness + VR-T3d
        -> VR-T3f remains deferred with broad V3-D2 (non-applicable to V3-G closure)
```

### Legacy phase map (superseded)

| Old | New |
| --- | --- |
| P0 ally + raid detect | V1 + `StorageOwnership` in V3 |
| P1 RaidDefense | V5 composed defense |
| P2 Trade | V2 |
| P3 Reputation | V4 reputation-aware factual use + V6 player-typed discounts |
| P4 Bad Omen | V6 |
| P5 Cure | **V6** (`D-VR-078`; prior V3 ownership superseded) |

**Proposed temporary datapack:** `test-datapacks/phase-village-raid/` (`spm_vr`) — V3-A…G may add
presets only when implementation/runtime work is separately authorized. The removed V2-H proof
datapack is not evidence that a V3 fixture currently exists.

**Cross-RFC:** V2+ trades chain to `MaterialDemandPolicy` / `RFC-VANILLA-AUTONOMOUS-PROGRESSION.md` and `RFC-TOOL-TIER-UPGRADES.md` D-TTU-017.

---

## Topic: MAIBS — behavioural prediction (pre-implementation)

**Gate:** MAIBS-1 — required before each movement/world-interaction phase, including V3 and V5,
receives implementation authorization.

### V1 — village perception + site selection

| Minute | Predicted observable | Failure mode |
| --- | --- | --- |
| 0–2 | Mob paths through village; greets villagers (`FriendlyGreetGoal`) | Ignores villagers entirely |
| 2–5 | First `KnownVillage` created; bell/beds cached | Creates duplicate villages per bed |
| 5–8 | Leaves village; returns to same anchor | Forgets cluster on short leave |
| 8–12 | Second village seen; prefers higher `VillageSiteScore` when trading | Always picks nearest chunk |
| 12–15 | `HOME_VILLAGE` set; commute back after explore | Treats all clusters as equal |

**Must not happen:** `RaidContainersGoal` loots denied storage while the explicit
`VillageScenarioProfile.VILLAGE_ALLY` + `D-VR-017` predicate is active. HOME/HIGH alone is not an
ally profile (shelter hold alone is also insufficient).

### V1-D — production perception driver (`IMPLEMENTED` — 1.10.0; VR-T1A **PASS**)

| Scenario | Predicted observable | Failure/weirdness under test |
| --- | --- | --- |
| VR-M1: one mob crosses a compact village | Memory appears after dirty request **obtains budget** (may be many ticks later) | Crosses completely before service; empty observe at exit |
| VR-M2: mob stands while villagers claim POIs | Heartbeat eventually refreshes facts | Chunk-only design never notices the change |
| VR-M3: combat during observation turn | Combat continues; flagless observer records facts | Readout/GoalSelector authority changes |
| VR-M4: 100 mobs enter together across levels | Deduplicated UUID requests drain round-robin; max one global query in a server tick | Same-tick POI storm, level starvation, stale queue entries |
| VR-M5: only persisted/unloaded POIs nearby | Empty observation creates no memory; **coverage/supersede use PerceptionCoverage only** (V1-R4) | Hidden POI counts or admitted quantity alter supersede |
| VR-M6: dimension change/teleport | New local observation is requested; old UUID memory survives | Old observer state suppresses local discovery |
| VR-M7: addon disabled | No query/new memory; existing memory remains | Disabled cleanup mutates or creates knowledge |

Temporal prediction: `T0` entry dirties observation and enqueues once; `T+0…bounded backlog` its
level lane receives the global query slot;
`T+200` stationary heartbeat can correct changed facts; `T+1200` prolonged presence should show
stable memory rather than repeated new identities. Runtime query counts and anchor sequences are
required evidence; code/static tests cannot confirm this timeline.

### V3 — Village Work behavioral prediction (`D-VR-078/079`; synchronized after tasks 52–58)

**Evidence baseline.** The original pre-implementation prediction is retained below as the MAIBS
contract. `CODE_CONFIRMED`: tasks 52–58 implement shared mandatory ownership, ally profile and
admission, storage safety, committed harvest→replant, population/HOME facts, population food, and
`ComposterWorkFacts` + `CompostGoal`. All physical behavior and performance claims remain
`UNVERIFIED` until an approved runtime campaign.

| Layer | Result |
| --- | --- |
| Intended behavior | An ally performs bounded village work without displacing urgent/mandatory activity, degrading managed fields, stealing uncertain storage, or inventing permission from Opinion/attachment |
| Current mechanism | V3-A/B/C/D1/E/F **shipped (static)**; runtime witness batched to task-59 / V3-G |
| Planned mechanism | Batched VR-T3 runtime campaign with temporary `spm_vr` presets |
| Predicted player-visible behavior | During genuine idle windows the mob performs one legible village job, finishes or safely invalidates it, then re-resolves; combat/commands/mandatory progression remain visibly dominant |
| Failure boundary | Any post-harvest bare managed farmland, UNKNOWN-container opening, mandatory-work displacement, stale workstation path, or surplus loop fails the design |
| Confidence | Architecture direction `DOCUMENTATION_CONFIRMED`; behavior/performance `UNVERIFIED` |

#### Harvest→replant ownership and geometry

Normal scenario: crop block `(10,64,10)` stands on valid farmland `(10,63,10)`; mob starts at
`(14,64,10)`. V3-C may select the crop only from loaded/perceivable space and after proving a crop
representation, a reserved seed already held, valid support, mobGriefing/interaction legality,
inventory/drop handling, and a vanilla navigation path that reaches the existing host interaction
distance. Arrival revalidates those facts. The server-thread commit performs harvest and age-0
replacement in one interaction tick; success is recorded only after the replacement exists.

Adversarial scenario: another mob breaks/tramples the crop/support while the first walks. The first
mob observes mismatch and abandons/reacquires without world mutation. If an exceptional failure
occurs after harvest mutation, the episode becomes mandatory bounded repair for that exact position;
discretionary work cannot hide the gap. Repair invalidates when farmland/support no longer exists or
another actor has already restored/changed the position.

**Alternatives considered:**

| Option | Benefit | Failure | Disposition |
| --- | --- | --- | --- |
| Separate `ReplantCropGoal` | Small local goal; independent retries | GoalSelector interruption creates an ordinary interval where harvest is “successful” and replant has no owner; seeds may be dropped/consumed and another activity may win | **REJECTED for managed V3 harvest** |
| One committed harvest→replant episode | Consumer, target, seed reserve, mutation, invalidation, and completion have one owner; no normal inter-goal gap | Needs explicit preflight and bounded exceptional repair; cannot claim full atomic rollback of arbitrary world changes | **SELECTED (`D-VR-079`)** |

#### Authority and GoalSelector interaction

| Activity | Current/proposed band | Flags | Can interrupt V3 work? | Retained state | Expected observable result |
| --- | ---: | --- | --- | --- | --- |
| Command/emergency/combat | Existing higher authority (combat P0–2 where registered) | commonly MOVE/LOOK | Yes before interaction; same server-tick commit is not split between Java statements | Crop candidate/path discarded; post-mutation repair only if exceptional failure occurred | Mob stops village travel and responds immediately; later re-resolves world truth |
| Mandatory Gather/Smelt/Craft/Trade | Deliberate-work band **priority 3** | MOVE/LOOK as applicable | Yes; `VillageWorkAdmission` refuses while **any live mandatory owner** exists — running **or** claimed-pending, read from shared `MandatoryOwnership` (`D-VR-082-A1`, `D-VR-084`) | Mandatory consumer survives; V3 candidate disposable | Mob continues progression instead of village work |
| V3 discretionary village work | **Priority 4**, **shared with `PlaceTorchGoal`** (`D-VR-082-A1`); semantically below mandatory work | MOVE/LOOK executor-specific; classifies `VILLAGE_WORK` | Peer discretionary work cannot steal a committed interaction (equal priority supplies this); running `VILLAGE_WORK` blocks a *fresh* discretionary selection | Candidate/path disposable before mutation; crop episode owns exceptional repair | One visible bounded job, then re-resolve |
| Host `HarvestCropsGoal` (stock SPM) | **Priority 6**, `wantsFood()`-gated | MOVE/LOOK | Yes — and *would* destroy a managed crop precisely while V3 is refused for hunger | none | Vetoed inside the managed crop domain only; unchanged in the wilderness (`D-VR-079-A1`) |
| Storage guard | Continuous policy (`D-VR-081`), not a competing goal | none | Vetoes host loot admission/continuation regardless of activity | Permission survives unload/restart until revoked or container gone | Ally never opens/continues denied container |

Semantic ordering is **locked** (`D-VR-082`, amended `D-VR-082-A1`): urgent > mandatory pending/running > committed cleanup >
discretionary village work. V3 goals at priority **4** sit below gather/craft/smelt/trade at **3**
(`SpmScavenger.java` deliberate-work band) and above explore/wander at **8**.

#### Time and feedback simulation

| Time | Expected physical loop | Re-evaluation / invalidation |
| --- | --- | --- |
| `T0` | No urgent/mandatory owner; bounded facts expose one crop/food candidate; future shared D2 may expose a workstation candidate | Profile, permission, reserves, evidence age/completeness, loaded target and path preconditions checked |
| `T+10` | Mob begins path; player sees one intelligible job target | Combat/command/mandatory demand cancels path with no world mutation |
| `T+60` | At reach, crop or food episode revalidates once; future compost consumes one eligible unit even if its probabilistic level roll does not advance | Moved villager, changed crop, lost reserve, stale POI, full/invalid composter aborts cleanly; unchanged level must not loop |
| `T+200` | Job terminates; authority and needs are re-resolved before another village action | No remembered Path, stale entity, or “work because work happened” appetite survives |
| `T+1200` | Repeated work remains bounded by real deficits/surplus and perception cadence | Empty/blocked scans back off; multiple mobs lose stale candidates rather than duplicate mutation |

#### Workstation/population/compost contention

- Workstation awareness is factual evidence only. It may report a transient restock block; it never
  grants trade permission, assigns a profession, or owns movement.
- Population support revalidates population need, chosen villager, path, and disposable food at
  handoff. Another mob satisfying the deficit invalidates the candidate.
- Composting is last in reserve authority: progression, survival, committed replant, and population
  allocations all win. UNKNOWN disposability refuses rather than consuming.
- All three are blocked while mandatory progression is pending, even when no mandatory executor is
  currently running.

#### Predicted weird behaviors and falsifiers

| Weird behavior | Classification | Required falsifying/confirming probe |
| --- | --- | --- |
| Two mobs path to one crop; second arrives after first replants and tries to harvest age 0 | `RUNTIME_QUESTION`, bounded by arrival revalidation | VR-T3k: observe second abandon/reacquire without breaking replacement |
| Repeated population changes cause approach→abort cycles near villagers | `RUNTIME_QUESTION` | VR-T3e multi-mob run: measure retries and require bounded backoff/no food loss |
| Composter/workstation perception dominates scans at 50+ mobs | `RUNTIME_QUESTION` | Profile query counts/tick cost at 1/10/50/100 mobs before any performance claim |
| Low-chance compostable is consumed but the level does not rise | `ACCEPTABLE_STEPPING_STONE` only for one attempt plus cooldown; this is vanilla semantics | VR-T3d: exactly one debit and termination, not rollback or immediate retry |
| Composter reaches level 8 and remains ready because gen-1 does not extract | `ACCEPTABLE_STEPPING_STONE`, bounded visible world state | VR-T3d: farmer/player may extract; SPM creates no phantom bone-meal demand |
| Two mobs select one composter and insert sequentially | `RUNTIME_QUESTION` | VR-T3d multi-mob: each COMMIT revalidates level/reserve; no double debit or stale loop |
| Managed crop is harvested, combat fires, field remains bare while mob fights/explores | `ARCHITECTURE_DEFECT` | VR-T3b/c must make this impossible in the normal path and retain bounded repair after exceptional failure |
| Ally opens UNKNOWN container because classifier returned no row | `ARCHITECTURE_DEFECT` | VR-T3h must prove UNKNOWN denial in admission and continuation |

**Must happen:** one managed harvest visibly ends with an age-0 crop at the same position; pending
mandatory progression prevents new village work; explicit storage permission and denial are both
observable and revalidated.

**Must not happen:** a successful harvest routinely leaves bare farmland; HOME/HIGH or Opinion
creates ally/storage permission; UNKNOWN permits loot; village work suppresses pending mandatory
progression; awareness scans load chunks or run unbounded every tick.

**MAIBS-1 result (synchronized 2026-08-22):** tasks 52–58 have statically discharged the original
architecture holds including V3-F compost reserve/transaction policies. Runtime remains
`UNVERIFIED`. V3 cannot **close** until task-59 / V3-G executes the batched VR-T3 campaign.

VR-T3a–m are the falsifying runtime family (**VR-T3f non-applicable** while V3-D2 deferred); no
runtime launch is authorized by this RFC pass.

### V5 — raid interrupt + resume

| Minute | Predicted observable | Failure mode |
| --- | --- | --- |
| 0–3 | Iron demand active; mob mining/gathering | — |
| 3–4 | Raid starts at home; mob paths to bell OR engages raider | Ignores raid; keeps mining |
| 4–8 | Combat vs `Raider`; no villager melee | Shoots villager; golem aggro |
| 8–10 | Coward profile: SCR-2R5 shelter interior | Stands in open doorway |
| 10–12 | Raid victory; collects drops | Loots villager chest first |
| 12–15 | Resumes iron demand if still valid | Starts unrelated explore |

**Strongest objection:** `RaidContainersGoal` at priority 3 may win post-raid unless VR-20 ships with V5.

### V2 — trade adapter (pre-implementation)

| Minute | Predicted observable | Failure mode |
| --- | --- | --- |
| 0–2 | `MaterialDemand` / chain ticket active; mob paths to familiar anchor | No demand; idle explore |
| 2–4 | `inspectOffers` returns snapshot; no GUI; `VILLAGE_TRADE` taxonomy | Client menu flash; `UNKNOWN_ACTIVE` suppresses Opinion |
| 4–6 | Picks offer matching demand; farmer/librarian soft match | Hardcoded profession bias |
| 6–8 | Atomic trade: backpack −input, +output; `notifyTrade` once | Duplication or voided items; double use increment |
| 8–12 | Chain step 2 or leaves villager; night → `BLOCKED` defer | Busy-waits blocking shelter |
| 12–15 | Demand ticket deficit decreases; familiarity bump via `onTradeEpisode` | Trades junk offers; greet mistaken for trade |

**Must not happen:** `FriendlyGreetGoal` crouch-gift mistaken for trade completion; smelt consumes chain sell inputs; emerald surplus without downstream consumer.

### V5b — dusk raid vs night shelter (`PROPOSED` — D-VR-018)

| Minute | Predicted observable (ALLY + HOME_VILLAGE) | Failure mode |
| --- | --- | --- |
| 0–1 | Sun touches horizon; raid active at home | Enters bed anyway |
| 1–3 | Paths to bell or raid edge; no new shelter adoption | Keeps mining off-site |
| 3–8 | Fights or SUPPORT; shelter hold cancelled if combat | Sleeps through wave 1 |
| 8–12 | Raid ends; if still dusk window, may seek shelter | Immediately loots chest (VR-20) |
| 12–15 | Dawn; resumes deferred trade ticket if valid | Forgets interrupted demand |

**Must not happen:** coward profile ordered to DEFEND by matrix — profile gate must win (row 3 vs 2).

---

## Topic: Deferred / unverified

| Item | Status |
| --- | --- |
| Full hero-trade optimization | **NOT PRACTICAL** gen-1 |
| Multi-village empire | **NOT PRACTICAL** |
| Villager breeding **automation** (micro-manage AI) | **Deferred** — population **support** in V3 is **PARTIAL** |
| Iron golem army directing | **NOT PRACTICAL** |
| `Raid.addHero(Entity)` full reward path | **CODE_CONFIRMED** — `Raid#tick` awards to any `LivingEntity` via `level.getEntity(uuid)`; runtime still `UNVERIFIED` (`Agent_Claude` F1) |
| Hero **discount** for a non-player | **BLOCKED** in vanilla — `updateSpecialPrices(Player)`; must be applied by `VillagerTradeAdapter` (B-VR-34) |
| A non-player **consumer** of villager reputation | **UNVERIFIED** — probe before V4 reputation-aware selection or V6 discounts; explicitly **not a Village Work V3 gate** (B-VR-36) |
| `PoiManager` unloaded-chunk leakage | **V1-R4 `ACCEPTED`** — dual pipeline; coverage independent of `getInRange` unloaded records; query cost `UNVERIFIED` (B-VR-58) |
| V1-R4 `PerceptionCoverage` | **`ACCEPTED`** — shipped 1.9.5; runtime VR-T1 partial **CONFIRMED** |
| V1 perception **driver** | **RUNTIME CONFIRMED** (Bob, 2026-08-14) — debounce `Long.MIN_VALUE` overflow fixed; `ensureVillagePerceptionObserver` on reload |
| `MaterialDemandPolicy` class name | **NOT FOUND** — ship trade via `WorkDemandPolicy` facade (B-VR-20) |
| Storage RFC (full personal/village chest system) | **Deferred** — `StorageOwnership` minimum in V3 |
| Runtime VR-T* tests | VR-T1A **PASS** (2026-08-14). VR-T1b 10/50/100-mob profiling **DEFERRED** (performance backlog). Temporary `village-probe` / `village-driver` / `village-memory` commands **REMOVED** post-VR-T1A |
| V2-TE absent/incompatible runtime (`VR-T2l`) | **DEFERRED / NON-BLOCKING** — positive compatibility path is closed; negative run does not reopen V2 or block V3 |
| V2-I trade inspector | **DEFERRED / NON-BLOCKING OPTIONAL TOOLING** — no new debug command and no phase dependency |
| V2 market performance profiling | **DEFERRED / NON-BLOCKING** — code now rejects viable existing routes before market discovery, carries one Q1 attempt into `start`, and throttles empty scans by demand key; actual tick/allocation gains and dense-villager scaling remain `UNVERIFIED` without a predefined profiler scenario |
| V2 dense-villager merchant window | **DEFERRED** — spatial radius is bounded but merchant count is not; implement only if profiling shows meaningful spikes, not as a hidden V3 gate |
| V2 runtime-registry proactive expiry sweep | **DEFERRED LOW SEVERITY** — normal lifecycle cleanup is wired; lazy expiration is not an independently scheduled physical prune |
| V2-W Wealth trading | **DEFERRED / NON-BLOCKING branch** — `D-VR-076` sequence is local to Wealth (`V2-TE → V2-W → portfolio evolution`), never a prerequisite for Village Work V3 |
| 48-block village identity radius | **UNVERIFIED** — our judgement, no vanilla constant exists. Upgrade path (POI-set overlap) designed and deferred pending runtime evidence (D-VR-022) |
| Mobs removed without **any** lifecycle event, or in a dimension absent from `getAllLevels()` | **BOUNDED, not eliminated** — held by `MAX_TRACKED_MOBS` (256/dimension), which warns when it fires (D-VR-023) |
| Monotone anchor following over a long observation sequence | **DOCUMENTED LIMITATION** — replacement tracks the newest equally-good view; separating "rebuilt" from "looks rebuilt" needs POI-set-overlap identity (D-VR-022). VR-T1 must report whether real sequences produce this shape |
| Place opinion at current anchor chunk | **ACCEPTED** (D-VR-026 **HELD**) — geographic fidelity; cross-chunk anchor drift may split Place history |
| Settlement-persistent preference across anchor moves | **SETTLEMENT evidence** — not solvable via frozen chunk key (B-VR-42 **REJECTED**) |
| Vertically spread settlement splits into two | **VR-T1 SCENARIO** — `distSqr` is 3D; 30 horizontal + 40 vertical exceeds the 48 sphere. Not fixed on speculation: 2D would diverge from `RaidAssociationPolicy`, which is 3D because vanilla is (User, 2026-08-14) |
| Reflection candidates | "structural tests must encode semantics, not incidental code shape"; "unload ≠ permanent removal" as a shared lifecycle rule **once a second persistent per-mob system needs it** — no framework built prematurely (User agreed, 2026-08-14) |
| TACZ / vehicle mods | Out of scope |
| PlayerMob-as-villager lifecycle | **Rejected** (`D-VR-004`) |
| Exploit-optimized trading hall AI | **Rejected** — emergent arbitrage only |
| Iron golem as village centroid heuristic | **Deferred** (B-VR-12) |
| GAO SOCIAL village browse | **→ V1.5-D** — discretionary SOCIAL weighting near familiar anchor (B-VR-65) |
| SETTLEMENT Opinion subject | **Deferred** — reopen if mob needs same-settlement pref when anchor geography moves (D-VR-026 **HELD**; B-VR-39) |
| Ominous Bottle RAID intent targeting home village | **PRODUCT DECISION** open; default refuse (B-VR-14 / D-VR-028) |
| `SettlementTier` decomposition | **OPEN architecture gate before V4/V5** — map consumers and saved-data migration before splitting home/economic/safety dimensions |
| Ominous Bottle drink near ally village | **PRODUCT DECISION** — default refuse for `VILLAGE_ALLY` (D-VR-028) |
| Raid EVACUATE threat hysteresis | **Deferred** — only if shelter flap observed (B-VR-21) |
| Nearby non-home raid night commute | **PRODUCT DECISION** — matrix row 6 (day/night topic) |
| Multi-night raid bed allowance | **PRODUCT DECISION** — between-waves sleep (day/night topic) |

---

## Topic: Decisions

### D-VR-001: Target is vanilla village/raid, not SPM “raid”

**Status:** `LOCKED`  
**Accepted:** Village RFC means `MerchantMenu` + `Raid` event.  
**Rejected:** Equating `RaidContainersGoal` with illager raids.

### D-VR-002: Player parity for Omen/Hero requires bridge — **SPLIT**

**Status:** `CONSENSUS`, amended by `Agent_Claude` 2026-08-14 — **Omen and Hero are not one problem**
**Accepted (amended):** the two halves have opposite difficulty and must not share a decision.

| Half | Gate | Cost | Owner |
| --- | --- | --- | --- |
| **Hero credit** | one `EntityType` comparison in `Raider#die`; the award loop already accepts any `LivingEntity` | **narrow mixin** | D-VR-020 |
| **Omen / raid initiation** | `ServerPlayer`-typed all the way down, incl. `setRaidOmenPosition` declared on `ServerPlayer`; `getOrCreateRaid` is `private` | **`MIXIN_FRAGILE`, V6+** | D-VR-002 (this) |

**Evidence:** `Raider#die` offsets 39–58; `Raid#tick` offsets 617–742; `Raids#createOrExtendRaid`
signature + `getOrCreateRaid` access flags; `ServerPlayer#{set,get,clear}RaidOmenPosition`. All from
the pinned 1.21.1 merged jar.
**Consequence for planning:** treating these as one item is what put hero credit in V6. See B-VR-35 —
the phase order at the raid end is inverted, and that is an open **product decision**.

### D-VR-003: Integration surface

**Status:** `LOCKED`  
**Accepted:** `spmscavenger` addon + capabilities; reuse SPM combat/social.  
**Rejected:** SPM source fork; datapack-only trade.

### D-VR-004: Human-player interaction parity, not villager lifecycle

**Status:** `LOCKED` (`Agent_ChatGPT`)  
**Accepted:** PlayerMob **interacts with** village system like Steve; no profession/workstation claim/gossip brain.  
**Rejected:** `PlayerMob` → villager entity parity.

### D-VR-005: `VillagerTradeAdapter` without fake GUI

**Status:** `LOCKED` (`Agent_ChatGPT`; amended `Agent_Cursor`; peer-reviewed `Agent_Codex` 2026-08-15)
**Accepted:** Server-side `inspectOffers` / `performTrade` via `MerchantOffer` + `notifyTrade`; `TradeWithVillagerGoal` executor. Gen-1 **without** `MerchantMenu` or `setTradingPlayer`.
**Rejected:** Client menu simulation for autonomous mobs; mandatory mixin for core single-hop trade.

### D-VR-006: `VillageInteractionDirector` orchestration

**Status:** `CONSENSUS` (`Agent_ChatGPT`)  
**Accepted:** Perception → Director → utility → executor goals; composable raid defense.  
**Rejected:** Monolithic `DefendVillageGoal`; Brain migration.

### D-VR-007: Trade evaluation via `MaterialDemandPolicy`

**Status:** `CONSENSUS` (`Agent_ChatGPT`)  
**Accepted:** Demand-driven offer scoring; trade as `AcquisitionStrategy`.  
**Rejected:** Hardcoded profession preferences (`librarian = good`).

### D-VR-008: Bell parity via entity ring API

**Status:** `LOCK RECOMMENDED` (`Agent_ChatGPT` + mapping verify)
**Accepted:** `RingVillageBellGoal` using **`BellBlock.attemptToRing(Entity, …)`** — entity initiator not `Player`-typed.
**Rejected:** Player-only bell assumption; colloquial `ring` without Mojmap name in implementation.

### D-VR-009: Advanced village site selection (`CONTESTED` — detection half)

**Status:** `CONTESTED` (`Agent_Claude`, 2026-08-14) — **scoring half endorsed, detection half contested**
**Accepted (peer-reviewed, endorse):** settlement tiers, bounded `VillageSiteScore`, home anchor NBT, no `/locate`.
Two viable alternatives were compared, objections were answered, and the score is bounded and testable.
**Contested (`Agent_Claude`):** the *detection* mechanism — “≥2 villagers + ≥3 beds OR bell + workstation,
merge clusters by anchor distance”. Superseded by **D-VR-019**: vanilla already defines a village as a
`PoiTypeTags.VILLAGE` + `IS_OCCUPIED` query, and the raid system uses that definition to place
`Raid.getCenter()`. A hand-rolled centroid can disagree with the raid centre by an unbounded distance,
**silently disabling D-VR-010** with no crash and no log line.
**Rejected:** nearest-chunk default; full vanilla POI *graph* clone (still rejected — D-VR-019 uses the
flat query, not the graph).
**Resolved 2026-08-14 (User).** D-VR-019 accepted and locked; the detection half of this decision is
**`SUPERSEDED`** by it. The scoring half — settlement tiers, `VillageSiteScore`, home anchor NBT — is
**`LOCKED`**. V1 ships the tier vocabulary and the `HOME_VILLAGE` identity; utility-driven promotion
and demotion remain V4, because their inputs (affinity, remembered traders, raid history) do not exist
yet and inventing them now would mean scoring zeros dressed as judgements.

### D-VR-010: Raid interrupt via `TaskLifecycle` snapshot

**Status:** `LOCKED` (`Agent_Cursor` proposed; **independent peer review `Agent_Claude` 2026-08-14**)
**Review:** the `MiningProject` → `MiningProjectEnd.COMBAT` → resume precedent is shipped and tested,
two alternatives were considered, and the snapshot/revalidate shape avoids the stale-ticket failure.
**Lock is conditional on D-VR-019**: the trigger `getRaidAt(homeVillageAnchor)` is only meaningful if the
anchor agrees with `Raid.getCenter()` (VR-23). Without it this decision is correct and inert.
**Accepted:** `RaidTask.previousTask` + revalidate demand on victory — mirror `MiningProject` COMBAT interrupt.
**Rejected:** Ad-hoc goal cancel without resume ticket.

### D-VR-011: Raid `EVACUATE` reuses shelter commitment

**Status:** `LOCKED` (`Agent_Cursor` proposed; **independent peer review `Agent_Claude` 2026-08-14**)
**Review:** reuse of SCR-2R5 over a new `HideInHouseGoal` is correct under SPM-2, and the B-VR-21
self-limiting interaction (raider aggro ejects the coward via `ShelterThreatPolicy`) is documented rather
than papered over. The hysteresis risk stays open as a runtime watch item, not a blocker.
**Accepted:** SCR-2R5 `SeekShelterGoal` interior cells for coward profile during raid waves.
**Rejected:** New standalone `HideInHouseGoal`.

### D-VR-012: Ally chest gate on `RaidContainersGoal` predicate

**Status:** `LOCKED`, amended by `D-VR-017` (original lock: `Agent_Cursor`; independent peer review
`Agent_Claude` 2026-08-14; authority conflict resolved by User synchronization directive 2026-08-19)
**Review:** a profile-gated predicate rather than a global goal strip is the right width, and it matches the
shipped `FriendlyGreetShelterHoldMixin` precedent. **GVC-5 applies**: the gate must be evaluated
continuously, not once on village entry — SPM mobs pick up and re-evaluate constantly, so a one-shot
check is a filter, not a guard.
**Accepted:** the one canonical predicate is `D-VR-017`: `VILLAGE_ALLY` may use the host loot goal
only when `StorageOwnership` carries explicit permission for that mob. `VILLAGE_PUBLIC`, `FOREIGN`,
and `UNKNOWN` deny. Evaluate in both `canUse` and `canContinueToUse`; revoked permission stops and
closes the host container through ordinary goal teardown.
**Rejected:** Disabling SPM loot goal globally; silent mixin strip without profile.

### D-VR-013: Mojmap bell API name (`CONFIRMED`)

**Status:** `LOCKED` (`Agent_Cursor`, mapping verify 2026-08-14)
**Accepted:** Implementation references `BellBlock.attemptToRing(Entity, Level, BlockPos, Direction)`.
**Rejected:** `BellBlock.ring` alias in production code without comment mapping to Mojmap.

### D-VR-014: Mojmap hero registration API (`CONFIRMED`)

**Status:** `LOCKED` (`Agent_Cursor`, mapping verify 2026-08-14)
**Accepted:** Hero credit bridge calls `Raid.addHeroOfTheVillage(Entity)`.
**Rejected:** Assuming hero registration implies full Hero-of-the-Village effect parity without V6 runtime proof.
**Evidence upgraded** (`Agent_Claude` 2026-08-14, `CODE_CONFIRMED`): registration is a bare
`heroesOfTheVillage.add(entity.getUUID())`, and the victory loop resolves it with
`ServerLevel.getEntity(uuid)` + `instanceof LivingEntity` — so the **effect** genuinely applies to a
PlayerMob. The caution this decision recorded was still correct for the **discount**, which remains
player-typed (`updateSpecialPrices(Player)`) and must be applied by `VillagerTradeAdapter` (B-VR-34).
Implementation shape is now D-VR-020.

### D-VR-015: Trade demand via `WorkDemandPolicy` facade (`Agent_Cursor`)

**Status:** `LOCKED` — amended User peer review 2026-08-15
**Accepted:** V2 trade evaluation extends existing `WorkDemandPolicy.MaterialDemand` + `consumerKey`
vocabulary without renaming the policy. For each consumer need (e.g. `ScavengerCrafting.ConsumerRecipeSpec`
iron pickaxe), derive **parallel acquisition candidates** — PROCESS/SMELT/CRAFT **and** TRADE only when
current evidence supports a reachable route with a useful offer. **`WorkDemandPolicy.select()` chooses
among feasible candidates only**; a TRADE candidate must not win and become the sole P3 owner if no loaded
villager can satisfy the demand (would wrongly suppress a valid smelt/craft path). Trade-owned demands use
`consumerKey` namespace `spmscavenger:trade_chain/<id>` **or** explicit `WorkType.TRADE_CHAIN`;
`TradeDemandGate` admits only when the **winning** demand is trade-owned.
**Rejected:** Blocking V2 on `MaterialDemandPolicy` rename; parallel emerald goals without arbitration;
registering TRADE before feasibility exists; a dedicated P3 trade goal that ignores `select()` winner.
**Cross-RFC:** heads toward future `AcquisitionStrategy` model — same consumer key, multiple routes.

### D-VR-016: Shelter threat policy is gen-1 raid combat override (`PROPOSED`)

**Status:** `PROPOSED` (`Agent_Cursor`)
**Accepted:** `ShelterThreatPolicy.NEARBY_HOSTILE` (Raider as `Enemy`) ejects coward EVACUATE — no parallel raid threat system gen-1.
**Rejected:** Disabling shelter override during raids globally (would trap mobs in beds while pillagers kill villagers).

### D-VR-017: `VillageScenarioProfile` gates ally behaviour (`LOCKED`, implemented/static accepted)

**Status:** `LOCKED` for authority semantics (User synchronization directive, 2026-08-19);
production wiring per **D-VR-080** (profile store) and **D-VR-081** (permission registry).

**Accepted:** `VILLAGE_ALLY` is the sole ally-policy authority for VR-20 storage suppression, trade
fairness, and later raid DEFEND priority. Settlement attachment (`HOME` / HIGH familiarity) is factual
relationship input and **never** permission by itself. Storage permission is independently factual:

```text
profile != VILLAGE_ALLY
    → D-VR-017 adds no restriction; existing host policy remains authoritative

profile == VILLAGE_ALLY
AND ownership in {MOB_OWNED, EXPLICITLY_SHARED_WITH_MOB}
    → storage policy may permit the existing executor, subject to all other host gates

profile == VILLAGE_ALLY
AND ownership in {VILLAGE_PUBLIC, FOREIGN, UNKNOWN}
    → refuse admission / continuation (fail closed)
```

**Production truth (`CODE_CONFIRMED`, synchronized 2026-08-21):** task-53 shipped
`VillageScenarioProfile`, cross-dimension `PlayerMobVillagePolicySavedData`, operator profile commands,
and `VillageWorkAdmission`; task-54 shipped `StorageOwnership`, its permission registry/policy, and the
continuous `RaidContainersAllyStorageMixin` guard. The 2026-08-19 three-probe absence finding is
preserved as historical pre-implementation evidence and is **SUPERSEDED** by those implementations.
Runtime VR-T3g–i remains `UNVERIFIED`; static acceptance is not runtime proof.

**Rejected:** per-mob hardcoded village UUID allowlists; HOME/HIGH as ally permission; treating
missing ownership evidence as public or permitted; globally stripping `RaidContainersGoal`; storing
`VillageScenarioProfile` in dimension-local `MobVillageMemory` (`D-VR-080`).

### D-VR-018: Day/night director arbitration (`PROPOSED`)

**Status:** `PROPOSED` (`Agent_Cursor`)
**Accepted:** `VillageInteractionDirector` owns a read-only `VillageDayNightContext` and gen-1 priority matrix; home-village active raid preempts new night-shelter adoption; trade defers when villagers unavailable at night; reuse `SeekShelterGoal` dusk constants and `ShelterInterruptionPolicy` — no parallel night director.
**Rejected:** Duplicate `SeekShelterGoal` scheduling inside village package; global reclassification of all night combat as village raid.

---

### D-VR-019: One canonical settlement coordinate system (`Agent_Claude`)

**Status:** `LOCKED` (`Agent_Claude` proposed; **User accepted and strengthened, 2026-08-14**)
**Implemented:** V1 — `village/` package, 28 tests, 4 negative controls.

**Accepted (four-part contract, as strengthened by the User):**

```text
Village membership:
    vanilla PoiTypeTags.VILLAGE
    + IS_OCCUPIED

Perception boundary:
    loaded/perceivable chunks only
    no chunk loads
    no persisted-unloaded POI knowledge

Canonical anchor:
    reproduce vanilla raid-center derivation
    from the bounded POI set

KnownVillage:
    stores that canonical anchor
```

**The User's strengthening was the load-bearing part.** My proposal said "derive from that POI
centroid — the same query". Same input predicate does **not** imply same output coordinate: any
section conversion, dedup, averaging or centring vanilla performs must be reproduced, or the
anchor is subtly wrong in exactly the way this decision exists to prevent. Reading
`Raids#createOrExtendRaid` at offsets 72–171 settled it — vanilla does **no** section conversion
(the hypothesis was wrong) but does four things a natural rewrite gets wrong, each a silent
one-block error:

| # | Vanilla | The idiomatic rewrite | Consequence |
| --- | --- | --- | --- |
| 1 | accumulates `p.getX()` (raw int) | `Vec3.atCenterOf(p)` | every component biased `+0.5`; survives the floor |
| 2 | `BlockPos.containing` = **floor** | `Math.round` | agree on positive coords, differ on negative — invisible in a world built at spawn |
| 3 | Y participates in the average | XZ-only anchor | changes the `distSqr` that `getNearbyRaid` compares |
| 4 | duplicates significant (one record per POI) | dedupe "to be tidy" | 20 beds vs 3 workstations is a real 20:3 weighting |

The single sentence "derive from the POI centroid" would have passed review and shipped three of
these four. **Reproduce, do not resemble** is now the written contract, and each property has a
regression test that computes the wrong answer alongside the right one.
**Anchor is `VillageAnchorPolicy.anchorOf`.**
**Why:** correctness first, elegance second. Agreement with the raid centre is what makes D-VR-010 fire at
all; tag-extensibility (SPM-0 level 3–4) is the bonus.
**Rejected:** hand-rolled bed/villager cluster heuristics (enumerate what a tag expresses, and drift from
the raid centre); the full vanilla POI *graph*; `/locate`.
**Hard constraint — a construction invariant, not a test (User, 2026-08-14):**

```text
VillagePerception may inspect remembered POI storage,
but MUST NOT admit a POI into perception unless its
chunk is currently within the allowed loaded/perception boundary.
The check itself must not cause a chunk load.
```

**Memory/storage availability is not perception** — the same philosophy as hidden ore in Mining
Intelligence: the server knows it is there; the mob does not necessarily know it is there.
Enforced structurally, not by convention: `VillagePerception` holds the addon's only `PoiManager`
reference (asserted across the whole package), the raw record stream never escapes the method, and
every record passes `withinPerception` before an `Observation` exists. The check is
`ServerLevel#hasChunk` on section coordinates — it resolves against the loaded chunk map and
cannot trigger a load or generation, so asking the question cannot manufacture its own answer
(same rule as D-GAO-057).
**Identity ownership superseded by D-VR-022:** D-VR-019 owns village POI membership, the loaded
perception boundary and the canonical raid-compatible anchor derivation. It does **not** own cognitive
settlement merging. `VillageIdentityPolicy` uses the separately locked 48-block working radius;
`RaidAssociationPolicy` alone uses vanilla's `9216` (96²) radius and may associate one raid with
multiple remembered villages. Do not restore a 96-block `KnownVillage` merge in the name of raid
compatibility.
**Evidence:** `Raids#createOrExtendRaid` offsets 44–171; `Raids#getOrCreateRaid`;
`ServerLevel#getRaidAt` (offset 5, `sipush 9216`); `village.json` POI tag. All from the pinned jar.
**Would change the perception design:** a measured `PoiManager` query cost exceeding alternatives at
50+ mobs. **Would change cognitive identity:** runtime duplicate-village evidence belongs to
D-VR-022 and its POI-overlap upgrade path, not this decision.

### D-VR-022: Village identity is ours; raid association is vanilla's (`Agent_Claude` + User)

**Status:** `LOCKED` (User review, 2026-08-14) — **splits** the identity half out of D-VR-019
**Accepted:** `VillageIdentityPolicy` (48 blocks, cognitive, ours) and `RaidAssociationPolicy`
(`9216`, vanilla-compatible, must not drift) are separate concerns. A raid may cover several
remembered villages without collapsing them into one identity; `associatedVillages` returns all
matches.
**Rejected:** one radius for both (the shipped V1 behaviour) — it made a HOME_VILLAGE and a
TRADING_POST 85 blocks apart unrepresentable, and every identity-keyed feature downstream would have
inherited that.
**`UNVERIFIED`:** the 48-block value itself. Vanilla has no settlement-identity constant, so this is
necessarily our judgement. Erring small is the recoverable direction — duplicates are visible,
collapse is not.
**Would change my mind:** runtime evidence of duplicate entries inside one real village → move to
POI-set-overlap identity (already designed, deferred for lack of evidence).

### D-VR-023: Unload parks, only removal deletes (`Agent_Claude` + User)

**Status:** `LOCKED` (User review, 2026-08-14) — repository-wide lifecycle rule for this addon
**Amended V1-R2 (User review):** the *reason*, not the event, is the decision.

```text
persisted semantic memory is deleted  <=>  RemovalReason.shouldDestroy()
                                           (KILLED, DISCARDED)
```

**Accepted:** `ENTITY_UNLOAD` may delete, but only when `shouldDestroy()` is true; `AFTER_DEATH` is
the other call site. Chunk unload, player-unload and dimension change preserve memory.
**Rejected (twice, for different reasons):** deleting on any unload — Fabric fires it for any entity
leaving a world; **and** a staleness TTL over memory age — `lastTouchedTick` measures memory freshness,
not owner existence, so an alive mob that spends a month away from villages would lose its home at the
next restart. RET-1 demands a bound, not a lifecycle violation, and both attempts produced a bound
that deleted the feature.
**Amended again V1-R3 (User review):** the deletion's **extent** is every dimension, not the one
holding the entity.

```text
memory is per-dimension   +   a mob is not   =>   permanent removal sweeps all levels
```

Villages are an Overworld feature and PlayerMobs die in the Nether, so a per-level `forget` leaked on
the *common* path. The UUID survives `CHANGED_DIMENSION` (`restoreFrom` copies all NBT but
`"Dimension"`; `saveWithoutId` writes `"UUID"`), which is simultaneously why the dimension change must
preserve memory and why the eventual delete must be global. The sweep uses the **non-creating**
`DimensionDataStorage#get`, so cleaning up cannot materialise memory files for dimensions that never
had one.

**Residual, bounded not eliminated:** a mob removed with no lifecycle event at all, or a dimension
absent from `getAllLevels()` at removal time. Held by `MAX_TRACKED_MOBS` = 256/dimension, which warns
when it fires and whose victim ordering is an acknowledged heuristic.
**Corollary locked:** memory decay, if ever wanted, is a **cognition policy** with its own design and
player-visible behaviour — never garbage collection.
**Evidence:** `Entity$RemovalReason` constructor flags (KILLED/DISCARDED `destroy=true`; the three
UNLOADED/CHANGED reasons `false`); `Entity#setRemoved` sets `removalReason` (offset 9) before
`levelCallback.onRemove` (offset 45), and Fabric's event fires downstream. Pinned 1.21.1 jar.
**Generalises:** any future per-mob `SavedData` in this addon inherits both halves of this rule.

### D-VR-024: Anchor evidence is coverage, not count (`Agent_Claude` + User)

**Status:** `LOCKED` — **closed by V1-R4 acceptance** (User, 2026-08-14)
**Accepted:** anchor replacement uses **`PerceptionCoverage`** (`loadedColumns`/`totalColumns` in the
64-block footprint), not POI quantity; equal coverage + newer tick replaces; cross-multiply compare.
**Implementation lock:** coverage computed independently of `getInRange()` unloaded records; persist
ints not float ratios; optimistic full-coverage NBT migration for pre-R4 rows.
**Rejected:** `newPoiCount > oldPoiCount`; admitted-count supersede; withheld/hidden POI in supersede.

### D-VR-025: Village factual utility vs Opinion preference (`User` + `Agent_Cursor`)

**Status:** `LOCKED` (User, 2026-08-14)
**Accepted:**

1. `VillageInteractionDirector` scores **legality and need fit** (`FactualVillageUtility`).
2. Opinion supplies **bounded soft bias** via `SettlementOpinionBias.request(...)` in the Opinion
   package — village code **consumes** `int` bias only; no direct personality/affect math in village.
3. **Candidate pool** = `MobVillageMemory.remembered()` settlements — persists while chunks unloaded;
   `VillagePerception` is a **refresh** path when present, not a membership gate.
4. Central rule: *preference does not create permission* (`docs/wiki/Opinion-System.md`).

**Rejected:** folding sociability/stress into `VillagePerception`; Opinion veto of the only legal trade
source when `MaterialDemand` is blocking; requiring current perception for remembered-village selection.

### D-VR-026: Place opinion at current anchor geography; SETTLEMENT deferred (`User` + `Agent_Cursor`)

**Status:** `HELD` (User amendment, 2026-08-14) — frozen chunk key rejected; geographic Place retained
**Accepted:**

1. Place learning and ranking use **current** `KnownVillage.anchor()` chunk — geographic fidelity
   (`PlaceOpinionRouteRanker.destinationBias` at current anchor coordinates).
2. `SettlementOpinionBias` composes Opinion-owned personality/affect/Place math; village consumes
   bounded `int` only (`D-VR-025`).
3. **SETTLEMENT** Opinion subject remains **deferred**. Anchor-cross-chunk drift may split Place
   history from settlement identity; factual `MobVillageMemory` (tier, home, trade) carries identity.
4. Reopen SETTLEMENT when runtime needs *"I like this same village regardless of anchor movement"* —
   that is evidence for a real settlement subject, not a chunk-key encoding.

**Rejected:** immutable **`placeOpinionChunkKey`** (conflates Place with settlement ID);
SETTLEMENT gen-1 without evidence; Place-entry migration on anchor supersede (defer).

### D-VR-020: Hero credit by widening one type check (`Agent_Claude`)

**Status:** `PROPOSED` (`Agent_Claude`, 2026-08-14) — narrows D-VR-014's implementation, does not reopen it
**Accepted:** VR-11 ships as a single mixin on `Raider#die` widening
`killer.getType() == EntityType.PLAYER` to admit `PlayerMobEntity`. Vanilla's `Raid#tick` then awards
`HERO_OF_THE_VILLAGE` to any `LivingEntity`, with vanilla duration, amplifier, spectator check and
persistence.
**Rejected:** reimplementing the reward loop; injecting into `Raid#tick`; awarding the effect ourselves.
**Dependency:** the discount half is player-typed (`updateSpecialPrices(Player)`), so the benefit is only
realised through `VillagerTradeAdapter` (D-VR-005). If D-VR-005 is ever replaced by a GUI-driven design,
this decision loses most of its value and must be revisited.
**Evidence:** `Raider#die` offsets 39–58; `Raid#tick` offsets 617–742; `Raid#addHeroOfTheVillage` 0–14.

### D-VR-021: Reputation is native; expose, do not bridge (`Agent_Claude`)

**Status:** `PROPOSED` (`Agent_Claude`, 2026-08-14) — supersedes VR-4's “REQUIRES API” framing
**Accepted:** the gossip **write** path already accepts any `Entity` and is running today with no addon
code. V4 reputation-aware selection / V6 discounts need only an accessor onto `Villager.gossips` to
**read** the mob's own reputation. Canonical Village Work V3 has no reputation consumer or probe gate.
**Rejected:** a reputation bridge, a UUID-mapping shim, or a parallel Scavenger-side reputation ledger
(SPM-2 duplication).
**Consequence to accept honestly:** B-VR-13 already happens — a PlayerMob that AOEs a villager is
accruing `MINOR_NEGATIVE 25` right now. The RFC previously listed this as a design risk to build; it is a
live behaviour to measure.
**Unverified:** whether any vanilla *consumer* of that reputation is non-player-typed. Until that probe
runs, the feature must not be described as “villagers remember you”.
**Evidence:** `Villager#onReputationEventFrom` offsets 0–116; `setLastHurtByMob` offsets 21–50
(the sole `instanceof Player` is a cosmetic particle broadcast); `tellWitnessesThatIWasMurdered` ungated.

### D-VR-027: Ominous Bottle strategic pickup with bounded retention (`User`)

**Status:** `LOCK RECOMMENDED` (peer-review amendment, 2026-08-14; P2 title cleanup pending)
**Accepted:** classify Ominous Bottles as **strategic/high pickup value when useful**, then apply a separate,
bounded retention/replacement budget using amplifier, active **event intent/demand** and backpack pressure.
**Rejected:** Auto-drink on pickup; treating bottles as undifferentiated junk; interpreting pickup rank as
unconditional retention of every amplifier stack.
**Evidence:** official 1.21 notes specify five variations and stack size 64; component-distinct stacks
can consume multiple slots, material in an eight-slot backpack.

### D-VR-028: Ominous Event policy owns consumption; Village contributes RAID intent (`User` + `Agent_Codex`)

**Status:** `REDESIGNED / SOURCE AUDIT COMPLETE`; bridge design remains `OPEN`
**Accepted:** cross-domain `OminousEventPolicy`; Village/Raid emits a target-bound RAID intent with a
600-tick Raid-Omen commit/abort policy. Bottle finishing uses vanilla LivingEntity behavior.
**Rejected:** Village globally owning bottle use; ally auto-drink in `HOME_VILLAGE`; captain kill
directly applying Bad Omen; blanket “bottle requires mixin” classification.
**Evidence:** pinned 1.21.1 method bodies: `OminousBottleItem#finishUsingItem` applies Bad Omen to any
`LivingEntity`; `BadOmenMobEffect` and `RaidOmenMobEffect` both gate their meaningful paths on
`ServerPlayer`. SPM `CommandedUse` does not prove targetless self-drink.

### D-VR-029: Two-step trade chains are first-class planner tickets (`User`)

**Status:** `LOCKED` (`User` + `Agent_Cursor`; amended/peer-reviewed `Agent_Codex` 2026-08-15)
**Accepted:** an external demand owns a bounded, expiring, revalidated SELL → BUY acquisition plan
with **disposable-quantity bounds** (`SellExpendabilityPolicy`, `D-VR-058`) and consumer identity. Gen-1 uses a transient `TradeChainPlan`; save/reload
closes neutrally and the still-valid external demand rebuilds from current world/offer state. Reuse a generic
`AcquisitionPlan` only if it satisfies the contract; `TradeChainTicket` is not mandated.
**Rejected:** Hardcoded single-hop trades only; selling last food; stale step execution; autonomous
arbitrage without an external demand.

### D-VR-053: Gen-1 trade execution is mixin-free (`Agent_Cursor`, 2026-08-15)

**Status:** `LOCKED` — peer-reviewed `Agent_Codex` 2026-08-15
**Accepted:** `VillagerTradeAdapter` uses public `MerchantOffer#satisfiedBy` / `#take`, `PlayerMobs.backpack`, and `AbstractVillager#notifyTrade` — no `MerchantMenu`, no `setTradingPlayer`.
**Rejected:** Treating all village trade work as **REQUIRES MIXIN**; fake-player menu driving.
**Evidence:** pinned 1.21.1 `MerchantOffer.java`, `AbstractVillager.java`, `Villager.java` (`updateSpecialPrices` private + player-only).
**Caveat:** hero discount and advancement criteria remain player-gated — V6 (B-VR-34).

### D-VR-054: `ActivityClass.VILLAGE_TRADE` taxonomy (`Agent_Cursor`, 2026-08-15)

**Status:** `LOCKED` — peer-reviewed `Agent_Codex` 2026-08-15
**Accepted:** `TradeWithVillagerGoal` reports `VILLAGE_TRADE`; pin in `MoveHolderClassifier` beside other village/economic classes. Scheduler occupant; not discretionary social.
**Rejected:** classifying trade as `SOCIAL_REFLEX` or `UNKNOWN_ACTIVE`; special-casing trade in `DiscretionaryEligibility` instead of taxonomy.
**Evidence:** V1.5 runtime — flagless/misclassified observers fail-closed entire Opinion director.

### D-VR-055: Gen-1 trades are demand-gated only (`Agent_Cursor`, 2026-08-15)

**Status:** `LOCKED` — amended/peer-reviewed `Agent_Codex` 2026-08-15
**Accepted:** `TradeWithVillagerGoal` runs only when `WorkDemandPolicy` (facade) or active `TradeChainPlan` supplies a `MaterialDemand` / step ticket. `TradeDemandGate` mutual exclusion at P3.
**Rejected:** always-on trade browse goal; parallel emerald-hoarding without `consumerKey`; priority-3 trade without demand arbitration.

### D-VR-056: Persistent `KnownVillager` memory (`Agent_Cursor`, 2026-08-15)

**Status:** `HELD / REMOVED FROM V2` — `Agent_Codex` peer review 2026-08-15
**Reason:** V2 can resolve loaded villagers and current offers from `KnownVillage` plus live world state;
no gen-1 consumer justifies durable villager UUIDs or volatile offer indexes. Relationship learning is
settlement-scoped. Add memory later only for a demonstrated capability such as wandering-trader TTL,
explicit return-to-merchant behavior, or offer-change recall, with RET-1 ownership and eviction.
**Rejected for V2:** `lastOfferIndex` as durable authority; full villager brain clone; global registry.

### D-VR-057: Trade familiarity via `onTradeEpisode` (`Agent_Cursor`, 2026-08-15)

**Status:** `LOCKED` — amended/peer-reviewed `Agent_Codex` 2026-08-15
**Accepted:** `SettlementRelationshipService.onTradeEpisode(mob, anchorAtStart, tick)` — separate bump from `onSocialEpisode`; does not increment `socialEventCount`; fires once per completed bounded visit/chain, not per offer use.
**Rejected:** crediting trade through greet binding; retroactive trade credit for unbound greets.

### D-VR-058: Sell expendability, not global inventory lock (`Agent_Cursor`, 2026-08-15)

**Status:** `LOCKED` — **amended** User peer review 2026-08-15
**Accepted:** V2-D uses **`SellExpendabilityPolicy`** following the `FuelExpendability` pattern
(permission before preference — **not** a second inventory owner, **not** hooks into SPM
`EatFoodGoal`). Invariant:

```text
disposable sell quantity
  = total stock
  - survival floor
  - progression reserves
  - equipped / in-use
  - current consumer reserves
```

Re-evaluate before **every** transaction. If God has 20 carrots earmarked for a farmer trade and
takes damage, SPM **may** eat one (`EatFoodGoal` splits from the mob's `SimpleContainer`); the chain
sees 19, revalidates, and either still trades or replans. **Do not** mixin SPM survival to preserve
an economic plan.
**Rejected:** global "protected inputs" that block SPM food consumption; generic reservation map
claiming ownership over SPM inventory consumers; re-derive-only without disposable-quantity math
(race with smelt at P3 remains for **Scavenger-owned** spenders — address via expendability, not locks).

### D-VR-059: Night defer via shared helper (`Agent_Cursor`, 2026-08-15)

**Status:** `LOCKED` — peer-reviewed `Agent_Codex` 2026-08-15
**Accepted:** `VillageDayNightContext.villagerWorkWindow` pure helper (reuse `SeekShelterGoal` dusk constants); `TradeWithVillagerGoal` returns `BLOCKED` until day — no busy-wait loop.
**Rejected:** duplicating night logic inside trade goal; sleeping through trade ticket forever (expiry still applies).

### D-VR-060: V2 MVP scope guard (`Agent_Cursor`, 2026-08-15)

**Status:** `LOCKED`
**Accepted:** V2 ships single-hop + one chain fixture (VR-T2 / VR-T2b); excludes restock, workstation repair, hero discount, wandering trader, Opinion trade browse.
**Rejected:** V2 absorbing V3 replant/storage or V6 hero bridges.

### D-VR-061: Staged slot-delta trade transaction (`Agent_Codex`, 2026-08-15)

**Status:** `LOCKED` — amended User peer review 2026-08-15 (`D-VR-071`, `D-VR-072`)
**Accepted:** at **commit instant** (after FACE, with fresh live backpack), build a component-aware
**joint** payment allocation and post-payment result insertion against copied slots (`D-VR-071`);
immediately before commit, require alive/reachable villager, no real trading player, and the same
still-available live offer/cost/result; apply one server-thread slot delta (`D-VR-072`); then
`notifyTrade` exactly once. Expected failures occur before mutation.
**Rejected:** passing arbitrary live backpack stacks directly to `MerchantOffer#take`; greedy pay-A-then-B
when predicates overlap; persisting `SlotDelta` across scheduler yields; shrink-then-hope rollback.
**Evidence:** pinned 1.21.1 `MerchantOffer#satisfiedBy/#take/getItemCostA/getItemCostB`,
`AbstractVillager#notifyTrade`, `Villager#stopTrading/resetSpecialPrices`; SPM `EatFoodGoal` mutates
backpack between ticks.

### D-VR-062: External demand is durable identity; offer snapshot is attempt evidence (`Agent_Codex`, 2026-08-15)

**Status:** `LOCKED`
**Accepted:** chain/intent identity is `consumerKey + desired demand/output`; villager UUID, offer index,
price, uses, and path are disposable observations. Re-resolve them after interruption. Gen-1 closes the
transient plan neutrally on load and lets the external demand replan.
**Rejected:** serialized `MerchantOffer`; stale `lastOfferIndex`; preserving a `Path` across preemption.

### D-VR-063: Trade relationship learning is visit-normalized (`Agent_Codex`, 2026-08-15)

**Status:** `LOCKED`
**Accepted:** one successful bounded trade visit/chain may emit one settlement relationship episode,
regardless of individual offer-use count. Milestones may be traced but do not each increase familiarity.
**Rejected:** per-click familiarity; greet/social counter reuse; learning from path failure or abort.

### D-VR-064: P3 trade arbitration preserves higher authority (`Agent_Codex`, 2026-08-15)

**Status:** `LOCKED WITH RUNTIME GATE`
**Accepted:** trade remains priority 3 and participates in the single Scavenger demand owner. Existing
command/combat/shelter/P1 behavior stays authoritative. An already-running host P3 loot episode may
finish; trade must then obtain a bounded admission window. VR-T2e decides whether a thin centralized
exact-admitted-trade compatibility hook is required.
**Rejected:** priority 2 trade; blanket cancellation of all host P3 behavior; implementing the full V3
ally-storage policy merely to make V2 schedule.
**Least-verified claim:** SPM's 20-tick post-visit cooldown is sufficient in the integrated insertion order.

### D-VR-065: No ownerless emerald accumulation (`Agent_Codex`, 2026-08-15)

**Status:** `LOCKED`
**Accepted:** a SELL step exists only to satisfy a named external consumer (for example an existing
tool/progression output demand) with protected survival stock and a bounded requirement. The V2
datapack may provide deterministic offers, but production evaluation remains generic.
**Rejected:** autonomous emerald appetite; assumed vanilla `iron ingot`/`book` fixture without pinned
offer evidence; profitable-trade loops that manufacture their own demand.

### D-VR-066: `VillagerTradeAvailability` + `TradeBlockedReason` (`Agent_Cursor`, 2026-08-15)

**Status:** `LOCKED` — amended User peer review pass 2 (2026-08-15)
**Accepted:** pure predicate pack for gen-1 defer paths: baby, zombified, sleeping, no currently
useful offer, outside `SettlementBoundsPolicy`, real-player customer (`getTradingPlayer() != null`),
unreachable/offline villager, night defer (`D-VR-059`), demand expired,
**`INSUFFICIENT_DISPOSABLE_QUANTITY`** (`SellExpendabilityPolicy` — fresh calculation per attempt;
not a global protected-input lock). Each failure maps to a stable `TradeBlockedReason` enum consumed
by `TradeWithVillagerGoal`, `TaskLifecycle.BLOCKED`, and optional `V2-I` inspector — not ad-hoc log strings.
**Rejected:** silent `canUse()` false with no reason; spinning path retries when all villagers sleep;
treating `TradeBlockedReason` as persistent saved state (ephemeral episode only); **`PROTECTED_INPUT`
/ reservation-style blocked reasons** that imply SPM inventory locks (`D-VR-058`).

### D-VR-067: Bilateral trade/greet claim windows (`Agent_Cursor`, 2026-08-15)

**Status:** `LOCKED` — **SOURCE-CONFIRMED mandatory** (User peer review 2026-08-15)
**Accepted:** `TradeSessionClaimWindow` mirrors `SocialGreetClaimWindow` (`opinion/` package pattern):
while `TradeWithVillagerGoal` is in **FACE or EXECUTE** against villager `V`, greet admission on **the
same villager `V` only** defers until trade completes, aborts, or bounded expiry. SPM
`FriendlyGreetGoal` owns MOVE+LOOK and classifies villagers as GREET targets — a real collision, not
defensive plumbing. Existing trade `canUse()` guards (no active greet / SOCIAL binding) remain. RET-1:
bounded per-mob map keyed by mob UUID; `clear` on episode end, unload, death, and **server stop**
(`shutdownServerState()` — mirror `SocialGreetClaimWindow.java` L74–76; prevents stale UUID claims on
integrated-server world switch in the same JVM).
**Rejected:** indefinite greet veto; taxonomy-only guard without time-bounded face-phase claim;
persistent per-villager reservation registry; optional/skip `TradeSessionClaimWindow` in task-47.

### D-VR-070: Trade settlement pick ≠ `commuteTarget()` (`User`, 2026-08-15)

**Status:** `LOCKED`
**Accepted:** `TradeSettlementPicker` (or equivalent) reuses **familiarity / HOME ranking concepts**
but **not** `SettlementReturnPolicy.commuteTarget()` itself. Filter remembered settlements that are
currently loadable for useful offers matching the active demand; rank by need-fit, legality, path, then
HOME/familiarity/utility. Unloaded villages with unknown offers are honestly unselectable.
**Rejected:** literal `commuteTarget()` for trade (HOME can beat a nearer site with the needed offer);
omniscient offer memory for unloaded settlements in V2.

### D-VR-071: Joint two-cost slot allocation (`User`, 2026-08-15)

**Status:** `LOCKED` — amended User peer review pass 2 (2026-08-15)
**Accepted:** when an offer has cost A and optional cost B, allocate across up to 8 backpack slots
with a bounded matching/backtracking pass. **Invariant: one item count may not be double-counted
across cost allocations** — not "one stack per cost." A single stack **may be partitioned** between
costs (e.g. cost A = 1 emerald, cost B = 1 emerald, one stack of 2 emeralds — valid human payment).
Required static negative controls: (1) generic diamond + component-specific diamond in different slots
(greedy A-then-B falsely rejects; joint assignment succeeds); (2) single emerald stack partitioned across
two emerald costs.
**Rejected:** sequential greedy pay-A-then-pay-B as sole allocator; **stack-level exclusivity** that
forbids partitioning one stack across costs; double-counting the same items toward both costs.

### D-VR-072: `SlotDelta` is commit-instant only (`User`, 2026-08-15)

**Status:** `LOCKED`
**Accepted:** `OfferSnapshot` and `TradeChainPlan` may survive ticks/interruption; `Path` must not
(`D-VR-062`). **`SlotDelta` / staged inventory mutation is built only after FACE with a fresh live
backpack and applied in the same tick** — no staged payment surviving WALK/animation/yields. Execution:
WALK → FACE → re-fetch → stage → simulate → revalidate → APPLY → `notifyTrade` once.
**Rejected:** multi-tick staged payment while SPM eat/loot/gift can mutate the container.

### D-VR-076: Wealth trading is a separate consumer, not part of V2-TE (`User`, 2026-08-16)

**Status:** `LOCKED` (architecture + Wealth-track-local sequence) · amendments A–D **RESOLVED and incorporated** by the 2026-08-16 review below

**Accepted — the layering.** Trade Everything is **opportunity truth, never economic motive**. It
answers *"given this villager and this item, what exact trade exists?"* and never *"should Bob become
rich?"* That stays Scavenger cognition, which preserves the `TradeOpportunitySource` boundary and
keeps `VillagerTradeAdapter` the sole transaction owner.

```text
NEED (WorkDemandPolicy)  ─┐
                          ├─► economic motive ─► TradeOpportunitySource ─► hard disposition gate
WEALTH (Portfolio)       ─┘                       (vanilla | Trade Everything)      │
                                                                                    ▼
                                          TradeWithVillagerGoal ─► VillagerTradeAdapter
```

**Sequence (locked; WEALTH TRACK LOCAL, not global phase order).** 1. **V2-TE** — compatibility only;
no Wealth motive, so the first compatibility runtime test stays interpretable (VR-T2k/l). 2.
**V2-W** — Portfolio utility + Greed authorizing discretionary trades, reusing the proven executor.
3. **Portfolio evolution** —
`D-VP-MI-022` first; `D-VP-MI-023` scarcity and `D-VP-MI-024` consumption velocity only after the
NEED/WEALTH split earns its complexity.

**Synchronization ruling (User directive, 2026-08-19):** this ordering constrains only work that
chooses to advance the optional Wealth branch. V2-W is **DEFERRED / NON-BLOCKING** and is not a
hidden prerequisite for canonical Village Work V3. The global phase order remains V2 → V3 → V4…;
when Wealth work resumes, it must still obey V2-TE → V2-W → portfolio evolution internally.

**Locked invariants.**

| | |
|---|---|
| **W-1** | Trade Everything is opportunity truth, never economic motive |
| **W-2** | NEED and WEALTH are separate reasons to trade; NEED has authority precedence |
| **W-3** | Wealth trading uses whole-portfolio before/after utility, not per-stack market value |
| **W-4** | Every discretionary wealth transaction must strictly increase one common `PortfolioUtility` by ε |
| **W-5** | Disposition/reservation runs **before** valuation; Greed can never manufacture spend permission |
| **W-6** | Unknown input value, output value, or reserve → refuse the Wealth trade |
| **W-7** | Progression SELL keeps the named BUY-deficit rule unchanged |
| **W-8** | Currency wealth, if ever allowed, needs an explicit bounded Portfolio owner; never revive ownerless emerald appetite |
| **W-9** | Wealth gets no new Goal and no second transaction engine |
| **W-10** | No speculative chain in gen-1: a Wealth trade may not accept a present loss on the hope of a later gain |

**Why `W-3` is not pedantry.** `wealthValue` is explicitly the marginal value of *one more unit at
the current amount*, so `value(3 emeralds) − value(32 logs)` is wrong for a 32-log transfer: the 1st
log given away may be worthless at saturation while the 32nd is valuable near the reserve. The
correct comparison is over the whole inventory transition, which needs
`StockUtility(category, N) = Σ marginal values for units 1..N`.

**`CONFIRMED` — the integral is well-formed.** `ResourceWealthPolicy.wealthValue` is a pure function
of `(category, currentAmount, greed, wealthLevel)`, and `wealthFactor` decreases linearly above the
comfortable band (`1 − 0.95·over/span`). Marginal value is therefore non-increasing in amount, so
`U(N)` is well-defined and **concave** — exactly the shape a potential function needs. `W-3`/`W-4`
are implementable against the shipped policy, not aspirational.

---

**Amendment A — `REJECTED AS WRITTEN`; the stronger invariant is locked instead.** The quote caveat
was unnecessary and actively weakened a correct rule. If `U` is inventory-only with fixed valuation
parameters, `U(A)` is the same number whatever the market is doing, so `A→B→A` needs both
`U(B) > U(A)+ε` and `U(A) > U(B)+ε` — a contradiction. Changed quotes create new *transitions*; they
cannot make an identical inventory state score higher.

> **`W-4` (final).** While the valuation function is unchanged, no sequence of accepted Wealth trades
> can return to an identical modeled inventory state. Market quotes may change which transitions are
> available; **market price never enters `PortfolioUtility`.**

`1 iron → 4 coal` then `2 coal → 2 iron` is **arbitrage, not a loop** — the end state differs and `U`
rose. The rule forbids returning to the same state, which is the thing that actually spins.

**Amendment B — `AMENDED`.** Snapshotting is required *within one comparison* so both sides use the
same function — `U(before, greed=.2)` against `U(after, greed=.9)` measures nothing. But the
snapshot grants **no durable execution authority**: if greed drops to 0 during the walk, the mob must
refuse at the villager, not execute because it was greedy eight seconds ago.

> One utility comparison snapshots all valuation inputs so before and after use the same function.
> That snapshot authorizes nothing. At the transaction boundary V2-W re-reads current
> greed/wealth parameters and performs a **fresh complete** before/after evaluation.

Identical in shape to V2-E's *planning permission does not authorize execution*.

**Amendment C — `REJECTED`; the original claim was factually wrong.** `ResourceWealthContext` is
`(category, currentAmount, greed, wealthLevel)` — **`CONFIRMED` by source**. Need allocations live in
a separate `ResourceNeedContext` consumed by `evaluateNeed`. No need state enters `wealthValue`, so
no "need snapshot" is required inside `PortfolioUtility` and the architecture is already cleaner than
the amendment assumed.

> **NEED and WEALTH are structurally separable.** Need allocation controls *spend permission*; it
> does not enter `PortfolioUtility`. A need arising mid-walk invalidates a trade through
> execution-time **disposition**, without redefining the wealth potential function.

**Amendment D — `ACCEPTED`, and gen-1 is stricter than first proposed.** Option A ("emeralds are
worth something when a purchase is nearby") **contradicts Amendment A**: it makes `U` depend on
villagers, offers and distance, which is exactly the market information barred from `U`. That
contradiction was in the original recommendation and is withdrawn.

> **Gen-1 (`LOCKED`).** Emeralds are **outside** Wealth `PortfolioUtility` entirely. A discretionary
> Wealth trade requires **modeled Wealth category → modeled Wealth category**, or it refuses.
> `logs → iron`, `cobble → coal` are candidates; `logs → emerald`, `emerald → diamond` and
> `diamond → emerald` are **refused on the Wealth path**. The NEED economy keeps using emeralds
> exactly as it does today, because a concrete consumer owns them.

If runtime evidence later shows greedy mobs feel stupid for never holding cash, `LiquidityUtility`
gets designed as its own problem rather than smuggled through `W-8`.

**Implementation note (V2-W design).** `StockUtility(N)` should **not** loop `1..N` at runtime. The
wealth curve is piecewise trivial — constant below comfortable, linear decline to saturation, floor
above — so the cumulative value has a closed form. Trade Everything can quote many stacks across many
villagers, and there is no reason to pay repeated linear work for something integrable by hand.

**Rejected.** Folding Wealth into V2-TE (it introduces an entirely new *reason* to trade, and would
make the first compatibility runtime test uninterpretable); a second trade Goal or transaction
engine (`W-9`); "remember recent trades and don't repeat them" as the loop defence — that hides a
valuation defect rather than fixing it.

### D-VR-075: Consumer-preserving trade output projection (`User`, 2026-08-16)

**Status:** `LOCKED` — discovered while constructing V2-H; scoped as prerequisite **V2-H0**.

**Evidence.** A probe over the real `VillagerTrades.TRADES` table (283 of 286 listings; the three
unsampled are `EmeraldsForVillagerTypeItem`, `EnchantBookForEmeralds`, `TreasureMapForEmeralds`,
none a plausible ingot or fuel source) found vanilla sells the finished `iron_pickaxe`, `iron_axe`,
`iron_sword` and iron armour — but **never `iron_ingot`, `charcoal` or `coal`**. Since
`chooseFundingTarget` requires an offer whose *result* satisfies the live `MaterialDemand`, the
registrar could never reach `TRADE` in an uncontaminated vanilla world. Correct machinery, empty
market — the north star's first invariant (*every demand must have a reachable supply*) failing.

**Root cause.** Not a fixture inconvenience: the implementation treated the route-specific
**ingredient** demand as if it were the consumer. `D-VR-015` always intended parallel acquisition
candidates for one `ConsumerRecipeSpec`; the abstraction stopped one layer short.

```text
consumer  spmscavenger:iron_pickaxe_upgrade
EXISTING_WORK   raw iron -> smelt -> iron_ingot x3 -> craft -> iron_pickaxe
TRADE           emeralds -> villager                       -> iron_pickaxe
```

**Runtime status (2026-08-16).** V2-E closed on **static acceptance** — `CODE_CONFIRMED`, MAIBS
`BEHAVIORALLY_PLAUSIBLE`, runtime `UNVERIFIED` — and that was the correct label at the time: its
executor had never been observed running. **VR-T2's PASS now covers that execution path
end-to-end**, exercising the bounded candidate round, exact-quote binding, execute-time SELL
reauthorization, cross-villager funding and post-SELL chain continuation across four sales and a
purchase. The distinction is worth keeping rather than flattening: V2-E was *accepted* on static
evidence and is *verified* by a later integration proof, which is not the same as having been
runtime-verified when it closed.

**Accepted.** A narrow pure projection, `TradePurchaseProjection`: source `MaterialDemand` +
active `ConsumerRecipeSpec` → recipe **output**, deficit **1**, **same `consumerKey`**. Live only
while that consumer's recipe is. **Direct material is evaluated first and wins when it is
actionable** — already funded, or carrying a SELL leg that fully closes its deficit. An unactionable
direct quote falls through to an actionable projected-output purchase; a datapack that ever sells a
*fundable* `iron_ingot` keeps the original path, which is why nothing here knows what a Toolsmith is.

*(Amended V2-H0-R1/R2. The original wording said "tried first and always wins", which let an
unfundable ingredient quote suppress a reachable tool purchase — first with no SELL leg at all, then
with a partial one. A future agent reading the superseded text could have "corrected" production
back into that defect, which is why the amendment is recorded here rather than only in the code.)*

**Bound to the source demand, not the projection:** `ExistingRouteFeasibility` and
`RouteExhaustionEvidence`. Their logic describes the raw-iron gather/smelt route, and reinterpreting
published exhaustion records as statements about crafting a pickaxe would be a category error. This
is enforced structurally as well as by test — `existingFeasible` is computed before `purchaseDemand`
exists, so the projection is not in scope at that call site.

**Rejected.** Changing `WorkDemandPolicy`'s iron demand to `iron_pickaxe ×1` globally (`iron_ingot`
remains correct for gather/smelt/craft and drives raw-iron gathering and furnace work); declaring
gen-1 vanilla trade unreachable (`Option 3` — would preserve the abstraction bug as product
behaviour); NBT-authored BUY offers as the core VR-T2 proof (`Option 2` — permitted by `D-VR-065` as
a **secondary** deterministic executor fixture only, since it proves Scavenger can use a vanilla
`MerchantOffer`, not that vanilla's economy contains a useful route).

### D-VR-074: `VILLAGE_TRADE` is ordinary host work, not cooperative project work (`User`, 2026-08-16)

**Context.** V2-F pins `TradeWithVillagerGoal` → `ActivityClass.VILLAGE_TRADE`. The pin itself is
mechanical; the consequential half is which `MoveHolderClassification` the `classify()` switch
returns, because that decides how a mining lease accounts for the trade attempt.

**Options.**

| Option | Lease effect | Claim being made |
| --- | --- | --- |
| `ORDINARY_HOST_WORK` **(chosen)** | ages — real MOVE contention | trade is a chore competing with mining, like `SCAVENGE_LOOT` / `FARMING` |
| `COOPERATIVE_PROJECT_WORK` | pauses — downstream handoff | trade is the project's own work by another route |
| leave at `default` → `UNKNOWN_MOVE_HOLDER` | unchanged | none; defers the semantics |

**Decision — `ORDINARY_HOST_WORK`.** `COOPERATIVE_PROJECT_WORK` has a stronger existing contract: an
**arbiter-recognised `MiningGoalKind` participant** allowed to do downstream project work.
`TradeWithVillagerGoal` has no project binding, and `MiningGoalKind.classify` returns empty for it,
so the arbiter never evaluates it at all. V2-C's `EXISTING_WORK` vs `TRADE` relationship is route
competition **at the consumer level** — it is not evidence of participation in the active
`MiningProject`. Granting lease-pausing semantics would manufacture cooperation out of shared demand.

**Rejected `UNKNOWN_MOVE_HOLDER`:** the holder is now semantically known, and reporting it as unknown
would keep `ActivityObservationService` counting every trade attempt as unidentified activity — the
telemetry honesty V2-F exists for.

**What would cause a switch.** A future slice that explicitly binds a *particular* trade attempt to
an active mining project. Add a conditional project-provenance path at that point; do **not**
globally declare all village trading cooperative mining work.

**Enforced by:** `ActivityTaxonomyTest.tradeIsOrdinaryHostWorkNotCooperativeProjectWork` (NC-32
inverts it and fails) and `ShelterCommitmentTest.tradeDoesNotDisplaceCommittedShelter` (the new enum
member's fall into the shelter policy's `default` branch, pinned rather than assumed).

**Blast radius of the new enum member — corrected.** Adding a value to `ActivityClass` is exactly the
moment a `default` branch silently gains a member, so the reachable switches were enumerated:

| Switch | Parameter type | Reached by `VILLAGE_TRADE`? |
| --- | --- | --- |
| `MoveHolderClassifier.classify` | `ActivityClass` | **yes** — explicit `case`, `D-VR-074` |
| `ShelterInterruptionPolicy.decideCandidate` | `ActivityClass` | **yes** — `default` → `BLOCK_WHILE_SHELTERED`, pinned |
| `ActivityAdmissions.forActivity` | `DiscretionaryActivity` | **no** |
| `ActivityContinuations.forActivity` | `DiscretionaryActivity` | **no** |
| `ActivityUtilityScorer.score` | `DiscretionaryActivity` | **no** |

The last three switch on **`DiscretionaryActivity`** — the director's three-member explore/rest/social
enum — not on `ActivityClass`. `VILLAGE_TRADE` cannot enter them at all, so their `default` arms are
irrelevant here. An earlier completion note described them as `ActivityClass` switches that "also
default"; that reasoning was wrong, and the correct statement is that trade is invisible to the
discretionary director by type, not by fall-through.

### D-VR-073: V2-F taxonomy pin follows V2-E goal type (`User`, 2026-08-15)

**Status:** `LOCKED`
**Accepted:** `MoveHolderClassifier` pin for `TradeWithVillagerGoal` → `VILLAGE_TRADE` lands in the
**same task batch as V2-E** or the immediately following commit. V2-E introduces the goal class (and
enum value) first; V2-F cannot complete ahead of E because the classifier target does not exist yet.
**Rejected:** locked sequence `… → V2-F → … → V2-E`; taxonomy pin before goal class exists.

### D-VR-068: Trade Everything is an optional post-core `TradeOpportunitySource` (`User` + `Agent_Codex`, 2026-08-15)

**Status:** `IMPLEMENTED / POSITIVE PATH RUNTIME CONFIRMED`; absent/incompatible-source negative
remains `UNVERIFIED` (`VR-T2l`).
**Accepted:** vanilla V2 establishes trade truth first (`D-VR-069`). A separate V2-TE task may add
`VanillaTradeSource` and optional `TradeEverythingTradeSource`, while `VillagerTradeAdapter` remains
the sole staged transaction owner. Never create a fake `ServerPlayer`, open `MerchantMenu`, or call
`setTradingPlayer` to materialize the session offer. Prefer an upstream official quote API; absent
that, support a pinned Trade Everything version through a bounded reflective `OfferQuoter.quote`
bridge that returns no opportunity on absence/signature/linkage failure. Quote only inventory stacks
already authorized as disposable and score the actual payout against external demand.
**Rejected:** making Trade Everything required; including it in V2-A acceptance; approximating its
economy from public valuation alone; selling by value without disposition protection; assuming every
quote pays emeralds; copying upstream pricing code.
**Evidence:** Trade Everything v0.3.0 / commit `fe305e663052c637dfeae2c9a8294c7748c611b0`:
`TradeEverythingApi`, `OfferQuoter`, `AbstractVillagerTradingMixin`, `SyntheticOfferFactory`, and
Fabric metadata. CurseForge confirms 1.21.1 Fabric and author BrennanHatton.
**Compatibility class:** preferred `API_STABLE` after upstream API; current fallback
`VERSION_LOCKED / API_DEPENDENT`. Absence must preserve vanilla V2 parity.

### D-VR-069: VR-T2 baseline is vanilla-only and uncontaminated (`User`, 2026-08-15)

**Status:** `LOCKED`
**Accepted:** task-47 core acceptance and VR-T2…VR-T2h runtime proof use a mod set of
`playermob` + `spmscavenger` + Fabric API only (plus `V2-H` test datapack when used). Proof chain:
Scavenger adapter → live vanilla `Villager` → live vanilla `MerchantOffer` from `getOffers()` → exact
cost paid → exact result received → offer uses incremented once. Trade Everything (`tradeeverything`)
is **absent** from this instance; VR-T2k/l run in a **separate** follow-up instance after VR-T2
**PASS**.
**Rejected:** proving V2 core with Trade Everything installed “because it might not matter”; treating
session-injected synthetic offers as baseline truth; VR-T2k as substitute for VR-T2.
**Evidence:** Trade Everything `AbstractVillagerTradingMixin` prepends/removes synthetic offer at index
0 during `setTradingPlayer` sessions and filters it from save NBT — baseline uncertainty even when
PlayerMob path bypasses player menus (`D-VR-068`).

### D-VR-030: Manual iron golem construction is distinct from village golem spawn (`User`)

**Status:** `CONCEPT LOCK RECOMMENDED` after source audit
**Accepted:** `VillageDefensePolicy` chooses repair-existing vs build-new; structured executor places
the body then pumpkin last through normal block placement and verifies spawn. Mandatory raid defense
and discretionary surplus improvement may share the executor but not authority policy.
**Rejected:** Equating with VR-15; inferring PlayerMob-specific golem ownership from `setPlayerCreated`;
griefing foreign builds; building another golem when repair offers comparable defense.
**Caveat (`INFERRED`):** `IronGolem#setPlayerCreated(true)` is a vanilla boolean classification, not
proof of which entity owns or commands the golem — later systems must not infer mob-specific
ownership without a stored relationship.
**Evidence:** pinned `CarvedPumpkinBlock#trySpawnGolem` unconditionally calls
`IronGolem#setPlayerCreated(true)` after matching the iron pattern, independent of placement actor.

### D-VR-031: Hero gift collection is post-raid reward behaviour (`User`)

**Status:** `LOCK RECOMMENDED` after owner-boundary amendment and source audit
**Accepted:** narrowly bridge `GiveGiftToHero` recipient discovery/lifecycle so an eligible PlayerMob
can receive vanilla gifts; reuse SPM `CollectFloorItemsGoal`; optional bounded AFTERMATH presence.
**Rejected:** `HeroGiftCollectionGoal`, new gift economy, duplicate pickup logic, looting villager
inventories.
**Evidence:** `GiveGiftToHero#getNearestTargetableHero` and lifecycle are Player-typed, while
`throwGift(Villager, LivingEntity)` is general after selection.

### D-VR-032: Village founding is distinct from village discovery (`User`)

**Status:** `HELD / REDESIGN`
**Accepted:** a future `VillageFoundingProject` changes world truth, then the ordinary D-VR-019
perception path alone may create/update `KnownVillage`. Founder history, if useful, is observer-relative
and belongs in a separate relationship/history model.
**Rejected:** `KnownVillage.origin = FOUNDED`; project-side memory insertion; single-bed wilderness
camp becoming `HOME_VILLAGE`; treating one mob's founding history as settlement identity for all.

### V1-D — task contract (`PROPOSED` — authorize for 1.10.0)

**Scope (in):** `VillagePerceptionObserver`, `VillagePerceptionScheduler`, server tick hook,
`SpmScavenger` registration, static tests for VR-M4/M7 + B-VR-57 admission fairness + V1-R4 regressions.

**Scope (out):** `KnownVillager`, bell goal, trade, raid, inspector readout, VR-T1 datapack runtime.

**Must happen:** production call path `observe` → `VillageMemorySavedData.record` for ticking PlayerMobs;
≤1 global POI query/server tick; no MOVE/LOOK flags on observer.

**Must not happen:** shared observation cache; hidden POI in supersede; starvation at admission among
≤100 simultaneous ticking observers; unload/death leaves stale queue UUIDs.

**Verification:** `.\gradlew.bat test --tests "*village*"` + structural contract tests; label runtime
`UNVERIFIED` until VR-T1 launch approval.

**Artifact:** `.superpowers/sdd/task-45-brief.md` (to be written on authorization).

### D-VR-033: Bounded individual village-perception scheduling (`Agent_Codex` + User review)

**Status:** `LOCK RECOMMENDED` (2026-08-14) — P0 **CLOSED** (V1-R4 1.9.5); P1 scheduler contracts
**CLOSED** in RFC (B-VR-56/57/60). **V1-D IMPLEMENTED** (1.10.0); VR-T1 core path **CONFIRMED** (Bob session).

**Runtime P0 (2026-08-14):** `VillagePerceptionObserver` debounce used `gameTime - Long.MIN_VALUE`, which
overflows and blocked every enqueue forever. Fixed with `VillagePerceptionEnqueueDebounce` (`hasEnqueued`
guard). Reload path also calls `ensureVillagePerceptionObserver` when `alreadyInstalled` skips full install.

**Accepted:**

- B2 per-level UUID lanes + **one server-global POI query budget** per tick.
- Flagless per-mob observer; chunk-dirty + heartbeat; no shared result cache.
- Conditional Must happen (mob must **remain** in region through service).
- Fair admission: ticking-mob-bound queue + round-robin retry; emergency cap abnormal only.
- `VillagePerception.observe(level, mob.blockPosition())` at **service** time — not enqueue time.

**Rejected:** extending `ExplorationActivityGoal` cadence (A); central POI cache (C); `MAX_QUEUE>=100`
as primary fairness; unconditional prompt observation guarantee.

**Provisional tuning constants:** `HEARTBEAT_TICKS=200`, `DEBOUNCE_TICKS=20`, `GLOBAL_QUERY_BUDGET=1`.

**Sequence:** ~~V1-R4~~ **DONE** → ~~V1-D~~ **DONE** → ~~VR-T1A~~ **PASS** → ~~V1.5~~ **CLOSED** → **V2 Trading** (next).

### VR-T1A — runtime closure (`PASS`, User, 2026-08-14)

**Scope:** core village perception path — not VR-T1b scale profiling, not raid-center alignment, not datapack fixture (B-VR-28).

**World:** natural taiga village, overworld. **Mob:** PlayerMob `Bob`. **Mod:** `spmscavenger` 1.10.0
(post-debounce fix). Evidence gathered via temporary VR-T1 diagnostics (since removed).

| Scenario | Result | Evidence |
| --- | --- | --- |
| Autonomous discovery | **CONFIRMED** | Observer→scheduler→service→record; 7–8 admitted POIs; anchor `-11666, 82, 7709` |
| Pre-fix V1-D blocker | **CONFIRMED** | `Long.MIN_VALUE` debounce overflow blocked all enqueues; `hasEnqueued` repair fixed it |
| Leave ~400 blocks | **CONFIRMED** | Still 1 village, same anchor, `First seen: 123682`, `Last seen` advanced |
| Return to village | **CONFIRMED** | Teleport `-11652, 82, 7686`; same anchor; `First seen` unchanged; `Last seen: 133393` |
| Save/reload | **CONFIRMED** | Quit/rejoin; anchor and `First seen` persisted |
| Cross-dimension persistence | **CONFIRMED** | Per-user session (User, 2026-08-14) |

**Deferred (not VR-T1A gates):**

- VR-T1b: 10/50/100-mob backlog + B-VR-58 POI query cost profiling
- Anchor vs `Raid.getCenter()` during active raid
- B-VR-28 datapack village fixture
- Permanent-removal sweep: **STATIC_CONFIRMED** (contract test); runtime eviction probe **DEFERRED**
- Monotone anchor sequence over long travel (D-VR-022)

**Post-VR-T1A cleanup:** all three temporary debug commands and trace/diagnostics plumbing removed.
Contract test `mustHappen_vrT1aDiagnosticsRemoved` guards against reintroduction.

**Reflection (PROVEN):** never use numeric extremes as sentinels in arithmetic debounce checks;
use explicit boolean state (`VillagePerceptionEnqueueDebounce`).

### VR-T1.5a — runtime closure (`PASS`, User, 2026-08-15)

**Scope:** autonomous return commute to designated home — not VR-T1.5b familiarity growth, not VR-T1.5c
village-aware social, not auto-home (`D-VR-042`).

**World:** natural taiga village, overworld (same Bob fixture as VR-T1A). **Mob:** PlayerMob `Bob`.
**Mod:** `spmscavenger` 1.11.0 (post repair pass 3 — commute dead-zone fix). Home anchor ~`-11666, 7709`
(via `/spmscavenger designate-home` fixture, **D-VR-039** / V1.5-F).

| Scenario | Result | Evidence |
| --- | --- | --- |
| Start far from home | **CONFIRMED** | User: Bob left village area; return commute seeded from distance |
| Autonomous multi-leg return | **CONFIRMED** | Bob pathing home without operator steering |
| Enter home village bounds | **CONFIRMED** | Crossed into actual village at ~`-11666` (inside 64-block anchor bounds) |
| Prior dead-zone failure | **REPAIRED** | Pre-fix stop at ~`-11592, 7716` (~74 blocks); repair pass 3 split start vs continue policy |
| Hostile interruption + resume | **CONFIRMED** | Monster encounter interrupted explore; flee/hide; exploration resumed after threat cleared (out of V1.5 scope for flee-past tuning) |

**Deferred (not VR-T1.5a gates):**

- ~~VR-T1.5b~~ **CLOSED** (2026-08-15)
- ~~VR-T1.5c~~ **CLOSED** (2026-08-15)
- Auto-home production policy (`D-VR-042` — `PRODUCT DECISION`)
- ~~Remove temporary `designate-home`~~ **DONE** (D-VR-051, 2026-08-15)

**Reflection (PROVEN):** `COMMUTE_MIN_DISTANCE` (128) is a **start** gate only; in-flight commute legs
must chain until `SettlementBoundsPolicy` (64) — applying 128 to chain legs creates a dead zone.

**Reflection (PROVEN):** hostile interruption during COMMUTE/DISCRETIONARY explore does not terminate the
expedition if the mob survives; resume after threat clearance is observable without V1.5 changes.

### VR-T1.5b — runtime closure (`CLOSED PASS`, User, 2026-08-15)

**Scope:** natural familiarity accumulation — not VR-T1.5c social bias, not auto-home (`D-VR-042`).
**No further VR-T1.5b testing** per User directive.

**World:** natural taiga village, overworld (VR-T1A anchor). **Mob:** PlayerMob `God`.
**Mod:** `spmscavenger` 1.11.0 (post repair passes 4–5 — tuning rebalance + visit/presence tick split).

| Check | Result | Evidence |
| --- | --- | --- |
| Bootstrap / initial familiarity | **PASS** | Natural discovery + relationship row |
| Passive presence accumulation | **PASS** | `Presence: 250 / 250` at cap |
| MEDIUM reachable naturally | **PASS** | `Familiarity: 300`, `Band: MEDIUM` |
| Presence cap at 250 | **PASS** | `Presence: 250 / 250` while total familiarity can exceed |
| Continuous-residency exploit | **BLOCKED** | Three status polls at 300/250 — no drift while standing inside |
| Leave → re-entry +50 | **PASS** | `300 → 350`; `lastVisit` `237861 → 259152`; presence stayed 250 |
| HOME independence | **PASS** | `Home: false` with MEDIUM attachment |

**Tuning shipped (repair passes 4–5):** visit +50; presence +5/200t capped at 250; `lastVisitTick` /
`lastPresenceTick` / `lastOutsideTick` split; re-entry visit only after departure.

### VR-T1.5c — runtime closure (`CLOSED PASS`, User, 2026-08-15)

**Scope:** village-aware discretionary SOCIAL + settlement social credit — not V2 trading.

**Mob:** God @ `-11671, 82, 7713`. **User directive:** no further V1.5 runtime tests.

| Check | Result | Evidence |
| --- | --- | --- |
| Opinion not blocked by observer taxonomy | **PASS** | Post-fix: `VillagePerceptionObserver → PASSIVE_OBSERVER`; `latestDispositionCause=NONE` |
| Village social credit | **PASS** | Familiarity `350 → 390`; `Social events: 0 → 1` |
| Root cause identified | **CONFIRMED** | Pre-fix: `UNKNOWN_ACTIVE` from unclassified `VillagePerceptionObserver` suppressed director |
| Repairs shipped | **CONFIRMED** | `MoveHolderClassifier` taxonomy; bounded greet claim window; runtime authority inspector |

**Reflection (PROVEN):** every flagless background observer must be pinned in `MoveHolderClassifier`
beside `ExplorationActivityGoal` or it fail-closes the entire discretionary director.

## Contribution

| Agent | Date | Change |
| --- | --- | --- |
| User + Agent_Codex | 2026-08-19 | **V2-DEF-003c-R1 RUNTIME PASS recorded.** Step-7A directly observed `ROUTE UNKNOWN/FEASIBLE -> GATHER PUBLISHED -> GATHER YIELDING -> ROUTE INFEASIBLE -> PLAN #1 TE -> TRADE #1 320->298`, then completed 12 exact Trade Everything funding sells, accumulated 12 emeralds, bought one iron pickaxe from a vanilla Toolsmith, and ended with `routeEvidence tracked=0`. Counters: `plans=13 (TE 12)`, `revals=13`, `trades=13`, `episodes=0`. Scope is deliberately narrow: V2-TE positive path and the V2-DEF-003c authority handoff are `RUNTIME_CONFIRMED`; `VR-T2l` and relationship learning are not promoted. Documentation only; no `MandatoryHandoffPolicy`, Gather/Trade scheduling, or production Java change. **Frontier before:** V2-DEF-003c runtime unverified / V2-TE positive path pending. **Frontier after:** positive path runtime-confirmed; absent/incompatible-source negative remains. |
| User + Agent_Claude | 2026-08-15 | **V2-E design LOCKED — targeted seam interlock; SOCIAL sub-mode rejected.** Ten constraints locked. Rejected the SOCIAL sub-mode because FriendlyGreet's integration is SOCIAL-specific end to end, so trade inside it would arrive as *social completion evidence* and make `ActivityClass.VILLAGE_TRADE` fight the architecture. **Four corrections to my prediction, two of them my errors:** (1) the claim must open at **attempt start**, not FACE — a FACE-only claim rests V2-E's correctness on an unproven GoalSelector ordering fact, and the answer to a long walk is bounding the *attempt*, not letting trade greet its own target; (2) the interlock must run **before** `recordObservation` publishes the target into the SOCIAL control plane, or Opinion forms a SOCIAL intent for an executor we deliberately made unavailable (verified: the observation is the mixin's fourth line); (3) *"decision cycle"* is ambiguous — failures live in a bounded candidate-attempt **round**, exhausted → cooldown → fresh round, keeping V2-C stateless while the physical executor holds transient attempt state; (4) the missing `getTradingPlayer()` guard is **`CODE_CONFIRMED`** not `RUNTIME_QUESTION` (the adapter guards `isAlive()` only), and sleeping-merchant legality is V2-E's, not assumed from V2-A. Gate MAIBS-1 → **`PASS — BEHAVIORALLY_PLAUSIBLE`**; runtime `UNVERIFIED`. **No code written.** |
| Agent_Claude | 2026-08-15 | **V2-E Behavioral Prediction (Gate MAIBS-1) — implementation held.** Result: **`FAIL — ARCHITECTURE_DEFECT` as briefed**, all three resolvable in design. (1) **A P3 goal cannot hold a claim against P1.** `FriendlyGreetGoal` is priority **1** with MOVE+LOOK and its `canUse` takes the *nearest greetable entity* — which is the villager the mob just walked to, so approaching *creates* the preemption. A `TradeSessionClaimWindow` owned by the P3 trade goal protects nothing. The only mechanism that can express it is the admission seam we already own, as a **targeted, expiring, `stop()`-released** suppression of one (mob → villager) pairing — narrow enough not to reintroduce the global veto 44D-R2 removed. (2) **Claim release on `stop()`** is undefined (combat during FACE leaves greeting suppressed for a villager nobody is trading with). (3) **Candidate demotion** is unspecified: best-unreachable must not re-select forever — *"best-ranked offer is unreachable" ≠ "trade is unreachable"*. Also surfaced: nothing refuses a merchant already held by a **human player** (`getTradingPlayer() != null`) — new V2-E requirement on the V2-A adapter. Five weird behaviours classified, adversarial A–O, T0…T+1200 trace, two design options, falsifying VR-T2 experiment. **No code written.** |
| User + Agent_Claude | 2026-08-16 | **V2-G CLOSED (R3 accepted); V2-H0 implemented under new `D-VR-075`.** R3: `consumeCreditFor(null)` **fails closed** — post-R2 the caller passes the chain that *earned* the episode, so `null` no longer means "terminated" but "pending evidence lost its owner", a state with no legitimate producer; the terminated-chain test is replaced by a lost-owner negative that also asserts refusal does not consume the ledger slot. NC-45 fires. **V2-H stopped before writing the fixture**: a vanilla supply probe showed no villager sells `iron_ingot`, `charcoal` or `coal`, so the whole V2-E path was economically unreachable in vanilla — see `D-VR-075`. `TradePurchaseProjection` added (pure, direct-material-first, same `consumerKey`, deficit 1, no profession hardcoding); feasibility and exhaustion stay on the source demand. 1169 tests; NC-46 (projection before direct), NC-48 (consumer check dropped), NC-49 (ingredient deficit carried) fire. **NC-47 is inexpressible**: feasibility is computed before `purchaseDemand` is declared, so no compiling mutation can feed it the projection — structural protection, recorded rather than claimed as a passing control. **Probe method note:** two earlier instruments were wrong — a `javap` window-heuristic contradicted itself on `IRON_INGOT`, and a runtime probe reached 7 of ~128 items because `ItemsForEmeralds` throws on a null entity. The finding only held once listing result fields were read directly and coverage was reported; `VanillaTradeSupplyProbeTest` now carries a coverage guard so a broken instrument fails loudly instead of looking like a finding. **Next: V2-H fixture on untouched vanilla offers.** |
| User + Agent_Claude | 2026-08-16 | **V2-G-R2 — chain-handoff repair.** R1's `onChainOpened()` reset made **planning mutate learning state**, and planning runs *between* a completed transaction and its emission: `continueChain` records the anchor, then replans, and `advanceChain` can terminate chain A and mint B before teardown emits anything. The pending A episode was then credited against B — **A re-credited** (its history had just been reset) and **B marked spent without ever trading**. Repair: `tradeEpisodeChain` is captured *with* the anchor inside the same first-success guard, `emitTradeEpisode` consumes credit for that captured chain (`earnedBy`) rather than the live field, and both pending fields are cleared before the relationship service is called. The reset API is **deleted, not merely uncalled** — `sameChainAs` already restores eligibility naturally via `createdAtTick`, so the reset was redundant as well as harmful, and a method with zero production callers is the shape Gate RET-1a exists to reject. Claim-release ordering from R1 preserved unchanged. 1159 tests; NC-42 (credit the live chain), NC-43 (planning resets learning state), NC-44 (pending chain not captured) all fire. |
| User + Agent_Claude | 2026-08-16 | **V2-G-R1 — two lifecycle repairs.** (1) **`D-VR-063` across preemption:** V2-G bounded credit with an anchor cleared at teardown, correct *within* one visit — but `TradeChainPlan` deliberately survives `stop()` (the hard lifetime Option A protects), so `SELL → combat → stop credits #1 → same chain resumes → BUY → credits #2` gave one bounded chain two relationship episodes. New transient **`TradeEpisodeLedger`** keyed on `TradeChainPlan.sameChainAs` (consumer + output + **`createdAtTick`**, since `at()` mints a new record per step while remaining one chain). Credit still fires immediately at the interruption — deferring to chain completion would lose the episode whenever a chain is abandoned after a real trade — and is restored **only** by `forDemand` minting a new chain, never at teardown. No persistence, no store; RET-1e unaffected. (2) **Claim release precedes credit in `endRound` too**, not only `stop()`; a throwing credit would leak the greet interlock from either path. Doc repaired: `onTradeEpisode(anchorAtStart)` is resolved at the **first successful transaction**, not round or chain start. Untouched as instructed: separate trade/social counters, `VillageMemorySavedData` ownership, the settlement-bounds requirement, and the `UNVERIFIED` `TRADE_FAMILIARITY_BUMP = 40`. 1157 tests. NC-38 (ledger reset at teardown), NC-39 (ledger not consulted), NC-40 (identity ignores `createdAtTick`), NC-41 (`endRound` credits before releasing) all fire. **Process note:** NC-40 initially did *not* fire — every ledger test called `onChainOpened()` between chains, so the identity comparison was never reached and the tick was untested; a direct `sameChainAs` control was added. Separately, an NC restore from a **stale** `/tmp` snapshot deleted `sameChainAs` outright — the second occurrence this session of restoring a backup older than the edit being protected. |
| User + Agent_Claude | 2026-08-16 | **`D-VR-076` amendments resolved.** **A REJECTED as written** — the quote caveat weakened a correct rule: with `U` inventory-only and parameters fixed, `U(A)` is invariant to the market, so `A→B→A` is self-contradictory regardless of restocks. Locked the stronger form plus **market price never enters `U`**; `1 iron → 4 coal` then `2 coal → 2 iron` is arbitrage (different end state), not a loop. **B AMENDED** — snapshot within one comparison so both sides share a function, but the snapshot grants no durable execution authority; greed dropping mid-walk must cause refusal at the villager, mirroring V2-E's planning-vs-execution rule. **C REJECTED — factually wrong.** `ResourceWealthContext` is `(category, currentAmount, greed, wealthLevel)`; need allocations live in `ResourceNeedContext` and never reach `wealthValue`. No need snapshot is needed inside `PortfolioUtility`; a mid-walk need change invalidates a trade through execution-time disposition instead. **D ACCEPTED and tightened** — Option A contradicted Amendment A by making `U` depend on villagers/offers/distance. Gen-1 puts emeralds **outside** Wealth utility entirely and requires modeled-category → modeled-category, refusing `logs → emerald` and `emerald → diamond` on the Wealth path while NEED keeps using currency normally. Also noted: `StockUtility(N)` has a closed form over the piecewise wealth curve and must not loop `1..N` per candidate quote. **No implementation authorized.** |
| User + Agent_Claude | 2026-08-16 | **`D-VR-076` LOCKED — Wealth trading is a separate consumer, not part of V2-TE.** Layering: Trade Everything supplies opportunity truth; NEED and WEALTH are two motives feeding the one proven executor. Sequence **V2-TE → V2-W → portfolio evolution**. `W-1…W-10` locked, including whole-portfolio before/after utility (`W-3`), the strict-increase anti-loop invariant (`W-4`), disposition-before-valuation (`W-5`) and no speculative chain (`W-10`). **`CONFIRMED`:** `wealthValue` is pure in `(category, amount, greed, wealthLevel)` and marginal value is non-increasing above the comfortable band, so `StockUtility(N) = Σ` is well-defined and concave — `W-3`/`W-4` are implementable against shipped code. **Four amendments left `OPEN`:** (A) `W-4` prevents loops only against *unchanged quotes*, and only if `U` is inventory-only — market price inside `U` destroys the potential function; (B) `U` is stable only while `greed`/`wealthLevel` are snapshotted, since both are live config; (C) `U` needs a fixed need-allocation snapshot or NEED/WEALTH must be provably separable; (D) `W-8` is the load-bearing rule — emeralds have no use value but purchasing power, so Option B liquidity revives ownerless appetite unless it saturates hard and cannot buy currency with useful goods. Option A recommended for gen-1. **No implementation authorized.** |
| User + Agent_Claude | 2026-08-16 | **VR-T2 CLOSED — RUNTIME PASS. V2 vanilla trading is complete.** First and only runtime acceptance run, against untouched vanilla economics: `T0 consumer spmscavenger:iron_pickaxe_upgrade`, `route UNKNOWN`; naturally rolled Toolsmith BUY **11 emerald → enchanted iron_pickaxe**; Fletcher **32 sticks → 1 emerald**; mob seeded exactly four emerald short. **Fletcher uses 0→4, Toolsmith uses 0→1**, final sticks 3 (the craft reserve, untouched), final emeralds 0, final pick tier **IRON**, consumer/source/projection all closed, **settlement episodes 0→1** — four sales and a purchase taught exactly one relationship episode. **Latched fails 0. Harness verdict PASS against the captured oracle.** This closes the loop the north star demands: an autonomous mob went from a stone pickaxe and no reachable iron to a finished iron pickaxe, through the real vanilla economy, having earned its own `INFEASIBLE` from a completed empty gather scan. **Runtime finding preserved (do not rediscover):** the original fixture used a **level-2** Toolsmith and failed with `setup FAILED - missing Toolsmith iron_pickaxe offer`. The iron pickaxe is a **level-3** `EnchantedItemForEmeralds` listing, and level 3 has **5** competing listings while `updateTrades` draws only **2** — so the route appears on roughly 40% of boards and is **probabilistic, not guaranteed**. Hence the bounded natural pool: several level-3 candidates, keep the first whose board naturally contains the route, selected on route presence alone, never price or enchantment. Pinned permanently by `VanillaTradeRouteContractTest.mustHappen_theIronPickaxeListingLevelIsKnown`. The static test that should have caught this found the listing and kept only its price, discarding the containing profession and level — proving "vanilla lists an iron pickaxe somewhere" while being cited for "the fixture's Toolsmith will have one". Same shape as this slice's other near-misses: a test whose subject was narrower than the claim it was read as supporting. |
| User + Agent_Claude | 2026-08-16 | **V2-H proof support removed.** Deleted `com.noobk.spmscavenger.debug` (`Vrt2ProofCommand`, `Vrt2Trace`, `Vrt2Oracle`), its tick sampler and command registration, `TradeWithVillagerGoal.DebugChainSnapshot`, the `peekStatus`/`peekExhaustedFor` observation seams, and `test-datapacks/phase-village-raid/`. All carried `TEMPORARY V2-H PROOF SUPPORT` and had no production callers once VR-T2 was captured — leaving them would be the zero-caller shape Gate RET-1a exists to reject. **Kept:** `VanillaTradeSupplyProbeTest` and `VanillaTradeRouteContractTest`, which are permanent regressions documenting the vanilla-supply and level-3 findings. |
| User + Agent_Claude | 2026-08-16 | **V2-G CLOSED.** `SettlementRelationship.recordTradeEpisode` + `SettlementRelationshipService.onTradeEpisode`, satisfying `D-VR-057` (separate credit — trade never touches `socialEventCount`) and `D-VR-063` (one episode per completed bounded visit/chain). **The once-per-visit rule lives in the executor, not the policy**: `TradeWithVillagerGoal.tradeEpisodeAnchor` is captured on the *first* successful transaction and cleared on emission, so a ten-use chain teaches one relationship and both teardown paths (`endRound`, `stop`) credit exactly once. Anchor captured at first success rather than round start — that is the moment an episode demonstrably exists *and* the mob is provably at the villager; a round opening in one settlement and succeeding in another would otherwise credit the wrong village. A round that never transacted, or one outside `SettlementBoundsPolicy`, credits nothing. **Gate RET-1e / B-VR-93: no new store.** The count persists inside `VillageMemorySavedData`, already registered in `PerMobSavedData.forgetAll`; `PerMobRemovalContractTest` green. Pre-V2-G saves load as `0` — absence and *never traded* are the same value, the one case where reading a missing field as zero is honest. `TRADE_FAMILIARITY_BUMP = 40` is set equal to the social bump as a **declared default, not a finding** — revisit with VR-T2 evidence. Also restated `mustHappen_stopReleasesTheClaimUnconditionally`: its `!body.contains("if (")` proxy held only while `stop()` was branchless and broke on a guarded statement *after* the release, which cannot make it conditional; it now checks the release is a top-level statement with no branch before it, and the release was moved first so a throwing credit cannot delay it. 1149 tests; NC-34 (social credit), NC-35 (per-click), NC-36 (non-idempotent emit), NC-37 (release delayed) all fire. **Next: V2-H.** |
| User + Agent_Claude | 2026-08-16 | **V2-F CLOSED.** `ActivityClass.VILLAGE_TRADE` added and `TradeWithVillagerGoal` pinned in `MoveHolderClassifier.staticActivityClass`, satisfying `D-VR-073` in V2-E's batch. New **`D-VR-074`**: the classification maps to `ORDINARY_HOST_WORK`, not `COOPERATIVE_PROJECT_WORK` — the latter's contract is an arbiter-recognised `MiningGoalKind` participant, and trade has neither project binding nor arbiter evaluation; consumer-level route competition is not project participation. Also enumerated the new enum member's blast radius: it reaches `MoveHolderClassifier.classify` (explicit case) and `ShelterInterruptionPolicy.decideCandidate` (`default` → `BLOCK_WHILE_SHELTERED`, now pinned). **Correction:** `ActivityAdmissions`, `ActivityContinuations` and `ActivityUtilityScorer` switch on **`DiscretionaryActivity`**, not `ActivityClass` — `VILLAGE_TRADE` never enters them, so an earlier note calling them defaulting `ActivityClass` switches was wrong. 1137 tests; NC-32 (cooperative semantics) and NC-33 (pin removed) both fire. **Next: V2-G.** |
| User + Agent_Claude | 2026-08-16 | **V2-E CLOSED — STATIC ACCEPT (R1…R8).** Eight review rounds, each finding real defects a green suite had not. Named defect class: *policy correct, caller lying, all policy unit tests green* — `RouteEvidence.of(false,…)`, `material -> 0`, and a `SellFundingLeg` chosen by list order while a different quote was attempted. **R4** tri-state epistemics (tool capability is `UNKNOWN`, not `FEASIBLE`) + one BUY-ranking authority. **R5** demand-episode invalidation; compound-cost SELL refused; **Option A locked** — `TradeChainPolicy` owns the production chain, chosen because R4's executor reproduced every V2-D invariant *except the hard lifetime*; production `RouteExhaustionEvidence` publisher with four guards. **R6** absolute `targetHeldQuantity` (holding 1 of 3 terminated the chain a purchase short), SELL **uses** vs item units, execute-time reauthorization, `completeCurrentSuccessfully`, fundable-BUY filter summing both emerald slots. **R7** cross-villager `TradeAttemptFunding`; V2-B leg selection (list order was a *deadlock*, not a preference); BUY's own non-emerald payment reserved against the funding SELL; `withoutMarketEvidence` so a strolling villager cannot restart the hard lifetime. **R8** exact BUY revalidation via read-only `VillagerTradeAdapter.revalidateOffer` (liveness ≠ existence); buyer-**local** offer index, never the flattened ranking slot; carried buyer bridges the post-SELL 16→30-block recentring. 1134 tests, 31 negative controls fired. Two agent-process lessons recorded: a negative-control loop restoring a **stale** backup silently reverted a landed repair (R6), and structural assertions scoped to a *file* rather than a *method body* passed while the code they policed was bypassed (R4, R6, R7). **MAIBS: PASS — BEHAVIORALLY_PLAUSIBLE. Evidence: `CODE_CONFIRMED`; runtime `UNVERIFIED`.** VR-T2 remains **HOLD** pending V2-F/G/H — not for any V2-E defect. |
| User | 2026-08-16 | **VR-T2 fixture debt recorded (→ V2-H).** Two gaps, neither a V2-E defect. (1) `test-datapacks/` currently contains only `shelter-commitment`; the locked runtime setup names `test-datapacks/phase-village-raid/`, which does not exist. (2) **Fixture semantics:** the first VR-T2 proof describes a farmer carrot→emerald SELL, but production correctly forbids ownerless emerald appetite — `WorkDemandPolicy` raises external material demand around the progression chains (iron ingot, charcoal). The fixture must therefore establish a **real bounded purchase consumer** that makes the farmer SELL causally necessary, rather than spawning carrots and expecting a sale because a farmer would pay. Spawning sellable goods is not a demand. |
| User + Agent_Cursor | 2026-08-15 | **V2 peer review pass 2 (continuation 11).** P1: `D-VR-071` count-level exclusivity (stack partition allowed); `D-VR-073` V2-E before V2-F. P2: `D-VR-067` server-stop cleanup. P3: `D-VR-066` → `INSUFFICIENT_DISPOSABLE_QUANTITY`. **LOCK-CLEAN for task-47.** |
| User + Agent_Cursor | 2026-08-15 | **V2 pre-task-47 peer review (continuation 10).** Locked: `D-VR-067` SOURCE-CONFIRMED mandatory; `D-VR-058` → `SellExpendabilityPolicy`; `D-VR-071` joint allocator; `D-VR-072` commit-instant SlotDelta; `D-VR-015` feasibility-before-win; `D-VR-070` reject literal `commuteTarget()`. Doc-debt sweep. **Ready for task-47.** |
| Agent_Claude | 2026-08-15 | **V2 brainstorm — stack identity and payment shape.** The LOCKED evidence baseline pinned the Merchant seams correctly; it did not pin **stack identity**. `MerchantOffer#getResult()` returns the **live** result field while `#assemble()` copies, and `#getBaseCostA()` is live while `#getCostA()` copies — an asymmetry that will fool an implementer who checks the cost side and generalises. Aliasing the result corrupts the villager's offer permanently and persists to the world. Separately, `take(a,b)` mutates **only the two stacks handed to it** because `MerchantMenu` guarantees consolidated payment slots; an 8-slot backpack pays 20 wheat as 16+4, so V2-A must debit across slots and use `take`/`satisfiedBy` as validation only. Also linked V2-D's "protected inputs" to the shipped `FuelExpendability` permission layer (SPM-2) and V2-G's persistence to `PerMobSavedData.forgetAll` (Gate RET-1e, already build-enforced). **Checked and rejected:** level-up offer-index invalidation — `updateTrades` appends, indices are stable. B-VR-90…94; V2-A/D/G amended. **No implementation authorization.** |
| User + Agent_Cursor | 2026-08-15 | **D-VR-069 LOCKED — uncontaminated VR-T2 baseline.** First V2 proof chain: Scavenger → vanilla Villager → vanilla MerchantOffer → exact cost/result → uses+1. VR-T2…T2h require `tradeeverything` **absent**; VR-T2k/l only after VR-T2 PASS in separate instance. |
| User + Agent_Codex | 2026-08-15 | **Trade Everything optional V2 compatibility brainstorm.** Verified v0.3.0 / commit `fe305e6`: synthetic offer exists only during a real player session and is excluded from NBT; public API exposes valuation but not exact quote. Locked `D-VR-068`: vanilla task-47 remains first; separate V2-TE uses a narrow `TradeOpportunitySource`, central V2 transaction ownership, disposable-input policy, actual-payout scoring, preferred upstream quote API or pinned fail-closed reflective fallback. Added B-VR-101…105 and VR-T2k/l. **No implementation/build/runtime/commit/push.** |
| Agent_Cursor | 2026-08-15 | **V2 brainstorm continuation 8.** Code audit: no `VILLAGE_TRADE`, `TradeSessionClaimWindow`, or `WorkDemandPolicy` trade path in `src`. Locked **D-VR-015** (`WorkDemandPolicy` extension, no rename blocker); added **D-VR-066** (`VillagerTradeAvailability` + `TradeBlockedReason`) and **D-VR-067** (bilateral `TradeSessionClaimWindow`). B-VR-94…100; reuse `SettlementReturnPolicy.commuteTarget` for trade anchor; V2 slice dependency order; **V2-I** optional inspector (`B-VR-79`); VR-T2i/T2j. Admission diagram → **LOCKED**. **V2 design closed for task-47; no implementation/build/runtime/commit/push.** |
| Agent_Codex | 2026-08-15 | **V2 brainstorm continuation 7 / peer review.** Source-audited mapped 1.21.1 `MerchantOffer`, `AbstractVillager`, and `Villager` plus SPM P3 `RaidContainersGoal` and current `WorkDemandPolicy`. Replaced live-mutation rollback with staged slot-index transaction (`D-VR-061`); separated durable demand from volatile offer/path identity (`D-VR-062`); normalized trade relationship episodes (`D-VR-063`); kept P3 authority with runtime starvation gate (`D-VR-064`); banned ownerless emerald accumulation (`D-VR-065`). Held persistent `KnownVillager` from V2. Added VR-T2e–h and MAIBS weirdness/temporal review. D-VR-005/029/053–055/057–059 locked after amendments. **V2 design ready for task-47 authorization; no implementation/build/runtime/commit/push.** |
| Agent_Cursor | 2026-08-15 | **V2 brainstorm continuation 6.** Pinned-jar audit: gen-1 trade **mixin-free** (`D-VR-053`); `ActivityClass.VILLAGE_TRADE` (`D-VR-054`); demand-only admission (`D-VR-055`); `KnownVillager` minimal (`D-VR-056`); `onTradeEpisode` (`D-VR-057`); protected chain inputs (`D-VR-058`); night defer helper (`D-VR-059`); V2 scope guard (`D-VR-060`). V2 implementation contract (slices A–H), VR-T2…T2d, B-VR-72…85. D-VR-005/029 → `LOCK RECOMMENDED`. VR-2 feasibility **PARTIAL**. **No implementation authorization.** |
| User + Agent_Cursor | 2026-08-15 | **V1.5 RUNTIME CLOSED.** VR-T1.5c **PASS** — God: `350→390`, social events `0→1`; taxonomy + claim window repaired; debug commands removed (D-VR-051). Frontier → **V2 Trading**. |
| User + Agent_Cursor | 2026-08-15 | **VR-T1.5b CLOSED PASS.** God (taiga): bootstrap, presence to 250 cap, MEDIUM at 300, standing-still exploit blocked, leave→re-entry +50 (`300→350`, `lastVisit` advanced), HOME independent. **No further VR-T1.5b testing.** Frontier → VR-T1.5c; God fixture ready (350/MEDIUM, 0 social). |
| User + Agent_Cursor | 2026-08-15 | **VR-T1.5a PASS.** Bob (overworld taiga): started far from home; autonomous multi-leg return; entered village at ~`-11666`; hostile interrupt + explore resume **CONFIRMED**. Prior ~74-block dead-zone stop **REPAIRED** (repair pass 3). Frontier → VR-T1.5b–c. `designate-home` removal eligible per D-VR-051; not executed this turn. |
| Agent_Cursor | 2026-08-14 | **D-VR-052 REJECT — task-46 AUTHORIZED.** User: V1.5-E **DEFER → V3** `StorageOwnership`; VR-T1.5d **DEFER → VR-T3**. Release scope: V1.5-A/B/C/D + temporary F only (1.11.0). Attachment ≠ container ownership. No `village-memory`/probe/driver resurrection. |
| Agent_Cursor | 2026-08-14 | **RFC sync — task-46 HOLD state.** Phased plan V1.5 → **HOLD** (D-VR-052 open); next-gate line updated; D-VR-041 → **LOCKED** after P1-2 closure. |
| User + Agent_Cursor | 2026-08-14 | **Task-46 HOLD — User P1 peer review.** Concept/placement/architecture **APPROVED**; implementation **HOLD**. Closed P1-1…P1-8: no `village-memory` re-add (D-VR-051); home single-owner + `onHomeDesignated` (P1-2); presence @ 64 not 96 (P1-3); COMMUTE admission sequence + multi-leg return (D-VR-047/048); `cfg.exploring` required (D-VR-046); social anchor at greet admission (D-VR-050); Option A rekey (D-VR-049). V1.5-E awaits product acceptance (D-VR-052). Bob VR-T1.5 overworld-only. |
| Agent_Cursor | 2026-08-14 | **V1.5 brainstorm continuation 5.** `SettlementBoundsPolicy` @ 64 (`D-VR-040`); `SettlementRelationshipService` write path (`D-VR-041`); commute/mining arbitration + forced-heading expedition (`D-VR-043`); relationship merge on village merge (`D-VR-044`); `SettlementSocialBias` not FriendlyGreet hack (`D-VR-045`); auto-home product decision (`D-VR-042`). B-VR-66…71. **Authorize task-46 / 1.11.0** is the only remaining frontier. |
| Agent_Cursor | 2026-08-14 | **PROGRESSIVE_CONTINUATION — V1.5 design closure.** Code audit: `designateHome()` **zero production callers** (`CONFIRMED`); `FriendlyGreetShelterHoldMixin` does not block `RaidContainersGoal` loot. Locked D-VR-034…037; added D-VR-038 (return via `ExploringGoal` not priority-3 goal), D-VR-039 (home designation gap). V1.5 implementation contract (slices A–F, Option A storage, accumulation bands, VR-T1.5d mixin note). Phased plan V1.5 → **READY**. Fixed stale V1-D / sequence lines. **No implementation authorization.** |
| User + Agent_Cursor | 2026-08-14 | **Village attachment brainstorm → V1.5 phase.** User model: KnownVillage (factual) → settlement experience → `SettlementRelationship` (mob-owned attachment/history) → Opinion/Director → behavior. Attachment accumulates; home stays factual `designateHome()`. **D-VR-034…037 PROPOSED:** V1.5 (return, social, manners precursor) **before V2 Trading**. Deduped against D-VR-025/026, V4 return/home, B-VR-17/40/39. Frontier → **V1.5**. **No implementation authorization.** |
| User + Agent_Cursor | 2026-08-14 | **VR-T1A PASS; frontier → V2 Trading.** User closed VR-T1A: autonomous discovery, full driver path, same-village identity, save/reload, cross-dimension persistence **CONFIRMED**. Debounce overflow root cause + repair runtime-confirmed. Removed `village-probe` / `village-driver` / `village-memory` and trace plumbing. VR-T1b 10/50/100 profiling **DEFERRED**. Permanent-removal sweep static-confirmed, runtime-deferred. |
| User + Agent_Cursor | 2026-08-14 | **VR-T1 partial CONFIRMED (Bob).** Post-debounce: driver RECORDED 8 POIs; memory 1 village anchor `-11666,82,7709`; leave/return same settlement; save/reload persists `First seen:123682`. Pre-fix: debounce `Long.MIN_VALUE` overflow blocked all enqueues. Diagnostics: `village-probe`, `village-driver`. VR-T1b + raid-center **UNVERIFIED**. |
| Agent_Cursor | 2026-08-14 | **PROGRESSIVE_CONTINUATION — D-VR-033 P1 closure.** V1-R4 P0 acknowledged closed. Locked B-VR-56 conditional service + B-VR-57/60 fair admission (ticking-mob-bound queue, round-robin retry, emergency cap only). Scheduler API sketch; V1-D task contract for 1.10.0; VR-33; D-VR-033 → `LOCK RECOMMENDED`. **V1-D authorization still required.** |
| User | 2026-08-14 | **Approve V1-R4 / 1.9.5** (pushed implementation). P0 epistemic leak **CLOSED**; admitted-count regression **CLOSED**. Runtime perception **UNVERIFIED**. D-VR-033 **REVIEW**; V1-D **BLOCKED** → **1.10.0** after scheduler P1 closure. P2: extend no-chunk-load structural test to `PerceptionCoverage.java` (deferred). |
| Agent_Cursor | 2026-08-14 | **V1-R4 implemented (1.9.5).** `PerceptionCoverage` dual pipeline; `ObservationQuality` cross-multiply supersede; optimistic NBT migration; `PerceptionCoverageTest` + contract/structural updates. Village tests green. V1-D still BLOCKED. |
| User | 2026-08-14 | **Accept V1-R4.** `PerceptionCoverage` LOCKED: dual pipeline (coverage independent of `getInRange`); `loadedColumns`/`totalColumns` + cross-multiply supersede; optimistic full-coverage NBT migration; required shrink + worse-coverage tests. D-VR-033 still REVIEW; V1-D BLOCKED. **No implementation authorization.** |
| User | 2026-08-14 | **V1-R4 design amendment.** Reject admitted-count supersede (regresses V1-R1 shrink freeze). Replace withheld with **`PerceptionCoverage`** (loaded/total chunk columns in 64-block footprint). Conditional scheduler Must happen; fair admission over `MAX_QUEUE>=100`; B-VR-59. **No implementation authorization.** |
| User | 2026-08-14 | **D-VR-033 implementation review — V1-D BLOCKED.** P0: `withheldPoiCount` epistemic leak (V1-R4). P1: dirty≠prompt service; queue admission fairness. D-VR-033 → `REVIEW`. D-VR-027 P2 title; 029/030/031 concept locks affirmed; 028 open; 032 held. B-VR-55…58. **No implementation authorization.** |
| User + Agent_Codex | 2026-08-14 | **Peer review of D-VR-027…033 + pinned-source reconciliation.** Split bottle pickup value from retention; redesigned consumption under cross-domain Ominous Event intent; source-confirmed LivingEntity bottle finish but ServerPlayer-gated Bad/Raid Omen effects; made trade abstraction flexible; source-confirmed vanilla golem creator flag; narrowed Hero gifts to recipient bridge + host pickup; rejected `KnownVillage.origin`; superseded pre-1.21 captain-effect and D-VR-019 96-block identity text; added SettlementTier decomposition gate. D-VR-033 selects bounded per-level UUID lanes under one server-global budget; no shared cache. **No implementation authorization.** |
| Agent_Codex | 2026-08-14 | **V1 perception-driver brainstorm.** Confirmed the implemented perception and memory APIs have no production caller (three negative probes). Compared existing-observer, per-mob-budgeted, and central-cache designs; proposed D-VR-033 / V1-D: individual flagless observer, chunk-dirty + heartbeat cadence, strict server-tick POI-query budget, no shared knowledge. Added B-VR-50…54, VR-M1…M7 and 1/10/50/100-mob falsifiers. **No implementation authorization.** |
| User | 2026-08-14 | **Lock D-VR-025.** Hold D-VR-026: reject frozen `placeOpinionChunkKey` (settlement-ID in Place store); keep Place learning/ranking at **current** anchor geography; settlement-persistent pref across anchor moves → SETTLEMENT evidence. B-VR-42 **REJECTED**. **No implementation authorization.** |
| User + Agent_Cursor | 2026-08-14 | **Six user-requested topics:** Ominous Bottle pickup (`OminousBottlePolicy`, HIGH), consumption policy, two-step sell→buy trade chains, manual iron golem construction, Hero gift collection, village founding vs discovery. VR-27…32; B-VR-44…49; D-VR-027…032; phased plan V2/V5/V6/V7 hooks. Fixed missing `VillagerTradeAdapter` topic header. **No implementation authorization.** |
| Agent_Claude + User | 2026-08-14 | **V1-R3 — permanent removal now sweeps every dimension (P0).** Memory is per-dimension, a mob is not, and nothing reconciled them: a mob that learned villages in the Overworld and died in the Nether had `forget()` run against the *Nether* store, leaving the Overworld entry immortal. Since villages are an Overworld feature and PlayerMobs die in the Nether, this was the **common** path — and it would have made V1-R2's `MAX_TRACKED_MOBS` warning fire for an ordinary cause, destroying its signal value. UUID survival across `CHANGED_DIMENSION` confirmed from the pinned jar (`restoreFrom` copies all NBT but `"Dimension"`; `saveWithoutId` writes `"UUID"`) — which is why the dimension change must preserve memory *and* why the delete must be global. Added `forgetEverywhere(server, uuid)` using the **non-creating** `DimensionDataStorage#get`, so cleanup cannot materialise memory files for dimensions that never had one; single-dimension `forget` banned from production by a structural test. **864 tests, 0 failures; 2 new negative controls fire.** Third repair in one family: eviction written against the wrong event (R1), the wrong clock (R2), the wrong scope (R3) — RET-1 asks who evicts and when, never over what **extent**. |
| User + Agent_Cursor | 2026-08-14 | **D-VR-025/026 pre-lock review.** (1) Remembered villages stay selectable while unloaded — pool = `MobVillageMemory`, not current perception. (2) Village consumes `SettlementOpinionBias` (±15); Opinion owns personality/affect math. (3) Current `anchor()` chunk unsafe as Place key — frozen `placeOpinionChunkKey` at discovery. D-VR-025/026 → `LOCK RECOMMENDED` (amended). VR-26; B-VR-41…43. **No implementation authorization.** |
| Agent_Claude + User | 2026-08-14 | **V1-R2 — owner lifecycle separated from memory freshness (P0).** The V1-R1 TTL was the same mistake in a new place: `lastTouchedTick` measures how fresh a memory is, not whether its owner exists, so an alive PlayerMob that mined for thirty in-game days would lose its `HOME_VILLAGE` at the next restart. Replaced with the real seam — `RemovalReason.shouldDestroy()` (`KILLED`/`DISCARDED` delete; chunk unload, player unload and dimension change preserve), verified against `Entity#setRemoved` offsets 9/45 so the reason is populated when Fabric's event fires. `ENTITY_UNLOAD` is usable after all; treating the *event* as the decision instead of the *reason* was the error. `MAX_TRACKED_MOBS` demoted to a warning safety valve with an acknowledged heuristic ordering. **Locked corollary:** memory decay is a cognition policy, never garbage collection. Also added the two User-raised adversarial tests: alternating equal-completeness observations show **no accumulated drift** (replacement, not blending, is why) and stay inside the raid-association radius every step; a monotone sequence **does** follow the settlement — recorded as a documented limitation needing POI-set-overlap identity. Vertical-settlement split (3D `distSqr`) characterised as a VR-T1 scenario. **858 tests, 0 failures; 2 new negative controls fire.** |
| User + Agent_Cursor | 2026-08-14 | **Opinion↔Village boundary (User architecture).** Village produces facts/legal candidates; Opinion soft-ranks among valid options only (*preference does not create permission*). Split `VillageSiteScore` → `FactualVillageUtility` + `OpinionVillageBias`; new topic with Place vs SETTLEMENT investigation (**SETTLEMENT deferred** — use `PlaceOpinionRouteRanker` at `KnownVillage.anchor`). Updated director diagram; VR-16/25; V4 phase; B-VR-37…40; D-VR-025/026 `PROPOSED`. **No implementation authorization.** |
| Agent_Claude + User | 2026-08-14 | **V1-R1 — three corrections from User review of V1.** **P0 (blocker):** `ENTITY_UNLOAD` deleted persisted village memory; Fabric fires it for any entity leaving a world, so a mob wandering out of range erased its own memory. Root cause: copied the shape of neighbouring *runtime*-state releases without checking semantics. **The structural test asserted two call sites and so enforced the defect** — a structural test locks in a wrong invariant as firmly as a right one; it now encodes semantics, not shape. RET-1 re-satisfied by a load-time TTL (30 in-game days) + cap (256/dimension) with a `SERVER_STARTED` caller. **P1a:** identity split from raid association — `VillageIdentityPolicy` (48, ours, `UNVERIFIED`) vs `RaidAssociationPolicy` (`9216`, vanilla, must not drift); one raid may now cover several remembered villages, so a HOME_VILLAGE and a TRADING_POST 85 blocks apart are representable. **P1b:** anchor evidence moved from POI *quantity* to observation *completeness* — `withheldPoiCount` was already computed and discarded; the old `newCount > oldCount` froze the anchor of any village that shrank (20→16) or was rebuilt in place (20→20). D-VR-022/023/024 `LOCKED`. **852 tests, 0 failures; 3 negative controls all fire.** Runtime still `UNVERIFIED`. |
| Agent_Claude + User | 2026-08-14 | **V1 implemented — Village Perception & Identity.** D-VR-019 `LOCKED` with the User's strengthened contract: the anchor must *reproduce* vanilla's raid-centre derivation, not merely share its input predicate. Reading `Raids#createOrExtendRaid` offsets 72–171 disproved the section-conversion hypothesis but found **four** properties a natural rewrite gets wrong (raw coords not block centres; floor not round; Y participates; duplicates significant) — three of which the original one-line wording would have shipped. Perception boundary made a **construction invariant**: `VillagePerception` is the addon's only `PoiManager` reference, the raw stream never escapes, `hasChunk` cannot load. *Storage availability is not perception* — the hidden-ore rule. Ships `VillageAnchorPolicy`, `VillagePerception`, `KnownVillage`, `SettlementTier`, `MobVillageMemory`, `VillageMemorySavedData`; RET-1 bounded (16, LRU, home exempt) with production eviction on unload + death. **28 new tests, 837 total, 0 failures; 4 negative controls all fire.** V1 got *smaller* under review — no bell, no `KnownVillager`, no site score, no goal. Runtime `UNVERIFIED`. |
| Agent_Claude | 2026-08-14 | **Vanilla player-gate audit + independent peer review.** Read method *bodies* from the pinned 1.21.1 jar rather than signatures: hero credit is gated by **one `EntityType` comparison** in `Raider#die` while `Raid#tick` awards to any `LivingEntity` (VR-11 downgraded, D-VR-020); villager gossip is **entity-agnostic and already running**, so B-VR-13 is a live behaviour not a feature (VR-4 reclassified, D-VR-021); vanilla defines “village” as a `PoiTypeTags.VILLAGE` + `IS_OCCUPIED` query that also places `Raid.getCenter()`, so a hand-rolled anchor would **silently disable D-VR-010** (D-VR-019, **contests D-VR-009's detection half**); raid *initiation* confirmed hard at the entity level (`ServerPlayer`-owned omen state). **D-VR-010/011/012 → `LOCKED`** on this review. B-VR-30–36; VR-23/24. **No implementation authorization.** |
| Agent_Cursor | 2026-08-14 | **Day/night director arbitration topic** (user request). `VillageDayNightContext`, priority matrix, shelter/raid/trade integration with `SeekShelterGoal` dusk window + `ShelterInterruptionPolicy`; MAIBS V5b; VR-22; B-VR-29; D-VR-018. **No implementation authorization.** |
| Agent_Cursor | 2026-08-14 | **Brainstorm continuation (2).** Mojmap verify: `BellBlock.attemptToRing`, `Raid.addHeroOfTheVillage`; `MaterialDemandPolicy` NOT FOUND → `WorkDemandPolicy` facade topic; `ShelterThreatPolicy` ↔ raider aggro coupling (B-VR-21); MAIBS V2 trade table; B-VR-19…28; D-VR-013…017; D-VR-008/009…012 **LOCK RECOMMENDED**. **No implementation authorization.** |
| Agent_Cursor | 2026-08-14 | **Brainstorm continuation.** Evidence re-audit (SPM v0.86.0 + Scavenger): `RaiderTargetsPlayerMobMixin` witch hostility, `RaidContainersGoal` P3 + shelter-hold mixin only partial ally fix, no `KnownVillage` in code (3× NOT FOUND). Added topics: **advanced village site selection** (settlement tiers, `VillageSiteScore`, micro anchor pick), **cross-system reuse** (TaskLifecycle, SCR-2R5 EVACUATE, ActivityAdmission pattern). VR-16…20; MAIBS V1/V5 tables; D-VR-009…012 `PROPOSED`; brainstorm B-VR-09…18. **No implementation authorization.** |
| Agent_Cursor | 2026-08-08 | Initial village/raid parity RFC; SPM v0.86.0 audit; Mineflayer comparison; no implementation |
| Agent_ChatGPT | 2026-08-08 | `VillageInteractionDirector`; human-vs-villager parity (`D-VR-004`); `VillagerTradeAdapter`; `VillageMemory`; bell/farm/population/golem/raid composition; V1–V7 phases; curiosity catalogue |
| Agent_Cursor | 2026-08-08 | Integrated ChatGPT contribution into RFC; decisions D-VR-004–008; superseded P0–P5 → V1–V7 |

---

## Contribution — Agent_Cursor (brainstorm continuation 2, 2026-08-14)

**Contribution type:** `BRAINSTORM_IN_RFC` / `PROGRESSIVE_CONTINUATION`

**Frontier before:** D-VR-009…012 proposed; bell/hero API names `INFERRED`; `MaterialDemandPolicy` cited but absent in code.

**Code evidence (`CONFIRMED`):**

- Mojmap: `BellBlock.attemptToRing(Entity, …)`, `Raid.addHeroOfTheVillage(Entity)` — Loom cache tiny mappings.
- `MaterialDemandPolicy` — **NOT FOUND** (3 probes); `WorkDemandPolicy.MaterialDemand` is production seam.
- `ShelterThreatPolicy` — `Enemy` + `NEARBY_HOSTILE` overrides shelter; couples EVACUATE to raider aggro.
- No `KnownVillage` / trade / bell goals in `src/main` — village domain remains RFC-only.

**Delivered:** API mapping topic; trade-demand facade topic; shelter↔raid coupling; MAIBS V2; B-VR-19…28; D-VR-013…017; VR-21 row; D-VR-008/009…012 **LOCK RECOMMENDED**.

**Strongest objection:** coward EVACUATE + `RaiderTargetsPlayerMobMixin` + `ShelterThreatPolicy` may produce door-bed oscillation without hysteresis — watch in VR-T5 before adding raid-specific threat fork.

**Viable alternative:** custom micro-executor for bell ring via `CommandedUse` prototype instead of direct `attemptToRing` — higher fidelity risk if fake-player path differs from entity initiator.

**Frontier after:** **lock D-VR-009…014** → authorize **V1 task brief** (`VillagePerception` + `KnownVillage` cluster heuristic + settlement tiers; no trade, no raid). VR-T1 datapack fixture (B-VR-28) can ship with V1 static tests.

---

## Contribution — Agent_Claude (brainstorm continuation 3, 2026-08-14)

**Agent:** `Agent_Claude`
**Contribution type:** `BRAINSTORM_IN_RFC` / `REVIEW` / `RESEARCH` — no implementation

**Frontier before:** D-VR-009…012 sitting at `LOCK RECOMMENDED` with no independent peer review;
bell/hero API names pinned by *signature* only; V1 task brief unauthorized.

**Reviewed:** `Agent_Cursor` — advanced village site selection, cross-system reuse, API mapping
verification, day/night arbitration. `Agent_ChatGPT` — `VillageInteractionDirector`,
`VillagerTradeAdapter`, bells/farming/population/golems, raid orchestration.

**Agreement.** The architecture is sound and I endorse it: director-over-executors rather than a
mega-goal; reuse of `TaskLifecycle`, SCR-2R5 and the shelter-hold mixin precedent instead of parallel
raid state; server-side trade adapter over a fake GUI; profile-gated ally behaviour. `Agent_Cursor`'s
`ShelterThreatPolicy` ↔ raider-aggro coupling (B-VR-21) is the kind of self-limiting interaction that
usually gets discovered at runtime, and finding it statically was good work.

**Concerns.** The RFC's evidence standard is *signatures*, and signatures under-determine gates. I
read the method bodies instead and three of four conclusions moved — two in our favour, one against.
Detail in `Topic: Vanilla player-gate audit`.

- **`CODE_CONFIRMED`** — hero credit is one `EntityType` comparison in `Raider#die`; the award loop
  in `Raid#tick` accepts any `LivingEntity` via `level.getEntity(uuid)`. VR-11 shrinks from
  "reimplement the reward" to "widen one check" (D-VR-020).
- **`CODE_CONFIRMED`** — villager gossip is entity-agnostic on both write and dispatch, with the sole
  `instanceof Player` in `Villager` being a cosmetic particle. VR-4's "REQUIRES API" is wrong, and
  B-VR-13 is a **live behaviour today**, not a feature to build (D-VR-021).
- **`CODE_CONFIRMED`** — vanilla defines a village as a `PoiTypeTags.VILLAGE` + `IS_OCCUPIED` query
  and uses it to place `Raid.getCenter()`. This is the one that matters: it **contests D-VR-009's
  detection half** (D-VR-019).
- **`CODE_CONFIRMED`** — raid *initiation* is harder than stated; the 1.21 omen intermediate state is
  declared on `ServerPlayer` itself.

**Strongest objection (mine).** D-VR-009's hand-rolled anchor and D-VR-010's `getRaidAt(anchor)`
trigger were designed in different topics and never reconciled. Together they produce the worst
failure shape in this RFC: **a correct interrupt that never fires**, with no crash, no log line, and a
post-mortem indistinguishable from "not implemented yet". Two independently reasonable decisions
compose into silence. That is why D-VR-010's lock is recorded as *conditional* on D-VR-019 rather
than granted outright.

**Objection against my own proposal.** `PoiManager` extends `SectionStorage` and returns persisted
POIs for unloaded chunks. Used naively, D-VR-019 is *more* omniscient than the heuristic it replaces
— the cluster scan could only ever see loaded blocks. The loaded-chunk bound is a must-not-happen
test (B-VR-33), not a footnote.

**Alternative I considered and rejected.** Keep the cluster heuristic and reconcile it to the raid
centre *after* the fact — on raid start, snap `KnownVillage.anchor` to `Raid.getCenter()`. It is
cheaper and needs no POI access, but it only repairs the anchor once a raid already exists, which is
exactly when the interrupt needed to have fired. Rejected: it fixes the symptom one tick too late.

**Recommendation.** Accept D-VR-019, re-lock D-VR-009, then authorize the V1 brief. V1 gets
*smaller* under this review, not larger — the detection heuristic disappears in favour of a query.

**RFC fields updated:** new `Topic: Vanilla player-gate audit` (F1–F4 + MAIBS-1 prediction);
VR-1, VR-4, VR-11 reclassified; VR-23, VR-24 added; B-VR-30…36; D-VR-019/020/021 `PROPOSED`;
D-VR-009 → `CONTESTED`; **D-VR-010, D-VR-011, D-VR-012 → `LOCKED`**; deferred table; identity header.

**Frontier after:** decisions are no longer the frontier. **Implementation authorization for V1 is.**
D-VR-019 needs one product acceptance (it contests a peer's decision, so I will not self-lock it),
and V1's scope is now: `VillagePerception` (bounded POI query) → `KnownVillage` with a raid-agreeing
anchor → settlement tiers → home anchor NBT. No trade, no raid, no bell.

---

## Contribution — User + Agent_Codex (D-VR-027…033 peer review, 2026-08-14)

**Agent:** `User` (review/design) + `Agent_Codex` (source audit/RFC integration)
**Contribution type:** `REVIEW` / `RESEARCH` / `DESIGN` — no implementation

**Frontier before:** D-VR-027…032 were broad proposals, D-VR-033 preferred a per-mob permit owner,
Scenario D still described the pre-1.21 captain-kill effect, and D-VR-019 still claimed the raid
association radius was settlement identity.

**Evidence audited:** official Java 1.21 notes (five Ominous Bottle variants, stack 64, captain drop,
Bad Omen as cross-event entry, 600-tick Raid Omen and milk abort); pinned 1.21.1 method bodies for
`OminousBottleItem`, `BadOmenMobEffect`, `RaidOmenMobEffect`, `CarvedPumpkinBlock` and
`GiveGiftToHero`; SPM `CommandedUse`, `EatFoodGoal` and `CollectFloorItemsGoal`.

**Agreement:** strategic bottle pickup, demand-owned multi-step trading, manual golem construction
and Hero gift parity are valuable. **Amendments:** retention is separate from pickup; exact trade
ticket is not locked; repair precedes build when comparable; Hero gifts reuse host floor pickup;
founding changes world truth and never mints memory directly. **Correction:** bottle finish itself is
LivingEntity-compatible, while the actual raid conversion/creation effects are ServerPlayer-gated.

**Scheduler resolution:** D-VR-033's knowledge boundary and hard budget are sound. B1's per-mob
permit contention needs remembered ordering to prove fairness, which recreates a queue implicitly;
B2 therefore wins as explicit bounded per-level UUID lanes under one server-global budget. It is not
the rejected shared cache because it schedules an individual observation without sharing its result.

**Strongest objection:** cross-domain Ominous Event ownership could become an abstraction with only
one consumer if Trial progression never exists; keep the boundary small and data-bearing rather than
building a generic event framework prematurely. For B2, stale UUID retention/cleanup is the principal
failure; for B1, starvation is the principal failure.

**Acceptance:** must preserve the 1.21 bottle lifecycle, individual village knowledge, ordinary V1
perception authority, host item pickup, and vanilla golem spawn semantics. Must not restore captain
kill → Bad Omen, broadcast observations, invent founded memory, or let a high pickup rank fill every
inventory slot.

**Frontier after:** **V1-R4 `ACCEPTED`** — implement when authorized, review code/tests, close
D-VR-033 scheduler P1s, re-lock D-VR-033, then V1-D authorization.

---

## Contribution — Agent_Codex (brainstorm continuation 4, 2026-08-14)

**Agent:** `Agent_Codex`
**Contribution type:** `BRAINSTORM_IN_RFC` / `RESEARCH` / `DESIGN` / `MAIBS` — no implementation

**Frontier before:** V1's pure perception, identity and persistence substrate was static-tested and
hardened through R3, but no production code called `VillagePerception.observe` or
`VillageMemorySavedData.record`. The RFC named cadence and 1/10/50/100-mob cost as next, without an
owner or pass/fail design.

**Evidence:** three production probes found no observer call, no memory-record call and no installed
village driver. `VillagePerception` confirms each observation is one radius-64 POI query;
`ExplorationActivityGoal` confirms a reusable flagless/staggered pattern but also owns unrelated
ten-tick control-plane work; `PhasedScanClock` confirms late GoalSelector polls can be scheduled
without losing a modulo slot. Evidence state is `CODE_CONFIRMED`; runtime cost remains `UNVERIFIED`.

**Design review:**

- **Option A:** attach the query to `ExplorationActivityGoal`. Lowest code count, but wrong coupling:
  village cost and mining/Opinion cadence would share a knob.
- **Option B (recommended):** single-purpose per-mob flagless driver plus a server-tick budget gate.
  Preserves individual perception and provides a hard burst ceiling without caching facts.
- **Option C:** central scheduler/cache. Best potential query deduplication, but adds RET-1 state and
  risks turning one mob's perception into shared omniscience before profiling proves it necessary.

**Predicted observable behaviour:** no movement, readout or scheduler change; a mob records a village
after physically entering it and waiting for a bounded permit. The likely weird cases are a compact
village crossed between scans, a boundary observation whose anchor changes as chunks become
available, a 100-mob pending backlog, and a denied entity starving. Chunk-transition dirtying,
heartbeat refresh, a hard permit cap and fairness tests address those at the contract level.

**Strongest objection:** proposed 200/20/1 values are not measured optima. The hard cap is a safety
bound, not proof of acceptable TPS. Peer review should challenge them; runtime VR-T1b must measure
query frequency/cost and discovery latency before performance is called confirmed.

**What would disprove the recommendation:** Option B should be reopened if bounded runtime profiling
shows spatially redundant POI queries dominate at 50/100 mobs, or if GoalSelector scheduling cannot
provide eventual service under collisions without a queue. That evidence would justify C, provided
individual observation remains the knowledge boundary.

**Delivered at that contribution stage (historical; superseded by the next peer review):** stable
`V1 perception driver and observation budget` topic; B-VR-50…54; D-VR-033 initially `PROPOSED`;
V1-D plan row; MAIBS VR-M1…M7; updated deferred/frontier state.

**Frontier after at that stage:** independent peer review of D-VR-033. User implementation review
(2026-08-14) accepted B2 direction but **blocked V1-D**. User **accepted V1-R4** `PerceptionCoverage`
(2026-08-14). Current frontier: implement R4 when authorized → review → close scheduler P1s → V1-D.

---

## Appendix A — SPM vs “Interactive Player Mobs”

Same mod (`playermob`). No separate IPM codebase in workspace.

## Appendix B — Link to survival progression

Village trades consume **emeralds** and **tools** that chain to `RFC-VANILLA-AUTONOMOUS-PROGRESSION.md` (iron pick, armor, food). Single `RequirementResolver` should include both graphs. `MaterialDemandPolicy` links trade evaluation to tool-tier and survival demands (`RFC-TOOL-TIER-UPGRADES.md` D-TTU-017). **Production today:** extend `WorkDemandPolicy.MaterialDemand` per D-VR-015 / B-VR-20 until a renamed policy ships.

## Appendix C — Licence constraint

SPM is **PolyForm Shield 1.0.0**. Trade/defense goals belong in **`spmscavenger`** with reflection (`PlayerMobs.java` pattern) or user-authorized SPM collaboration — not silent SPM forks.

## Appendix D — Village interaction executor catalogue (`Agent_ChatGPT`; conceptual names)

| Goal | Phase | Reuses |
| --- | --- | --- |
| `VillagePerception` (tick observer) | V1 | — |
| `RingVillageBellGoal` | V1 | `InteractableCapability` |
| `TradeWithVillagerGoal` | V2 | `VillagerTradeAdapter` |
| `SupportVillagePopulationGoal` | V3 | `GiftPolicy`, `HarvestCropsGoal` |
| Committed harvest→replant episode | V3 | one target-bound harvest executor; **not** a separately admitted `ReplantCropGoal` (`D-VR-079`) |
| `CompostGoal` | V3 | `InteractableCapability` |
| `CureVillagerGoal` | **V6 (DEFERRED; prior V3 row SUPERSEDED by D-VR-078)** | adapter + `CommandedUse` + player-credit/relationship decision |
| `RaidAwareness` (observer) | V5 | `level.getRaidAt` |
| Raid support bundle | V5 | bell + SPM combat + shelter |

Existing activity authority admits these executors: urgent work and live/pending mandatory
progression preempt discretionary village work. V3 does not add a second utility director
(`D-VR-078`); V3 goal priority **4** and `VillageWorkAdmission` are **LOCKED** (`D-VR-082`).

---

### D-VR-077: V2-TE trade-source architecture — three coordinates, source provenance, caller-authorized quote inputs (`User` + `Agent_Claude`, 2026-08-17)

**Status:** `IMPLEMENTED / POSITIVE PATH RUNTIME CONFIRMED` by `V2-DEF-003c-R1`; V2-TE is closed.
`VR-T2l` remains `UNVERIFIED / DEFERRED / NON-BLOCKING`.
**Refines:** `D-VR-068` (unchanged; this fixes the type design under it).
**Preconditions closed:** `P0-1` (ensureIndexed prerequisite, direct↔TE live quote parity, session
teardown), `P0-2` (detached execution source trace + runtime witness, TE `afterTrade` fires
detached, marker preservation, `rewardExp` observed), `P0-3` (economic B reachability). The
compatibility unknowns were answered experimentally, the permanent multi-source model was
implemented, and the 2026-08-19 Step-7A run exercised its positive path end to end. The separate
absent/incompatible-source negative is deferred validation, not the RFC frontier and not production
authority for the positive path.

#### Accepted — 1. Revalidation strictness is per source, and vanilla must not change

```
VANILLA   same source object at a stable address
          board[index] + transaction-equivalent effective cost A/B + result
TE        two independently generated objects, no shared identity
          fresh requote + strict planned-Q1 <-> execution-Q2 semantic correspondence
```

`OfferSnapshot.matchesLive` stays exactly as it is. Widening it to full semantic equality would
abort vanilla trades that succeed today, because `demand` and `specialPriceDiff` legitimately move
between selection and execution and the vanilla path executes the live object anyway. Strict
comparison is a **TE-source operation**, not a stronger `matchesLive`.

#### Accepted — 2. Three coordinates, never fewer

Today `OfferSnapshot.index` carries two meanings, and `TradeWithVillagerGoal` already builds a third
space by hand (`TradeWithVillagerGoal` ~L610–623: `int slot = 0` flat ranking space, `owners` keyed
by slot, inner `Candidate` retaining the villager-local index).

| # | Concept | Vanilla | Trade Everything |
| --- | --- | --- | --- |
| 1 | source-local resolution key | board index | exact requote input stack |
| 2 | source provenance | `VanillaTradeSource` | `TradeEverythingTradeSource` |
| 3 | round-local deterministic ordinal | flat ranking slot within one bounded planning pass | same |

```
OfferSnapshot { OfferRef ref; costA, costB, result; uses, maxUses, xp,
                priceMultiplier, demand, specialPriceDiff, rewardExp }

OfferRef      = BoardIndex(int index) | Requote(ItemStack inputKey)      // (1) ONLY

Candidate     { Villager villager; TradeOpportunitySource source;        // (2)
                OfferSnapshot offer; int rankOrdinal;                    // (3)
                consumerKey, materialKey }
```

**Rejected:** `OfferRef.tieBreak()`. A ref cannot supply the global ordinal — two villagers both
legitimately hold `BoardIndex(0)`, and two TE candidates can quote the same input against different
villagers. Deriving (3) from (1) re-creates the overload this decision exists to remove.

`TradeEvaluation.offerIndex` becomes `tieBreakOrdinal`. V2-B ranking has no business knowing a
market address, and naming it `index` is what invited the conflation.

#### Accepted — 3. Provenance is carried, never inferred

`Candidate`/attempt evidence retains its `TradeOpportunitySource` (or a stable source key), so
execution is `source.revalidate(villager, plannedOffer, …) -> MerchantOffer -> executeResolved(…)`.

**Rejected:** inferring the source from `ref instanceof BoardIndex`. That works for exactly two
sources and turns `OfferRef` into a disguised source enum.

#### Accepted — 4. Opportunity truth must not become spend permission

```
TradeOpportunitySource
    List<OfferSnapshot> offers(Villager villager, TradeOpportunityQuery query)
    Optional<MerchantOffer> revalidate(Villager villager, OfferSnapshot q1, Container backpack)

TradeOpportunityQuery { authorizedSellInputs : bounded exact stack copies }
```

**Rejected:** `offers(Villager, Container backpack)`. Handing a market source the whole backpack asks
it to decide what may be sold. Disposition is V2 policy and stays there:

```
SellReserveModel / disposition   "you MAY consider selling this"
TradeEverythingTradeSource       "if you did, the market quote is this"
```

The source must never derive the first statement from the second. Vanilla ignores the inputs and
exposes its real board; TE quotes only the supplied stacks. This also bounds the quoting cost
(0.172 ms measured per quote) to `authorized items x selected villagers` — at most three item
families today.

#### Accepted — 5. TE demand-immunity is scoped, not global

`OfferQuoter.quote` calls `BuybackPricer.buybackOffer(input, offers)` **first** and returns it when
present. Resolved by positional intermediary↔Mojmap pairing of `class_1914`:

```
method_19272 -> getCostA()      live, demand/special-price adjusted   <- BuybackPricer uses THIS
method_57556 -> getItemCostA()  base                                  <- TradePricer uses this
method_8250  -> getResult()                                           <- TradePricer uses this
```

So: **ordinary TE synthetic valuation reads base offer counts and is largely immune to vanilla
demand/special-price drift; TE's buyback path is explicitly live-price sensitive.** Gen-1 funding
inputs (logs/planks/sticks) are expected to take the ordinary path because they are not the
Armorer/Toolsmith outputs being bought, but that is an expectation about *our current inputs*, not a
property of TE. Strict execution-time requote is what actually handles it.

**Rejected:** the claim "TE quotes cannot move from demand drift" (`Agent_Claude`, corrected by
`User` and verified against the jar). It was true only for the ordinary path and false as stated.

#### Accepted — 6. `ensureIndexed` contract (gen-1)

```
prewarm at known lifecycle boundaries (server ready, datapack reload)
+ always ensureIndexed defensively before quote/requote
+ accept that an external TradeEverythingApi.reload() may cause ONE cold rebuild on next use
```

Measured: cold 11.739 ms, repeated 0.004 ms — memoized on `(RecipeManager, config)` identity.
`TradeEverythingApi.reload()` is public, so an external config swap can invalidate the memo at any
time and Scavenger cannot intercept it.

**Rejected:** "never call `ensureIndexed` in a goal tick" (`Agent_Claude`) — too strong, and it would
require an asynchronous indexing state machine for a rare external event. TE's own merchant-container
path calls `ensureIndexed` defensively before repricing; matching it is the consistent choice. The
cold cost is documented, not engineered around.

#### Accepted — 7. The gate is deterministic convergence, not statistical rarity

**Rejected:** "measure whether Q1↔Q2 divergence is rare" (`Agent_Claude`). A one-time board mutation
produces `plan → walk → reject → replan → execute`, which is convergence, not churn; a churn loop
(`RET-1c`) needs mutation during *every* bounded attempt, and the candidate round plus chain lifetime
already bound repeated failure.

The witness instead proves the architecture cannot fossilize on stale TE evidence:

```
Q1 -> mutate the real board through vanilla behaviour -> Q2
   -> strict TE revalidation REJECTS
   -> replan from the changed board -> Q3
   -> without further mutation, Q3 == execution requote -> execution admissible
```

Plus one explicit **buyback** case, since upstream source says live-price drift is detectable there.

#### Implementation sequence (authority still withheld)

1. `V2-DEF-001` repair, independently (`docs/porting/KNOWN_DEFECTS.md`).
2. Pure vanilla refactor: `OfferRef` = source-local resolution identity only; separate round-local
   `rankOrdinal`; `matchesLive` semantics preserved exactly.
3. Source provenance added to `Candidate`/attempt evidence.
4. `TradeOpportunitySource` with caller-authorized quote inputs, not raw backpack authority.
5. `VanillaTradeSource`; prove behaviour parity.
6. Optional gated `TradeEverythingTradeSource` (must not class-load when TE is absent — no
   `static final` field or direct reference on an eagerly-loaded common path; register behind
   `isModLoaded`).
7. Deterministic mutation → reject → replan → converge runtime witness.

#### Implementation tightening — `revalidate` takes no backpack (added post-lock by `User`, 2026-08-17)

The locked sketch read `revalidate(Villager, OfferSnapshot, Container backpack)`. Neither proven
source needs inventory to establish market truth:

```
VANILLA   villager board + BoardIndex + matchesLive
TE        villager board + Requote inputKey + fresh OfferQuoter.quote
```

Affordability and payment belong to `VillagerTradeAdapter` after resolution. Passing the raw backpack
would hand a market source authority it does not need — the same ownership temptation already removed
from `offers(...)`. The interface is therefore:

```java
interface TradeOpportunitySource {
    TradeSourceKey key();
    List<OfferSnapshot> offers(Villager villager, TradeOpportunityQuery query);
    Optional<MerchantOffer> revalidate(Villager villager, OfferSnapshot planned);
}
```

Not a change of meaning — the existing rule applied consistently: **the source reports opportunity
and world truth; it does not decide spend permission.**

#### Implementation invariant — attribution binding (added post-lock by `User`, 2026-08-17)

> Every production PlayerMob villager transaction must reach `notifyTrade` through the
> attribution-preserving villager binding. **No production path may supply raw
> `villager::notifyTrade` to `executeResolved`.**

`V2-DEF-001` is currently prevented by `performTrade` supplying
`preservingAttribution(villager)`. `executeResolved(backpack, live, Consumer)` is public and accepts
an arbitrary notifier, so a future TE source could write
`executeResolved(backpack, freshTeQuote, villager::notifyTrade)` and resurrect a defect that was
already fixed — with no compile error and no test failure, because no vanilla test would exercise
that call.

When the source architecture lands, make it impossible by shape rather than by rule:

```
source.revalidate(...) -> MerchantOffer live
        v
VillagerTradeAdapter.performResolvedTrade(backpack, villager, live)
        v   availability -> attribution preservation -> executeResolved
```

`executeResolved(..., Consumer)` then becomes package-private/test-only once the TE probes are
removed.

#### Implementation invariant (added post-lock by `User`, 2026-08-17 — not a decision change)

`OfferRef.Requote` identity is **item + exact components, never held count**. Canonicalize the stored
key to `input.copyWithCount(1)`, and apply the same normalization to
`TradeOpportunityQuery.authorizedSellInputs`: "exact" means exact item variant and components, while
count is canonicalized away for quotation.

```
64x oak_log   |
42x oak_log   |  different inventory quantities, SAME TE quote identity
```

Quantity belongs to current inventory, `SellReserveModel`, disposable units, affordable uses and the
transaction-time debit — not to `OfferRef`. The query says which stack *kinds* policy permits the
market source to quote; it must not accidentally encode how much permission exists.

This follows Trade Everything itself. `MerchantContainerMixin` compares its remembered input with
`ItemStack.isSameItemSameComponents` and stores it as `input.isEmpty() ? EMPTY : input.copyWithCount(1)`
(`CONFIRMED` — `method_31577` / `method_46651` resolved by positional intermediary↔Mojmap pairing of
`class_1799`; `iconst_1` at offset 148 of `tradeeverything$repriceInner`). Without it we would
recreate the identity/quantity conflation this decision exists to remove, one layer down.

Steps 2–5 are provable with Trade Everything uninstalled, which is where a regression would hide.
`VillagerTradeAdapter.executeResolved` remains the sole transaction owner throughout: one staging
array, one debit pair, one preflight, one commit, one `notifyTrade`. No second offer model, no second
transaction system, no `TradeEverythingTradeGoal`.

### D-VR-078: One canonical V3 phase and explicit capability dispositions (`User` + `Agent_Codex`, 2026-08-19)

**Status:** `LOCKED` for phase identity and scope disposition; individual V3 mechanisms remain at
their task/decision lifecycle states.

**Conflict preserved and resolved:** the earlier dependency graph called reputation/discounts
`Tier V3`, while the later implementation map called V3 Village Work. The dependency graph remains
useful history but its phase labels are `SUPERSEDED`; it is renamed conceptual `L0…L5`. “V3” now
unambiguously means:

```text
Village Work
  IN: committed crop harvest→replant
  IN: composting from disposable surplus
  IN: population food support
  IN: read-only workstation awareness
  IN: ally/public storage safety
  OUT / V6: zombie-villager curing
```

**Superseded statements:** legacy `P5 Cure → V3`, Appendix-D `CureVillagerGoal | V3`, and any old
`Tier V3 Reputation` implementation reading. Reputation-aware selection belongs to V4; player-typed
discounts and curing belong to V6. The non-player reputation-consumer probe follows those consumers
and does not gate V3.

**V2 disposition:** V2 and the V2-TE positive path are closed to their recorded runtime scope.
VR-T2l, V2-I, profiling, proactive registry pruning, and dense-villager merchant windows are
deferred/non-blocking. `D-VR-076` is a Wealth-track-local sequence, so V2-W does not block V3.

**Rejected:** leaving both V3 labels active; closing V3 from only replant + one chest scenario;
silently leaving curing owned by two phases; treating deferred V2 verification as the main frontier.

### D-VR-079: Managed harvesting is one committed harvest→replant episode (`User` + `Agent_Codex`, 2026-08-19)

**Status:** `LOCKED` architecture direction + **`IMPLEMENTED / STATIC-BEHAVIORAL ACCEPT`** in
task-55; runtime VR-T3a–c/k/l/m remains `UNVERIFIED`.

**Accepted:** candidate, crop/seed representation, held-seed reserve, target position, world
preconditions, harvest mutation, replant mutation, exceptional repair, invalidation, and completion
belong to one episode. Before mutation the candidate/path is disposable. At interaction, revalidate
and perform harvest→age-0 replacement in one server tick; declare success only after replacement is
present. If an exceptional post-mutation failure occurs, the exact-position repair remains mandatory
and bounded until restored or invalidated by changed support/another actor.

**Why this fits the target:** pinned host `HarvestCropsGoal` currently removes the crop, spills seeds,
and can be preempted like ordinary priority-6 work. A second independently admitted replant goal
would make bare farmland an expected GoalSelector state rather than an exceptional repair state.

**Alternative rejected:** separate `ReplantCropGoal` for the normal managed-field path. It is locally
simple but cannot preserve ownership across interruption, resource reallocation, or another activity
winning between harvest and replant. A later repair-only executor may implement the exceptional
cleanup step, but it consumes the same episode; it is not a second appetite or authority.

**Must happen:** managed harvest with valid support/held seed ends replanted at the same position.
**Must not happen:** successful managed harvest routinely returns to discretionary arbitration while
the position is bare.

### D-VR-080: `VillageScenarioProfile` persistence and acquisition (`User` + `Agent_Cursor`, 2026-08-19)

**Status:** `LOCKED`

**Accepted:**

- `VillageScenarioProfile` (`NEUTRAL`, `VILLAGE_ALLY`, `COWARD`, `TRADER`, `RAIDER`, …) is **one
  cross-dimension policy value per mob** — a PlayerMob policy, not village cognition.
- **Do not** store the profile in `MobVillageMemory` or `VillageMemorySavedData`. Those are
  deliberately **per-dimension** village memory; a profile there would allow incoherent states such as
  `VILLAGE_ALLY` in the Overworld and `NEUTRAL` in the Nether for the same mob.
- Implementation: entity-attached NBT **or** a dedicated cross-dimension policy `SavedData` accessor.
  If a new per-mob persisted store is introduced, it **must** register in
  `PerMobSavedData.forgetAll()` (`PerMobSavedData.java` — Gate RET-1e permanent-removal contract).
- **Default:** `NEUTRAL` on first load and for existing-world migration.
- **`VILLAGE_ALLY` acquisition (gen-1):** explicit operator command and/or `ScavengerConfig` default
  at spawn only — never silent promotion from `HOME_VILLAGE`, `AttachmentBand.HIGH`, trade episodes, or
  Opinion preference.
- `SettlementRelationship` remains factual input for V4/V5 (social bias, raid DEFEND utility) and does
  **not** imply ally permission (extends D-VR-017).

**Rejected:** profile inside dimension-local village memory; HOME/HIGH → `VILLAGE_ALLY` without explicit
assignment; per-village UUID allowlists.

### D-VR-081: Storage permission lifecycle and keys (`User` + `Agent_Cursor`, 2026-08-19)

**Status:** `LOCKED` (implements D-VR-017 positive-evidence half)

**Accepted:**

- Positive permission registry entries are keyed by **`GlobalPos`** (dimension + block position), not
  naked `BlockPos`.
- **Semantic persistent state** — permissions mean *"this mob may use this container"* until revoked or
  invalidated:
  - chunk unload → **PRESERVE**
  - mob dimension change → **PRESERVE**
  - server stop / restart → **PERSIST** (save/load)
  - explicit operator revoke → **DELETE**
  - container block destroyed or replaced (block entity identity lost) → **DELETE** or classify **UNKNOWN**
  - mob permanently removed (`PerMobSavedData.forgetAll`) → **DELETE** that mob's grants
- **Double chests:** gen-1 must define a **deterministic** rule — either attach permission to the
  logical container (both halves share one canonical key) or canonicalize both halves to the same
  registry entry before classify/admit. Opening the opposite half must not change ownership class.
- Classifier output unchanged from D-VR-017: `MOB_OWNED`, `EXPLICITLY_SHARED_WITH_MOB`,
  `VILLAGE_PUBLIC`, `FOREIGN`, `UNKNOWN` (ally + non-permitted → deny admission **and** continuation).
- Guard: continuous predicate on pinned SPM `RaidContainersGoal` admission/continuation — not global
  goal removal.

**Rejected:** evicting permission grants on chunk unload, dimension change, or server stop (conflicts
with RET-1 semantic-memory contract in `PerMobSavedData` javadoc).

### D-VR-082: Village work admission and priority (`User` + `Agent_Cursor`, 2026-08-19)

**Status:** `LOCKED`

**Accepted:**

- V3 executor goals register at **priority 4** — below deliberate-work band **3** (gather/craft/smelt/trade
  per `SpmScavenger.java`) and above explore/wander **8**.
- **`VillageWorkAdmission`** (single admission facade) gates every V3 goal `canUse()`. Invariant:

```text
NO LIVE MANDATORY OWNER  →  village work may admit
ANY LIVE MANDATORY OWNER →  village work must refuse
```

- **Mandatory owner** is **broader than `WorkDemandPolicy` / `MaterialDemand` alone.** Admission must
  consult at least:
  - live consumer `MaterialDemand` (progression)
  - active or pending **mining project execution** (`MiningExecutionGuard` / `PROJECT_EXECUTION`)
  - **mandatory cleanup/repair** commitment (e.g. post-mutation crop repair per D-VR-079)
  - **published mandatory handoff** still owning the slot (`MandatoryHandoffPolicy` / `HandoffPublication`)
  - urgent safety / player command / combat (may additionally be enforced by higher GoalSelector priority)
- Among discretionary V3 intents only, an optional **`VillageWorkSelector`** (or `VillageWorkPolicy`)
  picks ≤1 candidate (crop episode, population food, compost, …). This is **subordinate** to the RFC's
  village orchestration architecture (`VillageInteractionDirector` when shipped) — **not** a parallel
  `VillageWorkDirector` competing with `MiningDirector`, `DiscretionaryActivityDirector`, etc.
- Storage safety remains **outside** this admission (`D-VR-081` continuous guard).

**Rejected:** `WorkDemandPolicy.select().isEmpty()` as sole mandatory gate; introducing
`VillageWorkDirector` as a fourth top-level director; cargo-culting host `HarvestCropsGoal` priority 6
for village-work banding.

### D-VR-083: Village work budget contract (`User` + `Agent_Cursor`, 2026-08-19)

**Status:** `LOCKED` (contract); numeric constants **`PROVISIONAL` / `UNVERIFIED`** until profiling

**Accepted — budget contract (architecture):**

- Crop / workstation / composter discovery **must**: inspect **loaded** chunks only; use a **finite**
  spatial radius; cap **candidate count** per evaluation; **back off** after empty/blocked search (never
  scan every tick); **reuse** `VillagePerceptionScheduler` / existing village perception where
  applicable (no second world scanner for V3-D).
- **Population food support eligibility (gen-1) — amended by D-VR-083-A1 (User, 2026-08-21):**

```text
Task-56 factual layers (settlement-bound, loaded-only):
    adultVillagerCount
    totalUsableHomeCapacity
    claimedHomeCount
    currentFreeHomeCapacity

population-support candidate (task-57 revalidates before commit):
    facts == FRESH + COMPLETE
    AND adultVillagerCount >= 2
    AND currentFreeHomeCapacity > 0
```

  Settlement-wide `currentFreeHomeCapacity > 0` is a **candidate** fact, not proof breeding succeeds.
  Vanilla birth still requires a vacant HOME within 48 blocks of the breeder plus path reachability —
  task-57 owns that revalidation. **Deleted authority:** `freePopulationCapacity =
  max(0, eligibleBedCount - villagerCount)` and the term **`eligibleBedCount`** (ambiguous).

  V3 still does **not** command breeding, claim beds, or mutate villager Brain state (D-VR-078).
- **Disposable surplus** gates (compost / population / gift) run **after** survival, progression
  protected inputs, committed replant seed reserve, and active mandatory owners — same permission-before-
  preference shape as `FuelExpendability` / `SellExpendabilityPolicy`.
- **Numeric tuning:** initial values live in `VillageWorkTuning` as **`PROVISIONAL`** until 1/10/50/100-mob
  profiling. "Matches another subsystem" is a starting guess, not lock evidence. **Exception:** values
  **derived** from an existing lifecycle dependency (e.g. `MandatoryHandoffPolicy.YIELD_WINDOW_TICKS` from
  `TradeCandidateRound.EXHAUSTED_ROUND_COOLDOWN_TICKS`) may cite that derivation as evidenced tuning.

**Rejected:** locking specific constants (e.g. crop cap 8, radius 24, backoff 40) as architecture;
population predicate `villagerCount - bedCount > 0` (reversed — rewards villages that already lack beds).

**Task-dependency correction (2026-08-19, `Agent_Claude` + User; decision text otherwise unchanged).**
The locked predicate currently has **no fact source**. `CODE_CONFIRMED`: `KnownVillage` exposes only an
aggregate `poiCount()` — no per-POI-type breakdown, no villager count, no bed count. `VillagePerception`
does query `PoiManager` for `#acquirable_job_site + home + meeting`, so the *scan* exists and no second
scanner is needed; the **retained facts** do not. Therefore:

- the sequence gains the edge **`V3-D → V3-E`** (it read `V3-A → V3-C/D/E` as siblings);
- V3-D widens the bounded village-work facts to carry **adult villager count** and the three HOME
  capacity layers (`totalUsableHomeCapacity`, `claimedHomeCount`, `currentFreeHomeCapacity`);
- **`currentFreeHomeCapacity`** uses `PoiManager.Occupancy.HAS_SPACE` on `PoiTypes.HOME` — vanilla's
  breeding ticket truth. Diagnostic layers do not authorize support; only vacancy on FRESH+COMPLETE facts.

No second scanner. **Superseded predicate (do not implement):** `freePopulationCapacity =
max(0, eligibleBedCount - villagerCount)`.

### D-VR-083-A1: Population-support vacancy authority (`User`, 2026-08-21)

**Status:** `LOCKED` — amends D-VR-083 population-support candidate after task-56 Gate 0.

**Accepted:** vanilla vacancy (`currentFreeHomeCapacity > 0`) is the population-support candidate
signal; subtraction headroom is **rejected** (R1/R3). Task-56 observes settlement-wide vacancy;
task-57 revalidates breeder-local 48-block reachability before food commit.

**Rejected:** preserving `eligibleBedCount` / `freePopulationCapacity` subtraction as authority;
conjunctive subtraction **and** vacancy (R3) for gen-1.

### D-VR-085: Composter target discovery — executor-local bounded block scan (`Agent_Cursor`, 2026-08-21)

**Status:** **`CONTESTED / LOCK NOT RECOMMENDED`**. The original proposal is preserved, but its
premise was falsified by pinned 1.21.1 source: `PoiTypes.java:110` registers every composter block
state as `PoiTypes.FARMER`; `VillagerProfession.FARMER` consumes that POI. “Not a job-site POI” was
an inference from task-56's HOME-only kernel, not negative evidence about Minecraft.

**Still valid from the proposal:** loaded-only scope, finite candidate/path budgets, deterministic
ordering only within the returned bounded sample, empty/blocked backoff, and live COMMIT
revalidation. Those constraints apply whichever discovery owner wins.

**Superseded portion:** the executor-local **cubic block scan** and categorical rejection of POI
discovery are rejected by `D-VR-085-R1` below. This records the correction without rewriting the
historical proposal.

### D-VR-085-R1: Composter fact owner versus executor discovery (`Agent_Codex`, 2026-08-21; **AMENDED** `User` + `Agent_Cursor`, 2026-08-22)

**Status:** **`LOCKED` — Option A amended** (shared `ComposterWorkFacts` on existing perception/work
cadence). Option C remains **REJECTED**. Option B remains **REJECTED**. Prior Option A wording
(broad V3-D2 `PoiTypes.FARMER` workstation evidence) is **SUPERSEDED** — see dependency drift
correction in canonical V3 contract.

| Option | Safety/fit | Cost/failure mode | Disposition |
| --- | --- | --- | --- |
| **A — shared `ComposterWorkFacts` on perception/work refresh** | Extends existing scheduler/cache pattern (task-56); composter-specific; no executor scanner | Cache age/completeness surface | **`LOCKED`** |
| **B — executor-local bounded POI query** | Smaller slice | Duplicates discovery; violates V3 no-scanner rule | **REJECTED** |
| **C — executor-local cubic block scan** | Direct block-state lookup | Volumetric cost | **REJECTED** |
| **D — broad V3-D2 prerequisite before V3-F** | Closes VR-T3f generically | Stale assumption; over-engineers gen-1 compost | **REJECTED for V3-F** |

**Lock evidence (`CODE_CONFIRMED` — shipped tasks 52–58):**

1. `VillageWorkFacts` contains population/HOME fields only — composter positions via `ComposterWorkFacts`
   (`VillageWorkFacts.java`; `ComposterWorkFacts.java`).
2. `VillageWorkObservationKernel` observes HOME POIs + adult villagers only (task-56); composter
   observation is a separate kernel (task-58).
3. `VillageWorkFactsCache` + scheduler + anchor invalidation are transient — reused by
   `ComposterWorkFactsCache` without mutating D1 record.
4. `PoiTypes.FARMER` registers composters — observation filters to composter block states at
   bounded FARMER POI positions without a generic workstation framework.

### D-VR-085-A1: `VillageWorkstationFacts` companion record (`Agent_Cursor`, 2026-08-22)

**Status:** **`SUPERSEDED`** by **`D-VR-085-A2`** — broad workstation record rejected for gen-1 V3-F.

### D-VR-085-A2: `ComposterWorkFacts` transient cache (`User` + `Agent_Cursor`, 2026-08-22)

**Status:** **`LOCKED` + IMPLEMENTED** (task-58; Gate 0 PASS).

**Accepted:** publish a **separate** transient record keyed by `SettlementIdentity`, refreshed on the
**same scheduler budget** as village work/perception refresh, **without** mutating `VillageWorkFacts`:

```text
ComposterWorkFacts
    identity: SettlementIdentity
    composterPositions: bounded loaded BlockPos list (composter block states only)
    observedAtTick: long
    completeness: WorkFactsCompleteness   // INCOMPLETE when budget exceeded
    freshness: WorkFactsFreshness
```

**Enumeration:** lazy iterator over bounded loaded `PoiTypes.FARMER` candidates filtered to
`ComposterBlock` at COMMIT-revalidation positions; `MAX_COMPOSTERS_PER_OBSERVATION` cap (task-56
CLOSE-56-2 pattern). **No `.toList()`**. **No** stuffing into `VillageWorkFacts`.

**Consumer rule:** `CompostGoal` reads composter facts for **candidate discovery only**; at COMMIT it
revalidates live `ComposterBlock` state (`LEVEL < 7`, still loaded, settlement bounds, `mobGriefing`).

**Rejected:** retrofitting task-56 `VillageWorkFacts`; broad `VillageWorkstationFacts`; executor-local
cubic scan; extending `KnownVillage` persistence.

### D-VR-086: Compost expendability reserve ladder (`Agent_Cursor`, 2026-08-21)

**Status:** **`SUPERSEDED IN PART`** by **`D-VR-086-A1`** (locked 2026-08-22). Priority order below
remains authoritative; quantified owner is now specified.

**Accepted — reserve order (compost runs last among disposable-surplus gates):**

```text
1. Player survival nutrition reserve (MIN_SURVIVAL_NUTRITION_RESERVE = 12 — task-57)
2. Progression / trade / fuel protection (FuelExpendability, SellExpendabilityPolicy craft reserves — compose, SPM-2)
3. Managed-crop replant material (HarvestCandidatePolicy / CropReplantSemantics per supported crop)
4. Population-food disposable pool (PopulationFoodExpendabilityPolicy)
5. Compostable surplus only if still spare AND ComposterBlock accepts the item
```

**Required before lock:** ~~define one conservative quantity authority~~ **SATISFIED** by
`D-VR-086-A1`.

| Option | Benefit | Failure mode | Disposition |
| --- | --- | --- | --- |
| **A — shared disposable-quantity view** | Composes canonical owners | — | **`LOCKED` as `D-VR-086-A1`** |
| **B — compost-local composition** | Smaller slice | Duplicate brain | **REJECTED** |

**Rejected:** tag-only `c:compostables` allowlist without reserve composition; independent seed
appetite for composting.

### D-VR-086-A1: `CompostExpendabilityPolicy` composes canonical reserve owners (`Agent_Cursor`, 2026-08-22)

**Status:** **`SUPERSEDED IN PART`** by **`D-VR-086-A2`** (2026-08-22). Reserve **order** below remains
authoritative; gen-1 **narrow domain** and `CompostReserveModel` ownership are now specified.

### D-VR-086-A2: `CompostReserveModel` + compostability/expendability split (`User` + `Agent_Cursor`, 2026-08-22)

**Status:** **`LOCKED` + IMPLEMENTED** (task-58; Gate 0 PASS — wheat/beetroot seeds;
`CompostingChanceRegistry` at runtime for mechanical check).

**Accepted — two conceptual authorities:**

```text
Compostability     → vanilla/Fabric mechanical truth (ComposterBlock.getValue > 0) — NOT spend authority
CompostExpendabilityPolicy → Scavenger resource ownership — authorizes spending
```

**`CompostReserveModel`** (new pure class) answers task-58's question:

```text
heldUnits - explicit replant reserve - explicit other reserve = compostable disposable units
unknown → 0 disposable (NOT unknown → zero reserve)
```

**Gen-1 domain is deliberately narrow:** explicitly reserve-modelled **crop seed surplus** is the
cleanest first candidate (task-55 replant safety is higher authority). Gate 0 **PASS** — wheat seeds
and beetroot seeds after replant reserve = 1. **Do not** start with “anything vanilla says is
compostable → disposable unless known otherwise.”

**`CompostExpendabilityPolicy` composes protection layers** (compost runs last):

```text
for held stack S:
  0. reject if not mechanically compostable (Gate 0)
  1. FuelExpendability / held / offhand / damageable / never_fuel veto
  2. PlayerNutritionReserve — MIN_SURVIVAL_NUTRITION_RESERVE (= 12, task-57)
  3. SellReserveModel — applies only when material is **modelled** for sell (logs/planks/sticks).
     empty() = unmodelled for **sell funding** — NOT a compost veto; fall through to CompostReserveModel
  4. CompostReserveModel — replant + explicit surplus (primary gen-1 authority for seeds)
  5. PopulationFoodExpendabilityPolicy — deny villager breeding food (recipient-specific; not primary)
  6. remainder = compost-disposable (cap 1 per episode at executor)
```

**UNKNOWN compost material → refuse** (fail-closed at `CompostReserveModel`). `SellReserveModel.empty()`
does **NOT** veto items explicitly authorized by `CompostReserveModel` (e.g. unmodelled-for-sell seeds).

**Rejected:** `PopulationFoodExpendabilityPolicy` as primary compost authority; tag-only
`c:compostables` allowlist; independent seed/bone-meal appetite; “compostable therefore disposable.”

### D-VR-087: Compost episode shape and bone-meal disposition (`Agent_Cursor`, 2026-08-21)

**Status:** **`SUPERSEDED IN PART`** by pinned `ComposterBlock` semantics and **`D58-7…9`**. The
**P5 (provisional)** episode, one-unit cap, `mobGriefing` gates, SELECT→INTERACT_PREPARE→COMMIT
shape, interruption exits, and no bone-meal appetite survive.
The claim that COMMIT produces/pops bone meal does not: insertion only attempts a probabilistic level
increase and consumes the supplied unit; level 7 later ticks to 8; separate extraction creates bone
meal.

### D-VR-087-A1: Vanilla-owned one-attempt compost transaction (`Agent_Codex`, 2026-08-21; **LOCKED** `Agent_Cursor`, 2026-08-22)

**Status:** **`LOCKED`**. Transaction debit owner pinned by **`D-VR-087-TX1`**.

**Proposed:** `IDLE → SELECT → PATHING → INTERACT_PREPARE → COMMIT → DONE`; one target and one
insertion attempt. At INTERACT_PREPARE/COMMIT, revalidate all authority and target facts against
**current truth** (task-57 lesson), then let exactly one layer own inventory debit.
Because vanilla `insertItem(...)` shrinks its supplied stack after every eligible attempt, an
unchanged level is a completed vanilla attempt—not failure—and must not trigger immediate retry.
Gen-1 leaves level-8 extraction to vanilla farmers/players and creates no bone-meal demand.

**Rejected:** pre-debit plus vanilla shrink; “level did not rise” rollback/retry; crediting insertion
with bone-meal production; multi-stack drain; bone-meal extraction/application in the first slice.

### D-VR-087-TX1: Single debit owner — vanilla `insertItem` shrink (`Agent_Cursor`, 2026-08-22)

**Status:** **`LOCKED` (architecture)** — task-58 Gate 0 confirms slot wiring.

**Evidence (`CONFIRMED` — `ComposterBlock.java:273–278`):**

```text
insertItem(...):
  if level < 7 && getValue(stack) > 0:
      addItem(...)          // probabilistic level advance
      stack.shrink(1)       // ALWAYS when eligible — even if level unchanged
```

**Accepted commit pattern (mirrors task-57 handoff discipline):**

```text
1. PREPARE: CompostExpendabilityPolicy authorizes exactly 1 unit from slot S
2. COMMIT: copy stack from backpack slot S (or single-item view)
3. ComposterBlock.insertItem(mob, state, level, copy, pos)
4. If copy.count decreased: mirror removal into backpack slot S (same count delta)
5. NEVER call shrink/removeItem on backpack before insertItem succeeds eligibility
```

**Must not happen:** independent `backpack.removeItem` before insert; second shrink; retry on
unchanged level.

**Episode outcome:** `COMMIT_DONE` (level may or may not advance) → cooldown — no ACK phase
(compost success is immediate world truth unlike villager pickup). No RNG during SELECT/PATH/PREPARE.

### D58-1 … D58-12: Task-58 brief locks (`User` + `Agent_Cursor`, 2026-08-22)

**Status:** **`LOCKED`** — Gate 0 **PASS**; task-58 **CLOSED**. Brief numbers may not reopen philosophy.

| ID | Lock |
| --- | --- |
| **D58-1** | V3-F is opportunistic `VILLAGE_WORK`. It does not manufacture demand for compostables or bone meal. |
| **D58-2** | Composter position is perception evidence, not executor discovery. No every-tick/world block scanner. |
| **D58-3** | Composter evidence is transient, bounded, loaded-only, settlement-identity-bound, freshness-aware. |
| **D58-4** | Compostability does not imply expendability. |
| **D58-5** | Unknown reserve state fails closed. Only explicitly modelled disposable surplus may be composted. |
| **D58-6** | Replant, population-food, survival and progression ownership outrank composting. |
| **D58-7** | One activation performs at most ONE real compost attempt. |
| **D58-8** | No RNG during target selection/preflight. COMMIT is the single vanilla compost RNG/mutation attempt. |
| **D58-9** | COMMIT revalidates current settlement, target block, loaded state, distance, admission and disposable quantity. |
| **D58-10** | No `MandatoryOwnership` publisher. No `VillageWorkSelector`. No independent seed/bone-meal acquisition route. |
| **D58-11** | READY-output extraction is **NOT authorized** in gen-1 — **input-only** (Gate 0 PASS; no extraction code shipped). |
| **D58-12** | VR-T3d remains runtime-deferred to the batched V3 campaign. |

### D-VR-084: `MandatoryOwnership` — one claim-based discretionary-permission authority (`Agent_Claude` + `User`, 2026-08-19)

**Status:** `LOCKED` (architecture) + **`IMPLEMENTED / STATIC-BEHAVIORAL ACCEPT`** (task-52, 2026-08-20; R1 2026-08-20). Supersedes the admission-source list in `D-VR-082`.
**Consumed by:** `DiscretionaryActivityDirector` (implemented) and V3-A / task-53 `VillageWorkAdmission`
(a second **consumer**, not a second publisher). **Own slice:** **task-52** — see
`.superpowers/sdd/task-52-brief.md`. **Subsumes:** `V2-DEF-002` (promoted from deferred debt).

**Implementation status (task-52, 2026-08-20; R1 2026-08-20):** `IMPLEMENTED / STATIC-BEHAVIORAL ACCEPT`.
`MandatoryOwnershipClaim` + `MandatoryOwnershipRegistry` + `MandatoryOwnership` shipped in
`activity/`; `DiscretionaryActivityDirector` consumes `MandatoryOwnership.evaluate`;
`VILLAGE_TRADE` joins `blocksDiscretionaryChoice`; `InvalidationCause.MANDATORY_PENDING_CLAIM`
added; `GatherResourcesGoal` is the one wired publisher (`ownedMandatoryRoute` owns the full
`select → scanCovers → of` predicate, shared with `publishRouteExhaustion` — R1 removed the
duplicate coverage check; pending claim published before `scanClock.claim(now)`, generation minted
only at `EXECUTOR_STARTED` release with a live claim). Twelve scenarios + two temporal simulations +
producer-side controls: 53 new tests; full clean build **1357 tests, 0 failures**.
`V2-DEF-002` status is the four-part record (Gather path `REPAIRED / STATIC-BEHAVIORAL ACCEPT`;
shared seam `IMPLEMENTED`; unwired publishers `DEFERRED` fail-open; runtime witness deferred to the
batched V3 campaign) — not a blanket `REPAIRED`. The runtime witness is folded into the later V3
campaign, so no dedicated session is scheduled. Identity-bound release/start authorization is a
prerequisite of whichever future task first adds a SECOND `MandatoryOwnershipClaim` publisher
(concern 7 in `task-52-report.md`) — task-53 is not such a task (it adds a second consumer), and
this is not solved in task-52.

#### The evidence this comes from

`CODE_CONFIRMED`: `ActivityObservationService.observe` iterates `selector.getAvailableGoals()` and
records a class **only when `wrapped.isRunning()`**. `DiscretionaryEligibility.isDiscretionaryEligible`
is therefore an *occupancy* answer, and it already has exactly one production consumer
(`DiscretionaryActivityDirector`). Its `blocksDiscretionaryChoice` set already covers
`PROJECT_EXECUTION`, `SCAVENGE_WORK`, `SCAVENGE_LOOT`, `MANDATORY_*`, `SHELTER_HOLD` and `FARMING`,
and fails closed on `UNKNOWN_ACTIVE`.

`CODE_CONFIRMED`: `KNOWN_DEFECTS.md` `V2-DEF-002` is `OPEN` and documents the *other* half — a mob
with an unresolved iron-pickaxe demand walked out of its own village because no deliberate executor
was **running** yet. The same document rejects the naive repair: a blocker keyed on demand existence
converts a wandering mob into a frozen one when the demand is genuinely unservable.

#### Accepted

**One authority, two inputs, two consumers.**

```text
ActivityObservationService ──── running activity truth ────┐
                                                           ▼
                                              MandatoryOwnership
                                          (shared permission truth)
                                                           ▲
published pending claims ──────────────────────────────────┘

DiscretionaryActivityDirector ─┐
                               ├── consume the SAME permission
VillageWorkAdmission ──────────┘
```

**Rejected outright:** Opinion holding mandatory model A, Village Work model B, Explore special case
C and Trade workaround D. That is the duplicated-authority shape that produced `V2-DEF-003c-R1`
(`MandatoryHandoffPolicy` reconstructing a publication instead of consuming it), `V2-DEF-003`
(`GatherIntentPolicy` and `ScavengerCrafting` reading one recipe two ways) and the Step-2
`rankOrdinal` regression. It is also `SPM-0` level 7 — an enumeration where a derivation is available.

#### The four states

```text
mandatory executor RUNNING                    -> block discretionary
route PENDING under a live published claim    -> block discretionary
demand exists, no live claim (or it expired)  -> DO NOT block
nothing mandatory                             -> discretionary allowed
```

**Demand does not create authority. An owner accepting bounded responsibility does.** The third
state therefore needs no viability judgement by anybody: an unservable demand either produces no
claim or produces one that expires, and discretionary work resumes structurally rather than by a
heuristic that would itself become a second mandatory model.

#### The anti-self-renewal invariant (`User`, and the reason a TTL alone is insufficient)

A claim that may be reissued because the demand still exists is a timer wrapped around
`demand exists -> block`, and reproduces the freeze exactly:

```text
unservable iron demand exists
  -> owner publishes a 200-tick claim
  -> nothing becomes actionable
  -> tick 199: "demand still exists" -> republish
  -> forever
```

**Binding rule.** A *new* claim requires a *new* justification — meaningful progress, fresh
actionable evidence, or an actual ownership transition. **The continued existence of the same demand
must never refresh its claim.** `generation` exists to make that auditable: a republish carrying the
previous generation with no intervening justification is the defect, and it is detectable in a
log-frequency sample (Gate RET-1d).

**Producer-side sharpening (User, 2026-08-20).** `generation` is **producer-side authority, not a
retry counter**, and every publisher inherits this — not just the task-52 Gather one. It may **not**
advance for another `canUse()`, another tick, another scan-clock opportunity, TTL expiry, the
continued existence of the same demand, or an unchanged repeated empty scan. It advances only on a
semantic episode transition: the owned consumer/material identity changes, ownership leaves the owner
and later returns through an authoritative transition, or materially fresh route evidence changes the
context after the previous claim was abandoned.

Mechanization: **mint the generation at *release*, never at *publish*, and only ONE release reason
mints it — the executor actually starting.** `ROUTE_HANDED_OFF`, `ABANDONED`, ordinary release, TTL
expiry and continued demand existence all delete or leave the claim **without advancing it**. Letting
a *termination* mint the next generation would make `ABANDONED -> republish -> ABANDONED` a
self-renewal loop with extra steps: a claim that ends does not thereby earn its successor.
**Reacquiring the same identity after handoff or abandonment requires an explicit authoritative
transition or materially fresh actionable evidence — the previous claim having ended is not one.**
The producer therefore has no way to obtain a higher generation from an unchanged demand, and
requirement 3 becomes structural instead of a rule someone must remember. A `generation` field any caller may increment fails this decision regardless of green
tests. Every publisher must additionally derive its demand from the **canonical** mandatory
`MaterialDemand` its domain already uses, and must publish **early enough that its own scan/retry
cadence cannot open a discretionary gap** between accepting responsibility and becoming visible —
for Gather that means before `PhasedScanClock` grants the next sweep (`GatherResourcesGoal.java:217`,
`SCAN_INTERVAL = 60`). Full vectors: `.superpowers/sdd/task-52-brief.md`.

#### Shape

```text
MandatoryOwnershipClaim
  |- mobId
  |- consumerKey                one canonical pending owner per selected work episode
  |- owner / route identity
  |- generation, openedAt
  '- expiresAt
```

`consumerKey` is deliberately singular: Gather, Trade, Mining and future V3 cleanup do **not** each
pile an independent claim into another arbitration system. One canonical pending owner per episode,
or the claim layer becomes the very thing it replaced.

Precedent, and why this is a generalization rather than an invention:
`MandatoryHandoffPolicy.HandoffPublication` + `YieldWindow` is already exactly this pattern — a
publication that is identity-bound, expires instead of inferring authority forever, and is
*consumed* rather than reconstructed. It is runtime-confirmed by **VR-T2k**. D-VR-084 lifts it from
one boundary to all of them.

#### Lifetime (Gate RET-1 / RET-1e)

**Runtime-only. Never persisted.**

```text
ordinary entity unload / dimension transfer -> release
death / discard                             -> release
server stop                                 -> clear
restart                                     -> no resurrection; world truth reacquires ownership
```

A claim surviving a restart would resurrect a frozen mob with no live owner able to clear it. Being
runtime-only, the store is exempt from the `PerMobSavedData.forgetAll()` contract and **must not** be
registered there — RET-1e exempts runtime-only state, and registering it would imply a persistence
this decision forbids.

#### The tradeoff, stated rather than discovered later

The two halves fail in **opposite** directions, deliberately:

| Half | Failure mode | Direction |
| --- | --- | --- |
| running (`ActivityObservationService`) | a goal nobody classified reads `UNKNOWN_ACTIVE` | **closed** — blocks |
| pending (claims) | an owner that forgets to publish does not block during its pending window | **open** — allows |

This is accepted knowingly. The open direction lands on the safe side of the `V2-DEF-002` dilemma —
a mob that wanders when it should have waited, rather than a mob frozen forever guarding work nobody
can serve. Anyone tempted to "fix" the asymmetry must first answer the second row of the
`V2-DEF-002` repair gate.

**Must happen:** an unservable mandatory demand eventually returns discretionary permission without
any component judging viability.
**Must not happen:** a second mandatory-work model; a claim refreshed by demand alone; a persisted
claim outliving the session that created it.

#### Acceptance model — automated now, runtime batched later (`User`, 2026-08-19)

D-VR-084 is accepted on **automated behavioural acceptance**, and is explicitly *not* granted its own
Minecraft session. Twelve scenarios plus two temporal simulations are required; the full vectors live
in `.superpowers/sdd/task-52-brief.md`.

```text
 1 RUNNING mandatory work           -> denied        7 owner abandons/satisfies -> released now
 2 LIVE pending claim               -> denied        8 VILLAGE_TRADE running    -> denied
 3 demand exists, nobody claims     -> allowed       9 unknown running goal     -> fail closed
 4 claim expires without progress   -> allowed      10 owner forgets to publish -> fails open
 5 same demand after expiry         -> NO RENEW     11 unload/dim/stop          -> claim gone
 6 progress / fresh evidence        -> MAY publish  12 restart                  -> no resurrection
```

**Scenario 5 is the load-bearing negative control.** If it fails, the design has degenerated into
`demand exists -> block` with a timer wrapped around it, which is exactly the frozen-demand problem
`V2-DEF-002` rejects.

Two temporal simulations are required rather than optional — a servable demand
(`claim -> progress -> impossible -> abandoned -> EXPLORE legal at T121`) and an unservable one
(`no owner accepts -> no claim -> EXPLORE remains legal, and still legal at T400`). The second is
`V2-DEF-002`'s second repair-gate row.

**Resulting status wording.** `V2-DEF-002` is **not** marked fully `REPAIRED`. Four lines, recorded
separately (Gate AV-1):

```text
Gather-owned observed path      REPAIRED / STATIC-BEHAVIORAL ACCEPT
shared MandatoryOwnership seam  IMPLEMENTED
unwired mandatory publishers    DEFERRED - fail-open coverage, by design
runtime witness                 DEFERRED - batched V3 campaign
```

The third line must survive a green suite: the first slice wires **one** publisher, so the defect
general form outlives it. The deferred witness is one observation folded into the later
batched V3 runtime campaign:

```text
mandatory pending claim active -> no expedition
claim abandoned / expires      -> discretionary movement eventually resumes
```

That single witness can close D-VR-084 / `V2-DEF-002` alongside several VR-T3 rows instead of
creating another standalone session.

### D-VR-082-A1: amendment — consume the shared seam; taxonomy; P4 co-tenancy (`Agent_Claude` + `User`, 2026-08-19)

**Status:** `LOCKED`. Amends `D-VR-082`; everything not restated below stands.

**(1) Admission consumes, it does not reconstruct.** The D-VR-082 instruction that
`VillageWorkAdmission` "must consult at least" five named sources is **superseded**.
`VillageWorkAdmission` consumes `MandatoryOwnership` (D-VR-084). Anything the shared authority is
missing is added **there, once**, for both consumers. Priority **4** and the
`NO LIVE MANDATORY OWNER` invariant are unchanged.

**(2) `VILLAGE_TRADE` joins `blocksDiscretionaryChoice`.** `CODE_CONFIRMED`: `TradeWithVillagerGoal`
is deliberate-band mandatory work at priority 3 and classifies to `ActivityClass.VILLAGE_TRADE`,
which is **absent** from the blocking set. Physically this is not currently catastrophic — a running
P3 trade already holds MOVE/LOOK, so a P4 V3 goal cannot take those flags — but the shared authority
is *semantically lying*: `TradeWithVillagerGoal RUNNING` currently reads "discretionary eligible".
Since D-VR-084 makes that authority load-bearing for a second consumer, the lie must be fixed.
V2 behaviour is re-checked as part of the change.

**(3) New `ActivityClass.VILLAGE_WORK`; every V3 executor is taxonomy-pinned.**
`CODE_CONFIRMED`: `MoveHolderClassifier.activityClass` falls through to `UNKNOWN_ACTIVE`, which sets
`Observation.unknownActive()` and fails discretionary eligibility closed. An unclassified V3 goal
therefore compiles, runs correctly, and **silently suppresses all Opinion discretionary work**
whenever it holds MOVE. V3-C/E/F executors classify as `VILLAGE_WORK`, which **blocks a fresh
discretionary selection while running**:

```text
no work running        -> eligible -> choose Farm
Farm running           -> VILLAGE_WORK observed -> no second discretionary choice
Farm ends              -> eligibility returns
```

A discretionary action blocking the *start of another* discretionary action is the correct and
intended shape; it is not a contradiction with V3 work being discretionary by admission. (An earlier
review framing to that effect was withdrawn.) Precedent for the pin itself: `D-VR-054` / `D-VR-073`.

**(4) Priority 4 is shared, and the `MAINTENANCE` asymmetry is deliberate.**
`CODE_CONFIRMED`: `SpmScavenger.java` registers `PlaceTorchGoal` at **4** with `MOVE|LOOK`; it
classifies as `ActivityClass.MAINTENANCE`, and `MAINTENANCE` is **not** in
`blocksDiscretionaryChoice`. The RFC must stop describing 4 as a free band. The band reads:

```text
P3  mandatory deliberate work      Gather / Craft / Smelt / Trade
P4  bounded non-mandatory work     PlaceTorch (MAINTENANCE) + Village Work (VILLAGE_WORK)
P8  exploration
```

Equal-priority MOVE/LOOK jobs cannot steal each other's active interaction, which *supplies*
D-VR-082's "peer discretionary work cannot steal a committed interaction" for free. The interference
is intentional and reciprocal: **a running bounded V3 job may defer torch placement, and running
torch maintenance may defer V3 work; neither preempts the other.**

The two co-tenants keep **different** observation semantics, on purpose:

| Running | Blocks new discretionary selection? | Why |
| --- | --- | --- |
| `PlaceTorchGoal` -> `MAINTENANCE` | **no** | a short flagged action; physical P4 flag ownership already delays the selected work, and the torch finishes quickly |
| V3 executor -> `VILLAGE_WORK` | **yes** | a bounded work *commitment* whose whole point is to finish before another intent is chosen |

**Do not** later "fix" `MAINTENANCE` to match `VILLAGE_WORK` for symmetry's sake. The asymmetry is
the decision.

**Rejected:** moving V3 off priority 4 to obtain an empty band; a village-local mandatory-work
oracle; leaving `VILLAGE_TRADE` out of the blocking set because the flags happen to save us today.

### D-VR-079-A1: amendment — managed crop domain, host veto, and the replant loop (`Agent_Claude` + `User`, 2026-08-19)

**Status:** `LOCKED` + **`IMPLEMENTED / STATIC-BEHAVIORAL ACCEPT`** in task-55. Amends
`D-VR-079`; the committed-episode architecture itself is unchanged. Runtime remains `UNVERIFIED`.

#### (1) The mixin-scope table was wrong for crops

`CODE_CONFIRMED` against pinned SPM v0.86.0: `PlayerMobEntity` registers `HarvestCropsGoal` at
priority **6** with `MOVE|LOOK`; `canUse` is gated on `mob.wantsFood()`; the harvest banks edible
drops, calls `mob.dropAtLocation(drop)` for everything else, and then
`serverLevel.destroyBlock(targetPos, /* dropBlock */ false, mob)`. **It never replants.**

A V3-C goal at priority 4 preempts it *only while V3 admission passes*. Admission refuses whenever a
mandatory owner is live, and the host goal's own gate is hunger:

```text
PlayerMob becomes hungry -> MANDATORY_SURVIVAL active -> V3 farming refused
                         -> host HarvestCropsGoal runs -> crop broken -> no replant
```

The RFC's `Mixin scope (minimal)` row **"Crop harvest/replant | does not need mixin"** is therefore
`SUPERSEDED`. It is true for *performing* harvest+replant and false for *preventing* the destructive
host path, which is what VR-T3a's must-not-happen row actually requires. The implementation shape is
the shipped `*ShelterHoldMixin` family (`DoorOperationShelterHoldMixin`,
`FriendlyGreetShelterHoldMixin`, `WeaponAttackShelterHoldMixin`) — a continuous veto on a host goal's
admission/continuation, not goal removal, and not an SPM fork. **SPM stays stock.**

#### (2) The managed crop domain — derived, and free of `SettlementRelationship`

A first draft defined *managed* as "positions the episode has already claimed". **Rejected:** it
creates a loophole in which an unclaimed crop is grabbed by the host first, leaves bare farmland, and
is "technically unmanaged" — an acceptance test that passes by definition while the village is
damaged.

```text
managedCrop(mob, pos) ==
      profile(mob) == VILLAGE_ALLY               (D-VR-080; explicit assignment only)
  AND a positive factual village resolution exists
  AND pos is within that resolved settlement's bounds
  AND block truth says crop-on-farmland
```

The domain exists **before** any episode selects anything, so a host grab cannot beat us to it.

**No HOME / HIGH / familiarity / `SettlementRelationship` term.** An earlier draft carried "this mob
has an ally relationship with that village"; that would reintroduce through a side door precisely
what `D-VR-017`, `D-VR-052` and `D-VR-080` spent several reviews removing — attachment manufacturing
permission. `VILLAGE_ALLY` is the locked policy authority; village resolution supplies only
*geography*.

#### (3) Fail direction: toward stock — and why it is opposite to storage

```text
cannot positively establish the managed domain -> DO NOT veto host HarvestCropsGoal
```

This veto **removes existing SPM functionality**, so uncertainty must leave the host alone.
A perception gap must never silently disable stock food behaviour mod-wide. That is the **opposite**
direction from `D-VR-081`, where `UNKNOWN` storage **denies** access — and the asymmetry is
deliberate, because the two policies protect different parties:

| Policy | On uncertainty | Protects |
| --- | --- | --- |
| `D-VR-081` storage | **deny** | somebody else's container |
| `D-VR-079-A1` crop veto | **allow (stock)** | the host mod's own shipped behaviour |

Gen-1 keeps the veto narrow. If hungry allies should later eat from managed fields, that harvest goes
through the same committed harvest->replant executor — **not** by restoring the destructive host path.

*Behavioural note (`MAIBS`):* the veto does not starve an ally. `ForagePolicy.wantsFood` returns true
whenever the mob carries no food, and `HuntForFoodGoal` shares that gate and is untouched by a crop
veto. The ally forages animals instead of stripping the village's fields, which is better ally
behaviour, not a regression.

#### (4) The replant loop must close inside the episode (**F8**)

`CODE_CONFIRMED`: the host harvest spills seeds with `mob.dropAtLocation(drop)`. If V3-C mirrors that,
every episode consumes one held seed and throws its replacement on the floor; the reserve drains
monotonically and the phase stops admitting — a demand with no reachable supply, the first north-star
invariant. The only existing recovery path is host `CollectFloorItemsGoal` (priority **3**, radius 8,
classifies `SCAVENGE_LOOT`), i.e. a *higher-priority* goal that must interrupt the episode to restock
it. That is an assumption about another goal's scheduling dressed as a supply.

```text
HARVEST COMMITMENT
      -> mutate mature crop
      -> capture resulting drops DIRECTLY
      -> separate: food/output  |  replant material
      -> replant committed farmland
      -> return excess to inventory
```

**Binding invariant.** *V3-C directly banks every replant-capable drop produced by its own committed
harvest. It must never depend on floor-item pickup to recover its own planting supply. Replant
reserve accounting is crop-specific; no crop may be assumed to reproduce a planting item unless its
pinned drop semantics guarantee that.*

The crop-specific clause matters, and **"this makes the reserve self-sustaining" is deliberately not
locked for every crop**: mature wheat yields 1-4 seeds and carrots/potatoes 1-4 plantable items, but
mature beetroot can yield **0-3** seeds. So for beetroot:

```text
spare beetroot seed held -> may harvest -> replant one immediately -> bank whatever dropped
zero spare seeds after the episode -> stop further managed beetroot harvest; field stays planted
```

**Do not manufacture a replacement seed to make the loop mathematically infinite.** The safety
invariant is not "farming never pauses"; it is:

> Farming may pause from lack of planting stock. It may not leave a successfully managed field barren.

New closure rows **VR-T3l** (hungry mob, admission refused, veto holds) and **VR-T3m** (multi-cycle
reserve sustainability) carry this.

### Contribution — User + Agent_Codex (minimum RFC synchronization pass, 2026-08-19)

Agent: `Agent_Codex` acting on explicit User scope/disposition requirements
Contribution type: `DESIGN / DOCUMENTATION SYNCHRONIZATION / MAIBS PRE-IMPLEMENTATION`

**Frontier before:** contradictory V3 identities; V2 simultaneously closed/partial; V2 residuals
presented as frontier; D-VR-012 depended on proposed D-VR-017 while VR-T3 substituted HOME/HIGH;
UNKNOWN ownership undefined; V3 had five-plus ambiguous capabilities, one partial acceptance row,
no task decomposition, and no MAIBS prediction.

**Action:** established canonical Village Work V3 (`D-VR-078`); made the old graph conceptual L0…L5;
synchronized V2 closure and deferred work; scoped D-VR-076 locally; amended D-VR-012/017 and
explicitly superseded HOME/HIGH permission; failed UNKNOWN closed; deferred curing to V6; selected
committed crop ownership (`D-VR-079`); added V3-A…G, VR-T3a…k, and the V3 MAIBS prediction.

**Frontier after:** four pre-lock decisions remain: (1) production profile source/default/migration;
(2) positive storage-classification evidence for mob-owned/shared access; (3) numeric placement in
the existing activity authority; and (4) target-evidenced scan/backoff/deficit/surplus budgets.
V3 is **not LOCKED and not implementation-authorized**. No Java, tests, mixins, Gradle, config,
datapack, runtime, commit, or push action belongs to this contribution.

### Contribution — User peer review: D-VR-080…083 lock (`User` + `Agent_Cursor`, 2026-08-19)

**Frontier before:** Cursor-proposed D-VR-080…083 draft; V3-A/B **BLOCKED** on four pre-lock items.

**Action:** User challenged and corrected four proposals; **D-VR-080…083 `LOCKED`** with amendments:
(1) profile is cross-dimension per-mob policy — **not** `MobVillageMemory`; must join
`PerMobSavedData.forgetAll()`; (2) storage permission uses `GlobalPos`, survives unload/restart, deletes
only on revoke/destruction/permanent mob removal; double-chest canonicalization required; (3)
`VillageWorkAdmission` uses **NO LIVE MANDATORY OWNER** (broader than `MaterialDemand`); priority **4**;
`VillageWorkSelector` subordinate to village orchestration — no `VillageWorkDirector`; (4) budget
**contract** locked, numbers **PROVISIONAL**; population uses `max(0, beds − villagers)`.

**Frontier after:** V3 architecture **LOCKED** through D-VR-078/079/080…083. **Authorize task-52 / V3-A**
is the next step; implementation not authorized by this contribution.

*(Superseded the same day — see the amendment pass below. Task-52 was **not** authorized: a
code-evidenced review found V3-A's admission seam depends on a shared authority that does not exist
yet.)*

### Contribution — Agent_Claude + User (V3 amendment pass, 2026-08-19)

Agent: `Agent_Claude`, invoked as `Work the RFC V3 Village` (`RFC_DESIGN_WORK_READ_ONLY`), then
authorized by the User for an explicit amendment pass.
Contribution type: `DESIGN REVIEW / ARCHITECTURE AMENDMENT` — RFC + `KNOWN_DEFECTS.md` only.

**Frontier before:** `D-VR-080…083` `LOCKED`; recorded next step *"authorize task-52 / V3-A"*.

**Action:** pressure-tested V3-A/B/C against production and pinned-host source rather than the RFC's
description of them. Eight findings, seven confirmed against code; three were blockers:

| # | Finding | Outcome |
| --- | --- | --- |
| F1 | priority 4 already holds `PlaceTorchGoal` (`MAINTENANCE`) | co-tenancy **locked as intentional**, asymmetry recorded (`D-VR-082-A1`) |
| F2 | `VillageWorkAdmission`'s five-source list reconstructs truth `DiscretionaryEligibility` already owns | admission now **consumes** (`D-VR-082-A1`) |
| F3 | `VILLAGE_TRADE` absent from `blocksDiscretionaryChoice` — the shared authority lies | joins the blocking set |
| F4 | pending-vs-running gap **is** `V2-DEF-002`, still `OPEN`; V3-A would fork it | `D-VR-084`; defect **promoted to V3-A prerequisite** |
| F5 | unclassified V3 goal → `UNKNOWN_ACTIVE` → suppresses all Opinion discretionary work | taxonomy pin + new `ActivityClass.VILLAGE_WORK`. *Reviewer's "discretionary cannot block discretionary" framing **withdrawn** — a running discretionary action blocking a fresh selection is correct* |
| F6 | *"Crop harvest/replant does not need mixin"* falsified by host P6 `wantsFood()` harvest | row `SUPERSEDED`; managed-domain veto (`D-VR-079-A1`) |
| F7 | `KnownVillage` exposes only `poiCount()`; `D-VR-083`'s population predicate has no fact source | edge **V3-D → V3-E** added |
| F8 | replant loop does not close — host spills seeds; only recovery is a P3 pickup goal | episode banks its own drops; crop-specific reserve accounting |

**User corrections folded in (both changed locked invariants):** a claim TTL alone reproduces the
freeze, so **demand may never refresh a claim** — a new claim needs new justification
(`generation`); and the managed-crop predicate **drops the ally-relationship term**, which would have
smuggled `SettlementRelationship` back into permission through a side door.

**Reviewer position corrected on evidence:** a queued MAIBS objection (a foodless ally forbidden to
eat from a vetoed field) was **withdrawn** — `ForagePolicy.wantsFood` gates `HuntForFoodGoal`
identically and is untouched by a crop veto, so the ally forages animals instead of stripping the
village's fields.

**Rejected:** restarting V3 design; a village-local mandatory-work oracle; *managed* = "positions the
episode already claimed" (passes VR-T3a by definition while the village is damaged); manufacturing a
replacement seed so the replant loop is mathematically infinite; "fixing" `MAINTENANCE` to match
`VILLAGE_WORK`.

**Frontier after:** `V3-A` is **not implementation-ready**. The nearest frontier is the shared
`MandatoryOwnership` authority (`D-VR-084`) — also `V2-DEF-002`'s repair, and not itself
dependency-blocked. Renumbered to its own slice **task-52** (V3-A becomes task-53) and given an
automated-acceptance model with the runtime witness batched into the later V3 campaign; the
implementation brief is `.superpowers/sdd/task-52-brief.md`.

*(Superseded by task-52 implementation, 2026-08-20 — see the RFC identity header and the V3 task
table: `MandatoryOwnership` is `IMPLEMENTED / STATIC-BEHAVIORAL ACCEPT`, V3-A / task-53 is
`DEPENDENCY-READY / NOT YET IMPLEMENTATION-AUTHORIZED`, and the runtime witness remains deferred to
the batched V3 campaign. This dated contribution record is preserved as history.)*

**Reviewer position corrected by the User:** an earlier recommendation that D-VR-084 warrants its own
runtime session was **rejected** — a real shipped-behaviour change calls for a stronger automated
acceptance model, not an immediate launch. No Java, test, mixin, Gradle, config, datapack, runtime,
commit, or push action belongs to this contribution.

### Contribution — Agent_Cursor (V3-F design frontier, 2026-08-21)

**Agent:** `Agent_Cursor` · **Trigger:** `Work the RFC` (`RFC_DESIGN_WORK_ARTIFACT_ONLY`)
**Contribution type:** design contract + proposed decisions — **RFC artifact only**

**Frontier before:** V3-E **CLOSED / STATIC-BEHAVIORAL ACCEPT** (task-57; 1543 tests). RFC identity
header and V3 task table still described V3-B as the authorization frontier and V3-A…E as
`PROPOSED` / not implementation-authorized.

**Action:** code-evidenced V3-F (task-58) design pass against shipped tasks 52–57:

| # | Finding | Outcome |
| --- | --- | --- |
| F1 | No compost executor or `ComposterBlock` usage in production | V3-F is greenfield; depends on closed siblings |
| F2 | `VillageWorkFacts` carries population/HOME only — not composter positions | **HISTORICAL PROPOSAL, SUPERSEDED BELOW:** reject extending V3-D; select local scan |
| F3 | Composters are not villager job-site POIs in task-56 kernel | **FALSIFIED BELOW:** the kernel omitted FARMER POIs, but Minecraft registers composters as them |
| F4 | Reserve ladder must compose replant (V3-C) + population food (V3-E) before compost | Ordering survives; “lock-ready” status **SUPERSEDED** because no shared quantified reserve exists |
| F5 | Bone-meal pop must not invent progression appetite | No-appetite boundary survives; pop-at-COMMIT claim **FALSIFIED** |
| F6 | P4 registration order: harvest → population before compost | Historical task-58 proposal; numbering is now unassigned pending D2/F split |
| F7 | Bounded-sample terminology from task-57 CLOSE-57-1 applies to composter candidate scan | recorded in `D-VR-085` |

**Proposals added:** `D-VR-085` (target discovery), `D-VR-086` (expendability ladder), `D-VR-087`
(episode + bone meal); canonical V3-F implementation contract; MAIBS V3-F table; V3 task table
synced to tasks 52–57 **IMPLEMENTED / STATIC-BEHAVIORAL ACCEPT**.

**Frontier after (superseded by Task-58 closure 2026-08-22):** ~~BEGIN task-58 / V3-F — BRIEF DESIGN ONLY~~
→ **task-58 CLOSED**; **task-59 / V3-G — NEXT but HOLD**.

**Correction:** this contribution's F2/F3 conclusions and frontier are **SUPERSEDED** by the
code-evidenced peer challenge below. They are retained to preserve proposal history, not as current
authority.

### Contribution — Agent_Codex (V3-F peer challenge / Work the RFC, 2026-08-21)

**Agent:** `Agent_Codex` · **Mode:** `RFC_DESIGN_WORK_ARTIFACT_ONLY`
**Contribution type:** research + challenge + architecture correction + MAIBS — RFC artifact only

**Frontier before:** task-58 compost brief was presented as ready around an executor-local cubic
block scan, with shared workstation facts rejected and COMMIT described as potentially popping bone
meal.

**Evidence (`CODE_CONFIRMED` unless labelled):** pinned Mojmap source artifact
`minecraft-merged-1425f5a1b7-1.21.1-loom.mappings.1_21_1.layered+hash.2198-v2-sources.jar` shows:

1. `PoiTypes.java:110` registers `Blocks.COMPOSTER` as `PoiTypes.FARMER`, and
   `VillagerProfession.java:35–40` binds Farmer to that POI.
2. `ComposterBlock.insertItem:262–268` attempts `addItem(...)` and shrinks the supplied stack by one
   whenever the item is eligible and level is below 7. `addItem:293–306` may leave the level
   unchanged because advancement is probabilistic.
3. Level 7 schedules a 20-tick transition to level 8; `extractProduce:273–282` is the separate
   operation that creates bone meal. Insertion does not.
4. Task-56 delivered population/HOME facts, not the RFC's workstation-awareness capability. Three
   scoped negative probes found no workstation/job-site/restock/FARMER-POI symbol in (a)
   `village/work` production, (b) `village/work` tests, or (c) production goals.
5. Existing survival/population policies quantify only their own reserves; sell protection is
   narrow and crop candidacy does not expose one universal per-stack compost-disposable quantity.

**Challenge result:** F3 was incorrect, not merely unverified. Absence from a HOME-only observation
kernel did not prove absence from Minecraft's POI registry. The proposed scan would create a second
workstation interpreter beside the canonical V3-D scope. The transaction text also risked double
debit and mistaking a valid unchanged-level attempt for failure.

**Alternatives:** A = shared bounded D2 `PoiTypes.FARMER` evidence with live executor revalidation
(recommended); B = executor-local bounded FARMER-POI query (smaller, but leaves D2 open); C = cubic
block scan (rejected). Strongest objection to A is disproportionate cache lifecycle for one consumer;
switch to B only if Gate 0 proves that cost, while retaining POI truth and bounded discovery.

**Decision transitions:** `D-VR-085` → **CONTESTED** and partly superseded by `D-VR-085-R1`;
`D-VR-086` → **CONTESTED** pending a quantified shared reserve owner; `D-VR-087` → **CONTESTED /
SUPERSEDED IN PART** by vanilla-correct `D-VR-087-A1`. No earlier locked V3 authority was removed.

**MAIBS additions:** a low-probability compostable may be consumed with no level rise; level 8 may
remain ready until a farmer/player extracts; two mobs may sequentially converge; P4 torch contention
may starve the lowest village-work activity. These are now explicit acceptance/runtime questions.

**Frontier after:** decide shared D2 evidence versus local bounded POI query, decide whether D2 and F
are separate tasks (recommended: D2 first, F second), and pin the reserve/transaction owners. No task
brief, implementation, build, or runtime is authorized by this RFC pass.

*(Superseded 2026-08-22 by contribution below — decisions are now **LOCKED**.)*

### Contribution — Agent_Cursor (`Work the RFC`, 2026-08-22)

*(**SUPERSEDED** 2026-08-22 by User dependency-drift correction — broad `VillageWorkstationFacts`
task-58 split rejected; see contribution below.)*

**Agent:** `Agent_Cursor` · **Mode:** `RFC_DESIGN_WORK_ARTIFACT_ONLY`
**Contribution type:** architecture lock + task assignment + evidence — **RFC artifact only**

**Frontier before:** `D-VR-085-R1`, `D-VR-086`, and `D-VR-087-A1` were **CONTESTED** after
`Agent_Codex` peer challenge. Task numbering unassigned. V3-E closed at 1543 tests.

**Evidence (`CODE_CONFIRMED` — shipped production):**

| Probe | Result |
| --- | --- |
| `VillageWorkObservationKernel` lazy HOME iterator + budget cap | task-56 CLOSE-56-2 pattern reusable for FARMER |
| Transient `VillageWorkFactsCache` + scheduler + anchor invalidation | no SavedData — falsifies Codex switch-to-B condition |
| `PopulationFoodExpendabilityPolicy` + `PlayerNutritionReserve` | task-57 quantified survival + breeding-food pools |
| `PopulationFoodExpendabilityPolicy.disposableVillagerFoodValue` | public compose point for compost layer 5 |
| `ComposterBlock.insertItem` shrink-on-eligible | `D-VR-087-TX1` single-debit owner |
| `PopulationFoodSupportGoal` at P4 after harvest | registration order precedent for compost |

**Decision transitions (LOCKED):**

| ID | Transition |
| --- | --- |
| **D-VR-085-R1** | Option **A LOCKED**; B rejected; C rejected |
| **D-VR-085-A1** | **NEW LOCKED** — `VillageWorkstationFacts` companion record + `FarmerPoiCandidateSource` |
| **D-VR-086-A1** | **NEW LOCKED** — `CompostExpendabilityPolicy` composes canonical reserve owners |
| **D-VR-087-A1** | **LOCKED** |
| **D-VR-087-TX1** | **NEW LOCKED** — vanilla `insertItem` owns shrink; mirror delta to backpack |

**Task assignment (`SUPERSEDED / HISTORICAL — DO NOT USE`):**

```text
task-58 = V3-D2 shared workstation evidence
task-59 = V3-F compost executor
task-60 = V3-G integration/runtime closure
```

**Canonical assignment (LOCKED 2026-08-22):** `task-58 = V3-F`, `task-59 = V3-G` — see amended
contribution below and V3 tasks table.

**Strongest remaining objection:** P4 `PlaceTorchGoal` may starve lowest-priority village work —
**RUNTIME_QUESTION**; out of task-58/59 scope (task-57 precedent).

**MAIBS note:** compost COMMIT has no villager-style ACK_WAIT — unchanged composter level after
eligible insert is **vanilla success**, not failure; episode must still terminate and backoff.

**Frontier after (superseded):** ~~BEGIN task-58 / V3-F — BRIEF DESIGN ONLY~~ → **task-58 CLOSED**;
**task-59 / V3-G — NEXT but HOLD**.

### Contribution — User + `Agent_Cursor` (V3-F dependency drift, 2026-08-22)

**Agent:** `User` (architecture) · `Agent_Cursor` (RFC artifact) · **Mode:** `RFC_DESIGN_WORK_ARTIFACT_ONLY`

**Frontier before:** Prior pass locked broad V3-D2 `VillageWorkstationFacts` as task-58 prerequisite
for task-59 V3-F compost executor. Stale assumption: shipped V3-D supplies workstation awareness.

**Evidence (`CODE_CONFIRMED`):**

| Probe | Result |
| --- | --- |
| `VillageWorkFacts` fields | Population/HOME only — no composter/workstation positions |
| `VillageWorkObservationKernel` | HOME POIs + adult villagers only (task-56) |
| `VillagePerception` public surface | Anchor/count/coverage — not facility positions |
| `SellReserveModel` | **modelled-only** for sell — `empty()` does not veto `CompostReserveModel` seeds |

**Decision transitions:**

| ID | Transition |
| --- | --- |
| **D-VR-085-R1** | **AMENDED** — Option A = `ComposterWorkFacts` on perception cadence; broad D2 prerequisite **REJECTED** |
| **D-VR-085-A1** | **SUPERSEDED** by **D-VR-085-A2** |
| **D-VR-086-A1** | **SUPERSEDED IN PART** by **D-VR-086-A2** (`CompostReserveModel`; narrow gen-1) |
| **D58-1…D58-12** | **NEW LOCKED** — brief-ready design rules |
| **D-VR-087-A1** + **TX1** | **UNCHANGED** — one vanilla attempt; input-only gen-1 bone meal |

**Task assignment (amended):**

```text
task-58 = V3-F unified (ComposterWorkFacts + CompostGoal @ P5 provisional)
task-59 = V3-G integration/runtime closure
```

**Strongest remaining objection:** P5 may still lose to P4 village-work siblings — acceptable;
compost is explicitly a side activity (`RUNTIME_QUESTION` for torch contention only).

**Frontier after (superseded by closure sync 2026-08-22):** ~~BEGIN task-58 / V3-F — BRIEF DESIGN ONLY~~
→ **task-58 CLOSED**; **task-59 / V3-G — NEXT but HOLD**.

### Contribution — User RFC review + `Agent_Cursor` (Task-58 closure sync, 2026-08-22)

**Agent:** `User` (review verdict) · `Agent_Cursor` (RFC artifact) · **Mode:** `RFC_DESIGN_WORK_ARTIFACT_ONLY`

**Trigger:** User RFC review verdict — **NOT LOCK-READY for Task-59** until synchronization debt cleared.
Task-58 implementation/static closure **not reopened**.

**Corrections applied:**

| Finding | Resolution |
| --- | --- |
| Stale frontier (Tasks 52–57 / V3-F not authorized) | Identity header, phased plan, V3 tasks table, phase architecture → **task-58 CLOSED**, **1589 tests**, **task-59 HOLD** |
| `SellReserveModel` veto contradiction | `D-VR-086-A2` ladder: sell model **modelled-only**; `empty()` ≠ compost veto; `CompostReserveModel` is gen-1 seed authority |
| VR-T3f / V3-G closure contradiction | Phase closure **LOCKED**: applicable rows only; **VR-T3f non-applicable** while V3-D2 deferred |
| Obsolete LOCKED task assignment | Pre-amendment block marked **SUPERSEDED / HISTORICAL — DO NOT USE** |
| Pre-Gate-0 Task-58 prose | Seeds pinned (wheat/beetroot); input-only; P5 implemented; Gate 0 PASS |
| Task-59 Gate 0 ambiguity | Explicit disposition: **Gate 0 not required**; runtime launch separately authorized |

**Frontier after:** **Task-59 / V3-G — NEXT but HOLD** until separate authorization. Re-authorize only
after reviewer confirms lock-readiness.
