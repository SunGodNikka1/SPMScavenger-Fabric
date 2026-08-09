package com.noobk.spmscavenger.mining;

import com.noobk.spmscavenger.progression.TaskLifecycle;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * D-MIW-TS2 — when an {@link ExposureOpportunity} may be probed, held, or must be dropped.
 *
 * <p>Pure: no world access, no store access. The invariant it enforces is that an opportunity is
 * only ever consumable by the exact project session that produced it.
 */
public final class ExposureOpportunityPolicy {

    /**
     * How long an unprobed offer stands. The consumer's {@code canUse} is polled every tick, so this
     * only matters when something else legitimately owns the mob — the offer should not survive a
     * long combat and then send it back to a wall it has since walked away from.
     */
    public static final int OFFER_LIFETIME_TICKS = 100;

    /**
     * Idle tolerance inside a cooperative acquisition, refreshed on every take.
     *
     * <p>Must comfortably exceed one approach plus one capped break, or a vein-follow would be cut
     * off mid-vein and the tunnel would reacquire in the middle of the consumer's work. Related
     * bound, recorded rather than left implicit: {@code GatherResourcesGoal.MAX_APPROACH_TICKS}
     * (200) plus {@code MAX_BREAK_TICKS} (200).
     */
    public static final int VEIN_IDLE_TICKS = 600;

    private ExposureOpportunityPolicy() {
    }

    /**
     * A live opportunity for this project, or empty.
     *
     * <p>Every clause is load-bearing: the project must still exist and be running, be the mode that
     * produces exposures, be the <em>same session</em> (not merely the same mode at the same place
     * after a restart), and be inside the freshness window for its phase.
     */
    public static boolean isLive(
            @Nullable ExposureOpportunity opportunity,
            @Nullable MiningProject project,
            long now) {
        if (opportunity == null || project == null) {
            return false;
        }
        if (project.lifecycle() != TaskLifecycle.RUNNING) {
            return false;
        }
        if (opportunity.mode() != MiningProjectMode.TUNNEL_SEARCH) {
            return false;
        }
        if (!opportunity.belongsTo(project)) {
            return false;
        }
        return switch (opportunity.phase()) {
            case OFFERED -> now - opportunity.offeredAt() <= OFFER_LIFETIME_TICKS;
            case ACQUIRING -> now - opportunity.lastActivityAt() <= VEIN_IDLE_TICKS;
        };
    }

    /** The consumer may run its one exposure-local probe. */
    public static boolean offersProbe(
            @Nullable ExposureOpportunity opportunity,
            @Nullable MiningProject project,
            long now) {
        return isLive(opportunity, project, now)
                && opportunity.phase() == ExposureOpportunity.Phase.OFFERED;
    }

    /**
     * A cooperative acquisition is in progress, so the producer must not reacquire yet.
     *
     * <p>This is what stops the ping-pong: tunnel exposes ore, consumer takes one, tunnel resumes,
     * consumer wants the next vein member, tunnel yields again. Technically functional, visibly
     * absurd, and far harder to reason about than holding the session until the vein is done.
     */
    public static boolean holdsCooperativeSession(
            @Nullable ExposureOpportunity opportunity,
            @Nullable MiningProject project,
            long now) {
        return isLive(opportunity, project, now)
                && opportunity.phase() == ExposureOpportunity.Phase.ACQUIRING;
    }

    /**
     * Whether a candidate lies on the boundary this excavation opened.
     *
     * <p>Face-adjacency to an opened cell, which is exactly what "the cut revealed it" means. Not a
     * radius: a radius would re-admit the broad search the cooperative path exists to avoid, and
     * would start finding blocks the excavation had nothing to do with.
     */
    public static boolean isExposureLocal(
            @Nullable ExposureOpportunity opportunity, BlockPos candidate) {
        if (opportunity == null) {
            return false;
        }
        BlockPos cell = candidate.immutable();
        for (BlockPos opened : opportunity.openedCells()) {
            if (opened.equals(cell) || opened.distManhattan(cell) == 1) {
                return true;
            }
        }
        return false;
    }
}
