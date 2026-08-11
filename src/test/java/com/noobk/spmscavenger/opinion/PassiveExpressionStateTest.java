package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.experience.MobExperienceContext;
import com.noobk.spmscavenger.experience.OpinionExperienceSinks;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PassiveExpressionStateTest {

    @Test
    void profileIsEphemeralAndInvalidatesOnFreezeOrUnloadCleanup() {
        MobExperienceContext context = new MobExperienceContext(
                UUID.randomUUID(), OpinionExperienceSinks.noOp());
        PassiveExpressionProfile active = new PassiveExpressionProfile(
                true, PassiveExpressionTone.BORED, 20, 40, 8, 20, 90f, 30f, 0.5f);

        context.publishPassiveExpression(active);
        assertEquals(active, context.passiveExpressionProfile());

        context.freeze();
        assertFalse(context.passiveExpressionProfile().eligible());
        context.resume();
        assertFalse(context.passiveExpressionProfile().eligible(),
                "resume must not resurrect a stale cosmetic commitment");

        context.publishPassiveExpression(active);
        context.invalidateEphemeral();
        assertFalse(context.passiveExpressionProfile().eligible());
    }
}
