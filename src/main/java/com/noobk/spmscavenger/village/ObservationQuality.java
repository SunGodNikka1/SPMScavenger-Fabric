package com.noobk.spmscavenger.village;

import net.minecraft.nbt.CompoundTag;

/**
 * V1-R1 — how good a look at a settlement the mob actually got.
 *
 * <h2>Why quantity was the wrong confidence score</h2>
 *
 * The first implementation replaced a stored anchor only when the new observation admitted
 * <em>more</em> POIs than the old one. That protected a good anchor from a glancing edge observation,
 * which was the real risk it was written for — but it made the anchor unable to follow a settlement
 * that genuinely changes:
 *
 * <ul>
 *   <li>a village loses buildings: 20 POIs become 16, and {@code 16 <= 20} means the anchor is
 *       <b>never</b> updated again;</li>
 *   <li>a village is rebuilt in place: 20 POIs become 20 differently positioned ones, and
 *       {@code 20 <= 20} is likewise permanently rejected.</li>
 * </ul>
 *
 * In both cases the stored anchor drifts out of agreement with {@code Raid.getCenter()} — the exact
 * failure D-VR-019 exists to prevent, arrived at from the opposite direction. "More POIs" is a proxy
 * for "better view" that stops being true the moment the settlement itself is the thing that changed.
 *
 * <h2>What the right signal was</h2>
 *
 * {@code VillagePerception} already computes it and the first version discarded it:
 * {@code withheldPoiCount}, the POIs inside the query radius whose chunks were not loaded. That is a
 * direct measure of <b>how much of the settlement the mob could see</b>, and it is independent of how
 * big the settlement is. A mob standing in the middle of a loaded village withholds nothing; a mob
 * clipping the edge withholds most of it.
 *
 * @param admitted POIs that passed the perception boundary
 * @param withheld POIs the query returned that the boundary refused (unloaded chunks)
 */
public record ObservationQuality(int admitted, int withheld) {

    public ObservationQuality {
        if (admitted < 0 || withheld < 0) {
            throw new IllegalArgumentException("counts must be non-negative: " + admitted + "/" + withheld);
        }
    }

    public int totalVisible() {
        return admitted + withheld;
    }

    /**
     * Fraction of the settlement within the query radius that the mob was allowed to perceive.
     *
     * @return {@code 0.0} when nothing was admitted, {@code 1.0} when nothing was withheld
     */
    public float completeness() {
        int total = totalVisible();
        return total == 0 ? 0f : (float) admitted / total;
    }

    /** A complete look: every POI the query found was inside the perception boundary. */
    public boolean isComplete() {
        return admitted > 0 && withheld == 0;
    }

    /**
     * Whether an observation of this quality, taken at {@code newTick}, should replace an anchor
     * stored from {@code stored} at {@code storedTick}.
     *
     * <pre>
     * better view                      -> replace   (strictly more of the settlement was seen)
     * equally good view and newer      -> replace   (the settlement itself may have changed)
     * worse view                       -> keep      (an edge glance must not degrade a good anchor)
     * </pre>
     *
     * The middle rule is the repair. Under quantity-comparison, "equally good and newer" was
     * indistinguishable from "no new information" and was rejected, which is what froze the anchor of
     * any village that shrank or moved.
     */
    public boolean supersedes(ObservationQuality stored, long newTick, long storedTick) {
        if (stored == null) {
            return true;
        }
        float mine = completeness();
        float theirs = stored.completeness();
        if (mine > theirs) {
            return true;
        }
        if (mine < theirs) {
            return false;
        }
        return newTick > storedTick;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("admitted", admitted);
        tag.putInt("withheld", withheld);
        return tag;
    }

    /**
     * @return a quality reconstructed from NBT. A row written before this field existed loads as
     *     {@code admitted = poiCount, withheld = 0} at the call site — deliberately optimistic, since
     *     the alternative is treating every pre-upgrade anchor as worthless and re-deriving it from
     *     the first partial glance after the update.
     */
    public static ObservationQuality load(CompoundTag tag) {
        if (tag == null) {
            return new ObservationQuality(0, 0);
        }
        return new ObservationQuality(tag.getInt("admitted"), tag.getInt("withheld"));
    }
}
