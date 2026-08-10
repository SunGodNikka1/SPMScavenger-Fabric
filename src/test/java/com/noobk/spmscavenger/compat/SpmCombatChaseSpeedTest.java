package com.noobk.spmscavenger.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpmCombatChaseSpeedTest {

    @Test
    void clampsFightingChaseSpeed() {
        assertEquals(1.0, SpmCombatChaseSpeed.clampSpeed(0.5));
        assertEquals(1.35, SpmCombatChaseSpeed.clampSpeed(1.35));
        assertEquals(1.4, SpmCombatChaseSpeed.clampSpeed(1.4));
        assertEquals(1.5, SpmCombatChaseSpeed.clampSpeed(2.0));
    }
}
