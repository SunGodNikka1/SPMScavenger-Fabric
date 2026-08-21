# Task 55 brief: V3-C committed harvest→replant episode (`D-VR-079`, `D-VR-079-A1`)

**Slice:** one target-bound **managed crop** harvest→replant episode at priority **4**, plus a
continuous host-`HarvestCropsGoal` veto inside the **managed crop domain** that fails toward stock
when the domain cannot be positively established.

**Authorization gates (LOCKED sequence):**

| Gate | Status | User phrase to authorize |
| --- | --- | --- |
| **Brief design** | **v2 — peer-review repairs applied** | (this document) |
| **Gate 0 — read-only source audit** | **HOLD** — brief contradictions repaired; audit scope revised | **authorize task-55 gate 0** |
| **Gate 0 closure** | **BLOCKED** — atomic `setBlock` commit must be lockable; no `MandatoryOwnership` publisher | — |
| **Full implementation** | **NOT AUTHORIZED** | **authorize task-55** / **Implement V3-C** |

**Brief revision history:**

- v1 — initial draft (rejected: `ForagePolicy` compile import, ripeness-as-domain, wheat exclusion,
  `destroyBlock` normal path, `MandatoryOwnership` repair publisher, admission/repair continuation
  exception)
- v2 — **User peer review 2026-08-21:** split `managedCropCell` vs `harvestCandidate`; gen-1 crop
  set includes **wheat**; vanilla-only `CropReplantSemantics`; atomic mature→age-0 transaction; **no**
  second `MandatoryOwnership` publisher; `VillageWorkAdmission` is sole profile+mandatory gate for
  episode admission

**Target:** `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

**Source reference:** `d:\Apps\Minecraft Port\Projects\references\SocialPlayerMobs-v0.86.0`
**(read-only oracle only — SPM is deliberately NOT a compile dependency; see `build.gradle`)**

**Source evidence (host crop goal — read-only oracle):**

| Path | Evidence |
| --- | --- |
| `.../entity/goal/HarvestCropsGoal.java` | Priority **6**, `MOVE\|LOOK`; `canUse` gated on `wantsFood()`; phases `IDLE → PATHING → HARVESTING`; `targetPos` private; harvest uses `destroyBlock` + floor spill — **anti-pattern V3-C must not mirror** |
| `.../entity/PlayerMobEntity.java:842` | Registers `HarvestCropsGoal(this, 0.9, scanRadius 8)` at priority **6** |
| `.../entity/ForagePolicy.java` | Host **oracle only**: `isRipeFoodCrop` = max-age carrots/potatoes/beetroots; **wheat excluded from host forage** — V3 managed set is **broader** and **independent** |
| `build.gradle:30` | `Social Player Mobs is deliberately NOT a dependency` |

**Depends on (DONE / STATIC ACCEPT):**

| Task | Deliverable | V3-C use |
| --- | --- | --- |
| **task-52** | `MandatoryOwnership` | episode admission **consumes** via `VillageWorkAdmission` only — **task-55 does NOT publish** |
| **task-53** | `VillageScenarioProfile`, `VillageWorkAdmission`, `VILLAGE_WORK` taxonomy | **sole** profile + mandatory admissibility gate for episode `canUse` / `canContinueToUse` |
| **task-54** | storage safety (orthogonal) | **must not** import storage types into crop hot path |

**RFC:** `plans/RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md` — D-VR-079, D-VR-079-A1, D-VR-082-A1,
D-VR-083 (budget contract), VR-T3a–c/k/l/m.

**Not authorized without separate implementation authorization:** production Java · mixin wiring ·
Minecraft runtime launch · commit · push.

```text
                    VillageMemory (peek only)
                              │
         ┌────────────────────┴────────────────────┐
         ▼                                         ▼
  managedCropCell(pos)                    harvestCandidate(pos)
  ally + bounds + supported crop          managedCropCell
  on valid farmland — ANY AGE             + mature + replant feasible
         │                                         │
         ▼                                         ▼
HarvestCropsVetoMixin (P6)              VillageHarvestEpisodeGoal (P4)
fail open on uncertainty                 VillageWorkAdmission → permit?
         │                               + crop preflight/transaction
         │                                         │
         └──────── field identity stable ──────────┘
                    mature → age-0 (one setBlock; no destroy)
```

## Why this slice exists

Pinned host `HarvestCropsGoal` is a **destructive hunger drive** at priority 6. A V3 episode at
priority 4 preempts it **only while `VillageWorkAdmission` passes**. When mandatory survival owns the
mob, admission refuses and the host path would strip managed village fields without replanting —
exactly VR-T3a's must-not-happen. **D-VR-079-A1** therefore requires:

1. a **managed crop domain** (`managedCropCell`) that exists **before** any episode claims a target and
   **does not depend on maturity**;
2. a **continuous host veto** inside that domain (ShelterHold-family shape, not SPM fork);
3. an episode that performs an **atomic mature→age-0 replacement** and **banks staged drops directly**
   (F8) — never `destroyBlock`, never floor-pickup recovery, never a post-mutation repair publisher.

## Peer review incorporated (User, 2026-08-21)

| Finding | v1 defect | v2 repair |
| --- | --- | --- |
| Domain predicate | used `isRipeFoodCrop` → veto disappears after host matures crop | `managedCropCell` = supported crop **any age**; `harvestCandidate` adds maturity |
| Crop set | excluded wheat to match host | gen-1: **wheat, carrots, potatoes, beetroot** with crop-specific planting material |
| SPM coupling | compile-time `ForagePolicy` import | vanilla-only `CropReplantSemantics`; host audited as oracle only |
| Repair publisher | second `MandatoryOwnershipRegistry.publish` | **REJECTED** — single slot per mob is unsafe with Gather's UUID-only releases |
| Commit path | `destroyBlock` → hope `setBlock` | atomic `setBlock(ageZero)`; drops staged before mutation; abort leaves crop unchanged |
| Admission wording | "mandatory gate only" + separate profile check | `VillageWorkAdmission` = profile **+** mandatory; episode does not re-interpret profile |
| Continuation exception | repair mandate vs hard-abort | **removed** — no multi-tick repair phase; `canContinueToUse` re-checks admission; abort before COMMIT is safe |

## Load-bearing requirements

| # | Requirement |
| --- | --- |
| 1 | **Two predicates, not one.** `managedCropCell` (domain + veto identity) and `harvestCandidate` (episode selection) are separate. Domain **must not** depend on maturity. |
| 2 | **`managedCropCell`** — `profile == VILLAGE_ALLY` **and** positive remembered village resolution **and** `SettlementBoundsPolicy.within(pos, anchor)` **and** `CropReplantSemantics.supportedCrop(state)` on valid farmland. **Any crop age.** No `SettlementRelationship` term. |
| 3 | **`harvestCandidate`** — `managedCropCell` **and** `CropReplantSemantics.isMature(state)` **and** replant transaction feasible (see PREPARE). |
| 4 | **Gen-1 supported crops:** wheat, carrots, potatoes, beetroot — each with crop-specific planting item mapping in `CropReplantSemantics`. Host's narrower forage set is irrelevant. |
| 5 | **Fail direction on veto uncertainty** — cannot positively establish `managedCropCell` → **do not veto** host `HarvestCropsGoal`. Opposite of storage (`D-VR-081`). |
| 6 | **Host veto is continuous** on `canUse` **and** `canContinueToUse` when `managedCropCell` is positively true at `targetPos`. Wilderness and immature managed cells inside domain are still vetoed if they are supported crops on farmland (host targets mature only, but continuation must not lose veto when state changes). |
| 7 | **Episode admission** — delegate profile + mandatory admissibility **entirely** to `VillageWorkAdmission.evaluate(...)`. Episode policy **must not** re-check `VILLAGE_ALLY` separately. Host veto **may** read profile independently (it does not consume admission). |
| 8 | **Episode is not hunger-gated** — unlike host P6. Village maintenance runs when admission passes. |
| 9 | **Atomic commit** — normal path: mature crop → age-0 **same crop** via one `setBlock`; **no** `destroyBlock`; **no** deliberate bare-farmland intermediate. Drops computed **once** in PREPARE from actual `Block.getDrops` output. |
| 10 | **F8 own-drop banking** — after verified commit, bank replant surplus **first**, then food/output; **never** depend on `CollectFloorItemsGoal` (VR-T3m). |
| 11 | **Staged-drop preflight** — do not reason from minimum expected seed counts. Generate actual staged drops once in PREPARE; if no planting unit (staged replant material **or** exactly one mob-held reserve unit), **ABORT with world unchanged**. |
| 12 | **No `MandatoryOwnership` publisher in task-55.** If Gate 0 proves V3-caused bare state is unavoidable asynchronously, **STOP** and redesign identity-bound multi-publisher semantics **before** implementation — do not discover mid-task-55. |
| 13 | **Pre-mutation interruption** — SCAN/PATH/WINDUP: if `VillageWorkAdmission` becomes false → abort safely, no mutation (VR-T3b). |
| 14 | **Preflight abort** — invalid support/crop/planting unit before COMMIT → no world change (VR-T3c). |
| 15 | **Multi-mob** — no global reservation map; commit-time block-truth revalidation (VR-T3k). |
| 16 | **Budget contract (`D-VR-083`)** — loaded chunks only; finite radius; backoff; memory **peek** only — no `VillagePerception.observe()` on hot paths. |
| 17 | **`MoveHolderClassifier` pin** — `VillageHarvestEpisodeGoal` → `ActivityClass.VILLAGE_WORK`. |
| 18 | **Enforcement / diagnostics split** — pure policies + atomic transaction type; `@Pseudo` mixin + reflective resolver; hook-observed compat flags. |
| 19 | **Vanilla crop semantics owned by addon** — `CropReplantSemantics` implements `supportedCrop`, `isMature`, `plantingItem`, `ageZero` from pinned vanilla/MC APIs only. **No** compile-time import of SPM `ForagePolicy` or other host types in production code. |

## Rejected (locked)

| Proposal | Why rejected |
| --- | --- |
| `managed = positions the episode already claimed` | Host grabs first → bare farmland loophole |
| Ripeness / maturity as domain identity | Host state change drops veto mid-continuation |
| `ForagePolicy` compile import | SPM deliberately not a dependency; host is oracle only |
| Wheat exclusion from managed set | V3 crop system ≠ host forage subset |
| `destroyBlock` → `setBlock` normal path | Creates bare intermediate; outputs before verified replant |
| Second `MandatoryOwnership` publisher | Single slot per mob; Gather UUID-only `release` clobber hazard |
| Repair continuation despite admission deny | Smell removed by atomic commit — no repair phase |
| Separate profile check in episode `canUse` | Duplicates `VillageWorkAdmission` |
| Minimum expected seed preflight | Weaker than staged actual `getDrops` |
| Separate `ReplantCropGoal` | Breaks episode ownership |
| Hunger-gate V3 episode on `wantsFood()` | Wrong semantics |
| Floor-pickup replant recovery | F8 violation |
| Manufacture replacement seeds | Pause-not-barren is the invariant |
| `VillageWorkSelector` in this slice | Only one executor |

## Package layout (proposed)

| Path | Role |
| --- | --- |
| `village/crop/CropReplantSemantics.java` | **vanilla-only** `supportedCrop`, `isMature`, `plantingItem`, `ageZero`, replant-material classification |
| `village/crop/ManagedCropDomainPolicy.java` | pure `isManagedCell(mob, level, pos)` — `managedCropCell` predicate |
| `village/crop/HarvestCandidatePolicy.java` | pure `isHarvestCandidate(...)` + scan selection |
| `village/crop/HarvestCropVetoPolicy.java` | pure veto on `managedCropCell`; fail open |
| `village/crop/CropHarvestTransaction.java` | PREPARE / RESERVE / COMMIT / abort — atomic transaction type |
| `village/crop/VillageHarvestEpisodePolicy.java` | orchestrates transaction + tests (unit surface) |
| `village/crop/HarvestCropGuardCompatibility.java` | diagnostics: hook observed, veto counts, session reset |
| `goal/VillageHarvestEpisodeGoal.java` | P4 `Goal` — SCAN/PATH/WINDUP/COMMIT delegate |
| `compat/OptionalHarvestCropTargetResolver.java` | reflect `HarvestCropsGoal.targetPos` |
| `compat/OptionalMobInventoryInsert.java` | reflective backpack insert (Gate 0 pins API) |
| `mixin/HarvestCropsManagedDomainMixin.java` | `@Pseudo` continuous veto |
| `mining/MoveHolderClassifier.java` | `VillageHarvestEpisodeGoal` → `VILLAGE_WORK` |
| `SpmScavenger.java` | register P4 goal; compat session lifecycle |

**Removed from v1:** `HarvestEpisodeReserve` as separate persisted concept (reserve is transaction-local);
`VillageHarvestEpisode` repair handle; any `MandatoryOwnershipRegistry` import in crop package.

## Predicate definitions (locked)

### `managedCropCell` (domain + host veto)

```text
managedCropCell(mob, level, pos) :=
      profile(server, mob.uuid) == VILLAGE_ALLY
  AND exists KnownVillage v in VillageMemorySavedData.peek(level, mob.uuid)
      where SettlementBoundsPolicy.within(pos, v.anchor())
  AND CropReplantSemantics.supportedCrop(level.getBlockState(pos))
  AND validFarmlandSupport(level, pos)   // Gate 0 pins exact rule
```

**Stability property:** remains true when crop ages from 0→max and back, as long as the block stays a
supported crop on valid farmland. Maturity is **not** part of this predicate.

**Village resolution:** `VillageMemorySavedData.peek` only. Empty memory → not managed → veto does not fire.

### `harvestCandidate` (episode selection)

```text
harvestCandidate(mob, level, pos) :=
      managedCropCell(mob, level, pos)
  AND CropReplantSemantics.isMature(level.getBlockState(pos))
  AND replantTransactionFeasible(mob, level, pos)   // PREPARE dry-run succeeds
```

### Gen-1 crop table (contract — Gate 0 pins vanilla evidence)

| Crop | `supportedCrop` | `plantingItem` | Notes |
| --- | --- | --- | --- |
| Wheat | `Blocks.WHEAT` | wheat seeds | host does not forage wheat; V3 does |
| Carrots | `Blocks.CARROTS` | carrot item | |
| Potatoes | `Blocks.POTATOES` | potato item | |
| Beetroot | `Blocks.BEETROOTS` | beetroot seeds | zero-seed drops possible — staged preflight handles |

## Host harvest veto (mixin — Gate 0 must pin)

**Shape:** `*ShelterHoldMixin` family — `@Pseudo`, `require = 0`, reflect `mob` + `targetPos`.

```text
canUse @ RETURN:
    if host returned false → pass through
    resolve mob, targetPos
    if mob == null || targetPos == null → pass through
    if HarvestCropVetoPolicy.shouldVeto(mob, level, targetPos):
        clear targetPos if Gate 0 shows stale-target loop (mirror task-54)
        return false

canContinueToUse @ HEAD:
    same — veto when managedCropCell positively true at targetPos
```

**`HarvestCropVetoPolicy`:** uses `ManagedCropDomainPolicy.isManagedCell` only — **not**
`harvestCandidate`. Reads profile for ally check (veto path does not call `VillageWorkAdmission`).

## Episode admission (locked)

```text
episodeAdmission(mob, observation, liveClaim, now) :=
    VillageWorkAdmission.evaluate(profile, observation, combatTarget, liveClaim, now).permitted
```

**Episode `canUse` stack:**

```text
1. episodeAdmission → permit
2. GameRule mobGriefing
3. mob.getTarget() == null
4. findClosestHarvestCandidate() != null   // uses harvestCandidate predicate
```

**Episode `canContinueToUse`:**

```text
phase in {PATH, WINDUP}  // not yet mutating
AND target != null
AND alive, no combat target
AND episodeAdmission still permits
AND harvestCandidate still true at target   // maturity + feasibility may have changed
```

**No repair exception.** COMMIT is synchronous inside one goal tick; if admission fails during
SCAN/PATH/WINDUP, `stop()` abandons with no mutation.

## Atomic crop transaction (locked — normal path)

```text
PREPARE
  revalidate exact target BlockState (mature supported crop)
  revalidate farmland/support unchanged
  drops = Block.getDrops(state, level, pos, be=null, entity=mob, tool=mainHand)  // ONCE
  stage drops locally (no inventory mutation yet)
  plantingUnit = first staged replant-material drop
                 OR exactly one unit from mob inventory reserve (crop-specific)
  if plantingUnit == null → ABORT (world unchanged)

RESERVE
  if plantingUnit came from mob inventory:
      temporarily remove exactly one from inventory into transaction escrow

COMMIT
  newState = CropReplantSemantics.ageZero(originalState)
  if !level.setBlock(pos, newState, flags):   // Gate 0 pins flags + return semantics
      rollback RESERVE
      ABORT (mature crop preserved — Gate 0 must confirm)
  verify CropReplantSemantics.supportedCrop(ageZero) at pos

ON COMMIT VERIFIED
  consume exactly one planting unit (from staged or escrow)
  bank remaining replant-material drops FIRST
  bank food/output drops SECOND
  overflow only non-required surplus (Gate 0 pins insert failure semantics)
  DONE

ON COMMIT FAILED
  restore escrowed inventory unit
  discard staged drops (never granted)
  ABORT — no destroyBlock, no repair publisher
```

**Explicit bans on normal path:** `destroyBlock()` · deliberate air intermediate ·
`MandatoryOwnershipRegistry.publish` · floor pickup for replant stock.

**Beetroot / wheat examples (staged-drop logic):**

```text
beetroot, staged drops = 0 seeds, mob held = 0 → ABORT, crop stays mature
beetroot, staged drops = 0 seeds, mob held = 1 → RESERVE held seed → COMMIT → consume held seed
beetroot, staged drops = 2 seeds → COMMIT → consume 1 from staged, bank 1 replant surplus
```

## Episode state machine

```text
Phase: SCAN → PATH → WINDUP → COMMIT → DONE
         │      │       │         │
         │      │       │         └─ synchronous CropHarvestTransaction (one tick)
         │      │       └─ windup ticks (10, mirror host readability)
         │      └─ path timeout → stop, no mutation
         └─ empty → backoff (D-VR-083 numbers PROVISIONAL)
```

**Activity observation:** while `phase != IDLE`, classify as `VILLAGE_WORK`.

## Gate 0 audit checklist (revised — produces `task-55-gate0-report.md`)

**Gate 0 remains HOLD until User authorizes.** No implementation before closure.

### Host audit (read-only oracle)

| # | Question |
| --- | --- |
| H-1 | `HarvestCropsGoal` `mob` + `targetPos` field names/types |
| H-2 | `canUse` / `canContinueToUse` inject names (Yarn/intermediary) |
| H-3 | Does host leave `targetPos` populated after a RETURN veto would fire? |
| H-4 | Should veto clear `targetPos` like task-54 `clearTarget`? |
| H-5 | Exact host targeted crop set (`ForagePolicy` oracle) vs V3 superset |

### Vanilla crop audit

| # | Question |
| --- | --- |
| V-1 | wheat/carrot/potato/beetroot maturity API (`CropBlock.isMaxAge`, etc.) |
| V-2 | age-zero `BlockState` construction per crop |
| V-3 | planting item mapping per crop |
| V-4 | `Block.getDrops` behaviour per crop — can wheat/beetroot yield **zero** planting items? |
| V-5 | `getDrops` context required (tool, fortune, entity) for faithful staging |
| V-6 | Valid farmland support predicate (crop block + farmland below) |

### Atomic commit audit (**load-bearing**)

| # | Question |
| --- | --- |
| A-1 | `Level#setBlock` return semantics on crop replacement |
| A-2 | mature → age-0 direct `setBlock` — does failed call preserve old crop? |
| A-3 | Suitable `setBlock` flags (block update, notification) |
| A-4 | Block/crop callbacks on `setBlock` — can they create bare farmland from V3 action? |
| A-5 | Is `destroyBlock` ever required for correct drop semantics, or does `getDrops` + `setBlock` suffice? |

### Inventory audit

| # | Question |
| --- | --- |
| I-1 | SPM backpack insertion API (reflective compat — Gate 0 pins class/method) |
| I-2 | Capacity / failure semantics when banking staged drops |
| I-3 | Banking order: replant surplus before food/output |

### Compatibility audit

| # | Question |
| --- | --- |
| C-1 | Reflective `OptionalHarvestCropTargetResolver` shape |
| C-2 | Hook observation / warm-up diagnostics (mirror `StorageGuardCompatibility`) |
| C-3 | Fail-open when managed-domain evidence genuinely unavailable |

### Gate 0 decision gate (**mandatory exit criterion**)

```text
IF direct mature→age0 setBlock can be locked such that task-55 never deliberately
   creates a bare intermediate state on the normal path:
   → V3-C ships with NO new MandatoryOwnership publisher

ELSE IF source audit proves an exceptional V3-caused bare state still requires
     asynchronous repair:
   → Gate 0 STOP
   → reopen MandatoryOwnership for identity-bound multi-publisher semantics
   → do NOT proceed to task-55 implementation
```

**Gate 0 exit criteria:** mixin field access **LOCKED**; `CropReplantSemantics` table **LOCKED**;
atomic commit path **LOCKED** or **STOP** per decision gate; fail-open veto **LOCKED**.

## Test matrix (static — VR-T3a–c/k/l/m)

| ID | Test class (proposed) | Scenario |
| --- | --- | --- |
| **VR-T3a** | `CropHarvestTransactionTest` | mature managed crop + planting unit → age-0 at same pos; no `destroyBlock` |
| **VR-T3b** | `VillageHarvestEpisodePolicyTest` | interrupt during WINDUP / admission loss → no `setBlock` |
| **VR-T3c** | `CropHarvestTransactionTest` | PREPARE infeasible → no mutation; COMMIT `setBlock` false → escrow restored, crop preserved |
| **VR-T3k** | `CropHarvestTransactionTest` | stale target after first actor commits → second ABORT |
| **VR-T3l** | `HarvestCropVetoPolicyTest` | managed cell + hungry + admission refused → veto; immature managed cell still vetoed; wilderness false |
| **VR-T3m** | `CropHarvestTransactionTest` | multi-cycle staged banking; beetroot zero-drop + zero-held aborts; no floor pickup |
| **domain** | `ManagedCropDomainPolicyTest` | immature supported crop is managed; maturity not required; wheat included |
| **structural** | `ManagedCropDomainStructuralTest` | no SPM `ForagePolicy` import in production; no `MandatoryOwnershipRegistry` in crop package |
| **structural** | `VillageHarvestEpisodeWiringTest` | episode delegates admission; no duplicate profile gate |

**Negative controls:** veto false when memory empty; `canUse` false when `VillageWorkAdmission` denies;
transaction abort leaves block state unchanged; no `MandatoryOwnershipRegistry.publish` call sites in crop package.

**Runtime rows:** `UNVERIFIED` — batched V3 campaign.

## MAIBS prediction (pre-implementation)

**Scenario:** `VILLAGE_ALLY`, remembered village, **mature wheat** inside bounds, admission clear,
staged drops include ≥1 seed, reachable.

| Time | Prediction |
| --- | --- |
| T+0 | P4 episode selects wheat (host P6 would ignore it); veto blocks host on managed cells |
| T+15s | PATH + WINDUP; admission loss → clean abort, wheat still mature |
| T+20s | COMMIT: wheat age-0, seeds banked, no air intermediate |
| T+60s | Immature regrowth not a harvest candidate; managed cell still vetoed for host |

**Failure modes (runtime campaign):** mixin silent no-op; `setBlock` failure path preserves crop;
memory peek stale → veto fail-open.

## Documentation updates (implementation phase only)

| File | When |
| --- | --- |
| `task-55-gate0-report.md` | Gate 0 close |
| `task-55-report.md` | implementation handoff |
| `progress.md` | task complete line |
| `docs/porting/TEST_MATRIX.md` | VR-T3a–c/k/l/m static rows |

## Frontier after this brief

1. User reviews brief v2.
2. When satisfied: **authorize task-55 gate 0** → read-only audit → `task-55-gate0-report.md` with
   atomic-commit decision gate outcome.
3. Gate 0 closure → **authorize task-55** for implementation.
4. Runtime witnesses batch in V3-G campaign (Minecraft closed until explicit approval).

## Verdict table (User peer review 2026-08-21)

| Item | Status |
| --- | --- |
| Managed-domain / host-veto concept | **ACCEPT** |
| Committed P4 episode | **ACCEPT** |
| Direct own-drop banking | **ACCEPT** |
| No hunger gate | **ACCEPT** |
| Memory-peek / bounded scanning | **ACCEPT** |
| `VILLAGE_WORK` classifier pin | **ACCEPT** |
| `ForagePolicy` compile import | **REJECT** |
| Wheat exclusion | **REJECT** |
| Ripeness as domain identity | **REJECT** |
| `destroy` → replant normal path | **REJECT** |
| Second `MandatoryOwnership` publisher | **REJECT** for task-55 |
| Repair / admission continuation exception | **REMOVE** via atomic commit |
| task-55 Gate 0 | **HOLD** |
