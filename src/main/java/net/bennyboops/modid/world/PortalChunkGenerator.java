package net.bennyboops.modid.world;

import net.bennyboops.modid.block.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import xyz.nucleoid.fantasy.util.VoidChunkGenerator;

public class PortalChunkGenerator extends VoidChunkGenerator {
    private final BlockState portalState = ModBlocks.PORTAL.getDefaultState();

    public PortalChunkGenerator(Registry<Biome> biomeRegistry) {
        super(biomeRegistry,
                RegistryKey.of(RegistryKeys.BIOME, new Identifier("minecraft", "the_void")));
    }

    @Override
    public void generateFeatures(
            StructureWorldAccess world,
            Chunk chunk,
            StructureAccessor structureAccessor
    ) {
        super.generateFeatures(world, chunk, structureAccessor);

        ChunkPos chunkPos = chunk.getPos();
        for (int dy = -64; dy <= -61; dy++) {
            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    int worldX = (chunkPos.x << 4) + dx;
                    int worldZ = (chunkPos.z << 4) + dz;
                    BlockPos blockPos = new BlockPos(worldX, dy, worldZ);
                    world.setBlockState(blockPos, portalState, Block.NOTIFY_LISTENERS);
                }
            }
        }
    }
}






    /**
    private final BlockState portalState = ModBlocks.PORTAL.getDefaultState();

    public PortalChunkGenerator(Registry<Biome> biomeRegistry, RegistryKey<Biome> biomeKey) {
        super(biomeRegistry, biomeKey);
    }

    @Override
    public void generateFeatures(
            StructureWorldAccess world,
            net.minecraft.world.chunk.Chunk chunk,
            StructureAccessor structureAccessor
    ) {
        super.generateFeatures(world, chunk, structureAccessor);

        ChunkPos pos = chunk.getPos();
        // stamp from -64 up to and including -61
        for (int dy = -64; dy <= -61; dy++) {
            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    int worldX = (pos.x << 4) + dx;
                    int worldZ = (pos.z << 4) + dz;
                    chunk.setBlockState(
                            new BlockPos(worldX, dy, worldZ),
                            portalState, false
                    );
                }
            }
        }
    }
}
     **/