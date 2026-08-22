package com.noobk.spmscavenger.village.population;

import com.noobk.spmscavenger.opinion.SocialExecutionBindingRegistry;
import com.noobk.spmscavenger.village.trade.TradeSessionClaimWindow;

import java.util.UUID;

/**
 * Exact-villager occupancy interlocks for population handoff (PD-57-6 / trade).
 */
public final class PopulationFoodInterlocks {

    private PopulationFoodInterlocks() {}

    public static boolean blocksHandoff(UUID mobId, UUID villagerId, long gameTime) {
        if (mobId == null || villagerId == null) {
            return false;
        }
        if (TradeSessionClaimWindow.claims(mobId, villagerId, gameTime)) {
            return true;
        }
        return SocialExecutionBindingRegistry.binding(mobId)
                .filter(binding -> binding.subjectId().equals(villagerId))
                .filter(binding -> binding.phase() == SocialExecutionBindingRegistry.Phase.ADMITTED
                        || binding.phase() == SocialExecutionBindingRegistry.Phase.RUNNING)
                .isPresent();
    }
}
