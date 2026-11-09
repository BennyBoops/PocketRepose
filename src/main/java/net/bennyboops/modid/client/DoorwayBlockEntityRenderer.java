package net.bennyboops.modid.client;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.bennyboops.modid.block.entity.DoorwayBlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;

/** Draws an exit portal door's open doorway as a hallway. */
public class DoorwayBlockEntityRenderer implements BlockEntityRenderer<DoorwayBlockEntity> {
    public DoorwayBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
    }

    @Override
    public void render(DoorwayBlockEntity doorway, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        HallwayRenderer.render(doorway, tickDelta, matrices, vertexConsumers, light);
    }

    // The doorway is two blocks tall but its block entity is in the lower half
    @Override
    public boolean rendersOutsideBoundingBox(DoorwayBlockEntity doorway) {
        return true;
    }

    // NeoForge only draws a block entity while this box is in view. A pocket door's doorway (drawn from its lower
    // half) is two blocks tall, so the box covers the block above too
    @Override
    public Box getRenderBoundingBox(DoorwayBlockEntity blockEntity) {
        BlockPos pos = blockEntity.getPos();
        return new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1);
    }
}
