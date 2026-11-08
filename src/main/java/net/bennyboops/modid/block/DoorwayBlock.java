package net.bennyboops.modid.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSetType;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A door whose doorway (where the closed door sits) shows a black plane while it's open. Walking into
 * the open doorway does something (see onWalkThrough), and the doorway can be clicked like the door.
 */
public abstract class DoorwayBlock extends DoorBlock {
    protected DoorwayBlock(BlockSetType type, Settings settings) {
        super(type, settings);
    }

    /** Called when a player walks into the open doorway, from either side. */
    protected abstract void onWalkThrough(BlockState state, ServerWorld world, BlockPos pos, ServerPlayerEntity player);

    protected static BlockPos lowerHalf(BlockState state, BlockPos pos) {
        return state.get(HALF) == DoubleBlockHalf.LOWER ? pos : pos.down();
    }

    /** Where the closed door sits, in block coordinates; this is the plane players walk through. */
    protected Box doorway(BlockState state) {
        return super.getOutlineShape(state.with(OPEN, false), EmptyBlockView.INSTANCE, BlockPos.ORIGIN, ShapeContext.absent())
                .getBoundingBox();
    }

    // The black doorway of an open door can be clicked like the door itself
    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        VoxelShape door = super.getOutlineShape(state, world, pos, context);
        return state.get(OPEN) ? VoxelShapes.union(door, VoxelShapes.cuboid(doorway(state))) : door;
    }

    // Only the door itself is solid; the doorway can be walked through
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return super.getOutlineShape(state, world, pos, context);
    }

    // Pocket and exit doors play the suitcase's open and close sounds on top of the door's own
    @Override
    public void setOpen(@Nullable Entity entity, World world, BlockState state, BlockPos pos, boolean open) {
        boolean toggles = state.isOf(this) && state.get(OPEN) != open;
        super.setOpen(entity, world, state, pos, open);
        if (toggles && !world.isClient) SuitcaseBlock.playSuitcaseSound(world, pos, open);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        ActionResult result = super.onUse(state, world, pos, player, hit);
        playSuitcaseSoundIfToggled(state, world, pos);
        return result;
    }

    @Override
    protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify);
        playSuitcaseSoundIfToggled(state, world, pos);
    }

    private void playSuitcaseSoundIfToggled(BlockState before, World world, BlockPos pos) {
        BlockState after = world.getBlockState(pos);
        if (!world.isClient && after.isOf(this) && after.get(OPEN) != before.get(OPEN)) {
            SuitcaseBlock.playSuitcaseSound(world, pos, after.get(OPEN));
        }
    }

    @Override
    protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (world.isClient || !(entity instanceof ServerPlayerEntity player) || !state.get(OPEN)) return;
        if (!player.getBoundingBox().intersects(doorway(state).offset(pos))) return;
        onWalkThrough(state, (ServerWorld) world, pos, player);
    }
}
