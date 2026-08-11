package com.noobk.spmscavenger.opinion;

/** Relationship eligibility for a non-exclusive social glance. */
public final class PassiveExpressionSocialPolicy {

    private PassiveExpressionSocialPolicy() {
    }

    public static boolean isSelfLiked(Float feeling, float neutral) {
        return feeling != null && Float.isFinite(feeling) && feeling > neutral;
    }
}
