package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.activity.ActivityClass;
import net.minecraft.world.entity.Mob;

/**
 * SCR-2R5 shared O(1) admission seam for activities that could physically leave an arrived
 * nighttime shelter. It performs no scan, navigation, pathing, or state mutation.
 */
public final class ShelterActivityEnvelope {

    private ShelterActivityEnvelope() {
    }

    /** Scavenger work/exploration executors are voluntary and physically displacing. */
    public static boolean permitsVoluntaryDisplacement(Mob mob) {
        return !ShelterNightAuthority.holds(mob.getUUID());
    }

    /** Optional host adapters delegate here rather than owning policy in their Mixins. */
    public static boolean permitsCandidate(
            Mob mob, ActivityClass semanticClass, boolean displacing) {
        if (!ShelterNightAuthority.holds(mob.getUUID())) {
            return true;
        }
        return ShelterInterruptionPolicy.decideCandidate(semanticClass, displacing)
                != ShelterInterruptionPolicy.Decision.BLOCK_WHILE_SHELTERED;
    }

    /** Targeted host combat may move only when its provenance actually overrides shelter. */
    public static boolean permitsTargetedCombat(Mob mob) {
        return !ShelterNightAuthority.holds(mob.getUUID())
                || ShelterThreatPolicy.overridesShelter(mob);
    }
}
