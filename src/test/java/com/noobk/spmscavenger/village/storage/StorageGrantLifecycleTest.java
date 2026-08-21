package com.noobk.spmscavenger.village.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

/** Task-54 R1-2 — lifecycle invalidation unit tests (no ServerLevel). */
class StorageGrantLifecycleTest {

    private static final UUID MOB = UUID.fromString("00000000-0000-0000-0000-000000000054");
    private static final ResourceKey<Level> OVERWORLD = ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION,
            ResourceLocation.withDefaultNamespace("overworld"));
    private static final BlockPos POS = new BlockPos(10, 64, 10);
    private static final GlobalPos KEY = GlobalPos.of(OVERWORLD, POS);
    private static final GlobalPos NEIGHBOR_KEY = GlobalPos.of(OVERWORLD, new BlockPos(30, 64, 30));

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void propertyMutationDoesNotInvalidateGrant() {
        StoragePermissionSavedData data = granted();
        BlockState closed = chest(ChestType.SINGLE, Direction.NORTH);
        BlockState waterlogged = closed.setValue(BlockStateProperties.WATERLOGGED, true);
        StorageGrantLifecycle.invalidateIfIdentityChanged(data, OVERWORLD, POS, closed, waterlogged);
        assertTrue(data.hasExplicitPermission(KEY, MOB));
    }

    @Test
    void singleToDoubleInvalidatesGrant() {
        StoragePermissionSavedData data = granted();
        BlockState single = chest(ChestType.SINGLE, Direction.NORTH);
        BlockState left = chest(ChestType.LEFT, Direction.NORTH);
        StorageGrantLifecycle.invalidateIfIdentityChanged(data, OVERWORLD, POS, single, left);
        assertFalse(data.hasExplicitPermission(KEY, MOB));
    }

    @Test
    void replacementInvalidatesGrant() {
        StoragePermissionSavedData data = granted();
        BlockState chest = chest(ChestType.SINGLE, Direction.NORTH);
        BlockState stone = Blocks.STONE.defaultBlockState();
        StorageGrantLifecycle.invalidateIfIdentityChanged(data, OVERWORLD, POS, chest, stone);
        assertFalse(data.hasExplicitPermission(KEY, MOB));
    }

    @Test
    void sameKindChestReplacementInvalidatesGrant() {
        StoragePermissionSavedData data = granted();
        BlockState normal = chest(ChestType.SINGLE, Direction.NORTH);
        BlockState trapped = Blocks.TRAPPED_CHEST.defaultBlockState()
                .setValue(ChestBlock.TYPE, ChestType.SINGLE)
                .setValue(ChestBlock.FACING, Direction.NORTH);
        StorageGrantLifecycle.invalidateIfIdentityChanged(data, OVERWORLD, POS, normal, trapped);
        assertFalse(data.hasExplicitPermission(KEY, MOB));
    }

    @Test
    void staleGrantClearedWhenChestPlacedOnStone() {
        StoragePermissionSavedData data = new StoragePermissionSavedData();
        data.grantOwner(KEY, MOB);
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState chest = chest(ChestType.SINGLE, Direction.NORTH);
        StorageGrantLifecycle.invalidateIfIdentityChanged(data, OVERWORLD, POS, stone, chest);
        assertFalse(data.hasExplicitPermission(KEY, MOB));
        assertEquals(0, data.grantCount());
    }

    @Test
    void neighborGrantSurvivesUnrelatedIdentityChange() {
        StoragePermissionSavedData data = new StoragePermissionSavedData();
        data.grantOwner(KEY, MOB);
        data.grantOwner(NEIGHBOR_KEY, MOB);
        BlockState chest = chest(ChestType.SINGLE, Direction.NORTH);
        BlockState stone = Blocks.STONE.defaultBlockState();
        StorageGrantLifecycle.invalidateIfIdentityChanged(data, OVERWORLD, POS, chest, stone);
        assertFalse(data.hasExplicitPermission(KEY, MOB));
        assertTrue(data.hasExplicitPermission(NEIGHBOR_KEY, MOB));
    }

    private static StoragePermissionSavedData granted() {
        StoragePermissionSavedData data = new StoragePermissionSavedData();
        data.grantOwner(KEY, MOB);
        return data;
    }

    private static BlockState chest(ChestType type, Direction facing) {
        return Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.TYPE, type)
                .setValue(ChestBlock.FACING, facing);
    }
}
