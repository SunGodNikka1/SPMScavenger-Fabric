package com.noobk.spmscavenger.opinion;

/**
 * GAO-3 — which discretionary executors exist for this mob right now.
 */
public record DiscretionaryAvailability(
        boolean exploreExecutorPresent,
        boolean restExecutorPresent,
        boolean socialExecutorPresent) {

    public boolean hasExecutor(DiscretionaryActivity activity) {
        return switch (activity) {
            case EXPLORE -> exploreExecutorPresent;
            case REST -> restExecutorPresent;
            // GAO-10: false until 44D binds FriendlyGreet. Reporting an executor this addon cannot
            // actually start would be the worst kind of lie here - it is exactly the input the
            // admission and start gates trust.
            case SOCIAL -> socialExecutorPresent;
        };
    }

    public DiscretionaryAvailability(boolean exploreExecutorPresent, boolean restExecutorPresent) {
        this(exploreExecutorPresent, restExecutorPresent, false);
    }

    public static DiscretionaryAvailability bothPresent() {
        return new DiscretionaryAvailability(true, true, false);
    }

    public static DiscretionaryAvailability none() {
        return new DiscretionaryAvailability(false, false, false);
    }
}
