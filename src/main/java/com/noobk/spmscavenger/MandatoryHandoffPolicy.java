package com.noobk.spmscavenger;

import java.util.Optional;

/**
 * V2-DEF-003c — <b>publishing a handoff is not performing one.</b>
 *
 * <h2>What the runtime showed</h2>
 *
 * <pre>
 * GATHER PUBLISHED exhaustion for minecraft:iron_ingot
 * ROUTE  iron_ingot INFEASIBLE -&gt; trade may displace
 * PLAN   #1 TE armorer  Q1: 22 oak_log -&gt; 1 emerald
 * REVAL  #1 Q2: 22 oak_log -&gt; 1 emerald  OK
 * TRADE  #1 NO_ROOM   logs 324-&gt;324   em 0-&gt;0
 * </pre>
 *
 * The knowledge handoff worked. The <b>scheduling</b> handoff did not: after declaring the iron
 * route exhausted, gather returned {@code true} for an unrelated wealth log, chopped it, and the 4
 * logs it brought back (320 → 324) filled the one free slot the incoming emerald needed. Gather and
 * trade share priority 3, so once gather owned the deliberate-work slot trade could not preempt it.
 *
 * <h2>The rule</h2>
 *
 * While a mandatory consumer route has been declared exhausted and is waiting to be taken over,
 * gather must not hold the deliberate-work slot for work that does not serve that route. Optional
 * wealth is still legitimate — it simply stops outranking a pending handoff.
 *
 * <h2>Bounded, so the mob cannot freeze</h2>
 *
 * Yielding is capped at {@link #MAX_CONSECUTIVE_YIELDS} scans. If nothing takes the slot in that
 * window — no merchant, no affordable quote — gather resumes its optional work rather than standing
 * still forever. An unbounded yield would trade one stall for a quieter one, which is the
 * assign→refuse→assign churn shape this project has shipped before.
 *
 * <p>Pure: it takes the facts and returns a decision, so the interesting combinations are unit
 * tests rather than runtime observations.
 */
public final class MandatoryHandoffPolicy {

    /** Scans a pending handoff may hold gather back before optional work resumes. */
    public static final int MAX_CONSECUTIVE_YIELDS = 3;

    private MandatoryHandoffPolicy() {
    }

    /**
     * Should gather decline this selection so a pending mandatory handoff can proceed?
     *
     * @param mandatoryPrecursor the gather resource the live mandatory demand needs, if it has a
     *     modelled route at all
     * @param mandatoryFoundInSweep whether the completed sweep actually turned that resource up —
     *     if it did, nothing was handed off and gather keeps working
     * @param selectedFamily the family of the target gather chose
     * @param consecutiveYields how many scans have already been yielded for this
     */
    public static boolean yieldsToHandoff(
            Optional<GatherIntentPolicy.Resource> mandatoryPrecursor,
            boolean mandatoryFoundInSweep,
            Optional<GatherIntentPolicy.Resource> selectedFamily,
            int consecutiveYields) {
        if (mandatoryPrecursor.isEmpty() || mandatoryFoundInSweep) {
            // No modelled mandatory route, or the route is alive and gather is serving it.
            return false;
        }
        if (consecutiveYields >= MAX_CONSECUTIVE_YIELDS) {
            // The window has passed and nobody took the work. Standing still helps nobody.
            return false;
        }
        // Serving the exhausted route itself is impossible by definition here, but a selection of
        // the same family would still be progress toward it - only unrelated work yields.
        return !selectedFamily.equals(mandatoryPrecursor);
    }
}
