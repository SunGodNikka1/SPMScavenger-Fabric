package com.noobk.spmscavenger.opinion;

/**
 * GAO-4 — discretionary intent lifecycle states.
 */
public enum IntentLifecycle {
    PENDING,
    ADOPTED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    INTERRUPTED,
    INVALIDATED,
    EXPIRED,
    ABSTAINED;

    public boolean isActive() {
        return this == PENDING || this == ADOPTED || this == RUNNING;
    }

    public boolean isTerminal() {
        return switch (this) {
            case SUCCEEDED, FAILED, INTERRUPTED, INVALIDATED, EXPIRED, ABSTAINED -> true;
            default -> false;
        };
    }
}
