package com.noobk.spmscavenger.village.work;

import com.noobk.spmscavenger.village.VillagePerception;
import com.noobk.spmscavenger.village.VillagePerceptionTuning;

/**
 * Provisional caps for V3-D settlement-work observation (D-VR-083).
 */
public final class VillageWorkTuning {

    /** Reuse village perception radius family — settlement-bound counts from anchor. */
    public static final int OBSERVATION_RADIUS = VillagePerception.VILLAGE_QUERY_RADIUS;

    /** Freshness window — multiple of village heartbeat (Gate 0 provisional k=2). */
    public static final int FRESHNESS_WINDOW_TICKS = 2 * VillagePerceptionTuning.HEARTBEAT_TICKS;

    /** RET-1 bound for transient server cache entries. */
    public static final int MAX_CACHED_SETTLEMENTS = 64;

    /** Abort as INCOMPLETE when more HOME POI records would be scanned. */
    public static final int MAX_HOME_POIS_PER_OBSERVATION = 128;

    /** Abort as INCOMPLETE when more villager entities would be counted. */
    public static final int MAX_VILLAGERS_PER_OBSERVATION = 64;

    /** Abort as INCOMPLETE when more composter POI records would be scanned (V3-F). */
    public static final int MAX_COMPOSTERS_PER_OBSERVATION = 128;

    private VillageWorkTuning() {}
}
