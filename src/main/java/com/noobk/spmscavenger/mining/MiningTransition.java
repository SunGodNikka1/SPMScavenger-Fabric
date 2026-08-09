package com.noobk.spmscavenger.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import java.util.Objects;

/**
 * MI-14A — what a finished {@link MiningProject} hands to whatever acts next.
 *
 * <h2>Why this type has to exist</h2>
 *
 * A terminal reason on its own is not a handoff. {@code CAVE_FOUND} says a cave was opened; it does
 * not say <em>where</em>, or which way to continue, so nothing downstream can act on it.
 *
 * <p>Worse, the reason did not survive being emitted. {@code MiningProject.shouldPersist()} keeps
 * only {@code RUNNING}, {@code INTERRUPTED} and {@code RETRY}; every handoff reason maps to
 * {@code SUCCESS}, so {@code MiningProjectSavedData.completeProject} **removed the record**. The
 * outcome was deleted in the same call that produced it.
 *
 * <p>A transition is therefore recorded <b>before</b> the project is dropped, persists on its own,
 * and is consumed exactly once by the system that acts on it.
 *
 * @param at where the terminal condition was detected — the opening, the exhausted face, the hazard
 * @param heading the direction the project was working in, so continuation keeps its momentum
 * @param target optional preferred landing; {@link BlockPos#ZERO} when none was resolved
 */
public record MiningTransition(
        MiningProjectMode fromMode,
        MiningProjectEnd reason,
        BlockPos at,
        Direction heading,
        BlockPos target,
        long tick) {

    public MiningTransition {
        Objects.requireNonNull(fromMode, "fromMode");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(heading, "heading");
        Objects.requireNonNull(target, "target");
        at = at.immutable();
        target = target.immutable();
    }

    public static MiningTransition of(
            MiningProject project, MiningProjectEnd reason, BlockPos at, long tick) {
        return new MiningTransition(
                project.mode(), reason, at, project.heading(), BlockPos.ZERO, tick);
    }

    public MiningTransition withTarget(BlockPos preferredLanding) {
        return new MiningTransition(fromMode, reason, at, heading, preferredLanding, tick);
    }

    public boolean hasTarget() {
        return !BlockPos.ZERO.equals(target);
    }

    /**
     * Whether this outcome must be resolved before the same kind of project may start again.
     *
     * <p>Without this, the prolonged loop is: descent → budget exhausted → project removed →
     * descent pressure unchanged → immediately exhausted again → new descent, same area, forever.
     * A pending outcome is a claim on the next decision, not a note in a log.
     */
    public boolean blocksControlledDescentRestart() {
        return reason == MiningProjectEnd.SEARCH_BUDGET_EXHAUSTED
                || reason == MiningProjectEnd.HANDOFF_TUNNEL_SEARCH;
    }

    /** Actionable today: {@code ExploringGoal} can be rebased onto the opening. */
    public boolean isCaveContinuation() {
        return reason == MiningProjectEnd.CAVE_FOUND;
    }

    public boolean expired(long now, int lifetimeTicks) {
        return now - tick >= lifetimeTicks;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("FromMode", fromMode.name());
        tag.putString("Reason", reason.name());
        tag.putLong("At", at.asLong());
        tag.putString("Heading", heading.getName());
        tag.putLong("Target", target.asLong());
        tag.putLong("Tick", tick);
        return tag;
    }

    public static MiningTransition load(CompoundTag tag) {
        Direction heading = Direction.byName(tag.getString("Heading"));
        return new MiningTransition(
                MiningProjectMode.valueOf(tag.getString("FromMode")),
                MiningProjectEnd.valueOf(tag.getString("Reason")),
                BlockPos.of(tag.getLong("At")),
                heading == null ? Direction.NORTH : heading,
                BlockPos.of(tag.getLong("Target")),
                tag.getLong("Tick"));
    }
}
