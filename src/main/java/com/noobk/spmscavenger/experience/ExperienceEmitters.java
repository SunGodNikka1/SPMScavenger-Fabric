package com.noobk.spmscavenger.experience;

import com.noobk.spmscavenger.mining.MiningProject;
import com.noobk.spmscavenger.mining.MiningProjectEnd;
import com.noobk.spmscavenger.mining.MiningProjectMode;
import com.noobk.spmscavenger.experience.OpinionExperienceRegistry;
import com.noobk.spmscavenger.opinion.EntityOpinionService;
import com.noobk.spmscavenger.opinion.PlaceOpinionService;
import com.noobk.spmscavenger.progression.TaskLifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;

import java.util.Optional;
import java.util.UUID;

/**
 * GAO-0c — production emitters with causal episode ownership. Callers must not write affect or
 * opinion state directly.
 */
public final class ExperienceEmitters {

    private ExperienceEmitters() {
    }

    public static void expeditionUnlocked(Mob mob, UUID episodeId, long gameTime) {
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(mob.getUUID());
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
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(mob.getUUID());
        context.ensureEpisode(
                episodeId, startedGameTime, Optional.of(ActivityKind.OVERLAND_EXPLORATION));
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
                Optional.empty()));
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
                OpinionExperienceRegistry.contextFor(mob.getUUID()), end, at);
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
    }

    private static void ensureMiningEpisode(Mob mob, MiningProject project) {
        OpinionExperienceRegistry.contextFor(mob.getUUID()).ensureEpisode(
                episodeFor(project, mob.getUUID()),
                project.startedGameTime(),
                Optional.of(activityFor(project.mode())));
    }

    private static EpisodeRoutingPipeline pipeline(Mob mob) {
        return pipeline(mob.getUUID());
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
}
