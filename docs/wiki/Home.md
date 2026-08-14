# SPM Scavenger

SPM Scavenger is a Fabric addon that gives Social Player Mobs (SPM) practical survival behavior without replacing the host mod. PlayerMobs can gather resources, progress through tools, craft, use furnaces, light dark areas, seek shelter, explore, escape environmental hazards, and develop bounded opinions that influence eligible discretionary choices.

The source code is authoritative. This Wiki documents the durable architecture and extension contracts. RFCs under `plans/` are collaborative planning records: they preserve decisions, objections, and evidence, but may contain superseded proposals or stale status text.

## Feature index

- **Scavenging and progression** — resource demand, gathering, crafting, tool upgrades, and furnace work.
- **Exploration and mining** — local wandering, directional expeditions, mining projects, interruption handling, and bounded recovery.
- **Survival behavior** — environmental escape, lighting, campfires, and structural nighttime shelter.
- **Opinion system** — personality, affect, learned activity/entity/place/environment preferences, and discretionary EXPLORE, REST, and SOCIAL choices.
- **SPM compatibility** — optional, fail-closed integration that preserves host behavior when unavailable or disabled.

## Architecture index

- [[Opinion System|Opinion-System]] — current Opinion architecture and behavior.
- [[Mining and Wealth System|Mining-and-Wealth-System]] — resource demand, optional stockpiling, legitimate discovery, and bounded mining ownership.
- [[Extending Opinion|Extending-Opinion]] — how to add another discretionary activity safely.
- [[Compatibility Contracts|Compatibility-Contracts]] — reusable host/addon integration rules.
- [[Mod Support|Mod-Support]] — current compatibility surface and the preferred extension ladder.

For builds, configuration, installation, and current limitations, use the [main repository](https://github.com/SunGodNikka1/SPMScavenger-Fabric).

## Documentation rule for future agents

Read the current source before changing this Wiki. When source and an RFC disagree, verify the source and update the durable documentation rather than copying RFC chronology. Record plans in the RFC; record stable current truth here.
