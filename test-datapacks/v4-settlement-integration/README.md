# V4-G single-village validation fixture

This datapack is packaged only in `spmscavenger-1.11.0-validation.jar`. The validation command owns
the complete setup and passive witness lifecycle:

```text
/spmscavenger debug v4 run
/spmscavenger debug v4 status
/spmscavenger debug v4 report
/spmscavenger debug v4 stop
/spmscavenger debug v4 reset
```

The operator stands at the intended village-anchor end of a disposable test area in the Overworld.
`run` first acquires the exact validation-owned chunk set, then
`V4FixtureGeometryBuilder` creates and verifies the flat 180-block corridor, departure area, three
beds, bell, workstation, and all required entity spawn spaces. Only after that geometry gate passes
does the validation controller create the fixture trader, helper villager and PlayerMob through
checked registered-entity spawn/finalization gates. Bootstrap remains behind a separate first-tick
lifecycle stability gate. The legacy `spm_v4:scenario/v4_g` resource is documentation-only and is
not a runtime geometry owner. The controller supplies the controlled offer/inventory and waits for
production behavior; it does not force intent, navigation, trade, sleep, or HOME.

Fixture-owned tagged entities are removed by `reset`. Placed blocks are deliberately preserved:
after production begins, type/location alone is insufficient provenance for destructive rollback.
Use a disposable world or restore a backup.

Expected bounded windows:

- settlement + natural Gather/initial-board bootstrap: at most 2,400 ticks;
- Phase A REQUIRED_TRADE return/changed-offer transaction: at most 2,400 ticks;
- Phase B shelter/sleep/first-HOME: at most 2,400 ticks;
- overall hard stop: 7,200 ticks after successful setup.

The optional interruption sub-proof uses one exact tagged hostile. Failure to produce a natural
combat interruption is reported as `INCOMPLETE` for that sub-proof and never manufactures PASS.
