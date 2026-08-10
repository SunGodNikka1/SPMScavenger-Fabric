# Task 36 Brief — GAO-5B (place opinion destination ranking)

**Gate:** GAO-5B  
**Depends on:** Task 35 RET-GAO-1  
**Out of scope:** DescentHeadingPolicy wiring, MiningDirector admission, runtime launch

## Problem (GAO-5A defect)

Current-chunk `placeAnchor` in `ActivityUtilityScorer.scoreExplore` inverted avoidance semantics:
a disliked chunk lowered EXPLORE utility while the mob still stood on it.

## Design

- **`PlaceOpinionRouteRanker`** — soft additive bias (±15) on expedition route score from final
  destination chunk preference.
- **`ExploringGoal.createExpedition`** — apply route bias when ranking `RouteCandidate`s (non-forced
  paths only; descent macro-heading unchanged).
- **Remove** current-position PLACE term from EXPLORE utility; remove `placeAnchor` from director
  scoring input.

## Constraints

- PLACE is soft preference only — no veto on exploration, mandatory mining, or descent admission.
- `MAX_ROUTE_BIAS` (15) < anti-fixation penalties (e.g. -100 recent expedition region).
- Opinion-off parity: ranker returns 0.

## Acceptance

- Negative destination bias lets neutral destination win at equal base score.
- Current-chunk dislike does not change EXPLORE utility.
- Opinion-off returns zero route bias.
- Extreme dislike capped at `-MAX_ROUTE_BIAS`.

## Verification

```text
.\gradlew.bat clean build
```
