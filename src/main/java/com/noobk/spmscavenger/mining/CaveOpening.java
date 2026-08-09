package com.noobk.spmscavenger.mining;

import com.noobk.spmscavenger.CaveContextPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * MI-14-R2 — evidence that excavation intersected a natural traversable space.
 *
 * <h2>Why a boolean was not enough</h2>
 *
 * {@code openedTraversableCave} answered "am I underground?" and returned {@code true}. A covered
 * staircase satisfies that at eight blocks of rim depth, so the mob declared a cave having opened
 * nothing but its own corridor — then handed downstream systems a {@code CAVE_FOUND} with
 * {@code target = unresolved} and the staircase's own heading.
 *
 * <p>An opening is a <b>place</b>, not a state. Carrying the landing and continuation is what lets
 * {@code MiningTransition} say where to go instead of merely that something happened.
 *
 * @param landing standable floor inside the discovered space, outside the mob's own corridor
 * @param continuation direction from the stair position toward that landing
 * @param kind {@link CaveContextPolicy.SpaceKind#CAVE} or {@code RAVINE} — never a structure
 */
public record CaveOpening(
        BlockPos landing, Direction continuation, CaveContextPolicy.SpaceKind kind) {

    public CaveOpening {
        landing = landing.immutable();
    }

    /** Only genuinely subterranean spaces are opportunities; an enclosure is someone's building. */
    public boolean isSubterranean() {
        return kind == CaveContextPolicy.SpaceKind.CAVE
                || kind == CaveContextPolicy.SpaceKind.RAVINE;
    }
}
