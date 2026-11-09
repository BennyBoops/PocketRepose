package net.bennyboops.modid.client;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
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

    // NeoForge only draws a block entity while this box is in view. A pocket door's doorway (drawn from its lower
    // half) is two blocks tall, so the box covers the block above too
    @Override
    public Box getRenderBoundingBox(SuitcaseBlockEntity blockEntity) {
        BlockPos pos = blockEntity.getPos();
        return new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1);
    }
}
