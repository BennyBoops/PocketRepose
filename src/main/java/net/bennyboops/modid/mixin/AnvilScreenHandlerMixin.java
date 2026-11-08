package net.bennyboops.modid.mixin;

import net.bennyboops.modid.item.KeystoneItem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.Property;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilScreenHandler.class)
public abstract class AnvilScreenHandlerMixin {

    @Shadow @Final private Property levelCost;

    @Inject(method = "updateResult", at = @At("TAIL"))
    private void pocketRepose$clearKeystoneEnchants(CallbackInfo ci) {
        // Slot 0 is the item being worked on, slot 2 is the output slot on every anvil UI
        ItemStack input      = ((AnvilScreenHandler)(Object) this).getSlot(0).getStack();
        Slot      outputSlot = ((AnvilScreenHandler)(Object) this).getSlot(2);
        ItemStack result     = outputSlot.getStack();

        // A bound key can't be renamed (vanilla would allow a rename-only job for 39 levels), and a
        // name with no letters or numbers can't be bound, so neither gets an output
        boolean renamingBoundKey = KeystoneItem.isBound(input);
        boolean unbindableName = result.getItem() instanceof KeystoneItem
                && result.contains(DataComponentTypes.CUSTOM_NAME)
                && KeystoneItem.sanitizedName(result).isEmpty();
        if (renamingBoundKey || unbindableName) {
            outputSlot.setStack(ItemStack.EMPTY);
            levelCost.set(0);
            return;
        }

        if (result.getItem() instanceof KeystoneItem) {
            EnchantmentHelper.set(result, ItemEnchantmentsComponent.DEFAULT);
        }
    }

    // A renamed key's dimension is created as soon as it's taken out of the anvil. The key is left
    // as it is while on the cursor and gets marked as bound once it's in an inventory slot.
    @Inject(method = "onTakeOutput", at = @At("TAIL"))
    private void pocketRepose$bindTakenKeystone(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (!player.getWorld().isClient && !stack.isEmpty() && stack.getItem() instanceof KeystoneItem) {
            KeystoneItem.openDimension(player.getServer(), player, stack, true);
        }
    }
}
