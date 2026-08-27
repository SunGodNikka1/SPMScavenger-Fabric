package com.noobk.spmscavenger.village;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

import java.util.Objects;

/**
 * V1 — a settlement this mob has actually been to.
 *
 * <p>Answers the factual questions every later village feature is built on: <i>what village is
 * this, have I seen it, and where is its canonical anchor</i>. Home belongs to the owning
 * {@link MobVillageMemory}; villagers, offers, containers, and affinity have separate owners.
 *
 * <p>The anchor is {@link VillageAnchorPolicy#anchorOf} output, so it is comparable with
 * {@code Raid.getCenter()} by construction rather than by coincidence (D-VR-019).
 */
public final class KnownVillage {

    private final BlockPos anchor;
    private final long firstSeenTick;
    private long lastSeenTick;

    /**
     * How good a look produced the stored anchor.
     *
     * <p>V1-R1: this replaced a bare {@code poiCount}. Quantity was a proxy for "better view" that
     * stopped being true the moment the settlement itself changed — see {@link ObservationQuality}.
     * The count survives inside the quality as {@code admitted}, so village size is still readable.
     */
    private ObservationQuality quality;

    public KnownVillage(BlockPos anchor, long firstSeenTick, long lastSeenTick,
            ObservationQuality quality) {
        this.anchor = Objects.requireNonNull(anchor, "anchor").immutable();
        this.firstSeenTick = firstSeenTick;
        this.lastSeenTick = lastSeenTick;
        this.quality = Objects.requireNonNull(quality, "quality");
    }

    public static KnownVillage discovered(BlockPos anchor, long tick, ObservationQuality quality) {
        return new KnownVillage(anchor, tick, tick, quality);
    }

    public BlockPos anchor() {
        return anchor;
    }

    public long firstSeenTick() {
        return firstSeenTick;
    }

    public long lastSeenTick() {
        return lastSeenTick;
    }

    public ObservationQuality quality() {
        return quality;
    }

    /** POIs admitted when the anchor was derived — the settlement's perceived size. */
    public int poiCount() {
        return quality.admitted();
    }

    void observedAt(long tick) {
        if (tick > lastSeenTick) {
            this.lastSeenTick = tick;
        }
    }

    /**
     * Re-observation, accepted when it is a better or equally good view.
     *
     * <p>Returns a replacement rather than mutating {@code anchor}, because the anchor is the
     * settlement's identity: a mutable anchor could drift far enough across successive partial
     * observations that the village silently becomes a different village, which is the failure
     * D-VR-019 exists to prevent — reintroduced from the inside.
     *
     * <p>V1-R4: the acceptance rule uses {@link PerceptionCoverage} (see
     * {@link ObservationQuality#supersedes}). Under the old quantity rule a village that lost
     * that lost buildings (20 POIs to 16) or was rebuilt in place (20 to a different 20) could never
     * update its anchor again, because both fail {@code newCount > oldCount}.
     *
     * @return {@code this} when the new observation is a worse view than the stored one
     */
    KnownVillage withObservation(BlockPos newAnchor, long tick, ObservationQuality newQuality) {
        long previousSeen = lastSeenTick;
        observedAt(tick);
        if (!newQuality.supersedes(quality, tick, previousSeen)) {
            return this;
        }
        return new KnownVillage(
                newAnchor, firstSeenTick, Math.max(tick, previousSeen), newQuality);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("anchor", NbtUtils.writeBlockPos(anchor));
        tag.putLong("firstSeen", firstSeenTick);
        tag.putLong("lastSeen", lastSeenTick);
        tag.put("quality", quality.save());
        return tag;
    }

    /**
     * @return {@code null} when the factual entry cannot be trusted. An unreadable anchor must not
     *     resurrect as a village at the world origin. Legacy role text is deliberately ignored:
     *     V4-R0 migrates HOME at the owning-memory layer and drops unevidenced economic/safety roles.
     */
    public static KnownVillage load(CompoundTag tag) {
        if (tag == null || !tag.contains("anchor")) {
            return null;
        }
        BlockPos anchor = NbtUtils.readBlockPos(tag, "anchor").orElse(null);
        if (anchor == null) {
            return null;
        }
        // Rows written before V1-R1 carry a bare poiCount. Load them as a complete observation of
        // that size rather than as worthless: treating every pre-upgrade anchor as unusable would let
        // the first partial glance after the update overwrite a good anchor with a bad one, which is
        // the defect the acceptance rule exists to prevent.
        ObservationQuality quality = tag.contains("quality")
                ? ObservationQuality.load(tag.getCompound("quality"))
                : ObservationQuality.fullCoverage(tag.getInt("poiCount"));
        return new KnownVillage(
                anchor, tag.getLong("firstSeen"), tag.getLong("lastSeen"), quality);
    }

    @Override
    public String toString() {
        return "KnownVillage[" + anchor.toShortString()
                + " poi=" + quality.admitted()
                + " cov=" + quality.loadedColumns() + "/" + quality.totalColumns() + "]";
    }
}
