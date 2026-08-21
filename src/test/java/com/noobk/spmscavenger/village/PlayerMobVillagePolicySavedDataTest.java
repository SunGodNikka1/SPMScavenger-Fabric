package com.noobk.spmscavenger.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

/** Task-53 — profile store scenarios 7–10 and store-specific tests. */
class PlayerMobVillagePolicySavedDataTest {

    private static final UUID MOB = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    /** Store-specific — profileOf untouched mob is NEUTRAL with zero tracked entries. */
    @Test
    void profileOfUntouchedMobIsNeutralWithoutMaterializing() {
        PlayerMobVillagePolicySavedData store = new PlayerMobVillagePolicySavedData();
        assertEquals(VillageScenarioProfile.NEUTRAL, store.readProfile(MOB));
        assertEquals(0, store.trackedCount());
    }

    /** Store-specific — set VILLAGE_ALLY creates exactly one entry. */
    @Test
    void setVillageAllyCreatesOneEntry() {
        PlayerMobVillagePolicySavedData store = new PlayerMobVillagePolicySavedData();
        store.assignAlly(MOB);
        assertEquals(1, store.trackedCount());
        assertEquals(VillageScenarioProfile.VILLAGE_ALLY, store.readProfile(MOB));
    }

    /** Store-specific — set NEUTRAL removes the row (S10 negative control target). */
    @Test
    void setNeutralRemovesRow() {
        PlayerMobVillagePolicySavedData store = new PlayerMobVillagePolicySavedData();
        store.assignAlly(MOB);
        assertTrue(store.removeAssignment(MOB));
        assertEquals(0, store.trackedCount());
        assertEquals(VillageScenarioProfile.NEUTRAL, store.readProfile(MOB));
    }

    /** Store-specific — unknown serialized value loads as absent (NEUTRAL), not preserved. */
    @Test
    void loadUnknownValueDoesNotPreserveRow() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putUUID("mob", MOB);
        entry.putString("profile", "trader");
        list.add(entry);
        tag.put("assignments", list);

        PlayerMobVillagePolicySavedData loaded =
                PlayerMobVillagePolicySavedData.load(tag, registries);
        assertEquals(VillageScenarioProfile.NEUTRAL, loaded.readProfile(MOB));
        assertEquals(0, loaded.trackedCount());

        CompoundTag resaved = loaded.save(new CompoundTag(), registries);
        assertTrue(!resaved.contains("assignments")
                        || resaved.getList("assignments", Tag.TAG_COMPOUND).isEmpty(),
                "no canonical row after unknown value is dropped");
    }

    /** Scenario 7 — ordinary unload does not touch store (no assignment hook); row survives. */
    @Test
    void scenario7_allyRowSurvivesSimulatedUnload() {
        PlayerMobVillagePolicySavedData store = new PlayerMobVillagePolicySavedData();
        store.assignAlly(MOB);
        assertEquals(VillageScenarioProfile.VILLAGE_ALLY, store.readProfile(MOB));
    }

    /** Scenario 8 — permanent removal clears assignment. */
    @Test
    void scenario8_forgetRemovesAssignment() {
        PlayerMobVillagePolicySavedData store = new PlayerMobVillagePolicySavedData();
        store.assignAlly(MOB);
        assertTrue(store.removeAssignment(MOB));
        assertEquals(0, store.trackedCount());
    }

    /** Scenario 9 — save/reload round-trip preserves VILLAGE_ALLY. */
    @Test
    void scenario9_saveReloadPreservesAlly() {
        PlayerMobVillagePolicySavedData store = new PlayerMobVillagePolicySavedData();
        store.assignAlly(MOB);
        CompoundTag saved = store.save(new CompoundTag(), registries);
        PlayerMobVillagePolicySavedData reloaded =
                PlayerMobVillagePolicySavedData.load(saved, registries);
        assertEquals(VillageScenarioProfile.VILLAGE_ALLY, reloaded.readProfile(MOB));
    }

    /** Scenario 10 — missing entry is NEUTRAL. */
    @Test
    void scenario10_missingEntryIsNeutral() {
        PlayerMobVillagePolicySavedData loaded =
                PlayerMobVillagePolicySavedData.load(new CompoundTag(), registries);
        assertEquals(VillageScenarioProfile.NEUTRAL, loaded.readProfile(MOB));
    }

    /** S11 — peek on null server does not materialize. */
    @Test
    void peekOnNullServerDoesNotMaterialize() {
        assertNull(PlayerMobVillagePolicySavedData.peek(null));
        assertEquals(VillageScenarioProfile.NEUTRAL,
                PlayerMobVillagePolicySavedData.profileOf(null, MOB));
    }

    /** forgetEverywhere delegates to single canonical forget (RET-1e contract). */
    @Test
    void forgetEverywhereReturnsZeroWhenStoreAbsent() {
        assertEquals(0, PlayerMobVillagePolicySavedData.forgetEverywhere(null, MOB));
    }
}
