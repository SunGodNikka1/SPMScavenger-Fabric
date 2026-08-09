package com.noobk.spmscavenger.opinion;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * GAO-3 — ranked discretionary utility scores. Produces no intents and changes no Goals.
 */
public record ScoringResult(List<ActivityUtilityBreakdown> ranked) {

    private static final Comparator<ActivityUtilityBreakdown> RANK_ORDER =
            Comparator.comparing(ActivityUtilityBreakdown::total)
                    .reversed()
                    .thenComparing(ActivityUtilityBreakdown::activity);

    public ScoringResult {
        ranked = List.copyOf(Objects.requireNonNull(ranked, "ranked"));
    }

    public Optional<ActivityUtilityBreakdown> top() {
        return ranked.isEmpty() ? Optional.empty() : Optional.of(ranked.get(0));
    }

    public Optional<DiscretionaryActivity> topActivity() {
        return top().map(ActivityUtilityBreakdown::activity);
    }

    public static ScoringResult of(List<ActivityUtilityBreakdown> breakdowns) {
        return new ScoringResult(breakdowns.stream().sorted(RANK_ORDER).toList());
    }
}
