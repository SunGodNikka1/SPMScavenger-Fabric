package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.ScavengerConfig;

/**
 * GAO-1 — central gate for whether mood updates run (GAO-PARITY when off).
 */
public final class OpinionFeatureGate {

    /** When non-null, overrides config for unit tests. */
    static Boolean testOverride;

    private OpinionFeatureGate() {
    }

    public static boolean isEnabled() {
        if (testOverride != null) {
            return testOverride;
        }
        try {
            return ScavengerConfig.get().opinionEnabled;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }
}
