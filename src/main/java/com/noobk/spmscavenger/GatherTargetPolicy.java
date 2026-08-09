package com.noobk.spmscavenger;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Target legitimacy and priority among pass-one gather candidates (MI-2 / MI-13).
 *
 * <p>Blocking consumer demand outranks optional wealth among candidates that share the same
 * discovery legitimacy. Distance remains the tie-breaker. MI-6C applies cave ore bonus from
 * per-candidate opportunity, not mob-only posture.
 */
public final class GatherTargetPolicy {

    private static final int BLOCKING_TIER = 100;
    private static final int WEALTH_TIER = 50;
    private static final int NEWLY_EXPOSED_BONUS = 5;

    private GatherTargetPolicy() {
    }

    public static GatherIntentPolicy.Resource resourceFor(BlockState state) {
        if (state.is(BlockTags.LOGS)) {
            return GatherIntentPolicy.Resource.LOGS;
        }
        if (state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE)) {
            return GatherIntentPolicy.Resource.COAL;
        }
        if (state.is(Blocks.IRON_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE)) {
            return GatherIntentPolicy.Resource.RAW_IRON;
        }
        if (state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)) {
            return GatherIntentPolicy.Resource.DIAMOND;
        }
        if (state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE)) {
            return GatherIntentPolicy.Resource.COBBLESTONE;
        }
        return null;
    }

    public static boolean isLegitimateTarget(DiscoveryMode mode) {
        return mode.isLegitimate();
    }

    public static int priority(
            GatherIntentPolicy.GatherIntent intent,
            BlockState state,
            DiscoveryMode mode,
            float acquisitionCost) {
        return priority(intent, state, mode, acquisitionCost, false);
    }

    public static int priority(
            GatherIntentPolicy.GatherIntent intent,
            BlockState state,
            DiscoveryMode mode,
            float acquisitionCost,
            boolean caveOpportunity) {
        if (!isLegitimateTarget(mode)) {
            return Integer.MIN_VALUE;
        }
        GatherIntentPolicy.Resource resource = resourceFor(state);
        if (resource == null || !intent.wants(resource, acquisitionCost)) {
            return Integer.MIN_VALUE;
        }
        int tier = intent.requiredResources().contains(resource) ? BLOCKING_TIER : WEALTH_TIER;
        tier += CaveContextPolicy.orePriorityBonus(caveOpportunity, resource);
        if (mode == DiscoveryMode.NEWLY_EXPOSED) {
            tier += NEWLY_EXPOSED_BONUS;
        }
        int distPenalty = (int) Math.min(acquisitionCost * 8.0F, 127.0F);
        return tier * 256 - distPenalty;
    }

    public static int[] sortIndicesByPriority(
            BlockPos[] positions,
            double[] distances,
            int count,
            BlockGetter level,
            GatherIntentPolicy.GatherIntent intent,
            DiscoveryPolicy.HarvestReveal reveal,
            long gameTime) {
        return sortIndicesByPriority(
                positions, distances, count, level, intent, reveal, gameTime, null);
    }

    /**
     * @param caveOpportunityPerIndex length {@code count}; null treats every candidate as surface
     */
    public static int[] sortIndicesByPriority(
            BlockPos[] positions,
            double[] distances,
            int count,
            BlockGetter level,
            GatherIntentPolicy.GatherIntent intent,
            DiscoveryPolicy.HarvestReveal reveal,
            long gameTime,
            boolean[] caveOpportunityPerIndex) {
        int[] order = new int[count];
        int[] priorities = new int[count];
        for (int i = 0; i < count; i++) {
            order[i] = i;
            BlockPos pos = positions[i];
            BlockState state = level.getBlockState(pos);
            float acquisitionCost = (float) (Math.sqrt(distances[i]) / 8.0D);
            DiscoveryMode mode = DiscoveryPolicy.classify(level, pos, state, reveal, gameTime);
            boolean caveOpp = caveOpportunityPerIndex != null
                    && i < caveOpportunityPerIndex.length
                    && caveOpportunityPerIndex[i];
            priorities[i] = priority(intent, state, mode, acquisitionCost, caveOpp);
        }
        for (int i = 1; i < count; i++) {
            int key = order[i];
            int j = i - 1;
            while (j >= 0 && ranksLower(priorities, distances, order[j], key)) {
                order[j + 1] = order[j];
                j--;
            }
            order[j + 1] = key;
        }
        return order;
    }

    private static boolean ranksLower(int[] priorities, double[] distances, int left, int right) {
        if (priorities[left] != priorities[right]) {
            return priorities[left] < priorities[right];
        }
        return distances[left] > distances[right];
    }
}
