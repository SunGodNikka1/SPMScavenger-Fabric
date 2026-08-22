package com.noobk.spmscavenger.village.population;

import com.noobk.spmscavenger.village.work.SettlementIdentity;
import com.noobk.spmscavenger.village.work.VillageWorkFacts;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.pathfinder.Path;

import java.util.Objects;
import java.util.UUID;

/** Immutable episode binding selected at admission (task-57). */
public record PopulationFoodDeliveryPlan(
        SettlementIdentity settlement,
        VillageWorkFacts facts,
        UUID recipientId,
        Villager recipient,
        Path pathToRecipient,
        PopulationFoodExpendabilityPolicy.DeliveryOffer delivery) {

    public PopulationFoodDeliveryPlan {
        Objects.requireNonNull(settlement, "settlement");
        Objects.requireNonNull(facts, "facts");
        Objects.requireNonNull(recipientId, "recipientId");
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(delivery, "delivery");
    }
}
