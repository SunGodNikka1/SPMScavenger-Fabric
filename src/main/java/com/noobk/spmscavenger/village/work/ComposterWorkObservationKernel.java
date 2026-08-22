package com.noobk.spmscavenger.village.work;

import com.noobk.spmscavenger.village.PerceptionCoverage;
import com.noobk.spmscavenger.village.SettlementBoundsPolicy;
import com.noobk.spmscavenger.village.VillagePerception;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.ComposterBlock;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Pure bounded composter-position enumeration for one settlement anchor (V3-F).
 */
public final class ComposterWorkObservationKernel {

    public record Result(List<BlockPos> composterPositions, WorkFactsCompleteness completeness) {}

    /**
     * @return {@code false} when {@link VillageWorkTuning#MAX_COMPOSTERS_PER_OBSERVATION} is exceeded
     */
    @FunctionalInterface
    public interface ComposterPoiCandidateSource {
        boolean enumerate(Consumer<PoiRecord> visitor);
    }

    @FunctionalInterface
    interface SettlementEvidenceBounds {
        boolean admits(ServerLevel level, BlockPos pos, BlockPos anchor);
    }

    private static final SettlementEvidenceBounds PRODUCTION_BOUNDS = (level, pos, anchor) ->
            VillagePerception.withinPerception(level, pos) && SettlementBoundsPolicy.within(pos, anchor);

    public static Result observe(ServerLevel level, BlockPos anchor) {
        if (level == null || anchor == null) {
            return incomplete();
        }
        return observe(level, anchor, composterPoiSource(level, anchor));
    }

    static Result observe(
            ServerLevel level,
            BlockPos anchor,
            ComposterPoiCandidateSource composterPois) {
        if (level == null || anchor == null || composterPois == null) {
            return incomplete();
        }
        PerceptionCoverage coverage =
                PerceptionCoverage.compute(level, anchor, VillageWorkTuning.OBSERVATION_RADIUS);
        if (!coverage.isFull()) {
            return incomplete();
        }
        return enumerateComposters(level, anchor, composterPois, PRODUCTION_BOUNDS);
    }

    static Result enumerateComposters(
            ServerLevel level,
            BlockPos anchor,
            ComposterPoiCandidateSource composterPois,
            SettlementEvidenceBounds bounds) {
        if (anchor == null || composterPois == null || bounds == null) {
            return incomplete();
        }
        List<BlockPos> positions = new ArrayList<>();
        boolean withinBudget = composterPois.enumerate(record -> {
            BlockPos pos = record.getPos();
            if (!bounds.admits(level, pos, anchor)) {
                return;
            }
            if (!(level.getBlockState(pos).getBlock() instanceof ComposterBlock)) {
                return;
            }
            positions.add(pos.immutable());
        });
        if (!withinBudget) {
            return incomplete();
        }
        return new Result(List.copyOf(positions), WorkFactsCompleteness.COMPLETE);
    }

    private ComposterWorkObservationKernel() {}

    static ComposterPoiCandidateSource composterPoiSource(ServerLevel level, BlockPos anchor) {
        return visitor -> {
            int examined = 0;
            Iterator<PoiRecord> iterator = level.getPoiManager()
                    .getInRange(
                            holder -> holder.is(PoiTypes.FARMER),
                            anchor,
                            VillageWorkTuning.OBSERVATION_RADIUS,
                            PoiManager.Occupancy.ANY)
                    .iterator();
            while (iterator.hasNext()) {
                examined++;
                if (examined > VillageWorkTuning.MAX_COMPOSTERS_PER_OBSERVATION) {
                    return false;
                }
                visitor.accept(iterator.next());
            }
            return true;
        };
    }

    private static Result incomplete() {
        return new Result(List.of(), WorkFactsCompleteness.INCOMPLETE);
    }
}
