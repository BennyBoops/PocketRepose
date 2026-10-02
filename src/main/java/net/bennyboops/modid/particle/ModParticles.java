package net.bennyboops.modid.particle;

import net.bennyboops.modid.PocketRepose;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModParticles {
    /** Small glowing runes that drift upward and fade. Spawned by the pocket islands biome. */
    public static final DefaultParticleType RUNE = Registry.register(
            Registries.PARTICLE_TYPE,
            new Identifier(PocketRepose.MOD_ID, "rune"),
            FabricParticleTypes.simple(true));

    public static void registerParticles() {
    }
}
