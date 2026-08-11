package com.noobk.spmscavenger.experience;

import com.noobk.spmscavenger.mining.MiningProject;
import com.noobk.spmscavenger.mining.MiningProjectEnd;
import com.noobk.spmscavenger.mining.MiningProjectMode;
import com.noobk.spmscavenger.opinion.ActivityOpinionMemory;
import com.noobk.spmscavenger.opinion.DiscretionaryAuthority;
import com.noobk.spmscavenger.opinion.EntityOpinionService;
import com.noobk.spmscavenger.opinion.EnvironmentClassifier;
import com.noobk.spmscavenger.opinion.EnvironmentKind;
import com.noobk.spmscavenger.opinion.EnvironmentProfile;
import com.noobk.spmscavenger.opinion.OpinionDecisionTrace;
import com.noobk.spmscavenger.opinion.OpinionFeatureGate;
import com.noobk.spmscavenger.opinion.OpinionLearningPolicy;
import com.noobk.spmscavenger.opinion.PlaceOpinionService;
import com.noobk.spmscavenger.progression.TaskLifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * GAO-0c — production emitters with causal episode ownership. Callers must not write affect or
 * opinion state directly.
 */
public final class ExperienceEmitters {

    private ExperienceEmitters() {
    }

    public static void expeditionUnlocked(Mob mob, UUID episodeId, long gameTime) {
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(mob);
        context.ensureEpisode(
                episodeId, gameTime, Optional.of(ActivityKind.OVERLAND_EXPLORATION));
        pipeline(mob).accept(new ExperienceEvent(
                ExperienceKind.EXPEDITION_UNLOCKED,
                gameTime,
                episodeId,
                OutcomeClass.VOLUNTARY_SUCCESS,
                ExperienceCause.EXPEDITION_UNLOCKED,
                0.25f,
                -0.1f,
                0.0f,
                0.0f,
                0.5f,
                Optional.of(ActivityKind.OVERLAND_EXPLORATION),
                Optional.of(mob.blockPosition()),
                Optional.empty()));
    }

    public static void expeditionTerminal(
            Mob mob,
            UUID episodeId,
            long startedGameTime,
            long gameTime,
            ExpeditionEndAttribution.Semantics semantics) {
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(mob);
        UUID sourceIntentId = DiscretionaryAuthority.runningExploreIntentId(mob.getUUID());
        LearningBefore learningBefore = sourceIntentId == null
                ? null
                : captureLearningBefore(context, ActivityKind.OVERLAND_EXPLORATION);
        context.ensureEpisode(
                episodeId, startedGameTime, Optional.of(ActivityKind.OVERLAND_EXPLORATION));
        Optional<EnvironmentProfile> environment = Optional.empty();
        if (OpinionFeatureGate.isEnabled()
                && semantics.cause() == ExperienceCause.EXPEDITION_COMPLETE
                && mob.level() instanceof ServerLevel level) {
            environment = Optional.of(EnvironmentClassifier.classify(level, mob.blockPosition()));
        }
        pipeline(mob).accept(new ExperienceEvent(
                ExperienceKind.EXPEDITION_END,
                gameTime,
                episodeId,
                semantics.outcome(),
                semantics.cause(),
                0.0f,
                0.0f,
                semantics.satisfactionDelta(),
                0.0f,
                0.0f,
                Optional.of(ActivityKind.OVERLAND_EXPLORATION),
                Optional.of(mob.blockPosition()),
                Optional.empty(),
                environment));
        recordLearningOutcome(
                context,
                sourceIntentId,
                ActivityKind.OVERLAND_EXPLORATION,
                ExperienceKind.EXPEDITION_END,
                semantics.outcome(),
                semantics.cause(),
                gameTime,
                learningBefore);
    }

    public static void miningProgress(
            Mob mob, MiningProject project, ExperienceKind kind, ExperienceCause cause, long gameTime) {
        ensureMiningEpisode(mob, project);
        ActivityKind activity = activityFor(project.mode());
        pipeline(mob).accept(new ExperienceEvent(
                kind,
                gameTime,
                episodeFor(project, mob.getUUID()),
                OutcomeClass.VOLUNTARY_SUCCESS,
                cause,
                0.1f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                Optional.of(activity),
                Optional.of(mob.blockPosition()),
                Optional.empty()));
    }

    public static void miningTerminal(
            Mob mob, MiningProject project, MiningProjectEnd end, BlockPos at, long gameTime) {
        ensureMiningEpisode(mob, project);
        OutcomeClass outcome = outcomeFor(end);
        ExperienceCause cause = causeFor(end);
        float stress = end == MiningProjectEnd.NO_PROGRESS || end == MiningProjectEnd.HAZARD ? 0.25f : 0.0f;
        pipeline(mob).accept(new ExperienceEvent(
                ExperienceKind.PROJECT_END,
                gameTime,
                episodeFor(project, mob.getUUID()),
                outcome,
                cause,
                0.0f,
                0.0f,
                end.lifecycle() == TaskLifecycle.SUCCESS ? 0.2f : 0.0f,
                stress,
                end == MiningProjectEnd.CAVE_FOUND ? 0.5f : 0.0f,
                Optional.of(activityFor(project.mode())),
                Optional.of(at),
                Optional.empty()));
        PlaceOpinionService.applyMiningTerminal(
                OpinionExperienceRegistry.contextFor(mob), end, at);
    }

    public static void restSessionOpened(UUID mobId, RestSessionClaim claim, long gameTime) {
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(mobId);
        context.ensureEpisode(claim.claimId(), gameTime, Optional.of(ActivityKind.REST));
        pipeline(mobId).accept(new ExperienceEvent(
                ExperienceKind.REST_SESSION,
                gameTime,
                claim.claimId(),
                OutcomeClass.VOLUNTARY_SUCCESS,
                ExperienceCause.REST_SESSION_OPEN,
                0.05f,
                -0.05f,
                0.0f,
                -0.05f,
                0.0f,
                Optional.of(ActivityKind.REST),
                Optional.of(claim.anchor()),
                Optional.empty()));
    }

    public static void socialCompanionJoined(
            Mob leader, UUID companionId, UUID expeditionEpisodeId, long gameTime) {
        OpinionExperienceRegistry.contextFor(leader);
        socialCompanionJoined(
                leader.getUUID(), companionId, expeditionEpisodeId, leader.blockPosition(), gameTime);
    }

    /**
     * GAO-6R — social terminal on a dedicated sub-episode; parent expedition episode stays open.
     */
    public static void socialCompanionJoined(
            UUID leaderId,
            UUID companionId,
            UUID expeditionEpisodeId,
            BlockPos at,
            long gameTime) {
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(leaderId);
        UUID socialEpisodeId =
                SocialExperienceEpisodes.companionInviteEpisodeId(expeditionEpisodeId, companionId);
        if (context.hasCompletedEpisode(socialEpisodeId)) {
            return;
        }
        context.ensureEpisode(socialEpisodeId, gameTime, Optional.of(ActivityKind.SOCIALIZING));
        pipeline(leaderId).accept(new ExperienceEvent(
                ExperienceKind.SOCIAL_EXPEDITION,
                gameTime,
                socialEpisodeId,
                OutcomeClass.VOLUNTARY_SUCCESS,
                ExperienceCause.SOCIAL_COMPANION_INVITE,
                0.15f,
                -0.05f,
                0.1f,
                0.0f,
                0.2f,
                Optional.of(ActivityKind.SOCIALIZING),
                Optional.of(at),
                Optional.of(companionId)));
        EntityOpinionService.applyCompanionInvite(context, companionId);
    }

    public static void restSessionClosed(
            UUID mobId, RestSessionClaim claim, RestCloseReason reason, long gameTime) {
        RestCloseAttribution.Semantics semantics = RestCloseAttribution.forReason(reason);
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(mobId);
        UUID sourceIntentId = claim.sourceIntentId().orElse(null);
        LearningBefore learningBefore = sourceIntentId == null
                ? null
                : captureLearningBefore(context, ActivityKind.REST);
        context.ensureEpisode(claim.claimId(), claim.arrivedAt(), Optional.of(ActivityKind.REST));
        pipeline(mobId).accept(new ExperienceEvent(
                ExperienceKind.REST_SESSION,
                gameTime,
                claim.claimId(),
                semantics.experienceOutcome(),
                semantics.experienceCause(),
                0.0f,
                0.0f,
                semantics.satisfactionDelta(),
                0.0f,
                0.0f,
                Optional.of(ActivityKind.REST),
                Optional.of(claim.anchor()),
                Optional.empty()));
        recordLearningOutcome(
                context,
                sourceIntentId,
                ActivityKind.REST,
                ExperienceKind.REST_SESSION,
                semantics.experienceOutcome(),
                semantics.experienceCause(),
                gameTime,
                learningBefore);
    }

    private static void ensureMiningEpisode(Mob mob, MiningProject project) {
        OpinionExperienceRegistry.contextFor(mob).ensureEpisode(
                episodeFor(project, mob.getUUID()),
                project.startedGameTime(),
                Optional.of(activityFor(project.mode())));
    }

    private static EpisodeRoutingPipeline pipeline(Mob mob) {
        return OpinionExperienceRegistry.contextFor(mob).pipeline();
    }

    private static EpisodeRoutingPipeline pipeline(UUID mobId) {
        return OpinionExperienceRegistry.contextFor(mobId).pipeline();
    }

    private static UUID episodeFor(MiningProject project, UUID mobId) {
        return RestSessionCoordinator.episodeIdForProject(
                mobId, project.startedGameTime(), project.mode().name());
    }

    private static ActivityKind activityFor(MiningProjectMode mode) {
        return switch (mode) {
            case CONTROLLED_DESCENT -> ActivityKind.CONTROLLED_DESCENT;
            case TUNNEL_SEARCH -> ActivityKind.TUNNEL_SEARCH;
            case CAVE_EXPLORATION -> ActivityKind.CAVE_EXPLORATION;
            default -> ActivityKind.RESOURCE_GATHERING;
        };
    }

    private static OutcomeClass outcomeFor(MiningProjectEnd end) {
        return switch (end.lifecycle()) {
            case SUCCESS -> OutcomeClass.VOLUNTARY_SUCCESS;
            case RETRY, BLOCKED -> OutcomeClass.EXECUTION_FAILURE;
            case INTERRUPTED -> OutcomeClass.PROTECTED_INTERRUPT;
            default -> OutcomeClass.VOLUNTARY_ABANDON;
        };
    }

    private static ExperienceCause causeFor(MiningProjectEnd end) {
        return switch (end) {
            case CAVE_FOUND -> ExperienceCause.MINING_CAVE_FOUND;
            case HANDOFF_TUNNEL_SEARCH -> ExperienceCause.MINING_TUNNEL_HANDOFF;
            case NO_PROGRESS -> ExperienceCause.MINING_NO_PROGRESS;
            case SEARCH_BUDGET_EXHAUSTED -> ExperienceCause.MINING_BUDGET_EXHAUSTED;
            case HAZARD -> ExperienceCause.MINING_HAZARD;
            case COMBAT -> ExperienceCause.MINING_COMBAT;
            case PLAYER_ORDER -> ExperienceCause.MINING_PLAYER_ORDER;
            case LEASE_EXPIRED -> ExperienceCause.MINING_LEASE_EXPIRED;
            case TOOL_FAILURE -> ExperienceCause.ENVIRONMENT_BLOCKED;
            default -> ExperienceCause.UNSPECIFIED;
        };
    }

    private static LearningBefore captureLearningBefore(
            MobExperienceContext context, ActivityKind activity) {
        return new LearningBefore(
                activitySnapshot(context.opinionMemory().captureSnapshot(), activity),
                context.placeOpinionMemory().captureSnapshot(),
                context.environmentOpinionMemory().captureSnapshot());
    }

    private static void recordLearningOutcome(
            MobExperienceContext context,
            UUID sourceIntentId,
            ActivityKind activity,
            ExperienceKind terminalKind,
            OutcomeClass outcome,
            ExperienceCause cause,
            long gameTime,
            LearningBefore before) {
        if (sourceIntentId == null || before == null) {
            return;
        }
        Map<ActivityKind, ActivityOpinionMemory.Snapshot> activities =
                context.opinionMemory().captureSnapshot();
        OpinionDecisionTrace.ActivityLearningDelta activityDelta = activityDelta(
                before.activitySnapshot(), activitySnapshot(activities, activity));
        Map<Long, Float> placeDeltas = deltas(
                before.placePreferences(), context.placeOpinionMemory().captureSnapshot());
        Map<EnvironmentKind, Float> environmentDeltas = deltas(
                before.environmentPreferences(),
                context.environmentOpinionMemory().captureSnapshot());
        float learningWeight = ExperienceOutcomePolicy.preferenceSign(outcome, cause);
        EpisodeLearningEvidence evidence = new EpisodeLearningEvidence(
                sourceIntentId,
                Optional.of(activity),
                terminalKind,
                outcome,
                cause,
                learningWeight,
                gameTime);
        boolean activityEligible = activityDelta.changedAnything()
                || (learningWeight != 0f && OpinionLearningPolicy.accepts(evidence));
        DiscretionaryAuthority.onLearningObserved(
                context.mobId(),
                sourceIntentId,
                new OpinionDecisionTrace.LearningOutcome(
                        gameTime,
                        activity,
                        terminalKind,
                        outcome,
                        cause,
                        activityEligible,
                        activityDelta,
                        placeDeltas,
                        environmentDeltas));
    }

    private static ActivityOpinionMemory.Snapshot activitySnapshot(
            Map<ActivityKind, ActivityOpinionMemory.Snapshot> activities, ActivityKind activity) {
        ActivityOpinionMemory.Snapshot snapshot = activities.get(activity);
        return snapshot == null
                ? new ActivityOpinionMemory.Snapshot(0f, 0f, 0f, 0, 0L, 0L)
                : snapshot;
    }

    private static OpinionDecisionTrace.ActivityLearningDelta activityDelta(
            ActivityOpinionMemory.Snapshot before, ActivityOpinionMemory.Snapshot after) {
        return new OpinionDecisionTrace.ActivityLearningDelta(
                after.preference() - before.preference(),
                after.repetition() - before.repetition(),
                after.recentReward() - before.recentReward(),
                after.recentFailures() - before.recentFailures(),
                after.lastPerformed() - before.lastPerformed(),
                after.recentDuration() - before.recentDuration());
    }

    private static <K> Map<K, Float> deltas(Map<K, Float> before, Map<K, Float> after) {
        Set<K> keys = new HashSet<>(before.keySet());
        keys.addAll(after.keySet());
        Map<K, Float> result = new LinkedHashMap<>();
        for (K key : keys) {
            float delta = after.getOrDefault(key, 0f) - before.getOrDefault(key, 0f);
            if (delta != 0f) {
                result.put(key, delta);
            }
        }
        return Map.copyOf(result);
    }

    private record LearningBefore(
            ActivityOpinionMemory.Snapshot activitySnapshot,
            Map<Long, Float> placePreferences,
            Map<EnvironmentKind, Float> environmentPreferences) {}
}
