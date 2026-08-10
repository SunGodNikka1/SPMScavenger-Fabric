package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.activity.ActivityClass;
import com.noobk.spmscavenger.activity.ActivityObservationService;
import com.noobk.spmscavenger.experience.ActivityKind;
import com.noobk.spmscavenger.experience.MobExperienceContext;
import com.noobk.spmscavenger.experience.OpinionExperienceRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GAO-4 control-plane repair — director EXPLORE intent must not outrun {@link
 * com.noobk.spmscavenger.goal.ExplorationReadiness} adoption, and wander must not yield for pending
 * intent alone (RFC Rule 5).
 */
class ExploreAdoptionControlPlaneTest {

    private static final UUID MOB = UUID.randomUUID();

    private DiscretionaryDirectorState director;

    @BeforeEach
    void setUp() {
        OpinionFeatureGate.testOverride = true;
        director = OpinionExperienceRegistry.contextFor(MOB).discretionaryDirector();
        director.clearForTest();
        seedHighBoredomExploreWinner();
    }

    @AfterEach
    void tearDown() {
        OpinionExperienceRegistry.clearAll();
        OpinionFeatureGate.testOverride = null;
    }

    @Test
    void exploreIntentNotIssuedWhileAdoptionNotReady() {
        director.tick(tick(10L, false));

        assertTrue(director.intent().isEmpty());
        assertFalse(DiscretionaryAuthority.mustYieldWander(MOB));
    }

    @Test
    void exploreIntentIssuesOnceAdoptionReady() {
        director.tick(tick(10L, false));
        assertTrue(director.intent().isEmpty());

        director.tick(tick(20L, true));

        assertEquals(DiscretionaryActivity.EXPLORE, director.intent().get().activity());
        assertEquals(IntentLifecycle.PENDING, director.intent().get().lifecycle());
        assertFalse(DiscretionaryAuthority.mustYieldWander(MOB));
    }

    @Test
    void pendingExploreDoesNotForceWanderYield() {
        director.tick(tick(10L, true));
        assertEquals(IntentLifecycle.PENDING, director.intent().get().lifecycle());
        assertFalse(DiscretionaryAuthority.mustYieldWander(MOB));
    }

    @Test
    void runningExploreForcesWanderYield() {
        director.tick(tick(10L, true));
        UUID exploreId = director.pendingIntent().map(DiscretionaryIntent::intentId).orElseThrow();
        DiscretionaryAuthority.onExploreAdopted(MOB, 12L);

        assertEquals(exploreId, director.runningIntent().map(DiscretionaryIntent::intentId).orElseThrow());
        assertTrue(DiscretionaryAuthority.mustYieldWander(MOB));
    }

    private DirectorTickInput tick(long gameTime, boolean exploreAdoptionReady) {
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(MOB);
        return new DirectorTickInput(
                gameTime,
                true,
                false,
                false,
                ActivityObservationService.summarize(
                        EnumSet.of(ActivityClass.IDLE_CANDIDATE, ActivityClass.PASSIVE_COSMETIC)),
                DiscretionaryScoringInput.withoutPlace(
                        context.affectiveState(),
                        context.opinionMemory(),
                        DiscretionaryAvailability.bothPresent(),
                        true,
                        true),
                exploreAdoptionReady);
    }

    private void seedHighBoredomExploreWinner() {
        OpinionMemory memory = OpinionExperienceRegistry.contextFor(MOB).opinionMemory();
        memory.seedActivity(ActivityKind.OVERLAND_EXPLORATION, 55f, 5f, 0);
        memory.seedActivity(ActivityKind.REST, 11f, 4f, 0);
        OpinionExperienceRegistry.contextFor(MOB).affectiveState().seedChannels(0f, 80f, 0f, 5f, 10f);
    }
}
