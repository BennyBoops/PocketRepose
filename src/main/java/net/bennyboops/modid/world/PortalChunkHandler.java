package net.bennyboops.modid.world;

import net.bennyboops.modid.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.neoforge.event.level.ChunkEvent;

public class PortalChunkHandler {

    public static void onChunkLoad(ChunkEvent.Load event) {
        LevelAccessor levelAccessor = event.getLevel();
        if (!(levelAccessor instanceof Level level) || level.isClientSide()) {
            return;
        }

        String namespace = level.dimension().location().getNamespace();
        String path = level.dimension().location().getPath();

        if (!namespace.equals("pocket-repose") || !path.startsWith("pocket_dimension_")) {
            return;
        }

        ChunkAccess chunk = event.getChunk();
        BlockState portalState = ModBlocks.PORTAL.get().defaultBlockState();

        // Only fix portals if they're missing (loaded from old save)
        ChunkPos chunkPos = chunk.getPos();
        BlockPos testPos = new BlockPos((chunkPos.x << 4), -64, (chunkPos.z << 4));

        // Quick test - if first portal exists, assume rest are fine
        if (chunk.getBlockState(testPos).is(ModBlocks.PORTAL.get())) {
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
                    chunk.setBlockState(blockPos, portalState, false);
                }
            }
        }
    }
}
