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
     * V2-DEF-003b — which resource family a block belongs to, <b>independent of intent</b>.
     *
     * <h2>Why the scan must remember this</h2>
     *
     * {@code isPassOneCandidate} answers "does the mob want this block", collapsing mandatory need
     * and optional wealth into one boolean. The bounded sweep then collapsed further, to
     * {@code target != null}. Both collapses are lossy in the same direction, and together they
     * produced a stall:
     *
     * <pre>
     * RAW_IRON mandatory, none in radius
     * saturated LOG wealth candidate in radius   (greed 0.1, wealthLevel 0.1 -> utility still &gt; 0)
     *   -> findTarget returns the LOG
     *   -> scan is not NO_CANDIDATES_IN_RADIUS
     *   -> RAW_IRON exhaustion never published
     *   -> ExistingRouteFeasibility stays UNKNOWN, trade can never displace
     * </pre>
     *
     * <p>The invariant this restores: <b>optional opportunity may affect target selection, but may
     * not prevent a mandatory consumer route from reaching its own factual conclusion.</b> Wealth
     * keeps its ability to notice and acquire logs; it simply stops being able to answer a question
     * that was asked about iron.
     *
     * <p>Order mirrors {@link #isPassOneCandidate} so the two cannot classify the same block
     * differently — one reading of the block families, not two.
     */
    public static java.util.Optional<GatherIntentPolicy.Resource> familyOf(BlockState state) {
        if (state.is(BlockTags.LOGS)) {
            return java.util.Optional.of(GatherIntentPolicy.Resource.LOGS);
        }
        if (state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE)) {
            return java.util.Optional.of(GatherIntentPolicy.Resource.COAL);
        }
        if (state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)) {
            return java.util.Optional.of(GatherIntentPolicy.Resource.DIAMOND);
        }
        if (state.is(Blocks.IRON_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE)) {
            return java.util.Optional.of(GatherIntentPolicy.Resource.RAW_IRON);
        }
        if (state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE)) {
            return java.util.Optional.of(GatherIntentPolicy.Resource.COBBLESTONE);
        }
        return java.util.Optional.empty();
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
