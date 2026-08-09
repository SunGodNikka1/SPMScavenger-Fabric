package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.experience.OpinionExperienceRegistry;

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

    public static void onExploreYieldedForRest(UUID mobId, long gameTime) {
        if (!opinionGatesConsumers()) {
            return;
        }
        DiscretionaryDirectorState state = stateFor(mobId);
        state.markYield(DiscretionaryActivity.EXPLORE, DiscretionaryActivity.REST, gameTime);
        state.markTerminal(IntentLifecycle.INTERRUPTED, InvalidationCause.SUPERSEDED, gameTime, "yield-rest");
    }

    public static void onRestYieldedForExplore(UUID mobId, long gameTime) {
        if (!opinionGatesConsumers()) {
            return;
        }
        DiscretionaryDirectorState state = stateFor(mobId);
        state.markYield(DiscretionaryActivity.REST, DiscretionaryActivity.EXPLORE, gameTime);
        state.markTerminal(IntentLifecycle.INTERRUPTED, InvalidationCause.SUPERSEDED, gameTime, "yield-explore");
    }

    public static void onExploreTerminal(UUID mobId, IntentLifecycle terminal, long gameTime, String detail) {
        if (!opinionGatesConsumers()) {
            return;
        }
        stateFor(mobId).markTerminal(terminal, InvalidationCause.NONE, gameTime, detail);
    }

    public static void onRestTerminal(UUID mobId, IntentLifecycle terminal, long gameTime, String detail) {
        if (!opinionGatesConsumers()) {
            return;
        }
        stateFor(mobId).markTerminal(terminal, InvalidationCause.NONE, gameTime, detail);
    }

    public static void onExploreFailedToStart(UUID mobId, long gameTime) {
        if (!opinionGatesConsumers()) {
            return;
        }
        stateFor(mobId).markTerminal(IntentLifecycle.FAILED, InvalidationCause.NONE, gameTime, "no-route");
    }

    private static DiscretionaryDirectorState stateFor(UUID mobId) {
        return OpinionExperienceRegistry.contextFor(mobId).discretionaryDirector();
    }
}
