package net.bennyboops.modid.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public class CustomSuitcaseBlock extends SuitcaseBlock {
    public static final MapCodec<CustomSuitcaseBlock> CODEC = createCodec(CustomSuitcaseBlock::new);

    protected final VoxelShape customShapeN;
    protected final VoxelShape customShapeS;
    protected final VoxelShape customShapeE;
    protected final VoxelShape customShapeW;

    protected final SoundEvent openSound;
    protected final SoundEvent closeSound;

    public CustomSuitcaseBlock(Settings settings, VoxelShape shape, SoundEvent openSound, SoundEvent closeSound) {
        super(settings);
        this.customShapeN = shape;
        this.customShapeS = shape;
        this.customShapeE = shape;
        this.customShapeW = shape;
        this.openSound = openSound;
        this.closeSound = closeSound;
    }

    // Constructor for codec (just uses default shape and sounds)
    public CustomSuitcaseBlock(Settings settings) {
        this(settings,
                createCuboidShape(0, 0, 0, 16, 15, 16),
                net.minecraft.sound.SoundEvents.BLOCK_BARREL_OPEN,
                net.minecraft.sound.SoundEvents.BLOCK_BARREL_CLOSE);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return switch (state.get(FACING)) {
            case NORTH -> customShapeN;
            case SOUTH -> customShapeS;
            case EAST -> customShapeE;
            case WEST -> customShapeW;
            default -> customShapeN;
        };
    }
}