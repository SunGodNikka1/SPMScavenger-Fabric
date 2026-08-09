# Task 27 report: MI-14C2 — Execution Intent & Arbitration

**Status:** `DONE_WITH_CONCERNS`

## Verification

`.\gradlew.bat test` — **234 tests, 0 failures** (`CONFIRMED`, cwd `Projects/SPMScavenger-1.21.1-Fabric`)

## Delivered

| Component | Role | Evidence |
| --- | --- | --- |
| `ExecutionIntent` | Actionable vs neutral intent enum | `mining/ExecutionIntent.java` |
| `ExecutionIntentPolicy` | Derives intent from active project + pending transition | `mining/ExecutionIntentPolicy.java` |
| `ArbitrationDecision` | `ALLOW` / `YIELD` / `NEUTRAL` | `mining/ArbitrationDecision.java` |
| `MiningGoalKind` | Goal classification incl. `EXPLORING_CAVE_HANDOFF` | `mining/MiningGoalKind.java` |
| `MiningExecutionArbiter` | Pure `(intent, goalKind)` matrices | `mining/MiningExecutionArbiter.java` |
| `MiningExecutionGuard` | Shared `canUse` + `canContinueToUse` gate | `mining/MiningExecutionGuard.java` |
| `MoveContentionPolicy` | Detects yielding MOVE holders | `mining/MoveContentionPolicy.java` |
| `MiningDirector.resolveControlledDescentBlocker` | Maps scheduler contention → `CONTENTION` | `mining/MiningDirector.java` L163–177 |

### Goal wiring (admission + continuation)

| Goal | Guard kind |
| --- | --- |
| `ControlledDescentGoal` | `CONTROLLED_DESCENT` + `resolveControlledDescentBlocker` lease path |
| `GatherResourcesGoal` | `GATHER_RESOURCES` |
| `SmeltAtFurnaceGoal` | `SMELT_AT_FURNACE` |
| `CraftTorchesGoal` | `CRAFT_TORCHES` |
| `ExploringGoal` | `MiningGoalKind.classifyExploring(...)` (cave handoff vs ordinary) |

## Falsification C2-A…G (policy layer)

| ID | Test | Result |
| --- | --- | --- |
| C2-A | `c2a_gatherYieldsOnCaveHandoffExploreAllowed` | Gather → YIELD; explore cave handoff → ALLOW |
| C2-B | `c2b_smeltYieldsOnControlledDescentDescentAllowed` | Smelt → YIELD; descent → ALLOW |
| C2-C | `c2c_combatRemainsTemporaryNotContention` | `COMBAT_TARGET` ≠ `CONTENTION` class |
| C2-D | `c2d_combatSuspensionDoesNotRevokeHealthyProjectImmediately` | SUSPEND not REVOKE on fresh combat episode |
| C2-E | `c2e_tunnelHandoffPendingIsNeutralAndDoesNotConsumeTransition` | NEUTRAL; transition preserved |
| C2-F | `c2f_contentionUsesStartLeaseWhenNeverStarted` | CONTENTION revokes after start lease |
| C2-G | `c2g_intentChangeYieldsGatherForContinuation` | CAVE_HANDOFF yields gather, allows explore |

Additional: active project preferred over pending transition; `SEARCH_BUDGET_EXHAUSTED` → `NONE` intent.

## Concerns

- **Runtime arbitration UNVERIFIED** — no Minecraft launch; GoalSelector handoff timing not observed in-world
- **C2-F full stack UNVERIFIED** — `MoveContentionPolicy` + live `WrappedGoal` scan not unit-tested with a real mob/selector mock
- **C2-D resume after combat UNVERIFIED** at goal layer — lease policy only; no integration test with `MiningDirector.authorizeExecution` loop
- **MAIBS C2 pass** not run this session — static policy tests only
- `LOW_FOOD` not wired (per brief)
- No commits, no Minecraft launch

## Self-review vs brief

- D-MIW-037: intent separated from `CONTENTION` producer (`CONFIRMED` by design + tests)
- D-MIW-038: `TUNNEL_HANDOFF_PENDING` → NEUTRAL, transition not consumed (`CONFIRMED`, `c2e`)
- All five participating goals wired in **both** `canUse` and `canContinueToUse` (`CONFIRMED` by inspection)
- Goal priority integers unchanged (`CONFIRMED`)
- Deferred: MI-14C3, `TunnelSearchGoal`, `LOW_FOOD` director classification
