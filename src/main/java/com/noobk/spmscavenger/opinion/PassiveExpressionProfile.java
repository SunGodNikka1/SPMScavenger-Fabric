package com.noobk.spmscavenger.opinion;

import java.util.Objects;

/**
 * Ephemeral GAO-8A output profile. It contains cosmetic bounds only and grants no action authority.
 */
public record PassiveExpressionProfile(
        boolean eligible,
        PassiveExpressionTone tone,
        int minCooldownTicks,
        int maxCooldownTicks,
        int minHoldTicks,
        int maxHoldTicks,
        float horizontalRangeDegrees,
        float verticalRangeDegrees,
        float socialLookChance) {

    public static final PassiveExpressionProfile INACTIVE = new PassiveExpressionProfile(
            false, PassiveExpressionTone.NEUTRAL, 1, 1, 1, 1, 0f, 0f, 0f);

    public PassiveExpressionProfile {
        Objects.requireNonNull(tone, "tone");
        if (minCooldownTicks < 1 || maxCooldownTicks < minCooldownTicks
                || minHoldTicks < 1 || maxHoldTicks < minHoldTicks) {
            throw new IllegalArgumentException("passive-expression tick bounds are invalid");
        }
        if (!Float.isFinite(horizontalRangeDegrees) || horizontalRangeDegrees < 0f
                || !Float.isFinite(verticalRangeDegrees) || verticalRangeDegrees < 0f
                || !Float.isFinite(socialLookChance)
                || socialLookChance < 0f || socialLookChance > 1f) {
            throw new IllegalArgumentException("passive-expression scalar is invalid");
        }
    }
}
