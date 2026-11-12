package net.bennyboops.modid.config;

import net.bennyboops.modid.PocketRepose;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Sends the settings to players, and receives changes made in the config screen. */
public final class ConfigNetwork {
    /** Server to client: every setting, sent on joining and whenever one changes. */
    public record SettingsMessage(NbtCompound settings) {
        void encode(PacketByteBuf buf) {
            buf.writeNbt(settings);
        }

        static SettingsMessage decode(PacketByteBuf buf) {
            NbtCompound nbt = buf.readNbt();
            return new SettingsMessage(nbt == null ? new NbtCompound() : nbt);
        }
    }

    /** Client to server: settings changed in the config screen. */
    public record ChangeMessage(NbtCompound changes) {
        void encode(PacketByteBuf buf) {
            buf.writeNbt(changes);
        }

        static ChangeMessage decode(PacketByteBuf buf) {
            NbtCompound nbt = buf.readNbt();
            return new ChangeMessage(nbt == null ? new NbtCompound() : nbt);
        }
    }

    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new Identifier(PocketRepose.MOD_ID, "settings"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private ConfigNetwork() {
    }

    public static void init() {
        CHANNEL.messageBuilder(SettingsMessage.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SettingsMessage::encode)
                .decoder(SettingsMessage::decode)
                .consumerMainThread((message, context) -> ConfigState.receive(message.settings()))
                .add();
        CHANNEL.messageBuilder(ChangeMessage.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ChangeMessage::encode)
                .decoder(ChangeMessage::decode)
                .consumerMainThread((message, context) -> {
                    ServerPlayerEntity player = context.get().getSender();
                    if (player != null) PocketReposeConfig.onChangeRequested(player, message.changes());
                })
                .add();
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayerEntity player) sendTo(player, PocketReposeConfig.snapshot());
        });
    }

    public static void sendTo(ServerPlayerEntity player, NbtCompound settings) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SettingsMessage(settings));
    }

    public static void sendToAll(MinecraftServer server, NbtCompound settings) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) sendTo(player, settings);
    }

    /** Client side: asks the server to change settings. It only does if the player is an operator. */
    public static void sendToServer(NbtCompound changes) {
        CHANNEL.sendToServer(new ChangeMessage(changes));
    }
}
