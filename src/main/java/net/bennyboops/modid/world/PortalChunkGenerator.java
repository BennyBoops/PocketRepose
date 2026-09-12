package net.bennyboops.modid.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.bennyboops.modid.block.ModBlocks;
import net.bennyboops.modid.util.VoidChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.concurrent.CompletableFuture;

public class PortalChunkGenerator extends VoidChunkGenerator {
    public static final MapCodec<PortalChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Biome.CODEC.stable().fieldOf("biome").forGetter(PortalChunkGenerator::getBiome)
    ).apply(instance, instance.stable(PortalChunkGenerator::new)));

    public static final ResourceKey<Biome> POCKET_ISLANDS = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath("pocket-repose", "pocket_islands"));

    public PortalChunkGenerator(Holder<Biome> biome) {
        super(biome);
    }

    public PortalChunkGenerator(Registry<Biome> biomeRegistry) {
        super(biomeRegistry, POCKET_ISLANDS);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState,
                                                        StructureManager structureManager, ChunkAccess chunk) {
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager,
                             RandomState randomState, ChunkAccess chunk) {
        BlockState portalState = ModBlocks.PORTAL.get().defaultBlockState();
        ChunkPos chunkPos = chunk.getPos();

        for (int dy = -64; dy <= -61; dy++) {
            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    int worldX = (chunkPos.x << 4) + dx;
                    int worldZ = (chunkPos.z << 4) + dz;
                    BlockPos blockPos = new BlockPos(worldX, dy, worldZ);

                    chunk.setBlockState(blockPos, portalState, false);
                }
            }
        }
    }
}
