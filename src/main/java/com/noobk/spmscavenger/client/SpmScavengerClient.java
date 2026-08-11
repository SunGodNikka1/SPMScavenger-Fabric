package com.noobk.spmscavenger.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

/** Client-only registration for shader-compatible objective rendering. */
public final class SpmScavengerClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        WorldRenderEvents.START.register(context -> ShaderReadoutOverlay.beginFrame());
        HudRenderCallback.EVENT.register((graphics, tickCounter) ->
                ShaderReadoutOverlay.render(graphics));
    }
}
