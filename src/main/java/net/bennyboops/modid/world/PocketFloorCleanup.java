package net.bennyboops.modid.world;

import net.bennyboops.modid.block.ModBlocks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Removes the legacy portal-block floor from pocket dimension chunks as they load.
 *
 * Earlier versions filled y -64 to -61 of every chunk with portal blocks. The floor is no
 * longer generated and no longer needed, so any chunk still carrying it is cleaned the next
 * time it loads. Only that four-block band is touched, so the island's exit portal is safe.
 */
public final class PocketFloorCleanup {
    private static final int FLOOR_MIN_Y = -64;
    private static final int FLOOR_MAX_Y = -61;

    private PocketFloorCleanup() {
    }

    public static void register() {
        ServerChunkEvents.CHUNK_LOAD.register(PocketFloorCleanup::cleanChunk);
    }

    private static void cleanChunk(ServerWorld world, WorldChunk chunk) {
        if (!PocketVoidCatcher.isPocketDimension(world)) {
            return;
        }
        // The floor lives entirely in the bottom section. An empty one means nothing to do.
        int bottomSection = chunk.getSectionIndex(FLOOR_MIN_Y);
        if (chunk.getSection(bottomSection).isEmpty()) {
            return;
        }
        BlockState air = Blocks.AIR.getDefaultState();
        ChunkPos pos = chunk.getPos();
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        int removed = 0;
        for (int y = FLOOR_MIN_Y; y <= FLOOR_MAX_Y; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    cursor.set(pos.getStartX() + x, y, pos.getStartZ() + z);
                    if (chunk.getBlockState(cursor).isOf(ModBlocks.PORTAL)) {
                        chunk.setBlockState(cursor, air, false);
                        removed++;
                    }
                }
            }
        }
        if (removed > 0) {
            chunk.setNeedsSaving(true);
        }
    }
}
