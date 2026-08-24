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
