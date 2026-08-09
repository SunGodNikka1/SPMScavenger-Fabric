package com.noobk.spmscavenger;

/**
 * How good a candidate sleeping spot is. Pure, static, and unit-testable — no level, no entity.
 *
 * <h2>Why a score and not "nearest wins"</h2>
 *
 * v1.0 picked the closest spot that could not see the sky. That is true of a proper cave and equally
 * true of a one-block overhang, so a mob would take a ledge two blocks away over a sealed room eight
 * blocks away, every time — and a pitch-dark cave scored identically to a lit room despite being a
 * spawn point.
 *
 * <p>Scoring lets the goal say "much better shelter, slightly further", which is the judgement a
 * player actually makes. Distance still matters, but it is a penalty rather than the whole ranking.
 *
 * <h2>The weights, and why these</h2>
 *
 * <ul>
 *   <li><b>Bed +100.</b> Dominant on purpose. A bed is not merely shelter — the mob can lie down in
 *       it, which is the difference between "standing in a hole" and "went to bed". Nothing else in
 *       the table can outweigh a reachable bed.</li>
 *   <li><b>Enclosure +5 each, up to five sides.</b> Rewards a room over an overhang. Counting solid
 *       neighbours is a cheap stand-in for "how hard is it for something to walk in here".</li>
 *   <li><b>Light +15.</b> A lit spot is not a spawn point. Worth roughly three walls, and it pairs
 *       with the torch goal — a mob shelters somewhere dark, then lights it, and that spot scores
 *       better the next night.</li>
 *   <li><b>Distance −2 per block.</b> Deliberately mild. At this rate a bed stays the best option up
 *       to ~50 blocks away, while two similar holes are still decided by which is nearer.</li>
 * </ul>
 */
public final class ShelterScore {

    public static final int BED_BONUS = 100;
    public static final int ENCLOSURE_PER_SIDE = 5;
    public static final int MAX_ENCLOSURE_SIDES = 5;
    public static final int LIT_BONUS = 15;
    public static final double DISTANCE_PENALTY_PER_BLOCK = 1.0;

    private ShelterScore() {
    }

    /**
     * @param bed             whether this candidate is a bed the mob may actually sleep in
     * @param solidNeighbours solid blocks among the four sides and the ceiling, 0–5
     * @param blockLight      block light at the spot, 0–15
     * @param distance        blocks from the mob
     * @param litThreshold    light at or above which the spot counts as lit (the torch threshold)
     * @return higher is better; may be negative for a distant, exposed spot
     */
    public static double score(boolean bed, int solidNeighbours, int blockLight,
                               double distance, int litThreshold) {
        double score = 0;
        if (bed) {
            score += BED_BONUS;
        }
        score += Math.min(solidNeighbours, MAX_ENCLOSURE_SIDES) * (double) ENCLOSURE_PER_SIDE;
        if (blockLight >= litThreshold) {
            score += LIT_BONUS;
        }
        return score - distance * DISTANCE_PENALTY_PER_BLOCK;
    }

    /**
     * The floor a plain (bedless) spot must clear to be worth walking to at all, so "any roof" is not
     * automatically accepted.
     *
     * <p>Calibrated against real cases rather than picked: a bare overhang (one solid side, dark)
     * three blocks away scores 5−3 = 2 and is rejected; a four-sided cave six blocks away scores
     * 20−6 = 14 and is accepted; a lit enclosed room ten blocks away scores 20+15−10 = 25 and easily
     * wins. v1.1's floor of 10 with a −2 penalty rejected that middle case, which is why shelter
     * looked like it was barely working.
     */
    public static final double MIN_WORTHWHILE_SPOT = 5.0;
}
