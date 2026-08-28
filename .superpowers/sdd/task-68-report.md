# Task 68 report: V4-G runtime validation preparation

## Result

**PREPARED / STATIC+PACKAGE CONFIRMED. Runtime behavior remains UNVERIFIED.** Minecraft was not
launched and no production Java behavior was changed.

The validation sidecar now owns one bounded `v4` campaign that runs REQUIRED_TRADE return before
first-HOME promotion. Five validation-only mixins passively observe the production board-memory,
director/revalidation/arrival, existing COMMUTE seed/stop, exact transaction, and successful-sleep
promotion seams. The controller can mutate declared fixture inventory/world/offers/time, but its
structural gate forbids intent opening, ranking, post-window steering, transaction, sleep, HOME, and
route-exhaustion authority calls.

## Commands and windows

```text
/spmscavenger debug v4 run
/spmscavenger debug v4 status
/spmscavenger debug v4 report
/spmscavenger debug v4 stop
/spmscavenger debug v4 reset
```

Natural bootstrap, Phase A, and Phase B are each bounded to 2,400 ticks: at most 7,200 ticks after
successful setup. See `docs/porting/V4-G-RUNTIME-RUNBOOK.md`.

## Verification

- Focused validation tests: **65 passed**, zero failures/errors/skips.
- `gradlew.bat clean build`: **PASS**, 1,719 production + 65 validation tests.
- Production audit: 0 validation classes, 0 `spm_v4` resources, 0 upstream SPM classes, 0 upstream
  Trade Everything classes.
- Validation audit: 76 validation classes, 5 `spm_v4` resources, 0 production-class duplicates, 0
  upstream SPM/Trade Everything classes.
- Production JAR: `build/libs/spmscavenger-1.11.0.jar`, 1,224,539 bytes, SHA-256
  `918CA885EBD5FA985FBE234DE11D05E983DFAF882A4092921BA15F46B59E089B`.
- Validation JAR: `build/libs/spmscavenger-1.11.0-validation.jar`, 187,888 bytes, SHA-256
  `267381CE2A0255091428FF73621252AB283D448DD9D0E2F6B0AE2AD7ED5831C8`.
- Host SPM v0.96 artifact SHA-256:
  `508EDA58611A2A0738E257F98C2E14C5032C6EFBF5B1A985C9F93EE295131097`.

## Acceptance and negative controls

Must happen at runtime: HOME absent during Phase A; initial board remembered; changed live board
executed after production REQUIRED_TRADE/COMMUTE/arrival; then real shelter sleep promotes the same
canonical anchor.

Must not happen: initial cached terms execute; validation opens intent or ranks; subject is moved
after Phase A opens; validation calls trade/sleep/HOME; interruption publishes route failure.

Negative tests prove an initial-offer execution is recorded as failure, active revalidation before
interruption cannot fabricate resume evidence, wrong backpack/trader observations are ignored, and
reset releases all retained references.

## Risks retained honestly

The actual mixin attachment, SPM scheduler/navigation, settlement bootstrap, natural Gather
exhaustion lifetime, V2 live transaction, and sleep-to-HOME chain remain runtime `UNVERIFIED`.
Anchor/trader distance over 16 is a fixture failure. The optional hostile interruption can remain
`INCOMPLETE` without falsifying the principal two-phase proof, because natural target acquisition is
not fixture authority.
