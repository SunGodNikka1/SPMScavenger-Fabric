# V4-G representative runtime witness

## Evidence status and exact environment

**Preparation:** `STATIC/PACKAGE CONFIRMED` on 2026-08-27.  
**Physical behavior:** `UNVERIFIED` until the separately approved Minecraft session completes.  
**World:** disposable Overworld test area or a backed-up copy; the fixture preserves placed blocks.

| Component | Pin |
| --- | --- |
| Minecraft | `1.21.1` |
| Fabric Loader | `0.16.14` |
| Fabric API | `0.116.4+1.21.1` |
| Social Player Mobs | `playermob` `0.96.0`; SHA-256 `508EDA58611A2A0738E257F98C2E14C5032C6EFBF5B1A985C9F93EE295131097` |
| Production addon | `build/libs/spmscavenger-1.11.0.jar`; SHA-256 `918CA885EBD5FA985FBE234DE11D05E983DFAF882A4092921BA15F46B59E089B` |
| Validation sidecar | `build/libs/spmscavenger-1.11.0-validation.jar`; SHA-256 `2EA58B7A50D6B5E30CF4A9D840A0EC9EF6C988C1C4787C9DD8BF7AC12D32DC27` |

The runtime command records the actual relevant Scavenger configuration in `status` and `report`.
Preflight requires `enabled`, `gatherResources`, `craftTools`, `seekShelter`, and `sleepInBeds`; a
pick target of at least IRON; `0 < gatherSearchRadius <= 20`; `shelterSearchRadius >= 6`; and
`torchStockTarget <= 64`. The build defaults are gather radius `20`, shelter radius `16`, pick cap
`DIAMOND`, and torch target `8`. A mismatch terminates as `FIXTURE_FAILURE` before fixture mutation.

## One-command bounded procedure

Install the exact production and validation JARs beside the pinned host/Fabric dependencies. Stand
at the village end of a disposable, open, approximately 220 x 64 block arena in the Overworld, then
run:

```text
/spmscavenger debug v4 run
```

The command creates one fixture and then requires no operator babysitting. Before entity creation,
the validation sidecar forces the owned chunks, creates structural geometry plus non-obstructing
level-15 Light blocks, and enters `WAITING_FIXTURE_LIGHTING` for at most 200 ordinary server ticks.
Minecraft's threaded light engine propagates naturally; validation samples block light once per
tick and never calls or blocks on a synchronous light-engine flush. Only after light reaches the
minimum threshold does validation disable `doMobSpawning`, discard unrelated hostile entities
already inside the owned arena without damage, and create the required fixture entities. It
restores the original gamerule during every terminal, stop, and reset path. It does not change
difficulty or continuously delete entities after bootstrap begins.

The controller then performs:

1. natural settlement/board/route bootstrap, bounded to 2,400 ticks;
2. Phase A REQUIRED_TRADE return and changed-live-offer transaction, bounded to 2,400 ticks;
3. Phase B real shelter sleep and first-HOME promotion, bounded to 2,400 ticks.

The maximum planned behavioral evidence duration is therefore 7,200 ticks after successful setup,
preceded by at most 200 lighting-readiness ticks during which no fixture entities exist. The
lighting window does not consume startup-stability or behavioral bootstrap time. These are tick
budgets, not wall-clock promises. The subject is moved exactly once to the declared departure point
before Phase A opens and is never steered afterward.

Optional progress inspection:

```text
/spmscavenger debug v4 status
```

At terminal state:

```text
/spmscavenger debug v4 report
```

After saving the report/log evidence:

```text
/spmscavenger debug v4 reset
```

For an operator-aborted run use `stop`, save its report, then `reset`:

```text
/spmscavenger debug v4 stop
/spmscavenger debug v4 report
/spmscavenger debug v4 reset
```

`reset` removes only exact tagged fixture entities. It deliberately does not guess that matching
world blocks are fixture-owned after production has run; restore the disposable world/backup for a
full arena rollback.

## Verdict contract

`PASS` requires both phases in order. Phase A must report HOME absent, the initial `8 emerald -> 1
iron_pickaxe` board observed by the production V2 memory seam, the live board changed to `10
emerald -> 1 iron_pickaxe`, iron-pick demand with route `INFEASIBLE`, a production director intent,
existing REQUIRED_TRADE COMMUTE, anchor arrival/release, changed-board rediscovery, and transaction
of the changed fingerprint. Executing the initial fingerprint is immediate `FAIL`.

During bootstrap, a locally bound REQUIRED_TRADE intent is legal even before positive capability
memory exists. V4-C ranks `UNKNOWN` remembered settlements after positive hints; V4-D does not make
positive evidence an opening prerequisite. The report therefore records
`bootstrapLocalRequiredTradeCount`, `bootstrapLocalCommuteSeedCount`,
`bootstrapLocalArrivalCount`, and `bootstrapLocalIntentReleased`. Those events are bootstrap
evidence only and cannot become the Phase-A binding. Warm-up completion requires the local intent
to release/close naturally before the deliberate 180-block departure. Validation never clears it.

Phase B opens only afterward. It requires familiarity 600, exactly one remembered-settlement
association for the fixture beds, a running production `SeekShelterGoal`, real sleeping state, and
HOME equal to the canonical remembered anchor. The fixture never calls intent opening, ranking,
navigation, trade execution, `startSleeping`, `FirstHomePromotion`, or `designateHome`.

The optional tagged-hostile interruption is a sub-proof. If natural targeting occurs, the report
must show navigation disposal, retained exact intent, fresh revalidation/resume, and zero route
failure publication. If targeting does not occur within 200 ticks, the report marks that sub-proof
`INCOMPLETE`; it does not fabricate interruption or invalidate an otherwise meaningful principal
result.

## Known falsifiers and fixture risks

- Canonical anchor farther than 16 blocks from the fixture trader: `FIXTURE_FAILURE` with exact
  distance; no stale trader-position navigation is substituted.
- Threaded block light remains below seven after 200 ticks: `FIXTURE_FAILURE` with lighting start,
  deadline, wait, sample, and minimum-light evidence. No subject/trader/helper is created, and the
  owned forced chunks are released by normal teardown.
- Natural settlement/board observation or Gather exhaustion absent within bootstrap budget:
  `INCOMPLETE`, not product failure.
- A bootstrap-local REQUIRED_TRADE that outlives demand resolution: retained as product evidence;
  validation does not cancel it or let Phase A inherit its binding.
- The actual production villager query reports zero candidates: the same callback reports direct
  subject/trader positions, distance, life/sleep/trading-player/availability facts from tracked
  UUIDs without issuing another query. Unavailable terminal facts render `NOT_MEASURED`, never
  factual `false` or `0` defaults.
- Route-exhaustion evidence expires before the remote return completes: `INCOMPLETE` with status
  history; validation never republishes it.
- Validation mixin attachment and the physical SPM 0.96 scheduler/navigation/sleep sequence remain
  `UNVERIFIED` until this artifact pair loads and runs.

Alternative considered: two independent sessions would simplify each fixture, but would weaken the
causal HOME-negative proof and add operator/runtime work. The selected sequential session keeps HOME
absent until the trade integration is complete. A mandatory interruption was also considered; it
was rejected because host target acquisition is itself nondeterministic and could turn a V4 proof
into a combat-fixture test. Switch only if a deterministic, non-authoritative host interruption seam
is later demonstrated.
