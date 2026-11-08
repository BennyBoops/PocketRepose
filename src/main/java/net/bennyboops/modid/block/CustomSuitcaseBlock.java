package net.bennyboops.modid.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class CustomSuitcaseBlock extends SuitcaseBlock {
    protected final VoxelShape customShapeN;
    protected final VoxelShape customShapeS;
    protected final VoxelShape customShapeE;
    protected final VoxelShape customShapeW;

    protected final SoundEvent openSound;
    protected final SoundEvent closeSound;
    // The outline drawn when looking at it, when it differs from the hitbox (null: the same)
    @Nullable
    protected final VoxelShape outline;

    public CustomSuitcaseBlock(Settings settings, VoxelShape shape, SoundEvent openSound, SoundEvent closeSound) {
        this(settings, shape, null, openSound, closeSound);
    }

    public CustomSuitcaseBlock(Settings settings, VoxelShape shape, @Nullable VoxelShape outline,
                               SoundEvent openSound, SoundEvent closeSound) {
        super(settings);
        this.outline = outline;
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


    // Its own sounds (barrel, chest) on top of the suitcase's
    @Override
    protected void playOpenCloseSound(World world, BlockPos pos, boolean opening) {
        super.playOpenCloseSound(world, pos, opening);
        world.playSound(null, pos, opening ? openSound : closeSound, SoundCategory.BLOCKS,
                0.5F, world.random.nextFloat() * 0.1F + 0.9F);
    }

    // Secret blocks are entered from the top: being pressed against a side (in the gap a chest leaves
    // around itself) doesn't count
    @Override
    protected boolean canEnterFrom(BlockState state, World world, BlockPos pos, Entity player) {
        // Shrunk a little so a player only just touching a side (allowing for rounding) doesn't count
        Box shape = hitbox(state).getBoundingBox().offset(pos).contract(0.01, 0, 0.01);
        Box box = player.getBoundingBox();
        return box.minX < shape.maxX && box.maxX > shape.minX && box.minZ < shape.maxZ && box.maxZ > shape.minZ;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return outline != null ? outline : hitbox(state);
    }

    // Solid up to the hitbox; a player standing on top is then inside the block space, which is how entering works
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return hitbox(state);
    }

    protected VoxelShape hitbox(BlockState state) {
        return switch (state.get(FACING)) {
            case NORTH -> customShapeN;
            case SOUTH -> customShapeS;
            case EAST -> customShapeE;
            case WEST -> customShapeW;
            default -> customShapeN;
        };
    }
}