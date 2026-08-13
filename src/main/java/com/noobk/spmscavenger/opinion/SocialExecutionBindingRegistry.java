package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.experience.ExperienceEmitters;
import net.minecraft.world.entity.Mob;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Task 44D — exact causal bridge between one Opinion SOCIAL intent and one native SPM greet.
 *
 * <p>The registry is runtime-only and bounded to one record per loaded mob. The key is the mob
 * UUID; the value carries the intent UUID, exact subject UUID, and a monotonic admission generation.
 * Production eviction occurs at greet stop, entity unload/death and server stop. No entity reference
 * is retained.
 */
public final class SocialExecutionBindingRegistry {

    public enum Phase {
        ADMITTED,
        RUNNING
    }

    public record Binding(
            UUID mobId,
            UUID intentId,
            UUID subjectId,
            long admissionGeneration,
            long admittedAtTick,
            Phase phase,
            boolean completionObserved) {

        public Binding {
            if (admissionGeneration <= 0L) {
                throw new IllegalArgumentException("admissionGeneration must be positive");
            }
        }

        DiscretionaryCandidateKey candidateKey() {
            return new DiscretionaryCandidateKey(DiscretionaryActivity.SOCIAL, subjectId);
        }

        Binding running() {
            return new Binding(
                    mobId, intentId, subjectId, admissionGeneration, admittedAtTick,
                    Phase.RUNNING, completionObserved);
        }

        Binding completed() {
            return new Binding(
                    mobId, intentId, subjectId, admissionGeneration, admittedAtTick,
                    phase, true);
        }
    }

    public enum Terminal {
        COMPLETED,
        NON_COMPLETED,
        UNBOUND
    }

    private static final Map<UUID, Binding> BINDINGS = new ConcurrentHashMap<>();
    private static final AtomicLong GENERATIONS = new AtomicLong();

    private SocialExecutionBindingRegistry() {
    }

    /**
     * Bind only the exact currently startable SOCIAL intent to the target SPM selected now.
     * This method allocates no Opinion context when none exists.
     */
    public static Optional<Binding> admit(Mob mob, UUID targetId, long gameTime) {
        if (!OpinionFeatureGate.isEnabled() || mob == null || targetId == null) {
            return Optional.empty();
        }
        var context = com.noobk.spmscavenger.experience.OpinionExperienceRegistry.find(mob.getUUID());
        if (context == null) {
            return Optional.empty();
        }
        return admitExact(
                mob.getUUID(), targetId, gameTime, context.discretionaryDirector());
    }

    /** Pure-control overload used by contract tests; production supplies a live context above. */
    static Optional<Binding> admitExact(
            UUID mobId,
            UUID targetId,
            long gameTime,
            DiscretionaryDirectorState director) {
        if (mobId == null || targetId == null || director == null) {
            return Optional.empty();
        }
        DiscretionaryCandidateKey key =
                new DiscretionaryCandidateKey(DiscretionaryActivity.SOCIAL, targetId);
        DiscretionaryIntent intent = director.runningIntent()
                .filter(candidate -> candidate.candidateKey().equals(key))
                .or(() -> director.pendingIntent()
                        .filter(candidate -> candidate.candidateKey().equals(key)))
                .orElse(null);
        if (intent == null || !director.mayStartExecutor(key)) {
            rejectAdmission(mobId);
            return Optional.empty();
        }
        Binding binding = new Binding(
                mobId, intent.intentId(), targetId,
                GENERATIONS.incrementAndGet(), gameTime, Phase.ADMITTED, false);
        BINDINGS.put(mobId, binding);
        return Optional.of(binding);
    }

    /** Start confirms scheduler adoption. A stale/replaced intent fails closed and loses binding. */
    public static boolean started(Mob mob, long gameTime) {
        if (!OpinionFeatureGate.isEnabled() || mob == null) {
            return false;
        }
        Binding binding = BINDINGS.get(mob.getUUID());
        if (binding == null || binding.phase() != Phase.ADMITTED) {
            return false;
        }
        var context = com.noobk.spmscavenger.experience.OpinionExperienceRegistry.find(mob.getUUID());
        if (context == null) {
            BINDINGS.remove(mob.getUUID(), binding);
            return false;
        }
        return startedExact(mob.getUUID(), context.discretionaryDirector(), gameTime);
    }

    static boolean startedExact(
            UUID mobId, DiscretionaryDirectorState director, long gameTime) {
        Binding binding = BINDINGS.get(mobId);
        if (binding == null || binding.phase() != Phase.ADMITTED) {
            return false;
        }
        if (!director.mayStartExecutor(binding.candidateKey())) {
            BINDINGS.remove(mobId, binding);
            return false;
        }
        director.adopt(binding.candidateKey(), gameTime);
        director.markRunning(binding.candidateKey(), gameTime);
        Binding running = binding.running();
        BINDINGS.replace(mobId, binding, running);
        // The admission pulse described the start that just happened. Keeping it fresh after the
        // greet ends would manufacture another opportunity during SPM's cooldown.
        SocialAdmissionSeam.release(mobId);
        return true;
    }

    /** Called only after a pinned host execution-time {@code Phase.DONE} field write. */
    public static void completionObserved(Mob mob) {
        if (mob == null) {
            return;
        }
        completionObserved(mob.getUUID());
    }

    static void completionObserved(UUID mobId) {
        BINDINGS.computeIfPresent(mobId, (ignored, binding) ->
                binding.phase() == Phase.RUNNING ? binding.completed() : binding);
    }

    /** Reject only a not-yet-started episode; never erase a genuinely running greet. */
    public static void rejectAdmission(UUID mobId) {
        if (mobId == null) {
            return;
        }
        BINDINGS.computeIfPresent(mobId, (ignored, binding) ->
                binding.phase() == Phase.ADMITTED ? null : binding);
    }

    /**
     * Consume once at stop. Missing evidence is deliberately neutral/non-completed; stop alone is
     * never success and no host continuation/relationship predicate is probed.
     */
    public static Terminal stopped(Mob mob, long gameTime) {
        if (mob == null) {
            return Terminal.UNBOUND;
        }
        Binding binding = BINDINGS.remove(mob.getUUID());
        if (binding == null) {
            return Terminal.UNBOUND;
        }
        boolean completed = OpinionFeatureGate.isEnabled()
                && binding.phase() == Phase.RUNNING
                && binding.completionObserved();
        var context = com.noobk.spmscavenger.experience.OpinionExperienceRegistry.find(mob.getUUID());
        if (context != null && OpinionFeatureGate.isEnabled() && completed) {
            ExperienceEmitters.socialGreetTerminal(
                    mob,
                    binding.intentId(),
                    binding.subjectId(),
                    binding.admittedAtTick(),
                    gameTime,
                    true);
        } else if (context != null && OpinionFeatureGate.isEnabled()) {
            ExperienceEmitters.socialGreetTerminal(
                    mob,
                    binding.intentId(),
                    binding.subjectId(),
                    binding.admittedAtTick(),
                    gameTime,
                    false);
        }
        if (context != null) {
            context.discretionaryDirector().markTerminalForIntent(
                    binding.intentId(),
                    completed ? IntentLifecycle.SUCCEEDED : IntentLifecycle.INTERRUPTED,
                    InvalidationCause.NONE,
                    gameTime,
                    completed ? "social-greet-completed" : "social-greet-ended-without-completion");
        }
        if (completed) {
            return Terminal.COMPLETED;
        }
        return Terminal.NON_COMPLETED;
    }

    public static boolean isRunning(UUID mobId) {
        Binding binding = BINDINGS.get(mobId);
        if (binding == null || binding.phase() != Phase.RUNNING) {
            return false;
        }
        var context = com.noobk.spmscavenger.experience.OpinionExperienceRegistry.find(mobId);
        boolean exactLiveIntent = context != null
                && context.discretionaryDirector().runningIntent()
                        .filter(DiscretionaryIntent::isActive)
                        .filter(intent -> intent.intentId().equals(binding.intentId()))
                        .filter(intent -> intent.candidateKey().equals(binding.candidateKey()))
                        .isPresent();
        if (!exactLiveIntent) {
            BINDINGS.remove(mobId, binding);
        }
        return exactLiveIntent;
    }

    public static Optional<Binding> binding(UUID mobId) {
        return Optional.ofNullable(BINDINGS.get(mobId));
    }

    static Terminal terminalOf(Binding binding, boolean opinionEnabled) {
        if (binding == null) {
            return Terminal.UNBOUND;
        }
        return opinionEnabled
                        && binding.phase() == Phase.RUNNING
                        && binding.completionObserved()
                ? Terminal.COMPLETED
                : Terminal.NON_COMPLETED;
    }

    /** Exact incumbent subject for continuation scoring; non-allocating and binding-checked. */
    public static Optional<SocialIntent> runningSubject(UUID mobId) {
        Binding binding = BINDINGS.get(mobId);
        if (binding == null || binding.phase() != Phase.RUNNING) {
            return Optional.empty();
        }
        var context = com.noobk.spmscavenger.experience.OpinionExperienceRegistry.find(mobId);
        if (context == null) {
            return Optional.empty();
        }
        return context.discretionaryDirector().runningIntent()
                .filter(intent -> intent.intentId().equals(binding.intentId()))
                .filter(intent -> intent.candidateKey().equals(binding.candidateKey()))
                .map(DiscretionaryIntent::socialSubject);
    }

    public static void release(UUID mobId) {
        if (mobId != null) {
            BINDINGS.remove(mobId);
        }
    }

    public static void shutdownServerState() {
        BINDINGS.clear();
        GENERATIONS.set(0L);
    }

    public static int trackedBindingCount() {
        return BINDINGS.size();
    }
}
