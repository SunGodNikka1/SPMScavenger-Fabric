package com.noobk.spmscavenger.mining;

import com.noobk.spmscavenger.progression.TaskLifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MI-7A — immutable bounded mining session state. Physical dig remains in goal executors.
 */
public record MiningProject(
        MiningProjectMode mode,
        BlockPos origin,
        BlockPos lastSafeAnchor,
        int depthBelowOrigin,
        Direction heading,
        MiningBudget budget,
        MiningBudgetUsage budgetUsage,
        TaskLifecycle lifecycle,
        @Nullable MiningProjectEnd endReason,
        long startedGameTime,
        List<BlockPos> coarseReturnRoute) {

    public static final int MAX_RETURN_ROUTE = 32;

    public MiningProject {
        origin = origin.immutable();
        lastSafeAnchor = lastSafeAnchor.immutable();
        coarseReturnRoute = List.copyOf(coarseReturnRoute);
        if (budgetUsage == null) {
            budgetUsage = MiningBudgetUsage.EMPTY;
        }
    }

    public static MiningProject start(
            MiningProjectMode mode,
            BlockPos origin,
            Direction heading,
            MiningBudget budget,
            long startedGameTime) {
        return new MiningProject(
                mode,
                origin,
                origin,
                0,
                heading,
                budget,
                MiningBudgetUsage.EMPTY,
                TaskLifecycle.RUNNING,
                null,
                startedGameTime,
                List.of());
    }

    public static MiningProject startControlledDescent(
            BlockPos origin, Direction heading, long startedGameTime) {
        return start(
                MiningProjectMode.CONTROLLED_DESCENT,
                origin,
                heading,
                MiningBudget.controlledDescentDefaults(),
                startedGameTime);
    }

    public boolean isActive() {
        return lifecycle == TaskLifecycle.RUNNING;
    }

    public boolean isControlledDescent() {
        return mode == MiningProjectMode.CONTROLLED_DESCENT && isActive();
    }

    public boolean shouldPersist() {
        return lifecycle == TaskLifecycle.RUNNING
                || lifecycle == TaskLifecycle.INTERRUPTED
                || lifecycle == TaskLifecycle.RETRY;
    }

    public boolean isBudgetExhausted() {
        return budget.isSearchBudgetConsumed(budgetUsage)
                || budget.isBlocksExhausted(budgetUsage);
    }

    public MiningProject withLastSafeAnchor(BlockPos anchor) {
        return new MiningProject(
                mode,
                origin,
                anchor,
                depthBelowOrigin,
                heading,
                budget,
                budgetUsage,
                lifecycle,
                endReason,
                startedGameTime,
                coarseReturnRoute);
    }

    public MiningProject withDepthBelowOrigin(int depth) {
        return new MiningProject(
                mode,
                origin,
                lastSafeAnchor,
                Math.max(0, depth),
                heading,
                budget,
                budgetUsage,
                lifecycle,
                endReason,
                startedGameTime,
                coarseReturnRoute);
    }

    public MiningProject withBudgetUsage(MiningBudgetUsage usage) {
        return new MiningProject(
                mode,
                origin,
                lastSafeAnchor,
                depthBelowOrigin,
                heading,
                budget,
                usage,
                lifecycle,
                endReason,
                startedGameTime,
                coarseReturnRoute);
    }

    public MiningProject pushReturnStep(BlockPos step) {
        List<BlockPos> next = new ArrayList<>(coarseReturnRoute);
        next.add(step.immutable());
        if (next.size() > MAX_RETURN_ROUTE) {
            next.remove(0);
        }
        return new MiningProject(
                mode,
                origin,
                lastSafeAnchor,
                depthBelowOrigin,
                heading,
                budget,
                budgetUsage,
                lifecycle,
                endReason,
                startedGameTime,
                next);
    }

    public MiningProject complete(MiningProjectEnd end) {
        return new MiningProject(
                mode,
                origin,
                lastSafeAnchor,
                depthBelowOrigin,
                heading,
                budget,
                budgetUsage,
                end.lifecycle(),
                end,
                startedGameTime,
                coarseReturnRoute);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("mode", mode.name());
        tag.putInt("ox", origin.getX());
        tag.putInt("oy", origin.getY());
        tag.putInt("oz", origin.getZ());
        tag.putInt("ax", lastSafeAnchor.getX());
        tag.putInt("ay", lastSafeAnchor.getY());
        tag.putInt("az", lastSafeAnchor.getZ());
        tag.putInt("depth", depthBelowOrigin);
        tag.putString("heading", heading.getName());
        tag.put("budget", saveBudget(budget));
        tag.put("usage", saveUsage(budgetUsage));
        tag.putString("lifecycle", lifecycle.name());
        if (endReason != null) {
            tag.putString("end", endReason.name());
        }
        tag.putLong("started", startedGameTime);
        ListTag route = new ListTag();
        for (BlockPos pos : coarseReturnRoute) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("x", pos.getX());
            entry.putInt("y", pos.getY());
            entry.putInt("z", pos.getZ());
            route.add(entry);
        }
        tag.put("route", route);
        return tag;
    }

    public static MiningProject load(CompoundTag tag) {
        MiningProjectMode mode = MiningProjectMode.valueOf(tag.getString("mode"));
        BlockPos origin = new BlockPos(tag.getInt("ox"), tag.getInt("oy"), tag.getInt("oz"));
        BlockPos anchor = new BlockPos(tag.getInt("ax"), tag.getInt("ay"), tag.getInt("az"));
        int depth = tag.getInt("depth");
        Direction heading = Direction.byName(tag.getString("heading"));
        if (heading == null) {
            heading = Direction.NORTH;
        }
        MiningBudget budget = loadBudget(tag.getCompound("budget"));
        MiningBudgetUsage usage = tag.contains("usage")
                ? loadUsage(tag.getCompound("usage"))
                : MiningBudgetUsage.EMPTY;
        TaskLifecycle lifecycle = TaskLifecycle.valueOf(tag.getString("lifecycle"));
        MiningProjectEnd end = tag.contains("end")
                ? MiningProjectEnd.valueOf(tag.getString("end"))
                : null;
        long started = tag.getLong("started");
        List<BlockPos> route = new ArrayList<>();
        ListTag routeTag = tag.getList("route", Tag.TAG_COMPOUND);
        for (int i = 0; i < routeTag.size(); i++) {
            CompoundTag entry = routeTag.getCompound(i);
            route.add(new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z")));
        }
        return new MiningProject(
                mode, origin, anchor, depth, heading, budget, usage, lifecycle, end, started, route);
    }

    private static CompoundTag saveBudget(MiningBudget budget) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("blocks", budget.maxBlocksMined());
        tag.putInt("dist", budget.maxDistanceFromAnchor());
        tag.putInt("ticks", budget.maxTicks());
        tag.putInt("fails", budget.maxFailedSteps());
        tag.putInt("vert", budget.maxVerticalProgress());
        return tag;
    }

    private static MiningBudget loadBudget(CompoundTag tag) {
        return new MiningBudget(
                tag.getInt("blocks"),
                tag.getInt("dist"),
                tag.getInt("ticks"),
                tag.getInt("fails"),
                tag.getInt("vert"));
    }

    private static CompoundTag saveUsage(MiningBudgetUsage usage) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("blocks", usage.blocksMined());
        tag.putInt("dist", usage.maxHorizontalDistance());
        tag.putInt("ticks", usage.ticksElapsed());
        tag.putInt("fails", usage.failedSteps());
        tag.putInt("vert", usage.verticalDescent());
        return tag;
    }

    private static MiningBudgetUsage loadUsage(CompoundTag tag) {
        return new MiningBudgetUsage(
                tag.getInt("blocks"),
                tag.getInt("dist"),
                tag.getInt("ticks"),
                tag.getInt("fails"),
                tag.getInt("vert"));
    }

    public List<BlockPos> returnRouteView() {
        return Collections.unmodifiableList(coarseReturnRoute);
    }
}
