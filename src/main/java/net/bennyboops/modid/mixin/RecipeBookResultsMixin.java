package net.bennyboops.modid.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.recipebook.AnimatedResultButton;
import net.minecraft.client.gui.screen.recipebook.RecipeBookResults;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(RecipeBookResults.class)
public abstract class RecipeBookResultsMixin {
    @Shadow private AnimatedResultButton hoveredResultButton;

    // The recipe book's result buttons draw text-only tooltips; include the result's tooltip picture too, like a
    // normal slot does (a key's "Rename to bind" and preview are in that picture)
    @WrapOperation(method = "drawTooltip",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTooltip(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;II)V"))
    private void pocketRepose$resultTooltipData(DrawContext context, TextRenderer textRenderer, List<Text> lines, int x, int y,
                                                Operation<Void> original) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (hoveredResultButton == null || client.world == null) {
            original.call(context, textRenderer, lines, x, y);
            return;
        }
        ItemStack result = hoveredResultButton.currentRecipe().value().getResult(client.world.getRegistryManager());
        context.drawTooltip(textRenderer, lines, result.getTooltipData(), x, y);
    }
}
