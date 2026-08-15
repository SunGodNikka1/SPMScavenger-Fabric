package com.noobk.spmscavenger;

import com.noobk.spmscavenger.mixin.FurnaceRecipeCheckAccessor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/**
 * FS-R1 — does <b>this</b> station cook <b>this</b> input?
 *
 * <h2>The defect this closes (`RUNTIME_CONFIRMED`)</h2>
 *
 * A PlayerMob was observed standing at a blast furnace with an oak log in the input slot and
 * nothing happening, forever. The two halves that produced it were each locally reasonable:
 *
 * <pre>
 * FurnacePolicy   plans against RecipeType.SMELTING          (log -> charcoal)
 * FurnaceStations accepts FURNACE | BLAST_FURNACE | SMOKER   ("a furnace-like block")
 * SmeltAtFurnaceGoal checks `be instanceof AbstractFurnaceBlockEntity` before inserting
 * </pre>
 *
 * All three agree the blast furnace is a furnace. None of them asks whether it can cook a log — and
 * it cannot: blasting recipes are ores and metals. So the job inserted its input and fuel into a
 * machine that would never consume them, stranding both and blocking the ticket.
 *
 * <p>{@code AbstractFurnaceBlockEntity} is precisely the wrong granularity for this question: it is
 * the common supertype of all three machines, so the {@code instanceof} check was guaranteed to pass
 * for exactly the case that fails.
 *
 * <h2>Fail closed</h2>
 *
 * When the station cannot be interrogated the answer is <b>no</b>. Refusing a usable furnace costs
 * one smelting job; accepting an unusable one strands the mob's input and fuel inside a block it
 * will then have to be told to abandon.
 */
public final class FurnaceCapability {

    private FurnaceCapability() {
    }

    /**
     * @return {@code true} only when the station's own recipe check resolves a recipe for this input
     */
    public static boolean canCook(AbstractFurnaceBlockEntity furnace, Level level, ItemStack input) {
        if (furnace == null || level == null || input == null || input.isEmpty()) {
            return false;
        }
        if (!(furnace instanceof FurnaceRecipeCheckAccessor accessor)) {
            // The accessor did not apply — refuse rather than guess. See class note.
            return false;
        }
        try {
            return accessor.spmscavenger$quickCheck()
                    .getRecipeFor(new SingleRecipeInput(input), level)
                    .isPresent();
        } catch (RuntimeException stationRefusedToAnswer) {
            return false;
        }
    }
}
