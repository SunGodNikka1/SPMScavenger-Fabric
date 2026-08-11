package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.PlayerMobs;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * GAO-6 — read-only bridge from SPM {@code feelingToward} into opinion utility space.
 *
 * <p>Never writes to SPM's relationship ledger. Fails closed when the reading is unavailable.
 */
public final class SpmEntityOpinionBridge {

    /** Max discretionary utility supplement from entity affinity (soft only). */
    public static final float UTILITY_SUPPLEMENT_MAX = 12f;

    private SpmEntityOpinionBridge() {
    }

    /**
     * Normalized {@code [-1,+1]} channel from SPM's 0–10 feeling scale, or {@code 0} when unreadable
     * or opinion is disabled.
     */
    public static float feelingChannel(Mob self, LivingEntity other) {
        if (!OpinionFeatureGate.isEnabled()) {
            return 0f;
        }
        Float feeling = PlayerMobs.feelingToward(self, other);
        if (feeling == null) {
            return 0f;
        }
        return UtilityNormalizer.channel(mapSpmFeelingToOpinionScale(feeling));
    }

    /**
     * Whether two mobs may travel together — mutual-above-neutral on SPM {@code feelingToward}
     * readings only (same rule as exploration companion invites).
     */
    public static boolean travelsTogether(Mob self, Mob other) {
        Float selfFeeling = PlayerMobs.feelingToward(self, other);
        Float otherFeeling = PlayerMobs.feelingToward(other, self);
        float neutral = PlayerMobs.neutralFeeling();
        return mutualAboveNeutral(selfFeeling, otherFeeling, neutral);
    }

    /**
     * Soft utility supplement combining SPM authority (75%) and learned entity affinity (25%).
     * For future discretionary SOCIAL scoring; not a veto.
     */
    public static float utilitySupplement(
            Mob self, LivingEntity other, EntityOpinionMemory learned) {
        if (!OpinionFeatureGate.isEnabled()) {
            return 0f;
        }
        float spmChannel = feelingChannel(self, other);
        float learnedChannel = learned == null
                ? 0f
                : UtilityNormalizer.channel(learned.preference(other.getUUID()));
        return utilitySupplementFromNormalizedChannels(spmChannel, learnedChannel);
    }

    static float utilitySupplementFromChannels(float spmChannel, float learnedPreference) {
        return utilitySupplementFromNormalizedChannels(
                spmChannel, UtilityNormalizer.channel(learnedPreference));
    }

    static float utilitySupplementFromNormalizedChannels(float spmChannel, float learnedChannel) {
        return (spmChannel * 0.75f + learnedChannel * 0.25f) * UTILITY_SUPPLEMENT_MAX;
    }

    static boolean mutualAboveNeutral(Float selfFeeling, Float otherFeeling, float neutral) {
        return selfFeeling != null
                && otherFeeling != null
                && selfFeeling > neutral
                && otherFeeling > neutral;
    }

    static float mapSpmFeelingToOpinionScale(float spmFeeling) {
        float neutral = PlayerMobs.neutralFeeling();
        float halfRange = Math.max(0.1f, Math.max(neutral, 10f - neutral));
        return Math.max(
                OpinionMemory.CHANNEL_MIN,
                Math.min(OpinionMemory.CHANNEL_MAX, (spmFeeling - neutral) / halfRange * 100f));
    }
}
