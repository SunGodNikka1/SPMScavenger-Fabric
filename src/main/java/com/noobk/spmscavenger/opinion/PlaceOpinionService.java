package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.experience.MobExperienceContext;
import com.noobk.spmscavenger.experience.ActivityKind;
import com.noobk.spmscavenger.experience.MiningTerminalSemantics;
import com.noobk.spmscavenger.mining.MiningProjectEnd;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

/**
 * GAO-5 — applies mining terminals to {@link PlaceOpinionMemory} (no mandatory veto).
 */
public final class PlaceOpinionService {

    private PlaceOpinionService() {
    }

    /**
     * D-GAO-024 - place learning consumes the shared terminal semantics.
     *
     * <p>It used to map {@code MiningProjectEnd} to a magnitude through its own static table, which
     * contradicted D-GAO-023: {@code TOOL_FAILURE} is classified {@code PROTECTED_INTERRUPT} /
     * {@code ENVIRONMENT_BLOCKED} by the shared policy - explicitly not dislike - while this file
     * charged it -6f. A 117-cycle assign/revoke churn loop therefore drove one chunk toward the
     * preference floor for a mob that never broke a block.
     */
    public static void applyMiningTerminal(
            MobExperienceContext context, MiningTerminalSemantics semantics, ActivityKind activity,
            BlockPos at) {
        if (!OpinionFeatureGate.isEnabled() || context.isFrozen() || at == null) {
            return;
        }
        // Repeated physical no-progress is learnable; a single one is not. The threshold lives in
        // the shared policy, so place learning honours it rather than inventing its own.
        int failures = activity == null ? 0 : context.executionFailureTotal(activity);
        if (!semantics.mayLearnPreference(failures)) {
            return;
        }
        float delta = preferenceMagnitude(semantics.end());
        if (delta != 0f) {
            context.placeOpinionMemory().recordOutcome(new ChunkPos(at).toLong(), delta);
        }
    }

    /**
     * How <b>much</b> a learnable outcome moves place preference. Whether it is learnable at all is
     * decided upstream by {@link MiningTerminalSemantics#mayLearnPreference()}, so this table no
     * longer gets a vote on eligibility.
     *
     * <p>{@code TOOL_FAILURE} is gone: a capability outcome is never dislike (D-GAO-023), and it
     * only ever reached here through a terminal the executor had not begun.
     */
    public static float preferenceMagnitude(MiningProjectEnd end) {
        return switch (end) {
            case CAVE_FOUND -> 18f;
            case DEMAND_SATISFIED -> 12f;
            case SEARCH_BUDGET_EXHAUSTED, NO_PROGRESS -> -14f;
            case HAZARD -> -10f;
            default -> 0f;
        };
    }
}
