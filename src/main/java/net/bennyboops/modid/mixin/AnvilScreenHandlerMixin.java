package net.bennyboops.modid.mixin;

import net.bennyboops.modid.item.KeystoneItem;
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
public class AnvilScreenHandlerMixin {
    @Inject(method = "updateResult", at = @At("TAIL"))
    private void onAnvilRenameRemoveEnchants(CallbackInfo ci) {
        // slot 2 is the output
        Slot outputSlot = ((AnvilScreenHandler)(Object)this).slots.get(2);
        ItemStack result = outputSlot.getStack();

        // if it's our KeystoneItem with a new custom name, strip enchantments
        if (result.getItem() instanceof KeystoneItem) {

            // remove all enchantments
            EnchantmentHelper.set(Collections.emptyMap(), result);

        }
    }
}
