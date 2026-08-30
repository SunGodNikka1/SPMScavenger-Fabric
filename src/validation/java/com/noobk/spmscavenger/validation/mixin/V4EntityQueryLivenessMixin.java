package com.noobk.spmscavenger.validation.mixin;

import com.noobk.spmscavenger.validation.V4TradeLivenessWitness;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observes the list returned by the production query; never performs a second query. */
@Mixin(EntityGetter.class)
public interface V4EntityQueryLivenessMixin {

    @Inject(method = "getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;", at = @At("RETURN"))
    private <T extends Entity> void spmscavenger_validation$queryResult(
            Class<T> entityClass, AABB box, Predicate<? super T> predicate,
            CallbackInfoReturnable<List<T>> cir) {
        if (entityClass == Villager.class && V4TradeLivenessWitness.inTradeInvocation()) {
            @SuppressWarnings("unchecked")
            List<? extends Villager> villagers = (List<? extends Villager>) cir.getReturnValue();
            V4TradeLivenessWitness.observeVillagerQuery(villagers, -1L);
        }
    }
}
