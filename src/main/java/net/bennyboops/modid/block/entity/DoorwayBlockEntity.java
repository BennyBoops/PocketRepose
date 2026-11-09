package net.bennyboops.modid.block.entity;

import net.minecraft.util.math.Box;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

/**
 * Sits in the lower half of an exit portal door. It holds nothing; it's there so the doorway can be drawn as a
 * hallway (pocket doors draw theirs from the suitcase block entity they already have).
 */
public class DoorwayBlockEntity extends BlockEntity {
    public DoorwayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DOORWAY_BLOCK_ENTITY, pos, state);
    }

    // Forge only draws a block entity while this box is in view. A pocket door's doorway (drawn from its lower
    // half) is two blocks tall, so the box covers the block above too
    @Override
    public Box getRenderBoundingBox() {
        return new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1);
    }
}
