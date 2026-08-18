package net.bennyboops.modid.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CustomSuitcaseBlock extends SuitcaseBlock {
    public static final MapCodec<CustomSuitcaseBlock> CODEC = simpleCodec(CustomSuitcaseBlock::new);

    protected final VoxelShape customShapeN;
    protected final VoxelShape customShapeS;
    protected final VoxelShape customShapeE;
    protected final VoxelShape customShapeW;

    protected final SoundEvent openSound;
    protected final SoundEvent closeSound;

    public CustomSuitcaseBlock(Properties settings, VoxelShape shape, SoundEvent openSound, SoundEvent closeSound) {
        super(settings);
        this.customShapeN = shape;
        this.customShapeS = shape;
        this.customShapeE = shape;
        this.customShapeW = shape;
        this.openSound = openSound;
        this.closeSound = closeSound;
    }

    // Constructor for codec (just uses default shape and sounds)
    public CustomSuitcaseBlock(Properties settings) {
        this(settings,
                Block.box(0, 0, 0, 16, 15, 16),
                SoundEvents.BARREL_OPEN,
                SoundEvents.BARREL_CLOSE);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> customShapeN;
            case SOUTH -> customShapeS;
            case EAST -> customShapeE;
            case WEST -> customShapeW;
            default -> customShapeN;
        };
    }
}
