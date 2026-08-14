# Extending Opinion

Adding a fourth discretionary activity is an incremental extension. Do **not** reopen GAO-0 through GAO-10, duplicate the observation stack, or build a mega-goal that perceives, scores, navigates, acts, and learns by itself.

First confirm that the activity is genuinely discretionary. Emergency survival, self-defence, explicit player orders, mandatory shelter, required progression, and competence recovery belong to their owning authority. Opinion may express preference only among choices that authority has already permitted.

## Extension checklist

### 1. Define the activity and candidate identity

Add the activity kind used by utility and intent lifecycle. Decide whether one enum value identifies the candidate or whether it needs a stable subject/resource/site key.

Use a candidate key whenever two choices of the same activity are not interchangeable. Reject invalid combinations at construction time. Never defer identity recovery to class-name matching after execution starts.

### 2. Define availability

Create a cheap, bounded observation that answers whether a real opportunity exists. Availability may observe; it must not reserve, navigate, mutate inventories, call impure continuation methods, or create authority.

Prefer an existing shared observation or host-provided candidate. Do not add a new world scan unless the feature genuinely requires one and has a defined cadence and budget.

### 3. Define utility

Add a complete utility breakdown using relevant usefulness, preference, affect fit, novelty/reward/repetition/failure pressure, and cost terms. Keep scoring pure.

Utility answers **which legal candidate is preferred**. It must not bypass safety, player authority, shelter, project control, simulation boundaries, or executor prerequisites.

### 4. Define admission and continuation separately

Admission answers whether new work can start now. Continuation answers whether an exact already-running instance may continue. Do not call an executor's potentially impure `canContinueToUse()` merely to explain or classify it.

Record the blocker or reason so the Inspector can explain suppression without inventing causality.

### 5. Issue an exact intent

The winning decision must issue an intent containing its decision ID, activity, exact candidate identity, utility context, and timestamps. If the candidate has a subject, store the immutable subject value that was actually scored.

An old intent for another subject cannot satisfy or retain the new winner.

### 6. Use safe yield

If an incumbent discretionary executor must yield, create a bounded transaction that names the exact incumbent and exact challenger. Reconcile acknowledgement, expiry, mandatory invalidation, and supersession. Never clear an incumbent and assume the new executor will start.

### 7. Adopt at the executor boundary

The executor must adopt the exact pending candidate at the causal handoff, then mark it running only when physical execution really starts. Activity-only adoption must fail closed when the activity requires subject identity.

Keep the executor focused. Reuse Minecraft navigation and a small Goal or host executor rather than creating a parallel scheduler.

### 8. Preserve exact ownership

Across delays, Mixins, animation phases, chunk lifecycle, or host callbacks, correlate mob ID, intent ID, candidate key/subject, and an admission generation where needed. Store identifiers and immutable values, not live entity references.

Native host behavior may still execute when no Opinion intent exists. In that case it remains host-owned and must not receive Opinion credit.

### 9. Capture terminal evidence

Identify the observable success boundary and non-success stop paths. Stamp success only while exact ownership is live at that boundary. Once validly stamped, the historical completion may survive a later cleanup event.

Do not infer success from goal disappearance, `stop()`, proximity, a display label, or a probe that changes host state.

### 10. Define learning

Map terminal evidence to an `ExperienceEvent`/episode outcome and cause. State which evidence is eligible for activity, entity, place, or environment learning. Normalize frequency so chatty executors do not learn faster merely because they emit more milestones.

Personality may scale an eligible nonzero subjective delta. It may not create, invert, or authorize learning.

### 11. Extend the Inspector

Expose availability, suppression, complete utility terms, selected candidate identity, intent/yield/adoption/execution lifecycle, terminal evidence, and actual learning receipt. Keep snapshots bounded, immutable, server-validated, and read-only.

### 12. Clean up ownership

Declare the key and bound of every registry or cache. Add production cleanup for stop, unload, death, dimension/lifecycle changes where applicable, and server shutdown. Avoid freshly generated map keys with no deterministic eviction path.

## Required tests

At minimum protect these invariants:

- The new activity cannot start without availability and admission.
- A high preference cannot override a hard legality blocker.
- Candidate A cannot borrow candidate B's intent, continuation, completion, or learning.
- Adoption does not imply continuation, and request does not imply authority.
- Host-native execution without exact handoff remains host-owned.
- Terminal learning requires exact causal ownership and eligible evidence.
- Feature-disabled and optional-integration-missing paths preserve host behavior.
- Cleanup removes stale ownership without cancelling a newer generation.
- Existing EXPLORE, REST, and SOCIAL behavior remains unchanged.

Then perform a behavioral simulation from perception through several minutes of world feedback: interruptions, resumption, target change, path failure, chunk/ticking boundaries, multiple PlayerMobs, and long-duration repetition. Runtime claims require runtime evidence; a clean build proves packaging and compilation only.
