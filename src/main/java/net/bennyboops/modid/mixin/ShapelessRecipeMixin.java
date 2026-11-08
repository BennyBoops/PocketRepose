package net.bennyboops.modid.mixin;

import net.bennyboops.modid.block.SuitcaseBlock;
import net.bennyboops.modid.util.StackData;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.ShapelessRecipe;
import net.minecraft.registry.DynamicRegistryManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShapelessRecipe.class)
public abstract class ShapelessRecipeMixin {

    // Re-dyeing a suitcase (or turning it into a secret barrel) keeps its binding, travelers and name
    @Inject(method = "craft(Lnet/minecraft/inventory/RecipeInputInventory;Lnet/minecraft/registry/DynamicRegistryManager;)Lnet/minecraft/item/ItemStack;",
            at = @At("RETURN"), cancellable = true)
    private void pocketRepose$keepSuitcaseBinding(RecipeInputInventory input, DynamicRegistryManager registryManager,
                                                  CallbackInfoReturnable<ItemStack> cir) {
        ItemStack result = cir.getReturnValue();
        if (!pocketRepose$isSuitcase(result)) return;

        for (ItemStack ingredient : input.getInputStacks()) {
            if (!pocketRepose$isSuitcase(ingredient)) continue;

            ItemStack copy = result.copy();
            NbtCompound beData = StackData.getBlockEntityData(ingredient);
            if (beData != null) StackData.setBlockEntityData(copy, beData);
            NbtCompound display = ingredient.getSubNbt(ItemStack.DISPLAY_KEY);
            if (display != null) copy.setSubNbt(ItemStack.DISPLAY_KEY, display.copy());
            cir.setReturnValue(copy);
            return;
        }
    }

    @Unique
    private static boolean pocketRepose$isSuitcase(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && SuitcaseBlock.isPocketBlock(blockItem.getBlock());
    }
}
