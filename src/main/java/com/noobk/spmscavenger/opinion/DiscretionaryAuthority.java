package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.experience.OpinionExperienceRegistry;
import com.noobk.spmscavenger.experience.RestCloseAttribution;
import com.noobk.spmscavenger.experience.RestCloseReason;

import java.util.UUID;

/**
 * GAO-4 — executor and goal consumer gates for discretionary authority.
 */
public final class DiscretionaryAuthority {

    private DiscretionaryAuthority() {}

    public static boolean opinionGatesConsumers() {
        return OpinionFeatureGate.isEnabled();
    }

    public static boolean mayStartDiscretionaryExplore(UUID mobId) {
        if (!opinionGatesConsumers()) {
            return true;
        }
        return stateFor(mobId).mayStartExecutor(DiscretionaryActivity.EXPLORE);
    }

    public static boolean mayStartDiscretionaryRest(UUID mobId) {
        if (!opinionGatesConsumers()) {
            return true;
        }
        return stateFor(mobId).mayStartExecutor(DiscretionaryActivity.REST);
    }

    public static boolean mustYieldDiscretionaryRest(UUID mobId) {
        if (!opinionGatesConsumers()) {
            return false;
        }
        DiscretionaryDirectorState state = stateFor(mobId);
        return state.mustYield(DiscretionaryActivity.REST, state.lastGameTime());
    }

    public static boolean mustYieldDiscretionaryExplore(UUID mobId) {
        if (!opinionGatesConsumers()) {
            return false;
        }
        DiscretionaryDirectorState state = stateFor(mobId);
        return state.mustYield(DiscretionaryActivity.EXPLORE, state.lastGameTime());
    }

    public static boolean mustYieldWander(UUID mobId) {
        if (!opinionGatesConsumers()) {
            return false;
        }
        DiscretionaryDirectorState state = stateFor(mobId);
        // RFC Rule 5 — wander yields for adopted discretionary authority, not pending intent.
        return state.hasRunningActionableIntent(DiscretionaryActivity.EXPLORE)
                || state.hasRunningActionableIntent(DiscretionaryActivity.REST);
    }

    public static boolean shouldPreserveRestIntentOnCampfireStop(UUID mobId) {
        if (!opinionGatesConsumers()) {
            return false;
        }
        DiscretionaryDirectorState state = stateFor(mobId);
        RestAuthorityPhase phase = state.restAuthorityPhase();
        return phase == RestAuthorityPhase.CLAIMED || phase == RestAuthorityPhase.DELIVERY_COMPLETE;
    }

    public static void onExploreAdopted(UUID mobId, long gameTime) {
        if (!opinionGatesConsumers()) {
            return;
        }
        DiscretionaryDirectorState state = stateFor(mobId);
        state.adopt(DiscretionaryActivity.EXPLORE, gameTime);
        state.markRunning(DiscretionaryActivity.EXPLORE, gameTime);
    }

    public static void onRestAdopted(UUID mobId, long gameTime) {
        if (!opinionGatesConsumers()) {
            return;
        }
        DiscretionaryDirectorState state = stateFor(mobId);
        state.adopt(DiscretionaryActivity.REST, gameTime);
        state.markRunning(DiscretionaryActivity.REST, gameTime);
    }

    public static void onRestClaimOpened(UUID mobId, long gameTime) {
        if (!opinionGatesConsumers()) {
            return;
        }
        stateFor(mobId).markRestClaimOpened(gameTime);
    }

    public static void onRestDeliveryComplete(UUID mobId, long gameTime) {
        if (!opinionGatesConsumers()) {
            return;
        }
        stateFor(mobId).markRestDeliveryComplete(gameTime);
    }

    /**
     * D-GAO-051 — generic yield acknowledgement. Replaces
     * {@code onExploreYieldedForRest} / {@code onRestYieldedForExplore}.
     *
     * <h2>Why the executor must not name the challenger</h2>
     *
     * The pairwise callbacks needed one method per ordered pair, so a third activity would have
     * required six — the explosion D-GAO-051 exists to prevent. But the deeper problem is
     * knowledge: an executor that names its successor has to know the whole activity set, so
     * {@code ExploringGoal} would need to learn that SOCIAL exists.
     *
     * <p>The executor's entire responsibility is <em>"I, this execution, reached my safe yield
     * point."</em> The authority layer already holds the identity-bound {@link YieldRequest} and
     * resolves {@code challengerActivity} and {@code originDecisionId} from it. So EXPLORE need not
     * know REST exists, and neither will need to know about SOCIAL.
     *
     * @param releasingIntentId the execution acknowledging, so a stale acknowledgement from a
     *     replaced intent cannot terminalize the live one
     * @param releasingActivity what the acknowledging executor is
     */
    public static void onDiscretionaryYielded(
            UUID mobId,
            UUID releasingIntentId,
            DiscretionaryActivity releasingActivity,
            long gameTime) {
        if (!opinionGatesConsumers()) {
            return;
        }
        stateFor(mobId).acknowledgeYield(releasingIntentId, releasingActivity, gameTime);
    }

    public static void onExploreTerminal(UUID mobId, IntentLifecycle terminal, long gameTime, String detail) {
        if (!opinionGatesConsumers()) {
            return;
        }
        UUID exploreId = runningExploreIntentId(mobId);
        if (exploreId != null) {
            stateFor(mobId).markTerminalForIntent(
                    exploreId, terminal, InvalidationCause.NONE, gameTime, detail);
        }
    }

    public static void onRestTerminal(UUID mobId, IntentLifecycle terminal, long gameTime, String detail) {
        if (!opinionGatesConsumers()) {
            return;
        }
        UUID restId = runningRestIntentId(mobId);
        if (restId != null) {
            stateFor(mobId).markTerminalForIntent(
                    restId, terminal, InvalidationCause.NONE, gameTime, detail);
        }
    }

    public static void onRestClaimClosed(UUID mobId, long gameTime, RestCloseReason reason) {
        if (!opinionGatesConsumers()) {
            return;
        }
        UUID restId = runningRestIntentId(mobId);
        if (restId == null) {
            return;
        }
        RestCloseAttribution.Semantics semantics = RestCloseAttribution.forReason(reason);
        stateFor(mobId).markTerminalForIntent(
                restId,
                semantics.directorLifecycle(),
                semantics.directorCause(),
                gameTime,
                "rest-claim-closed:" + reason);
    }

    public static void onExploreFailedToStart(UUID mobId, long gameTime) {
        if (!opinionGatesConsumers()) {
            return;
        }
        DiscretionaryDirectorState state = stateFor(mobId);
        state.pendingIntent()
                .filter(intent -> intent.activity() == DiscretionaryActivity.EXPLORE)
                .ifPresent(intent -> state.markTerminalForIntent(
                        intent.intentId(),
                        IntentLifecycle.FAILED,
                        InvalidationCause.NONE,
                        gameTime,
                        "no-route"));
    }

    public static UUID runningRestIntentId(UUID mobId) {
        return stateFor(mobId)
                .runningIntent()
                .filter(intent -> intent.activity() == DiscretionaryActivity.REST)
                .map(DiscretionaryIntent::intentId)
                .orElse(null);
    }

    public static UUID runningExploreIntentId(UUID mobId) {
        return stateFor(mobId)
                .runningIntent()
                .filter(intent -> intent.activity() == DiscretionaryActivity.EXPLORE)
                .map(DiscretionaryIntent::intentId)
                .orElse(null);
    }

    public static void onLearningObserved(
            UUID mobId,
            UUID intentId,
            OpinionDecisionTrace.LearningOutcome learningOutcome) {
        if (!opinionGatesConsumers() || intentId == null) {
            return;
        }
        stateFor(mobId).recordLearningForIntent(intentId, learningOutcome);
    }

    private static DiscretionaryDirectorState stateFor(UUID mobId) {
        return OpinionExperienceRegistry.contextFor(mobId).discretionaryDirector();
    }
}
