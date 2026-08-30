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
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
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

    static void createAndVerify(
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

        writeChecked(level, origin.offset(1, 0, 0), Blocks.BELL.defaultBlockState(), diagnostics);
        writeBed(level, origin.offset(-4, 0, 1), origin.offset(-4, 0, 2),
                Blocks.RED_BED.defaultBlockState(), diagnostics);
        writeBed(level, origin.offset(-7, 0, 1), origin.offset(-7, 0, 2),
                Blocks.BLUE_BED.defaultBlockState(), diagnostics);
        writeBed(level, origin.offset(-10, 0, 1), origin.offset(-10, 0, 2),
                Blocks.WHITE_BED.defaultBlockState(), diagnostics);
        writeChecked(level, origin.offset(-1, 0, 4),
                Blocks.SMITHING_TABLE.defaultBlockState(), diagnostics);
        diagnostics.geometryMutationSucceeded = true;

        verifyPostconditions(level, origin, diagnostics);
        diagnostics.geometryVerified = true;
        diagnostics.geometryFailureStage = "NONE";
        diagnostics.geometryFailureCoordinate = "NONE";
        diagnostics.expectedBlock = "NONE";
        diagnostics.actualBlock = "NONE";
    }

    static List<ChunkPos> requiredChunks(BlockPos origin) {
        Set<ChunkPos> chunks = new LinkedHashSet<>();
        addChunkRectangle(chunks, origin.offset(-24, 0, -24), origin.offset(24, 0, 24));
        addChunkRectangle(chunks, origin.offset(0, 0, -2), origin.offset(180, 0, 2));
        addChunkRectangle(chunks, origin.offset(158, 0, -22), origin.offset(202, 0, 22));
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
        level.setDayTime(dayBase + 18_000L);
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
        boolean geometryVerified;
        String geometryFailureStage = "NOT_RUN";
        String geometryFailureCoordinate = "UNAVAILABLE";
        String expectedBlock = "UNAVAILABLE";
        String actualBlock = "UNAVAILABLE";

        boolean ready() {
            return geometryChunksRequired > 0
                    && geometryChunksReady == geometryChunksRequired
                    && geometryMutationAttempted
                    && geometryMutationSucceeded
                    && geometryMutationRejected == 0
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
                    "geometryVerified=" + yesNo(geometryVerified)
                            + " postconditionsChecked=" + geometryPostconditionsChecked,
                    "geometryFailureStage=" + geometryFailureStage
                            + " geometryFailureCoordinate=" + geometryFailureCoordinate,
                    "expectedBlock=" + expectedBlock + " actualBlock=" + actualBlock);
        }

        private static String yesNo(boolean value) {
            return value ? "YES" : "NO";
        }
    }
}
