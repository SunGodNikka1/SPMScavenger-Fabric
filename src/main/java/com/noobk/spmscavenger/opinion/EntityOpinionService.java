package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.experience.MobExperienceContext;

import java.util.UUID;

/**
 * GAO-6 — applies social experience to supplemental {@link EntityOpinionMemory} (no SPM mutation).
 */
public final class EntityOpinionService {

    private EntityOpinionService() {
    }

    public static void applyCompanionInvite(MobExperienceContext context, UUID companionId) {
        if (!OpinionFeatureGate.isEnabled() || context.isFrozen() || companionId == null) {
            return;
        }
        context.entityOpinionMemory().recordOutcome(companionId, 8f);
    }

    public static void applySocialInteraction(
            MobExperienceContext context, UUID otherId, float preferenceDelta) {
        if (!OpinionFeatureGate.isEnabled() || context.isFrozen() || otherId == null || preferenceDelta == 0f) {
            return;
        }
        context.entityOpinionMemory().recordOutcome(otherId, preferenceDelta);
    }
}
