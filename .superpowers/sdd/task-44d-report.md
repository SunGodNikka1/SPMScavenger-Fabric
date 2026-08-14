# Task 44D Report — FriendlyGreet Executor Binding

## Outcome

`COMPLETE — STATIC ACCEPT`. D-GAO-053 and D-GAO-059 are implemented. Runtime remains unverified.

## Design intent → actual implementation

- Exact SOCIAL subject chosen by Opinion is compared with the target SPM returns during its live
  admission call.
- A successful match creates one bounded binding containing mob UUID, intent UUID, subject UUID,
  and admission generation.
- `start()` adopts and runs that exact intent; dynamic activity observation changes only that bound
  instance from `SOCIAL_REFLEX` to `DISCRETIONARY_SOCIAL`.
- The six pinned `Phase.DONE` branches publish completion evidence. The FETCH write is excluded.
- `stop()` consumes once. DONE yields success/positive social evidence; no DONE yields a protected,
  non-learning terminal.
- Binding cleanup is wired to stop, unload, death, and server stop.

## Defects found during implementation

1. The RFC counted four DONE writes; unfiltered bytecode shows six. Corrected.
2. A first draft targeted every `phase` write in `tickGift`/`tickFetch`; `tickGift` also writes
   FETCH, which would have manufactured success. Repaired to target only `Phase.DONE` GETSTATIC.
3. A fresh admission pulse could remain after start and produce another decision during host
   cooldown. It is consumed when execution starts.
4. Dynamic classification initially trusted only a registry phase. It now confirms the exact live
   director intent and self-evicts stale bindings.

## Verification

- Focused tests: pass.
- Full suite: 807 tests, 0 failures/errors/skips.
- `gradlew.bat clean build`: pass.
- Remapped JAR package inspection: pass.
- Artifact: `build/libs/spmscavenger-1.9.4.jar`.
- SHA-256: `A9803703B020D8F2DD739BB11654AAE5B4809B0AB3412A57ECD17C286B4DEDE8`.
- Static MAIBS: `PASS — BEHAVIORALLY_PLAUSIBLE`.
- Minecraft runtime: `UNVERIFIED` (not authorized).

Expected loop: idle observation names Bob → SOCIAL/Bob wins → live host admission names Bob →
exact binding → host FOLLOW/CROUCH/GIFT/FETCH owns MOVE+LOOK → DONE evidence → one successful
terminal. Alice cannot substitute. Host interruption, missing Mixin site, unload, combat, or command
produces no positive evidence.

Remaining runtime questions: physical Mixin application, visible target correlation, subject-switch
stickiness from the intentional issue-time incumbent utility, and multi-mob greet cadence.

No Minecraft launch, commit, push, or PR occurred.

## 44D-R1 fix report — exact DONE-time ownership

Post-acceptance review found a causal hole: `completionObserved(UUID)` trusted registry phase
`RUNNING` without revalidating the exact live director intent and SOCIAL subject. The repair now
uses the same exact intent/candidate predicate at the DONE boundary. Invalidation before DONE
clears the stale binding and cannot teach; a marker validly stamped before later invalidation is
retained as historical evidence until `stop()` consumes it, but no longer reports current running
ownership.

FriendlyGreet activity semantics now have one shared binding-aware helper. Admission remains
`SOCIAL_REFLEX`; continuation becomes `DISCRETIONARY_SOCIAL` only for the exact live binding. Other
host travel goals retain the existing conservative `SOCIAL_REFLEX` envelope behavior.

### Verification

- RED: 3/10 focused tests failed against the accepted 44D implementation (stale DONE credited,
  valid completed history evicted, continuation classification not shared).
- GREEN focused tests: pass.
- Full suite and clean build: **809 tests, 0 failures/errors/skips**.
- Remapped JAR packages the repaired registry, optional shelter Mixin, and Mixin config.
- Artifact: `build/libs/spmscavenger-1.9.4.jar`.
- SHA-256: `DA017011A280DB38F390890B8104E64E59DBB213A33BA1BADBE2175C1430BF5C`.
- Static MAIBS: `PASS — BEHAVIORALLY_PLAUSIBLE`. The repair changes bookkeeping/classification at
  causal boundaries only; it does not alter Goal priority, flags, pathing, movement, or host phases.
- Runtime remains `UNVERIFIED`; no Minecraft launch occurred.

Predicted loop: exact SOCIAL/Bob owns execution → DONE validates Bob's live intent → completion is
stamped once → later scheduler invalidation cannot rewrite that past event → stop consumes it.
Invalidation before DONE → no live exact intent → binding cleared → no positive learning.
