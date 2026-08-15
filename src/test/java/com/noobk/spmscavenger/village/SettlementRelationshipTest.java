package com.noobk.spmscavenger.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/** V1.5 — relationship persistence, rekey, merge, and eviction sync (D-VR-049). */
class SettlementRelationshipTest {

    private static ObservationQuality complete(int admitted) {
        return ObservationQuality.fullCoverage(admitted);
    }

    private static BlockPos far(int index) {
        return new BlockPos(index * 500, 64, 0);
    }

    @Test
    void mustHappen_relationshipRoundTripsThroughNbt() {
        MobVillageMemory memory = new MobVillageMemory();
        memory.remember(far(0), 10L, complete(5));
        memory.putRelationship(far(0), new SettlementRelationship(250, 10L, 2));

        MobVillageMemory reloaded = MobVillageMemory.load(memory.save());
        SettlementRelationship relationship = reloaded.relationshipAt(far(0)).orElseThrow();
        assertEquals(250, relationship.familiarityScore());
        assertEquals(AttachmentBand.MEDIUM, relationship.attachmentBand());
    }

    @Test
    void mustHappen_rekeyMovesRelationshipWhenAnchorSupersedes() {
        MobVillageMemory memory = new MobVillageMemory();
        BlockPos weak = new BlockPos(0, 64, 0);
        memory.remember(weak, 100L, ObservationQuality.withCoverage(4, 24, 4));
        memory.putRelationship(weak, new SettlementRelationship(300, 100L, 1));

        BlockPos strong = new BlockPos(24, 64, 16);
        memory.remember(strong, 300L, complete(25));

        assertTrue(memory.relationshipAt(strong).isPresent());
        assertEquals(300, memory.relationshipAt(strong).orElseThrow().familiarityScore());
        assertEquals(
                300,
                memory.relationshipAt(weak).orElseThrow().familiarityScore(),
                "identity merge resolves relationship through canonical anchor");
    }

    @Test
    void mustNotHappen_evictionLeavesOrphanRelationship() {
        MobVillageMemory memory = new MobVillageMemory();
        for (int i = 0; i < MobVillageMemory.MAX_KNOWN_VILLAGES; i++) {
            memory.remember(far(i), 1000L + i, complete(5));
            memory.putRelationship(far(i), new SettlementRelationship(50 + i, 1000L + i, 0));
        }
        memory.remember(far(999), 9999L, complete(5));
        assertFalse(memory.relationshipAt(far(0)).isPresent(), "stale village relationship evicted with village");
    }

    @Test
    void mustHappen_attachmentBandDerivedNotStored() {
        SettlementRelationship low = new SettlementRelationship(50, 1L, 0);
        SettlementRelationship high = new SettlementRelationship(650, 1L, 3);
        assertEquals(AttachmentBand.LOW, low.attachmentBand());
        assertEquals(AttachmentBand.HIGH, high.attachmentBand());
    }
}
