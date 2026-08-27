package com.noobk.spmscavenger.village;

import net.minecraft.core.BlockPos;

/**
 * V1-R1 — <b>cognitive</b> settlement identity: "is this the same village I remember?"
 *
 * <h2>Why this is not the raid radius</h2>
 *
 * The first implementation answered this question with vanilla's raid-association radius (96 blocks),
 * reasoning that if vanilla runs one raid across two anchors then they are one settlement. That
 * conflates two genuinely different questions, and the cost is not hypothetical:
 *
 * <pre>
 * Village A  — independently designated home, where the mob sleeps and stores
 * Village B  — 85 blocks away, remembered separately for later trader evidence
 * </pre>
 *
 * Under a 96-block identity radius those are **one** {@code KnownVillage} and the model cannot hold
 * both tiers at once — the mob cannot have a home and a separate trading post that close. Every later
 * feature that keys off identity (affinity, storage ownership, trade routing, community projects)
 * inherits that collapse.
 *
 * <p>Vanilla considering two centres part of one raid neighbourhood is a statement about <em>raids</em>.
 * It is not a statement about what the mob should treat as one place. {@link RaidAssociationPolicy}
 * keeps the vanilla-compatible answer, and one raid may legitimately cover several remembered
 * villages without merging them.
 *
 * <h2>Choosing the radius — stated as the judgement it is</h2>
 *
 * {@code UNVERIFIED}. There is no vanilla constant for "one settlement" because vanilla has no
 * settlement identity — villages are an emergent property of POI density, which is precisely why this
 * value has to be ours. 48 blocks is chosen as roughly the scale of a generated village, and it sits
 * below the 64-block query radius so two observations of the same settlement taken from opposite
 * sides still share most of their admitted POIs and converge rather than forking.
 *
 * <p><b>The failure mode of choosing too small</b> is duplicate {@code KnownVillage} entries for one
 * real settlement, which is visible (two entries whose anchors sit inside one village) and cheap to
 * correct. <b>Too large</b> collapses distinct settlements irreversibly and is invisible — the model
 * simply cannot express the difference, and nothing looks wrong. Erring small is the recoverable
 * direction.
 *
 * <p><b>The evidence-backed upgrade, when runtime justifies it:</b> identity by admitted-POI-set
 * overlap rather than anchor distance. Two observations are the same settlement when they share POIs.
 * That is exact and radius-free; it is deferred because it means storing POI positions per remembered
 * village rather than a count, and V1 has no runtime evidence that a radius is insufficient.
 */
public final class VillageIdentityPolicy {

    /**
     * Squared distance within which two anchors name the same remembered settlement.
     *
     * <p>48 blocks. See the class note: deliberately smaller than {@link RaidAssociationPolicy}'s
     * radius, and deliberately below {@link VillagePerception#VILLAGE_QUERY_RADIUS}.
     */
    public static final int SAME_SETTLEMENT_RADIUS_SQR = 48 * 48;

    private VillageIdentityPolicy() {
    }

    public static boolean sameSettlement(BlockPos a, BlockPos b) {
        return a != null && b != null && a.distSqr(b) <= SAME_SETTLEMENT_RADIUS_SQR;
    }
}
