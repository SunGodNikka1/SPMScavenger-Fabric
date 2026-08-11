package com.noobk.spmscavenger.client;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ShaderReadoutOverlayTest {

    @Test
    void orthographicWorldPointProjectsIntoScaledGuiCoordinates() {
        Matrix4f projection = new Matrix4f().ortho(0.0F, 100.0F, 100.0F, 0.0F, -1.0F, 1.0F);

        ShaderReadoutOverlay.Projection result = ShaderReadoutOverlay.project(
                new Matrix4f(), new Matrix4f(), projection, 200, 200, 2.0, 10.0F, 20.0F);

        assertEquals(10.0F, result.x(), 0.001F);
        assertEquals(20.0F, result.y(), 0.001F);
        assertEquals(1.0F, result.scale(), 0.001F);
    }

    @Test
    void invalidFramebufferOrGuiScaleCannotCreateAnOverlay() {
        Matrix4f identity = new Matrix4f();
        assertNull(ShaderReadoutOverlay.project(
                identity, identity, identity, 0, 200, 2.0, 0.0F, 0.0F));
        assertNull(ShaderReadoutOverlay.project(
                identity, identity, identity, 200, 200, 0.0, 0.0F, 0.0F));
    }

    @Test
    void worldProjectionIsAppliedAfterTheEntityBillboardTransform() {
        Matrix4f projection = new Matrix4f().ortho(0.0F, 100.0F, 100.0F, 0.0F, -1.0F, 1.0F);
        Matrix4f billboard = new Matrix4f()
                .translate(30.0F, 40.0F, 0.0F)
                .scale(0.5F);
        Matrix4f position = new Matrix4f().translate(10.0F, -20.0F, 0.0F);

        ShaderReadoutOverlay.Projection result = ShaderReadoutOverlay.project(
                billboard, position, projection, 200, 200, 2.0, -10.0F, 20.0F);

        assertEquals(35.0F, result.x(), 0.001F);
        assertEquals(30.0F, result.y(), 0.001F);
        assertEquals(0.5F, result.scale(), 0.001F);
    }

    @Test
    void cameraRotationAndBillboardRotationCancelLikeTheWorldFontPipeline() {
        Matrix4f projection = new Matrix4f().ortho(
                -100.0F, 100.0F, -100.0F, 100.0F, -100.0F, 100.0F);
        Matrix4f position = new Matrix4f().rotateY((float) (Math.PI / 2.0));
        Matrix4f billboard = new Matrix4f()
                .translate(10.0F, 0.0F, 0.0F)
                .rotateY((float) (-Math.PI / 2.0));

        ShaderReadoutOverlay.Projection result = ShaderReadoutOverlay.project(
                billboard, position, projection, 200, 200, 2.0, 0.0F, 0.0F);

        assertEquals(50.0F, result.x(), 0.001F);
        assertEquals(50.0F, result.y(), 0.001F);
        assertEquals(0.5F, result.scale(), 0.001F);
    }

    @Test
    void terrainOcclusionUsesTheHostsFaintSeeThroughAlphaInsteadOfFullHudBrightness() {
        assertEquals(0x20E6E6E6,
                ShaderReadoutOverlay.colorForOcclusion(0xFFE6E6E6, true));
        assertEquals(0xFFE6E6E6,
                ShaderReadoutOverlay.colorForOcclusion(0xFFE6E6E6, false));
        assertEquals(0x20FFFFFF,
                ShaderReadoutOverlay.colorForOcclusion(0x20FFFFFF, true));
    }
}
