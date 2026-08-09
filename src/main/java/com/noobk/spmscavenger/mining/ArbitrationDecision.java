package com.noobk.spmscavenger.mining;

/**
 * MI-14C2 — whether a participating goal may run or continue under the current
 * {@link ExecutionIntent}.
 */
public enum ArbitrationDecision {
    /** This executor is the designated consumer for the actionable intent. */
    ALLOW,
    /** This ordinary chore must release {@code MOVE} for mining authority. */
    YIELD,
    /** Mining has no authority over this activity. */
    NEUTRAL;

    public boolean permitsAdmission() {
        return this != YIELD;
    }

    public boolean permitsDesignatedConsumer() {
        return this == ALLOW;
    }
}
