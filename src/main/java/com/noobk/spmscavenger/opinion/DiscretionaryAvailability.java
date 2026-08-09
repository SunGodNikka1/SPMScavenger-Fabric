package com.noobk.spmscavenger.opinion;

/**
 * GAO-3 — which discretionary executors exist for this mob right now.
 */
public record DiscretionaryAvailability(boolean exploreExecutorPresent, boolean restExecutorPresent) {

    public boolean hasExecutor(DiscretionaryActivity activity) {
        return switch (activity) {
            case EXPLORE -> exploreExecutorPresent;
            case REST -> restExecutorPresent;
        };
    }

    public static DiscretionaryAvailability bothPresent() {
        return new DiscretionaryAvailability(true, true);
    }

    public static DiscretionaryAvailability none() {
        return new DiscretionaryAvailability(false, false);
    }
}
