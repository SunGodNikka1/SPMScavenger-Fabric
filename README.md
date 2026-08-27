# Social Player Mobs: Scavenger

An AI and survival-behavior addon for **Social Player Mobs** on **Minecraft 1.21.1 Fabric**.

Scavenger gives PlayerMobs purposeful work outside Dungeon Train: they gather resources, craft and
upgrade tools, use furnaces, place torches, seek shelter, explore, recover from environmental traps,
and perform small player-like social behaviors.

> Current mod version: **1.9.4**
> Minecraft: **1.21.1** · Fabric Loader: **0.16.14+** · Java: **21**

## Requirements

| Dependency | Requirement | Purpose |
| --- | --- | --- |
| Minecraft | 1.21.1 | Target game version |
| Fabric Loader | 0.16.0 or newer | Mod loader |
| Fabric API | Required | Events and Fabric integration |
| Social Player Mobs | Recommended | Supplies the PlayerMob entities this addon enhances |
| Cloth Config | Optional | Configuration screen |
| Mod Menu | Optional | Opens the Cloth Config screen |

The addon loads safely without Social Player Mobs and logs that it is inactive. It does not bundle
or redistribute Social Player Mobs.

## Installation

1. Install Minecraft 1.21.1, Fabric Loader, and Fabric API.
2. Install Social Player Mobs.
3. Copy `spmscavenger-1.9.4.jar` into the instance's `mods` directory.
4. Optionally install Cloth Config and Mod Menu.
5. Keep the vanilla `mobGriefing` rule enabled if mobs should gather or place blocks:

   ```mcfunction
   /gamerule mobGriefing true
   ```

## Features

### Gathering and tool progression

PlayerMobs can:

- fell approved natural trees instead of breaking one log and abandoning the trunk;
- clear a small number of directly obstructing leaves after a real navigation stall;
- gather exposed coal, stone/cobblestone, iron ore, and deep exposed diamond ore when demanded;
- retain required drops directly when SPM's ordinary pickup policy would ignore them;
- craft planks, sticks, torches, crafting tables, furnaces, and campfires;
- craft wooden, stone, iron, and diamond pickaxes and axes;
- upgrade the pickaxe before the axe and drop the replaced tool only after an atomic craft succeeds;
- stop ore demand when the active tool consumer is satisfied or a suitable tool is looted.

Diamond demand is disabled above Y=16 until deeper mining intelligence is implemented. This avoids
surface mobs repeatedly scanning for ore that cannot exist within their local search volume.

### Furnace work

Mobs use real furnaces for charcoal and iron progression. Furnace jobs reserve input, fuel, output,
and ownership state so interruption or save/reload does not silently duplicate or lose resources.
Communal player/village furnaces are opt-in; by default mobs use furnaces they own or place.

### Purposeful exploration

The addon replaces SPM's ordinary idle stroll with two compatible layers:

- **Local wandering** for short idle movement.
- **Exploration expeditions** with a persistent heading and 2–4 forward-biased stages.

Expeditions retain intended waypoints across temporary combat or work interruptions, but calculate a
new Minecraft path when resuming. They never force chunk loading and validate server entity-ticking
territory rather than merely checking whether chunks are loaded. Recent destination memory reduces
repeated trips, and bounded failure/replan limits prevent permanent path loops.

Friendly PlayerMobs may join an expedition when both mobs' SPM relationship values are positive.
Persistent stay-near orders always win and cancel an incompatible expedition.

With Opinion enabled, a successfully completed expedition can build a small semantic affinity for
tagged `FOREST`, `OCEAN`, `SNOWY`, `NETHER`, or `END` environments. This is only a ±10 tie-breaker
among destinations that already passed ticking and route construction checks. It never changes
terrain safety, powder-snow avoidance/escape, mandatory descent, or pathfinding capability.

### Environmental escape

Trapped mobs attempt movement first, then may mine the actual entrapping powder snow, sand, gravel,
or suffocating block when configured. Escape mining:

- respects `mobGriefing`, hardness limits, allow/deny tags, and a per-incident block cap;
- temporarily equips the best owned tool;
- uses visible swing and block-breaking progress instead of instant deletion;
- spends tool durability and restores previous equipment after completion or interruption;
- yields immediately to SPM's existing fire/water escape behavior.

### Shelter, lighting, and camp life

- Seek ranked shelter at dusk: structural interiors outrank caves, trees, eaves, and porches;
  several mobs reserve separated standing capacity instead of crowding one cell.
- Treat arrival as a current condition: harmless social displacement returns to the same reserved
  shelter, while an already-safe interior never chases a bed across exposed ground.
- Capture a structurally better room encountered during a worse exterior shelter trip, including
  tiny door-adjacent village interiors; an optional stock-SPM guard prevents lost busy door opens.
- Sleep in real beds and release them at dawn or after interruption.
- Place backpack torches at dark supported positions; torches are consumed, never conjured.
- Craft and place a campfire after essential torch/tool needs are satisfied.
- Crouch with nearby players and bunny-hop while chasing when collision space permits.

## Configuration

Open **Mod Menu → Social Player Mobs: Scavenger**, or edit:

```text
config/spmscavenger.json
```

Important defaults:

| Setting | Default | Meaning |
| --- | ---: | --- |
| `enabled` | `true` | Master switch |
| `gatherResources` | `true` | Allows the destructive gathering goal |
| `protectPlayerBuilds` | `true` | Restricts gathering to natural/protected-safe targets |
| `clearLeafObstructions` | `true` | Bounded leaf recovery while approaching a tree |
| `craftTools` | `true` | Enables crafting-table and tool progression |
| `maxPickTier` / `maxAxeTier` | `STONE` | Maximum autonomous tool tier; supports NONE through DIAMOND |
| `cobbleStockTarget` | `6` | Cobble required while stone upgrades remain |
| `torchStockTarget` | `8` | Torch-chain stopping target |
| `smeltEnabled` | `true` | Enables charcoal and iron furnace work |
| `useCommunalFurnaces` | `false` | Allows empty non-owned furnaces |
| `exploring` | `true` | Enables local wander tracking and expeditions |
| `exploreIdleTicks` | `600` | Idle time before exploration becomes eligible |
| `exploreMinStageDistance` / `exploreMaxStageDistance` | `24` / `48` | Intended blocks per expedition stage |
| `environmentalEscape` | `true` | Enables powder-snow and suffocation recovery |
| `environmentalEscapeMaxBlocks` | `3` | Maximum removals in one continuous incident |
| `greed` / `wealthLevel` | `0.0` / `0.0` | Reserved wealth controls; zero preserves exact-consumer behavior |

All world-changing behavior also respects `mobGriefing`. Turning the gamerule off prevents block
breaking and placement even when the corresponding addon setting is enabled.

## Compatibility design

- Goals are attached only to confirmed Social Player Mobs on Fabric's server entity-load event.
- One accessor mixin exposes vanilla `Mob.goalSelector`; SPM code is not copied or bundled.
- One optional client compatibility mixin makes SPM 0.86.x's Creative decision glyphs full-bright
  while preserving SPM/Minecraft's user-controlled backdrop. With an active Iris shader pack, the
  already-formatted lines are redrawn in a bounded post-shader pass using Minecraft's complete
  projection/view/billboard transform so packs such as Photon cannot directionally darken them.
  Solid-terrain occlusion restores SPM's faint see-through presentation instead of leaving the HUD
  copy fully bright through blocks. Iris is detected reflectively and remains optional. The Mixin
  is `@Pseudo`/non-required and becomes a no-op if SPM is absent or its renderer signature changes;
  Scavenger does not replace or redistribute the host renderer.
- One optional common-side readout bridge replaces the fallback `Craft torches` label with the
  live recipe-only form `Crafting — <recipe>` (for example, `Crafting — diamond pickaxe`). The
  selected recipe remains visible while the mob travels to or places a crafting table.
- Existing SPM combat, fleeing, food, looting, social, order, and fire-escape goals retain higher
  priority.
- The addon reuses the PlayerMob backpack through vanilla inventory interfaces.
- Unknown or changed SPM APIs fail closed for the affected optional behavior.

## Building from source

From this directory on Windows:

```powershell
.\gradlew.bat clean build
```

On Linux or macOS:

```bash
./gradlew clean build
```

The normal build produces two independently installable artifacts:

```text
build/libs/spmscavenger-1.11.0.jar
build/libs/spmscavenger-1.11.0-validation.jar
```

Install the production JAR for normal play. The validation JAR is a non-distributed Task-59
certification sidecar; it depends on the matching production mod and is only needed for approved
Village/Raid runtime campaigns.

The latest verified clean build completed with **1,635 production tests and 57 validation tests,
zero failures, zero errors, and zero skips**. SHA-256 values were:

```text
spmscavenger-1.11.0.jar
05E77B7F9ACC29B0459FA8F4B5908082546188591A9AD4AFB6D024A4E00A930B

spmscavenger-1.11.0-validation.jar
BB02D551AEED4733434A3756401A9B520091C4056477A7C347CD656CC5F546A0
```

That hash changes whenever source or packaged resources change.

## Project wiki

Durable feature and architecture documentation lives in [`docs/wiki/`](docs/wiki/Home.md):

- [`Opinion System`](docs/wiki/Opinion-System.md) — how the finished Opinion feature works.
- [`Extending Opinion`](docs/wiki/Extending-Opinion.md) — how to add future discretionary activities without reopening GA-OPINION.
- [`Compatibility Contracts`](docs/wiki/Compatibility-Contracts.md) — reusable host-mod and optional integration rules.

## Development documentation

- [`plans/RFC-VANILLA-AUTONOMOUS-PROGRESSION.md`](plans/RFC-VANILLA-AUTONOMOUS-PROGRESSION.md) — united vanilla progression + mining intelligence deferred/partial backlog.
- [`plans/RFC-TOOL-TIER-UPGRADES.md`](plans/RFC-TOOL-TIER-UPGRADES.md) — tool progression decisions and parity.
- [`plans/RFC-FURNACE-SMELTING.md`](plans/RFC-FURNACE-SMELTING.md) — furnace ownership, transactions, and recovery.
- [`docs/porting/TEST_MATRIX.md`](docs/porting/TEST_MATRIX.md) — must-happen/must-not-happen checks.
- [`docs/porting/DECISIONS.md`](docs/porting/DECISIONS.md) — implementation decisions and failure history.
- [`.superpowers/sdd/progress.md`](.superpowers/sdd/progress.md) — task ledger and active frontiers.
- [`.superpowers/sdd/archive/`](.superpowers/sdd/archive/) — completed task briefs/reports (tasks 6–32).

## Verification status and known gaps

`CONFIRMED` means supported by current source, automated tests, or build/package evidence. It does not
automatically mean the behavior has been observed in Minecraft.

- **Build/unit/package:** confirmed for the current source tree.
- **Selected earlier gameplay:** bed use and basic gathering were observed in prior sessions.
- **Current combined 1.9.4 behavior:** runtime verification remains incomplete for the full
  gather → smelt → iron/diamond upgrade loop, long expeditions, companions, save/reload recovery,
  dedicated servers, and large-mob performance.
- **Mining intelligence:** MI-1 gather intent and MI-3/MI-23 NEED allocation exist. Marginal wealth,
  legitimate ore discovery classification, cave seeking, vein memory, and bounded deep mining remain
  planned or deferred.
- **Performance:** gathering and exploration contain bounded scans and staggered work, but current
  1/10/50/100-mob profiling is still unverified.

Minecraft runtime launches require explicit project approval. See the test matrix for reproducible
scenarios and the evidence needed before upgrading these claims.

## License

This addon is licensed under **MIT** and contains no Social Player Mobs source code. Social Player
Mobs has its own license, which applies to that project separately.
