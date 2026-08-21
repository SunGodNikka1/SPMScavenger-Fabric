package com.noobk.spmscavenger.village;

/**
 * D-VR-080 — cross-dimension PlayerMob village-work policy.
 *
 * <p>{@link #NEUTRAL} is the default and is represented by <b>absence</b> in
 * {@link PlayerMobVillagePolicySavedData} — no row is written for NEUTRAL. Only
 * {@link #VILLAGE_ALLY} is persisted when explicitly assigned.
 */
public enum VillageScenarioProfile {
    NEUTRAL,
    VILLAGE_ALLY;

    /** Deserialize: known values map exactly; anything else → {@link #NEUTRAL}. */
    public static VillageScenarioProfile fromSerialized(String raw) {
        if (raw == null) {
            return NEUTRAL;
        }
        return switch (raw) {
            case "village_ally", "VILLAGE_ALLY" -> VILLAGE_ALLY;
            case "neutral", "NEUTRAL" -> NEUTRAL;
            default -> NEUTRAL;
        };
    }

    /** Wire format for persisted ally rows only. */
    public String serialized() {
        return switch (this) {
            case NEUTRAL -> "neutral";
            case VILLAGE_ALLY -> "village_ally";
        };
    }
}
