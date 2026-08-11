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
                new Matrix4f(), projection, 200, 200, 2.0, 10.0F, 20.0F);

        assertEquals(10.0F, result.x(), 0.001F);
        assertEquals(20.0F, result.y(), 0.001F);
        assertEquals(1.0F, result.scale(), 0.001F);
    }

    @Test
    void invalidFramebufferOrGuiScaleCannotCreateAnOverlay() {
        Matrix4f identity = new Matrix4f();
        assertNull(ShaderReadoutOverlay.project(identity, identity, 0, 200, 2.0, 0.0F, 0.0F));
        assertNull(ShaderReadoutOverlay.project(identity, identity, 200, 200, 0.0, 0.0F, 0.0F));
    }
}
