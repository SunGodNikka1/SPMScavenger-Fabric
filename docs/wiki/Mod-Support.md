# Mod Support

Scavenger should grow through a compatibility ladder:

> **Generic mechanics first → registry/tag/data discovery second → mod-specific adapter only for genuinely new semantics.**

This page separates current support from recommended extension architecture. Names such as `ResourceDefinition`, `ToolCapability`, and `ProcessingStationAdapter` below are design contracts, not claims that those classes already exist.

## Compatibility surface

| System | Generic/native potential | Tag/data potential | Adapter need |
|---|---:|---:|---|
| Resource gathering and mining | High | High | Needed for new resources/harvest semantics |
| Tree harvesting | High | High | Sometimes |
| Tool and equipment progression | Medium | High | Needed when normal item data is insufficient |
| Crafting | Medium | High | Needed for custom progression semantics |
| Vanilla furnace smelting | High | High | Usually none for ordinary recipes |
| Custom machines/processing | Low | Low | Required |
| Shelter geometry | High | Medium | For special beds, doors, tents, or furniture |
| Exploration/navigation | High | Medium | For special dimensions/environment meanings |
| Opinion core | High | Not mod-specific | Must remain mod-agnostic |
| Full mod progression | Low | Medium | Required for unique machines, gates, dimensions, bosses, and objectives |

## Current source truth

### Resources and mining

Current gather intent and target policies explicitly model logs, coal, cobblestone, raw iron, and diamond, with a mixture of exact vanilla blocks/items and some vanilla tags. This is not yet a universal mod-resource registry.

A future generic layer should describe a resource semantically:

```text
ResourceDefinition
├ identity and item/block match
├ acquisition method
├ required tool capability
├ demand/consumer
├ scarcity/value
├ world-generation or placement constraints
└ optional owning mod
```

Gathering goals should consume this description rather than accumulate `if modLoaded(...)` branches.

### Tools and equipment

`ToolTierPolicy` currently recognizes exact vanilla wooden, stone, iron, golden, diamond, and netherite pickaxes/axes. Unknown modded tools resolve to no known tier even if they are powerful.

The preferred future model is semantic capability:

```text
ToolCapability
├ kind (pick, axe, ...)
├ harvest capability
├ mining speed
├ durability
├ combat utility
└ progression rank
```

Use standard item/tag/attribute/tool-component evidence first. Add an adapter only where a mod introduces semantics that those data cannot express.

### Crafting

`ScavengerCrafting` already has `IngredientKey` support for exact items or item tags, which is a useful data boundary. The actual step graph and output recipes remain largely explicit vanilla progression: planks, sticks, tools, torches, crafting table, campfire, and furnace.

Extending crafting therefore needs two separate questions:

- Can ingredients be matched generically? Often yes.
- Does the AI understand the new progression and interaction? Usually not without data or an adapter.

### Furnace processing

`FurnacePolicy` asks the live `RecipeManager` for `RecipeType.SMELTING`, assembles the real recipe output, reads cooking time, and uses Minecraft's furnace fuel map. Ordinary mod/datapack recipes that use the vanilla smelting type can therefore participate in recipe planning when their demanded input/output fits the current work model.

Physical execution is narrower: station discovery, placement, transfer, and completion use vanilla furnace items/blocks and `AbstractFurnaceBlockEntity`; current demand is charcoal or iron. An electric crusher, alloy smelter, press, mill, or other custom machine is not “another furnace recipe.”

For genuinely new processing semantics, prefer:

```text
ProcessingStationAdapter
├ canProcess
├ resolve recipe/transformation
├ locate and validate station
├ insert atomically
├ observe progress without mutation
├ extract atomically
└ recover and clean up
```

The generic AI expresses a material transformation; the adapter explains how that mod performs it.

## Opinion must stay mod-agnostic

Do not add `CREATE_ACTIVITY`, `MEKANISM_ACTIVITY`, or one Opinion enum per installed mod. A compatible feature exposes a semantic activity, exact candidate identity, executor/admission/continuation contract, terminal outcome, and eligible experience.

For example, a workshop integration may expose a generic `WORKSHOP_TINKERING` candidate backed by a mod-specific machine adapter. Opinion sees a legal activity lifecycle—not the mod's implementation details. Follow [[Extending Opinion|Extending-Opinion]] for the full contract.

## Recommended implementation order

1. **Resources/mining** — define semantic resource and harvest requirements.
2. **Tools** — replace exact-item-only recognition with proven capability discovery plus adapters.
3. **Crafting/processing** — preserve recipe/tag genericity and add transactional station adapters.
4. **Full mod progression** — represent prerequisites and alternatives as data/graphs, with target adapters for unique mechanics.
5. **Equipment/survival** — rank armor, shields, melee/ranged weapons, and special survival equipment semantically.

Each step must preserve host behavior when the integration is absent or disabled, and must distinguish source-confirmed generic support from runtime-unverified cross-mod behavior.
