# Task 34 MAIBS Report — GAO-4.1 + GAO-5 post-implementation

**Gate:** MAIBS-1  
**Scope:** Post-implementation semantic-drift review of GAO-4.1 threshold wiring and GAO-5 PLACE opinion MVP  
**Evidence class:** `CODE_CONFIRMED` (source inspection + unit tests); runtime `UNVERIFIED`  
**Date:** 2026-08-10  
**Revised:** 2026-08-10 (peer review — RET-1 outer registry + GAO-5 semantic inversion)

---

## MAIBS-1 gate result (revised)

**`CONDITIONAL — BEHAVIORALLY PLAUSIBLE, RET-1 BLOCKED`**

Task 34 implementation is **not rejected** — GAO-4.1 mechanics are sound and the discretionary loop remains
coherent at static level. The prior blanket `ACCEPTABLE_STEPPING_STONE` verdict is **withdrawn** because:

1. **RET-1 fails on the outer owner** — `OpinionExperienceRegistry.CONTEXTS` is session-unbounded.
2. **GAO-5 current-place penalty inverts spatial intent** — negative place memory lowers EXPLORE utility
   while the mob is still physically on the disliked chunk (`ARCHITECTURE_DEFECT` for avoidance semantics;
   acceptable only if MVP explicitly does **not** claim spatial avoidance).

**Recommended fix order (product):**

1. **Audit/fix `OpinionExperienceRegistry` lifetime** — production eviction on unload/death or bounded
   retention policy for frozen contexts.
2. **GAO-5B** — heading / expedition / re-descent consumer so negative place memory biases *where to go*,
   not *whether to leave the current chunk*.
3. RT-GAO-1 runtime falsification (unchanged frontier).

---

## Verdict table (revised)

| Area | Verdict | Notes |
| --- | --- | --- |
| GAO-4.1 mechanics | **PASS — BEHAVIORALLY PLAUSIBLE** | Shared threshold wiring correct |
| GAO-4.1 usefulness | **Weak / non-binding** in normal idle ramp | Readiness ~30s; boredom intent ~3–4 min |
| GAO-5 bounded chunk LRU | **PASS** | Inner `PlaceOpinionMemory` is excellent |
| GAO-5 spatial avoidance | **PARTIAL** | No heading consumer (`NOT FOUND` ×3) |
| Current-place EXPLORE penalty | **Semantic inversion risk** | `CODE_CONFIRMED`; see W3 below |
| RT-GAO runtime behavior | **UNVERIFIED** | No launch |
| RET-1 outer context registry | **FAIL / UNRESOLVED** | `CONTEXTS` grows with distinct UUIDs per session |

---

## PLANNED → IMPLEMENTED → PREDICTED RUNTIME

| Phase | GAO-4.1 (PD-GAO-01 C) | GAO-5 (PLACE MVP) |
| --- | --- | --- |
| **Planned** | Boredom shortens `exploreIdleTicks`; unlock expeditions sooner when bored | Mining terminals teach chunk preferences; soft bias on return vs new heading |
| **Implemented** | `ExploreIdleThresholdPolicy` → `ExploreReadinessThresholds`; shared by observer + `ExploringGoal` | `PlaceOpinionMemory` + `PlaceOpinionService`; EXPLORE utility term via **current** `mob.blockPosition()` anchor only |
| **Predicted runtime** | Threshold modulates at ~19s only if boredom already high; **normal path: readiness ~30s, opinion-driven explore ~3–4 min** | Disliked chunk **depresses EXPLORE while mob remains there**; no heading bias |

**Parity:** GAO-4.1 `FUNCTIONAL`; GAO-5 `PARTIAL` — utility-at-current-chunk only, not spatial avoidance.

---

## RET-1 — full audit (revised)

Gate RET-1 requires auditing **every long-lived collection**, including the **outer owner**, not only
inner bounded stores.

### Inner collection — `PlaceOpinionMemory` (`PASS`)

```text
per mob
└─ PlaceOpinionMemory
   └─ max 32 chunk entries (access-order LinkedHashMap + removeEldestEntry)
```

| Field | Value |
| --- | --- |
| Key | chunk `long` |
| Bound | 32 LRU |
| Production eviction | `removeEldestEntry` on insert |
| Death | `clear()` via `OpinionExperienceRegistry.onDeath` |
| Unload | context frozen; place memory **not** cleared (acceptable if context evicted) |

### Outer owner — `OpinionExperienceRegistry.CONTEXTS` (`FAIL`)

```text
ConcurrentMap<UUID, MobExperienceContext> CONTEXTS   // static, session-scoped
```

**Lifecycle (`CODE_CONFIRMED` — `OpinionExperienceRegistry.java`, `SpmScavenger.java`):**

| Event | What happens | Context removed? |
| --- | --- | --- |
| PlayerMob load | `resume()`; `contextFor()` may allocate | No |
| PlayerMob unload | `RestSessionCoordinator.invalidateOnUnload`; `freeze()` | **No** — context retained |
| PlayerMob death | `onDeath()` — partial opinion reset + `placeOpinionMemory.clear()` | **No** — context retained |
| Server stop | `shutdownServerState()` → `CONTEXTS.clear()` | Yes (whole map) |

**Scaling risk (session-unbounded):**

```text
100 distinct PlayerMob UUIDs seen  → ~100 MobExperienceContext retained
500 distinct UUIDs                 → ~500 contexts
2000 distinct UUIDs                → ~2000 contexts
```

…even when most mobs are currently unloaded.

**`remove(UUID)` exists** (`OpinionExperienceRegistry.java:109`) but **zero production callers**
(`NOT FOUND` — grep `OpinionExperienceRegistry.remove(` across project).

This is exactly the RET-1 anti-pattern: eviction API with no production call site; freeze is not deletion.

**Verdict:** Inner `PlaceOpinionMemory` **PASS**; outer registry **FAIL / UNRESOLVED**.

---

## GAO-5 semantic inversion (`CODE_CONFIRMED`)

### Mechanism chain

1. Director passes `mob.blockPosition()` as `placeAnchor` (`ExplorationActivityGoal.java:134`).
2. `ActivityUtilityScorer.scoreExplore` applies:
   `placeAffinity = channel(places.preference(chunk)) * PLACE_PREFERENCE` (weight **22**).
3. `NO_PROGRESS` records **−14** per terminal (`PlaceOpinionService`); repeated bad outcomes → **−100** clamp.

### Inverted loop

```text
Mob mines here
    ↓
NO_PROGRESS terminal
    ↓
"I dislike this chunk" (preference −14…−100)
    ↓
Mob still physically on this chunk
    ↓
placeAnchor = current chunk → negative placeAffinity
    ↓
EXPLORE utility decreases
    ↓
Mob less inclined to choose EXPLORE (the activity that would leave)
```

**Intended PLACE semantics:** “This location sucked; try somewhere else.”  
**Implemented effect at idle on site:** “This location sucks; I am **less** interested in exploring **from here**.”

### Proper claim separation

| Question | Verdict |
| --- | --- |
| Will REST actually beat EXPLORE at runtime? | `RUNTIME_QUESTION` |
| Does negative current-place preference lower EXPLORE utility? | **`CODE_CONFIRMED`** |
| Does that accomplish avoid-this-place / leave behavior? | **No** |

**Classification:** `ARCHITECTURE_DEFECT` for spatial-avoidance semantics. Acceptable **only** if MVP
explicitly documents that GAO-5 does not implement avoidance yet — which the RFC defers to GAO-5B.

**NOT FOUND (heading consumer) — unchanged:**

1. `PlaceOpinionMemory` in `ExploringGoal.java` — **NOT FOUND**
2. `PlaceOpinion` in `DescentHeadingPolicy` / `MiningDirector` — **NOT FOUND**
3. `placeAnchor` outside director/scorer — **NOT FOUND**

---

## GAO-4.1 — mechanics vs usefulness

### Mechanics (`PASS`)

- `ExploreReadinessThresholds` shared by observer and `ExploringGoal`.
- Max boredom @600 base → **375** ticks (`ExploreIdleThresholdPolicyTest`).
- Opinion-off parity preserves raw 600 (`RtGaoMinimalSanityTest`).

### Usefulness (`weak / non-binding` in normal idle ramp)

| Signal | Approximate timing |
| --- | --- |
| Boredom +0.12 / 10-tick observation | ~50 boredom after **~3.5 min** pure idle |
| `exploreAdoptionReady` (idleWorkTicks ≥ 600) | **~30 s** (boredom still ~7) |
| Director EXPLORE utility wins | **~3–4 min** (boredom ~50+) |

GAO-4.1 threshold drop (600→375) matters only when boredom is **already high** at idle onset — e.g.
mob enters discretionary idle already bored. For neutral→bored ramp, **readiness was never the binding
constraint**; director boredom utility was.

**Not broken** — just not a major behavioral feature in the default timeline.

---

## Intent vs mechanism vs prediction (selected rows)

| Behavior | Intended | Implemented | Predicted | Confidence |
| --- | --- | --- | --- | --- |
| Boredom lowers idle threshold | Yes | Yes | Saves ~11s at max boredom only | `CODE_CONFIRMED` |
| Opinion-driven explore timing | Boredom-driven | Director utility | ~3–4 min, not threshold | `CODE_CONFIRMED` |
| Disliked place → leave | Avoid / go elsewhere | Current-chunk EXPLORE penalty | **Less explore from bad site** | `CODE_CONFIRMED` inversion |
| Mandatory mining | Unchanged | No place hook in director | Unaffected | `INFERRED` |

---

## Time simulation — GAO-M4 (unchanged core)

Normal idle ramp:

```text
~30 s     exploreAdoptionReady true (idleWorkTicks ≥ 600; boredom still low)
~3–4 min  boredom ~50+ → EXPLORE utility wins → pending intent
~5 min+   ExploringGoal adopts if plan succeeds
```

GAO-4.1 does not materially advance the 3–4 minute opinion-driven explore timeline in the default case.

---

## Predicted weird behaviors (revised)

| # | Weird behavior | Classification | Notes |
| ---: | --- | --- | --- |
| W1 | Readiness ~30s but explore intent ~3–4 min | `ACCEPTABLE_STEPPING_STONE` | Dual gate by design |
| W2 | Failed mine does not steer expedition heading | `ACCEPTABLE_STEPPING_STONE` | GAO-5B deferred |
| W3 | **Disliked chunk lowers EXPLORE while mob still there** | **`ARCHITECTURE_DEFECT`** (avoidance semantics) | Inversion loop; REST-vs-EXPLORE winner still `RUNTIME_QUESTION` |
| W4 | Two-trip unlock bypasses boredom | `ACCEPTABLE_STEPPING_STONE` | Readiness design |
| W5 | GAO-4.1 invisible in normal play | `CODE_CONFIRMED` | Non-binding vs utility gate |
| W6 | **Session context map grows with every UUID touched** | **`ARCHITECTURE_DEFECT`** (RET-1) | `remove()` unwired |

---

## Acceptance tests (revised)

| Must happen | Status |
| --- | --- |
| High boredom lowers threshold below 600 | `CONFIRMED` (unit) |
| `NO_PROGRESS` reduces chunk preference | `CONFIRMED` (unit) |
| Director EXPLORE waits for adoption ready | `CONFIRMED` (GAO-4 tests) |
| Negative place memory eventually biases **departure / heading** | **NOT IMPLEMENTED** |

| Must not happen | Status |
| --- | --- |
| Place dislike blocks mandatory mining | `INFERRED` |
| `CONTEXTS` grows without bound per server session | **`CONFIRMED` risk** — current code |
| Permanent wander with rising boredom | `UNVERIFIED` runtime |

---

## Runtime probes (unchanged priority)

1. **GAO-M4** — 8 min idle: boredom, `idleTicks`, intent issue, expedition start timestamps.
2. **GAO-5** — `NO_PROGRESS` then idle at site vs 32 blocks away: director `SELECT` + EXPLORE/REST breakdown.
3. **GAO-PARITY** — `opinion.enabled=false` 5 min baseline.
4. **RET-1** — `OpinionExperienceRegistry.contextCount()` vs distinct PlayerMobs spawned/despawned over 30+ min session.

---

## Evidence paths

| Claim | Path |
| --- | --- |
| Unload freeze, no remove | `SpmScavenger.java:106–111` |
| Death partial reset, context retained | `SpmScavenger.java:123–126`, `OpinionExperienceRegistry.onDeath` |
| Server stop clear | `SpmScavenger.java:117–119`, `shutdownServerState()` |
| `remove()` unwired | `OpinionExperienceRegistry.java:109` — zero production callers |
| Current place anchor | `ExplorationActivityGoal.java:134` |
| Place utility term | `ActivityUtilityScorer.java`, `ActivityUtilityWeights.PLACE_PREFERENCE = 22f` |
| NO_PROGRESS delta | `PlaceOpinionService.preferenceDelta` → −14 |
