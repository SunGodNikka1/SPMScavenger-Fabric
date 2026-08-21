package com.noobk.spmscavenger.village.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.UUID;

/** Task-54 — diagnostic policy D1–D3. */
class StorageOwnershipPolicyTest {

    private static final UUID MOB = UUID.fromString("00000000-0000-0000-0000-000000000055");
    private static final GlobalPos KEY = GlobalPos.of(
            ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("overworld")),
            net.minecraft.core.BlockPos.ZERO);

    /** D1 — no village evidence must not collapse to FOREIGN. */
    @Test
    void d1_noVillageMemoryIsUnknownNotForeign() {
        ResolvedContainerFacts facts = new ResolvedContainerFacts(true, true, KEY);
        StorageOwnership ownership = StorageOwnershipPolicy.classify(
                facts,
                SettlementStorageFact.UNKNOWN,
                MOB,
                new StoragePermissionSavedData());
        assertEquals(StorageOwnership.UNKNOWN, ownership);
    }

    /** D2 — inside known anchor radius → VILLAGE_PUBLIC without grant. */
    @Test
    void d2_insideKnownSettlementIsVillagePublic() {
        ResolvedContainerFacts facts = new ResolvedContainerFacts(true, true, KEY);
        StorageOwnership ownership = StorageOwnershipPolicy.classify(
                facts,
                SettlementStorageFact.IN_KNOWN_SETTLEMENT,
                MOB,
                new StoragePermissionSavedData());
        assertEquals(StorageOwnership.VILLAGE_PUBLIC, ownership);
    }

    /** D3 — positively outside all anchors → FOREIGN. */
    @Test
    void d3_outsideKnownSettlementIsForeign() {
        ResolvedContainerFacts facts = new ResolvedContainerFacts(true, true, KEY);
        StorageOwnership ownership = StorageOwnershipPolicy.classify(
                facts,
                SettlementStorageFact.OUTSIDE_KNOWN_SETTLEMENT,
                MOB,
                new StoragePermissionSavedData());
        assertEquals(StorageOwnership.FOREIGN, ownership);
    }
}
