package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.experience.ActivityKind;

/**
 * GAO-3 — executable discretionary activities that may be scored. Only candidates with a real
 * executor are eligible; hypothetical activities are excluded.
 */
public enum DiscretionaryActivity {
    EXPLORE(ActivityKind.OVERLAND_EXPLORATION),
    REST(ActivityKind.REST);

    private final ActivityKind opinionKind;

    DiscretionaryActivity(ActivityKind opinionKind) {
        this.opinionKind = opinionKind;
    }

    public ActivityKind opinionKind() {
        return opinionKind;
    }
}
