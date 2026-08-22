package com.noobk.spmscavenger.village.compost;

import com.noobk.spmscavenger.village.work.ComposterWorkFacts;
import com.noobk.spmscavenger.village.work.SettlementIdentity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Path;

/**
 * Immutable compost episode binding (task-58 SELECT phase).
 */
public record CompostDeliveryPlan(
        SettlementIdentity settlement,
        ComposterWorkFacts facts,
        BlockPos composterPos,
        Path pathToComposter,
        CompostExpendabilityPolicy.InsertionOffer delivery) {}
