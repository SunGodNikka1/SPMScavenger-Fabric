package com.noobk.spmscavenger.village;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V4-F — first-home promotion is a one-shot consequence of one real sleep event. */
class FirstHomePromotionTest {

    private static final BlockPos BED = new BlockPos(0, 64, 0);
    private static final BlockPos VILLAGE_A = new BlockPos(20, 64, 0);
    private static final BlockPos VILLAGE_B = new BlockPos(-40, 64, 0);

    @Test
    void successfulSleepAtHighInvokesTheCanonicalWriterExactlyOnce() {
        MobVillageMemory memory = remembered(VILLAGE_A, 600);
        AtomicInteger writes = new AtomicInteger();

        assertTrue(FirstHomePromotion.promote(
                true, BED, memory, anchor -> {
                    writes.incrementAndGet();
                    return memory.designateHome(anchor);
                }));

        assertEquals(1, writes.get());
        assertEquals(VILLAGE_A, memory.homeAnchor().orElseThrow());
    }

    @Test
    void thresholdIsInclusiveAt600AndRejects599() {
        MobVillageMemory below = remembered(VILLAGE_A, 599);
        assertTrue(FirstHomePromotion.eligibleAnchor(true, BED, below).isEmpty());

        MobVillageMemory exact = remembered(VILLAGE_A, 600);
        assertEquals(VILLAGE_A,
                FirstHomePromotion.eligibleAnchor(true, BED, exact).orElseThrow());
    }

    @Test
    void failedStartSleepingCannotPromote() {
        MobVillageMemory memory = remembered(VILLAGE_A, 600);
        AtomicInteger writes = new AtomicInteger();

        assertFalse(FirstHomePromotion.promote(
                false, BED, memory, ignored -> {
                    writes.incrementAndGet();
                    return true;
                }));
        assertEquals(0, writes.get());
        assertTrue(memory.homeAnchor().isEmpty());
    }

    @Test
    void unknownOrAmbiguousBedAssociationFailsClosedWithoutCreatingMemory() {
        MobVillageMemory unknown = new MobVillageMemory();
        assertTrue(FirstHomePromotion.eligibleAnchor(true, BED, unknown).isEmpty());
        assertEquals(0, unknown.size());

        MobVillageMemory unrelated = remembered(new BlockPos(500, 64, 0), 600);
        assertTrue(FirstHomePromotion.eligibleAnchor(true, BED, unrelated).isEmpty(),
                "a remembered settlement outside the bed association envelope must not match");
        assertEquals(1, unrelated.size(), "association must not create or discard memory");

        MobVillageMemory ambiguous = remembered(VILLAGE_A, 600);
        ambiguous.remember(VILLAGE_B, 2L, ObservationQuality.fullCoverage(5));
        ambiguous.putRelationship(VILLAGE_B, new SettlementRelationship(800, 2L, 0));
        assertTrue(FirstHomePromotion.eligibleAnchor(true, BED, ambiguous).isEmpty(),
                "two remembered settlements inside the activity envelope must not become nearest-wins");
        assertEquals(2, ambiguous.size());
    }

    @Test
    void existingHomeNeverRehomesForSleepFamiliarityOpinionOrTraderFacts() {
        BlockPos existingHome = new BlockPos(500, 64, 0);
        MobVillageMemory memory = remembered(existingHome, 600);
        memory.remember(VILLAGE_A, 2L, ObservationQuality.fullCoverage(10));
        memory.putRelationship(VILLAGE_A, new SettlementRelationship(1_000, 2L, 0));
        assertTrue(memory.designateHome(existingHome));

        assertTrue(FirstHomePromotion.eligibleAnchor(true, BED, memory).isEmpty());
        assertEquals(existingHome, memory.homeAnchor().orElseThrow());
    }

    @Test
    void familiarityChangeAfterOldSleepDoesNotRetroactivelyPromoteButNextSleepDoes() {
        MobVillageMemory memory = remembered(VILLAGE_A, 550);
        assertFalse(FirstHomePromotion.promote(
                true, BED, memory, memory::designateHome));
        assertTrue(memory.homeAnchor().isEmpty());

        memory.putRelationship(VILLAGE_A, new SettlementRelationship(600, 2L, 0));
        assertTrue(memory.homeAnchor().isEmpty(), "there is no slept-here latch to replay later");

        assertTrue(FirstHomePromotion.promote(
                true, BED, memory, memory::designateHome));
        assertEquals(VILLAGE_A, memory.homeAnchor().orElseThrow());
    }

    @Test
    void promotedHomeRoundTripsAndRekeysToTheCanonicalSupersedingAnchor() {
        MobVillageMemory memory = remembered(VILLAGE_A, 600);
        assertTrue(FirstHomePromotion.promote(true, BED, memory, memory::designateHome));

        MobVillageMemory loaded = MobVillageMemory.load(memory.save());
        assertEquals(VILLAGE_A, loaded.homeAnchor().orElseThrow());

        BlockPos superseding = new BlockPos(30, 64, 10);
        loaded.remember(superseding, 100L, ObservationQuality.fullCoverage(30));
        assertEquals(superseding, loaded.homeAnchor().orElseThrow());
        assertEquals(600, loaded.relationshipAt(superseding).orElseThrow().familiarityScore());
    }

    @Test
    void productionWiringUsesTheRealSleepEdgeAndExistingWriterOnly() throws Exception {
        String shelter = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/goal/SeekShelterGoal.java"));
        String promotion = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/village/FirstHomePromotion.java"));
        String savedData = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/village/VillageMemorySavedData.java"));

        int start = shelter.indexOf("mob.startSleeping(bedPos);");
        int sleepingGuard = shelter.indexOf("mob.isSleeping()", start);
        int promotionCall = shelter.indexOf("FirstHomePromotion", start);
        assertTrue(start >= 0 && sleepingGuard > start && promotionCall > sleepingGuard,
                "promotion must occur only after the real sleep call and successful isSleeping guard");
        assertTrue(promotion.contains("peekInDimension") && promotion.contains(".peek("),
                "association must read existing memory without computeIfAbsent/memoryOf");
        assertFalse(promotion.contains("memoryOf(") || promotion.contains(".record("),
                "sleep must not manufacture settlement memory");
        assertTrue(promotion.contains(".designateHome("),
                "the existing SavedData writer owns home and relationship effects");
        assertEquals(1, occurrences(promotion, ".designateHome("),
                "the sleep edge must delegate through the canonical writer exactly once");
        assertFalse(promotion.contains("SettlementRelationshipService")
                        || promotion.contains("setDirty("),
                "V4-F must not duplicate canonical writer effects");
        assertEquals(1, occurrences(savedData,
                        "SettlementRelationshipService.onHomeDesignated("),
                "the canonical writer must retain exactly one relationship-effect call");
        assertFalse(promotion.contains("Opinion") || promotion.contains("KnownVillager"),
                "preference and trader evidence cannot become re-home inputs");
        for (String forbidden : new String[] {
                "sleptHere", "lastVillageSleep", "pendingHomePromotion", "sleepCounter"}) {
            assertFalse(promotion.contains(forbidden), "forbidden sleep latch: " + forbidden);
        }
    }

    private static int occurrences(String source, String needle) {
        return (source.length() - source.replace(needle, "").length()) / needle.length();
    }

    private static MobVillageMemory remembered(BlockPos anchor, int familiarity) {
        MobVillageMemory memory = new MobVillageMemory();
        memory.remember(anchor, 1L, ObservationQuality.fullCoverage(5));
        memory.putRelationship(anchor, new SettlementRelationship(familiarity, 1L, 0));
        return memory;
    }
}
