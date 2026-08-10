package com.noobk.spmscavenger.goal;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * PERF-1 — explicit furnace lookup semantics.
 *
 * <p>A {@code null} position is ambiguous: it can mean "scan not due" or "scan found nothing".
 * Placement fallback is only valid after {@link Outcome#ABSENT_RECENT}.
 */
final class FurnaceLookup {

    enum Outcome {
        FOUND,
        /** Scan phase has not arrived; world cube was not searched. */
        DEFERRED,
        /** A real scan recently found no usable furnace, or negative result is still cooling down. */
        ABSENT_RECENT
    }

    record Result(Outcome outcome, @Nullable BlockPos position) {
        Result {
            if (outcome == Outcome.FOUND) {
                Objects.requireNonNull(position, "position");
            } else {
                position = null;
            }
        }

        static Result found(BlockPos position) {
            return new Result(Outcome.FOUND, position.immutable());
        }

        static Result deferred() {
            return new Result(Outcome.DEFERRED, null);
        }

        static Result absentRecent() {
            return new Result(Outcome.ABSENT_RECENT, null);
        }

        boolean authorizesFurnacePlacement(boolean placeFurnacesEnabled, boolean hasPlaceableFurnace) {
            return outcome == Outcome.ABSENT_RECENT && placeFurnacesEnabled && hasPlaceableFurnace;
        }
    }

    record Resolution(Result result, @Nullable BlockPos cachedFurnace, long searchFailedUntilTick) {}

    private FurnaceLookup() {
    }

    static Resolution resolve(
            long now,
            long searchFailedUntilTick,
            @Nullable BlockPos cachedFurnace,
            boolean requireScanPhase,
            PhasedScanClock scanClock,
            Predicate<BlockPos> isUsableAt,
            Supplier<BlockPos> scanWorld,
            int failedSearchCooldownTicks) {
        if (now < searchFailedUntilTick) {
            return new Resolution(Result.absentRecent(), cachedFurnace, searchFailedUntilTick);
        }
        if (cachedFurnace != null && isUsableAt.test(cachedFurnace)) {
            return new Resolution(Result.found(cachedFurnace), cachedFurnace, searchFailedUntilTick);
        }
        if (requireScanPhase && !scanClock.claim(now)) {
            return new Resolution(Result.deferred(), null, searchFailedUntilTick);
        }
        BlockPos found = scanWorld.get();
        if (found != null) {
            return new Resolution(Result.found(found), found.immutable(), 0L);
        }
        scanClock.resetAfter(now);
        return new Resolution(
                Result.absentRecent(), null, now + failedSearchCooldownTicks);
    }
}
