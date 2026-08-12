package com.noobk.spmscavenger.client.opinion;

import com.mojang.blaze3d.platform.InputConstants;
import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.network.OpinionInspectPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

/** GAO-8B Task 42B — configurable inspect key (PD-GAO-14). */
public final class OpinionInspectKeybinds {

    private static final String CATEGORY = "key.categories.spmscavenger";
    public static final KeyMapping INSPECT_OPINION = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.spmscavenger.inspect_opinion",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            CATEGORY));

    private OpinionInspectKeybinds() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(OpinionInspectKeybinds::onClientTick);
    }

    private static void onClientTick(Minecraft client) {
        while (INSPECT_OPINION.consumeClick()) {
            if (client.player == null || client.level == null || !PlayerMobs.available()) {
                continue;
            }
            Entity target = targetedEntity(client);
            if (!(target instanceof Mob mob) || !PlayerMobs.isPlayerMob(mob)) {
                continue;
            }
            long requestId = OpinionInspectClient.nextRequestId();
            OpinionInspectClient.beginRequest(requestId, target.getId());
            sendInspectRequest(client, target.getId(), requestId);
        }
    }

    public static void sendInspectRequest(Minecraft client, int entityId, long requestId) {
        if (client.getConnection() == null) {
            return;
        }
        ClientPlayNetworking.send(new OpinionInspectPayloads.Request(requestId, entityId));
    }

    private static Entity targetedEntity(Minecraft client) {
        HitResult hit = client.hitResult;
        if (hit instanceof EntityHitResult entityHit) {
            return entityHit.getEntity();
        }
        return null;
    }
}
