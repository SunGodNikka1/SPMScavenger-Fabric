package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.experience.OpinionExperienceRegistry;
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
        return stateFor(mobId).hasActionableIntent(DiscretionaryActivity.EXPLORE);
    }

    public static boolean mayStartDiscretionaryRest(UUID mobId) {
        if (!opinionGatesConsumers()) {
            return true;
        }
        return stateFor(mobId).hasActionableIntent(DiscretionaryActivity.REST);
    }

    public static boolean mustYieldDiscretionaryRest(UUID mobId) {
        if (!opinionGatesConsumers()) {
            return false;
        }
        return stateFor(mobId).restYieldRequested();
    }

    public static boolean mustYieldDiscretionaryExplore(UUID mobId) {
        if (!opinionGatesConsumers()) {
            return false;
        }
        return stateFor(mobId).exploreYieldRequested();
    }

    public static boolean mustYieldWander(UUID mobId) {
        if (!opinionGatesConsumers()) {
            return false;
        }
        DiscretionaryDirectorState state = stateFor(mobId);
        return state.hasActionableIntent(DiscretionaryActivity.EXPLORE)
                || state.hasActionableIntent(DiscretionaryActivity.REST);
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

    public static void onExploreYieldedForRest(UUID mobId, UUID releasingIntentId, long gameTime) {
        if (!opinionGatesConsumers()) {
            return;
        }
        DiscretionaryDirectorState state = stateFor(mobId);
        state.markYield(
                releasingIntentId,
                DiscretionaryActivity.EXPLORE,
                DiscretionaryActivity.REST,
                gameTime);
        state.markTerminalForIntent(
                releasingIntentId,
                IntentLifecycle.INTERRUPTED,
                InvalidationCause.SUPERSEDED,
                gameTime,
                "yield-rest");
    }

    public static void onRestYieldedForExplore(UUID mobId, UUID releasingIntentId, long gameTime) {
        if (!opinionGatesConsumers()) {
            return;
        }
        DiscretionaryDirectorState state = stateFor(mobId);
        state.markYield(
                releasingIntentId,
                DiscretionaryActivity.REST,
                DiscretionaryActivity.EXPLORE,
                gameTime);
        state.markTerminalForIntent(
                releasingIntentId,
                IntentLifecycle.INTERRUPTED,
                InvalidationCause.SUPERSEDED,
                gameTime,
                "yield-explore");
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
        stateFor(mobId).markTerminalForIntent(
                restId,
                IntentLifecycle.SUCCEEDED,
                InvalidationCause.NONE,
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

    private static DiscretionaryDirectorState stateFor(UUID mobId) {
        return OpinionExperienceRegistry.contextFor(mobId).discretionaryDirector();
    }
}
