package net.bennyboops.modid.world;

import net.fabricmc.api.ModInitializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.bennyboops.modid.world.Fantasy;
import net.bennyboops.modid.util.VoidChunkGenerator;

public final class FantasyInitializer implements ModInitializer {
    @Override
    public void onInitialize() {
        Registry.register(Registries.CHUNK_GENERATOR, new Identifier(Fantasy.ID, "void"), VoidChunkGenerator.CODEC);
    }
}
