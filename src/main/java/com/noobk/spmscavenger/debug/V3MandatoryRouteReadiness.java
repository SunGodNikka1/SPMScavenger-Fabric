package com.noobk.spmscavenger.debug;

import com.noobk.spmscavenger.GatherCandidatePolicy;
import com.noobk.spmscavenger.GatherIntentPolicy;
import com.noobk.spmscavenger.GatherProtection;
import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.ScavengerCrafting;
import com.noobk.spmscavenger.ToolBox;
import com.noobk.spmscavenger.WorkDemandPolicy;
import com.noobk.spmscavenger.activity.MandatoryOwnershipClaim;
import com.noobk.spmscavenger.activity.MandatoryOwnershipRegistry;
import com.noobk.spmscavenger.village.trade.GatherRoutePrecursor;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.pathfinder.Path;

/** Passive pre-window proof that the mandatory fixtures can reach production Gather admission. */
final class V3MandatoryRouteReadiness {

    private static final ResourceLocation EXPECTED_MATERIAL =
            BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT);
    private static final ResourceLocation EXPECTED_CONSUMER =
            ScavengerCrafting.IRON_PICKAXE_RECIPE.consumerKey();
    private static final double REACH_SQR = 6.0D;

    enum Verdict {
        READY,
        INCOMPLETE
    }

    enum Source {
        LIVE_CLAIM,
        PASSIVE_FALLBACK,
        NONE
    }

    record TargetEvidence(boolean eligible, boolean reachable, String detail) {
    }

    record ClaimEvidence(
            String consumerKey,
            int generation,
            long openedAt,
            long expiresAt,
            long currentTick,
            String routeIdentity) {

        static ClaimEvidence capture(MandatoryOwnershipClaim claim, long now) {
            return new ClaimEvidence(
                    claim.consumerKey().toString(),
                    claim.generation(),
                    claim.openedAt(),
                    claim.expiresAt(),
                    now,
                    String.valueOf(claim.routeIdentity()));
        }
    }

    record Result(
            Verdict verdict,
            Source source,
            String material,
            String consumer,
            Optional<GatherIntentPolicy.Resource> precursor,
            ScavengerCrafting.Step readyCraftStep,
            boolean scanCovers,
            TargetEvidence targetEvidence,
            Optional<ClaimEvidence> claimEvidence,
            String reason) {
    }

    private record PolicyEvidence(
            boolean exactFrontier,
            String material,
            String consumer,
            Optional<GatherIntentPolicy.Resource> precursor,
            ScavengerCrafting.Step readyCraftStep,
            boolean scanCovers,
            String reason) {
    }

    private V3MandatoryRouteReadiness() {
    }

    static Result evaluate(ServerLevel level, Mob subject, BlockPos fixtureOrigin) {
        Container backpack = PlayerMobs.backpack(subject);
        if (backpack == null) {
            return incomplete("PlayerMob backpack unavailable", new TargetEvidence(
                    false, false, "target not evaluated"), Optional.empty());
        }
        long now = level.getGameTime();
        PolicyEvidence policy = inspectPolicy(
                backpack,
                subject.getMainHandItem(),
                subject.getOffhandItem(),
                ScavengerConfig.get(),
                subject.blockPosition().getY());
        Optional<MandatoryOwnershipClaim> claim =
                MandatoryOwnershipRegistry.liveClaim(subject.getUUID(), now);
        if (!policy.exactFrontier()) {
            return finish(policy, new TargetEvidence(false, false, "target not evaluated"), claim, now);
        }
        if (matchingLiveClaim(claim, now)) {
            return finish(policy, new TargetEvidence(
                    false, false, "not evaluated: matching live production claim supersedes geometry"),
                    claim, now);
        }
        TargetEvidence target = inspectFixtureTargets(level, subject, fixtureOrigin);
        return finish(policy, target, claim, now);
    }

    static Result evaluatePolicy(
            Container backpack,
            ItemStack mainHand,
            ItemStack offHand,
            ScavengerConfig cfg,
            int mobBlockY,
            TargetEvidence targetEvidence) {
        return evaluatePolicy(
                backpack, mainHand, offHand, cfg, mobBlockY, targetEvidence, Optional.empty(), 0L);
    }

    static Result evaluatePolicy(
            Container backpack,
            ItemStack mainHand,
            ItemStack offHand,
            ScavengerConfig cfg,
            int mobBlockY,
            TargetEvidence targetEvidence,
            Optional<MandatoryOwnershipClaim> claim,
            long now) {
        return finish(inspectPolicy(backpack, mainHand, offHand, cfg, mobBlockY),
                targetEvidence, claim, now);
    }

    private static PolicyEvidence inspectPolicy(
            Container backpack,
            ItemStack mainHand,
            ItemStack offHand,
            ScavengerConfig cfg,
            int mobBlockY) {
        Optional<WorkDemandPolicy.MaterialDemand> selected = WorkDemandPolicy
                .select(backpack, mainHand, offHand, cfg)
                .map(WorkDemandPolicy.WorkDemand::payload);
        if (selected.isEmpty()) {
            return policyIncomplete("WorkDemandPolicy selected no modeled demand");
        }

        WorkDemandPolicy.MaterialDemand demand = selected.get();
        GatherIntentPolicy.GatherIntent intent = GatherIntentPolicy.evaluate(
                backpack, mainHand, offHand, cfg, mobBlockY);
        Optional<GatherIntentPolicy.Resource> precursor = GatherRoutePrecursor.of(demand);
        boolean scanCovers = GatherRoutePrecursor.scanCovers(demand, intent);
        String material = demand.materialKey().toString();
        String consumer = demand.consumerKey().toString();

        if (!demand.materialKey().equals(EXPECTED_MATERIAL)
                || !demand.consumerKey().equals(EXPECTED_CONSUMER)) {
            return policy(false, material, consumer, precursor,
                    intent.readyCraftStep(), scanCovers,
                    "selected demand is not the fixture iron-pick frontier");
        }
        if (precursor.isEmpty() || precursor.get() != GatherIntentPolicy.Resource.RAW_IRON) {
            return policy(false, material, consumer, precursor,
                    intent.readyCraftStep(), scanCovers,
                    "iron demand has no RAW_IRON precursor");
        }
        if (intent.readyCraftStep() != ScavengerCrafting.Step.NOTHING) {
            return policy(false, material, consumer, precursor,
                    intent.readyCraftStep(), scanCovers,
                    "crafting already owns next step " + intent.readyCraftStep());
        }
        if (!intent.shouldGather() || !scanCovers) {
            return policy(false, material, consumer, precursor,
                    intent.readyCraftStep(), scanCovers,
                    "Gather intent does not cover the selected precursor");
        }
        return policy(true, material, consumer, precursor,
                intent.readyCraftStep(), true,
                "production demand, precursor, and Gather intent are ready");
    }

    private static Result finish(
            PolicyEvidence policy,
            TargetEvidence targetEvidence,
            Optional<MandatoryOwnershipClaim> claim,
            long now) {
        Optional<ClaimEvidence> claimEvidence = claim.map(value -> ClaimEvidence.capture(value, now));
        if (!policy.exactFrontier()) {
            return result(Verdict.INCOMPLETE, Source.NONE, policy, targetEvidence, claimEvidence,
                    policy.reason());
        }
        if (matchingLiveClaim(claim, now)) {
            return result(Verdict.READY, Source.LIVE_CLAIM, policy, targetEvidence, claimEvidence,
                    "matching live production claim owns the exact fixture frontier");
        }
        if (!targetEvidence.eligible() || !targetEvidence.reachable()) {
            return result(Verdict.INCOMPLETE, Source.NONE, policy, targetEvidence, claimEvidence,
                    "no eligible reachable fixture RAW_IRON target: " + targetEvidence.detail());
        }
        return result(Verdict.READY, Source.PASSIVE_FALLBACK, policy, targetEvidence, claimEvidence,
                "production demand, precursor, Gather intent, and target are ready");
    }

    private static boolean matchingLiveClaim(
            Optional<MandatoryOwnershipClaim> claim,
            long now) {
        return claim.filter(value -> !value.expired(now))
                .filter(value -> EXPECTED_CONSUMER.equals(value.consumerKey()))
                .isPresent();
    }

    private static TargetEvidence inspectFixtureTargets(
            ServerLevel level, Mob subject, BlockPos origin) {
        ScavengerConfig cfg = ScavengerConfig.get();
        int radius = (int) cfg.gatherSearchRadius;
        boolean eligibleSeen = false;
        String last = "fixture iron positions absent";
        for (int x = 10; x <= 12; x++) {
            BlockPos target = origin.offset(x, 0, 0);
            var state = level.getBlockState(target);
            if (GatherCandidatePolicy.familyOf(state)
                    .filter(resource -> resource == GatherIntentPolicy.Resource.RAW_IRON)
                    .isEmpty()) {
                last = target.toShortString() + " is " + state;
                continue;
            }
            int dx = Math.abs(target.getX() - subject.blockPosition().getX());
            int dz = Math.abs(target.getZ() - subject.blockPosition().getZ());
            int dy = Math.abs(target.getY() - subject.blockPosition().getY());
            if (dx > radius || dz > radius || dy > 4) {
                last = target.toShortString() + " outside production scan bounds";
                continue;
            }
            GatherIntentPolicy.GatherIntent intent = GatherIntentPolicy.evaluate(
                    PlayerMobs.backpack(subject),
                    subject.getMainHandItem(),
                    subject.getOffhandItem(),
                    cfg,
                    subject.blockPosition().getY());
            float acquisitionCost = (float) (Math.sqrt(target.distSqr(subject.blockPosition())) / 8.0D);
            boolean eligible = GatherCandidatePolicy.isPassOneCandidate(
                            level, target, state, intent,
                            toolState -> ToolBox.ownsToolFor(subject, toolState), acquisitionCost)
                    && GatherProtection.isGatherableOre(level, target, cfg);
            if (!eligible) {
                last = target.toShortString() + " rejected by production candidate/protection rules";
                continue;
            }
            eligibleSeen = true;
            if (subject.blockPosition().distSqr(target) <= REACH_SQR) {
                return new TargetEvidence(true, true,
                        target.toShortString() + " already within production reach");
            }
            Set<BlockPos> approaches = standingPositions(level, target);
            Path path = approaches.isEmpty()
                    ? null
                    : subject.getNavigation().createPath(approaches, 0);
            if (path != null && path.getTarget() != null && path.canReach()) {
                return new TargetEvidence(true, true,
                        target.toShortString() + " reachable via " + path.getTarget().toShortString());
            }
            last = target.toShortString() + " eligible but no complete approach path";
        }
        return new TargetEvidence(eligibleSeen, false, last);
    }

    private static Set<BlockPos> standingPositions(ServerLevel level, BlockPos block) {
        Set<BlockPos> result = new LinkedHashSet<>();
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos feet = block.offset(dx, dy, dz);
                    if (feet.equals(block) || feet.distSqr(block) > REACH_SQR) {
                        continue;
                    }
                    boolean feetClear = level.getBlockState(feet)
                            .getCollisionShape(level, feet).isEmpty();
                    boolean headClear = level.getBlockState(feet.above())
                            .getCollisionShape(level, feet.above()).isEmpty();
                    boolean floor = level.getBlockState(feet.below())
                            .isFaceSturdy(level, feet.below(), Direction.UP);
                    if (feetClear && headClear && floor) {
                        result.add(feet.immutable());
                    }
                }
            }
        }
        return result;
    }

    private static PolicyEvidence policyIncomplete(String reason) {
        return policy(false, "UNAVAILABLE", "UNAVAILABLE", Optional.empty(),
                ScavengerCrafting.Step.NOTHING, false, reason);
    }

    private static PolicyEvidence policy(
            boolean exactFrontier,
            String material,
            String consumer,
            Optional<GatherIntentPolicy.Resource> precursor,
            ScavengerCrafting.Step step,
            boolean scanCovers,
            String reason) {
        return new PolicyEvidence(
                exactFrontier, material, consumer, precursor, step, scanCovers, reason);
    }

    private static Result incomplete(
            String reason,
            TargetEvidence targetEvidence,
            Optional<ClaimEvidence> claimEvidence) {
        return result(Verdict.INCOMPLETE, Source.NONE, policyIncomplete(reason),
                targetEvidence, claimEvidence, reason);
    }

    private static Result result(
            Verdict verdict,
            Source source,
            PolicyEvidence policy,
            TargetEvidence target,
            Optional<ClaimEvidence> claimEvidence,
            String reason) {
        return new Result(verdict, source, policy.material(), policy.consumer(), policy.precursor(),
                policy.readyCraftStep(), policy.scanCovers(), target, claimEvidence, reason);
    }

    static String describe(Result result) {
        return "verdict=" + result.verdict()
                + " source=" + result.source()
                + " material=" + result.material()
                + " consumer=" + result.consumer()
                + " precursor=" + result.precursor().map(Enum::name).orElse("UNAVAILABLE")
                + " nextStep=" + result.readyCraftStep()
                + " scanCovers=" + result.scanCovers()
                + " targetEligible=" + result.targetEvidence().eligible()
                + " targetReachable=" + result.targetEvidence().reachable()
                + " target=" + result.targetEvidence().detail()
                + result.claimEvidence().map(value ->
                        " claimConsumerKey=" + value.consumerKey()
                                + " claimGeneration=" + value.generation()
                                + " claimOpenedAt=" + value.openedAt()
                                + " claimExpiresAt=" + value.expiresAt()
                                + " currentTick=" + value.currentTick()
                                + " claimRoute=" + value.routeIdentity())
                        .orElse(" claim=NONE currentTick=UNAVAILABLE")
                + " reason=" + result.reason();
    }
}
