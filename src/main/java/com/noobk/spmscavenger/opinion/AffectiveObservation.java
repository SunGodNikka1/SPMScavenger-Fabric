package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.activity.ActivityClass;
import com.noobk.spmscavenger.activity.ActivityObservationService;

import java.util.EnumSet;
import java.util.Set;

/**
 * GAO-1 — observation input for one 10-tick affect update.
 */
public record AffectiveObservation(
        boolean discretionaryIdleCandidate,
        boolean resting,
        boolean schedulerOccupied,
        boolean socialTravel,
        boolean meaningfulWork,
        int intervalTicks) {

    public static AffectiveObservation from(
            ActivityObservationService.Observation observation, int intervalTicks) {
        Set<ActivityClass> active = observation.activeClasses();
        boolean social = active.contains(ActivityClass.SOCIAL_TRAVEL);
        return new AffectiveObservation(
                observation.discretionaryIdleCandidate(),
                observation.resting(),
                observation.schedulerOccupied(),
                social,
                observation.meaningfulWorkForExpedition() || social,
                intervalTicks);
    }

    public boolean stalledOccupancy(boolean meaningfulProgressRecently) {
        return schedulerOccupied
                && !discretionaryIdleCandidate
                && !resting
                && !meaningfulProgressRecently;
    }

    static EnumSet<AffectiveChannel> channelsForBranch(AffectiveObservation observation, boolean progress) {
        if (observation.resting()) {
            return EnumSet.of(
                    AffectiveChannel.STRESS,
                    AffectiveChannel.ENGAGEMENT,
                    AffectiveChannel.BOREDOM);
        }
        if (observation.discretionaryIdleCandidate()) {
            return EnumSet.of(AffectiveChannel.BOREDOM);
        }
        if (observation.socialTravel()) {
            return EnumSet.of(AffectiveChannel.BOREDOM);
        }
        if (observation.stalledOccupancy(progress)) {
            return EnumSet.of(AffectiveChannel.BOREDOM);
        }
        return EnumSet.noneOf(AffectiveChannel.class);
    }

    enum AffectiveChannel {
        ENGAGEMENT,
        BOREDOM,
        SATISFACTION,
        STRESS,
        NOVELTY
    }
}
