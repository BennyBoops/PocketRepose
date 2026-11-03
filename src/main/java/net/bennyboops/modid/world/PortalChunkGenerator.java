package net.bennyboops.modid.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.bennyboops.modid.util.VoidChunkGenerator;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.chunk.ChunkGenerator;

/**
 * Empty pocket dimension terrain. It used to lay a floor of portal blocks at the bottom of the
 * world; players falling out of the world are now sent out by {@link net.bennyboops.modid.block.PocketTravelHandler}.
 */
public class PortalChunkGenerator extends VoidChunkGenerator {
    // Own codec so a reloaded pocket dimension keeps using this generator
    public static final MapCodec<PortalChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Biome.REGISTRY_CODEC.stable().fieldOf("biome").forGetter(PortalChunkGenerator::getBiome)
    ).apply(instance, instance.stable(PortalChunkGenerator::new)));

    public PortalChunkGenerator(RegistryEntry<Biome> biome) {
        super(biome);
    }

    public PortalChunkGenerator(Registry<Biome> biomeRegistry) {
        super(biomeRegistry,
                RegistryKey.of(RegistryKeys.BIOME, Identifier.of("pocket-repose", "pocket_islands")));
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> getCodec() {
        return CODEC;
    }
}
