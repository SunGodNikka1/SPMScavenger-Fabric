package com.noobk.spmscavenger.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

/**
 * D-VR-084 / task-52 — the registry half: anti-self-renewal, one slot per mob, generation
 * comparison, and RET-1 lifetime (expiry is deletion, not a predicate; release on unload/death;
 * server stop clears; restart resurrects nothing).
 *
 * <p>The registry is deliberately static and runtime-only, mirroring
 * {@code TradeSessionClaimWindow}. Each test clears state so runs are order-independent.
 */
class MandatoryOwnershipRegistryTest {

    private static final UUID MOB = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final ResourceLocation CONSUMER =
            ResourceLocation.fromNamespaceAndPath("spmscavenger", "iron_tool_frontier");
    private static final String ROUTE_A = "iron:raw_iron";
    private static final String ROUTE_B = "iron:iron_ingot";

    @AfterEach
    void clearRegistry() {
        MandatoryOwnershipRegistry.shutdownServerState();
    }

    // ------------------------------------------------------------------ publish / generation

    /** A first publish of a fresh identity is accepted and yields a live claim. */
    @Test
    void publishAcceptsAFreshIdentityAndExposesALiveClaim() {
        MandatoryOwnershipRegistry.PublishResult result =
                MandatoryOwnershipRegistry.publish(MOB, CONSUMER, ROUTE_A, 0, 100L);
        assertEquals(MandatoryOwnershipRegistry.PublishResult.ACCEPTED, result);
        assertTrue(MandatoryOwnershipRegistry.liveClaim(MOB, 100L).isPresent());
        assertEquals(1, MandatoryOwnershipRegistry.trackedClaimCount());
    }

    /**
     * Scenario 5 / negative control 1 — the load-bearing anti-self-renewal. After expiry with an
     * unchanged identity, republishing with the SAME generation is REFUSED. Removing the
     * generation comparison makes this test fail (the claim would self-renew forever).
     */
    @Test
    void scenario5_sameDemandAfterExpiryDoesNotSelfRenew() {
        MandatoryOwnershipRegistry.publish(MOB, CONSUMER, ROUTE_A, 0, 100L);
        // Claim expires at 100 + MAX_CLAIM_TICKS; advance far past it.
        long farFuture = 100L + MandatoryOwnershipRegistry.MAX_CLAIM_TICKS * 10;
        assertTrue(MandatoryOwnershipRegistry.liveClaim(MOB, farFuture).isEmpty(),
                "expired claim must be deleted (RET-1a), not merely predicate-false");
        MandatoryOwnershipRegistry.PublishResult republish =
                MandatoryOwnershipRegistry.publish(MOB, CONSUMER, ROUTE_A, 0, farFuture);
        assertEquals(MandatoryOwnershipRegistry.PublishResult.REFUSED_SAME_ROUTE_GENERATION,
                republish, "the same demand must not mint a successor claim");
    }

    /**
     * 6a — an EXECUTOR_STARTED release advances the episode generation, so the SAME canonical
     * identity may later publish with the NEXT generation (the only minting event).
     */
    @Test
    void scenario6a_executorStartedAllowsTheNextGeneration() {
        MandatoryOwnershipRegistry.publish(MOB, CONSUMER, ROUTE_A, 0, 100L);
        MandatoryOwnershipRegistry.release(MOB, MandatoryOwnershipRegistry.ReleaseReason.EXECUTOR_STARTED);
        MandatoryOwnershipRegistry.PublishResult successor =
                MandatoryOwnershipRegistry.publish(MOB, CONSUMER, ROUTE_A, 1, 200L);
        assertEquals(MandatoryOwnershipRegistry.PublishResult.ACCEPTED, successor);
    }

    /**
     * 6b — a genuinely different canonical route identity is a different episode: accepted
     * outright, generation not consulted. Exactly one distinct successor per change; repeating
     * the new pair with no intervening event is refused.
     */
    @Test
    void scenario6b_identityChangePublishesExactlyOneDistinctSuccessor() {
        MandatoryOwnershipRegistry.publish(MOB, CONSUMER, ROUTE_A, 0, 100L);
        MandatoryOwnershipRegistry.publish(MOB, CONSUMER, ROUTE_B, 0, 200L);
        assertTrue(MandatoryOwnershipRegistry.liveClaim(MOB, 200L).isPresent(),
                "a different route identity is a different pair and is accepted");
        MandatoryOwnershipRegistry.PublishResult repeat =
                MandatoryOwnershipRegistry.publish(MOB, CONSUMER, ROUTE_B, 0, 300L);
        assertEquals(MandatoryOwnershipRegistry.PublishResult.REFUSED_SAME_ROUTE_GENERATION,
                repeat, "a repeat of the new pair with no intervening event is refused");
    }

    /**
     * 6c — same canonical identity + merely fresher observation (no minting event) authorizes
     * NO successor claim within task-52.
     */
    @Test
    void scenario6c_sameIdentityAndFreshObservationGrantsNoSuccessor() {
        MandatoryOwnershipRegistry.publish(MOB, CONSUMER, ROUTE_A, 0, 100L);
        MandatoryOwnershipRegistry.release(MOB, MandatoryOwnershipRegistry.ReleaseReason.ABANDONED);
        MandatoryOwnershipRegistry.PublishResult republish =
                MandatoryOwnershipRegistry.publish(MOB, CONSUMER, ROUTE_A, 0, 200L);
        assertEquals(MandatoryOwnershipRegistry.PublishResult.REFUSED_SAME_ROUTE_GENERATION,
                republish, "fresh observation of the same identity is not a reacquisition");
    }

    // ------------------------------------------------------------------ release reasons

    /** Only EXECUTOR_STARTED advances; ROUTE_HANDED_OFF and ABANDONED delete but never mint. */
    @Test
    void routeHandoffAndAbandonDoNotAdvanceGeneration() {
        MandatoryOwnershipRegistry.publish(MOB, CONSUMER, ROUTE_A, 0, 100L);
        MandatoryOwnershipRegistry.release(MOB, MandatoryOwnershipRegistry.ReleaseReason.ROUTE_HANDED_OFF);
        MandatoryOwnershipRegistry.PublishResult afterHandoff =
                MandatoryOwnershipRegistry.publish(MOB, CONSUMER, ROUTE_A, 0, 200L);
        assertEquals(MandatoryOwnershipRegistry.PublishResult.REFUSED_SAME_ROUTE_GENERATION,
                afterHandoff);

        MandatoryOwnershipRegistry.publish(MOB, CONSUMER, ROUTE_B, 0, 300L);
        MandatoryOwnershipRegistry.release(MOB, MandatoryOwnershipRegistry.ReleaseReason.ABANDONED);
        MandatoryOwnershipRegistry.PublishResult afterAbandon =
                MandatoryOwnershipRegistry.publish(MOB, CONSUMER, ROUTE_B, 0, 400L);
        assertEquals(MandatoryOwnershipRegistry.PublishResult.REFUSED_SAME_ROUTE_GENERATION,
                afterAbandon);
    }

    /** Ordinary release (goal finished, owner satisfied) deletes the claim. */
    @Test
    void ordinaryReleaseDeletesTheClaim() {
        MandatoryOwnershipRegistry.publish(MOB, CONSUMER, ROUTE_A, 0, 100L);
        MandatoryOwnershipRegistry.release(MOB, MandatoryOwnershipRegistry.ReleaseReason.ORDINARY);
        assertTrue(MandatoryOwnershipRegistry.liveClaim(MOB, 200L).isEmpty());
    }

    // ------------------------------------------------------------------ lifetime (RET-1)

    /** Scenario 11 — unload / dimension transfer / server stop releases the runtime claim. */
    @Test
    void scenario11_unloadAndServerStopRemoveTheClaim() {
        MandatoryOwnershipRegistry.publish(MOB, CONSUMER, ROUTE_A, 0, 100L);
        MandatoryOwnershipRegistry.release(MOB, MandatoryOwnershipRegistry.ReleaseReason.ORDINARY);
        assertTrue(MandatoryOwnershipRegistry.liveClaim(MOB, 200L).isEmpty(),
                "entity unload/dimension change releases the claim");

        MandatoryOwnershipRegistry.publish(MOB, CONSUMER, ROUTE_A, 0, 300L);
        MandatoryOwnershipRegistry.shutdownServerState();
        assertTrue(MandatoryOwnershipRegistry.liveClaim(MOB, 400L).isEmpty(),
                "server stop clears the store");
    }

    /** Scenario 12 — a restart resurrects nothing: the store is runtime-only, empty on boot. */
    @Test
    void scenario12_restartResurrectsNothing() {
        MandatoryOwnershipRegistry.publish(MOB, CONSUMER, ROUTE_A, 0, 100L);
        MandatoryOwnershipRegistry.shutdownServerState();
        assertTrue(MandatoryOwnershipRegistry.liveClaim(MOB, 500L).isEmpty(),
                "no stale claim may survive a restart");
    }

    /** RET-1a — an expired claim is deleted, not left as a live-but-predicate-false entry. */
    @Test
    void expiredClaimIsDeletedNotPredicateFalse() {
        MandatoryOwnershipRegistry.publish(MOB, CONSUMER, ROUTE_A, 0, 100L);
        long afterExpiry = 100L + MandatoryOwnershipRegistry.MAX_CLAIM_TICKS + 1L;
        assertTrue(MandatoryOwnershipRegistry.liveClaim(MOB, afterExpiry).isEmpty());
        assertEquals(0, MandatoryOwnershipRegistry.trackedClaimCount(),
                "expiry must remove the slot (RET-1a)");
    }

    /** One slot per mob: a new episode replaces, never appends. */
    @Test
    void oneSlotPerMobReplacesNotAppends() {
        MandatoryOwnershipRegistry.publish(MOB, CONSUMER, ROUTE_A, 0, 100L);
        MandatoryOwnershipRegistry.publish(MOB, CONSUMER, ROUTE_B, 0, 200L);
        assertEquals(1, MandatoryOwnershipRegistry.trackedClaimCount());
        Optional<MandatoryOwnershipClaim> claim = MandatoryOwnershipRegistry.liveClaim(MOB, 200L);
        assertTrue(claim.isPresent());
        assertTrue(claim.get().sameRoute(CONSUMER, ROUTE_B));
    }

    /** The remembered slot rejects a publish whose generation is NOT greater than remembered. */
    @Test
    void publishRefusesWhenGenerationIsNotGreaterThanRemembered() {
        MandatoryOwnershipRegistry.publish(MOB, CONSUMER, ROUTE_A, 2, 100L);
        MandatoryOwnershipRegistry.release(MOB, MandatoryOwnershipRegistry.ReleaseReason.EXECUTOR_STARTED);
        MandatoryOwnershipRegistry.PublishResult stale =
                MandatoryOwnershipRegistry.publish(MOB, CONSUMER, ROUTE_A, 2, 200L);
        assertEquals(MandatoryOwnershipRegistry.PublishResult.REFUSED_SAME_ROUTE_GENERATION,
                stale, "generation must strictly exceed the remembered generation");
    }

    /** A claim is only live within its expiry window. */
    @Test
    void claimIsLiveOnlyWithinItsExpiryWindow() {
        MandatoryOwnershipRegistry.publish(MOB, CONSUMER, ROUTE_A, 0, 100L);
        assertTrue(MandatoryOwnershipRegistry.liveClaim(MOB, 100L).isPresent());
        assertTrue(MandatoryOwnershipRegistry.liveClaim(MOB, 100L + MandatoryOwnershipRegistry.MAX_CLAIM_TICKS - 1L)
                .isPresent());
        assertTrue(MandatoryOwnershipRegistry.liveClaim(MOB, 100L + MandatoryOwnershipRegistry.MAX_CLAIM_TICKS)
                .isEmpty());
    }
}
