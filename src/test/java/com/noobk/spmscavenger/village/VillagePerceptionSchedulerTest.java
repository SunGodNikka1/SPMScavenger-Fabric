package com.noobk.spmscavenger.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * V1-D scheduler contracts — B-VR-57 fair admission and global POI-query budget.
 */
class VillagePerceptionSchedulerTest {

    private static final ResourceKey<Level> OVERWORLD = Level.OVERWORLD;
    private static final ResourceKey<Level> NETHER = Level.NETHER;

    @Test
    void mustHappen_allDirtyMobsAdmittedWithinObserverBound() {
        AtomicInteger queries = new AtomicInteger();
        VillagePerceptionScheduler scheduler = VillagePerceptionScheduler.createForTest(
                (dimension, level, mobId) -> queries.incrementAndGet(),
                VillagePerceptionTuning.MAX_EMERGENCY_PENDING);
        List<UUID> mobs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            UUID mobId = UUID.randomUUID();
            mobs.add(mobId);
            scheduler.registerObserver(mobId);
        }
        for (UUID mobId : mobs) {
            assertTrue(scheduler.requestObservation(OVERWORLD, mobId));
        }
        assertEquals(10, scheduler.pendingCount());
        assertEquals(10, scheduler.registeredObserverCount());
    }

    @Test
    void mustHappen_duplicateRequestCoalescesToOnePendingEntry() {
        VillagePerceptionScheduler scheduler = VillagePerceptionScheduler.createForTest(
                (dimension, level, mobId) -> {}, VillagePerceptionTuning.MAX_EMERGENCY_PENDING);
        UUID mobId = UUID.randomUUID();
        scheduler.registerObserver(mobId);
        assertTrue(scheduler.requestObservation(OVERWORLD, mobId));
        assertTrue(scheduler.requestObservation(OVERWORLD, mobId));
        assertEquals(1, scheduler.pendingCount());
    }

    @Test
    void mustHappen_globalBudgetOneQueryPerServerTick() {
        AtomicInteger queries = new AtomicInteger();
        VillagePerceptionScheduler scheduler = VillagePerceptionScheduler.createForTest(
                (dimension, level, mobId) -> queries.incrementAndGet(),
                VillagePerceptionTuning.MAX_EMERGENCY_PENDING);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        scheduler.registerObserver(first);
        scheduler.registerObserver(second);
        scheduler.requestObservation(OVERWORLD, first);
        scheduler.requestObservation(OVERWORLD, second);
        scheduler.serviceUpToForTest(1, dimension -> null);
        assertEquals(1, queries.get());
        scheduler.serviceUpToForTest(1, dimension -> null);
        assertEquals(2, queries.get());
        assertEquals(0, scheduler.pendingCount());
    }

    @Test
    void mustHappen_dimensionsServicedRoundRobinUnderGlobalBudget() {
        AtomicInteger overworldQueries = new AtomicInteger();
        AtomicInteger netherQueries = new AtomicInteger();
        VillagePerceptionScheduler scheduler = VillagePerceptionScheduler.createForTest(
                (dimension, level, mobId) -> {
                    if (dimension.equals(OVERWORLD)) {
                        overworldQueries.incrementAndGet();
                    } else if (dimension.equals(NETHER)) {
                        netherQueries.incrementAndGet();
                    }
                },
                VillagePerceptionTuning.MAX_EMERGENCY_PENDING);
        UUID overworldMob = UUID.randomUUID();
        UUID netherMob = UUID.randomUUID();
        scheduler.registerObserver(overworldMob);
        scheduler.registerObserver(netherMob);
        scheduler.requestObservation(OVERWORLD, overworldMob);
        scheduler.requestObservation(NETHER, netherMob);
        scheduler.serviceUpToForTest(1, dimension -> null);
        assertEquals(1, overworldQueries.get() + netherQueries.get());
        scheduler.serviceUpToForTest(1, dimension -> null);
        assertEquals(1, overworldQueries.get());
        assertEquals(1, netherQueries.get());
    }

    @Test
    void mustHappen_unregisterRemovesPendingWithoutServicing() {
        AtomicInteger queries = new AtomicInteger();
        VillagePerceptionScheduler scheduler = VillagePerceptionScheduler.createForTest(
                (dimension, level, mobId) -> queries.incrementAndGet(),
                VillagePerceptionTuning.MAX_EMERGENCY_PENDING);
        UUID mobId = UUID.randomUUID();
        scheduler.registerObserver(mobId);
        scheduler.requestObservation(OVERWORLD, mobId);
        scheduler.unregisterObserver(mobId);
        assertEquals(0, scheduler.pendingCount());
        scheduler.serviceUpToForTest(1, dimension -> null);
        assertEquals(0, queries.get());
    }

    @Test
    void mustHappen_emergencyCapRefusesAdmissionMobStaysRetryable() {
        VillagePerceptionScheduler scheduler = VillagePerceptionScheduler.createForTest(
                (dimension, level, mobId) -> {}, 2);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        scheduler.registerObserver(first);
        scheduler.registerObserver(second);
        assertTrue(scheduler.requestObservation(OVERWORLD, first));
        assertTrue(scheduler.requestObservation(OVERWORLD, second));
        assertFalse(scheduler.requestObservation(OVERWORLD, third));
        assertEquals(2, scheduler.pendingCount());
    }
}
