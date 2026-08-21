package com.noobk.spmscavenger.village;

import com.noobk.spmscavenger.activity.ActivityObservationService;
import com.noobk.spmscavenger.activity.MandatoryOwnership;
import com.noobk.spmscavenger.activity.MandatoryOwnershipClaim;
import com.noobk.spmscavenger.opinion.InvalidationCause;

import java.util.Optional;

/**
 * D-VR-082-A1 / D-VR-084 — second consumer of the shared discretionary-permission seam.
 *
 * <p>Answers one question: <i>may this mob enter discretionary Village Work at all?</i> Profile gate
 * plus {@link MandatoryOwnership} only — settlement facts, crop legality, and storage permission
 * belong in future executor admission.
 */
public final class VillageWorkAdmission {

    public enum DenyCause {
        NONE,
        DENY_PROFILE,
        DENY_MANDATORY_AUTHORITY
    }

    public record Result(
            boolean permitted,
            DenyCause cause,
            InvalidationCause authorityCause) {
    }

    private VillageWorkAdmission() {
    }

    public static Result evaluate(
            VillageScenarioProfile profile,
            ActivityObservationService.Observation observation,
            boolean combatTarget,
            Optional<MandatoryOwnershipClaim> liveClaim,
            long now) {
        if (profile != VillageScenarioProfile.VILLAGE_ALLY) {
            return new Result(false, DenyCause.DENY_PROFILE, InvalidationCause.NONE);
        }
        MandatoryOwnership.Permission permission =
                MandatoryOwnership.evaluate(observation, combatTarget, liveClaim, now);
        if (!permission.eligible()) {
            return new Result(
                    false,
                    DenyCause.DENY_MANDATORY_AUTHORITY,
                    permission.cause());
        }
        return new Result(true, DenyCause.NONE, InvalidationCause.NONE);
    }
}
