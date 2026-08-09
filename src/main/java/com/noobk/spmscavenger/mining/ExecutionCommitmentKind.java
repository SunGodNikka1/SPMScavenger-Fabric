package com.noobk.spmscavenger.mining;

/** MI-14C2-R1 — persisted execution authority after a one-shot transition is consumed. */
public enum ExecutionCommitmentKind {
    /** Exploring is executing a cave route after {@link MiningProjectEnd#CAVE_FOUND} handoff. */
    CAVE_CONTINUATION
}
