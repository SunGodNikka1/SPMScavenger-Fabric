# Task 59 brief: V3-G integration and closure

**Slice:** integrate shipped V3-A…F static slices, produce the **approved VR-T3 runtime matrix** and
**`spm_vr` preset manifest**, run static/build regression, and map existing unit/scenario evidence to
applicable closure rows. Task-59 **closes** canonical V3 when runtime evidence is later captured —
it does **not** add new village-work features, mixins, or `MandatoryOwnership` publishers.

**Authorization gates (LOCKED sequence):**

| Gate | Status | User phrase |
| --- | --- | --- |
| **Preparation / integration** | **AUTHORIZED** (2026-08-22) | **Start Task-59 / V3-G preparation** |
| **Minecraft runtime campaign** | **NOT AUTHORIZED** | separate explicit launch approval per AGENTS.md Gate 6 |

**Brief revision history:**

- v1.4 — automated campaign execution controller (2026-08-23): single-command preset execution,
  bounded contamination isolation, automatic Gate-0/day/shelter gating, passive transition capture,
  exact row clocks, terminal evidence, and fixture-only verdicts
- v1.3 — settlement-row shelter-release precondition (2026-08-23): keep Gate 0 independent;
  require no live `SHELTER_HOLD` before a settlement-dependent row starts its evidence window
- v1.2 — Gate-0 witness completion (2026-08-23): remembered settlement + population facts +
  explicit PASS/FIXTURE_FAILURE/INCOMPLETE via non-creating, non-writing reads only
- v1.1 — runtime-preparation witness addendum (2026-08-23): one-shot passive V3 inspector,
  datapack help/cleanup, evidence worksheet, artifact reapproval; still no Minecraft launch
- v1 — initial brief (User authorized Task-59 prep 2026-08-22)

**Target:** `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

**Source reference (SPM host — canonical baseline):**
`d:\Apps\Minecraft Port\Projects\references\SocialPlayerMobs-v0.89.0` when present; else pin artifact
`playermob-fabric-0.89.0+1.21.1.jar` at runtime-matrix record time. Historical audits against
v0.86.0 (`4b80b5e849`) remain **provenance only**.

**RFC:** `plans/RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md` — V3-G, VR-T3a–m (VR-T3f **non-applicable**),
phase-closure rule, host-baseline section, D-VR-084 runtime witness.

**Depends on (CLOSED — DO NOT REOPEN):**

| Task | Slice | Task-59 use |
| --- | --- | --- |
| **task-52** | `MandatoryOwnership` | runtime witness row only — **no new publisher** |
| **task-53** | V3-A profile + admission | VR-T3j static map + runtime witness |
| **task-54** | V3-B storage guard | VR-T3g–i |
| **task-55** | V3-C crop episode | VR-T3a–c/k/l/m |
| **task-56** | V3-D1 population/HOME facts | VR-T3e foundation |
| **task-57** | V3-E population food | VR-T3e/j |
| **task-58** | V3-F compost | VR-T3d/j |

**Forbidden without separate authorization:**

- Reopen or modify task-52…58 production semantics (unless runtime falsifies a locked invariant —
  then report only, do not silently repair)
- Minecraft `runClient` / `runServer` / batched VR-T3 execution
- New V3 executor goals, broad V3-D2, bone-meal extraction, `VillageWorkSelector`,
  `MandatoryOwnership` publisher
- Commit · push

---

## Architecture lock — inherit without reopening

| Lock | Authority | Task-59 posture |
| --- | --- | --- |
| **Applicable rows only** | RFC phase closure 2026-08-22 | VR-T3a–e,g–m + D-VR-084 witness; **VR-T3f OUT** |
| **No subset closure** | RFC | replant + one chest row does not close V3 |
| **Host baseline** | RFC 2026-08-22 | canonical **`playermob` 0.89.0** in runtime matrix |
| **Target-lifecycle caution** | RFC host-delta | witness acquire/loss edges in VR-T3b/c/j — **not** a repair slice |
| **Task-58** | progress ledger | **DO NOT REOPEN** |
| **Gate 0** | RFC | **not repeated** — static/build + matrix + presets only |

---

## Deliverables (preparation phase — this authorization)

### D1 — Static/build baseline

Record current full-suite result after task-58 closure:

```powershell
cd "d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric"
.\gradlew.bat compileJava
.\gradlew.bat test
```

**Must happen:** compile success; test count ≥ 1589; 0 failures.  
**Must not happen:** claiming runtime VR-T3 closure from build success alone (AV-1).

### D2 — Approved VR-T3 runtime matrix

Create `docs/porting/VR-T3-RUNTIME-MATRIX.md` containing:

1. **Mod set** — `playermob` **0.89.0** + `spmscavenger` (current build) + Fabric API only for
   uncontaminated V3 proof; optional rows explicitly marked.
2. **Applicable scenario table** — every **applicable** VR-T3 row with must/must-not, preset id,
   static evidence pointer, runtime status (`UNVERIFIED` until launch).
3. **Explicit exclusions** — VR-T3f (V3-D2 deferred); no broad workstation framework.
4. **D-VR-084 witness** — mandatory-claim vs village-work arbitration live probe (deferred execution).
5. **SPM 0.89 compatibility cautions** — target acquire/loss social semantics (witness only).
6. **Launch gate** — matrix alone does not authorize Minecraft; User must approve campaign separately.
7. **Evidence capture recipe** — log paths, readout commands, minimum observation window per row.

### D3 — `spm_vr` preset manifest

Create `test-datapacks/phase-village-raid/PRESET-MANIFEST.md` (namespace **`spm_vr`**):

- One preset id per applicable VR-T3 cluster (see manifest file).
- Each preset: purpose, VR-T3 ids, world setup summary, mob profile (`VILLAGE_ALLY`), inventory
  seeds, success/falsifier observables.
- **Datapack bodies are NOT required in prep phase** — manifest + matrix are the deliverable.
  Physical preset functions may be added in a later sub-slice before runtime launch.

### D4 — Static evidence closure map

Extend `docs/porting/TEST_MATRIX.md` with a **V3-G closure map** section:

- Row per applicable VR-T3 id → static test classes / task reports already covering must/must-not.
- Label each `CONFIRMED` / `INFERRED` / `UNVERIFIED` per AV-1.
- Runtime column remains `UNVERIFIED` until campaign.

### D5 — Report

Write `.superpowers/sdd/task-59-report.md` with status `DONE` (prep) or `DONE_WITH_CONCERNS` if
baseline regresses. Runtime closure remains `UNVERIFIED`.

---

## Verification commands

| Step | Command | CWD |
| --- | --- | --- |
| Compile | `.\gradlew.bat compileJava` | project root |
| Full test | `.\gradlew.bat test` | project root |
| Village tests (spot) | `.\gradlew.bat test --tests "*village*"` | project root |

---

## Acceptance (preparation phase)

**Must happen:**

- D1–D5 artifacts exist and cross-reference each other.
- Runtime matrix pins `playermob` 0.89.0.
- VR-T3f explicitly excluded from closure requirements.
- No production Java changes unless baseline regression forces a minimal fix (report as concern).

**Must not happen:**

- Task-58 code paths reopened for “cleanup”
- Runtime rows marked `CONFIRMED` without log/runtime evidence
- VR-T3f pulled into V3-G closure via matrix wording
- Minecraft launch

---

## Report path

`.superpowers/sdd/task-59-report.md`

---

## v1.1 runtime-preparation witness addendum — 2026-08-23

### Evidence gap

Most VR-T3 outcomes are world-visible, but D-VR-084 specifically requires proof of a live pending
`MandatoryOwnershipClaim`. The existing client activity inspector reports running goals; it does not
expose that registry claim. Inferring pending authority from movement or an active Gather goal would
not satisfy AV-1.

### Authorized preparation surface

This v1.1 addendum narrowly supersedes the earlier preparation-phase prohibition on production-tree
Java changes for this temporary diagnostic only. It does not authorize any V3 behavior change.

Add one temporary operator-only snapshot command:

```text
/spmscavenger debug v3 inspect <mob>
```

It may read the target UUID/profile, current running-goal classifications, live pending claim,
shared `MandatoryOwnership` permission, and `VillageWorkAdmission` result. It must not retain a
session or entity/world reference and must not call Goal admission/continuation, navigation,
inventory mutation, claim publish/release, profile/storage mutation, or an executor.

Also add `spm_vr:help`, explicit entity/schedule cleanup, a standalone operator README, and a
per-row evidence worksheet. These are preparation surfaces only; production behavior remains the
sole authority.

### Removal manifest

After accepted runtime evidence, remove:

- `V3RuntimeWitnessCommands` and its tests;
- its registration call in `SpmScavenger`;
- any wording that requires the temporary command for future normal play.

Preserve the runtime evidence, datapack/runbook, and production V3 architecture.

### v1.1 acceptance

**Must happen:** the inspector reports a pending claim and the exact shared admission outcome from
production-owned state; the datapack exposes help and bounded fixture cleanup; every row has a place
for tick-bounded evidence.

**Must not happen:** the inspector publishes/releases authority, changes profile/storage/inventory,
forces a Goal, calls `canUse`/`canContinueToUse`, navigates, or launches Minecraft.

Changing this temporary diagnostic surface changes the remapped JAR hash; return a clean build,
test count, package audit, path, and SHA-256 for separate launch approval.

### v1.2 Gate-0 completion

Extend the same snapshot with the subject's remembered in-bounds settlement identity and the
production `VillageWorkFacts` population fields. Classification is exact:

- no remembered current settlement or no facts → `Gate0=INCOMPLETE`;
- facts not `COMPLETE + FRESH` → `Gate0=INCOMPLETE`;
- readable facts with `adultVillagerCount >= 2`, `claimedHomeCount >= 2`, and
  `currentFreeHomeCapacity >= 1` → `Gate0=PASS`;
- readable facts that fail any numeric fixture threshold → `Gate0=FIXTURE_FAILURE`.

The witness must use `VillageMemorySavedData.peekInDimension(...).peek(...)` and a non-creating,
non-writing Village Work facts accessor. Existing `VillageWorkFactsService.peek(...)` is not safe
for this purpose because its cache read may persist a freshness transition; add a read-only accessor
without changing production consumers or returned production semantics.

**Forbidden:** `refreshNow`, refresh scheduling, cache creation/write/invalidation, POI acquisition,
HOME-ticket changes, Brain/sleep NBT, village-memory allocation/recording, or manufactured evidence.
Existing authority/activity reporting remains unchanged.

### v1.3 settlement-row readiness

The supplied Gate-0 runtime snapshot is accepted as `RUNTIME_CONFIRMED`: remembered settlement
facts were `COMPLETE + FRESH` and met the numeric thresholds. It also showed a distinct fixture
condition at reported day time 912: `SeekShelterGoal:SHELTER_HOLD` still owned mandatory authority.
No settlement-dependent V3 work row may begin its evidence window while that activity remains live.

Extend the existing one-shot readout with an independent row-precondition verdict:

- no `SHELTER_HOLD` → `RowPrecondition=READY`;
- `SHELTER_HOLD` before daytime → `RowPrecondition=WAITING_DAYTIME`;
- `SHELTER_HOLD` during daytime → `RowPrecondition=FIXTURE_INCOMPLETE`.

Gate 0 and row readiness are deliberately separate. A row may start only when its required Gate-0
verdict is `PASS` **and** `RowPrecondition=READY`. `FIXTURE_INCOMPLETE` is fixture evidence, not a
V3 behavior failure, and must not start the row clock.

The inspector may read `ServerLevel.isDay()` and the already-produced activity observation only.
It must not set time, stop/restart a Goal, clear authority, navigate, refresh settlement state, or
otherwise manufacture readiness.

**Must happen:** a daytime snapshot with live `SHELTER_HOLD` reports exact
`RowPrecondition=FIXTURE_INCOMPLETE`; a snapshot without the hold reports `READY`.

**Must not happen:** Gate 0 is downgraded because of shelter state; the witness changes time or
Goal/authority state; an evidence window begins from `WAITING_DAYTIME` or `FIXTURE_INCOMPLETE`.

---

## v1.4 automated campaign execution controller

### Authorization and boundary

The User authorizes a temporary Task-59 controller under the existing operator command tree:

```text
/spmscavenger debug v3 run <preset>
/spmscavenger debug v3 status
/spmscavenger debug v3 report
/spmscavenger debug v3 stop
/spmscavenger debug v3 reset
```

The controller is fixture/evidence infrastructure. Tasks 52–58 and their Goals, admission,
mandatory claims, transactions, priorities, and execution semantics remain untouched. No Minecraft
launch, commit, or push is authorized.

### Evidence baseline (`CODE_CONFIRMED`)

- Thirteen scenario functions exist under
  `test-datapacks/phase-village-raid/data/spm_vr/function/scenario/`.
- `setup_village_stub` creates the bell, three beds, and two adult villagers, sets night, and only
  nudges villagers while vanilla AI acquires HOME tickets.
- `V3RuntimeWitnessCommands` already reads shared activity, mandatory claim, Village Work admission,
  remembered settlement, population facts, daylight, and shelter state without manufacturing them.
- `SpmScavenger` already owns server-tick, entity-unload/death, and server-stop lifecycle hooks.
- Pinned host artifact: `D:\Apps\Minecraft Port\Projects\references\artifacts\playermob-fabric-0.89.0+1.21.1.jar`.

### Selected architecture

One global, bounded `V3RuntimeCampaignController` owns at most one active session and one immutable
last report. It retains identifiers/value snapshots only—never a live Mob, Level, Goal, inventory,
Villager, or block-entity reference.

```text
operator run <preset>
  -> execute declared spm_vr scenario at command origin
  -> locate exact tagged subject
  -> remove non-fixture PlayerMobs inside 32-block arena (pre-window only)
  -> acquire only previously-unforced arena chunks so the operator may stand away
  -> natural Gate 0 observation
  -> after PASS, fixture advances world to day (declared pre-window input)
  -> wait for production SeekShelterGoal to release genuinely
  -> pre-open contamination check
  -> RowPrecondition READY
  -> record exact opening tick
  -> production AI runs without steering
  -> transition-only passive snapshots + scenario clock
  -> terminal evidence + observation disposition
```

After the window opens, the controller never teleports, removes, steers, retargets, heals, equips,
or otherwise changes the subject or declared scenario entities. An unrelated PlayerMob entering the
arena after opening terminates observation as `EXTERNAL_INTERFERENCE`; it is not deleted. Declared
zombie combat remains legitimate production input.

### Alternatives and decision

| Option | Benefit | Failure mode / cost | Disposition |
| --- | --- | --- | --- |
| Datapack schedules only | Little Java state | Cannot read hidden claims/facts reliably; scheduled context can detach triggers from the row clock | Rejected |
| Java controller directly starts/stops Goals or injects claims | Deterministic-looking runs | Manufactures the behavior under test and invalidates evidence | Rejected |
| Hybrid fixture controller + passive shared snapshot | Exact timing and hidden-state evidence while production retains behavior | Temporary tick/lifecycle surface requiring strict RET-1 cleanup | **Selected** |

Strongest objection: advancing time after Gate 0 changes a global world input. Waiting naturally is
safer for a shared world but costs about 5,000 ticks from the fixture's 18,000 start to dawn and
reintroduces operator/runtime delay. The selected controller changes time only before the row opens,
logs the exact mutation, and is restricted to a disposable `spm_vr` world. Switch to natural waiting
if runtime shows the day transition itself invalidates settlement facts or subject state.

Second option for unattended execution was to leave chunks player-loaded. That avoids persistent
vanilla force-load state but fails the user's stand-away requirement and can silently pause the
observation clock. The selected controller records only chunks whose `setChunkForced(true)` call
actually changed state and releases those exact chunks on every normal terminal/lifecycle path; it
never removes pre-existing foreign forced chunks. A JVM/process crash cannot execute that cleanup,
so the fixture remains restricted to a disposable backed-up world and the runbook requires forced-
chunk inspection before reuse.

### Scenario clocks

| Preset | Window completion clock | Bounded incomplete timeout |
| --- | --- | --- |
| `crop_managed_single` | first observed replant at target + 200t | 2400t |
| `crop_interrupt_combat` | controller invokes declared zombie helper at open+120t; observed combat release + 600t | 2400t |
| `crop_replant_failure` | 800t unchanged observation | 800t |
| `compost_seed_surplus` | first observed seed debit + 400t | 2400t |
| `population_food_deficit` | 1200t | 1200t |
| storage `public/unknown/granted` | 800t | 800t |
| `mandatory_blocks_village_work` | 1000t | 1000t |
| `crop_multi_mob` | first observed replant + 200t, with both declared mobs retained in snapshots | 2400t |
| `crop_hungry_veto` | 800t | 800t |
| `crop_multi_cycle` | two distinct observed replant transitions + 400t | 4000t |
| `mandatory_ownership_witness` | 1000t | 1000t |

Completion means `OBSERVATION_COMPLETE`, not product PASS. If a required transition never appears,
the controller reports `INCOMPLETE` with the last observed state.

### State, evidence, and lifecycle

Allowed fixture/runtime dispositions are `PREPARING`, `WAITING_GATE0`, `WAITING_DAYTIME`,
`WAITING_SHELTER_RELEASE`, `READY`, `OBSERVING`, `OBSERVATION_COMPLETE`, `INCOMPLETE`,
`FIXTURE_INCOMPLETE`, `FIXTURE_FAILURE`, `EXTERNAL_INTERFERENCE`, and `ABORTED`. The controller never emits a V3
behavioral PASS/FAIL.

Transition snapshots retain: tick, position, running activity classes, combat target, pending claim,
mandatory/Village Work result, Gate 0, row precondition, relevant block states, bounded fixture
entity identities/positions, and bounded inventory summaries. Log only phase/evidence transitions,
opening, midpoint, declared trigger, terminal, and final disposition under
`[spmscavenger/v3-campaign]`.

RET-1: active session key = singleton; bound = one. Last report bound = one. Active state is released
on completion, timeout, stop/reset, subject unload/death, dimension loss, or server stop. `reset`
may invoke existing fixture cleanup; server stop clears both slots. A new `run` replaces only a
terminal report, never another active session. Arena chunk ownership is a bounded 5×5 maximum in
the current 32-block fixture radius; only successfully newly-forced chunks join the release list.

### Behavioral Prediction (MAIBS-1)

| Layer | Prediction | Confidence |
| --- | --- | --- |
| Intended | One command prepares and observes a complete row without operator polling | `UNVERIFIED` until runtime |
| Mechanism | Preset mutates declared fixture inputs; controller gates and observes; production Goals path/interact | `CODE_CONFIRMED` after build/tests |
| Visible sequence | Night bootstrap → villagers claim beds → Gate 0 → day → shelter releases → exact row clock → report | `GAME_MECHANICS_INFERRED` until runtime |
| Interruption | Declared zombie may trigger ordinary combat; undeclared PlayerMob contamination ends evidence without suppression | `UNVERIFIED` until runtime |
| Termination | Fixed/transition-relative window ends, or bounded timeout/lifecycle/interference produces non-product disposition | `CODE_CONFIRMED` after tests |

Goal interaction: `SeekShelterGoal` owns the pre-window night hold; mandatory Gather remains
production-owned; V3 Goals at priorities 4/5 remain unforced; host combat can interrupt normally;
the controller owns no Goal flags and therefore cannot enter GoalSelector arbitration.

Predicted weird behaviors:

1. Villagers never acquire both HOME tickets → `FIXTURE_FAILURE`/`INCOMPLETE`, not product failure
   (`RUNTIME_QUESTION`).
2. A friendly unrelated PlayerMob wanders into the arena after open → observation ends
   `EXTERNAL_INTERFERENCE` (`ACCEPTABLE_STEPPING_STONE`; preserves uncontaminated evidence).
3. Production never reaches a transition-relative terminal (path failure/starvation) → bounded
   `INCOMPLETE` with terminal snapshot (`RUNTIME_QUESTION`, not silently extended forever).
4. Declared zombie targets another entity rather than the subject → VR-T3b times out `INCOMPLETE`
   (`RUNTIME_QUESTION`; controller must not force aggro).

MAIBS disposition: **PASS — BEHAVIORALLY_PLAUSIBLE for temporary fixture orchestration**. Runtime
behavior remains `UNVERIFIED` and the exact campaign run is the falsifying experiment.

### Acceptance

**Must happen:** one run command executes an allowlisted preset, opens only after Gate0/day/shelter
release, records the exact opening tick, observes the declared minimum clock, and produces a compact
report with raw evidence and no product verdict.

**Must not happen:** Goal invocation/steering, claim/admission manufacture, HOME/Brain/sleep writes,
post-open subject teleport/removal, expected-result awards, combat suppression, unbounded history,
or a Minecraft launch during implementation.

### Removal manifest

Remove with the Task-59 witness after accepted runtime evidence:

- `V3RuntimeCampaignController` and controller-only scenario/progress/evidence helpers;
- controller tests;
- `run/status/report/stop/reset` command branches;
- server tick, unload/death, and server-stop controller hooks;
- controller-only datapack trigger changes and documentation.

Preserve production Tasks 52–58, accepted runtime evidence, matrix history, and clean rebuild gates.

---

## v1.5 startup failure containment repair

### Runtime evidence and scope

The first live `run mandatory_blocks_village_work` returned Minecraft's generic unexpected-error
surface; immediate `status` reported no active campaign or report. `CODE_CONFIRMED` source shape:
the controller cleared `lastReport`, executed the nested scenario through raw Brigadier, discovered
the subject, and only then constructed `Session`; only `CommandSyntaxException` was caught.

Pinned Minecraft 1.21.1 sources confirm the root cause. `FunctionCommand` registers a
`CustomCommandExecutor.CommandAdapter`; that adapter's ordinary Brigadier `run()` throws exact
`UnsupportedOperationException("This function should not run")`. The controller called
`server.getCommands().getDispatcher().execute("function ...")`, bypassing Minecraft's execution
queue, while direct `/function` correctly uses `Commands.performCommand`/`ExecutionContext`.
Evidence is pinned in the Loom 1.21.1 sources JAR at
`net/minecraft/commands/execution/CustomCommandExecutor.java`,
`net/minecraft/server/commands/FunctionCommand.java`, and
`net/minecraft/commands/Commands.java` (the project's `.gradle/loom-cache/minecraftMaven/...-sources.jar`).
The separately observed ally-storage and managed-crop warm-up diagnostics remain
independent Task-59 compatibility evidence; they are not attributed to this startup exception.

### Selected boundary

Create a nullable-subject `PREPARING` session before datapack execution. Execute the loaded
`CommandFunction` through `ServerFunctionManager.execute` from the following server tick, outside
the operator command's active execution context; this lets the function queue drain synchronously
before subject discovery. One controller-owned startup guard covers scenario execution, subject discovery, chunk acquisition, contamination
cleanup, and activation. It contains any non-fatal `Throwable`, logs the full stack trace under the
campaign prefix, writes a concise `FIXTURE_FAILURE` report containing exception class/message,
detaches the active slot, and releases any acquired fixture chunks. `reset` remains available at the
recorded Overworld origin for partial tagged fixture cleanup. `VirtualMachineError`, `ThreadDeath`,
and `LinkageError` propagate rather than being disguised as fixture failures.

| Option | Benefit | Failure mode | Disposition |
| --- | --- | --- | --- |
| Keep raw Brigadier and catch its exception | Small change | permanently converts every 1.21 function start into fixture failure | Rejected |
| Call `Commands.performPrefixedCommand` synchronously inside `run` | Uses public command route | queues the function into the current context and returns before subject discovery | Rejected |
| Broad guard around the whole controller command tree | Maximum containment | can hide programmer/fatal failures in status/report/reset | Rejected |
| PREPARING session + bounded startup guard | Every startup stage is diagnosable and cleanup-aware; normal commands remain honest | requires nullable subject until discovery and explicit fatal classification | **Selected** |

### Behavioral Prediction (MAIBS-1)

| Layer | Predicted result | Confidence |
| --- | --- | --- |
| Intended | Operator receives `FIXTURE_FAILURE`, then `status`/`report` explain the failing stage | `CODE_CONFIRMED` after harness/build; live command `UNVERIFIED` |
| Fixture/world | Partial blocks/entities may remain, but exact tagged entity/schedule cleanup is available through `reset` | `CODE_CONFIRMED`; world-block rollback deliberately unavailable |
| Production AI | No Task 52–58 Goal, claim, admission, or behavior changes | `CODE_CONFIRMED` by diff/negative probes |
| Resource lifecycle | Any chunk tickets acquired before failure are released before the command returns | `CODE_CONFIRMED` by injected-failure harness |
| Next runtime | Function executes on the next server tick; any later startup failure names exact stage/class/root cause | `UNVERIFIED` until approved rerun |

Predicted weird cases: failure before subject discovery leaves tagged partial fixture state
(`ACCEPTABLE_STEPPING_STONE`, reset removes provable entities/schedules); failure while releasing a
chunk may leave a ticket needing disposable-world recovery (`RUNTIME_QUESTION`, log each release
failure); a JVM-fatal failure still reaches Minecraft/process handling (`ACCEPTABLE_STEPPING_STONE`,
required safety boundary).

**Must happen:** an injected unchecked fixture-executor failure cannot escape the startup guard;
the terminal state/report are `FIXTURE_FAILURE`, include class + concise message, and resource
release executes.

**Must not happen:** JVM-fatal failures are swallowed; `lastReport` disappears; reset loses the
fixture origin; the repair changes V3 production behavior or conflates warm-up diagnostics.

---

## v1.6 Gate-0 bootstrap sequencing repair

### Runtime evidence and correction

The first startup-contained `mandatory_blocks_village_work` run terminated at tick 124 with
`FIXTURE_FAILURE: claimedHomeCount < 2` before its evidence window opened. The run is discarded as
`FIXTURE_INCOMPLETE / PREMATURE_GATE0_ADJUDICATION`; VR-T3j did not start and this is not evidence
of a V3 production defect. `CODE_CONFIRMED`: after successful scenario execution the controller
entered `WAITING_GATE0`, and its next tick immediately treated the already-readable intermediate
`V3Gate0Assessment` result as terminal. The documented fixture contract requires 120 ticks of
natural villager HOME bootstrap before numeric Gate-0 thresholds become adjudicable.

### Selected temporal boundary

Record `bootstrapStartTick` immediately after the scenario function returns successfully, then use
an explicit `WAITING_GATE0_BOOTSTRAP` state for the first 120 elapsed ticks. Passive snapshots may
be retained diagnostically during that state, but no numeric Gate-0 result may terminate or advance
the fixture. At elapsed tick 120, transition to `WAITING_GATE0` and consume the same passive
assessment normally. The existing overall 2400-tick Gate-0 timeout remains measured from the
controller session start; `V3Gate0Assessment` remains unchanged.

| Option | Benefit | Failure mode | Disposition |
| --- | --- | --- | --- |
| Ignore threshold failures inside `tickGate0` until 120 ticks | Small diff | status falsely claims adjudication has begun; timing ownership stays implicit | Rejected |
| Make `V3Gate0Assessment` time-aware | Centralized output | couples a pure facts classifier to one fixture's orchestration clock | Rejected |
| Explicit bootstrap state owned by controller | Visible sequencing, exact clock, assessment remains pure | one additional state/report field | **Selected** |

### Behavioral Prediction (MAIBS-1)

| Layer | Predicted result | Confidence |
| --- | --- | --- |
| `T+0` after scenario function | Controller records exact bootstrap start and begins passive waiting | `CODE_CONFIRMED` after tests; runtime `UNVERIFIED` |
| `T+20`, `T+60`, `T+119` | COMPLETE/FRESH facts with 0 or 1 claimed HOME remain diagnostic; campaign does not terminate | `CODE_CONFIRMED` after threshold harness |
| `T+120` with 2 claimed / 1 free HOME | Gate0 PASS advances to declared daytime and shelter-release waiting | `CODE_CONFIRMED` after harness; world behavior `UNVERIFIED` |
| `T+120` with 1 claimed HOME | Fixture terminates `FIXTURE_FAILURE` without opening VR-T3j | `CODE_CONFIRMED` after harness |
| Production AI | Villager HOME acquisition and Tasks 52–58 remain entirely production-owned | `CODE_CONFIRMED` by diff/negative probes |

Goal interaction: villagers naturally run vanilla HOME acquisition during the bootstrap; the
subject may remain under `SHELTER_HOLD`; no V3 work window opens and the controller owns no Goal
flags. Only after Gate0 PASS does the existing daytime/shelter-release sequence proceed.

Predicted weird cases: both villagers acquire HOME before tick 120 but the controller deliberately
waits out the full contract (`ACCEPTABLE_STEPPING_STONE`); one villager never claims HOME and becomes
a correct post-grace fixture failure (`RUNTIME_QUESTION`); facts stay unreadable through the grace
and then remain bounded by the existing overall timeout (`RUNTIME_QUESTION`).

**Must happen:** ticks 20/60/119 with readable under-threshold facts remain
`WAITING_GATE0_BOOTSTRAP`; tick 120 maps passing facts to PASS and under-threshold facts to
`FIXTURE_FAILURE`.

**Must not happen:** the controller manufactures HOME evidence, invokes refresh, changes
`V3Gate0Assessment`, resets the 2400-tick timeout, opens a row during bootstrap, or changes Tasks
52–58 production semantics. T3k/T3m observation-model repairs remain separate controller backlog.

---

## v1.7 post-open spatial observation repair

### Code evidence and boundary ownership

`CODE_CONFIRMED`: `tickObservation` currently uses the same 32×16×32 `arena(origin)` both for
scenario fixture discovery/contamination and as an immediate subject-leash boundary. Any post-open
core exit terminates `INCOMPLETE`. That can erase the T3j observation the row exists to capture:
discretionary exploration may move the subject away while mandatory ownership remains absent.

The production `ExploringGoal` has a maximum expedition route distance of 150 blocks and a maximum
expedition lifetime of 2400 ticks. T3j observes 1000 ticks. A subject that opens within the 32-block
scenario core can therefore remain relevant at roughly 182 horizontal blocks from the fixture
origin during one normal expedition. The temporary witness rounds that to a 192-block observation
envelope and uses a separate 224-block escape boundary (the envelope plus one 32-block core width).
These are witness interpretation bounds, not movement authority. Only scenario-core chunks remain
fixture-forced; the controller does not manufacture distant ticking terrain.

| Option | Benefit | Failure mode | Disposition |
| --- | --- | --- | --- |
| Keep 32-block immediate terminal | Cheapest observation | hides ordinary exploration/authority failure as fixture loss | Rejected |
| Remove all spatial bounds | Never masks movement | crop/storage rows can complete after losing all relation to their evidence geometry | Rejected |
| Core + 192 envelope + 224 escape | Preserves normal expedition evidence while bounding geometry-dependent rows | envelope scans need a cadence; second expedition may exceed the estimate | **Selected** |

T3j is authority-observation-only and therefore does not terminate merely for crossing the escape
boundary; it retains the full fixed clock while the subject remains a readable entity. Other rows
may become `INCOMPLETE` only beyond 224 blocks because their crop/storage evidence is anchored to
the scenario core.

### Behavioral Prediction (MAIBS-1)

| Checkpoint | Predicted observable result | Confidence |
| --- | --- | --- |
| WINDOW_OPEN | Core target evidence and subject authority snapshot are captured; no steering begins | `CODE_CONFIRMED` after harness |
| First horizontal distance >32 | `SUBJECT_LEFT_CORE` records distance, pending claim, and active classes; row continues | `CODE_CONFIRMED` after harness; runtime `UNVERIFIED` |
| Distance 33–192 | **SUPERSEDED by v1.8:** ordinary exploration remains within the subject observation envelope; outer unrelated PlayerMobs require causal evidence before becoming contamination | historical v1.7 statement |
| Distance 193–224 | Outer margin transition is recorded; row still continues | `CODE_CONFIRMED` after harness |
| Distance >224 | Geometry-dependent rows become spatially `INCOMPLETE`; T3j continues and preserves whether any pending claim appeared | `CODE_CONFIRMED` after harness |
| T3j +1000 | Terminal evidence distinguishes core departure with no ownership from a fixture disappearance | `UNVERIFIED` until approved runtime |

Goal interaction: `ExploringGoal` retains normal MOVE ownership and route planning; mandatory work
may interrupt it through production arbitration; the controller owns no Goal flags and never calls
navigation. Combat and declared scenario entities remain unsuppressed.

Predicted weird cases: a subject walks into an unloaded region and lifecycle observation ends
despite spatial permission (`RUNTIME_QUESTION`, report retained exit evidence); a second expedition
crosses 224 during T3j (`ACCEPTABLE_STEPPING_STONE`, T3j deliberately continues); an unrelated
PlayerMob crosses the 192 envelope between bounded scans (`RUNTIME_QUESTION`, superseded v1.7
terminal semantics; v1.8 logs non-causal presence); extreme vertical terrain changes Y without horizontal escape
(`ACCEPTABLE_STEPPING_STONE`, interpretation is intentionally horizontal).

**Must happen:** leaving the 32-block core emits `SUBJECT_LEFT_CORE` with distance, pending claim,
and active classes and does not terminate; **SUPERSEDED by v1.8:** 192-block presence alone is not
contamination; T3j continues beyond 224 while preserving whether a claim was ever observed.

**Must not happen:** the controller teleports, leashes, freezes, path-corrects, navigates, expands
production authority, uses the scenario core as a subject terminal, or silently lets a
geometry-dependent row remain meaningful after true escape.

---

## v1.8 causal isolation + T3k/T3m temporal witness repair

### Rejected artifact evidence

Artifact `626FB459...2F599` is **DO NOT RUNTIME TEST**. Its accepted subject spatial policy reused
the 192-block observation envelope as terminal contamination authority, so an unrelated idle
PlayerMob 180 blocks away could end the row. Its final pre-window cleanup also shared the 20-tick
periodic throttle and could skip the exact boundary scan immediately before `WINDOW_OPEN`.

The known crop witnesses were also incomplete: T3k completed from one aggregate replant without
observing two contenders or stale-target release; T3m completed from two distinct positions rather
than a repeated temporal cycle.

### Selected isolation model

| Layer | Meaning | Action |
| --- | --- | --- |
| Subject observation envelope (192) | valid Alex telemetry | never contamination permission by itself |
| Pre-window quarantine (192) | remove likely future unrelated PlayerMob intruders | fresh forced scan immediately before open; periodic scans may throttle |
| Post-open outer presence | unrelated PlayerMob in envelope but outside causal regions | bounded telemetry only |
| Scenario core (32) | target/evidence geometry | unrelated PlayerMob presence is terminal contamination |
| Subject proximity (16) | conservative immediate interaction neighborhood | unrelated PlayerMob presence is terminal contamination |
| Target/combat relationship | directly observable causal link | terminal contamination |

Sixteen blocks is a witness-conservative interaction neighborhood, not an ownership claim. Core-only
contamination would miss another PlayerMob following or fighting an exploring subject; envelope-wide
termination creates known false positives. Outer-presence telemetry is bounded to eight unique UUIDs
per singleton session and never mutates those mobs after opening.

The exact pre-open boundary uses `FORCED_BOUNDARY`, which bypasses the periodic cadence even if the
last scan was five ticks earlier. Any boundary-scan exception becomes diagnosable fixture failure;
opening cannot occur without a completed fresh scan.

### T3k and T3m observation model

The temporary witness reads existing running `VillageHarvestEpisodeGoal` instances and their
committed target positions without invoking Goal methods or changing them.

- **T3k:** require one snapshot with both declared PlayerMobs running harvest episodes committed to
  the single scenario crop; then observe its mature→age-0 atomic transition; then observe at least
  one of those prior commitments released. The controller reports observation completeness only;
  runtime review still determines whether evidence satisfies first-commit/second-revalidation.
- **T3m:** track each scenario crop through time. Two different cells replanted once are insufficient.
  Require a second mature→age-0 transition at a previously harvested cell, with observed maturity
  between the two transitions. No growth, maturity, inventory, or seed state is manufactured.

### Behavioral Prediction (MAIBS-1)

| Timeline | Predicted result | Confidence |
| --- | --- | --- |
| pre-open scan at T-5 | periodic quarantine may scan | `CODE_CONFIRMED` after harness |
| exact open boundary | forced fresh scan runs again and removes/refuses remaining unrelated mobs | `CODE_CONFIRMED` after harness |
| idle unrelated mob at origin+180 | `OUTER_PRESENCE` telemetry; row continues | `CODE_CONFIRMED` after policy test |
| unrelated mob in core / within 16 of subject / targeting subject | `EXTERNAL_INTERFERENCE` | `CODE_CONFIRMED` after policy test |
| T3k two commitments → one replant → stale release | terminal transition clock begins | `CODE_CONFIRMED` after temporal harness; runtime `UNVERIFIED` |
| T3m two different crops replant once | remains observing | `CODE_CONFIRMED` after negative control |
| T3m same crop matures and replants again | temporal-cycle stabilization begins | `CODE_CONFIRMED` after harness; runtime `UNVERIFIED` |

Goal interaction: crop goals retain MOVE/LOOK and all canUse/continuation/commit authority. The
witness only reads running state/target and block state. Combat remains production-owned and is used
as contamination evidence only when an actual target relationship is observable.

Predicted weird cases: reflection cannot read the temporary goal target and the row times out
`INCOMPLETE` (`RUNTIME_QUESTION`, never infer contention); a contaminant crosses the causal radius
between 20-tick samples (`RUNTIME_QUESTION`, target relationship may still expose it); natural wheat
does not complete a temporal repeat within 4000 ticks (`RUNTIME_QUESTION`, honest T3m incomplete);
an outer mob is socially irrelevant but logged once (`ACCEPTABLE_STEPPING_STONE`).

**Must happen:** forced boundary scan bypasses throttle; outer-envelope-only presence is telemetry;
causal core/proximity/target presence is terminal; T3k requires contention→commit→release; T3m
requires repeated same-cell maturity-separated transition.

**Must not happen:** post-open unrelated mobs are removed or steered; envelope presence alone ends a
row; opening follows a skipped boundary scan; distinct T3m cells masquerade as temporal cycles; crop
Goals/transactions are invoked or Tasks 52–58 production semantics change.

---

## v1.9 final static T3k/T3m correction

Artifact `2D2E6492...8DF8D7D` is **SUPERSEDED for the consolidated campaign**. Its startup, Gate-0,
32/192/224 spatial model, causal contamination, and forced final isolation are accepted unchanged.
Only its T3k/T3m completion semantics are reopened.

### Behavioral Prediction (MAIBS-1)

| Timeline | Required witness result | Failure prevented | Confidence |
| --- | --- | --- | --- |
| T3k: two actors target the one crop | remember the exact pre-commit contender UUID set | aggregate replant without contention | `CODE_CONFIRMED` after state test |
| T3k: crop becomes age 0; winner stops, loser remains | continue observing | winner completion misreported as loser revalidation | `CODE_CONFIRMED` after negative control |
| T3k: all original stale episode claims clear | begin +200 stabilization | stale loser remains committed | `CODE_CONFIRMED` after state test; runtime `UNVERIFIED` |
| T3m: initial age 7 becomes age 0 | establish per-cell baseline only; cycle count remains zero | fixture maturity counted as natural growth | `CODE_CONFIRMED` after negative control |
| T3m: baseline age 0 → observed age 7 → age 0 | complete cycle 1 only | off-by-one closure | `CODE_CONFIRMED` after state test |
| T3m: same cell again age 0 → age 7 → age 0 | complete cycle 2; begin +400 stabilization | two distinct cells or one natural regrowth close row | `CODE_CONFIRMED` after state test; runtime `UNVERIFIED` |

Goal ownership is unchanged: both PlayerMobs retain production harvest Goal admission, MOVE/LOOK,
transaction, stop, and reacquisition. The controller only samples running target state and blocks;
it never identifies a winner by inventing authority or invoking a Goal method.

### Options and decision

| Option | Benefit | Risk | Disposition |
| --- | --- | --- | --- |
| Identify the winner through new transaction instrumentation | loser-specific proof | adds observer calls to the accepted Tasks 52–58 production seam | rejected for this final static slice |
| Require all original single-crop contenders to clear | passive and cannot confuse winner completion with loser release | conservative false-negative if an actor instantly owns a new episode on the same cell | **selected**; age-0 target invalidity bounds that risk |
| Count opening maturity as cycle growth | fits 4000 ticks more easily | semantically false | rejected |
| Baseline first replant, then require two maturity-separated replants | exact matrix meaning | exposes the fixture/window contradiction | **selected** |

### 4000-tick contradiction (`SOURCE_CONFIRMED` mechanics; runtime timing `UNVERIFIED`)

Pinned Minecraft 1.21.1 `CropBlock.randomTick` advances one of seven wheat ages only when the crop
is randomly selected and then passes `nextInt((int)(25/f)+1)`. Default `randomTickSpeed` is 3; each
section selects only three of 4096 positions per tick. The fixture creates a dry one-row farmland
strip with no water. Its three adjacent crops have growth speed about 2.25–2.5, so one age advance
has an expected scale around 15,000 ticks and one seven-age growth cycle around 100,000 ticks. Even
hydrated geometry remains tens of thousands of ticks per cycle. Random ticks additionally require a
ticking chunk with a sufficiently near player; a forced chunk alone is not that proof.

Therefore the existing **4000-tick maximum cannot realistically witness two natural complete
cycles**. This is a **MATRIX/FIXTURE CONTRADICTION**, not permission to weaken cycle counting.
This slice keeps the bound so failure remains a truthful `INCOMPLETE`; selecting a statistically
justified longer window plus a natural-ticking/player-proximity contract is a separate matrix
decision before VR-T3m can be expected to close.

Predicted weird cases: T3k misses a simultaneous commitment between samples (`RUNTIME_QUESTION`,
honest timeout); T3k clears after both episodes stop but cannot label the winner
(`ACCEPTABLE_STEPPING_STONE`, all-clear is stronger than required); T3m times out at 4000 despite
correct production behavior (`ARCHITECTURE_DEFECT` in matrix/fixture timing, not production AI).

**Must happen:** winner-only release remains observing; all original commitments cleared after the
commit starts T3k stabilization; T3m needs baseline plus two same-cell natural maturity/replant
cycles.

**Must not happen:** opening age 7 counts as growth; one post-baseline cycle closes T3m; Tasks 52–58,
spatial/isolation behavior, crop growth, random tick speed, or production Goal authority changes.

### v1.9 static validation

- Isolated progress state machine: **9/9 PASS**, including winner-only-release and initial-maturity
  negative controls.
- Focused retained harness: **19/19 PASS**.
- `clean build`: **1661 tests / 0 failures/errors/skips**.
- Artifact: `build/libs/spmscavenger-1.11.0.jar`, SHA-256
  `38C3E33276BFC7234CEBB44C99A559AF6FAD4D7A093D6FB8703E4716D58588FC`.
- Minecraft runtime: **NOT RUN / UNVERIFIED**. Commit: **not created**.

---

## v1.10 Gate-0 dynamic HOME occupancy falsification

### Live evidence and scope

Artifact `38C3E332...8588FC` produced `bootstrapStart=1816`, adjudication at exactly `1936`,
`claimedHomeCount < 2`, and `exactOpeningTick=NOT_OPEN`. This is `RUNTIME_CONFIRMED` evidence that
120 ticks is not a sound terminal deadline for naturally acquired vanilla HOME tickets. The local
workspace `logs/latest.log` and `run/logs/latest.log` do not contain this external live session;
the exact controller report supplied by the user is the pinned runtime evidence for this repair.

The 120-tick boundary remains a minimum grace period. The existing 2400-tick overall deadline
remains anchored to the original fixture `startTick`; facts refreshes do not restart it.

### Decision and alternatives

| Option | Benefit | Failure mode | Disposition |
| --- | --- | --- | --- |
| Increase the fixed HOME grace beyond 120 | minimal code change | merely moves the guessed terminal boundary; natural acquisition can still be later | rejected |
| Treat every readable deficit as non-terminal until timeout | avoids premature failure | wastes the full timeout for impossible missing villagers/beds | rejected |
| Split structural impossibility from dynamic HOME occupancy | immediate failure for impossible fixture; natural claims retain bounded time | requires explicit evidence kind and timeout classification | **selected** |

After complete/fresh facts, `adultVillagerCount < 2` or `totalUsableHomeCapacity < 3` is structural
fixture failure. `claimedHomeCount < 2` is dynamic HOME occupancy and remains waiting. Once claims
reach two, `currentFreeHomeCapacity < 1` is structural failure; otherwise Gate 0 passes.

### Behavioral Prediction (MAIBS-1)

| Time | Facts | Controller result | Observable effect |
| --- | --- | --- | --- |
| bootstrap +0…119 | any readable deficit | `WAITING_GATE0_BOOTSTRAP` | no row opens or fails |
| +120 | adults=2, homes=3, claimed=1 | `WAITING_GATE0` | villagers continue natural POI behavior; no ticket is manufactured |
| later, before overall timeout | claimed=2, free=1 | Gate0 `PASS` | normal daytime/shelter-release sequence begins |
| overall timeout | adults=2, homes=3, claimed=1 | `FIXTURE_INCOMPLETE` with final counts | T3 row never starts; report identifies natural claim timeout |
| +120 complete/fresh adults<2 or homes<3 | structural impossibility | `FIXTURE_FAILURE` immediately | operator repairs fixture rather than waiting uselessly |

Production villager Brain/POI acquisition remains the only authority for HOME tickets. No Goal,
Brain, POI, sleep, teleport, refresh, or facts-cache mutation is introduced.

Predicted weird cases: one villager acquires HOME just after timeout (`RUNTIME_QUESTION`, bounded
false-negative remains possible); complete/fresh facts fluctuate stale and delay classification
(`ACCEPTABLE_STEPPING_STONE`, timeout remains anchored); an external villager owns the spare HOME so
claims reach two but free stays zero (`ARCHITECTURE_DEFECT` in fixture isolation, correctly reported
as structural failure).

**Must happen:** HOME1 at +120 continues waiting; HOME2/free1 before the original deadline passes;
HOME1 through the original deadline becomes `FIXTURE_INCOMPLETE` with exact final counts.

**Must not happen:** facts refresh resets the deadline; dynamic HOME1 becomes immediate
`FIXTURE_FAILURE`; the harness calls `PoiManager.take`, writes HOME/Brain/sleep state, repeatedly
teleports villagers, or changes Tasks 52–58, spatial isolation, T3k, or T3m.

### v1.10 static validation

- Focused Gate-0/controller tests: **15/15 PASS**.
- Full temporary debug suite: **46/46 PASS**.
- `clean build`: **1663 tests / 0 failures/errors/skips**.
- JAR: `build/libs/spmscavenger-1.11.0.jar`, SHA-256
  `ED07F88D06AE46645AE827DF1C1B31726C687D114501A1444C8676A3F36F56E3`.
- Runtime repair: **UNVERIFIED** pending separately authorized rerun. Commit: **not created**.

---

## v1.11 T3j mandatory-route fixture falsification

### Live evidence and corrected classification

Artifact `ED07F88D...F56E3` completed a valid VR-T3j window from tick 282 through 1282 with
`pendingClaimObservedAfterOpen=false`. Gate 0, shelter release, isolation, the 1000-tick clock, and
the subject spatial model all operated successfully; the subject later entered `EXPEDITION` and
left the 32-block core without the harness terminating the row. VR-T3j is therefore
`FIXTURE_INCOMPLETE`: the mandatory route prerequisite never existed, so no V3 product verdict is
assigned.

`CODE_CONFIRMED`: `WorkDemandPolicy.select(...)` reads backpack, hands, and config. It does not read
world blocks. Charcoal demand additionally requires `FurnacePolicy.needsCharcoal(...)`, whose final
gate is at least one surplus carried log. Placing oak logs in the world cannot instantiate either
fixture's promised mandatory publisher.

Negative probes:

1. world/level/block inputs in `WorkDemandPolicy` — **NOT FOUND**;
2. claim/authority injection in either affected mcfunction — **NOT FOUND**;
3. a pre-window `MANDATORY_ROUTE_READY` production-policy witness in Task-59 — **NOT FOUND**.

### Alternatives and decision

| Option | Benefit | Failure mode | Disposition |
| --- | --- | --- | --- |
| Publish a fixture claim or invoke Gather directly | deterministic visible denial | tests the harness's authority, not production ownership | rejected |
| Seed carried surplus logs for charcoal | small fixture delta | charcoal outranks progression, depends on torch/fuel reserve and may hand immediately to smelting | rejected for these rows |
| Seed an iron-pick consumer frontier plus exposed reachable iron ore | demand, precursor, intent, tool capability, and world target are independently provable through production policies | requires an explicit read-only readiness boundary and fixture-only inventory setup | **selected** |

The fixture-only starting state is: stone pickaxe, diamond axe, two sticks, eight torches, no iron/raw
iron, plus the scenario's crop seeds. Three exposed iron ore blocks stand on a clear natural strip
inside the production gather radius. This makes `iron_pickaxe_upgrade` active while satisfying the
stick/tool prerequisites and suppressing torch/log, diamond, and axe frontiers.

### Behavioral Prediction (MAIBS-1)

| Checkpoint | Production truth | Predicted observable result |
| --- | --- | --- |
| fixture setup/night bootstrap | iron-ingot demand exists; precursor is `RAW_IRON`; craft step is `NOTHING`; ore is intact | shelter may own activity, but the route remains derivable without a claim injection |
| daytime + shelter release | the same live demand/intent is re-read; at least one target is eligible and path-reachable | `MANDATORY_ROUTE_READY` is logged before the window opens |
| first Gather admission | production Gather publishes the pending route claim before its scan cadence gate | village work is denied by mandatory authority, or running `SCAVENGE_WORK` takes over after executor start |
| ore acquisition | Gather equips the real stone pick, approaches, and mines exposed ore | the claim may disappear when the executor starts; running mandatory work remains the authority evidence |
| route advances/completes | raw iron enters the backpack and later smelt/craft may own progression | crop work becomes eligible only after mandatory ownership genuinely clears |
| prolonged failure | no demand, wrong precursor, ready craft, missing tool, protected ore, or no path | terminate before `WINDOW_OPEN` as `FIXTURE_INCOMPLETE`, with the failed production fact |

Goal interaction: night `SHELTER_HOLD` legitimately preempts the route before the window;
`SOCIAL_REFLEX` may briefly deny village work but does not satisfy readiness; Gather's pending claim
or running `SCAVENGE_WORK` remains production-owned; `EXPEDITION` is permitted only if mandatory
ownership never materializes or later clears.

Predicted weird cases: configuration caps tools below iron (`RUNTIME_QUESTION`, fail readiness rather
than opening); an ore target becomes protected/unreachable after setup (`RUNTIME_QUESTION`, fail
readiness with target evidence); Gather starts between the readiness snapshot and window opening
(`ACCEPTABLE_STEPPING_STONE`, the demand/target remains live and running work is stronger evidence
than a pending claim); production completes all three ore acquisitions early in the window
(`ACCEPTABLE_STEPPING_STONE`, T3j is about ordering, not indefinite suppression).

**Must happen:** both affected presets create the same policy-proven iron demand; readiness proves
selected material, consumer, precursor, `nextStep=NOTHING`, scan coverage, a usable tool, an eligible
fixture target, and a reachable approach before opening.

**Must not happen:** the fixture publishes/removes a claim, invokes a Gather Goal method, mutates
Goal state, changes VillageWork admission, awards a result, or changes Tasks 52–58, Gate 0, spatial
isolation, T3k, or T3m.

**Falsifying runtime experiment:** rerun `mandatory_blocks_village_work`. If
`MANDATORY_ROUTE_READY` is present but neither a pending claim nor running Gather ownership appears
before discretionary expedition, the fixture correction is present and the failure moves to the
production publisher/admission boundary. Static/build success alone leaves that behavior
`UNVERIFIED`.

### v1.11 static validation and semantic-drift review

- Focused debug + datapack suite: **68/68 PASS**, including missing-tool and unreachable-target
  negative controls.
- `clean build`: **1669 tests / 0 failures/errors/skips**.
- JAR: `build/libs/spmscavenger-1.11.0.jar` (1,254,383 bytes), SHA-256
  `8C2DBBA590B55AB55E80A96A84C88C28583F8700A151D90AD3EEFEA4A6CA69F2`.
- Packaged upstream Trade Everything classes: **0**; obsolete TE witness classes: **0**.
- Production Java outside temporary `debug/V3*`: **0 changed files**. No Minecraft launch or
  commit occurred.

`PLANNED → IMPLEMENTED → PREDICTED RUNTIME`: the planned iron frontier is the exact implemented
fixture state; the readiness boundary is stricter than demand presence because it also requires an
eligible complete approach path. The expected next live report is
`MANDATORY_ROUTE_FIXTURE_PREPARED → MANDATORY_ROUTE_READY → WINDOW_OPEN`, followed by a production
pending claim or running `SCAVENGE_WORK` before discretionary expedition. That behavioral claim is
`UNVERIFIED` until the replacement artifact runs.

Separate scope warning: VR-T3l still contains the same world-log-as-demand assumption, but combines
it with an empty-backpack `wantsFood()` contract. It is not silently repaired by the T3j frontier
and remains fixture-unready pending its own compatible design.

---

## v1.12 T3j / D-VR-084 live-claim stopping-rule repair

### Runtime falsification and scope

Three separately originated runs of artifact `8C2DBBA5...A6CA69F2` reached Gate 0 PASS, daytime,
released `SHELTER_HOLD`, and the exact production-derived
`iron_pickaxe_upgrade -> iron_ingot -> RAW_IRON` frontier. In the latest supplied runtime report a
live production `MandatoryOwnershipClaim` already denied Village Work, but the temporary readiness
oracle refused to open because its duplicate target-geometry prediction said the fixture ore was
outside scan bounds. This is `RUNTIME_CORROBORATED`, not row closure: the official window never
opened.

This repair changes only the temporary Task-59 stopping rule. Tasks 52–58 and production ownership,
Gather, route, Village Work, Goal, and navigation semantics are frozen.

### Alternatives and selected authority order

| Option | Benefit | Strongest failure mode | Disposition |
| --- | --- | --- | --- |
| Keep geometry as mandatory even after publication | independently predicts a target | a second, drift-prone scan oracle can veto production's already-published responsibility | superseded by live evidence |
| Accept any live claim | smallest controller rule | a wrong consumer or stale claim can open the wrong row | rejected |
| Re-prove the current exact policy frontier, then accept a matching non-expired live claim; retain geometry only when no matching claim exists | production remains sole authority while impossible/no-claim fixtures still fail early | a claim can expire just before the read and fall back to geometry | **selected** |

The controller must call only
`MandatoryOwnershipRegistry.liveClaim(subjectUUID, currentTick)`. A matching claim is usable only
when its consumer is `spmscavenger:iron_pickaxe_upgrade`, it is not expired, and the current public
production policy still resolves the exact expected material and precursor with
`nextStep=NOTHING` and Gather intent coverage. Its concrete `routeIdentity` is diagnostic text only.
The result is `MANDATORY_ROUTE_READY source=LIVE_CLAIM`, and target geometry/path readiness is not
evaluated in that branch. A wrong, expired, or absent claim cannot satisfy the gate; ordinary passive
target readiness remains the fallback.

Claim evidence retains `consumerKey`, `generation`, `openedAt`, `expiresAt`, `currentTick`, and
diagnostic route identity. No fixture or witness API may publish/release ownership, inspect private
Goal state, call Goal methods, move/teleport/steer the subject, or assign the expected result.

### Behavioral Prediction (MAIBS-1 delta)

| Checkpoint | Evidence owner | Predicted visible/controller result |
| --- | --- | --- |
| daytime boundary, matching live claim | production registry + current demand/intent policy | row opens immediately as `source=LIVE_CLAIM`, even if the duplicate target oracle would reject geometry |
| matching claim released before the read | current policy + passive target fallback | geometry decides readiness; no historical claim is treated as live |
| wrong-consumer claim | current policy + passive target fallback | wrong claim is logged diagnostically but grants no readiness |
| after `WINDOW_OPEN` | production activity/claim observations only | controller observes normal Gather/village-work ordering; it never steers or prolongs ownership |
| T+1000 | row clock | terminal evidence can adjudicate the official matrix instead of a pre-window geometric false negative |

Goal interaction is unchanged: `SHELTER_HOLD` must release first; a pending live Gather claim blocks
Village Work through shared production authority; when Gather starts, running mandatory activity may
replace the pending claim; urgent/combat authority may still preempt naturally.

Predicted weird cases:

1. claim expires exactly at the opening read -> `RUNTIME_QUESTION`; expiry is authoritative and the
   safe fallback runs;
2. Gather changes the concrete route-identity class -> `ACCEPTABLE_STEPPING_STONE`; the field is
   logged but never policy-matched;
3. a wrong-consumer claim coexists with a geometrically valid exact frontier ->
   `ACCEPTABLE_STEPPING_STONE`; readiness may come only from `PASSIVE_FALLBACK`, never that claim.

**Must happen:** matching live claim + exact current frontier yields `READY source=LIVE_CLAIM` with
all requested metadata and without target geometry being required.

**Must not happen:** wrong/expired/absent claims create permission; concrete route-identity classes
become policy; the debug controller invokes Goal/navigation behavior or mutates production state.

**Required RED/GREEN regressions:** matching live claim with false and true geometry; wrong-consumer
claim with false geometry; expired claim with valid fallback; no claim with valid fallback. The
wrong/expired/no-claim cases must prove their result source is not `LIVE_CLAIM`.

**Falsifying runtime experiment:** on the replacement artifact, reproduce the prior state. The row
must log `MANDATORY_ROUTE_READY source=LIVE_CLAIM` with metadata and open an official window. Failure
to open, policy drift away from the exact frontier, or a wrong-consumer claim opening the row falsifies
the repair. No Minecraft launch is authorized in this task turn.
