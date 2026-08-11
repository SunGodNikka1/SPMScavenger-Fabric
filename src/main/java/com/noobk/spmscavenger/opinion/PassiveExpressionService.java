package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.activity.ActivityObservationService;
import com.noobk.spmscavenger.experience.MobExperienceContext;
import com.noobk.spmscavenger.experience.OpinionExperienceRegistry;
import net.minecraft.world.entity.Mob;

/** Publishes GAO-8A output on the existing activity-observer cadence. */
public final class PassiveExpressionService {

    private PassiveExpressionService() {
    }

    public static void observe(
            Mob mob, ActivityObservationService.Observation observation) {
        MobExperienceContext context = OpinionExperienceRegistry.find(mob.getUUID());
        if (context == null || context.isFrozen()) {
            return;
        }
        context.publishPassiveExpression(PassiveExpressionPolicy.evaluate(
                context.affectiveState(),
                context.personalityModel(),
                observation,
                OpinionFeatureGate.isEnabled()));
    }
}
