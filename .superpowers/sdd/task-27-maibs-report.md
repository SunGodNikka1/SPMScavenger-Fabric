# MAIBS-1 — MI-14C2 Execution Control semantic-drift review

**Date:** 2026-08-09  
**Scope:** `ExecutionIntentPolicy`, `MiningExecutionArbiter`, `MiningExecutionGuard`,
`MoveContentionPolicy`, `ExploringGoal.acceptCaveHandoff`, `ControlledDescentGoal.stop`,
host SPM `FollowLovedOneGoal`  
**Mode:** Static multi-cycle scheduler simulation (no Minecraft launch)  
**Gate result:** **`FAIL — ARCHITECTURE_DEFECT`** — repair **MI-14C2-R1**, **MI-14C2-R2**,
**MI-14C1-R2** before MI-14C3.

---

## Verdict summary

| Layer | Result |
| --- | --- |
| **Policy matrices** | `CODE_CONFIRMED` — ALLOW/YIELD/NEUTRAL match locked contract while inputs hold |
| **Admission + continuation wiring** | `CODE_CONFIRMED` — five participating goals consult guard in both hooks |
| **Multi-cycle physical trace** | **`FAIL`** — handoff authority evaporates after transition consumption (M1) |
| **Scheduler-wide MOVE observation** | **`FAIL`** — host SPM goals invisible to contention (M2) |
| **Revocation lifecycle** | **`FAIL`** — executor `stop()` can overwrite director-completed state (M3) |

Unit suite C2-A…G tests matrices **while `CAVE_FOUND` remains pending**. None simulate
post-`consumeTransition()` arbitration or host-goal MOVE ownership.

---

## C2-A…G static verdict (revised)

| Case | Static result | Evidence |
| --- | --- | --- |
| **C2-A** Gather → `CAVE_FOUND` | **FAIL** end-to-end | Initial YIELD; authority → `NONE` after `acceptCaveHandoff` consumes transition |
| **C2-B** Smelt → descent | **PASS_WITH_CONCERNS** | Works among participating goals; host MOVE holders invisible (M2) |
| **C2-C** Combat interrupts | **PASS** | `COMBAT_TARGET` TEMPORARY, not contention |
| **C2-D** Combat ends / resume | **PASS** static | Episode clock + blocker clear → re-authorize (`INFERRED`; runtime `UNVERIFIED`) |
| **C2-E** Tunnel pending neutral | **PASS** | `TUNNEL_HANDOFF_PENDING` → NEUTRAL; transition not consumed |
| **C2-F** MOVE contention observable | **FAIL** | Unknown/host goals skipped in `MoveContentionPolicy` |
| **C2-G** Continuation on intent change | **PARTIAL/FAIL** E2E | Yield on pending transition; sustained cave authority lost after accept |

---

## M1 — `CAVE_HANDOFF` authority lifetime (`ARCHITECTURE_DEFECT`)

**Classification:** `ARCHITECTURE_DEFECT` — breaks Loop B after handoff acceptance.

### Mechanism (`CODE_CONFIRMED`)

`ExecutionIntentPolicy.derive` returns `CAVE_HANDOFF` **only** while a pending `CAVE_FOUND`
transition exists:

```16:22:Projects/SPMScavenger-1.21.1-Fabric/src/main/java/com/noobk/spmscavenger/mining/ExecutionIntentPolicy.java
    public static ExecutionIntent derive(MiningProjectSavedData store, UUID mobId, long now) {
        if (store.projectOf(mobId).filter(MiningProject::isControlledDescent).isPresent()) {
            return ExecutionIntent.CONTROLLED_DESCENT;
        }
        return store.pendingTransition(mobId)
                .map(transition -> fromPending(transition, now))
                .orElse(ExecutionIntent.NONE);
```

`ExploringGoal.acceptCaveHandoff()` consumes the transition inside `canUse()` **before** the cave
expedition runs:

```422:431:Projects/SPMScavenger-1.21.1-Fabric/src/main/java/com/noobk/spmscavenger/goal/ExploringGoal.java
        store.consumeTransition(mob.getUUID());
        mob.getNavigation().stop();
        navigationState = null;
        expedition = rebased;
        expedition.companionsInvited = true;
        ...
        return true;
```

`MiningGoalKind.classifyExploring` also keys off **pending** transition only — same gap.

### Multi-cycle trace

```text
T0: CAVE_FOUND pending → intent CAVE_HANDOFF → Gather YIELD, Explore ALLOW
T1: acceptCaveHandoff() → consumeTransition() → expedition installed
T2: no project, no transition → intent NONE → Explore EXPLORING_ORDINARY → Gather NEUTRAL
```

**Predicted:** Priority-3 gather/smelt can preempt the cave expedition immediately after acceptance.

### Repair — MI-14C2-R1 (Handoff Authority Lifetime)

Introduce persistent **`MiningExecutionCommitment`** (or equivalent) created atomically with
transition consumption:

```text
CAVE_FOUND → accept handoff → consume transition + install CAVE_CONTINUATION commitment
→ ExecutionIntentPolicy derives CAVE_HANDOFF from commitment until explicit complete/abandon
```

**Must-happen regression:** after accept + one scheduler tick, `GatherResourcesGoal` still YIELD.

**Must-not-happen:** keep transition pending forever (Loop D honesty preserved via commitment, not
stale transition).

---

## M2 — Scheduler-wide MOVE contention (`ARCHITECTURE_DEFECT`)

**Classification:** `ARCHITECTURE_DEFECT` — resurrects Loop A zombie assignment via host scheduler.

### Mechanism (`CODE_CONFIRMED`)

`MoveContentionPolicy` ignores goals where `MiningGoalKind.classify` returns empty:

```46:54:Projects/SPMScavenger-1.21.1-Fabric/src/main/java/com/noobk/spmscavenger/mining/MoveContentionPolicy.java
            MiningGoalKind kind =
                    MiningGoalKind.classify(goal, store, mobId, now).orElse(null);
            if (kind == null) {
                continue;
            }
```

`MiningGoalKind.classify` documents host goals as intentionally empty:

```26:28:Projects/SPMScavenger-1.21.1-Fabric/src/main/java/com/noobk/spmscavenger/mining/MiningGoalKind.java
     * Classifies a running or candidate goal. Returns empty for goals outside mining arbitration
     * (combat, survival, SPM host goals).
```

### Host evidence (`CODE_CONFIRMED`)

SPM `FollowLovedOneGoal`: priority **2**, `Flag.MOVE` + `Flag.LOOK`:

```27:28:Projects/references/SocialPlayerMobs-v0.86.0/src/main/java/games/brennan/playermob/entity/goal/FollowLovedOneGoal.java
 * <p><b>Priority &amp; combat.</b> goalSelector priority 2, after the attack goal; mutually
```

```56:58:Projects/references/SocialPlayerMobs-v0.86.0/src/main/java/games/brennan/playermob/entity/goal/FollowLovedOneGoal.java
    public FollowLovedOneGoal(PlayerMobEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
```

Registered at priority 2 on `PlayerMobEntity` (`PlayerMobEntity.java` L825).

### Multi-cycle trace

```text
FollowLovedOneGoal running (priority 2, MOVE)
Director assigns CONTROLLED_DESCENT
controlledDescentBlocker → NONE (no combat, pick ok)
MoveContentionPolicy → FollowLovedOne → classify empty → IGNORE
lease → AUTHORIZE
GoalSelector → FollowLovedOne still owns MOVE → ControlledDescent never starts
start lease never applies (requires CONTENTION blocker)
→ RUNNING project + never-started executor (Loop A zombie)
```

Also breaks Loop B when host goal blocks explore during `CAVE_FOUND` window.

### Repair — MI-14C2-R2 (Scheduler-Wide MOVE Contention)

Broaden scheduler observation without making mining dictator over protected host activity:

| Running MOVE holder | Classification | Mining response |
| --- | --- | --- |
| Combat / survival / recovery / explicit command | `PROTECTED_INTERRUPT` | Mining waits (NEUTRAL / existing blockers) |
| Ordinary host work (e.g. follow catch-up) | `ORDINARY_HOST_WORK` | Eligible for contention / yield semantics |
| Unknown non-participating goal with MOVE | `UNKNOWN_MOVE_HOLDER` | **Must count as contention** — lease cannot report healthy `NONE` |

**Must-happen:** synthetic unknown `Goal` with `MOVE` running + assigned descent → `CONTENTION` or
explicit protected classification — **not** eternal `AUTHORIZE`.

**Must-not-happen:** force every unknown host goal to YIELD (mining must not become global dictator).

---

## M3 — Revocation-safe executor stop (`ARCHITECTURE_DEFECT`)

**Classification:** `ARCHITECTURE_DEFECT` — violates MI-14B director ownership; dual truth on revoke.

### Mechanism (`CODE_CONFIRMED`)

`authorizeExecution` on revoke calls `completeProject` then returns `false` from `canContinueToUse`.
GoalSelector stops the executor. `ControlledDescentGoal.stop()` unconditionally persists local
`project` (still `RUNNING` in memory):

```144:147:Projects/SPMScavenger-1.21.1-Fabric/src/main/java/com/noobk/spmscavenger/goal/ControlledDescentGoal.java
    public void stop() {
        if (project != null && mob.level() instanceof ServerLevel level) {
            MiningProjectSavedData.get(level).putProject(mob.getUUID(), project);
        }
```

Director may have just written terminal `RETRY`/`BLOCKED` lifecycle + transition; local copy
overwrites with stale `RUNNING`.

### Multi-cycle trace

```text
canContinueToUse → authorizeExecution → REVOKE → completeProject (terminal end + transition)
→ return false → GoalSelector.stop()
stop() → putProject(local RUNNING copy)
→ terminal transition coexists with RUNNING project again
```

### Repair — MI-14C1-R2 (Revocation-Safe Executor Stop)

Persist interruption state **only** if store still owns the same active assignment:

```text
store still has this assignment active (match identity, not merely mode)?
  YES → persist safe interruption checkpoint
  NO  → director already terminated; discard local copy
```

---

## Goal interaction table (relevant slice)

| Goal | Priority | Flags | Consults mining guard? | Blocks mining MOVE invisibly? |
| --- | ---: | --- | --- | --- |
| SPM attack | 1 | MOVE+LOOK | No | Via `COMBAT_TARGET` when target set |
| `FollowLovedOneGoal` | 2 | MOVE+LOOK | No | **Yes — invisible to M2** |
| `GatherResourcesGoal` | 3 | MOVE+LOOK | Yes | Yields when intent actionable |
| `ControlledDescentGoal` | 3 | MOVE+LOOK | Yes | Designated consumer |
| `ExploringGoal` | 8 | MOVE+LOOK | Yes | Cave handoff authority lost post-accept (M1) |

---

## Predicted weird behaviors (post-review)

| # | Behavior | Classification |
| --- | --- | --- |
| W1 | Mob accepts cave handoff, immediately chops wood on priority 3 | `ARCHITECTURE_DEFECT` (M1) |
| W2 | Assigned descent forever with loved-one catch-up sprinting | `ARCHITECTURE_DEFECT` (M2) |
| W3 | Revoked descent flickers back to RUNNING for one persistence tick | `ARCHITECTURE_DEFECT` (M3) |
| W4 | C2 unit tests green while all three fail in multi-cycle trace | Method lesson — matrix tests ≠ lifecycle |

---

## Recommended repair sequence

```text
MI-14C2 CODE/UNIT (done)
  ↓
MAIBS C2 FAIL
  ↓
MI-14C2-R1  persistent CAVE_CONTINUATION commitment
  ↓
MI-14C2-R2  scheduler-wide MOVE contention classification
  ↓
MI-14C1-R2  revocation-safe executor stop()
  ↓
MAIBS C2 re-pass
  ↓
MI-14C3 progress lease
```

**Do not start MI-14C3** until R1/R2/R2-complete: C3 would measure progress on authority that C2
can lose immediately, and C1 can resurrect revoked assignments.

---

## Runtime experiments (deferred — launch approval required)

| ID | Probe |
| --- | --- |
| RT-C2-A | `CAVE_FOUND` → accept → observe gather re-preempt within 20 ticks |
| RT-C2-F | Loved-one catch-up + assigned descent → 600+ ticks without `markExecutorStarted` |
| RT-C1-R2 | Revoke on pick loss → inspect SavedData lifecycle before/after `stop()` |
