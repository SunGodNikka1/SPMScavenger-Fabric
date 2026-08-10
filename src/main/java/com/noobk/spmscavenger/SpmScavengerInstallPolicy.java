package com.noobk.spmscavenger;

/**
 * PERF-4 — testable install policy for which Scavenger surfaces attach to a PlayerMob.
 *
 * <p>Runtime config changes do not re-wire already-loaded entities in v1; these rules apply at
 * {@code ENTITY_LOAD}.
 */
public final class SpmScavengerInstallPolicy {

    private SpmScavengerInstallPolicy() {
    }

    /** Master switch: ordinary executors and combat chase retune. */
    public static boolean installsExecutors(ScavengerConfig cfg) {
        return cfg.enabled;
    }

    /** PERF-4 — SPM {@code WeaponAwareAttackGoal} must stay stock when disabled. */
    public static boolean appliesCombatChaseSpeed(ScavengerConfig cfg) {
        return cfg.enabled;
    }

    /**
     * When false, SPM {@code WaterAvoidingRandomStrollGoal} is left in place; mining stack may still
     * install without replacing host stroll.
     */
    public static boolean replacesHostStroll(ScavengerConfig cfg) {
        return cfg.enabled && cfg.exploring;
    }

    public static boolean installsOverlandExploration(ScavengerConfig cfg) {
        return cfg.enabled && cfg.exploring;
    }

    public static boolean installsMiningExecutors(ScavengerConfig cfg) {
        return cfg.enabled;
    }

    public static boolean installsLeaseCleanupObserver(ScavengerConfig cfg) {
        return !cfg.enabled;
    }
}
