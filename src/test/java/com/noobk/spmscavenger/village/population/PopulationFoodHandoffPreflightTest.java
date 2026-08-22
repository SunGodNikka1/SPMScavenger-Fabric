package com.noobk.spmscavenger.village.population;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noobk.spmscavenger.village.MobVillageMemory;
import com.noobk.spmscavenger.village.ObservationQuality;
import com.noobk.spmscavenger.village.PopulationFoodSupportAdmission;
import com.noobk.spmscavenger.village.work.SettlementIdentity;
import com.noobk.spmscavenger.village.work.VillageWorkFacts;
import com.noobk.spmscavenger.village.work.VillageWorkTuning;
import com.noobk.spmscavenger.village.work.WorkFactsCompleteness;
import com.noobk.spmscavenger.village.work.WorkFactsFreshness;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** CLOSE-57-2 / CLOSE-57-3 — handoff preflight evidence and distance gates. */
class PopulationFoodHandoffPreflightTest {

    private static final BlockPos ANCHOR_A = new BlockPos(0, 64, 0);
    private static final BlockPos ANCHOR_B = new BlockPos(48, 64, 0);

    @Test
    void close57_2_supersededAnchorIsNotRemembered() {
        MobVillageMemory memory = new MobVillageMemory();
        memory.remember(ANCHOR_B, 100L, ObservationQuality.fullCoverage(5));
        SettlementIdentity retired = SettlementIdentity.of(Level.OVERWORLD, ANCHOR_A);
        assertFalse(PopulationFoodSupportAdmission.settlementStillRemembered(memory, retired));
    }

    @Test
    void close57_2_exactAnchorStillRemembered() {
        MobVillageMemory memory = new MobVillageMemory();
        memory.remember(ANCHOR_A, 100L, ObservationQuality.fullCoverage(5));
        SettlementIdentity current = SettlementIdentity.of(Level.OVERWORLD, ANCHOR_A);
        assertTrue(PopulationFoodSupportAdmission.settlementStillRemembered(memory, current));
    }

    @Test
    void close57_2_staleCurrentCacheFailsEvenWhenPlanFactsWereFresh() {
        MobVillageMemory memory = new MobVillageMemory();
        memory.remember(ANCHOR_A, 100L, ObservationQuality.fullCoverage(5));
        SettlementIdentity identity = SettlementIdentity.of(Level.OVERWORLD, ANCHOR_A);
        VillageWorkFacts freshAtSelect = sampleFacts(identity, 2, 1, 50L);
        long staleTick = 50L + VillageWorkTuning.FRESHNESS_WINDOW_TICKS + 1;
        assertFalse(PopulationFoodSupportAdmission.currentSettlementEvidence(
                memory, identity, Optional.of(freshAtSelect), staleTick));
    }

    @Test
    void close57_2_currentVacancyLossFailsDespiteSelectablePlanFacts() {
        MobVillageMemory memory = new MobVillageMemory();
        memory.remember(ANCHOR_A, 100L, ObservationQuality.fullCoverage(5));
        SettlementIdentity identity = SettlementIdentity.of(Level.OVERWORLD, ANCHOR_A);
        VillageWorkFacts noVacancy = sampleFacts(identity, 2, 0, 100L);
        assertFalse(PopulationFoodSupportAdmission.currentSettlementEvidence(
                memory, identity, Optional.of(noVacancy), 100L));
    }

    @Test
    void close57_2_missingCurrentCacheFailsClosed() {
        MobVillageMemory memory = new MobVillageMemory();
        memory.remember(ANCHOR_A, 100L, ObservationQuality.fullCoverage(5));
        SettlementIdentity identity = SettlementIdentity.of(Level.OVERWORLD, ANCHOR_A);
        assertFalse(PopulationFoodSupportAdmission.currentSettlementEvidence(
                memory, identity, Optional.empty(), 100L));
    }

    @Test
    void close57_2_negativeControl_planFactsFallbackWouldHavePassed() {
        MobVillageMemory memory = new MobVillageMemory();
        memory.remember(ANCHOR_A, 100L, ObservationQuality.fullCoverage(5));
        SettlementIdentity identity = SettlementIdentity.of(Level.OVERWORLD, ANCHOR_A);
        VillageWorkFacts planFacts = sampleFacts(identity, 2, 1, 50L);
        long staleTick = 50L + VillageWorkTuning.FRESHNESS_WINDOW_TICKS + 1;
        assertTrue(planFacts.isReadable(), "plan-captured facts looked fresh at SELECT");
        assertFalse(PopulationFoodSupportAdmission.currentSettlementEvidence(
                memory, identity, Optional.of(planFacts), staleTick),
                "handoff must not reuse plan.facts() when current peek is stale");
    }

    @Test
    void close57_2_handoffPreflightPeeksCurrentFactsNotPlanFacts() throws IOException {
        String body = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/village/PopulationFoodSupportAdmission.java"));
        assertTrue(body.contains("VillageWorkFactsService.peek(level, plan.settlement())"));
        assertFalse(body.contains("plan.facts()"));
    }

    @Test
    void close57_3_recipientOutsideReachFailsDistanceGate() {
        Vec3 mobPos = new Vec3(0.0, 64.0, 0.0);
        Vec3 near = new Vec3(1.0, 64.0, 0.0);
        Vec3 far = new Vec3(3.0, 64.0, 0.0);
        assertTrue(mobPos.distanceToSqr(near) < PopulationFoodTuning.REACH_DISTANCE_SQR);
        assertFalse(mobPos.distanceToSqr(far) < PopulationFoodTuning.REACH_DISTANCE_SQR);
    }

    @Test
    void close57_3_handoffPreflightCallsDistanceGate() throws IOException {
        String body = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/village/PopulationFoodSupportAdmission.java"));
        assertTrue(body.contains("withinHandoffDistance(mob, recipient)"));
        assertTrue(body.contains("distanceToSqr(recipient)"));
    }

    private static VillageWorkFacts sampleFacts(
            SettlementIdentity identity, int adults, int freeHomes, long observedTick) {
        return new VillageWorkFacts(
                identity,
                adults,
                4,
                4 - freeHomes,
                freeHomes,
                observedTick,
                WorkFactsCompleteness.COMPLETE,
                WorkFactsFreshness.FRESH);
    }
}
