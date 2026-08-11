# Task 37 Brief — GAO-6 (ENTITY bridge / SPM integration)

**Gate:** GAO-6  
**Depends on:** GAO-4, RET-GAO-1  
**Out of scope:** SOCIAL discretionary activity, PersonalityModel, SPM ledger writes, runtime launch

## Problem

`ExperienceEvent.entity` existed in schema but had no production emitters. Companion invites already
read SPM `feelingToward` ad hoc; entity affinity had no bounded supplemental memory or unified bridge.

## Design (D-GAO-007)

- **`SpmEntityOpinionBridge`** — read-only `feelingToward` → normalized utility channel; mutual-above-neutral
  companion gate; fails closed when unreadable. Never writes SPM relationship state.
- **`EntityOpinionMemory`** — bounded LRU (16) supplemental per-entity affinity; not a second friendship graph.
- **`EntityOpinionService`** — applies social experience deltas to supplemental memory only.
- **`ExperienceEmitters.socialCompanionJoined`** — `SOCIAL_EXPEDITION` on successful companion invite;
  wires `ExploringGoal.inviteCompanions`.
- **Snapshot + death** — entity preferences in `MobExperienceSnapshot`; cleared on death like PLACE.

## Constraints

- SPM owns relationships (D-GAO-007); learned entity memory is 25% of utility supplement max, SPM 75%.
- No veto on mandatory work; no SOCIAL discretionary director activity in this slice.
- Companion gate unchanged semantically (mutual above neutral); opinion-off does not disable SPM reads.

## Acceptance

- Companion invite emits `SOCIAL_EXPEDITION` with `entity` UUID and records +8 learned affinity.
- Entity memory survives park/unload; clears on death.
- Bridge maps SPM feeling scale; utility supplement capped at ±12 soft bias.
- Opinion disabled skips supplemental learning only.

## Verification

```text
.\gradlew.bat clean build
```
