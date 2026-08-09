# Task 20 report: MI-7A — minimal MiningProject session state

## Status

`DONE_WITH_CONCERNS`

## Summary

Added mining session types under `com.noobk.spmscavenger.mining`: `MiningProjectMode`
(including `CONTROLLED_DESCENT`), `MiningProjectEnd`, `MiningBudget` caps record,
immutable `MiningProject` session, and dimension `MiningProjectSavedData` with NBT
round-trip. No goal wiring or excavation executor.

## Files

| File | Change |
| --- | --- |
| `mining/MiningProjectMode.java` | RFC mode catalog |
| `mining/MiningProjectEnd.java` | Terminal/interrupt reasons → `TaskLifecycle` |
| `mining/MiningBudget.java` | Caps record + `controlledDescentDefaults()` |
| `mining/MiningProject.java` | Session record, factories, NBT |
| `mining/MiningProjectSavedData.java` | Per-mob dimension store |
| `mining/MiningProjectTest.java` | Unit tests |

## Verification

| Command | CWD | Result |
| --- | --- | --- |
| `.\gradlew.bat test` | `SPMScavenger-1.21.1-Fabric` | `BUILD SUCCESSFUL` — **200** tests (`CONFIRMED`) |

## Evidence labels

| Claim | Label |
| --- | --- |
| CONTROLLED_DESCENT start + default budget | `CONFIRMED` — unit |
| End reason → lifecycle mapping | `CONFIRMED` — unit |
| SavedData NBT round-trip | `CONFIRMED` — unit |
| Return route cap 32 | `CONFIRMED` — unit |
| Runtime project persistence across reload | `UNVERIFIED` — no launch |

## Concerns

1. No goal or director reads `MiningProjectSavedData` yet (MI-7E / MI-14).
2. `MiningBudget` has caps only — usage counters and exhaustion in MI-7B.
3. `NaturalDescentExhaustionPolicy` (MI-7C) not started.

## Self-review vs brief

- Types + session API + SavedData: done
- Goal wiring / dig / exhaustion: correctly deferred
- Handoff enums `CAVE_FOUND`, `HANDOFF_TUNNEL_SEARCH` added for MI-7E (RFC-aligned)

## MAIBS-1 semantic-drift review (2026-08-09)

**Scope:** MI-7A post-implementation + live descent stack (MI-5, MI-6A/D/B/C).

```text
PLANNED (MI-7A)  → session types + SavedData for CONTROLLED_DESCENT
IMPLEMENTED      → mining/* records; zero goal/director references
PREDICTED RUNTIME → identical to pre-MI-7A; no observable world change
```

**NOT FOUND (3 probes):** `MiningProjectSavedData.get` in goals; `MiningProject` in
`goal/*`; `CONTROLLED_DESCENT` outside `mining` package.

**Gate MI-7A:** `CONDITIONAL — ACCEPTABLE_STEPPING_STONE` (foundation only; authorized).

**Gate full controlled descent:** `UNVERIFIED` / blocked until MI-7B–E + MI-7C wire.

**Must happen (7A):** SavedData round-trip — `CONFIRMED` unit.
**Must not (7A):** mob digs staircase — `CONFIRMED` absent (no executor).

**Falsifying probe (7A):** launch + log — project store must stay empty unless test code
writes it; after MI-7E, log `MiningProject.mode` on each descent step.
