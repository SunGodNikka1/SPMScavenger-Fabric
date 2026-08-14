# Compatibility Contracts

These contracts apply to SPM and future host/content-mod integrations. They are deliberately generic.

## Core contracts

1. **Prefer addon architecture.** Extend the host through supported APIs, data, tags, or narrow seams. Do not replace or fork the host unless a verified missing capability makes that unavoidable.
2. **Host legality remains authoritative.** Preserve host targeting, relationship, ownership, safety, and lifecycle rules. The addon may add policy only within the authority it actually owns.
3. **Optional integrations fail closed.** Missing classes, changed methods, rejected reflection, or an unavailable executor must disable only the optional feature—not crash startup or silently grant behavior.
4. **Disabled means host parity.** When an addon feature is disabled, preserve the host's target, decision, and execution path. Cleanup needed for old addon state may remain active but must not create new work.
5. **Observation is not ownership.** Seeing a host activity does not make it addon-owned.
6. **Adoption is not continuation.** New-work admission and exact running-instance continuation are separate contracts.
7. **Request is not authority.** A request, candidate, score, or intent cannot move an entity until the executor accepts the exact handoff.
8. **Desire is not start permission.** Preference affects choice among legal candidates; it never creates legality.
9. **Activity kind is not always candidate identity.** Include stable subject/resource/site identity when two candidates of one kind are not interchangeable.
10. **Establish ownership at causal handoff.** Do not reconstruct ownership afterward from a class name, visual similarity, proximity, or terminal event.
11. **Carry exact identity across async/lifecycle boundaries.** Correlate mob, intent, subject/candidate, and generation. Avoid long-lived entity references.
12. **Do not use impure observation probes.** A readout or classifier must not call methods that advance timers, reroll targets, mutate navigation, or consume host state.
13. **Keep reflection bridges bounded.** Pin expected classes/methods, cache successful resolution, fail closed, log actionable diagnostics once, and test absence/shape change.
14. **Use optional Mixins narrowly.** For an optional host, `@Pseudo`, string targets, and `require = 0` are appropriate when the target method may be absent. If production mapping requires it, cover both readable and intermediary method names. The optional hook's failure must preserve host startup and fallback behavior.
15. **Keep ordinary helpers out of Mixin-only packages.** Some loaders/classloaders treat those packages specially. Policy, DTOs, and reflection helpers belong in normal common/compat packages.
16. **Clean up retained state.** Every binding, claim, cache, or per-entity context needs a key, bound, and production removal path for stop, unload, death, and server shutdown; include dimension changes where the integration retains cross-world identity.
17. **Classify semantics centrally.** Reuse the shared activity taxonomy/classifier. Host-specific suffix knowledge must not drift across multiple scanners.
18. **Native host behavior may remain native.** A host goal can execute normally without an addon intent. Do not award addon learning or claim control unless the exact handoff occurred.
19. **Test invariants, not rituals.** Unit/static tests should protect identity, authority, fallback, and cleanup contracts. Use runtime only for behavior that truly requires Minecraft rendering, scheduling, networking, persistence, or cross-mod execution evidence.
20. **Keep compatibility work bounded.** Scans, reflection, target resolution, retries, diagnostics, and retained histories need explicit limits and cadence.
21. **Escalate repeated symptom patches.** If an integration repeatedly needs one-off guards, identify and centralize the shared invariant before adding another Mixin or special case.

## Compatibility ladder

Use the lowest layer that expresses the required semantics:

1. Host/native API or configuration.
2. Minecraft registry, recipe, attribute, capability, or tag data.
3. A project-owned generic adapter interface.
4. A narrow host-specific adapter.
5. Bounded reflection or optional Mixin only when no stable extension seam exists.

Do not copy another mod's dependency set or compatibility technique without proving it fits this target.

## How to add compatibility for another mod

1. **Pin and inspect both sides.** Record exact mod, Minecraft, loader, mapping, and source/artifact versions. Verify the real host lifecycle and the feature's actual semantics.
2. **Map native support first.** Determine what already works through vanilla APIs, recipes, tags, attributes, interfaces, or config. Record at least three relevant negative probes before declaring a seam absent.
3. **Choose the integration layer.** Compare at least two viable designs—usually data/generic adapter versus host-specific hook—and document tradeoffs and switch evidence.
4. **Define authority and identity.** State who observes, requests, admits, executes, continues, terminates, and learns. Define exact candidate identity and disabled/missing-host behavior.
5. **Implement the smallest seam.** Keep host-specific code at the boundary and generic policy in normal addon classes. Do not duplicate the host's AI or inventory.
6. **Bound lifecycle state.** Add stop/unload/death/server-shutdown cleanup before treating the integration as complete.
7. **Verify fallbacks and behavior.** Must happen: the compatible feature executes only after the verified handoff. Must not happen: absent/disabled/incompatible host code crashes startup, changes native behavior, or manufactures addon ownership. Add runtime evidence only where static tests cannot prove the claim.

For content-focused integrations, continue with [[Mod Support|Mod-Support]].
