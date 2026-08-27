package com.noobk.spmscavenger.village.routing;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteAttemptEvidenceTest {

    @Test
    void evidenceIsBoundedAndGenerationValidated() {
        List<RouteAttemptEvidence.Attempt> tooMany = new ArrayList<>();
        for (int i = 0; i <= RouteAttemptEvidence.MAX_ENTRIES; i++) {
            tooMany.add(new RouteAttemptEvidence.Attempt(
                    new SettlementKey(Level.OVERWORLD, new BlockPos(i * 100, 64, 0)), 100L, 1));
        }
        assertThrows(IllegalArgumentException.class, () -> RouteAttemptEvidence.of(tooMany));
        assertThrows(IllegalArgumentException.class, () -> new RouteAttemptEvidence.Attempt(
                new SettlementKey(Level.OVERWORLD, BlockPos.ZERO), 100L,
                RouteAttemptEvidence.MAX_FAILURE_GENERATION + 1));
    }

    @Test
    void duplicateSettlementUsesLatestExpiryWithoutCreatingHistory() {
        SettlementKey key = new SettlementKey(Level.OVERWORLD, BlockPos.ZERO);
        RouteAttemptEvidence evidence = RouteAttemptEvidence.of(List.of(
                new RouteAttemptEvidence.Attempt(key, 100L, 1),
                new RouteAttemptEvidence.Attempt(key, 200L, 2)));

        assertEquals(1, evidence.size());
        assertTrue(evidence.temporarilyUnavailable(key, 199L));
        assertTrue(!evidence.temporarilyUnavailable(key, 200L));
    }
}
