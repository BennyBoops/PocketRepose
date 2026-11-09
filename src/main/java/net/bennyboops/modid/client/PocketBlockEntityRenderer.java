package net.bennyboops.modid.client;

import net.bennyboops.modid.block.entity.SuitcaseBlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;

/** Every pocket block shares one block entity type: this draws the Secret Chest and every pocket block's opening. */
public class PocketBlockEntityRenderer implements BlockEntityRenderer<SuitcaseBlockEntity> {
    private final SecretChestRenderer secretChest;

    public PocketBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
        this.secretChest = new SecretChestRenderer(ctx);
    }

    @Override
    public void render(SuitcaseBlockEntity suitcase, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        secretChest.render(suitcase, tickDelta, matrices, vertexConsumers, light, overlay);
        HallwayRenderer.render(suitcase, tickDelta, matrices, vertexConsumers, light);
    }

    // A doorway is two blocks tall but its block entity is in the lower half, so don't cull it by that block alone
    @Override
    public boolean rendersOutsideBoundingBox(SuitcaseBlockEntity suitcase) {
        return HallwayRenderer.tall(suitcase.getCachedState());
    }
}
