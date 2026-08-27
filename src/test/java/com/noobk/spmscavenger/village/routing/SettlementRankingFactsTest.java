package com.noobk.spmscavenger.village.routing;

import com.noobk.spmscavenger.village.KnownVillager;
import com.noobk.spmscavenger.village.MobVillageMemory;
import com.noobk.spmscavenger.village.ObservationQuality;
import com.noobk.spmscavenger.village.TradeOutputCapability;
import com.noobk.spmscavenger.village.VillageMemorySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementRankingFactsTest {

    private static final BlockPos ANCHOR = new BlockPos(100, 64, 100);
    private static final ResourceLocation TOOLSMITH =
            ResourceLocation.withDefaultNamespace("toolsmith");

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void exactComponentEvidenceIsPositiveAndMismatchIsUnknown() {
        VillageMemorySavedData data = dataWith(namedPick("Exact"), 10L);
        UUID mob = MOB;

        assertEquals(CapabilityEvidenceClass.POSITIVE_HINT,
                data.rankingFacts(mob, Level.OVERWORLD,
                        TradeOutputCapability.of(namedPick("Exact")), 10L)
                        .getFirst().capabilityEvidence());
        assertEquals(CapabilityEvidenceClass.UNKNOWN,
                data.rankingFacts(mob, Level.OVERWORLD,
                        TradeOutputCapability.of(namedPick("Other")), 10L)
                        .getFirst().capabilityEvidence());
    }

    @Test
    void expiryPhysicallyPrunesAndReturnsUnknownNeverNegative() {
        VillageMemorySavedData data = dataWith(new ItemStack(Items.IRON_PICKAXE), 10L);
        long expiry = 10L + KnownVillager.CAPABILITY_TTL_TICKS;

        List<SettlementDestinationFacts> facts = data.rankingFacts(
                MOB, Level.OVERWORLD,
                TradeOutputCapability.of(new ItemStack(Items.IRON_PICKAXE)), expiry);

        assertEquals(CapabilityEvidenceClass.UNKNOWN, facts.getFirst().capabilityEvidence());
        assertTrue(data.peek(MOB).orElseThrow().knownTraders().getFirst()
                .capabilityHints().isEmpty());
        assertTrue(data.isDirty(), "ranking prune must be persisted by the SavedData owner");
        assertEquals(List.of(
                        CapabilityEvidenceClass.POSITIVE_HINT,
                        CapabilityEvidenceClass.UNKNOWN),
                List.of(CapabilityEvidenceClass.values()),
                "the epistemic model must contain no persistent negative class");
    }

    @Test
    void factsComeFromRememberedMemoryWithoutCurrentPerceptionOrChunkState() {
        VillageMemorySavedData data = dataWith(new ItemStack(Items.IRON_PICKAXE), 10L);
        MobVillageMemory memory = data.peek(MOB).orElseThrow();
        memory.designateHome(ANCHOR);
        memory.putRelationship(ANCHOR,
                new com.noobk.spmscavenger.village.SettlementRelationship(700, 5L, 2));

        SettlementDestinationFacts facts = data.rankingFacts(
                MOB, Level.OVERWORLD,
                TradeOutputCapability.of(new ItemStack(Items.IRON_PICKAXE)), 20L)
                .getFirst();

        assertEquals(ANCHOR, facts.village().anchor());
        assertTrue(facts.home());
        assertEquals(700, facts.familiarity());
        assertEquals(Level.OVERWORLD, facts.key().dimension());
        assertFalse(data.isDirty(), "non-expiring fact resolution is a read, not a rewrite");
    }

    @Test
    void missingMobMemoryDoesNotAllocateOrReturnCandidates() {
        VillageMemorySavedData data = new VillageMemorySavedData();
        assertTrue(data.rankingFacts(UUID.randomUUID(), Level.OVERWORLD,
                TradeOutputCapability.of(new ItemStack(Items.IRON_PICKAXE)), 10L).isEmpty());
        assertEquals(0, data.trackedMobCount());
        assertFalse(data.isDirty());
    }

    private static final UUID MOB =
            UUID.fromString("00000000-0000-0000-0000-000000000064");

    private static VillageMemorySavedData dataWith(ItemStack output, long tick) {
        VillageMemorySavedData data = new VillageMemorySavedData();
        data.memoryOf(MOB).remember(ANCHOR, 1L, ObservationQuality.fullCoverage(8));
        data.recordTraderObservation(MOB, ANCHOR, UUID.randomUUID(), TOOLSMITH, 3,
                List.of(output), tick);
        data.setDirty(false);
        return data;
    }

    private static ItemStack namedPick(String name) {
        ItemStack stack = new ItemStack(Items.IRON_PICKAXE);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }
}
