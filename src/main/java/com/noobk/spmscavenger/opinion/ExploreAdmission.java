package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.goal.ExplorationReadiness;

/**
 * GAO-4R — maps {@link ExplorationReadiness} into the generic admission contract.
 */
public final class ExploreAdmission {

    private ExploreAdmission() {
    }

    public static ActivityAdmission inspect(
            boolean executorPresent,
            ExplorationReadiness readiness,
            long now,
            int localTripThreshold,
            int idleTickThreshold) {
        if (!executorPresent) {
            return ActivityAdmission.executorAbsent();
        }
        if (readiness.eligibleForNewExpedition(now, localTripThreshold, idleTickThreshold)) {
            return ActivityAdmission.ready(true);
        }
        return ActivityAdmission.blocked(
                true,
                ActivityAdoptionBlocker.EXPLORE_NOT_READY,
                readiness.formatAdoptionBlocker(now, localTripThreshold, idleTickThreshold));
    }
}
