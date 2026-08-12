package com.noobk.spmscavenger.client.opinion;

import com.noobk.spmscavenger.network.OpinionInspectPayloads;
import com.noobk.spmscavenger.opinion.readout.OpinionInspectRejectReason;
import com.noobk.spmscavenger.opinion.readout.OpinionReadoutSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/** GAO-8B Task 42B — client response routing and late-packet discard (D-GAO-040). */
public final class OpinionInspectClient {

    private static final AtomicLong ACTIVE_REQUEST_ID = new AtomicLong(-1L);
    private static volatile int activeEntityId = -1;
    private static OpinionInspectorScreen openScreen;

    private OpinionInspectClient() {
    }

    public static long nextRequestId() {
        return System.nanoTime();
    }

    public static void beginRequest(long requestId, int entityId) {
        ACTIVE_REQUEST_ID.set(requestId);
        activeEntityId = entityId;
    }

    public static void handleResponse(OpinionInspectPayloads.Response response) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        if (response.requestId() != ACTIVE_REQUEST_ID.get()) {
            return;
        }
        if (response.rejectReason() != OpinionInspectRejectReason.NONE) {
            client.player.displayClientMessage(
                    Component.literal("Inspect Opinion: " + response.rejectReason().name()), false);
            return;
        }
        Optional<OpinionReadoutSnapshot> snapshot = response.snapshot();
        if (snapshot.isEmpty()) {
            client.player.displayClientMessage(
                    Component.literal("Inspect Opinion: empty response"), false);
            return;
        }
        if (openScreen != null && openScreen.entityId() == response.entityId()) {
            openScreen.applySnapshot(snapshot.get());
            return;
        }
        OpinionInspectorScreen screen = new OpinionInspectorScreen(snapshot.get());
        openScreen = screen;
        client.setScreen(screen);
    }

    public static void refreshFromScreen(OpinionInspectorScreen screen) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        long requestId = nextRequestId();
        beginRequest(requestId, screen.entityId());
        OpinionInspectKeybinds.sendInspectRequest(client, screen.entityId(), requestId);
    }

    public static void clearIfClosed(OpinionInspectorScreen screen) {
        if (openScreen == screen) {
            openScreen = null;
            ACTIVE_REQUEST_ID.set(-1L);
            activeEntityId = -1;
        }
    }
}
