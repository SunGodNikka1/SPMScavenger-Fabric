package com.noobk.spmscavenger.experience;

import com.noobk.spmscavenger.activity.ActivityClass;
import com.noobk.spmscavenger.activity.ActivityObservationService;
import com.noobk.spmscavenger.opinion.DiscretionaryAuthority;
import com.noobk.spmscavenger.opinion.OpinionFeatureGate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * GAO-0c — opens, validates, and closes arrival-anchored REST claims.
 */
public final class RestSessionCoordinator {

    private RestSessionCoordinator() {
    }

    public static Optional<RestSessionClaim> openCampfireRest(
            Mob mob, BlockPos firePos, BlockPos idlePos, long gameTime) {
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(mob.getUUID());
        if (context.hasLiveRestClaim()) {
            return context.restClaim();
        }
        UUID claimId = UUID.randomUUID();
        RestSessionClaim claim = new RestSessionClaim(
                claimId,
                Optional.empty(),
                claimId,
                RestSourceKind.DISCRETIONARY_REST,
                idlePos,
                RestAnchorType.CAMPFIRE,
                gameTime,
                gameTime,
                gameTime,
                Optional.empty());
        context.setRestClaim(Optional.of(claim));
        ExperienceEmitters.restSessionOpened(mob.getUUID(), claim, gameTime);
        return Optional.of(claim);
    }

    public static Optional<RestSessionClaim> openShelterRecovery(
            Mob mob, BlockPos anchor, RestAnchorType anchorType, long gameTime) {
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(mob.getUUID());
        if (context.hasLiveRestClaim()) {
            return context.restClaim();
        }
        UUID claimId = UUID.randomUUID();
        RestSessionClaim claim = new RestSessionClaim(
                claimId,
                Optional.empty(),
                claimId,
                RestSourceKind.SHELTER_RECOVERY,
                anchor,
                anchorType,
                gameTime,
                gameTime,
                gameTime,
                Optional.empty());
        context.setRestClaim(Optional.of(claim));
        ExperienceEmitters.restSessionOpened(mob.getUUID(), claim, gameTime);
        return Optional.of(claim);
    }

    public static void validate(Mob mob, ActivityObservationService.Observation observation, long gameTime) {
        if (mob == null) {
            return;
        }
        MobExperienceContext context = OpinionExperienceRegistry.find(mob.getUUID());
        if (context == null) {
            return;
        }
        Optional<RestSessionClaim> current = context.restClaim().filter(RestSessionClaim::isLive);
        if (current.isEmpty()) {
            return;
        }
        RestSessionClaim claim = current.get();
        Optional<RestCloseReason> reason = invalidationReason(mob, observation, claim, gameTime);
        if (reason.isPresent()) {
            close(context, claim, reason.get(), gameTime);
        } else {
            context.setRestClaim(Optional.of(claim.validated(gameTime)));
        }
    }

    public static void invalidateOnUnload(UUID mobId, long gameTime) {
        MobExperienceContext context = OpinionExperienceRegistry.find(mobId);
        if (context == null) {
            return;
        }
        context.restClaim().filter(RestSessionClaim::isLive).ifPresent(claim ->
                close(context, claim, RestCloseReason.CHUNK_UNLOAD, gameTime));
        context.invalidateEphemeral();
    }

    private static void close(
            MobExperienceContext context, RestSessionClaim claim, RestCloseReason reason, long gameTime) {
        RestSessionClaim closed = claim.closed(reason, gameTime);
        context.setRestClaim(Optional.of(closed));
        ExperienceEmitters.restSessionClosed(context.mobId(), closed, reason, gameTime);
        if (claim.sourceKind() == RestSourceKind.DISCRETIONARY_REST && OpinionFeatureGate.isEnabled()) {
            DiscretionaryAuthority.onRestClaimClosed(context.mobId(), gameTime, reason);
        }
    }

    private static Optional<RestCloseReason> invalidationReason(
            Mob mob,
            ActivityObservationService.Observation observation,
            RestSessionClaim claim,
            long gameTime) {
        if (mob.getTarget() != null) {
            return Optional.of(RestCloseReason.COMBAT);
        }
        if (mandatoryAuthority(observation)) {
            return Optional.of(RestCloseReason.MANDATORY_WORK);
        }
        if (gameTime - claim.arrivedAt() > RestSessionClaim.MAX_REST_TICKS) {
            return Optional.of(RestCloseReason.TIMEOUT);
        }
        if (mob.blockPosition().distSqr(claim.anchor()) > RestSessionClaim.REST_RADIUS_SQR) {
            return Optional.of(RestCloseReason.MOB_LEFT_RADIUS);
        }
        if (claim.anchorType() == RestAnchorType.CAMPFIRE && !campfireStillValid(mob.level(), claim.anchor())) {
            return Optional.of(RestCloseReason.FIRE_INVALID);
        }
        return Optional.empty();
    }

    private static boolean mandatoryAuthority(ActivityObservationService.Observation observation) {
        for (ActivityClass activity : observation.activeClasses()) {
            if (activity == ActivityClass.MANDATORY_COMBAT
                    || activity == ActivityClass.MANDATORY_COMMAND
                    || activity == ActivityClass.MANDATORY_SAFETY
                    || activity == ActivityClass.MANDATORY_SURVIVAL
                    || activity == ActivityClass.PROJECT_EXECUTION) {
                return true;
            }
        }
        return false;
    }

    private static boolean campfireStillValid(Level level, BlockPos idlePos) {
        for (BlockPos neighbour : BlockPos.betweenClosed(
                idlePos.offset(-1, -1, -1), idlePos.offset(1, 1, 1))) {
            BlockState state = level.getBlockState(neighbour);
            if (state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)) {
                return true;
            }
        }
        return false;
    }

    public static UUID episodeIdForProject(UUID mobId, long startedGameTime, String modeTag) {
        return UUID.nameUUIDFromBytes(
                (mobId + "|" + modeTag + "|" + startedGameTime).getBytes(StandardCharsets.UTF_8));
    }
}
