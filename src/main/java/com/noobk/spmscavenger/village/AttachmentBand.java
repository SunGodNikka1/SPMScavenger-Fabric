package com.noobk.spmscavenger.village;

/**
 * Derived familiarity band for settlement attachment (V1.5). Not persisted — computed from
 * {@link SettlementRelationship#familiarityScore()} only.
 */
public enum AttachmentBand {
    LOW,
    MEDIUM,
    HIGH;

    public static AttachmentBand fromScore(int familiarityScore) {
        if (familiarityScore >= SettlementTuning.HIGH_BAND_MIN) {
            return HIGH;
        }
        if (familiarityScore >= SettlementTuning.MEDIUM_BAND_MIN) {
            return MEDIUM;
        }
        return LOW;
    }
}
