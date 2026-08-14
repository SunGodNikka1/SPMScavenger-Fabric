package com.noobk.spmscavenger.village;

/**
 * V1 / D-VR-009 (scoring half, endorsed) — what a settlement is <em>to this mob</em>.
 *
 * <p>V1 ships the vocabulary and the {@link #HOME_VILLAGE} identity only. Utility-driven promotion
 * and demotion need affinity, remembered traders and raid history, none of which exist yet — so V1
 * deliberately assigns {@link #PASSING_THROUGH} on discovery and changes tier only on explicit
 * designation. Inventing promotion rules now would mean inventing the inputs too, and those inputs
 * would be zeros dressed as judgements.
 */
public enum SettlementTier {

    /** Seen; no established relationship. Every village starts here. */
    PASSING_THROUGH,

    /** Known useful villager(s); commute acceptable. **V4** — no V1 producer. */
    TRADING_POST,

    /** Defend-and-store profile; highest interrupt priority. The anchor D-VR-010 watches. */
    HOME_VILLAGE,

    /** Looted chests, hit villagers, golem hostility. **V3/V4** — no V1 producer. */
    AVOID;

    /**
     * Whether this tier makes the settlement a candidate for the raid interrupt (D-VR-010).
     *
     * <p>V1 defines the predicate but ships no consumer; V5 binds it. Stated here so the later
     * binding cannot quietly widen to "any known village", which would have a mob abandon its home
     * for a raid at a settlement it walked through once.
     */
    public boolean warrantsRaidInterrupt() {
        return this == HOME_VILLAGE;
    }
}
