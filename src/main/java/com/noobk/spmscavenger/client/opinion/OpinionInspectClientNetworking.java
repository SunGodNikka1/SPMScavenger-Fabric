package com.noobk.spmscavenger.client.opinion;

import com.noobk.spmscavenger.network.OpinionInspectPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** GAO-8B Task 42B — client networking registration. */
public final class OpinionInspectClientNetworking {

    private OpinionInspectClientNetworking() {
    }

    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(
                OpinionInspectPayloads.Response.TYPE,
                (payload, context) -> context.client().execute(() ->
                        OpinionInspectClient.handleResponse(payload)));
    }
}
