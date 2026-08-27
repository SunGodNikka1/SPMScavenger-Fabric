# Task 65 report: V4-D — VillageIntent ownership and lifecycle

## Status

**DONE / STATIC+PACKAGE ACCEPTED**

V4-D implements D-VR-091's transient reason/destination commitment and no execution behavior.
Runtime movement/resumption is not claimed and remains V4-E/G scope.

## Files created or changed

- `src/main/java/com/noobk/spmscavenger/WorkDemandPolicy.java`
- `src/main/java/com/noobk/spmscavenger/SpmScavenger.java`
- `src/main/java/com/noobk/spmscavenger/village/intent/VillageIntent.java`
- `src/main/java/com/noobk/spmscavenger/village/intent/VillageIntentFacts.java`
- `src/main/java/com/noobk/spmscavenger/village/intent/VillageIntentEvaluation.java`
- `src/main/java/com/noobk/spmscavenger/village/intent/VillageIntentPolicy.java`
- `src/main/java/com/noobk/spmscavenger/village/intent/VillageIntentRegistry.java`
- `src/test/java/com/noobk/spmscavenger/village/intent/VillageIntentPolicyTest.java`
- `src/test/java/com/noobk/spmscavenger/village/intent/VillageIntentRegistryTest.java`
- `src/test/java/com/noobk/spmscavenger/village/intent/VillageIntentBoundaryTest.java`
- retained V4-B/C boundary tests (advanced their ban from V4-D to V4-E+)
- `plans/RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md`
- `.superpowers/sdd/progress.md`
- `.superpowers/sdd/task-65-brief.md`
- `.superpowers/sdd/task-65-report.md`

## Summary

`MaterialDemandIdentity` now canonically identifies a live route by material plus consumer while
excluding the changing deficit. `VillageIntentPolicy` opens REQUIRED_TRADE only from a live demand,
canonical `INFEASIBLE` existing-route status, a V4-C selection, and current hard-compatible memory;
it then revalidates legitimacy without retaining rank, Opinion, capability evidence, market state,
or permission. `VillageIntentRegistry` holds at most one value-only intent per loaded mob UUID,
physically removes invalid entries, resists retargeting, and is released on unload/death/server stop.

## Commands and exact results

Working directory for every command:
`D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

### RED

```text
.\gradlew.bat test --tests "com.noobk.spmscavenger.village.intent.*"
```

**FAILED as expected** at `compileTestJava`: 78 missing-symbol errors for the absent
`MaterialDemand.identity()`, `VillageIntent*` policy, evaluation and registry APIs.

### Focused GREEN + retained boundaries

```text
.\gradlew.bat test --tests "com.noobk.spmscavenger.village.intent.*" \
  --tests "com.noobk.spmscavenger.village.routing.*" \
  --tests "com.noobk.spmscavenger.opinion.SettlementOpinionBoundaryTest"
```

**BUILD SUCCESSFUL** in 15s.

### Final dual build and audits

```text
.\gradlew.bat clean build
```

**BUILD SUCCESSFUL** in 39s; all 16 tasks executed. Production tests: **1,692**, zero
failures/errors. Validation tests: **57**, zero failures/errors. `auditProductionJar` and
`auditValidationJar` passed. One pre-existing deprecation warning remains in
`EpisodeRetentionTest`.

### Artifact/package evidence

- `build/libs/spmscavenger-1.11.0.jar`
  - SHA-256: `FF405C71CAED85ED2A3471434ECE7CED0AD5C5714E979232AF4169B82962328A`
  - upstream Trade Everything classes: **0**
  - validation namespace entries: **0**
  - packaged V4-D classes: `VillageIntent`, `VillageIntentFacts`,
    `VillageIntentEvaluation`, `VillageIntentPolicy`, `VillageIntentRegistry`
- `build/libs/spmscavenger-1.11.0-validation.jar`
  - SHA-256: `BB02D551AEED4733434A3756401A9B520091C4056477A7C347CD656CC5F546A0`
- `git diff --check`: exit 0 (line-ending notices only).

## Source evidence

- `CODE_CONFIRMED` — canonical deficit-independent identity:
  `WorkDemandPolicy.java:24-42`.
- `CODE_CONFIRMED` — minimal intent payload and kind invariant:
  `VillageIntent.java:15-41`.
- `CODE_CONFIRMED` — live opening/revalidation against canonical route status:
  `VillageIntentPolicy.java:16-57`.
- `CODE_CONFIRMED` — one-per-UUID retention, physical invalidation and explicit lifecycle:
  `VillageIntentRegistry.java:18-86`, `SpmScavenger.java:148,212,230`.
- `CONFIRMED` — pinned host artifact exists at
  `D:\Apps\Minecraft Port\Projects\references\artifacts\playermob-fabric-0.89.0+1.21.1.jar`;
  no host API or Goal was changed by this task.
- `UNVERIFIED / NOT APPLICABLE YET` — visible commute, combat resume and arrival behavior; V4-D has
  no Goal or executor, and Minecraft launch was explicitly forbidden.

## Self-review

- [x] Stable identity is material + consumer, never deficit.
- [x] Ranking alone cannot open intent; route must be positively `INFEASIBLE`.
- [x] Same deficit-changing demand survives; material/consumer changes close.
- [x] Demand disappearance, justification loss and destination invalidation physically close.
- [x] Interruption retains existence but denies current admissibility; fresh facts are required.
- [x] Existing intent cannot be stolen by a new ranking; invalidation and reselection are separate.
- [x] Capability expiry/Opinion/ranking are absent from revalidation.
- [x] No market object, cached authorization, path, Goal, SavedData or failure producer is stored.
- [x] One entry per UUID; unload/death/server-stop production cleanup is wired.
- [x] RETURN_HOME/VISIT_SETTLEMENT are structural kinds only; no producer added.
- [x] No V4-E, first-home, runtime witness, launch or commit.

## Semantic-drift / MAIBS review

`PLANNED -> IMPLEMENTED -> PREDICTED RUNTIME` is exact for V4-D. It cannot move or interact because
no Goal consumes the registry. A future consumer sees a stable destination through ordinary
scheduler interruption, but an interruption evaluation reports non-admissible and a subsequent
resume must pass current demand/route/destination facts. Entity unload intentionally discards the
execution commitment; persistent village/trader evidence remains the source for recomputation.

## Concerns

- V4-E must structurally require `VillageIntentRegistry.revalidate` at every admission/resume
  boundary. Calling `current()` alone is diagnostic existence, not permission.
- Physical interruption/resumption and long-distance behavior remain **UNVERIFIED** until V4-E/G.
- No runtime retention/performance claim is made; static RET-1 shape is confirmed, while any heap or
  frequency claim would require the separately approved runtime proof class.

No Minecraft launch. No commit or push.
