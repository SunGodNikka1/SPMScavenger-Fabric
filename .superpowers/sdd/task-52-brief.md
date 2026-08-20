# Task 52 brief: shared `MandatoryOwnership` + `V2-DEF-002` repair (`D-VR-084`)

**Slice:** the shared discretionary-permission authority **only**. **Authorization:** User,
2026-08-19 — *"write the implementation brief for the shared authority repair"*, with
`task-52 = MandatoryOwnership / V2-DEF-002 repair`, `task-53 = V3-A`, `task-54 = V3-B`, …
**Not authorized:** Minecraft runtime launch · commit · push · any V3 executor, profile store,
storage registry, or village work.

**This is not a Village Work task.** `D-VR-084` outgrew V3 during the amendment pass; it now sits
above two consumers and gets its own boundary, acceptance report, and task number.

```text
                        MandatoryOwnership
                              │
             ┌────────────────┴────────────────┐
             ▼                                 ▼
DiscretionaryActivityDirector          VillageWorkAdmission  (task-53, NOT this slice)
  EXPLORE / SOCIAL / REST                    V3 work
```

## Why this slice exists

`docs/porting/KNOWN_DEFECTS.md` **V2-DEF-002** — a PlayerMob with an unresolved iron-pickaxe
progression demand walked ~150 blocks out of its own village because no deliberate executor was
*running* yet:

```text
plans=0 (TE 0)  revals=0  trades=0     logs=320  emeralds=0     objective=Exploring
```

`CODE_CONFIRMED` root cause —
`src/main/java/com/noobk/spmscavenger/activity/ActivityObservationService.java:30-41` records an
`ActivityClass` **only when `wrapped.isRunning()`**. So
`opinion/DiscretionaryEligibility.java:13-27` is an *occupancy* answer, and "mandatory work exists
but nobody is executing it yet" is invisible to every consumer.

The defect document also **rejects the naive repair**: a blocker keyed on demand existence converts
a wandering mob into a frozen one when the demand is genuinely unservable. That is the whole
difficulty, and requirement 5 below is where it lives.

## Load-bearing requirements

| # | Requirement |
| --- | --- |
| 1 | **Demand never creates authority. A claim does.** No component judges "viability" |
| 2 | The running half is **consumed**, never reimplemented — `DiscretionaryEligibility` stays the owner of running truth |
| 3 | **A claim may never be refreshed by the continued existence of the same demand** (`D-VR-084`) |
| 4 | A *new* claim needs a *new* justification: progress, fresh actionable evidence, or an ownership transition |
| 5 | **One canonical pending owner per mob.** Gather, Trade, Mining and future V3 cleanup do not each pile claims into another arbitration system |
| 6 | Claims are **runtime-only** — never persisted, never registered in `PerMobSavedData.forgetAll()` |
| 7 | The pending half fails **open**; the running half keeps failing **closed**. Deliberate, and opposite |
| 8 | `VILLAGE_TRADE` joins `blocksDiscretionaryChoice` (`D-VR-082-A1` item 2) |
| 9 | `DiscretionaryActivityDirector` behaviour changes **only** by gaining the pending input — no scoring, utility, or intent-lifecycle change |
| 10 | Gate RET-1: named key, bound, and **production** eviction call sites |

## Deliverables

| Path (all under `Projects/SPMScavenger-1.21.1-Fabric/`) | Role |
| --- | --- |
| `src/main/java/com/noobk/spmscavenger/activity/MandatoryOwnershipClaim.java` | **new** — the record |
| `src/main/java/com/noobk/spmscavenger/activity/MandatoryOwnershipRegistry.java` | **new** — runtime-only per-mob claim slot + publish/release/expiry |
| `src/main/java/com/noobk/spmscavenger/activity/MandatoryOwnership.java` | **new** — the pure decision: running truth + claim → permission |
| `src/main/java/com/noobk/spmscavenger/opinion/DiscretionaryEligibility.java` | add `VILLAGE_TRADE` to `blocksDiscretionaryChoice` |
| `src/main/java/com/noobk/spmscavenger/opinion/InvalidationCause.java` | add `MANDATORY_PENDING_CLAIM` |
| `src/main/java/com/noobk/spmscavenger/opinion/DiscretionaryActivityDirector.java` | consume `MandatoryOwnership` instead of `DiscretionaryEligibility` directly (`:83`) |
| `src/main/java/com/noobk/spmscavenger/SpmScavenger.java` | three eviction call sites (below) |
| `src/main/java/com/noobk/spmscavenger/village/trade/…` / `goal/GatherResourcesGoal.java` | **one** publishing owner wired (see "Scope of publishers") |

**Package choice:** `activity`, not `opinion`. This is activity-ownership truth with two consumers;
putting it in `opinion` would make Village Work depend on Opinion for permission and re-create the
coupling the decision removes.

## Required semantics

### The four states (`D-VR-084`)

```text
mandatory executor RUNNING                    -> block discretionary
route PENDING under a live published claim    -> block discretionary
demand exists, no live claim (or it expired)  -> DO NOT block
nothing mandatory                             -> discretionary allowed
```

### Consume, do not reimplement

```java
public static Permission evaluate(
        ActivityObservationService.Observation observation,
        boolean combatTarget,
        Optional<MandatoryOwnershipClaim> liveClaim,
        long now)
```

The running arm **must** delegate to `DiscretionaryEligibility.isDiscretionaryEligible` /
`invalidationForObservation`. Re-deriving the blocking set here is the defect this task exists to
prevent; a structural test asserts the delegation.

### The record

```text
MandatoryOwnershipClaim
  |- mobId
  |- consumerKey          one canonical pending owner per selected work episode
  |- routeIdentity
  |- generation, openedAt
  '- expiresAt
```

### Anti-self-renewal (**requirement 3 — the heart of the slice**)

The registry keeps, per mob, **one** slot: the current claim plus the terminal
`(consumerKey, routeIdentity, generation)` of the claim that last occupied it.

```text
publish(mobId, consumerKey, routeIdentity, generation, now):
    same (consumerKey, routeIdentity) as the remembered slot
        AND generation <= remembered generation      -> REFUSED
    otherwise                                        -> accepted, slot replaced
```

`generation` is minted by the **owner** from a real event. "The demand is still there" is not an
event and must not mint one. A refused publish is a normal outcome, not an error — the owner simply
has nothing new to say.

**Known limit, to be recorded rather than fixed:** alternating two different `consumerKey`s could
in principle keep a mob blocked. Two different consumer keys are two different pieces of real work,
so this is accepted for gen-1; state it in the report and add it to the concerns section.

### Retention (Gate RET-1)

| Question | Answer |
| --- | --- |
| Key | `mobId` → exactly one slot |
| Bound | one entry per live PlayerMob; a new episode **replaces**, never appends |
| Expiry | `expiresAt`; an expired claim is **deleted**, not merely predicate-false (RET-1a) |
| Eviction call sites (**production**) | `SpmScavenger.java` `ENTITY_UNLOAD` → `release(uuid)`; `AFTER_DEATH` → `release(uuid)`; `SERVER_STOPPED` → `shutdownServerState()` |
| Persistence | **none**. Not in `PerMobSavedData.forgetAll()` — registering it would imply a persistence `D-VR-084` forbids |

Mirror `village/trade/TradeSessionClaimWindow.java` exactly; it is the shipped precedent for a
runtime-only per-mob claim registry with all three hooks already wired in `SpmScavenger.java`
(`:130`, `:188`, `:176`).

### Scope of publishers

Wire **one** publishing owner in this slice — the gather/route owner that `V2-DEF-002` actually
observed failing — and prove the seam. Do **not** retrofit Trade, Mining and future V3 cleanup in
task-52; each is its own reviewable change, and requirement 7 means an unwired owner degrades to
today's behaviour rather than breaking.

## Constraints

- **TDD required.** Capture RED output before GREEN for at least scenarios 3, 5 and 11.
- SPM stays **stock**. No mixin, no fork, no host-goal change in this slice.
- No new world scanner, no `Level` access in `MandatoryOwnership` — it must be unit-testable without `Bootstrap`.
- `DiscretionaryActivityDirector` gains an input; it does not gain a policy.
- Do not touch `MandatoryHandoffPolicy`, `GatherScanSweep`, transaction capacity, or the autonomous seed.
- **Gather remains the only party allowed to publish `RouteExhaustionEvidence`.** A `MandatoryOwnershipClaim` is not exhaustion evidence and must not be read as any.

## Verification — automated behavioural acceptance (User, 2026-08-19)

Runtime is **deliberately not** part of this slice. *"Unit tests cannot show a mob stopped
wandering"* does not imply launching Minecraft; it means the automated acceptance model must be
stronger than isolated unit tests. All twelve scenarios are required:

| # | Scenario | Expected |
| --- | --- | --- |
| 1 | RUNNING mandatory work | discretionary **denied** |
| 2 | LIVE pending claim | discretionary **denied** |
| 3 | demand exists, nobody claims | discretionary **allowed** |
| 4 | claim expires without progress | discretionary **becomes allowed** |
| 5 | **same demand still exists after expiry** | claim does **NOT** self-renew |
| 6 | meaningful progress / fresh actionable evidence | a new bounded claim **MAY** be published |
| 7 | owner abandons or satisfies the work | claim released **immediately** |
| 8 | `VILLAGE_TRADE` running | discretionary **denied** |
| 9 | unknown running goal | **fail closed** |
| 10 | a future owner forgets to publish | pending side **fails open** |
| 11 | unload / dimension transfer / server stop | runtime claim **disappears** |
| 12 | restart | **no stale `MandatoryOwnership` resurrects** |

**Scenario 5 is the load-bearing negative control.** If it fails, the design has degenerated into
`demand exists -> block` with a timer around it, which is the frozen-demand problem `V2-DEF-002`
explicitly rejects. Run it in isolation and name the assertion it breaks.

### Two temporal simulations (required, not optional)

```text
A — servable demand
T0    iron demand appears
T1    Gather accepts responsibility -> CLAIM
T40   scan / progress
T80   route becomes impossible
T120  owner abandons claim
T121  EXPLORE becomes legal

B — unservable demand
T0    impossible demand appears
T1    no route owner accepts it
T2    no CLAIM
T3    EXPLORE remains legal   (and stays legal at T400)
```

Simulation B is `V2-DEF-002`'s second repair-gate row — the one that stops a naive blocker turning
a wandering mob into a frozen one — and must be asserted over a long horizon, not one tick.

### Negative controls

Run each **in isolation** and name the test it breaks:

1. remove the `generation` comparison → scenario 5 must fail
2. make expiry a predicate instead of a deletion → RET-1a assertion must fail
3. re-derive the blocking set locally instead of delegating → the structural delegation test must fail

### Commands

```powershell
cd "d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric"
.\gradlew.bat compileJava
.\gradlew.bat test
```

Report the total test count and every negative-control result.

## Docs to update

| File | Update |
| --- | --- |
| `docs/porting/KNOWN_DEFECTS.md` | `V2-DEF-002` → **`REPAIRED / STATIC-BEHAVIORAL ACCEPT`, runtime witness `DEFERRED`**. Not `CLOSED`, not "runtime confirmed" |
| `docs/porting/TEST_MATRIX.md` | one row per scenario 1–12 plus both temporal simulations |
| `plans/RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md` | `D-VR-084` implementation status; record that the runtime witness is folded into the later batched V3 campaign |

**Deferred runtime witness (do not schedule a session for it).** One observation, batched with the
V3 integration campaign:

```text
mandatory pending claim active   -> no expedition
claim abandoned / expires        -> discretionary movement eventually resumes
```

## Report

`.superpowers/sdd/task-52-report.md` — status `DONE` / `DONE_WITH_CONCERNS` / `NEEDS_CONTEXT` /
`BLOCKED`; every command with working directory and exact result; evidence labelled
`CONFIRMED` / `INFERRED` / `UNVERIFIED` with paths and line ranges; RED-before-GREEN capture; the
self-review mapped to the requirement table; and a concerns section that names the alternating-key
limit and the deliberate fail-open of the pending half.

**Do not mark the behavioural claim `CONFIRMED`.** Per Gate AV-1 the proof class here is
static-behavioural acceptance; the runtime witness is deferred by decision, not by omission.
