package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.experience.AffectPulse;
import com.noobk.spmscavenger.experience.ExperienceKind;

/**
 * GAO-1 — short-term mood channels per mob (server-side). Does not choose activities.
 */
public final class AffectiveState {

    private float engagement;
    private float boredom;
    private float satisfaction;
    private float stress;
    private float novelty;
    private int ticksSinceMeaningfulProgress;
    private boolean frozen;

    public float engagement() {
        return engagement;
    }

    public float boredom() {
        return boredom;
    }

    public float satisfaction() {
        return satisfaction;
    }

    public float stress() {
        return stress;
    }

    public float novelty() {
        return novelty;
    }

    public int ticksSinceMeaningfulProgress() {
        return ticksSinceMeaningfulProgress;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public void freeze() {
        frozen = true;
    }

    public void resume() {
        frozen = false;
    }

    public boolean meaningfulProgressRecently() {
        return ticksSinceMeaningfulProgress < AffectiveRates.PROGRESS_FRESHNESS_TICKS;
    }

    /**
     * Applies one observation cadence update. No-ops while frozen (PD-GAO-07).
     */
    public void observe(AffectiveObservation observation) {
        if (frozen) {
            return;
        }
        int interval = Math.max(1, observation.intervalTicks());
        ticksSinceMeaningfulProgress = Math.min(
                Integer.MAX_VALUE - interval, ticksSinceMeaningfulProgress + interval);

        var touched = AffectiveObservation.channelsForBranch(observation, meaningfulProgressRecently());

        if (observation.resting()) {
            stress = adjustChannel(stress, -AffectiveRates.REST_STRESS_FALL_PER_OBSERVATION);
            engagement = adjustChannel(engagement, AffectiveRates.REST_ENGAGEMENT_PER_OBSERVATION);
            boredom = adjustChannel(boredom, AffectiveRates.REST_BOREDOM_PER_OBSERVATION);
        } else if (observation.discretionaryIdleCandidate()) {
            boredom = adjustChannel(boredom, AffectiveRates.IDLE_BOREDOM_PER_OBSERVATION);
        } else if (observation.socialTravel()) {
            boredom = adjustChannel(boredom, -AffectiveRates.SOCIAL_BOREDOM_RELIEF_PER_OBSERVATION);
        } else if (observation.stalledOccupancy(meaningfulProgressRecently())) {
            boredom = adjustChannel(boredom, AffectiveRates.STALLED_BOREDOM_PER_OBSERVATION);
        }

        decayUntouchedChannels(touched);
    }

    /** Bounded pulse from {@link com.noobk.spmscavenger.experience.ActivityEpisode}. */
    public void applyPulse(AffectPulse pulse) {
        if (frozen) {
            return;
        }
        engagement = adjustChannel(engagement, pulse.engagementDelta());
        boredom = adjustChannel(boredom, pulse.boredomDelta());
        satisfaction = adjustChannel(satisfaction, pulse.satisfactionDelta());
        stress = adjustChannel(stress, pulse.stressDelta());
        novelty = adjustChannel(novelty, pulse.noveltyDelta());
        if (countsAsMeaningfulProgress(pulse.kind())) {
            ticksSinceMeaningfulProgress = 0;
        }
    }

    private void decayUntouchedChannels(java.util.Set<AffectiveObservation.AffectiveChannel> touched) {
        if (!touched.contains(AffectiveObservation.AffectiveChannel.ENGAGEMENT)) {
            engagement = decayTowardNeutral(engagement);
        }
        if (!touched.contains(AffectiveObservation.AffectiveChannel.BOREDOM)) {
            boredom = decayTowardNeutral(boredom);
        }
        if (!touched.contains(AffectiveObservation.AffectiveChannel.SATISFACTION)) {
            satisfaction = decayTowardNeutral(satisfaction);
        }
        if (!touched.contains(AffectiveObservation.AffectiveChannel.STRESS)) {
            stress = decayTowardNeutral(stress);
        }
        if (!touched.contains(AffectiveObservation.AffectiveChannel.NOVELTY)) {
            novelty = decayTowardNeutral(novelty);
        }
    }

    private static float decayTowardNeutral(float value) {
        if (value > 0f) {
            return Math.max(0f, value - AffectiveRates.DECAY_PER_OBSERVATION);
        }
        if (value < 0f) {
            return Math.min(0f, value + AffectiveRates.DECAY_PER_OBSERVATION);
        }
        return 0f;
    }

    private static float adjustChannel(float current, float delta) {
        return clamp(current + delta);
    }

    private static float clamp(float value) {
        return Math.max(AffectiveRates.CHANNEL_MIN, Math.min(AffectiveRates.CHANNEL_MAX, value));
    }

    private static boolean countsAsMeaningfulProgress(ExperienceKind kind) {
        return switch (kind) {
            case BLOCK_BROKEN, STAIR_STEP, PROJECT_END, CAVE_HANDOFF_ACCEPTED, ORE_ACQUIRED,
                    EXPEDITION_UNLOCKED, EXPEDITION_STAGE, RESOURCE_HARVEST, REST_SESSION,
                    SOCIAL_EXPEDITION, SOCIAL_INTERACTION -> true;
            default -> false;
        };
    }

    /** Package-private test seam for GAO-3 scoring tests. */
    void seedChannels(float engagement, float boredom, float satisfaction, float stress, float novelty) {
        this.engagement = clamp(engagement);
        this.boredom = clamp(boredom);
        this.satisfaction = clamp(satisfaction);
        this.stress = clamp(stress);
        this.novelty = clamp(novelty);
    }
}
