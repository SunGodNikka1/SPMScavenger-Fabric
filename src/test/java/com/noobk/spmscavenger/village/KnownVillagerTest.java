package com.noobk.spmscavenger.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** V4-A — bounded positive trader capability evidence, never market authority. */
class KnownVillagerTest {

    private static final BlockPos A = new BlockPos(0, 64, 0);
    private static final ResourceLocation TOOLSMITH = ResourceLocation.withDefaultNamespace("toolsmith");
    private static HolderLookup.Provider registries;

    @BeforeAll
    static void registries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    private static MobVillageMemory memory() {
        MobVillageMemory memory = new MobVillageMemory();
        memory.remember(A, 1L, ObservationQuality.fullCoverage(8));
        return memory;
    }

    private static ItemStack namedPick(String name) {
        ItemStack stack = new ItemStack(Items.IRON_PICKAXE);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    @Test
    void componentSensitiveCapabilityIdentityNormalizesCountButNotComponents() {
        TradeOutputCapability plain = TradeOutputCapability.of(new ItemStack(Items.IRON_PICKAXE, 4));
        TradeOutputCapability namedA = TradeOutputCapability.of(namedPick("A"));
        TradeOutputCapability namedB = TradeOutputCapability.of(namedPick("B"));

        assertEquals(1, plain.sample().getCount());
        assertTrue(plain.matches(new ItemStack(Items.IRON_PICKAXE, 64)));
        assertFalse(plain.matches(namedPick("A")));
        assertNotEquals(namedA, namedB);
        assertTrue(namedA.matches(namedPick("A")));
    }

    @Test
    void observedOutputBecomesPositiveThenTtlPhysicallyPrunesToUnknown() {
        MobVillageMemory memory = memory();
        UUID trader = UUID.fromString("00000000-0000-0000-0000-000000000010");
        assertTrue(memory.observeTrader(A, trader, TOOLSMITH, 3,
                List.of(new ItemStack(Items.IRON_PICKAXE)), 10L));

        assertEquals(1, memory.knownTrader(trader).orElseThrow().activeCapabilities(10L).size());
        assertEquals(1, memory.knownTrader(trader).orElseThrow()
                .activeCapabilities(10L + KnownVillager.CAPABILITY_TTL_TICKS - 1).size());
        assertTrue(memory.pruneExpiredTraderCapabilities(
                10L + KnownVillager.CAPABILITY_TTL_TICKS));
        assertTrue(memory.knownTrader(trader).orElseThrow().capabilityHints().isEmpty(),
                "expiry is physical deletion, not a predicate-only tombstone");
        assertTrue(memory.knownTrader(trader).isPresent(), "trader identity outlives its hints");
    }

    @Test
    void liveRevisitSelectivelyInvalidatesOnlyMissingCapability() {
        MobVillageMemory memory = memory();
        UUID trader = UUID.randomUUID();
        memory.observeTrader(A, trader, TOOLSMITH, 2,
                List.of(new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.DIAMOND_PICKAXE)), 20L);

        memory.observeTrader(A, trader, TOOLSMITH, 3,
                List.of(new ItemStack(Items.DIAMOND_PICKAXE)), 30L);

        KnownVillager known = memory.knownTrader(trader).orElseThrow();
        assertEquals(1, known.capabilityHints().size());
        assertTrue(known.capabilityHints().getFirst().capability()
                .matches(new ItemStack(Items.DIAMOND_PICKAXE)));
        assertEquals(3, known.lastObservedLevel());
    }

    @Test
    void missingTraderDoesNotBecomeNegativeOrEraseIdentity() {
        MobVillageMemory memory = memory();
        UUID trader = UUID.randomUUID();
        memory.observeTrader(A, trader, TOOLSMITH, 2,
                List.of(new ItemStack(Items.IRON_PICKAXE)), 20L);

        // No observeTrader call models unloaded/not-found. Ordinary time below TTL changes nothing.
        memory.pruneExpiredTraderCapabilities(100L);
        assertTrue(memory.knownTrader(trader).isPresent());
        assertEquals(1, memory.knownTrader(trader).orElseThrow().capabilityHints().size());
    }

    @Test
    void capabilityHintBoundIsPhysicalAndDeterministic() {
        MobVillageMemory memory = memory();
        UUID trader = UUID.randomUUID();
        List<ItemStack> outputs = new ArrayList<>();
        for (int i = 0; i < KnownVillager.MAX_CAPABILITY_HINTS + 4; i++) {
            outputs.add(namedPick("variant-" + i));
        }
        memory.observeTrader(A, trader, TOOLSMITH, 1, outputs, 50L);
        assertEquals(KnownVillager.MAX_CAPABILITY_HINTS,
                memory.knownTrader(trader).orElseThrow().capabilityHints().size());
    }

    @Test
    void perSettlementBoundUsesOldestThenUuidTieBreak() {
        MobVillageMemory memory = memory();
        UUID smallest = new UUID(0L, 1L);
        for (int i = 0; i < MobVillageMemory.MAX_KNOWN_TRADERS_PER_SETTLEMENT; i++) {
            UUID id = i == 0 ? smallest : new UUID(0L, 100L + i);
            memory.observeTrader(A, id, TOOLSMITH, 1, List.of(new ItemStack(Items.IRON_PICKAXE)), 100L);
        }
        UUID newest = new UUID(1L, 1L);
        memory.observeTrader(A, newest, TOOLSMITH, 1,
                List.of(new ItemStack(Items.DIAMOND_PICKAXE)), 101L);

        assertEquals(MobVillageMemory.MAX_KNOWN_TRADERS_PER_SETTLEMENT,
                memory.knownTraders().size());
        assertTrue(memory.knownTrader(smallest).isEmpty(), "smallest UUID loses equal-oldest tie");
        assertTrue(memory.knownTrader(newest).isPresent());
    }

    @Test
    void globalAndPerSettlementBoundsAreEnforcedSimultaneously() {
        MobVillageMemory memory = new MobVillageMemory();
        for (int settlement = 0; settlement < 5; settlement++) {
            BlockPos anchor = new BlockPos(settlement * 500, 64, 0);
            memory.remember(anchor, settlement, ObservationQuality.fullCoverage(8));
            for (int trader = 0; trader < MobVillageMemory.MAX_KNOWN_TRADERS_PER_SETTLEMENT; trader++) {
                memory.observeTrader(anchor, new UUID(settlement + 1L, trader + 1L), TOOLSMITH, 1,
                        List.of(new ItemStack(Items.IRON_PICKAXE)), settlement * 100L + trader);
            }
        }

        assertEquals(MobVillageMemory.MAX_KNOWN_TRADERS_PER_MOB, memory.knownTraders().size());
        for (KnownVillage village : memory.villages()) {
            assertTrue(memory.knownTradersAt(village.anchor()).size()
                    <= MobVillageMemory.MAX_KNOWN_TRADERS_PER_SETTLEMENT);
        }
    }

    @Test
    void anchorSupersessionRekeysTraderAndSettlementEvictionDeletesIt() {
        MobVillageMemory memory = memory();
        UUID trader = UUID.randomUUID();
        memory.observeTrader(A, trader, TOOLSMITH, 1,
                List.of(new ItemStack(Items.IRON_PICKAXE)), 10L);
        BlockPos stronger = new BlockPos(20, 64, 0);
        memory.remember(stronger, 20L, ObservationQuality.fullCoverage(20));
        assertEquals(stronger, memory.knownTrader(trader).orElseThrow().settlementAnchor());

        for (int i = 1; i <= MobVillageMemory.MAX_KNOWN_VILLAGES; i++) {
            memory.remember(new BlockPos(i * 500, 64, 0), 100L + i,
                    ObservationQuality.fullCoverage(4));
        }
        assertTrue(memory.at(stronger).isEmpty());
        assertTrue(memory.knownTrader(trader).isEmpty());
    }

    @Test
    void saveLoadPreservesIdentityComponentsAndContainsNoMarketAuthorityFields() {
        MobVillageMemory memory = memory();
        UUID trader = UUID.randomUUID();
        memory.observeTrader(A, trader, TOOLSMITH, 4, List.of(namedPick("Exact")), 77L);

        var saved = memory.save(registries);
        String serialized = saved.toString();
        assertFalse(serialized.contains("price"));
        assertFalse(serialized.contains("offerIndex"));
        assertFalse(serialized.contains("rankOrdinal"));
        assertFalse(serialized.contains("uses"));
        assertFalse(serialized.contains("afford"));
        MobVillageMemory loaded = MobVillageMemory.load(saved, registries);

        KnownVillager known = loaded.knownTrader(trader).orElseThrow();
        assertEquals(A, known.settlementAnchor());
        assertEquals(TOOLSMITH, known.lastObservedProfession());
        assertEquals(4, known.lastObservedLevel());
        assertTrue(known.capabilityHints().getFirst().capability().matches(namedPick("Exact")));
        assertFalse(known.capabilityHints().getFirst().capability().matches(namedPick("Other")));
    }

    @Test
    void savedDataRoundTripAndDirtyOwnerPersistObservationAndExpiry() {
        VillageMemorySavedData store = new VillageMemorySavedData();
        UUID mob = UUID.randomUUID();
        UUID trader = UUID.randomUUID();
        store.memoryOf(mob).remember(A, 1L, ObservationQuality.fullCoverage(8));
        assertTrue(store.recordTraderObservation(mob, A, trader, TOOLSMITH, 2,
                List.of(namedPick("Persisted")), 40L));
        assertTrue(store.isDirty());

        net.minecraft.nbt.CompoundTag root = store.save(new net.minecraft.nbt.CompoundTag(), registries);
        VillageMemorySavedData loaded = VillageMemorySavedData.load(root, registries);
        assertTrue(loaded.peek(mob).orElseThrow().knownTrader(trader).orElseThrow()
                .capabilityHints().getFirst().capability().matches(namedPick("Persisted")));

        assertTrue(loaded.activeKnownTrader(
                mob, trader, 40L + KnownVillager.CAPABILITY_TTL_TICKS).isPresent());
        assertTrue(loaded.peek(mob).orElseThrow().knownTrader(trader).orElseThrow()
                .capabilityHints().isEmpty());
        assertTrue(loaded.isDirty(), "physical expiry pruning must schedule persistence");
    }

    @Test
    void unknownSettlementCannotManufactureTraderOrVillageMemory() {
        MobVillageMemory memory = new MobVillageMemory();
        assertFalse(memory.observeTrader(A, UUID.randomUUID(), TOOLSMITH, 1,
                List.of(new ItemStack(Items.IRON_PICKAXE)), 10L));
        assertTrue(memory.knownTraders().isEmpty());
        assertEquals(0, memory.size());

        VillageMemorySavedData store = new VillageMemorySavedData();
        assertFalse(store.recordTraderObservation(UUID.randomUUID(), A, UUID.randomUUID(),
                TOOLSMITH, 1, List.of(new ItemStack(Items.IRON_PICKAXE)), 10L));
        assertEquals(0, store.trackedMobCount());
        assertFalse(store.isDirty());
    }

    @Test
    void orphanTraderRowsFailClosedAndPermanentOwnerCleanupRemovesContainingMemory() {
        MobVillageMemory memory = memory();
        UUID trader = UUID.randomUUID();
        memory.observeTrader(A, trader, TOOLSMITH, 1,
                List.of(new ItemStack(Items.IRON_PICKAXE)), 20L);
        var saved = memory.save(registries);
        saved.getList("knownTraders", 10).getCompound(0)
                .put("settlement", net.minecraft.nbt.NbtUtils.writeBlockPos(new BlockPos(9999, 64, 0)));
        assertTrue(MobVillageMemory.load(saved, registries).knownTraders().isEmpty());

        VillageMemorySavedData store = new VillageMemorySavedData();
        UUID mob = UUID.randomUUID();
        MobVillageMemory owned = store.memoryOf(mob);
        owned.remember(A, 1L, ObservationQuality.fullCoverage(8));
        owned.observeTrader(A, trader, TOOLSMITH, 1,
                List.of(new ItemStack(Items.IRON_PICKAXE)), 20L);
        assertEquals(1, VillageMemorySavedData.forgetIn(List.of(store), mob));
        assertTrue(store.peek(mob).isEmpty());
    }
}
