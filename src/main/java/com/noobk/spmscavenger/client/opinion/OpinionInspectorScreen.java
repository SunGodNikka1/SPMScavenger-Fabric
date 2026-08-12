package com.noobk.spmscavenger.client.opinion;

import com.noobk.spmscavenger.opinion.readout.ActivityAdmissionView;
import com.noobk.spmscavenger.opinion.readout.OpinionReadoutDecisionView;
import com.noobk.spmscavenger.opinion.readout.OpinionReadoutSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** GAO-8B Task 42B — addon-owned read-only inspector (PD-GAO-15). */
public final class OpinionInspectorScreen extends Screen {

    private static final int PADDING = 8;
    private static final int BUTTON_WIDTH = 72;
    private static final int BUTTON_GAP = 4;
    /** Left-side panel width cap — keeps the inspected mob visible on the right. */
    private static final int PANEL_MAX_WIDTH = 400;
    /** ARGB translucent dark fill; no blur, world stays sharp behind the panel. */
    private static final int PANEL_COLOR = 0xB0101010;
    private static final int PANEL_EDGE_COLOR = 0xFF404040;

    private OpinionReadoutSnapshot snapshot;
    private final List<String> bodyLines = new ArrayList<>();
    private SelectableReadoutBox textBox;

    public OpinionInspectorScreen(OpinionReadoutSnapshot snapshot) {
        super(Component.literal("Inspect Opinion — " + snapshot.mobDisplayName()));
        this.snapshot = snapshot;
        rebuildBody();
    }

    public int entityId() {
        return snapshot.entityId();
    }

    public void applySnapshot(OpinionReadoutSnapshot updated) {
        this.snapshot = updated;
        rebuildBody();
        if (textBox != null) {
            textBox.setValue(readoutText());
        }
    }

    @Override
    protected void init() {
        int panelWidth = panelWidth();
        int textTop = 20;
        int textHeight = Math.max(40, this.height - 52);
        textBox = new SelectableReadoutBox(
                this.font,
                PADDING,
                textTop,
                panelWidth - PADDING * 2,
                textHeight,
                readoutText());
        addRenderableWidget(textBox);

        int y = this.height - 24;
        int x = PADDING;
        addRenderableWidget(Button.builder(Component.literal("Copy"), button -> copyReadoutToClipboard())
                .bounds(x, y, BUTTON_WIDTH, 20)
                .build());
        x += BUTTON_WIDTH + BUTTON_GAP;
        addRenderableWidget(Button.builder(Component.literal("Refresh"), button ->
                OpinionInspectClient.refreshFromScreen(this))
                .bounds(x, y, BUTTON_WIDTH, 20)
                .build());
        x += BUTTON_WIDTH + BUTTON_GAP;
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(x, y, BUTTON_WIDTH, 20)
                .build());
    }

    @Override
    public void onClose() {
        OpinionInspectClient.clearIfClosed(this);
        super.onClose();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderInspectorPanel(graphics);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, PADDING, 6, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_C) {
            copyReadoutToClipboard();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void copyReadoutToClipboard() {
        if (this.minecraft == null) {
            return;
        }
        this.minecraft.keyboardHandler.setClipboard(readoutText());
    }

    private String readoutText() {
        return String.join("\n", bodyLines);
    }

    private void renderInspectorPanel(GuiGraphics graphics) {
        int panelWidth = panelWidth();
        graphics.fill(0, 0, panelWidth, this.height, PANEL_COLOR);
        graphics.fill(panelWidth - 1, 0, panelWidth, this.height, PANEL_EDGE_COLOR);
    }

    private int panelWidth() {
        return Math.min(PANEL_MAX_WIDTH, Math.max(220, this.width / 2));
    }

    private void rebuildBody() {
        bodyLines.clear();
        bodyLines.add("Status: " + snapshot.status());
        bodyLines.addAll(snapshot.summaryLines());
        bodyLines.add("");
        bodyLines.add("— Affect —");
        bodyLines.add("engagement=" + snapshot.engagement()
                + " boredom=" + snapshot.boredom()
                + " satisfaction=" + snapshot.satisfaction());
        bodyLines.add("stress=" + snapshot.stress()
                + " novelty=" + snapshot.novelty()
                + " frozen=" + snapshot.frozen()
                + " ticksSinceProgress=" + snapshot.ticksSinceMeaningfulProgress());
        bodyLines.add("");
        bodyLines.add("— Director layers —");
        if (!snapshot.incumbentActivity().isBlank()) {
            bodyLines.add("incumbent=" + snapshot.incumbentActivity());
        }
        if (!snapshot.currentIntentActivity().isBlank()) {
            bodyLines.add("intent=" + snapshot.currentIntentActivity()
                    + " (" + snapshot.currentIntentLifecycle() + ")");
        }
        bodyLines.add("latestDecisionDisposition=" + snapshot.currentDisposition()
                + " restPhase=" + snapshot.restAuthorityPhase());
        ActivityAdmissionView explore = snapshot.exploreAdmission();
        ActivityAdmissionView rest = snapshot.restAdmission();
        if (!explore.isEmpty()) {
            bodyLines.add("exploreAdmission: installed=" + explore.executorPresent()
                    + " adoptable=" + explore.adoptionReady()
                    + " blocker=" + explore.blocker()
                    + (explore.detail().isBlank() ? "" : " (" + explore.detail() + ")"));
        }
        if (!rest.isEmpty()) {
            bodyLines.add("restAdmission: installed=" + rest.executorPresent()
                    + " adoptable=" + rest.adoptionReady()
                    + " blocker=" + rest.blocker()
                    + (rest.detail().isBlank() ? "" : " (" + rest.detail() + ")"));
        }
        bodyLines.add("placePreferences=" + snapshot.placePreferenceCount()
                + " entityPreferences=" + snapshot.entityPreferenceCount());
        bodyLines.add("");
        bodyLines.add("— Personality —");
        var p = snapshot.personality();
        bodyLines.add("curiosity=" + p.curiosity()
                + " sociability=" + p.sociability()
                + " risk=" + p.riskTolerance());
        bodyLines.add("persistence=" + p.persistence()
                + " materialism=" + p.materialism()
                + " adventure=" + p.adventurousness());
        bodyLines.add("");
        bodyLines.add("resting=" + snapshot.resting());
        snapshot.shelterHold().ifPresent(hold -> bodyLines.add(
                "shelter=" + hold.phase() + " @ "
                        + hold.anchorX() + "," + hold.anchorY() + "," + hold.anchorZ()));
        if (!snapshot.activityPreferences().isEmpty()) {
            bodyLines.add("");
            bodyLines.add("— Activity preferences —");
            snapshot.activityPreferences().forEach((key, value) ->
                    bodyLines.add(key + "=" + value));
        }
        if (!snapshot.environmentPreferences().isEmpty()) {
            bodyLines.add("");
            bodyLines.add("— Environment preferences —");
            snapshot.environmentPreferences().forEach((key, value) ->
                    bodyLines.add(key + "=" + value));
        }
        bodyLines.add("");
        bodyLines.add("— Recent decisions —");
        for (OpinionReadoutDecisionView decision : snapshot.recentDecisions()) {
            bodyLines.add("#" + decision.decisionId()
                    + " " + decision.disposition()
                    + (decision.counterfactualOnly() ? " (non-causal)" : ""));
            bodyLines.add("  " + decision.explanation());
            decision.candidateLines().forEach(line -> bodyLines.add("  cand: " + line));
            decision.transitionLines().forEach(line -> bodyLines.add("  tx: " + line));
        }
    }

    /**
     * Read-only {@link MultiLineEditBox} — supports click/drag selection and Ctrl+C, but blocks edits.
     */
    private static final class SelectableReadoutBox extends MultiLineEditBox {

        SelectableReadoutBox(
                net.minecraft.client.gui.Font font,
                int x,
                int y,
                int width,
                int height,
                String value) {
            super(font, x, y, width, height, Component.empty(), Component.empty());
            setValue(value);
        }

        @Override
        public boolean charTyped(char codePoint, int modifiers) {
            return false;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (Screen.hasControlDown()) {
                if (keyCode == GLFW.GLFW_KEY_V || keyCode == GLFW.GLFW_KEY_X) {
                    return false;
                }
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
                return false;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }
}
