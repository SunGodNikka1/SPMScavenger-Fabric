package com.noobk.spmscavenger.validation;

/** Separates permitted pre-window fixture cleanup from post-open evidence contamination. */
final class V3CampaignContaminationPolicy {

    enum Action {
        IGNORE,
        REMOVE_PRE_WINDOW,
        EXTERNAL_INTERFERENCE
    }

    private V3CampaignContaminationPolicy() {
    }

    static Action decide(boolean windowOpen, boolean fixtureTagged) {
        if (fixtureTagged) {
            return Action.IGNORE;
        }
        return windowOpen ? Action.EXTERNAL_INTERFERENCE : Action.REMOVE_PRE_WINDOW;
    }
}
