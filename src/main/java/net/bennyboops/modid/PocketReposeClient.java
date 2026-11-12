package net.bennyboops.modid;

import net.bennyboops.modid.client.ConfigClientNetwork;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.bennyboops.modid.block.ModBlocks;
import net.bennyboops.modid.block.entity.ModBlockEntities;
import net.bennyboops.modid.client.DoorwayBlockEntityRenderer;
import net.bennyboops.modid.client.HallwayRenderer;
import net.bennyboops.modid.client.HallwaySettings;
import net.bennyboops.modid.client.PocketBlockEntityRenderer;
import net.minecraft.block.Block;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;

import java.io.IOException;

/** Client-only setup, run from the mod's constructor on the client. */
public final class PocketReposeClient {
    private PocketReposeClient() {
    }

    public static void init(IEventBus modEventBus) {
        // Settings sync, and the config button in the mods list
        ConfigClientNetwork.init();
        modEventBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {
            // Door textures have see-through windows
            for (Block door : ModBlocks.POCKET_DOORS) RenderLayers.setRenderLayer(door, RenderLayer.getCutout());
            for (Block door : ModBlocks.EXIT_DOORS) RenderLayers.setRenderLayer(door, RenderLayer.getCutout());
        }));
        modEventBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> {
            event.registerBlockEntityRenderer(ModBlockEntities.SUITCASE_BLOCK_ENTITY, PocketBlockEntityRenderer::new);
            event.registerBlockEntityRenderer(ModBlockEntities.DOORWAY_BLOCK_ENTITY, DoorwayBlockEntityRenderer::new);
        });
        // The shader that draws open pocket blocks as a dark hallway
        modEventBus.addListener((RegisterShadersEvent event) -> {
            try {
                event.registerShader(new ShaderProgram(event.getResourceProvider(), Identifier.of(PocketRepose.MOD_ID, "doorway"),
                        VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL), HallwayRenderer::setShader);
            } catch (IOException e) {
                PocketRepose.LOGGER.error("Couldn't load the pocket block hallway shader", e);
            }
        });
        // Each player's own choice: /pocketReposeClient hallwayShader [true|false]
        MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) -> event.getDispatcher().register(
                CommandManager.literal("pocketReposeClient")
                        .then(CommandManager.literal("hallwayShader")
                                .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                            HallwaySettings.setEnabled(enabled);
                                            ctx.getSource().sendFeedback(() -> Text.literal("Hallway shader in pocket blocks: "
                                                    + (enabled ? "\u00a7aEnabled" : "\u00a7cDisabled")), false);
                                            return 1;
                                        }))
                                .executes(ctx -> {
                                    ctx.getSource().sendFeedback(() -> Text.literal("\u00a77Hallway shader in pocket blocks is currently: "
                                            + (HallwaySettings.enabled() ? "\u00a7aEnabled" : "\u00a7cDisabled")), false);
                                    return 1;
                                }))));
    }
}
