package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.experience.MobExperienceContext;
import com.noobk.spmscavenger.mining.MiningProjectEnd;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

/**
 * GAO-5 — applies mining terminals to {@link PlaceOpinionMemory} (no mandatory veto).
 */
public final class PlaceOpinionService {

    private PlaceOpinionService() {
    }

    public static void applyMiningTerminal(
            MobExperienceContext context, MiningProjectEnd end, BlockPos at) {
        if (!OpinionFeatureGate.isEnabled() || context.isFrozen() || at == null) {
            return;
        }
        long chunkKey = new ChunkPos(at).toLong();
        float delta = preferenceDelta(end);
        if (delta != 0f) {
            context.placeOpinionMemory().recordOutcome(chunkKey, delta);
        }
    }

    static float preferenceDelta(MiningProjectEnd end) {
        return switch (end) {
            case CAVE_FOUND -> 18f;
            case DEMAND_SATISFIED -> 12f;
            case SEARCH_BUDGET_EXHAUSTED, NO_PROGRESS -> -14f;
            case HAZARD -> -10f;
            case TOOL_FAILURE -> -6f;
            default -> 0f;
        };
    }
}
