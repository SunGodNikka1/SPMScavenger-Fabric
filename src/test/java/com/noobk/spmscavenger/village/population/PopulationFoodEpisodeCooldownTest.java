package com.noobk.spmscavenger.village.population;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

/** Task-57 — G0-B anti-loop cooldown. */
class PopulationFoodEpisodeCooldownTest {

    private static final UUID MOB = UUID.randomUUID();

    @BeforeEach
    void clean() {
        PopulationFoodEpisodeCooldown.shutdownServerState();
    }

    @Test
    void committedUnconfirmedUsesSameCooldownAsDeliveredAck() {
        long now = 100L;
        PopulationFoodEpisodeCooldown.recordOutcome(
                MOB, PopulationFoodTerminalOutcome.COMMITTED_UNCONFIRMED, now);
        assertTrue(PopulationFoodEpisodeCooldown.isCooling(MOB, now + 1));
        assertFalse(PopulationFoodEpisodeCooldown.isCooling(
                MOB, now + PopulationFoodTuning.POST_EPISODE_COOLDOWN_TICKS));
    }

    @Test
    void abortedOutcomeDoesNotCooldown() {
        PopulationFoodEpisodeCooldown.recordOutcome(
                MOB, PopulationFoodTerminalOutcome.ABORTED, 0L);
        assertFalse(PopulationFoodEpisodeCooldown.isCooling(MOB, 1L));
    }

    @Test
    void negativeControl_unconfirmedMustNotImmediatelyRetry() {
        PopulationFoodEpisodeCooldown.recordOutcome(
                MOB, PopulationFoodTerminalOutcome.COMMITTED_UNCONFIRMED, 0L);
        assertTrue(PopulationFoodEpisodeCooldown.isCooling(MOB, 50L),
            "missing cooldown would allow immediate second episode");
  }
}
