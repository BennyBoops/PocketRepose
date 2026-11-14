package net.bennyboops.modid.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(RecipeBookWidget.class)
public abstract class RecipeBookWidgetMixin {

    // The recipe book's ghost recipe (shown faded in the crafting grid) draws its tooltip without the item's tooltip
    // picture; include it, like a normal slot does (a key's "Rename to bind" and preview are in that picture)
    @SuppressWarnings("unchecked")
    @WrapOperation(method = "drawGhostSlotTooltip",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;renderComponentTooltip(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;IILnet/minecraft/item/ItemStack;)V"))
    private void pocketRepose$ghostTooltipData(DrawContext context, TextRenderer textRenderer, List<?> lines, int x, int y,
                                               ItemStack stack, Operation<Void> original) {
        if (stack.getTooltipData().isEmpty()) {
            original.call(context, textRenderer, lines, x, y, stack);
            return;
        }
        context.drawTooltip(textRenderer, (List<Text>) lines, stack.getTooltipData(), x, y);
    }
}
