package com.noobk.spmscavenger.village;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;

/**
 * V1 / D-VR-019 — the canonical settlement anchor.
 *
 * <h2>Contract: reproduce, do not resemble</h2>
 *
 * This class exists to produce <b>the same coordinate vanilla would</b> for the same bounded POI set,
 * not merely a plausible centre of the same points. Sharing an input predicate does not imply sharing
 * an output coordinate, and the whole value of D-VR-019 is that {@code KnownVillage.anchor} and
 * {@code Raid.getCenter()} name the same place.
 *
 * <p>Transcribed from {@code Raids#createOrExtendRaid} in the pinned 1.21.1 merged jar
 * (bytecode offsets 72–171):
 *
 * <pre>
 * int count = 0;
 * Vec3 acc = Vec3.ZERO;
 * for (PoiRecord r : pois) {
 *     BlockPos p = r.getPos();
 *     acc = acc.add(p.getX(), p.getY(), p.getZ());   // offsets 118–141
 *     count++;
 * }
 * BlockPos centre = count > 0
 *         ? BlockPos.containing(acc.scale(1.0D / count))   // offsets 154–171
 *         : queryOrigin;                                   // offsets 176–177
 * </pre>
 *
 * <h2>Four properties an idiomatic rewrite gets wrong</h2>
 *
 * Each is a silent one-block class of error, and each has its own regression test. They are listed
 * because "average the positions" is the obvious implementation and it is wrong four different ways.
 *
 * <ol>
 *   <li><b>Raw block coordinates, not block centres.</b> Vanilla accumulates {@code p.getX()}, not
 *       {@code Vec3.atCenterOf(p)}. Using centres biases every component by {@code +0.5}, which
 *       survives the floor below and shifts the anchor.</li>
 *   <li><b>{@code BlockPos.containing} floors; it does not round.</b> The two agree on positive
 *       coordinates and disagree on negative ones, so the bug is invisible in a test world built at
 *       spawn and appears in half the map.</li>
 *   <li><b>Y participates.</b> A village anchor "obviously" only needs XZ, and dropping Y changes
 *       nothing about the horizontal answer — but it changes the {@code distSqr} that
 *       {@code Raids#getNearbyRaid} computes, which is the comparison this whole decision exists to
 *       make agree.</li>
 *   <li><b>Duplicates are not removed.</b> The query returns one record per POI, so a village with
 *       twenty beds and three workstations is weighted twenty-to-three toward the beds. That skew is
 *       vanilla's answer, therefore it is ours.</li>
 * </ol>
 *
 * Pure: no world access, no allocation beyond the accumulator, deterministic.
 */
public final class VillageAnchorPolicy {

    private VillageAnchorPolicy() {
    }

    /**
     * @param poiPositions positions of the POIs admitted into perception, in any order, duplicates
     *     significant (see property 4)
     * @param queryOrigin what vanilla falls back to when the set is empty — the position the query
     *     was issued from. Returning this rather than "no village" mirrors vanilla exactly; callers
     *     decide whether an empty set means a settlement at all (see {@link #isSettlement}).
     */
    public static BlockPos anchorOf(Collection<BlockPos> poiPositions, BlockPos queryOrigin) {
        if (poiPositions == null || poiPositions.isEmpty()) {
            return queryOrigin;
        }
        Vec3 accumulated = Vec3.ZERO;
        int count = 0;
        for (BlockPos pos : poiPositions) {
            // Raw ints. Vec3.atCenterOf(pos) would be the idiomatic call and would be wrong.
            accumulated = accumulated.add(pos.getX(), pos.getY(), pos.getZ());
            count++;
        }
        // BlockPos.containing == floor per component, not round.
        return BlockPos.containing(accumulated.scale(1.0D / count));
    }

    /**
     * Whether an admitted POI set constitutes a settlement at all.
     *
     * <p>Vanilla never asks this — it is answering "where do I centre a raid the player already
     * earned", so an empty set legitimately falls back to the player. We are answering "is there a
     * village here", which is a different question, and conflating them would make every position in
     * the world a village of size zero.
     */
    public static boolean isSettlement(Collection<BlockPos> admittedPoiPositions) {
        return admittedPoiPositions != null && !admittedPoiPositions.isEmpty();
    }

    /**
     * Whether two anchors name the same settlement.
     *
     * <p>Deliberately the same radius {@code ServerLevel#getRaidAt} uses — it calls
     * {@code raids.getNearbyRaid(pos, 9216)}, i.e. 96 blocks squared, and
     * {@code Raids#getOrCreateRaid} reuses any raid found within it rather than creating a second
     * one. So vanilla already considers two anchors this close to be one settlement for raid
     * purposes, and adopting a different merge radius would reintroduce exactly the disagreement
     * D-VR-019 removes — merely at a different scale.
     *
     * <p><b>Accepted cost:</b> two genuinely distinct villages 90 blocks apart merge into one
     * {@code KnownVillage}. That is a real loss of fidelity, and it is the correct one: vanilla will
     * also run a single raid across both.
     */
    public static final int SAME_SETTLEMENT_RADIUS_SQR = 9216;

    public static boolean sameSettlement(BlockPos a, BlockPos b) {
        return a != null && b != null && a.distSqr(b) <= SAME_SETTLEMENT_RADIUS_SQR;
    }
}
