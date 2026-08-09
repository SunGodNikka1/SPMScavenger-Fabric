package com.noobk.spmscavenger.experience;

/**
 * GAO-0c — why a {@link RestSessionClaim} closed.
 */
public enum RestCloseReason {
    VALIDITY_LOST,
    MANDATORY_WORK,
    COMBAT,
    PLAYER_ORDER,
    TIMEOUT,
    MOB_LEFT_RADIUS,
    FIRE_INVALID,
    CHUNK_UNLOAD
}
