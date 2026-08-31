package com.noobk.spmscavenger.validation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * Validation-only checked creation boundary for the deterministic V4-G world geometry.
 *
 * <p>The builder owns the geometry. It acquires every required chunk before the first block
 * mutation, verifies every direct mutation result, and verifies representative final
 * postconditions before fixture entity creation is permitted. It does not touch production AI,
 * intent, navigation, trade, sleep, or HOME state.
 */
final class V4FixtureGeometryBuilder {

    private static final int MUTATION_FLAGS =
            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private V4FixtureGeometryBuilder() {
    }

    static void createAndVerifyStructure(
            ServerLevel level,
            BlockPos origin,
            Set<ChunkPos> ownedForcedChunks,
            Diagnostics diagnostics) {
        diagnostics.commandModificationBlockLimit = level.getGameRules().getInt(
                GameRules.RULE_COMMAND_MODIFICATION_BLOCK_LIMIT);
        List<ChunkPos> requiredChunks = requiredChunks(origin);
        diagnostics.geometryChunksRequired = requiredChunks.size();

        acquireChunks(level, requiredChunks, ownedForcedChunks, diagnostics);
        configureScenarioEnvironment(level);

        diagnostics.geometryMutationAttempted = true;
        writeVolume(level, origin.offset(-24, -4, -24), origin.offset(24, 4, 24),
                Blocks.AIR.defaultBlockState(), diagnostics);
        writeVolume(level, origin.offset(-24, -1, -24), origin.offset(24, -1, 24),
                Blocks.STONE.defaultBlockState(), diagnostics);

        writeVolume(level, origin.offset(0, -1, -2), origin.offset(180, -1, 2),
                Blocks.STONE.defaultBlockState(), diagnostics);
        writeVolume(level, origin.offset(0, 0, -2), origin.offset(180, 3, 2),
                Blocks.AIR.defaultBlockState(), diagnostics);

        writeVolume(level, origin.offset(158, -4, -22), origin.offset(202, 4, 22),
                Blocks.AIR.defaultBlockState(), diagnostics);
        writeVolume(level, origin.offset(158, -1, -22), origin.offset(202, -1, 22),
                Blocks.STONE.defaultBlockState(), diagnostics);

        placeArenaBoundary(level, origin, diagnostics);

        writeChecked(level, origin.offset(1, 0, 0), Blocks.BELL.defaultBlockState(), diagnostics);
        writeBed(level, origin.offset(-4, 0, 1), origin.offset(-4, 0, 2),
                Blocks.RED_BED.defaultBlockState(), diagnostics);
        writeBed(level, origin.offset(-7, 0, 1), origin.offset(-7, 0, 2),
                Blocks.BLUE_BED.defaultBlockState(), diagnostics);
        writeBed(level, origin.offset(-10, 0, 1), origin.offset(-10, 0, 2),
                Blocks.WHITE_BED.defaultBlockState(), diagnostics);
        writeChecked(level, origin.offset(-1, 0, 4),
                Blocks.SMITHING_TABLE.defaultBlockState(), diagnostics);
        placeArenaLighting(level, origin, diagnostics);
        verifyPostconditions(level, origin, diagnostics);
        verifyArenaBoundary(level, origin, diagnostics);
        verifyLightBlocksPresent(level, origin, diagnostics);
        diagnostics.geometryMutationSucceeded = true;
        diagnostics.geometryStructureVerified = true;
        diagnostics.geometryFailureStage = "NONE";
        diagnostics.geometryFailureCoordinate = "NONE";
        diagnostics.expectedBlock = "NONE";
        diagnostics.actualBlock = "NONE";
    }

    static boolean verifyPropagatedLighting(
            ServerLevel level, BlockPos origin, long now, Diagnostics diagnostics) {
        diagnostics.fixtureLightSamplesChecked = 0;
        int minimum = 15;
        minimum = Math.min(minimum,
                sampleLitWalkableVolume(level, origin, -24, 24, -24, 24, diagnostics));
        minimum = Math.min(minimum,
                sampleLitWalkableVolume(level, origin, 0, 180, -2, 2, diagnostics));
        minimum = Math.min(minimum,
                sampleLitWalkableVolume(level, origin, 158, 202, -22, 22, diagnostics));
        diagnostics.minimumRepresentativeBlockLight = minimum;
        diagnostics.fixtureLightingVerified = minimum >= 7;
        diagnostics.lightingWaitTicks = diagnostics.lightingWaitStartedTick < 0L
                ? 0L : Math.max(0L, now - diagnostics.lightingWaitStartedTick);
        if (diagnostics.fixtureLightingVerified) {
            diagnostics.lightingReadyTick = now;
            diagnostics.geometryVerified = true;
            diagnostics.geometryFailureStage = "NONE";
            diagnostics.geometryFailureCoordinate = "NONE";
            diagnostics.expectedBlock = "NONE";
            diagnostics.actualBlock = "NONE";
        }
        return diagnostics.fixtureLightingVerified;
    }

    static void beginLightingWait(Diagnostics diagnostics, long startTick, long deadline) {
        diagnostics.lightingWaitStartedTick = startTick;
        diagnostics.lightingWaitDeadline = deadline;
        diagnostics.lightingReadyTick = -1L;
        diagnostics.lightingWaitTicks = 0L;
    }

    static void markLightingTimeout(Diagnostics diagnostics, BlockPos origin, long now) {
        diagnostics.lightingWaitTicks = Math.max(0L, now - diagnostics.lightingWaitStartedTick);
        diagnostics.geometryFailureStage = "lighting_propagation_timeout";
        diagnostics.geometryFailureCoordinate = origin.toShortString();
        diagnostics.expectedBlock = "minimum block light >= 7";
        diagnostics.actualBlock = Integer.toString(diagnostics.minimumRepresentativeBlockLight);
    }

    static List<ChunkPos> requiredChunks(BlockPos origin) {
        Set<ChunkPos> chunks = new LinkedHashSet<>();
        addChunkRectangle(chunks, origin.offset(-25, 0, -25), origin.offset(25, 0, 25));
        addChunkRectangle(chunks, origin.offset(-1, 0, -3), origin.offset(181, 0, 3));
        addChunkRectangle(chunks, origin.offset(157, 0, -23), origin.offset(203, 0, 23));
        return chunks.stream()
                .sorted(Comparator.comparingInt((ChunkPos chunk) -> chunk.x)
                        .thenComparingInt(chunk -> chunk.z))
                .toList();
    }

    static List<Postcondition> representativePostconditions(BlockPos origin) {
        List<Postcondition> checks = new ArrayList<>();

        addSpawnPostconditions(checks, "subject", origin.offset(2, 0, 0));
        addSpawnPostconditions(checks, "trader", origin.offset(-1, 0, 0));
        addSpawnPostconditions(checks, "helper", origin.offset(-7, 0, -2));

        checks.add(new Postcondition("village-floor-northwest",
                origin.offset(-24, -1, -24), ExpectedGeometry.STONE));
        checks.add(new Postcondition("village-floor-southeast",
                origin.offset(24, -1, 24), ExpectedGeometry.STONE));

        for (int x : new int[] {0, 90, 180}) {
            checks.add(new Postcondition("corridor-floor-" + x,
                    origin.offset(x, -1, 0), ExpectedGeometry.STONE));
            checks.add(new Postcondition("corridor-feet-clearance-" + x,
                    origin.offset(x, 0, 0), ExpectedGeometry.AIR));
            checks.add(new Postcondition("corridor-head-clearance-" + x,
                    origin.offset(x, 3, 0), ExpectedGeometry.AIR));
        }

        checks.add(new Postcondition("departure-floor",
                origin.offset(202, -1, 22), ExpectedGeometry.STONE));
        checks.add(new Postcondition("departure-feet-clearance",
                origin.offset(180, 0, 20), ExpectedGeometry.AIR));
        checks.add(new Postcondition("departure-head-clearance",
                origin.offset(180, 3, 20), ExpectedGeometry.AIR));

        checks.add(new Postcondition("bell",
                origin.offset(1, 0, 0), ExpectedGeometry.BELL));
        checks.add(new Postcondition("red-bed-head",
                origin.offset(-4, 0, 1), ExpectedGeometry.RED_BED_HEAD_SOUTH));
        checks.add(new Postcondition("red-bed-foot",
                origin.offset(-4, 0, 2), ExpectedGeometry.RED_BED_FOOT_SOUTH));
        checks.add(new Postcondition("blue-bed-head",
                origin.offset(-7, 0, 1), ExpectedGeometry.BLUE_BED_HEAD_SOUTH));
        checks.add(new Postcondition("blue-bed-foot",
                origin.offset(-7, 0, 2), ExpectedGeometry.BLUE_BED_FOOT_SOUTH));
        checks.add(new Postcondition("white-bed-head",
                origin.offset(-10, 0, 1), ExpectedGeometry.WHITE_BED_HEAD_SOUTH));
        checks.add(new Postcondition("white-bed-foot",
                origin.offset(-10, 0, 2), ExpectedGeometry.WHITE_BED_FOOT_SOUTH));
        checks.add(new Postcondition("workstation",
                origin.offset(-1, 0, 4), ExpectedGeometry.SMITHING_TABLE));
        return List.copyOf(checks);
    }

    private static void acquireChunks(
            ServerLevel level,
            List<ChunkPos> requiredChunks,
            Set<ChunkPos> ownedForcedChunks,
            Diagnostics diagnostics) {
        for (ChunkPos chunk : requiredChunks) {
            boolean alreadyForced = level.getForcedChunks().contains(chunk.toLong());
            if (alreadyForced) {
                diagnostics.geometryChunksAlreadyForced++;
            } else {
                if (!level.setChunkForced(chunk.x, chunk.z, true)) {
                    throw diagnostics.fail("chunk_force", chunk.getWorldPosition(),
                            "forced chunk", "setChunkForced returned false");
                }
                ownedForcedChunks.add(chunk);
                diagnostics.geometryChunksAcquired++;
            }
            level.getChunk(chunk.x, chunk.z);
            if (!level.getChunkSource().hasChunk(chunk.x, chunk.z)) {
                throw diagnostics.fail("chunk_ready", chunk.getWorldPosition(),
                        "loaded full chunk", "chunk source reports unavailable");
            }
            diagnostics.geometryChunksReady++;
        }
        if (diagnostics.geometryChunksReady != diagnostics.geometryChunksRequired) {
            throw diagnostics.fail("chunk_ready", originOf(requiredChunks),
                    Integer.toString(diagnostics.geometryChunksRequired),
                    Integer.toString(diagnostics.geometryChunksReady));
        }
    }

    private static BlockPos originOf(List<ChunkPos> chunks) {
        return chunks.isEmpty() ? BlockPos.ZERO : chunks.getFirst().getWorldPosition();
    }

    private static void configureScenarioEnvironment(ServerLevel level) {
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, level.getServer());
        level.setWeatherParameters(0, 0, false, false);
        long dayBase = Math.floorDiv(level.getDayTime(), 24_000L) * 24_000L;
        level.setDayTime(dayBase + 1_000L);
    }

    private static void writeVolume(
            ServerLevel level,
            BlockPos from,
            BlockPos to,
            BlockState expected,
            Diagnostics diagnostics) {
        for (BlockPos cursor : BlockPos.betweenClosed(from, to)) {
            writeChecked(level, cursor, expected, diagnostics);
        }
    }

    private static void writeBed(
            ServerLevel level,
            BlockPos head,
            BlockPos foot,
            BlockState bed,
            Diagnostics diagnostics) {
        BlockState headState = bed.setValue(BedBlock.PART, BedPart.HEAD)
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH);
        BlockState footState = bed.setValue(BedBlock.PART, BedPart.FOOT)
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH);
        writeChecked(level, head, headState, diagnostics);
        writeChecked(level, foot, footState, diagnostics);
    }

    private static void writeChecked(
            ServerLevel level,
            BlockPos pos,
            BlockState expected,
            Diagnostics diagnostics) {
        diagnostics.geometryMutationWrites++;
        boolean changed = level.setBlock(pos, expected, MUTATION_FLAGS);
        BlockState actual = level.getBlockState(pos);
        if (changed) {
            diagnostics.geometryMutationChanged++;
        } else if (actual.equals(expected)) {
            diagnostics.geometryMutationAlreadyMatched++;
        } else {
            diagnostics.geometryMutationRejected++;
        }
        if (!actual.equals(expected)) {
            throw diagnostics.fail("mutation_result", pos, expected.toString(), actual.toString());
        }
    }

    private static void verifyPostconditions(
            ServerLevel level, BlockPos origin, Diagnostics diagnostics) {
        for (Postcondition check : representativePostconditions(origin)) {
            BlockState actual = level.getBlockState(check.pos());
            diagnostics.geometryPostconditionsChecked++;
            if (!check.expected().matches(actual)) {
                throw diagnostics.fail("postcondition:" + check.label(), check.pos(),
                        check.expected().description(), actual.toString());
            }
        }
        verifySpawnGeometry(level, "subject", origin.offset(2, 0, 0), diagnostics);
        verifySpawnGeometry(level, "trader", origin.offset(-1, 0, 0), diagnostics);
        verifySpawnGeometry(level, "helper", origin.offset(-7, 0, -2), diagnostics);
    }

    private static void placeArenaLighting(
            ServerLevel level, BlockPos origin, Diagnostics diagnostics) {
        BlockState light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);
        placeLightGrid(level, origin, -24, 24, -24, 24, diagnostics, light);
        for (int x : coveredAxis(0, 180)) {
            writeChecked(level, origin.offset(x, 2, 0), light, diagnostics);
            diagnostics.fixtureLightBlocksPlaced++;
        }
        placeLightGrid(level, origin, 158, 202, -22, 22, diagnostics, light);
    }

    private static void placeArenaBoundary(
            ServerLevel level, BlockPos origin, Diagnostics diagnostics) {
        for (BlockPos relative : arenaBoundaryOffsets()) {
            writeChecked(level, origin.offset(relative), Blocks.BARRIER.defaultBlockState(), diagnostics);
            diagnostics.fixtureBarrierBlocksPlaced++;
        }
    }

    private static void verifyArenaBoundary(
            ServerLevel level, BlockPos origin, Diagnostics diagnostics) {
        for (BlockPos relative : arenaBoundaryOffsets()) {
            BlockPos pos = origin.offset(relative);
            BlockState actual = level.getBlockState(pos);
            if (!actual.is(Blocks.BARRIER)) {
                throw diagnostics.fail("arena_boundary", pos,
                        "minecraft:barrier", actual.toString());
            }
            diagnostics.fixtureBarrierBlocksVerified++;
        }
        diagnostics.arenaBoundaryVerified = diagnostics.fixtureBarrierBlocksPlaced > 0
                && diagnostics.fixtureBarrierBlocksVerified
                        == diagnostics.fixtureBarrierBlocksPlaced;
    }

    static List<BlockPos> arenaBoundaryOffsets() {
        List<BlockPos> boundary = new ArrayList<>();
        for (int x = -25; x <= 203; x++) {
            for (int z = -25; z <= 25; z++) {
                if (arenaInteriorColumn(x, z)) {
                    continue;
                }
                boolean horizontallyAdjacent = false;
                for (Direction direction : new Direction[] {
                        Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
                    horizontallyAdjacent |= arenaInteriorColumn(
                            x + direction.getStepX(), z + direction.getStepZ());
                }
                if (horizontallyAdjacent) {
                    // Side wall only. Never cap an interior X/Z column: ExploringGoal resolves
                    // landing Y from MOTION_BLOCKING_NO_LEAVES and must see the walkable surface,
                    // not an unreachable validation-owned roof.
                    for (int y = 0; y <= 4; y++) {
                        boundary.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return List.copyOf(boundary);
    }

    static boolean arenaInterior(int x, int y, int z) {
        boolean village = x >= -24 && x <= 24 && y >= 0 && y <= 4
                && z >= -24 && z <= 24;
        boolean corridor = x >= 0 && x <= 180 && y >= 0 && y <= 3
                && z >= -2 && z <= 2;
        boolean departure = x >= 158 && x <= 202 && y >= 0 && y <= 4
                && z >= -22 && z <= 22;
        return village || corridor || departure;
    }

    static boolean arenaInteriorColumn(int x, int z) {
        boolean village = x >= -24 && x <= 24 && z >= -24 && z <= 24;
        boolean corridor = x >= 0 && x <= 180 && z >= -2 && z <= 2;
        boolean departure = x >= 158 && x <= 202 && z >= -22 && z <= 22;
        return village || corridor || departure;
    }

    private static void placeLightGrid(
            ServerLevel level, BlockPos origin,
            int minX, int maxX, int minZ, int maxZ,
            Diagnostics diagnostics, BlockState light) {
        for (int x : coveredAxis(minX, maxX)) {
            for (int z : coveredAxis(minZ, maxZ)) {
                writeChecked(level, origin.offset(x, 2, z), light, diagnostics);
                diagnostics.fixtureLightBlocksPlaced++;
            }
        }
    }

    private static void verifyLightBlocksPresent(
            ServerLevel level, BlockPos origin, Diagnostics diagnostics) {
        verifyLightGrid(level, origin, -24, 24, -24, 24, diagnostics);
        for (int x : coveredAxis(0, 180)) {
            verifyLightBlock(level, origin.offset(x, 2, 0), diagnostics);
        }
        verifyLightGrid(level, origin, 158, 202, -22, 22, diagnostics);
    }

    private static void verifyLightGrid(
            ServerLevel level, BlockPos origin,
            int minX, int maxX, int minZ, int maxZ,
            Diagnostics diagnostics) {
        for (int x : coveredAxis(minX, maxX)) {
            for (int z : coveredAxis(minZ, maxZ)) {
                verifyLightBlock(level, origin.offset(x, 2, z), diagnostics);
            }
        }
    }

    private static void verifyLightBlock(
            ServerLevel level, BlockPos pos, Diagnostics diagnostics) {
        BlockState actual = level.getBlockState(pos);
        if (!actual.is(Blocks.LIGHT) || actual.getValue(LightBlock.LEVEL) != 15) {
            throw diagnostics.fail("light_block", pos,
                    "minecraft:light[level=15]", actual.toString());
        }
        diagnostics.fixtureLightBlocksVerified++;
    }

    /** Six-block spacing keeps every collision-free floor sample at block light seven or higher. */
    static List<Integer> coveredAxis(int min, int max) {
        List<Integer> values = new ArrayList<>();
        for (int value = min; value <= max; value += 6) {
            values.add(value);
        }
        if (values.isEmpty() || values.getLast() != max) {
            values.add(max);
        }
        return List.copyOf(values);
    }

    private static int sampleLitWalkableVolume(
            ServerLevel level, BlockPos origin,
            int minX, int maxX, int minZ, int maxZ,
            Diagnostics diagnostics) {
        int minimum = 15;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos feet = origin.offset(x, 0, z);
                if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                        || !level.getBlockState(feet.above())
                                .getCollisionShape(level, feet.above()).isEmpty()) {
                    continue;
                }
                int blockLight = level.getBrightness(LightLayer.BLOCK, feet);
                diagnostics.fixtureLightSamplesChecked++;
                minimum = Math.min(minimum, blockLight);
            }
        }
        return minimum;
    }

    private static void verifySpawnGeometry(
            ServerLevel level, String role, BlockPos pos, Diagnostics diagnostics) {
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        BlockState below = level.getBlockState(pos.below());
        if (!feet.getCollisionShape(level, pos).isEmpty()) {
            throw diagnostics.fail("postcondition:" + role + "_feet", pos,
                    "collision-free", feet.toString());
        }
        if (!head.getCollisionShape(level, pos.above()).isEmpty()) {
            throw diagnostics.fail("postcondition:" + role + "_head", pos.above(),
                    "collision-free", head.toString());
        }
        if (!below.isFaceSturdy(level, pos.below(), Direction.UP)) {
            throw diagnostics.fail("postcondition:" + role + "_support", pos.below(),
                    "sturdy UP support", below.toString());
        }
    }

    private static void addSpawnPostconditions(
            List<Postcondition> checks, String role, BlockPos pos) {
        checks.add(new Postcondition(role + "-feet", pos, ExpectedGeometry.AIR));
        checks.add(new Postcondition(role + "-head", pos.above(), ExpectedGeometry.AIR));
        checks.add(new Postcondition(role + "-support", pos.below(), ExpectedGeometry.STONE));
    }

    private static void addChunkRectangle(Set<ChunkPos> chunks, BlockPos from, BlockPos to) {
        int minX = Math.min(from.getX(), to.getX()) >> 4;
        int maxX = Math.max(from.getX(), to.getX()) >> 4;
        int minZ = Math.min(from.getZ(), to.getZ()) >> 4;
        int maxZ = Math.max(from.getZ(), to.getZ()) >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                chunks.add(new ChunkPos(x, z));
            }
        }
    }

    record Postcondition(String label, BlockPos pos, ExpectedGeometry expected) {
    }

    enum ExpectedGeometry {
        AIR("minecraft:air"),
        STONE("minecraft:stone"),
        BELL("minecraft:bell"),
        RED_BED_HEAD_SOUTH("minecraft:red_bed[part=head,facing=south]"),
        RED_BED_FOOT_SOUTH("minecraft:red_bed[part=foot,facing=south]"),
        BLUE_BED_HEAD_SOUTH("minecraft:blue_bed[part=head,facing=south]"),
        BLUE_BED_FOOT_SOUTH("minecraft:blue_bed[part=foot,facing=south]"),
        WHITE_BED_HEAD_SOUTH("minecraft:white_bed[part=head,facing=south]"),
        WHITE_BED_FOOT_SOUTH("minecraft:white_bed[part=foot,facing=south]"),
        SMITHING_TABLE("minecraft:smithing_table");

        private final String description;

        ExpectedGeometry(String description) {
            this.description = description;
        }

        String description() {
            return description;
        }

        boolean matches(BlockState state) {
            return switch (this) {
                case AIR -> state.is(Blocks.AIR);
                case STONE -> state.is(Blocks.STONE);
                case BELL -> state.is(Blocks.BELL);
                case RED_BED_HEAD_SOUTH -> bedMatches(state, Blocks.RED_BED, BedPart.HEAD);
                case RED_BED_FOOT_SOUTH -> bedMatches(state, Blocks.RED_BED, BedPart.FOOT);
                case BLUE_BED_HEAD_SOUTH -> bedMatches(state, Blocks.BLUE_BED, BedPart.HEAD);
                case BLUE_BED_FOOT_SOUTH -> bedMatches(state, Blocks.BLUE_BED, BedPart.FOOT);
                case WHITE_BED_HEAD_SOUTH -> bedMatches(state, Blocks.WHITE_BED, BedPart.HEAD);
                case WHITE_BED_FOOT_SOUTH -> bedMatches(state, Blocks.WHITE_BED, BedPart.FOOT);
                case SMITHING_TABLE -> state.is(Blocks.SMITHING_TABLE);
            };
        }

        private static boolean bedMatches(BlockState state, Block bed, BedPart part) {
            return state.is(bed)
                    && state.getValue(BedBlock.PART) == part
                    && state.getValue(HorizontalDirectionalBlock.FACING) == Direction.SOUTH;
        }
    }

    static final class Diagnostics {
        int commandModificationBlockLimit = -1;
        int geometryChunksRequired;
        int geometryChunksReady;
        int geometryChunksAlreadyForced;
        int geometryChunksAcquired;
        boolean geometryMutationAttempted;
        boolean geometryMutationSucceeded;
        int geometryMutationWrites;
        int geometryMutationChanged;
        int geometryMutationAlreadyMatched;
        int geometryMutationRejected;
        int geometryPostconditionsChecked;
        int fixtureLightBlocksPlaced;
        int fixtureLightBlocksVerified;
        int fixtureLightSamplesChecked;
        int fixtureBarrierBlocksPlaced;
        int fixtureBarrierBlocksVerified;
        boolean arenaBoundaryVerified;
        int minimumRepresentativeBlockLight = -1;
        boolean fixtureLightingVerified;
        boolean geometryStructureVerified;
        boolean geometryVerified;
        long lightingWaitStartedTick = -1L;
        long lightingWaitDeadline = -1L;
        long lightingReadyTick = -1L;
        long lightingWaitTicks = -1L;
        String geometryFailureStage = "NOT_RUN";
        String geometryFailureCoordinate = "UNAVAILABLE";
        String expectedBlock = "UNAVAILABLE";
        String actualBlock = "UNAVAILABLE";

        boolean readyForLightingWait() {
            return geometryChunksRequired > 0
                    && geometryChunksReady == geometryChunksRequired
                    && geometryMutationAttempted
                    && geometryMutationSucceeded
                    && geometryMutationRejected == 0
                    && fixtureLightBlocksPlaced > 0
                    && fixtureLightBlocksVerified == fixtureLightBlocksPlaced
                    && arenaBoundaryVerified
                    && geometryStructureVerified;
        }

        boolean ready() {
            return readyForLightingWait()
                    && fixtureLightingVerified
                    && geometryVerified;
        }

        IllegalStateException fail(
                String stage, BlockPos coordinate, String expected, String actual) {
            geometryFailureStage = stage;
            geometryFailureCoordinate = coordinate == null
                    ? "UNAVAILABLE" : coordinate.toShortString();
            expectedBlock = expected;
            actualBlock = actual;
            return new IllegalStateException("fixture geometry " + stage + " at "
                    + geometryFailureCoordinate + " expected=" + expected + " actual=" + actual);
        }

        List<String> lines() {
            return List.of(
                    "geometryOwner=VALIDATION_JAVA geometryFunctionInvoked=NO",
                    "commandModificationBlockLimit=" + commandModificationBlockLimit,
                    "geometryChunksRequired=" + geometryChunksRequired
                            + " geometryChunksReady=" + geometryChunksReady
                            + " alreadyForced=" + geometryChunksAlreadyForced
                            + " acquired=" + geometryChunksAcquired,
                    "geometryMutationAttempted=" + yesNo(geometryMutationAttempted)
                            + " geometryMutationSucceeded=" + yesNo(geometryMutationSucceeded)
                            + " writes=" + geometryMutationWrites
                            + " changed=" + geometryMutationChanged
                            + " alreadyMatched=" + geometryMutationAlreadyMatched
                            + " rejected=" + geometryMutationRejected,
                    "geometryStructureVerified=" + yesNo(geometryStructureVerified)
                            + " geometryVerified=" + yesNo(geometryVerified)
                            + " postconditionsChecked=" + geometryPostconditionsChecked,
                    "arenaBoundaryVerified=" + yesNo(arenaBoundaryVerified)
                            + " barrierBlocksPlaced=" + fixtureBarrierBlocksPlaced
                            + " barrierBlocksVerified=" + fixtureBarrierBlocksVerified,
                    "fixtureLightingVerified=" + yesNo(fixtureLightingVerified)
                            + " lightBlocksPlaced=" + fixtureLightBlocksPlaced
                            + " lightBlocksVerified=" + fixtureLightBlocksVerified
                            + " lightSamplesChecked=" + fixtureLightSamplesChecked
                            + " minimumRepresentativeBlockLight="
                            + minimumRepresentativeBlockLight,
                    "lightingWaitStartedTick=" + lightingWaitStartedTick
                            + " lightingWaitDeadline=" + lightingWaitDeadline
                            + " lightingReadyTick=" + lightingReadyTick
                            + " lightingWaitTicks=" + lightingWaitTicks,
                    "geometryFailureStage=" + geometryFailureStage
                            + " geometryFailureCoordinate=" + geometryFailureCoordinate,
                    "expectedBlock=" + expectedBlock + " actualBlock=" + actualBlock);
        }

        private static String yesNo(boolean value) {
            return value ? "YES" : "NO";
        }
    }
}
