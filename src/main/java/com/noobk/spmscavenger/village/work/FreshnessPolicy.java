package com.noobk.spmscavenger.village.work;

/**
 * Pure freshness classification for {@link VillageWorkFacts}.
 */
public final class FreshnessPolicy {

    private FreshnessPolicy() {}

    public static WorkFactsFreshness classify(long observedAtTick, long currentTick) {
        if (currentTick - observedAtTick <= VillageWorkTuning.FRESHNESS_WINDOW_TICKS) {
            return WorkFactsFreshness.FRESH;
        }
        return WorkFactsFreshness.STALE;
    }

    public static VillageWorkFacts apply(VillageWorkFacts facts, long currentTick) {
        if (facts.completeness() != WorkFactsCompleteness.COMPLETE) {
            return facts;
        }
        WorkFactsFreshness next = classify(facts.observedAtTick(), currentTick);
        if (next == facts.freshness()) {
            return facts;
        }
        return facts.withFreshness(next, currentTick);
    }
}
