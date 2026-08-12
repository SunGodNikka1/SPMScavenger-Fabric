package com.noobk.spmscavenger.client.opinion;

import com.noobk.spmscavenger.opinion.readout.OpinionReadoutDecisionView;
import com.noobk.spmscavenger.opinion.readout.OpinionReadoutSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** GAO-8B Task 42B — addon-owned read-only inspector (PD-GAO-15). */
public final class OpinionInspectorScreen extends Screen {

    private static final int LINE_HEIGHT = 10;
    private static final int PADDING = 8;

    private OpinionReadoutSnapshot snapshot;
    private final List<String> bodyLines = new ArrayList<>();
    private int scrollOffset;

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
    }

    @Override
    protected void init() {
        int buttonWidth = 80;
        int y = this.height - 24;
        addRenderableWidget(Button.builder(Component.literal("Refresh"), button ->
                OpinionInspectClient.refreshFromScreen(this))
                .bounds(this.width / 2 - buttonWidth - 4, y, buttonWidth, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(this.width / 2 + 4, y, buttonWidth, 20)
                .build());
    }

    @Override
    public void onClose() {
        OpinionInspectClient.clearIfClosed(this);
        super.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 6, 0xFFFFFF);
        int y = PADDING + 14;
        int maxY = this.height - 36;
        int index = 0;
        for (String line : bodyLines) {
            if (index++ < scrollOffset) {
                continue;
            }
            if (y > maxY) {
                break;
            }
            graphics.drawString(this.font, line, PADDING, y, 0xE0E0E0);
            y += LINE_HEIGHT;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset = Math.max(0, scrollOffset - (int) scrollY);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void rebuildBody() {
        bodyLines.clear();
        scrollOffset = 0;
        bodyLines.add("Status: " + snapshot.status());
        bodyLines.addAll(snapshot.summaryLines());
        bodyLines.add("");
        bodyLines.add("— Affect —");
        bodyLines.add("engagement=" + snapshot.engagement()
                + " boredom=" + snapshot.boredom()
                + " satisfaction=" + snapshot.satisfaction());
        bodyLines.add("stress=" + snapshot.stress()
                + " novelty=" + snapshot.novelty()
                + " frozen=" + snapshot.frozen());
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
        bodyLines.add("resting=" + snapshot.resting()
                + " disposition=" + snapshot.currentDisposition()
                + " restPhase=" + snapshot.restAuthorityPhase());
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
}
