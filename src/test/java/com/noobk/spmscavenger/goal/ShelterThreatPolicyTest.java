package com.noobk.spmscavenger.goal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShelterThreatPolicyTest {

    @Test
    void passiveHuntAndUnknownTargetDoNotManufactureEmergencyAuthority() {
        var passiveHunt = new ShelterThreatPolicy.Evidence(
                false, false, true, false, false, true, true);

        var threat = ShelterThreatPolicy.classify(passiveHunt);

        assertEquals(ShelterThreatPolicy.Threat.UNKNOWN_OR_PROACTIVE, threat);
        assertFalse(ShelterThreatPolicy.overridesShelter(threat));
    }

    @Test
    void onlyAttributableDangerOrPlayerCombatOrderOverrides() {
        assertTrue(ShelterThreatPolicy.overridesShelter(
                ShelterThreatPolicy.Threat.SELF_DEFENCE));
        assertTrue(ShelterThreatPolicy.overridesShelter(
                ShelterThreatPolicy.Threat.NEARBY_HOSTILE));
        assertTrue(ShelterThreatPolicy.overridesShelter(
                ShelterThreatPolicy.Threat.PLAYER_ORDERED_COMBAT));
        assertFalse(ShelterThreatPolicy.overridesShelter(
                ShelterThreatPolicy.Threat.UNKNOWN_OR_PROACTIVE));
    }

    @Test
    void hostileMarkerRequiresNearbyVisibleTarget() {
        assertEquals(ShelterThreatPolicy.Threat.UNKNOWN_OR_PROACTIVE,
                ShelterThreatPolicy.classify(new ShelterThreatPolicy.Evidence(
                        false, false, true, true, true, false, true)));
        assertEquals(ShelterThreatPolicy.Threat.UNKNOWN_OR_PROACTIVE,
                ShelterThreatPolicy.classify(new ShelterThreatPolicy.Evidence(
                        false, false, true, true, true, true, false)));
        assertEquals(ShelterThreatPolicy.Threat.UNKNOWN_OR_PROACTIVE,
                ShelterThreatPolicy.classify(new ShelterThreatPolicy.Evidence(
                        false, false, true, true, false, true, true)),
                "A merely visible hostile is proactive aggression, not an active threat");
        assertEquals(ShelterThreatPolicy.Threat.NEARBY_HOSTILE,
                ShelterThreatPolicy.classify(new ShelterThreatPolicy.Evidence(
                        false, false, true, true, true, true, true)));
    }
}
