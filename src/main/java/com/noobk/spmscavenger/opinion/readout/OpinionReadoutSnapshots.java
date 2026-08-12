package com.noobk.spmscavenger.opinion.readout;

import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.experience.MobExperienceContext;
import com.noobk.spmscavenger.experience.OpinionExperienceRegistry;
import com.noobk.spmscavenger.goal.ShelterNightAuthority;
import com.noobk.spmscavenger.opinion.AffectiveState;
import com.noobk.spmscavenger.opinion.OpinionDecisionTrace;
import com.noobk.spmscavenger.opinion.ActivityAdmissions;
import com.noobk.spmscavenger.opinion.OpinionFeatureGate;
import com.noobk.spmscavenger.opinion.PersonalityModel;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * GAO-8B Task 42B — non-allocating snapshot factory (D-GAO-039).
 *
 * <p>Uses {@link OpinionExperienceRegistry#find} only; never {@code contextFor}.
 */
public final class OpinionReadoutSnapshots {

    private OpinionReadoutSnapshots() {
    }

    public static OpinionReadoutSnapshot unavailable(
            long requestId, int entityId, String mobDisplayName, OpinionReadoutStatus status) {
        return new OpinionReadoutSnapshot(
                requestId,
                entityId,
                mobDisplayName,
                status,
                summaryForStatus(status),
                0f, 0f, 0f, 0f, 0f, 0,
                false,
                PersonalityModel.NEUTRAL,
                Map.of(),
                Map.of(),
                0,
                0,
                false,
                Optional.empty(),
                "",
                "",
                "",
                "",
                "",
                ActivityAdmissionView.empty(),
                ActivityAdmissionView.empty(),
                List.of());
    }

    public static OpinionReadoutSnapshot capture(
            long requestId,
            int entityId,
            String mobDisplayName,
            MobExperienceContext context) {
        AffectiveState affect = context.affectiveState();
        Optional<ShelterNightAuthority.Hold> shelter = ShelterNightAuthority.hold(context.mobId());
        Optional<OpinionDecisionTrace.Decision> latest =
                OpinionReadoutExplanation.latestDecision(context);
        List<String> summary = OpinionReadoutExplanation.buildSummary(context, shelter, latest);
        if (summary.size() > OpinionReadoutSnapshot.MAX_SUMMARY_LINES) {
            summary = summary.subList(0, OpinionReadoutSnapshot.MAX_SUMMARY_LINES);
        }

        OpinionReadoutStatus status = OpinionFeatureGate.isEnabled()
                ? OpinionReadoutStatus.READY
                : OpinionReadoutStatus.OPINION_DISABLED;

        return new OpinionReadoutSnapshot(
                requestId,
                entityId,
                mobDisplayName,
                status,
                summary,
                affect.engagement(),
                affect.boredom(),
                affect.satisfaction(),
                affect.stress(),
                affect.novelty(),
                affect.ticksSinceMeaningfulProgress(),
                context.isFrozen(),
                context.personalityModel(),
                OpinionReadoutExplanation.boundedActivityPreferences(
                        context.opinionMemory().captureSnapshot()),
                OpinionReadoutExplanation.boundedEnvironmentPreferences(
                        context.environmentOpinionMemory().captureSnapshot()),
                context.placeOpinionMemory().captureSnapshot().size(),
                context.entityOpinionMemory().captureSnapshot().size(),
                context.hasLiveRestClaim(),
                OpinionReadoutExplanation.shelterView(context.mobId()),
                context.discretionaryDirector().incumbentActivity().map(Enum::name).orElse(""),
                context.discretionaryDirector().intent().map(i -> i.activity().name()).orElse(""),
                context.discretionaryDirector().intent().map(i -> i.lifecycle().name()).orElse(""),
                context.discretionaryDirector().restAuthorityPhase().name(),
                OpinionReadoutExplanation.currentDisposition(context),
                ActivityAdmissionView.from(context.discretionaryDirector().lastAdmissions().explore()),
                ActivityAdmissionView.from(context.discretionaryDirector().lastAdmissions().rest()),
                OpinionReadoutExplanation.recentDecisions(context));
    }

    public static Optional<OpinionReadoutSnapshot> captureIfPresent(
            long requestId, int entityId, String mobDisplayName, UUID mobId) {
        if (!PlayerMobs.available()) {
            return Optional.of(unavailable(
                    requestId, entityId, mobDisplayName, OpinionReadoutStatus.SPM_UNAVAILABLE));
        }
        MobExperienceContext context = OpinionExperienceRegistry.find(mobId);
        if (context == null) {
            return Optional.of(unavailable(
                    requestId, entityId, mobDisplayName, OpinionReadoutStatus.NO_CONTEXT));
        }
        return Optional.of(capture(requestId, entityId, mobDisplayName, context));
    }

    private static List<String> summaryForStatus(OpinionReadoutStatus status) {
        return switch (status) {
            case NO_CONTEXT -> List.of("No Opinion state yet for this mob.");
            case OPINION_DISABLED -> List.of("Opinion is disabled on this server.");
            case SPM_UNAVAILABLE -> List.of("Social Player Mobs is not available.");
            case READY -> List.of();
        };
    }
}
