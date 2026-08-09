# Phase 1 tool-tier runtime test datapack

**Canonical format:** `docs/agent-workflows/RUNTIME_TEST_DATAPACK.md`  
**RFC:** `plans/RFC-TOOL-TIER-UPGRADES.md`  
**Namespace:** `spm_phase1`

Copy this folder into your world's `datapacks/` directory, then `/reload`.

## Quick start

```mcfunction
/function spm_phase1:quickstart
```

Stand on flat ground first — that position becomes the arena anchor.

## Prerequisites (manual)

`config/spmscavenger.json`:

| Key | Test value |
| --- | --- |
| `enabled` | `true` |
| `craftTools` | `true` |
| `gatherResources` | `true` |
| `protectPlayerBuilds` | `true` |
| `maxPickTier` / `maxAxeTier` | `STONE` (`IRON`/`DIAMOND` clamp on load) |
| `cobbleStockTarget` | `6` |
| `torchStockTarget` | `8` |

`/gamerule mobGriefing true` (set by `setup`).

## Runtime matrix

| ID | Commands | Must happen | Must not |
| --- | --- | --- | --- |
| TT-0R | `arena/build` + `spawn/full_pack` | Atomic stone-pick craft | Ingredient loss |
| TT-1 | `spawn/need_cobble` | Stone → cobble → stone pick | Mine stone wall |
| TT-2 | `spawn/equipped_done` | No cobble hoarding | Strip mine |
| TT-3 | `spawn/looted_stone` | Skip redundant craft | Re-craft wood |
| TT-4 | `spawn/torch_stocked` | Gather stops | Cobble blocks torches |
| TT-5 | `need_cobble` + `tools/break_mainhand` | Re-craft pick | Idle with stock |
| TT-6 | manual powder snow | Escape uses durability | Stranded toolless |

## Arena offsets (from anchor)

| Fixture | Offset |
| --- | --- |
| Crafting table | +1 ~ +1 |
| Sample tree | +3 ~ +8 |
| Exposed stone | +5 ~ +5 |
| Coal ore | +8 ~ +2 |
| Stone wall (negative) | +5~+7 ~ +10 |

## Remove when done

Delete from `datapacks/` and `/reload`.
