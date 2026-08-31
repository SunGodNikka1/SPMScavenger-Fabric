package com.noobk.spmscavenger.validation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.noobk.spmscavenger.goal.ExploringGoal;
import com.noobk.spmscavenger.validation.V4RuntimeWitnessTracker;
import com.noobk.spmscavenger.village.interaction.CommuteDirective;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.entity.PathfinderMob;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Confirms that the existing ExploringGoal seeded its existing COMMUTE executor. */
@Mixin(ExploringGoal.class)
public abstract class V4ExploringGoalWitnessMixin {

    @Shadow @Final private PathfinderMob mob;

    @Inject(method = "seedRequiredTradeCommuteExpedition", at = @At("RETURN"))
    private void spmscavenger_validation$observeCommuteSeed(
            ServerLevel level, long now, CommuteDirective directive,
            CallbackInfoReturnable<Boolean> cir) {
        V4RuntimeWitnessTracker.observeCommuteSeed(
                mob.getUUID(), directive.binding().intent(),
                Boolean.TRUE.equals(cir.getReturnValue()), now);
    }

    // ExploringGoal overrides Goal.stop(). The production Fabric JAR exposes that inherited
    // Minecraft method under its intermediary name, while validation development uses the
    // readable name. Keep both selectors and retain the required-injector contract so failure to
    // attach remains fatal instead of silently weakening the runtime witness.
    @Inject(method = {"stop", "method_6270"}, at = @At("TAIL"))
    private void spmscavenger_validation$observeNavigationDiscard(CallbackInfo ci) {
        if (mob.level() instanceof ServerLevel level) {
            V4RuntimeWitnessTracker.observeNavigationStop(mob.getUUID(), level.getGameTime());
        }
    }

    /**
     * Observes the one production path request in planCurrentStage. Calling the supplied operation
     * exactly once preserves the original invocation, arguments, returned Path identity and all
     * production navigation behavior.
     */
    @WrapOperation(
            method = "planCurrentStage",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;"
                            + "createPath(Lnet/minecraft/core/BlockPos;I)"
                            + "Lnet/minecraft/world/level/pathfinder/Path;"))
    private Path spmscavenger_validation$observeRequiredTradePathRequest(
            PathNavigation navigation, BlockPos candidate, int reach,
            Operation<Path> original) {
        Path path = original.call(navigation, candidate, reach);
        if (mob.level() instanceof ServerLevel level) {
            BlockPos supportPos = candidate.below();
            BlockState feet = level.getBlockState(candidate);
            BlockState head = level.getBlockState(candidate.above());
            BlockState support = level.getBlockState(supportPos);
            V4RuntimeWitnessTracker.observePathPlanningCall(
                    mob.getUUID(),
                    new V4RuntimeWitnessTracker.PathPlanningEvidence(
                            level.getGameTime(),
                            position(mob.blockPosition()),
                            mob.onGround(),
                            mob.isInWater(),
                            mob.isPassenger(),
                            navigation.getClass().getName(),
                            mob.getAttributeValue(Attributes.FOLLOW_RANGE),
                            position(candidate),
                            Math.sqrt(mob.distanceToSqr(
                                    candidate.getX() + 0.5D,
                                    candidate.getY(),
                                    candidate.getZ() + 0.5D)),
                            feet.toString(),
                            head.toString(),
                            support.toString(),
                            support.isFaceSturdy(level, supportPos, Direction.UP),
                            path == null
                                    ? V4RuntimeWitnessTracker.PathResult.NULL
                                    : V4RuntimeWitnessTracker.PathResult.NON_NULL,
                            path == null ? null : path.canReach(),
                            path == null ? null : path.getNodeCount(),
                            path == null ? null : position(path.getTarget()),
                            path == null ? null : (double) path.getDistToTarget()));
        }
        return path;
    }

    private static String position(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
