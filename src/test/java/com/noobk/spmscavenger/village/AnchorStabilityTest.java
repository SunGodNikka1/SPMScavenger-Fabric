package com.noobk.spmscavenger.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/**
 * V1-R2 — adversarial coverage for D-VR-024, raised in User review.
 *
 * <p>The acceptance rule replaces an anchor on an <em>equally complete and newer</em> observation, so
 * a sequence of individually valid observations could in principle walk the anchor across the map in
 * sub-{@link VillageIdentityPolicy#SAME_SETTLEMENT_RADIUS_SQR} steps — each step legal, the
 * accumulation not. These tests characterise what actually happens rather than asserting a hope.
 */
class AnchorStabilityTest {

    private static ObservationQuality complete(int admitted) {
        return new ObservationQuality(admitted, 0);
    }

    /**
     * The adversarial sequence: alternate two equally complete observations from opposite sides of
     * one village, forty times.
     *
     * <p><b>What this proves and what it does not.</b> It proves the anchor cannot accumulate drift
     * from alternating observations — it oscillates between the two reported anchors and returns
     * exactly to each, because {@code withObservation} <em>replaces</em> the stored anchor rather than
     * blending toward the new one. Averaging or nudging would have drifted; replacement cannot.
     *
     * <p>It does <b>not</b> prove the anchor is stable in a real world, where each observation's POI
     * set depends on where the mob stood — that is VR-T1, and the oscillation itself is still a real
     * concern for D-VR-019's agreement guarantee (below).
     */
    @Test
    void mustNotHappen_alternatingObservationsWalkTheAnchorAcrossTheMap() {
        MobVillageMemory memory = new MobVillageMemory();
        BlockPos west = new BlockPos(0, 64, 0);
        BlockPos east = new BlockPos(30, 64, 0);

        memory.remember(west, 100L, complete(20));
        for (int i = 1; i <= 40; i++) {
            memory.remember(i % 2 == 1 ? east : west, 100L + i * 100L, complete(20));
        }

        assertEquals(1, memory.size(), "still one settlement throughout");
        BlockPos settled = memory.villages().get(0).anchor();
        assertTrue(settled.equals(west) || settled.equals(east),
                "the anchor is one of the two observed anchors, not a third position drifted between "
                        + "them — got " + settled);
        assertEquals(west, memory.at(west).orElseThrow().anchor().equals(east) ? west : west,
                "and both original anchors still resolve to this settlement");
        assertTrue(memory.at(east).isPresent());
    }

    /**
     * The guarantee that actually matters: however the anchor oscillates, it must stay inside the
     * radius at which {@code ServerLevel#getRaidAt} would still find the raid — otherwise D-VR-010's
     * trigger becomes intermittent, which is worse than never firing because it is not reproducible.
     */
    @Test
    void mustHappen_oscillationStaysInsideTheRaidAssociationRadius() {
        MobVillageMemory memory = new MobVillageMemory();
        BlockPos raidCentre = new BlockPos(15, 64, 0);
        BlockPos west = new BlockPos(0, 64, 0);
        BlockPos east = new BlockPos(30, 64, 0);

        memory.remember(west, 100L, complete(20));
        for (int i = 1; i <= 20; i++) {
            memory.remember(i % 2 == 1 ? east : west, 100L + i * 100L, complete(20));
            assertTrue(
                    RaidAssociationPolicy.associated(memory.villages().get(0).anchor(), raidCentre),
                    "anchor left the raid association radius on step " + i);
        }
    }

    /**
     * A monotone walk in one direction — the worse version of the same worry. Each step is inside the
     * identity radius, so each merges; the question is whether twenty of them add up.
     *
     * <p>They do. This is a <b>real limitation</b>, recorded rather than papered over: replacement
     * means the anchor tracks the most recent equally-good observation, so a settlement that is
     * genuinely rebuilt progressively eastward will follow it — which is correct behaviour — and an
     * observation sequence that merely <em>looks</em> like that will also be followed, which is not.
     * Distinguishing them needs the POI-set-overlap identity deferred under D-VR-022. VR-T1 should
     * report whether real observation sequences produce this shape at all.
     */
    @Test
    void characterise_monotoneSequenceFollowsTheSettlement() {
        MobVillageMemory memory = new MobVillageMemory();
        memory.remember(new BlockPos(0, 64, 0), 100L, complete(20));
        for (int step = 1; step <= 20; step++) {
            memory.remember(new BlockPos(step * 20, 64, 0), 100L + step * 100L, complete(20));
        }
        assertEquals(1, memory.size(), "each 20-block step is inside the 48-block identity radius");
        assertEquals(400, memory.villages().get(0).anchor().getX(),
                "the anchor followed the sequence — documented limitation, see javadoc");
    }

    /** A worse view never moves the anchor, however long the sequence. */
    @Test
    void mustNotHappen_repeatedEdgeGlancesMoveTheAnchor() {
        MobVillageMemory memory = new MobVillageMemory();
        BlockPos good = new BlockPos(0, 64, 0);
        memory.remember(good, 100L, complete(30));
        for (int i = 1; i <= 50; i++) {
            memory.remember(new BlockPos(30, 64, 20), 100L + i * 100L, new ObservationQuality(2, 28));
        }
        assertEquals(good, memory.villages().get(0).anchor());
    }

    /**
     * User-raised: {@code BlockPos.distSqr} is 3D, so a vertically spread settlement can exceed the
     * 48-block identity sphere while being horizontally compact.
     *
     * <p>This test <b>documents the exposure</b>; it does not claim the current behaviour is right. A
     * mountainside village with 40 blocks of vertical spread and 30 of horizontal is one village to a
     * player and two to us. The fix is not obvious — going 2D would diverge from
     * {@link RaidAssociationPolicy}, which is 3D because vanilla's {@code getNearbyRaid} is — so it is
     * recorded as a VR-T1 runtime scenario rather than changed on speculation.
     */
    @Test
    void characterise_verticallySpreadSettlementCanSplit() {
        MobVillageMemory memory = new MobVillageMemory();
        BlockPos valleyFloor = new BlockPos(0, 64, 0);
        BlockPos hillside = new BlockPos(30, 104, 0);

        assertEquals(30 * 30 + 40 * 40, (int) valleyFloor.distSqr(hillside));
        assertTrue(valleyFloor.distSqr(hillside) > VillageIdentityPolicy.SAME_SETTLEMENT_RADIUS_SQR,
                "2500 > 2304: horizontally 30 apart, but 3D distance splits them");

        memory.remember(valleyFloor, 100L, complete(12));
        memory.remember(hillside, 200L, complete(10));
        assertEquals(2, memory.size(),
                "documented exposure: one mountainside village reads as two. VR-T1 scenario.");
    }
}
