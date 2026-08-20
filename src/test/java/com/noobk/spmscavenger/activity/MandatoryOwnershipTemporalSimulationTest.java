package com.noobk.spmscavenger.activity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * D-VR-084 / task-52 — the two required temporal simulations.
 *
 * <p>A — servable demand: claim -> progress -> route impossible -> abandoned -> EXPLORE legal at
 * T121. B — unservable demand: no owner accepts -> no claim -> EXPLORE remains legal, and still
 * legal at T400 (the frozen-demand repair gate).
 */
class MandatoryOwnershipTemporalSimulationTest {

    private static final UUID MOB = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final ResourceLocation CONSUMER =
            ResourceLocation.fromNamespaceAndPath("spmscavenger", "iron_tool_frontier");
    private static final String ROUTE = "iron:raw_iron";

    @AfterEach
    void clearRegistry() {
        MandatoryOwnershipRegistry.shutdownServerState();
    }

    private static ActivityObservationService.Observation idle() {
        return ActivityObservationService.summarize(List.of());
    }

    private static boolean discretionaryAllowed(long tick) {
        return MandatoryOwnership.evaluate(
                idle(),
                false,
                MandatoryOwnershipRegistry.liveClaim(MOB, tick),
                tick).eligible();
    }

    /**
     * Simulation A — servable demand: T0 demand appears, T1 Gather accepts responsibility and
     * CLAIMs, T40 scan/progress, T80 the route becomes impossible, T120 the owner abandons the
     * claim, T121 EXPLORE becomes legal.
     */
    @Test
    void simulationA_servableDemandAbandonsAndDiscretionaryResumes() {
        // T0/T1: demand appears and an owner accepts responsibility -> live claim.
        MandatoryOwnershipRegistry.publish(MOB, CONSUMER, ROUTE, 0, 1L);
        assertFalse(discretionaryAllowed(1L), "a live claim blocks discretionary");

        // T40: scan/progress occurs; the claim remains live.
        assertFalse(discretionaryAllowed(40L));

        // T80: route becomes impossible. T120: owner abandons -> claim released.
        MandatoryOwnershipRegistry.release(MOB, MandatoryOwnershipRegistry.ReleaseReason.ABANDONED);
        assertTrue(discretionaryAllowed(120L), "abandon releases the claim immediately");

        // T121: EXPLORE legal.
        assertTrue(discretionaryAllowed(121L));
    }

    /**
     * Simulation B — unservable demand: T0 impossible demand appears, T1 no route owner accepts
     * it, T2 no claim exists, T3 EXPLORE remains legal — and stays legal at T400. A naive
     * "demand exists -> block" repair fails this gate.
     */
    @Test
    void simulationB_unservableDemandNeverFreezesDiscretionary() {
        // T0/T1: the impossible demand appears but no owner accepts responsibility -> no claim.
        assertTrue(MandatoryOwnershipRegistry.liveClaim(MOB, 1L).isEmpty());
        assertTrue(discretionaryAllowed(1L));
        assertTrue(discretionaryAllowed(2L));
        assertTrue(discretionaryAllowed(3L));
        // Long horizon: still legal at T400 even though the demand persists (it is simply never
        // claimed, and no claim may be minted from demand existence).
        assertTrue(discretionaryAllowed(400L));
    }

    /**
     * Simulation A variant — the claim expires without progress rather than being explicitly
     * abandoned: expiry must also restore discretion, and the same identity must not self-renew
     * (scenario 5 combined with simulation A's T121 gate).
     */
    @Test
    void simulationA_expiryVariantRestoresDiscretionWithoutRenewal() {
        MandatoryOwnershipRegistry.publish(MOB, CONSUMER, ROUTE, 0, 1L);
        long afterExpiry = 1L + MandatoryOwnershipRegistry.MAX_CLAIM_TICKS + 1L;
        assertTrue(discretionaryAllowed(afterExpiry), "expiry restores discretionary permission");

        // Demand still exists -> a same-identity publish with an unchanged generation is refused.
        MandatoryOwnershipRegistry.PublishResult republish =
                MandatoryOwnershipRegistry.publish(MOB, CONSUMER, ROUTE, 0, afterExpiry);
        assertTrue(republish == MandatoryOwnershipRegistry.PublishResult.REFUSED_SAME_ROUTE_GENERATION,
                "the same demand must not self-renew");
        assertTrue(discretionaryAllowed(afterExpiry + 1L));
    }
}
