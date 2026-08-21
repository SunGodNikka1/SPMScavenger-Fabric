# Task 53 brief: V3-A authority/profile foundation (`D-VR-080`, `D-VR-082-A1`, `D-VR-084` consumer)

**Slice:** cross-dimension `VillageScenarioProfile` persistence + `VillageWorkAdmission` as the
**second consumer** of `MandatoryOwnership` + `ActivityClass.VILLAGE_WORK` taxonomy pin (enum +
`DiscretionaryEligibility` only). **Authorization:** User scope lock, 2026-08-20 — brief written
from peer review; **implementation is NOT authorized** until User separately says **authorize
task-53** or **Implement V3-A**.

**Target:** `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

**Source reference:** `d:\Apps\Minecraft Port\Projects\references\SocialPlayerMobs-v0.86.0`

**Source evidence (context only — no SPM edits):** pinned host registers deliberate-band goals at
priority 3 and `PlaceTorchGoal` at priority 4 (`PlayerMobEntity` goal wiring); addon owns village
policy on top. RFC: `plans/RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md` — D-VR-080, D-VR-082-A1,
D-VR-084; task-52 report closes the shared seam.

**Not authorized:** Minecraft runtime launch · commit · push · any V3 executor · storage registry ·
`VillageWorkSelector` · `VillageWorkIntent` · stub/no-op P4 goal · config-at-spawn profile default ·
reserved profile enum values · settlement/crop/storage gates inside admission.

```text
                        MandatoryOwnership          (task-52 — DONE)
                              │
             ┌────────────────┴────────────────┐
             ▼                                 ▼
DiscretionaryActivityDirector          VillageWorkAdmission  (task-53 — this slice)
  EXPLORE / SOCIAL / REST                    "may village work compete at all?"
                                                    │
                                                    ▼
                                             (future executors — NOT task-53)
```

## Why this slice exists

V3 Village Work needs a **cross-dimension policy** (`D-VR-080`) and a **shared admission seam**
(`D-VR-082-A1` + `D-VR-084`) before any executor mutates the world. Task-52 implemented
`MandatoryOwnership` with one consumer (`DiscretionaryActivityDirector`). Task-53 adds the second
consumer and the profile store — **without** inventing placeholder scheduler participants.

**Peer review incorporated (User, 2026-08-20):** task-53 is intentionally **smaller** than the
first Cursor draft. No fake goals, no empty selectors, no speculative enum values, no
village-memory eviction copy, no mega-admission.

## Load-bearing requirements

| # | Requirement |
| --- | --- |
| 1 | **`VillageScenarioProfile` ships only `NEUTRAL` and `VILLAGE_ALLY`.** Unknown, future, or corrupt serialized values deserialize to **`NEUTRAL`**. Do not add `COWARD` / `TRADER` / `RAIDER` names until they have contracts. |
| 2 | **Cross-dimension store — not in `MobVillageMemory` / `VillageMemorySavedData`.** One server-global profile per mob UUID, same value in Overworld / Nether / End. |
| 3 | **`PlayerMobVillagePolicySavedData`** — single canonical instance hosted on **Overworld** `DataStorage`. **`get(server)`** uses `computeIfAbsent`; **`peek(server)`** uses non-creating `get`; **`forget(server, mobId)`** uses **`peek` only** so cleanup never materializes a file for a mob that never had a profile. |
| 4 | **RET-1 for this store:** key = mob `UUID`; normal bound = one tiny entry per **explicitly assigned** mob; **normal eviction = permanent mob removal only** (`PerMobSavedData.forgetAll`); chunk unload / dimension change **preserve**; server stop **persist**. **Do not** copy `VillageMemorySavedData.MAX_TRACKED_MOBS` silent eviction — silently demoting `VILLAGE_ALLY` → `NEUTRAL` is worse than forgetting observational cache. If a catastrophic leak guard is added later, it must be generous, diagnostic, or alert — not silent policy mutation on live mobs. |
| 5 | **`VillageWorkAdmission` answers one question:** *Is this mob generally permitted to enter discretionary Village Work right now?* **Profile gate + `MandatoryOwnership.evaluate` only.** No settlement anchor, crop candidate, composter, population, or storage checks. |
| 6 | **`VILLAGE_ALLY` acquisition (gen-1):** explicit operator command only. HOME village, HIGH relationship, successful trade, and village discovery **must not** promote. No config-at-spawn in this slice. |
| 7 | **`ActivityClass.VILLAGE_WORK`** added to the enum; **`DiscretionaryEligibility`** adds `VILLAGE_WORK` to `blocksDiscretionaryChoice`. **`MAINTENANCE` stays out** — deliberate P4 asymmetry (`D-VR-082-A1` §4). |
| 8 | **No production goal at P4.** Classifier pin on `MoveHolderClassifier` **deferred** until the first real V3 executor exists. Taxonomy is tested via `DiscretionaryEligibility` + synthetic observations. |
| 9 | **`VillageWorkAdmission` is a consumer, not a publisher.** It must **not** call `MandatoryOwnershipRegistry` directly, inspect `WorkDemandPolicy`, enumerate `ActivityClass` blockers locally, or read `SettlementRelationship`. |
| 10 | Register the new store in **`PerMobSavedData.forgetAll()`** using the non-creating forget route. |

## Deliverables

| Path (under target project) | Role |
| --- | --- |
| `src/main/java/com/noobk/spmscavenger/village/VillageScenarioProfile.java` | **new** — enum: `NEUTRAL`, `VILLAGE_ALLY`; codec with unknown → `NEUTRAL` |
| `src/main/java/com/noobk/spmscavenger/village/PlayerMobVillagePolicySavedData.java` | **new** — Overworld-hosted `SavedData`; `get` / `peek` / `forget` / `profileOf` / `setProfile` |
| `src/main/java/com/noobk/spmscavenger/village/VillageWorkAdmission.java` | **new** — pure admission: profile + `MandatoryOwnership` |
| `src/main/java/com/noobk/spmscavenger/activity/ActivityClass.java` | add `VILLAGE_WORK` |
| `src/main/java/com/noobk/spmscavenger/opinion/DiscretionaryEligibility.java` | add `VILLAGE_WORK` to `blocksDiscretionaryChoice` |
| `src/main/java/com/noobk/spmscavenger/PerMobSavedData.java` | register `PlayerMobVillagePolicySavedData.forget(server, mobId)` |
| `src/main/java/com/noobk/spmscavenger/command/VillageProfileCommands.java` (or equivalent) | operator `get` / `set` — see Commands |
| `src/main/java/com/noobk/spmscavenger/SpmScavenger.java` (or mod init) | register commands via Fabric `CommandRegistrationCallback` |
| `src/test/java/.../village/VillageWorkAdmissionTest.java` | **new** — scenarios 1–6, structural negatives |
| `src/test/java/.../village/PlayerMobVillagePolicySavedDataTest.java` | **new** — scenarios 7–10, peek/forget non-materialization |
| `src/test/java/.../village/VillageWorkTaxonomyTest.java` (or extend existing eligibility tests) | scenarios 11–12 |
| `src/test/java/.../village/VillageWorkAdmissionWiringTest.java` | **new** — structural: delegates to `MandatoryOwnership`; forbidden imports/calls |

**Explicitly NOT in this slice:**

- `VillageWorkProbeGoal`, any no-op P4 goal, any `SpmScavenger.addGoal` for village work
- `MoveHolderClassifier` changes
- `VillageWorkSelector`, `VillageWorkIntent`, `HARVEST_REPLANT` / `COMPOST` / `POP_FOOD` enums
- `StorageOwnership`, `GlobalPos` registry (task-54)
- New `MandatoryOwnership` publishers (Trade/Mining remain fail-open — task-52 scenario 10)
- Config-at-spawn default profile

## `VillageScenarioProfile`

```java
public enum VillageScenarioProfile {
    NEUTRAL,
    VILLAGE_ALLY;

    /** Deserialize: known values map exactly; anything else → NEUTRAL. */
    public static VillageScenarioProfile fromSerialized(String raw) { ... }
}
```

**Default:** `NEUTRAL` for missing entries, existing-world migration, and corrupt NBT.

**Rejected:** reserved enum constants without semantics; storing profile inside
`MobVillageMemory`; HOME/HIGH/trade → ally.

## `PlayerMobVillagePolicySavedData`

**Hosting (locked):**

```text
get(server)   → server.overworld().getDataStorage().computeIfAbsent(...)
peek(server)  → server.overworld().getDataStorage().get(...)     // non-creating
forget(server, mobId)
    → peek(server); if non-null, remove mobId from map; markDirty if changed
```

**Cross-dimension read/write:** all accessors take `MinecraftServer` (or derive from any
`ServerLevel` via `level.getServer()`), never a dimension-local store.

**`forgetEverywhere` is NOT required** — unlike `VillageMemorySavedData`, there is exactly one
canonical file. `PerMobSavedData.forgetAll` calls `PlayerMobVillagePolicySavedData.forget(server,
mobId)` once.

**Persistence contract (RET-1):**

| Event | Behaviour |
| --- | --- |
| Explicit `setProfile` / operator command | write entry |
| Chunk unload | **preserve** |
| Dimension change | **preserve** |
| Server stop / restart | **persist** (save/load round-trip) |
| Permanent mob removal | **`forget`** via `PerMobSavedData.forgetAll` |
| Ordinary unload only | **preserve** — no hook on `ENTITY_UNLOAD` for this store |

**Must not happen:** `forgetEverywhere` sweeping all dimensions; `computeIfAbsent` on the forget
path; silent LRU eviction of live ally assignments.

## `VillageWorkAdmission`

**Single question:** may discretionary village work compete at all?

```java
public record Result(boolean permitted, DenyCause cause) { ... }

public enum DenyCause {
    NONE,
    DENY_PROFILE,           // not VILLAGE_ALLY
    DENY_MANDATORY_AUTHORITY // MandatoryOwnership denied — carry underlying InvalidationCause in tests
}

public static Result evaluate(
        VillageScenarioProfile profile,
        ActivityObservationService.Observation observation,
        boolean combatTarget,
        Optional<MandatoryOwnershipClaim> liveClaim,
        long now)
```

**Decision order (locked):**

```text
profile != VILLAGE_ALLY
    → DENY_PROFILE

MandatoryOwnership.evaluate(...).eligible() == false
    → DENY_MANDATORY_AUTHORITY

otherwise
    → permitted
```

**Consume, do not reimplement:**

```java
MandatoryOwnership.Permission permission =
        MandatoryOwnership.evaluate(observation, combatTarget, liveClaim, now);
```

The admission class must not duplicate combat handling, pending-claim handling, or running-arm
delegation.

**Layering for future work (document in report, do not implement):**

```text
VillageWorkAdmission          → "may village work compete at all?"
executor canUse / selector    → "which legal work exists?"   (future tasks)
```

## Taxonomy — `VILLAGE_WORK` without a goal

Add `ActivityClass.VILLAGE_WORK` and pin blocking semantics in `DiscretionaryEligibility`:

| Running class | Blocks fresh discretionary selection? |
| --- | --- |
| `VILLAGE_WORK` | **yes** |
| `MAINTENANCE` (`PlaceTorchGoal`) | **no** (unchanged) |

Tests use **synthetic** `ActivityObservationService.Observation` values — no production goal
required. The first real V3 executor task will add `MoveHolderClassifier` mapping when that goal
exists.

## Commands

Minimal explicit assignment path (`D-VR-080`):

```text
/spmscavenger village profile get <mob>
/spmscavenger village profile set <mob> neutral
/spmscavenger village profile set <mob> village_ally
```

**Properties (must hold in tests or structural assertions where applicable):**

- Only explicit assignment sets `VILLAGE_ALLY`
- `SettlementRelationship` / HOME / HIGH / trade success / village discovery **must not** call
  `setProfile` anywhere in production code added by this slice

Target selector: `@e` PlayerMob entities (use existing `PlayerMobs.isPlayerMob` guard).

**Not in scope:** config-at-spawn mass-default allies; storage permit commands (task-54).

## Constraints

- **TDD required** for admission scenarios 1–6 and structural negatives; capture RED before GREEN
  for at least scenarios 4 (fail-open mandatory) and 5 (NEUTRAL deny).
- SPM stays **stock**. No mixin, no fork, no host-goal registration in this slice.
- No `Level` access inside `VillageWorkAdmission` or `MandatoryOwnership` — unit-testable without
  `Bootstrap` where possible; SavedData codec tests may use in-memory NBT.
- Do not touch `MandatoryOwnershipRegistry` publish/release semantics, `GatherResourcesGoal`, storage,
  or crop executors.

## Verification — twelve scenarios + structural negatives

| # | Scenario | Expected |
| --- | --- | --- |
| 1 | Pending Gather claim active; profile `VILLAGE_ALLY` | admission **denied** (`DENY_MANDATORY_AUTHORITY` / pending cause) |
| 2 | Running Gather; profile `VILLAGE_ALLY` | **denied** |
| 3 | Running village trade (`VILLAGE_TRADE`); profile `VILLAGE_ALLY` | **denied** |
| 4 | Demand exists, no claim, no running mandatory executor; profile `VILLAGE_ALLY` | **allowed** (fail-open third state — task-52 simulation B) |
| 5 | Profile `NEUTRAL` regardless of settlement relationship | **denied** (`DENY_PROFILE`) |
| 6 | Profile `VILLAGE_ALLY` read via Overworld / Nether / End accessors | **identical** value |
| 7 | Chunk unload / dimension change | profile **persists** |
| 8 | Permanent mob removal (`forgetAll`) | profile entry **removed** |
| 9 | Save / reload round-trip | `VILLAGE_ALLY` **survives** |
| 10 | Missing / unknown serialized profile | **`NEUTRAL`** |
| 11 | Synthetic observation: `VILLAGE_WORK` running | `DiscretionaryEligibility` **blocks** fresh discretionary selection |
| 12 | Synthetic observation: `MAINTENANCE` running alone | `DiscretionaryEligibility` **does not block** |

### Structural negatives (run in isolation; name the test each breaks)

| Control | Mutation / check | Must fail |
| --- | --- | --- |
| S1 | Admission re-implements mandatory logic instead of calling `MandatoryOwnership.evaluate` | delegation structural test |
| S2 | Admission inspects `WorkDemandPolicy` | wiring / import test |
| S3 | Admission calls `MandatoryOwnershipRegistry` directly | wiring test |
| S4 | Admission reads `SettlementRelationship` or village memory | wiring test |
| S5 | Profile stored in `MobVillageMemory` | structural / grep test |
| S6 | Profile deleted on ordinary `ENTITY_UNLOAD` | must not — no unload hook |
| S7 | `forgetAll` uses `computeIfAbsent` on policy store | `PerMobRemovalContractTest` peek materialization pattern |
| S8 | New SavedData with UUID keys not registered in `PerMobSavedData.forgetAll` | `PerMobRemovalContractTest` |
| S9 | Silent `MAX_TRACKED_MOBS`-style eviction on policy store | reject — no such eviction in production code |

### Commands

```powershell
cd "d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric"
.\gradlew.bat compileJava
.\gradlew.bat test
```

Report total test count and every structural negative result.

## Docs to update (when implementation is authorized)

| File | Update |
| --- | --- |
| `docs/porting/TEST_MATRIX.md` | task-53 scenario rows 1–12 + structural negatives |
| `plans/RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md` | V3-A task-53 brief status; note deferred selector/stub goal per User 2026-08-20 |
| `.superpowers/sdd/progress.md` | append when task accepted |

## Report

`.superpowers/sdd/task-53-report.md` — status `DONE` / `DONE_WITH_CONCERNS` / `NEEDS_CONTEXT` /
`BLOCKED`; every command with working directory and exact result; evidence labelled
`CONFIRMED` / `INFERRED` / `UNVERIFIED`; RED-before-GREEN where required; self-review mapped to
the requirement table; concerns section naming any deferred items (selector, classifier pin, unwired
Trade/Mining publishers).

**Do not mark behavioural village-work claims `CONFIRMED`.** This slice proves authority/profile
static acceptance only; no executor runs in-world (AV-1).

## Deferred to later tasks (name in report, do not implement)

| Item | When |
| --- | --- |
| `VillageWorkSelector` / intent enum | First real executor batch — decide if shared selector is needed |
| `MoveHolderClassifier` pin for `VILLAGE_WORK` | Same task as first V3 executor goal |
| P4 village work goals | task-55+ (harvest, compost, population, …) |
| `StorageOwnership` / D-VR-081 | task-54 |
| Config-at-spawn profile default | Only if explicit user need |
| Trade/Mining as `MandatoryOwnership` publishers | Separate tasks; fail-open until wired |
