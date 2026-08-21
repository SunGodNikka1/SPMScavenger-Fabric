package com.noobk.spmscavenger.mixin;

import com.noobk.spmscavenger.village.storage.StorageGrantLifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gate 0-A — central block-state lifecycle seam for storage grant invalidation.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelStorageGrantLifecycleMixin {

    @Inject(method = "onBlockStateChange", at = @At("HEAD"))
    private void spmscavenger$storageGrantLifecycle(
            BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        StorageGrantLifecycle.onBlockStateChange((ServerLevel) (Object) this, pos, oldState, newState);
    }
}
