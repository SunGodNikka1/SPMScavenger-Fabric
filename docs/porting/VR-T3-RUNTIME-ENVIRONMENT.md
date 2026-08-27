# VR-T3 runtime environment pin (pre-launch)

**Status:** the last runtime-confirmed pair remains pinned below for historical VR-T3j evidence. V4-P0
has produced a clean production + validation-sidecar pair, but that replacement is **PREPARED / NOT
RUNTIME-AUTHORIZED**. Any further launch requires exact pair approval.

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
| **SPM Scavenger** (`spmscavenger`) | `spmscavenger-1.11.0.jar` | `BDAA788CAE2126FDE46F858A4076DF69FF0590F151CD3A6B88A32A580A0B2BDC` | Task-59 live-claim stopping-rule repair at `build/libs/spmscavenger-1.11.0.jar`; the exact production frontier plus a matching live claim opens as `source=LIVE_CLAIM` without the superseded geometry veto. No-claim cases retain the passive target fallback. `8C2D...A69F2` is superseded after three runtime reproductions showed a real claim could be rejected by duplicate geometry. |

### Prepared replacement pair — do not install/launch without separate approval

| Mod | File | SHA-256 | Evidence status |
| --- | --- | --- | --- |
| Production Scavenger | `spmscavenger-1.11.0.jar` | `05E77B7F9ACC29B0459FA8F4B5908082546188591A9AD4AFB6D024A4E00A930B` | V4-R0 `clean build` + production package audit **CONFIRMED**; runtime loading **UNVERIFIED** |
| Task-59 validation sidecar | `spmscavenger-1.11.0-validation.jar` | `BB02D551AEED4733434A3756401A9B520091C4056477A7C347CD656CC5F546A0` | compile/test/package audit **CONFIRMED**; sidecar initialization/commands **UNVERIFIED** |

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
| Invoke | `/spmscavenger debug v3 run <preset_id>` (direct function remains manual fallback) |

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
| 2026-08-26 | V4-R0 changed only settlement-memory representation; prepared replacement production hash after 1,635 production + 57 validation tests; validation sidecar unchanged; no Minecraft launch |
| 2026-08-26 | V4-P0 extracted Task-59 into the separately packaged validation sidecar; prepared exact replacement pair after 1,624 production + 57 validation tests and package audits; no Minecraft launch |
| 2026-08-24 | Exact `BDAA788C...A0B2BDC` artifact produced VR-T3j RUNTIME PASS over the full 1000-tick window, including autonomous pig-combat preemption and Gather resumption; remaining rows not implied |
| 2026-08-24 | T3j/D-VR-084 live-claim stopping rule — matching live production ownership now supersedes duplicate geometry while exact policy remains required; 1675-test clean build; SHA `BDAA788C...A0B2BDC`; no Minecraft relaunch |
| 2026-08-24 | T3j live fixture falsification — replaced world-log assumption in T3j/D-VR-084 with controller-seeded iron-pick frontier, exposed reachable iron, and passive `MANDATORY_ROUTE_READY`; repinned after 1669-test clean build; no Minecraft relaunch |
| 2026-08-24 | Gate-0 dynamic HOME repair — +120 is minimum grace; HOME claim deficits wait within original 2400-tick session deadline and time out as `FIXTURE_INCOMPLETE`; repinned after 1663-test clean build; no Minecraft relaunch |
| 2026-08-24 | Post-open spatial repair — core exit is evidence, 192-block contamination envelope, 224-block geometry escape, T3j nonterminal; repinned after 1653-test clean build; no Minecraft relaunch |
| 2026-08-24 | Gate-0 sequencing repair — explicit 120-tick natural HOME bootstrap measured after scenario execution; repinned after 1649-test clean build; no Minecraft relaunch |
| 2026-08-24 | Startup containment repair — replaced invalid raw Brigadier function execution with next-tick `ServerFunctionManager`; repinned after 1645-test clean build; no Minecraft relaunch |
| 2026-08-23 | Automated campaign controller — repinned after 1643-test clean build and package audit; no Minecraft launch |
| 2026-08-23 | Settlement-row precondition — repinned after passive daytime/`SHELTER_HOLD` readiness classification; 1628-test clean build |
| 2026-08-23 | Gate-0 completion — repinned after non-creating settlement/facts witness extension; 1625-test clean build |
| 2026-08-23 | Runtime-validation preparation — repinned to temporary V3 one-shot witness artifact; 1618-test clean build |
| 2026-08-23 | Task-59 resume — repinned Scavenger to the clean W2.4 production artifact after removal of temporary V2-TE witness/fixture tooling |
| 2026-08-22 | Semantic fixture repair — occupied settlement bootstrap; VR-T3b/e/k/l preset fixes; structural regression tests |
| 2026-08-22 | Initial environment pin — Task-59 pre-launch sub-slice |
