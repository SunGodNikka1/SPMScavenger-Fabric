package com.noobk.spmscavenger.opinion.readout;

import com.noobk.spmscavenger.activity.ActivityClass;
import com.noobk.spmscavenger.activity.ActivityObservationService;
import com.noobk.spmscavenger.experience.MobExperienceContext;
import com.noobk.spmscavenger.mining.MiningProjectSavedData;
import com.noobk.spmscavenger.mining.MoveHolderClassifier;
import com.noobk.spmscavenger.mixin.MobGoalSelectorAccessor;
import com.noobk.spmscavenger.opinion.DiscretionaryEligibility;
import com.noobk.spmscavenger.opinion.InvalidationCause;
import com.noobk.spmscavenger.opinion.SocialAdmissionSeam;
import com.noobk.spmscavenger.opinion.SocialExecutionBindingRegistry;
import com.noobk.spmscavenger.opinion.SocialGreetClaimWindow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One-shot read-only GoalSelector scan for the Opinion inspector.
 *
 * <p>Uses {@link MobGoalSelectorAccessor} and {@link MoveHolderClassifier} only — never invokes
 * {@code canUse()}, {@code canContinueToUse()}, or host relationship search.
 */
public final class OpinionRuntimeAuthorityProbe {

    private OpinionRuntimeAuthorityProbe() {
    }

    public static OpinionRuntimeAuthorityView capture(Mob mob, MobExperienceContext context) {
        if (mob == null || context == null) {
            return OpinionRuntimeAuthorityView.empty();
        }
        long now = mob.level().getGameTime();
        UUID mobId = mob.getUUID();
        MiningProjectSavedData store = mob.level() instanceof ServerLevel level
                ? MiningProjectSavedData.get(level)
                : null;
        GoalSelector selector = ((MobGoalSelectorAccessor) mob).spmscavenger$getGoalSelector();

        List<RunningGoalView> runningGoals = scanRunningGoals(selector, mob, store, mobId, now);
        boolean combatTarget = mob.getTarget() != null;
        ActivityObservationService.Observation observation =
                ActivityObservationService.observe(selector, mob, store, now);
        BlockerAttribution blocker = attributeBlocker(runningGoals, observation, combatTarget);

        String latestDispositionCause = OpinionReadoutExplanation.latestDecision(context)
                .map(decision -> decision.dispositionCause().name())
                .orElse("");

        String socialAdmissionTargetId = SocialAdmissionSeam.observation(mobId, now)
                .filter(obs -> obs.hasTarget())
                .map(obs -> obs.targetId().toString())
                .orElse("");

        String greetClaimTargetId = "";
        long greetClaimTicksRemaining = OpinionRuntimeAuthorityView.NO_GREET_CLAIM_TICKS;
        Optional<SocialGreetClaimWindow.ClaimEpisodeStatus> claim =
                SocialGreetClaimWindow.episodeStatus(mobId, now);
        if (claim.isPresent()) {
            greetClaimTargetId = claim.get().targetId().toString();
            greetClaimTicksRemaining = claim.get().ticksRemaining();
        }

        String socialBindingPhase = "";
        String socialBindingSubjectId = "";
        Optional<SocialExecutionBindingRegistry.Binding> binding =
                SocialExecutionBindingRegistry.binding(mobId);
        if (binding.isPresent()) {
            socialBindingPhase = binding.get().phase().name();
            socialBindingSubjectId = binding.get().subjectId().toString();
        }

        return new OpinionRuntimeAuthorityView(
                latestDispositionCause,
                runningGoals,
                blocker.goalSimpleName(),
                blocker.activityClass(),
                blocker.cause(),
                combatTarget,
                socialAdmissionTargetId,
                greetClaimTargetId,
                greetClaimTicksRemaining,
                socialBindingPhase,
                socialBindingSubjectId);
    }

    static List<RunningGoalView> scanRunningGoals(
            GoalSelector selector,
            Mob mob,
            MiningProjectSavedData store,
            UUID mobId,
            long now) {
        List<RunningGoalView> runningGoals = new ArrayList<>();
        for (WrappedGoal wrapped : selector.getAvailableGoals()) {
            if (!wrapped.isRunning()) {
                continue;
            }
            Goal goal = wrapped.getGoal();
            ActivityClass activity = MoveHolderClassifier.activityClass(
                    goal, mob, store, mobId, now);
            runningGoals.add(new RunningGoalView(goal.getClass().getSimpleName(), activity.name()));
            if (runningGoals.size() >= OpinionRuntimeAuthorityView.MAX_RUNNING_GOALS) {
                break;
            }
        }
        return List.copyOf(runningGoals);
    }

    static BlockerAttribution attributeBlocker(
            List<RunningGoalView> runningGoals,
            ActivityObservationService.Observation observation,
            boolean combatTarget) {
        if (combatTarget) {
            return new BlockerAttribution(
                    "",
                    "COMBAT_TARGET",
                    InvalidationCause.COMBAT_TARGET.name());
        }
        for (RunningGoalView running : runningGoals) {
            ActivityClass activity = ActivityClass.valueOf(running.activityClass());
            if (DiscretionaryEligibility.blocksDiscretionaryChoice(activity)) {
                return new BlockerAttribution(
                        running.goalSimpleName(),
                        running.activityClass(),
                        DiscretionaryEligibility.invalidationCauseForActivity(activity).name());
            }
        }
        InvalidationCause observationCause =
                DiscretionaryEligibility.invalidationForObservation(observation, false);
        if (observationCause == InvalidationCause.UNKNOWN_ACTIVE) {
            return new BlockerAttribution("", "UNKNOWN_ACTIVE", observationCause.name());
        }
        return BlockerAttribution.none();
    }

    record BlockerAttribution(String goalSimpleName, String activityClass, String cause) {
        static BlockerAttribution none() {
            return new BlockerAttribution("", "", "");
        }
    }
}
