package com.noobk.spmscavenger.activity;

import com.noobk.spmscavenger.experience.OpinionExperienceRegistry;
import com.noobk.spmscavenger.mining.MiningProjectSavedData;
import com.noobk.spmscavenger.mining.MoveHolderClassifier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * GAO-0 — the single scheduler-wide activity observer.
 *
 * <p>It performs one pass over running Goals and delegates every semantic classification to
 * {@link MoveHolderClassifier}. The resulting predicates are deliberately independent: expedition
 * work, discretionary idle, rest, occupancy, and unknown fail-safe state are not interchangeable.
 * No method in this service mutates Goal state or chooses an activity.
 */
public final class ActivityObservationService {

    private ActivityObservationService() {
    }

    public static Observation observe(
            GoalSelector selector,
            Mob mob,
            @Nullable MiningProjectSavedData store,
            long now) {
        EnumSet<ActivityClass> active = EnumSet.noneOf(ActivityClass.class);
        for (WrappedGoal wrapped : selector.getAvailableGoals()) {
            if (wrapped.isRunning()) {
                active.add(MoveHolderClassifier.activityClass(
                        wrapped.getGoal(), mob, store, mob.getUUID(), now));
            }
        }
        return summarize(active, externalRestState(mob));
    }

    private static boolean externalRestState(@Nullable Mob mob) {
        if (mob == null) {
            return false;
        }
        if (mob.isSleeping()) {
            return true;
        }
        return OpinionExperienceRegistry.hasLiveRestClaim(mob.getUUID());
    }

    /** Deterministic test seam; production uses {@link #observe} and therefore one selector pass. */
    public static Observation observeRunningGoals(
            Iterable<? extends Goal> runningGoals,
            @Nullable Mob mob,
            @Nullable MiningProjectSavedData store,
            UUID mobId,
            long now) {
        EnumSet<ActivityClass> active = EnumSet.noneOf(ActivityClass.class);
        for (Goal goal : runningGoals) {
            active.add(MoveHolderClassifier.activityClass(goal, mob, store, mobId, now));
        }
        return summarize(active, false);
    }

    public static Observation summarize(Iterable<ActivityClass> activeClasses) {
        return summarize(activeClasses, false);
    }

    public static Observation summarize(Iterable<ActivityClass> activeClasses, boolean externalRest) {
        EnumSet<ActivityClass> active = EnumSet.noneOf(ActivityClass.class);
        boolean meaningfulWork = false;
        boolean exploring = false;
        boolean resting = false;
        boolean occupied = false;
        boolean unknown = false;
        for (ActivityClass activity : activeClasses) {
            active.add(activity);
            occupied |= activity.isSchedulerOccupant();
            unknown |= activity == ActivityClass.UNKNOWN_ACTIVE;
            exploring |= activity.isExpedition();
            resting |= activity.isRest();

            // Compatibility rule: only the exact historical wander/look/antics/observer family is
            // ignored. PASSIVE_HELPER remains meaningful for readiness until a later, separately
            // authorized parity decision changes that behavior.
            if (!activity.isExpedition() && !activity.isLegacyIdleNoise()) {
                meaningfulWork = true;
            }
        }
        resting |= externalRest;
        return new Observation(
                active,
                meaningfulWork,
                exploring,
                !meaningfulWork && !exploring && !resting,
                resting,
                occupied,
                unknown);
    }

    public record Observation(
            Set<ActivityClass> activeClasses,
            boolean meaningfulWorkForExpedition,
            boolean exploring,
            boolean discretionaryIdleCandidate,
            boolean resting,
            boolean schedulerOccupied,
            boolean unknownActive) {

        public Observation {
            activeClasses = Collections.unmodifiableSet(
                    activeClasses.isEmpty()
                            ? EnumSet.noneOf(ActivityClass.class)
                            : EnumSet.copyOf(activeClasses));
        }
    }

}
