package com.noobk.spmscavenger.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import java.util.Objects;

/**
 * MI-14C2-R1 — execution authority that survives transition consumption until the executor finishes,
 * abandons, or the commitment expires.
 */
public record MiningExecutionCommitment(
        ExecutionCommitmentKind kind,
        BlockPos at,
        Direction heading,
        BlockPos target,
        long claimedAt,
        long expiresAt) {

    public MiningExecutionCommitment {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(heading, "heading");
        Objects.requireNonNull(target, "target");
        at = at.immutable();
        target = target.immutable();
    }

    public static MiningExecutionCommitment caveContinuation(MiningTransition handoff, long now) {
        long expiresAt = handoff.tick() + ExecutionIntentPolicy.CAVE_HANDOFF_LIFETIME_TICKS;
        return new MiningExecutionCommitment(
                ExecutionCommitmentKind.CAVE_CONTINUATION,
                handoff.at(),
                handoff.heading(),
                handoff.target(),
                now,
                expiresAt);
    }

    public boolean isActive(long now) {
        return kind == ExecutionCommitmentKind.CAVE_CONTINUATION && now < expiresAt;
    }

    public boolean blocksControlledDescentRestart(long now) {
        return isActive(now);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("kind", kind.name());
        tag.putLong("at", at.asLong());
        tag.putString("heading", heading.getName());
        tag.putLong("target", target.asLong());
        tag.putLong("claimedAt", claimedAt);
        tag.putLong("expiresAt", expiresAt);
        return tag;
    }

    public static MiningExecutionCommitment load(CompoundTag tag) {
        Direction heading = Direction.byName(tag.getString("heading"));
        return new MiningExecutionCommitment(
                ExecutionCommitmentKind.valueOf(tag.getString("kind")),
                BlockPos.of(tag.getLong("at")),
                heading == null ? Direction.NORTH : heading,
                BlockPos.of(tag.getLong("target")),
                tag.getLong("claimedAt"),
                tag.getLong("expiresAt"));
    }
}
