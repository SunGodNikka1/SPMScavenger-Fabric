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
