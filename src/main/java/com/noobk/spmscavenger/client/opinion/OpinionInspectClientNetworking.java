package com.noobk.spmscavenger.client.opinion;

import com.noobk.spmscavenger.network.OpinionInspectPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** GAO-8B Task 42B — client networking registration. */
public final class OpinionInspectClientNetworking {

    private OpinionInspectClientNetworking() {
    }

    public static void registerClient() {
        PayloadTypeRegistry.playC2S().register(
                OpinionInspectPayloads.Request.TYPE, OpinionInspectPayloads.Request.CODEC);
        PayloadTypeRegistry.playS2C().register(
                OpinionInspectPayloads.Response.TYPE, OpinionInspectPayloads.Response.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(
                OpinionInspectPayloads.Response.TYPE,
                (payload, context) -> context.client().execute(() ->
                        OpinionInspectClient.handleResponse(payload)));
    }
}
