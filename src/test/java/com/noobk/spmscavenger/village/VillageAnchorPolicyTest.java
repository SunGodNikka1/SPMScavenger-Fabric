package com.noobk.spmscavenger.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * D-VR-019 — the anchor must <b>reproduce</b> vanilla's raid-centre derivation, not resemble it.
 *
 * <p>Each test below pins one property where the idiomatic rewrite differs by exactly one block, and
 * computes the wrong answer alongside the right one so the test states the difference rather than
 * asserting a magic coordinate. A one-block anchor error is invisible in play and silently shifts
 * {@code distSqr} against {@code Raid.getCenter()}.
 */
class VillageAnchorPolicyTest {

    private static final BlockPos ORIGIN = new BlockPos(0, 64, 0);

    /** The transcribed reference: exactly what {@code Raids#createOrExtendRaid} does. */
    private static BlockPos vanilla(List<BlockPos> pois) {
        Vec3 acc = Vec3.ZERO;
        int count = 0;
        for (BlockPos p : pois) {
            acc = acc.add(p.getX(), p.getY(), p.getZ());
            count++;
        }
        return count > 0 ? BlockPos.containing(acc.scale(1.0D / count)) : ORIGIN;
    }

    @Test
    void mustHappen_matchesVanillaOnAnOrdinaryVillage() {
        List<BlockPos> pois = List.of(
                new BlockPos(10, 64, 10), new BlockPos(14, 65, 12), new BlockPos(9, 63, 17),
                new BlockPos(21, 64, 8), new BlockPos(11, 66, 14));
        assertEquals(vanilla(pois), VillageAnchorPolicy.anchorOf(pois, ORIGIN));
    }

    /**
     * Property 1 — raw block coordinates, not block centres.
     *
     * <p>{@code Vec3.atCenterOf(pos)} is the idiomatic way to turn a BlockPos into a Vec3, and it
     * adds 0.5 to every component. The bias survives the floor.
     */
    @Test
    void mustNotHappen_accumulatesBlockCentres() {
        List<BlockPos> pois = List.of(new BlockPos(0, 0, 0), new BlockPos(1, 1, 1));

        Vec3 centred = Vec3.ZERO;
        for (BlockPos p : pois) {
            centred = centred.add(Vec3.atCenterOf(p));
        }
        BlockPos wrong = BlockPos.containing(centred.scale(1.0D / pois.size()));

        assertEquals(new BlockPos(0, 0, 0), VillageAnchorPolicy.anchorOf(pois, ORIGIN));
        assertEquals(new BlockPos(1, 1, 1), wrong, "sanity: the centre-based rewrite really does differ");
        assertNotEquals(wrong, VillageAnchorPolicy.anchorOf(pois, ORIGIN));
    }

    /**
     * Property 2 — {@code BlockPos.containing} floors; it does not round.
     *
     * <p>The two agree for positive coordinates, which is why a village built near spawn would never
     * expose this. Negative coordinates are half the world.
     */
    @Test
    void mustNotHappen_roundsInsteadOfFloors() {
        // Mean x = -3.5 -> floor -4, round-half-up -3.
        List<BlockPos> pois = List.of(new BlockPos(-3, 64, 0), new BlockPos(-4, 64, 0));

        BlockPos actual = VillageAnchorPolicy.anchorOf(pois, ORIGIN);
        assertEquals(-4, actual.getX(), "floor, as vanilla does");
        assertEquals(Math.round(-3.5D), -3L, "sanity: rounding really would give -3");
        assertEquals(vanilla(pois), actual);
    }

    /** Property 3 — Y participates; an XZ-only anchor changes the distSqr the raid check uses. */
    @Test
    void mustNotHappen_dropsTheYComponent() {
        List<BlockPos> pois = List.of(new BlockPos(0, 70, 0), new BlockPos(0, 40, 0));
        BlockPos actual = VillageAnchorPolicy.anchorOf(pois, ORIGIN);
        assertEquals(55, actual.getY(), "Y is averaged, not discarded or defaulted");
    }

    /**
     * Property 4 — duplicates are significant.
     *
     * <p>The query returns one record per POI, so twenty beds outweigh three workstations twenty to
     * three. Deduplicating positions "to be tidy" would move the anchor.
     */
    @Test
    void mustNotHappen_deduplicatesPositions() {
        List<BlockPos> bedHeavy = List.of(
                new BlockPos(0, 64, 0), new BlockPos(0, 64, 0), new BlockPos(0, 64, 0),
                new BlockPos(60, 64, 0));
        BlockPos weighted = VillageAnchorPolicy.anchorOf(bedHeavy, ORIGIN);
        BlockPos deduped = VillageAnchorPolicy.anchorOf(
                List.copyOf(new java.util.LinkedHashSet<>(bedHeavy)), ORIGIN);

        assertEquals(15, weighted.getX(), "3 at x=0 and 1 at x=60 -> 15");
        assertEquals(30, deduped.getX(), "sanity: deduplicating really does move it");
        assertNotEquals(deduped, weighted);
    }

    /** Vanilla's own empty-set behaviour: fall back to the query origin. */
    @Test
    void mustHappen_emptySetFallsBackToQueryOrigin() {
        assertEquals(ORIGIN, VillageAnchorPolicy.anchorOf(List.of(), ORIGIN));
        assertEquals(ORIGIN, VillageAnchorPolicy.anchorOf(null, ORIGIN));
    }

    /**
     * …but "vanilla returns the origin" is not "there is a village here". Vanilla is centring a raid
     * the player already earned; we are asking whether a settlement exists. Conflating them makes
     * every position in the world a village of size zero.
     */
    @Test
    void mustNotHappen_emptyPerceptionCountsAsASettlement() {
        assertFalse(VillageAnchorPolicy.isSettlement(List.of()));
        assertFalse(VillageAnchorPolicy.isSettlement(null));
        assertTrue(VillageAnchorPolicy.isSettlement(List.of(new BlockPos(1, 1, 1))));
    }

    /**
     * V1-R1: settlement identity is no longer this class's business.
     *
     * <p>It used to answer "same village?" with vanilla's 96-block raid radius, which collapsed a
     * designated home and another useful village 85 blocks apart into a single entry that could not hold both
     * tiers. Identity moved to {@code VillageIdentityPolicy} and raid compatibility to
     * {@code RaidAssociationPolicy}; this class is now purely the anchor derivation, which is the one
     * thing that must match vanilla exactly. Asserted structurally so the two cannot silently
     * re-merge here.
     */
    @Test
    void mustNotHappen_anchorPolicyRegainsSettlementIdentity() {
        for (java.lang.reflect.Method method : VillageAnchorPolicy.class.getDeclaredMethods()) {
            String name = method.getName();
            assertTrue(name.equals("anchorOf") || name.equals("isSettlement"),
                    "identity belongs to VillageIdentityPolicy, not here: " + name);
        }
        for (java.lang.reflect.Field field : VillageAnchorPolicy.class.getDeclaredFields()) {
            assertFalse(field.getName().contains("RADIUS"),
                    "no radius constant belongs to the anchor derivation: " + field.getName());
        }
    }
}
