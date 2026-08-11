package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.experience.MobExperienceContext;
import com.noobk.spmscavenger.experience.OpinionExperienceRegistry;
import com.noobk.spmscavenger.opinion.OpinionFeatureGate;
import com.noobk.spmscavenger.opinion.PassiveExpressionProfile;
import com.noobk.spmscavenger.opinion.PassiveExpressionSocialPolicy;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.EnumSet;

/**
 * GAO-8A scheduler-owned passive gaze. It owns LOOK only and never calls navigation or world action.
 */
public final class PassiveExpressionGoal extends RandomLookAroundGoal {

    public static final int PRIORITY = 8;
    private static final double SOCIAL_RANGE = 8.0;
    private static final double GAZE_DISTANCE = 6.0;
    private static final EnumSet<Goal.Flag> REQUIRED_FLAGS = EnumSet.of(Goal.Flag.LOOK);

    private final Mob mob;
    private int holdTicks;
    private int nextEligibleTick;
    private Mob socialTarget;
    private double targetX;
    private double targetY;
    private double targetZ;

    public PassiveExpressionGoal(Mob mob) {
        super(mob);
        this.mob = mob;
        setFlags(REQUIRED_FLAGS);
        nextEligibleTick = mob.tickCount + Math.floorMod(mob.getId(), 40);
    }

    static EnumSet<Goal.Flag> requiredFlags() {
        return EnumSet.copyOf(REQUIRED_FLAGS);
    }

    @Override
    public boolean canUse() {
        PassiveExpressionProfile profile = profile();
        return expressionEnabled(ScavengerConfig.get().enabled, OpinionFeatureGate.isEnabled())
                && mob.getTarget() == null
                && profile.eligible()
                && mob.tickCount >= nextEligibleTick;
    }

    @Override
    public boolean canContinueToUse() {
        return expressionEnabled(ScavengerConfig.get().enabled, OpinionFeatureGate.isEnabled())
                && mob.getTarget() == null
                && profile().eligible()
                && holdTicks > 0;
    }

    @Override
    public void start() {
        PassiveExpressionProfile profile = profile();
        holdTicks = sample(profile.minHoldTicks(), profile.maxHoldTicks());
        socialTarget = chooseSocialTarget(profile);
        if (socialTarget == null) {
            chooseCosmeticPoint(profile);
        }
    }

    @Override
    public void tick() {
        if (holdTicks > 0) {
            holdTicks--;
        }
        if (socialTargetValid(
                socialTarget != null,
                socialTarget != null && socialTarget.isAlive(),
                socialTarget != null && mob.distanceToSqr(socialTarget) <= SOCIAL_RANGE * SOCIAL_RANGE,
                socialTarget != null && mob.getSensing().hasLineOfSight(socialTarget))) {
            mob.getLookControl().setLookAt(socialTarget, 30f, 30f);
            return;
        }
        if (socialTarget != null) {
            socialTarget = null;
            chooseCosmeticPoint(profile());
        }
        mob.getLookControl().setLookAt(targetX, targetY, targetZ, 30f, 30f);
    }

    @Override
    public void stop() {
        PassiveExpressionProfile profile = profile();
        nextEligibleTick = mob.tickCount + sample(
                profile.minCooldownTicks(), profile.maxCooldownTicks());
        holdTicks = 0;
        socialTarget = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private Mob chooseSocialTarget(PassiveExpressionProfile profile) {
        if (mob.getRandom().nextFloat() >= profile.socialLookChance()) {
            return null;
        }
        AABB area = mob.getBoundingBox().inflate(SOCIAL_RANGE);
        return mob.level().getEntitiesOfClass(Mob.class, area, other ->
                        other != mob
                                && other.isAlive()
                                && PlayerMobs.isPlayerMob(other)
                                && mob.getSensing().hasLineOfSight(other)
                                && PassiveExpressionSocialPolicy.isSelfLiked(
                                        PlayerMobs.feelingToward(mob, other),
                                        PlayerMobs.neutralFeeling()))
                .stream()
                .min(Comparator.comparingDouble(mob::distanceToSqr))
                .orElse(null);
    }

    private void chooseCosmeticPoint(PassiveExpressionProfile profile) {
        float yawOffset = randomSigned(profile.horizontalRangeDegrees());
        float pitchOffset = randomSigned(profile.verticalRangeDegrees());
        double yaw = Math.toRadians(mob.getYRot() + yawOffset);
        double pitch = Math.toRadians(pitchOffset);
        double horizontal = Math.cos(pitch) * GAZE_DISTANCE;
        targetX = mob.getX() - Math.sin(yaw) * horizontal;
        targetY = mob.getEyeY() + Math.sin(pitch) * GAZE_DISTANCE;
        targetZ = mob.getZ() + Math.cos(yaw) * horizontal;
    }

    private float randomSigned(float magnitude) {
        return (mob.getRandom().nextFloat() * 2f - 1f) * magnitude;
    }

    private int sample(int min, int max) {
        return min == max ? min : min + mob.getRandom().nextInt(max - min + 1);
    }

    static int clampSample(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static boolean expressionEnabled(boolean addonEnabled, boolean opinionEnabled) {
        return addonEnabled && opinionEnabled;
    }

    static boolean socialTargetValid(
            boolean present, boolean alive, boolean inRange, boolean visible) {
        return present && alive && inRange && visible;
    }

    private PassiveExpressionProfile profile() {
        MobExperienceContext context = OpinionExperienceRegistry.find(mob.getUUID());
        return context == null
                ? PassiveExpressionProfile.INACTIVE
                : context.passiveExpressionProfile();
    }
}
