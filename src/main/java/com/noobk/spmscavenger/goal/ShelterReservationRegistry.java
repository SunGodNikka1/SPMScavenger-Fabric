package com.noobk.spmscavenger.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** SCR-2 bounded, dimension-aware standing-capacity ownership. */
final class ShelterReservationRegistry {

    static final double DEFAULT_SPACING_RADIUS = 1.5;
    static final long RESERVATION_TICKS = 600L;
    private static final long REFRESH_WHEN_REMAINING_TICKS = 200L;

    /** One entry per owner UUID; minted commitment IDs are conditional tokens, never map keys. */
    private static final Map<UUID, Reservation> BY_OWNER = new HashMap<>();

    record Reservation(
            UUID ownerMob,
            UUID commitmentId,
            GlobalPos site,
            double spacingRadius,
            long expiresAt) {
    }

    private ShelterReservationRegistry() {
    }

    static synchronized boolean available(
            UUID owner,
            ResourceKey<Level> dimension,
            BlockPos site,
            double spacingRadius,
            long now) {
        sweepExpired(now);
        return noConflict(owner, GlobalPos.of(dimension, site.immutable()), spacingRadius);
    }

    static synchronized boolean reserve(
            UUID owner,
            UUID commitmentId,
            ResourceKey<Level> dimension,
            BlockPos site,
            double spacingRadius,
            long now) {
        sweepExpired(now);
        GlobalPos globalSite = GlobalPos.of(dimension, site.immutable());
        if (!noConflict(owner, globalSite, spacingRadius)) {
            return false;
        }
        BY_OWNER.put(owner, new Reservation(
                owner, commitmentId, globalSite, spacingRadius, now + RESERVATION_TICKS));
        return true;
    }

    static synchronized boolean ownsAndRefresh(
            UUID owner, UUID commitmentId, GlobalPos expectedSite, long now) {
        sweepExpired(now);
        Reservation current = BY_OWNER.get(owner);
        if (current == null
                || !current.commitmentId().equals(commitmentId)
                || !current.site().equals(expectedSite)) {
            return false;
        }
        if (current.expiresAt() - now <= REFRESH_WHEN_REMAINING_TICKS) {
            BY_OWNER.put(owner, new Reservation(
                    owner,
                    commitmentId,
                    expectedSite,
                    current.spacingRadius(),
                    now + RESERVATION_TICKS));
        }
        return true;
    }

    static synchronized void release(UUID owner, UUID commitmentId) {
        Reservation current = BY_OWNER.get(owner);
        if (current != null && current.commitmentId().equals(commitmentId)) {
            BY_OWNER.remove(owner);
        }
    }

    static synchronized void releaseOwner(UUID owner) {
        BY_OWNER.remove(owner);
    }

    static synchronized void shutdownServerState() {
        BY_OWNER.clear();
    }

    static synchronized int size() {
        return BY_OWNER.size();
    }

    static synchronized Reservation reservationFor(UUID owner) {
        return BY_OWNER.get(owner);
    }

    private static boolean noConflict(UUID owner, GlobalPos site, double spacingRadius) {
        for (Reservation existing : BY_OWNER.values()) {
            if (existing.ownerMob().equals(owner)
                    || !existing.site().dimension().equals(site.dimension())) {
                continue;
            }
            double required = Math.max(spacingRadius, existing.spacingRadius());
            if (existing.site().pos().distSqr(site.pos()) < required * required) {
                return false;
            }
        }
        return true;
    }

    private static void sweepExpired(long now) {
        BY_OWNER.entrySet().removeIf(entry -> now > entry.getValue().expiresAt());
    }
}
