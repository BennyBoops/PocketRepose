package net.bennyboops.modid;

import java.util.List;
import net.bennyboops.modid.item.KeystoneItem;
import net.bennyboops.modid.item.ModItems;
import net.minecraft.item.Item;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.bennyboops.modid.client.KeyPreviewTooltip;
import net.bennyboops.modid.item.KeyPreviewData;
import net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback;
import net.bennyboops.modid.block.ModBlocks;
import net.bennyboops.modid.block.entity.ModBlockEntities;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.bennyboops.modid.client.DoorwayBlockEntityRenderer;
import net.bennyboops.modid.client.HallwayRenderer;
import net.bennyboops.modid.client.ConfigClientNetwork;
import net.bennyboops.modid.client.HallwaySettings;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.Text;
import net.bennyboops.modid.client.PocketBlockEntityRenderer;
import net.bennyboops.modid.client.SecretChestRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.block.Block;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.util.Identifier;

public class PocketReposeClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ConfigClientNetwork.init();
        // Pictures of what each key creates, in the keys' tooltips
        TooltipComponentCallback.EVENT.register(data -> data instanceof KeyPreviewData preview ? new KeyPreviewTooltip(preview) : null);
        // Keys look broken until they have a name (also in the anvil's output slot)
        for (Item key : List.of(ModItems.GOLD_KEY, ModItems.IRON_KEY, ModItems.NETHERITE_KEY, ModItems.LEGACY_KEYSTONE)) {
            ModelPredicateProviderRegistry.register(key, Identifier.of(PocketRepose.MOD_ID, "named"),
                    (stack, world, entity, seed) -> KeystoneItem.showsRepaired(stack) ? 1f : 0f);
        }
        BlockEntityRendererFactories.register(ModBlockEntities.SUITCASE_BLOCK_ENTITY, PocketBlockEntityRenderer::new);
        BlockEntityRendererFactories.register(ModBlockEntities.DOORWAY_BLOCK_ENTITY, DoorwayBlockEntityRenderer::new);
        // The shader that draws open pocket blocks as a dark hallway
        CoreShaderRegistrationCallback.EVENT.register(context -> context.register(
                Identifier.of(PocketRepose.MOD_ID, "doorway"), VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
                HallwayRenderer::setShader));
        // Each player's own choice: /pocketReposeClient hallwayShader [true|false]
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("pocketReposeClient")
                        .then(ClientCommandManager.literal("hallwayShader")
                                .then(ClientCommandManager.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                            HallwaySettings.setEnabled(enabled);
                                            ctx.getSource().sendFeedback(Text.literal("Hallway shader in pocket blocks: "
                                                    + (enabled ? "§aEnabled" : "§cDisabled")));
                                            return 1;
                                        }))
                                .executes(ctx -> {
                                    ctx.getSource().sendFeedback(Text.literal("§7Hallway shader in pocket blocks is currently: "
                                            + (HallwaySettings.enabled() ? "§aEnabled" : "§cDisabled")));
                                    return 1;
                                }))));
        BuiltinItemRendererRegistry.INSTANCE.register(ModBlocks.SECRET_CHEST, SecretChestRenderer::renderItem);
        // Door textures have see-through windows
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(), ModBlocks.POCKET_DOORS.toArray(new Block[0]));
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(), ModBlocks.EXIT_DOORS.toArray(new Block[0]));
    }
}
