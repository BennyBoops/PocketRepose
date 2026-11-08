package net.bennyboops.modid.client;

import net.bennyboops.modid.block.SecretChestBlock;
import net.bennyboops.modid.block.SuitcaseBlock;
import net.bennyboops.modid.block.entity.SuitcaseBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;

/** Draws the Secret Chest with the vanilla chest model and texture, lid included. */
public class SecretChestRenderer implements BlockEntityRenderer<SuitcaseBlockEntity> {
    private static SecretChestRenderer itemRenderer;

    private final ModelPart base;
    private final ModelPart lid;
    private final ModelPart latch;

    public SecretChestRenderer(BlockEntityRendererFactory.Context ctx) {
        this(ctx.getLayerModelPart(EntityModelLayers.CHEST));
    }

    private SecretChestRenderer(ModelPart chest) {
        this.base = chest.getChild("bottom");
        this.lid = chest.getChild("lid");
        this.latch = chest.getChild("lock");
    }

    @Override
    public void render(SuitcaseBlockEntity suitcase, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        // Every suitcase shares this block entity type; only the Secret Chest is drawn here
        BlockState state = suitcase.getCachedState();
        if (!(state.getBlock() instanceof SecretChestBlock)) return;

        render(matrices, vertexConsumers, state.get(SuitcaseBlock.FACING), suitcase.getLidProgress(tickDelta), light, overlay);
    }

    /** The Secret Chest item, drawn like the vanilla chest item (closed, facing south). */
    public static void renderItem(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
                                  VertexConsumerProvider vertexConsumers, int light, int overlay) {
        if (itemRenderer == null) {
            itemRenderer = new SecretChestRenderer(
                    MinecraftClient.getInstance().getEntityModelLoader().getModelPart(EntityModelLayers.CHEST));
        }
        itemRenderer.render(matrices, vertexConsumers, Direction.SOUTH, 0f, light, overlay);
    }

    private void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Direction facing,
                        float openProgress, int light, int overlay) {
        matrices.push();
        matrices.translate(0.5F, 0.5F, 0.5F);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facing.asRotation()));
        matrices.translate(-0.5F, -0.5F, -0.5F);

        // Same easing as the vanilla chest lid
        float closed = 1.0F - openProgress;
        float eased = 1.0F - closed * closed * closed;
        lid.pitch = -(eased * (float) (Math.PI / 2));
        latch.pitch = lid.pitch;

        VertexConsumer vertices = TexturedRenderLayers.NORMAL.getVertexConsumer(vertexConsumers, RenderLayer::getEntityCutout);
        lid.render(matrices, vertices, light, overlay);
        latch.render(matrices, vertices, light, overlay);
        base.render(matrices, vertices, light, overlay);
        matrices.pop();
    }
}
