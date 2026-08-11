package com.noobk.spmscavenger.experience;

import net.minecraft.core.BlockPos;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * GAO-0c — arrival-anchored sustained REST state (D-GAO-021). Outlives the navigation Goal that
 * delivered the mob to the anchor.
 */
public record RestSessionClaim(
        UUID claimId,
        Optional<UUID> sourceIntentId,
        UUID commitmentId,
        RestSourceKind sourceKind,
        BlockPos anchor,
        RestAnchorType anchorType,
        long adoptedAt,
        long arrivedAt,
        long lastValidatedAt,
        boolean suspended,
        Optional<RestCloseReason> closeReason) {

    public static final double REST_RADIUS_SQR = 9.0;
    public static final long MAX_REST_TICKS = 6_000L;

    public RestSessionClaim {
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(sourceIntentId, "sourceIntentId");
        Objects.requireNonNull(commitmentId, "commitmentId");
        Objects.requireNonNull(sourceKind, "sourceKind");
        anchor = anchor.immutable();
        Objects.requireNonNull(anchorType, "anchorType");
        Objects.requireNonNull(closeReason, "closeReason");
    }

    /** Source-compatible constructor for pre-SCR-2R2 callers; a new claim starts active. */
    public RestSessionClaim(
            UUID claimId,
            Optional<UUID> sourceIntentId,
            UUID commitmentId,
            RestSourceKind sourceKind,
            BlockPos anchor,
            RestAnchorType anchorType,
            long adoptedAt,
            long arrivedAt,
            long lastValidatedAt,
            Optional<RestCloseReason> closeReason) {
        this(claimId, sourceIntentId, commitmentId, sourceKind, anchor, anchorType,
                adoptedAt, arrivedAt, lastValidatedAt, false, closeReason);
    }

    public boolean isLive() {
        return closeReason.isEmpty();
    }

    public boolean isActive() {
        return isLive() && !suspended;
    }

    public RestSessionClaim validated(long gameTime) {
        return new RestSessionClaim(
                claimId,
                sourceIntentId,
                commitmentId,
                sourceKind,
                anchor,
                anchorType,
                adoptedAt,
                arrivedAt,
                gameTime,
                suspended,
                closeReason);
    }

    public RestSessionClaim suspended(long gameTime) {
        if (!isLive() || suspended) {
            return validated(gameTime);
        }
        return new RestSessionClaim(
                claimId, sourceIntentId, commitmentId, sourceKind, anchor, anchorType,
                adoptedAt, arrivedAt, gameTime, true, closeReason);
    }

    public RestSessionClaim resumed(long gameTime) {
        if (!isLive() || !suspended) {
            return validated(gameTime);
        }
        return new RestSessionClaim(
                claimId, sourceIntentId, commitmentId, sourceKind, anchor, anchorType,
                adoptedAt, arrivedAt, gameTime, false, closeReason);
    }

    public RestSessionClaim closed(RestCloseReason reason, long gameTime) {
        Objects.requireNonNull(reason, "reason");
        return new RestSessionClaim(
                claimId,
                sourceIntentId,
                commitmentId,
                sourceKind,
                anchor,
                anchorType,
                adoptedAt,
                arrivedAt,
                gameTime,
                suspended,
                Optional.of(reason));
    }
}
