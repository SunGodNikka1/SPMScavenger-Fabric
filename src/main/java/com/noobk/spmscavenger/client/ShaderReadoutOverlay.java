package com.noobk.spmscavenger.client;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Post-shader fallback for SPM's world-space objective billboard.
 *
 * <p>Photon lights the normal world-text render type directionally even when its packed light is
 * full-bright. Capturing the host-formatted glyph origins and drawing them in the HUD phase keeps
 * the objective legible without patching Photon, Iris, SPM text ownership, or AI behavior.
 */
public final class ShaderReadoutOverlay {

    /** Hard cap for adversarial/full-stack readouts; production eviction occurs every frame. */
    static final int MAX_CAPTURED_LINES = 512;
    private static final List<CapturedLine> LINES = new ArrayList<>();

    private ShaderReadoutOverlay() {
    }

    /** Called from the world-render START event, before any entity billboard can be captured. */
    public static void beginFrame() {
        LINES.clear();
    }

    public static IrisShaderState.Snapshot shaderState() {
        return IrisShaderState.snapshot();
    }

    /** Capture only the solid pass; the see-through pass carries the same line and transform. */
    public static void capture(String text, float x, float y, int color, Matrix4f modelMatrix) {
        if (LINES.size() >= MAX_CAPTURED_LINES || text == null || text.isEmpty()) return;

        Minecraft minecraft = Minecraft.getInstance();
        Window window = minecraft.getWindow();
        Projection projection = project(
                modelMatrix,
                RenderSystem.getProjectionMatrix(),
                window.getWidth(),
                window.getHeight(),
                window.getGuiScale(),
                x,
                y);
        if (projection != null) {
            LINES.add(new CapturedLine(text, color, projection));
        }
    }

    /** Runs through Fabric's HUD callback, after Iris/Photon has completed world post-processing. */
    public static void render(GuiGraphics graphics) {
        try {
            if (!IrisShaderState.snapshot().shaderPackInUse()) return;

            Minecraft minecraft = Minecraft.getInstance();
            for (CapturedLine line : LINES) {
                Projection p = line.projection();
                if (p.scale() <= 0.01F || p.scale() > 8.0F) continue;
                graphics.pose().pushPose();
                graphics.pose().translate(p.x(), p.y(), 0.0F);
                graphics.pose().scale(p.scale(), p.scale(), 1.0F);
                graphics.drawString(minecraft.font, line.text(), 0, 0, line.color(), true);
                graphics.pose().popPose();
            }
        } finally {
            // Also evict after consumption so a frame with no subsequent world render cannot ghost.
            LINES.clear();
        }
    }

    /** Pure projection seam used by regression tests. */
    static Projection project(
            Matrix4f modelMatrix,
            Matrix4f projectionMatrix,
            int framebufferWidth,
            int framebufferHeight,
            double guiScale,
            float x,
            float y) {
        if (framebufferWidth <= 0 || framebufferHeight <= 0 || guiScale <= 0.0) return null;

        Matrix4f mvp = new Matrix4f(projectionMatrix).mul(modelMatrix);
        int[] viewport = {0, 0, framebufferWidth, framebufferHeight};
        Vector3f origin = mvp.project(x, y, 0.0F, viewport, new Vector3f());
        Vector3f xUnit = mvp.project(x + 1.0F, y, 0.0F, viewport, new Vector3f());
        if (!Float.isFinite(origin.x()) || !Float.isFinite(origin.y())
                || !Float.isFinite(origin.z()) || origin.z() < 0.0F || origin.z() > 1.0F) {
            return null;
        }

        float scale = Math.abs(xUnit.x() - origin.x()) / (float) guiScale;
        return new Projection(
                origin.x() / (float) guiScale,
                (framebufferHeight - origin.y()) / (float) guiScale,
                scale);
    }

    record Projection(float x, float y, float scale) {
    }

    private record CapturedLine(String text, int color, Projection projection) {
    }
}
