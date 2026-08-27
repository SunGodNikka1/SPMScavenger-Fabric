# Task 66 brief: V4-E — VillageInteractionDirector + existing COMMUTE integration

## Status and target

**Status:** V4-E implementation complete; closure PAUSED for authorized host-baseline sync, 2026-08-27.  
**Target:** `D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`  
**Canonical source reference:** `D:\Apps\Minecraft Port\Projects\references\artifacts\playermob-fabric-0.96.0+1.21.1.jar`  
**Historical comparison reference:** `D:\Apps\Minecraft Port\Projects\references\artifacts\playermob-fabric-0.89.0+1.21.1.jar`  
**Canonical decisions:** D-VR-091–093; V4-E only.  
**Report:** `.superpowers/sdd/task-66-report.md`

## Source evidence (`CODE_CONFIRMED`)

- `ExploringGoal` already owns the sole `ExpeditionState` durable-route / `NavigationState`
  disposable-path split, <=150-block chained COMMUTE legs, path probing, progress detection, and
  terminal six-failure budget.
- Existing V1.5 COMMUTE seeds only through `SettlementReturnPolicy` and terminates chaining inside
  `SettlementBoundsPolicy`'s 64-block presence envelope.
- `VillageIntentRegistry.revalidate(...)` is authoritative; `current()` is explicitly diagnostic or
  post-revalidation consumption only.
- `VillageMemorySavedData.rankingFacts(...)`, `SettlementDestinationRanker`,
  `SettlementOpinionBias`, `WorkDemandPolicy`, and `ExistingRouteFeasibility` already provide every
  fact needed by a production orchestration facade.
- `RouteAttemptEvidence` is immutable, bounded to 16 entries, and deliberately has no producer.
- Negative probes: `VillageInteractionDirector` — **NOT FOUND**; `CommuteDirective` — **NOT FOUND**;
  route-attempt history writer — **NOT FOUND**.

## Binding architecture

1. Add `VillageInteractionDirector` as the only production assembler of live demand, existing-route
   status, remembered capability facts, Opinion context, destination ranking, intent opening, and
   intent revalidation.
2. Return a `CommuteDirective` containing the actual anchor and a binding to the exact immutable
   `VillageIntent` instance. It is movement admission only; it contains no offer, price, path, or
   cached authorization.
3. `ExploringGoal` consumes only director evaluations/directives. It never imports the ranker,
   Opinion composer, KnownVillager logic, or live merchant types.
4. Existing COMMUTE gains a source distinction: `SETTLEMENT_RETURN` retains its exact V1.5 64-block
   completion rule; `REQUIRED_TRADE` uses the same path machinery but chains until a final leg aimed
   at the current settlement anchor completes.
5. Every continuation/resumption revalidates through the director. `INTERRUPTED` retains the exact
   intent binding while `stop()` drops navigation; `CLOSED` discards that expedition; `ACTIVE`
   permits a fresh path or continued path.
6. Terminal `PATH_FAILURE` on a bound required-trade COMMUTE alone records temporary demotion.
   Interruptions, simulation frontiers, lifecycle release, demand loss, stop, or ordinary replans do
   not. History is transient, bounded per loaded mob, and released on unload/death/server stop.
7. Arrival releases the travel intent and ends V4 ownership. Existing V2 alone may inspect the live
   market and transact.

## Alternatives and decision

| Option | Benefit | Failure mode | Disposition |
| --- | --- | --- | --- |
| Put V4-A/B/C/D assembly directly in `ExploringGoal` | Fewer classes | Movement executor becomes a second village/progression brain | Rejected |
| Add `RequiredTradeTravelGoal` | Isolated code path | Duplicates navigation lifetime, retry state, priority arbitration, and interruption semantics | Rejected |
| Director facade + exact binding + existing COMMUTE | One authority seam and one physical executor | Existing COMMUTE becomes more stateful and needs source-specific completion tests | Selected |

Switch only if runtime V4-G proves the existing COMMUTE path machinery cannot physically service
settlement anchors without changing V1.5 behavior; that evidence would justify refactoring the one
executor, not adding a parallel Goal.

## Behavioral Prediction — MAIBS-1

At T0, an iron-tool demand with exhausted local acquisition and remembered trader evidence produces
one bound REQUIRED_TRADE directive. A LOW-familiarity/non-home village is legal because this route
does not call `SettlementReturnPolicy`. At 300 horizontal blocks, existing COMMUTE walks successive
<=150-block expeditions; the final expedition is aimed at the settlement anchor and does not stop at
the 64-block familiarity envelope.

Combat or shelter preempts priority-8 MOVE. `stop()` deletes only the `Path`; the exact intent binding
survives. On resumption the director re-reads demand, route status, and destination memory. If still
ACTIVE, a fresh path serves the same binding. If demand disappeared during combat, CLOSED destroys
the old expedition and it cannot be inherited by a replacement intent at the same anchor. Arrival
releases intent and V2 performs fresh local market discovery.

Predicted weird behaviors:

1. A village anchor can be more than V2's 16-block discovery radius from the remembered trader in a
   large settlement — `RUNTIME_QUESTION`; V4-G must falsify the chosen anchor handoff without adding
   stale trader position authority in E.
2. Simulation-frontier interruption may repeatedly postpone a remote trip without demoting the
   destination — `ACCEPTABLE_STEPPING_STONE`; unloaded terrain is not route infeasibility.
3. A stale positive hint can lead to a fruitless arrival — `INTENDED`; memory is reason to
   investigate, and live V2 may find no current offer.
4. Priority-8 COMMUTE can be delayed by legitimate higher-priority local work —
   `ACCEPTABLE_STEPPING_STONE`; the intent mints no scheduler authority.

**Falsifiers:** old navigation resumes after demand loss; combat publishes route failure; a
replacement same-anchor intent inherits the prior expedition; V1.5 return starts targeting anchors
instead of its established 64-block envelope; or V4 executes a cached market fact.

**Gate:** `PASS — BEHAVIORALLY_PLAUSIBLE` for static integration only. Actual travel, interruption,
arrival, and changed-offer behavior remain `UNVERIFIED` until V4-G runtime.

## TDD and verification

RED then GREEN covers director opening/revalidation, LOW/non-home admission, exact binding,
source-specific commute completion, interruption/resume, demand-loss closure, replacement isolation,
arrival handoff, terminal-only transient demotion, lifecycle cleanup, and structural dependency
boundaries.

```text
.\gradlew.bat test --tests "com.noobk.spmscavenger.village.interaction.*" \
  --tests "com.noobk.spmscavenger.goal.*Commute*"
.\gradlew.bat clean build
```

**Must happen:** a valid required-trade intent can use the sole COMMUTE executor to reach the actual
settlement anchor, survive legitimate interruption, and resume only after fresh revalidation.  
**Must not happen:** intent existence alone authorizes motion, V4 owns market execution, an ordinary
interruption creates route-failure evidence, or a second Goal/navigation/retry lifetime appears.

No Minecraft launch. No commit or push.

## Host-baseline sync addendum (authorized 2026-08-27)

Before V4-E closes, replace the canonical Social Player Mobs Fabric 1.21.1 reference baseline
`v0.89.0` with upstream `v0.96.0`, but only after a source-and-artifact delta audit establishes the
load-bearing compatibility surface. Audit the complete host goal priority/flag table, the new fire
goals and activity taxonomy, Harvest/Raid/FriendlyGreet mixin seams, `searchContainers`, target and
reaction-speed timing, lifecycle/removal/death, pet-owner mixins, and every SPM symbol referenced by
Scavenger. Each retained v0.89 assumption receives one of `UNCHANGED`, `COMPATIBLE CHANGE`,
`REQUIRES SCAVENGER REPAIR`, or `OBSOLETE`.

Two viable sync strategies were considered:

| Option | Benefit | Failure mode | Disposition |
| --- | --- | --- | --- |
| Change only the documented/JAR pin and trust optional mixins | Smallest diff | Silent optional-mixin detachment and new host Goals falling through authority classification | Rejected |
| Pin both source revisions and artifact, audit every seam, then apply compatibility-only repairs | Falsifiable compatibility boundary | More static work before the dependency moves | Selected |

**Must happen:** v0.96 becomes the canonical compile/test/reference baseline and every load-bearing
V3/V4 host assumption has an explicit delta verdict.  
**Must not happen:** the sync introduces new Scavenger behavior, treats build success as runtime
proof, or classifies the new fire goals merely to silence tests without inspecting their real MOVE /
LOOK semantics.

The complete production + validation build and package audits are required. Minecraft launch and
commit remain unauthorized.
