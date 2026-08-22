# Task-57 peer review — V3-E population food (`Agent_Cursor`, MRFC-1)

**Review type:** `REVIEW` / `DESIGN` — brief v1.1 + Gate 0 report; **no implementation diff** (none exists).

**Reviewer:** `Agent_Cursor`  
**Date:** 2026-08-21  
**Artifacts:** `task-57-brief.md` v1.1 · `task-57-gate0-report.md` · `RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md` (V3-E, D-VR-078/083-A1/084)

**Implementation probe (`CONFIRMED`):** `PopulationFoodSupport*` — **NOT FOUND** in `src/main` (0 files). Review is **pre-implementation**.

---

## Verdict

| Layer | Status | Notes |
| --- | --- | --- |
| **Brief v1.1** | **ACCEPT WITH MINOR AMENDMENTS** | Architecture sound; fix doc drift; add 3 pre-ship clarifications |
| **Gate 0** | **ACCEPT** | Evidence pinned; G0-A/G0-B correctly folded into brief |
| **RFC alignment** | **PASS** | Matches locked D-VR-078, 083-A1, 082-A1, 084; does not reopen deleted subtraction authority |
| **Dependency closure** | **PASS** | task-52…56 deliverables present in tree |
| **Implementation authorization** | **READY** after brief v1.2 nits (below) — still requires explicit **authorize task-57** |

---

## What is strong (agree)

1. **Authority split is correct and non-reopenable.** Settlement candidacy (`PopulationSupportVacancyPolicy`) vs breeder-local commit proof (`BreederLocalHomeProof`) mirrors D-VR-083-A1 and task-56 closure. Collapsing layers would recreate the rejected `freePopulationCapacity` failure mode.

2. **G0-A dual reachability is the highest-value lock.** Proving PlayerMob→recipient (A) cannot substitute for villager→HOME (B) is the difference between “food on the ground near a bed” and “vanilla-plausible breeding support.” Mandatory static negative control (brief + gate0) is appropriate.

3. **G0-B commit/ACK separation** avoids the classic “spawned `ItemEntity` = success” lie and binds anti-loop cooldown to **outcome**, not merely “episode ended.” Aligns with AV-1 and VR-T3e.

4. **Episode shape mirrors proven V3-C.** `VillageHarvestEpisodeGoal` PATHING → late WINDUP re-admission → single commit (`VillageHarvestEpisodeGoal.java` L166–197) is the right template; population food maps cleanly to HANDOFF_PREPARE/COMMIT/ACK_WAIT.

5. **Expendability honesty.** Gate 0 correctly reports food is **unmodelled** in `SellReserveModel` (`OptionalInt.empty()` — `SellReserveModel.java` L82–85). A dedicated `PopulationFoodExpendabilityPolicy` is required; delegating to trade sell path would be wrong.

6. **Minimum P4 arbitration.** Shared `MOVE|LOOK` at priority 4 + `VillageHarvestEpisodeGoal` already registered (`SpmScavenger.java` L259–260) — T57-10 is testable without premature `VillageWorkSelector`.

7. **Admission seam preserved.** `VillageWorkAdmission` remains profile + mandatory only (`VillageWorkAdmission.java`); facts/candidacy stay in executor path — matches D-VR-082-A1.

---

## Material objections and gaps

### 1. `mobGriefing` gate missing from brief §8 (should fix before implement)

`VillageHarvestEpisodeGoal` refuses start/continue when `RULE_MOBGRIEFING` is false (L68–70, L108–110). Gate 0 pins villager pickup behind `canEntityGrief` (G0-2). Brief §8 interrupt table does **not** list griefing-off as abort/admission deny.

**Risk:** episode commits toss; villager never picks up → perpetual `COMMITTED_UNCONFIRMED` + wasted food.

**Recommendation:** add to `PopulationFoodSupportAdmission` + T57 scenario row (deny at `canUse` when griefing false).

### 2. G0-6 survival food reserve still `UNVERIFIED` (acceptance gate, not blocker)

Gate 0 flags PlayerMob survival nutrition reserve as not yet quantified in Scavenger. Brief lists survival reserve in expendability order but does not set a **must-ship** numeric floor.

**Recommendation:** brief v1.2 — implementation must pin conservative `PopulationFoodTuning` reserve (or shared helper) with documented probe; task-57 report must label `CONFIRMED`/`INFERRED`/`UNVERIFIED`. Block handoff if reserve math is stubbed to zero without evidence.

### 3. P4 band contention beyond harvest ↔ population (document, not necessarily fix in V3-E)

`PlaceTorchGoal` also registers at P4 with `MOVE|LOOK` (`PlaceTorchGoal.java` L64), **before** harvest in `SpmScavenger.java`. When torch demand is live, it can win `canUse` over harvest and (future) population on the same tick.

**Classification:** pre-existing P4 policy; not introduced by task-57. **INFERRED** starvation of village work when torch backlog is high.

**Recommendation:** note in task-57 report “known P4 neighbors”; do not scope-fix unless VR-T3 campaign shows village work starvation.

### 4. Same-villager greet interlock — open product decision

Brief locks trade interlock (`TradeSessionClaimWindow`) but not **FriendlyGreet** gift toward the same villager during population PATHING. P1 greet can preempt P4 MOVE.

**Recommendation:** add **PD-57-6** — defer vs block population toward villager with active SOCIAL binding / greet target (mirror trade). Default lean: **block handoff toward bound greet target**, not block episode start globally.

### 5. Brief self-review table drift (doc hygiene)

`task-57-brief.md` L679 lists “Gate 0 not run | **DONE**” while L697 states Gate 0 **PASS**. Confusing for implementers.

**Recommendation:** v1.2 correct row to “Gate 0 PASS | DONE”.

### 6. `canBreed()` as recipient filter (optional tighten)

Gate 0 allows `wantsMoreFood()` / `hasExcessFood()` as rank keys. Brief does not require skipping recipients who **already** `canBreed()` and `!wantsMoreFood()`.

**Risk:** disposable food offered to saturated adults — wasteful but not authority-violating.

**Recommendation:** **PRODUCT DECISION** — prefer negative rank for `hasExcessFood()`; optional hard-skip if `canBreed()` && !`wantsMoreFood()` at SELECT (reduces VR-T3e waste loops).

---

## RFC / scenario parity

| RFC row | Brief coverage |
| --- | --- |
| VR-T3e | T57-1…T57-12 map cleanly |
| VR-T3j | T57-5, T57-10 |
| D-VR-078 | no breeding command / bed claim / Brain — **locked in brief** |
| D-VR-083-A1 | `PopulationSupportVacancyPolicy.java` matches brief (`CONFIRMED`) |

**NOT FOUND:** RFC amendment row for task-57 peer review — optional append to Village RFC contribution table on implementation start.

---

## MAIBS static (pre-implementation)

| Minute | Predicted observable | Failure mode |
| --- | --- | --- |
| 0–1 | Facts candidate; mob selects adult with local vacant HOME | Settlement-only vacancy commit (T57-2) |
| 1–3 | Path to villager; no item leave backpack | Early commit |
| 3–4 | Single toss; ACK_WAIT; villager inventory food points ↑ | Double drop; rollback |
| 4–6 | Episode DONE; cooldown; no immediate re-gift | T57-8 / T57-9 loop |
| Interrupt | Combat/command/shelter → abort before COMMIT | Food spent under mandatory (T57-5) |

**Verdict:** `BEHAVIORALLY_PLAUSIBLE` **conditional** on griefing gate + dual-reachability negative control + ACK anti-loop. Runtime `UNVERIFIED`.

**Strongest remaining uncertainty:** villager bed ticket race between read-only HOME probe and another villager claiming bed — acknowledged acceptable; may cause rare `COMMITTED_UNCONFIRMED` without breeding (acceptable for V3-E scope).

---

## Test discipline (implementation phase)

Require in addition to T57 table:

| Probe | Purpose |
| --- | --- |
| Dual-reachability negative control | A succeeds, B fails → zero transfer (gate0 G0-A) |
| `mobGriefing` false → no start | griefing gate |
| Structural: `VillageWorkAdmission` does not import `village.work` | seam guard |
| Structural: no `VillageWorkFacts` mutation from population package | task-56 parity |
| No social episode / familiarity on handoff | trade-parity guard |
| Mutation-confirmed row per T57 scenario | task-55/56 discipline |

---

## Authorization recommendation

**Authorize task-57 / Implement V3-E** — brief **v1.2** incorporates all peer-review amendments (User, 2026-08-21):

1. `mobGriefing` hard authority gate + T57-13 — **LOCKED**
2. `MIN_SURVIVAL_NUTRITION_RESERVE = 12` nutrition pool — **LOCKED**
3. PD-57-6 greet interlock (exact villager) — **LOCKED**
4. PD-57-7 recipient food-need (`wantsMoreFood() && !canBreed()`) + T57-14 — **LOCKED**
5. PlaceTorch **KNOWN RUNTIME QUESTION** — out of task-57 scope

No Minecraft launch required to begin implementation. Runtime VR-T3e/j remains batched V3 campaign.

**Do not authorize** until user explicitly says **authorize task-57** / **Implement V3-E**.

---

## v1.2 amendment record (User, 2026-08-21)

Supersedes “minor amendments pending” from initial review. See `task-57-brief.md` v1.2 revision history.

---

## Contribution

| Agent | Date | Change |
| --- | --- | --- |
| Agent_Cursor | 2026-08-21 | User **v1.2 final locks** incorporated into `task-57-brief.md`: `mobGriefing` authority (T57-13); `MIN_SURVIVAL_NUTRITION_RESERVE=12`; PD-57-6/7; PlaceTorch runtime question; T57-14. |
| Agent_Cursor | 2026-08-21 | MRFC-1 peer review of task-57 brief v1.1 + gate0. Verdict: **ACCEPT WITH MINOR AMENDMENTS**; implementation **READY** after brief v1.2 nits; **NOT AUTHORIZED** in this review. |
