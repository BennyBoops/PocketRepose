package net.bennyboops.modid;

import net.bennyboops.modid.block.ModBlocks;
import net.bennyboops.modid.block.entity.ModBlockEntities;
import net.bennyboops.modid.client.SecretChestRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.block.Block;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

public class PocketReposeClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockEntityRendererFactories.register(ModBlockEntities.SUITCASE_BLOCK_ENTITY, SecretChestRenderer::new);
        BuiltinItemRendererRegistry.INSTANCE.register(ModBlocks.SECRET_CHEST, SecretChestRenderer::renderItem);
        // Door textures have see-through windows
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(), ModBlocks.POCKET_DOORS.toArray(new Block[0]));
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(), ModBlocks.EXIT_DOORS.toArray(new Block[0]));
    }
}
