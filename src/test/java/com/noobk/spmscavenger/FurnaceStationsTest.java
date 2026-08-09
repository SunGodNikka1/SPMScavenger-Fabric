package com.noobk.spmscavenger;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FS-2 / D-FSM-002 / D-FSM-007 — station policy, walk claims, ticket persistence. */
class FurnaceStationsTest {

    private static RegistryAccess.Frozen registries;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    @BeforeEach
    @AfterEach
    void clearClaims() {
        FurnaceStations.clearWalkClaimsForTest();
    }

    @Test
    void mayUse_scavengerOwnedEmptyAllowed() {
        UUID mob = UUID.randomUUID();
        assertTrue(FurnaceStations.mayUse(true, true, false, null, mob, false));
    }

    @Test
    void mayUse_foreignNonEmptyRejectedEvenWhenCommunal() {
        UUID mob = UUID.randomUUID();
        assertFalse(FurnaceStations.mayUse(false, false, false, null, mob, true));
    }

    @Test
    void mayUse_communalEmptyOnlyWhenOptIn() {
        UUID mob = UUID.randomUUID();
        assertFalse(FurnaceStations.mayUse(false, true, false, null, mob, false));
        assertTrue(FurnaceStations.mayUse(false, true, false, null, mob, true));
    }

    @Test
    void mayUse_openTicketOnlyForClaimant() {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        assertTrue(FurnaceStations.mayUse(true, false, true, owner, owner, false));
        assertFalse(FurnaceStations.mayUse(true, false, true, owner, other, true));
    }

    @Test
    void walkClaimBlocksSecondMobUntilExpiry() {
        BlockPos pos = new BlockPos(1, 64, 1);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertTrue(FurnaceStations.tryClaimWalk(pos, a, 100));
        assertFalse(FurnaceStations.tryClaimWalk(pos, b, 100));
        assertTrue(FurnaceStations.tryClaimWalk(pos, b, 100 + FurnaceStations.WALK_CLAIM_TICKS + 1));
    }

    /** U-F6: ticket Survives save/load; reclaim closes mismatched fingerprints. */
    @Test
    void uF6_ticketRoundTripAndFailClosedReclaim() {
        FurnaceJobSavedData data = FurnaceJobSavedData.createEmpty();
        BlockPos pos = new BlockPos(3, 70, -2);
        UUID mob = UUID.randomUUID();
        data.recordPlaced(pos);

        FurnaceJobSavedData.FurnaceJobTicket ticket = new FurnaceJobSavedData.FurnaceJobTicket(
                pos,
                mob,
                StackFingerprint.of(Items.OAK_LOG, 1),
                StackFingerprint.of(Items.COAL, 1),
                StackFingerprint.of(Items.CHARCOAL, 1),
                1,
                12345L,
                ResourceLocation.fromNamespaceAndPath("minecraft", "charcoal"),
                FurnaceJobSavedData.JobPhase.INSERTED);
        data.putTicket(ticket);

        CompoundTag saved = data.save(new CompoundTag(), registries);
        FurnaceJobSavedData loaded = FurnaceJobSavedData.load(saved, registries);

        assertTrue(loaded.isScavengerOwned(pos));
        assertTrue(loaded.ticketAt(pos).isPresent());
        assertEquals(FurnaceJobSavedData.JobPhase.INSERTED, loaded.ticketAt(pos).get().phase());
        assertEquals(mob, loaded.ticketAt(pos).get().claimantMob());
        assertTrue(loaded.ticketAt(pos).get().input().matchesExact(
                new net.minecraft.world.item.ItemStack(Items.OAK_LOG, 1)));

        int closed = loaded.reclaimOrClose(t -> false);
        assertEquals(1, closed);
        assertTrue(loaded.ticketAt(pos).isEmpty());
        assertTrue(loaded.isScavengerOwned(pos), "ownership survives fail-closed ticket close");
    }
}
