package com.noobk.spmscavenger.goal;

import net.minecraft.core.BlockPos;

import java.util.Objects;
import java.util.UUID;

/** Pure SCR door-passage terminal/admission policy; owns no Goal, path, timer, or door mutation. */
public final class DoorPassagePolicy {

    public static final double ENCOUNTER_RESET_DISTANCE_SQR = 6.25D;
    public static final int MAX_OPEN_ATTEMPTS_PER_ENCOUNTER = 2;
    public static final int MAX_ENCOUNTER_GENERATION = Integer.MAX_VALUE;

    public enum ApproachSide { WEST, EAST, NORTH, SOUTH }

    /** Mob-scoped physical doorway identity. Generation changes only after separation/new door. */
    public record EncounterKey(
            UUID mobId,
            BlockPos doorPos,
            ApproachSide approachSide,
            int generation) {
        public EncounterKey {
            Objects.requireNonNull(mobId, "mobId");
            doorPos = Objects.requireNonNull(doorPos, "doorPos").immutable();
            Objects.requireNonNull(approachSide, "approachSide");
        }
    }

    private DoorPassagePolicy() {
    }

    /** A door that is open needs traversal, not another deliberate OPEN animation. */
    public static boolean admitOpenEpisode(
            boolean alreadyOpen,
            boolean completedEncounter,
            boolean sameApproachSide,
            int attempts) {
        return !alreadyOpen
                && !completedEncounter
                && sameApproachSide
                && attempts < MAX_OPEN_ATTEMPTS_PER_ENCOUNTER;
    }

    public static ApproachSide approachSide(BlockPos doorPos, double mobX, double mobZ) {
        double dx = mobX - (doorPos.getX() + 0.5D);
        double dz = mobZ - (doorPos.getZ() + 0.5D);
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx < 0.0D ? ApproachSide.WEST : ApproachSide.EAST;
        }
        return dz < 0.0D ? ApproachSide.NORTH : ApproachSide.SOUTH;
    }

    public static EncounterKey key(
            UUID mobId, BlockPos doorPos, double mobX, double mobZ, int generation) {
        return new EncounterKey(
                mobId, doorPos, approachSide(doorPos, mobX, mobZ), generation);
    }

    /** Fixed-width correlation only; generation is not a retention key or lifetime counter. */
    public static int nextGeneration(int generation) {
        return generation >= MAX_ENCOUNTER_GENERATION ? 0 : generation + 1;
    }

    public static boolean sameDoor(EncounterKey encounter, UUID mobId, BlockPos doorPos) {
        return encounter != null
                && encounter.mobId().equals(mobId)
                && encounter.doorPos().equals(doorPos);
    }

    public static boolean separated(EncounterKey encounter, double mobX, double mobZ) {
        if (encounter == null) {
            return true;
        }
        double dx = mobX - (encounter.doorPos().getX() + 0.5D);
        double dz = mobZ - (encounter.doorPos().getZ() + 0.5D);
        return dx * dx + dz * dz > ENCOUNTER_RESET_DISTANCE_SQR;
    }

    /** Closing is eligible only after physical door-plane passage was observed. */
    public static boolean closeAfterEpisode(boolean crossedDoorPlane) {
        return crossedDoorPlane;
    }
}
