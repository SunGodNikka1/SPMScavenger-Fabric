# Mining and Wealth System

SPM Scavenger separates **resources required to accomplish work** from **optional desire to keep additional useful resources**. Mining intelligence decides how legitimate underground work is pursued; the wealth policy decides whether one more nearby resource is worth collecting. Neither system may invent permission to mine an unsafe, unreachable, hidden, or otherwise illegal target.

The source code is authoritative. This page describes the current implementation, not the chronology of the mining RFC.

## Core ownership

```text
Recipe/tool/project requirements
        ↓
GatherIntentPolicy / WorkDemandPolicy
        ↓
required resources + optional wealth contexts
        ↓
GatherTargetPolicy
        ↓
legitimate candidate ranking
        ↓
GatherResourcesGoal performs physical collection

Deep-work demand
        ↓
MiningDirector + MiningProject
        ↓
ControlledDescentGoal / TunnelSearchGoal
        ↓
newly exposed resources return to normal gathering
```

Policy and execution remain separate. `MiningDirector` and `MiningProject` own bounded assignment/session state. Existing goals own navigation and physical block interaction. Executors do not create projects merely because they happen to be runnable.

## NEED is not WEALTH

Required demand always outranks optional stockpiling:

1. immediate recipe or survival demand;
2. replacement equipment demand;
3. active project demand;
4. working reserve;
5. optional wealth desire.

`ResourceWealthPolicy.evaluateNeed()` implements this allocation order as a pure, unit-tested API. It is not currently the production owner of all need calculation: live recipe and equipment shortfalls are still assembled by `GatherIntentPolicy`, `WorkDemandPolicy`, and their consumer specifications. Future work must not claim complete NEED-layer integration merely because the pure API exists.

## Wealth behavior

Wealth is implemented in live gathering through `GatherIntentPolicy`, `ResourceWealthPolicy`, and `GatherTargetPolicy`.

Each resource has a comfortable and saturation band:

| Category | Comfortable | Saturation | Notes |
| --- | ---: | ---: | --- |
| Logs | 8 | 32 | Common crafting/fuel material |
| Coal/charcoal | 16 | 64 | Fuel and torches |
| Cobblestone | 12 | 48 | Tool/building reserve |
| Iron | 12 | 48 | Broad equipment utility |
| Diamond | 6 | 24 | Rare and highly hoardable |

The marginal factor is `1.0` below the comfortable amount, declines linearly toward `0.05`, and remains at that floor at or above saturation. Saturation prevents floor-level desire from starting a new wealth-only scan.

Candidate value is intentionally local:

```text
wealth desire
×
proximity within a greed-scaled detour budget
=
acquisition utility
```

Distance is applied once through proximity. It is not subtracted again as a raw cost. Required resources use a higher ranking tier than wealth resources, so optional stockpiling cannot displace a blocking recipe/tool need.

## Configuration and parity

| Setting | Range | Default | Meaning |
| --- | ---: | ---: | --- |
| `greed` | `0.0–1.0` | `0.0` | How strongly additional stock is valued and how far a wealth detour may extend |
| `wealthLevel` | `0.0–4.0` | `0.0` | Global wealth-value multiplier |

Either value at zero disables wealth utility. The default therefore preserves exact-consumer gathering behavior. Wealth adds optional desire; it never reduces or replaces an existing consumer deficit.

## Legitimate discovery

Wealth does not grant clairvoyance. A resource must pass the normal discovery and safety pipeline before wealth can rank it:

- exposed/visible and newly exposed candidates may be considered;
- undiscovered ore behind solid blocks is not an exact gather target;
- required tool capability and normal gathering legality still apply;
- mining projects create bounded exposure opportunities rather than scanning hidden ore;
- cave, newly exposed, and distance signals rank already legitimate candidates only.

The durable rule is:

> Preference changes ranking inside the legal candidate set; it does not create a candidate or permission.

## Mining execution controls

The current deep-mining stack includes:

- deterministic descent headings;
- bounded natural-descent exhaustion;
- safe staircase planning and controlled descent;
- assignment/start/progress leases;
- explicit scheduler arbitration and protected interruption handling;
- a straight `1×2` tunnel-search mode;
- cooperative handoff to normal gathering when excavation exposes a legitimate resource;
- cave-breakthrough handoff rather than tunneling blindly through an opening.

Safety, combat, player commands, shelter authority, ticking boundaries, tool capability, and bounded failure remain stronger than autonomous mining desire.

## Current limitations

These are not part of the current proven runtime contract:

- full production use of the pure NEED allocation API;
- persistent `MiningMemory` and resumable abandoned mining projects;
- `VeinFrontier`, resource portfolios, scarcity memory, and consumption-velocity reserves;
- personality-derived per-mob greed or mining styles;
- branch-mine layouts beyond the bounded straight corridor;
- generic action-aware/Baritone-like navigation;
- Nether ancient-debris progression;
- automatic compatibility with every modded ore or processing machine;
- large-mob performance parity and complete multi-strategy runtime verification.

Mod support must follow [[Mod Support|Mod-Support]]: generic mechanics first, tags/data second, and a mod-specific adapter only for genuinely new semantics.

## Evidence and extension points

Current implementation:

- [ResourceWealthPolicy.java](https://github.com/SunGodNikka1/SPMScavenger-Fabric/blob/master/src/main/java/com/noobk/spmscavenger/ResourceWealthPolicy.java)
- [GatherIntentPolicy.java](https://github.com/SunGodNikka1/SPMScavenger-Fabric/blob/master/src/main/java/com/noobk/spmscavenger/GatherIntentPolicy.java)
- [GatherTargetPolicy.java](https://github.com/SunGodNikka1/SPMScavenger-Fabric/blob/master/src/main/java/com/noobk/spmscavenger/GatherTargetPolicy.java)
- [MiningDirector.java](https://github.com/SunGodNikka1/SPMScavenger-Fabric/blob/master/src/main/java/com/noobk/spmscavenger/mining/MiningDirector.java)
- [ControlledDescentGoal.java](https://github.com/SunGodNikka1/SPMScavenger-Fabric/blob/master/src/main/java/com/noobk/spmscavenger/goal/ControlledDescentGoal.java)
- [TunnelSearchGoal.java](https://github.com/SunGodNikka1/SPMScavenger-Fabric/blob/master/src/main/java/com/noobk/spmscavenger/goal/TunnelSearchGoal.java)

Automated tests prove policy arithmetic, ordering, bounded state transitions, and control contracts. They do not prove several-minute physical Minecraft behavior, save/reload behavior, multiplayer contention, or performance. Those claims remain runtime-unverified until tested with the required evidence.
