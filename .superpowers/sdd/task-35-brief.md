# Task 35 Brief — RET-GAO-1 (OpinionExperienceRegistry lifetime)

**Gate:** RET-GAO-1 / RET-1 outer owner  
**Status:** Implementation authorized  
**Out of scope:** GAO-5B, runtime launch, disk persistence

## Problem

`OpinionExperienceRegistry` retained every `MobExperienceContext` for the server session on chunk
unload (`freeze()` only). Suspended episodes could keep abandoned contexts alive until server stop.
No production eviction path; `remove(UUID)` unwired.

## Field classification (`MobExperienceContext`)

| State | Class | On temporary unload |
| --- | --- | --- |
| REST claim | Ephemeral | Discard (close with learning first via `RestSessionCoordinator`) |
| Pending/running discretionary intent | Ephemeral | Discard (`DiscretionaryDirectorState.clearForUnload`) |
| Live/suspended activity episodes | Ephemeral | Abandon (`abandonForUnload`); do not park suspended zombies |
| Episode tombstones, execution failure totals | Ephemeral | Clear |
| Short-term affect (`AffectiveState`) | Unload-resumable | Snapshot |
| `OpinionMemory` learned preferences | Durable (session) | Snapshot — must not vanish on ordinary unload |
| `PlaceOpinionMemory` | Session MVP | Snapshot — loss on frozen LRU/TTL eviction is explicit |
| Entire live `MobExperienceContext` | Ephemeral shell | Discard after snapshot |

## Design

1. **Live map** — only loaded entities with heavyweight context.
2. **`FrozenContextStore`** — bounded LRU (128) + TTL (24_000 ticks) of `MobExperienceSnapshot`.
3. **`parkOnUnload`** — REST invalidate → `prepareForUnloadPark` → snapshot → remove live.
4. **`resumeOnLoad`** — rehydrate snapshot to live on `ENTITY_LOAD`.
5. **Death** — partial reset on live or frozen snapshot (PD-GAO-03); no blind `remove()` on unload.

## Acceptance

- **Must happen:** `contextCount()` bounded across 500 unique park cycles (≤ `MAX_SNAPSHOTS` when none loaded).
- **Must happen:** learned opinion + place pref survive same-UUID park/reload.
- **Must happen:** suspended episodes do not survive park.
- **Must happen:** LRU evicts oldest frozen snapshot; TTL evicts stale.
- **Must not happen:** `ENTITY_UNLOAD → remove(uuid)` without semantics.
- **Must not happen:** unbounded live context growth from abandoned suspended episodes.

## Verification

```text
.\gradlew.bat clean build
```

## Artifacts

- `task-35-report.md`
- `OpinionExperienceRegistryRetentionTest`
- `progress.md` update
