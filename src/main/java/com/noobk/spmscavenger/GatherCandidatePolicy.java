package com.noobk.spmscavenger;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

/**
 * Pass-one gather candidate rules (MI-13a). Ore must be air-exposed before entering the nearest
 * {@code MAX_CANDIDATES} buffer so buried blocks cannot starve legitimately visible veins.
 */
public final class GatherCandidatePolicy {

    public enum ScanFailureReason {
        /** Scan completed and at least one pass-one candidate existed. */
        NONE,
        /** No block in radius matched pass-one rules. */
        NO_CANDIDATES_IN_RADIUS,
        /** Pass-one candidates existed but every pass-two protection check failed. */
        CANDIDATES_ALL_REJECTED_PROTECTION
    }

    private GatherCandidatePolicy() {
    }

    /**
     * Cheap pass-one test: block family, intent, tool capability, and (for ore) air exposure.
     * Build-protection and path checks remain pass two.
     */
    public static boolean isPassOneCandidate(
            BlockGetter level,
            BlockPos pos,
            BlockState state,
            GatherIntentPolicy.GatherIntent intent,
            Predicate<BlockState> ownsToolFor) {
        return isPassOneCandidate(level, pos, state, intent, ownsToolFor, 0.0F);
    }

    public static boolean isPassOneCandidate(
            BlockGetter level,
            BlockPos pos,
            BlockState state,
            GatherIntentPolicy.GatherIntent intent,
            Predicate<BlockState> ownsToolFor,
            float acquisitionCost) {
        if (state.is(BlockTags.LOGS)) {
            return intent.wants(GatherIntentPolicy.Resource.LOGS, acquisitionCost);
        }
        if (state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE)) {
            return intent.wants(GatherIntentPolicy.Resource.COAL, acquisitionCost)
                    && ownsToolFor.test(state)
                    && GatherProtection.isExposedToAir(level, pos);
        }
        if (intent.wants(GatherIntentPolicy.Resource.DIAMOND, acquisitionCost)
                && (state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE))) {
            return ownsToolFor.test(state) && GatherProtection.isExposedToAir(level, pos);
        }
        if (intent.wants(GatherIntentPolicy.Resource.RAW_IRON, acquisitionCost)
                && (state.is(Blocks.IRON_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE))) {
            return ownsToolFor.test(state) && GatherProtection.isExposedToAir(level, pos);
        }
        if (intent.wants(GatherIntentPolicy.Resource.COBBLESTONE, acquisitionCost)
                && (state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE))) {
            return ownsToolFor.test(state);
        }
        return false;
    }
}
