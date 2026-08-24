# Task 59 pre-launch readiness report (fixture sub-slice)

**Status:** `DONE` — semantic fixture repair complete; **Minecraft / VR-T3 campaign NOT AUTHORIZED**  
**Brief:** `.superpowers/sdd/task-59-brief.md`  
**Target:** `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`  
**Date:** 2026-08-22

---

## Authorization state

| Scope | Status |
| --- | --- |
| Task-59 preparation | **ACCEPTED** |
| Pre-launch fixture sub-slice (structure) | **DONE** |
| Fixture semantic repair sub-slice | **DONE** |
| Minecraft / `runClient` / batched VR-T3 | **NOT AUTHORIZED** |

---

## Semantic repair disposition (user-authorized slice)

| Defect | Fix | Evidence |
| --- | --- | --- |
| Shared bootstrap fake POI claim | **FIXED** — `claim_village_beds` TP-only; `settlementBootstrapDoesNotFakeHomeOwnership` forbids NBT injection | `_lib/claim_village_beds.mcfunction`; matrix gate 0 preflight |
| VR-T3e wrong recipient (second PlayerMob) | `population_food_deficit`: villager `spm_vr.villager2` cleared inventory + `spm_vr.food_recipient`; subject carries bread | `scenario/population_food_deficit.mcfunction`; `populationFoodDeficitTargetsVillagerRecipient` |
| VR-T3b immediate interrupt | Staged zombie via `schedule … stage_interrupt_zombie 120t` after `crop_managed_single` | `scenario/crop_interrupt_combat.mcfunction`, `_lib/stage_interrupt_zombie.mcfunction`; `cropInterruptCombatStagesInterruptionAfterPathingWindow` |
| VR-T3k two crops bypass contention | **One** mature wheat; two allies with seeds | `scenario/crop_multi_mob.mcfunction`; `cropMultiMobContendsForSingleMatureCrop` |
| VR-T3l Hunger effect ≠ wantsFood | Mature **carrots**, **empty backpack**, **oak logs** for mandatory gather → `VillageWorkAdmission` deny while `VILLAGE_ALLY` via `spawn_ally` | `scenario/crop_hungry_veto.mcfunction`; `cropHungryVetoEstablishesWantsFoodAndAdmissionDenialShape` |

**Explicit non-changes:** Tasks 52–58 production semantics untouched. No runtime instrumentation. No Minecraft launch.

---

## Structural test suite

`SpmVrDatapackStructureTest` — **14 tests** (shape + semantic regression guards).  
These do **not** prove runtime behavior; they block recurrence of the identified fixture mistakes.

---

## Build evidence (`CONFIRMED`)

| Command | Result |
| --- | --- |
| `.\gradlew.bat test` | **1603 tests**, 0 failures (`build/reports/tests/test/index.html`) |
| `SpmVrDatapackStructureTest` | 14/14 pass |

---

## Operator notes (live campaign — still unauthorized)

1. Run **settlement bootstrap preflight** (matrix gate 0) — ≥120t after stub load; halt on `FIXTURE_FAILURE`.
2. Allow vanilla bed acquisition; **do not** inject HOME/sleep NBT if preflight fails.

---

## Remaining `UNVERIFIED` (expected until runtime campaign)

| Item | Notes |
| --- | --- |
| Bed POI occupation in live world | Vanilla `PoiManager.take()` only — preflight gate 0 verifies before VR-T3 rows |

---

## Launch gate (for your final pre-launch checkpoint)

| Gate | Ready? |
| --- | --- |
| Environment pin + VR-T3c wording + observation windows | **YES** |
| 13 presets with semantic shape fixes | **YES** |
| Structural + regression string guards | **YES** |
| Runtime VR-T3 evidence | **NO** — awaiting authorization |

---

## Resume revalidation — 2026-08-23

Task-59 was resumed after V2-TE-W2.4 removed its temporary witness/fixture tooling. The runtime
environment is now repinned to the resulting clean production artifact; no V3 production code or
fixture semantics changed during this reconciliation.

| Evidence | Result |
| --- | --- |
| Focused `SpmVrDatapackStructureTest` | **14/14 PASS** |
| Executable scenario inventory | **13/13 present** — VR-T3a–e,g–m + D-VR-084 witness |
| `.\gradlew.bat clean build` | **PASS** — 1614 tests, 0 failures/errors/skips |
| Clean Scavenger JAR | `build/libs/spmscavenger-1.11.0.jar` |
| SHA-256 | `5EF3639FF03DA20191C3C83BCF662461DB081A8ABFA00E2CEBDC8C93A8B49BF9` |
| Host 0.89.0 artifact SHA-256 | `C8DC0E89C3FD632B6DCC7F8E46D3AE4955DD5504CBA53F72B62314850A64E612` — matches environment pin |
| Packaged upstream Trade Everything classes | **0** |
| Temporary V2-TE witness classes/references | **0 / 0** |

**AV-1 boundary:** fixture structure, artifact identity, compile, tests, and packaging are
`CONFIRMED`. All live VR-T3 behavior remains `UNVERIFIED`; Minecraft launch still requires explicit
authorization.

---

## Runtime-validation packet — 2026-08-23

The earlier clean-artifact checkpoint is superseded for launch purposes by a temporary instrumented
artifact. Code inspection established that D-VR-084's pending claim was not exposed by the existing
running-goal readout, so the matrix could not collect the proof class it required.

### Delivered

- one-shot `/spmscavenger debug v3 inspect <mob>` snapshot of profile, running activities, pending
  claim, shared mandatory permission, and Village Work admission;
- stable transition-on-request log prefix `[spmscavenger/v3-witness]`;
- `/function spm_vr:help` and provenance-safe `/function spm_vr:cleanup`;
- standalone datapack operator runbook and `VR-T3-RUNTIME-EVIDENCE.md` worksheet;
- explicit post-acceptance removal manifest in task brief v1.1.

The command owns no session or tick hook and retains no mob/world reference. Structural negative
controls forbid claim publish/release, Goal invocation, navigation, profile/storage/inventory
mutation, and trade execution.

### Verification evidence (`CONFIRMED` for static/package scope)

| Check | Result |
| --- | --- |
| Witness tests RED | 3/3 failed with `NoSuchFileException` before implementation |
| Witness tests GREEN | 3/3 pass |
| Combined witness + datapack focused suite | **18/18 pass** (15 datapack + 3 witness) |
| `.\gradlew.bat clean build` | **PASS** — 1618 tests, 0 failures/errors/skips |
| Remapped JAR | `build/libs/spmscavenger-1.11.0.jar` |
| SHA-256 | `063585AA5782B576E5CCFDAD5739B133842173EF17232CEBF7B4DA95B01AA628` |
| Temporary V3 witness JAR entries | **1** — expected |
| Packaged upstream Trade Everything classes | **0** |
| Project-owned TE compatibility classes | **5** |
| Removed V2-TE witness entries | **0** |

Three explicit source probes returned `NOT FOUND`: (1) authority/profile/storage mutation calls,
(2) Goal admission/navigation/executor calls, and (3) retained session/tick-hook state.

**AV-1 boundary:** the witness's read-only code shape and artifact identity are `CONFIRMED`.
Minecraft behavior remains `UNVERIFIED`; no runtime process was launched.

---

## Gate-0 witness completion — 2026-08-23

The existing command now reports the subject's actual remembered in-bounds settlement anchor/
identity and cached population facts: adult villagers, total usable HOME capacity, claimed HOME
count, current free capacity, completeness, freshness, and observation tick. It emits exactly one of
`Gate0=PASS`, `Gate0=FIXTURE_FAILURE`, or `Gate0=INCOMPLETE`.

`VillageWorkFactsService.peek(...)` was not reused because its current cache path may write a
freshness transition. The extension instead adds `peekReadOnly`: it does not create a server cache
and projects current freshness without replacing the stored snapshot. Production consumers remain
on the existing `peek` contract.

| Verification | Result |
| --- | --- |
| Focused Gate-0/witness/cache tests | **12/12 PASS** |
| Gate classification unit rows | no settlement/facts → INCOMPLETE; stale/incomplete → INCOMPLETE; exact thresholds → PASS; each numeric deficit → FIXTURE_FAILURE |
| Read-only cache unit control | projected STALE snapshot does not replace stored FRESH object |
| Structural read-only controls | no cache creation/write, refresh/schedule, authority mutation, Goal/navigation/executor call, POI/Brain/sleep mutation, or retained session |
| `.\gradlew.bat clean build` | **PASS** — 1625 tests, 0 failures/errors/skips |
| Remapped JAR | `build/libs/spmscavenger-1.11.0.jar` |
| SHA-256 | `1185EBCF362CB5409FC0D61DC4A49EE00016385FAF402C0244C8DC9DF7CD22C6` |
| Temporary V3 witness-related JAR entries | **6** (command, assessment, and nested record/enum classes) |
| Packaged upstream Trade Everything classes | **0** |
| Project-owned TE compatibility classes | **5** |
| Removed V2-TE witness entries | **0** |

**Semantic-drift review:** production refresh cadence, VillageMemory recording, HOME ticket state,
cache writes used by production consumers, goal admission, priorities, navigation, and activity
authority are unchanged. Runtime Gate-0 behavior remains `UNVERIFIED`; no Minecraft launch occurred.

Three Gate-0 source probes returned `NOT FOUND`: (1) authority/profile/storage/inventory/executor
mutation, (2) refresh/POI/Brain/memory/cache evidence manufacturing, and (3) retained session/tick
hook/Goal/navigation calls.

---

## Settlement-row shelter-release checkpoint — 2026-08-23

The first live Gate-0 sample passed its settlement thresholds but exposed a fixture-level owner:
`SeekShelterGoal:SHELTER_HOLD` remained active at reported day time 912. The row clock did not start.
The new artifact keeps Gate 0 independent and reports:

- `READY` when no shelter hold is active;
- `WAITING_DAYTIME` when shelter is active before daytime;
- `FIXTURE_INCOMPLETE` when shelter remains active during daytime.

| Verification | Result |
| --- | --- |
| Initial RED | focused compile failed because `V3RowPrecondition` did not exist |
| Focused V3 + facts-cache tests | **15/15 PASS** |
| `clean build` | **PASS** — 1628 tests, 0 failures/errors/skips |
| Remapped JAR | `build/libs/spmscavenger-1.11.0.jar` |
| SHA-256 | `766F099FBC004A007A615DD044A9243901F8FBF621A66EF3A8BBC33C6A3CCA40` |
| Temporary V3 entries | **9** |
| Upstream Trade Everything classes | **0** |
| Project-owned TE compat classes | **5** (plus one package directory entry) |
| Removed V2 witness entries | **0** |

Three source probes returned `NOT FOUND`: (1) time or mandatory-authority mutation, (2) Goal stop/
admission/navigation or executor mutation, and (3) retained session/tick-hook or settlement refresh
state. No Minecraft process used the replacement artifact.

---

## Automated campaign artifact — 2026-08-23

The manual launch packet is superseded by a one-command temporary controller. Preferred contract:

```text
/spmscavenger debug v3 run <preset>
/spmscavenger debug v3 status
/spmscavenger debug v3 report
/spmscavenger debug v3 reset
```

It owns fixture orchestration only: allowlisted preset execution, pre-window contamination removal,
natural Gate0 wait, declared day/weather transition, genuine shelter-release wait, bounded fixture
chunk liveness, row timing, and passive snapshots. It does not start/stop Goals, publish/remove
claims, alter VillageWork admission, inject HOME/Brain/sleep evidence, steer/equip the subject after
opening, award outcomes, or suppress declared combat.

| Check | Result |
| --- | --- |
| Focused campaign/witness/datapack tests | **33/33 PASS** |
| Clean build | **1643 tests; 0 failures/errors/skips** |
| JAR | `build/libs/spmscavenger-1.11.0.jar` |
| SHA-256 | `94534E28364ACF9E6C7FAFB1940D2F3AEF3F90581103DEED58D416A2DAA06F3C` |
| Package audit | 28 temporary V3 classes (21 controller/snapshot/evidence), 0 upstream TE classes, 5 project-owned TE classes, 0 removed V2 witness entries |

Three explicit source probes returned `NOT FOUND`: production claim/admission authority calls;
navigation/target/sleep/Brain/HOME/inventory mutation; and refresh/POI/trade/Goal-execution calls.
No Minecraft launch occurred. The exact hash above requires separate runtime approval.

---

## Startup-containment replacement artifact — 2026-08-24

The first live controller command exposed an invalid Minecraft 1.21 API boundary: raw Brigadier
execution cannot invoke the custom `function` executor. Exact source behavior is
`UnsupportedOperationException("This function should not run")`. The repaired controller creates
`PREPARING` first and invokes the loaded function through `ServerFunctionManager` on the next tick,
then performs discovery/resource activation under a non-fatal containment guard.

| Check | Result |
| --- | --- |
| Unchecked fixture-executor harness | contained; `FIXTURE_FAILURE`; status/report evidence; release callback invoked |
| Fatal control | `OutOfMemoryError` propagated |
| Focused Task-59 suite | **35/35 PASS** |
| Clean build | **1645 tests; 0 failures/errors/skips** |
| JAR | `build/libs/spmscavenger-1.11.0.jar` (1,213,620 bytes) |
| SHA-256 | `732BBB65C5604D617A9FC84120F7878622C3018DA3B6F84035DFBFEB9A532ECC` |
| Package audit | 32 temporary V3 classes, 0 upstream TE classes, 5 project-owned TE classes, 0 removed V2 witness entries |

The ally-storage and managed-crop warm-up diagnostics remain separate and `UNVERIFIED`. No
Minecraft relaunch occurred; the exact replacement hash requires approval.

---

## Gate-0 bootstrap replacement artifact — 2026-08-24

The startup-contained VR-T3j attempt ended before its window opened because readable
`claimedHomeCount < 2` was adjudicated before the fixture's required 120-tick natural HOME
bootstrap. The attempt is discarded as `FIXTURE_INCOMPLETE / PREMATURE_GATE0_ADJUDICATION`; it is
not a V3 behavior result.

The replacement controller records the exact successful scenario-function tick, exposes
`WAITING_GATE0_BOOTSTRAP`, and waits through elapsed tick 119 without terminally consuming numeric
Gate-0 thresholds. Tick 120 begins normal adjudication. The existing 2400-tick overall timeout and
the read-only `V3Gate0Assessment` contract remain unchanged.

| Check | Result |
| --- | --- |
| Boundary regression | tick 20/home 0, tick 60/home 1, tick 119/home 1 remain waiting; tick 120 home 2/free 1 passes; tick 120 home 1 fails fixture |
| Focused V3/datapack suite | **48/48 PASS** |
| Clean build | **1649 tests; 0 failures/errors/skips** |
| JAR | `build/libs/spmscavenger-1.11.0.jar` (1,218,391 bytes) |
| SHA-256 | `7BD5205B1CFF85608BA53C9C446BC40D00A20E2FBDDCF3FA68A2798CF1CA8577` |
| Package audit | 36 temporary V3 debug entries (4 bootstrap-gate), 0 upstream TE classes, 5 project-owned TE classes, 0 removed V2 witness entries |

VR-T3k and VR-T3m observation-model corrections remain controller backlog and are not included in
this artifact. No Minecraft relaunch occurred; exact-hash approval is required.
