package com.noobk.spmscavenger.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.noobk.spmscavenger.goal.ExploringGoal;
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
        int authorityTicks) {

    public MiningExecutionCommitment {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(heading, "heading");
        Objects.requireNonNull(target, "target");
        at = at.immutable();
        target = target.immutable();
    }

    /**
     * MI-14C2-R2 — authority runs from the <b>claim</b>, not from the discovery.
     *
     * <p>The previous version stored {@code claimedAt = now} and then computed
     * {@code expiresAt = handoff.tick() + 400} — the same instant the admission window closes. A
     * handoff claimed at discovery+399 therefore received a single tick of authority, and the
     * 48-block continuation it exists to protect lost its protection immediately: intent fell to
     * {@code NONE}, {@code ExploringGoal} reverted to ordinary exploration, and priority-3 chores
     * outranked it again. Worse, expiry also unblocked {@code mayStartControlledDescent}, so the mob
     * could begin a fresh staircase beside the cave it had just broken into.
     *
     * <p>Admission and authority answer different questions and need different clocks:
     *
     * <pre>
     * admission  400 ticks from DISCOVERY  — is this find still fresh enough to act on?
     * authority  {@code authorityTicks} from CLAIM — how long may an accepted expedition stay alive?
     * </pre>
     *
     * <p>{@code authorityTicks} is supplied by the owner of the continuation's lifetime rather than
     * invented here, so authority cannot expire while the expedition it protects is still legally
     * running. It is a ceiling: the normal path clears the commitment when the expedition completes
     * or is abandoned.
     *
     * @param authorityTicks maximum lifetime of the accepted continuation, from {@code now}
     */
    public static MiningExecutionCommitment caveContinuation(
            MiningTransition handoff, long now, int authorityTicks) {
        return new MiningExecutionCommitment(
                ExecutionCommitmentKind.CAVE_CONTINUATION,
                handoff.at(),
                handoff.heading(),
                handoff.target(),
                now,
                Math.max(0, authorityTicks));
    }

    /**
     * MI-14C2-M2: asks the lifetime's owner, rather than re-implementing the boundary. A commitment
     * is alive for exactly as long as the expedition it protects would be.
     */
    public boolean isActive(long now) {
        return kind == ExecutionCommitmentKind.CAVE_CONTINUATION
                && !ExploringGoal.expeditionExpired(claimedAt, now, authorityTicks);
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
        tag.putInt("authorityTicks", authorityTicks);
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
                loadAuthorityTicks(tag));
    }

    /** Pre-M2 saves stored an absolute deadline; recover the window it represented. */
    private static int loadAuthorityTicks(CompoundTag tag) {
        if (tag.contains("authorityTicks")) {
            return tag.getInt("authorityTicks");
        }
        long legacyWindow = tag.getLong("expiresAt") - tag.getLong("claimedAt");
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, legacyWindow));
    }
}
