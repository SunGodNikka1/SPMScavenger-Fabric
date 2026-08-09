package com.noobk.spmscavenger.goal;

import java.util.Set;

/**
 * Turns "what did we notice in that chunk" into a number. No Minecraft types, so the arithmetic
 * that decides where mobs go is testable on its own.
 *
 * <h2>An interest signal, not a structure detector</h2>
 *
 * Block entities are cheap evidence of placed or built things. They are <b>not</b> a reliable
 * detector of anything: villages, mineshafts, caves, forests and mountains are overwhelmingly made
 * of ordinary blocks and register nothing here. So this is a <b>bonus only</b>. A chunk with no
 * block entities scores zero, never a penalty — unexplored wilderness is exactly what exploration
 * is for, and penalising it would teach the mob to avoid the whole point of its own goal.
 *
 * <p>The names are deliberately about the evidence rather than a conclusion. A bed is not proof of
 * a village and a chest is not proof of a dungeon; both merely say this chunk is not
 * undifferentiated terrain. Deciding <em>what</em> a place is belongs to a later, separate system.
 */
final class ExplorationInterest {

    /**
     * Kinds of evidence, ordered by how strongly they suggest the walk is worth making.
     *
     * <p>Scored by <b>presence</b>, never by count — see {@link #chunkScore}.
     */
    enum Signal {
        /** A spawner or trial spawner: the strongest single hint that something is over there. */
        SPAWNER(40),
        /** A vault. */
        VAULT(30),
        /** Chests, barrels, shulker boxes: possible structure or constructed area. */
        STORAGE(20),
        /** Beds: possible inhabited area. */
        REST(16),
        /** Brewing stands. */
        BREWING(16),
        /** Furnaces, blast furnaces, smokers: weak constructed-area evidence. */
        SMELTING(8),
        /** Hoppers, dispensers, droppers. */
        TRANSPORT(4),
        /** Anything else worth a nod — lecterns, campfires, beehives. */
        OTHER(2);

        final int weight;

        Signal(int weight) {
            this.weight = weight;
        }
    }

    /**
     * A chunk is worth at most the strongest thing that can be in it. Set to {@link Signal#SPAWNER}
     * deliberately, which is what makes early exit on a spawner exact rather than merely an
     * optimisation: once found, nothing else in that chunk can change the answer.
     */
    static final int CHUNK_CAP = Signal.SPAWNER.weight;

    /**
     * Ceiling on a whole route's interest.
     *
     * <p>Held <b>below the recent-destination penalty</b> ({@code -100} per repeated region) on
     * purpose. Interest must be strong enough to break ties and to outweigh a repeated heading
     * ({@code -35}) or a merely-visited region ({@code -20}), and never strong enough to send a mob
     * back to the same rewarding chunk expedition after expedition. Scoring presence instead of
     * quantity stops one warehouse looking like heaven; this stops the <em>same</em> warehouse
     * looking like heaven twice.
     */
    static final int ROUTE_CAP = 60;

    /** Block entities examined per chunk before giving up. Presence, not a census. */
    static final int SAMPLE_LIMIT = 32;

    private ExplorationInterest() {
    }

    /**
     * Presence per category, never quantity. A base with 180 chests, 40 hoppers and 20 furnaces
     * scores exactly what one chest, one hopper and one furnace score.
     */
    static int chunkScore(Set<Signal> signals) {
        int total = 0;
        for (Signal signal : signals) {
            total += signal.weight;
        }
        return Math.min(CHUNK_CAP, total);
    }

    /**
     * True when this signal already reaches {@link #CHUNK_CAP}, so the rest of the sample cannot
     * change the chunk's score and inspecting it would be wasted work.
     */
    static boolean saturates(Signal signal) {
        return signal.weight >= CHUNK_CAP;
    }

    /** Total interest across a route's chunks, clamped to {@link #ROUTE_CAP}. */
    static int routeScore(int accumulated) {
        return Math.min(ROUTE_CAP, Math.max(0, accumulated));
    }
}
