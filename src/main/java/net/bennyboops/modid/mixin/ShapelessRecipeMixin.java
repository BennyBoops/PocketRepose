package net.bennyboops.modid.mixin;

import net.bennyboops.modid.block.SuitcaseBlock;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.ShapelessRecipe;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShapelessRecipe.class)
public abstract class ShapelessRecipeMixin {

    // Re-dyeing a suitcase (or turning it into a secret barrel) keeps its binding, travelers and name
    @Inject(method = "craft(Lnet/minecraft/recipe/input/CraftingRecipeInput;Lnet/minecraft/registry/RegistryWrapper$WrapperLookup;)Lnet/minecraft/item/ItemStack;",
            at = @At("RETURN"), cancellable = true)
    private void pocketRepose$keepSuitcaseBinding(CraftingRecipeInput input, RegistryWrapper.WrapperLookup lookup,
                                                  CallbackInfoReturnable<ItemStack> cir) {
        ItemStack result = cir.getReturnValue();
        if (!pocketRepose$isSuitcase(result)) return;

        for (ItemStack ingredient : input.getStacks()) {
            if (!pocketRepose$isSuitcase(ingredient)) continue;

            ItemStack copy = result.copy();
            pocketRepose$copy(DataComponentTypes.BLOCK_ENTITY_DATA, ingredient, copy);
            pocketRepose$copy(DataComponentTypes.LORE, ingredient, copy);
            pocketRepose$copy(DataComponentTypes.CUSTOM_NAME, ingredient, copy);
            cir.setReturnValue(copy);
            return;
        }
    }

    @Unique
    private static boolean pocketRepose$isSuitcase(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && SuitcaseBlock.isPocketBlock(blockItem.getBlock());
    }

    @Unique
    private static <T> void pocketRepose$copy(ComponentType<T> type, ItemStack from, ItemStack to) {
        T value = from.get(type);
        if (value != null) to.set(type, value);
    }
}
