package com.noobk.spmscavenger.validation;

import com.noobk.spmscavenger.PlayerMobs;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.entity.Mob;

/** Validation-only Task-59 lifecycle and command entrypoint. */
public final class SpmScavengerValidation implements ModInitializer {

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) ->
                        {
                            V3RuntimeWitnessCommands.register(dispatcher);
                            V4RuntimeWitnessCommands.register(dispatcher);
                        });
        ServerTickEvents.END_SERVER_TICK.register(V3RuntimeCampaignController::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(V4RuntimeCampaignController::onServerTick);
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof Mob mob && PlayerMobs.isPlayerMob(mob)) {
                V3RuntimeCampaignController.onSubjectUnavailable(
                        world.getServer(), mob.getUUID(), "entity_unload", world.getGameTime());
                V4RuntimeCampaignController.onSubjectUnavailable(
                        world.getServer(), mob.getUUID(), "entity_unload", world.getGameTime());
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof Mob mob && PlayerMobs.isPlayerMob(mob)) {
                V3RuntimeCampaignController.onSubjectUnavailable(
                        mob.level().getServer(), mob.getUUID(), "death", mob.level().getGameTime());
                V4RuntimeCampaignController.onSubjectUnavailable(
                        mob.level().getServer(), mob.getUUID(), "death", mob.level().getGameTime());
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(
                V3RuntimeCampaignController::shutdownServerState);
        ServerLifecycleEvents.SERVER_STOPPED.register(
                V4RuntimeCampaignController::shutdownServerState);
    }
}
