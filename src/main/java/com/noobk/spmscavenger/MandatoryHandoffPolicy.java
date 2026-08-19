package com.noobk.spmscavenger;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * V2-DEF-003c-R1 — <b>the publication is the authority.</b>
 *
 * <h2>What the first attempt got wrong</h2>
 *
 * It claimed to answer "a handoff was published; should gather yield?" while independently
 * <i>reconstructing</i> whether a handoff ought to exist, from precursor + sweep result + selection.
 * That is the duplicated-authority pattern this workstream keeps removing, and it had a concrete
 * self-stall:
 *
 * <pre>
 * demand IRON_INGOT, and this scan did NOT cover RAW_IRON
 *   publishRouteExhaustion  -> refuses (scanCovers false) -> NO evidence
 *   reconstructed inference -> precursor present, not found in sweep, log selected -> YIELD
 *
 *   Gather: "I am yielding to trade."
 *   Trade:  reads RouteExhaustionEvidence, finds none, declines.
 * </pre>
 *
 * Deterministic stall, with both halves behaving correctly by their own lights.
 *
 * <p>So the decision now <b>consumes</b> a {@link HandoffPublication}, which exists only when
 * {@code RouteExhaustionEvidence.publish} actually ran. Every precondition the publisher enforces —
 * full sweep, no live-demand change, a modelled route, and {@code GatherRoutePrecursor.scanCovers}
 * — is inherited for free, because there is no second path to a yield.
 *
 * <h2>The protocol this is a small piece of</h2>
 *
 * <pre>
 * gather route --publishes exhaustion--&gt; HANDOFF AVAILABLE
 *                                          |
 *                    trade claims it ------+------ trade cannot serve it
 *                            |                            |
 *                 mandatory progression          optional work may resume
 * </pre>
 *
 * Not a global scheduler — just an explicit protocol for the one boundary that has now needed three
 * repairs.
 */
public final class MandatoryHandoffPolicy {

    /**
     * How long optional work stands aside for a published handoff.
     *
     * <h2>Why this number, and what kind of number it is</h2>
     *
     * <b>Implementation policy, not an architectural invariant.</b> The real event it stands in for
     * is "another route claimed or refused this handoff", which gather cannot observe without
     * coupling itself to trade's internals.
     *
     * <p>Sized so it cannot expire underneath trade's own retry lifecycle:
     * {@code TradeCandidateRound.EXHAUSTED_ROUND_COOLDOWN_TICKS} is 200 ticks, so a trade round that
     * fails and must wait out its cooldown still gets a full retry inside this window. The previous
     * form — three gather scans at 60 ticks, about 180 — could expire <i>before</i> trade was
     * legally allowed to try again, which is a stall built from two unrelated constants happening
     * not to line up.
     *
     * <p>Derived from that constant with a stated margin rather than tuned to match it: if trade's
     * cooldown changes, this should be re-derived, not nudged.
     */
    public static final long YIELD_WINDOW_TICKS =
            com.noobk.spmscavenger.village.trade.TradeCandidateRound.EXHAUSTED_ROUND_COOLDOWN_TICKS
                    * 2L;

    private MandatoryHandoffPolicy() {
    }

    /** A handoff that <b>actually happened</b>: evidence was published for this exact consumer. */
    public record HandoffPublication(
            ResourceLocation consumer,
            ResourceLocation material,
            GatherIntentPolicy.Resource precursor) {
    }

    /**
     * The concession window, bound to the handoff it belongs to.
     *
     * <p>A naked counter let one handoff inherit another's budget: if handoff A was taken and
     * handoff B arose before gather had a non-yield scan, B started part-spent. Identity is carried
     * so a new consumer/material episode opens a new window by construction rather than by
     * remembering to reset something.
     */
    public record YieldWindow(ResourceLocation consumer, ResourceLocation material, long openedAt) {

        public static final YieldWindow NONE = new YieldWindow(null, null, 0L);

        public boolean isFor(HandoffPublication publication) {
            return publication != null
                    && publication.consumer().equals(consumer)
                    && publication.material().equals(material);
        }

        public boolean expired(long now, long windowTicks) {
            return now - openedAt >= windowTicks;
        }
    }

    /**
     * Should gather decline this selection so a published handoff can be taken up?
     *
     * @param published the publisher's own result. Empty means no handoff exists — <b>and therefore
     *     no yield</b>, however tempting the surrounding facts look.
     * @param selectedFamily the family of the target gather chose
     * @param current the window carried from the previous scan
     * @return the window to carry forward when yielding, or empty to proceed with the selection
     */
    public static Optional<YieldWindow> yieldsToHandoff(
            Optional<HandoffPublication> published,
            Optional<GatherIntentPolicy.Resource> selectedFamily,
            YieldWindow current,
            long now) {
        if (published.isEmpty()) {
            return Optional.empty();
        }
        HandoffPublication handoff = published.get();
        // Work that serves the handed-off route is progress toward it, not an obstruction.
        if (selectedFamily.map(handoff.precursor()::equals).orElse(false)) {
            return Optional.empty();
        }
        YieldWindow window = current.isFor(handoff)
                ? current
                : new YieldWindow(handoff.consumer(), handoff.material(), now);
        if (window.expired(now, YIELD_WINDOW_TICKS)) {
            // Nobody took it. Standing still helps nobody, and an unbounded yield would only trade
            // one stall for a quieter one.
            return Optional.empty();
        }
        return Optional.of(window);
    }
}
