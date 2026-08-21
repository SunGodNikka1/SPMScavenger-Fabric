package com.noobk.spmscavenger.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noobk.spmscavenger.activity.ActivityClass;
import com.noobk.spmscavenger.activity.ActivityObservationService;
import com.noobk.spmscavenger.activity.MandatoryOwnershipClaim;
import com.noobk.spmscavenger.opinion.InvalidationCause;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Task-53 — VillageWorkAdmission scenarios 1–6 (VR-T3j authority/profile). */
class VillageWorkAdmissionTest {

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

    /** Scenario 1 — pending Gather claim denies village work with exact authority cause. */
    @Test
    void scenario1_pendingClaimDeniedWithMandatoryPendingCause() {
        VillageWorkAdmission.Result result = VillageWorkAdmission.evaluate(
                VillageScenarioProfile.VILLAGE_ALLY,
                observing(),
                false,
                Optional.of(liveClaim(100L, 500L)),
                100L);
        assertFalse(result.permitted());
        assertEquals(VillageWorkAdmission.DenyCause.DENY_MANDATORY_AUTHORITY, result.cause());
        assertEquals(InvalidationCause.MANDATORY_PENDING_CLAIM, result.authorityCause());
    }

    /** Scenario 2 — running Gather denies with MANDATORY_AUTHORITY. */
    @Test
    void scenario2_runningGatherDenied() {
        VillageWorkAdmission.Result result = VillageWorkAdmission.evaluate(
                VillageScenarioProfile.VILLAGE_ALLY,
                observing(ActivityClass.SCAVENGE_WORK),
                false,
                Optional.empty(),
                100L);
        assertFalse(result.permitted());
        assertEquals(VillageWorkAdmission.DenyCause.DENY_MANDATORY_AUTHORITY, result.cause());
        assertEquals(InvalidationCause.MANDATORY_AUTHORITY, result.authorityCause());
    }

    /** Scenario 3 — running village trade denies with MANDATORY_AUTHORITY. */
    @Test
    void scenario3_runningTradeDenied() {
        VillageWorkAdmission.Result result = VillageWorkAdmission.evaluate(
                VillageScenarioProfile.VILLAGE_ALLY,
                observing(ActivityClass.VILLAGE_TRADE),
                false,
                Optional.empty(),
                100L);
        assertFalse(result.permitted());
        assertEquals(VillageWorkAdmission.DenyCause.DENY_MANDATORY_AUTHORITY, result.cause());
        assertEquals(InvalidationCause.MANDATORY_AUTHORITY, result.authorityCause());
    }

    /**
     * Scenario 4 — fail-open third state: demand exists, no claim, no running mandatory executor.
     *
     * <p>RED-before-GREEN gate: this must fail if admission re-implements demand-based blocking.
     */
    @Test
    void scenario4_unclaimedDemandAllowsAlly() {
        VillageWorkAdmission.Result result = VillageWorkAdmission.evaluate(
                VillageScenarioProfile.VILLAGE_ALLY,
                observing(),
                false,
                Optional.empty(),
                100L);
        assertTrue(result.permitted());
        assertEquals(VillageWorkAdmission.DenyCause.NONE, result.cause());
        assertEquals(InvalidationCause.NONE, result.authorityCause());
    }

    /**
     * Scenario 5 — NEUTRAL (absent row) denies regardless of mandatory clearance.
     *
     * <p>RED-before-GREEN gate.
     */
    @Test
    void scenario5_neutralProfileDenied() {
        VillageWorkAdmission.Result result = VillageWorkAdmission.evaluate(
                VillageScenarioProfile.NEUTRAL,
                observing(),
                false,
                Optional.empty(),
                100L);
        assertFalse(result.permitted());
        assertEquals(VillageWorkAdmission.DenyCause.DENY_PROFILE, result.cause());
        assertEquals(InvalidationCause.NONE, result.authorityCause());
    }

    /** Scenario 6 — server-canonical profile read is dimension-independent (instance core). */
    @Test
    void scenario6_canonicalStoreIsSingleSourceOfTruth() {
        PlayerMobVillagePolicySavedData store = new PlayerMobVillagePolicySavedData();
        store.assignAlly(MOB);
        assertEquals(VillageScenarioProfile.VILLAGE_ALLY, store.readProfile(MOB));
        assertEquals(VillageScenarioProfile.VILLAGE_ALLY, store.readProfile(MOB),
                "same store regardless of which dimension context would call profileOf(server, …)");
    }
}
