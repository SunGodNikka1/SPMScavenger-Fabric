package com.noobk.spmscavenger.village.compost;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** RET-1 bounded post-episode cooldown (task-58 anti-loop). */
public final class CompostEpisodeCooldown {

    private static final Map<UUID, Long> COOLDOWN_UNTIL = new ConcurrentHashMap<>();

    private CompostEpisodeCooldown() {}

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

    public static void recordOutcome(UUID mobId, CompostTerminalOutcome outcome, long gameTime) {
        if (mobId == null || outcome == null || outcome == CompostTerminalOutcome.ABORTED) {
            return;
        }
        COOLDOWN_UNTIL.put(mobId, gameTime + CompostTuning.POST_EPISODE_COOLDOWN_TICKS);
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
