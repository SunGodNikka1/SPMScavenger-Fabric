package com.noobk.spmscavenger.mining;

import com.noobk.spmscavenger.SpmScavenger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.Optional;
import java.util.UUID;

/**
 * MI-14B — owns when a {@link MiningProject} starts and how it ends.
 *
 * <h2>What moved here, and why</h2>
 *
 * {@code ControlledDescentGoal} previously decided <em>whether</em> to mine, chose the heading,
 * created the project, executed it, judged the outcome, and completed the lifecycle. Six
 * responsibilities in an executor, and the only mode that could ever exist was the one that executor
 * implemented — a second mode had nowhere to be decided.
 *
 * <p>The split is now: <b>the director decides and owns lifecycle; executors execute.</b> An
 * executor that cannot find an assigned project does nothing. It can no longer invent work.
 *
 * <p>Deliberately <b>not</b> a {@code Goal}: it claims no flags and competes for nothing (D-MIW-001).
 * It is ticked by the existing flagless observer, so it adds no goal-arbitration surface. Choosing
 * <em>who may act</em> on a decision is MI-14C's job and is not attempted here.
 */
public final class MiningDirector {

    /** Sampled rim/surface pairs for heading choice, supplied by the caller. */
    @FunctionalInterface
    public interface TerrainSampler {
        int[] sampleAt(int x, int z);
    }

    private MiningDirector() {
    }

    /**
     * Whether a new project of this kind may begin.
     *
     * <p>An unresolved outcome is a claim on the next decision: every handoff reason blocks a fresh
     * controlled descent until it is consumed or expires (MI-14A-R1). Without that, the executor
     * that produced the outcome immediately out-competes the consumer that was supposed to act on
     * it — {@code ControlledDescentGoal} holds priority 3, {@code ExploringGoal} priority 8.
     */
    public static boolean mayStartControlledDescent(
            MiningProjectSavedData store, UUID mobId, NaturalDescentStatus status,
            boolean descentPressure) {
        if (store.projectOf(mobId).isPresent()) {
            return false;
        }
        if (!descentPressure) {
            return false;
        }
        if (store.pendingTransition(mobId)
                .filter(MiningTransition::blocksControlledDescentRestart)
                .isPresent()) {
            return false;
        }
        return NaturalDescentExhaustionPolicy.mayStartControlledDescent(status);
    }

    /**
     * Creates and assigns a controlled-descent project. The executor picks it up on its next
     * {@code canUse}; it never constructs one itself.
     */
    public static MiningProject startControlledDescent(
            ServerLevel level, Mob mob, MiningProjectSavedData store, Direction heading) {
        BlockPos origin = mob.blockPosition();
        MiningProject project =
                MiningProject.startControlledDescent(origin, heading, level.getGameTime());
        store.putProject(mob.getUUID(), project);
        SpmScavenger.LOGGER.info(
                "[spmscavenger] director assigned CONTROLLED_DESCENT entity={} heading={} origin={}",
                mob.getId(), heading, origin);
        return project;
    }

    /**
     * Completes an assigned project and preserves its outcome atomically.
     *
     * <p>The executor reports the <em>fact</em> it observed; the director owns what that means for
     * persistence and for whatever acts next. Routing completion through one place is what stops a
     * terminal reason being emitted into a record that the same call deletes.
     */
    public static void completeProject(
            ServerLevel level, Mob mob, MiningProject project, MiningProjectEnd end, BlockPos at) {
        MiningTransition transition =
                MiningTransition.of(project, end, at, level.getGameTime());
        MiningProjectSavedData.get(level)
                .completeProject(mob.getUUID(), end, transition);
        SpmScavenger.LOGGER.info(
                "[spmscavenger] director completed mode={} entity={} reason={} at={} heading={}",
                project.mode(), mob.getId(), end, transition.at(), transition.heading());
    }

    /** The project this mob is assigned, if any. Executors ask; they do not create. */
    public static Optional<MiningProject> assignedProject(
            MiningProjectSavedData store, UUID mobId, MiningProjectMode mode) {
        return store.projectOf(mobId).filter(project -> project.mode() == mode);
    }
}
