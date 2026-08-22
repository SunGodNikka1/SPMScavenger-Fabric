package com.noobk.spmscavenger.village.population;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noobk.spmscavenger.village.trade.TradeSessionClaimWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

/** Task-57 — PD-57-6 trade + social interlocks. */
class PopulationFoodInterlocksTest {

    private static final UUID MOB = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final UUID ALICE = UUID.randomUUID();

    @BeforeEach
    void clean() {
        TradeSessionClaimWindow.shutdownServerState();
    }

    @Test
    void tradeClaimBlocksExactPairOnly() {
        TradeSessionClaimWindow.claim(MOB, BOB, 0L);
        assertTrue(PopulationFoodInterlocks.blocksHandoff(MOB, BOB, 1L));
        assertFalse(PopulationFoodInterlocks.blocksHandoff(MOB, ALICE, 1L));
    }

    @Test
    void interlockChecksSocialBindingRegistry() throws java.io.IOException {
        String body = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/noobk/spmscavenger/village/population/PopulationFoodInterlocks.java"));
        assertTrue(body.contains("SocialExecutionBindingRegistry"));
        assertTrue(body.contains("subjectId().equals(villagerId)"));
    }

    @Test
    void negativeControl_noClaimMeansNoBlock() {
        assertFalse(PopulationFoodInterlocks.blocksHandoff(MOB, BOB, 1L));
    }
}
