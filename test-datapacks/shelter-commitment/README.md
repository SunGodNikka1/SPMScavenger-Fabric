# Shelter Commitment and Interior Capacity runtime test

**RFC:** `plans/RFC-VANILLA-AUTONOMOUS-PROGRESSION.md` (`SCR-1`, `SCR-2`)

**Namespace:** `spm_shelter`

**Target:** Minecraft 1.21.1 Fabric, Social Player Mobs 0.86.0, current Scavenger build

This temporary datapack builds a short enclosed house whose only entrance is a closed wooden door.
It does not ship in the mod JAR and does not prove behavior by itself.

Copy this folder into the test world's `datapacks/`, run `/reload`, stand on flat ground, then run:

```mcfunction
/function spm_shelter:quickstart
```

## Required config

In `config/spmscavenger.json`:

| Key | Value |
| --- | --- |
| `enabled` | `true` |
| `seekShelter` | `true` |
| `sleepInBeds` | `true` |
| `shelterSearchRadius` | at least `12` |

`setup` freezes time at night, clears weather, disables natural spawning, and enables
`mobGriefing`. The spawned mob is deliberately **not** given an SPM stay order because that player
authority must cancel autonomous shelter.

## Runtime matrix

| ID | Command | Must happen | Must not happen |
| --- | --- | --- | --- |
| SCR-1A | `/function spm_shelter:scenario/occupied_bed` | Mob selects the covered interior, pauses for the door operation, generates a fresh path, crosses the doorway, and remains sheltered | Repeated open/close loop; shelter target resets at each door action |
| SCR-1B | `/function spm_shelter:scenario/free_bed` | Mob retains its bed claim through the door operation, resumes, and sleeps in that same bed | Another scan/reselection or loss of the bed claim during the short interruption |
| SCR-1C | Run either scenario, then `/time set day` while the door operation is active | Commitment cancels and shelter does not resume | Old destination resumes after dawn |
| SCR-1D | Break the selected bed/interior floor while interrupted | Commitment cancels; a later scan may choose a newly valid destination | Navigation retries the destroyed destination forever |
| SCR-2A | `/function spm_shelter:scenario/interior_one` | One mob crosses the door and continues to its deeper reserved interior block even though a nearer exterior eave/inside threshold is covered | Porch/eave or the block immediately inside the door is accepted as completed shelter |
| SCR-2B | `/function spm_shelter:scenario/capacity_four` | Four mobs choose separated interior standing areas | Mobs reserve the same or immediately adjacent cells and pile at the entrance |
| SCR-2C | `/function spm_shelter:scenario/over_capacity` | Interior capacity fills first; surplus mobs choose separated lower-tier fallback or remain without a shelter commitment | Lower-tier candidates displace available interior slots; all mobs converge on one block |
| SCR-2R2-A | Start `interior_one`, then place a leaf canopy/tree fallback closer to the test mob | The structural house interior wins; a log-walled house remains valid | Leaf canopy becomes `INTERIOR_ROOM`, or logs are blacklisted as walls |
| SCR-2R2-B | Teleport the mob to a free cell in the built room before dusk | It adopts the current interior or a same-protected-site bed and stays indoors | It crosses the exterior to chase a remote bed or opens the door to seek generic shelter |
| SCR-2R2-C | After standing shelter reaches `ARRIVED`, trigger a friendly greeting that moves it away | The claim suspends, the same commitment enters `RETURNING`, and the mob returns to the exact reserved cell | Historical `ARRIVED` remains sticky or a new shelter is selected after the greeting |
| SCR-2R2-D | Put a free bed in a second house across at least three sky-exposed path nodes | A mob already safe in the first interior refuses that bed upgrade | Bed tier overrides terrain safety and pulls the mob outside |
| SCR-2R2-E | Let a mob settle under a tree/eave, then make a valid house interior reachable | The bounded 200-tick upgrade check replaces the fallback only with a strictly higher tier | Equal-tier target churn or a new scan every tick |

For the combat negative case, spawn or lure a hostile that gives the PlayerMob an active combat
target during the door interruption. Combat must win; the old shelter commitment must not resume.

Use `/function spm_shelter:tools/inspect` before, during, and after the door operation. Capture a
screenshot or video plus `latest.log`. Runtime remains `UNVERIFIED` until those observations exist.

## Arena offsets

All offsets are relative to the marker created by `anchor/set`:

| Fixture | Offset |
| --- | --- |
| Test mob | `0, +1, 0` |
| Closed oak door | `+4, +1..+2, 0` |
| Covered interior | `+5..+9, +1, -2..+2` |
| Red bed (foot/head) | `+8/+9, +1, 0` |

Remove the datapack after testing and run `/reload`.
