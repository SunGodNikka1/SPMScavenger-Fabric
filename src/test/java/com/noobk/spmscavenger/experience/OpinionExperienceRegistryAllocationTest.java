package com.noobk.spmscavenger;

import com.noobk.spmscavenger.activity.ActivityClass;
import com.noobk.spmscavenger.activity.ActivityObservationService;
import com.noobk.spmscavenger.experience.OpinionExperienceRegistry;
import com.noobk.spmscavenger.experience.RestSessionCoordinator;
import com.noobk.spmscavenger.opinion.DiscretionaryActivityDirector;
import com.noobk.spmscavenger.opinion.ActivityAdmissions;
import com.noobk.spmscavenger.opinion.DiscretionaryAvailability;
import com.noobk.spmscavenger.opinion.OpinionFeatureGate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PERF-5A — read/check paths must not allocate experience contexts. */
class OpinionExperienceRegistryAllocationTest {

    @AfterEach
    void reset() {
        OpinionFeatureGate.clearTestOverride();
        OpinionExperienceRegistry.clearAll();
    }

    @Test
    void findAndHasLiveRestClaimDoNotAllocate() {
        UUID mob = UUID.randomUUID();

        assertNull(OpinionExperienceRegistry.find(mob));
        assertFalse(OpinionExperienceRegistry.hasLiveRestClaim(mob));
        assertEquals(0, OpinionExperienceRegistry.contextCount());

        OpinionExperienceRegistry.contextFor(mob);
        assertEquals(1, OpinionExperienceRegistry.contextCount());
        assertFalse(OpinionExperienceRegistry.hasLiveRestClaim(mob));
    }

    @Test
    void invalidateOnUnloadDoesNotAllocateAbsentContext() {
        UUID mob = UUID.randomUUID();

        RestSessionCoordinator.invalidateOnUnload(mob, 10L);

        assertEquals(0, OpinionExperienceRegistry.contextCount());
    }

    @Test
    void discretionaryDirectorDoesNotAllocateWhenOpinionDisabledAndNoContext() {
        OpinionFeatureGate.setTestOverride(false);
        UUID mob = UUID.randomUUID();
        ActivityObservationService.Observation observation =
                ActivityObservationService.summarize(EnumSet.noneOf(ActivityClass.class));

        DiscretionaryActivityDirector.tick(
                mob,
                0L,
                observation,
                new DiscretionaryAvailability(true, true),
                false,
                ActivityAdmissions.unavailable());

        assertEquals(0, OpinionExperienceRegistry.contextCount());
    }
}
