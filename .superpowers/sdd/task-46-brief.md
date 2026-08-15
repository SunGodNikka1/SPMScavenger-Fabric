# Task 46 — V1.5 Settlement attachment & return

**Status:** authorized (User: D-VR-052 REJECT V1.5-E; task-46 AUTHORIZED)  
**Target version:** 1.11.0  
**RFC:** `plans/RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md` — V1.5 implementation contract, D-VR-034…052

## Goal

Ship mob-owned settlement attachment (familiarity + visit history), autonomous return commute,
village-aware discretionary SOCIAL, and a **temporary** `designate-home` operator command for
VR-T1.5a. **Do not** ship V1.5-E (`RaidContainersGoal` suppression) — deferred to V3
`StorageOwnership` per D-VR-052.

## Target

`d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

## Source evidence (in-repo, `CONFIRMED`)

| Path | Role |
| --- | --- |
| `village/MobVillageMemory.java` | Factual villages; `designateHome()`; Option A host |
| `village/KnownVillage.java` | Factual only — no attachment fields |
| `village/VillageMemorySavedData.java` | `record`, `designateHome` persistence |
| `village/VillagePerception.java` | `VILLAGE_QUERY_RADIUS = 64` |
| `goal/ExploringGoal.java` | `MAX_EXPEDITION_DISTANCE = 150`; commute admission site |
| `goal/VillagePerceptionObserver.java` | Presence heartbeat hook |
| `opinion/SocialExecutionBindingRegistry.java` | Greet binding; extend with settlement anchor |
| `mixin/FriendlyGreetAdmissionSeamMixin.java` | Greet admission seam for anchor snapshot |
| `test/.../VillagePerceptionContractTest.java` | VR-T1A removal guards — must stay green |

## Binding constraints

- No Minecraft launch (VR-T1.5 separate approval).
- No commits unless user requests.
- **No** `village-memory`, `village-probe`, or `village-driver` commands (D-VR-051; contract test).
- **No** `RaidContainersGoal` mixin or V1.5-E code (D-VR-052).
- Temporary `/spmscavenger designate-home` only; **must be removed** after VR-T1.5 PASS (same lifecycle as VR-T1A diagnostics).
- Home factual owner: `MobVillageMemory.designateHome()` only. Relationship stores **no** home flag.
- Return requires `ScavengerConfig.exploring == true` (D-VR-046).
- Bob VR-T1.5: overworld-only; no Nether/cross-dimension retest.

## Slices (in order)

### V1.5-A — `SettlementRelationship` persistence

- New `SettlementRelationship` record: `familiarityScore`, `lastVisitTick`, `socialEventCount`.
- `attachmentBand()` derived from score only — **not** NBT (LOW `< 200`, MEDIUM `200–599`, HIGH `≥ 600`).
- Option A: `Map<BlockPos, SettlementRelationship> relationships` on `MobVillageMemory`; key = canonical merged anchor.
- NBT codec on `MobVillageMemory.save` / `load`.
- RET-1: evict relationship row when village LRU evicts non-home settlement.
- D-VR-049: `rekeyRelationship(oldAnchor, newAnchor)` on anchor supersede; merge on `remember()` (D-VR-044).

### V1.5-B — Accumulation (`SettlementRelationshipService`)

Single write path (D-VR-041):

```text
onVillageRecorded(mob, KnownVillage, tick)
onPresenceHeartbeat(mob, anchor, tick)      // SettlementBoundsPolicy @ 64²
onSocialEpisode(mob, anchorAtStart, tick) // D-VR-050
onHomeDesignated(mob, anchor, tick)       // familiarity floor only
```

- `SettlementBoundsPolicy.within(mobPos, anchor)` → `distSqr ≤ 64²` (D-VR-040).
- Gen-1 tuning (constants, VR-T1.5b may tune): visit bump on stale `record`; `+1`/200t presence heartbeat; social bump on completed greet with anchor at admission; home designation floor.
- Wire hooks: `VillageMemorySavedData.record`, `VillagePerceptionObserver` heartbeat, binding terminal `COMPLETED`.

### V1.5-C — Return commute

- `SettlementReturnPolicy.shouldCommute` / `trySeedCommute`.
- `ExpeditionKind.COMMUTE` — non-discretionary; **does not** call `DiscretionaryAuthority.onExploreAdopted`.
- Admission in `ExploringGoal.canUse()` **after** `MiningExecutionGuard` + cave handoff, **before** `mayStartDiscretionaryExplore()` (D-VR-047).
- Multi-leg chaining until inside `SettlementBoundsPolicy` (D-VR-048); reuse `MAX_EXPEDITION_DISTANCE = 150` per leg.
- Refuse during mining commitment, cave handoff, `readiness.hasDescentPressure()` (D-VR-043).
- Target: `home().anchor()` else highest-familiarity anchor; min distance threshold (e.g. 128 blocks).

### V1.5-D — Village-aware SOCIAL

- `SettlementSocialBias` in opinion package — bounded bump to discretionary SOCIAL near MEDIUM+ familiarity settlement (D-VR-045).
- **No** global `FriendlyGreetGoal` mixin.
- D-VR-050: extend `SocialExecutionBindingRegistry.Binding` with `Optional<BlockPos> settlementAnchorAtStart` snapshotted at greet **admission** (`FriendlyGreetAdmissionSeamMixin`).

### V1.5-F — Temporary designate-home (test fixture only)

- `/spmscavenger designate-home <target>` — calls `MobVillageMemory.designateHome()` + `onHomeDesignated`.
- Document in task report for post-VR-T1.5 removal.
- **Forbidden:** read-only memory dump commands; probe/driver resurrection.

## Must happen (static)

- Relationship persists across save/reload; evict + rekey sync with village identity.
- Contract test extensions for D-VR-049 (rekey on supersede, merge on remember, evict sync).
- `mustHappen_vrT1aDiagnosticsRemoved` stays green.
- Commute unit tests: admission sequence, multi-leg, mining/cave refusal.
- Social credit only with `settlementAnchorAtStart` at admission + `COMPLETED`.

## Must not happen

- Attachment fields inside `KnownVillage` NBT.
- Orphan `ReturnToSettlementGoal` at priority 3.
- `RaidContainersGoal` suppression (V1.5-E).
- `village-memory` / probe / driver commands.
- Second persisted home representation in relationship map.
- Presence accumulation at 96² raid radius.

## Verification

```powershell
cd "d:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric"
.\gradlew.bat test --tests "*village*"
.\gradlew.bat test --tests "*Settlement*"
.\gradlew.bat test --tests "*SocialExecution*"
.\gradlew.bat clean build
```

Runtime VR-T1.5a–c: `UNVERIFIED` until separate launch approval.

## Report

`.superpowers/sdd/task-46-report.md`
