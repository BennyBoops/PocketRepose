package net.bennyboops.modid.block;

import net.bennyboops.modid.block.entity.SuitcaseBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockSetType;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * A door that works like a suitcase: bind it with a key, lock it, and walk through its open doorway
 * (no sneaking needed) to enter the pocket dimension. The suitcase data lives in a block entity on the
 * lower half.
 */
public class PocketDoorBlock extends DoorwayBlock implements BlockEntityProvider {

    public PocketDoorBlock(BlockSetType type, Settings settings) {
        super(type, settings);
    }


    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return state.get(HALF) == DoubleBlockHalf.LOWER ? new SuitcaseBlockEntity(pos, state) : null;
    }

    /**
     * Where someone who walked through this door comes back out: half a block from the doorway, on the
     * side they came from (or, when placing it, the side the placer stood on).
     */
    public Vec3d exitPoint(BlockState state, BlockPos pos, Vec3d from) {
        BlockPos lower = lowerHalf(state, pos);
        Box doorway = doorway(state);
        if (doorway.getXLength() < doorway.getZLength()) {
            double plane = lower.getX() + (doorway.minX + doorway.maxX) / 2;
            double x = from.x < plane ? lower.getX() + doorway.minX - 0.5 : lower.getX() + doorway.maxX + 0.5;
            return new Vec3d(x, lower.getY(), lower.getZ() + 0.5);
        }
        double plane = lower.getZ() + (doorway.minZ + doorway.maxZ) / 2;
        double z = from.z < plane ? lower.getZ() + doorway.minZ - 0.5 : lower.getZ() + doorway.maxZ + 0.5;
        return new Vec3d(lower.getX() + 0.5, lower.getY(), z);
    }

    /**
     * For someone coming back out of the pocket door with this suitcase id at exit: the yaw that faces
     * away from the door, or null if that door isn't there.
     */
    @Nullable
    public static Float yawAwayFromDoor(World world, Vec3d exit, @Nullable UUID suitcaseId) {
        if (suitcaseId == null) return null;
        BlockPos center = BlockPos.ofFloored(exit);
        for (BlockPos pos : BlockPos.iterate(center.add(-1, 0, -1), center.add(1, 0, 1))) {
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof PocketDoorBlock door && state.get(HALF) == DoubleBlockHalf.LOWER
                    && world.getBlockEntity(pos) instanceof SuitcaseBlockEntity suitcase
                    && suitcaseId.equals(suitcase.getSuitcaseId())) {
                Vec3d away = exit.subtract(door.doorway(state).offset(pos).getCenter());
                return (float) Math.toDegrees(Math.atan2(-away.x, away.z));
            }
        }
        return null;
    }

    @Override
    protected void onWalkThrough(BlockState state, ServerWorld world, BlockPos pos, ServerPlayerEntity player) {
        if (SuitcaseBlock.recursionBlocked(world, pos, player)) return;

        // Entered at the end of the tick, like suitcases (see PocketTravelHandler)
        PocketTravelHandler.requestEntry(player, world, lowerHalf(state, pos));
    }

    private ActionResult onUseWithItem(ItemStack held, BlockState state, World world, BlockPos pos,
                                             PlayerEntity player, Hand hand, BlockHitResult hit) {
        BlockPos lower = lowerHalf(state, pos);
        if (!(world.getBlockEntity(lower) instanceof SuitcaseBlockEntity suitcase)) {
            return ActionResult.PASS;
        }
        BlockState lowerState = world.getBlockState(lower);

        if (world.isClient) {
            // Open both halves right away instead of waiting for the server, so they move together
            if (SuitcaseBlock.willToggle(suitcase, held, world, player)) {
                setOpen(null, world, lowerState, lower, !lowerState.get(OPEN));
            }
            return ActionResult.SUCCESS;
        }
        return SuitcaseBlock.useSuitcase(suitcase, held, world, lower, player, lowerState.get(OPEN),
                opening -> setOpen(null, world, lowerState, lower, opening));
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        return onUseWithItem(player.getStackInHand(hand), state, world, pos, player, hand, hit);
    }

    // Like suitcases, pocket doors only open for a player: redstone doesn't open them
    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockState state = super.getPlacementState(ctx);
        return state == null ? null : state.with(OPEN, false).with(POWERED, false);
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (world.isClient || !(world.getBlockEntity(pos) instanceof SuitcaseBlockEntity suitcase)) return;

        Vec3d from = placer != null ? placer.getPos() : Vec3d.ofCenter(pos);
        SuitcaseBlock.trackPlacedSuitcase(world, suitcase, stack, exitPoint(state, pos, from));
    }

    // Drops itself with its binding, like a suitcase (instead of a loot table drop)
    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof SuitcaseBlockEntity suitcase) {
            ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), SuitcaseBlock.droppedStack(this, suitcase));
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) {
        return Collections.emptyList();
    }
}
