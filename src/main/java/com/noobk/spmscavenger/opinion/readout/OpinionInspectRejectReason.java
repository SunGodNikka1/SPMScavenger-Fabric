package com.noobk.spmscavenger.opinion.readout;

/**
 * GAO-8B Task 42B — server-side rejection before a snapshot is returned.
 */
public enum OpinionInspectRejectReason {
    NONE,
    PERMISSION_DENIED,
    OUT_OF_RANGE,
    NOT_PLAYER_MOB,
    NOT_ALIVE,
    WRONG_DIMENSION,
    ENTITY_NOT_FOUND,
    SPM_UNAVAILABLE
}
