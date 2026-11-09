package net.bennyboops.modid.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSetType;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldAccess;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
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
    // Another door (facing the same way) is right beside the latch side, so the open doorway's frame isn't drawn
    // on that side and the two doorways read as one
    public static final BooleanProperty PAIRED = BooleanProperty.of("paired");

    protected DoorwayBlock(BlockSetType type, Settings settings) {
        super(settings, type);
        setDefaultState(getDefaultState().with(PAIRED, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(PAIRED);
    }

    /** The side away from the hinge, where a door opening the other way would sit in a double door. */
    protected static Direction latchSide(BlockState state) {
        Direction facing = state.get(FACING);
        return state.get(HINGE) == DoorHinge.LEFT ? facing.rotateYClockwise() : facing.rotateYCounterclockwise();
    }

    private static boolean pairsWith(BlockState state, BlockState neighbor) {
        return neighbor.getBlock() instanceof DoorBlock && neighbor.get(FACING) == state.get(FACING);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockState state = super.getPlacementState(ctx);
        if (state == null) return null;
        BlockState neighbor = ctx.getWorld().getBlockState(ctx.getBlockPos().offset(latchSide(state)));
        return state.with(PAIRED, pairsWith(state, neighbor));
    }

    // Kept up to date as doors are placed or broken beside it (the other half copies it, as halves do)
    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
                                                   WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        BlockState updated = super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
        if (updated.isOf(this) && direction == latchSide(updated)) {
            return updated.with(PAIRED, pairsWith(updated, neighborState));
        }
        return updated;
    }

    /** Called when an item is thrown into the open doorway, from either side. */
    protected void onItemThrough(BlockState state, ServerWorld world, BlockPos pos, ItemEntity item) {
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
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        VoxelShape door = super.getOutlineShape(state, world, pos, context);
        return state.get(OPEN) ? VoxelShapes.union(door, VoxelShapes.cuboid(doorway(state))) : door;
    }

    // Only the door itself is solid; the doorway can be walked through
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        VoxelShape door = super.getOutlineShape(state, world, pos, context);
        if (!state.get(OPEN) || state.get(PAIRED) || !hasDoorframe()) return door;
        return VoxelShapes.union(door, VoxelShapes.cuboid(frameSide(state)));
    }

    /** Whether this door draws a frame around its open doorway (every pocket and exit portal door does). */
    public boolean hasDoorframe() {
        return true;
    }

    /** The open doorway's frame on the latch side: one pixel of the closed door's edge, full height and thickness. */
    protected Box frameSide(BlockState state) {
        Box d = doorway(state);
        double px = 1 / 16.0;
        return switch (latchSide(state)) {
            case NORTH -> d.withMaxZ(d.minZ + px);
            case SOUTH -> d.withMinZ(d.maxZ - px);
            case WEST -> d.withMaxX(d.minX + px);
            default -> d.withMinX(d.maxX - px);
        };
    }

    // Pocket and exit doors play the suitcase's open and close sounds on top of the door's own
    @Override
    public void setOpen(@Nullable Entity entity, World world, BlockState state, BlockPos pos, boolean open) {
        boolean toggles = state.isOf(this) && state.get(OPEN) != open;
        super.setOpen(entity, world, state, pos, open);
        if (toggles && !world.isClient) SuitcaseBlock.playSuitcaseSound(world, pos, open);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        // A door placed before it had a block entity gets one here (looking it up creates it)
        world.getBlockEntity(lowerHalf(state, pos));
        ActionResult result = super.onUse(state, world, pos, player, hand, hit);
        playSuitcaseSoundIfToggled(state, world, pos);
        return result;
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
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
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (world.isClient || !state.get(OPEN)) return;
        if (entity instanceof ItemEntity item) {
            if (item.getBoundingBox().intersects(doorway(state).offset(pos))) {
                onItemThrough(state, (ServerWorld) world, pos, item);
            }
            return;
        }
        if (!(entity instanceof ServerPlayerEntity player)) return;
        if (!player.getBoundingBox().intersects(doorway(state).offset(pos))) return;
        onWalkThrough(state, (ServerWorld) world, pos, player);
    }
}
