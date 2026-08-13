package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.experience.ActivityKind;

/**
 * GAO-3 — executable discretionary activities that may be scored. Only candidates with a real
 * executor are eligible; hypothetical activities are excluded.
 */
public enum DiscretionaryActivity {
    EXPLORE(ActivityKind.OVERLAND_EXPLORATION),
    REST(ActivityKind.REST),

    /**
     * GAO-10 — greeting a specific entity SPM recently named.
     *
     * <p>Unlike its two siblings, SOCIAL is <b>about somebody</b>. Its candidacy therefore depends
     * on a live opportunity, and the winning subject is bound immutably to the intent that decision
     * creates: the activity may be re-chosen, but a chosen subject is never re-derived.
     */
    SOCIAL(ActivityKind.SOCIALIZING);

    private final ActivityKind opinionKind;

    DiscretionaryActivity(ActivityKind opinionKind) {
        this.opinionKind = opinionKind;
    }

    public ActivityKind opinionKind() {
        return opinionKind;
    }
}
