package com.noobk.spmscavenger.mixin;

import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads a furnace's own recipe check, so "can this station cook this?" is answered by the station.
 *
 * <h2>Why an accessor rather than a class map</h2>
 *
 * The obvious fix for the blast-furnace/charcoal mismatch is a three-way map:
 * {@code FurnaceBlockEntity → SMELTING}, {@code BlastFurnaceBlockEntity → BLASTING},
 * {@code SmokerBlockEntity → SMOKING}. That is an enumeration of vanilla's own binding, and it is
 * wrong for every modded furnace — which would either be refused (capability lost) or accepted and
 * then strand its input (the defect we are fixing, with extra steps).
 *
 * <p>In 1.21.1 {@code AbstractFurnaceBlockEntity} has no {@code recipeType} field. The type is a
 * constructor parameter captured inside {@code quickCheck}, a
 * {@code RecipeManager.CachedCheck} bound to exactly that type — so reading {@code quickCheck} and
 * asking it for a recipe <b>is</b> asking the station what it can cook, whatever subclass it is and
 * whatever recipe type a mod gave it.
 *
 * <p>Accessor only: nothing is injected, no behaviour changes, and if the field is ever renamed the
 * consumer degrades to refusing every station (fail closed — no item is stranded).
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public interface FurnaceRecipeCheckAccessor {

    @Accessor("quickCheck")
    RecipeManager.CachedCheck<SingleRecipeInput, ? extends AbstractCookingRecipe>
            spmscavenger$quickCheck();
}
