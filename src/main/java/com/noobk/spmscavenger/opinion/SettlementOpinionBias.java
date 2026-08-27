package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.village.KnownVillage;

import java.util.Objects;

/**
 * D-VR-025 — single Opinion-owned composition boundary for settlement preference.
 *
 * <p>The returned integer is a bounded soft rank among destinations already admitted by factual
 * policy. It is not legality, feasibility, permission, denial, or progression authority.
 * Personality, affect, relationship, and trader-preference terms remain neutral until a locked
 * mapping defines their normalization and coefficient.
 */
public final class SettlementOpinionBias {

    private SettlementOpinionBias() {
    }

    public static int request(
            KnownVillage village,
            DiscretionaryScoringInput opinionInput,
            SettlementOpinionContext ctx) {
        Objects.requireNonNull(village, "village");
        Objects.requireNonNull(opinionInput, "opinionInput");
        Objects.requireNonNull(ctx, "ctx");

        if (!OpinionFeatureGate.isEnabled() || !opinionInput.opinionEnabled()) {
            return 0;
        }

        int placeBias = PlaceOpinionRouteRanker.destinationBias(
                ctx.placePreferences(), village.anchor().getX(), village.anchor().getZ());
        return Math.max(
                -PlaceOpinionRouteRanker.MAX_ROUTE_BIAS,
                Math.min(PlaceOpinionRouteRanker.MAX_ROUTE_BIAS, placeBias));
    }
}
