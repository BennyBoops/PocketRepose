package net.bennyboops.modid.mixin;

import net.bennyboops.modid.item.KeystoneItem;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;

@Mixin(AnvilScreenHandler.class)
public abstract class AnvilScreenHandlerMixin {

    @Inject(method = "updateResult", at = @At("TAIL"))
    private void pocketRepose$clearKeystoneEnchants(CallbackInfo ci) {
        // Slot 2 is the output slot on every anvil UI
        Slot      outputSlot = ((AnvilScreenHandler)(Object) this).getSlot(2);
        ItemStack result     = outputSlot.getStack();

        if (result.getItem() instanceof KeystoneItem) {
            EnchantmentHelper.set(result, ItemEnchantmentsComponent.DEFAULT);
        }
    }
}
