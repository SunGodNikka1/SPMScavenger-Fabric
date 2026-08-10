# Task 34 Report — RT-GAO minimal + GAO-4.1 + GAO-5

**Status:** `DONE_WITH_CONCERNS` — **RET-1 BLOCKED** on outer `OpinionExperienceRegistry.CONTEXTS`

**Scope:** User roadmap `RT-GAO minimal sanity check → GAO-4.1 → GAO-5`

## Delivered

### RT-GAO minimal (static)
- `RtGaoMinimalSanityTest` — GAO-PARITY threshold unchanged when opinion off; GAO-4.1 boredom modulation via `ExploreReadinessThresholds`; GAO-5 context ownership
- No Minecraft launch (not authorized for full RT-GAO-1 matrix)

### GAO-4.1 — PD-GAO-01 C threshold wiring
- `ExploreIdleThresholdPolicy` — boredom scales `exploreIdleTicks` (max boredom @600 base → 375 ticks)
- `ExploreReadinessThresholds` — shared helper with `OpinionFeatureGate` parity path
- Wired in `ExplorationActivityGoal` (observer adoption + director) and `ExploringGoal.canUse`
- `ExplorationReadinessTest.gao41LowerIdleThresholdUnlocksExpeditionSooner`

### GAO-5 — PLACE opinion MVP
- `PlaceOpinionMemory` — chunk LRU (32), preference clamp
- `PlaceOpinionService` — mining terminal deltas
- `MobExperienceContext.placeOpinionMemory()`; death clear via `OpinionExperienceRegistry.onDeath`
- `ExperienceEmitters.miningTerminal` hook
- `DiscretionaryScoringInput` + `ActivityUtilityScorer` place affinity (`PLACE_PREFERENCE=22`)
- `DiscretionaryActivityDirector.tick` accepts `BlockPos placeAnchor`

## Verification (`CONFIRMED`)

```text
Working directory: Projects/SPMScavenger-1.21.1-Fabric
Command: .\gradlew.bat clean build
Result: BUILD SUCCESSFUL — 548 tests, 0 failures
```

New tests: `ExploreIdleThresholdPolicyTest`, `RtGaoMinimalSanityTest`, `PlaceOpinionMemoryTest`, `PlaceOpinionServiceTest`, `PlaceOpinionScoringTest`, `ExplorationReadinessTest.gao41*`

## Concerns / UNVERIFIED

| Claim | Status | Missing probe |
| --- | --- | --- |
| GAO-PARITY in live world | `UNVERIFIED` | `opinion.enabled=false` runtime session |
| GAO-M4 bored expedition unlock | `UNVERIFIED` | multi-minute observation with opinion on |
| GAO-5 place bias in explore heading | **NOT IMPLEMENTED** | GAO-5B heading consumer |
| GAO-5 current-place EXPLORE penalty | **`CODE_CONFIRMED` inversion** | negative chunk pref lowers EXPLORE while mob still on site |
| GAO-4.1 threshold in normal idle ramp | **Non-binding** | readiness ~30s; boredom intent ~3–4 min |
| GAO-THRESHOLD runtime | `UNVERIFIED` | static math only |
| Save/reload place memory | `UNVERIFIED` | not persisted (by design for MVP) |
| **RET-1 outer `CONTEXTS` map** | **`FAIL`** | session-unbounded; `remove()` has zero production callers; freeze ≠ eviction |

## Self-review

- **Must happen:** boredom lowers idle threshold when opinion on — `CONFIRMED` (unit tests)
- **Must happen:** `NO_PROGRESS` reduces place preference — `CONFIRMED` (`PlaceOpinionServiceTest`)
- **Must not happen:** place dislike vetoes mandatory work — `INFERRED` (utility-only, no admission hooks changed)
- **GAO-PARITY:** opinion off returns raw `cfg.exploreIdleTicks` — `CONFIRMED` (`RtGaoMinimalSanityTest`)

## Frontier

**Priority 1:** Fix/audit `OpinionExperienceRegistry` lifetime (RET-1 outer owner) — production eviction
on unload/death or bounded frozen-context retention.

**Priority 2:** GAO-5B — heading / expedition consumer so negative place memory biases *where to go*,
not EXPLORE utility at the current anchor.

**RT-GAO-1** full runtime matrix — requires explicit Minecraft launch approval.

**MAIBS:** `CONDITIONAL — BEHAVIORALLY PLAUSIBLE, RET-1 BLOCKED` — see `task-34-maibs-report.md`
(revised 2026-08-10 peer review).
