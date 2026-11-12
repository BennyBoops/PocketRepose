package net.bennyboops.modid.config;

import net.bennyboops.modid.PocketRepose;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Sends the settings to players, and receives changes made in the config screen. */
public final class ConfigNetwork {
    /** Server to client: every setting, sent on joining and whenever one changes. */
    public static final Identifier SETTINGS = new Identifier(PocketRepose.MOD_ID, "settings");
    /** Client to server: settings changed in the config screen. */
    public static final Identifier CHANGE = new Identifier(PocketRepose.MOD_ID, "change_settings");

    private ConfigNetwork() {
    }

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(CHANGE, (server, player, handler, buf, responseSender) -> {
            NbtCompound changes = buf.readNbt();
            if (changes != null) server.execute(() -> PocketReposeConfig.onChangeRequested(player, changes));
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendTo(handler.player, PocketReposeConfig.snapshot()));
    }

    public static void sendTo(ServerPlayerEntity player, NbtCompound settings) {
        if (!ServerPlayNetworking.canSend(player, SETTINGS)) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeNbt(settings);
        ServerPlayNetworking.send(player, SETTINGS, buf);
    }

    public static void sendToAll(MinecraftServer server, NbtCompound settings) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) sendTo(player, settings);
    }
}
