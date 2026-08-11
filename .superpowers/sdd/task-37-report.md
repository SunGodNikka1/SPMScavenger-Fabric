# Task 37 Report — GAO-6 (ENTITY bridge / SPM integration)

**Status:** `DONE` (static ACCEPT)  
**Gate:** GAO-6  
**Brief:** `task-37-brief.md`

## Summary

Implemented read-only SPM entity bridge, bounded supplemental entity opinion memory, first production
`SOCIAL_EXPEDITION` emitter on companion invite, and RET-GAO-1 snapshot/death lifecycle for entity prefs.

## Files changed

| Area | Path |
| --- | --- |
| Bridge | `opinion/SpmEntityOpinionBridge.java` |
| Memory | `opinion/EntityOpinionMemory.java` |
| Service | `opinion/EntityOpinionService.java` |
| Context/snapshot | `experience/MobExperienceContext.java`, `MobExperienceSnapshot.java` |
| Registry death | `experience/OpinionExperienceRegistry.java` |
| Emitter | `experience/ExperienceEmitters.java` |
| Wiring | `goal/ExploringGoal.java` |
| Tests | `EntityOpinionMemoryTest`, `SpmEntityOpinionBridgeTest`, `EntityOpinionServiceTest`; retention test extended |

## Verification

| Command | CWD | Result |
| --- | --- | --- |
| `.\gradlew.bat clean build` | `Projects/SPMScavenger-1.21.1-Fabric` | **BUILD SUCCESSFUL** — 569 tests, 0 failures (`CONFIRMED`) |

## Evidence labels

| Claim | Label |
| --- | --- |
| Compile + unit tests pass | `CONFIRMED` (build output) |
| Companion invite emits social event + +8 affinity | `CONFIRMED` (`EntityOpinionServiceTest`, wiring in `ExploringGoal`) |
| Entity memory survives park/unload | `CONFIRMED` (`OpinionExperienceRegistryRetentionTest`) |
| Death clears entity memory | `CONFIRMED` (`EntityOpinionServiceTest`) |
| SPM `feelingToward` at runtime with real PlayerMobs | `UNVERIFIED` (reflection path; no launch) |
| Utility supplement affects live discretionary SOCIAL scoring | `UNVERIFIED` (API only; no SOCIAL director yet) |

## Self-review vs brief

- D-GAO-007 respected: no SPM writes; supplemental memory only.
- No SOCIAL discretionary activity added (in scope exclusion).
- `travelsTogether` does not gate on `OpinionFeatureGate` — companion invites behave as before.
- `utilitySupplement` reserved for future SOCIAL scoring (±12 max, 75/25 SPM/learned blend).

## Concerns

- `SOCIAL_INTERACTION` emitter still absent (greet/follow deferred).
- Runtime SPM reflection parity not exercised this session.

## Must happen / must not happen

| Must happen | Result |
| --- | --- |
| First production `entity` field on experience event | PASS — `socialCompanionJoined` |
| Bounded entity memory with death clear | PASS |
| Snapshot round-trip on unload | PASS |

| Must not happen | Result |
| --- | --- |
| Duplicate SPM friendship graph authority | PASS — read-only bridge |
| Veto mandatory work via entity opinion | PASS — no director wiring |

---

## MAIBS-1 — post-implementation semantic-drift review (2026-08-10)

**Scope:** GAO-6 ENTITY bridge + `ExploringGoal` companion invite wiring  
**Gate result:** **`CONDITIONAL — ACCEPTABLE_STEPPING_STONE`** (physical loop) + **`ARCHITECTURE_DEFECT`** (episode ownership sub-loop when companions join)

### PLANNED → IMPLEMENTED → PREDICTED RUNTIME

| Layer | Planned | Implemented | Predicted observable |
| --- | --- | --- | --- |
| SPM authority | Read-only `feelingToward`; no ledger writes | `SpmEntityOpinionBridge` + `PlayerMobs` reflection only | Same companion eligibility as pre-GAO-6 (`CODE_CONFIRMED`) |
| Supplemental memory | Bounded entity affinity, not friendship graph | `EntityOpinionMemory` LRU 16; leader-only `+8` on invite | No visible mob behavior; internal leader bias only (`CODE_CONFIRMED`) |
| Social experience | `SOCIAL_EXPEDITION` on companion invite | `ExperienceEmitters.socialCompanionJoined` on successful `acceptCompanionInvitation` | No movement change; optional mood pulse on leader if opinion on (`CODE_CONFIRMED`) |
| Discretionary SOCIAL | Future utility supplement | `utilitySupplement` has **zero production callers** | **No effect** on EXPLORE/REST choice (`CODE_CONFIRMED` — 3× `NOT FOUND`: `DiscretionaryActivity`, `ActivityUtilityScorer`, `IdleOpportunityPolicy`) |

### Intent vs reality (material behaviors)

| Intended | Mechanism | Predicted player observation | Weirdness | Confidence |
| --- | --- | --- | --- | --- |
| Companions join liked mobs on expedition | `inviteCompanions` after `PlanResult.READY`; `travelsWith` → mutual `feeling > neutral` | 0–4 nearby PlayerMobs peel off in parallel with lateral offset; walk same heading | Entity scan order undefined — arbitrary who fills slots | `CODE_CONFIRMED` |
| SPM owns friendship | `travelsTogether` reads SPM only; learned memory not used for invite gate | Repeated joint trips do **not** lower SPM bar; supplemental memory unused for invites | Learned +8 on leader only; companions never record leader | `ACCEPTABLE_STEPPING_STONE` |
| Social experience enriches mood/opinion | `SOCIAL_EXPEDITION` terminal on **same** `expeditionEpisodeId` | First companion: engagement/satisfaction/novelty bump on leader; **closes expedition episode** | `EXPEDITION_END` learning swallowed for that expedition (`EpisodeBoundaryPolicy` terminal) | **`ARCHITECTURE_DEFECT`** |
| Entity opinion biases future social utility | `utilitySupplement` ±12 | **Nothing** — no consumer wired | Dead API until GAO-7/SOCIAL director | `ACCEPTABLE_STEPPING_STONE` |

### Geometry + time trace (representative)

**Setup (`CODE_CONFIRMED` from `ExploringGoal`):** Leader **L** at `(100, 64, 100)`; companion **C** at `(108, 64, 104)` (within `exploreCompanionRadius` 4–24). Expedition waypoints planned; `PlanResult.READY` triggers one-shot `inviteCompanions`.

| Tick | Owner | Physical action |
| --- | --- | --- |
| T0 | `DiscretionaryActivityDirector` → `ExploringGoal` | L idle; director adopts EXPLORE |
| T+plan | `ExploringGoal` | `createExpedition`; path to first waypoint |
| T+ready | `ExploringGoal.inviteCompanions` | C accepts via `acceptCompanionInvitation` (bypasses readiness); parallel expedition with `companionLateralOffset(slot)` |
| T+60…1200 | Each mob's navigation | Independent paths along shared heading; may desync on obstacles (`GAME_MECHANICS_INFERRED`) |
| T+≤2400 | `ExploringGoal` | Complete/abandon; companions use same lifetime rules |

**Arrival / termination:** Unchanged from pre-GAO-6 — waypoint arrival radius, stall timeout, `MAX_EXPEDITION_TICKS` (2400), stay-anchor yield, combat `getTarget != null`.

### GoalSelector table (relevant slice)

| Goal | Priority | Flags | Interrupts explore? | State retained | Observable |
| --- | ---: | --- | --- | --- | --- |
| SPM `FollowLovedOneGoal` | 2 | MOVE | Yes — higher than `ExploringGoal` (8) | SPM | Follow one loved entity; **not** parallel expedition (`CODE_CONFIRMED` classifier) |
| `ExploringGoal` | 8 | MOVE | — | `ExpeditionState`, waypoints | Long-range heading walk |
| `ExplorationActivityGoal` | 9 | — | Readiness/director | Readiness counters | Gates explore eligibility |

**Interruption:** Combat target on L or C → `travelsWith` false / `acceptCompanionInvitation` false → no new invites; in-flight expedition may `stop()` on yield (`CODE_CONFIRMED`).

### RET-1 (entity memory)

| Field | Value | Evidence |
| --- | --- | --- |
| Key | Companion `UUID` | `EntityOpinionMemory` |
| Bound | 16 LRU | `removeEldestEntry` |
| Eviction | On insert | production `recordOutcome` |
| Death | `onDeath` → `clear()` | `OpinionExperienceRegistry` |
| Unload | Snapshot in `MobExperienceSnapshot` | retention test |
| `utilitySupplement` consumer | **None** | `NOT FOUND` ×3 |

### Predicted weird behaviors (≥3)

| # | Behavior | Class | Falsifying probe |
| --- | --- | --- | --- |
| 1 | First companion invite **closes** expedition episode; completion `EXPEDITION_END` does not commit OVERLAND_EXPLORATION terminal learning | **`ARCHITECTURE_DEFECT`** | Launch: expedition with companion → compare `opinionMemory(OVERLAND_EXPLORATION)` after successful completion vs solo expedition |
| 2 | Second+ companion gets `+8` entity memory but **no** second affect pulse (episode already tombstoned) | **`ARCHITECTURE_DEFECT`** | Log affect channels after inviting 2 companions in one tick |
| 3 | Leader stacks supplemental affinity (+8×N) toward same UUID; **never** affects who can be invited (SPM gate only) | `ACCEPTABLE_STEPPING_STONE` | Repeated expeditions with same pair; invite set unchanged |
| 4 | `utilitySupplement` dead — player sees **zero** discretionary SOCIAL bias | `ACCEPTABLE_STEPPING_STONE` | Grep + runtime: no SOCIAL in `DiscretionaryActivity` |
| 5 | Parallel expeditions ≠ SPM follow — mobs can walk together without `FollowLovedOneGoal` | `ACCEPTABLE_STEPPING_STONE` (by design per `ExploringGoal` javadoc) | Observe no follow goal but shared heading |

### Acceptance tests

**Must happen (physical):** Leader with mutual-above-neutral companions within radius recruits 1–4 mobs once route is walkable; companions walk parallel without SPM ledger mutation.  
**Must not happen:** Entity opinion vetoes mining/combat/stay-anchor; SPM friendship overwritten.

**Must happen (learning — currently FAIL):** Expedition episode remains open from `EXPEDITION_UNLOCKED` until `EXPEDITION_END` even when companions join.

### Runtime probes (`RUNTIME_QUESTION`)

1. **Companion parity:** Two PlayerMobs, feelings 6/6, `exploreCompanions=true` — confirm log `[exploration departed … companions=N]`.
2. **Episode defect:** Opinion on — solo vs companion expedition — measure `OVERLAND_EXPLORATION` preference after completion.
3. **SPM contention:** Mob with active `FollowLovedOneGoal` — discretionary explore must not preempt (pre-existing GAO-1 parity).

### Recommended fix (not implemented)

Emit `SOCIAL_EXPEDITION` on a **dedicated milestone episode id** (or reclassify as non-terminal milestone) so it does not close the expedition episode. Until fixed, static ACCEPT stands for bridge/memory only; expedition learning integrity is **`PARTIAL`** when companions join.

---

## GAO-6R — social sub-episode repair (Task 38)

**Status:** `DONE` (static ACCEPT) — **GAO-6 phase CLOSED**

**Fix (`CONFIRMED`):** `SocialExperienceEpisodes.companionInviteEpisodeId(expeditionEpisodeId, companionId)` —
deterministic social sub-episode per companion; `ExperienceEmitters.socialCompanionJoined` no longer
aliases the exploration episode. Tombstone guard makes duplicate same-companion invites idempotent.

**Ownership after repair:**

```text
EXPEDITION_UNLOCKED (exploration episode open)
        ├─ companion A → SOCIAL sub-episode A → terminal (compact)
        ├─ companion B → SOCIAL sub-episode B → terminal (compact)
        └─ EXPEDITION_END → terminal (exploration episode)
```

**Tests:** `SocialCompanionEpisodeRepairTest` (5) — all four repair proofs + deterministic id.

**MAIBS re-pass:** `PASS — BEHAVIORALLY_PLAUSIBLE` for episode ownership; defects #1–#2 **resolved**.

**Build:** `.\gradlew.bat clean build` — BUILD SUCCESSFUL; **574 tests**, 0 failures.

**Frontier:** **GAO-7** PersonalityModel.
