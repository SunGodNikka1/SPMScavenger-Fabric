package com.noobk.spmscavenger;

import com.noobk.spmscavenger.CaveOpportunityPolicy.CaveOpportunity;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongPredicate;

/**
 * MI-6F — bridges explore landing preference keys to {@link CaveOpportunityPolicy} scores and
 * reorders candidate lists so a committed branch is tried first.
 */
public final class CaveOpportunitySelection {

    private CaveOpportunitySelection() {
    }

    /** Landing sort keys are lower-is-better; policy scores are higher-is-better. */
    public static int preferenceToScore(int landingPreferenceKey) {
        return -landingPreferenceKey;
    }

    /**
     * Updates commitment and returns candidates with the committed landing first when present.
     *
     * @param preferenceKeys position id → landing preference key (lower is better)
     */
    public static CommitmentResult commitBestScored(
            List<BlockPos> candidates,
            Map<Long, Integer> preferenceKeys,
            CaveOpportunity held,
            LongPredicate heldStillValid,
            long now) {
        if (candidates.isEmpty()) {
            return new CommitmentResult(held, candidates);
        }
        BlockPos best = candidates.get(0);
        int bestScore = Integer.MIN_VALUE;
        for (BlockPos candidate : candidates) {
            Integer key = preferenceKeys.get(candidate.asLong());
            if (key == null) {
                continue;
            }
            int score = preferenceToScore(key);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        if (bestScore == Integer.MIN_VALUE) {
            bestScore = 0;
        }
        boolean valid = held != null && heldStillValid.test(held.id());
        CaveOpportunity next = CaveOpportunityPolicy.arbitrate(
                held, valid, best.asLong(), bestScore, now);
        if (candidates.size() == 1) {
            return new CommitmentResult(next, candidates);
        }
        long committedId = next.id();
        List<BlockPos> ordered = new ArrayList<>(candidates.size());
        for (BlockPos candidate : candidates) {
            if (candidate.asLong() == committedId) {
                ordered.add(0, candidate);
            } else {
                ordered.add(candidate);
            }
        }
        return new CommitmentResult(next, ordered);
    }

    public static Map<Long, Integer> preferenceKeyMap() {
        return new HashMap<>();
    }

    public record CommitmentResult(CaveOpportunity commitment, List<BlockPos> candidates) {
    }
}
