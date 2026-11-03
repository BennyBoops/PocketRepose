package net.bennyboops.modid.world;

import net.bennyboops.modid.block.ModBlocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

public class PortalChunkHandler {
    private static final BlockState AIR_STATE = Blocks.AIR.getDefaultState();

    public static void initialize() {
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Load event) -> {
            if (event.getLevel() instanceof ServerWorld world && event.getChunk() instanceof WorldChunk chunk) {
                onChunkLoad(world, chunk);
            }
        });
    }

    private static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
        String namespace = world.getRegistryKey().getValue().getNamespace();
        String path = world.getRegistryKey().getValue().getPath();

        if (!namespace.equals("pocket-repose") || !path.startsWith("pocket_dimension_")) {
            return;
        }

        // Older versions generated a floor of portal blocks at the bottom of every pocket dimension
        // chunk. The floor isn't needed anymore (falling out of the world exits the pocket), so remove it.
        ChunkPos chunkPos = chunk.getPos();
        int bottomY = world.getBottomY();
        BlockPos testPos = new BlockPos(chunkPos.getStartX(), bottomY, chunkPos.getStartZ());

        // Quick test - the old floor filled the whole layer, so if the corner isn't a portal there's no floor
        if (!chunk.getBlockState(testPos).isOf(ModBlocks.PORTAL)) {
            return;
        }

        BlockPos.Mutable blockPos = new BlockPos.Mutable();
        for (int dy = bottomY; dy <= bottomY + 3; dy++) {
            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    blockPos.set(chunkPos.getStartX() + dx, dy, chunkPos.getStartZ() + dz);
                    if (chunk.getBlockState(blockPos).isOf(ModBlocks.PORTAL)) {
                        chunk.setBlockState(blockPos, AIR_STATE, false);
                    }
                }
            }
        }
    }
}
