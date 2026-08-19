package com.noobk.spmscavenger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.Optional;

/**
 * V2-DEF-003c — the scheduling half of the handoff.
 *
 * <p>Knowledge moved correctly: gather published iron exhaustion and
 * {@code ExistingRouteFeasibility} flipped to INFEASIBLE. Then gather kept the priority-3
 * deliberate-work slot for a wealth log, chopped it, and the four logs it returned with filled the
 * one free backpack slot the incoming emerald needed:
 *
 * <pre>
 * TRADE #1 NO_ROOM   logs 324-&gt;324   em 0-&gt;0
 * </pre>
 *
 * The capacity failure is the symptom. The defect is that optional work outranked a handoff that had
 * already been declared.
 */
class MandatoryHandoffPolicyTest {

    private static final Optional<GatherIntentPolicy.Resource> IRON =
            Optional.of(GatherIntentPolicy.Resource.RAW_IRON);
    private static final Optional<GatherIntentPolicy.Resource> LOGS =
            Optional.of(GatherIntentPolicy.Resource.LOGS);

    /** The observed run: iron route exhausted, wealth log selected. */
    @Test
    void mustHappen_unrelatedWealthYieldsToAPendingHandoff() {
        assertTrue(MandatoryHandoffPolicy.yieldsToHandoff(IRON, false, LOGS, 0),
                "the iron route was just declared exhausted; a log does not serve it, and holding "
                        + "the shared priority-3 slot is what stopped trade taking over");
    }

    /** If the sweep did find the mandatory resource, nothing was handed off and gather works on. */
    @Test
    void mustNotHappen_gatherYieldsWhileItsOwnRouteIsAlive() {
        assertFalse(MandatoryHandoffPolicy.yieldsToHandoff(IRON, true, LOGS, 0),
                "iron is in radius - there is no handoff pending and no reason to stand aside");
        assertFalse(MandatoryHandoffPolicy.yieldsToHandoff(IRON, true, IRON, 0));
    }

    /** Work that serves the mandatory route is never yielded. */
    @Test
    void mustNotHappen_gatherYieldsWorkThatServesTheMandatoryRoute() {
        assertFalse(MandatoryHandoffPolicy.yieldsToHandoff(IRON, false, IRON, 0),
                "selecting the demanded resource IS progress toward it");
    }

    /** With no modelled mandatory route there is nothing to hand off. */
    @Test
    void mustNotHappen_pureWealthWorkYieldsToNothing() {
        assertFalse(MandatoryHandoffPolicy.yieldsToHandoff(Optional.empty(), false, LOGS, 0),
                "no mandatory demand means ordinary wealth gathering is untouched");
    }

    /**
     * Bounded, so a handoff nobody takes cannot freeze the mob.
     *
     * <p>An unbounded yield would trade one stall for a quieter one: no merchant in range, no
     * affordable quote, and a mob standing beside a tree it is not allowed to chop. That is the
     * assign→refuse→assign shape, and it is why the cap exists rather than being assumed unnecessary.
     */
    @Test
    void mustNotHappen_anUntakenHandoffFreezesGatherForever() {
        for (int yields = 0; yields < MandatoryHandoffPolicy.MAX_CONSECUTIVE_YIELDS; yields++) {
            assertTrue(MandatoryHandoffPolicy.yieldsToHandoff(IRON, false, LOGS, yields),
                    "still inside the window at " + yields);
        }
        assertFalse(MandatoryHandoffPolicy.yieldsToHandoff(
                        IRON, false, LOGS, MandatoryHandoffPolicy.MAX_CONSECUTIVE_YIELDS),
                "the window has passed and nobody took the work - standing still helps nobody");
        assertFalse(MandatoryHandoffPolicy.yieldsToHandoff(IRON, false, LOGS, 99));
    }

    /** The production binding: reset on any non-yield, so the cap measures consecutive scans. */
    @Test
    void mustHappen_theYieldCounterIsConsecutiveNotCumulative() throws java.io.IOException {
        String gather = java.nio.file.Files.readString(java.nio.file.Path.of(
                        "src/main/java/com/noobk/spmscavenger/goal/GatherResourcesGoal.java"))
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");

        assertTrue(gather.contains("handoffYields = 0;"),
                "a cumulative counter would permanently disable the yield after three lifetime "
                        + "occurrences, which is not what a window means");
        assertTrue(gather.contains("yieldsToPendingHandoff(cfg, selected)"),
                "and the check sits on the selection path, where the slot is actually claimed");
    }
}
