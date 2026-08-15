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

    /**
     * P1 regression (User audit): {@code removePendingFor} removed from {@code lanes} while iterating
     * {@code lanes.entrySet()}.
     *
     * <p><b>Why the existing unregister test could not catch it.</b> {@code HashMap}'s iterator is
     * fail-fast in {@code next()}, not in {@code hasNext()}. With a single Overworld lane the removal
     * happened on the final entry, {@code hasNext()} then returned false, {@code next()} was never
     * called again, and no exception was thrown. The defect needs <b>two or more</b> dimension lanes
     * with the emptied one visited first — which is a property of the fixture, not of the assertion,
     * and no amount of strengthening the single-lane test would have found it.
     */
    @Test
    void mustNotHappen_unregisterConcurrentlyModifiesTheLaneMap() {
        VillagePerceptionScheduler scheduler = VillagePerceptionScheduler.createForTest(
                (dimension, level, mobId) -> {}, VillagePerceptionTuning.MAX_EMERGENCY_PENDING);

        UUID soleOverworldMob = UUID.randomUUID();
        scheduler.registerObserver(soleOverworldMob);
        scheduler.requestObservation(OVERWORLD, soleOverworldMob);

        // Several other lanes so the emptied one is not the last entry the iterator reaches.
        for (ResourceKey<Level> dimension : List.of(NETHER, Level.END)) {
            for (int i = 0; i < 3; i++) {
                UUID other = UUID.randomUUID();
                scheduler.registerObserver(other);
                scheduler.requestObservation(dimension, other);
            }
        }

        // Threw ConcurrentModificationException before the repair.
        scheduler.unregisterObserver(soleOverworldMob);

        // And the emptied lane is genuinely retired, not merely survived.
        AtomicInteger overworldServiced = new AtomicInteger();
        VillagePerceptionScheduler after = VillagePerceptionScheduler.createForTest(
                (dimension, level, mobId) -> {
                    if (dimension.equals(OVERWORLD)) {
                        overworldServiced.incrementAndGet();
                    }
                }, VillagePerceptionTuning.MAX_EMERGENCY_PENDING);
        UUID gone = UUID.randomUUID();
        after.registerObserver(gone);
        after.requestObservation(OVERWORLD, gone);
        after.unregisterObserver(gone);
        after.serviceUpToForTest(8, dimension -> null);
        assertEquals(0, overworldServiced.get(), "an unregistered mob must not be serviced");
    }

    /** Every lane emptying at once is the shape most likely to trip an iterator. */
    @Test
    void mustNotHappen_unregisterBreaksWhenItEmptiesEveryLane() {
        VillagePerceptionScheduler scheduler = VillagePerceptionScheduler.createForTest(
                (dimension, level, mobId) -> {}, VillagePerceptionTuning.MAX_EMERGENCY_PENDING);

        UUID everywhere = UUID.randomUUID();
        scheduler.registerObserver(everywhere);
        for (ResourceKey<Level> dimension : List.of(OVERWORLD, NETHER, Level.END)) {
            scheduler.requestObservation(dimension, everywhere);
        }

        scheduler.unregisterObserver(everywhere);

        AtomicInteger serviced = new AtomicInteger();
        VillagePerceptionScheduler probe = VillagePerceptionScheduler.createForTest(
                (dimension, level, mobId) -> serviced.incrementAndGet(),
                VillagePerceptionTuning.MAX_EMERGENCY_PENDING);
        probe.serviceUpToForTest(4, dimension -> null);
        assertEquals(0, serviced.get());
    }

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
