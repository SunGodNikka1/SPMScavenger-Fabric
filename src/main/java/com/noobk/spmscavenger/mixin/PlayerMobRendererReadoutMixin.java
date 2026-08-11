package com.noobk.spmscavenger.mixin;

import com.noobk.spmscavenger.client.DecisionReadoutContrast;
import net.minecraft.client.gui.Font;
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
        args.set(3, DecisionReadoutContrast.textColor(args.get(3), seeThrough));
        args.set(8, DecisionReadoutContrast.backgroundColor(args.get(8), seeThrough));
        args.set(9, DecisionReadoutContrast.packedLight());
    }
}
