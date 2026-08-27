# Task 61 report: V4-R0 settlement representation decomposition

## Status

**DONE — STATIC/PACKAGE ACCEPTED**

`D-VR-089` is implemented without V4 behavior expansion. Home is now one independent optional
settlement in `MobVillageMemory`; `SettlementTier` no longer exists in production representation.

## Files created or changed

- `src/main/java/com/noobk/spmscavenger/village/KnownVillage.java`
- `src/main/java/com/noobk/spmscavenger/village/MobVillageMemory.java`
- `src/main/java/com/noobk/spmscavenger/village/VillageMemorySavedData.java`
- deleted `src/main/java/com/noobk/spmscavenger/village/SettlementTier.java`
- settlement identity/raid/anchor comments updated to remove stale tier assumptions
- `src/test/java/com/noobk/spmscavenger/village/MobVillageMemoryHomeMigrationTest.java`
- retained memory/lifecycle/identity tests adapted to the independent-home model
- `build.gradle` production-JAR assertion for absent `SettlementTier.class`
- RFC, TEST_MATRIX, DECISIONS, README, runtime-environment pin, progress, brief, and this report

## Implementation summary

`KnownVillage` now owns factual observation only. `MobVillageMemory.homeAnchor` is nullable,
canonical, root-persisted, and resolved back to a remembered `KnownVillage` by `home()`. The existing
relationship map, per-mob/per-village bounds, permanent-removal lifecycle, and non-home LRU policy
remain unchanged.

Legacy migration uses per-row `tier` only as load input. HOME migrates; all other and unknown roles
create no new state. New explicit home has precedence, corruption fails safe, and migration marks the
owning SavedData dirty for canonical rewrite.

## RED → GREEN

Working directory:
`D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

RED command:

```text
.\gradlew.bat test --tests "com.noobk.spmscavenger.village.MobVillageMemoryHomeMigrationTest"
```

Result: **FAILED as intended** at `compileTestJava`; seven errors reported missing
`MobVillageMemory.homeAnchor()`.

GREEN focused command:

```text
.\gradlew.bat test --tests "com.noobk.spmscavenger.village.MobVillageMemoryHomeMigrationTest" --tests "com.noobk.spmscavenger.village.MobVillageMemoryTest" --tests "com.noobk.spmscavenger.village.SettlementRelationshipTest" --tests "com.noobk.spmscavenger.village.VillageLifecycleAndIdentityTest" --tests "com.noobk.spmscavenger.village.SettlementReturnPolicyTest"
```

Result: **BUILD SUCCESSFUL**; the new migration class contains **12 tests**, all passing, plus all
selected retained suites.

Final command:

```text
.\gradlew.bat clean build
```

Result: **BUILD SUCCESSFUL in 42 seconds**, 16 tasks executed.

| Suite | Tests | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: |
| Production | 1,635 | 0 | 0 | 0 |
| Validation | 57 | 0 | 0 | 0 |

The pre-existing `EpisodeRetentionTest` deprecation warning remains unrelated.

## Source evidence

- `CODE_CONFIRMED` — independent field/read/designation/rekey/eviction/save/load:
  `MobVillageMemory.java:52–315`.
- `CODE_CONFIRMED` — factual-only village codec: `KnownVillage.java:18–135`.
- `CODE_CONFIRMED` — legacy migration schedules rewrite: `VillageMemorySavedData.java:319–338`.
- `CODE_CONFIRMED` — migration/single-home/rekey/eviction/corruption/save-load/negative structure:
  `MobVillageMemoryHomeMigrationTest.java` (12 passing tests).
- `NOT FOUND` — production `SettlementTier` reference; `TRADING_POST` writer; `AVOID` writer.
- `UNVERIFIED` — loading an actual historical world in Minecraft; runtime launch was forbidden and
  no runtime behavior claim is made.

## Package audit

| Assertion | Result |
| --- | --- |
| Production `SettlementTier.class` | 0 |
| Production validation namespace | 0 |
| Production legacy `debug/V3*` | 0 |
| Production Task-59 scenario resources | 0 |
| Validation classes outside validation namespace | 0 |
| Validation/production duplicate classes | 0 |

| Artifact | Size | SHA-256 |
| --- | ---: | --- |
| `build/libs/spmscavenger-1.11.0.jar` | 1,159,288 bytes | `05E77B7F9ACC29B0459FA8F4B5908082546188591A9AD4AFB6D024A4E00A930B` |
| `build/libs/spmscavenger-1.11.0-validation.jar` | 135,814 bytes | `BB02D551AEED4733434A3756401A9B520091C4056477A7C347CD656CC5F546A0` |

## Self-review against brief

- Exactly one independent optional home: **PASS**.
- Deterministic legacy HOME migration and duplicate handling: **PASS**.
- Explicit-home corruption/fail-safe behavior: **PASS**.
- Anchor merge/supersession rekey: **PASS**.
- Home-protected LRU and relationship eviction preservation: **PASS**.
- Save/load and dirty canonical rewrite: **PASS**.
- No economic/safety replacement state: **PASS**.
- Every production tier reader/writer removed/adapted: **PASS**.
- No KnownVillager/ranking/director/General Debug/first-home behavior: **PASS**.
- No Minecraft launch, commit, or push: **PASS**.

## Alternatives and strongest objection

Keeping a reduced non-home enum was the viable smaller-diff alternative. It was rejected because no
production writer/consumer exists for its economic or safety values, so it would preserve dead state
and the architectural coupling V4-R0 exists to remove.

Strongest remaining objection: a synthetic NBT test does not prove a specific real-world file loads
through Fabric/Minecraft storage end to end. That remains explicitly unverified; it would require a
separately approved backup-world migration run, not more production logic.

## MAIBS-1 semantic-drift review

```text
PLANNED: home becomes independent; no AI behavior changes
IMPLEMENTED: independent canonical home + legacy migration
PREDICTED RUNTIME: existing home consumers select the same settlement; no new Goal or producer
```

No priorities, flags, pathing, interruption, authority, inventory, or world interaction changed.
The only intended observable migration difference is that obsolete/unknown tier text no longer
deletes otherwise valid factual village memory.

## Handoff

V4-R0 is complete to its locked proof class. V4-A is next only with separate authorization. The
remaining Task-59 runtime campaign remains open and uses the unchanged validation sidecar paired with
the new production hash after exact approval.
