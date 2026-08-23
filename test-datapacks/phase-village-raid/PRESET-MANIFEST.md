# `spm_vr` preset manifest — phase village / raid test kit

**Namespace:** `spm_vr`  
**Datapack root (planned):** `test-datapacks/phase-village-raid/`  
**Status:** manifest only — **function bodies not required for task-59 prep**; implement before runtime launch.

**Matrix:** `docs/porting/VR-T3-RUNTIME-MATRIX.md`  
**Brief:** `.superpowers/sdd/task-59-brief.md`

---

## Shared fixture assumptions

| Field | Value |
| --- | --- |
| Dimension | overworld |
| Mob profile | `VILLAGE_ALLY` via operator command or spawn config |
| Settlement | one anchored village within perception bounds (64² presence) |
| `mobGriefing` | **true** (crop/compost rows require it) |
| Host | `playermob` **0.89.0** |

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

### `spm_vr:crop_interrupt_combat` — VR-T3b + SPM 0.89 caution

| Field | Value |
| --- | --- |
| **VR-T3** | VR-T3b; target-edge witness |
| **Purpose** | Interrupt crop episode before COMMIT (combat or command) |
| **World** | Same as `crop_managed_single` |
| **Trigger** | Spawn hostile or `/damage` mob during PATHING |
| **Must observe** | Crop unchanged; mob later re-resolves |
| **Falsifier** | Crop broken; stale path resumes without revalidation |

### `spm_vr:crop_replant_failure` — VR-T3c

| Field | Value |
| --- | --- |
| **VR-T3** | VR-T3c |
| **Purpose** | Replant write failure or seed loss before commit |
| **World** | Managed crop; optionally remove seeds mid-episode via command |
| **Must observe** | No harvest or bounded repair — no discretionary explore gap |
| **Falsifier** | Bare farmland + mob wanders |

### `spm_vr:compost_seed_surplus` — VR-T3d

| Field | Value |
| --- | --- |
| **VR-T3** | VR-T3d |
| **Purpose** | Loaded composter level &lt; 7; wheat/beetroot seed surplus |
| **World** | Composter within `ComposterWorkFacts` range; farmer POI optional |
| **Mob inventory** | seed surplus after replant reserve = 1 |
| **Must observe** | Exactly one seed consumed per episode; terminate on unchanged level |
| **Falsifier** | Double debit; bone-meal registered as demand |

### `spm_vr:population_food_deficit` — VR-T3e

| Field | Value |
| --- | --- |
| **VR-T3** | VR-T3e |
| **Purpose** | Population support candidacy + eligible villager |
| **World** | ≥2 adult villagers; free HOME capacity; villager wants food |
| **Mob inventory** | disposable breeding food (not survival reserve) |
| **Must observe** | One delivery episode; no breeding command |
| **Falsifier** | Reserve violation; endless gifting |

### `spm_vr:storage_public_deny` — VR-T3g

| Field | Value |
| --- | --- |
| **VR-T3** | VR-T3g |
| **Purpose** | Ally + village public chest — host loot denied |
| **World** | `VILLAGE_PUBLIC` classified chest with items |
| **Must observe** | `RaidContainersGoal` does not open/continue |
| **Falsifier** | Chest emptied without explicit grant |

### `spm_vr:storage_unknown_deny` — VR-T3h

| Field | Value |
| --- | --- |
| **VR-T3** | VR-T3h |
| **Purpose** | Unclassified container — fail closed |
| **World** | Chest with no ownership row |
| **Must observe** | No open; no continuation |
| **Falsifier** | Loot treated as public |

### `spm_vr:storage_granted_permit` — VR-T3i

| Field | Value |
| --- | --- |
| **VR-T3** | VR-T3i |
| **Purpose** | Explicit mob-owned or shared grant permits access |
| **World** | Chest with positive `StoragePermission` grant |
| **Must observe** | Permitted access proceeds |
| **Falsifier** | Denied despite grant |

### `spm_vr:mandatory_blocks_village_work` — VR-T3j

| Field | Value |
| --- | --- |
| **VR-T3** | VR-T3j |
| **Purpose** | Live mandatory progression blocks village work |
| **World** | Crop or compost available; simultaneous gather/smelt demand |
| **Must observe** | Village work waits; mandatory completes first |
| **Falsifier** | Compost/harvest wins over pending mandatory |

### `spm_vr:crop_multi_mob` — VR-T3k

| Field | Value |
| --- | --- |
| **VR-T3** | VR-T3k |
| **Purpose** | Two ally mobs; one crop |
| **World** | `crop_managed_single` × 2 mobs |
| **Must observe** | First commits; second abandons/reacquires |
| **Falsifier** | Double harvest |

### `spm_vr:crop_hungry_veto` — VR-T3l

| Field | Value |
| --- | --- |
| **VR-T3** | VR-T3l |
| **Purpose** | Hungry mob; V3 admission refused; managed crop protected |
| **World** | Managed crop; mob `wantsFood()`; block village harvest admission |
| **Must observe** | Host `HarvestCropsGoal` vetoed at position; field stays planted |
| **Falsifier** | Host strips crop in managed domain |

### `spm_vr:crop_multi_cycle` — VR-T3m

| Field | Value |
| --- | --- |
| **VR-T3** | VR-T3m |
| **Purpose** | Many harvest cycles without floor-pickup replant supply |
| **World** | Multi-plot or repeatable single plot |
| **Must observe** | Replant stock from episode banked drops |
| **Falsifier** | Field harvested barren when reserve exhausted |

### `spm_vr:mandatory_ownership_witness` — D-VR-084

| Field | Value |
| --- | --- |
| **VR-T3** | (witness row — not a VR-T3 letter id) |
| **Purpose** | Pending mandatory claim visible to `VillageWorkAdmission` |
| **World** | Active gather route with published claim |
| **Must observe** | Village work refused while claim live |
| **Falsifier** | Village work starts despite pending claim |

---

## Explicitly out of scope

| Item | Reason |
| --- | --- |
| **VR-T3f** | Broad V3-D2 deferred — non-applicable to V3-G closure |
| Trade / TE fixtures | V2 closed; contaminates V3 baseline |
| Raid defense | V5 — not V3-G |

---

## Implementation note (pre-runtime)

When implementing datapack bodies, follow `test-datapacks/shelter-commitment/` (`spm_shelter`) as the
structural precedent: one function per scenario id, documented load order, no production code coupling.
