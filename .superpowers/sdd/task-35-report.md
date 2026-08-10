# Task 35 Report — RET-GAO-1 (OpinionExperienceRegistry lifetime)

**Status:** `DONE` — RET-1 outer owner **PASS** (static/unit); runtime `UNVERIFIED`

**Scope:** RET-GAO-1 only. No GAO-5B.

## Delivered

### Architecture
- Split registry into **live contexts** (`LIVE_CONTEXTS`) + **frozen snapshots** (`FrozenContextStore`).
- `MobExperienceSnapshot` — minimal unload-surviving state (affect + `OpinionMemory` + `PlaceOpinionMemory`).
- `MobExperienceContext.prepareForUnloadPark()` — abandons suspended/live episodes, clears tombstones,
  director intents, REST already closed upstream.
- `ActivityEpisode.abandonForUnload()` — close without resurrection path.
- `OpinionExperienceRegistry.parkOnUnload` / `resumeOnLoad` — production lifecycle.
- `SpmScavenger` — `ENTITY_UNLOAD` → `parkOnUnload`; `ENTITY_LOAD` → `resumeOnLoad` (replaces freeze-only).

### Bounds (`CONFIRMED` — constants in `FrozenContextStore`)
| Bound | Value | Eviction |
| --- | --- | --- |
| Max frozen snapshots | 128 | LRU eldest |
| Frozen TTL | 24_000 ticks (~20 min) | `evictExpiredFrozen` on park + explicit API |
| Live contexts | Loaded mobs only | `parkOnUnload` removes live entry |

### Stepping-stone honesty
- No disk persistence. Evicted frozen snapshots **lose** session learned state intentionally.
- `PlaceOpinionMemory` remains session-memory MVP (same as Task 34).

## Field classification (implemented)

| Field | Class | Park behavior |
| --- | --- | --- |
| `restClaim` | Ephemeral | Cleared via `invalidateOnUnload` before park |
| `discretionaryDirector` | Ephemeral | `clearForUnload()` |
| `episodes` / `closedEpisodeIds` | Ephemeral | Abandon + clear |
| `executionFailureTotals` | Ephemeral | Clear |
| `affectiveState` | Snapshot | Restored on reload |
| `opinionMemory` | Snapshot | Restored on reload |
| `placeOpinionMemory` | Snapshot (session) | Restored on reload; lost if LRU/TTL evicts |
| `frozen` flag | N/A | Fresh live context on rehydrate |

## Verification (`CONFIRMED`)

```text
Working directory: Projects/SPMScavenger-1.21.1-Fabric
Command: .\gradlew.bat clean build
Result: BUILD SUCCESSFUL — 553 tests, 0 failures
```

New: `OpinionExperienceRegistryRetentionTest` (5 tests) — bounded unique unload cycles, opinion
survival, suspended episode abandon, LRU cap, TTL eviction.

## RET-1 / MAIBS re-pass (static)

| Check | Task 34 | Task 35 |
| --- | --- | --- |
| Outer registry bound | **FAIL** | **PASS** — live + frozen ≤ loaded + 128 |
| Unload eviction path | freeze only | `parkOnUnload` production wired |
| Abandoned suspended episodes | survive until stop | **abandonForUnload** on park |
| Learned opinion on reload | would survive freeze | **PASS** — snapshot round-trip |
| Death while frozen | untested | partial reset on snapshot re-park |
| Runtime chunk cross | `UNVERIFIED` | `UNVERIFIED` — needs launch |

**Verdict:** `RET-1 PASS` (unit/static). **MAIBS:** upgrade outer retention from **FAIL** to
**CONDITIONAL PASS** — unload/reload semantics addressed in code; multi-minute physical unload
crossing still `UNVERIFIED` until runtime.

## Concerns / UNVERIFIED

| Claim | Status |
| --- | --- |
| PlayerMob chunk unload → reload opinion continuity in-world | `UNVERIFIED` — no launch |
| LRU/TTL tuning under real session mob churn | `UNVERIFIED` |
| Death while unloaded + later respawn same UUID | `INFERRED` — snapshot death reset path exists |
| Disk persistence across server restart | **Deferred** — not in Task 35 |

## Frontier

1. **MAIBS runtime re-pass** — optional with RT-MI-TS1 / RT-GAO-1 launch matrix.
2. **GAO-5B** — heading consumer (authorized after this task).
3. Full persistence — future task if session loss on LRU eviction is unacceptable.
