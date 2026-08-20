package com.noobk.spmscavenger.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noobk.spmscavenger.opinion.InvalidationCause;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * D-VR-084 / task-52 — the shared discretionary-permission decision.
 *
 * <p>The four states, asserted at the pure decision boundary: running mandatory work blocks,
 * a live published pending claim blocks, an unclaimed demand does not block, and an expired or
 * released claim does not block. The running arm must delegate to
 * {@code DiscretionaryEligibility}; the pending arm is claim-based and never demand-based.
 */
class MandatoryOwnershipTest {

    private static final UUID MOB = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final ResourceLocation CONSUMER =
            ResourceLocation.fromNamespaceAndPath("spmscavenger", "iron_tool_frontier");
    private static final String ROUTE = "iron:raw_iron";

    private static ActivityObservationService.Observation observing(ActivityClass... classes) {
        return ActivityObservationService.summarize(List.of(classes));
    }

    private static MandatoryOwnershipClaim liveClaim(long now, long expiresAt) {
        return new MandatoryOwnershipClaim(MOB, CONSUMER, ROUTE, 0, now, expiresAt);
    }

    // ------------------------------------------------------------------ scenario 1-4, 7, 9, 10

    /** Scenario 1 — RUNNING mandatory work blocks discretionary. */
    @Test
    void scenario1_runningMandatoryWorkDeniesDiscretionary() {
        MandatoryOwnership.Permission p = MandatoryOwnership.evaluate(
                observing(ActivityClass.SCAVENGE_WORK), false, Optional.empty(), 100L);
        assertFalse(p.eligible());
        assertEquals(InvalidationCause.MANDATORY_AUTHORITY, p.cause());
    }

    /** Scenario 2 — a live published pending claim blocks discretionary. */
    @Test
    void scenario2_livePendingClaimDeniesDiscretionary() {
        MandatoryOwnership.Permission p = MandatoryOwnership.evaluate(
                observing(), false, Optional.of(liveClaim(100L, 500L)), 100L);
        assertFalse(p.eligible());
        assertEquals(InvalidationCause.MANDATORY_PENDING_CLAIM, p.cause());
    }

    /**
     * Scenario 3 — demand exists, nobody claims: discretionary is ALLOWED.
     *
     * <p>This is the frozen-demand gate. A claim-less demand must not block; a naive
     * "demand exists -> block" repair fails this test by construction.
     */
    @Test
    void scenario3_demandExistsNobodyClaimsAllowsDiscretionary() {
        MandatoryOwnership.Permission p = MandatoryOwnership.evaluate(
                observing(), false, Optional.empty(), 100L);
        assertTrue(p.eligible());
        assertEquals(InvalidationCause.NONE, p.cause());
    }

    /** Scenario 4 — an expired claim does not block discretionary. */
    @Test
    void scenario4_claimExpiresWithoutProgressAllowsDiscretionary() {
        MandatoryOwnership.Permission p = MandatoryOwnership.evaluate(
                observing(), false, Optional.of(liveClaim(100L, 200L)), 300L);
        assertTrue(p.eligible());
    }

    /** Scenario 7 — after release, discretionary is allowed immediately. */
    @Test
    void scenario7_releasedClaimAllowsDiscretionary() {
        MandatoryOwnership.Permission p = MandatoryOwnership.evaluate(
                observing(), false, Optional.empty(), 100L);
        assertTrue(p.eligible());
    }

    /** Scenario 9 — an unknown running goal fails closed. */
    @Test
    void scenario9_unknownRunningGoalFailsClosed() {
        MandatoryOwnership.Permission p = MandatoryOwnership.evaluate(
                observing(ActivityClass.UNKNOWN_ACTIVE), false, Optional.empty(), 100L);
        assertFalse(p.eligible());
        assertEquals(InvalidationCause.UNKNOWN_ACTIVE, p.cause());
    }

    /**
     * Scenario 10 — an owner that forgets to publish leaves the pending side open: no claim,
     * no running work, discretionary allowed. This is the deliberate fail-open direction.
     */
    @Test
    void scenario10_ownerForgetsToPublishFailsOpen() {
        MandatoryOwnership.Permission p = MandatoryOwnership.evaluate(
                observing(), false, Optional.empty(), 100L);
        assertTrue(p.eligible());
    }

    /** Scenario 8 — VILLAGE_TRADE running blocks discretionary (D-VR-082-A1 item 2). */
    @Test
    void scenario8_villageTradeRunningDeniesDiscretionary() {
        MandatoryOwnership.Permission p = MandatoryOwnership.evaluate(
                observing(ActivityClass.VILLAGE_TRADE), false, Optional.empty(), 100L);
        assertFalse(p.eligible());
        assertEquals(InvalidationCause.MANDATORY_AUTHORITY, p.cause());
    }

    /** Combat remains an immediate hard block regardless of claims. */
    @Test
    void combatTargetAlwaysDenies() {
        MandatoryOwnership.Permission p = MandatoryOwnership.evaluate(
                observing(), true, Optional.of(liveClaim(100L, 500L)), 100L);
        assertFalse(p.eligible());
        assertEquals(InvalidationCause.COMBAT_TARGET, p.cause());
    }

    /**
     * Negative control 3 — the running arm must DELEGATE to DiscretionaryEligibility, never
     * re-derive the blocking set locally. Removing the delegation call fails this test.
     */
    @Test
    void negativeControl_runningArmDelegatesToDiscretionaryEligibility() throws Exception {
        String body = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/noobk/spmscavenger/activity/MandatoryOwnership.java"));
        assertTrue(body.contains("DiscretionaryEligibility.isDiscretionaryEligible"),
                "running arm must consume DiscretionaryEligibility.isDiscretionaryEligible");
        assertTrue(body.contains("DiscretionaryEligibility.invalidationForObservation"),
                "running arm must consume DiscretionaryEligibility.invalidationForObservation");
    }
}
