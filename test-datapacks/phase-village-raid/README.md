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
  of running activities, pending mandatory ownership, and Village Work admission. It retains no
  session and changes no decision.

## Install and preflight

1. Use a disposable backed-up overworld. Copy this folder into `<world>/datapacks/phase-village-raid`.
2. Install only Fabric API 0.116.4+1.21.1, Social Player Mobs 0.89.0, and the exact Scavenger JAR
   pinned in `VR-T3-RUNTIME-ENVIRONMENT.md`. Exclude Trade Everything and other optional compat mods.
3. Start or reload the world, then run `/function spm_vr:help`.
4. Stand on flat overworld ground with clear space at least 32 blocks around the executor position.
5. Run one preset. For settlement rows, wait at least 120 ticks and apply matrix Gate 0 before
   judging product behavior.
6. Capture the starting game tick with `/time query gametime`, run
   `/spmscavenger debug v3 inspect @e[tag=spm_vr.subject,limit=1]`, and repeat snapshots at the
   row's meaningful transition and terminal window.
7. Record screenshots or video, exact tick range, relevant inspector/log lines, final block/entity/
   inventory state, and PASS/FAIL/INCOMPLETE in `VR-T3-RUNTIME-EVIDENCE.md`.
8. Run `/function spm_vr:cleanup` between rows. This removes fixture-tagged entities and scheduled
   helpers only. It deliberately preserves placed blocks because same-type world state lacks safe
   provenance; restore the disposable world backup or move to a fresh area between clusters.

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
