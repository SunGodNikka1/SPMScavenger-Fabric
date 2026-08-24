# Task-59 / V3-G runtime fixture

Temporary Minecraft 1.21.1 runtime kit for the Village Work VR-T3 campaign. Canonical behavior,
windows, and falsifiers live in `docs/porting/VR-T3-RUNTIME-MATRIX.md`; record results in
`docs/porting/VR-T3-RUNTIME-EVIDENCE.md`.

## Authority boundary

- **Fixture authority:** each `spm_vr:scenario/*` function creates only declared world/entity/
  inventory/profile inputs and tags fixture entities for cleanup.
- **Production authority:** ordinary Scavenger and Social Player Mobs Goals choose, path, admit,
  mutate, and terminate. The fixture does not force a Goal or award an expected result.
- **Evidence authority:** `/spmscavenger debug v3 inspect <mob>` takes a one-shot read-only snapshot
  of running activities, pending mandatory ownership, Village Work admission, the subject's actual
  remembered in-bounds settlement, and cached population facts. It retains no session, creates no
  settlement/facts cache, schedules no refresh, and changes no decision.
- **Campaign authority (temporary):** `/spmscavenger debug v3 run <preset>` owns only fixture setup,
  pre-window removal of unrelated PlayerMobs in the protected observation envelope, natural Gate-0 waiting,
  a logged pre-window day/weather transition, fixture chunk tickets, row timing, and passive
  evidence. It never starts a Goal, publishes a claim, changes admission, writes HOME/Brain/sleep
  state, steers the subject after opening, or assigns a product verdict.

## Install and preflight

1. Use a disposable backed-up overworld. Copy this folder into `<world>/datapacks/phase-village-raid`.
2. Install only Fabric API 0.116.4+1.21.1, Social Player Mobs 0.89.0, and the exact Scavenger JAR
   pinned in `VR-T3-RUNTIME-ENVIRONMENT.md`. Exclude Trade Everything and other optional compat mods.
3. Start or reload the world, then run `/function spm_vr:help`.
4. Stand on flat overworld ground with clear space at least 32 blocks around the executor position.
5. Start exactly one row with `/spmscavenger debug v3 run <preset>`. The controller establishes
   `PREPARING`, executes the loaded preset at the command origin on the next server tick, waits for natural Gate 0, advances the disposable fixture to day,
   waits up to 200 daytime ticks for genuine `SHELTER_HOLD` release, and opens the evidence window
   only after `RowPrecondition=READY`.
6. Play normally or stand away. Use `/spmscavenger debug v3 status` for bounded progress and
   `/spmscavenger debug v3 report` for opening/transition/terminal evidence. The controller force-
   loads only 32-block scenario-core chunks it newly acquired and releases those exact chunks at every terminal.
7. The controller returns observation states—not product PASS/FAIL. Review its raw report against
   the matrix, then record the adjudicated result in `VR-T3-RUNTIME-EVIDENCE.md`.
8. Run `/spmscavenger debug v3 reset` between rows. This releases controller state and invokes the
   existing tagged fixture cleanup. The manual fallback `/function spm_vr:cleanup` removes fixture-
   tagged entities and scheduled helpers only. It deliberately preserves placed blocks because
   same-type world state lacks safe
   provenance; restore the disposable world backup or move to a fresh area between clusters.

### Controller dispositions

- `OBSERVATION_COMPLETE`: the required observation clock completed; it is **not** product PASS.
- `INCOMPLETE`: the required production transition did not occur before the bounded timeout.
- `FIXTURE_INCOMPLETE`: daytime shelter ownership did not release within 200 ticks.
- `FIXTURE_FAILURE`: preset/Gate-0 fixture contract failed.
- `EXTERNAL_INTERFERENCE`: unrelated PlayerMob contamination entered after the row opened.

If the JVM or host crashes, ordinary lifecycle cleanup cannot run. Treat the world as disposable
and inspect vanilla forced chunks before reuse; do not remove foreign forced chunks blindly.

If startup fails, `status` and `report` retain `startupStage`, exception class/message, and root
cause; `latest.log` retains the stack trace. Use `reset` for tagged partial-fixture cleanup.

After `WINDOW_OPEN`, the 32-block scenario core is evidence geometry, not a leash. A subject exit
emits `SUBJECT_LEFT_CORE` with horizontal distance, `pendingClaim`, and `activeClasses`, and the row
continues. Contamination detection covers the 192-block observation envelope on a bounded cadence.
The 224-block escape boundary can end geometry-dependent rows as spatially `INCOMPLETE`, but VR-T3j
continues because its authority evidence remains meaningful away from the core. The controller does
not teleport, steer, freeze, or path-correct the subject, and it does not force envelope chunks.

## Campaign order

1. `mandatory_blocks_village_work`, `mandatory_ownership_witness`
2. `storage_public_deny`, `storage_unknown_deny`, `storage_granted_permit`
3. `crop_managed_single`, `crop_interrupt_combat`, `crop_replant_failure`, `crop_multi_mob`,
   `crop_hungry_veto`, `crop_multi_cycle`
4. `population_food_deficit`
5. `compost_seed_surplus`

Use `/function spm_vr:help` for the exact function names. VR-T3f is not part of this campaign.

## Verdict discipline

- `PASS`: the complete minimum window shows the must-happen and excludes the must-not-happen.
- `FAIL`: a locked invariant is visibly violated; record the first violation and do not repair code
  during the campaign.
- `INCOMPLETE`: setup, pathing, target availability, or observation was insufficient to decide.
- `WEIRD`: unexpected visible behavior that does not yet prove the invariant passed or failed.

Datapack setup and clean builds do not prove runtime behavior. No row becomes `CONFIRMED` until its
approved live evidence is recorded.

## Gate-0 inspector verdict

After a settlement preset has run for at least 120 ticks, the same inspect command prints:

- `settlement observed: YES/NO`, anchor, and `SettlementIdentity`;
- adult villagers, total usable HOME capacity, claimed HOME count, and current free capacity;
- completeness and freshness;
- `Gate0=PASS`, `Gate0=FIXTURE_FAILURE`, or `Gate0=INCOMPLETE`.

`INCOMPLETE` means normal production has not yet produced readable current facts. Wait for its
ordinary cadence and sample again; do not invoke refresh or inject HOME/sleep state. Stop the
campaign on `FIXTURE_FAILURE` and preserve the output.

The automated controller measures those 120 ticks from successful scenario-function execution,
not command submission. While it reports `WAITING_GATE0_BOOTSTRAP`, readable values such as
`claimedHomeCount < 2` are diagnostic intermediate state and cannot terminate the run. Numeric
thresholds become adjudicable only at elapsed bootstrap tick 120; the overall Gate-0 timeout remains
2400 ticks.

Gate 0 is independent of activity release. The inspector also reports daytime, `SHELTER_HOLD`, and
one row-precondition verdict. `WAITING_DAYTIME` means transition/wait and resample.
`FIXTURE_INCOMPLETE` means shelter remained active during daytime: preserve the snapshot and do not
start the settlement-row clock. The command never changes time or stops shelter itself.
