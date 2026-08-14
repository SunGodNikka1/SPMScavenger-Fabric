package com.noobk.spmscavenger.village;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

import java.util.Objects;

/**
 * V1 — a settlement this mob has actually been to.
 *
 * <p>Answers the four questions every later village feature is built on: <i>what village is this,
 * have I seen it, is it my home, where is its canonical anchor</i>. Nothing else. No villagers, no
 * offers, no containers, no affinity — those are V2/V4 and each needs its own perception first.
 *
 * <p>The anchor is {@link VillageAnchorPolicy#anchorOf} output, so it is comparable with
 * {@code Raid.getCenter()} by construction rather than by coincidence (D-VR-019).
 */
public final class KnownVillage {

    private final BlockPos anchor;
    private SettlementTier tier;
    private final long firstSeenTick;
    private long lastSeenTick;

    /**
     * How many POIs were admitted when the anchor was last recomputed.
     *
     * <p>Kept because it is the honest confidence signal for the anchor: an anchor derived from
     * three admitted POIs at the edge of perception is a worse estimate of the settlement centre
     * than one derived from thirty, and a later re-observation with more POIs should win. Without
     * it, "seen more recently" would be the only tiebreak and a glancing pass at the village edge
     * would overwrite a good anchor with a bad one.
     */
    private int poiCount;

    public KnownVillage(BlockPos anchor, SettlementTier tier, long firstSeenTick, long lastSeenTick,
            int poiCount) {
        this.anchor = Objects.requireNonNull(anchor, "anchor").immutable();
        this.tier = Objects.requireNonNull(tier, "tier");
        this.firstSeenTick = firstSeenTick;
        this.lastSeenTick = lastSeenTick;
        this.poiCount = poiCount;
    }

    public static KnownVillage discovered(BlockPos anchor, long tick, int poiCount) {
        return new KnownVillage(anchor, SettlementTier.PASSING_THROUGH, tick, tick, poiCount);
    }

    public BlockPos anchor() {
        return anchor;
    }

    public SettlementTier tier() {
        return tier;
    }

    public long firstSeenTick() {
        return firstSeenTick;
    }

    public long lastSeenTick() {
        return lastSeenTick;
    }

    public int poiCount() {
        return poiCount;
    }

    public boolean isHome() {
        return tier == SettlementTier.HOME_VILLAGE;
    }

    void setTier(SettlementTier next) {
        this.tier = Objects.requireNonNull(next, "tier");
    }

    void observedAt(long tick) {
        if (tick > lastSeenTick) {
            this.lastSeenTick = tick;
        }
    }

    /**
     * Re-observation with a better-supported anchor.
     *
     * <p>Returns a replacement rather than mutating {@code anchor}, because the anchor is the
     * settlement's identity: a mutable anchor could drift far enough across successive partial
     * observations that the village silently becomes a different village, which is the failure
     * D-VR-019 exists to prevent — reintroduced from the inside.
     *
     * @return {@code this} when the new observation is not better supported
     */
    KnownVillage withStrongerObservation(BlockPos newAnchor, long tick, int newPoiCount) {
        observedAt(tick);
        if (newPoiCount <= poiCount) {
            return this;
        }
        KnownVillage replacement =
                new KnownVillage(newAnchor, tier, firstSeenTick, Math.max(tick, lastSeenTick), newPoiCount);
        return replacement;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("anchor", NbtUtils.writeBlockPos(anchor));
        tag.putString("tier", tier.name());
        tag.putLong("firstSeen", firstSeenTick);
        tag.putLong("lastSeen", lastSeenTick);
        tag.putInt("poiCount", poiCount);
        return tag;
    }

    /**
     * @return {@code null} when the entry cannot be trusted. An unreadable anchor or an unknown tier
     *     name (a tier removed in a later version, or a hand-edited save) must not resurrect as a
     *     village at the world origin with an arbitrary tier — dropping the row is the safe
     *     direction, since the mob will simply rediscover the settlement on its next visit.
     */
    public static KnownVillage load(CompoundTag tag) {
        if (tag == null || !tag.contains("anchor")) {
            return null;
        }
        BlockPos anchor = NbtUtils.readBlockPos(tag, "anchor").orElse(null);
        if (anchor == null) {
            return null;
        }
        SettlementTier tier;
        try {
            tier = SettlementTier.valueOf(tag.getString("tier"));
        } catch (IllegalArgumentException unknownTier) {
            return null;
        }
        return new KnownVillage(
                anchor, tier, tag.getLong("firstSeen"), tag.getLong("lastSeen"), tag.getInt("poiCount"));
    }

    @Override
    public String toString() {
        return "KnownVillage[" + anchor.toShortString() + " " + tier + " poi=" + poiCount + "]";
    }
}
