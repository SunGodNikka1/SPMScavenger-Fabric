package com.noobk.spmscavenger.village.crop;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Bounded scan → shortlist → path-probe selection for harvest targets (task-55 R1-1).
 */
public final class HarvestCropTargetSelector {

    public static final int SCAN_RADIUS = 8;
    public static final int MAX_CANDIDATES = 16;
    public static final int MAX_PATH_PROBES = 3;
    private static final double REACH_SQR = 4.0;

    public record HarvestTarget(
            BlockPos cropPos,
            BlockState matureState,
            BlockPos approachPos,
            Path path) {
    }

    public record SelectionResult(@Nullable HarvestTarget target, int pathProbesUsed) {
    }

    @FunctionalInterface
    public interface PathProbe {
        @Nullable
        Path probe(Mob mob, List<BlockPos> approachPositions);
    }

    private HarvestCropTargetSelector() {
    }

    public static SelectionResult select(
            Mob mob,
            ServerLevel level,
            Container backpack,
            ManagedCropDomainContext domain,
            HarvestTargetBackoff backoff,
            long gameTime) {
        return selectAt(
                mob.blockPosition(),
                mob,
                CropWorldView.from(level),
                backpack,
                domain,
                backoff,
                gameTime,
                (m, approaches) -> m.getNavigation().createPath(approaches.stream(), 0));
    }

    static SelectionResult selectAt(
            BlockPos origin,
            Mob pathMob,
            CropWorldView world,
            Container backpack,
            ManagedCropDomainContext domain,
            HarvestTargetBackoff backoff,
            long gameTime,
            PathProbe pathProbe) {
        backoff.prune(gameTime);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        List<Candidate> candidates = new ArrayList<>();

        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dy = -SCAN_RADIUS; dy <= SCAN_RADIUS; dy++) {
                for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!world.isLoaded(cursor)) {
                        continue;
                    }
                    BlockState state = world.getBlockState(cursor);
                    if (!CropReplantSemantics.supportedCrop(state)) {
                        continue;
                    }
                    if (!domain.isManagedCell(world, cursor, state)) {
                        continue;
                    }
                    if (!HarvestCandidatePolicy.isHarvestCandidate(true, state, backpack)) {
                        continue;
                    }
                    BlockPos immutable = cursor.immutable();
                    if (backoff.isActive(immutable, gameTime)) {
                        continue;
                    }
                    candidates.add(new Candidate(immutable, state, origin.distSqr(immutable)));
                }
            }
        }

        if (candidates.isEmpty()) {
            return new SelectionResult(null, 0);
        }

        candidates.sort(Comparator.comparingDouble(Candidate::distanceSq));
        int shortlistSize = Math.min(candidates.size(), MAX_CANDIDATES);
        int pathProbes = 0;

        for (int i = 0; i < shortlistSize && pathProbes < MAX_PATH_PROBES; i++) {
            Candidate candidate = candidates.get(i);
            if (origin.distSqr(candidate.pos()) <= REACH_SQR) {
                return new SelectionResult(
                        new HarvestTarget(
                                candidate.pos(),
                                candidate.state(),
                                origin.immutable(),
                                null),
                        pathProbes);
            }

            List<BlockPos> approaches = approachPositions(world, candidate.pos());
            if (approaches.isEmpty()) {
                backoff.recordFailure(candidate.pos(), gameTime);
                continue;
            }

            pathProbes++;
            Path path = pathProbe.probe(pathMob, approaches);
            if (path == null || path.getTarget() == null) {
                backoff.recordFailure(candidate.pos(), gameTime);
                continue;
            }
            if (!path.canReach()) {
                backoff.recordFailure(candidate.pos(), gameTime);
                continue;
            }
            return new SelectionResult(
                    new HarvestTarget(
                            candidate.pos(),
                            candidate.state(),
                            path.getTarget().immutable(),
                            path),
                    pathProbes);
        }

        return new SelectionResult(null, pathProbes);
    }

    static List<BlockPos> approachPositions(CropWorldView world, BlockPos crop) {
        List<BlockPos> result = new ArrayList<>();
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos feet = crop.offset(dx, dy, dz);
                    if (feet.distSqr(crop) > REACH_SQR) {
                        continue;
                    }
                    if (world.getBlockState(feet.below()).isAir()) {
                        continue;
                    }
                    if (!world.getBlockState(feet).isAir()) {
                        continue;
                    }
                    if (!world.getBlockState(feet.above()).isAir()) {
                        continue;
                    }
                    result.add(feet.immutable());
                }
            }
        }
        return result;
    }

    private record Candidate(BlockPos pos, BlockState state, double distanceSq) {
    }
}
