package com.noobk.spmscavenger.opinion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VR-T1.5c — bounded greet claim window between admission pulse and native greet start.
 */
class SocialGreetClaimWindowTest {

    private static final UUID MOB = UUID.randomUUID();
    private static final UUID TARGET = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        SocialGreetClaimWindow.clearForTest();
    }

    @Test
    void mustHappen_claimWindowDerivedFromObserverCadence() throws Exception {
        String observer = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/goal/ExplorationActivityGoal.java"));
        assertTrue(observer.contains("private static final int OBSERVE_INTERVAL = 10"),
                "observer cadence must remain documented at 10 ticks for this derivation");

        assertEquals(10, DiscretionaryDirectorConstants.OPINION_OBSERVE_INTERVAL_TICKS);
        assertEquals(13, DiscretionaryDirectorConstants.GREET_CLAIM_WINDOW_TICKS,
                "worst-case phase (9) + observer pass (1) + canUse retry slack (3)");
    }

    @Test
    void mustHappen_boundClaimProceedsImmediatelyWithoutDefer() {
        assertEquals(SocialGreetClaimWindow.Outcome.PROCEED,
                SocialGreetClaimWindow.evaluate(MOB, TARGET, 100L, true));
        assertEquals(0, SocialGreetClaimWindow.trackedClaimCount());
    }

    @Test
    void mustHappen_unboundClaimDefersUntilWindowExpires() {
        long start = 1_000L;
        int window = DiscretionaryDirectorConstants.GREET_CLAIM_WINDOW_TICKS;

        for (long tick = start; tick < start + window; tick++) {
            assertEquals(SocialGreetClaimWindow.Outcome.DEFER,
                    SocialGreetClaimWindow.evaluate(MOB, TARGET, tick, false),
                    "tick " + tick + " should defer");
        }
        assertEquals(SocialGreetClaimWindow.Outcome.PROCEED,
                SocialGreetClaimWindow.evaluate(MOB, TARGET, start + window, false),
                "timeout must release native greet");
        assertEquals(0, SocialGreetClaimWindow.trackedClaimCount());
    }

    @Test
    void mustHappen_targetChangeResetsTheWindow() {
        UUID other = UUID.randomUUID();
        SocialGreetClaimWindow.evaluate(MOB, TARGET, 100L, false);
        assertEquals(SocialGreetClaimWindow.Outcome.DEFER,
                SocialGreetClaimWindow.evaluate(MOB, other, 110L, false),
                "new target opens a fresh claim window");
    }

    @Test
    void mustHappen_lifecycleReleaseClearsPendingClaim() {
        SocialGreetClaimWindow.evaluate(MOB, TARGET, 100L, false);
        assertEquals(1, SocialGreetClaimWindow.trackedClaimCount());
        SocialGreetClaimWindow.release(MOB);
        assertEquals(0, SocialGreetClaimWindow.trackedClaimCount());
    }
}
