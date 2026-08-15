package com.noobk.spmscavenger.village;

import net.minecraft.nbt.CompoundTag;

import java.util.Objects;

/**
 * Mob-owned familiarity with a remembered settlement (V1.5). Keyed by canonical village anchor on
 * {@link MobVillageMemory}; home designation stays factual on {@link KnownVillage} only.
 */
public final class SettlementRelationship {

    private int familiarityScore;
    private int presenceFamiliarity;
    private long lastVisitTick;
    private long lastPresenceTick;
    private long lastOutsideTick;
    private int socialEventCount;

    public SettlementRelationship(int familiarityScore, long lastVisitTick, int socialEventCount) {
        this(familiarityScore, lastVisitTick, socialEventCount, 0, 0L, 0L);
    }

    public SettlementRelationship(
            int familiarityScore,
            long lastVisitTick,
            int socialEventCount,
            int presenceFamiliarity) {
        this(familiarityScore, lastVisitTick, socialEventCount, presenceFamiliarity, 0L, 0L);
    }

    public SettlementRelationship(
            int familiarityScore,
            long lastVisitTick,
            int socialEventCount,
            int presenceFamiliarity,
            long lastPresenceTick,
            long lastOutsideTick) {
        this.familiarityScore = clampFamiliarity(familiarityScore);
        this.presenceFamiliarity = clampPresenceFamiliarity(presenceFamiliarity);
        this.lastVisitTick = lastVisitTick;
        this.lastPresenceTick = lastPresenceTick;
        this.lastOutsideTick = lastOutsideTick;
        this.socialEventCount = Math.max(0, socialEventCount);
    }

    /**
     * No familiarity bumps yet. {@code lastVisitTick == 0} means "never visited" for re-entry gates.
     */
    public static SettlementRelationship empty() {
        return new SettlementRelationship(0, 0L, 0);
    }

    /** @deprecated use {@link #empty()} — seeding {@code lastVisitTick = tick} blocks bootstrap */
    @Deprecated
    public static SettlementRelationship empty(long tick) {
        return empty();
    }

    public int familiarityScore() {
        return familiarityScore;
    }

    public int presenceFamiliarity() {
        return presenceFamiliarity;
    }

    public long lastVisitTick() {
        return lastVisitTick;
    }

    public long lastPresenceTick() {
        return lastPresenceTick;
    }

    public long lastOutsideTick() {
        return lastOutsideTick;
    }

    public int socialEventCount() {
        return socialEventCount;
    }

    public AttachmentBand attachmentBand() {
        return AttachmentBand.fromScore(familiarityScore);
    }

    /** Meaningful arrival / return visit, social, home-designation, and future trade/defense bumps. */
    public SettlementRelationship bumpFamiliarity(int amount, long tick) {
        familiarityScore = clampFamiliarity(familiarityScore + amount);
        if (tick > lastVisitTick) {
            lastVisitTick = tick;
        }
        return this;
    }

    /**
     * Passive in-bounds heartbeat. Capped at {@link SettlementTuning#PRESENCE_FAMILIARITY_CAP}.
     * Always advances {@link #lastPresenceTick} even when capped — never touches
     * {@link #lastVisitTick}.
     */
    public SettlementRelationship recordPresenceHeartbeat(int amount, long tick) {
        int headroom = SettlementTuning.PRESENCE_FAMILIARITY_CAP - presenceFamiliarity;
        if (headroom > 0 && amount > 0) {
            int applied = Math.min(amount, headroom);
            presenceFamiliarity += applied;
            familiarityScore = clampFamiliarity(familiarityScore + applied);
        }
        if (tick > lastPresenceTick) {
            lastPresenceTick = tick;
        }
        return this;
    }

    /** Mob left {@link SettlementBoundsPolicy} for this settlement anchor. */
    public SettlementRelationship noteOutsideBounds(long tick) {
        if (tick > lastOutsideTick) {
            lastOutsideTick = tick;
        }
        return this;
    }

    /**
     * Re-entry visit after the mob was outside since the last meaningful visit.
     */
    public boolean qualifiesForReentryVisit() {
        return lastOutsideTick > lastVisitTick;
    }

    public SettlementRelationship recordSocialEpisode(long tick) {
        socialEventCount = Math.min(
                SettlementTuning.MAX_SOCIAL_EVENT_COUNT, socialEventCount + 1);
        return bumpFamiliarity(SettlementTuning.SOCIAL_FAMILIARITY_BUMP, tick);
    }

    public SettlementRelationship applyHomeDesignationFloor() {
        familiarityScore = Math.max(familiarityScore, SettlementTuning.HOME_DESIGNATION_FAMILIARITY_FLOOR);
        return this;
    }

    public SettlementRelationship mergeWith(SettlementRelationship other) {
        if (other == null) {
            return this;
        }
        familiarityScore = Math.max(familiarityScore, other.familiarityScore);
        presenceFamiliarity = Math.max(presenceFamiliarity, other.presenceFamiliarity);
        lastVisitTick = Math.max(lastVisitTick, other.lastVisitTick);
        lastPresenceTick = Math.max(lastPresenceTick, other.lastPresenceTick);
        lastOutsideTick = Math.max(lastOutsideTick, other.lastOutsideTick);
        socialEventCount = Math.min(
                SettlementTuning.MAX_SOCIAL_EVENT_COUNT,
                socialEventCount + other.socialEventCount);
        return this;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("familiarity", familiarityScore);
        tag.putInt("presenceFamiliarity", presenceFamiliarity);
        tag.putLong("lastVisit", lastVisitTick);
        tag.putLong("lastPresence", lastPresenceTick);
        tag.putLong("lastOutside", lastOutsideTick);
        tag.putInt("socialEvents", socialEventCount);
        return tag;
    }

    public static SettlementRelationship load(CompoundTag tag) {
        if (tag == null) {
            return empty();
        }
        int familiarity = tag.getInt("familiarity");
        int presence = tag.contains("presenceFamiliarity")
                ? tag.getInt("presenceFamiliarity")
                : Math.min(familiarity, SettlementTuning.PRESENCE_FAMILIARITY_CAP);
        return new SettlementRelationship(
                familiarity,
                tag.getLong("lastVisit"),
                tag.getInt("socialEvents"),
                presence,
                tag.getLong("lastPresence"),
                tag.getLong("lastOutside"));
    }

    static SettlementRelationship clampMerged(SettlementRelationship left, SettlementRelationship right) {
        return Objects.requireNonNull(left).mergeWith(right);
    }

    private static int clampFamiliarity(int score) {
        return Math.max(0, Math.min(SettlementTuning.MAX_FAMILIARITY, score));
    }

    private static int clampPresenceFamiliarity(int score) {
        return Math.max(0, Math.min(SettlementTuning.PRESENCE_FAMILIARITY_CAP, score));
    }
}
