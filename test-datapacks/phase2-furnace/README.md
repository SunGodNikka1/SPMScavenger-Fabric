# Phase 2 furnace runtime test datapack

**Canonical format:** `docs/agent-workflows/RUNTIME_TEST_DATAPACK.md`  
**RFC:** `plans/RFC-FURNACE-SMELTING.md`  
**Namespace:** `spm_phase2`

Copy this folder into your world's `datapacks/` directory, then `/reload`.

## Quick start

```mcfunction
/function spm_phase2:quickstart
```

Stand on flat ground first — that position becomes the arena anchor.

## Prerequisites (manual)

`config/spmscavenger.json`:

| Key | Test value |
| --- | --- |
| `enabled` | `true` |
| `smeltEnabled` | `true` |
| `maxPickTier` | `IRON` for RT-F2/RT-F4/RT-F5 |
| `maxAxeTier` | `STONE` to isolate the pick frontier |
| `placeFurnaces` | `true` |
| `useCommunalFurnaces` | `false` (default; RT-F3 expects skip of busy player furnace) |
| `furnaceSearchRadius` | `16` (or ≥ arena span) |
| `craftTools` | `true` (charcoal → torch chain) |
| `gatherResources` | `true` optional |

`/gamerule mobGriefing true` (set by `setup`).

**Iron note (D-FSM-010 / FS-8):** iron smelting is consumer-driven. The preset supplies a stone
pick, sticks, fuel, and raw iron; `maxPickTier=IRON` creates the live three-ingot deficit. Raw iron
without that consumer must not start a new batch. The datapack setup alone is not runtime proof.

## Runtime matrix

| ID | Commands | Must happen | Must not |
| --- | --- | --- | --- |
| RT-F1 | `quickstart` / `spawn/need_charcoal` | Charcoal at owned furnace → torches | Burn all logs |
| RT-F2 | Set `maxPickTier=IRON`, then `arena/build` + `spawn/need_iron_smelt` | Smelt 3 ingots then craft iron pick | Hoard ingots or steal player-furnace contents |
| RT-F3 | `arena/build` + `spawn/player_furnace_test` | Skip busy furnace at +6 | Steal player coal |
| RT-F4 | RT-F2 + interrupt `/reload` | Reclaim or fail-closed | Duplicate stacks |
| RT-F5 | RT-F2 → wait → `spawn/second_claimant` | One furnace claimant | Double insert |

## Arena offsets (from anchor)

| Fixture | Offset |
| --- | --- |
| Crafting table | +1 ~ +1 |
| Place pad (air + gravel) | +3 ~ +3 |
| Empty non-owned furnace (communal-only; skipped when `useCommunalFurnaces=false`) | +4 ~ +3 |
| Log stash (charcoal input) | +2 ~ +5 |
| Cobble stash (MAKE_FURNACE) | +0 ~ +4 |
| Busy player furnace (coal in fuel) | +6 ~ +0 |
| Spawn P2Test | +3 ~ +0 |
| Spawn P2B (second claimant) | +5 ~ +0 |

## Remove when done

Delete from `datapacks/` and `/reload`. This kit is temporary and must not ship in the mod JAR.
