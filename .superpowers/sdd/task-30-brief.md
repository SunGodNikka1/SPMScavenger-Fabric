# Task 30 brief: MI-14C3-R1 protected interruption lease semantics

## Target

`D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

## Source reference

`D:\Apps\Minecraft Port\Projects\references\SocialPlayerMobs-v0.86.0`

## Source evidence

- Pinned SPM goal registration and flags under
  `D:\Apps\Minecraft Port\Projects\references\SocialPlayerMobs-v0.86.0\src\main\java`:
  priority 0--3 host goals define the scheduler conflicts recorded in the RFC's
  `MI-14C3-R1` mapping.
- Target executor flags:
  `src/main/java/com/noobk/spmscavenger/goal/ControlledDescentGoal.java` (`MOVE + LOOK`).
- Existing C2 arbitration and C3 lease implementation:
  `MoveHolderClassifier`, `MoveContentionPolicy`, `MiningExecutionLease`,
  `ExecutionLeasePolicy`, and `MiningDirector` in
  `src/main/java/com/noobk/spmscavenger/mining/`.

## Authorization and binding constraints

- User explicitly authorized: **Begin MI-14C3-R1**.
- Do not implement Tunnel Search or alter the 2400-tick absolute controlled-descent budget.
- Do not make mining preempt safety, recovery, combat, or player commands.
- Do not launch Minecraft. Do not commit or push.
- Use `apply_patch` for edits. Preserve compatibility with SPM absent or API-changed.

## Locked decision

1. Separate arbitration preemptibility from lease availability.
2. Test every running goal for intersection with the executor's complete required flags
   (`MOVE + LOOK`), not MOVE alone.
3. Map environmental escape, fire-bucket recovery, flee, shelter, and train recovery to
   `SAFETY_RECOVERY`, a condition-bound protected pause with no 1200-tick mining expiry.
4. Map persistent stay anchors and running commanded actions to hard `PLAYER_ORDER` prevention or
   revocation.
5. Preserve `COMBAT_TARGET` and its 1200-tick episode grace.
6. Persist a separate pre-start pause accumulator; do not mutate historical `assignedAt`.
7. Set the admissible no-progress window to **400 ticks**. A successful break marks progress at
   once; 400 is conservative tolerance over the known <=200-tick break operation plus navigation,
   replanning, scheduler cadence, and server-tick irregularity.

## Behavioral prediction — BEHAVIORALLY_PLAUSIBLE

| Layer | Result |
| --- | --- |
| Intended behavior | Safety/recovery can interrupt mining indefinitely while observable; commands override mining; ordinary work remains bounded contention. |
| Implemented mechanism required | Required-flag scheduler intersection produces a typed lease blocker; lease persists exact pre/post-start pause durations; progress expiry is 400 admissible ticks. |
| Predicted behavior | A miner stops while escaping/eating/recovering, then resumes with its remaining lease window. A stay/command order prevents or ends the dig. A genuinely stuck admitted dig ends with `NO_PROGRESS` before its total project budget. |
| Failure/weirdness | Unknown future LOOK/MOVE host goals become ordinary contention; a stale persistent stay reflection must fail closed rather than silently mine; rapid blocker changes must not duplicate pause credit. |
| Confidence | `CODE_CONFIRMED` for flags, priorities, lease arithmetic, and policy wiring; runtime visuals remain `UNVERIFIED`. |

### Goal interaction prediction

| Goal family | Priority/flags | Lease impact | Predicted observable result |
| --- | --- | --- | --- |
| EnvironmentalEscape | 0, MOVE+LOOK | SAFETY_RECOVERY | Escape owns movement; dig pauses until escape clears. |
| StayNear / CommandedAction | 2/1, MOVE+LOOK | PLAYER_ORDER | Autonomous dig is prevented or revoked. |
| EatFood | 3, LOOK | SAFETY_RECOVERY | Eating already in progress blocks complete descent admission, then cleanly releases it. |
| Combat | high, MOVE+LOOK | COMBAT_TARGET | Existing bounded combat suspension remains unchanged. |
| Gather/smelt/follow/unknown work | MOVE and/or LOOK | CONTENTION | Never-started admission remains bounded by the effective 600-tick start lease. |

### Predicted weird behaviors

1. Unknown future host goals that own LOOK are conservatively treated as contention
   (`ACCEPTABLE_STEPPING_STONE`): safe, diagnosable, but may release a mining assignment after 600
   admissible pre-start ticks.
2. A permanently active safety goal can preserve a mining assignment indefinitely
   (`ACCEPTABLE_STEPPING_STONE`): revoking mining cannot solve the safety trap; the safety system
   owns recovery/termination.
3. A command arriving just before a progress event revokes rather than resuming later
   (`ACCEPTABLE_STEPPING_STONE`): player authority intentionally outranks autonomous continuity.
4. Missing required-flag intersection or double-settled pause time is an
   `ARCHITECTURE_DEFECT` and blocks acceptance.

## Alternatives and trade-offs

- **Selected: typed blockers plus a scheduler lease-impact resolver.** Smallest repair that keeps
  arbitration and lease semantics distinct. Risk: class-name taxonomy is version-sensitive;
  bounded by pinned source, conservative unknown handling, and tests.
- **Alternative: one two-axis `{preemptibility, leaseImpact}` record.** More compiler-enforced but
  needlessly refactors the already-working C2 arbitration surface for this generation. Reconsider
  if another executor or materially larger taxonomy is added.
- **Rejected: map all protected goals to CONTENTION.** Smaller but erases command/safety meaning
  and can expire legitimate recovery through the start lease.

## Required implementation

- Add explicit `SAFETY_RECOVERY` / protected-pause and `PLAYER_ORDER` blocker semantics.
- Generalize the scheduler scan from MOVE ownership to any required-flag intersection.
- Cover the pinned host-goal mapping, including LOOK-only eating.
- Prevent assignment under a present stay anchor and revoke an already assigned incompatible
  project. Treat an unavailable stay-anchor API conservatively and document the chosen behavior.
- Add persisted `startPausedTicks` with backward-compatible NBT migration.
- Use effective start age `now - assignedAt - startPausedTicks` only where the start lease applies.
- Change progress lease to 400 ticks; retain absolute project budget 2400.

## Falsification and acceptance

Implement deterministic C3-F1 through C3-F7 tests from the RFC.

- **Must happen:** safety/recovery beyond 2400 wall ticks pauses both relevant clocks and resumes
  with the exact remaining admissible window; a stalled admitted project reaches `NO_PROGRESS`
  before total-budget exhaustion.
- **Must not happen:** a LOOK-only conflicting goal yields `NONE/AUTHORIZE`; a persistent player
  order leaves a zombie assignment; blocker transitions duplicate/loss pause time; combat inherits
  condition-bound safety semantics.

Run targeted tests, full `clean test`, and `clean build`. Then perform a fresh MAIBS static pass
against actual code. Runtime behavior remains `UNVERIFIED` without separately approved launch.

## Documentation and report

Update the existing RFC topic, decision D-MIW-040, task row, gates, scenario evidence,
`.superpowers/sdd/progress.md`, and relevant test/maintainer documentation only where this repair
changes them. Write the final evidence to:

`.superpowers/sdd/task-30-report.md`
