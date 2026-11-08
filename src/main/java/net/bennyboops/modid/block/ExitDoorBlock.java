package net.bennyboops.modid.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockSetType;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/** Looks like a pocket door, works like the portal block: walking through its open doorway leaves the pocket dimension. */
public class ExitDoorBlock extends DoorwayBlock {
    public static final MapCodec<ExitDoorBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BlockSetType.CODEC.fieldOf("block_set_type").forGetter(DoorBlock::getBlockSetType),
            createSettingsCodec()
    ).apply(instance, ExitDoorBlock::new));

    public ExitDoorBlock(BlockSetType type, Settings settings) {
        super(type, settings);
    }

    @Override
    public MapCodec<? extends DoorBlock> getCodec() {
        return CODEC;
    }

    // Placing and breaking sound like the portal block (DoorBlock sets the wood's sounds, so this overrides them)
    @Override
    protected BlockSoundGroup getSoundGroup(BlockState state) {
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
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
        tooltip.add(PocketPortalBlock.exitTooltip());
    }
}
