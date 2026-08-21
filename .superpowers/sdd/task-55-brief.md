# Task 55 brief: V3-C committed harvest→replant episode (`D-VR-079`, `D-VR-079-A1`)

**Slice:** one target-bound **managed crop** harvest→replant episode at priority **4**, plus a
continuous host-`HarvestCropsGoal` veto inside the **managed crop domain** that fails toward stock
when the domain cannot be positively established.

**Authorization gates (LOCKED sequence):**

| Gate | Status | User phrase to authorize |
| --- | --- | --- |
| **Brief design** | **v1 — initial draft** | (this document) |
| **Gate 0 — read-only source audit** | **OPEN** | **authorize task-55 gate 0** |
| **Gate 0 closure** | **BLOCKED** — mixin field access, drop/replant semantics, `ForagePolicy` import | — |
| **Full implementation** | **NOT AUTHORIZED** | **authorize task-55** / **Implement V3-C** |

**Target:** `d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

**Source reference:** `d:\Apps\Minecraft Port\Projects\references\SocialPlayerMobs-v0.86.0`

**Source evidence (host crop goal — read-only oracle):**

| Path | Evidence |
| --- | --- |
| `.../entity/goal/HarvestCropsGoal.java` | Priority **6**, `MOVE\|LOOK`; `canUse` gated on `wantsFood()`; phases `IDLE → PATHING → HARVESTING`; `targetPos` private; harvest banks edible drops, spills seeds via `dropAtLocation`, `destroyBlock(..., false)` — **never replants** |
| `.../entity/PlayerMobEntity.java:842` | Registers `HarvestCropsGoal(this, 0.9, scanRadius 8)` at priority **6** |
| `.../entity/ForagePolicy.java` | `isRipeFoodCrop` = max-age carrots/potatoes/beetroots only; wheat excluded; `wantsFood` pure predicate |
| `.../entity/goal/HarvestCropsGoal.java:195-207` | `Block.getDrops` → edible to backpack, non-edible spilled — **F8 anti-pattern** V3-C must not mirror |

**Depends on (DONE / STATIC ACCEPT):**

| Task | Deliverable | V3-C use |
| --- | --- | --- |
| **task-52** | `MandatoryOwnership` | post-mutation repair publisher; blocks V3 while live |
| **task-53** | `VillageScenarioProfile`, `VillageWorkAdmission`, `VILLAGE_WORK` taxonomy | admission + `VILLAGE_ALLY` gate; **no** settlement/crop logic inside admission |
| **task-54** | storage safety (orthogonal) | **must not** import storage types into crop hot path |

**RFC:** `plans/RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md` — D-VR-079, D-VR-079-A1, D-VR-082-A1,
D-VR-083 (budget contract), VR-T3a–c/k/l/m.

**Not authorized without separate implementation authorization:** production Java · mixin wiring ·
Minecraft runtime launch · commit · push.

```text
  VILLAGE_ALLY profile          VillageMemory (peek only)
         │                              │
         └──────────┬───────────────────┘
                    ▼
         ManagedCropDomainPolicy     ← geography only; NO SettlementRelationship
                    │
      ┌─────────────┴─────────────┐
      ▼                           ▼
HarvestCropsVetoMixin      VillageHarvestEpisodeGoal (P4)
(host P6 — fail open)       VillageWorkAdmission + episode preflight
      │                           │
      │                           ▼
      │                    harvest → bank drops → replant (one tick)
      │                           │
      └──────── field stays planted when V3 refused / host blocked
```

## Why this slice exists

Pinned host `HarvestCropsGoal` is a **destructive hunger drive** at priority 6. A V3 episode at
priority 4 preempts it **only while `VillageWorkAdmission` passes**. When mandatory survival owns the
mob, admission refuses and the host path would strip managed village fields without replanting —
exactly VR-T3a's must-not-happen. **D-VR-079-A1** therefore requires:

1. a **managed crop domain** that exists **before** any episode claims a target;
2. a **continuous host veto** inside that domain (ShelterHold-family shape, not SPM fork);
3. an episode that **banks its own replant-capable drops** (F8) instead of depending on P3 floor pickup.

## Load-bearing requirements

| # | Requirement |
| --- | --- |
| 1 | **One committed episode owns the full loop** — candidate, reserve, target, preflight, harvest mutation, drop capture, replant mutation, invalidation, exceptional repair, completion (`D-VR-079`). **No** separately admitted `ReplantCropGoal` on the normal path. |
| 2 | **Managed crop domain** — pure predicate, no `SettlementRelationship` term (`D-VR-079-A1 §2`): `profile == VILLAGE_ALLY` **and** positive remembered village resolution **and** `SettlementBoundsPolicy.within(pos, anchor)` **and** ripe crop-on-farmland block truth. Domain is **pre-claim**; host cannot grab first and escape as "unmanaged". |
| 3 | **Fail direction on veto uncertainty** — cannot positively establish managed domain → **do not veto** host `HarvestCropsGoal`. Opposite of storage (`D-VR-081` deny-on-unknown). |
| 4 | **Host veto is continuous** on `canUse` **and** `canContinueToUse` for positions in the managed domain when predicate (2) is positively true. Wilderness crops and stock `HuntForFoodGoal` remain untouched (VR-T3l). |
| 5 | **Episode `canUse` consumes `VillageWorkAdmission` only for the mandatory gate** — not settlement, storage, population, or crop-specific checks inside admission itself (task-53 contract preserved). |
| 6 | **Episode is not hunger-gated** — unlike host P6. Village maintenance runs when admission passes; host hunger path is separately vetoed in-domain. |
| 7 | **Harvest + replant commit in one server tick** after reach + windup; success declared only when age-0 crop is present at the same position (`D-VR-079`). |
| 8 | **F8 own-drop banking** — episode captures `Block.getDrops` output directly; separates food/output vs replant material; replants from banked stock; **never** depends on `CollectFloorItemsGoal` to recover planting supply (VR-T3m). |
| 9 | **Crop-specific reserve accounting** — beetroot 0–3 seeds: zero spare after episode → pause further managed beetroot harvest; **do not** manufacture seeds. Safety invariant: farming may pause; it may not leave a successfully managed field barren. |
| 10 | **Post-mutation failure → mandatory bounded repair** publishes through `MandatoryOwnership` until restored or invalidated; blocks fresh discretionary work per `D-VR-082`. |
| 11 | **Pre-mutation interruption** — combat, command, shelter, higher-priority goals: candidate/path disposable; **no world mutation** (VR-T3b). |
| 12 | **Preflight abort** — seed/support/crop invalid before commit: no harvest; no bare farmland (VR-T3c). |
| 13 | **Multi-mob** — no global reservation map; second mob detects target invalidation after first commits (VR-T3k). Optimistic concurrency via block-truth revalidation at commit. |
| 14 | **Budget contract (`D-VR-083`)** — crop scan inspects **loaded** chunks only; finite radius; backoff when empty; **no** `VillagePerception.observe()` on episode or veto hot paths (memory **peek** only, same pattern as `SettlementStorageFactSource`). |
| 15 | **`MoveHolderClassifier` pin** — first real P4 executor: map `VillageHarvestEpisodeGoal` → `ActivityClass.VILLAGE_WORK` (deferred from task-53 scenario 12). |
| 16 | **Enforcement / diagnostics split** — veto policy and episode logic are pure/testable; mixin + optional resolver compat are thin; hook-observed flags for batched runtime campaign (mirror `StorageGuardCompatibility` pattern). |

## Rejected (locked)

| Proposal | Why rejected |
| --- | --- |
| `managed = positions the episode already claimed` | Host grabs first → bare farmland → "unmanaged" loophole (D-VR-079-A1) |
| Separate `ReplantCropGoal` at P4/P6 | Breaks ownership across interruption; bare farmland becomes normal state |
| Hunger-gate the V3 episode on `wantsFood()` | Conflates village maintenance with host forage; wrong admission semantics |
| Mirror host spill-to-floor for replant seeds | F8 — monotonic reserve drain; P3 pickup dependency |
| Manufacture replacement seeds for infinite beetroot loop | Safety invariant is pause-not-barren, not infinite farming |
| `SettlementRelationship` / HOME / HIGH in managed predicate | Reintroduces permission through relationship side door |
| Veto when village memory uncertain | Starves stock SPM food behaviour mod-wide; fail toward host |
| Global crop claim map / mutex | RET-1 risk; VR-T3k solved by commit-time block truth |
| `VillageWorkSelector` in this slice | Only one V3 executor ships; selector deferred until ≥2 intents compete |

## Package layout (proposed)

| Path | Role |
| --- | --- |
| `village/crop/ManagedCropDomainPolicy.java` | pure `isManaged(mob, level, pos)` — profile + memory peek + bounds + crop truth |
| `village/crop/HarvestCropVetoPolicy.java` | pure veto decision; **fail open** on uncertainty |
| `village/crop/CropReplantSemantics.java` | crop → seed item, age-0 state, drop classification (food vs replant) |
| `village/crop/HarvestEpisodeReserve.java` | per-episode banked replant stock (not persisted across episodes) |
| `village/crop/VillageHarvestEpisode.java` | episode state machine owner (target, phase, reserve, repair handle) |
| `village/crop/VillageHarvestEpisodePolicy.java` | preflight, commit, invalidation, repair predicates (unit-test surface) |
| `village/crop/HarvestCropGuardCompatibility.java` | diagnostics: hook observed, veto counts, session reset |
| `goal/VillageHarvestEpisodeGoal.java` | P4 `Goal` — scan/path/windup/delegate commit |
| `compat/OptionalHarvestCropTargetResolver.java` | reflect `HarvestCropsGoal.targetPos` (Gate 0 pins field name) |
| `mixin/HarvestCropsManagedDomainMixin.java` | `@Pseudo` continuous veto on host goal |
| `mining/MoveHolderClassifier.java` | add `VillageHarvestEpisodeGoal` → `VILLAGE_WORK` |
| `SpmScavenger.java` | register P4 goal beside `PlaceTorchGoal`; session lifecycle hooks |

**Explicitly NOT in this slice:** `VillageWorkSelector` · `VillageWorkIntent` enum beyond this goal ·
composting (V3-F) · population food (V3-E) · workstation facts (V3-D) · storage changes · config-at-spawn
ally default · operator crop commands (profile/storage commands suffice for test setup).

## Managed crop domain (locked predicate)

```text
managedCrop(mob, level, pos) :=
      profile(server, mob.uuid) == VILLAGE_ALLY
  AND exists KnownVillage v in VillageMemorySavedData.peek(level, mob.uuid)
      where SettlementBoundsPolicy.within(pos, v.anchor())
  AND blockTruth(level, pos) is ripe crop-on-farmland
```

**Crop truth:** reuse host `ForagePolicy.isRipeFoodCrop` via compile-time import (SPM is a declared
dependency). Gate 0 must confirm the method is public and stable on the pinned reference tag.

**Village resolution:** `VillageMemorySavedData.peek` — **never** `VillagePerception.observe()` on
this path. If memory is empty → not managed → veto does not fire.

## Host harvest veto (mixin — Gate 0 must pin)

**Shape:** `*ShelterHoldMixin` family — `@Pseudo`, `require = 0`, reflect `mob` + `targetPos`.

```text
canUse @ RETURN:
    if host returned false → pass through
  resolve mob, targetPos
  if mob == null || targetPos == null → pass through (do not break host)
  if HarvestCropVetoPolicy.shouldVeto(mob, level, targetPos) → return false
    (optional: clear targetPos if Gate 0 shows host loops on stale target)

canContinueToUse @ HEAD (or symmetric RETURN):
    same veto predicate → return false
```

**`HarvestCropVetoPolicy.shouldVeto`:**

```text
if ManagedCropDomainPolicy.isManaged(mob, level, pos) → true (veto host)
else → false (fail open — includes NEUTRAL profile, no village memory, unloaded/unknown)
```

## Episode state machine

```text
Phase: SCAN → PATH → WINDUP → COMMIT → DONE
         │      │       │         │
         │      │       │         └─ single-tick: getDrops → bank → destroy → setBlock(age 0)
         │      │       └─ mirror host windup ticks (10) for readable swing
         │      └─ path timeout / unreachable → abandon (no mutation)
         └─ empty → backoff (D-VR-083 contract numbers PROVISIONAL)
```

**`canUse` stack:**

```text
1. VillageWorkAdmission.evaluate(mob) → permit
2. profile == VILLAGE_ALLY
3. GameRule mobGriefing (episode mutates blocks)
4. mob.getTarget() == null (combat preempts — same as host)
5. findClosestManagedRipeCrop() != null
6. preflightReplant(candidate) → held seed OR harvest would yield ≥1 plantable item
```

**`canContinueToUse`:** phase not idle; target non-null; alive; no combat target; admission still
permit **or** episode already in COMMIT/repair (Gate 0 decides whether admission re-check aborts
mid-episode — **recommend:** permit continuation while `VillageHarvestEpisode` owns an active repair
mandate even if a transient admission blip would refuse a fresh `canUse`).

**Activity observation:** while `phase != IDLE`, classify running holder as `VILLAGE_WORK` (classifier
pin). Running `VILLAGE_WORK` blocks fresh discretionary selection (`DiscretionaryEligibility`).

## F8 drop banking (locked semantics)

```text
COMMIT:
  drops = Block.getDrops(state, level, pos, null, mob, mainHand)
  for each drop:
      if CropReplantSemantics.isReplantMaterial(state, drop) → episodeReserve.deposit(drop)
      else if edible → bank to mob inventory (mirror host edible path)
      else → excess inventory / dropAtLocation (non-replant surplus only)
  destroyBlock(pos, false, mob)
  replantItem = episodeReserve.withdrawOnePlanting()
  if replantItem empty → enter MANDATORY_REPAIR (publish MandatoryOwnership) — should not happen if preflight correct
  setBlock(pos, CropReplantSemantics.ageZero(state))
  release repair if replant verified
  deposit leftover replant stock back to mob inventory or abandon per policy
```

**Preflight `preflightReplant`:** simulate minimum drop guarantee per crop kind (wheat excluded from
managed set). Beetroot: require `heldSeeds > 0` **or** accept 0-yield risk by **refusing** harvest when
held seeds == 0 (locked: pause, don't gamble).

## Mandatory repair publisher

When commit destroys crop but replant write fails or bank lacks planting item:

```text
MandatoryOwnershipRegistry.publish(mob, repairOwnerId, cause=CROP_REPAIR, ...)
→ blocks VillageWorkAdmission and discretionary work
→ episode retries replant/reacquisition within bounded attempts/ticks
→ release on success or invalidate on external actor change
```

Gate 0 must pin existing `MandatoryOwnershipRegistry.publish` signature and choose a stable
`repairOwnerId` namespace (e.g. `spmscavenger:crop_repair:<pos>`).

## Gate 0 audit checklist (read-only — produces `task-55-gate0-report.md`)

| # | Question | Oracle |
| --- | --- | --- |
| G0-1 | `HarvestCropsGoal.targetPos` field name + type | decompile / reference source |
| G0-2 | `HarvestCropsGoal` Yarn/intermediary method names for inject | pinned SPM JAR / refmap |
| G0-3 | `ForagePolicy.isRipeFoodCrop` visibility + crop set | `ForagePolicy.java` |
| G0-4 | Replant age-0 state per crop block | `CropBlock.getStateForAge(0)` per block |
| G0-5 | `Block.getDrops` seed counts distribution for beetroot vs carrots | vanilla crop loot tables / unit fixture |
| G0-6 | `EquipmentEvaluator.addToContainer` accessibility for edible banking | SPM reference |
| G0-7 | `SpmScavenger` P4 registration site for new goal | `SpmScavenger.java:254` |
| G0-8 | `MoveHolderClassifier` insertion point for `VILLAGE_WORK` | existing classifier |
| G0-9 | `MandatoryOwnershipRegistry.publish` contract for repair slice | task-52 implementation |
| G0-10 | Existing mixin resolver patterns to mirror | `OptionalRaidContainerTargetResolver`, `OptionalGoalMobResolver` |

**Gate 0 exit criteria:** mixin field access **LOCKED**; replant table **LOCKED**; fail-open veto
**LOCKED**; no implementation until user closes Gate 0.

## Test matrix (static — VR-T3a–c/k/l/m)

| ID | Test class (proposed) | Scenario |
| --- | --- | --- |
| **VR-T3a** | `VillageHarvestEpisodePolicyTest` | mature managed crop + seed → commit leaves age-0 at same pos |
| **VR-T3b** | `VillageHarvestEpisodePolicyTest` | interrupt before COMMIT → no `Level.setBlock` / destroy |
| **VR-T3c** | `VillageHarvestEpisodePolicyTest` | invalid seed/support at preflight → no harvest; post-mutation fail → repair publisher |
| **VR-T3k** | `VillageHarvestEpisodePolicyTest` | stale target after first actor commits → second aborts without mutation |
| **VR-T3l** | `HarvestCropVetoPolicyTest` | managed + hungry + admission refused → veto true; wilderness → veto false; NEUTRAL → veto false |
| **VR-T3m** | `HarvestEpisodeReserveTest` | multi-cycle bank sustains replant; beetroot zero-seed pauses; no floor-pickup dependency |
| **structural** | `ManagedCropDomainStructuralTest` | no `SettlementRelationship` import; veto policy does not import episode goal |
| **structural** | `VillageHarvestEpisodeWiringTest` | admission delegation only; no direct `MandatoryOwnershipRegistry` in admission |
| **parse** | (none expected) | no new commands in v1 |

**Negative controls:** veto true when memory peek empty; episode `canUse` false for NEUTRAL;
`HarvestCropVetoPolicy` returns false when `targetPos` null; repair publisher released on successful replant.

**Runtime rows (VR-T3a–m live):** `UNVERIFIED` — batched V3 runtime campaign; not task-55 Gate 0 scope.

## MAIBS prediction (pre-implementation)

**Scenario:** `VILLAGE_ALLY` mob, remembered village, mature carrot row inside bounds, mandatory
clear, held carrot, reachable.

| Time | Prediction |
| --- | --- |
| T+0 | P4 episode wins over P6 host in-domain; host veto would block P6 even if hungry later |
| T+15s | Path + windup; no explore/trade interleave if `VILLAGE_WORK` running blocks fresh discretionary |
| T+20s | Commit: crop age-0, edible in backpack, no floor seeds for replant |
| T+60s | Second episode selects another mature crop; reserve from prior banked seeds if any |

**Failure modes to watch (runtime campaign):**

- Mixin `require = 0` silently not applying → host strips field (VR-T3l witness)
- Memory peek stale after long absence → veto fails open; host may harvest until perception refreshes
- `PlaceTorchGoal` co-tenancy at P4 causes flicker — acceptable per D-VR-082-A1
- Preemption mid-windup must not leave bare farmland (VR-T3b)

## Documentation updates (implementation phase only)

| File | When |
| --- | --- |
| `task-55-gate0-report.md` | Gate 0 close |
| `task-55-report.md` | implementation handoff |
| `progress.md` | task complete line |
| `docs/porting/TEST_MATRIX.md` | VR-T3a–c/k/l/m static rows |
| `PORTING_GUIDE.md` | only if commands/tooling change (not expected v1) |

## Frontier after this brief

1. User reviews brief v1 → peer-review amendments if needed.
2. **authorize task-55 gate 0** → read-only audit → `task-55-gate0-report.md`.
3. Gate 0 closure → **authorize task-55** for implementation.
4. Runtime VR-T3 witnesses batch with task-54 storage + future V3-D/E/F in **V3-G** campaign (Minecraft
   remains closed until explicit launch approval).
