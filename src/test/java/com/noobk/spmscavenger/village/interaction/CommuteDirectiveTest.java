package com.noobk.spmscavenger.village.interaction;

import com.noobk.spmscavenger.WorkDemandPolicy;
import com.noobk.spmscavenger.village.intent.VillageIntent;
import com.noobk.spmscavenger.village.routing.SettlementKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommuteDirectiveTest {

    @Test
    void bindingUsesExactIntentInstanceNotEqualDestinationAndDemandValue() {
        VillageIntent first = intent(100L);
        VillageIntent equalReplacement = intent(100L);
        CommuteDirective directive = CommuteDirective.requiredTrade(first);

        assertSame(first, directive.binding().intent());
        assertTrue(directive.binding().matchesExact(first));
        assertFalse(directive.binding().matchesExact(equalReplacement));
        assertFalse(directive.binding().matchesExact(
                CommuteDirective.requiredTrade(equalReplacement).binding()));
    }

    private static VillageIntent intent(long openedAt) {
        return new VillageIntent(
                VillageIntent.Kind.REQUIRED_TRADE,
                new SettlementKey(Level.OVERWORLD, new BlockPos(80, 64, 0)),
                openedAt,
                Optional.of(new WorkDemandPolicy.MaterialDemandIdentity(
                        ResourceLocation.withDefaultNamespace("iron_ingot"),
                        ResourceLocation.fromNamespaceAndPath(
                                "spmscavenger", "iron_pickaxe_upgrade"))));
    }
}
