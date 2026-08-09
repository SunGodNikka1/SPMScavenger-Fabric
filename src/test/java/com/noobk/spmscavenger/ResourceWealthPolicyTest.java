package com.noobk.spmscavenger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceWealthPolicyTest {
    @Test
    void stockIsAllocatedOnceAcrossBlockingLayersThenReserve() {
        ResourceWealthPolicy.NeedUtility result = ResourceWealthPolicy.evaluateNeed(
                new ResourceWealthPolicy.ResourceNeedContext(
                        ResourceWealthPolicy.ResourceCategory.IRON,
                        5, 3, 2, 4, 2));

        assertEquals(0, result.immediateShortfall());
        assertEquals(0, result.replacementShortfall());
        assertEquals(2, result.projectShortfall());
        assertEquals(4, result.reserveShortfall());
        assertEquals(2, result.blockingShortfall());
    }

    @Test
    void surplusReachesReserveWithoutInventingWealth() {
        ResourceWealthPolicy.NeedUtility result = ResourceWealthPolicy.evaluateNeed(
                new ResourceWealthPolicy.ResourceNeedContext(
                        ResourceWealthPolicy.ResourceCategory.COAL,
                        10, 2, 0, 4, 0));

        assertEquals(0, result.blockingShortfall());
        assertEquals(0, result.reserveShortfall());
        assertEquals(4, result.surplus());
    }

    @Test
    void invalidNegativeDemandFailsClosed() {
        assertThrows(IllegalArgumentException.class, () ->
                new ResourceWealthPolicy.ResourceNeedContext(
                        ResourceWealthPolicy.ResourceCategory.DIAMOND,
                        0, -1, 0, 0, 0));
    }

    @Test
    void zeroGreedProducesZeroWealthValue() {
        ResourceWealthPolicy.ResourceWealthContext ctx =
                new ResourceWealthPolicy.ResourceWealthContext(
                        ResourceWealthPolicy.ResourceCategory.IRON, 0, 0.0F, 1.0F);
        assertEquals(0.0F, ResourceWealthPolicy.wealthValue(ctx));
    }

    @Test
    void saturationBandApproachesFloor() {
        ResourceWealthPolicy.ResourceWealthProfile profile =
                ResourceWealthPolicy.profileFor(ResourceWealthPolicy.ResourceCategory.IRON);
        assertEquals(1.0F, ResourceWealthPolicy.wealthFactor(0, profile));
        assertEquals(0.05F, ResourceWealthPolicy.wealthFactor(48, profile), 0.001F);
    }

    @Test
    void opportunityBonusScalesWithProximity() {
        float wealth = 0.5F;
        float near = ResourceWealthPolicy.opportunityBonus(wealth, 0.55F, 3.0F);
        float far = ResourceWealthPolicy.opportunityBonus(wealth, 0.55F, 35.0F);
        assertTrue(near > far);
        assertEquals(0.0F, far);
    }

    @Test
    void evaluateWealthNetUtilityFavorsNearbyExposedVein() {
        ResourceWealthPolicy.ResourceWealthContext ctx =
                new ResourceWealthPolicy.ResourceWealthContext(
                        ResourceWealthPolicy.ResourceCategory.IRON, 10, 0.75F, 1.0F);
        ResourceWealthPolicy.WealthUtility near =
                ResourceWealthPolicy.evaluateWealth(ctx, 1.0F);
        ResourceWealthPolicy.WealthUtility far =
                ResourceWealthPolicy.evaluateWealth(ctx, 35.0F);
        assertTrue(near.opportunityBonus() > 0.0F);
        assertEquals(0.0F, far.opportunityBonus());
        assertTrue(near.netUtility() > far.netUtility());
    }
}
