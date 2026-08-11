package com.noobk.spmscavenger.mixin;

import com.noobk.spmscavenger.client.DecisionReadoutContrast;
import com.noobk.spmscavenger.client.IrisShaderState;
import com.noobk.spmscavenger.client.ShaderReadoutOverlay;
import net.minecraft.client.gui.Font;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Optional client compatibility patch for SPM 0.86.x's Creative objective billboard.
 *
 * <p>{@link Pseudo} and {@code require = 0} are deliberate: Social Player Mobs is recommended, not
 * required. Missing SPM or a future renderer signature must disable this cosmetic repair rather
 * than prevent Minecraft from starting.
 */
@Pseudo
@Mixin(targets = "games.brennan.playermob.client.PlayerMobRenderer")
public abstract class PlayerMobRendererReadoutMixin {

    @ModifyArgs(
            method = "renderObjectiveReadout",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;drawInBatch(Ljava/lang/String;FFIZ"
                            + "Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;"
                            + "Lnet/minecraft/client/gui/Font$DisplayMode;II)I"),
            require = 0)
    private void spmscavenger$brightenDecisionReadout(Args args) {
        Font.DisplayMode mode = args.get(7);
        boolean seeThrough = mode == Font.DisplayMode.SEE_THROUGH;
        IrisShaderState.Snapshot shader = ShaderReadoutOverlay.shaderState();
        if (shader.shaderPackInUse()) {
            if (!seeThrough && !shader.shadowPass()) {
                ShaderReadoutOverlay.capture(
                        args.get(0), args.get(1), args.get(2), args.get(3), (Matrix4f) args.get(5));
            }
            // Photon directionally lights world text. Suppress both host passes only while its
            // pipeline is active; the captured line is redrawn after world post-processing.
            args.set(3, 0x00000000);
            args.set(8, 0x00000000);
            return;
        }
        args.set(3, DecisionReadoutContrast.textColor(args.get(3), seeThrough));
        args.set(8, DecisionReadoutContrast.backgroundColor(args.get(8), seeThrough));
        args.set(9, DecisionReadoutContrast.packedLight());
    }
}
