package net.bennyboops.modid.client;

import net.bennyboops.modid.config.ConfigNetwork;
import net.bennyboops.modid.config.ConfigState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;

/** The client's half of the settings sync. */
public final class ConfigClientNetwork {
    private ConfigClientNetwork() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ConfigNetwork.SETTINGS, (client, handler, buf, responseSender) -> {
            NbtCompound settings = buf.readNbt();
            if (settings != null) client.execute(() -> ConfigState.receive(settings));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ConfigState.clear());
    }

    /** Asks the server to change settings. It only does if the player is an operator. */
    public static void sendChanges(NbtCompound changes) {
        if (!ClientPlayNetworking.canSend(ConfigNetwork.CHANGE)) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeNbt(changes);
        ClientPlayNetworking.send(ConfigNetwork.CHANGE, buf);
    }
}
