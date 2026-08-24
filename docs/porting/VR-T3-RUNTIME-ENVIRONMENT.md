# VR-T3 runtime environment pin (pre-launch)

**Status:** pinned to the temporary Task-59 V3 witness artifact — **Minecraft campaign NOT AUTHORIZED**.

**Matrix:** `docs/porting/VR-T3-RUNTIME-MATRIX.md`  
**Datapack:** `test-datapacks/phase-village-raid/` (`spm_vr`)

---

## Platform

| Component | Pinned value | Evidence |
| --- | --- | --- |
| Minecraft | **1.21.1** | `gradle.properties` → `minecraft_version=1.21.1` |
| Fabric Loader | **0.16.14** | `gradle.properties` → `loader_version=0.16.14` |
| Fabric API | **0.116.4+1.21.1** | `gradle.properties` → `fabric_api_version` |
| Java (build) | toolchain from Loom project | `build.gradle` |

---

## Mod artifacts (runtime instance must match)

| Mod | File | SHA-256 | Evidence |
| --- | --- | --- | --- |
| **Social Player Mobs** (`playermob`) | `playermob-fabric-0.89.0+1.21.1.jar` | `C8DC0E89C3FD632B6DCC7F8E46D3AE4955DD5504CBA53F72B62314850A64E612` | Downloaded from GitHub release `bh679/playermob-mc` tag `v0.89.0` (485 500 bytes); cached at `Projects/references/artifacts/playermob-fabric-0.89.0+1.21.1.jar` |
| **SPM Scavenger** (`spmscavenger`) | `spmscavenger-1.11.0.jar` | `1185EBCF362CB5409FC0D61DC4A49EE00016385FAF402C0244C8DC9DF7CD22C6` | Task-59 instrumented artifact at `build/libs/spmscavenger-1.11.0.jar`; temporary one-shot V3 diagnostic includes Gate-0 settlement/population readout |

**SPM source tag (audit):** `v0.89.0` on `https://github.com/bh679/playermob-mc` (`mod_version=0.89.0` in tag `gradle.properties`).

**Operator check before launch:**

```powershell
Get-FileHash "path\to\playermob-fabric-0.89.0+1.21.1.jar" -Algorithm SHA256
Get-FileHash "path\to\spmscavenger-1.11.0.jar" -Algorithm SHA256
```

Hashes must match this document before the batched VR-T3 campaign.

---

## Datapack

| Field | Value |
| --- | --- |
| Root | `test-datapacks/phase-village-raid/` |
| Namespace | `spm_vr` |
| `pack_format` | **48** (1.21.1) |
| Preset count | **13** — twelve applicable VR-T3 letter rows (`a–e`, `g–m`) + **D-VR-084 witness** |
| Load hook | `data/minecraft/tags/function/load.json` → `spm_vr:load` |
| Invoke | `/function spm_vr:scenario/<preset_id>` |

Structural validation (no Minecraft): `SpmVrDatapackStructureTest`.

---

## Contamination exclusions

| Mod | Status |
| --- | --- |
| Trade Everything | **excluded** |
| Optional compat mods | **excluded** unless a row is explicitly marked optional |

---

## Revision history

| Date | Change |
| --- | --- |
| 2026-08-23 | Gate-0 completion — repinned after non-creating settlement/facts witness extension; 1625-test clean build |
| 2026-08-23 | Runtime-validation preparation — repinned to temporary V3 one-shot witness artifact; 1618-test clean build |
| 2026-08-23 | Task-59 resume — repinned Scavenger to the clean W2.4 production artifact after removal of temporary V2-TE witness/fixture tooling |
| 2026-08-22 | Semantic fixture repair — occupied settlement bootstrap; VR-T3b/e/k/l preset fixes; structural regression tests |
| 2026-08-22 | Initial environment pin — Task-59 pre-launch sub-slice |
