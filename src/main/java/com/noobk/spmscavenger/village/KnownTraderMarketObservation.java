package com.noobk.spmscavenger.village;

import com.noobk.spmscavenger.village.trade.OfferSnapshot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

/** Passive V4-A bridge from an already-performed complete vanilla-board scan into memory. */
public final class KnownTraderMarketObservation {

    private KnownTraderMarketObservation() {
    }

    public static boolean recordVanillaBoard(
            ServerLevel level, UUID mobId, Villager villager, List<OfferSnapshot> board) {
        if (level == null || mobId == null || villager == null || board == null) {
            return false;
        }
        VillageMemorySavedData data = VillageMemorySavedData.peekInDimension(level);
        if (data == null) {
            return false;
        }
        List<ItemStack> outputs = board.stream()
                .filter(java.util.Objects::nonNull)
                .map(OfferSnapshot::result)
                .filter(output -> output != null && !output.isEmpty())
                .toList();
        var villagerData = villager.getVillagerData();
        return data.recordTraderObservation(
                mobId,
                villager.blockPosition(),
                villager.getUUID(),
                BuiltInRegistries.VILLAGER_PROFESSION.getKey(villagerData.getProfession()),
                villagerData.getLevel(),
                outputs,
                level.getGameTime());
    }
}
