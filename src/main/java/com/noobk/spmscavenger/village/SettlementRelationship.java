package com.noobk.spmscavenger.village;

import net.minecraft.nbt.CompoundTag;

import java.util.Objects;

/**
 * Mob-owned familiarity with a remembered settlement (V1.5). Keyed by canonical village anchor on
 * {@link MobVillageMemory}; home designation stays factual on {@link KnownVillage} only.
 */
public final class SettlementRelationship {

    private int familiarityScore;
    private long lastVisitTick;
    private int socialEventCount;

    public SettlementRelationship(int familiarityScore, long lastVisitTick, int socialEventCount) {
        this.familiarityScore = clampFamiliarity(familiarityScore);
        this.lastVisitTick = lastVisitTick;
        this.socialEventCount = Math.max(0, socialEventCount);
    }

    /**
     * No familiarity bumps yet. {@code lastVisitTick == 0} means "never bumped" so stale gates treat
     * the first visit/presence as eligible (V1.5-B bootstrap).
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

    public long lastVisitTick() {
        return lastVisitTick;
    }

    public int socialEventCount() {
        return socialEventCount;
    }

    public AttachmentBand attachmentBand() {
        return AttachmentBand.fromScore(familiarityScore);
    }

    public SettlementRelationship bumpFamiliarity(int amount, long tick) {
        familiarityScore = clampFamiliarity(familiarityScore + amount);
        if (tick > lastVisitTick) {
            lastVisitTick = tick;
        }
        return this;
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
        lastVisitTick = Math.max(lastVisitTick, other.lastVisitTick);
        socialEventCount = Math.min(
                SettlementTuning.MAX_SOCIAL_EVENT_COUNT,
                socialEventCount + other.socialEventCount);
        return this;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("familiarity", familiarityScore);
        tag.putLong("lastVisit", lastVisitTick);
        tag.putInt("socialEvents", socialEventCount);
        return tag;
    }

    public static SettlementRelationship load(CompoundTag tag) {
        if (tag == null) {
            return empty();
        }
        return new SettlementRelationship(
                tag.getInt("familiarity"),
                tag.getLong("lastVisit"),
                tag.getInt("socialEvents"));
    }

    static SettlementRelationship clampMerged(SettlementRelationship left, SettlementRelationship right) {
        return Objects.requireNonNull(left).mergeWith(right);
    }

    private static int clampFamiliarity(int score) {
        return Math.max(0, Math.min(SettlementTuning.MAX_FAMILIARITY, score));
    }
}
