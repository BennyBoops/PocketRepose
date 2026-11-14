package net.bennyboops.modid.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
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

    // The recipe book's ghost recipe (shown faded in the crafting grid) draws text-only tooltips; include the item's
    // tooltip picture too, like a normal slot does (a key's "Rename to bind" and preview are in that picture)
    @WrapOperation(method = "drawGhostSlotTooltip",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTooltip(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;II)V"))
    private void pocketRepose$ghostTooltipData(DrawContext context, TextRenderer textRenderer, List<Text> lines, int x, int y,
                                               Operation<Void> original, @Local(ordinal = 0) ItemStack stack) {
        context.drawTooltip(textRenderer, lines, stack.getTooltipData(), x, y);
    }
}
