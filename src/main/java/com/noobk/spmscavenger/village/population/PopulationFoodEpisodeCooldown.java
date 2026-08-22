package com.noobk.spmscavenger.village.population;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** RET-1 bounded post-episode cooldown (task-57 anti-loop). */
public final class PopulationFoodEpisodeCooldown {

    private static final Map<UUID, Long> COOLDOWN_UNTIL = new ConcurrentHashMap<>();

    private PopulationFoodEpisodeCooldown() {}

    public static boolean isCooling(UUID mobId, long gameTime) {
        if (mobId == null) {
            return false;
        }
        Long until = COOLDOWN_UNTIL.get(mobId);
        if (until == null) {
            return false;
        }
        if (gameTime >= until) {
            COOLDOWN_UNTIL.remove(mobId, until);
            return false;
        }
        return true;
    }

    public static void recordOutcome(UUID mobId, PopulationFoodTerminalOutcome outcome, long gameTime) {
        if (mobId == null || outcome == null || outcome == PopulationFoodTerminalOutcome.ABORTED) {
            return;
        }
        long duration = PopulationFoodTuning.POST_EPISODE_COOLDOWN_TICKS;
        COOLDOWN_UNTIL.put(mobId, gameTime + duration);
    }

    public static void release(UUID mobId) {
        if (mobId != null) {
            COOLDOWN_UNTIL.remove(mobId);
        }
    }

    public static void shutdownServerState() {
        COOLDOWN_UNTIL.clear();
    }
}
