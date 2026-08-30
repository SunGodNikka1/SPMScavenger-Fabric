package com.noobk.spmscavenger.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RequiredMixinTargetAuditTest {

    @Test
    void negativeControl_readableOnlySelectorDoesNotMatchRemappedGoalOverride() {
        Set<RequiredMixinTargetAudit.MethodSignature> target = Set.of(
                new RequiredMixinTargetAudit.MethodSignature("method_6264", "()Z"));

        assertTrue(RequiredMixinTargetAudit.resolve(target, List.of("canUse")).isEmpty(),
                "negative control: the exact selector that crashed must remain unresolved");
        assertEquals(target, RequiredMixinTargetAudit.resolve(
                target, List.of("canUse", "method_6264")));
    }

    @Test
    void descriptorParticipatesInRuntimeResolution() {
        Set<RequiredMixinTargetAudit.MethodSignature> target = Set.of(
                new RequiredMixinTargetAudit.MethodSignature("offers", "(LA;)Ljava/util/List;"),
                new RequiredMixinTargetAudit.MethodSignature("offers", "(LB;)Ljava/util/List;"));

        assertEquals(Set.of(new RequiredMixinTargetAudit.MethodSignature(
                        "offers", "(LB;)Ljava/util/List;")),
                RequiredMixinTargetAudit.resolve(
                        target, List.of("offers(LB;)Ljava/util/List;")));
        assertEquals(2, RequiredMixinTargetAudit.resolve(target, List.of("offers")).size(),
                "descriptorless overloads must be rejected as ambiguous by the artifact gate");
    }

    @Test
    void livenessGoalOverridesCarryPinnedDualSelectorsAndRemainStrict() throws Exception {
        String gather = Files.readString(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/mixin/"
                        + "V4GatherHandoffLivenessMixin.java"));
        String trade = Files.readString(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/mixin/"
                        + "V4TradeGoalLivenessMixin.java"));

        assertTrue(gather.contains("{\"canUse\", \"method_6264\"}"));
        assertTrue(gather.contains("{\"stop\", \"method_6270\"}"));
        assertTrue(trade.contains("{\"canUse\", \"method_6264\"}"));
        assertTrue(trade.contains("{\"start\", \"method_6269\"}"));
        assertTrue(trade.contains("{\"tick\", \"method_6268\"}"));
        assertTrue(trade.contains("{\"stop\", \"method_6270\"}"));
        assertFalse(gather.contains("require = 0"));
        assertFalse(trade.contains("require = 0"));

        for (String modOwned : new String[] {
                "ownedMandatoryRoute", "liveDemand", "existingRouteInfeasible",
                "authorizedCandidate"}) {
            assertTrue((gather + trade).contains("method = \"" + modOwned + "\""),
                    "mod-owned seam must retain its readable production name: " + modOwned);
        }
    }
}
