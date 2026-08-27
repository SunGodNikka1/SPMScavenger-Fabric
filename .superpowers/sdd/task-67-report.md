# Task 67 report: V4-F — first-home promotion

## Outcome

**Status:** DONE / STATIC+PACKAGE ACCEPTED.  
**Runtime:** UNVERIFIED; no Minecraft launch was authorized or performed.  
**Commit:** none.

V4-F adds one event-bound first-home transition and no autonomous re-home policy. The transition is
evaluated only after `SeekShelterGoal.lieDown()` calls the real `mob.startSleeping(bedPos)` and
observes `mob.isSleeping()` true.

## Implemented boundary (`CODE_CONFIRMED`)

- `FirstHomePromotion.afterSuccessfulSleep(...)` uses `VillageMemorySavedData.peekInDimension`
  and `peek(uuid)` only. Sleeping never creates settlement memory.
- The exact bed position must fall within `SettlementBoundsPolicy` for exactly one canonical
  remembered village. Zero or multiple matches fail closed; there is no nearest fallback.
- The matched settlement relationship must have familiarity >=
  `SettlementTuning.HIGH_BAND_MIN` (600), and `MobVillageMemory.homeAnchor()` must be empty.
- The sole write delegates once to the existing
  `VillageMemorySavedData.designateHome(level, uuid, canonicalAnchor, now)` path. V4-F does not
  duplicate home mutation, `onHomeDesignated`, or SavedData dirtying.
- No sleep latch, pending-home record, counter, timer, config, periodic poll, utility/Opinion/trader
  input, navigation behavior, or automatic re-home was introduced.

## Deterministic acceptance evidence

The focused suite proves successful sleep at 600 writes the canonical first home once; 599, failed
sleep, absent memory, an unrelated settlement, and ambiguous association do not. It also proves an
existing home cannot be replaced by a higher-familiarity second settlement, an old sleep is not
replayed after familiarity rises, a later real sleep may promote, and the resulting R0 home
round-trips and rekeys on anchor supersession.

Structural tests prove `startSleeping` precedes the `isSleeping` guard and promotion call; V4-F uses
non-creating reads and the canonical writer; no duplicate relationship/dirty effects or forbidden
latch fields appear. Three earlier V4 boundary tests that deliberately required the future V4-F
producer to be absent were synchronized to require exactly one dedicated producer while retaining
their bans on a second Goal/navigation/market authority.

## Alternatives and strongest risk

Rejected: use the 48-block village identity merge radius, because identity equivalence is not the
same question as bed association. Rejected: choose the nearest remembered anchor, because it turns
ambiguous evidence into invented certainty. Selected: exactly one remembered settlement inside the
existing 64-block settlement activity/presence envelope.

The strongest risk is geometric: an ordinary bed near a poorly located large-village anchor may
fall outside 64 blocks, or two close remembered settlements may overlap and deliberately produce no
home. That is fail-safe, but its real frequency is **UNVERIFIED**. Evidence of systematic false
negatives would reopen association geometry, not authorize nearest-wins automatically.

## Verification (`CONFIRMED`)

```text
.\gradlew.bat test --tests com.noobk.spmscavenger.village.FirstHomePromotionTest \
  --tests com.noobk.spmscavenger.village.MobVillageMemoryHomeMigrationTest \
  --tests com.noobk.spmscavenger.goal.ShelterClaimLifecycleTest
.\gradlew.bat clean build
```

- Focused suite: PASS.
- Production tests: **1,719**, failures/errors: **0**.
- Validation tests: **57**, failures/errors: **0**.
- Production validation-namespace classes: **0**.
- Production Task-59 temporary classes: **0**.
- Production upstream Trade Everything classes: **0**.
- Production/validation duplicate classes: **0**.

Artifacts:

- `build/libs/spmscavenger-1.11.0.jar`  
  SHA-256 `918CA885EBD5FA985FBE234DE11D05E983DFAF882A4092921BA15F46B59E089B`
- `build/libs/spmscavenger-1.11.0-validation.jar`  
  SHA-256 `5CAF12091A17A96B7D09D502F7FA2467A6C5E193E4F07510F1F0EA5D23DD0EFF`

The first full build exposed three stale pre-V4-F negative assertions. After synchronizing those
tests, one compile attempt exposed missing static `assertEquals` imports; those imports were added.
The final clean build is the evidence above.

## MAIBS disposition

The implementation matches the predicted one-event feedback loop and changes no Goal priority,
flags, path, claim, shelter, wake, or scheduler behavior. A qualifying successful sleep may now
write the first canonical home in the same tick; later sleeps are no-ops once home exists. This is
**BEHAVIORALLY_PLAUSIBLE**, not runtime-confirmed. V4-G remains the sole representative runtime
frontier and requires separate preparation and launch approval.
