package com.noobk.spmscavenger.opinion;

/**
 * GAO-4 — why a discretionary intent reached {@link IntentLifecycle#INVALIDATED}.
 */
public enum InvalidationCause {
    NONE,
    MANDATORY_AUTHORITY,
    COMBAT_TARGET,
    PLAYER_COMMAND,
    UNKNOWN_ACTIVE,
    OPINION_DISABLED,
    UNLOAD_FREEZE,
    SUPERSEDED
}
