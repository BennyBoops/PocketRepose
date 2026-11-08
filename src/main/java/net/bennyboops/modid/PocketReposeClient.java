package net.bennyboops.modid;

import net.bennyboops.modid.block.ModBlocks;
import net.bennyboops.modid.block.entity.ModBlockEntities;
import net.bennyboops.modid.client.SecretChestRenderer;
import net.minecraft.block.Block;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client-only setup, run from the mod's constructor on the client. */
public final class PocketReposeClient {
    private PocketReposeClient() {
    }

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {
            // Door textures have see-through windows
            for (Block door : ModBlocks.POCKET_DOORS) RenderLayers.setRenderLayer(door, RenderLayer.getCutout());
            for (Block door : ModBlocks.EXIT_DOORS) RenderLayers.setRenderLayer(door, RenderLayer.getCutout());
        }));
        modEventBus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
                event.registerBlockEntityRenderer(ModBlockEntities.SUITCASE_BLOCK_ENTITY, SecretChestRenderer::new));
    }
}
