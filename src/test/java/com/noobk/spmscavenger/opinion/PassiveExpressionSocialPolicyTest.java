package com.noobk.spmscavenger.opinion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PassiveExpressionSocialPolicyTest {

    @Test
    void onlyStrictlySelfLikedRelationshipsQualify() {
        assertTrue(PassiveExpressionSocialPolicy.isSelfLiked(5.01f, 5f));
        assertFalse(PassiveExpressionSocialPolicy.isSelfLiked(5f, 5f));
        assertFalse(PassiveExpressionSocialPolicy.isSelfLiked(0f, 5f));
        assertFalse(PassiveExpressionSocialPolicy.isSelfLiked(null, 5f));
    }
}
