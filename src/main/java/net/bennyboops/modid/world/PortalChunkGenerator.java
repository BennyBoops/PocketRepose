package net.bennyboops.modid.world;

import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import net.bennyboops.modid.util.VoidChunkGenerator;

/**
 * Generates the empty pocket-island biome. Nothing is placed: the island comes from the
 * structure template, and anything that falls off is caught by {@link PocketVoidCatcher}.
 *
 * Older versions laid a world-wide floor of portal blocks at the bottom of the world; see
 * {@link PocketFloorCleanup} for how chunks generated back then are tidied up.
 */
public class PortalChunkGenerator extends VoidChunkGenerator {
    public PortalChunkGenerator(Registry<Biome> biomeRegistry) {
        super(biomeRegistry,
                RegistryKey.of(RegistryKeys.BIOME, new Identifier("pocket-repose", "pocket_islands")));
    }
}
