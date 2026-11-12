package net.bennyboops.modid.client;

import net.bennyboops.modid.config.ConfigNetwork;
import net.bennyboops.modid.config.ConfigState;
import net.minecraft.nbt.NbtCompound;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;

/** The client's half of the settings sync, and the config button in the mods list. */
public final class ConfigClientNetwork {
    private ConfigClientNetwork() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ConfigState.clear());
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((client, parent) -> new PocketReposeConfigScreen(parent)));
    }

    /** Asks the server to change settings. It only does if the player is an operator. */
    public static void sendChanges(NbtCompound changes) {
        ConfigNetwork.sendToServer(changes);
    }
}
