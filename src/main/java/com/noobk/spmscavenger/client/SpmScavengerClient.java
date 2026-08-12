package com.noobk.spmscavenger.client;

import com.noobk.spmscavenger.client.opinion.OpinionInspectClientNetworking;
import com.noobk.spmscavenger.client.opinion.OpinionInspectKeybinds;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

/** Client-only registration for shader-compatible objective rendering. */
public final class SpmScavengerClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        OpinionInspectKeybinds.register();
        OpinionInspectClientNetworking.registerClient();
        WorldRenderEvents.START.register(context -> {
            // Iris can run a nested shadow-world render. Its projection must never replace the
            // main camera snapshot later used for the post-HUD billboard.
            if (!ShaderReadoutOverlay.shaderState().shadowPass()) {
                ShaderReadoutOverlay.beginFrame(
                        context.projectionMatrix(),
                        context.positionMatrix());
            }
        });
        HudRenderCallback.EVENT.register((graphics, tickCounter) ->
                ShaderReadoutOverlay.render(graphics));
    }
}
