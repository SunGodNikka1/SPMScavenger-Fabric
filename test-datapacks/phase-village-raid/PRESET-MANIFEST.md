# `spm_vr` preset manifest — phase village / raid test kit

**Namespace:** `spm_vr`  
**Datapack root:** `test-datapacks/phase-village-raid/`  
**Status:** **executable fixtures implemented** — structural validation via `SpmVrDatapackStructureTest`; **Minecraft campaign NOT AUTHORIZED**.

**Matrix:** `docs/porting/VR-T3-RUNTIME-MATRIX.md`  
**Environment:** `docs/porting/VR-T3-RUNTIME-ENVIRONMENT.md`  
**Brief:** `.superpowers/sdd/task-59-brief.md`

---

## Preset inventory

| # | Preset ID | VR-T3 / witness | Function |
| --- | --- | --- | --- |
| 1 | `crop_managed_single` | VR-T3a | `spm_vr:scenario/crop_managed_single` |
| 2 | `crop_interrupt_combat` | VR-T3b (+ SPM 0.89 caution) | `spm_vr:scenario/crop_interrupt_combat` |
| 3 | `crop_replant_failure` | VR-T3c | `spm_vr:scenario/crop_replant_failure` |
| 4 | `compost_seed_surplus` | VR-T3d | `spm_vr:scenario/compost_seed_surplus` |
| 5 | `population_food_deficit` | VR-T3e | `spm_vr:scenario/population_food_deficit` |
| 6 | `storage_public_deny` | VR-T3g | `spm_vr:scenario/storage_public_deny` |
| 7 | `storage_unknown_deny` | VR-T3h | `spm_vr:scenario/storage_unknown_deny` |
| 8 | `storage_granted_permit` | VR-T3i | `spm_vr:scenario/storage_granted_permit` |
| 9 | `mandatory_blocks_village_work` | VR-T3j (+ SPM 0.89 caution) | `spm_vr:scenario/mandatory_blocks_village_work` |
| 10 | `crop_multi_mob` | VR-T3k | `spm_vr:scenario/crop_multi_mob` |
| 11 | `crop_hungry_veto` | VR-T3l | `spm_vr:scenario/crop_hungry_veto` |
| 12 | `crop_multi_cycle` | VR-T3m | `spm_vr:scenario/crop_multi_cycle` |
| 13 | `mandatory_ownership_witness` | D-VR-084 witness | `spm_vr:scenario/mandatory_ownership_witness` |

**Twelve applicable VR-T3 letter rows** (`a–e`, `g–m`) **+ D-VR-084 witness = thirteen preset IDs.** SPM 0.89 caution reuses presets 2 and 9.

---

## Operator usage

Full runbook: `README.md`. Evidence worksheet: `docs/porting/VR-T3-RUNTIME-EVIDENCE.md`.

1. Copy or symlink `test-datapacks/phase-village-raid/` into the instance `datapacks/` folder.
2. Verify JAR hashes in `VR-T3-RUNTIME-ENVIRONMENT.md`.
3. `/reload` (or restart), then `/function spm_vr:help`.
4. Stand at the fixture anchor (flat overworld recommended); run `/function spm_vr:scenario/<preset_id>`.
5. Use `/spmscavenger debug v3 inspect @e[tag=spm_vr.subject,limit=1]` at meaningful transitions.
6. Observe for the **minimum window** in the matrix row before recording PASS/FAIL/INCOMPLETE.
7. `/function spm_vr:cleanup` removes tagged entities/schedules; world blocks are intentionally preserved.

Shared library: `spm_vr:_lib/reset`, `setup_village_stub`, `spawn_ally`.

---

## Shared fixture assumptions

| Field | Value |
| --- | --- |
| Dimension | overworld |
| Mob profile | `VILLAGE_ALLY` via `/spmscavenger village profile set <mob> village_ally` |
| Settlement | bell + **3 HOME beds**; **≥2 adult villagers** (vanilla AI); night + proximity nudge only |
| Bootstrap | `setup_village_stub` + scheduled `claim_village_beds` (TP only — **no** HOME/sleep NBT injection) |
| Runtime gate 0 | Settlement bootstrap preflight in `VR-T3-RUNTIME-MATRIX.md` — halt on `FIXTURE_FAILURE` |
| `mobGriefing` | **true** (crop/compost rows require it) |
| Host | `playermob` **0.89.0** (hash pinned) |

---

## Preset catalog

### `spm_vr:crop_managed_single` — VR-T3a

| Field | Value |
| --- | --- |
| **VR-T3** | VR-T3a |
| **Purpose** | Single mature managed crop; seed in backpack; ally harvest→replant |
| **World** | One farmland plot inside settlement bounds; wheat age 7 |
| **Mob inventory** | wheat seeds ≥ replant reserve + 1 |
| **Must observe** | Same position replanted age 0 after one episode |
| **Falsifier** | Bare farmland after successful pathing |
| **Min window** | terminal COMMIT + 200 ticks (matrix) |

### `spm_vr:crop_interrupt_combat` — VR-T3b + SPM 0.89 caution

| Field | Value |
| --- | --- |
| **VR-T3** | VR-T3b; target-edge witness |
| **Purpose** | Interrupt crop episode **after PATHING begins**, before COMMIT |
| **World** | Chains `crop_managed_single` |
| **Trigger** | `_lib/stage_interrupt_zombie` scheduled **120t** after preset load |
| **Must observe** | Crop unchanged; mob later re-resolves |
| **Falsifier** | Crop broken; stale path resumes without revalidation |
| **Min window** | 600 ticks post-interrupt (matrix) |

### `spm_vr:crop_replant_failure` — VR-T3c

| Field | Value |
| --- | --- |
| **VR-T3** | VR-T3c |
| **Purpose** | Pre-COMMIT invalidation — **atomic abort** (task-55); no repair phase |
| **World** | Managed crop; mob spawned **without** replant seeds |
| **Must observe** | **Zero mutation** — preflight ABORT before harvest; optional `INVARIANT_FAILURE` only if a real transaction invariant is induced |
| **Falsifier** | Bare farmland; discretionary explore gap; any post-abort “repair” or cleanup ownership |
| **Min window** | terminal ABORT/INVARIANT_FAILURE + 200 ticks unchanged (matrix) |

### `spm_vr:compost_seed_surplus` — VR-T3d

| Field | Value |
| --- | --- |
| **VR-T3** | VR-T3d |
| **Purpose** | Loaded composter level &lt; 7; wheat seed surplus |
| **World** | Composter within range |
| **Mob inventory** | seed surplus after replant reserve |
| **Must observe** | Exactly one seed consumed per episode; terminate on unchanged level |
| **Falsifier** | Double debit; bone-meal registered as demand |
| **Min window** | one terminal activation + 400 ticks (matrix) |

### `spm_vr:population_food_deficit` — VR-T3e

| Field | Value |
| --- | --- |
| **VR-T3** | VR-T3e |
| **Purpose** | PlayerMob → **adult villager** food delivery (V3-E) |
| **World** | Occupied settlement (≥2 adult villagers, spare HOME from bootstrap) |
| **Recipient** | `spm_vr.villager2` with cleared inventory + `spm_vr.food_recipient` tag |
| **Mob inventory** | disposable bread on subject ally |
| **Must observe** | One delivery episode to villager; no breeding command |
| **Falsifier** | PlayerMob-to-PlayerMob gifting; reserve violation; endless gifting |
| **Min window** | 1200 ticks — one handoff only (matrix) |

### `spm_vr:storage_public_deny` — VR-T3g

| Field | Value |
| --- | --- |
| **VR-T3** | VR-T3g |
| **Purpose** | Ally + village public chest — host loot denied |
| **World** | `VILLAGE_PUBLIC` classified chest with items inside settlement |
| **Must observe** | `RaidContainersGoal` does not open/continue |
| **Falsifier** | Chest emptied without explicit grant |
| **Min window** | 800 ticks (matrix) |

### `spm_vr:storage_unknown_deny` — VR-T3h

| Field | Value |
| --- | --- |
| **VR-T3** | VR-T3h |
| **Purpose** | Unclassified container — fail closed |
| **World** | Chest **outside** settlement stub |
| **Must observe** | No open; no continuation |
| **Falsifier** | Loot treated as public |
| **Min window** | 800 ticks (matrix) |

### `spm_vr:storage_granted_permit` — VR-T3i

| Field | Value |
| --- | --- |
| **VR-T3** | VR-T3i |
| **Purpose** | Explicit mob-owned grant permits access |
| **World** | Chest in settlement; **`/spmscavenger village storage own <mob> <pos>`** on exact mob+container |
| **Must observe** | Permitted access proceeds |
| **Falsifier** | Denied despite grant |
| **Min window** | 800 ticks (matrix) |

### `spm_vr:mandatory_blocks_village_work` — VR-T3j

| Field | Value |
| --- | --- |
| **VR-T3** | VR-T3j |
| **Purpose** | Live mandatory progression blocks village work |
| **World** | Crop + gather logs (simultaneous demand) |
| **Must observe** | Village work waits; mandatory completes first |
| **Falsifier** | Compost/harvest wins over pending mandatory |
| **Min window** | 1000 ticks (matrix) |

### `spm_vr:crop_multi_mob` — VR-T3k

| Field | Value |
| --- | --- |
| **VR-T3** | VR-T3k |
| **Purpose** | Two ally mobs; **one** mature crop |
| **World** | Single wheat age 7; two allies with seeds |
| **Must observe** | First commits; second abandons/reacquires |
| **Falsifier** | Double harvest; split-crop bypass |
| **Min window** | first COMMIT + second revalidation + 200 ticks (matrix) |

### `spm_vr:crop_hungry_veto` — VR-T3l

| Field | Value |
| --- | --- |
| **VR-T3** | VR-T3l |
| **Purpose** | `wantsFood()` + `VillageWorkAdmission` denied; host veto in managed domain |
| **World** | Mature **carrots** (host food crop); empty ally backpack; oak logs for mandatory gather claim |
| **Must observe** | Host `HarvestCropsGoal` vetoed; field stays planted; VILLAGE_ALLY preserved |
| **Falsifier** | Hunger effect substitute; host strips managed carrots |
| **Min window** | 800 ticks (matrix) |

### `spm_vr:crop_multi_cycle` — VR-T3m

| Field | Value |
| --- | --- |
| **VR-T3** | VR-T3m |
| **Purpose** | Many harvest cycles without floor-pickup replant supply |
| **World** | Three mature wheat; limited seed budget |
| **Must observe** | Replant stock from episode banked drops |
| **Falsifier** | Field harvested barren when reserve exhausted |
| **Min window** | ≥2 complete cycles + 400 ticks (matrix) |

### `spm_vr:mandatory_ownership_witness` — D-VR-084

| Field | Value |
| --- | --- |
| **VR-T3** | (witness row — not a VR-T3 letter id) |
| **Purpose** | Pending mandatory claim visible to `VillageWorkAdmission` |
| **World** | Oak logs for **real Gather publisher** claim; crop nearby; **no authority injection** |
| **Must observe** | Village work refused while claim live |
| **Falsifier** | Village work starts despite pending claim |
| **Min window** | 1000 ticks (matrix) |

---

## Explicitly out of scope

| Item | Reason |
| --- | --- |
| **VR-T3f** | Broad V3-D2 deferred — non-applicable to V3-G closure |
| Trade / TE fixtures | V2 closed; contaminates V3 baseline |
| Raid defense | V5 — not V3-G |

---

## Structural precedent

Follows `test-datapacks/shelter-commitment/` (`spm_shelter`) layout: one function per scenario id,
shared `_lib`, load tag, no production code coupling. Validated by `SpmVrDatapackStructureTest`.
