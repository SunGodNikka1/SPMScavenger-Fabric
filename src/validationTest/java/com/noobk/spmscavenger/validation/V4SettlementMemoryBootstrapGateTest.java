package com.noobk.spmscavenger.validation;

import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.WorkDemandPolicy;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class V4SettlementMemoryBootstrapGateTest {

    @Test
    void settlementPerceptionInventoryCreatesNoScavengerDemand() {
        ScavengerConfig config = new ScavengerConfig();
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(
                Items.TORCH, Math.max(8, config.torchStockTarget)));
        backpack.setItem(1, new ItemStack(Items.DIAMOND_AXE));

        assertEquals(java.util.Optional.empty(), WorkDemandPolicy.select(
                backpack, new ItemStack(Items.DIAMOND_PICKAXE), ItemStack.EMPTY, config));
    }

    @Test
    void waitsForExactlyOneNaturallyRememberedSettlement() {
        assertEquals(V4SettlementMemoryBootstrapGate.Verdict.WAITING,
                V4SettlementMemoryBootstrapGate.evaluate(
                        0, false, false, false, false).verdict());
        assertEquals(V4SettlementMemoryBootstrapGate.Verdict.READY,
                V4SettlementMemoryBootstrapGate.evaluate(
                        1, false, false, false, true).verdict());
    }

    @Test
    void preWarmupDemandHomeAmbiguityAndBadGeometryFailClosed() {
        assertEquals(V4SettlementMemoryBootstrapGate.Verdict.FIXTURE_FAILURE,
                V4SettlementMemoryBootstrapGate.evaluate(
                        0, false, true, true, false).verdict());
        assertEquals(V4SettlementMemoryBootstrapGate.Verdict.FIXTURE_FAILURE,
                V4SettlementMemoryBootstrapGate.evaluate(
                        1, true, false, false, true).verdict());
        assertEquals(V4SettlementMemoryBootstrapGate.Verdict.FIXTURE_FAILURE,
                V4SettlementMemoryBootstrapGate.evaluate(
                        2, false, false, false, true).verdict());
        assertEquals(V4SettlementMemoryBootstrapGate.Verdict.FIXTURE_FAILURE,
                V4SettlementMemoryBootstrapGate.evaluate(
                        1, false, false, false, false).verdict());
    }
}
