package com.noobk.spmscavenger.mining;

import com.noobk.spmscavenger.progression.TaskLifecycle;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiningProjectTest {

    private static HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    @Test
    void startControlledDescentUsesDefaults() {
        BlockPos origin = new BlockPos(10, 64, -3);
        MiningProject project = MiningProject.startControlledDescent(origin, Direction.EAST, 100L);

        assertEquals(MiningProjectMode.CONTROLLED_DESCENT, project.mode());
        assertEquals(origin, project.origin());
        assertEquals(origin, project.lastSafeAnchor());
        assertEquals(Direction.EAST, project.heading());
        assertEquals(TaskLifecycle.RUNNING, project.lifecycle());
        assertTrue(project.isActive());
        assertTrue(project.isControlledDescent());
        assertEquals(MiningBudget.controlledDescentDefaults(), project.budget());
    }

    @Test
    void completeMapsEndReasonToLifecycle() {
        MiningProject running = MiningProject.start(
                MiningProjectMode.TUNNEL_SEARCH,
                BlockPos.ZERO,
                Direction.SOUTH,
                MiningBudget.controlledDescentDefaults(),
                0L);

        MiningProject hazard = running.complete(MiningProjectEnd.HAZARD);
        assertEquals(TaskLifecycle.INTERRUPTED, hazard.lifecycle());
        assertEquals(MiningProjectEnd.HAZARD, hazard.endReason());
        assertFalse(hazard.isActive());
        assertTrue(hazard.shouldPersist());

        MiningProject satisfied = running.complete(MiningProjectEnd.DEMAND_SATISFIED);
        assertEquals(TaskLifecycle.SUCCESS, satisfied.lifecycle());
        assertFalse(satisfied.shouldPersist());
    }

    @Test
    void returnRouteCapsAtThirtyTwo() {
        MiningProject project = MiningProject.startControlledDescent(
                BlockPos.ZERO, Direction.NORTH, 0L);
        for (int i = 0; i < 40; i++) {
            project = project.pushReturnStep(new BlockPos(i, 0, 0));
        }
        assertEquals(MiningProject.MAX_RETURN_ROUTE, project.coarseReturnRoute().size());
        assertEquals(new BlockPos(8, 0, 0), project.coarseReturnRoute().get(0));
        assertEquals(new BlockPos(39, 0, 0), project.coarseReturnRoute().get(31));
    }

    @Test
    void savedDataRoundTripPreservesActiveProject() {
        UUID mob = UUID.randomUUID();
        MiningProjectSavedData data = MiningProjectSavedData.createEmpty();
        MiningProject project = MiningProject.startControlledDescent(
                new BlockPos(1, 50, 2), Direction.WEST, 42L)
                .withDepthBelowOrigin(6)
                .withLastSafeAnchor(new BlockPos(1, 56, 2))
                .pushReturnStep(new BlockPos(1, 55, 2));
        data.putProject(mob, project);

        HolderLookup.Provider lookup = registries;
        CompoundTag saved = data.save(new CompoundTag(), lookup);
        MiningProjectSavedData loaded = MiningProjectSavedData.load(saved, lookup);

        MiningProject roundTrip = loaded.projectOf(mob).orElseThrow();
        assertEquals(MiningProjectMode.CONTROLLED_DESCENT, roundTrip.mode());
        assertEquals(6, roundTrip.depthBelowOrigin());
        assertEquals(new BlockPos(1, 56, 2), roundTrip.lastSafeAnchor());
        assertEquals(1, roundTrip.returnRouteView().size());
        assertEquals(42L, roundTrip.startedGameTime());
    }

    @Test
    void completedProjectRemovedFromStore() {
        UUID mob = UUID.randomUUID();
        MiningProjectSavedData data = MiningProjectSavedData.createEmpty();
        data.putProject(mob, MiningProject.startControlledDescent(
                BlockPos.ZERO, Direction.NORTH, 0L));

        data.completeProject(mob, MiningProjectEnd.DEMAND_SATISFIED);
        assertTrue(data.projectOf(mob).isEmpty());
    }
}
