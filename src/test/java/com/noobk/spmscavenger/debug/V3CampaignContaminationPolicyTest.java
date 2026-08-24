package com.noobk.spmscavenger.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class V3CampaignContaminationPolicyTest {

    @Test
    void unrelatedPlayerMobIsRemovedOnlyBeforeWindowOpens() {
        assertEquals(V3CampaignContaminationPolicy.Action.REMOVE_PRE_WINDOW,
                V3CampaignContaminationPolicy.decide(false, false));
        assertEquals(V3CampaignContaminationPolicy.Action.EXTERNAL_INTERFERENCE,
                V3CampaignContaminationPolicy.decide(true, false));
    }

    @Test
    void declaredFixtureMobsAreNeverContamination() {
        assertEquals(V3CampaignContaminationPolicy.Action.IGNORE,
                V3CampaignContaminationPolicy.decide(false, true));
        assertEquals(V3CampaignContaminationPolicy.Action.IGNORE,
                V3CampaignContaminationPolicy.decide(true, true));
    }
}
