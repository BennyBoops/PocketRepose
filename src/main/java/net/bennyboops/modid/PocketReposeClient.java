package net.bennyboops.modid;

import net.bennyboops.modid.client.PocketDimensionEffects;
import net.bennyboops.modid.client.PocketSkyRenderer;
import net.bennyboops.modid.client.RuneParticle;
import net.bennyboops.modid.particle.ModParticles;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.bennyboops.modid.client.FallingStarRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;

public class PocketReposeClient implements ClientModInitializer {
    /** Matches the "effects" field of the pocket dimension type. */
    public static final Identifier POCKET_EFFECTS_ID = new Identifier(PocketRepose.MOD_ID, "pocket");
    private static final String DIMENSION_PREFIX = "pocket_dimension_";

    private static final PocketSkyRenderer SKY_RENDERER = new PocketSkyRenderer();
    private static final FallingStarRenderer FALLING_STARS = new FallingStarRenderer();
    private static final Set<RegistryKey<World>> REGISTERED = new HashSet<>();

    @Override
    public void onInitializeClient() {
        DimensionRenderingRegistry.registerDimensionEffects(POCKET_EFFECTS_ID, new PocketDimensionEffects());
        ParticleFactoryRegistry.getInstance().register(ModParticles.RUNE, RuneParticle.Factory::new);

        // Falling stars live in world space near the island, so they are drawn after the
        // terrain (not in the sky pass) and the island can occlude them.
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            if (context.world() != null && isPocketDimension(context.world().getRegistryKey())) {
                FALLING_STARS.render(context);
            }
        });

        // Pocket worlds are created at runtime with dynamic keys, so the sky renderer is
        // attached the first time the client finds itself inside one of them.
        ClientTickEvents.END_CLIENT_TICK.register(PocketReposeClient::registerSkyForCurrentWorld);
    }

    private static boolean isPocketDimension(RegistryKey<World> key) {
        Identifier id = key.getValue();
        return id.getNamespace().equals(PocketRepose.MOD_ID) && id.getPath().startsWith(DIMENSION_PREFIX);
    }

    private static void registerSkyForCurrentWorld(MinecraftClient client) {
        if (client.world == null) {
            return;
        }
        RegistryKey<World> key = client.world.getRegistryKey();
        if (REGISTERED.contains(key)) {
            return;
        }
        Identifier id = key.getValue();
        if (!id.getNamespace().equals(PocketRepose.MOD_ID) || !id.getPath().startsWith(DIMENSION_PREFIX)) {
            return;
        }
        if (DimensionRenderingRegistry.getSkyRenderer(key) == null) {
            DimensionRenderingRegistry.registerSkyRenderer(key, SKY_RENDERER);
        }
        REGISTERED.add(key);
    }
}
