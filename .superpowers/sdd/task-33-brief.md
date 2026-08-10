# Task 33 brief: RT-MI-TS1 runtime falsification (multi-strategy mining loop)

## Target

`D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric`

## RFC

`plans/RFC-MINING-INTELLIGENCE-AND-WEALTH-SYSTEM.md` — TUNNEL_SEARCH frontier, RT-MI-TS1 rows

## Binding constraints

- **Minecraft launch requires explicit user approval** (Gate 6). This brief authorizes datapack
  authoring and a written observation protocol only until the user approves a specific launch.
- No commit unless user requests.
- Use Fabulously Optimized or a disposable copy — do not download mods into Gradle `run/mods/`.
- Fixture must not use `playermob stay` (blocks expeditions; see phase4-perf lesson).

## Goal

Prove or falsify the first end-to-end autonomous mining loop in a running game:

```text
DESCENT → HANDOFF_TUNNEL_SEARCH → TUNNEL (1x2) → EXPOSE → GATHER → VEIN → RESUME → CAVE_FOUND
```

## Deliverables

### 1. Runtime datapack `test-datapacks/phase3-mining-tunnel/`

Namespace `spm_phase3`. Follow `docs/agent-workflows/RUNTIME_TEST_DATAPACK.md`.

| Function | Purpose |
| --- | --- |
| `help` | Lists RT-MI-TS1 steps |
| `setup` | `mobGriefing true`, day, clear weather |
| `anchor/set` | Pin arena origin |
| `arena/build` | Deepslate band at Y≈12–14; 20-block straight tunnel runway; **one side-wall diamond** exposed only after a cut cell; terminal **cave pocket** beyond last solid wall |
| `spawn/tunnel_ready` | Iron pick, torches, empty diamond demand trigger (`diamondProgressionDemand > 0` via starter kit / cleared inventory per README) |
| `tools/inspect` | Nearest test mob: mode, project, exposure state, hand tool |
| `cleanup` | Kill tagged mobs, clear anchor |

Document each RT-MI-TS1a–d row in `README.md` with must-happen / must-not-happen.

### 2. Observation protocol (`docs/porting/MINING_RUNTIME_LOG.md`)

For the approved session, record:

- Instance path, mod JAR hash, seed, anchor coords
- Timestamped log grep: `spmscavenger` exploration/mining lines
- Per-phase tick estimate (descent, handoff, first cut, gather takeover, resume, cave)
- Pass/fail per RT-MI-TS1a–d with quoted log lines or `UNVERIFIED` + reason

### 3. Report

`.superpowers/sdd/task-33-report.md` — status `DONE`, `DONE_WITH_CONCERNS`, or `BLOCKED`.

## Acceptance (from RFC)

| ID | Must happen | Must not |
| --- | --- | --- |
| RT-MI-TS1a | `HANDOFF_TUNNEL_SEARCH` consumed; mob digs horizontally at Y≤16 | Stop at band with pending transition |
| RT-MI-TS1b | Side diamond gathered by existing `GatherResourcesGoal` | Clairvoyant ore path |
| RT-MI-TS1c | Same tunnel project + heading after gather | False `NO_PROGRESS`; second staircase |
| RT-MI-TS1d | Cave breakthrough → `CAVE_FOUND` | Drill through open cave |

## Verification (pre-launch, static)

```powershell
cd "D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric"
.\gradlew.bat compileJava
.\gradlew.bat test --tests "*TunnelExecutionChain*"
```

Full suite optional; record if Gradle worker fails.

## Out of scope

- Branch-mine geometry (D-MIW-041 Option B)
- Project resumption after combat interrupt
- Wealth runtime matrix (`phase-mining-wealth/`)
