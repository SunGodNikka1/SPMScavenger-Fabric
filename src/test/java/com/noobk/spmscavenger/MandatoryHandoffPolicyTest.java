package com.noobk.spmscavenger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noobk.spmscavenger.village.trade.TradeCandidateRound;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;

/**
 * V2-DEF-003c-R1 — the scheduling half, with <b>publication as the single authority</b>.
 *
 * <h2>The run this came from</h2>
 *
 * <pre>
 * GATHER PUBLISHED exhaustion for minecraft:iron_ingot
 * ROUTE  iron_ingot INFEASIBLE -&gt; trade may displace
 * PLAN   #1 TE armorer  Q1: 22 oak_log -&gt; 1 emerald
 * TRADE  #1 NO_ROOM   logs 324-&gt;324
 * </pre>
 *
 * Knowledge moved; scheduling did not. Gather kept the shared priority-3 slot for a wealth log,
 * chopped it, and the four logs filled the slot the emerald needed.
 *
 * <h2>And the defect in the first repair</h2>
 *
 * That fix reconstructed whether a handoff <i>ought</i> to exist rather than consuming the fact that
 * one <i>did</i>. It omitted {@code GatherRoutePrecursor.scanCovers}, so a scan that never looked
 * for iron could still make gather stand aside for a handoff nobody had published — gather waiting
 * for trade, trade seeing no evidence, both correct, neither moving.
 */
class MandatoryHandoffPolicyTest {

    private static final ResourceLocation CONSUMER =
            ResourceLocation.fromNamespaceAndPath("spmscavenger", "iron_pickaxe_upgrade");
    private static final ResourceLocation TORCH_CONSUMER =
            ResourceLocation.fromNamespaceAndPath("spmscavenger", "torch_chain");
    private static final ResourceLocation IRON_INGOT =
            ResourceLocation.withDefaultNamespace("iron_ingot");
    private static final ResourceLocation CHARCOAL =
            ResourceLocation.withDefaultNamespace("charcoal");

    private static final Optional<GatherIntentPolicy.Resource> LOGS =
            Optional.of(GatherIntentPolicy.Resource.LOGS);
    private static final Optional<GatherIntentPolicy.Resource> IRON =
            Optional.of(GatherIntentPolicy.Resource.RAW_IRON);

    private static Optional<MandatoryHandoffPolicy.HandoffPublication> ironPublished() {
        return Optional.of(new MandatoryHandoffPolicy.HandoffPublication(
                CONSUMER, IRON_INGOT, GatherIntentPolicy.Resource.RAW_IRON));
    }

    // ------------------------------------------------------------------ authority

    /**
     * <b>No publication, no yield</b> — the control the first repair was missing.
     *
     * <p>A scan that did not cover RAW_IRON is forbidden to publish exhaustion. The reconstructing
     * version still saw "precursor present, not found in this sweep, log selected" and yielded, so
     * gather stood aside for a handoff that did not exist and trade declined for want of evidence.
     */
    @Test
    void mustNotHappen_gatherYieldsWithoutAPublishedHandoff() {
        assertTrue(MandatoryHandoffPolicy.yieldsToHandoff(
                        Optional.empty(), LOGS, MandatoryHandoffPolicy.YieldWindow.NONE, 1_000L)
                .isEmpty(),
                "the publisher refused, so there is nothing to stand aside for - a self-stall "
                        + "where both halves are individually correct");
    }

    /** Every publisher precondition is inherited, because there is only one path to a yield. */
    @Test
    void mustHappen_theSchedulerHasNoSecondPathToAYield() {
        assertEquals(1, java.util.Arrays.stream(MandatoryHandoffPolicy.class.getMethods())
                        .filter(method -> method.getName().equals("yieldsToHandoff"))
                        .peek(method -> assertEquals(
                                Optional.class, method.getParameterTypes()[0],
                                "the first argument must be the publisher's own result, not facts "
                                        + "the scheduler could re-derive a decision from"))
                        .count());
    }

    // ------------------------------------------------------------------ the decision

    @Test
    void mustHappen_unrelatedWealthYieldsToAPublishedHandoff() {
        assertTrue(MandatoryHandoffPolicy.yieldsToHandoff(
                        ironPublished(), LOGS, MandatoryHandoffPolicy.YieldWindow.NONE, 1_000L)
                .isPresent(),
                "a log does not serve the iron route, and holding the shared priority-3 slot is "
                        + "what stopped trade taking over");
    }

    @Test
    void mustNotHappen_gatherYieldsWorkThatServesTheHandedOffRoute() {
        assertTrue(MandatoryHandoffPolicy.yieldsToHandoff(
                        ironPublished(), IRON, MandatoryHandoffPolicy.YieldWindow.NONE, 1_000L)
                .isEmpty(),
                "selecting the demanded resource IS progress toward it");
    }

    @Test
    void mustNotHappen_anUnclassifiedTargetIsTreatedAsServingTheRoute() {
        assertTrue(MandatoryHandoffPolicy.yieldsToHandoff(
                        ironPublished(), Optional.empty(),
                        MandatoryHandoffPolicy.YieldWindow.NONE, 1_000L)
                .isPresent(),
                "a target of no gatherable family cannot be iron progress");
    }

    // ------------------------------------------------------------------ window identity

    /**
     * A new handoff opens a new window.
     *
     * <p>The previous naked counter let handoff B start part-spent because handoff A had used some
     * of the budget and gather had not had a non-yield scan in between.
     */
    @Test
    void mustNotHappen_oneHandoffInheritsAnothersBudget() {
        MandatoryHandoffPolicy.YieldWindow ironWindow = MandatoryHandoffPolicy.yieldsToHandoff(
                ironPublished(), LOGS, MandatoryHandoffPolicy.YieldWindow.NONE, 1_000L).orElseThrow();

        long nearlyExpired = 1_000L + MandatoryHandoffPolicy.YIELD_WINDOW_TICKS - 1;
        Optional<MandatoryHandoffPolicy.HandoffPublication> torch =
                Optional.of(new MandatoryHandoffPolicy.HandoffPublication(
                        TORCH_CONSUMER, CHARCOAL, GatherIntentPolicy.Resource.COAL));

        MandatoryHandoffPolicy.YieldWindow torchWindow = MandatoryHandoffPolicy
                .yieldsToHandoff(torch, LOGS, ironWindow, nearlyExpired).orElseThrow();

        assertNotEquals(ironWindow.openedAt(), torchWindow.openedAt(),
                "a different consumer/material is a different episode and starts its own window");
        assertEquals(nearlyExpired, torchWindow.openedAt());
        assertEquals(CHARCOAL, torchWindow.material());
    }

    /** The same handoff keeps its original window rather than restarting it every scan. */
    @Test
    void mustNotHappen_theSameHandoffRestartsItsWindowEveryScan() {
        MandatoryHandoffPolicy.YieldWindow first = MandatoryHandoffPolicy.yieldsToHandoff(
                ironPublished(), LOGS, MandatoryHandoffPolicy.YieldWindow.NONE, 1_000L).orElseThrow();
        MandatoryHandoffPolicy.YieldWindow later = MandatoryHandoffPolicy.yieldsToHandoff(
                ironPublished(), LOGS, first, 1_060L).orElseThrow();

        assertEquals(1_000L, later.openedAt(),
                "otherwise the window never expires and the bound is decorative");
    }

    // ------------------------------------------------------------------ the bound

    /**
     * The window must outlive trade's own retry lifecycle.
     *
     * <p>The first form was three gather scans at 60 ticks — about 180 — while a failed trade round
     * waits {@code EXHAUSTED_ROUND_COOLDOWN_TICKS} = 200 before it may retry. Gather's concession
     * could expire <b>before trade was legally allowed to try again</b>: a stall assembled from two
     * unrelated constants that happened not to line up.
     *
     * <p>This asserts the relationship, not the number, so re-tuning either constant is caught here
     * rather than in a runtime session.
     */
    @Test
    void mustNotHappen_theWindowExpiresUnderneathTradesRetryCycle() {
        assertTrue(MandatoryHandoffPolicy.YIELD_WINDOW_TICKS
                        > TradeCandidateRound.EXHAUSTED_ROUND_COOLDOWN_TICKS,
                "a trade round that fails and waits out its cooldown must still get a retry "
                        + "inside the concession window");

        long afterOneFailedRound = 1_000L + TradeCandidateRound.EXHAUSTED_ROUND_COOLDOWN_TICKS;
        MandatoryHandoffPolicy.YieldWindow window = MandatoryHandoffPolicy.yieldsToHandoff(
                ironPublished(), LOGS, MandatoryHandoffPolicy.YieldWindow.NONE, 1_000L).orElseThrow();

        assertTrue(MandatoryHandoffPolicy
                        .yieldsToHandoff(ironPublished(), LOGS, window, afterOneFailedRound)
                        .isPresent(),
                "still conceding when trade becomes eligible to retry");
    }

    /** And it does end, so a handoff nobody can serve cannot freeze optional work. */
    @Test
    void mustNotHappen_anUntakenHandoffFreezesGatherForever() {
        MandatoryHandoffPolicy.YieldWindow window = MandatoryHandoffPolicy.yieldsToHandoff(
                ironPublished(), LOGS, MandatoryHandoffPolicy.YieldWindow.NONE, 1_000L).orElseThrow();

        assertTrue(MandatoryHandoffPolicy.yieldsToHandoff(ironPublished(), LOGS, window,
                        1_000L + MandatoryHandoffPolicy.YIELD_WINDOW_TICKS).isEmpty(),
                "nobody took it; standing still helps nobody");
        assertTrue(MandatoryHandoffPolicy.yieldsToHandoff(ironPublished(), LOGS, window,
                        1_000L + MandatoryHandoffPolicy.YIELD_WINDOW_TICKS * 10).isEmpty());
    }

    /** The bound is implementation policy standing in for an event, and says so. */
    @Test
    void mustHappen_theWindowIsDerivedFromTradesCooldownNotInvented() throws java.io.IOException {
        String policy = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/noobk/spmscavenger/MandatoryHandoffPolicy.java"));

        assertTrue(policy.contains("EXHAUSTED_ROUND_COOLDOWN_TICKS"),
                "derived from the lifecycle it must outlive, so a change there forces a re-derive");
        assertTrue(policy.contains("Implementation policy, not an architectural invariant"),
                "the real event is 'another route claimed or refused this handoff'; the timer is a "
                        + "stand-in and must not be read as the design");
    }

    // ------------------------------------------------------------------ production binding

    @Test
    void mustHappen_theGoalConsumesThePublisherResult() throws java.io.IOException {
        String gather = java.nio.file.Files.readString(java.nio.file.Path.of(
                        "src/main/java/com/noobk/spmscavenger/goal/GatherResourcesGoal.java"))
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");

        assertTrue(gather.contains("handoff =") && gather.contains("publishRouteExhaustion(cfg, now)"),
                "the publisher's own result is captured");
        assertTrue(gather.contains("yieldsToHandoff(handoff,"),
                "and handed straight to the decision - no re-derivation in between");
        assertFalse(gather.contains("handoffYields"),
                "the naked counter is gone; identity lives in the window");
    }
}
