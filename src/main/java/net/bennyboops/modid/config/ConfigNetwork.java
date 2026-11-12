package net.bennyboops.modid.config;

import net.bennyboops.modid.PocketRepose;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Sends the settings to players, and receives changes made in the config screen. */
public final class ConfigNetwork {
    /** Server to client: every setting, sent on joining and whenever one changes. */
    public record SettingsPayload(NbtCompound settings) implements CustomPayload {
        public static final Id<SettingsPayload> ID = new Id<>(Identifier.of(PocketRepose.MOD_ID, "settings"));
        public static final PacketCodec<RegistryByteBuf, SettingsPayload> CODEC =
                PacketCodec.tuple(PacketCodecs.NBT_COMPOUND, SettingsPayload::settings, SettingsPayload::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Client to server: settings changed in the config screen. */
    public record ChangePayload(NbtCompound changes) implements CustomPayload {
        public static final Id<ChangePayload> ID = new Id<>(Identifier.of(PocketRepose.MOD_ID, "change_settings"));
        public static final PacketCodec<RegistryByteBuf, ChangePayload> CODEC =
                PacketCodec.tuple(PacketCodecs.NBT_COMPOUND, ChangePayload::changes, ChangePayload::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private ConfigNetwork() {
    }

    public static void init() {
        PayloadTypeRegistry.playS2C().register(SettingsPayload.ID, SettingsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ChangePayload.ID, ChangePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ChangePayload.ID,
                (payload, context) -> PocketReposeConfig.onChangeRequested(context.player(), payload.changes()));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendTo(handler.player, PocketReposeConfig.snapshot()));
    }

    public static void sendTo(ServerPlayerEntity player, NbtCompound settings) {
        if (ServerPlayNetworking.canSend(player, SettingsPayload.ID)) {
            ServerPlayNetworking.send(player, new SettingsPayload(settings));
        }
    }

    public static void sendToAll(MinecraftServer server, NbtCompound settings) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) sendTo(player, settings);
    }
}
