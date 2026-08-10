package com.noobk.spmscavenger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PERF-4 — install policy at {@code ENTITY_LOAD}. */
class SpmScavengerInstallPolicyTest {

    @Test
    void disabledInstallsOnlyLeaseCleanup() {
        ScavengerConfig cfg = new ScavengerConfig();
        cfg.enabled = false;

        assertTrue(SpmScavengerInstallPolicy.installsLeaseCleanupObserver(cfg));
        assertFalse(SpmScavengerInstallPolicy.installsExecutors(cfg));
        assertFalse(SpmScavengerInstallPolicy.appliesCombatChaseSpeed(cfg));
        assertFalse(SpmScavengerInstallPolicy.installsMiningExecutors(cfg));
        assertFalse(SpmScavengerInstallPolicy.installsOverlandExploration(cfg));
        assertFalse(SpmScavengerInstallPolicy.replacesHostStroll(cfg));
    }

    @Test
    void exploringFalseKeepsHostStrollButAllowsMining() {
        ScavengerConfig cfg = new ScavengerConfig();
        cfg.enabled = true;
        cfg.exploring = false;

        assertTrue(SpmScavengerInstallPolicy.installsExecutors(cfg));
        assertTrue(SpmScavengerInstallPolicy.appliesCombatChaseSpeed(cfg));
        assertTrue(SpmScavengerInstallPolicy.installsMiningExecutors(cfg));
        assertFalse(SpmScavengerInstallPolicy.installsOverlandExploration(cfg));
        assertFalse(SpmScavengerInstallPolicy.replacesHostStroll(cfg));
    }

    @Test
    void exploringTrueReplacesStrollAndInstallsOverland() {
        ScavengerConfig cfg = new ScavengerConfig();
        cfg.enabled = true;
        cfg.exploring = true;

        assertTrue(SpmScavengerInstallPolicy.installsOverlandExploration(cfg));
        assertTrue(SpmScavengerInstallPolicy.replacesHostStroll(cfg));
    }
}
