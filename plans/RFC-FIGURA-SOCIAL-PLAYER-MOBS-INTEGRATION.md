# RFC: Figura × Social Player Mobs Read-Only Avatar Integration

## RFC Identity

| Field | Value |
| --- | --- |
| Canonical artifact | `plans/RFC-FIGURA-SOCIAL-PLAYER-MOBS-INTEGRATION.md` |
| Planning home | `D:\Apps\Minecraft Port\Projects\SPMScavenger-1.21.1-Fabric` |
| Mode | `PLANNING` — RFC artifact only; no implementation or Minecraft launch |
| Status | `RESEARCHING / PROPOSED` — native feasibility is source-confirmed; runtime compatibility and assignment ownership remain open |
| Scope | Optional Figura avatars for SPM `PlayerMobEntity`, generic entity Lua compatibility, per-mob assignment, and read-only SPM/Scavenger presentation state |
| Out of scope | Lua control of server AI, arbitrary server actions, a second AI/activity system, Figura source modification, and avatar authoring itself |
| Minecraft | `1.21.1` |
| Loader | Fabric Loader `0.16.14`; Fabric API `0.116.4+1.21.1` |
| SPM baseline | `0.96.0`; SHA-256 `508EDA58611A2A0738E257F98C2E14C5032C6EFBF5B1A985C9F93EE295131097` |
| Scavenger baseline | `1.11.0`; current working tree at RFC creation |
| Figura baseline | `0.1.6+1.21.1` Fabric; tag commit `07f34184d52c25d2fec9390eaf1530566efd89e3`; SHA-256 `7B1278CADB52507ABF6645955DCF3F92EC34BF9EA4A9A665C6A98423E2668628` |
| Initial contributors | User/Product Owner; Codex/Primary RFC Author; Figura_Source_Audit; SPM_State_Audit; Figura_Architecture_Reviewer |
| Last updated | 2026-08-29 |

### Baseline correction

`SOURCE_CONFIRMED`: Figura `0.1.6` supports Fabric 1.21.1, but the official tag, GitHub release,
CurseForge file list, and Modrinth version metadata date the release to **2026-06-19**, not
2026-08-26. The later date in the initiating proposal is therefore `UNVERIFIED` and is not used as
baseline evidence.

## Executive Summary

Figura 0.1.6 already owns most of the difficult rendering and Lua machinery needed for an SPM
avatar. Its generic `LivingEntityRenderer` mixin asks `AvatarManager.getAvatar(livingEntity)` for
every living entity. Its entity/CEM path creates `Avatar(Entity)`, binds the Lua runtime to that
entity, and supplies both `user` and the familiar `player` global. Since SPM 0.96 renders
`PlayerMobEntity` through `HumanoidMobRenderer` with a real `PlayerModel`, the static architecture is
an unusually close fit.

That source fit is not runtime proof. The smallest defensible first step is therefore **not a new
compatibility implementation**. It is a no-code/native Figura witness using a type-wide CEM resource
or Figura's debug-only `set_avatar` command to prove the actual SPM renderer, model layers, Lua tick,
and generic entity API behavior.

The proposal contains two distinct products that must not be conflated:

1. **Avatar compatibility:** Figura renders and ticks an avatar on a `PlayerMobEntity`.
2. **Persistent per-mob identity:** every viewer resolves the same durable avatar assignment for a
   specific PlayerMob UUID.

Figura already supplies the first path structurally. It does **not** supply a public persistent,
viewer-consistent per-entity assignment service. Its `set_avatar` command is debug-only,
client-local, transient, and keyed by the current `Entity` object. Per-mob persistence and
multiplayer distribution therefore remain a separate product decision.

The recommended long-term ownership is:

```text
SERVER TRUTH
SPM synced traits + Scavenger passive/non-creating truth providers
                         |
                         | compact, allowlisted, read-only snapshot
                         v
CLIENT PRESENTATION CACHE
                         |
                         v
Figura official figura_api entrypoint
                         |
                         v
read-only Lua global: spm
                         |
                         v
models / animations / particles / sounds
```

The reverse direction does not exist:

```text
Figura Lua -X-> GoalSelector / inventory mutation / teleport / forced target / authority
```

SPM and Scavenger remain the sole owners of AI, authority, goals, inventory, navigation, combat,
relationships, and world mutation. Figura owns client presentation only.

## Collaboration Protocol

- Every material claim uses `SOURCE_CONFIRMED`, `CODE_CONFIRMED`, `RUNTIME_CONFIRMED`,
  `INFERRED`, or `UNVERIFIED`.
- Exact Figura claims cite tag `07f34184d52c25d2fec9390eaf1530566efd89e3` or the hashed
  release artifact; moving branch names are not authority.
- Cross-mod claims require evidence from both Figura and SPM/Scavenger.
- A build can prove compilation/package shape only. It cannot close rendering, Lua, multiplayer,
  or performance rows.
- Nontrivial decisions require alternatives, objections, performance/lifecycle consequences, and
  independent review or explicit user resolution before `LOCKED`.
- This RFC is the single plan for this feature. Later agents update its stable topic slots rather
  than creating a second Figura plan.
- Minecraft runtime launches require separate explicit approval.

## Baselines and Current Implementation

### Figura 0.1.6

`SOURCE_CONFIRMED`:

- [`AvatarManager`](https://github.com/FiguraMC/Figura/blob/07f34184d52c25d2fec9390eaf1530566efd89e3/common/src/main/java/org/figuramc/figura/avatar/AvatarManager.java)
  owns `LOADED_CEM`, looks up CEM avatars by entity type, creates `new Avatar(entity)`, and exposes
  the debug `set_avatar` command.
- [`LivingEntityRendererMixin`](https://github.com/FiguraMC/Figura/blob/07f34184d52c25d2fec9390eaf1530566efd89e3/common/src/main/java/org/figuramc/figura/mixin/render/renderers/LivingEntityRendererMixin.java)
  targets generic `LivingEntityRenderer`, calls `AvatarManager.getAvatar(livingEntity)`, and invokes
  avatar render hooks after vanilla `setupAnim`.
- [`Avatar`](https://github.com/FiguraMC/Figura/blob/07f34184d52c25d2fec9390eaf1530566efd89e3/common/src/main/java/org/figuramc/figura/avatar/Avatar.java)
  has an entity constructor and binds the resolved owner entity to its Lua runtime.
- [`FiguraLuaRuntime`](https://github.com/FiguraMC/Figura/blob/07f34184d52c25d2fec9390eaf1530566efd89e3/common/src/main/java/org/figuramc/figura/lua/FiguraLuaRuntime.java)
  sets `user` and `player` to the same wrapped entity.
- [`EntityAPI.wrap`](https://github.com/FiguraMC/Figura/blob/07f34184d52c25d2fec9390eaf1530566efd89e3/common/src/main/java/org/figuramc/figura/lua/api/entity/EntityAPI.java)
  returns `PlayerAPI` only for literal `Player`, `LivingEntityAPI` for other living entities, and
  `EntityAPI` otherwise.
- [`FiguraAPI`](https://github.com/FiguraMC/Figura/blob/07f34184d52c25d2fec9390eaf1530566efd89e3/common/src/main/java/org/figuramc/figura/entries/FiguraAPI.java)
  and [`EntryPointManager`](https://github.com/FiguraMC/Figura/blob/07f34184d52c25d2fec9390eaf1530566efd89e3/common/src/main/java/org/figuramc/figura/entries/EntryPointManager.java)
  provide an official `figura_api` addon entrypoint for installing a Lua global.
- Packaged `fabric.mod.json` declares client environment and PolyForm Noncommercial 1.0.0.

`UNVERIFIED`: whether an avatar visibly renders correctly on the exact SPM 0.96 renderer and current
Scavenger artifact. Static inheritance and signatures do not prove mixin coexistence or layer
behavior.

### Social Player Mobs 0.96

`CODE_CONFIRMED`:

- `PlayerMobEntity extends PathfinderMob`:
  `Projects/references/SocialPlayerMobs-v0.96.0/src/main/java/games/brennan/playermob/entity/PlayerMobEntity.java`.
- `PlayerMobRenderer extends HumanoidMobRenderer<PlayerMobEntity, PlayerModel<PlayerMobEntity>>` and
  explicitly installs armor and held-item layers:
  `Projects/references/SocialPlayerMobs-v0.96.0/src/main/java/games/brennan/playermob/client/PlayerMobRenderer.java`.
- SPM already syncs fight/flight, friendliness, reaction speed, feelings, skin state, and a
  human-readable objective string through `SynchedEntityData`.

### Scavenger 1.11.0

`CODE_CONFIRMED`:

- Semantic activity, Opinion personality, mining/project truth, shelter phases, mandatory
  ownership, and required-trade commute bindings are server-side.
- Passive/non-creating reads already exist, including `ActivityObservationService.observe`,
  `OpinionExperienceRegistry.find`, `MiningProjectSavedData.peekReadOnly`,
  `VillageMemorySavedData.peekInDimension`, `VillageWorkFactsService.peekReadOnly`, and
  `MandatoryOwnershipRegistry.peekLiveClaim`.
- `GeneralDebugSnapshot.capture` already demonstrates passive composition for an operator command.
- The existing Opinion inspector demonstrates server-authoritative snapshot networking, but its
  manual request, permission, range, and diagnostic payload contract are not appropriate for
  continuous avatar state.

### Negative evidence

The required absence probes found:

1. `NOT FOUND`: any Figura/Figurity reference in current SPM 0.96 source.
2. `NOT FOUND`: any Figura/Figurity production source, resource, build dependency, or RFC in
   Scavenger before this document.
3. `NOT FOUND`: a public Figura persistent UUID-to-avatar assignment provider/entrypoint.
4. `NOT FOUND`: a Figura entity-assignment packet or persistent entity assignment store.
5. `NOT FOUND`: a current semantic Scavenger activity snapshot available to ordinary clients.
6. `NOT FOUND`: reliable custom target synchronization for SPM `Mob#getTarget()`.

## Research Ledger

| Reference | Relevant technique | Applicability | Limitation | Evidence |
| --- | --- | --- | --- | --- |
| Figura 0.1.6 tag | Generic living-entity render injection and CEM avatar map | Direct structural fit | Runtime coexistence unproven | `SOURCE_CONFIRMED` |
| Figura `figura_api` entrypoint | Addon-owned Lua global per Avatar | Preferred read-only API seam | Exact-version dependency; not assignment | `SOURCE_CONFIRMED` |
| Figura `set_avatar` command | Copy loaded avatar NBT to arbitrary loaded entity | Excellent disposable prototype | Debug-only, local, transient, unsynchronized | `SOURCE_CONFIRMED` |
| SPM 0.96 renderer | Real `PlayerModel` under `HumanoidMobRenderer` | Compatible with Figura generic hook | Custom layers/slim swap need runtime proof | `CODE_CONFIRMED` / `INFERRED` |
| SPM synced disposition | Already client-visible traits and feelings | No new packet required for host traits | Objective string is not semantic API | `CODE_CONFIRMED` |
| Scavenger passive truth APIs | Non-creating server observation | Source for compact cosmetic snapshot | No continuous client projection exists | `CODE_CONFIRMED` |
| Scavenger Opinion inspector | Immutable server-to-client snapshot precedent | Proves project networking pattern | Too heavy/manual/restricted for Lua polling | `CODE_CONFIRMED` |
| [Figurity](https://modrinth.com/mod/figurity) 2.0.1 | Persistent entity avatar assignment through NBT | Existing alternative worth evaluating | Server-required, all-rights-reserved, no published source URL; broader commands/actions and mixins than this RFC needs | Metadata `DOCUMENTATION_CONFIRMED`; artifact shape `BINARY_CONFIRMED`; behavior `UNVERIFIED` |

## Brainstorming

- Type-wide PlayerMob CEM avatar pack as a zero-Java compatibility witness.
- Local debug `set_avatar` as a one-entity proof before any assignment architecture.
- Read-only `spm` Lua global through the official Figura addon entrypoint.
- Nested `spm.host` and optional `spm.scavenger` views to separate base-SPM state from addon state.
- Avatar expressions driven by typed activity snapshots: combat, shelter, mining, trading, commute,
  village work, rest, and unknown/stale.
- Optional local-only assignment mode for single-player creators.
- Shared server assignment by identifier only, with Figura/local systems retaining avatar-byte
  ownership.
- Validation-side sample avatar and instrumentation; never ship the witness avatar as production
  identity data.

Serious candidates are analyzed in the stable topics below.

## Topic Index

1. Native avatar attachment and renderer coexistence
2. Lua entity compatibility and official extension seam
3. Client/server authority and read-only state exposure
4. Per-PlayerMob assignment and multiplayer consistency
5. Lifecycle, retention, performance, and security
6. Packaging, dependency, update, and license boundaries
7. Behavioral prediction and validation

## Topic: Native Avatar Attachment and Renderer Coexistence

Status: `PROPOSED / RUNTIME UNVERIFIED`

Goal: prove that Figura can render and tick an avatar on the existing SPM renderer without adding a
second renderer or changing AI.

Current implementation:

```text
PlayerMobEntity
  -> PlayerMobRenderer
  -> HumanoidMobRenderer<PlayerMobEntity, PlayerModel<PlayerMobEntity>>
  -> LivingEntityRenderer
  -> Figura LivingEntityRendererMixin
  -> AvatarManager.getAvatar(entity)
```

Candidate designs:

### Option A — type-wide native CEM witness (recommended first)

- Supply a validation-only compressed avatar at
  `assets/figura/cem/playermob/player_mob.nbt`.
- Figura owns lookup, load, tick, render, and cleanup.
- Every PlayerMob uses the same test avatar.

Trade-off: it proves the generic path with no integration code, but says nothing about per-mob
identity.

### Option B — debug `set_avatar` witness

- Enable Figura debug command registration.
- Load a known source avatar and assign it to one loaded PlayerMob UUID.

Trade-off: it proves per-entity attachment quickly, but is client-local and transient. It must never
be mistaken for production assignment.

### Option C — custom renderer/mixin

- Add an SPM-specific render injection or replacement.

Trade-off: gives maximum control but duplicates Figura's existing generic hook, increases mixin
conflict risk, and has no evidenced need. Rejected unless the native witness finds a concrete gap
that cannot be solved through Figura's public seams.

Strongest objection: Figura's generic hook may execute, yet vanilla-model editing, SPM slim/wide
model swapping, armor, held items, invisibility, glow, nameplate, or shader layers may still render
incorrectly.

Current preferred design: Option A plus Option B in one approved validation session before any Java
compatibility code.

Validation must happen:

- Avatar model visibly renders on a PlayerMob.
- Lua tick and render events advance.
- movement, crouch, sprint, sleeping, held item, armor, fire, and death/unload transitions are
  observed.

Validation must not happen:

- duplicate vanilla body/layers when the avatar requests replacement;
- avatar rendering on unrelated entity types;
- stale avatar remaining after entity removal/world change;
- any server AI change.

## Topic: Lua Entity Compatibility and Official Extension Seam

Status: `PROPOSED`

Goal: make ordinary entity-compatible Figura scripts work and expose SPM-specific read-only state
without modifying Figura's Lua runtime internals.

Evidence:

- `player == user` for entity avatars.
- A PlayerMob receives `LivingEntityAPI`, not `PlayerAPI`.
- Figura's official `figura_api` entrypoint builds a per-avatar global.

Candidate designs:

### Option A — official `figura_api` global (recommended)

Register a global named `spm`. Build it against the `Avatar.owner` identity and return a read-only
view. Do not mix into `EntityAPI`, `LivingEntityAPI`, or `FiguraLuaRuntime`.

Conceptual shape:

```lua
spm:isPlayerMob()

spm.host:getFightFlight()
spm.host:getFriendliness()
spm.host:getReactionSpeed()
spm.host:getFeelingTowardViewer()

spm.scavenger:getPersonality()
spm.scavenger:getPrimaryActivity()
spm.scavenger:getActivityFlags()
spm.scavenger:getShelterPhase()
spm.scavenger:isTrading()
spm.scavenger:isMining()
spm.scavenger:isRequiredTradeCommuting()
spm.scavenger:getFreshness()
```

The exact method surface remains `OPEN`; the nested ownership split is preferred so base SPM facts
do not pretend Scavenger is always installed.

### Option B — extend/wrap Figura entity API

Trade-off: makes calls look native to `player`, but couples to Figura internals, risks name
collisions, and obscures whether a method is generic entity truth or optional addon truth. Rejected.

### Option C — avatar scripts parse `getObjectivesReadout()`

Trade-off: needs no new packet but parses a localized/human presentation string, cannot reliably
separate simultaneous goals, and cannot prove an exact required-trade binding. Allowed only as a
disposable prototype; rejected for production.

Compatibility rule:

- Generic `LivingEntityAPI` scripts are the Phase-1 target.
- Player-only methods are not emulated by lying about entity type.
- Compatibility documentation must identify supported generic methods and show feature detection
  for player-only scripts.
- A missing/stale optional state returns `UNKNOWN`/`nil`, not a fabricated false.

## Topic: Client/Server Authority and Read-Only State Exposure

Status: `PROPOSED`; privacy field set `OPEN`

Goal: expose enough factual SPM/Scavenger state for animation without granting Figura or the client
server authority.

Canonical ownership:

```text
SPM/Scavenger server truth
  -> passive, non-creating presentation projection
  -> compact immutable snapshot
  -> clients already tracking the entity
  -> bounded client cache
  -> read-only Figura API
```

Never:

```text
Lua query -> C2S request -> Goal/admission/mutation
client cache -> server permission
objective text -> inferred mandatory authority
VillageIntentRegistry.current() -> "currently commuting"
```

Two-source rule:

1. **Client-local host facts:** vanilla pose/movement/items plus SPM's already-synced fight/flight,
   friendliness, reaction speed, and the single feeling toward the current viewer.
2. **Server snapshot:** Scavenger-only semantic facts that do not already exist on the client.

Do not copy already-synced host facts into the new packet merely for convenience.

Conceptual Scavenger snapshot:

```text
SpmCosmeticStateSnapshot
  entityId + UUID
  revision
  observedAtTick
  availability/freshness
  optional personality view
  primary semantic activity
  bounded activity flags
  optional target-present / target UUID if privacy allows
  shelter phase
  trade/mining/commute facts
```

Activity must be a typed semantic projection, not Goal class names. Required-trade commute must be
derived from actual running activity plus the exact execution binding; non-authoritative intent
existence alone is insufficient.

Alternatives:

- Reuse full Opinion inspector payload: lower implementation effort, but too large, manual,
  permission-gated, range-limited, and designed for diagnosis. Rejected.
- Add more `SynchedEntityData` fields to PlayerMob: simple entity tracking, but forces Scavenger
  concepts into the host entity and consumes a host ABI/save-adjacent seam. Not preferred.
- Compact change-driven payload: explicit ownership and extensibility, but requires lifecycle and
  cadence design. Recommended.

Open privacy decision: expose only visually meaningful, already-observable categories by default.
Raw relationship ledgers, complete target histories, internal utility scores, remembered villages,
inventory contents, and authorization evidence remain out of the Lua surface unless separately
justified.

The first API slice must use only vanilla client state and SPM fields already synchronized to the
viewer. Server-only Scavenger cognition is a later opt-in slice after each field has a visibility
and privacy disposition. A server snapshot, if authorized, is delivered only to clients already
tracking the entity and cannot be queried for arbitrary UUIDs.

## Topic: Per-PlayerMob Assignment and Multiplayer Consistency

Status: `OPEN / PRODUCT DECISION`

Goal: decide what "per-PlayerMob avatar" means before building persistence or networking.

Confirmed boundary: Figura's debug `set_avatar` assigns an avatar to one loaded entity on one
client. It does not persist or synchronize that assignment.

### Option A — type-wide CEM only

- All PlayerMobs use one installed CEM avatar per client/resource pack.
- No server state, no network, no per-mob identity.
- Lowest risk and may satisfy many modpack use cases.

### Option B — local per-UUID assignment

- Client configuration maps PlayerMob UUID to a locally installed avatar identifier.
- Good for single-player/screenshots and needs no server.
- Different viewers can see different avatars; identity disappears if local assets/config are absent.

### Option C — shared server assignment by identifier (recommended if shared identity is required)

- Server stores only a bounded assignment fact such as `(PlayerMob UUID -> avatar reference)`.
- Tracking clients receive the reference and resolve it through a separately defined trusted source.
- Avatar bytes, Lua execution, permissions, and rendering remain Figura/client concerns.
- Requires missing-asset behavior, access control, persistence, migration, viewer consistency, and
  versioning.

### Option D — depend on Figurity

- Existing mod supplies entity-NBT assignment.
- It is server-required, all-rights-reserved, and has no published source URL in its project
  metadata. The inspected `2.0.1+1.21.1` Fabric artifact (SHA-256
  `F4D9A532ACD3EB272F35A693297DBFCDBFAF13C5F3F8933F7B80B775B3337FAF`) persists a model path,
  synchronizes it, injects broader Player-like behavior, and includes operator commands/action
  execution. Runtime compatibility, lifecycle semantics, and redistribution remain `UNVERIFIED`.
- Keep as an evaluated alternative, not an assumed foundation.

### Option E — direct `AvatarManager.loadEntityAvatar` production adapter

- Fast, but depends on concrete internals, remains client-local/transient, and lacks public removal
  or assignment-provider seams.
- Rejected as the persistence architecture; may be used in a version-pinned disposable witness.

Required product answers before Phase 2 implementation:

1. Is type-wide appearance sufficient, or is durable per-mob identity required?
2. Must all multiplayer viewers see the same avatar?
3. Who may assign/change an avatar: local viewer, server operator, mob owner, datapack, or config?
4. Where do clients obtain the referenced avatar, and what is the trust/permission model?
5. Is assignment part of Scavenger, base SPM compatibility, or a separate optional compat mod?

## Topic: Lifecycle, Retention, Performance, and Security

Status: `PROPOSED`; exact budgets `OPEN`

RET-1 design for any Scavenger cosmetic cache:

| Concern | Required contract |
| --- | --- |
| Key | `(client entityId, entity UUID)` to resist entity-ID reuse |
| Bound | Hard maximum plus active tracking membership; exact maximum must be justified before implementation |
| Create | Only from a valid snapshot for a currently tracked PlayerMob |
| Refresh | Change-driven, with a bounded heartbeat; never per-render-frame or per-Lua-call networking |
| Stale | Return `UNKNOWN`; do not keep asserting old activity |
| Untrack/removal | Remove cache entry |
| World/dimension change | Clear affected entries |
| Disconnect/server stop | Clear all client/server transport state |
| Death/permanent removal | Remove assignment/runtime state according to its owner; persistent assignment policy remains an explicit product choice |

Preferred client storage is a client-only latest-snapshot attachment on the tracked entity, which
naturally follows entity lifetime and avoids a second UUID registry. If mappings/API constraints
force a separate cache, the table above becomes mandatory and the hard cap/physical prune must have
production call sites. Neither form may retain `Avatar`, `Entity`, or `Level` references beyond the
tracked entity's lifetime.

Figura itself stores CEM avatars in a strong `Map<Entity, Avatar>`, prunes `isRemoved()` entities,
and clears all CEM avatars on resource/world clearing. There is no explicit numeric bound.
`UNVERIFIED`: abnormal removal and GPU cleanup behavior for the per-tick prune path.

Performance risks:

- Figura ticks one Lua runtime per loaded CEM avatar.
- A server snapshot producer that scans GoalSelector separately for every field would duplicate work.
- Per-frame Lua calls must be O(1) local cache reads.
- Many tracked PlayerMobs with complex avatars can be client CPU/GPU bound even if the bridge is
  cheap.

Required performance proof class: a predefined scenario and budget comparing Figura absent,
Figura present/no avatar, type-wide avatar, and read-only state API across increasing tracked
PlayerMob counts. Record frame time, Lua/avatar tick time where Figura profiling exposes it, packet
rate/bytes, heap/cache cardinality, and GC. No performance claim is currently made.

Security/authority invariant:

- Lua receives immutable values only.
- No API method gives items, changes targets, alters GoalSelector, teleports, mutates Opinion,
  changes assignments on the server, or dispatches commands.
- No C2S "action" channel is part of this RFC.
- Existing Figura permissions still govern Figura effects; this bridge does not elevate them.
- Figura gives CEM mob avatars broad permissions; therefore a server must not silently push or
  auto-load arbitrary avatar/Lua blobs. Assignment requires an explicit trust/consent policy and
  the bridge transports no script bytes in its first generation.
- Malformed scripts may fail their avatar but cannot change server state.

## Topic: Packaging, Dependency, Update, and License Boundaries

Status: `OPEN / PROPOSED`

### Implementation-home alternatives

#### Option A — client-only code inside Scavenger

Pros: reuses Scavenger passive truth and existing build/networking. Cons: base SPM users cannot use
the API without installing Scavenger; direct Figura types must remain isolated from dedicated-server
class loading.

#### Option B — separate `spm_figura_compat` mod (preferred architecture)

Pros: clean optional dependency, base-SPM host view works without Scavenger, Scavenger becomes an
optional provider, dedicated-server and client boundaries are explicit. Cons: new artifact,
version matrix, and provider API.

#### Option C — upstream contribution to Figura or SPM

Pros: widest reuse. Cons: external acceptance/version cadence and may not belong in either core.

Recommendation: do not decide until the native Phase-0 witness proves a code gap exists. If only a
read-only Lua global is needed, prefer a separate client compatibility artifact with an optional
Scavenger provider. Do not build a new project merely to reproduce behavior Figura already has.

Dependency rules:

- Figura remains an unbundled optional client dependency.
- No Figura class may appear in common/dedicated-server load paths.
- Prefer the official `figura_api` entrypoint over mixins/reflection into Figura Lua internals.
- Pin exact tested versions. Unsupported versions fail closed to vanilla SPM rendering and no
  `spm` global rather than crash.
- Package audit must show zero packaged upstream Figura classes.

License rules:

- Figura is PolyForm Noncommercial 1.0.0; do not copy or redistribute its source/classes in this
  project.
- Direct API compilation against an unbundled dependency still requires a distribution/use review
  for any commercial context.
- Figurity is all-rights-reserved and source-unpublished in project metadata; do not copy or infer
  its implementation.

## Topic: Behavioral Prediction and Validation

Status: `BEHAVIORALLY PLAUSIBLE / RUNTIME UNVERIFIED`

This feature is presentation-only, but MAIBS-1 still requires the complete visible feedback loop.

| Layer | Result |
| --- | --- |
| Intended behavior | A PlayerMob wears a Figura avatar whose visuals react to factual entity/AI state |
| Implemented mechanism today | Figura generic entity/CEM render and Lua paths; no SPM-specific integration yet |
| Predicted behavior | A type-wide avatar can render and generic movement/pose/item Lua can animate; Player-only API calls are absent; Scavenger semantics need a typed snapshot |
| Failure/weirdness | layer duplication, local-only identity disagreement, stale animation, script error from PlayerAPI assumptions, client cost scaling |
| Confidence | Static mechanism `CONFIRMED`; in-world result `UNVERIFIED` |

### Temporal simulation

- `T0 tracking/spawn`: Figura resolves the PlayerMob entity type or receives a local assignment and
  creates an entity Avatar.
- `T+1..10`: Lua receives a `LivingEntityAPI` as both `user` and `player`; generic movement, pose,
  held-item, fire, and name/UUID reads update animation.
- `T+20..60`: if a future semantic snapshot exists, the client receives change-driven activity
  state; Lua reads the local cache without a network request.
- `T+200`: combat/shelter/trade/mining transitions replace the semantic snapshot; visual changes
  follow factual state. Missing updates age to `UNKNOWN` rather than freezing a false activity.
- `T+1200`: repeated state changes must not grow caches, retain removed entities, spam packets, or
  let visual state affect scheduler ownership.
- `untrack/death/world change`: entity avatar and bridge cache release; re-tracking rebuilds from
  current truth rather than stale client state.

### Goal/authority interaction

| System | Owns movement/action? | Bridge interaction | Expected result |
| --- | --- | --- | --- |
| SPM GoalSelector | Yes | Read-only observation or existing synced objective/traits | behavior unchanged |
| Scavenger ActivityAuthority | Yes | Supplies semantic facts only | permission unchanged |
| Figura avatar/Lua | No server authority | Reads client entity + snapshot cache | visuals only |
| Combat/safety/player order | Higher-priority production behavior | May change snapshot | animation follows; never blocks/preempts |

### Predicted weird behaviors

1. **Player-only script crash/error** — an avatar calls a `PlayerAPI` method on a PlayerMob's
   `LivingEntityAPI`. Classification: `ACCEPTABLE_STEPPING_STONE` only with documented feature
   detection and a validation avatar; otherwise compatibility defect.
2. **Two viewers see different per-mob avatars** — local assignment is mistaken for shared
   identity. Classification: `ARCHITECTURE_DEFECT` if shared identity is selected; expected behavior
   under explicitly local-only mode.
3. **Stale "mining" animation after interruption** — snapshot delivery or cache invalidation
   fails. Classification: `ARCHITECTURE_DEFECT`; stale must become `UNKNOWN`.
4. **Double armor/held item or invisible body pieces** — Figura and SPM layers disagree.
   Classification: `RUNTIME_QUESTION`; native witness must inspect it.
5. **Many PlayerMobs cause client frame spikes** — Lua/avatar complexity dominates even though
   bridge reads are O(1). Classification: `RUNTIME_QUESTION`; profile against a predeclared budget.

### Falsifying runtime experiment

One controlled client session with exact Figura/SPM/Scavenger hashes:

1. Spawn one PlayerMob and attach a validation avatar through native CEM and debug `set_avatar`.
2. Exercise idle, walk, sprint, crouch, sleep, held item, armor, fire, combat, death/untrack, and
   reload.
3. The Lua script logs wrapper type and bounded state transitions.
4. Observe a second PlayerMob to prove type-wide versus per-entity scope.
5. With multiplayer testing later, compare two viewers before claiming shared assignment.

Must happen: native entity avatar rendering and generic Lua state update without AI changes.

Must not happen: any Lua action changes server AI/inventory/world state, player-only type is
fabricated, or local assignment is reported as persistent/shared.

## Decision Registry and Locked Decisions

| ID | Decision | Status | Rationale / unlock condition |
| --- | --- | --- | --- |
| D-FIG-001 | Figura is presentation-only; Lua cannot mint server authority or mutate SPM/Scavenger behavior | `LOCKED` | Explicit user constraint, code ownership, and independent review |
| D-FIG-002 | Run a native no-code CEM/`set_avatar` witness before writing renderer integration | `LOCKED` | Source shows existing path; user direction and two independent reviews support testing it before code |
| D-FIG-003 | Expose read-only Lua state through official `figura_api`, not Lua-runtime/entity-API mixins | `CONSENSUS` | Exact 0.1.6 public entrypoint exists; compile/runtime adapter proof remains |
| D-FIG-004 | Reuse existing client-synced host facts; add only a compact server snapshot for Scavenger-only semantic truth | `PROPOSED` | Avoid duplicate sync and presentation-string parsing |
| D-FIG-005 | Missing/stale optional state is `UNKNOWN`, never fabricated false or permission | `PROPOSED` | Evidence-bounded, fail-safe semantics |
| D-FIG-006 | Persistent per-mob assignment mode and multiplayer consistency | `OPEN PRODUCT DECISION` | Figura native command does not solve persistence/distribution |
| D-FIG-007 | Compatibility implementation home: Scavenger client code vs separate compat mod | `OPEN PRODUCT DECISION` | Decide after native witness and desired audience |
| D-FIG-008 | Snapshot delivery is change-driven with bounded heartbeat and tracked-entity cache | `PROPOSED` | Avoid per-frame/per-Lua-call network and satisfy RET-1 |
| D-FIG-009 | Exact Figura version adapter, client-only loading, fail closed, no bundled Figura classes | `PROPOSED` | Internal drift and dedicated-server safety |
| D-FIG-010 | Lua API separates base host facts from optional Scavenger facts | `PROPOSED` | Prevents a universal god-record and optional-mod ambiguity |
| D-FIG-011 | First implementation does not transport or reconstruct avatar blobs | `PROPOSED` | Figura remains avatar/runtime owner; assignment source is unresolved |
| D-FIG-012 | Performance remains `UNVERIFIED` until profiled with increasing tracked PlayerMobs | `LOCKED evidence rule` | AV-1 proof class |

## Feature Parity

| Feature | Native Figura evidence | Planned scope | Proof required | Status |
| --- | --- | --- | --- | --- |
| PlayerMob avatar lookup | Generic CEM/entity path | Phase 0 | runtime render witness | `SOURCE_CONFIRMED / RUNTIME_UNVERIFIED` |
| Custom model/replacement | Avatar renderer exists | Phase 1 | visible model/layer witness | `UNVERIFIED` |
| Lua tick/render | Entity Avatar owns Lua runtime | Phase 1 | transition log + animation | `SOURCE_CONFIRMED / RUNTIME_UNVERIFIED` |
| Generic entity methods | `LivingEntityAPI` wrapper | Phase 1 | representative Lua contract test | `SOURCE_CONFIRMED` |
| Player-only methods | Only literal Player gets `PlayerAPI` | compatibility documentation, no fake parity | negative test | `ADAPTED` |
| Held items/armor | SPM and Figura both touch layers | Phase 1 | visual runtime matrix | `UNVERIFIED` |
| Particles/sounds | Figura client effects | Phase 1 | bounded effect witness | `UNVERIFIED` |
| Type-wide avatar | native CEM by entity type | Phase 0/1 | resource witness | `SOURCE_CONFIRMED` |
| Local per-entity assignment | debug `set_avatar` | prototype only | local lifecycle witness | `SOURCE_CONFIRMED / TRANSIENT` |
| Persistent per-mob assignment | no native public service found | Phase 2 optional | product decision + runtime | `OPEN` |
| Multiplayer-consistent assignment | no native entity assignment sync found | Phase 2 optional | two-client witness | `OPEN` |
| SPM traits/feeling | already synced | Phase 3 host API | deterministic API tests + runtime | `CODE_CONFIRMED` |
| Typed Scavenger activity | server-only passive reads | Phase 3 snapshot | semantic transition witness | `OPEN` |
| Personality/Opinion | server registry | Phase 3 optional allowlist | privacy decision + snapshot test | `OPEN` |
| Shelter/trade/mining/commute | server semantic truth | Phase 3 | exact producer/binding tests | `OPEN` |
| Lua-to-AI control | deliberately prohibited | never | structural/network negative tests | `LOCKED OUT` |

## Scenario Parity

| ID | Scenario | Must happen | Must not happen | Proof class |
| --- | --- | --- | --- | --- |
| FIG-S0 | Figura absent | vanilla SPM/Scavenger load/render remains | client Figura class loads on server/common path | build + dedicated-server runtime |
| FIG-S1 | Figura present, no avatar | vanilla PlayerMob render remains | crash or hidden body | runtime |
| FIG-S2 | type-wide CEM avatar | one avatar renders on every PlayerMob of the type | unrelated entity gets avatar | runtime |
| FIG-S3 | debug per-entity assignment | target mob alone changes locally | claim of persistence/shared identity | runtime |
| FIG-S4 | generic Lua | pose/move/item/fire/name/UUID reads work | fake `PlayerAPI` identity | runtime + scripted assertions |
| FIG-S5 | player-only script call | predictable nil/error/feature-detection path | server crash or fabricated method | runtime |
| FIG-S6 | armor/held items/slim-wide | intended layer policy is visible | double/misaligned/invisible layers | visual runtime |
| FIG-S7 | activity transitions | typed state changes and ages to unknown | stale activity becomes authority | runtime/log |
| FIG-S8 | combat interruption | visual state follows combat then resume | AI scheduling changes | runtime/log |
| FIG-S9 | unload/reload/death | caches/avatars release and reacquire current state | stale entity-id state transfers | runtime + heap/cardinality |
| FIG-S10 | two viewers | selected assignment semantics are identical or explicitly local | accidental disagreement under shared mode | two-client runtime |
| FIG-S11 | malformed avatar/script | Figura fails avatar safely | server state changes | runtime |
| FIG-S12 | many tracked mobs | declared frame/packet/cache budget holds | unbounded cache or packet-per-frame | profiler/runtime |

## Tasks

### FIG-0 — Decision and baseline lock

Dependencies: this RFC.

- Confirm/correct Figura release metadata and hashes.
- Lock the native-first sequence and preserve the strict presentation-only authority boundary.
- Record assignment semantics, implementation home, privacy, and performance as later explicit
  decisions rather than silently selecting them.

Exit: baseline and native-witness scope are lock-ready; assignment/API implementation is not.

Status: `COMPLETE` for planning evidence.

### FIG-1 — Native compatibility witness

Dependencies: FIG-0 baseline; separate runtime approval.

- Build or obtain a validation-only CEM NBT for `playermob:player_mob`.
- Use native CEM and debug `set_avatar` without SPM renderer changes.
- Run FIG-S1 through FIG-S6 plus removal/reload.
- Record exact artifacts, config, logs, screenshots/video, and wrapper type.

Stopping rule: if native attachment fails, identify the exact Figura/SPM seam before proposing any
renderer code. If it passes, no custom renderer task is created.

### FIG-2 — Optional compatibility shell

Dependencies: FIG-1; D-FIG-007 implementation home locked; privacy baseline defined.

- Establish client-only optional dependency and official `figura_api` entrypoint.
- Prove Figura absent and dedicated-server-safe class loading.
- Expose only `isPlayerMob` initially.
- Package audit: zero bundled Figura classes.

### FIG-3 — Base SPM read-only Lua view

Dependencies: FIG-2.

- Read vanilla client entity facts and existing SPM synced disposition.
- Expose viewer-selected feeling, not the full ledger.
- Add generic-vs-player-only compatibility documentation/tests.
- No new server packet unless evidence shows a required host fact is absent.

### FIG-4 — Per-PlayerMob assignment, if selected

Dependencies: D-FIG-006 locked and FIG-1.

- Implement only the selected local or shared assignment contract.
- Define assignment authority, identifier resolution, persistence, migration, missing assets,
  multiplayer consistency, and RET-1 lifecycle.
- Do not transmit avatar NBT/Lua through Scavenger in the first generation.

If type-wide CEM is selected, this task closes as `NOT REQUIRED`.

### FIG-5 — Scavenger cosmetic truth projection

Dependencies: FIG-2; privacy decision; at least two evidenced consumers before extracting a new
universal provider.

- Define a small passive semantic presentation projection.
- Reuse existing non-creating truth APIs.
- Have General Debug consume shared fields where that genuinely removes duplicate interpretation;
  do not force unrelated diagnostics into the cosmetic record.
- Add change detection and a bounded server producer.
- Do not call Goal methods, admission/ranking, memory-creating APIs, or mutation paths.

### FIG-6 — Tracking transport and client cache

Dependencies: FIG-5.

- Send snapshots only to clients tracking the PlayerMob.
- Implement revision/freshness/unknown semantics.
- Key cache by entity ID + UUID; enforce hard cap and lifecycle cleanup.
- Expose O(1) local reads to Lua.
- Add packet/cardinality instrumentation in validation only.

### FIG-7 — AI-reactive API surface

Dependencies: FIG-3 and FIG-6.

- Expose locked personality/activity/shelter/trade/mining/commute fields.
- Use semantic enums/flags, not Goal class names or objective-string parsing.
- Preserve read-only and optional-mod behavior.
- Add component/version documentation for avatar authors.

### FIG-8 — Integration, multiplayer, and performance closure

Dependencies: applicable FIG-1 through FIG-7 tasks; separate runtime approval.

- Run full scenario matrix with exact artifacts.
- Use two clients if shared assignment is claimed.
- Profile increasing PlayerMob counts with a predeclared budget.
- Verify no server authority/network action surface and no retained cache growth.

## Gates

| Gate | Pass condition | Current state |
| --- | --- | --- |
| FIG-G0 Baseline | exact Figura/SPM/Scavenger versions and hashes pinned | `PASS` |
| FIG-G1 Native feasibility | actual PlayerMob avatar + Lua runtime observed | `UNVERIFIED` |
| FIG-G2 Ownership | no client/Lua path can mutate or authorize server behavior | design `PASS`; implementation pending |
| FIG-G3 Assignment | local/shared semantics, authority, persistence, distribution locked | `BLOCKED — PRODUCT DECISION` |
| FIG-G4 API boundary | official `figura_api`; no Figura runtime mixin unless evidenced | `PROPOSED` |
| FIG-G5 Side safety | Figura absent and dedicated server load safely | `UNVERIFIED` |
| FIG-G6 RET-1 | explicit key, hard bound, production eviction, all lifecycle paths | `OPEN` |
| FIG-G7 Compatibility | generic vs PlayerAPI matrix and layer behavior proven | `UNVERIFIED` |
| FIG-G8 Multiplayer | two-viewer evidence for any shared assignment claim | `UNVERIFIED` |
| FIG-G9 Performance | predefined frame/packet/cache budgets profiled | `UNVERIFIED` |
| FIG-G10 Packaging/license | no bundled Figura classes; dependency/license review complete | `OPEN` |
| MRFC-1 | later agent can find decisions, tasks, evidence, and frontier | `PASS` |

## Deferred / Unverified

- Exact native runtime compatibility of Figura 0.1.6 with SPM 0.96 and current Scavenger.
- Figura compatibility with SPM armor, held-item, slim/wide, nameplate, invisibility, glow, shader,
  Sodium/Iris, and other render-layer combinations.
- Persistent per-mob assignment and multiplayer distribution.
- Avatar source/reference format and trust policy.
- Scavenger personality/privacy allowlist.
- Exact cosmetic snapshot cadence, TTL, hard cache bound, and packet budget.
- Client performance with multiple scripted PlayerMobs.
- Figura CEM abnormal-removal/GPU cleanup behavior.
- Any future allowlisted Lua-to-server social request. This is explicitly outside the current RFC
  and would require a new authority/security design; it is not a deferred implementation task.

## Nearest Frontier

The next defensible action is **FIG-1 Native Compatibility Witness**, under separate Minecraft
runtime-launch approval. It can run without deciding persistent assignment or implementation home
because it uses Figura's native CEM/debug path and changes no production renderer.

After FIG-1 evidence, resolve D-FIG-007 before an API shell and resolve D-FIG-006 only before any
persistent per-mob assignment task. Do not write renderer integration before the native witness.

## Contribution Archive and Change Log

### Contribution — User / Product Owner

Agent: User
Date/Session: 2026-08-29
Contribution type: DESIGN / PRODUCT DIRECTION

Reviewed: Figura entity/CEM path, SPM renderer fit, Lua wrapper behavior, optional compatibility
architecture, and proposed phases.

Agreement: keep AI/world authority in SPM/Scavenger and use Figura for visuals/client effects.

Concern preserved: player-specific Figura scripts may not work because PlayerMob is not a literal
Minecraft `Player`.

Recommendation: avatar compatibility, per-mob assignment, then read-only SPM state.

RFC fields updated: scope, ownership invariant, feature candidates, phase sequence.

### Contribution — Figura_Source_Audit

Agent: Figura_Source_Audit
Date/Session: 2026-08-29
Contribution type: RESEARCH / OBJECTION

Reviewed: exact Figura 0.1.6 tag and Fabric artifact.

Agreement: generic renderer, entity Avatar, `LivingEntityAPI`, and official Lua addon seam are real.

Concern: per-entity `set_avatar` is debug-only, local, transient, and not assignment authority.

Alternative: type-wide CEM first; separately design assignment only if required.

Recommendation: no-code witness -> official read-only Lua API -> assignment product decision.

RFC fields updated: baseline, native attachment, Lua, assignment, lifecycle, gates.

### Contribution — SPM_State_Audit

Agent: SPM_State_Audit
Date/Session: 2026-08-29
Contribution type: RESEARCH / DESIGN

Reviewed: SPM 0.96 renderer/synced fields and Scavenger truth/network surfaces.

Agreement: vanilla/entity and SPM disposition facts already exist client-side.

Concern: typed Scavenger activity, target, shelter, trade, mining, commute, and Opinion personality
remain server-side; objective text is not a semantic contract.

Alternative: compact change-driven snapshot, not Opinion-inspector polling or objective parsing.

Recommendation: split client-local host view from a small server-owned Scavenger snapshot; stale is
`UNKNOWN`.

RFC fields updated: state ownership, transport, cache, API, tasks, scenario parity.

### Contribution — Codex / Primary RFC Author

Agent: Codex
Date/Session: 2026-08-29
Contribution type: RESEARCH / DESIGN / SYNTHESIS

Reviewed: user proposal, exact Figura source/artifact, SPM 0.96 source, current Scavenger client and
server truth surfaces, Figurity alternative, repository governance, AV-1, SPM-UA-1, and MAIBS-1.

Agreement: the integration is feasible enough to justify a native runtime witness and a read-only
Lua API plan.

Concerns: release-date mismatch; runtime renderer/layer proof missing; assignment/distribution
unsolved; server-only AI facts need a bounded semantic snapshot; performance and license claims
remain unverified.

Recommendation: preserve strict presentation ownership, prove native compatibility before code,
then implement only the selected assignment/API scope.

RFC fields updated: all canonical sections and nearest frontier.

### Contribution — Figura_Architecture_Reviewer

Agent: Figura_Architecture_Reviewer
Date/Session: 2026-08-29
Contribution type: REVIEW / OBJECTION / DESIGN

Reviewed: exact Figura/SPM render and Lua seams, multiplayer identity, Figurity, permission/trust,
RET-1, performance, and runtime falsifiers.

Agreement: native CEM first and official `figura_api` are the narrowest evidence-backed path.

Concerns: local assignment is not shared identity; server-only cognition can become an ESP/privacy
channel; CEM scripts have a meaningful trust surface; renderer layer/model-state coexistence and
client scaling remain runtime-unverified.

Alternative: use entity-attached client snapshot state; use Figurity only after an explicit
security/license/product decision; otherwise build a narrow assignment identifier protocol only if
shared identity is required.

Recommendation: first expose already-synced host facts, keep assignment and state APIs independent,
and require a native render witness before code.

RFC fields updated: assignment, privacy, retention, security, performance, and validation.

### Change Log

| Date | Change | Author |
| --- | --- | --- |
| 2026-08-29 | Created canonical Figura × SPM integration RFC from pinned cross-mod evidence | Codex + named contributors |
