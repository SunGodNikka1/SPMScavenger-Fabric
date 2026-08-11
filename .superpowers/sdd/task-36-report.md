# Task 36 Report — GAO-5B (place opinion destination ranking)

**Status:** `DONE` (static/unit); runtime `UNVERIFIED`

## Delivered

### Core change
| GAO-5A (removed) | GAO-5B (added) |
| --- | --- |
| Current chunk → EXPLORE utility | Final destination chunk → route candidate score |
| Disliked site lowers EXPLORE | Disliked destination lowers that route's rank |

### Files
- **NEW** `PlaceOpinionRouteRanker` — `destinationBias` / `routeBias`, `MAX_ROUTE_BIAS = 15`
- **EDIT** `ExploringGoal` — route scoring adds place bias on final waypoint; `placeMemoryForRouteRanking()` uses `find()` (no `MobExperienceContext` alloc); may still allocate empty `PlaceOpinionMemory` when no context exists (ephemeral, not RET-1)
- **EDIT** `ActivityUtilityScorer` — PLACE term removed from `scoreExplore`
- **EDIT** `DiscretionaryScoringInput` — `placeAnchor` / `placeOpinionMemory` removed
- **EDIT** `DiscretionaryActivityDirector.tick` — no `placeAnchor` parameter
- **EDIT** `ExplorationActivityGoal` — director tick without `blockPosition()`
- **NEW** `PlaceOpinionRouteRankerTest` (6 tests); **DELETED** `PlaceOpinionScoringTest`

### Deferred (documented)
- `DescentHeadingPolicy` — same `destinationBias` primitive for equally-valid descent headings; not wired (mandatory descent path untouched).

## Verification (`CONFIRMED`)

```text
Working directory: Projects/SPMScavenger-1.21.1-Fabric
Command: .\gradlew.bat clean build
Result: BUILD SUCCESSFUL — 556 tests, 0 failures
```

## Acceptance mapping

| Must happen / not happen | Evidence |
| --- | --- |
| Negative destination loses to neutral at equal base | `PlaceOpinionRouteRankerTest.mustHappen_negativeDestinationLosesToNeutralWhenBaseScoresEqual` |
| Opinion-off parity | `mustHappen_opinionOffReturnsZeroBias` |
| Neutral preference → 0 bias | `mustHappen_neutralPreferenceIsZeroBias` |
| Anti-fixation dominates place | `mustHappen_antiFixationDominatesPlaceBias` |
| No veto / capped worst bias | `mustNotHappen_extremeDislikeCannotVetoAllRoutes` |
| Current chunk dislike ≠ lower EXPLORE utility | `mustNotHappen_currentChunkDislikeDoesNotLowerExploreUtility` |

## Minor notes (non-blocking)

| Item | Verdict |
| --- | --- |
| No-context planning `new PlaceOpinionMemory()` | 🟡 Ephemeral alloc on route plan when mob has no experience context; ranker could accept null/shared empty later |
| Intermediate-path dislike | 🟡 Not modeled — final destination chunk only |

## UNVERIFIED

- Mob with `NO_PROGRESS` memory actually walks away from disliked chunk on next expedition (RT-GAO-1).
- Descent expedition heading bias when multiple valid headings exist.

## Frontier

**RT-GAO-1** — not a blanket launch gate. Use:

```text
CODE + TESTS + MAIBS
        ↓
Can behavior be determined confidently?
       YES → ACCEPT STATIC
       NO  → Is uncertainty Minecraft/SPM/mod interaction?
              YES → TARGETED RUNTIME TEST
              NO  → Improve code/static analysis
```

**GAO static ACCEPT** (`CONFIRMED` — 556 tests + source): GAO-PARITY paths, GAO-5B route bias arithmetic, inversion removed from `ActivityUtilityScorer`, RET-GAO-1 park/rehydrate lifecycle.

**Legitimate `RUNTIME_QUESTION`s** (when filed): real `PathNavigation` outcomes, SPM `GoalSelector` contention, modpack perf/heap — not utility math.
