package com.noobk.spmscavenger.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
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
    void mustHappen_presenceHeartbeatBootstrapsRelationshipRow() {
        MobVillageMemory memory = new MobVillageMemory();
        BlockPos anchor = far(0);
        memory.remember(anchor, 100L, complete(5));
        long tick = 150L;

        boolean bootstrap = memory.relationshipAt(anchor).isEmpty();
        SettlementRelationship relationship = memory.relationshipAt(anchor)
                .orElseGet(SettlementRelationship::empty);
        assertFalse(!bootstrap && tick - relationship.lastVisitTick() < SettlementTuning.PRESENCE_HEARTBEAT_TICKS);
        relationship.bumpPresenceFamiliarity(SettlementTuning.PRESENCE_FAMILIARITY_BUMP, tick);
        memory.putRelationship(anchor, relationship);

        assertEquals(5, memory.relationshipAt(anchor).orElseThrow().familiarityScore());
        assertEquals(5, memory.relationshipAt(anchor).orElseThrow().presenceFamiliarity());
    }

    @Test
    void mustHappen_firstVisitRecordBootstrapsRelationshipRow() {
        MobVillageMemory memory = new MobVillageMemory();
        BlockPos anchor = far(0);
        memory.remember(anchor, 500L, complete(5));
        assertTrue(memory.relationshipAt(anchor).isEmpty());

        boolean bootstrap = memory.relationshipAt(anchor).isEmpty();
        SettlementRelationship relationship = memory.relationshipAt(anchor)
                .orElseGet(SettlementRelationship::empty);
        assertTrue(bootstrap || 500L - relationship.lastVisitTick() >= SettlementTuning.VISIT_STALE_TICKS);
        relationship.bumpFamiliarity(SettlementTuning.VISIT_FAMILIARITY_BUMP, 500L);
        memory.putRelationship(anchor, relationship);

        assertEquals(
                SettlementTuning.VISIT_FAMILIARITY_BUMP,
                memory.relationshipAt(anchor).orElseThrow().familiarityScore());
    }

    @Test
    void mustNotHappen_emptySeededAtCurrentTickBlocksStaleGateForever() {
        long tick = 1_000L;
        SettlementRelationship seededAtNow = new SettlementRelationship(0, tick, 0);
        assertEquals(
                0L,
                tick - seededAtNow.lastVisitTick(),
                "old empty(tick) pattern makes age zero forever");
        assertFalse(tick - seededAtNow.lastVisitTick() >= SettlementTuning.VISIT_STALE_TICKS);

        SettlementRelationship neverBumped = SettlementRelationship.empty();
        assertEquals(0L, neverBumped.lastVisitTick());
        assertTrue(tick - neverBumped.lastVisitTick() >= SettlementTuning.VISIT_STALE_TICKS);
    }

    @Test
    void mustNotHappen_loadRestoresRelationshipForEvictedVillage() {
        CompoundTag tag = new CompoundTag();
        ListTag villageList = new ListTag();
        ListTag relationshipList = new ListTag();
        for (int i = 0; i <= MobVillageMemory.MAX_KNOWN_VILLAGES; i++) {
            BlockPos anchor = far(i);
            villageList.add(KnownVillage.discovered(anchor, 1000L + i, complete(5)).save());
            CompoundTag row = new CompoundTag();
            row.put("anchor", NbtUtils.writeBlockPos(anchor));
            row.put("relationship", new SettlementRelationship(50 + i, 1000L + i, 0).save());
            relationshipList.add(row);
        }
        tag.put("villages", villageList);
        tag.put("relationships", relationshipList);

        MobVillageMemory reloaded = MobVillageMemory.load(tag);
        assertEquals(MobVillageMemory.MAX_KNOWN_VILLAGES, reloaded.size());
        assertFalse(
                reloaded.relationshipAt(far(0)).isPresent(),
                "evicted village relationship must not resurrect on load");
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
    void mustHappen_presenceFamiliarityRoundTripsThroughNbt() {
        MobVillageMemory memory = new MobVillageMemory();
        memory.remember(far(0), 10L, complete(5));
        memory.putRelationship(far(0), new SettlementRelationship(220, 10L, 0, 200));

        MobVillageMemory reloaded = MobVillageMemory.load(memory.save());
        SettlementRelationship relationship = reloaded.relationshipAt(far(0)).orElseThrow();
        assertEquals(220, relationship.familiarityScore());
        assertEquals(200, relationship.presenceFamiliarity());
    }

    @Test
    void mustHappen_attachmentBandDerivedNotStored() {
        SettlementRelationship low = new SettlementRelationship(50, 1L, 0);
        SettlementRelationship high = new SettlementRelationship(650, 1L, 3);
        assertEquals(AttachmentBand.LOW, low.attachmentBand());
        assertEquals(AttachmentBand.HIGH, high.attachmentBand());
    }
}
