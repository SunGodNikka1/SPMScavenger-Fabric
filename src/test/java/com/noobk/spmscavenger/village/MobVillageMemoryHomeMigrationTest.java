package com.noobk.spmscavenger.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** V4-R0 — independent home representation and deterministic legacy migration. */
class MobVillageMemoryHomeMigrationTest {

    private static final UUID MOB =
            UUID.fromString("00000000-0000-0000-0000-000000000061");
    private static HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    private static ObservationQuality complete(int admitted) {
        return ObservationQuality.fullCoverage(admitted);
    }

    private static BlockPos far(int index) {
        return new BlockPos(index * 500, 64, 0);
    }

    private static CompoundTag legacyVillage(BlockPos anchor, String tier, long tick) {
        CompoundTag row = KnownVillage.discovered(anchor, tick, complete(5)).save();
        row.putString("tier", tier);
        return row;
    }

    private static CompoundTag memoryTag(CompoundTag... rows) {
        CompoundTag memory = new CompoundTag();
        ListTag villages = new ListTag();
        for (CompoundTag row : rows) {
            villages.add(row);
        }
        memory.put("villages", villages);
        return memory;
    }

    @Test
    void legacyHomeMigratesToOneRootHomeAndTierIsNotResaved() {
        BlockPos home = far(0);
        CompoundTag legacy = memoryTag(
                legacyVillage(home, "HOME_VILLAGE", 1L),
                legacyVillage(far(1), "PASSING_THROUGH", 2L));

        MobVillageMemory loaded = MobVillageMemory.load(legacy);
        assertEquals(home, loaded.homeAnchor().orElseThrow());
        assertEquals(home, loaded.home().orElseThrow().anchor());

        CompoundTag canonical = loaded.save();
        assertEquals(home, NbtUtils.readBlockPos(canonical, "homeAnchor").orElseThrow());
        ListTag rows = canonical.getList("villages", Tag.TAG_COMPOUND);
        assertFalse(rows.getCompound(0).contains("tier"));
        assertFalse(rows.getCompound(1).contains("tier"));
    }

    @Test
    void nonHomeAndUnknownLegacyRolesPreserveVillagesButCreateNoRole() {
        CompoundTag legacy = memoryTag(
                legacyVillage(far(0), "PASSING_THROUGH", 1L),
                legacyVillage(far(1), "TRADING_POST", 2L),
                legacyVillage(far(2), "AVOID", 3L),
                legacyVillage(far(3), "MARKET_TOWN", 4L));

        MobVillageMemory loaded = MobVillageMemory.load(legacy);
        assertEquals(4, loaded.size(), "tier text no longer controls factual village validity");
        assertTrue(loaded.home().isEmpty());
        assertFalse(loaded.save().contains("economicRole"));
        assertFalse(loaded.save().contains("safetyRole"));
    }

    @Test
    void multipleLegacyHomesChooseFirstValidVillageDeterministically() {
        CompoundTag malformed = new CompoundTag();
        malformed.putString("tier", "HOME_VILLAGE");
        CompoundTag legacy = memoryTag(
                malformed,
                legacyVillage(far(1), "HOME_VILLAGE", 1L),
                legacyVillage(far(2), "HOME_VILLAGE", 2L));

        MobVillageMemory loaded = MobVillageMemory.load(legacy);
        assertEquals(far(1), loaded.homeAnchor().orElseThrow());
        assertEquals(2, loaded.size());
    }

    @Test
    void explicitHomeTakesPrecedenceOverLegacyHome() {
        CompoundTag mixed = memoryTag(
                legacyVillage(far(0), "HOME_VILLAGE", 1L),
                legacyVillage(far(1), "PASSING_THROUGH", 2L));
        mixed.put("homeAnchor", NbtUtils.writeBlockPos(far(1)));

        MobVillageMemory loaded = MobVillageMemory.load(mixed);
        assertEquals(far(1), loaded.homeAnchor().orElseThrow());
    }

    @Test
    void malformedOrOrphanExplicitHomeFailsSafeWithoutLegacyFallback() {
        CompoundTag malformed = memoryTag(legacyVillage(far(0), "HOME_VILLAGE", 1L));
        malformed.put("homeAnchor", new CompoundTag());
        assertTrue(MobVillageMemory.load(malformed).home().isEmpty());

        CompoundTag orphan = memoryTag(legacyVillage(far(0), "HOME_VILLAGE", 1L));
        orphan.put("homeAnchor", NbtUtils.writeBlockPos(far(99)));
        MobVillageMemory loaded = MobVillageMemory.load(orphan);
        assertTrue(loaded.home().isEmpty());
        assertEquals(1, loaded.size(), "corrupt home must not manufacture a village");
    }

    @Test
    void homeAndRelationshipRekeyTogetherWhenAnchorSupersedes() {
        MobVillageMemory memory = new MobVillageMemory();
        BlockPos weak = new BlockPos(0, 64, 0);
        BlockPos strong = new BlockPos(24, 64, 16);
        memory.remember(weak, 100L, ObservationQuality.withCoverage(4, 24, 4));
        memory.putRelationship(weak, new SettlementRelationship(300, 100L, 1));
        assertTrue(memory.designateHome(weak));

        memory.remember(strong, 300L, complete(25));

        assertEquals(strong, memory.homeAnchor().orElseThrow());
        assertEquals(strong, memory.home().orElseThrow().anchor());
        assertEquals(300, memory.relationshipAt(strong).orElseThrow().familiarityScore());
    }

    @Test
    void weakerIdentityMergePreservesCanonicalHomeWithoutRekey() {
        MobVillageMemory memory = new MobVillageMemory();
        BlockPos canonical = new BlockPos(0, 64, 0);
        memory.remember(canonical, 100L, complete(20));
        memory.designateHome(canonical);

        memory.remember(
                new BlockPos(20, 64, 15),
                200L,
                ObservationQuality.withCoverage(2, 20, 2));

        assertEquals(canonical, memory.homeAnchor().orElseThrow());
        assertEquals(canonical, memory.home().orElseThrow().anchor());
        assertEquals(1, memory.size());
    }

    @Test
    void homeRemainsEvictionExemptAndRelationshipEvictionIsUnchanged() {
        MobVillageMemory memory = new MobVillageMemory();
        BlockPos home = far(0);
        memory.remember(home, 1L, complete(5));
        memory.putRelationship(home, new SettlementRelationship(600, 1L, 2));
        memory.designateHome(home);
        for (int i = 1; i < MobVillageMemory.MAX_KNOWN_VILLAGES * 3; i++) {
            memory.remember(far(i), 1_000L + i, complete(5));
            memory.putRelationship(far(i), new SettlementRelationship(i, i, 0));
        }

        assertEquals(MobVillageMemory.MAX_KNOWN_VILLAGES, memory.size());
        assertEquals(home, memory.homeAnchor().orElseThrow());
        assertEquals(600, memory.relationshipAt(home).orElseThrow().familiarityScore());
        assertTrue(memory.at(far(1)).isEmpty(), "old non-home village remains the LRU victim");
        assertTrue(memory.relationshipAt(far(1)).isEmpty());
    }

    @Test
    void legacyTopLevelSavedDataIsMarkedDirtyForCanonicalRewrite() {
        CompoundTag root = new CompoundTag();
        CompoundTag mobRow = new CompoundTag();
        mobRow.putUUID("mob", MOB);
        mobRow.put("memory", memoryTag(legacyVillage(far(0), "HOME_VILLAGE", 1L)));
        ListTag mobs = new ListTag();
        mobs.add(mobRow);
        root.put("mobs", mobs);

        VillageMemorySavedData loaded = VillageMemorySavedData.load(root, registries);
        assertTrue(loaded.isDirty(), "legacy tier migration must schedule a canonical rewrite");
        assertEquals(far(0), loaded.peek(MOB).orElseThrow().homeAnchor().orElseThrow());
    }

    @Test
    void currentSchemaRoundTripDoesNotDependOnTier() {
        MobVillageMemory memory = new MobVillageMemory();
        memory.remember(far(0), 1L, complete(5));
        memory.remember(far(1), 2L, complete(6));
        memory.designateHome(far(1));

        MobVillageMemory loaded = MobVillageMemory.load(memory.save());
        assertEquals(far(1), loaded.homeAnchor().orElseThrow());
        assertEquals(2, loaded.size());
    }

    @Test
    void currentTopLevelSchemaDoesNotMarkItselfAsMigrated() {
        MobVillageMemory memory = new MobVillageMemory();
        memory.remember(far(0), 1L, complete(5));
        memory.designateHome(far(0));
        CompoundTag root = new CompoundTag();
        CompoundTag mobRow = new CompoundTag();
        mobRow.putUUID("mob", MOB);
        mobRow.put("memory", memory.save());
        ListTag mobs = new ListTag();
        mobs.add(mobRow);
        root.put("mobs", mobs);

        VillageMemorySavedData loaded = VillageMemorySavedData.load(root, registries);
        assertFalse(loaded.isDirty(), "canonical data needs no migration rewrite");
        assertEquals(far(0), loaded.peek(MOB).orElseThrow().homeAnchor().orElseThrow());
    }

    @Test
    void structuralModelContainsNoTierAndV4eDoesNotIntroduceHomeProducer() throws IOException {
        Path villageSource = Path.of("src/main/java/com/noobk/spmscavenger/village");
        assertFalse(Files.exists(villageSource.resolve("SettlementTier.java")));
        String known = Files.readString(villageSource.resolve("KnownVillage.java"));
        String memory = Files.readString(villageSource.resolve("MobVillageMemory.java"));
        assertFalse(known.contains("SettlementTier"));
        assertFalse(known.contains("putString(\"tier\""));
        assertFalse(memory.contains("TRADING_POST"));
        assertFalse(memory.contains("AVOID"));
        assertFalse(memory.contains("VillageInteractionDirector"));
        assertFalse(memory.contains("startSleeping"));
        assertTrue(findProductionSource("KnownVillager") != null,
                "V4-A KnownVillager evidence is now the authorized frontier");
        assertNotNull(findProductionSource("VillageInteractionDirector"));
        assertNull(findProductionSource("FirstHomePromotion"));
    }

    private static Path findProductionSource(String fileStem) throws IOException {
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            return paths.filter(path -> path.getFileName().toString().equals(fileStem + ".java"))
                    .findFirst()
                    .orElse(null);
        }
    }
}
