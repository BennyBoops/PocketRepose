package net.bennyboops.modid.world;

import net.bennyboops.modid.block.ModBlocks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

public class PortalChunkHandler {
    private static final BlockState PORTAL_STATE = ModBlocks.PORTAL.getDefaultState();

    public static void initialize() {
        ServerChunkEvents.CHUNK_LOAD.register(PortalChunkHandler::onChunkLoad);
    }

    private static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
        String namespace = world.getRegistryKey().getValue().getNamespace();
        String path = world.getRegistryKey().getValue().getPath();

        if (!namespace.equals("pocket-repose") || !path.startsWith("pocket_dimension_")) {
            return;
        }

        // Only fix portals if they're missing (loaded from old save)
        ChunkPos chunkPos = chunk.getPos();
        BlockPos testPos = new BlockPos((chunkPos.x << 4), -64, (chunkPos.z << 4));

        // Quick test - if first portal exists, assume rest are fine
        if (chunk.getBlockState(testPos).isOf(ModBlocks.PORTAL)) {
            return;
        }

        // Portals missing - place them directly in the chunk (fast, no world access)
        for (int dy = -64; dy <= -61; dy++) {
            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    int worldX = (chunkPos.x << 4) + dx;
                    int worldZ = (chunkPos.z << 4) + dz;
                    BlockPos blockPos = new BlockPos(worldX, dy, worldZ);

                    // Set directly in chunk - no block updates, very fast
                    chunk.setBlockState(blockPos, PORTAL_STATE, false);
                }
            }
        }
    }
}