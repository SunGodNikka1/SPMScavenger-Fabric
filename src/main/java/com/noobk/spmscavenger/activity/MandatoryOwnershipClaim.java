package com.noobk.spmscavenger.activity;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

/**
 * D-VR-084 — a published pending-claim record: "this owner has accepted bounded responsibility
 * for this route and is not yet running."
 *
 * <p>Deliberately a record over stable identity only. {@code consumerKey} is the canonical
 * consumer; {@code routeIdentity} is the owner's stable semantic route identity (for Gather:
 * consumer/material/precursor — never scan results, timestamps, candidate positions, or evidence
 * epochs). {@code generation} is minted by the owner at release, never at publish; see
 * {@link MandatoryOwnershipRegistry}.
 *
 * <p>Runtime-only. Never persisted; never registered in {@code PerMobSavedData.forgetAll()}.
 */
public record MandatoryOwnershipClaim(
        UUID mobId,
        ResourceLocation consumerKey,
        Object routeIdentity,
        int generation,
        long openedAt,
        long expiresAt) {

    public MandatoryOwnershipClaim {
        Objects.requireNonNull(mobId, "mobId");
        Objects.requireNonNull(consumerKey, "consumerKey");
        Objects.requireNonNull(routeIdentity, "routeIdentity");
    }

    public boolean expired(long now) {
        return now >= expiresAt;
    }

    /**
     * Identity comparison used by the anti-self-renewal slot: same consumer and same stable
     * route identity means "the same episode".
     */
    public boolean sameRoute(ResourceLocation otherConsumer, Object otherRoute) {
        return consumerKey.equals(otherConsumer) && routeIdentity.equals(otherRoute);
    }
}
