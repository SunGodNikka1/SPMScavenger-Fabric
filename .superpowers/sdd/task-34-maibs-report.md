# Task 34 MAIBS Report — GAO-4.1 + GAO-5 post-implementation

**Gate:** MAIBS-1  
**Scope:** Post-implementation semantic-drift review of GAO-4.1 threshold wiring and GAO-5 PLACE opinion MVP  
**Evidence class:** `CODE_CONFIRMED` (source inspection + unit tests); runtime `UNVERIFIED`  
**Date:** 2026-08-10

## PLANNED → IMPLEMENTED → PREDICTED RUNTIME

| Phase | GAO-4.1 (PD-GAO-01 C) | GAO-5 (PLACE MVP) |
| --- | --- | --- |
| **Planned** | Boredom shortens `exploreIdleTicks`; unlock expeditions sooner when bored | Mining terminals teach chunk preferences; soft bias on return vs new heading |
| **Implemented** | `ExploreIdleThresholdPolicy` → `ExploreReadinessThresholds`; shared by observer + `ExploringGoal` | `PlaceOpinionMemory` + `PlaceOpinionService`; EXPLORE utility term via current `mob.blockPosition()` anchor only |
| **Predicted runtime** | ~11s earlier idle unlock at max boredom vs parity (375 vs 600 ticks); **dominant gate remains director utility + boredom ramp (~3–4 min)** | Player may see REST preferred over EXPLORE while standing on disliked chunk; **no visible change to expedition heading or re-descent site choice** |

**Parity:** GAO-4.1 `FUNCTIONAL`; GAO-5 `PARTIAL` (utility-at-idle-site only, not route/heading consumer per RFC minimum viable diagram).

---

## Intent vs mechanism vs prediction

| Behavior | Intended | Implemented mechanism | Predicted observable | Weirdness / failure | Confidence |
| --- | --- | --- | --- | --- | --- |
| Bored mob unlocks explore sooner | Mood lowers idle threshold | `idleWorkTicks >= effectiveIdleTicks(boredom)`; max @600 base → 375 | Unlock ~19s idle vs ~30s at boredom 0; intent still ~3–4 min | Threshold change is weak vs boredom utility gate | `CODE_CONFIRMED` |
| Director EXPLORE intent | Only when adoption ready | `scoreWithExploreAdoptionGate` strips EXPLORE when `!exploreAdoptionReady` | Intent cannot outrun readiness (GAO-4 control plane holds) | Adoption ready at 30s while intent waits for boredom | `CODE_CONFIRMED` |
| Discretionary explore execution | Intent + plan | `mayStartDiscretionaryExplore` + `ExploringGoal` path probe | Directed multi-stage travel when plan succeeds | Plan fail → cooldown 600 ticks, intent may expire TTL 200 | `CODE_CONFIRMED` |
| Failed mine site dislike | Avoid returning | Chunk delta on terminal; utility at **current** anchor | While still on site after `NO_PROGRESS`, explore score drops | Leaving chunk neutralizes anchor; heading unchanged on next expedition | `CODE_CONFIRMED` |
| Mandatory mining | Unaffected by place dislike | No hook in `MiningDirector` / `WorkDemandPolicy` | Iron NEED still assigns | — | `INFERRED` |
| Opinion off parity | Raw 600 idle ticks | `OpinionFeatureGate` bypass in `ExploreReadinessThresholds` | Same wander/explore timing as pre-opinion | — | `CODE_CONFIRMED` |

---

## GoalSelector interaction table (relevant slice)

| Goal | Priority | Flags | Can interrupt feature? | State retained? | Expected observable |
| ---: | ---: | --- | --- | --- | --- |
| Gather/Craft/Smelt/Descent/Tunnel | 3 | MOVE/… | Yes — blocks explore/mining admission | Project NBT | Work preempts discretionary |
| `CampfireGoal` | 7 | MOVE | Yes vs wander; competes with explore 8 | REST claim + director | Voluntary rest when utility wins |
| `ExploringGoal` | 8 | MOVE | Preempts wander 9 when `canUse` | `ExpeditionState` preserved on `stop()` | Long-range travel |
| `TrackedLocalWanderGoal` | 9 | MOVE | Yields when `mustYieldWander` (running discretionary) | Trip counter on natural complete | Local loops; 2 trips can unlock explore |
| `ExplorationActivityGoal` | 9 | **none** | Never steals MOVE | `ExplorationReadiness` + director | Observer every 10 ticks |

**Interruption probe (combat):** `DiscretionaryEligibility.invalidationForObservation` + `mob.getTarget() != null` → director invalidates intents; `ExploringGoal` returns false on target. **Resume:** expedition state retained; path replanned. `CODE_CONFIRMED` from GAO-4 tests; runtime `UNVERIFIED`.

---

## Time simulation — GAO-M4 baseline (curious preset, iron satisfied, no project)

Assumptions: `opinion.enabled=true`, `exploring=true`, `campfire=true`, replace-host-stroll install, no combat, no descent pressure.

| Checkpoint | Ticks | idleWorkTicks | boredom (≈) | effective idle threshold | Director / executor |
| ---: | ---: | ---: | ---: | ---: | --- |
| T0 | 0 | 0 | 0 | 600 | Wander pri 9 |
| T+10 | 10 | 10 | 0.12 | 600 | Idle classify; affect pulse |
| T+600 | 600 | 600 | ~7 | ~598 | **exploreAdoptionReady true**; explore utility still low → likely abstain |
| T+2000 | 2000 | 2000* | ~24 | ~590 | Still wandering; readiness consumed only on expedition start |
| T+4170 | ~4170 | saturated** | ~50 | ~488 | EXPLORE likely wins utility → **pending intent** |
| T+4200+ | plan OK | — | — | — | `ExploringGoal` MOVE; `onExploreAdopted`; wander yields |
| T+1200+ travel | — | — | rises novelty/engagement | — | Stage waypoints; repetition builds |
| T+end | cooldown 600 | reset on consume | slower boredom decay if satisfied | 600→375 modulated | Return wander; repeat loop |

\* idleWorkTicks only increments when `!meaningfulWork && !exploring`; resets on meaningful work.  
\*\* After first expedition `consume()`, idle counter resets; subsequent unlocks wait new accumulation.

**GAO-4.1 delta vs pre-wiring:** At boredom 100, threshold 375 saves **225 idle ticks (~11.25s)** vs 600. Does **not** materially shift the 3–4 minute boredom→intent timeline.

---

## Geometry / coordinates (GAO-5)

**Scenario:** Mob at `(80, 64, 80)` ends `NO_PROGRESS` mine; terminal records chunk key `(5, 5)`.

| Question | Answer |
| --- | --- |
| Selected coordinates | `mob.blockPosition()` at director tick — **current** chunk, not expedition waypoint |
| Physical space | Chunk-level preference; no block-level cave mouth geometry |
| Path / reachability | GAO-5 does not select path targets |
| Arrival / continuation | N/A for place utility; explore heading chosen by `ExploringGoal` heightmap/region memory (unchanged) |
| Terrain change | Place memory persists in session; death clears; unload freezes, does not clear |

**NOT FOUND (heading consumer):**

1. `PlaceOpinionMemory` / `placeOpinionMemory` in `ExploringGoal.java` — **NOT FOUND**
2. `PlaceOpinion` in `DescentHeadingPolicy` / `MiningDirector` — **NOT FOUND**
3. `placeAnchor` outside `DiscretionaryActivityDirector` / `ActivityUtilityScorer` — **NOT FOUND**

---

## Threshold audit — boredom 0 → high (GAO-4.1)

| Transition | Turns off | Turns on | Owner | Re-activation gap |
| --- | --- | --- | --- | --- |
| idleWorkTicks crosses `effectiveThreshold` | — | `exploreAdoptionReady` | `ExplorationReadiness` | Cooldown 600 after consume |
| boredom crosses utility band | REST explore fit low | EXPLORE boredom fit high | `IdleOpportunityPolicy` | Continuous |
| EXPLORE intent issued | — | `pendingIntent` | `DiscretionaryDirectorState` | TTL 200 ticks without adoption |
| Plan failure | MOVE (explore) | wander | `ExploringGoal` | `retryAfterTick` + cooldown |

**Gap risk:** Between adoption-ready (30s) and boredom-driven intent (3–4 min), mob keeps wandering — **intentional**, not a stall.

---

## Adversarial scenarios

| Scenario | GAO-4.1 + director | GAO-5 |
| --- | --- | --- |
| Normal terrain idle | Wander → intent → explore | Neutral place anchor |
| Flat / no signal | Same; trips may unlock earlier | No terminals → empty place memory |
| Failed mine at site | Readiness independent | Dislike depresses EXPLORE **at site only** |
| Left bad site, idle elsewhere | — | Anchor neutral; **no “avoid return”** |
| Combat mid-wander | Intent invalidated | — |
| 2 local trips quickly | Explore without boredom (`localTripsThreshold`) | — |
| `opinion.enabled=false` | Threshold 600 fixed; gates pass-through | No place learning |
| Two mobs same chunk | Independent contexts | Independent place maps |
| Long session (30+ min) | LRU 32 places; episode tombstones bounded | Place LRU eviction |

---

## Predicted weird behaviors (≥3)

| # | Weird behavior | Classification | Falsifying probe |
| ---: | --- | --- | --- |
| W1 | **“Boredom takes minutes but threshold math says 30s ready”** — player waits minutes because director utility gate dominates GAO-4.1 | `ACCEPTABLE_STEPPING_STONE` — document dual gate | Log `exploreAdoptionReady` vs intent issue tick |
| W2 | **Failed mine does not change where explore goes** — mob revisits disliked chunk on next expedition | `ACCEPTABLE_STEPPING_STONE` — MVP scope | Record expedition waypoints after `NO_PROGRESS` |
| W3 | **Standing on bad chunk pushes REST over EXPLORE** — looks like “giving up” not “going somewhere else” | `RUNTIME_QUESTION` | Compare REST/EXPLORE trace at mine site vs 32 blocks away |
| W4 | **Two-trip unlock bypasses affect** — explore without ever feeling “bored” | `ACCEPTABLE_STEPPING_STONE` — existing readiness design | Count trips vs boredom at first expedition |
| W5 | **GAO-4.1 invisible in play** — 11s savings drowned in minute-scale boredom | `RUNTIME_QUESTION` | A/B `opinion on` with boredom seeded high vs threshold only |

---

## RET-1 (GAO-5 collections)

| Collection | Key | Bound | Production eviction | Death / unload |
| --- | --- | ---: | --- | --- |
| `PlaceOpinionMemory.byChunk` | chunk long | 32 LRU | `removeEldestEntry` on insert | `clear()` on `onDeath`; freeze on unload, **not** cleared |

**Verdict:** `PASS` for bounded in-session store; no disk persistence (`UNVERIFIED` cross-session).

---

## Acceptance tests

| Must happen | Must not happen | Status |
| --- | --- | --- |
| High boredom lowers `ExploreReadinessThresholds` below 600 | Place dislike blocks `MiningDirector` assignment | Static `CONFIRMED` / `INFERRED` |
| `NO_PROGRESS` reduces preference at chunk | Permanent wander with opinion on + boredom high | `UNVERIFIED` runtime |
| Director EXPLORE waits for `exploreAdoptionReady` | Intent without executor adoption forces wander yield | `CODE_CONFIRMED` (GAO-4 tests) |

---

## MAIBS-1 gate result

**`CONDITIONAL — ACCEPTABLE_STEPPING_STONE`**

- **GAO-4.1:** Mechanism is coherent and correctly shared across observer and executor; observable effect is **bounded and weak** relative to boredom-utility timeline. Not an architecture defect.
- **GAO-5 MVP:** Correctly bounded soft utility at idle anchor; **semantic drift** from RFC diagram’s “heading / re-descent admission” is explicit and authorized only as stepping-stone.
- **Overall loop:** Perceive → affect → score → intent → readiness → explore path → terminals → place learn is **closed at static level**; several-minute plausibility remains **`UNVERIFIED`** until RT-GAO-1 launch.

**Recommended runtime probes (RT-GAO-1 priority):**

1. GAO-M4 — 8 min idle wander: log boredom, `idleTicks`, intent issue, expedition start timestamps.
2. GAO-5 — `NO_PROGRESS` then idle 2 min: capture director trace `SELECT` winner at mine chunk vs after relocation.
3. GAO-PARITY — `opinion.enabled=false` 5 min: compare expedition count/timing to baseline.
