package com.noobk.spmscavenger.goal;

/** Pure SCR door-passage terminal/admission policy; owns no Goal, path, timer, or door mutation. */
public final class DoorPassagePolicy {

    private DoorPassagePolicy() {
    }

    /** A door that is open needs traversal, not another deliberate OPEN animation. */
    public static boolean admitOpenEpisode(boolean alreadyOpen, boolean unchangedEncounter) {
        return !alreadyOpen && !unchangedEncounter;
    }

    /**
     * A crossed episode stays complete for the same door/path even when its close-behind changes
     * OPEN to false. An aborted pre-crossing episode may retry only when an external state change
     * made the door closed again; a new path is independently a new encounter.
     */
    public static boolean unchangedEncounter(
            boolean sameDoorAndPath, boolean previousCrossed, boolean doorStateChanged) {
        return sameDoorAndPath && (previousCrossed || !doorStateChanged);
    }

    /** Closing is eligible only after physical door-plane passage was observed. */
    public static boolean closeAfterEpisode(boolean crossedDoorPlane) {
        return crossedDoorPlane;
    }
}
