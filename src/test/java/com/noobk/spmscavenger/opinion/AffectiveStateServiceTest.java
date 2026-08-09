package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.activity.ActivityClass;
import com.noobk.spmscavenger.activity.ActivityObservationService;
import com.noobk.spmscavenger.experience.ExperienceKind;
import com.noobk.spmscavenger.experience.OpinionExperienceRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AffectiveStateServiceTest {

    private static final UUID MOB = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        OpinionExperienceRegistry.clearAll();
        OpinionFeatureGate.testOverride = null;
    }

    @Test
    void opinionDisabledSkipsObservationUpdates() {
        OpinionFeatureGate.testOverride = false;
        var observation = ActivityObservationService.summarize(List.of(ActivityClass.IDLE_CANDIDATE));
        for (int i = 0; i < 200; i++) {
            AffectiveStateService.observe(MOB, observation, 10);
        }
        assertEquals(0f, OpinionExperienceRegistry.contextFor(MOB).affectiveState().boredom());
    }

    @Test
    void opinionEnabledWiresPulseIntoAffectiveState() {
        OpinionFeatureGate.testOverride = true;
        OpinionExperienceRegistry.contextFor(MOB).pipeline().accept(
                new com.noobk.spmscavenger.experience.ExperienceEvent(
                        ExperienceKind.ORE_ACQUIRED,
                        1L,
                        UUID.randomUUID(),
                        com.noobk.spmscavenger.experience.OutcomeClass.VOLUNTARY_SUCCESS,
                        com.noobk.spmscavenger.experience.ExperienceCause.EXPOSURE_ORE_TAKEN,
                        0.1f, -0.05f, 0.2f, 0f, 0.1f,
                        java.util.Optional.of(com.noobk.spmscavenger.experience.ActivityKind.RESOURCE_GATHERING),
                        java.util.Optional.empty(),
                        java.util.Optional.empty()));

        assertTrue(OpinionExperienceRegistry.contextFor(MOB).affectiveState().engagement() > 0f);
        assertEquals(0, OpinionExperienceRegistry.contextFor(MOB).affectiveState().ticksSinceMeaningfulProgress());
    }
}
