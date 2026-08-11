package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.opinion.OpinionFeatureGate;
import com.noobk.spmscavenger.activity.ActivityObservationService;
import com.noobk.spmscavenger.experience.MobExperienceContext;
import com.noobk.spmscavenger.experience.OpinionExperienceRegistry;
import net.minecraft.world.entity.Mob;

import java.util.UUID;

/**
 * GAO-1 — observation cadence entry point for per-mob mood updates.
 */
public final class AffectiveStateService {

    private AffectiveStateService() {
    }

    public static void observe(
            UUID mobId,
            ActivityObservationService.Observation observation,
            int intervalTicks) {
        if (!OpinionFeatureGate.isEnabled()) {
            return;
        }
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(mobId);
        if (context.isFrozen()) {
            return;
        }
        context.affectiveState().observe(AffectiveObservation.from(observation, intervalTicks));
    }

    /** Production overload binds the host-anchored GAO-7 profile on first context allocation. */
    public static void observe(
            Mob mob,
            ActivityObservationService.Observation observation,
            int intervalTicks) {
        if (!OpinionFeatureGate.isEnabled()) {
            return;
        }
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(mob);
        if (context.isFrozen()) {
            return;
        }
        context.affectiveState().observe(AffectiveObservation.from(observation, intervalTicks));
    }
}
