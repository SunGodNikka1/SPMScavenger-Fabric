package com.noobk.spmscavenger.debug;

import com.noobk.spmscavenger.GatherCandidatePolicy;
import com.noobk.spmscavenger.GatherIntentPolicy;
import com.noobk.spmscavenger.GatherProtection;
import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.ScavengerCrafting;
import com.noobk.spmscavenger.ToolBox;
import com.noobk.spmscavenger.WorkDemandPolicy;
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

    record TargetEvidence(boolean eligible, boolean reachable, String detail) {
    }

    record Result(
            Verdict verdict,
            String material,
            String consumer,
            Optional<GatherIntentPolicy.Resource> precursor,
            ScavengerCrafting.Step readyCraftStep,
            boolean scanCovers,
            TargetEvidence targetEvidence,
            String reason) {
    }

    private V3MandatoryRouteReadiness() {
    }

    static Result evaluate(ServerLevel level, Mob subject, BlockPos fixtureOrigin) {
        Container backpack = PlayerMobs.backpack(subject);
        if (backpack == null) {
            return incomplete("PlayerMob backpack unavailable", new TargetEvidence(
                    false, false, "target not evaluated"));
        }
        TargetEvidence target = inspectFixtureTargets(level, subject, fixtureOrigin);
        return evaluatePolicy(
                backpack,
                subject.getMainHandItem(),
                subject.getOffhandItem(),
                ScavengerConfig.get(),
                subject.blockPosition().getY(),
                target);
    }

    static Result evaluatePolicy(
            Container backpack,
            ItemStack mainHand,
            ItemStack offHand,
            ScavengerConfig cfg,
            int mobBlockY,
            TargetEvidence targetEvidence) {
        Optional<WorkDemandPolicy.MaterialDemand> selected = WorkDemandPolicy
                .select(backpack, mainHand, offHand, cfg)
                .map(WorkDemandPolicy.WorkDemand::payload);
        if (selected.isEmpty()) {
            return incomplete("WorkDemandPolicy selected no modeled demand", targetEvidence);
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
            return result(Verdict.INCOMPLETE, material, consumer, precursor,
                    intent.readyCraftStep(), scanCovers, targetEvidence,
                    "selected demand is not the fixture iron-pick frontier");
        }
        if (precursor.isEmpty() || precursor.get() != GatherIntentPolicy.Resource.RAW_IRON) {
            return result(Verdict.INCOMPLETE, material, consumer, precursor,
                    intent.readyCraftStep(), scanCovers, targetEvidence,
                    "iron demand has no RAW_IRON precursor");
        }
        if (intent.readyCraftStep() != ScavengerCrafting.Step.NOTHING) {
            return result(Verdict.INCOMPLETE, material, consumer, precursor,
                    intent.readyCraftStep(), scanCovers, targetEvidence,
                    "crafting already owns next step " + intent.readyCraftStep());
        }
        if (!intent.shouldGather() || !scanCovers) {
            return result(Verdict.INCOMPLETE, material, consumer, precursor,
                    intent.readyCraftStep(), scanCovers, targetEvidence,
                    "Gather intent does not cover the selected precursor");
        }
        if (!targetEvidence.eligible() || !targetEvidence.reachable()) {
            return result(Verdict.INCOMPLETE, material, consumer, precursor,
                    intent.readyCraftStep(), scanCovers, targetEvidence,
                    "no eligible reachable fixture RAW_IRON target: " + targetEvidence.detail());
        }
        return result(Verdict.READY, material, consumer, precursor,
                intent.readyCraftStep(), true, targetEvidence,
                "production demand, precursor, Gather intent, and target are ready");
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

    private static Result incomplete(String reason, TargetEvidence targetEvidence) {
        return result(Verdict.INCOMPLETE, "UNAVAILABLE", "UNAVAILABLE", Optional.empty(),
                ScavengerCrafting.Step.NOTHING, false, targetEvidence, reason);
    }

    private static Result result(
            Verdict verdict,
            String material,
            String consumer,
            Optional<GatherIntentPolicy.Resource> precursor,
            ScavengerCrafting.Step step,
            boolean scanCovers,
            TargetEvidence target,
            String reason) {
        return new Result(verdict, material, consumer, precursor, step, scanCovers, target, reason);
    }

    static String describe(Result result) {
        return "verdict=" + result.verdict()
                + " material=" + result.material()
                + " consumer=" + result.consumer()
                + " precursor=" + result.precursor().map(Enum::name).orElse("UNAVAILABLE")
                + " nextStep=" + result.readyCraftStep()
                + " scanCovers=" + result.scanCovers()
                + " targetEligible=" + result.targetEvidence().eligible()
                + " targetReachable=" + result.targetEvidence().reachable()
                + " target=" + result.targetEvidence().detail()
                + " reason=" + result.reason();
    }
}
