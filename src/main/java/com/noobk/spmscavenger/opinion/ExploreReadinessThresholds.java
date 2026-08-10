package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.experience.MobExperienceContext;
import com.noobk.spmscavenger.experience.OpinionExperienceRegistry;

import java.util.UUID;

/**
 * GAO-4.1 — shared explore readiness idle threshold for observer and {@code ExploringGoal}.
 */
public final class ExploreReadinessThresholds {

    private ExploreReadinessThresholds() {
    }

    public static int idleTicks(ScavengerConfig cfg, UUID mobId) {
        if (!OpinionFeatureGate.isEnabled()) {
            return cfg.exploreIdleTicks;
        }
        MobExperienceContext context = OpinionExperienceRegistry.find(mobId);
        float boredom = context == null ? 0f : context.affectiveState().boredom();
        return ExploreIdleThresholdPolicy.effectiveIdleTicks(
                cfg.exploreIdleTicks, boredom, true);
    }
}
