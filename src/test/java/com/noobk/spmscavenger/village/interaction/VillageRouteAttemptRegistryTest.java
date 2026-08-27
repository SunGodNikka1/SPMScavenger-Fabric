package com.noobk.spmscavenger.village.interaction;

import com.noobk.spmscavenger.village.routing.RouteAttemptEvidence;
import com.noobk.spmscavenger.village.routing.SettlementKey;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillageRouteAttemptRegistryTest {

    @AfterEach
    void clear() {
        VillageRouteAttemptRegistry.shutdownServerState();
    }

    @Test
    void terminalFailureCreatesBoundedTemporaryDemotionAndExpiryPhysicallyPrunes() {
        UUID mob = UUID.randomUUID();
        SettlementKey failed = key(1);
        VillageRouteAttemptRegistry.recordTerminalFailure(mob, failed, 100L);

        RouteAttemptEvidence active = VillageRouteAttemptRegistry.snapshot(mob, 100L);
        assertEquals(1, active.size());
        assertTrue(active.temporarilyUnavailable(failed, 100L));

        RouteAttemptEvidence expired = VillageRouteAttemptRegistry.snapshot(
                mob, 100L + VillageRouteAttemptRegistry.DEMOTION_TICKS);
        assertEquals(0, expired.size());
        assertEquals(0, VillageRouteAttemptRegistry.trackedMobCount());
    }

    @Test
    void perMobHistoryNeverExceedsImmutableEvidenceBound() {
        UUID mob = UUID.randomUUID();
        for (int i = 0; i < RouteAttemptEvidence.MAX_ENTRIES + 5; i++) {
            VillageRouteAttemptRegistry.recordTerminalFailure(mob, key(i), 20L + i);
        }
        assertEquals(RouteAttemptEvidence.MAX_ENTRIES,
                VillageRouteAttemptRegistry.snapshot(mob, 50L).size());
    }

    @Test
    void arrivalAndOwnerLifecycleClearTransientEvidence() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        SettlementKey destination = key(4);
        VillageRouteAttemptRegistry.recordTerminalFailure(first, destination, 0L);
        VillageRouteAttemptRegistry.recordTerminalFailure(second, destination, 0L);

        VillageRouteAttemptRegistry.recordArrival(first, destination);
        assertFalse(VillageRouteAttemptRegistry.snapshot(first, 1L)
                .temporarilyUnavailable(destination, 1L));
        assertTrue(VillageRouteAttemptRegistry.snapshot(second, 1L)
                .temporarilyUnavailable(destination, 1L));

        VillageRouteAttemptRegistry.release(second);
        assertEquals(0, VillageRouteAttemptRegistry.trackedMobCount());
    }

    private static SettlementKey key(int x) {
        return new SettlementKey(Level.OVERWORLD, new BlockPos(x, 64, 0));
    }
}
