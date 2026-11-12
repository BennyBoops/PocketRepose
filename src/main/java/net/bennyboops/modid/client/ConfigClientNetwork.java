package net.bennyboops.modid.client;

import net.bennyboops.modid.config.ConfigNetwork;
import net.bennyboops.modid.config.ConfigState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.nbt.NbtCompound;

/** The client's half of the settings sync. */
public final class ConfigClientNetwork {
    private ConfigClientNetwork() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ConfigNetwork.SettingsPayload.ID,
                (payload, context) -> ConfigState.receive(payload.settings()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ConfigState.clear());
    }

    /** Asks the server to change settings. It only does if the player is an operator. */
    public static void sendChanges(NbtCompound changes) {
        if (ClientPlayNetworking.canSend(ConfigNetwork.ChangePayload.ID)) {
            ClientPlayNetworking.send(new ConfigNetwork.ChangePayload(changes));
        }
    }
}
