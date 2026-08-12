package com.noobk.spmscavenger.network;

import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.opinion.readout.OpinionInspectPermissions;
import com.noobk.spmscavenger.opinion.readout.OpinionInspectRejectReason;
import com.noobk.spmscavenger.opinion.readout.OpinionReadoutSnapshot;
import com.noobk.spmscavenger.opinion.readout.OpinionReadoutSnapshots;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

/** GAO-8B Task 42B — server networking registration and handler. */
public final class OpinionInspectNetworking {

    private OpinionInspectNetworking() {
    }

    public static void registerServer() {
        PayloadTypeRegistry.playC2S().register(OpinionInspectPayloads.Request.TYPE, OpinionInspectPayloads.Request.CODEC);
        PayloadTypeRegistry.playS2C().register(OpinionInspectPayloads.Response.TYPE, OpinionInspectPayloads.Response.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(
                OpinionInspectPayloads.Request.TYPE,
                (payload, context) -> context.server().execute(() -> handleRequest(context.player(), payload)));
    }

    private static void handleRequest(ServerPlayer player, OpinionInspectPayloads.Request payload) {
        if (!OpinionInspectPermissions.mayRequest(player)) {
            send(player, rejected(payload, OpinionInspectRejectReason.PERMISSION_DENIED));
            return;
        }

        Entity entity = player.level().getEntity(payload.entityId());
        OpinionInspectRejectReason targetReason = OpinionInspectPermissions.validateTarget(player, entity);
        if (targetReason != OpinionInspectRejectReason.NONE) {
            send(player, rejected(payload, targetReason));
            return;
        }

        String displayName = entity.getName().getString();
        Optional<OpinionReadoutSnapshot> snapshot = OpinionReadoutSnapshots.captureIfPresent(
                payload.requestId(),
                payload.entityId(),
                displayName,
                entity.getUUID());
        send(player, new OpinionInspectPayloads.Response(
                payload.requestId(),
                payload.entityId(),
                OpinionInspectRejectReason.NONE,
                snapshot));
    }

    private static OpinionInspectPayloads.Response rejected(
            OpinionInspectPayloads.Request payload, OpinionInspectRejectReason reason) {
        return new OpinionInspectPayloads.Response(
                payload.requestId(),
                payload.entityId(),
                reason,
                Optional.empty());
    }

    private static void send(ServerPlayer player, OpinionInspectPayloads.Response response) {
        if (!PlayerMobs.available() && response.rejectReason() == OpinionInspectRejectReason.NONE) {
            return;
        }
        ServerPlayNetworking.send(player, response);
    }
}
