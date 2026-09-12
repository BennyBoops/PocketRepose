package net.bennyboops.modid.mixin;

import net.bennyboops.modid.item.KeystoneItem;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {

    @Inject(method = "createResult", at = @At("TAIL"))
    private void pocketRepose$clearKeystoneEnchants(CallbackInfo ci) {
        // Slot 2 is the output slot on every anvil UI
        Slot outputSlot = ((AnvilMenu) (Object) this).getSlot(2);
        ItemStack result = outputSlot.getItem();

        if (result.getItem() instanceof KeystoneItem) {
            EnchantmentHelper.setEnchantments(result, ItemEnchantments.EMPTY);
        }
    }
}
