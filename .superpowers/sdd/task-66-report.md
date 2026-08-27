# Task 66 report: V4-E — VillageInteractionDirector + existing COMMUTE integration

## Outcome

**Status:** DONE / STATIC+PACKAGE ACCEPTED.  
**Runtime:** UNVERIFIED; no Minecraft launch was authorized or performed.  
**Commit:** none.

V4-E adds the production village-orchestration facade and binds its revalidated directive to the
existing `ExploringGoal` COMMUTE executor. It does not add another movement Goal, navigation state,
market executor, or persisted authority.

## Implemented boundary (`CODE_CONFIRMED`)

- `VillageInteractionDirector` is the sole production assembler of live `WorkDemandPolicy`,
  `ExistingRouteFeasibility`, remembered village/trader capability facts,
  `SettlementDestinationRanker`, Opinion inputs, and `VillageIntentRegistry`.
- `SettlementOpinionInputs` keeps affect/Opinion composition inside the Opinion package; village
  orchestration receives an immutable/non-creating snapshot boundary and does not import
  `AffectiveState`, `PersonalityModel`, or their coefficient math.
- `CommuteDirective` carries only the current settlement anchor, `REQUIRED_TRADE` purpose, and an
  identity-bound reference to the exact immutable `VillageIntent`. Equal replacement values do not
  match the old binding.
- `ExploringGoal` imports only director/directive/evaluation types from V4. It continues to own the
  sole `ExpeditionState` and `NavigationState`, path creation, progress, retry and terminal failure
  budget.
- Existing V1.5 `SettlementReturnPolicy` COMMUTE remains intact and continues to use the 64-block
  presence boundary. Required trade bypasses that qualification and uses deterministic <=150-block
  legs whose final X/Z target is the current settlement anchor.
- A bound required-trade commute calls `VillageIntentRegistry.revalidate(...)` through the director
  at continuation/resumption and leg-transition boundaries. Combat produces `INTERRUPTED`, drops
  disposable navigation through ordinary `stop()`, and retains the exact expedition/intent binding.
  CLOSED facts delete the old expedition without publishing route failure.
- Arrival conditionally releases only the exact current intent and ends V4 ownership. No live offer,
  affordability, or transaction API appears in the director or movement Goal; existing V2 owns the
  next local decision.
- `VillageRouteAttemptRegistry` records a 600-tick temporary destination demotion only after the
  existing six-failure COMMUTE budget terminates with `PATH_FAILURE`. It is capped at 16 settlement
  rows per loaded mob, prunes expired rows physically, and is cleared on unload/death/server stop.
  Stop, combat, simulation frontier, one failed probe, demand invalidation and ordinary replanning
  produce no failure evidence.

## Acceptance evidence

Deterministic tests prove:

- live displaced demand opens REQUIRED_TRADE to a non-home, zero-familiarity settlement;
- ranking facts without live demand cannot open intent or movement admission;
- equal replacement intents at one destination cannot inherit an old exact binding;
- V1.5 return-policy and broad presence-bound chaining remain present;
- required-trade final-leg generation targets the actual anchor instead of the 64-block boundary;
- binding lives in durable expedition state and not disposable navigation state;
- `stop()` drops navigation without route-failure publication;
- arrival releases travel ownership with no market work;
- terminal route failure creates bounded temporary evidence, expiry physically prunes it, arrival
  clears it, and owner lifecycle clears all rows;
- structural scans find no `VillageTravelGoal`, `RequiredTradeTravelGoal`, second navigation state,
  second retry machine, merchant offer access, or production dependency inversion.

## Alternatives and risks

Rejected: composing V4-A/B/C/D directly in `ExploringGoal`, because it makes movement another
village/progression brain. Rejected: a new required-trade Goal, because it duplicates navigation,
retry and scheduler arbitration. The selected facade-plus-binding approach adds source-specific
waypoint generation while preserving one physical executor.

The least-verified claim is physical anchor handoff. A large village's remembered trader may be
farther than V2's 16-block live discovery radius from the settlement anchor. V4-G must falsify that
integration boundary with the locked single-village changed-offer witness; static evidence cannot
claim the journey, interruption, arrival, or transaction works in Minecraft.

## Verification (`CONFIRMED`)

```text
.\gradlew.bat test --tests "com.noobk.spmscavenger.village.interaction.*" \
  --tests "com.noobk.spmscavenger.goal.*Commute*" \
  --tests "com.noobk.spmscavenger.village.intent.*"
.\gradlew.bat clean build
```

- Production tests: **1,707**, failures/errors: **0**
- Validation tests: **57**, failures/errors: **0**
- Production/validation package audits: PASS
- Production validation-namespace classes: **0**
- Production upstream `games/brennan/tradeeverything/**` classes: **0**
- Classes duplicated between production and validation JARs: **0**

Artifacts:

- `build/libs/spmscavenger-1.11.0.jar`  
  SHA-256 `6593D528E7398C8EDC32F931FF759A42B1FABB14E402564E08E12C29946F3E45`
- `build/libs/spmscavenger-1.11.0-validation.jar`  
  SHA-256 `BB02D551AEED4733434A3756401A9B520091C4056477A7C347CD656CC5F546A0`

## MAIBS disposition

Static implementation matches the predicted authority loop: required route may preempt ordinary
return/exploration; combat deletes path but not reason; resume revalidates; demand loss closes the
old route; final arrival hands to V2. This is **BEHAVIORALLY_PLAUSIBLE**, not runtime-confirmed.
V4-F first-home promotion remains the next implementation slice; V4-G remains the sole representative
runtime integration session after A–F.

## Closure addendum — Social Player Mobs v0.89 → v0.96 host sync (2026-08-27)

**Status:** DONE / STATIC+PACKAGE ACCEPTED. The canonical Fabric 1.21.1 compile/test/reference
baseline is now Social Player Mobs **v0.96.0**. Runtime behavior on v0.96 remains **UNVERIFIED**;
accepted v0.89 runtime evidence was preserved as historical evidence and was not relabelled.

Pinned upstream evidence:

- v0.89 source commit `a1bd88bfe7605bcc6f7c409669012afc8a47d448`;
- v0.96 source commit `38ebf89f2f8464e41d7c47be197b2ff27ef9edec`;
- `playermob-fabric-0.96.0+1.21.1.jar` SHA-256
  `508EDA58611A2A0738E257F98C2E14C5032C6EFBF5B1A985C9F93EE295131097`.

The full load-bearing verdict table is canonical in the RFC host-baseline section. Existing goal
priorities and seams remain compatible, with two required repairs caused by v0.94's new P1 fire
goals: `DouseFireInPathGoal` is now centrally classified as MOVE+LOOK mandatory safety;
`FlintAndSteelIgniteGoal` is centrally classified as MOVE+LOOK mandatory combat and is included in
the shelter target-provenance mixin. Deterministic tests prove neither falls through activity or
MOVE-holder unknown classifications. This was selected over leaving them unknown (which would
fail closed and suppress unrelated discretionary work) or labelling both combat (which would
misrepresent target-independent fire safety).

The audited v0.96 `registerGoals()` table is:

| Priority | Goal-selector entries | Flags |
| --- | --- | --- |
| 0 | `FloatGoal`; `FireBucketGoal` | JUMP; MOVE+LOOK |
| 1 | `CommandedActionGoal`, `TrainRecoveryGoal`, `FleeFromCategoryGoal`, `SkepticalWatchGoal`, `FriendlyGreetGoal` | MOVE+LOOK |
| 1 | `PlayerMobDoorGoal`, `BlockArrowsGoal`, `DigThroughGoal` | none |
| 1 | `DoorOperationGoal`, **`DouseFireInPathGoal`**, **`FlintAndSteelIgniteGoal`** | MOVE+LOOK |
| 2 | `TntCombatGoal`, `EndCrystalCombatGoal`, `SeekAmmoGoal`, `FollowLovedOneGoal`, `StayNearGoal` | MOVE+LOOK |
| 2 | `WeaponAwareAttackGoal` | MOVE+LOOK+JUMP |
| 3 | `EatFoodGoal` | LOOK |
| 3 | `RaidContainersGoal`, `RaidArmorStandsGoal`, `CollectFloorItemsGoal` | MOVE+LOOK |
| 6 | `HarvestCropsGoal` | MOVE+LOOK |
| 7 | `AdvanceCarriageGoal`, `CrossGroupGapGoal` | MOVE+LOOK |
| 8 / 9 / 10 | vanilla stroll / look-at / random-look | MOVE / LOOK / LOOK |

Target selector remains P1 `HurtByTargetGoal` then `DefendLovedOneGoal`, P2 reactive nearest
attackable target, P3 `HuntForFoodGoal`, all TARGET owners. Source diff found no reordered existing
entry: the only goal-table additions are the two bold P1 fire goals.

`HarvestCropsGoal`, `RaidContainersGoal`, and `FriendlyGreetGoal` retain their attachment seams.
Harvest and Raid timing is reaction-scaled; Raid also has the new earlier `searchContainers` host
gate. Therefore future T3g must explicitly enable that host setting, and T3l must attach against
v0.96 with a timing-tolerant window. Delayed retaliation changes acquisition timing but does not
replace target truth. Pet-owner fallback mixins do not overlap any Scavenger target class.

Negative source-diff probes recorded: no FriendlyGreet source change; no PlayerMob death/removal
change; no Scavenger pet-owner mixin target overlap; no existing goal-priority change outside the
two added P1 fire goals.

Final verification:

- `gradlew.bat clean build`: **PASS**;
- production tests: **1,711**, failures/errors/skips **0**;
- validation tests: **57**, failures/errors/skips **0**;
- production/validation package audits: **PASS**;
- packaged SPM classes: **0** in both artifacts;
- packaged Trade Everything classes: **0** in both artifacts;
- production validation namespace / Task-59 controller classes: **0**;
- duplicate production/validation classes: **0**.

Artifacts:

- `build/libs/spmscavenger-1.11.0.jar` — SHA-256
  `67F3F063DD22312FF08BF1DFC0431B13749450B430AC6DFBDF38E6FEA1B0A3AB`;
- `build/libs/spmscavenger-1.11.0-validation.jar` — SHA-256
  `5CAF12091A17A96B7D09D502F7FA2467A6C5E193E4F07510F1F0EA5D23DD0EFF`.

No Minecraft launch and no commit were performed. Task-66 is closed; V4-F is the next product
slice.
