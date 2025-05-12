package net.bennyboops.modid.block;

import net.bennyboops.modid.block.entity.SuitcaseBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.block.entity.BlockEntity;

public class CustomSuitcaseBlock extends SuitcaseBlock {
    protected final VoxelShape customShapeN;
    protected final VoxelShape customShapeS;
    protected final VoxelShape customShapeE;
    protected final VoxelShape customShapeW;

    protected final SoundEvent openSound;
    protected final SoundEvent closeSound;

    public CustomSuitcaseBlock(Settings settings,
                               VoxelShape shape,
                               SoundEvent openSound,
                               SoundEvent closeSound) {
        super(settings);
        this.customShapeN = shape;
        this.customShapeS = shape;
        this.customShapeE = shape;
        this.customShapeW = shape;
        this.openSound = openSound;
        this.closeSound = closeSound;
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

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        ActionResult result = super.onUse(state, world, pos, player, hand, hit);

        if (result == ActionResult.SUCCESS && !world.isClient && (!player.isSneaking() || player.getStackInHand(hand).isEmpty())) {
            boolean isOpen = state.get(OPEN);

            if (isOpen) {
                world.playSound(null, pos, closeSound,
                        SoundCategory.BLOCKS, 0.3F, 1.0F);
            } else {
                world.playSound(null, pos, openSound,
                        SoundCategory.BLOCKS, 0.3F, 1.0F);
            }
        }

        return result;
    }
}