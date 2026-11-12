package net.bennyboops.modid.config;

import net.bennyboops.modid.PocketRepose;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

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

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener((RegisterPayloadHandlersEvent event) -> {
            PayloadRegistrar registrar = event.registrar("1");
            registrar.playToClient(SettingsPayload.ID, SettingsPayload.CODEC,
                    (payload, context) -> ConfigState.receive(payload.settings()));
            registrar.playToServer(ChangePayload.ID, ChangePayload.CODEC, (payload, context) -> {
                if (context.player() instanceof ServerPlayerEntity player) PocketReposeConfig.onChangeRequested(player, payload.changes());
            });
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayerEntity player) sendTo(player, PocketReposeConfig.snapshot());
        });
    }

    public static void sendTo(ServerPlayerEntity player, NbtCompound settings) {
        PacketDistributor.sendToPlayer(player, new SettingsPayload(settings));
    }

    public static void sendToAll(MinecraftServer server, NbtCompound settings) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) sendTo(player, settings);
    }

    /** Client side: asks the server to change settings. It only does if the player is an operator. */
    public static void sendToServer(NbtCompound changes) {
        PacketDistributor.sendToServer(new ChangePayload(changes));
    }
}
