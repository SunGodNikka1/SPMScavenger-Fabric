package com.noobk.spmscavenger.progression;

/** Outcome of a planned work step or active executor session (D-VP-001). */
public enum TaskLifecycle {
    RUNNING,
    SUCCESS,
    FAILURE,
    BLOCKED,
    INTERRUPTED,
    RETRY
}
