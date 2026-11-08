package net.bennyboops.modid.block;

import net.minecraft.block.BlockSetType;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.world.BlockView;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import org.jetbrains.annotations.Nullable;

/** Looks like a pocket door, works like the portal block: walking through its open doorway leaves the pocket dimension. */
public class ExitDoorBlock extends DoorwayBlock {

    public ExitDoorBlock(BlockSetType type, Settings settings) {
        super(type, settings);
    }


    // Placing and breaking sound like the portal block (DoorBlock sets the wood's sounds, so this overrides them)
    @Override
    public BlockSoundGroup getSoundGroup(BlockState state) {
        return BlockSoundGroup.LODESTONE;
    }

    // Like the portal block, it only does something inside a pocket dimension
    @Override
    protected void onWalkThrough(BlockState state, ServerWorld world, BlockPos pos, ServerPlayerEntity player) {
        if (PocketPortalBlock.isPocketDimension(world)) {
            PocketTravelHandler.requestExit(player);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable BlockView world, List<Text> tooltip, TooltipContext options) {
        tooltip.add(PocketPortalBlock.exitTooltip());
    }
}
