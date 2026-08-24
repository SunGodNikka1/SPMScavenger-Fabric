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
