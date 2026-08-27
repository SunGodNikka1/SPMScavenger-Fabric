# Task 61 brief: V4-R0 settlement representation decomposition

## Status and target

**Status:** AUTHORIZED by User, 2026-08-26.  
**Target:** `D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`  
**Pinned host reference:** `D:\Apps\Minecraft Port\Projects\references\artifacts\playermob-fabric-0.89.0+1.21.1.jar`  
**Canonical decision:** `plans/RFC-VILLAGE-RAID-AUTONOMOUS-PROGRESSION.md`, `D-VR-089` / V4-R0.  
**Report:** `.superpowers/sdd/task-61-report.md`

This is an addon SavedData representation migration. The host artifact remains the pinned runtime
baseline but does not own Scavenger's settlement-memory schema.

## Source evidence (`CODE_CONFIRMED`)

- `src/main/java/com/noobk/spmscavenger/village/KnownVillage.java`: persists a mutually exclusive
  `SettlementTier`, exposes `tier()`/`isHome()`, and rejects unknown tier names.
- `src/main/java/com/noobk/spmscavenger/village/MobVillageMemory.java`: derives `home()` by scanning
  village tiers, changes tiers in `designateHome`, exempts tier-home from eviction, and demotes
  duplicate tier-homes during load.
- `src/main/java/com/noobk/spmscavenger/village/VillageMemorySavedData.java`: owns per-dimension,
  per-mob persistence, production home designation, relationship retention, and permanent-removal
  deletion.
- Production writer probes: `SettlementTier.TRADING_POST` — **NOT FOUND**;
  `SettlementTier.AVOID` — **NOT FOUND**; any settlement-tier mutation outside
  `MobVillageMemory.designateHome/load` — **NOT FOUND**.

## Binding implementation contract

1. Delete `SettlementTier` from current production representation. `KnownVillage` becomes factual:
   anchor, first/last observation, and observation quality only.
2. `MobVillageMemory` owns exactly one nullable/canonical `homeAnchor`, exposed as optional home
   settlement and optional anchor. `designateHome` succeeds only for an already remembered village.
3. New saves write `homeAnchor` once at memory root and write no per-village `tier`.
4. Legacy migration:
   - valid legacy `HOME_VILLAGE` row becomes `homeAnchor`;
   - multiple valid legacy home rows choose the first valid village-list row deterministically;
   - `PASSING_THROUGH`, `TRADING_POST`, `AVOID`, and unknown tier text migrate to no role;
   - a present explicit `homeAnchor` has precedence over legacy tier rows;
   - malformed/unremembered explicit home fails safe to no home and does not create a village;
   - legacy schema detection marks top-level SavedData dirty so a later save rewrites the schema.
5. When observation quality supersedes an anchor, both relationship identity and `homeAnchor` rekey
   to the replacement anchor. Identity merge without replacement preserves the canonical home.
6. Home remains eviction-exempt; non-home LRU and relationship-row eviction remain unchanged.
7. Do not persist economic usefulness or safety state. No `TRADING_POST` replacement, `AVOID`
   replacement, KnownVillager, ranking, director, General Debug expansion, or first-home producer.

## Alternatives and decision

| Option | Benefit | Failure mode | Disposition |
| --- | --- | --- | --- |
| Keep reduced `SettlementTier` without HOME | Smaller diff | Preserves dead economic/safety roles and invites later writers to reuse the wrong mutually exclusive model | Rejected |
| Remove tier; persist only independent home | Matches evidenced facts and D-VR-089; clean later capability seam | Requires deterministic migration and full reader/writer audit | Selected |

Switch back only if the implementation audit finds an actual persisted production producer/consumer
for `TRADING_POST` or `AVOID`; the three negative probes currently find none.

## Behavioral Prediction — MAIBS-1

This slice changes durable representation, not Goals, priorities, navigation, inventory, or world
mutation. A mob with a valid existing home should continue returning, composting, and selecting
population-food targets against the same remembered anchor before and after save/load. A mob with no
home should gain none merely by loading legacy passing/trading/avoid rows.

| Time | Predicted observable behavior | Invalidation/reacquisition |
| --- | --- | --- |
| T0/load | Legacy HOME resolves to the same remembered settlement; other legacy roles disappear | Invalid/orphan home resolves to none; rediscovery alone does not designate home |
| T+10 | Existing return/work consumers read `memory.home()` unchanged | No new producer runs; only explicit existing designation can change home |
| T+200 | Anchor supersession moves home and relationship to the stronger observed anchor | Worse observation keeps both identities unchanged |
| T+1200/save-reload | Exactly one home persists at root; home stays eviction-exempt | Permanent owner deletion still removes the entire memory through existing SavedData lifecycle |

Goal interaction: none changed. Existing return/compost/population consumers continue to receive the
same `KnownVillage`; all movement, interruption, and executor ownership stays in existing systems.

Predicted weird behaviors:

1. Corrupt legacy save with two HOME rows selects the first list row —
   `ACCEPTABLE_STEPPING_STONE`, deterministic and single-valued.
2. Mixed save with malformed explicit home plus valid legacy HOME yields no home —
   `ACCEPTABLE_STEPPING_STONE`, fail-safe precedence prevents stale fallback from overriding the new
   schema.
3. Missed home rekey would make `home()` disappear after a stronger observation —
   `ARCHITECTURE_DEFECT`; deterministic anchor-supersession test must falsify it before build.

**Gate:** `PASS — BEHAVIORALLY_PLAUSIBLE` for implementation. Runtime movement parity is not a
closure requirement for this representation-only slice; build/static evidence must not be labeled
runtime proof.

## TDD and verification

Record RED before GREEN for:

- legacy HOME migration and new-schema save shape;
- deterministic multiple-home corruption handling;
- explicit-home corruption/fail-safe behavior;
- exactly one home designation;
- anchor supersession/rekey;
- home-protected and non-home LRU eviction with relationships;
- save/load round trip and dirty legacy SavedData migration;
- structural absence of `SettlementTier`, per-village `tier`, KnownVillager, ranking, director, and
  first-home producers.

Commands:

```text
.\gradlew.bat test --tests "com.noobk.spmscavenger.village.*Home*" --tests "com.noobk.spmscavenger.village.MobVillageMemoryTest" --tests "com.noobk.spmscavenger.village.SettlementRelationshipTest"
.\gradlew.bat clean build
```

Package audit must preserve the clean production/validation split and contain no
`SettlementTier.class`.

## Binding constraints

- No Minecraft launch.
- No commit or push.
- Preserve unrelated user changes.
- No V4 product behavior beyond D-VR-089 representation migration.
- Update RFC, `docs/porting/TEST_MATRIX.md`, progress, and `.superpowers/sdd/task-61-report.md`.

## Acceptance

**Must happen:** an existing valid legacy home becomes exactly one independent home, survives anchor
supersession, eviction pressure, and save/load, while relationship rows retain their prior semantics.

**Must not happen:** loading creates home from non-home/unknown roles, economic/safety roles are
persisted, orphan/corrupt home creates a village, or any new AI behavior/authority producer appears.
