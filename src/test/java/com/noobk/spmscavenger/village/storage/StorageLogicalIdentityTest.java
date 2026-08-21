package com.noobk.spmscavenger.village.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Task-54 R1-2 — pure logical identity predicate. */
class StorageLogicalIdentityTest {

    private static final BlockPos POS = new BlockPos(10, 64, 10);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void ordinaryChestPropertyMutationPreservesIdentity() {
        BlockState closed = chest(ChestType.SINGLE, Direction.NORTH);
        BlockState waterlogged = closed.setValue(BlockStateProperties.WATERLOGGED, true);
        assertFalse(StorageLogicalIdentity.logicalIdentityChanged(closed, waterlogged, POS));
    }

    @Test
    void barrelFacingChangePreservesIdentity() {
        BlockState north = Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.NORTH);
        BlockState east = north.setValue(BlockStateProperties.FACING, Direction.EAST);
        assertFalse(StorageLogicalIdentity.logicalIdentityChanged(north, east, POS));
    }

    @Test
    void singleToDoubleChangesIdentity() {
        BlockState single = chest(ChestType.SINGLE, Direction.NORTH);
        BlockState left = chest(ChestType.LEFT, Direction.NORTH);
        assertTrue(StorageLogicalIdentity.logicalIdentityChanged(single, left, POS));
    }

    @Test
    void doubleToSingleChangesIdentity() {
        BlockState left = chest(ChestType.LEFT, Direction.NORTH);
        BlockState single = chest(ChestType.SINGLE, Direction.NORTH);
        assertTrue(StorageLogicalIdentity.logicalIdentityChanged(left, single, POS));
    }

    @Test
    void doubleChestDirectionChangeChangesIdentity() {
        BlockState leftEast = chest(ChestType.LEFT, Direction.NORTH);
        BlockState leftWest = chest(ChestType.LEFT, Direction.WEST);
        BlockPos oldCanonical = StorageLogicalIdentity.canonicalPos(leftEast, POS);
        BlockPos newCanonical = StorageLogicalIdentity.canonicalPos(leftWest, POS);
        assertTrue(StorageLogicalIdentity.logicalIdentityChanged(leftEast, leftWest, POS));
        assertFalse(oldCanonical.equals(newCanonical));
    }

    @Test
    void supportedContainerReplacementChangesIdentity() {
        BlockState chest = chest(ChestType.SINGLE, Direction.NORTH);
        BlockState stone = Blocks.STONE.defaultBlockState();
        assertTrue(StorageLogicalIdentity.logicalIdentityChanged(chest, stone, POS));
    }

    @Test
    void chestToBarrelChangesIdentity() {
        BlockState chest = chest(ChestType.SINGLE, Direction.NORTH);
        BlockState barrel = Blocks.BARREL.defaultBlockState();
        assertTrue(StorageLogicalIdentity.logicalIdentityChanged(chest, barrel, POS));
    }

    @Test
    void normalChestToTrappedChestChangesIdentity() {
        BlockState normal = chest(ChestType.SINGLE, Direction.NORTH);
        BlockState trapped = Blocks.TRAPPED_CHEST.defaultBlockState()
                .setValue(ChestBlock.TYPE, ChestType.SINGLE)
                .setValue(ChestBlock.FACING, Direction.NORTH);
        assertTrue(StorageLogicalIdentity.logicalIdentityChanged(normal, trapped, POS));
    }

    @Test
    void shulkerColorChangeChangesIdentity() {
        BlockState white = Blocks.WHITE_SHULKER_BOX.defaultBlockState();
        BlockState red = Blocks.RED_SHULKER_BOX.defaultBlockState();
        assertTrue(StorageLogicalIdentity.logicalIdentityChanged(white, red, POS));
    }

    @Test
    void stoneToStoneDoesNotChangeIdentity() {
        BlockState a = Blocks.STONE.defaultBlockState();
        BlockState b = Blocks.COBBLESTONE.defaultBlockState();
        assertFalse(StorageLogicalIdentity.logicalIdentityChanged(a, b, POS));
    }

    private static BlockState chest(ChestType type, Direction facing) {
        return Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.TYPE, type)
                .setValue(ChestBlock.FACING, facing);
    }
}
