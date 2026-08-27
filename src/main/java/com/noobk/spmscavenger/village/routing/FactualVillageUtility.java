package com.noobk.spmscavenger.village.routing;

import net.minecraft.core.BlockPos;

import java.util.Objects;

/**
 * D-VR-092 — non-additive factual ordering inside one capability-evidence class.
 *
 * <p>Horizontal distance is first. Home and familiarity are factual tie-breaks only; there are no
 * weights that can grow until they cross the capability-class boundary. Y is deliberately absent.
 */
public record FactualVillageUtility(
        long horizontalDistanceSquared,
        boolean home,
        int familiarity) implements Comparable<FactualVillageUtility> {

    public FactualVillageUtility {
        if (horizontalDistanceSquared < 0L) {
            throw new IllegalArgumentException("horizontalDistanceSquared must be non-negative");
        }
    }

    public static FactualVillageUtility from(
            BlockPos origin, SettlementDestinationFacts candidate) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(candidate, "candidate");
        long dx = (long) candidate.village().anchor().getX() - origin.getX();
        long dz = (long) candidate.village().anchor().getZ() - origin.getZ();
        return new FactualVillageUtility(
                dx * dx + dz * dz, candidate.home(), candidate.familiarity());
    }

    @Override
    public int compareTo(FactualVillageUtility other) {
        int distanceOrder = Long.compare(horizontalDistanceSquared, other.horizontalDistanceSquared);
        if (distanceOrder != 0) {
            return distanceOrder;
        }
        int homeOrder = Boolean.compare(other.home, home);
        return homeOrder != 0 ? homeOrder : Integer.compare(other.familiarity, familiarity);
    }
}
