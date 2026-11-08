package net.bennyboops.modid.item;

import net.bennyboops.modid.client.SecretChestRenderer;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/** The Secret Chest's item, drawn with the vanilla chest model (see SecretChestRenderer). */
public class SecretChestItem extends BlockItem {
    public SecretChestItem(Block block, Settings settings) {
        super(block, settings);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BuiltinModelItemRenderer renderer;

            @Override
            public BuiltinModelItemRenderer getCustomRenderer() {
                if (renderer == null) {
                    MinecraftClient client = MinecraftClient.getInstance();
                    renderer = new BuiltinModelItemRenderer(client.getBlockEntityRenderDispatcher(), client.getEntityModelLoader()) {
                        @Override
                        public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
                                           VertexConsumerProvider vertexConsumers, int light, int overlay) {
                            SecretChestRenderer.renderItem(stack, mode, matrices, vertexConsumers, light, overlay);
                        }
                    };
                }
                return renderer;
            }
        });
    }
}
