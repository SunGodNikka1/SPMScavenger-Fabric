# Task 59 report: V3-G integration and closure (preparation phase)

**Status:** `DONE` (preparation) — runtime VR-T3 campaign remains `UNVERIFIED`  
**Slice:** V3-G prep — static baseline, runtime matrix, `spm_vr` manifest, closure map  
**Brief:** `.superpowers/sdd/task-59-brief.md` v1  
**Target:** `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

## Summary

Task-59 preparation delivered the integration artifacts required before any Minecraft runtime
campaign: approved VR-T3 runtime matrix pinning `playermob` 0.89.0, `spm_vr` preset manifest,
TEST_MATRIX closure map, and a clean static/build baseline. No production Java changed. Runtime
execution is **not authorized**.

## Files created or changed

| Path | Action |
| --- | --- |
| `.superpowers/sdd/task-59-brief.md` | created |
| `.superpowers/sdd/task-59-report.md` | created |
| `docs/porting/VR-T3-RUNTIME-MATRIX.md` | created |
| `test-datapacks/phase-village-raid/PRESET-MANIFEST.md` | created |
| `docs/porting/TEST_MATRIX.md` | V3-G closure map section added |

## Commands and exact results

| Command | CWD | Result |
| --- | --- | --- |
| `.\gradlew.bat compileJava` | `Projects/SPMScavenger-1.21.1-Fabric` | **CONFIRMED** — BUILD SUCCESSFUL |
| `.\gradlew.bat test --rerun-tasks` | same | **CONFIRMED** — **1589 tests**, 0 failures (`build/reports/tests/test/index.html`) |

## Self-review (mapped to brief)

| Deliverable | Status |
| --- | --- |
| D1 static/build baseline | **DONE** — 1589 tests, 0 failures |
| D2 VR-T3 runtime matrix | **DONE** — `docs/porting/VR-T3-RUNTIME-MATRIX.md` |
| D3 `spm_vr` preset manifest | **DONE** — manifest only; datapack bodies deferred pre-launch |
| D4 TEST_MATRIX closure map | **DONE** |
| D5 report | **DONE** (this file) |
| VR-T3f excluded | **CONFIRMED** — matrix + manifest |
| Task-58 untouched | **CONFIRMED** — no Java edits |
| Runtime launch | **NOT DONE** — correctly withheld |

## Evidence labels

| Claim | Label | Evidence |
| --- | --- | --- |
| 1589 tests pass | `CONFIRMED` | `build/reports/tests/test/index.html` counter |
| Runtime matrix pins 0.89.0 | `CONFIRMED` | `VR-T3-RUNTIME-MATRIX.md` mod set table |
| VR-T3 runtime closure | `UNVERIFIED` | no Minecraft session |
| `spm_vr` datapack functions | `UNVERIFIED` | manifest only — bodies not implemented |

## Concerns / next checkpoint

| Item | Notes |
| --- | --- |
| **Runtime campaign** | User must separately authorize `runClient` / batched VR-T3 |
| **`spm_vr` datapack bodies** | Implement preset functions before launch (structural precedent: `spm_shelter`) |
| **SPM 0.89 reference checkout** | `SocialPlayerMobs-v0.89.0` path not verified in workspace — matrix pins artifact version; pin jar hash at launch |
| **P5 vs P4 torch contention** | remains `RUNTIME_QUESTION` — document during VR-T3d if observed |

## Frontier after

**Preparation DONE.** User checkpoint: review matrix + manifest; authorize Minecraft campaign when
ready. V3 phase runtime closure remains open until applicable VR-T3 rows record runtime evidence.

## Resume addendum — 2026-08-23

The earlier manifest-only concern is **SUPERSEDED** by the implemented executable datapack and its
14-test structural/semantic guard suite. Task-59 resume repinned the environment from the obsolete
pre-W2.4 hash to clean production JAR SHA-256
`5EF3639FF03DA20191C3C83BCF662461DB081A8ABFA00E2CEBDC8C93A8B49BF9`. Focused fixture validation
passed 14/14 and `.\gradlew.bat clean build` passed 1614 tests with zero failures/errors/skips.
There were no V3 production Java changes. Runtime closure remains `UNVERIFIED` and unlaunched.

## Runtime-preparation addendum — 2026-08-23

Task-59 now has an operator-complete launch packet. A temporary one-shot V3 witness command closes
the D-VR-084 hidden-claim evidence gap without retaining state or acquiring production authority;
the datapack now exposes help, honest entity/schedule-only cleanup, a standalone runbook, and a
13-row evidence worksheet. RED/GREEN witness tests and the focused fixture suite pass. Full
`clean build` passes **1618 tests / 0 failures/errors/skips**. Instrumented JAR SHA-256:
`063585AA5782B576E5CCFDAD5739B133842173EF17232CEBF7B4DA95B01AA628`.

Runtime remains `UNVERIFIED`. The temporary command/test/registration must be removed after accepted
evidence, followed by a clean production rebuild. No Minecraft launch occurred.

## Gate-0 completion addendum — 2026-08-23

The same one-shot witness now closes the preflight observability gap: it reports the remembered
current settlement identity, all population facts, completeness/freshness, and a fail-safe Gate-0
verdict. A new non-creating/non-writing facts accessor was required because the existing production
`peek` may persist freshness into its cache; production consumers were not changed. Focused tests
pass 12/12 and `clean build` passes **1625 tests / 0 failures/errors/skips**. New instrumented JAR
SHA-256: `1185EBCF362CB5409FC0D61DC4A49EE00016385FAF402C0244C8DC9DF7CD22C6`.

No production semantic or Minecraft runtime claim is upgraded by these static results.

## Gate-0 R1 / shelter-release addendum — 2026-08-23

User-supplied runtime output confirms Gate 0 at game tick 1240: remembered overworld settlement
`-20,-60,15`, `2` adult villagers, `3` usable HOME capacity, `2` claimed, `1` free, and
`COMPLETE + FRESH`. The same snapshot showed `SeekShelterGoal:SHELTER_HOLD` retaining mandatory
authority at reported day time 912. Gate 0 is therefore `RUNTIME_CONFIRMED`, while no V3 work-row
window started.

The temporary one-shot inspector now emits a separate pure row-precondition verdict from
`ServerLevel.isDay()` and the existing activity observation: `READY`, `WAITING_DAYTIME`, or exact
`FIXTURE_INCOMPLETE`. It neither mutates time nor stops/clears a Goal or authority. Focused V3/cache
tests pass **15/15**; `clean build` passes **1628 tests / 0 failures/errors/skips**. Instrumented JAR
SHA-256: `766F099FBC004A007A615DD044A9243901F8FBF621A66EF3A8BBC33C6A3CCA40`.

Package audit: nine expected V3 witness/assessment/precondition entries, zero upstream Trade
Everything classes, five project-owned TE compatibility classes, and zero removed V2 witness
entries. The replacement artifact has not been launched and requires separate approval.

## Automated campaign-controller addendum — 2026-08-23

The operator-intensive manual row loop is superseded by a temporary singleton controller under
`/spmscavenger debug v3`: `run <preset>`, `status`, `report`, `stop`, and `reset`. It executes only
allowlisted `spm_vr` fixtures in the Overworld, removes unrelated PlayerMobs only before opening,
waits for natural Gate0, performs a declared pre-window day/weather transition, waits for genuine
`SHELTER_HOLD` release, records the exact opening tick, and captures bounded transition/terminal
evidence through the row's minimum clock. `OBSERVATION_COMPLETE` is explicitly not product PASS.

To support standing away, the controller force-loads only arena chunks it newly acquires and
releases those exact chunks on all normal terminal/lifecycle paths. It detaches the active session
before release so chunk-unload callbacks cannot re-enter completion. Foreign forced chunks are
never removed. Hard-process-crash cleanup remains unavailable and is documented as a disposable-
world limitation.

The VR-T3b zombie trigger was corrected from preset-load-relative scheduling to exact
window-open+120 execution; the helper still only summons the declared hostile and never forces
target/aggro. Scheduled village bed-claim and interrupt helpers now anchor to the tagged fixture
subject instead of inheriting server-spawn execution position.

| Verification | Result |
| --- | --- |
| Initial RED | focused test compilation failed with missing controller/helper symbols |
| Focused controller/witness/datapack suite | **33/33 PASS** |
| Three authority/steering/evidence-manufacture probes | **NOT FOUND** |
| `clean build` | **PASS — 1643 tests, 0 failures/errors/skips** |
| Remapped JAR | `build/libs/spmscavenger-1.11.0.jar` (1,206,432 bytes) |
| SHA-256 | `94534E28364ACF9E6C7FAFB1940D2F3AEF3F90581103DEED58D416A2DAA06F3C` |
| Temporary V3 debug classes | **28** total; **21** campaign/snapshot/evidence entries |
| Packaged upstream Trade Everything classes | **0** |
| Project-owned TE compatibility classes | **5** |
| Removed V2 witness entries | **0** |

No Minecraft process was launched. Static orchestration, tests, artifact identity, and packaging are
`CONFIRMED`; live controller behavior and every VR-T3 row remain `UNVERIFIED` pending separate
approval for this exact artifact.

## Automated startup containment repair — 2026-08-24

The first live `run mandatory_blocks_village_work` failed before session/report creation. Pinned
Minecraft 1.21.1 source identifies the cause: raw Brigadier invocation of the `function` command
calls `CustomCommandExecutor.CommandAdapter.run()`, which intentionally throws
`UnsupportedOperationException("This function should not run")`. Direct `/function` succeeds
because it uses Minecraft's execution queue.

The controller now establishes `PREPARING` immediately, executes the loaded `CommandFunction`
through `ServerFunctionManager` on the next server tick, and only then discovers the subject and
acquires resources. Any non-fatal failure records stage/class/message/root cause, logs its stack,
terminates `FIXTURE_FAILURE`, releases owned chunks, and leaves `status`/`report` plus reset origin.
JVM-fatal errors remain uncontained.

Focused tests: **35/35 PASS**. Full `clean build`: **1645 tests**, zero failures/errors/skips.
JAR: `build/libs/spmscavenger-1.11.0.jar`, SHA-256
`732BBB65C5604D617A9FC84120F7878622C3018DA3B6F84035DFBFEB9A532ECC`.
No Minecraft relaunch occurred; live repair remains `UNVERIFIED`.

## Gate-0 bootstrap sequencing repair — 2026-08-24

The first startup-contained VR-T3j campaign reached readable facts too early and terminated
`FIXTURE_FAILURE: claimedHomeCount < 2` with `window=NOT_OPEN->124`. This attempt is discarded as
`FIXTURE_INCOMPLETE / PREMATURE_GATE0_ADJUDICATION`; VR-T3j was not started and no V3 runtime defect
is assigned. Code inspection confirmed the controller moved directly from scenario preparation to
numeric Gate-0 adjudication, despite the fixture's existing 120-tick natural HOME bootstrap contract.

The controller now records `bootstrapStartTick` immediately after successful scenario-function
execution, enters explicit `WAITING_GATE0_BOOTSTRAP`, and prevents threshold results from advancing
or terminating the fixture before elapsed tick 120. At/after the boundary, PASS and readable
numeric failure retain their existing meanings; incomplete facts retain bounded waiting under the
unchanged 2400-tick overall timeout. `V3Gate0Assessment` and Tasks 52–58 were not modified.

Regression controls pin COMPLETE/FRESH facts with claimed HOME counts 0 at tick 20, 1 at ticks
60/119, 2 at tick 120, and 1 at tick 120. Focused V3/datapack suite: **48/48 PASS**. Full
`clean build`: **1649 tests**, zero failures/errors/skips. JAR:
`build/libs/spmscavenger-1.11.0.jar` (1,218,391 bytes), SHA-256
`7BD5205B1CFF85608BA53C9C446BC40D00A20E2FBDDCF3FA68A2798CF1CA8577`.
Package audit: **36** temporary V3 debug entries (**4** bootstrap-gate entries), **0** upstream
Trade Everything classes, **5** project-owned TE compatibility classes, and **0** removed V2
witness entries.
No Minecraft relaunch occurred; the repaired temporal behavior remains `UNVERIFIED`.

Controller backlog remains separate: VR-T3k must observe first-commit versus second-mob
invalidate/abandon/reacquire rather than one aggregate replant, and VR-T3m must prove temporal
cycles rather than distinct replanted positions. Neither observation model changed in this repair.

## Post-open spatial observation repair — 2026-08-24

Code review confirmed that the controller reused its 32-block fixture `arena` as an immediate
post-open subject terminal. That was a false-negative mechanism for VR-T3j: normal exploration
could leave the evidence core while mandatory ownership remained absent, only for the controller to
rename the authority observation `INCOMPLETE`.

The temporary controller now separates a 32-block `SCENARIO_CORE`, a 192-block protected
`OBSERVATION_ENVELOPE`, a 192–224 escape margin, and an `ESCAPED` state. The envelope derives from
the core radius plus `ExploringGoal`'s pinned 150-block expedition route cap, rounded to 192; the
extra core width gives geometry-dependent rows a 224-block final relevance boundary. T3j is exempt
from spatial termination because its fixed 1000-tick evidence is authority state rather than a
crop/container interaction. Core chunks remain the only forced chunks.

First departure records `SUBJECT_LEFT_CORE`, horizontal distance, pending claim, and active
classes. Reports retain first exit, maximum distance, final zone, and whether any pending claim was
observed after open. Unrelated PlayerMobs are checked across the observation envelope every 20
ticks. No navigation, teleport, leash, freeze, or path correction was added.

Focused V3/datapack suite: **52/52 PASS**. Full `clean build`: **1653 tests**, zero
failures/errors/skips. JAR: `build/libs/spmscavenger-1.11.0.jar` (1,224,603 bytes), SHA-256
`626FB459EBCA54A866E50A535399E04D5E07B1CEBA8D5DD0A5F4103DA582F599`.
Package audit: **39** temporary V3 debug entries (**3** spatial-policy entries), **0** upstream
Trade Everything classes, **5** project-owned TE compatibility classes, and **0** removed V2
witness entries. No Minecraft relaunch occurred; live spatial evidence remains `UNVERIFIED`.

## Causal isolation + T3k/T3m temporal witness repair — 2026-08-24

Artifact `626FB459...2F599` is superseded and **DO NOT RUNTIME TEST**. The accepted 32/192/224
subject geometry remains unchanged. Post-open outer-envelope presence is now bounded telemetry;
terminal interference requires scenario-core entry, <=16-block subject proximity, or an observed
targeting relationship. The exact pre-window boundary scan uses `FORCED_BOUNDARY`, bypasses the
20-tick periodic throttle, and failure-closes before `WINDOW_OPEN` with a diagnosable report.

The T3k row now passively observes two declared PlayerMobs running harvest episodes against the
single crop, followed by one age-0 commit and stale contender release. T3m now requires two
mature→age-0 transitions on the same cell; distinct one-time replants are a negative control. The
temporary reflection probe only reads installed goal running/phase/target state, invokes no Goal
method, and degrades to unavailable evidence if reflection/accessor reads fail.

Full `clean build`: **1660 tests**, zero failures/errors/skips. JAR:
`build/libs/spmscavenger-1.11.0.jar` (1,236,700 bytes), SHA-256
`2D2E64924465D18D1590AB87A63B8F8AE650FB8911BE5351C8613F9BE8DF8D7D`.
Package audit: **46** temporary V3 debug class files, **0** upstream Trade Everything classes,
**5** project-owned TE compatibility classes. Diff audit: **0** changed production Java files
outside the temporary `debug/V3*` harness; Tasks 52–58 are untouched. Three negative probes found
no Goal invocation, post-open movement/entity mutation, or production mandatory-authority publish
calls in the new witness seams. No Minecraft launch or commit occurred; runtime behavior remains
`UNVERIFIED` pending explicit authorization for this exact artifact.

## Final static T3k/T3m correction — 2026-08-24

Artifact `2D2E6492...8DF8D7D` is superseded for consolidated campaign use. Its accepted startup,
Gate-0, spatial, contamination, and final-isolation behavior was not changed.

T3k now requires all UUIDs in the observed pre-commit single-crop contention set to leave that
stale episode after replant. The successful winner stopping while the loser remains is an isolated
negative control and no longer starts stabilization. T3m now treats the first replant as baseline;
only two later same-cell maturity→replant transitions start its +400 clock. Fixture-provided opening
maturity and a single natural regrowth cycle remain insufficient.

Pinned source inspection also exposes a matrix contradiction: default random ticking and the dry
fixture put one full wheat cycle on an expected scale near 100,000 ticks, while the controller caps
T3m at 4000. The harness keeps the exact two-cycle definition and will report honest `INCOMPLETE`;
it does not accelerate growth or reinterpret the opening crop.

Isolated state-machine tests: **9/9 PASS**. Focused retained harness: **19/19 PASS**. Full `clean
build`: **1661 tests**, zero failures/errors/skips. JAR:
`build/libs/spmscavenger-1.11.0.jar` (1,236,566 bytes), SHA-256
`38C3E33276BFC7234CEBB44C99A559AF6FAD4D7A093D6FB8703E4716D58588FC`.
No Minecraft launch or commit occurred; runtime remains `UNVERIFIED`.

## Gate-0 dynamic HOME occupancy repair — 2026-08-24

Live artifact `38C3E332...8588FC` reached exactly bootstrap+120 with one naturally claimed HOME and
terminated before opening. This falsifies the controller's fixed-deadline semantic assumption;
VR-T3j was not started and no Tasks 52–58 production defect is assigned.

The controller now distinguishes structural impossibility from dynamic HOME occupancy. After the
unchanged minimum grace, adults<2 or usable homes<3 is immediate `FIXTURE_FAILURE`.
`claimedHomeCount<2` remains waiting under the original fixture-start-based 2400-tick deadline.
Claims>=2/free>=1 passes; claims>=2/free<1 fails structurally. HOME claim deficit persisting through
the original deadline becomes `FIXTURE_INCOMPLETE` with exact final counts. Unreadable facts at the
deadline retain ordinary `INCOMPLETE`.

No POI ticket, HOME/Brain/sleep state, refresh, teleport, Goal, spatial, T3k, T3m, or Tasks 52–58
behavior changed. Focused Gate-0/controller tests: **15/15 PASS**. Temporary debug suite: **46/46
PASS**. Full `clean build`: **1663 tests**, zero failures/errors/skips. JAR:
`build/libs/spmscavenger-1.11.0.jar` (1,241,777 bytes), SHA-256
`ED07F88D06AE46645AE827DF1C1B31726C687D114501A1444C8676A3F36F56E3`.
No Minecraft launch or commit occurred; the repair remains runtime `UNVERIFIED`.

## T3j mandatory-route fixture repair — 2026-08-24

Live artifact `ED07F88D...F56E3` completed a valid 1000-tick VR-T3j window (282→1282) with Gate 0,
shelter release, isolation, and spatial tracking intact, but `pendingClaimObservedAfterOpen=false`.
The crop remained mature while the subject eventually entered `EXPEDITION`. This is
`FIXTURE_INCOMPLETE`, not V3 PASS/FAIL: world oak logs did not instantiate a modeled demand.

Both T3j and D-VR-084 now use the same fixture-only inventory frontier: stone pickaxe, diamond axe,
two sticks, eight torches, crop seeds, and no iron/raw iron. Their datapacks provide a clear natural
corridor and three exposed iron ores. Before opening, `V3MandatoryRouteReadiness` consumes the real
`WorkDemandPolicy`, `GatherIntentPolicy`, `GatherRoutePrecursor`, candidate/protection/tool policy,
and a non-steering path probe. It emits `MANDATORY_ROUTE_READY` only for the exact iron-pick
consumer with `RAW_IRON`, `nextStep=NOTHING`, scan coverage, and an eligible reachable target.
Failure is `FIXTURE_INCOMPLETE` before the clock starts.

The fixture/witness contains no claim publication/removal, Gather Goal invocation, admission
mutation, target steering, or result award. Tasks 52–58, Gate 0, spatial isolation, T3k, and T3m are
unchanged. Focused debug/datapack tests: **68/68 PASS**. Full `clean build`: **1669 tests**, zero
failures/errors/skips. JAR: `build/libs/spmscavenger-1.11.0.jar` (1,254,383 bytes), SHA-256
`8C2DBBA590B55AB55E80A96A84C88C28583F8700A151D90AD3EEFEA4A6CA69F2`. Packaged upstream Trade
Everything classes: **0**. No Minecraft launch or commit occurred; corrected runtime behavior is
`UNVERIFIED`.

## Live-claim stopping-rule repair — 2026-08-24

**Status:** `DONE_WITH_CONCERNS` — static repair and package gate complete; official T3j/D-VR-084
runtime closure remains `UNVERIFIED` because this turn forbade Minecraft launch.

### Files changed

- `src/main/java/com/noobk/spmscavenger/debug/V3MandatoryRouteReadiness.java`
- `src/test/java/com/noobk/spmscavenger/debug/V3MandatoryRouteReadinessTest.java`
- `.superpowers/sdd/task-59-brief.md`
- `.superpowers/sdd/task-59-report.md`
- `plans/RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md`
- `docs/porting/VR-T3-RUNTIME-MATRIX.md`
- `docs/porting/VR-T3-RUNTIME-ENVIRONMENT.md`
- `test-datapacks/phase-village-raid/README.md`
- `test-datapacks/phase-village-raid/PRESET-MANIFEST.md`
- `.superpowers/sdd/progress.md`

### Delivered semantics

`V3MandatoryRouteReadiness` now re-proves the exact public production frontier and reads
`MandatoryOwnershipRegistry.liveClaim(subjectUUID, now)`. A matching non-expired
`spmscavenger:iron_pickaxe_upgrade` claim returns `READY source=LIVE_CLAIM` before fixture target/path
inspection. Wrong, expired, absent, or policy-drifted claims cannot do so; the existing passive
geometry check remains the fallback. Reports retain consumer, generation, open/expiry/current ticks,
and diagnostic route identity without depending on its concrete class.

### Commands and exact results

Working directory: `D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`.

- RED: `.\gradlew.bat test --tests com.noobk.spmscavenger.debug.V3MandatoryRouteReadinessTest`
  failed at `compileTestJava` with 17 missing-symbol/signature errors for `Source`, `source()`,
  `claimEvidence()`, and the claim-aware `evaluatePolicy` overload.
- GREEN focused controller/readiness suite: build successful.
- GREEN `.\gradlew.bat test --tests "com.noobk.spmscavenger.debug.*"`: **57 tests**, zero
  failures/errors/skips.
- `.\gradlew.bat clean build`: **BUILD SUCCESSFUL**, **1675 tests**, zero failures/errors/skips;
  one pre-existing deprecation warning in `EpisodeRetentionTest`.
- Package audit: `build/libs/spmscavenger-1.11.0.jar`, **1,260,259 bytes**, SHA-256
  `BDAA788CAE2126FDE46F858A4076DF69FF0590F151CD3A6B88A32A580A0B2BDC`; packaged upstream Trade
  Everything classes **0**; temporary V3 class entries **59** as expected for the removal-bound
  Task-59 campaign artifact.

### Source evidence and self-review

- `CONFIRMED`: `MandatoryOwnershipClaim.expired(now)` is `now >= expiresAt`; registry `liveClaim`
  deletes and returns empty for expiry. The controller uses that public registry seam.
- `CONFIRMED`: matching false/true geometry, wrong consumer, expired/no claim fallback, policy drift,
  metadata, no private route-class policy, and claim-before-geometry ordering are regression-covered.
- `CONFIRMED`: only temporary `debug/V3MandatoryRouteReadiness.java` changed under production Java;
  Tasks 52–58 production files are untouched.
- Negative source probes: claim publication/release/removal in the fixture/readiness pair —
  **NOT FOUND**; `GatherResourcesGoal`/`canUse()`/concrete route-identity type policy — **NOT FOUND**;
  movement/teleport/navigation steering — **NOT FOUND**.
- `RUNTIME_CORROBORATED`: three external live reports observed real mandatory ownership and Village
  Work denial; the third official window did not open. Local logs do not contain that external run.
- `UNVERIFIED`: replacement-artifact `source=LIVE_CLAIM -> WINDOW_OPEN` and official row result.

No Minecraft launch or commit occurred.

## VR-T3j runtime acceptance — 2026-08-24

**Status:** VR-T3j `RUNTIME PASS`; Task-59 remains `DONE_WITH_CONCERNS` at campaign level because the
reduced representative runtime set defined by `D-VR-088` is incomplete.

User-adjudicated evidence from exact artifact `BDAA788C...A0B2BDC` records a matching `LIVE_CLAIM`
opening, a full 1000-tick official window, autonomous pig-combat preemption, Village Work remaining
blocked throughout combat, Gather resumption, running `SCAVENGE_WORK` ownership, and no Village Work
displacement. This confirms the must-happen and must-not-happen outcomes for VR-T3j, including its
interruption/resume path.

The external runtime's raw `latest.log` is not present in the local workspace. Evidence provenance is
therefore recorded as the user's runtime report rather than falsely citing local logs. The statement
that D-VR-084 remained an independent runtime row is superseded by the closure-minimization decision
below; no production change, build, Minecraft relaunch, or commit was performed while recording this
result.

## Closure-minimization decision — 2026-08-26

**Status:** `LOCKED / DOCUMENTATION SYNCHRONIZED` (`D-VR-088`). Task-59 remains
`DONE_WITH_CONCERNS` until the reduced representative runtime set completes.

The user accepted the evidence review and replaced the blanket standalone-runtime interpretation
with proof matched to each invariant's failure mode. This explicitly restores the original Task-52
batched-witness decision and supersedes later wording that made D-VR-084 independent.

| Obligation | Final disposition | Evidence / next proof |
| --- | --- | --- |
| D-VR-084 | **CLOSURE SATISFIED COMPOSITIONALLY** | accepted T3j live claim/preemption/resume/deny sequence + task-52 scenarios, temporal simulations, and mutation controls for expiry/no-renewal/release/lifecycle |
| VR-T3c | **CLOSURE SATISFIED — STATIC/TRANSACTION** | `CropHarvestTransactionBehaviorTest` preflight, escrow rollback, no-loot-on-failure, exact replacement, and conservation |
| VR-T3h | **CLOSURE SATISFIED — STATIC-SUBSUMED** | task-54 UNKNOWN/tri-state evidence; T3g remains the representative live host deny-hook proof |
| VR-T3m | **STATIC SUBSTITUTION** | representative T3a live crop episode + deterministic F8 banking/conservation; impossible two-unaccelerated-generation/4000t obligation removed |
| VR-T3j | **RUNTIME PASS / NO RERUN** | `BDAA788C...A0B2BDC` accepted evidence |
| VR-T3l | **RUNTIME REQUIRED / FIXTURE REDESIGN REQUIRED** | minimal passive SPM 0.89 `HarvestCropsGoal` optional-mixin attachment/refusal witness with wilderness/unresolved fail-open control |

The existing T3l fixture is invalid because nearby oak logs do not establish mandatory Gather
authority. Its replacement must not attempt to repair that premise. The witness owns only fixture
setup and passive observation: production host scheduling must reach `HarvestCropsGoal`; the attached
hook must refuse a positively managed crop; a wilderness or unresolved-domain control must remain
fail-open. No fake claim, Goal invocation, steering, target injection, or awarded product verdict is
permitted.

**Remaining runtime-required rows:** `VR-T3a`, `VR-T3b`, `VR-T3d`, `VR-T3e`, `VR-T3g`, `VR-T3i`,
`VR-T3k`, and redesigned `VR-T3l`.

**Strongest alternative rejected:** static-only closure would minimize operator work further but
cannot prove optional host-mixin attachment, GoalSelector/navigation behavior, handoff, world
mutation, or multi-mob contention in SPM 0.89. The opposite all-standalone policy was also rejected
because it duplicates mutation-tested deterministic seams and turns fixture defects into artificial
runtime work.

**Acceptance:** must observe every unique integration seam once under its exact conditions. Must not
generalize a runtime observation to an unobserved seam, relabel build/static evidence as runtime, or
rerun T3j/D-VR-084 merely for checklist symmetry.

**This documentation pass changed only:** the RFC, runtime matrix, this report, runtime evidence
ledger, test matrix, and progress ledger. It did not modify production code, tests, mixins, Gradle,
configs, or datapack fixtures; it did not build, launch Minecraft, commit, or push.

## Certification-tooling disposition — D-VR-096 (2026-08-26)

**Status:** architecture **LOCKED**; extraction **IMPLEMENTED + STATIC/PACKAGE ACCEPTED** by Task 60. Task-59 certification remains
partially complete/open and its remaining rows are not awarded or deferred by this decision.

The temporary controller, Gate-0/bootstrap/contamination machinery, scenario commands, temporal
witnesses, validation mixins/accessors, and fixtures will move to a separately packaged validation
mod. Dependency direction is strict:

```text
spmscavenger_validation -> spmscavenger -> Minecraft / SPM
```

Validation source uses `com.noobk.spmscavenger.validation.*`, never production package names, and may
consume only public passive production truth surfaces. Production must not depend on validation or
widen mutation/authority APIs for certification. General Debug and legitimate non-creating reads
remain production; scenario construction/adjudication remains validation-only.

The normal clean build must compile/test/package/audit both the production and validation artifacts.
The production JAR must contain zero Task-59 controller/scenario/Gate0/contamination/temporal-witness
classes or validation resources. The installable validation JAR must declare mod id
`spmscavenger_validation` and depend on `spmscavenger`.

Task 60 moved all 18 Task-59 production classes and their 15 tests into the dedicated validation
source/test surfaces, added the `spmscavenger_validation` initializer/metadata, and removed every
controller command/tick/unload/death/shutdown hook from `SpmScavenger`. Production retains only the
one-shot passive `/spmscavenger debug inspect <mob>` readout and non-creating truth peeks.

`gradlew.bat clean build` passed with **1,624 production tests + 57 validation tests**, zero
failures/errors/skips. The production JAR contains zero validation-namespace or legacy `debug/V3*`
classes and zero `spm_vr` scenario resources; the validation JAR contains 60 validation-namespace
classes, 24 scenario function resources, and zero production-class duplicates. Packaged upstream
Trade Everything classes remain zero.

Artifacts:

- production `build/libs/spmscavenger-1.11.0.jar` — SHA-256
  `4A742B531C0518CA06E53045D7EB571FB7E50443BCC1C74CE289E42E2B1A99D0`;
- validation `build/libs/spmscavenger-1.11.0-validation.jar` — SHA-256
  `BB02D551AEED4733434A3756401A9B520091C4056477A7C347CD656CC5F546A0`.

**Next:** V4-R0 may begin independently. Remaining VR-T3 certification resumes only from an exact
approved production/validation artifact pair. Sidecar loading/runtime command registration remains
**UNVERIFIED** because P0 did not authorize a Minecraft launch.
