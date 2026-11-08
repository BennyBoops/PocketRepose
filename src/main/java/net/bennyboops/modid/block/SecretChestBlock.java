package net.bennyboops.modid.block;

import com.mojang.serialization.MapCodec;
import net.bennyboops.modid.block.entity.ModBlockEntities;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A suitcase disguised as a chest: chest hitbox and sounds, drawn with the vanilla chest model
 * (see SecretChestRenderer) so the lid animates when it opens and closes.
 */
public class SecretChestBlock extends CustomSuitcaseBlock {
    public static final MapCodec<SecretChestBlock> CODEC = createCodec(SecretChestBlock::new);

    public SecretChestBlock(Settings settings) {
        super(settings,
                createCuboidShape(1, 0, 1, 15, 14, 15),
                SoundEvents.BLOCK_CHEST_OPEN,
                SoundEvents.BLOCK_CHEST_CLOSE);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    // The block entity renderer draws the chest
    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    // Lid animation only runs on the client
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient
                ? validateTicker(type, ModBlockEntities.SUITCASE_BLOCK_ENTITY,
                        (tickWorld, pos, tickState, suitcase) -> suitcase.tickLid(tickState.get(OPEN)))
                : null;
    }
}
